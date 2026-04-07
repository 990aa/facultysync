package edu.facultysync.ui;

import edu.facultysync.db.DatabaseManager;
import edu.facultysync.db.ScheduledEventDAO;
import edu.facultysync.model.ScheduledEvent;
import edu.facultysync.service.DataCache;
import edu.facultysync.util.TimePolicy;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Home Page with welcome message, statistics cards,
 * quick-action navigation, and recent activity.
 */
public class HomePage {

    private final DatabaseManager dbManager;
    private final DataCache cache;
    private final TabPane tabPane;
    private final VBox content;

    public HomePage(DatabaseManager dbManager, DataCache cache, TabPane tabPane) {
        this.dbManager = dbManager;
        this.cache = cache;
        this.tabPane = tabPane;
        this.content = new VBox();
        this.content.getStyleClass().add("home-page");
        refresh();
    }

    public VBox getContent() {
        return content;
    }

    /** Refresh statistics when tab is selected. */
    public void refresh() {
        refresh(null);
    }

    public void refresh(Runnable onComplete) {
        showLoadingState();

        Task<HomeSnapshot> task = new Task<>() {
            @Override
            protected HomeSnapshot call() throws Exception {
                cache.refresh();
                List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
                cache.enrichAll(events);

                HomeSnapshot snapshot = new HomeSnapshot();
                snapshot.eventCount = events.size();
                snapshot.courseCount = cache.getAllCourses().size();
                snapshot.profCount = cache.getAllProfessors().size();
                snapshot.roomCount = cache.getAllLocations().size();
                snapshot.deptCount = cache.getAllDepartments().size();

                int start = Math.max(0, events.size() - 8);
                for (int i = events.size() - 1; i >= start; i--) {
                    snapshot.recentEvents.add(events.get(i));
                }
                return snapshot;
            }
        };

        task.setOnSucceeded(e -> {
            VBox newContent = buildContent(task.getValue());
            content.getChildren().setAll(newContent.getChildren());
            if (onComplete != null) {
                onComplete.run();
            }
        });
        task.setOnFailed(e -> {
            showErrorState(task.getException());
            if (onComplete != null) {
                onComplete.run();
            }
        });

        Thread thread = new Thread(task, "HomePageRefresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void showLoadingState() {
        VBox loadingBox = new VBox(12);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPadding(new Insets(48));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(42, 42);

        Label loadingLabel = new Label("Loading dashboard data...");
        loadingLabel.getStyleClass().add("empty-state-desc");

        loadingBox.getChildren().addAll(spinner, loadingLabel);
        content.getChildren().setAll(loadingBox);
    }

    private void showErrorState(Throwable error) {
        VBox errorBox = new VBox(8);
        errorBox.setAlignment(Pos.CENTER);
        errorBox.setPadding(new Insets(48));

        Label title = new Label("Could not load home dashboard");
        title.getStyleClass().add("empty-state-title");

        String message = error != null && error.getMessage() != null
                ? error.getMessage()
                : "Unknown error";
        Label detail = new Label(message);
        detail.getStyleClass().add("empty-state-desc");

        errorBox.getChildren().addAll(title, detail);
        content.getChildren().setAll(errorBox);
    }

    private VBox buildContent(HomeSnapshot snapshot) {
        VBox root = new VBox(0);
        root.getStyleClass().add("home-page");

        // Hero section
        VBox hero = buildHeroSection();
        // Stats cards
        HBox statsRow = buildStatsRow(snapshot);
        // Quick actions
        VBox quickActions = buildQuickActions();
        // Recent activity
        VBox recentActivity = buildRecentActivity(snapshot.recentEvents);

        VBox innerContent = new VBox(24, hero, statsRow, quickActions, recentActivity);
        innerContent.setPadding(new Insets(30, 40, 30, 40));

        ScrollPane scroll = new ScrollPane(innerContent);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("home-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().add(scroll);
        return root;
    }

    private VBox buildHeroSection() {
        VBox hero = new VBox(8);
        hero.getStyleClass().add("hero-section");
        hero.setPadding(new Insets(30, 30, 30, 30));
        hero.setAlignment(Pos.CENTER_LEFT);

        Label welcomeIcon = new Label("\uD83C\uDF93"); // graduation cap
        welcomeIcon.setStyle("-fx-font-size: 48px;");

        Label welcomeTitle = new Label("Welcome to FacultySync");
        welcomeTitle.getStyleClass().add("hero-title");

        Label welcomeSubtitle = new Label(
                "University Schedule & Conflict Management System\n" +
                "Detect scheduling conflicts, visualize your calendar, and optimize room assignments."
        );
        welcomeSubtitle.getStyleClass().add("hero-subtitle");
        welcomeSubtitle.setWrapText(true);

        hero.getChildren().addAll(welcomeIcon, welcomeTitle, welcomeSubtitle);
        return hero;
    }

    private HBox buildStatsRow(HomeSnapshot snapshot) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);

        row.getChildren().addAll(
                buildStatCard("\uD83D\uDCC5", "Events", String.valueOf(snapshot.eventCount), "stat-card-blue"),
                buildStatCard("\uD83D\uDCDA", "Courses", String.valueOf(snapshot.courseCount), "stat-card-green"),
                buildStatCard("\uD83D\uDC68\u200D\uD83C\uDFEB", "Professors", String.valueOf(snapshot.profCount), "stat-card-purple"),
                buildStatCard("\uD83C\uDFE2", "Rooms", String.valueOf(snapshot.roomCount), "stat-card-orange"),
                buildStatCard("\uD83C\uDFDB", "Departments", String.valueOf(snapshot.deptCount), "stat-card-teal")
        );

        return row;
    }

