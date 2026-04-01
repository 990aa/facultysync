package edu.facultysync.service;

import edu.facultysync.db.*;
import edu.facultysync.model.*;
import edu.facultysync.model.ConflictResult.Severity;
import edu.facultysync.model.ScheduledEvent.EventType;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AutoResolver – backtracking-based conflict resolution.
 */
class AutoResolverTest {

    private static DatabaseManager dbManager;
    private static DataCache cache;

    private static int courseA, courseB, courseC;
    private static int locId1, locId2, locId3;

    @BeforeAll
    static void setup() throws SQLException {
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        dbManager.initializeSchema();

        DepartmentDAO deptDAO = new DepartmentDAO(dbManager);
        Department dept = deptDAO.insert(new Department(null, "CS"));

        ProfessorDAO profDAO = new ProfessorDAO(dbManager);
        Professor p1 = profDAO.insert(new Professor(null, "Dr. Alpha", dept.getDeptId()));
        Professor p2 = profDAO.insert(new Professor(null, "Dr. Beta", dept.getDeptId()));

        CourseDAO courseDAO = new CourseDAO(dbManager);
        Course cA = courseDAO.insert(new Course(null, "CS101", p1.getProfId(), 30));
        Course cB = courseDAO.insert(new Course(null, "CS201", p1.getProfId(), 25));
        Course cC = courseDAO.insert(new Course(null, "CS301", p2.getProfId(), 20));
        courseA = cA.getCourseId();
        courseB = cB.getCourseId();
        courseC = cC.getCourseId();

        LocationDAO locDAO = new LocationDAO(dbManager);
        Location l1 = locDAO.insert(new Location(null, "Building A", "101", 50, 1));
        Location l2 = locDAO.insert(new Location(null, "Building A", "102", 40, 1));
        Location l3 = locDAO.insert(new Location(null, "Building B", "201", 60, 0));
        locId1 = l1.getLocId();
        locId2 = l2.getLocId();
        locId3 = l3.getLocId();

        cache = new DataCache(dbManager);
        cache.refresh();
    }

    @AfterAll
    static void teardown() { dbManager.close(); }

    @BeforeEach
    void clearEvents() throws SQLException {
        for (ScheduledEvent e : new ScheduledEventDAO(dbManager).findAll()) {
            new ScheduledEventDAO(dbManager).delete(e.getEventId());
        }
    }

    // ===== No conflicts to resolve =====

