package edu.facultysync.service;

import edu.facultysync.db.*;
import edu.facultysync.model.*;
import edu.facultysync.model.ScheduledEvent.EventType;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DataCache – in-memory caching of reference data.
 */
class DataCacheTest {

    private static DatabaseManager dbManager;
    private static DataCache cache;

    @BeforeAll
    static void setup() throws SQLException {
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        dbManager.initializeSchema();

        // Seed data
        DepartmentDAO deptDAO = new DepartmentDAO(dbManager);
        Department dept = deptDAO.insert(new Department(null, "Computer Science"));

        ProfessorDAO profDAO = new ProfessorDAO(dbManager);
        Professor prof = profDAO.insert(new Professor(null, "Dr. Smith", dept.getDeptId()));

        CourseDAO courseDAO = new CourseDAO(dbManager);
        courseDAO.insert(new Course(null, "CS101", prof.getProfId(), 30));

        LocationDAO locDAO = new LocationDAO(dbManager);
        locDAO.insert(new Location(null, "Science Hall", "101", 50, 1));

        cache = new DataCache(dbManager);
        cache.refresh();
    }

    @AfterAll
    static void teardown() { dbManager.close(); }

    @Test
    void testCacheLoadsAllDepartments() {
        assertFalse(cache.getAllDepartments().isEmpty());
    }

    @Test
    void testCacheLoadsAllProfessors() {
        assertFalse(cache.getAllProfessors().isEmpty());
    }

    @Test
    void testCacheLoadsAllCourses() {
        assertFalse(cache.getAllCourses().isEmpty());
    }

    @Test
    void testCacheLoadsAllLocations() {
        assertFalse(cache.getAllLocations().isEmpty());
    }

    @Test
    void testGetLocation() {
        Map.Entry<Integer, Location> entry = cache.getAllLocations().entrySet().iterator().next();
        Location loc = cache.getLocation(entry.getKey());
        assertNotNull(loc);
        assertEquals("Science Hall", loc.getBuilding());
    }

    @Test
    void testGetLocationNull() {
        assertNull(cache.getLocation(null));
        assertNull(cache.getLocation(9999));
    }

    @Test
    void testGetCourse() {
        Map.Entry<Integer, Course> entry = cache.getAllCourses().entrySet().iterator().next();
        Course c = cache.getCourse(entry.getKey());
        assertNotNull(c);
        assertEquals("CS101", c.getCourseCode());
    }

    @Test
    void testGetCourseNull() {
        assertNull(cache.getCourse(null));
    }

    @Test
    void testGetProfessor() {
        Map.Entry<Integer, Professor> entry = cache.getAllProfessors().entrySet().iterator().next();
        Professor p = cache.getProfessor(entry.getKey());
        assertNotNull(p);
        assertEquals("Dr. Smith", p.getName());
    }

    @Test
    void testGetDepartment() {
        Map.Entry<Integer, Department> entry = cache.getAllDepartments().entrySet().iterator().next();
        Department d = cache.getDepartment(entry.getKey());
        assertNotNull(d);
        assertEquals("Computer Science", d.getName());
    }

    @Test
    void testEnrichEvent() {
        Map.Entry<Integer, Course> cEntry = cache.getAllCourses().entrySet().iterator().next();
        Map.Entry<Integer, Location> lEntry = cache.getAllLocations().entrySet().iterator().next();

        ScheduledEvent event = new ScheduledEvent(1, cEntry.getKey(), lEntry.getKey(),
                EventType.LECTURE, 1000L, 2000L);
        cache.enrich(event);

        assertEquals("CS101", event.getCourseCode());
        assertEquals("Dr. Smith", event.getProfessorName());
        assertEquals("Science Hall 101", event.getLocationName());
    }

    @Test
    void testEnrichEvent_nullIds() {
        ScheduledEvent event = new ScheduledEvent(1, null, null, EventType.LECTURE, 1000L, 2000L);
        // Should not throw
        assertDoesNotThrow(() -> cache.enrich(event));
        assertNull(event.getCourseCode());
        assertNull(event.getLocationName());
    }

    @Test
    void testEnrichAll() throws SQLException {
        Map.Entry<Integer, Course> cEntry = cache.getAllCourses().entrySet().iterator().next();
        ScheduledEvent e1 = new ScheduledEvent(1, cEntry.getKey(), null, EventType.EXAM, 1000L, 2000L);
        ScheduledEvent e2 = new ScheduledEvent(2, cEntry.getKey(), null, EventType.LECTURE, 3000L, 4000L);
        cache.enrichAll(List.of(e1, e2));
        assertNotNull(e1.getCourseCode());
        assertNotNull(e2.getCourseCode());
    }

    @Test
    void testRefresh() throws SQLException {
        // Add a new department, refresh, verify it's in cache
        DepartmentDAO dao = new DepartmentDAO(dbManager);
        dao.insert(new Department(null, "Mathematics"));
        cache.refresh();
        boolean found = cache.getAllDepartments().values().stream()
                .anyMatch(d -> "Mathematics".equals(d.getName()));
        assertTrue(found);
    }
}
