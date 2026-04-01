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
 * Tests for the ConflictEngine – verifying overlap detection and tight-transition detection.
 */
class ConflictEngineTest {

    private static DatabaseManager dbManager;
    private static DataCache cache;
    private static ConflictEngine engine;

    // Reference IDs
    private static int deptId, profId1, profId2, courseA, courseB, courseC, locId1, locId2;

    @BeforeAll
    static void setup() throws SQLException {
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        dbManager.initializeSchema();

        DepartmentDAO deptDAO = new DepartmentDAO(dbManager);
        Department dept = deptDAO.insert(new Department(null, "CS"));
        deptId = dept.getDeptId();

        ProfessorDAO profDAO = new ProfessorDAO(dbManager);
        Professor p1 = profDAO.insert(new Professor(null, "Dr. Alpha", deptId));
        Professor p2 = profDAO.insert(new Professor(null, "Dr. Beta", deptId));
        profId1 = p1.getProfId();
        profId2 = p2.getProfId();

        CourseDAO courseDAO = new CourseDAO(dbManager);
        Course cA = courseDAO.insert(new Course(null, "CS101", profId1, 30));
        Course cB = courseDAO.insert(new Course(null, "CS201", profId1, 25));
        Course cC = courseDAO.insert(new Course(null, "MATH100", profId2, 40));
        courseA = cA.getCourseId();
        courseB = cB.getCourseId();
        courseC = cC.getCourseId();

        LocationDAO locDAO = new LocationDAO(dbManager);
        Location l1 = locDAO.insert(new Location(null, "Building A", "101", 50, 1));
        Location l2 = locDAO.insert(new Location(null, "Building B", "201", 40, 0));
        locDAO.insert(new Location(null, "Building A", "102", 60, 1));  // available alternative
        locId1 = l1.getLocId();
        locId2 = l2.getLocId();

        cache = new DataCache(dbManager);
        cache.refresh();
        engine = new ConflictEngine(dbManager, cache);
    }

    @AfterAll
    static void teardown() { dbManager.close(); }

    @BeforeEach
    void clearEvents() throws SQLException {
        // Delete all events before each test
        for (ScheduledEvent e : new ScheduledEventDAO(dbManager).findAll()) {
            new ScheduledEventDAO(dbManager).delete(e.getEventId());
        }
    }

    @Test
    void noConflicts_emptySchedule() throws SQLException {
        List<ConflictResult> conflicts = engine.analyzeAll();
        assertTrue(conflicts.isEmpty());
    }

