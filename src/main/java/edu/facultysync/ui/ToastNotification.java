package edu.facultysync.ui;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Non-blocking toast notification system.
 * Supports SUCCESS, WARNING, ERROR, and INFO types.
 * Toast slides in from top-right and fades out automatically.
 */
public class ToastNotification {

    public enum ToastType {
        SUCCESS("toast-success", "\u2713"),  // checkmark
        WARNING("toast-warning", "\u26A0"),  // warning sign
        ERROR("toast-error", "\u2717"),      // X mark
        INFO("toast-info", "\u2139");        // info circle

        final String styleClass;
        final String icon;

        ToastType(String styleClass, String icon) {
            this.styleClass = styleClass;
            this.icon = icon;
        }
    }

    private static VBox toastContainer;
    private static final int MAX_VISIBLE_TOASTS = 5;
    private static final int MAX_PENDING_REQUESTS = 200;
    private static final AtomicInteger pendingUiRequests = new AtomicInteger(0);
    private static final LinkedHashMap<HBox, SequentialTransition> activeToasts = new LinkedHashMap<>();

    /**
     * Initialize the toast container and attach it to the given StackPane root.
     * Call once at startup.
     */
    public static void initialize(StackPane overlay) {
        toastContainer = new VBox(8);
        toastContainer.setAlignment(Pos.TOP_RIGHT);
        toastContainer.setPadding(new Insets(50, 20, 0, 0));
        toastContainer.setPickOnBounds(false);
        toastContainer.setMouseTransparent(false);
        activeToasts.clear();
        pendingUiRequests.set(0);
        overlay.getChildren().add(toastContainer);
        StackPane.setAlignment(toastContainer, Pos.TOP_RIGHT);
    }

    /**
     * Show a toast notification.
     */
    public static void show(String message, ToastType type) {
        show(message, type, 4.0);
    }

    /**
     * Show a toast with custom duration (seconds).
     */
    public static void show(String message, ToastType type, double durationSeconds) {
        if (toastContainer == null) return;

        if (pendingUiRequests.incrementAndGet() > MAX_PENDING_REQUESTS) {
            pendingUiRequests.decrementAndGet();
            return;
        }

        javafx.application.Platform.runLater(() -> {
            try {
                HBox toast = buildToast(message, type);

                // Start invisible
                toast.setOpacity(0);
                toast.setTranslateX(300);

                if (activeToasts.size() >= MAX_VISIBLE_TOASTS) {
                    HBox oldest = activeToasts.keySet().iterator().next();
                    dismissToast(oldest);
                }

                toastContainer.getChildren().add(toast);

                // Slide in
                TranslateTransition slideIn = new TranslateTransition(Duration.millis(350), toast);
                slideIn.setFromX(300);
                slideIn.setToX(0);
                slideIn.setInterpolator(Interpolator.EASE_OUT);

                FadeTransition fadeIn = new FadeTransition(Duration.millis(350), toast);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);

                ParallelTransition showAnim = new ParallelTransition(slideIn, fadeIn);

                // Fade out after delay
                PauseTransition pause = new PauseTransition(Duration.seconds(durationSeconds));

                FadeTransition fadeOut = new FadeTransition(Duration.millis(500), toast);
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);

                TranslateTransition slideOut = new TranslateTransition(Duration.millis(500), toast);
                slideOut.setToX(300);
                slideOut.setInterpolator(Interpolator.EASE_IN);

                ParallelTransition hideAnim = new ParallelTransition(fadeOut, slideOut);
                hideAnim.setOnFinished(e -> dismissToast(toast));

                SequentialTransition sequence = new SequentialTransition(showAnim, pause, hideAnim);
                activeToasts.put(toast, sequence);
                sequence.play();

                // Click to dismiss early
                toast.setOnMouseClicked(e -> dismissToast(toast));
            } finally {
                pendingUiRequests.decrementAndGet();
            }
        });
    }

    private static void dismissToast(HBox toast) {
        SequentialTransition sequence = activeToasts.remove(toast);
        if (sequence != null) {
            sequence.stop();
        }
        if (toastContainer != null) {
            toastContainer.getChildren().remove(toast);
        }
    }

    private static HBox buildToast(String message, ToastType type) {
        Label iconLabel = new Label(type.icon);
        iconLabel.getStyleClass().add("toast-icon");

        Label msgLabel = new Label(message);
        msgLabel.getStyleClass().add("toast-message");
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(350);

        Label closeLabel = new Label("\u2715");
        closeLabel.getStyleClass().add("toast-close");

        HBox toast = new HBox(10, iconLabel, msgLabel, closeLabel);
        toast.getStyleClass().addAll("toast", type.styleClass);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setPadding(new Insets(12, 16, 12, 16));
        toast.setMaxWidth(400);
        toast.setMinWidth(280);

        return toast;
    }
}
