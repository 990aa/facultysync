package edu.facultysync.db;

import edu.facultysync.model.*;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SeedData – ensuring demo data is correctly seeded and idempotent.
 */
class SeedDataTest {

    private DatabaseManager dbManager;

    @BeforeEach
    void setup() throws SQLException {
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        dbManager.initializeSchema();
    }

    @AfterEach
    void teardown() {
        dbManager.close();
    }

    // ===== seedIfEmpty =====

    @Test
    void seedIfEmpty_seedsEmptyDatabase() throws SQLException {
        SeedData.seedIfEmpty(dbManager);

        List<Department> depts = new DepartmentDAO(dbManager).findAll();
        assertFalse(depts.isEmpty(), "Should have seeded departments");
        assertEquals(5, depts.size(), "Should have 5 departments");
    }

    @Test
    void seedIfEmpty_doesNotSeedTwice() throws SQLException {
        SeedData.seedIfEmpty(dbManager);
        int countBefore = new DepartmentDAO(dbManager).findAll().size();

        // Call again – should be idempotent
        SeedData.seedIfEmpty(dbManager);
        int countAfter = new DepartmentDAO(dbManager).findAll().size();

        assertEquals(countBefore, countAfter, "seedIfEmpty should be idempotent");
    }

    @Test
    void seedIfEmpty_doesNotSeedWhenDataExists() throws SQLException {
        // Insert a department manually
        new DepartmentDAO(dbManager).insert(new Department(null, "Pre-existing Dept"));

        SeedData.seedIfEmpty(dbManager);

        // Should only have the one we manually inserted
        List<Department> depts = new DepartmentDAO(dbManager).findAll();
        assertEquals(1, depts.size(), "Should not seed when data already exists");
    }

    // ===== seed =====

    @Test
    void seed_createsDepartments() throws SQLException {
        SeedData.seed(dbManager);

        List<Department> depts = new DepartmentDAO(dbManager).findAll();
        assertEquals(5, depts.size());

        List<String> names = depts.stream().map(Department::getName).toList();
        assertTrue(names.contains("Computer Science"));
        assertTrue(names.contains("Mathematics"));
        assertTrue(names.contains("Physics"));
        assertTrue(names.contains("Engineering"));
        assertTrue(names.contains("Business"));
    }

    @Test
    void seed_createsProfessors() throws SQLException {
        SeedData.seed(dbManager);

        List<Professor> profs = new ProfessorDAO(dbManager).findAll();
        assertEquals(9, profs.size());

        List<String> names = profs.stream().map(Professor::getName).toList();
        assertTrue(names.contains("Dr. Alan Turing"));
        assertTrue(names.contains("Dr. Edsger Dijkstra"));
        assertTrue(names.contains("Dr. Donald Knuth"));
        assertTrue(names.contains("Dr. Leonhard Euler"));
        assertTrue(names.contains("Dr. Carl Gauss"));
        assertTrue(names.contains("Dr. Isaac Newton"));
        assertTrue(names.contains("Dr. Richard Feynman"));
        assertTrue(names.contains("Dr. Nikola Tesla"));
        assertTrue(names.contains("Dr. Peter Drucker"));
    }

    @Test
    void seed_createsCourses() throws SQLException {
        SeedData.seed(dbManager);

        List<Course> courses = new CourseDAO(dbManager).findAll();
        assertEquals(10, courses.size());

        List<String> codes = courses.stream().map(Course::getCourseCode).toList();
        assertTrue(codes.contains("CS101"));
        assertTrue(codes.contains("CS201"));
        assertTrue(codes.contains("CS301"));
        assertTrue(codes.contains("CS401"));
        assertTrue(codes.contains("MATH101"));
        assertTrue(codes.contains("MATH201"));
        assertTrue(codes.contains("PHYS101"));
        assertTrue(codes.contains("PHYS201"));
        assertTrue(codes.contains("ENG101"));
        assertTrue(codes.contains("BUS101"));
    }

    @Test
    void seed_coursesHaveEnrollmentCounts() throws SQLException {
        SeedData.seed(dbManager);

        CourseDAO dao = new CourseDAO(dbManager);
        Course cs101 = dao.findByCode("CS101");
        assertNotNull(cs101);
        assertNotNull(cs101.getEnrollmentCount());
        assertEquals(150, cs101.getEnrollmentCount());
    }

    @Test
    void seed_createsLocations() throws SQLException {
        SeedData.seed(dbManager);

        List<Location> locs = new LocationDAO(dbManager).findAll();
        assertEquals(10, locs.size());

        // Check specific buildings
        long sciACount = locs.stream().filter(l -> "Science Building A".equals(l.getBuilding())).count();
        assertTrue(sciACount >= 3, "Should have at least 3 rooms in Science Building A");

        long engCount = locs.stream().filter(l -> "Engineering Hall".equals(l.getBuilding())).count();
        assertTrue(engCount >= 2, "Should have at least 2 rooms in Engineering Hall");
    }

    @Test
    void seed_createsEvents() throws SQLException {
        SeedData.seed(dbManager);

        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
        assertTrue(events.size() >= 30, "Should have at least 30 events. Got: " + events.size());
    }

