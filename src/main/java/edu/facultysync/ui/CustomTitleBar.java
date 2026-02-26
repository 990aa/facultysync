package edu.facultysync.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

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

    public CustomTitleBar(Stage stage, String title) {
        getStyleClass().add("custom-title-bar");
        setPadding(new Insets(0, 0, 0, 12));
        setAlignment(Pos.CENTER_LEFT);
        setPrefHeight(40);
        setMinHeight(40);
        setMaxHeight(40);

        // App icon (unicode glyph)
        Label icon = new Label("\uD83C\uDF93"); // graduation cap
        icon.getStyleClass().add("title-bar-icon");

        // Title text
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("title-bar-label");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Window control buttons
        Button minimizeBtn = createControlButton("\u2014", "title-btn-minimize"); // em dash
        Button maximizeBtn = createControlButton("\u25A1", "title-btn-maximize"); // square
        Button closeBtn = createControlButton("\u2715", "title-btn-close");       // X mark

        minimizeBtn.setOnAction(e -> stage.setIconified(true));

        maximizeBtn.setOnAction(e -> toggleMaximize(stage, maximizeBtn));

        closeBtn.setOnAction(e -> {
            stage.close();
            javafx.application.Platform.exit();
        });

        getChildren().addAll(icon, titleLabel, spacer, minimizeBtn, maximizeBtn, closeBtn);

        // Drag support
        setOnMousePressed(event -> {
            if (!maximized) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });

        setOnMouseDragged(event -> {
            if (maximized) {
                // Restore from maximized when dragging
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

        // Double-click to maximize/restore
        setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                toggleMaximize(stage, maximizeBtn);
            }
        });
    }

    /**
     * Attach resize handlers to the scene root after the scene is set.
     * Must be called after primaryStage.setScene().
     */
    public void installResizeHandlers(Stage stage) {
        final int RESIZE_MARGIN = 6;

        stage.getScene().getRoot().setOnMouseMoved(event -> {
            if (maximized) { stage.getScene().setCursor(Cursor.DEFAULT); return; }
            double x = event.getX();
            double y = event.getY();
            double w = stage.getScene().getWidth();
            double h = stage.getScene().getHeight();

            boolean left = x < RESIZE_MARGIN;
            boolean right = x > w - RESIZE_MARGIN;
            boolean top = y < RESIZE_MARGIN;
            boolean bottom = y > h - RESIZE_MARGIN;

            if (left && top) stage.getScene().setCursor(Cursor.NW_RESIZE);
            else if (right && top) stage.getScene().setCursor(Cursor.NE_RESIZE);
            else if (left && bottom) stage.getScene().setCursor(Cursor.SW_RESIZE);
            else if (right && bottom) stage.getScene().setCursor(Cursor.SE_RESIZE);
            else if (left) stage.getScene().setCursor(Cursor.W_RESIZE);
            else if (right) stage.getScene().setCursor(Cursor.E_RESIZE);
            else if (top) stage.getScene().setCursor(Cursor.N_RESIZE);
            else if (bottom) stage.getScene().setCursor(Cursor.S_RESIZE);
            else stage.getScene().setCursor(Cursor.DEFAULT);
        });

        final double[] dragStart = new double[4]; // x, y, w, h

        stage.getScene().getRoot().setOnMousePressed(event -> {
            dragStart[0] = event.getScreenX();
            dragStart[1] = event.getScreenY();
            dragStart[2] = stage.getWidth();
            dragStart[3] = stage.getHeight();
        });

        stage.getScene().getRoot().setOnMouseDragged(event -> {
            if (maximized) return;
            Cursor cursor = stage.getScene().getCursor();
            if (cursor == Cursor.DEFAULT) return;

            double dx = event.getScreenX() - dragStart[0];
            double dy = event.getScreenY() - dragStart[1];

            if (cursor == Cursor.E_RESIZE || cursor == Cursor.NE_RESIZE || cursor == Cursor.SE_RESIZE) {
                double newW = Math.max(stage.getMinWidth(), dragStart[2] + dx);
                stage.setWidth(newW);
            }
            if (cursor == Cursor.S_RESIZE || cursor == Cursor.SE_RESIZE || cursor == Cursor.SW_RESIZE) {
                double newH = Math.max(stage.getMinHeight(), dragStart[3] + dy);
                stage.setHeight(newH);
            }
            if (cursor == Cursor.W_RESIZE || cursor == Cursor.NW_RESIZE || cursor == Cursor.SW_RESIZE) {
                double newW = Math.max(stage.getMinWidth(), dragStart[2] - dx);
                if (newW > stage.getMinWidth()) {
                    stage.setWidth(newW);
                    stage.setX(event.getScreenX());
                }
            }
            if (cursor == Cursor.N_RESIZE || cursor == Cursor.NW_RESIZE || cursor == Cursor.NE_RESIZE) {
                double newH = Math.max(stage.getMinHeight(), dragStart[3] - dy);
                if (newH > stage.getMinHeight()) {
                    stage.setHeight(newH);
                    stage.setY(event.getScreenY());
                }
            }
        });
    }

    private void toggleMaximize(Stage stage, Button maximizeBtn) {
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

            var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            stage.setX(screen.getMinX());
            stage.setY(screen.getMinY());
            stage.setWidth(screen.getWidth());
            stage.setHeight(screen.getHeight());
            maximizeBtn.setText("\u25A3"); // filled square
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

    public boolean isMaximized() {
        return maximized;
    }
}
