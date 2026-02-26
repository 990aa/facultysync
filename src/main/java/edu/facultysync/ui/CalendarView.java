package edu.facultysync.ui;

import edu.facultysync.db.DatabaseManager;
import edu.facultysync.db.LocationDAO;
import edu.facultysync.db.ScheduledEventDAO;
import edu.facultysync.model.Location;
import edu.facultysync.model.ScheduledEvent;
import edu.facultysync.service.ConflictEngine;
import edu.facultysync.service.DataCache;
import edu.facultysync.model.ConflictResult;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Interactive visual calendar grid (Google Calendar-like weekly view).
 * Events are displayed as colored blocks positioned by day and hour.
 * Supports drag-and-drop for room reassignment.
 */
public class CalendarView {

    private static final SimpleDateFormat DAY_FMT = new SimpleDateFormat("EEE MM/dd");
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm");
    private static final int START_HOUR = 7;
    private static final int END_HOUR = 22;
    private static final int HOUR_HEIGHT = 60;
    private static final String[] DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private final DatabaseManager dbManager;
    private final DataCache cache;
    private final ConflictEngine conflictEngine;
    private final VBox root;
    private GridPane calendarGrid;
    private Calendar currentWeekStart;
    private Label weekLabel;
    private Set<Integer> conflictEventIds = new HashSet<>();

    public CalendarView(DatabaseManager dbManager, DataCache cache) {
        this.dbManager = dbManager;
        this.cache = cache;
        this.conflictEngine = new ConflictEngine(dbManager, cache);

        currentWeekStart = Calendar.getInstance();
        currentWeekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        currentWeekStart.set(Calendar.HOUR_OF_DAY, 0);
        currentWeekStart.set(Calendar.MINUTE, 0);
        currentWeekStart.set(Calendar.SECOND, 0);
        currentWeekStart.set(Calendar.MILLISECOND, 0);

        root = buildView();
        refresh();
    }

    public VBox getView() {
        return root;
    }

    public void refresh() {
        buildCalendarContent();
    }

