package edu.facultysync.io;

import edu.facultysync.db.*;
import edu.facultysync.model.*;
import edu.facultysync.model.ScheduledEvent.EventType;
import edu.facultysync.service.DataCache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CsvImporter and ReportExporter.
 */
class IoTest {

    private static DatabaseManager dbManager;
    private static DataCache cache;
    private static int courseAId;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setup() throws SQLException {
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        dbManager.initializeSchema();

        DepartmentDAO deptDAO = new DepartmentDAO(dbManager);
        Department dept = deptDAO.insert(new Department(null, "CS"));

        ProfessorDAO profDAO = new ProfessorDAO(dbManager);
        Professor prof = profDAO.insert(new Professor(null, "Dr. Smith", dept.getDeptId()));

        CourseDAO courseDAO = new CourseDAO(dbManager);
        Course cA = courseDAO.insert(new Course(null, "CS101", prof.getProfId(), 30));
        courseAId = cA.getCourseId();

        LocationDAO locDAO = new LocationDAO(dbManager);
        locDAO.insert(new Location(null, "Science Hall", "101", 50, 1));

        cache = new DataCache(dbManager);
        cache.refresh();
    }

    @AfterAll
    static void teardown() { dbManager.close(); }

    // ===== CsvImporter =====

    @Test
    void import_validCsv() throws Exception {
        File csv = tempDir.resolve("valid.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            bw.write("CS101,Lecture,Science Hall,101,2026-03-01 09:00,2026-03-01 10:00\n");
            bw.write("CS101,Exam,Science Hall,101,2026-03-02 14:00,2026-03-02 16:00\n");
        }

        CsvImporter importer = new CsvImporter(dbManager);
        List<ScheduledEvent> imported = importer.importFile(csv, null);
        assertEquals(2, imported.size());
    }

    @Test
    void import_withProgress() throws Exception {
        File csv = tempDir.resolve("progress.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            bw.write("CS101,Lecture,Science Hall,101,2026-04-01 09:00,2026-04-01 10:00\n");
        }

        List<String> messages = new ArrayList<>();
        CsvImporter importer = new CsvImporter(dbManager);
        importer.importFile(csv, (current, total, msg) -> messages.add(msg));
        assertFalse(messages.isEmpty());
        assertTrue(messages.get(messages.size() - 1).contains("complete"));
    }

    @Test
    void import_unknownCourse() throws Exception {
        File csv = tempDir.resolve("unknown.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            bw.write("UNKNOWN999,Lecture,Science Hall,101,2026-03-01 09:00,2026-03-01 10:00\n");
        }

        CsvImporter importer = new CsvImporter(dbManager);
        List<ScheduledEvent> imported = importer.importFile(csv, null);
        // Unknown course should be skipped
        assertEquals(0, imported.size());
    }

    @Test
    void import_onlineEvent_emptyRoom() throws Exception {
        File csv = tempDir.resolve("online.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            bw.write("CS101,Office Hours,,,2026-03-01 15:00,2026-03-01 16:00\n");
        }

        CsvImporter importer = new CsvImporter(dbManager);
        List<ScheduledEvent> imported = importer.importFile(csv, null);
        assertEquals(1, imported.size());
        assertNull(imported.get(0).getLocId());
    }

    @Test
    void import_invalidEventType() throws Exception {
        File csv = tempDir.resolve("badtype.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            bw.write("CS101,InvalidType,Science Hall,101,2026-03-01 09:00,2026-03-01 10:00\n");
        }

        CsvImporter importer = new CsvImporter(dbManager);
        List<ScheduledEvent> imported = importer.importFile(csv, null);
        assertEquals(0, imported.size());
    }

    @Test
    void import_badDateFormat() throws Exception {
        File csv = tempDir.resolve("baddate.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            bw.write("CS101,Lecture,Science Hall,101,not-a-date,also-not-a-date\n");
        }

        CsvImporter importer = new CsvImporter(dbManager);
        List<ScheduledEvent> imported = importer.importFile(csv, null);
        assertEquals(0, imported.size());
    }

    @Test
    void import_endBeforeStart() throws Exception {
        File csv = tempDir.resolve("endbeforestart.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            bw.write("CS101,Lecture,Science Hall,101,2026-03-01 10:00,2026-03-01 09:00\n");
        }

        CsvImporter importer = new CsvImporter(dbManager);
        List<ScheduledEvent> imported = importer.importFile(csv, null);
        assertEquals(0, imported.size());
    }

    @Test
    void import_malformedRow() throws Exception {
        File csv = tempDir.resolve("malformed.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            bw.write("CS101,Lecture\n"); // too few columns
        }

        CsvImporter importer = new CsvImporter(dbManager);
        List<ScheduledEvent> imported = importer.importFile(csv, null);
        assertEquals(0, imported.size());
    }

    @Test
    void import_epochTimestamps() throws Exception {
        File csv = tempDir.resolve("epoch.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            bw.write("CS101,Lecture,Science Hall,101,1800000000000,1800003600000\n");
        }

        CsvImporter importer = new CsvImporter(dbManager);
        List<ScheduledEvent> imported = importer.importFile(csv, null);
        assertEquals(1, imported.size());
        assertEquals(1800000000000L, imported.get(0).getStartEpoch());
    }

    @Test
    void import_emptyFile() throws Exception {
        File csv = tempDir.resolve("empty.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            // no data rows
        }

        CsvImporter importer = new CsvImporter(dbManager);
        List<ScheduledEvent> imported = importer.importFile(csv, null);
        assertEquals(0, imported.size());
    }

