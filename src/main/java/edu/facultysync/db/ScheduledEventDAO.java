package edu.facultysync.db;

import edu.facultysync.model.ScheduledEvent;
import edu.facultysync.model.ScheduledEvent.EventType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ScheduledEventDAO {
    private final DatabaseManager dbManager;

    public ScheduledEventDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public ScheduledEvent insert(ScheduledEvent event) throws SQLException {
        String sql = "INSERT INTO scheduled_events (course_id, loc_id, event_type, start_epoch, end_epoch) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = dbManager.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
        String sql = "SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch "
                + "FROM scheduled_events WHERE event_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public List<ScheduledEvent> findAll() throws SQLException {
        List<ScheduledEvent> list = new ArrayList<>();
        String sql = "SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch "
                + "FROM scheduled_events ORDER BY start_epoch";
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<ScheduledEvent> findByTimeRange(long startEpoch, long endEpoch) throws SQLException {
        List<ScheduledEvent> list = new ArrayList<>();
        String sql = "SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch "
                + "FROM scheduled_events WHERE start_epoch < ? AND end_epoch > ? ORDER BY start_epoch";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
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
        String sql = "SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch "
                + "FROM scheduled_events WHERE loc_id = ? ORDER BY start_epoch";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, locId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public List<ScheduledEvent> findByCourse(int courseId) throws SQLException {
        List<ScheduledEvent> list = new ArrayList<>();
        String sql = "SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch "
                + "FROM scheduled_events WHERE course_id = ? ORDER BY start_epoch";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
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
        String sql = "SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch "
                + "FROM scheduled_events WHERE loc_id = ? AND start_epoch < ? AND end_epoch > ? ORDER BY start_epoch";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
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
        String sql = "UPDATE scheduled_events SET course_id = ?, loc_id = ?, event_type = ?, "
                + "start_epoch = ?, end_epoch = ? WHERE event_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
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
        String sql = "DELETE FROM scheduled_events WHERE event_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
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
