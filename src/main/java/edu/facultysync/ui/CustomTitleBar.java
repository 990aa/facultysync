package edu.facultysync.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.io.InputStream;

/**
 * Custom undecorated title bar with minimize, maximize/restore, and close buttons.
 * Supports drag-to-move, double-click-to-maximize, and edge-resize on all borders.
 * Uses the same dark background as the sidebar so it blends seamlessly.
 */
public class CustomTitleBar extends HBox {

    private double xOffset = 0;
    private double yOffset = 0;
    private boolean maximized = false;
    private double prevX, prevY, prevW, prevH;
    private final Button minimizeBtn;
    private final Button maximizeBtn;
    private final Button closeBtn;
    private final Node iconNode;

    public CustomTitleBar(Stage stage, String title) {
        setId("customTitleBar");
        getStyleClass().add("custom-title-bar");
        setPadding(new Insets(0, 0, 0, 12));
        setAlignment(Pos.CENTER_LEFT);
        setPrefHeight(40);
        setMinHeight(40);
        setMaxHeight(40);
        setVisible(true);
        setManaged(true);

        // App icon – use app-icon.png if available, otherwise fall back to Unicode emoji
        InputStream iconStream = getClass().getResourceAsStream("/app-icon.png");
        if (iconStream != null) {
            ImageView iconImage = new ImageView(new Image(iconStream, 22, 22, true, true));
            iconImage.setId("titleBarIcon");
            iconImage.getStyleClass().add("title-bar-icon");
            iconNode = iconImage;
        } else {
            Label iconLabel = new Label("\uD83C\uDF93");
            iconLabel.setId("titleBarIcon");
            iconLabel.getStyleClass().add("title-bar-icon");
            iconNode = iconLabel;
        }

        // Title text
        Label titleLabel = new Label(title);
        titleLabel.setId("titleBarLabel");
        titleLabel.getStyleClass().add("title-bar-label");

        // Spacer pushes buttons to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Window control buttons – use common font glyphs
        minimizeBtn = createControlButton("\u2013", "title-btn-minimize"); // en dash
        minimizeBtn.setId("minimizeBtn");
        maximizeBtn = createControlButton("\u25A1", "title-btn-maximize"); // white square
        maximizeBtn.setId("maximizeBtn");
        closeBtn = createControlButton("\u2715", "title-btn-close");      // multiplication X
        closeBtn.setId("closeBtn");

        minimizeBtn.setOnAction(e -> stage.setIconified(true));
        maximizeBtn.setOnAction(e -> toggleMaximize(stage));
        closeBtn.setOnAction(e -> {
            stage.close();
            javafx.application.Platform.exit();
        });

        getChildren().addAll(iconNode, titleLabel, spacer, minimizeBtn, maximizeBtn, closeBtn);

        // ---- Drag to move ----
        setOnMousePressed(event -> {
            if (!maximized) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });

        setOnMouseDragged(event -> {
            if (maximized) {
                maximized = false;
                maximizeBtn.setText("\u25A1");
                stage.setWidth(prevW);
                stage.setHeight(prevH);
                stage.setX(event.getScreenX() - prevW / 2);
                stage.setY(event.getScreenY());
                xOffset = prevW / 2;
                yOffset = event.getSceneY();
            }
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });

        // Double-click to toggle maximize
        setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                toggleMaximize(stage);
            }
        });
    }

    /**
     * Attach edge-resize handlers to the scene root.
     * Must be called AFTER primaryStage.setScene().
     */
    public void installResizeHandlers(Stage stage) {
        final int RESIZE_MARGIN = 6;
        Node root = stage.getScene().getRoot();

        root.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (maximized) { stage.getScene().setCursor(Cursor.DEFAULT); return; }
            double x = event.getX(), y = event.getY();
            double w = stage.getScene().getWidth(), h = stage.getScene().getHeight();

            boolean r = x > w - RESIZE_MARGIN;
            boolean b = y > h - RESIZE_MARGIN;

            // Restrict resizing to right/bottom edges to avoid top/left jitter on undecorated windows.
            if (r && b) stage.getScene().setCursor(Cursor.SE_RESIZE);
            else if (r) stage.getScene().setCursor(Cursor.E_RESIZE);
            else if (b) stage.getScene().setCursor(Cursor.S_RESIZE);
            else stage.getScene().setCursor(Cursor.DEFAULT);
        });

        final double[] dragStart = new double[4]; // screenX, screenY, stageW, stageH

        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            dragStart[0] = event.getScreenX();
            dragStart[1] = event.getScreenY();
            dragStart[2] = stage.getWidth();
            dragStart[3] = stage.getHeight();
        });

        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (maximized) return;
            Cursor cursor = stage.getScene().getCursor();
            if (cursor == null || cursor == Cursor.DEFAULT) return;

            double dx = event.getScreenX() - dragStart[0];
            double dy = event.getScreenY() - dragStart[1];

            if (cursor == Cursor.E_RESIZE || cursor == Cursor.SE_RESIZE) {
                stage.setWidth(Math.max(stage.getMinWidth(), dragStart[2] + dx));
            }
            if (cursor == Cursor.S_RESIZE || cursor == Cursor.SE_RESIZE) {
                stage.setHeight(Math.max(stage.getMinHeight(), dragStart[3] + dy));
            }
        });
    }

    private void toggleMaximize(Stage stage) {
        if (maximized) {
            stage.setX(prevX);
            stage.setY(prevY);
            stage.setWidth(prevW);
            stage.setHeight(prevH);
            maximizeBtn.setText("\u25A1");
            maximized = false;
        } else {
            prevX = stage.getX();
            prevY = stage.getY();
            prevW = stage.getWidth();
            prevH = stage.getHeight();

            var screens = javafx.stage.Screen.getScreensForRectangle(
                    stage.getX(),
                    stage.getY(),
                    Math.max(1, stage.getWidth()),
                    Math.max(1, stage.getHeight())
            );
            var targetScreen = screens.isEmpty() ? javafx.stage.Screen.getPrimary() : screens.get(0);
            var bounds = targetScreen.getVisualBounds();

            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
            maximizeBtn.setText("\u25A3");
            maximized = true;
        }
    }

    private Button createControlButton(String text, String styleClass) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll("title-btn", styleClass);
        btn.setFocusTraversable(false);
        btn.setPrefSize(46, 40);
        btn.setMinSize(46, 40);
        btn.setMaxSize(46, 40);
        return btn;
    }

    public boolean isMaximized() { return maximized; }
    public Button getMinimizeButton() { return minimizeBtn; }
    public Button getMaximizeButton() { return maximizeBtn; }
    public Button getCloseButton() { return closeBtn; }
    public Node getIconNode() { return iconNode; }
}
