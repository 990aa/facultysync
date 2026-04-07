package edu.facultysync.db;

import edu.facultysync.model.Location;

import java.sql.Connection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for CRUD operations on locations.
 */
public class LocationDAO {
    private final DatabaseManager dbManager;

    public LocationDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Location insert(Location loc) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn
                .prepareStatement(SqlQueries.Location.INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, loc.getBuilding());
            ps.setString(2, loc.getRoomNumber());
            if (loc.getCapacity() != null) ps.setInt(3, loc.getCapacity());
            else ps.setNull(3, Types.INTEGER);
            if (loc.getHasProjector() != null) ps.setInt(4, loc.getHasProjector());
            else ps.setNull(4, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) loc.setLocId(keys.getInt(1));
            }
        }
        return loc;
    }

    public Location findById(int id) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Location.SELECT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public List<Location> findAll() throws SQLException {
        List<Location> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SqlQueries.Location.SELECT_ALL)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /**
     * Finds rooms that are available during a given time range and meet capacity requirements.
     * Excludes rooms that are already booked during [startEpoch, endEpoch).
     */
    public List<Location> findAvailable(long startEpoch, long endEpoch, int minCapacity) throws SQLException {
        List<Location> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Location.FIND_AVAILABLE)) {
            ps.setInt(1, minCapacity);
            ps.setLong(2, endEpoch);
            ps.setLong(3, startEpoch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public void update(Location loc) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Location.UPDATE)) {
            ps.setString(1, loc.getBuilding());
            ps.setString(2, loc.getRoomNumber());
            if (loc.getCapacity() != null) ps.setInt(3, loc.getCapacity());
            else ps.setNull(3, Types.INTEGER);
            if (loc.getHasProjector() != null) ps.setInt(4, loc.getHasProjector());
            else ps.setNull(4, Types.INTEGER);
            ps.setInt(5, loc.getLocId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.Location.DELETE)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Location map(ResultSet rs) throws SQLException {
        Location l = new Location();
        l.setLocId(rs.getInt("loc_id"));
        l.setBuilding(rs.getString("building"));
        l.setRoomNumber(rs.getString("room_number"));
        int cap = rs.getInt("capacity");
        l.setCapacity(rs.wasNull() ? null : cap);
        int proj = rs.getInt("has_projector");
        l.setHasProjector(rs.wasNull() ? null : proj);
        return l;
    }
}
