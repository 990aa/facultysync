package edu.facultysync;

import edu.facultysync.db.DatabaseManager;
import edu.facultysync.ui.DashboardController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * FacultySync – University scheduling conflict detection and management.
 */
public class App extends Application {

    private DatabaseManager dbManager;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize database
        dbManager = new DatabaseManager();
        dbManager.initializeSchema();

        // Build UI
        DashboardController dashboard = new DashboardController(dbManager, primaryStage);
        Scene scene = new Scene(dashboard.getRoot(), 1200, 750);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("FacultySync – Schedule & Conflict Manager");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (dbManager != null) dbManager.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