    @Test
    void seed_eventsHaveValidCourseIds() throws SQLException {
        SeedData.seed(dbManager);

        CourseDAO courseDao = new CourseDAO(dbManager);
        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
        for (ScheduledEvent e : events) {
            assertNotNull(e.getCourseId());
            assertNotNull(courseDao.findById(e.getCourseId()),
                    "Event references non-existent course: " + e.getCourseId());
        }
    }

    @Test
    void seed_eventsHaveValidLocationIds() throws SQLException {
        SeedData.seed(dbManager);

        LocationDAO locDao = new LocationDAO(dbManager);
        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
        for (ScheduledEvent e : events) {
            if (e.getLocId() != null) {
                assertNotNull(locDao.findById(e.getLocId()),
                        "Event references non-existent location: " + e.getLocId());
            }
        }
    }

    @Test
    void seed_eventsHaveValidTimeRanges() throws SQLException {
        SeedData.seed(dbManager);

        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
        for (ScheduledEvent e : events) {
            assertNotNull(e.getStartEpoch());
            assertNotNull(e.getEndEpoch());
            assertTrue(e.getEndEpoch() > e.getStartEpoch(),
                    "Event end should be after start: " + e.getEventId());
        }
    }

    @Test
    void seed_eventsHaveValidTypes() throws SQLException {
        SeedData.seed(dbManager);

        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
        for (ScheduledEvent e : events) {
            assertNotNull(e.getEventType(), "Event should have a type: " + e.getEventId());
        }
    }

    @Test
    void seed_hasAllEventTypes() throws SQLException {
        SeedData.seed(dbManager);

        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
        boolean hasLecture = events.stream().anyMatch(e -> e.getEventType() == ScheduledEvent.EventType.LECTURE);
        boolean hasExam = events.stream().anyMatch(e -> e.getEventType() == ScheduledEvent.EventType.EXAM);
        boolean hasOfficeHours = events.stream().anyMatch(e -> e.getEventType() == ScheduledEvent.EventType.OFFICE_HOURS);

        assertTrue(hasLecture, "Should have lecture events");
        assertTrue(hasExam, "Should have exam events");
        assertTrue(hasOfficeHours, "Should have office hours events");
    }

    // ===== Intentional conflicts =====

    @Test
    void seed_createsIntentionalConflicts() throws SQLException {
        SeedData.seed(dbManager);

        edu.facultysync.service.DataCache cache = new edu.facultysync.service.DataCache(dbManager);
        cache.refresh();
        edu.facultysync.service.ConflictEngine engine = new edu.facultysync.service.ConflictEngine(dbManager, cache);
        List<edu.facultysync.model.ConflictResult> conflicts = engine.analyzeAll();

        assertFalse(conflicts.isEmpty(),
                "Seed data should include intentional scheduling conflicts");
    }

    @Test
    void seed_hasHardOverlapConflicts() throws SQLException {
        SeedData.seed(dbManager);

        edu.facultysync.service.DataCache cache = new edu.facultysync.service.DataCache(dbManager);
        cache.refresh();
        edu.facultysync.service.ConflictEngine engine = new edu.facultysync.service.ConflictEngine(dbManager, cache);
        List<edu.facultysync.model.ConflictResult> conflicts = engine.analyzeAll();

        long hardOverlaps = conflicts.stream()
                .filter(c -> c.getSeverity() == edu.facultysync.model.ConflictResult.Severity.HARD_OVERLAP)
                .count();
        assertTrue(hardOverlaps >= 3, "Should have at least 3 hard overlap conflicts. Got: " + hardOverlaps);
    }

    @Test
    void seed_hasTightTransitionConflicts() throws SQLException {
        SeedData.seed(dbManager);

        edu.facultysync.service.DataCache cache = new edu.facultysync.service.DataCache(dbManager);
        cache.refresh();
        edu.facultysync.service.ConflictEngine engine = new edu.facultysync.service.ConflictEngine(dbManager, cache);
        List<edu.facultysync.model.ConflictResult> conflicts = engine.analyzeAll();

        long tightTransitions = conflicts.stream()
                .filter(c -> c.getSeverity() == edu.facultysync.model.ConflictResult.Severity.TIGHT_TRANSITION)
                .count();
        assertTrue(tightTransitions >= 1, "Should have at least 1 tight transition conflict. Got: " + tightTransitions);
    }

    @Test
    void seed_hasProfessorOverlapConflicts() throws SQLException {
        SeedData.seed(dbManager);

        edu.facultysync.service.DataCache cache = new edu.facultysync.service.DataCache(dbManager);
        cache.refresh();
        edu.facultysync.service.ConflictEngine engine = new edu.facultysync.service.ConflictEngine(dbManager, cache);
        List<edu.facultysync.model.ConflictResult> conflicts = engine.analyzeAll();

        long professorOverlaps = conflicts.stream()
                .filter(c -> c.getSeverity() == edu.facultysync.model.ConflictResult.Severity.PROFESSOR_OVERLAP)
                .count();
        assertTrue(professorOverlaps >= 1,
                "Should have at least 1 professor overlap conflict. Got: " + professorOverlaps);
    }

