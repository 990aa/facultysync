package edu.facultysync.ui;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the custom window title bar (minimize, maximize/restore, close).
 * Uses a single shared JavaFX Stage to avoid FX thread contention.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomTitleBarTest {

    private Stage stage;
    private CustomTitleBar titleBar;

    @BeforeAll
    void initFxAndBuildBar() throws Exception {
        CountDownLatch startupLatch = new CountDownLatch(1);
        try {
            Platform.startup(startupLatch::countDown);
        } catch (IllegalStateException e) {
            startupLatch.countDown(); // already started
        }
        assertTrue(startupLatch.await(5, TimeUnit.SECONDS), "JavaFX platform should start");

        // Create stage + title bar on FX thread
        CountDownLatch buildLatch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                stage = new Stage(StageStyle.UNDECORATED);
                titleBar = new CustomTitleBar(stage, "FacultySync v1.0");
                Region content = new Region();
                VBox container = new VBox(titleBar, content);
                Scene scene = new Scene(container, 800, 600);
                stage.setScene(scene);
                titleBar.installResizeHandlers(stage);
                stage.show();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                buildLatch.countDown();
            }
        });
        assertTrue(buildLatch.await(10, TimeUnit.SECONDS), "Stage should be created");
        if (err.get() != null) fail(err.get());
    }

    @AfterAll
    void cleanup() throws Exception {
        if (stage != null) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> { stage.close(); latch.countDown(); });
            latch.await(5, TimeUnit.SECONDS);
        }
    }

    /** Run an assertion block on the FX thread and wait. */
    private void onFx(Runnable action) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable t) { error.set(t); }
            finally { latch.countDown(); }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "FX thread should respond");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) throw ae;
            fail(error.get());
        }
    }

    // ————— Structure tests —————

    @Test @Order(1)
    void titleBar_hasCorrectId() throws Exception {
        onFx(() -> assertEquals("customTitleBar", titleBar.getId()));
    }

    @Test @Order(2)
    void titleBar_hasCorrectChildCount() throws Exception {
        // icon, title label, spacer, minimize, maximize, close
        onFx(() -> assertEquals(6, titleBar.getChildren().size()));
    }

    @Test @Order(3)
    void titleBar_containsAllExpectedNodes() throws Exception {
        onFx(() -> {
            assertNotNull(titleBar.lookup("#titleBarIcon"), "Should have icon");
            assertNotNull(titleBar.lookup("#titleBarLabel"), "Should have label");
            assertNotNull(titleBar.lookup("#minimizeBtn"), "Should have minimize");
            assertNotNull(titleBar.lookup("#maximizeBtn"), "Should have maximize");
            assertNotNull(titleBar.lookup("#closeBtn"), "Should have close");
        });
    }

    @Test @Order(4)
    void titleBar_showsTitleText() throws Exception {
        onFx(() -> {
            Label lbl = (Label) titleBar.lookup("#titleBarLabel");
            assertEquals("FacultySync v1.0", lbl.getText());
        });
    }

    @Test @Order(5)
    void titleBar_heightIs40() throws Exception {
        onFx(() -> {
            assertEquals(40, titleBar.getMinHeight());
            assertEquals(40, titleBar.getPrefHeight());
            assertEquals(40, titleBar.getMaxHeight());
        });
    }

    @Test @Order(6)
    void titleBar_isVisibleAndManaged() throws Exception {
        onFx(() -> {
            assertTrue(titleBar.isVisible(), "Title bar should be visible");
            assertTrue(titleBar.isManaged(), "Title bar should be managed");
        });
    }

    // ————— Button text tests —————

    @Test @Order(10)
    void minimizeButton_hasText() throws Exception {
        onFx(() -> {
            String text = titleBar.getMinimizeButton().getText();
            assertNotNull(text);
            assertFalse(text.isEmpty(), "Minimize button should have text");
        });
    }

    @Test @Order(11)
    void maximizeButton_hasText() throws Exception {
        onFx(() -> {
            String text = titleBar.getMaximizeButton().getText();
            assertNotNull(text);
            assertFalse(text.isEmpty(), "Maximize button should have text");
        });
    }

    @Test @Order(12)
    void closeButton_hasText() throws Exception {
        onFx(() -> {
            String text = titleBar.getCloseButton().getText();
            assertNotNull(text);
            assertEquals("\u2715", text, "Close button should show X mark");
        });
    }

    // ————— Maximize toggle tests —————

    @Test @Order(20)
    void initialState_isNotMaximized() throws Exception {
        onFx(() -> assertFalse(titleBar.isMaximized(), "Should not start maximized"));
    }

    @Test @Order(21)
    void maximize_togglesState() throws Exception {
        onFx(() -> {
            assertFalse(titleBar.isMaximized());
            titleBar.getMaximizeButton().fire();
            assertTrue(titleBar.isMaximized(), "Should be maximized after click");
            // Restore
            titleBar.getMaximizeButton().fire();
            assertFalse(titleBar.isMaximized(), "Should restore after second click");
        });
    }

    // ————— Style class tests —————

    @Test @Order(30)
    void titleBar_hasStyleClass() throws Exception {
        onFx(() -> assertTrue(titleBar.getStyleClass().contains("custom-title-bar")));
    }

    @Test @Order(31)
    void buttons_haveStyleClasses() throws Exception {
        onFx(() -> {
            assertTrue(titleBar.getMinimizeButton().getStyleClass().contains("title-btn"));
            assertTrue(titleBar.getMinimizeButton().getStyleClass().contains("title-btn-minimize"));
            assertTrue(titleBar.getMaximizeButton().getStyleClass().contains("title-btn-maximize"));
            assertTrue(titleBar.getCloseButton().getStyleClass().contains("title-btn-close"));
        });
    }

    // ————— Integration —————

    @Test @Order(40)
    void titleBar_isFirstChildInAppContainer() throws Exception {
        onFx(() -> {
            VBox container = (VBox) stage.getScene().getRoot();
            assertEquals(titleBar, container.getChildren().get(0), "Title bar should be first child");
        });
    }

    @Test @Order(41)
    void installResizeHandlers_doesNotThrow() throws Exception {
        // Already called in @BeforeAll – if we got here, it didn't throw
        assertNotNull(titleBar);
    }

    // ————— Icon tests —————

    @Test @Order(50)
    void titleBar_hasIconNode() throws Exception {
        onFx(() -> {
            Node icon = titleBar.getIconNode();
            assertNotNull(icon, "Title bar should have an icon node");
            assertEquals("titleBarIcon", icon.getId(), "Icon node should have correct ID");
        });
    }

    @Test @Order(51)
    void titleBar_iconIsInChildList() throws Exception {
        onFx(() -> {
            Node icon = titleBar.getIconNode();
            assertTrue(titleBar.getChildren().contains(icon), "Icon should be in child list");
            assertEquals(0, titleBar.getChildren().indexOf(icon), "Icon should be first child");
        });
    }

    @Test @Order(52)
    void titleBar_iconLookupById() throws Exception {
        onFx(() -> {
            Node found = titleBar.lookup("#titleBarIcon");
            assertNotNull(found, "Should find icon by #titleBarIcon lookup");
        });
    }

    // ————— Window does NOT cover taskbar (visual bounds) —————

    @Test @Order(60)
    void maximize_usesVisualBounds() throws Exception {
        onFx(() -> {
            // Start un-maximized
            if (titleBar.isMaximized()) titleBar.getMaximizeButton().fire();
            assertFalse(titleBar.isMaximized());

            // Maximize
            titleBar.getMaximizeButton().fire();
            assertTrue(titleBar.isMaximized());

                Rectangle2D visual = screenBoundsForStage();
            // Stage should not exceed visual bounds (which exclude the OS taskbar)
            assertTrue(stage.getX() >= visual.getMinX(),
                    "Stage X should be >= visual bounds minX");
            assertTrue(stage.getY() >= visual.getMinY(),
                    "Stage Y should be >= visual bounds minY");
            assertTrue(stage.getWidth() <= visual.getWidth() + 1,
                    "Stage width should not exceed visual bounds width");
            assertTrue(stage.getHeight() <= visual.getHeight() + 1,
                    "Stage height should not exceed visual bounds height");

            // Restore
            titleBar.getMaximizeButton().fire();
            assertFalse(titleBar.isMaximized());
        });
    }

    @Test @Order(61)
    void initialState_stageIsNotFullscreen() throws Exception {
        onFx(() -> {
            Rectangle2D visual = screenBoundsForStage();
            // The test stage is 800x600 – should NOT fill the screen
            assertTrue(stage.getWidth() < visual.getWidth(),
                    "Stage should not fill screen width");
            assertTrue(stage.getHeight() < visual.getHeight(),
                    "Stage should not fill screen height");
        });
    }

    @Test @Order(62)
    void maximize_restore_returnsToPreviousSize() throws Exception {
        onFx(() -> {
            if (titleBar.isMaximized()) titleBar.getMaximizeButton().fire();

            double origW = stage.getWidth();
            double origH = stage.getHeight();

            titleBar.getMaximizeButton().fire(); // maximize
            assertTrue(titleBar.isMaximized());

            titleBar.getMaximizeButton().fire(); // restore
            assertFalse(titleBar.isMaximized());

            assertEquals(origW, stage.getWidth(), 2, "Width should restore");
            assertEquals(origH, stage.getHeight(), 2, "Height should restore");
        });
    }

    // ————— Button dimension tests —————

    @Test @Order(70)
    void controlButtons_have46x40Size() throws Exception {
        onFx(() -> {
            for (Button btn : new Button[]{ titleBar.getMinimizeButton(), titleBar.getMaximizeButton(), titleBar.getCloseButton() }) {
                assertEquals(46, btn.getPrefWidth(), "Button pref width should be 46");
                assertEquals(40, btn.getPrefHeight(), "Button pref height should be 40");
                assertEquals(46, btn.getMinWidth(), "Button min width should be 46");
                assertEquals(40, btn.getMinHeight(), "Button min height should be 40");
            }
        });
    }

    @Test @Order(71)
    void controlButtons_areNotFocusTraversable() throws Exception {
        onFx(() -> {
            assertFalse(titleBar.getMinimizeButton().isFocusTraversable());
            assertFalse(titleBar.getMaximizeButton().isFocusTraversable());
            assertFalse(titleBar.getCloseButton().isFocusTraversable());
        });
    }

    // ————— Maximize button text changes —————

    @Test @Order(80)
    void maximizeButton_textChangesOnToggle() throws Exception {
        onFx(() -> {
            if (titleBar.isMaximized()) titleBar.getMaximizeButton().fire();
            assertEquals("\u25A1", titleBar.getMaximizeButton().getText(), "Normal state: white square");

            titleBar.getMaximizeButton().fire();
            assertEquals("\u25A3", titleBar.getMaximizeButton().getText(), "Maximized state: filled square");

            titleBar.getMaximizeButton().fire();
            assertEquals("\u25A1", titleBar.getMaximizeButton().getText(), "Restored state: white square again");
        });
    }

    private Rectangle2D screenBoundsForStage() {
        var screens = Screen.getScreensForRectangle(
                stage.getX(),
                stage.getY(),
                Math.max(1, stage.getWidth()),
                Math.max(1, stage.getHeight())
        );
        Screen target = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        return target.getVisualBounds();
    }
}
