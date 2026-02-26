package edu.facultysync.model;

/**
 * Represents a scheduled event (Lecture, Exam, Office Hours).
 * Uses epoch milliseconds for start/end times.
 * Implements Schedulable for IntervalTree compatibility.
 */
public class ScheduledEvent implements Schedulable {

    public enum EventType {
        LECTURE("Lecture"),
        EXAM("Exam"),
        OFFICE_HOURS("Office Hours");

        private final String display;
        EventType(String display) { this.display = display; }
        public String getDisplay() { return display; }

        public static EventType fromString(String s) {
            if (s == null) return null;
            switch (s.trim().toUpperCase()) {
                case "LECTURE": return LECTURE;
                case "EXAM": return EXAM;
                case "OFFICE HOURS":
                case "OFFICE_HOURS":
                case "OFFICEHOURS": return OFFICE_HOURS;
                default: return null;
            }
        }

        @Override
        public String toString() { return display; }
    }

    private Integer eventId;
    private Integer courseId;
    private Integer locId;     // nullable – online events may have no room
    private EventType eventType;
    private Long startEpoch;   // epoch millis
    private Long endEpoch;     // epoch millis

    // Transient helpers – not persisted, populated from cache
    private transient String courseCode;
    private transient String locationName;
    private transient String professorName;

    public ScheduledEvent() {}

    public ScheduledEvent(Integer eventId, Integer courseId, Integer locId,
                          EventType eventType, Long startEpoch, Long endEpoch) {
        this.eventId = eventId;
        this.courseId = courseId;
        this.locId = locId;
        this.eventType = eventType;
        this.startEpoch = startEpoch;
        this.endEpoch = endEpoch;
    }

    // Schedulable implementation
    @Override
    public long getStart() { return startEpoch != null ? startEpoch : 0L; }
    @Override
    public long getEnd() { return endEpoch != null ? endEpoch : 0L; }

    // Getters & setters
    public Integer getEventId() { return eventId; }
    public void setEventId(Integer eventId) { this.eventId = eventId; }

    public Integer getCourseId() { return courseId; }
    public void setCourseId(Integer courseId) { this.courseId = courseId; }

    public Integer getLocId() { return locId; }
    public void setLocId(Integer locId) { this.locId = locId; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public Long getStartEpoch() { return startEpoch; }
    public void setStartEpoch(Long startEpoch) { this.startEpoch = startEpoch; }

    public Long getEndEpoch() { return endEpoch; }
    public void setEndEpoch(Long endEpoch) { this.endEpoch = endEpoch; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getProfessorName() { return professorName; }
    public void setProfessorName(String professorName) { this.professorName = professorName; }

    /**
     * Returns duration in minutes.
     */
    public long getDurationMinutes() {
        if (startEpoch == null || endEpoch == null) return 0;
        return (endEpoch - startEpoch) / 60_000;
    }

    @Override
    public String toString() {
        return (eventType != null ? eventType.getDisplay() : "Event") +
               " [" + (courseCode != null ? courseCode : "course=" + courseId) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduledEvent that = (ScheduledEvent) o;
        return eventId != null && eventId.equals(that.eventId);
    }

    @Override
    public int hashCode() { return eventId != null ? eventId.hashCode() : 0; }
}
