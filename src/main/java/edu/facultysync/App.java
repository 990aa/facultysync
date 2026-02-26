package edu.facultysync;

import edu.facultysync.db.DatabaseManager;
import edu.facultysync.db.SeedData;
import edu.facultysync.ui.CustomTitleBar;
import edu.facultysync.ui.DashboardController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;

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

        // Seed demo data if database is empty
        SeedData.seedIfEmpty(dbManager);

        // Undecorated stage for custom title bar
        primaryStage.initStyle(StageStyle.UNDECORATED);

        // Build UI
        DashboardController dashboard = new DashboardController(dbManager, primaryStage);

        // Wrap with custom title bar
        CustomTitleBar titleBar = new CustomTitleBar(primaryStage, "FacultySync");
        VBox appContainer = new VBox();
        appContainer.getChildren().addAll(titleBar, dashboard.getRoot());
        javafx.scene.layout.VBox.setVgrow(dashboard.getRoot(), javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(appContainer, 1280, 800);

        // Defensive CSS loading – null-check the resource URL
        URL cssUrl = getClass().getResource("/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("Warning: style.css not found on classpath.");
        }

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
