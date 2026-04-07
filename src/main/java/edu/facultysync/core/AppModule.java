package edu.facultysync.core;

import com.google.common.eventbus.EventBus;
import edu.facultysync.db.DatabaseManager;
import edu.facultysync.service.ConflictEngine;
import edu.facultysync.service.DataCache;

import java.sql.SQLException;

/**
 * Lightweight application dependency container.
 *
 * <p>Provides shared singletons for UI, services, and event-driven updates
 * without introducing a heavy DI framework.</p>
 */
public record AppModule(
        DatabaseManager dbManager,
        DataCache cache,
        ConflictEngine conflictEngine,
        EventBus eventBus
) {
    /**
     * Builds a fully wired application module from the database manager.
     *
     * @param dbManager shared database manager used by DAOs and services
     * @return initialized module containing cache, conflict engine, and UI event bus
     * @throws SQLException if cache bootstrap fails
     */
    public static AppModule create(DatabaseManager dbManager) throws SQLException {
        DataCache cache = new DataCache(dbManager);
        cache.refresh();
        ConflictEngine conflictEngine = new ConflictEngine(dbManager, cache);
        EventBus eventBus = new EventBus("FacultySyncUIBus");
        return new AppModule(dbManager, cache, conflictEngine, eventBus);
    }
}
