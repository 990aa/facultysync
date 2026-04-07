package edu.facultysync;

import edu.facultysync.service.NotificationService;
import edu.facultysync.ui.ToastNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks for application updates from the GitHub repository.
 * On startup, queries the GitHub Releases API for the latest version
 * and prompts the user to update if a newer version is available.
 */
public class UpdateChecker {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateChecker.class);

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/990aa/facultysync/releases/latest";

    /**
     * Check for updates asynchronously.
     * If a newer version is found, shows a dialog to the user.
     */
    public static void checkForUpdates(Stage owner) {
        Thread updateThread = new Thread(() -> {
            try {
                String latestVersion = fetchLatestVersion();
                if (latestVersion != null && isNewer(latestVersion, App.VERSION)) {
                    Platform.runLater(() -> showUpdateDialog(owner, latestVersion));
                    NotificationService.info("Update Available",
                            "FacultySync v" + latestVersion + " is available. You are running v" + App.VERSION);
                }
            } catch (Exception e) {
                // Keep startup non-blocking, but log details for diagnostics.
                LOG.debug("Update check failed", e);
            }
        }, "UpdateChecker");
        updateThread.setDaemon(true);
        updateThread.start();
    }

    /**
     * Fetch the latest release version tag from GitHub API.
     */
    private static String fetchLatestVersion() throws Exception {
        URL url = URI.create(GITHUB_API_URL).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("User-Agent", "FacultySync/" + App.VERSION);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() != 200) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        // Simple JSON parsing for "tag_name": "vX.Y.Z"
        String json = sb.toString();
        Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Compare semantic versions. Returns true if latest > current.
     */
    static boolean isNewer(String latest, String current) {
        try {
            int[] l = parseVersion(latest);
            int[] c = parseVersion(current);
            for (int i = 0; i < 3; i++) {
                if (l[i] > c[i]) return true;
                if (l[i] < c[i]) return false;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        // Strip leading 'v' if present
        String v = version.startsWith("v") ? version.substring(1) : version;
        String[] parts = v.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try {
                result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    /**
     * Show a dialog informing the user about the available update.
     */
    private static void showUpdateDialog(Stage owner, String latestVersion) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Update Available");
        alert.setHeaderText("FacultySync v" + latestVersion + " is available!");
        alert.setContentText(
                "You are currently running v" + App.VERSION + ".\n\n" +
                "Click 'Update' to download the latest version from GitHub,\n" +
                "or 'Later' to skip this update.");

        ButtonType updateBtn = new ButtonType("Update", ButtonBar.ButtonData.OK_DONE);
        ButtonType laterBtn = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(updateBtn, laterBtn);

        alert.showAndWait().ifPresent(response -> {
            if (response == updateBtn) {
                downloadUpdate(latestVersion);
            }
        });
    }

    /**
     * Open the GitHub releases page for the user to download the latest version.
     */
    private static void downloadUpdate(String version) {
        try {
            String url = "https://github.com/990aa/facultysync/releases/tag/v" + version;
            java.awt.Desktop.getDesktop().browse(URI.create(url));
            ToastNotification.show("Opening download page in browser...",
                    ToastNotification.ToastType.INFO);
        } catch (Exception e) {
            ToastNotification.show("Could not open browser: " + e.getMessage(),
                    ToastNotification.ToastType.ERROR);
        }
    }
}
