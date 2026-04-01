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

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Main dashboard UI controller for FacultySync.
 * Uses a BorderPane layout: left sidebar, center with Home/Schedule/Conflicts/Calendar/Analytics tabs.
 * Integrates toast notifications, auto-resolve, drag-drop calendar, and analytics charts.
 */
public class DashboardController {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

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
            if (newTab == homeTab) homePage.refresh();
            else if (newTab == calendarTab) calendarView.refresh();
            else if (newTab == analyticsTab) analyticsView.refresh();
            else if (newTab == scheduleTab) refreshData();
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
        refreshBtn.setOnAction(e -> {
            try { cache.refresh(); } catch (SQLException ignored) {}
            refreshData();
            ToastNotification.show("Data refreshed", ToastNotification.ToastType.INFO);
        });

        panel.getChildren().addAll(
                title, subtitle, new Separator(),
                deptLabel, departmentCombo,
                importBtn, exportScheduleBtn, exportConflictBtn,
                analyzeBtn, autoResolveBtn,
                sep1, manageLabel,
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

        TableColumn<ScheduledEvent, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getStartEpoch() != null
                        ? DATE_FMT.format(new Date(cd.getValue().getStartEpoch())) : ""));

        TableColumn<ScheduledEvent, String> endCol = new TableColumn<>("End");
        endCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getEndEpoch() != null
                        ? DATE_FMT.format(new Date(cd.getValue().getEndEpoch())) : ""));

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
        });
        task.setOnFailed(e -> statusLabel.setText("Error loading events: " + task.getException().getMessage()));
        new Thread(task, "RefreshData").start();
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

        Task<List<ScheduledEvent>> task = new Task<>() {
            @Override
            protected List<ScheduledEvent> call() throws Exception {
                CsvImporter importer = new CsvImporter(dbManager);
                return importer.importFile(file, (current, total, msg) -> {
                    updateProgress(current, total);
                    updateMessage(msg);
                });
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);
            try { cache.refresh(); } catch (SQLException ignored) {}
            refreshData();
            int count = task.getValue().size();
            statusLabel.setText("Imported " + count + " events.");
            ToastNotification.show("Successfully imported " + count + " events from CSV",
                    ToastNotification.ToastType.SUCCESS);
            NotificationService.info("CSV Import Complete", count + " events imported successfully.");
        });
        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);
            statusLabel.setText("Import failed: " + task.getException().getMessage());
            ToastNotification.show("Import failed: " + task.getException().getMessage(),
                    ToastNotification.ToastType.ERROR);
        });
        new Thread(task, "CSVImport").start();
    }

    private void handleExportSchedule() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Schedule CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fc.setInitialFileName("schedule_export.csv");
        File file = fc.showSaveDialog(stage);
        if (file == null) return;

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
            statusLabel.setText("Schedule exported to " + file.getName());
            ToastNotification.show("Schedule exported to " + file.getName(),
                    ToastNotification.ToastType.SUCCESS);
        });
        task.setOnFailed(e -> {
            statusLabel.setText("Export failed: " + task.getException().getMessage());
            ToastNotification.show("Export failed", ToastNotification.ToastType.ERROR);
        });
        new Thread(task, "ExportSchedule").start();
    }

    private void handleExportConflictReport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Conflict Report");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fc.setInitialFileName("conflict_report.txt");
        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<ConflictResult> conflicts = conflictEngine.analyzeAll();
                new ReportExporter().exportConflictReport(file, conflicts);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            statusLabel.setText("Conflict report exported to " + file.getName());
            ToastNotification.show("Conflict report exported", ToastNotification.ToastType.SUCCESS);
        });
        task.setOnFailed(e -> {
            statusLabel.setText("Export failed: " + task.getException().getMessage());
            ToastNotification.show("Export failed", ToastNotification.ToastType.ERROR);
        });
        new Thread(task, "ExportConflict").start();
    }

    private void handleAnalyze() {
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("Analyzing conflicts...");

        Task<List<ConflictResult>> task = new Task<>() {
            @Override
            protected List<ConflictResult> call() throws Exception {
                cache.refresh();
                return conflictEngine.analyzeAll();
            }
        };
        task.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            progressBar.setProgress(0);
            List<ConflictResult> conflicts = task.getValue();
            conflictTable.setItems(FXCollections.observableArrayList(conflicts));

            long hard = conflicts.stream().filter(c -> c.getSeverity() == Severity.HARD_OVERLAP).count();
            long tight = conflicts.stream().filter(c -> c.getSeverity() == Severity.TIGHT_TRANSITION).count();
            conflictSummaryLabel.setText(String.format(
                    "Total: %d conflicts (%d hard overlaps, %d tight transitions)",
                    conflicts.size(), hard, tight));

            statusLabel.setText("Analysis complete. " + conflicts.size() + " conflicts found.");

            if (conflicts.isEmpty()) {
                ToastNotification.show("No scheduling conflicts detected!", ToastNotification.ToastType.SUCCESS);
                NotificationService.info("Conflict Analysis", "No scheduling conflicts detected.");
            } else {
                ToastNotification.show(conflicts.size() + " conflicts detected! Double-click to reassign.",
                        hard > 0 ? ToastNotification.ToastType.WARNING : ToastNotification.ToastType.INFO);
                NotificationService.warning("Conflicts Detected",
                        conflicts.size() + " conflicts found (" + hard + " hard overlaps).");
            }

            // Switch to conflicts tab
            tabPane.getSelectionModel().select(2);
        });
        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            statusLabel.setText("Analysis failed: " + task.getException().getMessage());
            ToastNotification.show("Analysis failed: " + task.getException().getMessage(),
                    ToastNotification.ToastType.ERROR);
        });
        new Thread(task, "ConflictAnalysis").start();
    }

    private void handleAutoResolve() {
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("Auto-resolving conflicts...");

        Task<AutoResolver.ResolveResult> task = new Task<>() {
            @Override
            protected AutoResolver.ResolveResult call() throws Exception {
                AutoResolver resolver = new AutoResolver(dbManager, cache);
                return resolver.resolveAll();
            }
        };
        task.setOnSucceeded(e -> {
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

            refreshData();
            handleAnalyze();
        });
        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            statusLabel.setText("Auto-resolve failed: " + task.getException().getMessage());
            ToastNotification.show("Auto-resolve failed", ToastNotification.ToastType.ERROR);
        });
        new Thread(task, "AutoResolve").start();
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
            eventB.setLocId(newLoc.getLocId());
            try {
                new ScheduledEventDAO(dbManager).update(eventB);
                cache.refresh();
                refreshData();
                handleAnalyze();
                ToastNotification.show("Reassigned to " + newLoc.getDisplayName(),
                        ToastNotification.ToastType.SUCCESS);
            } catch (SQLException ex) {
                ToastNotification.show("Failed to reassign: " + ex.getMessage(),
                        ToastNotification.ToastType.ERROR);
            }
        });
    }

    // ========== ADD HANDLERS ==========

    private void handleAddDepartment() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Department");
        dialog.setHeaderText("Enter department name:");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) return;
            try {
                new DepartmentDAO(dbManager).insert(new Department(null, name.trim()));
                cache.refresh();
                departmentCombo.getItems().setAll(cache.getAllDepartments().values());
                ToastNotification.show("Department '" + name.trim() + "' added",
                        ToastNotification.ToastType.SUCCESS);
            } catch (SQLException ex) {
                ToastNotification.show("Error: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            }
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
            try {
                new ProfessorDAO(dbManager).insert(prof);
                cache.refresh();
                ToastNotification.show("Professor '" + prof.getName() + "' added",
                        ToastNotification.ToastType.SUCCESS);
            } catch (SQLException ex) {
                ToastNotification.show("Error: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            }
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
        TextField enrollField = new TextField(); enrollField.setPromptText("Enrollment count");

        grid.add(new Label("Code:"), 0, 0); grid.add(codeField, 1, 0);
        grid.add(new Label("Professor:"), 0, 1); grid.add(profBox, 1, 1);
        grid.add(new Label("Enrollment:"), 0, 2); grid.add(enrollField, 1, 2);
        dialog.getDialogPane().setContent(grid);

        ButtonType okBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == okBtn && !codeField.getText().trim().isEmpty() && profBox.getValue() != null) {
                Integer enroll = null;
                try { enroll = Integer.parseInt(enrollField.getText().trim()); } catch (NumberFormatException ignored) {}
                return new Course(null, codeField.getText().trim(), profBox.getValue().getProfId(), enroll);
            }
            return null;
        });
        dialog.showAndWait().ifPresent(course -> {
            try {
                new CourseDAO(dbManager).insert(course);
                cache.refresh();
                ToastNotification.show("Course '" + course.getCourseCode() + "' added",
                        ToastNotification.ToastType.SUCCESS);
            } catch (SQLException ex) {
                ToastNotification.show("Error: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            }
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
        TextField capField = new TextField(); capField.setPromptText("Capacity");
        CheckBox projBox = new CheckBox("Has Projector");

        grid.add(new Label("Building:"), 0, 0); grid.add(buildingField, 1, 0);
        grid.add(new Label("Room:"), 0, 1); grid.add(roomField, 1, 1);
        grid.add(new Label("Capacity:"), 0, 2); grid.add(capField, 1, 2);
        grid.add(projBox, 1, 3);
        dialog.getDialogPane().setContent(grid);

        ButtonType okBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == okBtn && !buildingField.getText().trim().isEmpty() && !roomField.getText().trim().isEmpty()) {
                Integer cap = null;
                try { cap = Integer.parseInt(capField.getText().trim()); } catch (NumberFormatException ignored) {}
                return new Location(null, buildingField.getText().trim(), roomField.getText().trim(),
                        cap, projBox.isSelected() ? 1 : 0);
            }
            return null;
        });
        dialog.showAndWait().ifPresent(loc -> {
            try {
                new LocationDAO(dbManager).insert(loc);
                cache.refresh();
                ToastNotification.show("Location '" + loc.getDisplayName() + "' added",
                        ToastNotification.ToastType.SUCCESS);
            } catch (SQLException ex) {
                ToastNotification.show("Error: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            }
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

        TextField startField = new TextField(); startField.setPromptText("yyyy-MM-dd HH:mm");
        TextField endField = new TextField(); endField.setPromptText("yyyy-MM-dd HH:mm");

        grid.add(new Label("Course:"), 0, 0); grid.add(courseBox, 1, 0);
        grid.add(new Label("Type:"), 0, 1); grid.add(typeBox, 1, 1);
        grid.add(new Label("Location:"), 0, 2); grid.add(locBox, 1, 2);
        grid.add(new Label("Start:"), 0, 3); grid.add(startField, 1, 3);
        grid.add(new Label("End:"), 0, 4); grid.add(endField, 1, 4);
        dialog.getDialogPane().setContent(grid);

        ButtonType okBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != okBtn || courseBox.getValue() == null) return null;
            try {
                Long start = DATE_FMT.parse(startField.getText().trim()).getTime();
                Long end = DATE_FMT.parse(endField.getText().trim()).getTime();
                if (end <= start) return null;
                Integer locId = locBox.getValue() != null ? locBox.getValue().getLocId() : null;
                ScheduledEvent.EventType et = ScheduledEvent.EventType.fromString(typeBox.getValue());
                return new ScheduledEvent(null, courseBox.getValue().getCourseId(), locId, et, start, end);
            } catch (Exception ex) { return null; }
        });
        dialog.showAndWait().ifPresent(event -> {
            try {
                new ScheduledEventDAO(dbManager).insert(event);
                cache.refresh();
                refreshData();
                ToastNotification.show("Event added successfully",
                        ToastNotification.ToastType.SUCCESS);
            } catch (SQLException ex) {
                ToastNotification.show("Error: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            }
        });
    }
}
