package edu.facultysync.ui;

import edu.facultysync.db.DatabaseManager;
import edu.facultysync.db.ScheduledEventDAO;
import edu.facultysync.model.ConflictResult;
import edu.facultysync.model.ScheduledEvent;
import edu.facultysync.service.ConflictEngine;
import edu.facultysync.service.DataCache;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interactive visual calendar grid (Google Calendar-like weekly view).
 * Events are displayed as colored blocks positioned by day and hour.
 * Supports drag-and-drop for room reassignment.
 */
public class CalendarView {

    private static final SimpleDateFormat DAY_FMT = new SimpleDateFormat("EEE MM/dd");
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm");
    private static final int DEFAULT_START_HOUR = 7;
    private static final int DEFAULT_END_HOUR = 22;
    private static final int HOUR_HEIGHT = 60;
    private static final String[] DAY_NAMES = {
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    private final DatabaseManager dbManager;
    private final DataCache cache;
    private final ConflictEngine conflictEngine;
    private final VBox root;

    private GridPane calendarGrid;
    private Calendar currentWeekStart;
    private Label weekLabel;
    private DatePicker weekPicker;

    private int renderStartHour = DEFAULT_START_HOUR;
    private int renderEndHour = DEFAULT_END_HOUR;
    private Set<Integer> conflictEventIds = new HashSet<>();
    private boolean suppressWeekPickerEvent;

    public CalendarView(DatabaseManager dbManager, DataCache cache) {
        this.dbManager = dbManager;
        this.cache = cache;
        this.conflictEngine = new ConflictEngine(dbManager, cache);

        setWeekFromDate(LocalDate.now());
        root = buildView();
        refresh();
    }

    public VBox getView() {
        return root;
    }

    public void refresh() {
        weekLabel.setText("Loading week...");

        Task<CalendarSnapshot> task = new Task<>() {
            @Override
            protected CalendarSnapshot call() throws Exception {
                Calendar weekStart = (Calendar) currentWeekStart.clone();
                Calendar weekEnd = (Calendar) weekStart.clone();
                weekEnd.add(Calendar.DAY_OF_YEAR, 7);

                List<ScheduledEvent> events = new ScheduledEventDAO(dbManager)
                        .findByTimeRange(weekStart.getTimeInMillis(), weekEnd.getTimeInMillis());
                cache.enrichAll(events);

                List<ConflictResult> conflicts = conflictEngine.analyze(events);
                Set<Integer> conflictIds = new HashSet<>();
                for (ConflictResult cr : conflicts) {
                    if (cr.getEventA() != null && cr.getEventA().getEventId() != null) {
                        conflictIds.add(cr.getEventA().getEventId());
                    }
                    if (cr.getEventB() != null && cr.getEventB().getEventId() != null) {
                        conflictIds.add(cr.getEventB().getEventId());
                    }
                }

                int[] bounds = computeHourBounds(events);
                Calendar weekEndLabel = (Calendar) weekStart.clone();
                weekEndLabel.add(Calendar.DAY_OF_YEAR, 6);
                SimpleDateFormat rangeFmt = new SimpleDateFormat("MMM d, yyyy");
                String labelText = rangeFmt.format(weekStart.getTime()) + " - "
                        + rangeFmt.format(weekEndLabel.getTime());

                return new CalendarSnapshot(
                        weekStart.getTimeInMillis(),
                        events,
                        conflictIds,
                        bounds[0],
                        bounds[1],
                        labelText
                );
            }
        };

        task.setOnSucceeded(e -> {
            CalendarSnapshot snapshot = task.getValue();
            if (snapshot.weekStartEpoch != currentWeekStart.getTimeInMillis()) {
                return;
            }

            renderStartHour = snapshot.startHour;
            renderEndHour = snapshot.endHour;
            conflictEventIds = snapshot.conflictEventIds;
            weekLabel.setText(snapshot.weekLabelText);
            buildCalendarContent(snapshot.events);
        });

        task.setOnFailed(e -> {
            weekLabel.setText("Unable to load week");
            calendarGrid.getChildren().clear();
            calendarGrid.getColumnConstraints().clear();
            calendarGrid.getRowConstraints().clear();
            Label error = new Label("Could not load calendar data: "
                    + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
            error.getStyleClass().add("empty-state-label");
            calendarGrid.add(error, 0, 0);
        });

        Thread thread = new Thread(task, "CalendarRefresh");
        thread.setDaemon(true);
        thread.start();
    }

    private VBox buildView() {
        VBox container = new VBox(0);
        container.getStyleClass().add("calendar-container");

        HBox navBar = buildNavBar();

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
        Button prevBtn = new Button("< Prev");
        prevBtn.getStyleClass().add("calendar-nav-btn");
        prevBtn.setOnAction(e -> {
            currentWeekStart.add(Calendar.WEEK_OF_YEAR, -1);
            syncWeekPicker();
            refresh();
        });

        Button todayBtn = new Button("Today");
        todayBtn.getStyleClass().addAll("calendar-nav-btn", "primary-btn");
        todayBtn.setOnAction(e -> {
            setWeekFromDate(LocalDate.now());
            refresh();
        });

        Button nextBtn = new Button("Next >");
        nextBtn.getStyleClass().add("calendar-nav-btn");
        nextBtn.setOnAction(e -> {
            currentWeekStart.add(Calendar.WEEK_OF_YEAR, 1);
            syncWeekPicker();
            refresh();
        });

        Label jumpLabel = new Label("Jump to week:");
        jumpLabel.getStyleClass().add("calendar-jump-label");

        weekPicker = new DatePicker(toLocalDate(currentWeekStart.getTimeInMillis()));
        weekPicker.getStyleClass().add("calendar-week-picker");
        weekPicker.setEditable(false);
        weekPicker.setOnAction(e -> {
            if (suppressWeekPickerEvent) {
                return;
            }
            LocalDate selected = weekPicker.getValue();
            if (selected != null) {
                setWeekFromDate(selected);
                refresh();
            }
        });

        weekLabel = new Label();
        weekLabel.getStyleClass().add("calendar-week-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox legend = new HBox(12);
        legend.setAlignment(Pos.CENTER_RIGHT);
        legend.getChildren().addAll(
                legendItem("calendar-legend-lecture", "Lecture"),
                legendItem("calendar-legend-exam", "Exam"),
                legendItem("calendar-legend-office", "Office Hrs"),
                legendItem("calendar-legend-conflict", "Conflict")
        );

        HBox navBar = new HBox(10,
                prevBtn,
                todayBtn,
                nextBtn,
                jumpLabel,
                weekPicker,
                weekLabel,
                spacer,
                legend
        );
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.setPadding(new Insets(10, 16, 10, 16));
        navBar.getStyleClass().add("calendar-nav");
        return navBar;
    }

    private HBox legendItem(String dotStyleClass, String label) {
        Region dot = new Region();
        dot.setPrefSize(12, 12);
        dot.setMinSize(12, 12);
        dot.getStyleClass().addAll("calendar-legend-dot", dotStyleClass);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("calendar-legend-label");

        HBox item = new HBox(4, dot, lbl);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private void buildCalendarContent(List<ScheduledEvent> events) {
        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        calendarGrid.getRowConstraints().clear();

        ColumnConstraints timeCol = new ColumnConstraints(70);
        timeCol.setMinWidth(70);
        calendarGrid.getColumnConstraints().add(timeCol);
        for (int d = 0; d < 7; d++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setHgrow(Priority.ALWAYS);
            dayCol.setMinWidth(100);
            calendarGrid.getColumnConstraints().add(dayCol);
        }

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

        for (int h = renderStartHour; h < renderEndHour; h++) {
            int rowIndex = h - renderStartHour + 1;
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
                cell.setOnDragExited(event -> cell.getStyleClass().remove("calendar-cell-drop-target"));
                cell.setOnDragDropped(event -> handleDrop(event, dayOffset, hour));

                calendarGrid.add(cell, d + 1, rowIndex);
            }
        }

        for (ScheduledEvent ev : events) {
            placeEventBlock(ev);
        }
    }

    private void placeEventBlock(ScheduledEvent ev) {
        if (ev.getStartEpoch() == null || ev.getEndEpoch() == null) {
            return;
        }

        Calendar evStart = Calendar.getInstance();
        evStart.setTimeInMillis(ev.getStartEpoch());

        Calendar evEnd = Calendar.getInstance();
        evEnd.setTimeInMillis(ev.getEndEpoch());

        int dayIndex = (int) ChronoUnit.DAYS.between(
                toLocalDate(currentWeekStart.getTimeInMillis()),
                toLocalDate(evStart.getTimeInMillis())
        );
        if (dayIndex < 0 || dayIndex > 6) {
            return;
        }

        double startHour = evStart.get(Calendar.HOUR_OF_DAY) + evStart.get(Calendar.MINUTE) / 60.0;
        double endHour = evEnd.get(Calendar.HOUR_OF_DAY) + evEnd.get(Calendar.MINUTE) / 60.0;
        if (endHour <= renderStartHour || startHour >= renderEndHour) {
            return;
        }

        startHour = Math.max(startHour, renderStartHour);
        endHour = Math.min(endHour, renderEndHour);

        int startRow = (int) (startHour - renderStartHour) + 1;
        int rowSpan = Math.max(
                1,
                (int) Math.ceil(endHour - renderStartHour)
                        - (int) (startHour - renderStartHour)
        );

        VBox block = buildEventBlock(ev);
        calendarGrid.add(block, dayIndex + 1, startRow, 1, rowSpan);
    }

    private VBox buildEventBlock(ScheduledEvent ev) {
        String courseText = ev.getCourseCode() != null ? ev.getCourseCode() : "Event";
        String typeText = ev.getEventType() != null ? ev.getEventType().getDisplay() : "";
        String locText = ev.getLocationName() != null ? ev.getLocationName() : "Online";
        String timeText = "";
        if (ev.getStartEpoch() != null && ev.getEndEpoch() != null) {
            timeText = TIME_FMT.format(new Date(ev.getStartEpoch()))
                    + "-"
                    + TIME_FMT.format(new Date(ev.getEndEpoch()));
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

        block.setOnDragDetected(event -> {
            if (ev.getEventId() != null) {
                Dragboard db = block.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(String.valueOf(ev.getEventId()));
                db.setContent(content);
                event.consume();
            }
        });

        Tooltip tooltip = new Tooltip(String.format(
                "%s - %s\n%s\n%s\nProfessor: %s\nDuration: %d min%s",
                courseText,
                typeText,
                timeText,
                locText,
                ev.getProfessorName() != null ? ev.getProfessorName() : "N/A",
                ev.getDurationMinutes(),
                isConflict ? "\n! CONFLICT DETECTED" : ""
        ));
        tooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(block, tooltip);

        return block;
    }

    private void handleDrop(DragEvent event, int dayOffset, int hour) {
        Dragboard db = event.getDragboard();
        if (!db.hasString()) {
            event.setDropCompleted(false);
            event.consume();
            return;
        }

        final int eventId;
        try {
            eventId = Integer.parseInt(db.getString());
        } catch (NumberFormatException ex) {
            event.setDropCompleted(false);
            event.consume();
            return;
        }

        Calendar newStart = (Calendar) currentWeekStart.clone();
        newStart.add(Calendar.DAY_OF_YEAR, dayOffset);
        newStart.set(Calendar.HOUR_OF_DAY, hour);
        newStart.set(Calendar.MINUTE, 0);
        newStart.set(Calendar.SECOND, 0);
        newStart.set(Calendar.MILLISECOND, 0);

        long newStartEpoch = newStart.getTimeInMillis();

        Task<Void> moveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
                ScheduledEvent ev = dao.findById(eventId);
                if (ev == null || ev.getStartEpoch() == null || ev.getEndEpoch() == null) {
                    throw new SQLException("Event not found or has invalid schedule.");
                }

                long duration = ev.getEndEpoch() - ev.getStartEpoch();
                ev.setStartEpoch(newStartEpoch);
                ev.setEndEpoch(newStartEpoch + duration);
                dao.update(ev);
                return null;
            }
        };

        moveTask.setOnSucceeded(e -> {
            ToastNotification.show(
                    "Event moved to " + DAY_NAMES[dayOffset] + " at " + String.format("%02d:00", hour),
                    ToastNotification.ToastType.SUCCESS
            );
            refresh();
        });

        moveTask.setOnFailed(e -> ToastNotification.show(
                "Failed to move event: "
                        + (moveTask.getException() != null
                        ? moveTask.getException().getMessage()
                        : "Unknown error"),
                ToastNotification.ToastType.ERROR
        ));

        Thread thread = new Thread(moveTask, "CalendarMoveEvent");
        thread.setDaemon(true);
        thread.start();

        event.setDropCompleted(true);
        event.consume();
    }

    private int[] computeHourBounds(List<ScheduledEvent> events) {
        int minHour = DEFAULT_START_HOUR;
        int maxHour = DEFAULT_END_HOUR;

        boolean hasTimedEvents = false;
        for (ScheduledEvent ev : events) {
            if (ev.getStartEpoch() == null || ev.getEndEpoch() == null) {
                continue;
            }
            hasTimedEvents = true;
            minHour = Math.min(minHour, hourFloor(ev.getStartEpoch()));
            maxHour = Math.max(maxHour, hourCeil(ev.getEndEpoch()));
        }

        if (!hasTimedEvents) {
            return new int[]{DEFAULT_START_HOUR, DEFAULT_END_HOUR};
        }

        minHour = Math.max(0, minHour);
        maxHour = Math.min(24, maxHour);
        if (maxHour <= minHour) {
            maxHour = Math.min(24, minHour + 1);
        }
        return new int[]{minHour, maxHour};
    }

    private int hourFloor(long epochMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochMs);
        return cal.get(Calendar.HOUR_OF_DAY);
    }

    private int hourCeil(long epochMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochMs);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        boolean exactHour = cal.get(Calendar.MINUTE) == 0
                && cal.get(Calendar.SECOND) == 0
                && cal.get(Calendar.MILLISECOND) == 0;
        return exactHour ? hour : hour + 1;
    }

