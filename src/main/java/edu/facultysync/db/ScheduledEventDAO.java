package edu.facultysync.db;

import edu.facultysync.model.ScheduledEvent;
import edu.facultysync.model.ScheduledEvent.EventType;

import java.sql.Connection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for CRUD operations on scheduled events.
 */
public class ScheduledEventDAO {
    private final DatabaseManager dbManager;

    public ScheduledEventDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public ScheduledEvent insert(ScheduledEvent event) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn
                .prepareStatement(SqlQueries.ScheduledEvent.INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, event.getCourseId());
            if (event.getLocId() != null) ps.setInt(2, event.getLocId());
            else ps.setNull(2, Types.INTEGER);
            ps.setString(3, event.getEventType().getDisplay());
            ps.setLong(4, event.getStartEpoch());
            ps.setLong(5, event.getEndEpoch());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) event.setEventId(keys.getInt(1));
            }
        }
        return event;
    }

    public ScheduledEvent findById(int id) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.ScheduledEvent.SELECT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public List<ScheduledEvent> findAll() throws SQLException {
        List<ScheduledEvent> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SqlQueries.ScheduledEvent.SELECT_ALL)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<ScheduledEvent> findByTimeRange(long startEpoch, long endEpoch) throws SQLException {
        List<ScheduledEvent> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.ScheduledEvent.SELECT_BY_TIME_RANGE)) {
            ps.setLong(1, endEpoch);
            ps.setLong(2, startEpoch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public List<ScheduledEvent> findByLocation(int locId) throws SQLException {
        List<ScheduledEvent> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.ScheduledEvent.SELECT_BY_LOCATION)) {
            ps.setInt(1, locId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public List<ScheduledEvent> findByCourse(int courseId) throws SQLException {
        List<ScheduledEvent> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.ScheduledEvent.SELECT_BY_COURSE)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Find overlapping events for a specific location during a time range.
     */
    public List<ScheduledEvent> findOverlapping(int locId, long startEpoch, long endEpoch) throws SQLException {
        List<ScheduledEvent> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.ScheduledEvent.SELECT_OVERLAPPING)) {
            ps.setInt(1, locId);
            ps.setLong(2, endEpoch);
            ps.setLong(3, startEpoch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public void update(ScheduledEvent event) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.ScheduledEvent.UPDATE)) {
            ps.setInt(1, event.getCourseId());
            if (event.getLocId() != null) ps.setInt(2, event.getLocId());
            else ps.setNull(2, Types.INTEGER);
            ps.setString(3, event.getEventType().getDisplay());
            ps.setLong(4, event.getStartEpoch());
            ps.setLong(5, event.getEndEpoch());
            ps.setInt(6, event.getEventId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SqlQueries.ScheduledEvent.DELETE)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private ScheduledEvent map(ResultSet rs) throws SQLException {
        ScheduledEvent e = new ScheduledEvent();
        e.setEventId(rs.getInt("event_id"));
        e.setCourseId(rs.getInt("course_id"));
        int locId = rs.getInt("loc_id");
        e.setLocId(rs.wasNull() ? null : locId);
        e.setEventType(EventType.fromString(rs.getString("event_type")));
        e.setStartEpoch(rs.getLong("start_epoch"));
        e.setEndEpoch(rs.getLong("end_epoch"));
        return e;
    }
}
