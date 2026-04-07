package edu.facultysync.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Centralized time policy for parsing and formatting schedule timestamps.
 *
 * <p>Default policy is UTC to keep rendering stable across machines.
 * The zone can be overridden with:
 * <ul>
 *   <li>System property: facultysync.timezone</li>
 *   <li>Environment variable: FACULTYSYNC_TIMEZONE</li>
 * </ul>
 */
public final class TimePolicy {

    private static final String TZ_PROPERTY = "facultysync.timezone";
    private static final String TZ_ENV = "FACULTYSYNC_TIMEZONE";

    private static final ZoneId APP_ZONE = resolveZone();
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_TIME_WITH_ZONE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("EEE MM/dd");
    private static final DateTimeFormatter WEEK_RANGE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private TimePolicy() {}

    public static ZoneId zone() {
        return APP_ZONE;
    }

    public static String zoneLabel() {
        return APP_ZONE.getId();
    }

    public static Long parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            LocalDateTime dt = LocalDateTime.parse(text.trim(), DATE_TIME_FMT);
            return dt.atZone(APP_ZONE).toInstant().toEpochMilli();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public static String formatEpoch(Long epochMs) {
        if (epochMs == null) {
            return "";
        }
        return DATE_TIME_FMT.format(Instant.ofEpochMilli(epochMs).atZone(APP_ZONE));
    }

    public static String formatEpochWithZone(Long epochMs) {
        if (epochMs == null) {
            return "";
        }
        return DATE_TIME_WITH_ZONE_FMT.format(Instant.ofEpochMilli(epochMs).atZone(APP_ZONE))
                + " " + zoneLabel();
    }

    public static String formatDay(long epochMs) {
        return DAY_FMT.format(Instant.ofEpochMilli(epochMs).atZone(APP_ZONE));
    }

    public static String formatWeekRange(long epochMs) {
        return WEEK_RANGE_FMT.format(Instant.ofEpochMilli(epochMs).atZone(APP_ZONE));
    }

    public static String formatTime(Long epochMs) {
        if (epochMs == null) {
            return "";
        }
        return TIME_FMT.format(Instant.ofEpochMilli(epochMs).atZone(APP_ZONE));
    }

    private static ZoneId resolveZone() {
        String raw = System.getProperty(TZ_PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(TZ_ENV);
        }

        if (raw != null && !raw.isBlank()) {
            try {
                return ZoneId.of(raw.trim());
            } catch (Exception ignored) {
                // Fall back to UTC if config is invalid.
            }
        }
        return ZoneOffset.UTC;
    }
}
