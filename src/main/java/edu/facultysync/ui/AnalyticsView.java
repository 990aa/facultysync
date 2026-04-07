package edu.facultysync.ui;

import edu.facultysync.db.DatabaseManager;
import edu.facultysync.db.ScheduledEventDAO;
import edu.facultysync.model.*;
import edu.facultysync.service.ConflictEngine;
import edu.facultysync.service.DataCache;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.util.*;

/**
 * Analytics Dashboard with interactive charts.
 * - PieChart: Room utilization by building
 * - BarChart: Peak hours distribution
 * - PieChart: Event type distribution
 * - BarChart: Events per department
 */
public class AnalyticsView {

    private final DatabaseManager dbManager;
    private final DataCache cache;
    private final VBox root;

    // Chart containers for refresh
    private VBox chartsContainer;

    public AnalyticsView(DatabaseManager dbManager, DataCache cache) {
        this.dbManager = dbManager;
        this.cache = cache;
        this.root = buildView();
    }

    public VBox getView() {
        return root;
    }

    public void refresh() {
        refresh(null);
    }

    public void refresh(Runnable onComplete) {
        chartsContainer.getChildren().clear();
        populateCharts(onComplete);
    }

    private VBox buildView() {
        VBox container = new VBox(0);
        container.getStyleClass().add("analytics-container");

        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 20, 10, 20));
        header.getStyleClass().add("analytics-header");

        Label title = new Label("\uD83D\uDCCA Analytics Dashboard");
        title.getStyleClass().add("analytics-title");

        header.getChildren().add(title);

        chartsContainer = new VBox(20);
        chartsContainer.setPadding(new Insets(20));

        ScrollPane scroll = new ScrollPane(chartsContainer);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("analytics-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        container.getChildren().addAll(header, scroll);

        // Load charts on background thread
        populateCharts(null);

        return container;
    }

    private void populateCharts(Runnable onComplete) {
        Task<ChartData> task = new Task<>() {
            @Override
            protected ChartData call() throws Exception {
                cache.refresh();
                return gatherData();
            }
        };

        task.setOnSucceeded(e -> {
            ChartData data = task.getValue();
            buildChartUI(data);
            if (onComplete != null) {
                onComplete.run();
            }
        });

        task.setOnFailed(e -> {
            Label errorLabel = new Label("Failed to load analytics: " + task.getException().getMessage());
            errorLabel.setStyle("-fx-text-fill: #e74c3c;");
            chartsContainer.getChildren().add(errorLabel);
            if (onComplete != null) {
                onComplete.run();
            }
        });

        new Thread(task, "AnalyticsLoad").start();
    }

    private void buildChartUI(ChartData data) {
        if (data.totalEvents == 0) {
            VBox emptyState = new VBox(12);
            emptyState.setAlignment(Pos.CENTER);
            emptyState.setPadding(new Insets(60));

            Label emptyIcon = new Label("\uD83D\uDCCA");
            emptyIcon.setStyle("-fx-font-size: 64px;");

            Label emptyTitle = new Label("No Data Available");
            emptyTitle.getStyleClass().add("empty-state-title");

            Label emptyDesc = new Label("Import events or add them manually to see analytics.");
            emptyDesc.getStyleClass().add("empty-state-desc");

            emptyState.getChildren().addAll(emptyIcon, emptyTitle, emptyDesc);
            chartsContainer.getChildren().add(emptyState);
            return;
        }

        // Row 1: Summary cards
        HBox summaryRow = buildSummaryCards(data);

        // Row 2: Event type pie + Peak hours bar
        HBox row1 = new HBox(20);
        row1.setAlignment(Pos.TOP_CENTER);

        VBox eventTypePie = buildEventTypePieChart(data);
        VBox peakHoursBar = buildPeakHoursBarChart(data);
        HBox.setHgrow(eventTypePie, Priority.ALWAYS);
        HBox.setHgrow(peakHoursBar, Priority.ALWAYS);
        row1.getChildren().addAll(eventTypePie, peakHoursBar);

        // Row 3: Building utilization + Department events
        HBox row2 = new HBox(20);
        row2.setAlignment(Pos.TOP_CENTER);

        VBox buildingPie = buildBuildingUtilizationChart(data);
        VBox deptBar = buildDepartmentBarChart(data);
        HBox.setHgrow(buildingPie, Priority.ALWAYS);
        HBox.setHgrow(deptBar, Priority.ALWAYS);
        row2.getChildren().addAll(buildingPie, deptBar);

        chartsContainer.getChildren().addAll(summaryRow, row1, row2);
    }

    private HBox buildSummaryCards(ChartData data) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);

        row.getChildren().addAll(
                summaryCard("\uD83D\uDCC5", "Total Events", String.valueOf(data.totalEvents)),
                summaryCard("\u26A0", "Conflicts", String.valueOf(data.conflictCount)),
                summaryCard("\uD83C\uDFE2", "Rooms Used", String.valueOf(data.roomsUsed)),
                summaryCard("\u23F0", "Avg Duration", data.avgDurationMin + " min"),
                summaryCard("\uD83D\uDCCA", "Utilization", data.utilizationPercent + "%")
        );
        return row;
    }

    private VBox summaryCard(String icon, String label, String value) {
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 24px;");

        Label valueLbl = new Label(value);
        valueLbl.getStyleClass().add("summary-value");

        Label labelLbl = new Label(label);
        labelLbl.getStyleClass().add("summary-label");

        VBox card = new VBox(4, iconLbl, valueLbl, labelLbl);
        card.getStyleClass().add("summary-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(14, 20, 14, 20));
        card.setMinWidth(130);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox buildEventTypePieChart(ChartData data) {
        PieChart pie = new PieChart();
        pie.setTitle("Event Type Distribution");
        pie.getStyleClass().add("analytics-chart");
        pie.setLabelsVisible(true);
        pie.setLegendVisible(true);

        for (Map.Entry<String, Integer> entry : data.eventsByType.entrySet()) {
            pie.getData().add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
        }

        pie.setPrefSize(400, 300);
        VBox wrapper = chartWrapper("Event Types", pie);
        return wrapper;
    }

    private VBox buildPeakHoursBarChart(ChartData data) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Hour");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Events");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Peak Hours Distribution");
        barChart.getStyleClass().add("analytics-chart");
        barChart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Events");

        for (int h = 7; h < 22; h++) {
            String hourStr = String.format("%02d:00", h);
            int count = data.eventsByHour.getOrDefault(h, 0);
            series.getData().add(new XYChart.Data<>(hourStr, count));
        }

        barChart.getData().add(series);
        barChart.setPrefSize(400, 300);

        VBox wrapper = chartWrapper("Peak Hours", barChart);
        return wrapper;
    }

    private VBox buildBuildingUtilizationChart(ChartData data) {
        PieChart pie = new PieChart();
        pie.setTitle("Room Utilization by Building");
        pie.getStyleClass().add("analytics-chart");
        pie.setLabelsVisible(true);

        for (Map.Entry<String, Integer> entry : data.eventsByBuilding.entrySet()) {
            pie.getData().add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
        }

        if (data.onlineEvents > 0) {
            pie.getData().add(new PieChart.Data("Online (" + data.onlineEvents + ")", data.onlineEvents));
        }

        pie.setPrefSize(400, 300);
        VBox wrapper = chartWrapper("Building Utilization", pie);
        return wrapper;
    }

    private VBox buildDepartmentBarChart(ChartData data) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Department");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Events");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Events per Department");
        barChart.getStyleClass().add("analytics-chart");
        barChart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Events");

        for (Map.Entry<String, Integer> entry : data.eventsByDepartment.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        barChart.getData().add(series);
        barChart.setPrefSize(400, 300);

        VBox wrapper = chartWrapper("Department Activity", barChart);
        return wrapper;
    }

    private VBox chartWrapper(String title, javafx.scene.Node chart) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("chart-wrapper-title");

        VBox wrapper = new VBox(8, titleLabel, chart);
        wrapper.getStyleClass().add("chart-wrapper");
        wrapper.setPadding(new Insets(16));
        wrapper.setAlignment(Pos.TOP_CENTER);
        return wrapper;
    }

    // ===== Data Gathering =====

    private ChartData gatherData() throws SQLException {
        ChartData data = new ChartData();

        List<ScheduledEvent> events = new ScheduledEventDAO(dbManager).findAll();
        cache.enrichAll(events);
        data.totalEvents = events.size();

        // Conflict count
        try {
            ConflictEngine engine = new ConflictEngine(dbManager, cache);
            data.conflictCount = engine.analyzeAll().size();
        } catch (Exception e) {
            data.conflictCount = 0;
        }

        // Events by type
        for (ScheduledEvent ev : events) {
            String type = ev.getEventType() != null ? ev.getEventType().getDisplay() : "Unknown";
            data.eventsByType.merge(type, 1, Integer::sum);
        }

        // Events by hour
        for (ScheduledEvent ev : events) {
            if (ev.getStartEpoch() != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(ev.getStartEpoch());
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                data.eventsByHour.merge(hour, 1, Integer::sum);
            }
        }

        // Events by building
        int onlineCount = 0;
        Set<Integer> usedRooms = new HashSet<>();
        for (ScheduledEvent ev : events) {
            Location loc = cache.getLocation(ev.getLocId());
            if (loc != null) {
                data.eventsByBuilding.merge(loc.getBuilding(), 1, Integer::sum);
                usedRooms.add(loc.getLocId());
            } else {
                onlineCount++;
            }
        }
        data.onlineEvents = onlineCount;
        data.roomsUsed = usedRooms.size();

        // Events by department
        for (ScheduledEvent ev : events) {
            Course c = cache.getCourse(ev.getCourseId());
            if (c != null) {
                Professor p = cache.getProfessor(c.getProfId());
                if (p != null) {
                    Department d = cache.getDepartment(p.getDeptId());
                    if (d != null) {
                        data.eventsByDepartment.merge(d.getName(), 1, Integer::sum);
                    }
                }
            }
        }

        // Avg duration
        if (!events.isEmpty()) {
            long totalDuration = events.stream()
                    .filter(e -> e.getStartEpoch() != null && e.getEndEpoch() != null)
                    .mapToLong(e -> e.getEndEpoch() - e.getStartEpoch())
                    .sum();
            data.avgDurationMin = (int) (totalDuration / events.size() / 60_000);
        }

        // Utilization: hours used / (rooms * weekday hours available)
        int totalRooms = cache.getAllLocations().size();
        if (totalRooms > 0 && !events.isEmpty()) {
            long totalHoursUsed = events.stream()
                    .filter(e -> e.getLocId() != null && e.getStartEpoch() != null && e.getEndEpoch() != null)
                    .mapToLong(e -> (e.getEndEpoch() - e.getStartEpoch()) / 3_600_000)
                    .sum();
            long availableSlots = (long) totalRooms * 5 * (END_HOUR - START_HOUR); // 5 weekdays
            data.utilizationPercent = availableSlots > 0 ? (int) (totalHoursUsed * 100 / availableSlots) : 0;
        }

        return data;
    }

    private static final int START_HOUR = 7;
    private static final int END_HOUR = 22;

    /** Internal data container for chart information. */
    private static class ChartData {
        int totalEvents = 0;
        int conflictCount = 0;
        int roomsUsed = 0;
        int avgDurationMin = 0;
        int utilizationPercent = 0;
        int onlineEvents = 0;
        Map<String, Integer> eventsByType = new LinkedHashMap<>();
        Map<Integer, Integer> eventsByHour = new TreeMap<>();
        Map<String, Integer> eventsByBuilding = new LinkedHashMap<>();
        Map<String, Integer> eventsByDepartment = new LinkedHashMap<>();
    }
}
