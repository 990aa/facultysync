package edu.facultysync.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all ORM model classes: Department, Professor, Course, Location, ScheduledEvent, ConflictResult.
 */
class ModelTest {

    // ===== Department =====

    @Test
    void department_defaultConstructor() {
        Department d = new Department();
        assertNull(d.getDeptId());
        assertNull(d.getName());
    }

    @Test
    void department_paramConstructor() {
        Department d = new Department(1, "Computer Science");
        assertEquals(1, d.getDeptId());
        assertEquals("Computer Science", d.getName());
    }

    @Test
    void department_settersGetters() {
        Department d = new Department();
        d.setDeptId(5);
        d.setName("Math");
        assertEquals(5, d.getDeptId());
        assertEquals("Math", d.getName());
    }

    @Test
    void department_toStringWithName() {
        assertEquals("Physics", new Department(1, "Physics").toString());
    }

    @Test
    void department_toStringWithoutName() {
        Department d = new Department();
        d.setDeptId(7);
        assertEquals("Department#7", d.toString());
    }

    @Test
    void department_equalsAndHashCode() {
        Department a = new Department(1, "CS");
        Department b = new Department(1, "Different");
        Department c = new Department(2, "CS");
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void department_equalsNull() {
        assertNotEquals(new Department(1, "CS"), null);
    }

    @Test
    void department_equalsSameInstance() {
        Department d = new Department(1, "CS");
        assertEquals(d, d);
    }

    @Test
    void department_equalsDifferentType() {
        assertNotEquals(new Department(1, "CS"), "CS");
    }

    @Test
    void department_equalsNullId() {
        Department a = new Department(null, "CS");
        Department b = new Department(null, "CS");
        assertNotEquals(a, b); // null ids can't be equal
    }

    @Test
    void department_hashCodeNullId() {
        assertEquals(0, new Department(null, "CS").hashCode());
    }

    // ===== Professor =====

    @Test
    void professor_defaultConstructor() {
        Professor p = new Professor();
        assertNull(p.getProfId());
        assertNull(p.getName());
        assertNull(p.getDeptId());
    }

    @Test
    void professor_paramConstructor() {
        Professor p = new Professor(1, "Dr. Smith", 2);
        assertEquals(1, p.getProfId());
        assertEquals("Dr. Smith", p.getName());
        assertEquals(2, p.getDeptId());
    }

    @Test
    void professor_settersGetters() {
        Professor p = new Professor();
        p.setProfId(3);
        p.setName("Dr. Jones");
        p.setDeptId(4);
        assertEquals(3, p.getProfId());
        assertEquals("Dr. Jones", p.getName());
        assertEquals(4, p.getDeptId());
    }

    @Test
    void professor_toString() {
        assertEquals("Dr. Smith", new Professor(1, "Dr. Smith", 1).toString());
        Professor p = new Professor();
        p.setProfId(5);
        assertEquals("Professor#5", p.toString());
    }

    @Test
    void professor_equalsAndHashCode() {
        Professor a = new Professor(1, "A", 1);
        Professor b = new Professor(1, "B", 2);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new Professor(2, "A", 1));
    }

    // ===== Course =====

    @Test
    void course_defaultConstructor() {
        Course c = new Course();
        assertNull(c.getCourseId());
        assertNull(c.getCourseCode());
        assertNull(c.getProfId());
        assertNull(c.getEnrollmentCount());
    }

    @Test
    void course_paramConstructor() {
        Course c = new Course(1, "CS101", 2, 35);
        assertEquals(1, c.getCourseId());
        assertEquals("CS101", c.getCourseCode());
        assertEquals(2, c.getProfId());
        assertEquals(35, c.getEnrollmentCount());
    }

    @Test
    void course_settersGetters() {
        Course c = new Course();
        c.setCourseId(10);
        c.setCourseCode("MATH200");
        c.setProfId(3);
        c.setEnrollmentCount(null); // test nullable wrapper
        assertEquals(10, c.getCourseId());
        assertEquals("MATH200", c.getCourseCode());
        assertEquals(3, c.getProfId());
        assertNull(c.getEnrollmentCount());
    }

    @Test
    void course_toString() {
        assertEquals("CS101", new Course(1, "CS101", 1, 30).toString());
        Course c = new Course();
        c.setCourseId(7);
        assertEquals("Course#7", c.toString());
    }