    @Test
    void noConflicts_noOverlap() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 2000L));
        dao.insert(new ScheduledEvent(null, courseB, locId1, EventType.LECTURE, 3000L, 4000L));
        List<ConflictResult> conflicts = engine.analyzeAll();
        assertTrue(conflicts.isEmpty());
    }

    @Test
    void hardOverlap_sameRoom() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 3000L));
        dao.insert(new ScheduledEvent(null, courseC, locId1, EventType.EXAM, 2000L, 4000L));

        List<ConflictResult> conflicts = engine.analyzeAll();
        assertFalse(conflicts.isEmpty());
        assertTrue(conflicts.stream().anyMatch(c -> c.getSeverity() == Severity.HARD_OVERLAP));
    }

    @Test
    void hardOverlap_suggestsAlternatives() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 3000L));
        dao.insert(new ScheduledEvent(null, courseC, locId1, EventType.EXAM, 2000L, 4000L));

        List<ConflictResult> conflicts = engine.analyzeAll();
        ConflictResult overlap = conflicts.stream()
                .filter(c -> c.getSeverity() == Severity.HARD_OVERLAP)
                .findFirst().orElse(null);
        assertNotNull(overlap);
        assertNotNull(overlap.getAvailableAlternatives());
        // Building B 201 and Building A 102 should be available
        assertTrue(overlap.getAvailableAlternatives().size() >= 1);
    }

    @Test
    void noOverlap_differentRooms() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 3000L));
        dao.insert(new ScheduledEvent(null, courseC, locId2, EventType.LECTURE, 1000L, 3000L));

        List<ConflictResult> conflicts = engine.analyzeAll();
        // No hard overlap (different rooms), no tight transition (different professors)
        boolean hasHardOverlap = conflicts.stream().anyMatch(c -> c.getSeverity() == Severity.HARD_OVERLAP);
        assertFalse(hasHardOverlap);
    }

    @Test
    void tightTransition_sameProfDifferentBuildings() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        // Prof 1 teaches CS101 in Building A, then CS201 in Building B 5 min later
        long baseTime = 100_000_000L;
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE,
                baseTime, baseTime + 60 * 60_000L)); // 1 hour lecture
        dao.insert(new ScheduledEvent(null, courseB, locId2, EventType.LECTURE,
                baseTime + 60 * 60_000L + 5 * 60_000L,    // 5 min gap
                baseTime + 120 * 60_000L + 5 * 60_000L));

        List<ConflictResult> conflicts = engine.analyzeAll();
        assertTrue(conflicts.stream().anyMatch(c -> c.getSeverity() == Severity.TIGHT_TRANSITION));
    }

        @Test
        void tightTransition_suggestsSameBuildingAlternatives() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        long baseTime = 150_000_000L;
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE,
            baseTime, baseTime + 60 * 60_000L));
        dao.insert(new ScheduledEvent(null, courseB, locId2, EventType.LECTURE,
            baseTime + 60 * 60_000L + 5 * 60_000L,
            baseTime + 120 * 60_000L + 5 * 60_000L));

        List<ConflictResult> conflicts = engine.analyzeAll();
        ConflictResult tight = conflicts.stream()
            .filter(c -> c.getSeverity() == Severity.TIGHT_TRANSITION)
            .findFirst()
            .orElse(null);

        assertNotNull(tight);
        assertNotNull(tight.getAvailableAlternatives());
        assertFalse(tight.getAvailableAlternatives().isEmpty());
        assertTrue(tight.getAvailableAlternatives().stream()
            .allMatch(l -> "Building A".equals(l.getBuilding())));
        }

    @Test
    void noTightTransition_enoughGap() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        long baseTime = 100_000_000L;
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE,
                baseTime, baseTime + 60 * 60_000L));
        dao.insert(new ScheduledEvent(null, courseB, locId2, EventType.LECTURE,
                baseTime + 60 * 60_000L + 30 * 60_000L,    // 30 min gap
                baseTime + 120 * 60_000L + 30 * 60_000L));

        List<ConflictResult> conflicts = engine.analyzeAll();
        boolean hasTight = conflicts.stream().anyMatch(c -> c.getSeverity() == Severity.TIGHT_TRANSITION);
        assertFalse(hasTight);
    }

    @Test
    void noTightTransition_sameBuilding() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        // Same building – no transition issue even with short gap
        LocationDAO locDAO = new LocationDAO(dbManager);
        // locId1 is Building A 101, let's use Building A 102
        List<Location> allLocs = locDAO.findAll();
        Integer locSameBuilding = allLocs.stream()
                .filter(l -> "Building A".equals(l.getBuilding()) && !"101".equals(l.getRoomNumber()))
                .map(Location::getLocId).findFirst().orElse(null);
        assertNotNull(locSameBuilding);

        long baseTime = 200_000_000L;
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE,
                baseTime, baseTime + 60 * 60_000L));
        dao.insert(new ScheduledEvent(null, courseB, locSameBuilding, EventType.LECTURE,
                baseTime + 60 * 60_000L + 5 * 60_000L,
                baseTime + 120 * 60_000L));

        List<ConflictResult> conflicts = engine.analyzeAll();
        boolean hasTight = conflicts.stream().anyMatch(c -> c.getSeverity() == Severity.TIGHT_TRANSITION);
        assertFalse(hasTight);
    }

    @Test
    void onlineEvent_noRoomConflict() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        // Online event (null locId) should not cause room conflicts
        dao.insert(new ScheduledEvent(null, courseA, null, EventType.OFFICE_HOURS, 1000L, 3000L));
        dao.insert(new ScheduledEvent(null, courseB, null, EventType.OFFICE_HOURS, 1000L, 3000L));

        List<ConflictResult> conflicts = engine.analyzeAll();
        boolean hasHardOverlap = conflicts.stream().anyMatch(c -> c.getSeverity() == Severity.HARD_OVERLAP);
        assertFalse(hasHardOverlap);
    }

    @Test
    void multipleOverlaps_sameRoom() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        dao.insert(new ScheduledEvent(null, courseA, locId1, EventType.LECTURE, 1000L, 5000L));
        dao.insert(new ScheduledEvent(null, courseB, locId1, EventType.LECTURE, 2000L, 4000L));
        dao.insert(new ScheduledEvent(null, courseC, locId1, EventType.EXAM, 3000L, 6000L));

        List<ConflictResult> conflicts = engine.analyzeAll();
        long hardCount = conflicts.stream().filter(c -> c.getSeverity() == Severity.HARD_OVERLAP).count();
        assertTrue(hardCount >= 2); // At least 2 pairs overlap
    }
}
