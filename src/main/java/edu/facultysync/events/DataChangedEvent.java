package edu.facultysync.events;

/**
 * Broadcast when persisted schedule data changes and dependent UI views should refresh.
 */
public record DataChangedEvent(String reason) {
}
