package edu.facultysync.ui;

import edu.facultysync.db.*;
import edu.facultysync.io.CsvImporter;
import edu.facultysync.io.ReportExporter;
import edu.facultysync.model.*;
import edu.facultysync.model.ConflictResult.Severity;
import edu.facultysync.service.ConflictEngine;
import edu.facultysync.service.DataCache;

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
 * Uses a BorderPane layout: left control panel, center schedule/conflict view.
 */
public class DashboardController {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final DatabaseManager dbManager;
    private final Stage stage;
    private final DataCache cache;
    private final ConflictEngine conflictEngine;
    private final BorderPane root;

    // UI components
    private final ComboBox<Department> departmentCombo = new ComboBox<>();
    private final TableView<ScheduledEvent> eventTable = new TableView<>();
    private final TableView<ConflictResult> conflictTable = new TableView<>();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("Ready");
    private final Label conflictSummaryLabel = new Label();

    public DashboardController(DatabaseManager dbManager, Stage stage) throws SQLException {
        this.dbManager = dbManager;
        this.stage = stage;
        this.cache = new DataCache(dbManager);
        this.conflictEngine = new ConflictEngine(dbManager, cache);

        cache.refresh();
        root = buildLayout();
        refreshData();
    }

    public BorderPane getRoot() { return root; }

    // ========== LAYOUT ==========

    private BorderPane buildLayout() {
        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("root-pane");

        // --- Left control panel ---
        VBox leftPanel = buildLeftPanel();
        leftPanel.getStyleClass().add("left-panel");
        pane.setLeft(leftPanel);

        // --- Center: tabs for schedule and conflicts ---
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab scheduleTab = new Tab("Schedule", buildScheduleView());
        scheduleTab.setId("scheduleTab");
        Tab conflictTab = new Tab("Conflicts", buildConflictView());
        conflictTab.setId("conflictTab");

        tabPane.getTabs().addAll(scheduleTab, conflictTab);
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
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(15));
        panel.setPrefWidth(250);

        Label title = new Label("FacultySync");
        title.getStyleClass().add("app-title");

        // Department filter
        Label deptLabel = new Label("Department:");
        departmentCombo.setId("departmentCombo");
        departmentCombo.setMaxWidth(Double.MAX_VALUE);
        departmentCombo.setPromptText("All Departments");
        departmentCombo.getItems().addAll(cache.getAllDepartments().values());
        departmentCombo.setOnAction(e -> refreshData());

        // Buttons
        Button importBtn = new Button("Import CSV");
        importBtn.setId("importBtn");
        importBtn.setMaxWidth(Double.MAX_VALUE);
        importBtn.getStyleClass().add("primary-btn");
        importBtn.setOnAction(e -> handleImport());

        Button exportScheduleBtn = new Button("Export Schedule");
        exportScheduleBtn.setId("exportScheduleBtn");
        exportScheduleBtn.setMaxWidth(Double.MAX_VALUE);
        exportScheduleBtn.setOnAction(e -> handleExportSchedule());

        Button exportConflictBtn = new Button("Export Conflict Report");
        exportConflictBtn.setId("exportConflictBtn");
        exportConflictBtn.setMaxWidth(Double.MAX_VALUE);
        exportConflictBtn.setOnAction(e -> handleExportConflictReport());

        Button analyzeBtn = new Button("Analyze Conflicts");
        analyzeBtn.setId("analyzeBtn");
        analyzeBtn.setMaxWidth(Double.MAX_VALUE);
        analyzeBtn.getStyleClass().add("warning-btn");
        analyzeBtn.setOnAction(e -> handleAnalyze());

        Separator sep1 = new Separator();

        Button addDeptBtn = new Button("Add Department");
        addDeptBtn.setId("addDeptBtn");
        addDeptBtn.setMaxWidth(Double.MAX_VALUE);
        addDeptBtn.setOnAction(e -> handleAddDepartment());

        Button addProfBtn = new Button("Add Professor");
        addProfBtn.setId("addProfBtn");
        addProfBtn.setMaxWidth(Double.MAX_VALUE);
        addProfBtn.setOnAction(e -> handleAddProfessor());

        Button addCourseBtn = new Button("Add Course");
        addCourseBtn.setId("addCourseBtn");
        addCourseBtn.setMaxWidth(Double.MAX_VALUE);
        addCourseBtn.setOnAction(e -> handleAddCourse());

        Button addLocationBtn = new Button("Add Location");
        addLocationBtn.setId("addLocationBtn");
        addLocationBtn.setMaxWidth(Double.MAX_VALUE);
        addLocationBtn.setOnAction(e -> handleAddLocation());

        Button addEventBtn = new Button("Add Event");
        addEventBtn.setId("addEventBtn");
        addEventBtn.setMaxWidth(Double.MAX_VALUE);
        addEventBtn.setOnAction(e -> handleAddEvent());

        Separator sep2 = new Separator();

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setId("refreshBtn");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setOnAction(e -> {
            try { cache.refresh(); } catch (SQLException ignored) {}
            refreshData();
        });

