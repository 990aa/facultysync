package edu.facultysync.service;

import java.awt.*;
import java.awt.TrayIcon.MessageType;

/**
 * Windows system-tray notification service.
 * Sends native OS toast notifications for schedule events,
 * conflict alerts, import completions, and update prompts.
 */
public class NotificationService {

    private static TrayIcon trayIcon;
    private static boolean available = false;

    /**
     * Initialize the system tray icon. Call once at startup.
     */
    public static void initialize() {
        if (!SystemTray.isSupported()) {
            System.err.println("System tray not supported – native notifications disabled.");
            return;
        }
        try {
            SystemTray tray = SystemTray.getSystemTray();

            // Use a simple generated image for the tray icon
            Image image = createTrayImage();
            trayIcon = new TrayIcon(image, "FacultySync");
            trayIcon.setImageAutoSize(true);
            tray.add(trayIcon);
            available = true;
        } catch (AWTException e) {
            System.err.println("Could not add system tray icon: " + e.getMessage());
        }
    }

    /**
     * Show a native Windows notification.
     *
     * @param title   Notification title
     * @param message Notification body text
     * @param type    INFO, WARNING, or ERROR
     */
    public static void notify(String title, String message, MessageType type) {
        if (!available || trayIcon == null) return;
        trayIcon.displayMessage(title, message, type);
    }

    /** Convenience: info notification. */
    public static void info(String title, String message) {
        notify(title, message, MessageType.INFO);
    }

    /** Convenience: warning notification. */
    public static void warning(String title, String message) {
        notify(title, message, MessageType.WARNING);
    }

    /** Convenience: error notification. */
    public static void error(String title, String message) {
        notify(title, message, MessageType.ERROR);
    }

    /** Remove tray icon on shutdown. */
    public static void shutdown() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * Create a small 16x16 tray icon image programmatically (green 'F' on dark bg).
     */
    private static Image createTrayImage() {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Dark background circle
        g.setColor(new Color(44, 62, 80));
        g.fillRoundRect(0, 0, 16, 16, 4, 4);
        // Green "F" letter
        g.setColor(new Color(26, 188, 156));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("F", 3, 13);
        g.dispose();
        return img;
    }
}