    @Test
    void import_createsNewLocation() throws Exception {
        File csv = tempDir.resolve("newloc.csv").toFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csv))) {
            bw.write("course_code,event_type,building,room_number,start_datetime,end_datetime\n");
            bw.write("CS101,Lecture,New Building,999,2026-05-01 09:00,2026-05-01 10:00\n");
        }

        int locCountBefore = new LocationDAO(dbManager).findAll().size();
        CsvImporter importer = new CsvImporter(dbManager);
        importer.importFile(csv, null);
        int locCountAfter = new LocationDAO(dbManager).findAll().size();
        assertTrue(locCountAfter > locCountBefore);
    }

    // ===== ReportExporter =====

    @Test
    void exportConflictReport_noConflicts() throws Exception {
        File output = tempDir.resolve("report_empty.txt").toFile();
        new ReportExporter().exportConflictReport(output, List.of());
        String content = Files.readString(output.toPath());
        assertTrue(content.contains("No scheduling conflicts detected"));
    }

    @Test
    void exportConflictReport_withConflicts() throws Exception {
        ScheduledEvent a = new ScheduledEvent(1, 1, 1, EventType.LECTURE, 1000000L, 2000000L);
        a.setCourseCode("CS101");
        a.setLocationName("Science Hall 101");
        ScheduledEvent b = new ScheduledEvent(2, 2, 1, EventType.EXAM, 1500000L, 2500000L);
        b.setCourseCode("CS201");
        b.setLocationName("Science Hall 101");

        ConflictResult cr = new ConflictResult(a, b, ConflictResult.Severity.HARD_OVERLAP,
                "Room Science Hall 101 is double-booked");
        Location alt = new Location(3, "Eng", "201", 40, 1);
        cr.setAvailableAlternatives(List.of(alt));

        File output = tempDir.resolve("report.txt").toFile();
        new ReportExporter().exportConflictReport(output, List.of(cr));
        String content = Files.readString(output.toPath());

        assertTrue(content.contains("FACULTYSYNC"));
        assertTrue(content.contains("HARD_OVERLAP"));
        assertTrue(content.contains("double-booked"));
        assertTrue(content.contains("Eng 201"));
        assertTrue(content.contains("Total conflicts found: 1"));
    }

    @Test
    void exportConflictReport_withNoAlternatives() throws Exception {
        ScheduledEvent a = new ScheduledEvent(1, 1, 1, EventType.LECTURE, 1000L, 2000L);
        a.setCourseCode("CS101");
        ConflictResult cr = new ConflictResult(a, a, ConflictResult.Severity.HARD_OVERLAP, "Overlap");
        cr.setAvailableAlternatives(List.of());

        File output = tempDir.resolve("report_noalt.txt").toFile();
        new ReportExporter().exportConflictReport(output, List.of(cr));
        String content = Files.readString(output.toPath());
        assertTrue(content.contains("No alternative rooms"));
    }

    @Test
    void exportConflictReport_tightTransition() throws Exception {
        ScheduledEvent a = new ScheduledEvent(1, 1, 1, EventType.LECTURE, 1000L, 2000L);
        a.setCourseCode("CS101");
        ConflictResult cr = new ConflictResult(a, a, ConflictResult.Severity.TIGHT_TRANSITION, "Tight gap");

        File output = tempDir.resolve("report_tight.txt").toFile();
        new ReportExporter().exportConflictReport(output, List.of(cr));
        String content = Files.readString(output.toPath());
        assertTrue(content.contains("TIGHT_TRANSITION"));
    }

    @Test
    void exportScheduleCsv() throws Exception {
        ScheduledEvent e = new ScheduledEvent(1, 1, 1, EventType.LECTURE, 1700000000000L, 1700003600000L);
        e.setCourseCode("CS101");
        e.setLocationName("Science Hall 101");
        e.setProfessorName("Dr. Smith");

        File output = tempDir.resolve("schedule.csv").toFile();
        new ReportExporter().exportScheduleCsv(output, List.of(e));
        String content = Files.readString(output.toPath());
        assertTrue(content.contains("event_id,course_code,event_type,location,start,end,professor"));
        assertTrue(content.contains("CS101"));
        assertTrue(content.contains("Dr. Smith"));
    }

    @Test
    void exportScheduleCsv_empty() throws Exception {
        File output = tempDir.resolve("schedule_empty.csv").toFile();
        new ReportExporter().exportScheduleCsv(output, List.of());
        String content = Files.readString(output.toPath());
        // Only header line
        String[] lines = content.split("\n");
        assertEquals(1, lines.length);
    }

    @Test
    void exportScheduleCsv_nullFields() throws Exception {
        ScheduledEvent e = new ScheduledEvent(null, null, null, null, null, null);
        File output = tempDir.resolve("schedule_null.csv").toFile();
        new ReportExporter().exportScheduleCsv(output, List.of(e));
        String content = Files.readString(output.toPath());
        assertTrue(content.contains(",,,"));
    }

    @Test
    void export_csvSafeHandlesCommas() throws Exception {
        ScheduledEvent e = new ScheduledEvent(1, 1, 1, EventType.LECTURE, 1000L, 2000L);
        e.setCourseCode("CS,101");
        e.setLocationName("Building \"A\"");
        e.setProfessorName("Dr. O'Smith");

        File output = tempDir.resolve("schedule_special.csv").toFile();
        new ReportExporter().exportScheduleCsv(output, List.of(e));
        String content = Files.readString(output.toPath());
        // CSV should properly quote fields with commas/quotes
        assertTrue(content.contains("\"CS,101\""));
        assertTrue(content.contains("\"Building \"\"A\"\"\""));
    }
}
