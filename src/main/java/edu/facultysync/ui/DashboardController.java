package edu.facultysync.ui;

import edu.facultysync.db.*;
import edu.facultysync.io.CsvImporter;
import edu.facultysync.io.ReportExporter;
import edu.facultysync.model.*;
import edu.facultysync.model.ConflictResult.Severity;
import edu.facultysync.service.AutoResolver;
import edu.facultysync.service.ConflictEngine;
import edu.facultysync.service.DataCache;
import edu.facultysync.service.NotificationService;
import edu.facultysync.util.TimePolicy;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Main dashboard UI controller for FacultySync.
 * Uses a BorderPane layout: left sidebar, center with Home/Schedule/Conflicts/Calendar/Analytics tabs.
 * Integrates toast notifications, auto-resolve, drag-drop calendar, and analytics charts.
 */
public class DashboardController {

    private final DatabaseManager dbManager;
    private final Stage stage;
    private final DataCache cache;
    private final ConflictEngine conflictEngine;
    private final StackPane rootStack; // root for toasts overlay
    private final BorderPane root;

    // UI components
    private TabPane tabPane;
    private final ComboBox<Department> departmentCombo = new ComboBox<>();
    private final TableView<ScheduledEvent> eventTable = new TableView<>();
    private final TableView<ConflictResult> conflictTable = new TableView<>();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("Ready");
    private final Label conflictSummaryLabel = new Label();
    private final StackPane busyOverlay = new StackPane();
    private final Label busyOverlayLabel = new Label("Loading...");
    private int busyDepth = 0;

    // Sub-views
    private HomePage homePage;
    private CalendarView calendarView;
    private AnalyticsView analyticsView;

    public DashboardController(DatabaseManager dbManager, Stage stage) throws SQLException {
        this.dbManager = dbManager;
        this.stage = stage;
        this.cache = new DataCache(dbManager);
        this.conflictEngine = new ConflictEngine(dbManager, cache);

        cache.refresh();
        root = buildLayout();

        // Wrap in StackPane for toast overlay
        rootStack = new StackPane(root);
        rootStack.getStyleClass().add("root-stack");
        ToastNotification.initialize(rootStack);
        initializeBusyOverlay();

        refreshData();
    }

    /** Returns the root node (StackPane wrapping BorderPane + toast layer). */
    public StackPane getRoot() { return rootStack; }

    // ========== LAYOUT ==========

