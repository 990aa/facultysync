package edu.facultysync.service;

import edu.facultysync.algo.IntervalTree;
import edu.facultysync.db.*;
import edu.facultysync.model.*;
import edu.facultysync.model.ConflictResult.Severity;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The Conflict Engine – detects schedule collisions using IntervalTree
 * and provides alternative room suggestions.
 *
 * <p>Conflict types detected:
 * <ul>
 *   <li><b>HARD_OVERLAP</b> – same room, overlapping times</li>
 *   <li><b>TIGHT_TRANSITION</b> – same professor, different buildings, gap &lt; threshold</li>
 * </ul>
 */
public class ConflictEngine {

    /** Default minimum gap (millis) between events in different buildings before warning. */
    private static final long DEFAULT_TIGHT_TRANSITION_THRESHOLD_MS = 15 * 60_000;
    private static final String THRESHOLD_MINUTES_PROPERTY = "facultysync.tightTransitionMinutes";
    private static final String THRESHOLD_MINUTES_ENV = "FACULTYSYNC_TIGHT_TRANSITION_MINUTES";

    private final DatabaseManager dbManager;
    private final DataCache cache;
    private final long tightTransitionThresholdMs;

    public ConflictEngine(DatabaseManager dbManager, DataCache cache) {
        this.dbManager = dbManager;
        this.cache = cache;
        this.tightTransitionThresholdMs = resolveThresholdMs();
    }

    /**
     * Run full conflict analysis on all scheduled events.
     * @return list of detected conflicts
     */
    public List<ConflictResult> analyzeAll() throws SQLException {
        cache.refresh();
        List<ScheduledEvent> allEvents = new ScheduledEventDAO(dbManager).findAll();
        cache.enrichAll(allEvents);
        return analyze(allEvents);
    }

    /**
     * Analyze a specific list of events for conflicts.
     */
    public List<ConflictResult> analyze(List<ScheduledEvent> events) throws SQLException {
        List<ConflictResult> conflicts = new ArrayList<>();
        Set<String> overlapPairKeys = new HashSet<>();

        // 1. Room-based overlap detection using IntervalTree per location
        Map<Integer, List<ScheduledEvent>> byLocation = events.stream()
                .filter(e -> e.getLocId() != null)
                .collect(Collectors.groupingBy(ScheduledEvent::getLocId));

        for (Map.Entry<Integer, List<ScheduledEvent>> entry : byLocation.entrySet()) {
            Integer locId = entry.getKey();
            List<ScheduledEvent> locEvents = entry.getValue();
            IntervalTree<ScheduledEvent> tree = new IntervalTree<>(locEvents);
            List<List<ScheduledEvent>> overlaps = tree.findAllOverlaps();
            for (List<ScheduledEvent> pair : overlaps) {
                ScheduledEvent a = pair.get(0);
                ScheduledEvent b = pair.get(1);
                Location loc = cache.getLocation(locId);
                String desc = String.format("Room %s is double-booked: %s overlaps with %s",
                        loc != null ? loc.getDisplayName() : locId,
                        formatEvent(a), formatEvent(b));
                ConflictResult cr = new ConflictResult(a, b, Severity.HARD_OVERLAP, desc);
                overlapPairKeys.add(pairKey(a, b));

                // Suggest alternatives
                int minCap = getMinCapacity(a, b);
                List<Location> alternatives = new LocationDAO(dbManager)
                        .findAvailable(
                                Math.min(a.getStart(), b.getStart()),
                                Math.max(a.getEnd(), b.getEnd()),
                                minCap);
                cr.setAvailableAlternatives(alternatives);
                conflicts.add(cr);
            }
        }

        // 2. Professor-based hard overlap detection (double-booking)
        Map<Integer, List<ScheduledEvent>> byProf = new HashMap<>();
        for (ScheduledEvent e : events) {
            Course c = cache.getCourse(e.getCourseId());
            if (c != null && c.getProfId() != null) {
                byProf.computeIfAbsent(c.getProfId(), k -> new ArrayList<>()).add(e);
            }
        }

        for (Map.Entry<Integer, List<ScheduledEvent>> entry : byProf.entrySet()) {
            Integer profId = entry.getKey();
            List<ScheduledEvent> profEvents = entry.getValue();
            IntervalTree<ScheduledEvent> tree = new IntervalTree<>(profEvents);
            List<List<ScheduledEvent>> overlaps = tree.findAllOverlaps();

            for (List<ScheduledEvent> pair : overlaps) {
                ScheduledEvent a = pair.get(0);
                ScheduledEvent b = pair.get(1);
                String key = pairKey(a, b);
                if (overlapPairKeys.contains(key)) {
                    continue;
                }

                String professorName = a.getProfessorName();
                if (professorName == null || professorName.isBlank()) {
                    Professor professor = cache.getProfessor(profId);
                    professorName = professor != null ? professor.getName() : "Professor";
                }

                String desc = String.format(
                        "Professor %s is double-booked: %s overlaps with %s",
                        professorName,
                        formatEvent(a),
                        formatEvent(b)
                );
                conflicts.add(new ConflictResult(a, b, Severity.PROFESSOR_OVERLAP, desc));
            }
        }

        // 3. Professor-based tight-transition detection
        for (Map.Entry<Integer, List<ScheduledEvent>> entry : byProf.entrySet()) {
            List<ScheduledEvent> profEvents = entry.getValue();
            profEvents.sort(Comparator.comparingLong(ScheduledEvent::getStart));
            for (int i = 0; i < profEvents.size() - 1; i++) {
                ScheduledEvent a = profEvents.get(i);
                ScheduledEvent b = profEvents.get(i + 1);
                long gap = b.getStart() - a.getEnd();
                if (gap >= 0 && gap < tightTransitionThresholdMs
                        && a.getLocId() != null && b.getLocId() != null) {
                    Location locA = cache.getLocation(a.getLocId());
                    Location locB = cache.getLocation(b.getLocId());
                    if (locA != null && locB != null
                            && !Objects.equals(locA.getBuilding(), locB.getBuilding())) {
                        String desc = String.format(
                                "Tight transition for %s: %d min gap between %s (%s) and %s (%s)",
                                a.getProfessorName() != null ? a.getProfessorName() : "Professor",
                                gap / 60_000,
                                formatEvent(a), locA.getDisplayName(),
                                formatEvent(b), locB.getDisplayName());

                        ConflictResult cr = new ConflictResult(a, b, Severity.TIGHT_TRANSITION, desc);
                        int minCap = getRequiredCapacity(b);
                        List<Location> alternatives = new LocationDAO(dbManager)
                                .findAvailable(b.getStart(), b.getEnd(), minCap)
                                .stream()
                                .filter(loc -> Objects.equals(loc.getBuilding(), locA.getBuilding()))
                                .filter(loc -> !Objects.equals(loc.getLocId(), b.getLocId()))
                                .collect(Collectors.toList());
                        cr.setAvailableAlternatives(alternatives);
                        conflicts.add(cr);
                    }
                }
            }
        }

        return conflicts;
    }

