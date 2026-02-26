package edu.facultysync.service;

import edu.facultysync.db.*;
import edu.facultysync.model.*;
import edu.facultysync.model.ConflictResult.Severity;

import java.sql.SQLException;
import java.util.*;

/**
 * Auto-resolve scheduling conflicts using a backtracking algorithm.
 * Attempts to reassign conflicting events to available alternative rooms.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Detect all HARD_OVERLAP conflicts</li>
 *   <li>For each conflict, try reassigning event B to an available room</li>
 *   <li>Verify no new conflicts are introduced</li>
 *   <li>Backtrack if the reassignment causes additional conflicts</li>
 * </ol>
 */
public class AutoResolver {

    private final DatabaseManager dbManager;
    private final DataCache cache;
    private final ConflictEngine conflictEngine;

    public AutoResolver(DatabaseManager dbManager, DataCache cache) {
        this.dbManager = dbManager;
        this.cache = cache;
        this.conflictEngine = new ConflictEngine(dbManager, cache);
    }

    /**
     * Result of an auto-resolve session.
     */
    public static class ResolveResult {
        private final int totalConflicts;
        private final int resolved;
        private final int unresolvable;
        private final List<String> actions;

        public ResolveResult(int totalConflicts, int resolved, int unresolvable, List<String> actions) {
            this.totalConflicts = totalConflicts;
            this.resolved = resolved;
            this.unresolvable = unresolvable;
            this.actions = actions;
        }

        public int getTotalConflicts() { return totalConflicts; }
        public int getResolved() { return resolved; }
        public int getUnresolvable() { return unresolvable; }
        public List<String> getActions() { return actions; }
    }

    /**
     * Attempt to automatically resolve all HARD_OVERLAP conflicts.
     * @return summary of the resolution session
     */
    public ResolveResult resolveAll() throws SQLException {
        cache.refresh();
        List<ConflictResult> conflicts = conflictEngine.analyzeAll();

        // Only handle hard overlaps (room double-bookings)
        List<ConflictResult> hardOverlaps = new ArrayList<>();
        for (ConflictResult c : conflicts) {
            if (c.getSeverity() == Severity.HARD_OVERLAP) {
                hardOverlaps.add(c);
            }
        }

        int totalConflicts = hardOverlaps.size();
        int resolved = 0;
        int unresolvable = 0;
        List<String> actions = new ArrayList<>();
        Set<Integer> alreadyMoved = new HashSet<>();

        ScheduledEventDAO eventDao = new ScheduledEventDAO(dbManager);
        LocationDAO locationDao = new LocationDAO(dbManager);

        for (ConflictResult conflict : hardOverlaps) {
            ScheduledEvent eventB = conflict.getEventB();
            if (eventB == null || eventB.getEventId() == null) {
                unresolvable++;
                continue;
            }

            // Skip if already moved
            if (alreadyMoved.contains(eventB.getEventId())) {
                continue;
            }

            // Get minimum capacity needed
            Course course = cache.getCourse(eventB.getCourseId());
            int minCap = (course != null && course.getEnrollmentCount() != null)
                    ? course.getEnrollmentCount() : 0;

            // Find available rooms during this time slot
            List<Location> available = locationDao.findAvailable(
                    eventB.getStartEpoch(), eventB.getEndEpoch(), minCap);

            // Remove the current conflicting room from alternatives
            if (eventB.getLocId() != null) {
                available.removeIf(loc -> loc.getLocId().equals(eventB.getLocId()));
            }

            if (available.isEmpty()) {
                unresolvable++;
                actions.add("UNRESOLVABLE: " + formatEvent(eventB, conflict) + " — no alternative rooms available");
                continue;
            }

            // Try each alternative with backtracking
            boolean wasResolved = false;
            Integer originalLocId = eventB.getLocId();

            for (Location alt : available) {
                // Tentatively reassign
                eventB.setLocId(alt.getLocId());
                eventDao.update(eventB);

                // Check if this creates new conflicts
                cache.refresh();
                List<ConflictResult> newConflicts = conflictEngine.analyzeAll();
                long newHardOverlaps = newConflicts.stream()
                        .filter(c -> c.getSeverity() == Severity.HARD_OVERLAP)
                        .count();

                // If we haven't introduced new hard overlaps for this event
                boolean eventStillConflicts = newConflicts.stream()
                        .filter(c -> c.getSeverity() == Severity.HARD_OVERLAP)
                        .anyMatch(c -> involvesEvent(c, eventB.getEventId()));

                if (!eventStillConflicts) {
                    // Success!
                    resolved++;
                    alreadyMoved.add(eventB.getEventId());
                    String courseCode = eventB.getCourseCode() != null ? eventB.getCourseCode() : "Event#" + eventB.getEventId();
                    Location oldLoc = cache.getLocation(originalLocId);
                    String oldName = oldLoc != null ? oldLoc.getDisplayName() : "Room#" + originalLocId;
                    actions.add("RESOLVED: " + courseCode + " moved from " + oldName + " to " + alt.getDisplayName());
                    wasResolved = true;
                    break;
                } else {
                    // Backtrack
                    eventB.setLocId(originalLocId);
                    eventDao.update(eventB);
                }
            }

            if (!wasResolved) {
                // Restore original
                eventB.setLocId(originalLocId);
                eventDao.update(eventB);
                unresolvable++;
                actions.add("UNRESOLVABLE: " + formatEvent(eventB, conflict) + " — all alternatives still conflict");
            }
        }

        cache.refresh();
        return new ResolveResult(totalConflicts, resolved, unresolvable, actions);
    }

    private boolean involvesEvent(ConflictResult conflict, int eventId) {
        return (conflict.getEventA() != null && conflict.getEventA().getEventId() != null
                && conflict.getEventA().getEventId() == eventId)
                || (conflict.getEventB() != null && conflict.getEventB().getEventId() != null
                && conflict.getEventB().getEventId() == eventId);
    }

    private String formatEvent(ScheduledEvent ev, ConflictResult conflict) {
        String code = ev.getCourseCode() != null ? ev.getCourseCode() : "Event#" + ev.getEventId();
        return code + " (" + conflict.getDescription() + ")";
    }
}
