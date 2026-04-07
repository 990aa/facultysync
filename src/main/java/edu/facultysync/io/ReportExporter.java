package edu.facultysync.io;

import edu.facultysync.model.ConflictResult;
import edu.facultysync.model.Location;
import edu.facultysync.model.ScheduledEvent;
import edu.facultysync.util.TimePolicy;

import java.io.*;
import java.util.List;

/**
 * Generates text-based conflict reports and CSV schedule exports.
 * Uses BufferedWriter / FileWriter for efficient I/O.
 */
public class ReportExporter {

    /**
     * Export a full conflict report as a formatted text file.
     */
    public void exportConflictReport(File outputFile, List<ConflictResult> conflicts) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {
            bw.write("=============================================================");
            bw.newLine();
            bw.write("           FACULTYSYNC - CONFLICT REPORT");
            bw.newLine();
            bw.write("           Generated: " + TimePolicy.formatEpochWithZone(System.currentTimeMillis()));
            bw.newLine();
            bw.write("=============================================================");
            bw.newLine();
            bw.newLine();

            if (conflicts.isEmpty()) {
                bw.write("No scheduling conflicts detected. All clear!");
                bw.newLine();
                return;
            }

            bw.write("Total conflicts found: " + conflicts.size());
            bw.newLine();
            bw.newLine();

            int idx = 1;
            for (ConflictResult cr : conflicts) {
                bw.write("-------------------------------------------------------------");
                bw.newLine();
                bw.write("Conflict #" + idx++);
                bw.newLine();
                bw.write("  Severity : " + cr.getSeverity());
                bw.newLine();
                bw.write("  Details  : " + cr.getDescription());
                bw.newLine();

                bw.write("  Event A  : " + formatEvent(cr.getEventA()));
                bw.newLine();
                bw.write("  Event B  : " + formatEvent(cr.getEventB()));
                bw.newLine();

                if (cr.getAvailableAlternatives() != null && !cr.getAvailableAlternatives().isEmpty()) {
                    bw.write("  Available Alternative Rooms:");
                    bw.newLine();
                    for (Location loc : cr.getAvailableAlternatives()) {
                        bw.write("    - " + loc.getDisplayName()
                                + " (capacity: " + (loc.getCapacity() != null ? loc.getCapacity() : "unknown")
                                + ", projector: " + (loc.getHasProjector() != null && loc.getHasProjector() == 1 ? "yes" : "no") + ")");
                        bw.newLine();
                    }
                } else if (cr.getSeverity() == ConflictResult.Severity.HARD_OVERLAP) {
                    bw.write("  No alternative rooms available at this time.");
                    bw.newLine();
                }
                bw.newLine();
            }

            bw.write("=============================================================");
            bw.newLine();
            bw.write("End of Report");
            bw.newLine();
        }
    }

    /**
     * Export schedule events as CSV.
     */
    public void exportScheduleCsv(File outputFile, List<ScheduledEvent> events) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {
            bw.write("event_id,course_code,event_type,location,start,end,professor");
            bw.newLine();
            for (ScheduledEvent e : events) {
                bw.write(String.join(",",
                        str(e.getEventId()),
                        csvSafe(e.getCourseCode()),
                        e.getEventType() != null ? e.getEventType().getDisplay() : "",
                        csvSafe(e.getLocationName()),
                        TimePolicy.formatEpoch(e.getStartEpoch()),
                        TimePolicy.formatEpoch(e.getEndEpoch()),
                        csvSafe(e.getProfessorName())
                ));
                bw.newLine();
            }
        }
    }

    private String formatEvent(ScheduledEvent e) {
        if (e == null) return "N/A";
        String code = e.getCourseCode() != null ? e.getCourseCode() : "Course#" + e.getCourseId();
        String type = e.getEventType() != null ? e.getEventType().getDisplay() : "Event";
        String time = "";
        if (e.getStartEpoch() != null && e.getEndEpoch() != null) {
            time = " (" + TimePolicy.formatEpoch(e.getStartEpoch())
                + " to " + TimePolicy.formatEpoch(e.getEndEpoch()) + ")";
        }
        String loc = e.getLocationName() != null ? " @ " + e.getLocationName() : "";
        return code + " " + type + time + loc;
    }

    private String str(Object o) { return o != null ? o.toString() : ""; }

    private String csvSafe(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