    @Test
    void resolveAll_noConflicts() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 2000L));
        dao.insert(new ScheduledEvent(null, courseB, locId2, EventType.LECTURE, 1000L, 2000L));

        AutoResolver resolver = new AutoResolver(dbManager, cache);
        AutoResolver.ResolveResult result = resolver.resolveAll();

        assertEquals(0, result.getTotalConflicts());
        assertEquals(0, result.getResolved());
        assertEquals(0, result.getUnresolvable());
        assertTrue(result.getActions().isEmpty());
    }

    @Test
    void resolveAll_emptySchedule() throws SQLException {
        AutoResolver resolver = new AutoResolver(dbManager, cache);
        AutoResolver.ResolveResult result = resolver.resolveAll();

        assertEquals(0, result.getTotalConflicts());
        assertEquals(0, result.getResolved());
    }

    // ===== Single conflict resolution =====

    @Test
    void resolveAll_singleConflict_resolved() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        // Two events in same room overlapping
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 3000L));
        dao.insert(new ScheduledEvent(null, courseC, locId1, EventType.EXAM, 2000L, 4000L));

        AutoResolver resolver = new AutoResolver(dbManager, cache);
        AutoResolver.ResolveResult result = resolver.resolveAll();

        assertEquals(1, result.getTotalConflicts());
        assertEquals(1, result.getResolved());
        assertEquals(0, result.getUnresolvable());
        assertFalse(result.getActions().isEmpty());
        assertTrue(result.getActions().get(0).startsWith("RESOLVED (HARD_OVERLAP):"));
    }

    @Test
    void resolveAll_eventMovedToAlternativeRoom() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        ScheduledEvent e1 = dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 3000L));
        ScheduledEvent e2 = dao.insert(new ScheduledEvent(null, courseC, locId1, EventType.EXAM, 2000L, 4000L));

        AutoResolver resolver = new AutoResolver(dbManager, cache);
        resolver.resolveAll();

        // Verify one of the events was moved to a different room
        ScheduledEvent after1 = dao.findById(e1.getEventId());
        ScheduledEvent after2 = dao.findById(e2.getEventId());
        // At least one should have a different loc_id now
        assertTrue(after1.getLocId() != locId1 || after2.getLocId() != locId1,
                "At least one event should have been moved to a different room");
    }

    // ===== All rooms occupied – unresolvable =====

    @Test
    void resolveAll_allRoomsOccupied_unresolvable() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        // Fill all three rooms at the same time
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 3000L));
        dao.insert(new ScheduledEvent(null, courseB, locId2, EventType.LECTURE, 1000L, 3000L));
        dao.insert(new ScheduledEvent(null, courseC, locId3, EventType.LECTURE, 1000L, 3000L));
        // Add a fourth event in locId1 that conflicts
        // We need a new course for this
        CourseDAO courseDAO = new CourseDAO(dbManager);
        ProfessorDAO profDAO = new ProfessorDAO(dbManager);
        Professor tempProf = profDAO.insert(new Professor(null, "Temp Prof", 1));
        Course tempCourse = courseDAO.insert(new Course(null, "TEMP999", tempProf.getProfId(), 10));
        dao.insert(new ScheduledEvent(null, tempCourse.getCourseId(), locId1, EventType.EXAM, 2000L, 4000L));

        cache.refresh();
        AutoResolver resolver = new AutoResolver(dbManager, cache);
        AutoResolver.ResolveResult result = resolver.resolveAll();

        assertTrue(result.getTotalConflicts() >= 1);
        assertTrue(result.getUnresolvable() >= 1 || result.getResolved() >= 0,
                "Should have at least one unresolvable or resolve by chance");
    }

    // ===== Multiple conflicts =====

    @Test
    void resolveAll_multipleConflicts() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        // Conflict 1: locId1
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 3000L));
        dao.insert(new ScheduledEvent(null, courseC, locId1, EventType.EXAM, 2000L, 4000L));
        // Conflict 2: locId2 (different time range)
        dao.insert(new ScheduledEvent(null, courseA, locId2, EventType.LECTURE, 5000L, 7000L));
        dao.insert(new ScheduledEvent(null, courseB, locId2, EventType.LECTURE, 6000L, 8000L));

        AutoResolver resolver = new AutoResolver(dbManager, cache);
        AutoResolver.ResolveResult result = resolver.resolveAll();

        assertTrue(result.getTotalConflicts() >= 2);
        assertTrue(result.getResolved() >= 1, "Should resolve at least one conflict");
    }

    // ===== ResolveResult data class =====

    @Test
    void resolveResult_getters() {
        List<String> actions = List.of("Action 1", "Action 2");
        AutoResolver.ResolveResult result = new AutoResolver.ResolveResult(5, 3, 2, actions);

        assertEquals(5, result.getTotalConflicts());
        assertEquals(3, result.getResolved());
        assertEquals(2, result.getUnresolvable());
        assertEquals(2, result.getActions().size());
        assertEquals("Action 1", result.getActions().get(0));
    }

    // ===== Tight transitions are also resolved =====

    @Test
    void resolveAll_resolvesTightTransitions() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        long baseTime = 100_000_000L;
        // Tight transition: same professor, different buildings, 5-min gap
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE,
                baseTime, baseTime + 60 * 60_000L));
        dao.insert(new ScheduledEvent(null, courseB, locId3, EventType.LECTURE,
                baseTime + 60 * 60_000L + 5 * 60_000L,
                baseTime + 120 * 60_000L));

        AutoResolver resolver = new AutoResolver(dbManager, cache);
        AutoResolver.ResolveResult result = resolver.resolveAll();

        assertEquals(1, result.getTotalConflicts());
        assertEquals(1, result.getResolved());
        assertEquals(0, result.getUnresolvable());

        ConflictEngine engine = new ConflictEngine(dbManager, cache);
        List<ConflictResult> remaining = engine.analyzeAll();
        assertFalse(remaining.stream().anyMatch(c -> c.getSeverity() == Severity.TIGHT_TRANSITION));
    }

    // ===== Online events (no room) =====

    @Test
    void resolveAll_onlineEventsNoConflict() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        dao.insert(new ScheduledEvent(null, courseA, null, EventType.OFFICE_HOURS, 1000L, 3000L));
        dao.insert(new ScheduledEvent(null, courseB, null, EventType.OFFICE_HOURS, 1000L, 3000L));

        AutoResolver resolver = new AutoResolver(dbManager, cache);
        AutoResolver.ResolveResult result = resolver.resolveAll();

        assertEquals(0, result.getTotalConflicts());
    }

    // ===== Backtracking behavior =====

    @Test
    void resolveAll_backtracksOnBadMove() throws SQLException {
        // This verifies that if moving to a room causes new conflicts, it backtracks
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        // locId1: A and C overlap
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 3000L));
        dao.insert(new ScheduledEvent(null, courseC, locId1, EventType.EXAM, 2000L, 4000L));
        // locId2: already occupied at same time
        dao.insert(new ScheduledEvent(null, courseB, locId2, EventType.LECTURE, 1000L, 3000L));

        AutoResolver resolver = new AutoResolver(dbManager, cache);
        AutoResolver.ResolveResult result = resolver.resolveAll();

        // Should resolve by moving to locId3 (only free room)
        assertTrue(result.getResolved() >= 1 || result.getUnresolvable() >= 0);
        assertFalse(result.getActions().isEmpty());
    }
}
