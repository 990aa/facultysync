package edu.facultysync.db;

import edu.facultysync.model.*;
import edu.facultysync.model.ScheduledEvent.EventType;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full integration tests for DatabaseManager and all DAO classes.
 * Uses in-memory SQLite for speed and isolation.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseTest {

    private static DatabaseManager dbManager;

    @BeforeAll
    static void setup() throws SQLException {
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        dbManager.initializeSchema();
    }

    @AfterAll
    static void teardown() {
        dbManager.close();
    }

    // ===== DatabaseManager =====

    @Test
    @Order(1)
    void testConnection() throws SQLException {
        Connection conn = dbManager.getConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
    }

    @Test
    @Order(2)
    void testForeignKeysEnabled() throws SQLException {
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    @Order(3)
    void testWalMode() throws SQLException {
        // In-memory SQLite returns 'memory' for journal_mode; on disk it returns 'wal'.
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
            assertTrue(rs.next());
            String mode = rs.getString(1).toLowerCase();
            assertTrue(mode.equals("wal") || mode.equals("memory"),
                    "Expected 'wal' or 'memory' but got '" + mode + "'");
        }
    }

    @Test
    @Order(4)
    void testSchemaIdempotent() {
        // Should not throw when called again
        assertDoesNotThrow(() -> dbManager.initializeSchema());
    }

    // ===== DepartmentDAO =====

    @Test
    @Order(10)
    void testInsertDepartment() throws SQLException {
        DepartmentDAO dao = new DepartmentDAO(dbManager);
        Department d = dao.insert(new Department(null, "Computer Science"));
        assertNotNull(d.getDeptId());
        assertTrue(d.getDeptId() > 0);
    }

    @Test
    @Order(11)
    void testFindDepartmentById() throws SQLException {
        DepartmentDAO dao = new DepartmentDAO(dbManager);
        Department d = dao.findById(1);
        assertNotNull(d);
        assertEquals("Computer Science", d.getName());
    }

    @Test
    @Order(12)
    void testFindDepartmentById_notFound() throws SQLException {
        assertNull(new DepartmentDAO(dbManager).findById(9999));
    }

    @Test
    @Order(13)
    void testFindAllDepartments() throws SQLException {
        DepartmentDAO dao = new DepartmentDAO(dbManager);
        dao.insert(new Department(null, "Mathematics"));
        dao.insert(new Department(null, "Physics"));
        List<Department> all = dao.findAll();
        assertTrue(all.size() >= 3);
    }

    @Test
    @Order(14)
    void testUpdateDepartment() throws SQLException {
        DepartmentDAO dao = new DepartmentDAO(dbManager);
        Department d = dao.findById(1);
        d.setName("CS Department");
        dao.update(d);
        assertEquals("CS Department", dao.findById(1).getName());
        // restore
        d.setName("Computer Science");
        dao.update(d);
    }

    @Test
    @Order(15)
    void testDuplicateDepartmentName() {
        DepartmentDAO dao = new DepartmentDAO(dbManager);
        assertThrows(SQLException.class, () -> dao.insert(new Department(null, "Computer Science")));
    }

    // ===== ProfessorDAO =====

    @Test
    @Order(20)
    void testInsertProfessor() throws SQLException {
        ProfessorDAO dao = new ProfessorDAO(dbManager);
        Professor p = dao.insert(new Professor(null, "Dr. Smith", 1));
        assertNotNull(p.getProfId());
        assertTrue(p.getProfId() > 0);
    }

    @Test
    @Order(21)
    void testInsertProfessor_invalidDept() {
        ProfessorDAO dao = new ProfessorDAO(dbManager);
        assertThrows(SQLException.class, () -> dao.insert(new Professor(null, "Dr. X", 9999)));
    }

    @Test
    @Order(22)
    void testFindProfessorById() throws SQLException {
        ProfessorDAO dao = new ProfessorDAO(dbManager);
        Professor p = dao.findById(1);
        assertNotNull(p);
        assertEquals("Dr. Smith", p.getName());
    }

    @Test
    @Order(23)
    void testFindProfessorsByDepartment() throws SQLException {
        ProfessorDAO dao = new ProfessorDAO(dbManager);
        dao.insert(new Professor(null, "Dr. Jones", 1));
        List<Professor> profs = dao.findByDepartment(1);
        assertTrue(profs.size() >= 2);
    }

    @Test
    @Order(24)
    void testFindAllProfessors() throws SQLException {
        List<Professor> all = new ProfessorDAO(dbManager).findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    @Order(25)
    void testUpdateProfessor() throws SQLException {
        ProfessorDAO dao = new ProfessorDAO(dbManager);
        Professor p = dao.findById(1);
        p.setName("Dr. Smith Jr.");
        dao.update(p);
        assertEquals("Dr. Smith Jr.", dao.findById(1).getName());
        p.setName("Dr. Smith");
        dao.update(p);
    }

    // ===== CourseDAO =====

    @Test
    @Order(30)
    void testInsertCourse() throws SQLException {
        CourseDAO dao = new CourseDAO(dbManager);
        Course c = dao.insert(new Course(null, "CS101", 1, 35));
        assertNotNull(c.getCourseId());
        assertTrue(c.getCourseId() > 0);
    }

    @Test
    @Order(31)
    void testInsertCourse_nullEnrollment() throws SQLException {
        CourseDAO dao = new CourseDAO(dbManager);
        Course c = dao.insert(new Course(null, "CS102", 1, null));
        assertNotNull(c.getCourseId());
        Course fetched = dao.findById(c.getCourseId());
        assertNull(fetched.getEnrollmentCount());
    }

    @Test
    @Order(32)
    void testInsertCourse_invalidProfessor() {
        CourseDAO dao = new CourseDAO(dbManager);
        assertThrows(SQLException.class, () -> dao.insert(new Course(null, "INVALID", 9999, 10)));
    }

    @Test
    @Order(33)
    void testFindCourseByCode() throws SQLException {
        Course c = new CourseDAO(dbManager).findByCode("CS101");
        assertNotNull(c);
        assertEquals("CS101", c.getCourseCode());
    }

    @Test
    @Order(34)
    void testFindCourseByCode_notFound() throws SQLException {
        assertNull(new CourseDAO(dbManager).findByCode("NONEXISTENT"));
    }

    @Test
    @Order(35)
    void testFindCoursesByProfessor() throws SQLException {
        List<Course> courses = new CourseDAO(dbManager).findByProfessor(1);
        assertTrue(courses.size() >= 2);
    }

    @Test
    @Order(36)
    void testUpdateCourse() throws SQLException {
        CourseDAO dao = new CourseDAO(dbManager);
        Course c = dao.findByCode("CS101");
        c.setEnrollmentCount(40);
        dao.update(c);
        assertEquals(40, dao.findById(c.getCourseId()).getEnrollmentCount());
    }

    @Test
    @Order(37)
    void testDuplicateCourseCode() {
        CourseDAO dao = new CourseDAO(dbManager);
        assertThrows(SQLException.class, () -> dao.insert(new Course(null, "CS101", 1, 10)));
    }

    // ===== LocationDAO =====

    @Test
    @Order(40)
    void testInsertLocation() throws SQLException {
        LocationDAO dao = new LocationDAO(dbManager);
        Location l = dao.insert(new Location(null, "Science Hall", "101", 50, 1));
        assertNotNull(l.getLocId());
        assertTrue(l.getLocId() > 0);
    }

    @Test
    @Order(41)
    void testInsertLocation_nullCapacity() throws SQLException {
        LocationDAO dao = new LocationDAO(dbManager);
        Location l = dao.insert(new Location(null, "Online Wing", "Virtual", null, null));
        Location fetched = dao.findById(l.getLocId());
        assertNull(fetched.getCapacity());
        assertNull(fetched.getHasProjector());
    }

    @Test
    @Order(42)
    void testDuplicateLocation() {
        LocationDAO dao = new LocationDAO(dbManager);
        assertThrows(SQLException.class, () ->
            dao.insert(new Location(null, "Science Hall", "101", 50, 1)));
    }

    @Test
    @Order(43)
    void testFindAllLocations() throws SQLException {
        LocationDAO dao = new LocationDAO(dbManager);
        dao.insert(new Location(null, "Engineering", "201", 30, 0));
        dao.insert(new Location(null, "Arts", "305", 25, 1));
        List<Location> all = dao.findAll();
        assertTrue(all.size() >= 3);
    }

    @Test
    @Order(44)
    void testUpdateLocation() throws SQLException {
        LocationDAO dao = new LocationDAO(dbManager);
        Location l = dao.findById(1);
        l.setCapacity(60);
        dao.update(l);
        assertEquals(60, dao.findById(1).getCapacity());
    }

    // ===== ScheduledEventDAO =====

    @Test
    @Order(50)
    void testInsertEvent() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        Course c = new CourseDAO(dbManager).findByCode("CS101");
        ScheduledEvent e = dao.insert(new ScheduledEvent(null, c.getCourseId(), 1,
                EventType.LECTURE, 1000000L, 2000000L));
        assertNotNull(e.getEventId());
        assertTrue(e.getEventId() > 0);
    }

    @Test
    @Order(51)
    void testInsertEvent_nullLocation() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        Course c = new CourseDAO(dbManager).findByCode("CS101");
        ScheduledEvent e = dao.insert(new ScheduledEvent(null, c.getCourseId(), null,
                EventType.OFFICE_HOURS, 3000000L, 4000000L));
        assertNotNull(e.getEventId());
        ScheduledEvent fetched = dao.findById(e.getEventId());
        assertNull(fetched.getLocId());
    }

    @Test
    @Order(52)
    void testInsertEvent_invalidEndBeforeStart() {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        assertThrows(SQLException.class, () ->
            dao.insert(new ScheduledEvent(null, 1, 1, EventType.EXAM, 5000000L, 4000000L)));
    }

    @Test
    @Order(53)
    void testInsertEvent_invalidCourse() {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        assertThrows(SQLException.class, () ->
            dao.insert(new ScheduledEvent(null, 9999, 1, EventType.LECTURE, 1000L, 2000L)));
    }

    @Test
    @Order(54)
    void testFindEventById() throws SQLException {
        ScheduledEvent e = new ScheduledEventDAO(dbManager).findById(1);
        assertNotNull(e);
        assertEquals(EventType.LECTURE, e.getEventType());
    }

    @Test
    @Order(55)
    void testFindEventsByTimeRange() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        List<ScheduledEvent> events = dao.findByTimeRange(500000L, 1500000L);
        assertFalse(events.isEmpty());
    }

    @Test
    @Order(56)
    void testFindEventsByTimeRange_noResult() throws SQLException {
        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findByTimeRange(0L, 100L);
        assertTrue(events.isEmpty());
    }

    @Test
    @Order(57)
    void testFindEventsByLocation() throws SQLException {
        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findByLocation(1);
        assertFalse(events.isEmpty());
    }

    @Test
    @Order(58)
    void testFindEventsByCourse() throws SQLException {
        Course c = new CourseDAO(dbManager).findByCode("CS101");
        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findByCourse(c.getCourseId());
        assertFalse(events.isEmpty());
    }

    @Test
    @Order(59)
    void testUpdateEvent() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        ScheduledEvent e = dao.findById(1);
        e.setEventType(EventType.EXAM);
        dao.update(e);
        assertEquals(EventType.EXAM, dao.findById(1).getEventType());
    }

    @Test
    @Order(60)
    void testFindOverlapping() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        // Add a second event at same location overlapping
        Course c = new CourseDAO(dbManager).findByCode("CS102");
        dao.insert(new ScheduledEvent(null, c.getCourseId(), 1,
                EventType.EXAM, 1500000L, 2500000L));
        List<ScheduledEvent> overlaps = dao.findOverlapping(1, 1500000L, 2500000L);
        assertTrue(overlaps.size() >= 1);
    }

    @Test
    @Order(61)
    void testFindAvailableLocations() throws SQLException {
        LocationDAO locDao = new LocationDAO(dbManager);
        // Arts 305 should be available during time when Science Hall 101 is booked
        List<Location> available = locDao.findAvailable(1000000L, 2000000L, 20);
        assertNotNull(available);
        // Should include rooms not booked at that time
    }

    // ===== Delete Operations =====

    @Test
    @Order(90)
    void testDeleteEvent() throws SQLException {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        // Insert then delete
        Course c = new CourseDAO(dbManager).findByCode("CS101");
        ScheduledEvent e = dao.insert(new ScheduledEvent(null, c.getCourseId(), null,
                EventType.LECTURE, 9000000L, 9100000L));
        int id = e.getEventId();
        dao.delete(id);
        assertNull(dao.findById(id));
    }

    @Test
    @Order(91)
    void testDeleteLocation() throws SQLException {
        LocationDAO dao = new LocationDAO(dbManager);
        Location l = dao.insert(new Location(null, "Temp", "000", 10, 0));
        int id = l.getLocId();
        dao.delete(id);
        assertNull(dao.findById(id));
    }

    @Test
    @Order(92)
    void testDeleteCourse() throws SQLException {
        CourseDAO cDao = new CourseDAO(dbManager);
        ProfessorDAO pDao = new ProfessorDAO(dbManager);
        Professor p = pDao.insert(new Professor(null, "Temp Prof", 1));
        Course c = cDao.insert(new Course(null, "TEMP999", p.getProfId(), 5));
        int id = c.getCourseId();
        cDao.delete(id);
        assertNull(cDao.findById(id));
    }

    @Test
    @Order(93)
    void testDeleteProfessor() throws SQLException {
        ProfessorDAO dao = new ProfessorDAO(dbManager);
        Professor p = dao.insert(new Professor(null, "ToDelete", 1));
        int id = p.getProfId();
        dao.delete(id);
        assertNull(dao.findById(id));
    }

    @Test
    @Order(94)
    void testDeleteDepartment() throws SQLException {
        DepartmentDAO dDao = new DepartmentDAO(dbManager);
        Department d = dDao.insert(new Department(null, "TempDept"));
        int id = d.getDeptId();
        dDao.delete(id);
        assertNull(dDao.findById(id));
    }

    // ===== Schema Integrity =====

    @Test
    @Order(95)
    void testEndEpochConstraint() {
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        // end == start should fail (CHECK end_epoch > start_epoch)
        assertThrows(SQLException.class, () ->
            dao.insert(new ScheduledEvent(null, 1, 1, EventType.LECTURE, 5000L, 5000L)));
    }

    @Test
    @Order(96)
    void testEventTypeConstraint() {
        // Invalid event type
        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
        ScheduledEvent e = new ScheduledEvent();
        e.setCourseId(1);
        e.setLocId(1);
        e.setStartEpoch(100L);
        e.setEndEpoch(200L);
        // eventType is null – cannot insert with null type due to NOT NULL constraint
        assertThrows(Exception.class, () -> dao.insert(e));
    }
}
