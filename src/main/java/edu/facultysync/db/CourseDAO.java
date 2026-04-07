package edu.facultysync.db;

import edu.facultysync.model.Course;

import java.sql.Connection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for CRUD operations on courses.
 */
public class CourseDAO {
    private final DatabaseManager dbManager;

    public CourseDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Course insert(Course course) throws SQLException {
        Integer newId = null;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn
                .prepareStatement(SqlQueries.Course.INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, course.getCourseCode());
            ps.setInt(2, course.getProfId());
            if (course.getEnrollmentCount() != null) ps.setInt(3, course.getEnrollmentCount());
            else ps.setNull(3, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    newId = keys.getInt(1);
                }
            }
        }
        return newId != null ? course.withCourseId(newId) : course;
    }

    public Course findById(int id) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Course.SELECT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public Course findByCode(String code) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Course.SELECT_BY_CODE)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public List<Course> findAll() throws SQLException {
        List<Course> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SqlQueries.Course.SELECT_ALL)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<Course> findByProfessor(int profId) throws SQLException {
        List<Course> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Course.SELECT_BY_PROFESSOR)) {
            ps.setInt(1, profId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public void update(Course course) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Course.UPDATE)) {
            ps.setString(1, course.getCourseCode());
            ps.setInt(2, course.getProfId());
            if (course.getEnrollmentCount() != null) ps.setInt(3, course.getEnrollmentCount());
            else ps.setNull(3, Types.INTEGER);
            ps.setInt(4, course.getCourseId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Course.DELETE)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Course map(ResultSet rs) throws SQLException {
        Integer courseId = rs.getInt("course_id");
        String courseCode = rs.getString("course_code");
        Integer profId = rs.getInt("prof_id");
        int enroll = rs.getInt("enrollment_count");
        Integer enrollment = rs.wasNull() ? null : enroll;
        return new Course(courseId, courseCode, profId, enrollment);
    }
}
