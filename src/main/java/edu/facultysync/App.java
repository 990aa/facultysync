package edu.facultysync;

import edu.facultysync.db.DatabaseManager;
import edu.facultysync.db.SeedData;
import edu.facultysync.core.AppModule;
import edu.facultysync.service.NotificationService;
import edu.facultysync.ui.CustomTitleBar;
import edu.facultysync.ui.DashboardController;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;

/**
 * FacultySync – University scheduling conflict detection and management.
 */
public class App extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    /** Application version – updated by release script. */
    public static final String VERSION = "0.8.1";

    private DatabaseManager dbManager;

    static String windowTitle() {
        return "FacultySync";
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize database
        dbManager = new DatabaseManager();
        dbManager.initializeSchema();

        // Initialize native Windows notifications
        NotificationService.initialize();

        // Seed demo data if database is empty
        SeedData.seedIfEmpty(dbManager);

        // Re-inject documented demo conflicts in case previous runs resolved or moved them.
        SeedData.ensureIntentionalConflicts(dbManager);

        // Undecorated stage for custom title bar
        primaryStage.initStyle(StageStyle.UNDECORATED);

        // Set application icon (appears in taskbar and window switcher)
        InputStream iconStream = getClass().getResourceAsStream("/app-icon.png");
        if (iconStream != null) {
            primaryStage.getIcons().add(new Image(iconStream));
        }

        // Build UI
        AppModule appModule = AppModule.create(dbManager);
        DashboardController dashboard = new DashboardController(appModule, primaryStage);

        // Wrap with custom title bar
        CustomTitleBar titleBar = new CustomTitleBar(primaryStage, windowTitle());
        VBox appContainer = new VBox();
        appContainer.getChildren().addAll(titleBar, dashboard.getRoot());
        VBox.setVgrow(dashboard.getRoot(), javafx.scene.layout.Priority.ALWAYS);

        // Use visual bounds (excludes taskbar) to size the window properly
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double initWidth = Math.min(1280, screenBounds.getWidth() * 0.82);
        double initHeight = Math.min(800, screenBounds.getHeight() * 0.85);

        Scene scene = new Scene(appContainer, initWidth, initHeight);

        // Defensive CSS loading – null-check the resource URL
        URL cssUrl = getClass().getResource("/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            LOG.warn("style.css not found on classpath.");
        }

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        // Center window on screen (within visual bounds, not covering taskbar)
        primaryStage.setX(screenBounds.getMinX() + (screenBounds.getWidth() - initWidth) / 2);
        primaryStage.setY(screenBounds.getMinY() + (screenBounds.getHeight() - initHeight) / 2);

        // Install resize handlers (requires scene to be set)
        titleBar.installResizeHandlers(primaryStage);

        primaryStage.show();

        // Check for updates asynchronously after window is shown
        javafx.application.Platform.runLater(() -> UpdateChecker.checkForUpdates(primaryStage));
    }

    @Override
    public void stop() {
        NotificationService.shutdown();
        if (dbManager != null) dbManager.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