    @Test
    void course_equalsAndHashCode() {
        Course a = new Course(1, "CS101", 1, 30);
        Course b = new Course(1, "CS102", 2, 40);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // ===== Location =====

    @Test
    void location_defaultConstructor() {
        Location l = new Location();
        assertNull(l.getLocId());
        assertNull(l.getBuilding());
        assertNull(l.getRoomNumber());
        assertNull(l.getCapacity());
        assertNull(l.getHasProjector());
    }

    @Test
    void location_paramConstructor() {
        Location l = new Location(1, "Science Hall", "101", 50, 1);
        assertEquals(1, l.getLocId());
        assertEquals("Science Hall", l.getBuilding());
        assertEquals("101", l.getRoomNumber());
        assertEquals(50, l.getCapacity());
        assertEquals(1, l.getHasProjector());
    }

    @Test
    void location_displayName() {
        assertEquals("Science Hall 101", new Location(1, "Science Hall", "101", 50, 1).getDisplayName());
        assertEquals("? ?", new Location().getDisplayName());
    }

    @Test
    void location_toString() {
        Location l = new Location(1, "Building A", "202", 30, 0);
        assertEquals("Building A 202", l.toString());
    }

    @Test
    void location_nullCapacity() {
        Location l = new Location(1, "B", "1", null, null);
        assertNull(l.getCapacity());
        assertNull(l.getHasProjector());
    }

    @Test
    void location_equalsAndHashCode() {
        Location a = new Location(1, "A", "1", 10, 1);
        Location b = new Location(1, "B", "2", 20, 0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // ===== ScheduledEvent =====

    @Test
    void scheduledEvent_defaultConstructor() {
        ScheduledEvent e = new ScheduledEvent();
        assertNull(e.getEventId());
        assertNull(e.getCourseId());
        assertNull(e.getLocId());
        assertNull(e.getEventType());
        assertNull(e.getStartEpoch());
        assertNull(e.getEndEpoch());
    }

    @Test
    void scheduledEvent_paramConstructor() {
        ScheduledEvent e = new ScheduledEvent(1, 2, 3,
                ScheduledEvent.EventType.LECTURE, 1000L, 2000L);
        assertEquals(1, e.getEventId());
        assertEquals(2, e.getCourseId());
        assertEquals(3, e.getLocId());
        assertEquals(ScheduledEvent.EventType.LECTURE, e.getEventType());
        assertEquals(1000L, e.getStartEpoch());
        assertEquals(2000L, e.getEndEpoch());
    }

    @Test
    void scheduledEvent_schedulableInterface() {
        ScheduledEvent e = new ScheduledEvent(1, 1, 1,
                ScheduledEvent.EventType.EXAM, 5000L, 6000L);
        assertEquals(5000L, e.getStart());
        assertEquals(6000L, e.getEnd());
    }

    @Test
    void scheduledEvent_schedulableNullDefaults() {
        ScheduledEvent e = new ScheduledEvent();
        assertEquals(0L, e.getStart());
        assertEquals(0L, e.getEnd());
    }

    @Test
    void scheduledEvent_durationMinutes() {
        ScheduledEvent e = new ScheduledEvent(1, 1, 1,
                ScheduledEvent.EventType.LECTURE, 0L, 90 * 60_000L);
        assertEquals(90, e.getDurationMinutes());
    }

    @Test
    void scheduledEvent_durationNullTimes() {
        assertEquals(0, new ScheduledEvent().getDurationMinutes());
    }

    @Test
    void scheduledEvent_transientFields() {
        ScheduledEvent e = new ScheduledEvent();
        e.setCourseCode("CS101");
        e.setLocationName("Science Hall 101");
        e.setProfessorName("Dr. Smith");
        assertEquals("CS101", e.getCourseCode());
        assertEquals("Science Hall 101", e.getLocationName());
        assertEquals("Dr. Smith", e.getProfessorName());
    }

    @Test
    void scheduledEvent_toString() {
        ScheduledEvent e = new ScheduledEvent(1, 1, 1, ScheduledEvent.EventType.EXAM, 0L, 1L);
        e.setCourseCode("CS101");
        assertEquals("Exam [CS101]", e.toString());
    }

    @Test
    void scheduledEvent_toStringNoCourseCode() {
        ScheduledEvent e = new ScheduledEvent(1, 5, 1, ScheduledEvent.EventType.LECTURE, 0L, 1L);
        assertEquals("Lecture [course=5]", e.toString());
    }

    @Test
    void scheduledEvent_nullLocId() {
        ScheduledEvent e = new ScheduledEvent(1, 1, null,
                ScheduledEvent.EventType.OFFICE_HOURS, 1000L, 2000L);
        assertNull(e.getLocId());
    }

    @Test
    void scheduledEvent_equalsAndHashCode() {
        ScheduledEvent a = new ScheduledEvent(1, 1, 1, ScheduledEvent.EventType.LECTURE, 0L, 1L);
        ScheduledEvent b = new ScheduledEvent(1, 2, 2, ScheduledEvent.EventType.EXAM, 10L, 20L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // ===== EventType =====

    @ParameterizedTest
    @CsvSource({
        "Lecture, LECTURE",
        "LECTURE, LECTURE",
        "lecture, LECTURE",
        "Exam, EXAM",
        "EXAM, EXAM",
        "Office Hours, OFFICE_HOURS",
        "OFFICE_HOURS, OFFICE_HOURS",
        "OFFICE HOURS, OFFICE_HOURS",
        "OfficeHours, OFFICE_HOURS"
    })
    void eventType_fromString(String input, ScheduledEvent.EventType expected) {
        assertEquals(expected, ScheduledEvent.EventType.fromString(input));
    }

    @Test
    void eventType_fromStringNull() {
        assertNull(ScheduledEvent.EventType.fromString(null));
    }

    @Test
    void eventType_fromStringUnknown() {
        assertNull(ScheduledEvent.EventType.fromString("Unknown"));
    }

    @Test
    void eventType_display() {
        assertEquals("Lecture", ScheduledEvent.EventType.LECTURE.getDisplay());
        assertEquals("Exam", ScheduledEvent.EventType.EXAM.getDisplay());
        assertEquals("Office Hours", ScheduledEvent.EventType.OFFICE_HOURS.getDisplay());
    }

    @Test
    void eventType_toStringDisplay() {
        assertEquals("Lecture", ScheduledEvent.EventType.LECTURE.toString());
    }

    // ===== ConflictResult =====

    @Test
    void conflictResult_construction() {
        ScheduledEvent a = new ScheduledEvent(1, 1, 1, ScheduledEvent.EventType.LECTURE, 0L, 1L);
        ScheduledEvent b = new ScheduledEvent(2, 2, 1, ScheduledEvent.EventType.EXAM, 0L, 1L);
        ConflictResult cr = new ConflictResult(a, b, ConflictResult.Severity.HARD_OVERLAP, "Overlap!");
        assertEquals(a, cr.getEventA());
        assertEquals(b, cr.getEventB());
        assertEquals(ConflictResult.Severity.HARD_OVERLAP, cr.getSeverity());
        assertEquals("Overlap!", cr.getDescription());
        assertNull(cr.getAvailableAlternatives());
    }

    @Test
    void conflictResult_alternatives() {
        ScheduledEvent a = new ScheduledEvent(1, 1, 1, ScheduledEvent.EventType.LECTURE, 0L, 1L);
        ScheduledEvent b = new ScheduledEvent(2, 2, 1, ScheduledEvent.EventType.EXAM, 0L, 1L);
        ConflictResult cr = new ConflictResult(a, b, ConflictResult.Severity.HARD_OVERLAP, "desc");
        java.util.List<Location> alts = java.util.List.of(new Location(10, "B", "1", 50, 1));
        cr.setAvailableAlternatives(alts);
        assertEquals(1, cr.getAvailableAlternatives().size());
    }

    @Test
    void conflictResult_toString() {
        ScheduledEvent a = new ScheduledEvent(1, 1, 1, ScheduledEvent.EventType.LECTURE, 0L, 1L);
        ConflictResult cr = new ConflictResult(a, a, ConflictResult.Severity.TIGHT_TRANSITION, "Tight!");
        assertEquals("TIGHT_TRANSITION: Tight!", cr.toString());
    }

    @Test
    void conflictSeverity_values() {
        assertEquals(2, ConflictResult.Severity.values().length);
        assertNotNull(ConflictResult.Severity.valueOf("HARD_OVERLAP"));
        assertNotNull(ConflictResult.Severity.valueOf("TIGHT_TRANSITION"));
    }
}
