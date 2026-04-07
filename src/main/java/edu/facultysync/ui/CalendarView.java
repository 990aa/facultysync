package edu.facultysync.ui;

import edu.facultysync.db.DatabaseManager;
import edu.facultysync.db.ScheduledEventDAO;
import edu.facultysync.events.AppEventBus;
import edu.facultysync.events.DataChangedEvent;
import edu.facultysync.model.ConflictResult;
import edu.facultysync.model.Course;
import edu.facultysync.model.ScheduledEvent;
import edu.facultysync.service.ConflictEngine;
import edu.facultysync.service.DataCache;
import edu.facultysync.util.TimePolicy;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Interactive visual calendar grid (Google Calendar-like weekly view).
 * Events are displayed as colored blocks positioned by day and hour.
 * Supports drag-and-drop for room reassignment.
 */
public class CalendarView {

    private static final int DEFAULT_START_HOUR = 7;
    private static final int DEFAULT_END_HOUR = 22;
    private static final int HOUR_HEIGHT = 60;
    private static final String[] DAY_NAMES = {
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    private final DatabaseManager dbManager;
    private final DataCache cache;
    private final ConflictEngine conflictEngine;
    private final AppEventBus eventBus;
    private final VBox root;

    private GridPane calendarGrid;
    private LocalDate currentWeekStart;
    private Label weekLabel;
    private DatePicker weekPicker;

    private int renderStartHour = DEFAULT_START_HOUR;
    private int renderEndHour = DEFAULT_END_HOUR;
    private Set<Integer> conflictEventIds = new HashSet<>();
    private boolean suppressWeekPickerEvent;
    private Map<Integer, EventPlacement> placementsByEventId = new HashMap<>();
    private Task<CalendarSnapshot> activeRefreshTask;

    public CalendarView(DatabaseManager dbManager, DataCache cache) {
        this(dbManager, cache, null);
    }

    public CalendarView(DatabaseManager dbManager, DataCache cache, AppEventBus eventBus) {
        this.dbManager = dbManager;
        this.cache = cache;
        this.conflictEngine = new ConflictEngine(dbManager, cache);
        this.eventBus = eventBus;

        setWeekFromDate(LocalDate.now());
        root = buildView();
        if (this.eventBus != null) {
            this.eventBus.subscribe(DataChangedEvent.class, this::onDataChanged);
        }
        refresh();
    }

    public void onDataChanged(DataChangedEvent event) {
        Platform.runLater(this::refresh);
    }

    public VBox getView() {
        return root;
    }

    public void refresh() {
        refresh(null);
    }

    public void refresh(Runnable onComplete) {
        cancelRefresh();
        weekLabel.setText("Loading week...");

        Task<CalendarSnapshot> task = new Task<>() {
            @Override
            protected CalendarSnapshot call() throws Exception {
                if (isCancelled()) {
                    return null;
                }
                LocalDate weekStart = currentWeekStart;
                LocalDate weekEnd = weekStart.plusDays(7);
                long weekStartEpoch = weekStart.atStartOfDay(TimePolicy.zone()).toInstant().toEpochMilli();
                long weekEndEpoch = weekEnd.atStartOfDay(TimePolicy.zone()).toInstant().toEpochMilli();

                List<ScheduledEvent> events = new ScheduledEventDAO(dbManager)
                        .findByTimeRange(weekStartEpoch, weekEndEpoch);
                if (isCancelled()) {
                    return null;
                }
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
        long weekEndLabelEpoch = weekStart.plusDays(6)
            .atStartOfDay(TimePolicy.zone())
            .toInstant()
            .toEpochMilli();
        String labelText = TimePolicy.formatWeekRange(weekStartEpoch)
                        + " - "
            + TimePolicy.formatWeekRange(weekEndLabelEpoch);

                return new CalendarSnapshot(
            weekStart,
                        events,
                        conflictIds,
                        bounds[0],
                        bounds[1],
                        labelText
                );
            }
        };
        activeRefreshTask = task;

        task.setOnSucceeded(e -> {
            if (task != activeRefreshTask || task.isCancelled() || task.getValue() == null) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            CalendarSnapshot snapshot = task.getValue();
            if (!snapshot.weekStart.equals(currentWeekStart)) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }

            renderStartHour = snapshot.startHour;
            renderEndHour = snapshot.endHour;
            conflictEventIds = snapshot.conflictEventIds;
            placementsByEventId = computePlacements(snapshot.events);
            weekLabel.setText(snapshot.weekLabelText);
            buildCalendarContent(snapshot.events);

            if (onComplete != null) {
                onComplete.run();
            }
        });

        task.setOnFailed(e -> {
            if (task.isCancelled()) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            weekLabel.setText("Unable to load week");
            calendarGrid.getChildren().clear();
            calendarGrid.getColumnConstraints().clear();
            calendarGrid.getRowConstraints().clear();
            Label error = new Label("Could not load calendar data: "
                    + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
            error.getStyleClass().add("empty-state-label");
            calendarGrid.add(error, 0, 0);
            if (onComplete != null) {
                onComplete.run();
            }
        });

        task.setOnCancelled(e -> {
            if (onComplete != null) {
                onComplete.run();
            }
        });

        Thread thread = new Thread(task, "CalendarRefresh");
        thread.setDaemon(true);
        thread.start();
    }

    public void cancelRefresh() {
        if (activeRefreshTask != null && activeRefreshTask.isRunning()) {
            activeRefreshTask.cancel();
        }
    }

    private VBox buildView() {
        VBox container = new VBox(0);
        container.setFillWidth(true);
        container.getStyleClass().add("calendar-container");

        HBox navBar = buildNavBar();

        calendarGrid = new GridPane();
        calendarGrid.getStyleClass().add("calendar-grid");

        ScrollPane scrollPane = new ScrollPane(calendarGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("calendar-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        container.getChildren().addAll(navBar, scrollPane);
        return container;
    }

    private HBox buildNavBar() {
        Button prevBtn = new Button("< Prev");
        prevBtn.getStyleClass().add("calendar-nav-btn");
        prevBtn.setOnAction(e -> {
            currentWeekStart = currentWeekStart.minusWeeks(1);
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
            currentWeekStart = currentWeekStart.plusWeeks(1);
            syncWeekPicker();
            refresh();
        });

        Label jumpLabel = new Label("Jump to week:");
        jumpLabel.getStyleClass().add("calendar-jump-label");

        weekPicker = new DatePicker(currentWeekStart);
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

        LocalDate dayCal = currentWeekStart;
        for (int d = 0; d < 7; d++) {
            long dayEpoch = dayCal.atStartOfDay(TimePolicy.zone()).toInstant().toEpochMilli();
            Label dayLabel = new Label(TimePolicy.formatDay(dayEpoch));
            dayLabel.getStyleClass().add("calendar-day-header");
            dayLabel.setMaxWidth(Double.MAX_VALUE);
            dayLabel.setAlignment(Pos.CENTER);
            calendarGrid.add(dayLabel, d + 1, 0);
            dayCal = dayCal.plusDays(1);
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

        int dayIndex = dayIndexForEpoch(ev.getStartEpoch());
        if (dayIndex < 0 || dayIndex > 6) {
            return;
        }

        ZonedDateTime start = Instant.ofEpochMilli(ev.getStartEpoch()).atZone(TimePolicy.zone());
        ZonedDateTime end = Instant.ofEpochMilli(ev.getEndEpoch()).atZone(TimePolicy.zone());

        double startHour = start.getHour() + start.getMinute() / 60.0;
        double endHour = end.getHour() + end.getMinute() / 60.0;
        if (endHour <= renderStartHour || startHour >= renderEndHour) {
            return;
        }

        startHour = Math.max(startHour, renderStartHour);
        endHour = Math.min(endHour, renderEndHour);

        int startRow = (int) (startHour - renderStartHour) + 1;
        int rowSpan = Math.max(1,
                (int) Math.ceil(endHour - renderStartHour) - (int) (startHour - renderStartHour));

        VBox block = buildEventBlock(ev);
        EventPlacement placement = ev.getEventId() != null ? placementsByEventId.get(ev.getEventId()) : null;
        if (placement != null && placement.laneCount > 1) {
            double laneInset = 14;
            double left = placement.lane * laneInset;
            double right = (placement.laneCount - placement.lane - 1) * laneInset;
            GridPane.setMargin(block, new Insets(2, 2 + right, 2, 2 + left));
        }

        calendarGrid.add(block, dayIndex + 1, startRow, 1, rowSpan);
    }

    private VBox buildEventBlock(ScheduledEvent ev) {
        String courseText = ev.getCourseCode() != null ? ev.getCourseCode() : "Event";
        String typeText = ev.getEventType() != null ? ev.getEventType().getDisplay() : "";
        String locText = ev.getLocationName() != null ? ev.getLocationName() : "Online";
        String timeText = TimePolicy.formatTime(ev.getStartEpoch()) + "-" + TimePolicy.formatTime(ev.getEndEpoch());

        Label courseLbl = new Label(courseText);
        courseLbl.getStyleClass().add("cal-event-title");
        courseLbl.setWrapText(false);
        courseLbl.setTextOverrun(OverrunStyle.ELLIPSIS);
        courseLbl.setMinWidth(0);
        courseLbl.setMaxWidth(Double.MAX_VALUE);

        Label timeLbl = new Label(timeText);
        timeLbl.getStyleClass().add("cal-event-time");
        timeLbl.setWrapText(false);
        timeLbl.setMinWidth(0);
        timeLbl.setMaxWidth(Double.MAX_VALUE);

        Label locLbl = new Label(locText);
        locLbl.getStyleClass().add("cal-event-loc");
        locLbl.setWrapText(false);
        locLbl.setTextOverrun(OverrunStyle.ELLIPSIS);
        locLbl.setMinWidth(0);
        locLbl.setMaxWidth(Double.MAX_VALUE);

        VBox block = new VBox(2, courseLbl, timeLbl, locLbl);
        block.setFillWidth(true);
        block.setPadding(new Insets(4, 6, 4, 6));
        block.getStyleClass().add("cal-event-block");
        block.setCursor(Cursor.HAND);

        courseLbl.maxWidthProperty().bind(block.widthProperty().subtract(12));
        timeLbl.maxWidthProperty().bind(block.widthProperty().subtract(12));
        locLbl.maxWidthProperty().bind(block.widthProperty().subtract(12));

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

        long newStartEpoch = currentWeekStart
            .plusDays(dayOffset)
            .atTime(hour, 0)
            .atZone(TimePolicy.zone())
            .toInstant()
            .toEpochMilli();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Move Event");
        confirm.setHeaderText("Confirm schedule change");
        confirm.setContentText("Move event to " + DAY_NAMES[dayOffset] + " at "
                + String.format("%02d:00", hour) + " (" + TimePolicy.zoneLabel() + ")?");

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            event.setDropCompleted(false);
            event.consume();
            return;
        }

        Task<MoveResult> moveTask = new Task<>() {
            @Override
            protected MoveResult call() throws Exception {
                ScheduledEventDAO dao = new ScheduledEventDAO(dbManager);
                ScheduledEvent target = dao.findById(eventId);
                if (target == null || target.getStartEpoch() == null || target.getEndEpoch() == null) {
                    throw new SQLException("Event not found or has invalid schedule.");
                }

                long duration = target.getEndEpoch() - target.getStartEpoch();
                target.setStartEpoch(newStartEpoch);
                target.setEndEpoch(newStartEpoch + duration);
                dao.update(target);

                cache.refresh();
                List<ScheduledEvent> allEvents = dao.findAll();
                cache.enrichAll(allEvents);
                List<ConflictResult> conflicts = conflictEngine.analyze(allEvents);
                boolean nowConflicted = conflicts.stream().anyMatch(c -> involvesEvent(c, eventId));

                Course course = cache.getCourse(target.getCourseId());
                String label = course != null ? course.getCourseCode() : "Event#" + eventId;
                return new MoveResult(label, nowConflicted);
             }
         };

        moveTask.setOnSucceeded(e -> {
            MoveResult result = moveTask.getValue();
            if (result.hasConflictAfterMove) {
                ToastNotification.show(
                        result.eventLabel + " moved, but now participates in a conflict.",
                        ToastNotification.ToastType.WARNING
                );
            } else {
                 ToastNotification.show(
                         "Event moved to " + DAY_NAMES[dayOffset] + " at " + String.format("%02d:00", hour),
                         ToastNotification.ToastType.SUCCESS
                 );
            }
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

    private Map<Integer, EventPlacement> computePlacements(List<ScheduledEvent> events) {
        Map<Integer, EventPlacement> placements = new HashMap<>();
        Map<Integer, List<ScheduledEvent>> byDay = new HashMap<>();

        for (ScheduledEvent event : events) {
            if (event.getEventId() == null || event.getStartEpoch() == null || event.getEndEpoch() == null) {
                continue;
            }
            int dayIndex = dayIndexForEpoch(event.getStartEpoch());
            if (dayIndex >= 0 && dayIndex <= 6) {
                byDay.computeIfAbsent(dayIndex, d -> new ArrayList<>()).add(event);
            }
        }

        for (List<ScheduledEvent> dayEvents : byDay.values()) {
            dayEvents.sort(Comparator.comparingLong(ScheduledEvent::getStart));
            int index = 0;
            while (index < dayEvents.size()) {
                List<ScheduledEvent> cluster = new ArrayList<>();
                long clusterEnd = dayEvents.get(index).getEnd();
                int cursor = index;

                while (cursor < dayEvents.size() && dayEvents.get(cursor).getStart() < clusterEnd) {
                    ScheduledEvent candidate = dayEvents.get(cursor);
                    cluster.add(candidate);
                    clusterEnd = Math.max(clusterEnd, candidate.getEnd());
                    cursor++;
                }

                if (cluster.isEmpty()) {
                    cluster.add(dayEvents.get(index));
                    cursor = index + 1;
                }

                List<Long> laneEnds = new ArrayList<>();
                Map<Integer, Integer> laneByEvent = new HashMap<>();

                for (ScheduledEvent event : cluster) {
                    int lane = 0;
                    while (lane < laneEnds.size() && laneEnds.get(lane) > event.getStart()) {
                        lane++;
                    }
                    if (lane == laneEnds.size()) {
                        laneEnds.add(event.getEnd());
                    } else {
                        laneEnds.set(lane, event.getEnd());
                    }
                    laneByEvent.put(event.getEventId(), lane);
                }

                int laneCount = Math.max(1, laneEnds.size());
                for (ScheduledEvent event : cluster) {
                    placements.put(event.getEventId(), new EventPlacement(laneByEvent.get(event.getEventId()), laneCount));
                }

                index = cursor;
            }
        }

        return placements;
    }

    private boolean involvesEvent(ConflictResult conflict, int eventId) {
        return (conflict.getEventA() != null && conflict.getEventA().getEventId() != null
                && conflict.getEventA().getEventId() == eventId)
                || (conflict.getEventB() != null && conflict.getEventB().getEventId() != null
                && conflict.getEventB().getEventId() == eventId);
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
        return Instant.ofEpochMilli(epochMs).atZone(TimePolicy.zone()).getHour();
    }

    private int hourCeil(long epochMs) {
        ZonedDateTime dt = Instant.ofEpochMilli(epochMs).atZone(TimePolicy.zone());
        boolean exactHour = dt.getMinute() == 0 && dt.getSecond() == 0 && dt.getNano() == 0;
        return exactHour ? dt.getHour() : dt.getHour() + 1;
    }

    private void setWeekFromDate(LocalDate date) {
        currentWeekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        syncWeekPicker();
    }

    private void syncWeekPicker() {
        if (weekPicker == null) {
            return;
        }

        suppressWeekPickerEvent = true;
        try {
            weekPicker.setValue(currentWeekStart);
        } finally {
            suppressWeekPickerEvent = false;
        }
    }

    private LocalDate toLocalDate(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(TimePolicy.zone()).toLocalDate();
    }

    private int dayIndexForEpoch(long epochMs) {
        return (int) ChronoUnit.DAYS.between(
            currentWeekStart,
                toLocalDate(epochMs)
        );
    }

    private static class CalendarSnapshot {
        final LocalDate weekStart;
        final List<ScheduledEvent> events;
        final Set<Integer> conflictEventIds;
        final int startHour;
        final int endHour;
        final String weekLabelText;

        CalendarSnapshot(
            LocalDate weekStart,
                List<ScheduledEvent> events,
                Set<Integer> conflictEventIds,
                int startHour,
                int endHour,
                String weekLabelText
        ) {
            this.weekStart = weekStart;
            this.events = new ArrayList<>(events);
            this.conflictEventIds = new HashSet<>(conflictEventIds);
            this.startHour = startHour;
            this.endHour = endHour;
            this.weekLabelText = weekLabelText;
        }
    }

    private static class EventPlacement {
        final int lane;
        final int laneCount;

        EventPlacement(int lane, int laneCount) {
            this.lane = lane;
            this.laneCount = laneCount;
        }
    }

    private static class MoveResult {
        final String eventLabel;
        final boolean hasConflictAfterMove;

        MoveResult(String eventLabel, boolean hasConflictAfterMove) {
            this.eventLabel = eventLabel;
            this.hasConflictAfterMove = hasConflictAfterMove;
        }
    }
}
