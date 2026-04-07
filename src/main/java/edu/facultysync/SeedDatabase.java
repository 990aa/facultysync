package edu.facultysync;

import edu.facultysync.db.DatabaseManager;
import edu.facultysync.db.ScheduledEventDAO;
import edu.facultysync.db.SeedData;
import edu.facultysync.model.ConflictResult;
import edu.facultysync.service.ConflictEngine;
import edu.facultysync.service.DataCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Standalone script that seeds the database with demo data only.
 *
 * <p>Usage: {@code gradle seedDb}</p>
 */
public class SeedDatabase {

    private static final Logger LOG = LoggerFactory.getLogger(SeedDatabase.class);

    public static void main(String[] args) {
        LOG.info("=== FacultySync - Seed Database Script ===");

        DatabaseManager dbManager = new DatabaseManager();
        try {
            dbManager.initializeSchema();
            SeedData.seedIfEmpty(dbManager);
            SeedData.ensureIntentionalConflicts(dbManager);

            DataCache cache = new DataCache(dbManager);
            cache.refresh();

            int eventCount = new ScheduledEventDAO(dbManager).findAll().size();
            LOG.info("[OK] Seed completed.");
            LOG.info("  Departments: {}", cache.getAllDepartments().size());
            LOG.info("  Professors:  {}", cache.getAllProfessors().size());
            LOG.info("  Courses:     {}", cache.getAllCourses().size());
            LOG.info("  Locations:   {}", cache.getAllLocations().size());
            LOG.info("  Events:      {}", eventCount);

            List<ConflictResult> conflicts = new ConflictEngine(dbManager, cache).analyzeAll();
            long hard = conflicts.stream()
                    .filter(c -> c.getSeverity() == ConflictResult.Severity.HARD_OVERLAP)
                    .count();
            long professor = conflicts.stream()
                    .filter(c -> c.getSeverity() == ConflictResult.Severity.PROFESSOR_OVERLAP)
                    .count();
            long tight = conflicts.stream()
                    .filter(c -> c.getSeverity() == ConflictResult.Severity.TIGHT_TRANSITION)
                    .count();

            LOG.info("[OK] Conflicts available after seeding (for demo): {} total", conflicts.size());
            LOG.info("  HARD_OVERLAP:      {}", hard);
            LOG.info("  PROFESSOR_OVERLAP: {}", professor);
            LOG.info("  TIGHT_TRANSITION:  {}", tight);
            LOG.info("=== Seed script finished successfully ===");
        } catch (Exception e) {
            LOG.error("Seed script failed", e);
            System.exit(1);
        } finally {
            dbManager.close();
        }
    }
}
