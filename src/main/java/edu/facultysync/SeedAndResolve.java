package edu.facultysync;

import edu.facultysync.db.*;
import edu.facultysync.model.*;
import edu.facultysync.service.*;
import edu.facultysync.util.TimePolicy;

import java.sql.SQLException;
import java.util.List;

/**
 * Standalone script that seeds the database with demo data,
 * runs conflict analysis, and performs auto-resolution.
 * Results are persisted so they are visible when the app is launched with {@code gradle run}.
 *
 * <p>Usage: {@code gradle seedAndResolve}</p>
 */
public class SeedAndResolve {

    public static void main(String[] args) {
        System.out.println("=== FacultySync – Seed & Resolve Script ===\n");

        DatabaseManager dbManager = new DatabaseManager();
        try {
            // 1. Initialize schema
            dbManager.initializeSchema();
            System.out.println("[OK] Database schema initialized.");

            // 2. Seed demo data
            SeedData.seedIfEmpty(dbManager);
            SeedData.ensureIntentionalConflicts(dbManager);
            System.out.println("[OK] Demo data seeded.\n");

            // 3. Print summary of seeded data
            DataCache cache = new DataCache(dbManager);
            printDataSummary(dbManager, cache);

            // 4. Run conflict analysis
            System.out.println("\n--- Conflict Analysis ---");
            ConflictEngine engine = new ConflictEngine(dbManager, cache);
            List<ConflictResult> conflicts = engine.analyzeAll();

            if (conflicts.isEmpty()) {
                System.out.println("[OK] No conflicts detected.");
            } else {
                System.out.println("[!] " + conflicts.size() + " conflicts detected:\n");
                long hard = conflicts.stream()
                        .filter(c -> c.getSeverity() == ConflictResult.Severity.HARD_OVERLAP).count();
                long tight = conflicts.stream()
                        .filter(c -> c.getSeverity() == ConflictResult.Severity.TIGHT_TRANSITION).count();
                System.out.println("    Hard Overlaps:      " + hard);
                System.out.println("    Tight Transitions:  " + tight);
                System.out.println();

                for (int i = 0; i < conflicts.size(); i++) {
                    ConflictResult c = conflicts.get(i);
                    System.out.printf("  %d. [%s] %s%n", i + 1, c.getSeverity(), c.getDescription());
                    if (c.getAvailableAlternatives() != null && !c.getAvailableAlternatives().isEmpty()) {
                        System.out.print("     Alternatives: ");
                        for (Location alt : c.getAvailableAlternatives()) {
                            System.out.print(alt.getDisplayName() + "; ");
                        }
                        System.out.println();
                    }
                }
            }

            // 5. Run auto-resolve
            System.out.println("\n--- Auto-Resolve ---");
            AutoResolver resolver = new AutoResolver(dbManager, cache);
            AutoResolver.ResolveResult result = resolver.resolveAll();

            System.out.println("Total conflicts:  " + result.getTotalConflicts());
            System.out.println("Resolved:         " + result.getResolved());
            System.out.println("Unresolvable:     " + result.getUnresolvable());

            if (!result.getActions().isEmpty()) {
                System.out.println("\nActions taken:");
                for (String action : result.getActions()) {
                    System.out.println("  - " + action);
                }
            }

            // 6. Re-check conflicts after resolution
            System.out.println("\n--- Post-Resolve Analysis ---");
            cache.refresh();
            List<ConflictResult> remaining = engine.analyzeAll();
            System.out.println("Remaining conflicts: " + remaining.size());
            for (int i = 0; i < remaining.size(); i++) {
                ConflictResult c = remaining.get(i);
                System.out.printf("  %d. [%s] %s%n", i + 1, c.getSeverity(), c.getDescription());
            }

            // 7. Print final data summary
            System.out.println("\n--- Final Data Summary ---");
            cache.refresh();
            printDataSummary(dbManager, cache);

            System.out.println("\n=== Script completed successfully. Run 'gradle run' to see results. ===");

        } catch (Exception e) {
            System.err.println("Script failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            dbManager.close();
        }
    }

    private static void printDataSummary(DatabaseManager dbManager, DataCache cache) throws SQLException {
        cache.refresh();
        System.out.println("  Departments:  " + cache.getAllDepartments().size());
        System.out.println("  Professors:   " + cache.getAllProfessors().size());
        System.out.println("  Courses:      " + cache.getAllCourses().size());
        System.out.println("  Locations:    " + cache.getAllLocations().size());

        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
        cache.enrichAll(events);
        System.out.println("  Events:       " + events.size());

        if (!events.isEmpty()) {
            System.out.println("\n  Recent events:");
            int start = Math.max(0, events.size() - 5);
            for (int i = events.size() - 1; i >= start; i--) {
                ScheduledEvent ev = events.get(i);
                String course = ev.getCourseCode() != null ? ev.getCourseCode() : "Course#" + ev.getCourseId();
                String type = ev.getEventType() != null ? ev.getEventType().getDisplay() : "Event";
                String loc = ev.getLocationName() != null ? ev.getLocationName() : "Online";
                String time = ev.getStartEpoch() != null ? TimePolicy.formatEpoch(ev.getStartEpoch()) : "?";
                System.out.printf("    - %s %s @ %s (%s)%n", course, type, loc, time);
            }
        }
    }
}
