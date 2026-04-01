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
        cache.refresh();
        List<ConflictResult> conflicts = conflictEngine.analyzeAll();

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

        ScheduledEventDAO eventDao = new ScheduledEventDAO(dbManager);
        LocationDAO locationDao = new LocationDAO(dbManager);

        for (ConflictResult conflict : actionable) {
            cache.refresh();

            ScheduledEvent eventToMove = conflict.getEventB();
            if (eventToMove == null || eventToMove.getEventId() == null
                    || eventToMove.getStartEpoch() == null || eventToMove.getEndEpoch() == null) {
                unresolvable++;
                actions.add("UNRESOLVABLE: " + formatEvent(eventToMove, conflict) + " — invalid event data");
                continue;
            }

            // Get minimum capacity needed
            Course course = cache.getCourse(eventToMove.getCourseId());
            int minCap = (course != null && course.getEnrollmentCount() != null)
                    ? course.getEnrollmentCount() : 0;

            // Find available rooms during this time slot
            List<Location> available = locationDao.findAvailable(
                    eventToMove.getStartEpoch(), eventToMove.getEndEpoch(), minCap);

            if (conflict.getSeverity() == Severity.TIGHT_TRANSITION) {
                ScheduledEvent first = conflict.getEventA();
                Location firstLoc = cache.getLocation(first != null ? first.getLocId() : null);
                if (firstLoc != null) {
                    available.removeIf(loc -> !Objects.equals(loc.getBuilding(), firstLoc.getBuilding()));
                }
            }

            // Remove the current room from alternatives
            if (eventToMove.getLocId() != null) {
                available.removeIf(loc -> loc.getLocId().equals(eventToMove.getLocId()));
            }

            if (available.isEmpty()) {
                unresolvable++;
                actions.add("UNRESOLVABLE: " + formatEvent(eventToMove, conflict) + " — no alternative rooms available");
                continue;
            }

            // Try each alternative with backtracking
            boolean wasResolved = false;
            Integer originalLocId = eventToMove.getLocId();

            for (Location alt : available) {
                // Tentatively reassign
                eventToMove.setLocId(alt.getLocId());
                eventDao.update(eventToMove);

                // Check if this creates new conflicts
                cache.refresh();
                List<ConflictResult> newConflicts = conflictEngine.analyzeAll();

                // Success only when the moved event is no longer involved in any conflict.
                boolean eventStillConflicts = newConflicts.stream()
                        .anyMatch(c -> involvesEvent(c, eventToMove.getEventId()));

                if (!eventStillConflicts) {
                    resolved++;
                    String courseCode = eventToMove.getCourseCode() != null
                            ? eventToMove.getCourseCode()
                            : "Event#" + eventToMove.getEventId();
                    Location oldLoc = cache.getLocation(originalLocId);
                    String oldName = oldLoc != null ? oldLoc.getDisplayName() : "Room#" + originalLocId;
                    actions.add("RESOLVED (" + conflict.getSeverity() + "): "
                            + courseCode + " moved from " + oldName + " to " + alt.getDisplayName());
                    wasResolved = true;
                    break;
                }

                // Backtrack
                eventToMove.setLocId(originalLocId);
                eventDao.update(eventToMove);
            }

            if (!wasResolved) {
                // Restore original
                eventToMove.setLocId(originalLocId);
                eventDao.update(eventToMove);
                unresolvable++;
                actions.add("UNRESOLVABLE: " + formatEvent(eventToMove, conflict) + " — all alternatives still conflict");
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
        if (ev == null) {
            return "Unknown event";
        }
        String code = ev.getCourseCode() != null ? ev.getCourseCode() : "Event#" + ev.getEventId();
        return code + " (" + conflict.getDescription() + ")";
    }
}
