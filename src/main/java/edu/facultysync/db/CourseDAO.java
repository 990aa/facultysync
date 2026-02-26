package edu.facultysync.db;

import edu.facultysync.model.Course;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourseDAO {
    private final DatabaseManager dbManager;

    public CourseDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Course insert(Course course) throws SQLException {
        String sql = "INSERT INTO courses (course_code, prof_id, enrollment_count) VALUES (?, ?, ?)";
        try (PreparedStatement ps = dbManager.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, course.getCourseCode());
            ps.setInt(2, course.getProfId());
            if (course.getEnrollmentCount() != null) ps.setInt(3, course.getEnrollmentCount());
            else ps.setNull(3, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) course.setCourseId(keys.getInt(1));
            }
        }
        return course;
    }

    public Course findById(int id) throws SQLException {
        String sql = "SELECT course_id, course_code, prof_id, enrollment_count FROM courses WHERE course_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public Course findByCode(String code) throws SQLException {
        String sql = "SELECT course_id, course_code, prof_id, enrollment_count FROM courses WHERE course_code = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public List<Course> findAll() throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT course_id, course_code, prof_id, enrollment_count FROM courses ORDER BY course_code";
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<Course> findByProfessor(int profId) throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT course_id, course_code, prof_id, enrollment_count FROM courses WHERE prof_id = ? ORDER BY course_code";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, profId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public void update(Course course) throws SQLException {
        String sql = "UPDATE courses SET course_code = ?, prof_id = ?, enrollment_count = ? WHERE course_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, course.getCourseCode());
            ps.setInt(2, course.getProfId());
            if (course.getEnrollmentCount() != null) ps.setInt(3, course.getEnrollmentCount());
            else ps.setNull(3, Types.INTEGER);
            ps.setInt(4, course.getCourseId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM courses WHERE course_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Course map(ResultSet rs) throws SQLException {
        Course c = new Course();
        c.setCourseId(rs.getInt("course_id"));
        c.setCourseCode(rs.getString("course_code"));
        c.setProfId(rs.getInt("prof_id"));
        int enroll = rs.getInt("enrollment_count");
        c.setEnrollmentCount(rs.wasNull() ? null : enroll);
        return c;
    }
}