    public long getTightTransitionThresholdMs() {
        return tightTransitionThresholdMs;
    }

    private long resolveThresholdMs() {
        String raw = System.getProperty(THRESHOLD_MINUTES_PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(THRESHOLD_MINUTES_ENV);
        }
        if (raw != null && !raw.isBlank()) {
            try {
                long minutes = Long.parseLong(raw.trim());
                if (minutes > 0) {
                    return minutes * 60_000;
                }
            } catch (NumberFormatException ignored) {
                // Fall back to default if misconfigured.
            }
        }
        return DEFAULT_TIGHT_TRANSITION_THRESHOLD_MS;
    }

    private String formatEvent(ScheduledEvent e) {
        String code = e.getCourseCode() != null ? e.getCourseCode() : "Course#" + e.getCourseId();
        String type = e.getEventType() != null ? e.getEventType().getDisplay() : "Event";
        return code + " " + type;
    }

    private int getMinCapacity(ScheduledEvent a, ScheduledEvent b) {
        Course ca = cache.getCourse(a.getCourseId());
        Course cb = cache.getCourse(b.getCourseId());
        int capA = (ca != null && ca.getEnrollmentCount() != null) ? ca.getEnrollmentCount() : 0;
        int capB = (cb != null && cb.getEnrollmentCount() != null) ? cb.getEnrollmentCount() : 0;
        return Math.max(capA, capB);
    }

    private int getRequiredCapacity(ScheduledEvent event) {
        Course c = cache.getCourse(event.getCourseId());
        return (c != null && c.getEnrollmentCount() != null) ? c.getEnrollmentCount() : 0;
    }

    private String pairKey(ScheduledEvent a, ScheduledEvent b) {
        Integer aId = a != null ? a.getEventId() : null;
        Integer bId = b != null ? b.getEventId() : null;

        if (aId == null || bId == null) {
            long startA = a != null ? a.getStart() : 0;
            long startB = b != null ? b.getStart() : 0;
            if (startA <= startB) {
                return startA + "-" + startB + "-ephemeral";
            }
            return startB + "-" + startA + "-ephemeral";
        }

        int first = Math.min(aId, bId);
        int second = Math.max(aId, bId);
        return first + ":" + second;
    }
}