    @Test
    void seed_conflictsCoverEveryDepartment() throws SQLException {
        SeedData.seed(dbManager);

        edu.facultysync.service.DataCache cache = new edu.facultysync.service.DataCache(dbManager);
        cache.refresh();
        edu.facultysync.service.ConflictEngine engine = new edu.facultysync.service.ConflictEngine(dbManager, cache);
        List<edu.facultysync.model.ConflictResult> conflicts = engine.analyzeAll();

        Set<String> departmentsInConflicts = new HashSet<>();
        for (edu.facultysync.model.ConflictResult conflict : conflicts) {
            collectDepartment(conflict.getEventA(), cache, departmentsInConflicts);
            collectDepartment(conflict.getEventB(), cache, departmentsInConflicts);
        }

        assertTrue(departmentsInConflicts.contains("Computer Science"));
        assertTrue(departmentsInConflicts.contains("Mathematics"));
        assertTrue(departmentsInConflicts.contains("Physics"));
        assertTrue(departmentsInConflicts.contains("Engineering"));
        assertTrue(departmentsInConflicts.contains("Business"));
    }

        @Test
        void ensureIntentionalConflicts_restoresDemoConflicts() throws SQLException {
        SeedData.seed(dbManager);

        edu.facultysync.service.DataCache cache = new edu.facultysync.service.DataCache(dbManager);
        cache.refresh();
        edu.facultysync.service.AutoResolver resolver = new edu.facultysync.service.AutoResolver(dbManager, cache);
        resolver.resolveAll();

        SeedData.ensureIntentionalConflicts(dbManager);

        edu.facultysync.service.ConflictEngine engine = new edu.facultysync.service.ConflictEngine(dbManager, cache);
        List<edu.facultysync.model.ConflictResult> conflicts = engine.analyzeAll();
        long hardOverlaps = conflicts.stream()
            .filter(c -> c.getSeverity() == edu.facultysync.model.ConflictResult.Severity.HARD_OVERLAP)
            .count();
        long tightTransitions = conflicts.stream()
            .filter(c -> c.getSeverity() == edu.facultysync.model.ConflictResult.Severity.TIGHT_TRANSITION)
            .count();

        assertTrue(hardOverlaps >= 3, "Should restore at least 3 hard overlaps");
        assertTrue(tightTransitions >= 1, "Should restore at least 1 tight transition");
        }

    @Test
    void ensureIntentionalConflicts_handlesNormalizedCourseCodes() throws SQLException {
        SeedData.seed(dbManager);

        CourseDAO courseDao = new CourseDAO(dbManager);
        Course cs101 = courseDao.findByCode("CS101");
        assertNotNull(cs101);

        // Simulate a user editing a demo course code format.
        cs101 = cs101.withCourseCode("CS 101");
        courseDao.update(cs101);

        edu.facultysync.service.DataCache cache = new edu.facultysync.service.DataCache(dbManager);
        cache.refresh();
        edu.facultysync.service.AutoResolver resolver = new edu.facultysync.service.AutoResolver(dbManager, cache);
        resolver.resolveAll();

        SeedData.ensureIntentionalConflicts(dbManager);

        edu.facultysync.service.ConflictEngine engine = new edu.facultysync.service.ConflictEngine(dbManager, cache);
        List<edu.facultysync.model.ConflictResult> conflicts = engine.analyzeAll();

        long hardOverlaps = conflicts.stream()
                .filter(c -> c.getSeverity() == edu.facultysync.model.ConflictResult.Severity.HARD_OVERLAP)
                .count();
        assertTrue(hardOverlaps >= 3, "Should restore hard overlaps even with normalized course code edits");
    }

    // ===== Professor-Department associations =====

    @Test
    void seed_professorsLinkedToCorrectDepartments() throws SQLException {
        SeedData.seed(dbManager);

        ProfessorDAO profDao = new ProfessorDAO(dbManager);
        DepartmentDAO deptDao = new DepartmentDAO(dbManager);

        // Find CS department
        Department cs = deptDao.findAll().stream()
                .filter(d -> "Computer Science".equals(d.getName()))
                .findFirst().orElse(null);
        assertNotNull(cs);

        List<Professor> csProfs = profDao.findByDepartment(cs.getDeptId());
        assertTrue(csProfs.size() >= 3, "CS should have at least 3 professors");
    }

    private void collectDepartment(ScheduledEvent event,
                                   edu.facultysync.service.DataCache cache,
                                   Set<String> out) {
        if (event == null || event.getCourseId() == null) {
            return;
        }
        Course course = cache.getCourse(event.getCourseId());
        if (course == null || course.getProfId() == null) {
            return;
        }
        Professor professor = cache.getProfessor(course.getProfId());
        if (professor == null || professor.getDeptId() == null) {
            return;
        }
        Department department = cache.getDepartment(professor.getDeptId());
        if (department != null && department.getName() != null) {
            out.add(department.getName());
        }
    }
}
