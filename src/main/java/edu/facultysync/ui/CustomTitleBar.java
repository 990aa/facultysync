package edu.facultysync.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * Custom undecorated title bar with minimize, maximize/restore, and close buttons.
 * Supports drag-to-move and double-click-to-maximize.
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
        setPrefHeight(38);
        setMinHeight(38);
        setMaxHeight(38);

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

        // Resize cursor regions
        setupResizeHandlers(stage);
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
        btn.setPrefSize(46, 38);
        btn.setMinSize(46, 38);
        btn.setMaxSize(46, 38);
        return btn;
    }

    private void setupResizeHandlers(Stage stage) {
        final int RESIZE_MARGIN = 6;

        stage.getScene();

        // We'll add resize support via the scene root later
        // For now, the title bar handles move and maximize
    }

    public boolean isMaximized() {
        return maximized;
    }
}
