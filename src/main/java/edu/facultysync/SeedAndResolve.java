package edu.facultysync;

import edu.facultysync.db.*;
import edu.facultysync.model.*;
import edu.facultysync.service.*;
import edu.facultysync.util.TimePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(SeedAndResolve.class);

    public static void main(String[] args) {
        LOG.info("=== FacultySync - Seed & Resolve Script ===");

        DatabaseManager dbManager = new DatabaseManager();
        try {
            // 1. Initialize schema
            dbManager.initializeSchema();
            LOG.info("[OK] Database schema initialized.");

            // 2. Seed demo data
            SeedData.seedIfEmpty(dbManager);
            SeedData.ensureIntentionalConflicts(dbManager);
            LOG.info("[OK] Demo data seeded.");

            // 3. Print summary of seeded data
            DataCache cache = new DataCache(dbManager);
            printDataSummary(dbManager, cache);

            // 4. Run conflict analysis
            LOG.info("--- Conflict Analysis ---");
            ConflictEngine engine = new ConflictEngine(dbManager, cache);
            List<ConflictResult> conflicts = engine.analyzeAll();

            if (conflicts.isEmpty()) {
                LOG.info("[OK] No conflicts detected.");
            } else {
                LOG.warn("[!] {} conflicts detected", conflicts.size());
                long hard = conflicts.stream()
                        .filter(c -> c.getSeverity() == ConflictResult.Severity.HARD_OVERLAP).count();
                long tight = conflicts.stream()
                        .filter(c -> c.getSeverity() == ConflictResult.Severity.TIGHT_TRANSITION).count();
                LOG.info("    Hard Overlaps:      {}", hard);
                LOG.info("    Tight Transitions:  {}", tight);

                for (int i = 0; i < conflicts.size(); i++) {
                    ConflictResult c = conflicts.get(i);
                    LOG.info("  {}. [{}] {}", i + 1, c.getSeverity(), c.getDescription());
                    if (c.getAvailableAlternatives() != null && !c.getAvailableAlternatives().isEmpty()) {
                        StringBuilder alternatives = new StringBuilder();
                        for (Location alt : c.getAvailableAlternatives()) {
                            alternatives.append(alt.getDisplayName()).append("; ");
                        }
                        LOG.info("     Alternatives: {}", alternatives);
                    }
                }
            }

            // 5. Run auto-resolve
            LOG.info("--- Auto-Resolve ---");
            AutoResolver resolver = new AutoResolver(dbManager, cache);
            AutoResolver.ResolveResult result = resolver.resolveAll();

            LOG.info("Total conflicts:  {}", result.getTotalConflicts());
            LOG.info("Resolved:         {}", result.getResolved());
            LOG.info("Unresolvable:     {}", result.getUnresolvable());

            if (!result.getActions().isEmpty()) {
                LOG.info("Actions taken:");
                for (String action : result.getActions()) {
                    LOG.info("  - {}", action);
                }
            }

            // 6. Re-check conflicts after resolution
            LOG.info("--- Post-Resolve Analysis ---");
            cache.refresh();
            List<ConflictResult> remaining = engine.analyzeAll();
            LOG.info("Remaining conflicts: {}", remaining.size());
            for (int i = 0; i < remaining.size(); i++) {
                ConflictResult c = remaining.get(i);
                LOG.info("  {}. [{}] {}", i + 1, c.getSeverity(), c.getDescription());
            }

            // 7. Print final data summary
            LOG.info("--- Final Data Summary ---");
            cache.refresh();
            printDataSummary(dbManager, cache);

            LOG.info("=== Script completed successfully. Run 'gradle run' to see results. ===");

        } catch (Exception e) {
            LOG.error("Script failed", e);
            System.exit(1);
        } finally {
            dbManager.close();
        }
    }

    private static void printDataSummary(DatabaseManager dbManager, DataCache cache) throws SQLException {
        cache.refresh();
        LOG.info("  Departments:  {}", cache.getAllDepartments().size());
        LOG.info("  Professors:   {}", cache.getAllProfessors().size());
        LOG.info("  Courses:      {}", cache.getAllCourses().size());
        LOG.info("  Locations:    {}", cache.getAllLocations().size());

        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
        cache.enrichAll(events);
        LOG.info("  Events:       {}", events.size());

        if (!events.isEmpty()) {
            LOG.info("  Recent events:");
            int start = Math.max(0, events.size() - 5);
            for (int i = events.size() - 1; i >= start; i--) {
                ScheduledEvent ev = events.get(i);
                String course = ev.getCourseCode() != null ? ev.getCourseCode() : "Course#" + ev.getCourseId();
                String type = ev.getEventType() != null ? ev.getEventType().getDisplay() : "Event";
                String loc = ev.getLocationName() != null ? ev.getLocationName() : "Online";
                String time = ev.getStartEpoch() != null ? TimePolicy.formatEpoch(ev.getStartEpoch()) : "?";
                LOG.info("    - {} {} @ {} ({})", course, type, loc, time);
            }
        }
    }
}
