package edu.facultysync.service;

import edu.facultysync.db.*;
import edu.facultysync.model.*;
import edu.facultysync.model.ConflictResult.Severity;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Auto-resolve scheduling conflicts using a backtracking algorithm.
 * Attempts to reassign conflicting events to available alternative rooms.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Detect all actionable conflicts (HARD_OVERLAP and TIGHT_TRANSITION)</li>
 *   <li>For each conflict, try reassigning event B to a valid alternative room</li>
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

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
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
     * Attempt to automatically resolve actionable conflicts.
     * @return summary of the resolution session
     */
    public ResolveResult resolveAll() throws SQLException {
        return resolveAll(null);
    }

    /**
     * Attempt to automatically resolve actionable conflicts with progress updates.
     */
    public ResolveResult resolveAll(ProgressCallback progressCallback) throws SQLException {
        cache.refresh();
        ScheduledEventDAO eventDao = new ScheduledEventDAO(dbManager);
        LocationDAO locationDao = new LocationDAO(dbManager);

        List<ScheduledEvent> workingEvents = eventDao.findAll();
        cache.enrichAll(workingEvents);
        Map<Integer, ScheduledEvent> eventsById = workingEvents.stream()
            .filter(e -> e.getEventId() != null)
            .collect(Collectors.toMap(ScheduledEvent::getEventId, e -> e));

        List<ConflictResult> conflicts = conflictEngine.analyze(workingEvents);

        // Handle all conflict types that can be resolved via room reassignment.
        List<ConflictResult> actionable = new ArrayList<>();
        for (ConflictResult c : conflicts) {
            if (c.getSeverity() == Severity.HARD_OVERLAP
                    || c.getSeverity() == Severity.TIGHT_TRANSITION) {
                actionable.add(c);
            }
        }

        int totalConflicts = actionable.size();
        int resolved = 0;
        int unresolvable = 0;
        List<String> actions = new ArrayList<>();

        if (progressCallback != null) {
            if (totalConflicts == 0) {
                progressCallback.onProgress(1, 1, "No resolvable conflicts found.");
            } else {
                progressCallback.onProgress(0, totalConflicts, "Auto-resolve started...");
            }
        }

        int processed = 0;

        for (ConflictResult conflict : actionable) {
            ScheduledEvent eventToMove = conflict.getEventB();
            if (eventToMove == null || eventToMove.getEventId() == null
                    || eventToMove.getStartEpoch() == null || eventToMove.getEndEpoch() == null) {
                unresolvable++;
                actions.add("UNRESOLVABLE: " + formatEvent(eventToMove, conflict) + " — invalid event data");
                processed++;
                reportProgress(progressCallback, processed, totalConflicts,
                        "Skipped invalid conflict data (" + processed + "/" + totalConflicts + ")");
                continue;
            }

            ScheduledEvent mutableEvent = eventsById.get(eventToMove.getEventId());
            if (mutableEvent == null) {
                unresolvable++;
                actions.add("UNRESOLVABLE: " + formatEvent(eventToMove, conflict) + " — event missing in working set");
                processed++;
                reportProgress(progressCallback, processed, totalConflicts,
                        "Skipped stale conflict data (" + processed + "/" + totalConflicts + ")");
                continue;
            }

            // Get minimum capacity needed
            Course course = cache.getCourse(mutableEvent.getCourseId());
            int minCap = (course != null && course.getEnrollmentCount() != null)
                    ? course.getEnrollmentCount() : 0;

            // Find available rooms during this time slot
            List<Location> available = locationDao.findAvailable(
                    mutableEvent.getStartEpoch(), mutableEvent.getEndEpoch(), minCap);

            if (conflict.getSeverity() == Severity.TIGHT_TRANSITION) {
                ScheduledEvent first = conflict.getEventA() != null && conflict.getEventA().getEventId() != null
                        ? eventsById.get(conflict.getEventA().getEventId())
                        : conflict.getEventA();
                Location firstLoc = cache.getLocation(first != null ? first.getLocId() : null);
                if (firstLoc != null) {
                    available.removeIf(loc -> !Objects.equals(loc.getBuilding(), firstLoc.getBuilding()));
                }
            }

            // Remove the current room from alternatives
            if (mutableEvent.getLocId() != null) {
                available.removeIf(loc -> loc.getLocId().equals(mutableEvent.getLocId()));
            }

            if (available.isEmpty()) {
                unresolvable++;
                actions.add("UNRESOLVABLE: " + formatEvent(mutableEvent, conflict) + " — no alternative rooms available");
                processed++;
                reportProgress(progressCallback, processed, totalConflicts,
                        "No alternative rooms for event " + safeEventLabel(mutableEvent));
                continue;
            }

            // Try each alternative with backtracking
            boolean wasResolved = false;
            Integer originalLocId = mutableEvent.getLocId();

            for (Location alt : available) {
                // Tentatively reassign
                mutableEvent.setLocId(alt.getLocId());

                // Check if this creates/retains conflicts for the moved event.
                List<ConflictResult> newConflicts = conflictEngine.analyze(workingEvents);

                // Success only when the moved event is no longer involved in any conflict.
                boolean eventStillConflicts = newConflicts.stream()
                        .anyMatch(c -> involvesEvent(c, mutableEvent.getEventId()));

                if (!eventStillConflicts) {
                    eventDao.update(mutableEvent);
                    resolved++;
                    String courseCode = mutableEvent.getCourseCode() != null
                            ? mutableEvent.getCourseCode()
                            : "Event#" + mutableEvent.getEventId();
                    Location oldLoc = cache.getLocation(originalLocId);
                    String oldName = oldLoc != null ? oldLoc.getDisplayName() : "Room#" + originalLocId;
                    actions.add("RESOLVED (" + conflict.getSeverity() + "): "
                            + courseCode + " moved from " + oldName + " to " + alt.getDisplayName());
                    wasResolved = true;
                    break;
                }

                // Backtrack
                mutableEvent.setLocId(originalLocId);
            }

            if (!wasResolved) {
                // Restore original
                mutableEvent.setLocId(originalLocId);
                unresolvable++;
                actions.add("UNRESOLVABLE: " + formatEvent(mutableEvent, conflict) + " — all alternatives still conflict");
            }

            processed++;
            reportProgress(progressCallback, processed, totalConflicts,
                    "Processed " + safeEventLabel(mutableEvent) + " (" + processed + "/" + totalConflicts + ")");
        }

        cache.refresh();
        return new ResolveResult(totalConflicts, resolved, unresolvable, actions);
    }

    private void reportProgress(ProgressCallback callback, int current, int total, String message) {
        if (callback != null) {
            callback.onProgress(current, Math.max(total, 1), message);
        }
    }

    private String safeEventLabel(ScheduledEvent event) {
        if (event == null) {
            return "event";
        }
        return event.getCourseCode() != null
                ? event.getCourseCode()
                : "Event#" + event.getEventId();
    }

    private boolean involvesEvent(ConflictResult conflict, int eventId) {
        return (conflict.getEventA() != null && conflict.getEventA().getEventId() != null
                && conflict.getEventA().getEventId() == eventId)
                || (conflict.getEventB() != null && conflict.getEventB().getEventId() != null
                && conflict.getEventB().getEventId() == eventId);
    }

    private String formatEvent(ScheduledEvent ev, ConflictResult conflict) {
        if (ev == null) {
            return "Unknown event";
        }
        String code = ev.getCourseCode() != null ? ev.getCourseCode() : "Event#" + ev.getEventId();
        return code + " (" + conflict.getDescription() + ")";
    }
}
