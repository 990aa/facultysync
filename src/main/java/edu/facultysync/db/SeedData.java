package edu.facultysync.db;

import edu.facultysync.model.*;
import edu.facultysync.model.ScheduledEvent.EventType;
import edu.facultysync.util.TimePolicy;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;

/**
 * Seeds the database with realistic demonstration data.
 * Creates departments, professors, courses, locations, and scheduled events
 * including intentional conflicts for testing the conflict detection engine.
 *
 * <p>Only seeds if the database is empty (idempotent).</p>
 */
public class SeedData {

    /**
     * Seed the database if it is empty (no departments exist).
     */
    public static void seedIfEmpty(DatabaseManager dbManager) throws SQLException {
        DepartmentDAO deptDao = new DepartmentDAO(dbManager);
        if (!deptDao.findAll().isEmpty()) return; // already seeded

        seed(dbManager);
    }

    /**
     * Force-seed the database with demo data.
     */
    public static void seed(DatabaseManager dbManager) throws SQLException {
        DepartmentDAO deptDao = new DepartmentDAO(dbManager);
        ProfessorDAO profDao = new ProfessorDAO(dbManager);
        CourseDAO courseDao = new CourseDAO(dbManager);
        LocationDAO locDao = new LocationDAO(dbManager);
        ScheduledEventDAO eventDao = new ScheduledEventDAO(dbManager);

        // ===== DEPARTMENTS =====
        Department cs = deptDao.insert(new Department(null, "Computer Science"));
        Department math = deptDao.insert(new Department(null, "Mathematics"));
        Department phys = deptDao.insert(new Department(null, "Physics"));
        Department eng = deptDao.insert(new Department(null, "Engineering"));
        Department bus = deptDao.insert(new Department(null, "Business"));

        // ===== PROFESSORS =====
        Professor pTuring = profDao.insert(new Professor(null, "Dr. Alan Turing", cs.getDeptId()));
        Professor pDijkstra = profDao.insert(new Professor(null, "Dr. Edsger Dijkstra", cs.getDeptId()));
        Professor pKnuth = profDao.insert(new Professor(null, "Dr. Donald Knuth", cs.getDeptId()));
        Professor pEuler = profDao.insert(new Professor(null, "Dr. Leonhard Euler", math.getDeptId()));
        Professor pGauss = profDao.insert(new Professor(null, "Dr. Carl Gauss", math.getDeptId()));
        Professor pNewton = profDao.insert(new Professor(null, "Dr. Isaac Newton", phys.getDeptId()));
        Professor pFeynman = profDao.insert(new Professor(null, "Dr. Richard Feynman", phys.getDeptId()));
        Professor pTesla = profDao.insert(new Professor(null, "Dr. Nikola Tesla", eng.getDeptId()));
        Professor pDrucker = profDao.insert(new Professor(null, "Dr. Peter Drucker", bus.getDeptId()));

        // ===== COURSES =====
        Course cs101 = courseDao.insert(new Course(null, "CS101", pTuring.getProfId(), 150));
        Course cs201 = courseDao.insert(new Course(null, "CS201", pDijkstra.getProfId(), 80));
        Course cs301 = courseDao.insert(new Course(null, "CS301", pKnuth.getProfId(), 45));
        Course cs401 = courseDao.insert(new Course(null, "CS401", pTuring.getProfId(), 30));
        Course math101 = courseDao.insert(new Course(null, "MATH101", pEuler.getProfId(), 200));
        Course math201 = courseDao.insert(new Course(null, "MATH201", pGauss.getProfId(), 60));
        Course phys101 = courseDao.insert(new Course(null, "PHYS101", pNewton.getProfId(), 120));
        Course phys201 = courseDao.insert(new Course(null, "PHYS201", pFeynman.getProfId(), 90));
        Course eng101 = courseDao.insert(new Course(null, "ENG101", pTesla.getProfId(), 100));
        Course bus101 = courseDao.insert(new Course(null, "BUS101", pDrucker.getProfId(), 180));

        // ===== LOCATIONS =====
        Location sciA101 = locDao.insert(new Location(null, "Science Building A", "101", 200, 1));
        Location sciA201 = locDao.insert(new Location(null, "Science Building A", "201", 80, 1));
        Location sciA301 = locDao.insert(new Location(null, "Science Building A", "301", 50, 1));
        Location sciB102 = locDao.insert(new Location(null, "Science Building B", "102", 150, 1));
        Location sciB202 = locDao.insert(new Location(null, "Science Building B", "202", 60, 0));
        Location engHall = locDao.insert(new Location(null, "Engineering Hall", "100", 120, 1));
        Location busCenter = locDao.insert(new Location(null, "Business Center", "A1", 200, 1));
        Location libRoom = locDao.insert(new Location(null, "Library", "Seminar-1", 25, 1));

        // ===== SCHEDULED EVENTS =====
        // Use next Monday as base date for events
        LocalDate base = getNextMonday();

        // Monday events
        eventDao.insert(makeEvent(cs101, sciA101, EventType.LECTURE, base, 0, 9, 0, 10, 30));   // Mon 9:00-10:30
        eventDao.insert(makeEvent(math101, sciA101, EventType.LECTURE, base, 0, 11, 0, 12, 30));  // Mon 11:00-12:30
        eventDao.insert(makeEvent(phys101, sciB102, EventType.LECTURE, base, 0, 9, 0, 10, 30));   // Mon 9:00-10:30
        eventDao.insert(makeEvent(bus101, busCenter, EventType.LECTURE, base, 0, 10, 0, 11, 30)); // Mon 10:00-11:30
        eventDao.insert(makeEvent(eng101, engHall, EventType.LECTURE, base, 0, 14, 0, 15, 30));   // Mon 14:00-15:30

        // INTENTIONAL CONFLICT 1: Same room, overlapping times (Mon 9:00-10:30 in Sci A 201)
        eventDao.insert(makeEvent(cs201, sciA201, EventType.LECTURE, base, 0, 9, 0, 10, 30));    // Mon 9:00-10:30
        eventDao.insert(makeEvent(math201, sciA201, EventType.LECTURE, base, 0, 9, 30, 11, 0));  // Mon 9:30-11:00 OVERLAP!

        // Tuesday events
        eventDao.insert(makeEvent(cs101, sciA101, EventType.LECTURE, base, 1, 9, 0, 10, 30));    // Tue 9:00-10:30
        eventDao.insert(makeEvent(cs301, sciA301, EventType.LECTURE, base, 1, 11, 0, 12, 30));   // Tue 11:00-12:30
        eventDao.insert(makeEvent(phys201, sciB202, EventType.LECTURE, base, 1, 13, 0, 14, 30)); // Tue 13:00-14:30
        eventDao.insert(makeEvent(bus101, busCenter, EventType.LECTURE, base, 1, 10, 0, 11, 30));// Tue 10:00-11:30
        eventDao.insert(makeEvent(eng101, engHall, EventType.LECTURE, base, 1, 15, 0, 16, 30));  // Tue 15:00-16:30

        // Wednesday events
        eventDao.insert(makeEvent(math101, sciA101, EventType.LECTURE, base, 2, 9, 0, 10, 30));  // Wed 9:00-10:30
        eventDao.insert(makeEvent(cs201, sciA201, EventType.LECTURE, base, 2, 11, 0, 12, 30));   // Wed 11:00-12:30
        eventDao.insert(makeEvent(cs401, sciA301, EventType.LECTURE, base, 2, 14, 0, 15, 30));   // Wed 14:00-15:30

        // INTENTIONAL CONFLICT 2: Same room double-booking (Wed 14:00-15:30 in Eng Hall)
        eventDao.insert(makeEvent(eng101, engHall, EventType.LECTURE, base, 2, 14, 0, 15, 30));  // Wed 14:00-15:30
        eventDao.insert(makeEvent(phys101, engHall, EventType.LECTURE, base, 2, 14, 30, 16, 0)); // Wed 14:30-16:00 OVERLAP!

        // INTENTIONAL CONFLICT 3: Tight transition – Dr. Turing in different buildings with <15min gap
        // CS101 at Sci A 101 ending 10:30, CS401 at Eng Hall starting 10:35 (5 min gap, different buildings)
        eventDao.insert(makeEvent(cs101, sciA101, EventType.LECTURE, base, 3, 9, 0, 10, 30));    // Thu 9:00-10:30 Sci A
        eventDao.insert(makeEvent(cs401, engHall, EventType.LECTURE, base, 3, 10, 35, 12, 0));   // Thu 10:35-12:00 Eng Hall

        // Thursday events
        eventDao.insert(makeEvent(math201, sciB202, EventType.LECTURE, base, 3, 13, 0, 14, 30)); // Thu 13:00-14:30
        eventDao.insert(makeEvent(phys201, sciB102, EventType.LECTURE, base, 3, 15, 0, 16, 30)); // Thu 15:00-16:30

        // Friday events
        eventDao.insert(makeEvent(cs101, sciA101, EventType.LECTURE, base, 4, 9, 0, 10, 30));    // Fri 9:00-10:30
        eventDao.insert(makeEvent(bus101, busCenter, EventType.LECTURE, base, 4, 11, 0, 12, 30));// Fri 11:00-12:30
        eventDao.insert(makeEvent(eng101, engHall, EventType.LECTURE, base, 4, 14, 0, 15, 30));  // Fri 14:00-15:30

        // Exams (next week)
        eventDao.insert(makeEvent(cs101, sciA101, EventType.EXAM, base, 7, 9, 0, 12, 0));        // Next Mon 9:00-12:00
        eventDao.insert(makeEvent(math101, busCenter, EventType.EXAM, base, 7, 13, 0, 16, 0));   // Next Mon 13:00-16:00
        eventDao.insert(makeEvent(phys101, sciB102, EventType.EXAM, base, 8, 9, 0, 12, 0));      // Next Tue 9:00-12:00

        // Office hours
        eventDao.insert(makeEvent(cs101, libRoom, EventType.OFFICE_HOURS, base, 0, 15, 0, 16, 0)); // Mon 15:00-16:00
        eventDao.insert(makeEvent(math101, libRoom, EventType.OFFICE_HOURS, base, 2, 15, 0, 16, 0));// Wed 15:00-16:00
        eventDao.insert(makeEvent(cs301, libRoom, EventType.OFFICE_HOURS, base, 4, 15, 0, 16, 0)); // Fri 15:00-16:00

        // INTENTIONAL CONFLICT 4: Office hours overlap in library
        eventDao.insert(makeEvent(phys101, libRoom, EventType.OFFICE_HOURS, base, 0, 15, 0, 16, 0)); // Mon 15:00-16:00 OVERLAP with CS101!
    }

