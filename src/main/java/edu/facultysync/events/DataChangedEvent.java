package edu.facultysync.events;

/**
 * Broadcast when persisted schedule data changes and dependent UI views should refresh.
 *
 * @param reason short machine-readable reason code (for example: course-added, dashboard-refresh)
 */
public record DataChangedEvent(String reason) {
}
