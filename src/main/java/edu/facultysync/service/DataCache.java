package edu.facultysync.service;

import edu.facultysync.db.*;
import edu.facultysync.model.*;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * In-memory cache for reference data (Locations, Courses, Professors, Departments).
 * Avoids hundreds of redundant SELECTs during UI rendering.
 */
public class DataCache {

    private final DatabaseManager dbManager;
    private volatile Map<Integer, Location> locationCache = Map.of();
    private volatile Map<Integer, Course> courseCache = Map.of();
    private volatile Map<Integer, Professor> professorCache = Map.of();
    private volatile Map<Integer, Department> departmentCache = Map.of();

    public DataCache(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /** Reload all reference data from DB. */
    public void refresh() throws SQLException {
        Map<Integer, Location> newLocationCache = new HashMap<>();
        Map<Integer, Course> newCourseCache = new HashMap<>();
        Map<Integer, Professor> newProfessorCache = new HashMap<>();
        Map<Integer, Department> newDepartmentCache = new HashMap<>();

        for (Location l : new LocationDAO(dbManager).findAll()) {
            newLocationCache.put(l.getLocId(), l);
        }
        for (Course c : new CourseDAO(dbManager).findAll()) {
            newCourseCache.put(c.getCourseId(), c);
        }
        for (Professor p : new ProfessorDAO(dbManager).findAll()) {
            newProfessorCache.put(p.getProfId(), p);
        }
        for (Department d : new DepartmentDAO(dbManager).findAll()) {
            newDepartmentCache.put(d.getDeptId(), d);
        }

        // Swap full snapshots at once so readers never see partially populated state.
        locationCache = Collections.unmodifiableMap(newLocationCache);
        courseCache = Collections.unmodifiableMap(newCourseCache);
        professorCache = Collections.unmodifiableMap(newProfessorCache);
        departmentCache = Collections.unmodifiableMap(newDepartmentCache);
    }

    public Location getLocation(Integer id) { return id != null ? locationCache.get(id) : null; }
    public Course getCourse(Integer id) { return id != null ? courseCache.get(id) : null; }
    public Professor getProfessor(Integer id) { return id != null ? professorCache.get(id) : null; }
    public Department getDepartment(Integer id) { return id != null ? departmentCache.get(id) : null; }

    public Map<Integer, Location> getAllLocations() { return locationCache; }
    public Map<Integer, Course> getAllCourses() { return courseCache; }
    public Map<Integer, Professor> getAllProfessors() { return professorCache; }
    public Map<Integer, Department> getAllDepartments() { return departmentCache; }

    /**
     * Enriches a ScheduledEvent with cached display names.
     */
    public void enrich(ScheduledEvent event) {
        Course c = getCourse(event.getCourseId());
        if (c != null) {
            event.setCourseCode(c.getCourseCode());
            Professor p = getProfessor(c.getProfId());
            if (p != null) event.setProfessorName(p.getName());
        }
        Location l = getLocation(event.getLocId());
        if (l != null) event.setLocationName(l.getDisplayName());
    }

    /**
     * Enriches a list of events.
     */
    public void enrichAll(List<ScheduledEvent> events) {
        for (ScheduledEvent e : events) enrich(e);
    }
}