    private void setWeekFromDate(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        ZonedDateTime zdt = monday.atStartOfDay(ZoneId.systemDefault());
        currentWeekStart = GregorianCalendar.from(zdt);
        syncWeekPicker();
    }

    private void syncWeekPicker() {
        if (weekPicker == null) {
            return;
        }

        suppressWeekPickerEvent = true;
        try {
            weekPicker.setValue(toLocalDate(currentWeekStart.getTimeInMillis()));
        } finally {
            suppressWeekPickerEvent = false;
        }
    }

    private LocalDate toLocalDate(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static class CalendarSnapshot {
        final long weekStartEpoch;
        final List<ScheduledEvent> events;
        final Set<Integer> conflictEventIds;
        final int startHour;
        final int endHour;
        final String weekLabelText;

        CalendarSnapshot(
                long weekStartEpoch,
                List<ScheduledEvent> events,
                Set<Integer> conflictEventIds,
                int startHour,
                int endHour,
                String weekLabelText
        ) {
            this.weekStartEpoch = weekStartEpoch;
            this.events = new ArrayList<>(events);
            this.conflictEventIds = new HashSet<>(conflictEventIds);
            this.startHour = startHour;
            this.endHour = endHour;
            this.weekLabelText = weekLabelText;
        }
    }
}
