package edu.facultysync.db;

import edu.facultysync.model.Professor;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProfessorDAO {
    private final DatabaseManager dbManager;

    public ProfessorDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Professor insert(Professor prof) throws SQLException {
        String sql = "INSERT INTO professors (name, dept_id) VALUES (?, ?)";
        try (PreparedStatement ps = dbManager.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, prof.getName());
            ps.setInt(2, prof.getDeptId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) prof.setProfId(keys.getInt(1));
            }
        }
        return prof;
    }

    public Professor findById(int id) throws SQLException {
        String sql = "SELECT prof_id, name, dept_id FROM professors WHERE prof_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public List<Professor> findAll() throws SQLException {
        List<Professor> list = new ArrayList<>();
        String sql = "SELECT prof_id, name, dept_id FROM professors ORDER BY name";
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<Professor> findByDepartment(int deptId) throws SQLException {
        List<Professor> list = new ArrayList<>();
        String sql = "SELECT prof_id, name, dept_id FROM professors WHERE dept_id = ? ORDER BY name";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, deptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public void update(Professor prof) throws SQLException {
        String sql = "UPDATE professors SET name = ?, dept_id = ? WHERE prof_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, prof.getName());
            ps.setInt(2, prof.getDeptId());
            ps.setInt(3, prof.getProfId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM professors WHERE prof_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Professor map(ResultSet rs) throws SQLException {
        return new Professor(rs.getInt("prof_id"), rs.getString("name"), rs.getInt("dept_id"));
    }
}