        /**
         * Ensures all documented intentional demo conflicts exist.
         *
         * <p>This method is safe to run on every startup. It updates known demo events back to
         * conflict-causing locations/times if they were previously moved, and inserts missing
         * conflict events when needed.</p>
         */
        public static void ensureIntentionalConflicts(DatabaseManager dbManager) throws SQLException {
        CourseDAO courseDao = new CourseDAO(dbManager);
        LocationDAO locationDao = new LocationDAO(dbManager);
        ScheduledEventDAO eventDao = new ScheduledEventDAO(dbManager);

        Course cs101 = findCourseByDemoCode(courseDao, "CS101");
        Course cs201 = findCourseByDemoCode(courseDao, "CS201");
        Course cs401 = findCourseByDemoCode(courseDao, "CS401");
        Course math201 = findCourseByDemoCode(courseDao, "MATH201");
        Course phys101 = findCourseByDemoCode(courseDao, "PHYS101");
        Course eng101 = findCourseByDemoCode(courseDao, "ENG101");
        if (cs101 == null || cs201 == null || cs401 == null
            || math201 == null || phys101 == null || eng101 == null) {
            return;
        }

        Location sciA101 = findLocation(locationDao, "Science Building A", "101");
        Location sciA201 = findLocation(locationDao, "Science Building A", "201");
        Location engHall = findLocation(locationDao, "Engineering Hall", "100");
        Location libRoom = findLocation(locationDao, "Library", "Seminar-1");
        if (sciA101 == null || sciA201 == null || engHall == null || libRoom == null) {
            return;
        }

        LocalDate base = findExistingDemoBaseMonday(eventDao, cs101.getCourseId());
        if (base == null) {
            base = getNextMonday();
        }

        // HARD_OVERLAP #1
        upsertConflictEvent(eventDao, cs201.getCourseId(), sciA201.getLocId(), EventType.LECTURE,
            base, 0, 9, 0, 10, 30);
        upsertConflictEvent(eventDao, math201.getCourseId(), sciA201.getLocId(), EventType.LECTURE,
            base, 0, 9, 30, 11, 0);

        // HARD_OVERLAP #2
        upsertConflictEvent(eventDao, eng101.getCourseId(), engHall.getLocId(), EventType.LECTURE,
            base, 2, 14, 0, 15, 30);
        upsertConflictEvent(eventDao, phys101.getCourseId(), engHall.getLocId(), EventType.LECTURE,
            base, 2, 14, 30, 16, 0);

        // HARD_OVERLAP #3
        upsertConflictEvent(eventDao, cs101.getCourseId(), libRoom.getLocId(), EventType.OFFICE_HOURS,
            base, 0, 15, 0, 16, 0);
        upsertConflictEvent(eventDao, phys101.getCourseId(), libRoom.getLocId(), EventType.OFFICE_HOURS,
            base, 0, 15, 0, 16, 0);

        // TIGHT_TRANSITION
        upsertConflictEvent(eventDao, cs101.getCourseId(), sciA101.getLocId(), EventType.LECTURE,
            base, 3, 9, 0, 10, 30);
        upsertConflictEvent(eventDao, cs401.getCourseId(), engHall.getLocId(), EventType.LECTURE,
            base, 3, 10, 35, 12, 0);
        }

