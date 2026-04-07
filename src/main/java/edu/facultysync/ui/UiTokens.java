package edu.facultysync.ui;

/**
 * Shared UI tokens to avoid duplicated magic strings for icons and CSS class names.
 */
public final class UiTokens {

    private UiTokens() {
    }

    public static final class Icons {
        public static final String SCHEDULE = "\uD83D\uDCC5";
        public static final String IMPORT = "\uD83D\uDCE5";
        public static final String EXPORT = "\uD83D\uDCE4";
        public static final String REPORT = "\uD83D\uDCCB";
        public static final String WARNING = "\u26A0";
        public static final String RESOLVE = "\u2728";
        public static final String SETTINGS = "\u2699";
        public static final String ADD = "\u2795";
        public static final String REFRESH = "\u21BB";

        private Icons() {
        }
    }

    public static final class Styles {
        public static final String CALENDAR_NAV_BUTTON = "calendar-nav-btn";
        public static final String CALENDAR_JUMP_LABEL = "calendar-jump-label";
        public static final String CALENDAR_WEEK_PICKER = "calendar-week-picker";
        public static final String CALENDAR_WEEK_LABEL = "calendar-week-label";
        public static final String CALENDAR_LEGEND_DOT = "calendar-legend-dot";
        public static final String CALENDAR_LEGEND_LABEL = "calendar-legend-label";
        public static final String CALENDAR_NAV = "calendar-nav";
        public static final String CALENDAR_CORNER = "calendar-corner";
        public static final String CALENDAR_DAY_HEADER = "calendar-day-header";
        public static final String CALENDAR_TIME_LABEL = "calendar-time-label";
        public static final String CALENDAR_CELL = "calendar-cell";
        public static final String CALENDAR_CELL_DROP_TARGET = "calendar-cell-drop-target";

        private Styles() {
        }
    }
}
