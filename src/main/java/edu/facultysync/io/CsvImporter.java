package edu.facultysync.io;

import edu.facultysync.db.*;
import edu.facultysync.model.*;
import edu.facultysync.model.ScheduledEvent.EventType;
import edu.facultysync.util.TimePolicy;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        return importFileWithReport(file, progressCallback).getImportedEvents();
    }

    /**
     * Import events and return detailed diagnostics for dropped rows.
     */
    public ImportReport importFileWithReport(File file, ProgressCallback progressCallback)
            throws IOException, SQLException {

        List<CsvRow> rows = readCsv(file);
        List<ScheduledEvent> imported = new ArrayList<>();
        List<ImportFailure> failures = new ArrayList<>();

        CourseDAO courseDAO = new CourseDAO(dbManager);
        LocationDAO locationDAO = new LocationDAO(dbManager);
        ScheduledEventDAO eventDAO = new ScheduledEventDAO(dbManager);

        Map<String, Integer> locationIndex = new HashMap<>();
        for (Location loc : locationDAO.findAll()) {
            locationIndex.put(locationKey(loc.getBuilding(), loc.getRoomNumber()), loc.getLocId());
        }

        int total = rows.size();
        int current = 0;

        for (CsvRow row : rows) {
            current++;
            String[] cols = row.cols();
            if (cols.length < 6) {
                failures.add(new ImportFailure(row.rowNumber(), "Expected 6 columns", row.rawLine()));
                continue;
            }

            String courseCode = cols[0].trim();
            String eventTypeStr = cols[1].trim();
            String building = cols[2].trim();
            String roomNumber = cols[3].trim();
            String startStr = cols[4].trim();
            String endStr = cols[5].trim();

            if (progressCallback != null)
                progressCallback.onProgress(current, total, "Checking row " + row.rowNumber() + "...");

            if (courseCode.isEmpty()) {
                failures.add(new ImportFailure(row.rowNumber(), "Missing course code", row.rawLine()));
                continue;
            }

            // Resolve course (must exist)
            Course course = courseDAO.findByCode(courseCode);
            if (course == null) {
                failures.add(new ImportFailure(row.rowNumber(),
                        "Unknown course code: " + courseCode, row.rawLine()));
                continue;
            }

            // Parse event type
            EventType eventType = EventType.fromString(eventTypeStr);
            if (eventType == null) {
                failures.add(new ImportFailure(row.rowNumber(),
                        "Unknown event type: " + eventTypeStr, row.rawLine()));
                continue;
            }

            // Resolve location – use Wrapper (Integer) to allow null for online events
            Integer locId = null;
            if (!building.isEmpty() && !roomNumber.isEmpty()) {
                String key = locationKey(building, roomNumber);
                locId = locationIndex.get(key);
                if (locId == null) {
                    try {
                        Location newLoc = new Location(null, building, roomNumber, null, null);
                        locationDAO.insert(newLoc);
                        locId = newLoc.getLocId();
                        locationIndex.put(key, locId);
                    } catch (SQLException ex) {
                        failures.add(new ImportFailure(row.rowNumber(),
                                "Failed to create location " + building + " " + roomNumber
                                        + ": " + ex.getMessage(),
                                row.rawLine()));
                        continue;
                    }
                }
            }

            // Parse epoch timestamps
            Long startEpoch = parseEpoch(startStr);
            Long endEpoch = parseEpoch(endStr);
            if (startEpoch == null || endEpoch == null) {
                failures.add(new ImportFailure(row.rowNumber(),
                        "Invalid datetime value", row.rawLine()));
                continue;
            }
            if (endEpoch <= startEpoch) {
                failures.add(new ImportFailure(row.rowNumber(),
                        "End time must be after start time", row.rawLine()));
                continue;
            }

            ScheduledEvent event = new ScheduledEvent(null, course.getCourseId(), locId,
                    eventType, startEpoch, endEpoch);
            try {
                eventDAO.insert(event);
                imported.add(event);
            } catch (SQLException ex) {
                failures.add(new ImportFailure(row.rowNumber(),
                        "Failed to insert event: " + ex.getMessage(), row.rawLine()));
            }
        }

        if (progressCallback != null) {
            progressCallback.onProgress(total, total, "Import complete.");
        }

        return new ImportReport(imported, failures, total);
    }

    private List<CsvRow> readCsv(File file) throws IOException {
        List<CsvRow> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        try (Reader reader = new BufferedReader(new FileReader(file));
             CSVParser parser = format.parse(reader)) {
            for (CSVRecord record : parser) {
                if (isBlankRecord(record)) {
                    continue;
                }
                String[] cols = toColumns(record);
                rows.add(new CsvRow((int) record.getRecordNumber() + 1, recordToRaw(record), cols));
            }
        }
        return rows;
    }

    private boolean isBlankRecord(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String[] toColumns(CSVRecord record) {
        String[] cols = new String[record.size()];
        for (int i = 0; i < record.size(); i++) {
            cols[i] = record.get(i);
        }
        return cols;
    }

    private String recordToRaw(CSVRecord record) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < record.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            String value = record.get(i);
            sb.append(value == null ? "" : value);
        }
        return sb.toString();
    }

    private String locationKey(String building, String roomNumber) {
        return (building == null ? "" : building.trim().toLowerCase(Locale.ROOT))
                + "|"
                + (roomNumber == null ? "" : roomNumber.trim().toLowerCase(Locale.ROOT));
    }

    private Long parseEpoch(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        Long parsed = TimePolicy.parseDateTime(dateStr);
        if (parsed != null) {
            return parsed;
        }
        try {
            return Long.parseLong(dateStr);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Callback interface for progress reporting. */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
    }

    /** Per-row import failure details for UI feedback/reporting. */
    public static class ImportFailure {
        private final int rowNumber;
        private final String reason;
        private final String rawRow;

        public ImportFailure(int rowNumber, String reason, String rawRow) {
            this.rowNumber = rowNumber;
            this.reason = reason;
            this.rawRow = rawRow;
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public String getReason() {
            return reason;
        }

        public String getRawRow() {
            return rawRow;
        }
    }

    /** Detailed import report with successful events and row-level failures. */
    public static class ImportReport {
        private final List<ScheduledEvent> importedEvents;
        private final List<ImportFailure> failures;
        private final int totalRows;

        public ImportReport(List<ScheduledEvent> importedEvents, List<ImportFailure> failures, int totalRows) {
            this.importedEvents = new ArrayList<>(importedEvents);
            this.failures = new ArrayList<>(failures);
            this.totalRows = totalRows;
        }

        public List<ScheduledEvent> getImportedEvents() {
            return importedEvents;
        }

        public List<ImportFailure> getFailures() {
            return failures;
        }

        public int getTotalRows() {
            return totalRows;
        }

        public int getImportedCount() {
            return importedEvents.size();
        }

        public int getFailureCount() {
            return failures.size();
        }
    }

    private record CsvRow(int rowNumber, String rawLine, String[] cols) {}
}