    private static LocalDate getNextMonday() {
        return LocalDate.now(TimePolicy.zone()).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private static LocalDate findExistingDemoBaseMonday(ScheduledEventDAO eventDao, Integer cs101CourseId)
            throws SQLException {
        if (cs101CourseId == null) {
            return null;
        }
        List<ScheduledEvent> cs101Events = eventDao.findByCourse(cs101CourseId);
        for (ScheduledEvent e : cs101Events) {
            if (e.getEventType() != EventType.LECTURE
                    || e.getStartEpoch() == null
                    || e.getEndEpoch() == null) {
                continue;
            }

            ZonedDateTime start = Instant.ofEpochMilli(e.getStartEpoch()).atZone(TimePolicy.zone());
            ZonedDateTime end = Instant.ofEpochMilli(e.getEndEpoch()).atZone(TimePolicy.zone());

            if (start.getDayOfWeek() == DayOfWeek.MONDAY
                    && start.getHour() == 9
                    && start.getMinute() == 0
                    && end.getHour() == 10
                    && end.getMinute() == 30) {
                return start.toLocalDate();
            }
        }
        return null;
    }

    private static Location findLocation(LocationDAO locationDao, String building, String roomNumber)
            throws SQLException {
        for (Location loc : locationDao.findAll()) {
            if (Objects.equals(building, loc.getBuilding())
                    && Objects.equals(roomNumber, loc.getRoomNumber())) {
                return loc;
            }
        }
        return null;
    }

    private static Course findCourseByDemoCode(CourseDAO courseDao, String demoCode) throws SQLException {
        Course exact = courseDao.findByCode(demoCode);
        if (exact != null) {
            return exact;
        }

        String normalizedDemoCode = normalizeCourseCode(demoCode);
        for (Course candidate : courseDao.findAll()) {
            if (normalizeCourseCode(candidate.getCourseCode()).equals(normalizedDemoCode)) {
                return candidate;
            }
        }
        return null;
    }

    private static String normalizeCourseCode(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private static void upsertConflictEvent(ScheduledEventDAO eventDao,
                                            Integer courseId,
                                            Integer locId,
                                            EventType type,
                                            LocalDate baseMonday,
                                            int dayOffset,
                                            int startHour,
                                            int startMin,
                                            int endHour,
                                            int endMin) throws SQLException {
        if (courseId == null || locId == null || type == null) {
            return;
        }

        long startEpoch = buildEpoch(baseMonday, dayOffset, startHour, startMin);
        long endEpoch = buildEpoch(baseMonday, dayOffset, endHour, endMin);

        List<ScheduledEvent> courseEvents = eventDao.findByCourse(courseId);
        for (ScheduledEvent existing : courseEvents) {
            if (existing.getEventType() == type
                    && Objects.equals(existing.getStartEpoch(), startEpoch)
                    && Objects.equals(existing.getEndEpoch(), endEpoch)) {
                if (!Objects.equals(existing.getLocId(), locId)) {
                    existing.setLocId(locId);
                    eventDao.update(existing);
                }
                return;
            }
        }

        eventDao.insert(new ScheduledEvent(null, courseId, locId, type, startEpoch, endEpoch));
    }

    private static long buildEpoch(LocalDate baseMonday, int dayOffset, int hour, int minute) {
        return baseMonday
            .plusDays(dayOffset)
            .atTime(hour, minute)
            .atZone(TimePolicy.zone())
            .toInstant()
            .toEpochMilli();
    }

    private static ScheduledEvent makeEvent(Course course, Location loc, EventType type,
                             LocalDate baseMonday, int dayOffset,
                                             int startHour, int startMin,
                                             int endHour, int endMin) {
        long startEpoch = buildEpoch(baseMonday, dayOffset, startHour, startMin);
        long endEpoch = buildEpoch(baseMonday, dayOffset, endHour, endMin);

        return new ScheduledEvent(null, course.getCourseId(), loc.getLocId(),
            type, startEpoch, endEpoch);
    }
}
