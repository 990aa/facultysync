package edu.facultysync.db;

import edu.facultysync.model.Department;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DepartmentDAO {
    private final DatabaseManager dbManager;

    public DepartmentDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Department insert(Department dept) throws SQLException {
        String sql = "INSERT INTO departments (name) VALUES (?)";
        try (PreparedStatement ps = dbManager.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, dept.getName());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) dept.setDeptId(keys.getInt(1));
            }
        }
        return dept;
    }

    public Department findById(int id) throws SQLException {
        String sql = "SELECT dept_id, name FROM departments WHERE dept_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public List<Department> findAll() throws SQLException {
        List<Department> list = new ArrayList<>();
        String sql = "SELECT dept_id, name FROM departments ORDER BY name";
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public void update(Department dept) throws SQLException {
        String sql = "UPDATE departments SET name = ? WHERE dept_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, dept.getName());
            ps.setInt(2, dept.getDeptId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM departments WHERE dept_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Department map(ResultSet rs) throws SQLException {
        return new Department(rs.getInt("dept_id"), rs.getString("name"));
    }
}
