package edu.facultysync.model;

import java.util.List;

/**
 * Encapsulates the result of a conflict check between two overlapping events.
 */
public class ConflictResult {

    public enum Severity {
        /** Hard overlap – two events share the same time and location. */
        HARD_OVERLAP,
        /** Hard overlap – same professor is booked for overlapping events. */
        PROFESSOR_OVERLAP,
        /** Tight transition – professor must move between buildings with little gap. */
        TIGHT_TRANSITION
    }

    private final ScheduledEvent eventA;
    private final ScheduledEvent eventB;
    private final Severity severity;
    private final String description;
    private List<Location> availableAlternatives;

    public ConflictResult(ScheduledEvent eventA, ScheduledEvent eventB,
                          Severity severity, String description) {
        this.eventA = eventA;
        this.eventB = eventB;
        this.severity = severity;
        this.description = description;
    }

    public ScheduledEvent getEventA() { return eventA; }
    public ScheduledEvent getEventB() { return eventB; }
    public Severity getSeverity() { return severity; }
    public String getDescription() { return description; }

    public List<Location> getAvailableAlternatives() { return availableAlternatives; }
    public void setAvailableAlternatives(List<Location> availableAlternatives) {
        this.availableAlternatives = availableAlternatives;
    }

    @Override
    public String toString() {
        return severity + ": " + description;
    }
}