        panel.getChildren().addAll(
                title, new Separator(),
                deptLabel, departmentCombo,
                importBtn, exportScheduleBtn, exportConflictBtn, analyzeBtn,
                sep1,
                addDeptBtn, addProfBtn, addCourseBtn, addLocationBtn, addEventBtn,
                sep2,
                refreshBtn
        );
        return panel;
    }

    @SuppressWarnings("unchecked")
    private VBox buildScheduleView() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));

        // Columns
        TableColumn<ScheduledEvent, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getCourseCode() != null
                        ? cd.getValue().getCourseCode() : ""));
        courseCol.setPrefWidth(100);

        TableColumn<ScheduledEvent, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getEventType() != null
                        ? cd.getValue().getEventType().getDisplay() : ""));
        typeCol.setPrefWidth(100);

        TableColumn<ScheduledEvent, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getLocationName() != null
                        ? cd.getValue().getLocationName() : "Online"));
        locCol.setPrefWidth(140);

        TableColumn<ScheduledEvent, String> profCol = new TableColumn<>("Professor");
        profCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getProfessorName() != null
                        ? cd.getValue().getProfessorName() : ""));
        profCol.setPrefWidth(140);

        TableColumn<ScheduledEvent, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getStartEpoch() != null
                        ? DATE_FMT.format(new Date(cd.getValue().getStartEpoch())) : ""));
        startCol.setPrefWidth(140);

        TableColumn<ScheduledEvent, String> endCol = new TableColumn<>("End");
        endCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getEndEpoch() != null
                        ? DATE_FMT.format(new Date(cd.getValue().getEndEpoch())) : ""));
        endCol.setPrefWidth(140);

        TableColumn<ScheduledEvent, String> durCol = new TableColumn<>("Duration");
        durCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getDurationMinutes() + " min"));
        durCol.setPrefWidth(80);

        eventTable.getColumns().addAll(courseCol, typeCol, locCol, profCol, startCol, endCol, durCol);
        eventTable.setId("eventTable");
        eventTable.setPlaceholder(new Label("No events scheduled. Import a CSV or add events manually."));
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
        descCol.setPrefWidth(500);

        TableColumn<ConflictResult, String> altCol = new TableColumn<>("Alternatives");
        altCol.setCellValueFactory(cd -> {
            List<Location> alts = cd.getValue().getAvailableAlternatives();
            if (alts == null || alts.isEmpty()) return new SimpleStringProperty("None");
            StringBuilder sb = new StringBuilder();
            for (Location l : alts) sb.append(l.getDisplayName()).append("; ");
            return new SimpleStringProperty(sb.toString());
        });
        altCol.setPrefWidth(200);

        conflictTable.getColumns().addAll(sevCol, descCol, altCol);
        conflictTable.setId("conflictTable");
        conflictTable.setPlaceholder(new Label("Click 'Analyze Conflicts' to scan for scheduling issues."));

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
            statusLabel.setText("Imported " + task.getValue().size() + " events.");
        });
        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setVisible(false);
            statusLabel.setText("Import failed: " + task.getException().getMessage());
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
        task.setOnSucceeded(e -> statusLabel.setText("Schedule exported to " + file.getName()));
        task.setOnFailed(e -> statusLabel.setText("Export failed: " + task.getException().getMessage()));
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
        task.setOnSucceeded(e -> statusLabel.setText("Conflict report exported to " + file.getName()));
        task.setOnFailed(e -> statusLabel.setText("Export failed: " + task.getException().getMessage()));
        new Thread(task, "ExportConflict").start();
    }

    private void handleAnalyze() {
        progressBar.setVisible(true);
        progressBar.setProgress(-1); // indeterminate
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
        });
        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            statusLabel.setText("Analysis failed: " + task.getException().getMessage());
        });
        new Thread(task, "ConflictAnalysis").start();
    }

    private void handleReassign(ConflictResult conflict) {
        if (conflict.getAvailableAlternatives() == null || conflict.getAvailableAlternatives().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Alternatives",
                    "No alternative rooms are available for this time slot.");
            return;
        }

        ChoiceDialog<Location> dialog = new ChoiceDialog<>(
                conflict.getAvailableAlternatives().get(0),
                conflict.getAvailableAlternatives());
        dialog.setTitle("Reassign Room");
        dialog.setHeaderText(conflict.getDescription());
        dialog.setContentText("Select alternative room:");

        dialog.showAndWait().ifPresent(newLoc -> {
            // Reassign event B to the new location
            ScheduledEvent eventB = conflict.getEventB();
            eventB.setLocId(newLoc.getLocId());
            try {
                new ScheduledEventDAO(dbManager).update(eventB);
                cache.refresh();
                refreshData();
                handleAnalyze();
                statusLabel.setText("Reassigned to " + newLoc.getDisplayName());
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to reassign: " + ex.getMessage());
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
                statusLabel.setText("Department '" + name.trim() + "' added.");
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });
    }

    private void handleAddProfessor() {
        if (cache.getAllDepartments().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Departments", "Add a department first.");
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
                statusLabel.setText("Professor '" + prof.getName() + "' added.");
            } catch (SQLException ex) { showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
        });
    }

    private void handleAddCourse() {
        if (cache.getAllProfessors().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Professors", "Add a professor first.");
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
                statusLabel.setText("Course '" + course.getCourseCode() + "' added.");
            } catch (SQLException ex) { showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
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
                statusLabel.setText("Location '" + loc.getDisplayName() + "' added.");
            } catch (SQLException ex) { showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
        });
    }

    private void handleAddEvent() {
        if (cache.getAllCourses().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Courses", "Add a course first.");
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
                statusLabel.setText("Event added.");
            } catch (SQLException ex) { showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
