package edu.facultysync.db;

/**
 * Centralized SQL strings used by DAOs.
 * Keeping queries in one place reduces duplication and avoids accidental drift.
 */
public final class SqlQueries {

    private SqlQueries() {
    }

    public static final class Department {
        public static final String INSERT = "INSERT INTO departments (name) VALUES (?)";
        public static final String SELECT_BY_ID = "SELECT dept_id, name FROM departments WHERE dept_id = ?";
        public static final String SELECT_ALL = "SELECT dept_id, name FROM departments ORDER BY name";
        public static final String UPDATE = "UPDATE departments SET name = ? WHERE dept_id = ?";
        public static final String DELETE = "DELETE FROM departments WHERE dept_id = ?";

        private Department() {
        }
    }

    public static final class Professor {
        public static final String INSERT = "INSERT INTO professors (name, dept_id) VALUES (?, ?)";
        public static final String SELECT_BY_ID = "SELECT prof_id, name, dept_id FROM professors WHERE prof_id = ?";
        public static final String SELECT_ALL = "SELECT prof_id, name, dept_id FROM professors ORDER BY name";
        public static final String SELECT_BY_DEPARTMENT =
                "SELECT prof_id, name, dept_id FROM professors WHERE dept_id = ? ORDER BY name";
        public static final String UPDATE = "UPDATE professors SET name = ?, dept_id = ? WHERE prof_id = ?";
        public static final String DELETE = "DELETE FROM professors WHERE prof_id = ?";

        private Professor() {
        }
    }

    public static final class Course {
        public static final String INSERT =
                "INSERT INTO courses (course_code, prof_id, enrollment_count) VALUES (?, ?, ?)";
        public static final String SELECT_BY_ID =
                "SELECT course_id, course_code, prof_id, enrollment_count FROM courses WHERE course_id = ?";
        public static final String SELECT_BY_CODE =
                "SELECT course_id, course_code, prof_id, enrollment_count FROM courses WHERE course_code = ?";
        public static final String SELECT_ALL =
                "SELECT course_id, course_code, prof_id, enrollment_count FROM courses ORDER BY course_code";
        public static final String SELECT_BY_PROFESSOR =
                "SELECT course_id, course_code, prof_id, enrollment_count FROM courses WHERE prof_id = ? ORDER BY course_code";
        public static final String UPDATE =
                "UPDATE courses SET course_code = ?, prof_id = ?, enrollment_count = ? WHERE course_id = ?";
        public static final String DELETE = "DELETE FROM courses WHERE course_id = ?";

        private Course() {
        }
    }

    public static final class Location {
        public static final String INSERT =
                "INSERT INTO locations (building, room_number, capacity, has_projector) VALUES (?, ?, ?, ?)";
        public static final String SELECT_BY_ID =
                "SELECT loc_id, building, room_number, capacity, has_projector FROM locations WHERE loc_id = ?";
        public static final String SELECT_ALL =
                "SELECT loc_id, building, room_number, capacity, has_projector FROM locations ORDER BY building, room_number";
        public static final String FIND_AVAILABLE = """
                SELECT loc_id, building, room_number, capacity, has_projector FROM locations
                WHERE capacity >= ?
                  AND loc_id NOT IN (
                    SELECT DISTINCT loc_id FROM scheduled_events
                    WHERE loc_id IS NOT NULL AND start_epoch < ? AND end_epoch > ?
                  )
                ORDER BY capacity
                """;
        public static final String UPDATE =
                "UPDATE locations SET building = ?, room_number = ?, capacity = ?, has_projector = ? WHERE loc_id = ?";
        public static final String DELETE = "DELETE FROM locations WHERE loc_id = ?";

        private Location() {
        }
    }

    public static final class ScheduledEvent {
        public static final String INSERT = """
                INSERT INTO scheduled_events (course_id, loc_id, event_type, start_epoch, end_epoch)
                VALUES (?, ?, ?, ?, ?)
                """;
        public static final String SELECT_BY_ID = """
                SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch
                FROM scheduled_events
                WHERE event_id = ?
                """;
        public static final String SELECT_ALL = """
                SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch
                FROM scheduled_events
                ORDER BY start_epoch
                """;
        public static final String SELECT_BY_TIME_RANGE = """
                SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch
                FROM scheduled_events
                WHERE start_epoch < ? AND end_epoch > ?
                ORDER BY start_epoch
                """;
        public static final String SELECT_BY_LOCATION = """
                SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch
                FROM scheduled_events
                WHERE loc_id = ?
                ORDER BY start_epoch
                """;
        public static final String SELECT_BY_COURSE = """
                SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch
                FROM scheduled_events
                WHERE course_id = ?
                ORDER BY start_epoch
                """;
        public static final String SELECT_OVERLAPPING = """
                SELECT event_id, course_id, loc_id, event_type, start_epoch, end_epoch
                FROM scheduled_events
                WHERE loc_id = ? AND start_epoch < ? AND end_epoch > ?
                ORDER BY start_epoch
                """;
        public static final String UPDATE = """
                UPDATE scheduled_events
                SET course_id = ?, loc_id = ?, event_type = ?, start_epoch = ?, end_epoch = ?
                WHERE event_id = ?
                """;
        public static final String DELETE = "DELETE FROM scheduled_events WHERE event_id = ?";

        private ScheduledEvent() {
        }
    }
}