    private VBox buildStatCard(String icon, String label, String value, String styleClass) {
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 28px;");

        Label valueLbl = new Label(value);
        valueLbl.getStyleClass().add("stat-value");

        Label labelLbl = new Label(label);
        labelLbl.getStyleClass().add("stat-label");

        VBox card = new VBox(6, iconLbl, valueLbl, labelLbl);
        card.getStyleClass().addAll("stat-card", styleClass);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(18, 24, 18, 24));
        card.setPrefWidth(180);
        card.setMinWidth(140);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox buildQuickActions() {
        Label sectionTitle = new Label("\u26A1 Quick Actions");
        sectionTitle.getStyleClass().add("section-title");

        HBox actions = new HBox(16);
        actions.setAlignment(Pos.CENTER_LEFT);

        actions.getChildren().addAll(
                buildActionCard("\uD83D\uDCC5", "Schedule", "View & manage\nall events", 1),
                buildActionCard("\u26A0", "Conflicts", "Detect & resolve\nscheduling issues", 2),
                buildActionCard("\uD83D\uDCC6", "Calendar", "Visual weekly\ncalendar view", 3),
                buildActionCard("\uD83D\uDCCA", "Analytics", "Charts & insights\non utilization", 4)
        );

        VBox section = new VBox(12, sectionTitle, actions);
        return section;
    }

    private VBox buildActionCard(String icon, String title, String desc, int tabIndex) {
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 32px;");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("action-card-title");

        Label descLbl = new Label(desc);
        descLbl.getStyleClass().add("action-card-desc");
        descLbl.setWrapText(true);

        VBox card = new VBox(8, iconLbl, titleLbl, descLbl);
        card.getStyleClass().add("action-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20));
        card.setPrefWidth(200);
        card.setMinWidth(160);
        HBox.setHgrow(card, Priority.ALWAYS);

        // Navigate to tab on click
        card.setOnMouseClicked(e -> {
            if (tabPane != null && tabIndex < tabPane.getTabs().size()) {
                tabPane.getSelectionModel().select(tabIndex);
            }
        });

        card.setCursor(javafx.scene.Cursor.HAND);

        return card;
    }

    private VBox buildRecentActivity(List<ScheduledEvent> recentEvents) {
        Label sectionTitle = new Label("\uD83D\uDD53 Recent Events");
        sectionTitle.getStyleClass().add("section-title");

        VBox activityList = new VBox(6);
        activityList.getStyleClass().add("activity-list");
        activityList.setPadding(new Insets(12));

        if (recentEvents.isEmpty()) {
            Label empty = new Label("\uD83D\uDCED  No events yet. Import a CSV or add events to get started!");
            empty.getStyleClass().add("empty-state-label");
            empty.setWrapText(true);
            activityList.getChildren().add(empty);
        } else {
            for (ScheduledEvent ev : recentEvents) {
                activityList.getChildren().add(buildActivityRow(ev));
            }
        }

        VBox section = new VBox(12, sectionTitle, activityList);
        return section;
    }

    private HBox buildActivityRow(ScheduledEvent ev) {
        String typeIcon;
        if (ev.getEventType() != null) {
            typeIcon = switch (ev.getEventType()) {
                case LECTURE -> "\uD83D\uDCDD";
                case EXAM -> "\uD83D\uDCDD";
                case OFFICE_HOURS -> "\uD83D\uDDE3";
            };
        } else {
            typeIcon = "\uD83D\uDCC5";
        }

        Label icon = new Label(typeIcon);
        icon.setStyle("-fx-font-size: 18px;");

        String courseText = ev.getCourseCode() != null ? ev.getCourseCode() : "Course#" + ev.getCourseId();
        String typeText = ev.getEventType() != null ? ev.getEventType().getDisplay() : "Event";
        Label info = new Label(courseText + " – " + typeText);
        info.getStyleClass().add("activity-info");

        String loc = ev.getLocationName() != null ? ev.getLocationName() : "Online";
        Label locLabel = new Label(loc);
        locLabel.getStyleClass().add("activity-location");

        String time = "";
        if (ev.getStartEpoch() != null) {
            time = TimePolicy.formatEpoch(ev.getStartEpoch());
        }
        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("activity-time");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, icon, info, locLabel, spacer, timeLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("activity-row");
        row.setPadding(new Insets(8, 12, 8, 12));
        return row;
    }

    private static class HomeSnapshot {
        int eventCount;
        int courseCount;
        int profCount;
        int roomCount;
        int deptCount;
        List<ScheduledEvent> recentEvents = new ArrayList<>();
    }
}