    private VBox buildView() {
        VBox container = new VBox(0);
        container.getStyleClass().add("calendar-container");

        // Navigation bar
        HBox navBar = buildNavBar();

        // Calendar grid wrapped in scroll
        calendarGrid = new GridPane();
        calendarGrid.getStyleClass().add("calendar-grid");

        ScrollPane scrollPane = new ScrollPane(calendarGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("calendar-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        container.getChildren().addAll(navBar, scrollPane);
        return container;
    }

    private HBox buildNavBar() {
        Button prevBtn = new Button("\u25C0 Prev");
        prevBtn.getStyleClass().add("calendar-nav-btn");
        prevBtn.setOnAction(e -> {
            currentWeekStart.add(Calendar.WEEK_OF_YEAR, -1);
            refresh();
        });

        Button todayBtn = new Button("Today");
        todayBtn.getStyleClass().addAll("calendar-nav-btn", "primary-btn");
        todayBtn.setOnAction(e -> {
            currentWeekStart = Calendar.getInstance();
            currentWeekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            currentWeekStart.set(Calendar.HOUR_OF_DAY, 0);
            currentWeekStart.set(Calendar.MINUTE, 0);
            currentWeekStart.set(Calendar.SECOND, 0);
            currentWeekStart.set(Calendar.MILLISECOND, 0);
            refresh();
        });

        Button nextBtn = new Button("Next \u25B6");
        nextBtn.getStyleClass().add("calendar-nav-btn");
        nextBtn.setOnAction(e -> {
            currentWeekStart.add(Calendar.WEEK_OF_YEAR, 1);
            refresh();
        });

        weekLabel = new Label();
        weekLabel.getStyleClass().add("calendar-week-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Legend
        HBox legend = new HBox(12);
        legend.setAlignment(Pos.CENTER_RIGHT);
        legend.getChildren().addAll(
                legendItem("#27ae60", "Lecture"),
                legendItem("#2980b9", "Exam"),
                legendItem("#8e44ad", "Office Hrs"),
                legendItem("#e74c3c", "Conflict")
        );

        HBox navBar = new HBox(10, prevBtn, todayBtn, nextBtn, weekLabel, spacer, legend);
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.setPadding(new Insets(10, 16, 10, 16));
        navBar.getStyleClass().add("calendar-nav");
        return navBar;
    }

    private HBox legendItem(String color, String label) {
        Region dot = new Region();
        dot.setPrefSize(12, 12);
        dot.setMinSize(12, 12);
        dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        HBox item = new HBox(4, dot, lbl);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private void buildCalendarContent() {
        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        calendarGrid.getRowConstraints().clear();

        // Determine conflict events
        conflictEventIds.clear();
        try {
            List<ConflictResult> conflicts = conflictEngine.analyzeAll();
            for (ConflictResult cr : conflicts) {
                if (cr.getEventA() != null && cr.getEventA().getEventId() != null)
                    conflictEventIds.add(cr.getEventA().getEventId());
                if (cr.getEventB() != null && cr.getEventB().getEventId() != null)
                    conflictEventIds.add(cr.getEventB().getEventId());
            }
        } catch (SQLException ignored) {}

        // Update week label
        Calendar weekEnd = (Calendar) currentWeekStart.clone();
        weekEnd.add(Calendar.DAY_OF_YEAR, 6);
        SimpleDateFormat rangeFmt = new SimpleDateFormat("MMM d, yyyy");
        weekLabel.setText(rangeFmt.format(currentWeekStart.getTime()) + " — " + rangeFmt.format(weekEnd.getTime()));

        // Column constraints: time column + 7 day columns
        ColumnConstraints timeCol = new ColumnConstraints(70);
        timeCol.setMinWidth(70);
        calendarGrid.getColumnConstraints().add(timeCol);
        for (int d = 0; d < 7; d++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setHgrow(Priority.ALWAYS);
            dayCol.setMinWidth(100);
            calendarGrid.getColumnConstraints().add(dayCol);
        }

        // Header row
        RowConstraints headerRow = new RowConstraints(40);
        calendarGrid.getRowConstraints().add(headerRow);

        Label cornerLabel = new Label("");
        cornerLabel.getStyleClass().add("calendar-corner");
        calendarGrid.add(cornerLabel, 0, 0);

        Calendar dayCal = (Calendar) currentWeekStart.clone();
        for (int d = 0; d < 7; d++) {
            Label dayLabel = new Label(DAY_FMT.format(dayCal.getTime()));
            dayLabel.getStyleClass().add("calendar-day-header");
            dayLabel.setMaxWidth(Double.MAX_VALUE);
            dayLabel.setAlignment(Pos.CENTER);
            calendarGrid.add(dayLabel, d + 1, 0);
            dayCal.add(Calendar.DAY_OF_YEAR, 1);
        }

        // Hour rows
        for (int h = START_HOUR; h < END_HOUR; h++) {
            int rowIndex = h - START_HOUR + 1;
            RowConstraints rc = new RowConstraints(HOUR_HEIGHT);
            calendarGrid.getRowConstraints().add(rc);

            Label timeLabel = new Label(String.format("%02d:00", h));
            timeLabel.getStyleClass().add("calendar-time-label");
            timeLabel.setMaxHeight(Double.MAX_VALUE);
            timeLabel.setAlignment(Pos.TOP_RIGHT);
            timeLabel.setPadding(new Insets(2, 6, 0, 0));
            calendarGrid.add(timeLabel, 0, rowIndex);

            for (int d = 0; d < 7; d++) {
                StackPane cell = new StackPane();
                cell.getStyleClass().add("calendar-cell");
                cell.setMinHeight(HOUR_HEIGHT);

                // Drop target for drag-and-drop
                final int dayOffset = d;
                final int hour = h;
                cell.setOnDragOver(event -> {
                    if (event.getGestureSource() != cell && event.getDragboard().hasString()) {
                        event.acceptTransferModes(TransferMode.MOVE);
                    }
                    event.consume();
                });
                cell.setOnDragEntered(event -> {
                    if (event.getDragboard().hasString()) {
                        cell.getStyleClass().add("calendar-cell-drop-target");
                    }
                });
                cell.setOnDragExited(event -> {
                    cell.getStyleClass().remove("calendar-cell-drop-target");
                });
                cell.setOnDragDropped(event -> {
                    handleDrop(event, dayOffset, hour);
                });

                calendarGrid.add(cell, d + 1, rowIndex);
            }
        }

        // Place events
        placeEvents();
    }

    private void placeEvents() {
        Calendar weekStart = (Calendar) currentWeekStart.clone();
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        Calendar weekEndCal = (Calendar) weekStart.clone();
        weekEndCal.add(Calendar.DAY_OF_YEAR, 7);

        try {
            List<ScheduledEvent> events = new ScheduledEventDAO(dbManager)
                    .findByTimeRange(weekStart.getTimeInMillis(), weekEndCal.getTimeInMillis());
            cache.enrichAll(events);

            for (ScheduledEvent ev : events) {
                placeEventBlock(ev);
            }
        } catch (SQLException ignored) {}
    }

    private void placeEventBlock(ScheduledEvent ev) {
        if (ev.getStartEpoch() == null || ev.getEndEpoch() == null) return;

        Calendar evStart = Calendar.getInstance();
        evStart.setTimeInMillis(ev.getStartEpoch());

        Calendar evEnd = Calendar.getInstance();
        evEnd.setTimeInMillis(ev.getEndEpoch());

        // Determine day column (0-6)
        Calendar weekMon = (Calendar) currentWeekStart.clone();
        long diffMs = evStart.getTimeInMillis() - weekMon.getTimeInMillis();
        int dayIndex = (int) (diffMs / (24 * 60 * 60 * 1000L));
        if (dayIndex < 0 || dayIndex > 6) return;

        // Calculate position within the grid
        double startHour = evStart.get(Calendar.HOUR_OF_DAY) + evStart.get(Calendar.MINUTE) / 60.0;
        double endHour = evEnd.get(Calendar.HOUR_OF_DAY) + evEnd.get(Calendar.MINUTE) / 60.0;
        if (endHour <= START_HOUR || startHour >= END_HOUR) return;

        startHour = Math.max(startHour, START_HOUR);
        endHour = Math.min(endHour, END_HOUR);

        int startRow = (int) (startHour - START_HOUR) + 1;
        int rowSpan = Math.max(1, (int) Math.ceil(endHour - START_HOUR) - (int) (startHour - START_HOUR));

        // Build event block
        VBox block = buildEventBlock(ev);

        // Add to grid as overlay
        calendarGrid.add(block, dayIndex + 1, startRow, 1, rowSpan);
    }

    private VBox buildEventBlock(ScheduledEvent ev) {
        String courseText = ev.getCourseCode() != null ? ev.getCourseCode() : "Event";
        String typeText = ev.getEventType() != null ? ev.getEventType().getDisplay() : "";
        String locText = ev.getLocationName() != null ? ev.getLocationName() : "Online";
        String timeText = "";
        if (ev.getStartEpoch() != null && ev.getEndEpoch() != null) {
            timeText = TIME_FMT.format(new Date(ev.getStartEpoch())) + "-" + TIME_FMT.format(new Date(ev.getEndEpoch()));
        }

        Label courseLbl = new Label(courseText);
        courseLbl.getStyleClass().add("cal-event-title");

        Label timeLbl = new Label(timeText);
        timeLbl.getStyleClass().add("cal-event-time");

        Label locLbl = new Label(locText);
        locLbl.getStyleClass().add("cal-event-loc");

        VBox block = new VBox(2, courseLbl, timeLbl, locLbl);
        block.setPadding(new Insets(4, 6, 4, 6));
        block.getStyleClass().add("cal-event-block");
        block.setCursor(Cursor.HAND);

        // Color based on type and conflict status
        boolean isConflict = ev.getEventId() != null && conflictEventIds.contains(ev.getEventId());
        if (isConflict) {
            block.getStyleClass().add("cal-event-conflict");
        } else if (ev.getEventType() != null) {
            switch (ev.getEventType()) {
                case LECTURE -> block.getStyleClass().add("cal-event-lecture");
                case EXAM -> block.getStyleClass().add("cal-event-exam");
                case OFFICE_HOURS -> block.getStyleClass().add("cal-event-office");
            }
        }

        // Drag support for reassignment
        block.setOnDragDetected(event -> {
            if (ev.getEventId() != null) {
                Dragboard db = block.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(String.valueOf(ev.getEventId()));
                db.setContent(content);
                event.consume();
            }
        });

        // Tooltip with full details
        Tooltip tooltip = new Tooltip(String.format(
                "%s – %s\n%s\n%s\nProfessor: %s\nDuration: %d min%s",
                courseText, typeText, timeText, locText,
                ev.getProfessorName() != null ? ev.getProfessorName() : "N/A",
                ev.getDurationMinutes(),
                isConflict ? "\n\u26A0 CONFLICT DETECTED" : ""
        ));
        tooltip.setShowDelay(javafx.util.Duration.millis(200));
        Tooltip.install(block, tooltip);

        return block;
    }

    private void handleDrop(DragEvent event, int dayOffset, int hour) {
        Dragboard db = event.getDragboard();
        if (!db.hasString()) return;

        try {
            int eventId = Integer.parseInt(db.getString());
            ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
            ScheduledEvent ev = dao.findById(eventId);
            if (ev == null) return;

            // Calculate new start time preserving duration
            long duration = ev.getEndEpoch() - ev.getStartEpoch();
            Calendar newStart = (Calendar) currentWeekStart.clone();
            newStart.add(Calendar.DAY_OF_YEAR, dayOffset);
            newStart.set(Calendar.HOUR_OF_DAY, hour);
            newStart.set(Calendar.MINUTE, 0);
            newStart.set(Calendar.SECOND, 0);

            ev.setStartEpoch(newStart.getTimeInMillis());
            ev.setEndEpoch(newStart.getTimeInMillis() + duration);
            dao.update(ev);

            ToastNotification.show(
                    "Event moved to " + DAY_NAMES[dayOffset] + " at " + String.format("%02d:00", hour),
                    ToastNotification.ToastType.SUCCESS
            );

            refresh();
            event.setDropCompleted(true);
        } catch (Exception ex) {
            ToastNotification.show("Failed to move event: " + ex.getMessage(),
                    ToastNotification.ToastType.ERROR);
            event.setDropCompleted(false);
        }
        event.consume();
    }
}
