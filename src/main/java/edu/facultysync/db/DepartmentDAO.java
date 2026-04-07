package edu.facultysync.db;

import edu.facultysync.model.Department;

import java.sql.Connection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for CRUD operations on departments.
 */
public class DepartmentDAO {
    private final DatabaseManager dbManager;

    public DepartmentDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Department insert(Department dept) throws SQLException {
        Integer newId = null;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn
                .prepareStatement(SqlQueries.Department.INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, dept.getName());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    newId = keys.getInt(1);
                }
            }
        }
        return newId != null ? dept.withDeptId(newId) : dept;
    }

    public Department findById(int id) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Department.SELECT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public List<Department> findAll() throws SQLException {
        List<Department> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SqlQueries.Department.SELECT_ALL)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public void update(Department dept) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Department.UPDATE)) {
            ps.setString(1, dept.getName());
            ps.setInt(2, dept.getDeptId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Department.DELETE)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Department map(ResultSet rs) throws SQLException {
        return new Department(rs.getInt("dept_id"), rs.getString("name"));
    }
}
