package edu.facultysync.ui;

import edu.facultysync.core.AppModule;
import edu.facultysync.db.DatabaseManager;
import edu.facultysync.db.SeedData;
import edu.facultysync.model.Department;
import edu.facultysync.service.DataCache;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardAnalyticsUiTest {

    @BeforeAll
    void ensureFxStarted() throws Exception {
        // Keep one startup path for this class without requiring global test ordering.
        CountDownLatch startupLatch = new CountDownLatch(1);
        try {
            Platform.setImplicitExit(false);
            Platform.startup(startupLatch::countDown);
        } catch (IllegalStateException e) {
            Platform.setImplicitExit(false);
            startupLatch.countDown();
        }
        assertTrue(startupLatch.await(5, TimeUnit.SECONDS));
    }

    @AfterAll
    void noopTearDown() {
        // Intentionally empty: JavaFX platform stays alive across tests.
    }

    @Test
    void dashboardSidebar_isScrollable_noImportButton_noFilterVisible() throws Exception {
        DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        dbManager.initializeSchema();
        SeedData.seed(dbManager);

        AppModule appModule = AppModule.create(dbManager);
        AtomicReference<Stage> stageRef = new AtomicReference<>();

        onFx(() -> {
            Stage stage = new Stage(StageStyle.UNDECORATED);
            DashboardController dashboard = new DashboardController(appModule, stage);
            Scene scene = new Scene(new VBox(dashboard.getRoot()), 920, 560);
            stage.setScene(scene);
            stageRef.set(stage);
        });

        try {
            waitUntil(() -> onFxCall(() -> {
                Parent root = stageRef.get().getScene().getRoot();
                root.applyCss();
                root.layout();
                return root.lookup("#departmentCombo") != null;
            }), 8000);

            ScrollPane sidebarScroll = onFxCall(() -> {
                VBox root = (VBox) stageRef.get().getScene().getRoot();
                StackPane dashboardRoot = (StackPane) root.getChildren().get(0);
                BorderPane borderPane = (BorderPane) dashboardRoot.getChildren().get(0);
                return (ScrollPane) borderPane.getLeft();
            });

            assertNotNull(sidebarScroll, "Sidebar should be wrapped in a ScrollPane");

            boolean hasImportButton = onFxCall(() -> stageRef.get().getScene().lookup("#importBtn") != null);
            assertFalse(hasImportButton, "Import CSV button should be removed from sidebar");

            @SuppressWarnings("unchecked")
            ComboBox<Department> combo = (ComboBox<Department>) onFxCall(() -> stageRef.get().getScene().lookup("#departmentCombo"));
            assertNotNull(combo, "Department filter combo should exist");

            Department selected = onFxCall(() -> combo.getSelectionModel().getSelectedItem());
            assertNotNull(selected);
            assertEquals("No Filter (All Departments)", selected.getName());

            boolean hasNoFilterChoice = onFxCall(() -> combo.getItems().stream()
                    .anyMatch(d -> "No Filter (All Departments)".equals(d.getName())));
            assertTrue(hasNoFilterChoice, "Department combo should include a no-filter option");

            String comboStyle = onFxCall(combo::getStyle);
            assertTrue(comboStyle.contains("-fx-text-fill: black"), "Department combo text should be visible (black)");

            boolean sidebarScrollable = onFxCall(() -> {
                sidebarScroll.applyCss();
                sidebarScroll.layout();
                double contentHeight = sidebarScroll.getContent().prefHeight(sidebarScroll.getWidth());
                double viewportHeight = sidebarScroll.getViewportBounds().getHeight();
                return contentHeight > viewportHeight;
            });
            assertTrue(sidebarScrollable, "Sidebar should be vertically scrollable");

            onFx(() -> sidebarScroll.setVvalue(1.0));
            double vvalue = onFxCall(sidebarScroll::getVvalue);
            assertTrue(vvalue >= 0.95, "Sidebar should scroll to the bottom");
        } finally {
            closeStage(stageRef.get());
            dbManager.close();
        }
    }

    @Test
    void homePage_removesQuickActionsSection() throws Exception {
        DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        dbManager.initializeSchema();
        SeedData.seed(dbManager);

        DataCache cache = new DataCache(dbManager);
        cache.refresh();

        AtomicReference<Stage> stageRef = new AtomicReference<>();

        onFx(() -> {
            Stage stage = new Stage(StageStyle.UNDECORATED);
            HomePage homePage = new HomePage(dbManager, cache, null);
            Scene scene = new Scene(homePage.getContent(), 920, 560);
            stage.setScene(scene);
            stageRef.set(stage);
        });

        try {
            waitUntil(() -> onFxCall(() -> containsLabelContaining(stageRef.get().getScene().getRoot(), "Recent Events")), 10000);
            boolean hasQuickActions = onFxCall(() -> containsLabelContaining(stageRef.get().getScene().getRoot(), "Quick Actions"));
            assertFalse(hasQuickActions, "Home page should not render Quick Actions section");
        } finally {
            closeStage(stageRef.get());
            dbManager.close();
        }
    }

    @Test
    void analyticsCharts_areComplete_andScrollableToBottom() throws Exception {
        DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        dbManager.initializeSchema();
        SeedData.seed(dbManager);

        DataCache cache = new DataCache(dbManager);
        cache.refresh();

        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<AnalyticsView> analyticsRef = new AtomicReference<>();

        onFx(() -> {
            Stage stage = new Stage(StageStyle.UNDECORATED);
            AnalyticsView analyticsView = new AnalyticsView(dbManager, cache, null);
            Scene scene = new Scene(analyticsView.getView(), 960, 560);
            stage.setScene(scene);
            stageRef.set(stage);
            analyticsRef.set(analyticsView);
        });

        try {
            waitUntil(() -> onFxCall(() -> {
                Parent root = stageRef.get().getScene().getRoot();
                root.applyCss();
                root.layout();
                return collectNodes(root, BarChart.class).size() >= 2;
            }), 15000);

                List<BarChart> bars = onFxCall(() -> collectNodes(stageRef.get().getScene().getRoot(), BarChart.class));
            List<PieChart> pies = onFxCall(() -> collectNodes(stageRef.get().getScene().getRoot(), PieChart.class));

            assertTrue(bars.size() >= 2, "Analytics should render bar charts");
            assertTrue(pies.size() >= 2, "Analytics should render pie charts");

            for (BarChart bar : bars) {
                assertTrue(bar.isLegendVisible(), "Bar chart legend should be visible");
                assertNotNull(bar.getXAxis(), "Bar chart should expose X axis");
                assertNotNull(bar.getYAxis(), "Bar chart should expose Y axis");
                assertNotNull(bar.getXAxis().getLabel(), "X axis label should be present");
                assertNotNull(bar.getYAxis().getLabel(), "Y axis label should be present");
                assertFalse(bar.getXAxis().getLabel().isBlank(), "X axis label should not be blank");
                assertFalse(bar.getYAxis().getLabel().isBlank(), "Y axis label should not be blank");
            }

            for (PieChart pie : pies) {
                assertTrue(pie.isLegendVisible(), "Pie chart legend should be visible");
            }

            ScrollPane analyticsScroll = onFxCall(() -> collectNodes(stageRef.get().getScene().getRoot(), ScrollPane.class)
                    .stream()
                    .filter(sp -> sp.getStyleClass().contains("analytics-scroll"))
                    .findFirst()
                    .orElse(null));
            assertNotNull(analyticsScroll, "Analytics view should use a ScrollPane");

            boolean canScroll = onFxCall(() -> {
                analyticsScroll.applyCss();
                analyticsScroll.layout();
                double contentHeight = analyticsScroll.getContent().prefHeight(analyticsScroll.getWidth());
                double viewportHeight = analyticsScroll.getViewportBounds().getHeight();
                return contentHeight > viewportHeight;
            });
            assertTrue(canScroll, "Analytics content should extend beyond viewport and be scrollable");

            onFx(() -> analyticsScroll.setVvalue(1.0));
            double vvalue = onFxCall(analyticsScroll::getVvalue);
            assertTrue(vvalue >= 0.95, "Analytics should scroll to the bottom");

            // Ensure refresh path also keeps charts present.
            onFx(() -> analyticsRef.get().refresh());
            waitUntil(() -> onFxCall(() -> {
                Parent root = stageRef.get().getScene().getRoot();
                root.applyCss();
                root.layout();
                return collectNodes(root, BarChart.class).size() >= 2;
            }), 15000);
        } finally {
            closeStage(stageRef.get());
            dbManager.close();
        }
    }

    private void closeStage(Stage stage) throws Exception {
        if (stage == null) {
            return;
        }
        onFx(stage::close);
    }

    private boolean containsLabel(Node root, String expectedText) {
        for (Label label : collectNodes(root, Label.class)) {
            if (expectedText.equals(label.getText())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLabelContaining(Node root, String textFragment) {
        for (Label label : collectNodes(root, Label.class)) {
            String text = label.getText();
            if (text != null && text.contains(textFragment)) {
                return true;
            }
        }
        return false;
    }

    private <T extends Node> List<T> collectNodes(Node root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                found.addAll(collectNodes(child, type));
            }
        }
        return found;
    }

    private void waitUntil(BooleanSupplier condition, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Timed out waiting for condition");
    }

    private void onFx(ThrowingRunnable action) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS), "FX operation timed out");
        if (error.get() != null) {
            if (error.get() instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(error.get());
        }
    }

    private <T> T onFxCall(Callable<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        try {
            onFx(() -> result.set(action.call()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
