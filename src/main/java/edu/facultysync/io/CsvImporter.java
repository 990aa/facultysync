package edu.facultysync.io;

import edu.facultysync.db.*;
import edu.facultysync.model.*;
import edu.facultysync.model.ScheduledEvent.EventType;

import java.io.*;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Robust CSV parser for importing university schedule data.
 * Uses Wrapper classes (Integer, Long) instead of primitives to safely handle missing data.
 *
 * <p>Expected CSV columns:
 * <pre>course_code,event_type,building,room_number,start_datetime,end_datetime</pre>
 *
 * <p>Missing building/room gracefully results in null loc_id (online event).
 */
public class CsvImporter {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final DatabaseManager dbManager;

    public CsvImporter(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Import events from a CSV file.
     *
     * @param file           the CSV file
     * @param progressCallback optional callback receiving (current, total, message)
     * @return list of imported events
     */
    public List<ScheduledEvent> importFile(File file, ProgressCallback progressCallback)
            throws IOException, SQLException {

        List<String[]> rows = readCsv(file);
        List<ScheduledEvent> imported = new ArrayList<>();

        CourseDAO courseDAO = new CourseDAO(dbManager);
        LocationDAO locationDAO = new LocationDAO(dbManager);
        ScheduledEventDAO eventDAO = new ScheduledEventDAO(dbManager);

        int total = rows.size();
        int current = 0;

        for (String[] cols : rows) {
            current++;
            if (cols.length < 6) continue; // skip malformed rows

            String courseCode = cols[0].trim();
            String eventTypeStr = cols[1].trim();
            String building = cols[2].trim();
            String roomNumber = cols[3].trim();
            String startStr = cols[4].trim();
            String endStr = cols[5].trim();

            if (progressCallback != null)
                progressCallback.onProgress(current, total, "Checking " + courseCode + "...");

            // Resolve course (must exist)
            Course course = courseDAO.findByCode(courseCode);
            if (course == null) continue; // skip unknown courses

            // Parse event type
            EventType eventType = EventType.fromString(eventTypeStr);
            if (eventType == null) continue;

            // Resolve location – use Wrapper (Integer) to allow null for online events
            Integer locId = null;
            if (!building.isEmpty() && !roomNumber.isEmpty()) {
                // Try to find existing location
                List<Location> allLocs = locationDAO.findAll();
                for (Location loc : allLocs) {
                    if (building.equalsIgnoreCase(loc.getBuilding())
                            && roomNumber.equalsIgnoreCase(loc.getRoomNumber())) {
                        locId = loc.getLocId();
                        break;
                    }
                }
                if (locId == null) {
                    // Create location on the fly
                    Location newLoc = new Location(null, building, roomNumber, null, null);
                    locationDAO.insert(newLoc);
                    locId = newLoc.getLocId();
                }
            }

            // Parse epoch timestamps
            Long startEpoch = parseEpoch(startStr);
            Long endEpoch = parseEpoch(endStr);
            if (startEpoch == null || endEpoch == null || endEpoch <= startEpoch) continue;

            ScheduledEvent event = new ScheduledEvent(null, course.getCourseId(), locId,
                    eventType, startEpoch, endEpoch);
            eventDAO.insert(event);
            imported.add(event);
        }

        if (progressCallback != null)
            progressCallback.onProgress(total, total, "Import complete.");

        return imported;
    }

    private List<String[]> readCsv(File file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                rows.add(line.split(",", -1));
            }
        }
        return rows;
    }

    private Long parseEpoch(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            Date d = DATE_FMT.parse(dateStr);
            return d.getTime();
        } catch (ParseException e) {
            // Try epoch directly
            try { return Long.parseLong(dateStr); } catch (NumberFormatException ex) { return null; }
        }
    }

    /** Callback interface for progress reporting. */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
    }
}