    private BorderPane buildLayout() {
        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("root-pane");

        // --- Left control panel ---
        VBox leftPanel = buildLeftPanel();
        leftPanel.getStyleClass().add("left-panel");
        pane.setLeft(leftPanel);

        // --- Center: tabs ---
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("main-tab-pane");

        // Home tab
        homePage = new HomePage(dbManager, cache, tabPane);
        Tab homeTab = new Tab("  \u2302 Home  ", homePage.getContent());
        homeTab.setId("homeTab");

        // Schedule tab
        Tab scheduleTab = new Tab("  \uD83D\uDCC5 Schedule  ", buildScheduleView());
        scheduleTab.setId("scheduleTab");

        // Conflicts tab
        Tab conflictTab = new Tab("  \u26A0 Conflicts  ", buildConflictView());
        conflictTab.setId("conflictTab");

        // Calendar tab
        calendarView = new CalendarView(dbManager, cache);
        Tab calendarTab = new Tab("  \uD83D\uDCC6 Calendar  ", calendarView.getView());
        calendarTab.setId("calendarTab");

        // Analytics tab
        analyticsView = new AnalyticsView(dbManager, cache);
        Tab analyticsTab = new Tab("  \uD83D\uDCCA Analytics  ", analyticsView.getView());
        analyticsTab.setId("analyticsTab");

        tabPane.getTabs().addAll(homeTab, scheduleTab, conflictTab, calendarTab, analyticsTab);

        // Refresh views when tab is selected
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == homeTab) {
                showBusy("Loading Home...");
                homePage.refresh(this::hideBusy);
            } else if (newTab == calendarTab) {
                showBusy("Loading Calendar...");
                calendarView.refresh(this::hideBusy);
            } else if (newTab == analyticsTab) {
                showBusy("Loading Analytics...");
                analyticsView.refresh(this::hideBusy);
            } else if (newTab == scheduleTab) {
                refreshData();
            }
        });

        pane.setCenter(tabPane);

        // --- Bottom status bar ---
        HBox statusBar = new HBox(10, progressBar, statusLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.getStyleClass().add("status-bar");
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);
        pane.setBottom(statusBar);

        return pane;
    }

    private VBox buildLeftPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));
        panel.setPrefWidth(240);

        Label title = new Label("FacultySync");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Schedule Manager");
        subtitle.getStyleClass().add("app-subtitle");

        // Department filter
        Label deptLabel = new Label("\uD83C\uDFDB Department Filter");
        deptLabel.getStyleClass().add("sidebar-section-label");
        departmentCombo.setId("departmentCombo");
        departmentCombo.setMaxWidth(Double.MAX_VALUE);
        departmentCombo.setPromptText("All Departments");
        departmentCombo.getItems().addAll(cache.getAllDepartments().values());
        departmentCombo.setOnAction(e -> refreshData());

        // Action buttons with icons
        Button importBtn = createSidebarButton("\uD83D\uDCE5 Import CSV", "primary-btn");
        importBtn.setId("importBtn");
        importBtn.setOnAction(e -> handleImport());

        Button exportScheduleBtn = createSidebarButton("\uD83D\uDCE4 Export Schedule", null);
        exportScheduleBtn.setId("exportScheduleBtn");
        exportScheduleBtn.setOnAction(e -> handleExportSchedule());

        Button exportConflictBtn = createSidebarButton("\uD83D\uDCCB Export Conflicts", null);
        exportConflictBtn.setId("exportConflictBtn");
        exportConflictBtn.setOnAction(e -> handleExportConflictReport());

        Button analyzeBtn = createSidebarButton("\u26A0 Analyze Conflicts", "warning-btn");
        analyzeBtn.setId("analyzeBtn");
        analyzeBtn.setOnAction(e -> handleAnalyze());

        Button autoResolveBtn = createSidebarButton("\u2728 Auto-Resolve", "resolve-btn");
        autoResolveBtn.setId("autoResolveBtn");
        autoResolveBtn.setOnAction(e -> handleAutoResolve());

        Separator sep1 = new Separator();
        sep1.getStyleClass().add("sidebar-separator");

        Label manageLabel = new Label("\u2699 Manage Data");
        manageLabel.getStyleClass().add("sidebar-section-label");

        Button manageDeptBtn = createSidebarButton("\uD83D\uDCCB Departments", null);
        manageDeptBtn.setId("manageDeptBtn");
        manageDeptBtn.setOnAction(e -> handleManageDepartments());

        Button manageProfBtn = createSidebarButton("\uD83D\uDCCB Professors", null);
        manageProfBtn.setId("manageProfBtn");
        manageProfBtn.setOnAction(e -> handleManageProfessors());

        Button manageCourseBtn = createSidebarButton("\uD83D\uDCCB Courses", null);
        manageCourseBtn.setId("manageCourseBtn");
        manageCourseBtn.setOnAction(e -> handleManageCourses());

        Button manageLocationBtn = createSidebarButton("\uD83D\uDCCB Locations", null);
        manageLocationBtn.setId("manageLocationBtn");
        manageLocationBtn.setOnAction(e -> handleManageLocations());

        Button addDeptBtn = createSidebarButton("\u2795 Department", null);
        addDeptBtn.setId("addDeptBtn");
        addDeptBtn.setOnAction(e -> handleAddDepartment());

        Button addProfBtn = createSidebarButton("\u2795 Professor", null);
        addProfBtn.setId("addProfBtn");
        addProfBtn.setOnAction(e -> handleAddProfessor());

        Button addCourseBtn = createSidebarButton("\u2795 Course", null);
        addCourseBtn.setId("addCourseBtn");
        addCourseBtn.setOnAction(e -> handleAddCourse());

        Button addLocationBtn = createSidebarButton("\u2795 Location", null);
        addLocationBtn.setId("addLocationBtn");
        addLocationBtn.setOnAction(e -> handleAddLocation());

        Button addEventBtn = createSidebarButton("\u2795 Event", null);
        addEventBtn.setId("addEventBtn");
        addEventBtn.setOnAction(e -> handleAddEvent());

        Separator sep2 = new Separator();
        sep2.getStyleClass().add("sidebar-separator");

        Button refreshBtn = createSidebarButton("\u21BB Refresh All", null);
        refreshBtn.setId("refreshBtn");
        refreshBtn.setOnAction(e -> runDbTask(
                "RefreshAllData",
                "Refreshing all data...",
                () -> {
                    cache.refresh();
                    return null;
                },
                unused -> {
                    refreshAllViews();
                    ToastNotification.show("Data refreshed", ToastNotification.ToastType.INFO);
                }
        ));

        panel.getChildren().addAll(
                title, subtitle, new Separator(),
                deptLabel, departmentCombo,
                importBtn, exportScheduleBtn, exportConflictBtn,
                analyzeBtn, autoResolveBtn,
                sep1, manageLabel,
                manageDeptBtn, manageProfBtn, manageCourseBtn, manageLocationBtn,
                addDeptBtn, addProfBtn, addCourseBtn, addLocationBtn, addEventBtn,
                sep2, refreshBtn
        );
        return panel;
    }

    private Button createSidebarButton(String text, String extraStyleClass) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("sidebar-btn");
        if (extraStyleClass != null) {
            btn.getStyleClass().add(extraStyleClass);
        }
        return btn;
    }

    @SuppressWarnings("unchecked")
    private VBox buildScheduleView() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));

        // Columns with CONSTRAINED_RESIZE for auto-fill
        TableColumn<ScheduledEvent, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getCourseCode() != null
                        ? cd.getValue().getCourseCode() : ""));

        TableColumn<ScheduledEvent, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getEventType() != null
                        ? cd.getValue().getEventType().getDisplay() : ""));

        TableColumn<ScheduledEvent, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getLocationName() != null
                        ? cd.getValue().getLocationName() : "Online"));

        TableColumn<ScheduledEvent, String> profCol = new TableColumn<>("Professor");
        profCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getProfessorName() != null
                        ? cd.getValue().getProfessorName() : ""));

        TableColumn<ScheduledEvent, String> startCol = new TableColumn<>("Start (UTC)");
        startCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getStartEpoch() != null
                ? TimePolicy.formatEpoch(cd.getValue().getStartEpoch()) : ""));

        TableColumn<ScheduledEvent, String> endCol = new TableColumn<>("End (UTC)");
        endCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getEndEpoch() != null
                ? TimePolicy.formatEpoch(cd.getValue().getEndEpoch()) : ""));

        TableColumn<ScheduledEvent, String> durCol = new TableColumn<>("Duration");
        durCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getDurationMinutes() + " min"));

        eventTable.getColumns().addAll(courseCol, typeCol, locCol, profCol, startCol, endCol, durCol);
        eventTable.setId("eventTable");
        eventTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Empty state with illustration
        Label emptyLabel = new Label("\uD83D\uDCED  No events scheduled.\nImport a CSV or add events manually to get started!");
        emptyLabel.getStyleClass().add("empty-state-label");
        emptyLabel.setWrapText(true);
        eventTable.setPlaceholder(emptyLabel);

        eventTable.setRowFactory(tv -> {
            TableRow<ScheduledEvent> row = new TableRow<>();

            MenuItem editItem = new MenuItem("Edit Event");
            editItem.setOnAction(e -> handleEditEvent(row.getItem()));

            MenuItem deleteItem = new MenuItem("Delete Event");
            deleteItem.setOnAction(e -> handleDeleteEvent(row.getItem()));

            ContextMenu menu = new ContextMenu(editItem, deleteItem);
            row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> row.setContextMenu(isEmpty ? null : menu));
            row.setContextMenu(row.isEmpty() ? null : menu);

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleEditEvent(row.getItem());
                }
            });

            return row;
        });

        VBox.setVgrow(eventTable, Priority.ALWAYS);

        box.getChildren().add(eventTable);
        return box;
    }

    @SuppressWarnings("unchecked")
    private VBox buildConflictView() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));

        conflictSummaryLabel.setId("conflictSummaryLabel");

        // Conflict table
        TableColumn<ConflictResult, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getSeverity().name()));
        sevCol.setPrefWidth(130);
        sevCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    Rectangle indicator = new Rectangle(12, 12);
                    indicator.setArcWidth(3);
                    indicator.setArcHeight(3);
                    if ("HARD_OVERLAP".equals(item)) {
                        indicator.setFill(Color.web("#e74c3c"));
                    } else if ("PROFESSOR_OVERLAP".equals(item)) {
                        indicator.setFill(Color.web("#c0392b"));
                    } else {
                        indicator.setFill(Color.web("#f39c12"));
                    }
                    setGraphic(indicator);
                } else {
                    setGraphic(null);
                }
            }
        });

        TableColumn<ConflictResult, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getDescription()));

        TableColumn<ConflictResult, String> altCol = new TableColumn<>("Alternatives");
        altCol.setCellValueFactory(cd -> {
            List<Location> alts = cd.getValue().getAvailableAlternatives();
            if (alts == null || alts.isEmpty()) return new SimpleStringProperty("None");
            StringBuilder sb = new StringBuilder();
            for (Location l : alts) sb.append(l.getDisplayName()).append("; ");
            return new SimpleStringProperty(sb.toString());
        });

        conflictTable.getColumns().addAll(sevCol, descCol, altCol);
        conflictTable.setId("conflictTable");
        conflictTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Empty state
        Label emptyLabel = new Label("\u2705  No conflicts detected.\nClick 'Analyze Conflicts' to scan for scheduling issues.");
        emptyLabel.getStyleClass().add("empty-state-label");
        emptyLabel.setWrapText(true);
        conflictTable.setPlaceholder(emptyLabel);

        // Double-click to reassign
        conflictTable.setRowFactory(tv -> {
            TableRow<ConflictResult> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleReassign(row.getItem());
                }
            });
            return row;
        });

        VBox.setVgrow(conflictTable, Priority.ALWAYS);
        box.getChildren().addAll(conflictSummaryLabel, conflictTable);
        return box;
    }

    // ========== DATA ==========

    private void refreshData() {
        refreshData(null);
    }

    private void refreshData(Runnable onComplete) {
        Task<List<ScheduledEvent>> task = new Task<>() {
            @Override
            protected List<ScheduledEvent> call() throws Exception {
                List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
                cache.enrichAll(events);
                return events;
            }
        };
        task.setOnSucceeded(e -> {
            eventTable.setItems(FXCollections.observableArrayList(task.getValue()));
            statusLabel.setText("Loaded " + task.getValue().size() + " events.");
            if (onComplete != null) {
                onComplete.run();
            }
        });
        task.setOnFailed(e -> {
            statusLabel.setText("Error loading events: " + task.getException().getMessage());
            if (onComplete != null) {
                onComplete.run();
            }
        });
        Thread thread = new Thread(task, "RefreshData");
        thread.setDaemon(true);
        thread.start();
    }

    // ========== HANDLERS ==========

    private void handleImport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import CSV Schedule");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return;

        progressBar.setVisible(true);
        statusLabel.setText("Importing...");

        showBusy("Importing CSV...");

        Task<CsvImporter.ImportReport> task = new Task<>() {
            @Override
            protected CsvImporter.ImportReport call() throws Exception {
                CsvImporter importer = new CsvImporter(dbManager);
                CsvImporter.ImportReport report = importer.importFileWithReport(file, (current, total, msg) -> {
                    updateProgress(current, total);
                    updateMessage(msg);
                });
                cache.refresh();
                return report;
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            hideBusy();
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);

            CsvImporter.ImportReport report = task.getValue();
            refreshAllViews();

            int importedCount = report.getImportedCount();
            int failureCount = report.getFailureCount();
            statusLabel.setText("Imported " + importedCount + " events"
                    + (failureCount > 0 ? ", skipped " + failureCount + " rows." : "."));

            if (failureCount > 0) {
                ToastNotification.show(
                        "Imported " + importedCount + " events, skipped " + failureCount + " rows.",
                        ToastNotification.ToastType.WARNING
                );
                NotificationService.warning(
                        "CSV Import Completed With Skips",
                        importedCount + " imported, " + failureCount + " rows skipped."
                );
                showImportDiagnostics(report);
            } else {
                ToastNotification.show("Successfully imported " + importedCount + " events from CSV",
                        ToastNotification.ToastType.SUCCESS);
                NotificationService.info("CSV Import Complete", importedCount + " events imported successfully.");
            }
        });
        task.setOnFailed(e -> {
            hideBusy();
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);
            statusLabel.setText("Import failed: " + task.getException().getMessage());
            ToastNotification.show("Import failed: " + task.getException().getMessage(),
                    ToastNotification.ToastType.ERROR);
        });
        Thread thread = new Thread(task, "CSVImport");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleExportSchedule() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Schedule CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fc.setInitialFileName("schedule_export.csv");
        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        showBusy("Exporting schedule...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
                cache.enrichAll(events);
                new ReportExporter().exportScheduleCsv(file, events);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            hideBusy();
            statusLabel.setText("Schedule exported to " + file.getName());
            ToastNotification.show("Schedule exported to " + file.getName(),
                    ToastNotification.ToastType.SUCCESS);
        });
        task.setOnFailed(e -> {
            hideBusy();
            statusLabel.setText("Export failed: " + task.getException().getMessage());
            ToastNotification.show("Export failed", ToastNotification.ToastType.ERROR);
        });
        Thread thread = new Thread(task, "ExportSchedule");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleExportConflictReport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Conflict Report");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fc.setInitialFileName("conflict_report.txt");
        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        showBusy("Exporting conflict report...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<ConflictResult> conflicts = conflictEngine.analyzeAll();
                new ReportExporter().exportConflictReport(file, conflicts);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            hideBusy();
            statusLabel.setText("Conflict report exported to " + file.getName());
            ToastNotification.show("Conflict report exported", ToastNotification.ToastType.SUCCESS);
        });
        task.setOnFailed(e -> {
            hideBusy();
            statusLabel.setText("Export failed: " + task.getException().getMessage());
            ToastNotification.show("Export failed", ToastNotification.ToastType.ERROR);
        });
        Thread thread = new Thread(task, "ExportConflict");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleAnalyze() {
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("Analyzing conflicts...");
        showBusy("Analyzing conflicts...");

        Task<List<ConflictResult>> task = new Task<>() {
            @Override
            protected List<ConflictResult> call() throws Exception {
                return conflictEngine.analyzeAll();
            }
        };
        task.setOnSucceeded(e -> {
            hideBusy();
            progressBar.setVisible(false);
            progressBar.setProgress(0);
            List<ConflictResult> conflicts = task.getValue();
            conflictTable.setItems(FXCollections.observableArrayList(conflicts));

            long hard = conflicts.stream().filter(c -> c.getSeverity() == Severity.HARD_OVERLAP).count();
                long professorOverlap = conflicts.stream().filter(c -> c.getSeverity() == Severity.PROFESSOR_OVERLAP).count();
            long tight = conflicts.stream().filter(c -> c.getSeverity() == Severity.TIGHT_TRANSITION).count();
            conflictSummaryLabel.setText(String.format(
                    "Total: %d conflicts (%d room overlaps, %d professor overlaps, %d tight transitions)",
                    conflicts.size(), hard, professorOverlap, tight));

            statusLabel.setText("Analysis complete. " + conflicts.size() + " conflicts found.");

            if (conflicts.isEmpty()) {
                ToastNotification.show("No scheduling conflicts detected!", ToastNotification.ToastType.SUCCESS);
                NotificationService.info("Conflict Analysis", "No scheduling conflicts detected.");
            } else {
                ToastNotification.show(conflicts.size() + " conflicts detected! Double-click to reassign.",
                    (hard > 0 || professorOverlap > 0)
                        ? ToastNotification.ToastType.WARNING
                        : ToastNotification.ToastType.INFO);
                NotificationService.warning("Conflicts Detected",
                    conflicts.size() + " conflicts found (" + hard + " room overlaps, "
                        + professorOverlap + " professor overlaps).");
            }

            // Switch to conflicts tab
            tabPane.getSelectionModel().select(2);
        });
        task.setOnFailed(e -> {
            hideBusy();
            progressBar.setVisible(false);
            statusLabel.setText("Analysis failed: " + task.getException().getMessage());
            ToastNotification.show("Analysis failed: " + task.getException().getMessage(),
                    ToastNotification.ToastType.ERROR);
        });
        Thread thread = new Thread(task, "ConflictAnalysis");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleAutoResolve() {
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        statusLabel.setText("Auto-resolving conflicts...");
        showBusy("Auto-resolving conflicts...");

        Task<AutoResolver.ResolveResult> task = new Task<>() {
            @Override
            protected AutoResolver.ResolveResult call() throws Exception {
                AutoResolver resolver = new AutoResolver(dbManager, cache);
                return resolver.resolveAll((current, total, message) -> {
                    updateProgress(current, total);
                    updateMessage(message);
                });
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            hideBusy();
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);
            progressBar.setProgress(0);
            AutoResolver.ResolveResult result = task.getValue();

            statusLabel.setText(String.format("Auto-resolve: %d/%d conflicts resolved.",
                    result.getResolved(), result.getTotalConflicts()));

            if (result.getTotalConflicts() == 0) {
                ToastNotification.show("No resolvable conflicts found!",
                        ToastNotification.ToastType.INFO);
            } else if (result.getResolved() > 0) {
                ToastNotification.show(String.format("Resolved %d of %d conflicts!",
                        result.getResolved(), result.getTotalConflicts()),
                        ToastNotification.ToastType.SUCCESS);
                NotificationService.info("Auto-Resolve Complete",
                        result.getResolved() + " of " + result.getTotalConflicts() + " conflicts resolved.");

                // Show details
                StringBuilder details = new StringBuilder("Auto-Resolve Results:\n\n");
                for (String action : result.getActions()) {
                    details.append("  ").append(action).append("\n");
                }
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Auto-Resolve Results");
                info.setHeaderText(result.getResolved() + " of " + result.getTotalConflicts() + " conflicts resolved");
                TextArea ta = new TextArea(details.toString());
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setPrefSize(500, 300);
                info.getDialogPane().setContent(ta);
                info.showAndWait();
            } else {
                ToastNotification.show("Could not auto-resolve any conflicts – manual intervention needed.",
                        ToastNotification.ToastType.WARNING);
            }

            refreshAllViews();
            handleAnalyze();
        });
        task.setOnFailed(e -> {
            hideBusy();
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);
            statusLabel.setText("Auto-resolve failed: " + task.getException().getMessage());
            ToastNotification.show("Auto-resolve failed", ToastNotification.ToastType.ERROR);
        });
        Thread thread = new Thread(task, "AutoResolve");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleReassign(ConflictResult conflict) {
        if (conflict.getAvailableAlternatives() == null || conflict.getAvailableAlternatives().isEmpty()) {
            ToastNotification.show("No alternative rooms available for this time slot.",
                    ToastNotification.ToastType.WARNING);
            return;
        }

        ChoiceDialog<Location> dialog = new ChoiceDialog<>(
                conflict.getAvailableAlternatives().get(0),
                conflict.getAvailableAlternatives());
        dialog.setTitle("Reassign Room");
        dialog.setHeaderText(conflict.getDescription());
        dialog.setContentText("Select alternative room:");

        dialog.showAndWait().ifPresent(newLoc -> {
            ScheduledEvent eventB = conflict.getEventB();
            if (eventB == null || eventB.getEventId() == null) {
                ToastNotification.show("Selected conflict event is invalid.", ToastNotification.ToastType.ERROR);
                return;
            }

            runDbTask(
                    "ReassignEvent",
                    "Reassigning room...",
                    () -> {
                        ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
                        ScheduledEvent latest = dao.findById(eventB.getEventId());
                        if (latest == null) {
                            throw new SQLException("Event not found.");
                        }
                        latest.setLocId(newLoc.getLocId());
                        dao.update(latest);
                        cache.refresh();
                        return latest;
                    },
                    updated -> {
                        refreshAllViews();
                        handleAnalyze();
                        ToastNotification.show("Reassigned to " + newLoc.getDisplayName(),
                                ToastNotification.ToastType.SUCCESS);
                    }
            );
        });
    }

    // ========== ADD HANDLERS ==========

    private void handleAddDepartment() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Department");
        dialog.setHeaderText("Enter department name:");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) return;

            runDbTask(
                    "AddDepartment",
                    "Adding department...",
                    () -> {
                        Department created = new DepartmentDAO(dbManager).insert(new Department(null, trimmed));
                        cache.refresh();
                        return created;
                    },
                    created -> {
                        refreshAllViews();
                        ToastNotification.show("Department '" + created.getName() + "' added",
                                ToastNotification.ToastType.SUCCESS);
                    }
            );
        });
    }

    private void handleAddProfessor() {
        if (cache.getAllDepartments().isEmpty()) {
            ToastNotification.show("Add a department first", ToastNotification.ToastType.WARNING);
            return;
        }
        Dialog<Professor> dialog = new Dialog<>();
        dialog.setTitle("Add Professor");
        dialog.setHeaderText("Enter professor details:");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        TextField nameField = new TextField();
        nameField.setPromptText("Name");
        ComboBox<Department> deptBox = new ComboBox<>();
        deptBox.getItems().addAll(cache.getAllDepartments().values());
        if (!deptBox.getItems().isEmpty()) deptBox.getSelectionModel().selectFirst();

        grid.add(new Label("Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Department:"), 0, 1); grid.add(deptBox, 1, 1);
        dialog.getDialogPane().setContent(grid);

        ButtonType okBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == okBtn && !nameField.getText().trim().isEmpty() && deptBox.getValue() != null)
                return new Professor(null, nameField.getText().trim(), deptBox.getValue().getDeptId());
            return null;
        });
        dialog.showAndWait().ifPresent(prof -> {
            runDbTask(
                    "AddProfessor",
                    "Adding professor...",
                    () -> {
                        Professor created = new ProfessorDAO(dbManager).insert(prof);
                        cache.refresh();
                        return created;
                    },
                    created -> {
                        refreshAllViews();
                        ToastNotification.show("Professor '" + created.getName() + "' added",
                                ToastNotification.ToastType.SUCCESS);
                    }
            );
        });
    }

    private void handleAddCourse() {
        if (cache.getAllProfessors().isEmpty()) {
            ToastNotification.show("Add a professor first", ToastNotification.ToastType.WARNING);
            return;
        }
        Dialog<Course> dialog = new Dialog<>();
        dialog.setTitle("Add Course");
        dialog.setHeaderText("Enter course details:");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));

        TextField codeField = new TextField(); codeField.setPromptText("e.g. CS101");
        ComboBox<Professor> profBox = new ComboBox<>();
        profBox.getItems().addAll(cache.getAllProfessors().values());
        if (!profBox.getItems().isEmpty()) profBox.getSelectionModel().selectFirst();
        TextField enrollField = new TextField(); enrollField.setPromptText("Enrollment count (required)");

        grid.add(new Label("Code:"), 0, 0); grid.add(codeField, 1, 0);
        grid.add(new Label("Professor:"), 0, 1); grid.add(profBox, 1, 1);
        grid.add(new Label("Enrollment:"), 0, 2); grid.add(enrollField, 1, 2);
        dialog.getDialogPane().setContent(grid);

        ButtonType okBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);
        Node addBtn = dialog.getDialogPane().lookupButton(okBtn);
        addBtn.disableProperty().bind(Bindings.createBooleanBinding(
            () -> codeField.getText().trim().isEmpty()
                || profBox.getValue() == null
                || !isPositiveInteger(enrollField.getText().trim()),
            codeField.textProperty(),
            profBox.valueProperty(),
            enrollField.textProperty()
        ));

        dialog.setResultConverter(btn -> {
            if (btn == okBtn && !codeField.getText().trim().isEmpty() && profBox.getValue() != null) {
            Integer enroll = Integer.parseInt(enrollField.getText().trim());
                return new Course(null, codeField.getText().trim(), profBox.getValue().getProfId(), enroll);
            }
            return null;
        });
        dialog.showAndWait().ifPresent(course -> {
            runDbTask(
                    "AddCourse",
                    "Adding course...",
                    () -> {
                        Course created = new CourseDAO(dbManager).insert(course);
                        cache.refresh();
                        return created;
                    },
                    created -> {
                        refreshAllViews();
                        ToastNotification.show("Course '" + created.getCourseCode() + "' added",
                                ToastNotification.ToastType.SUCCESS);
                    }
            );
        });
    }

    private void handleAddLocation() {
        Dialog<Location> dialog = new Dialog<>();
        dialog.setTitle("Add Location");
        dialog.setHeaderText("Enter room details:");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));

        TextField buildingField = new TextField(); buildingField.setPromptText("Building name");
        TextField roomField = new TextField(); roomField.setPromptText("Room number");
        TextField capField = new TextField(); capField.setPromptText("Capacity (required)");
        CheckBox projBox = new CheckBox("Has Projector");

        grid.add(new Label("Building:"), 0, 0); grid.add(buildingField, 1, 0);
        grid.add(new Label("Room:"), 0, 1); grid.add(roomField, 1, 1);
        grid.add(new Label("Capacity:"), 0, 2); grid.add(capField, 1, 2);
        grid.add(projBox, 1, 3);
        dialog.getDialogPane().setContent(grid);

        ButtonType okBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);
        Node addBtn = dialog.getDialogPane().lookupButton(okBtn);
        addBtn.disableProperty().bind(Bindings.createBooleanBinding(
            () -> buildingField.getText().trim().isEmpty()
                || roomField.getText().trim().isEmpty()
                || !isPositiveInteger(capField.getText().trim()),
            buildingField.textProperty(),
            roomField.textProperty(),
            capField.textProperty()
        ));

        dialog.setResultConverter(btn -> {
            if (btn == okBtn && !buildingField.getText().trim().isEmpty() && !roomField.getText().trim().isEmpty()) {
            Integer cap = Integer.parseInt(capField.getText().trim());
                return new Location(null, buildingField.getText().trim(), roomField.getText().trim(),
                        cap, projBox.isSelected() ? 1 : 0);
            }
            return null;
        });
        dialog.showAndWait().ifPresent(loc -> {
            runDbTask(
                    "AddLocation",
                    "Adding location...",
                    () -> {
                        Location created = new LocationDAO(dbManager).insert(loc);
                        cache.refresh();
                        return created;
                    },
                    created -> {
                        refreshAllViews();
                        ToastNotification.show("Location '" + created.getDisplayName() + "' added",
                                ToastNotification.ToastType.SUCCESS);
                    }
            );
        });
    }

    private void handleAddEvent() {
        if (cache.getAllCourses().isEmpty()) {
            ToastNotification.show("Add a course first", ToastNotification.ToastType.WARNING);
            return;
        }
        Dialog<ScheduledEvent> dialog = new Dialog<>();
        dialog.setTitle("Add Event");
        dialog.setHeaderText("Schedule a new event:");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));

        ComboBox<Course> courseBox = new ComboBox<>();
        courseBox.getItems().addAll(cache.getAllCourses().values());
        if (!courseBox.getItems().isEmpty()) courseBox.getSelectionModel().selectFirst();

        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("Lecture", "Exam", "Office Hours"));
        typeBox.getSelectionModel().selectFirst();

        ComboBox<Location> locBox = new ComboBox<>();
        locBox.setPromptText("(Optional – leave for online)");
        locBox.getItems().addAll(cache.getAllLocations().values());

        TextField startField = new TextField(); startField.setPromptText("yyyy-MM-dd HH:mm (UTC)");
        TextField endField = new TextField(); endField.setPromptText("yyyy-MM-dd HH:mm (UTC)");

        grid.add(new Label("Course:"), 0, 0); grid.add(courseBox, 1, 0);
        grid.add(new Label("Type:"), 0, 1); grid.add(typeBox, 1, 1);
        grid.add(new Label("Location:"), 0, 2); grid.add(locBox, 1, 2);
        grid.add(new Label("Start:"), 0, 3); grid.add(startField, 1, 3);
        grid.add(new Label("End:"), 0, 4); grid.add(endField, 1, 4);
        dialog.getDialogPane().setContent(grid);

        ButtonType okBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);
        Node addBtn = dialog.getDialogPane().lookupButton(okBtn);
        addBtn.disableProperty().bind(Bindings.createBooleanBinding(
                () -> courseBox.getValue() == null
                        || typeBox.getValue() == null
                        || !isValidDateTimeRange(startField.getText(), endField.getText()),
                courseBox.valueProperty(),
                typeBox.valueProperty(),
                startField.textProperty(),
                endField.textProperty()
        ));

        dialog.setResultConverter(btn -> {
            if (btn != okBtn || courseBox.getValue() == null) return null;
            Long start = TimePolicy.parseDateTime(startField.getText().trim());
            Long end = TimePolicy.parseDateTime(endField.getText().trim());
            if (start == null || end == null || end <= start) return null;
            Integer locId = locBox.getValue() != null ? locBox.getValue().getLocId() : null;
            ScheduledEvent.EventType et = ScheduledEvent.EventType.fromString(typeBox.getValue());
            return new ScheduledEvent(null, courseBox.getValue().getCourseId(), locId, et, start, end);
        });
        dialog.showAndWait().ifPresent(event -> {
            runDbTask(
                    "AddEvent",
                    "Adding event...",
                    () -> {
                        ScheduledEvent created = new ScheduledEventDAO(dbManager).insert(event);
                        cache.refresh();
                        return created;
                    },
                    created -> {
                        refreshAllViews();
                        ToastNotification.show("Event added successfully",
                                ToastNotification.ToastType.SUCCESS);
                    }
            );
        });
    }

    private void handleEditEvent(ScheduledEvent selected) {
        if (selected == null || selected.getEventId() == null) {
            return;
        }
        if (cache.getAllCourses().isEmpty()) {
            ToastNotification.show("Add a course first", ToastNotification.ToastType.WARNING);
            return;
        }

        Dialog<ScheduledEvent> dialog = new Dialog<>();
        dialog.setTitle("Edit Event");
        dialog.setHeaderText("Update selected event:");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        ComboBox<Course> courseBox = new ComboBox<>();
        courseBox.getItems().addAll(cache.getAllCourses().values());
        if (selected.getCourseId() != null) {
            cache.getAllCourses().values().stream()
                    .filter(c -> Objects.equals(c.getCourseId(), selected.getCourseId()))
                    .findFirst()
                    .ifPresent(courseBox::setValue);
        }
        if (courseBox.getValue() == null && !courseBox.getItems().isEmpty()) {
            courseBox.getSelectionModel().selectFirst();
        }

        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("Lecture", "Exam", "Office Hours"));
        String selectedType = selected.getEventType() != null ? selected.getEventType().getDisplay() : null;
        if (selectedType != null) {
            typeBox.setValue(selectedType);
        }
        if (typeBox.getValue() == null) {
            typeBox.getSelectionModel().selectFirst();
        }

        ComboBox<Location> locBox = new ComboBox<>();
        locBox.setPromptText("(Optional – leave for online)");
        locBox.getItems().addAll(cache.getAllLocations().values());
        if (selected.getLocId() != null) {
            cache.getAllLocations().values().stream()
                    .filter(l -> Objects.equals(l.getLocId(), selected.getLocId()))
                    .findFirst()
                    .ifPresent(locBox::setValue);
        }

        TextField startField = new TextField(TimePolicy.formatEpoch(selected.getStartEpoch()));
        startField.setPromptText("yyyy-MM-dd HH:mm (UTC)");

        TextField endField = new TextField(TimePolicy.formatEpoch(selected.getEndEpoch()));
        endField.setPromptText("yyyy-MM-dd HH:mm (UTC)");

        grid.add(new Label("Course:"), 0, 0); grid.add(courseBox, 1, 0);
        grid.add(new Label("Type:"), 0, 1); grid.add(typeBox, 1, 1);
        grid.add(new Label("Location:"), 0, 2); grid.add(locBox, 1, 2);
        grid.add(new Label("Start:"), 0, 3); grid.add(startField, 1, 3);
        grid.add(new Label("End:"), 0, 4); grid.add(endField, 1, 4);
        dialog.getDialogPane().setContent(grid);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        Node saveButton = dialog.getDialogPane().lookupButton(saveBtn);
        saveButton.disableProperty().bind(Bindings.createBooleanBinding(
            () -> courseBox.getValue() == null
                || typeBox.getValue() == null
                || !isValidDateTimeRange(startField.getText(), endField.getText()),
            courseBox.valueProperty(),
            typeBox.valueProperty(),
            startField.textProperty(),
            endField.textProperty()
        ));

        dialog.setResultConverter(btn -> {
            if (btn != saveBtn || courseBox.getValue() == null) {
                return null;
            }
            Long start = TimePolicy.parseDateTime(startField.getText().trim());
            Long end = TimePolicy.parseDateTime(endField.getText().trim());
            if (start == null || end == null || end <= start) {
                return null;
            }
            Integer locId = locBox.getValue() != null ? locBox.getValue().getLocId() : null;
            ScheduledEvent.EventType et = ScheduledEvent.EventType.fromString(typeBox.getValue());
            ScheduledEvent updated = new ScheduledEvent(
                    selected.getEventId(),
                    courseBox.getValue().getCourseId(),
                    locId,
                    et,
                    start,
                    end
            );
            return updated;
        });

        dialog.showAndWait().ifPresent(updated -> runDbTask(
                "EditEvent",
                "Saving event changes...",
                () -> {
                    new ScheduledEventDAO(dbManager).update(updated);
                    cache.refresh();
                    return updated;
                },
                result -> {
                    refreshAllViews();
                    ToastNotification.show("Event updated successfully", ToastNotification.ToastType.SUCCESS);
                }
        ));
    }

    private void handleDeleteEvent(ScheduledEvent selected) {
        if (selected == null || selected.getEventId() == null) {
            return;
        }

        String label = selected.getCourseCode() != null
                ? selected.getCourseCode()
                : "Event #" + selected.getEventId();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Event");
        confirm.setHeaderText("Delete selected event?");
        confirm.setContentText(label + " will be permanently removed.");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) {
                return;
            }
            runDbTask(
                    "DeleteEvent",
                    "Deleting event...",
                    () -> {
                        new ScheduledEventDAO(dbManager).delete(selected.getEventId());
                        cache.refresh();
                        return selected.getEventId();
                    },
                    deletedId -> {
                        refreshAllViews();
                        ToastNotification.show("Event deleted", ToastNotification.ToastType.SUCCESS);
                    }
            );
        });
    }

    private void initializeBusyOverlay() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(46, 46);

        busyOverlayLabel.getStyleClass().add("busy-overlay-label");

        VBox content = new VBox(12, indicator, busyOverlayLabel);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("busy-overlay-card");

        busyOverlay.getChildren().setAll(content);
        busyOverlay.getStyleClass().add("busy-overlay");
        busyOverlay.setVisible(false);
        busyOverlay.setManaged(false);
        busyOverlay.setMouseTransparent(true);

        rootStack.getChildren().add(busyOverlay);
        StackPane.setAlignment(busyOverlay, Pos.CENTER);
    }

    private void showBusy(String message) {
        Platform.runLater(() -> {
            busyDepth++;
            busyOverlayLabel.setText(message != null ? message : "Loading...");
            busyOverlay.setVisible(true);
            busyOverlay.toFront();
        });
    }

    private void hideBusy() {
        Platform.runLater(() -> {
            busyDepth = Math.max(0, busyDepth - 1);
            if (busyDepth == 0) {
                busyOverlay.setVisible(false);
            }
        });
    }

    private void refreshAllViews() {
        refreshData();
        departmentCombo.getItems().setAll(cache.getAllDepartments().values());
        homePage.refresh();
        calendarView.refresh();
        analyticsView.refresh();
    }

    private <T> void runDbTask(
            String threadName,
            String loadingMessage,
            Callable<T> work,
            Consumer<T> onSuccess
    ) {
        showBusy(loadingMessage);

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };

        task.setOnSucceeded(e -> {
            hideBusy();
            if (onSuccess != null) {
                onSuccess.accept(task.getValue());
            }
        });

        task.setOnFailed(e -> {
            hideBusy();
            String message = task.getException() != null && task.getException().getMessage() != null
                    ? task.getException().getMessage()
                    : "Unknown error";
            statusLabel.setText("Operation failed: " + message);
            ToastNotification.show("Operation failed: " + message, ToastNotification.ToastType.ERROR);
        });

        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void showImportDiagnostics(CsvImporter.ImportReport report) {
        if (report == null || report.getFailures().isEmpty()) {
            return;
        }

        StringBuilder details = new StringBuilder();
        details.append("Imported ")
                .append(report.getImportedCount())
                .append(" of ")
                .append(report.getTotalRows())
                .append(" rows.\n")
                .append("Skipped rows:\n\n");

        for (CsvImporter.ImportFailure failure : report.getFailures()) {
            details.append("Row ")
                    .append(failure.getRowNumber())
                    .append(": ")
                    .append(failure.getReason())
                    .append("\n");
        }

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("CSV Import Diagnostics");
        alert.setHeaderText(report.getFailureCount() + " row(s) were skipped");

        TextArea area = new TextArea(details.toString());
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefSize(600, 340);
        alert.getDialogPane().setContent(area);
        alert.showAndWait();
    }
}
