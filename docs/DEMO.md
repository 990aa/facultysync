# FacultySync Live Demo Guide

This runbook is written for the current codebase and assumes you start from seeded demo data.

## 1. Pre-Demo Setup

Run the seed-only script first:

```bash
./gradlew seedDb
```

Expected seed output includes:

- schema initialization success
- entity totals (departments, professors, courses, locations, events)
- detected conflicts grouped by:
  - `HARD_OVERLAP`
  - `PROFESSOR_OVERLAP`
  - `TIGHT_TRANSITION`

Then launch the app:

```bash
./gradlew run
```

## 2. Launch Verification

Immediately confirm:

1. Window title shows `FacultySync` (no version suffix).
2. Left sidebar is scrollable to the bottom.
3. There is no `Import CSV` button in the sidebar.
4. Department dropdown text is clearly visible (black text).
5. Department dropdown contains `No Filter (All Departments)`.

## 3. Home Tab Demo

Demonstrate:

1. Summary cards update from seeded data.
2. `Recent Events` list is visible.
3. `Quick Actions` section is absent (removed from Home).
4. Scroll works fully from top to bottom.

Narration tip: explain that Home reflects async-refresh snapshots, not hardcoded values.

## 4. Schedule Tab Demo

Demonstrate full schedule behavior:

1. Open Schedule tab and show seeded events table.
2. Use department dropdown:
   - select `No Filter (All Departments)` to show all data
   - switch to each department and show filtered rows
3. Right-click an event row:
   - open context menu
   - show `Edit Event` and `Delete Event`
4. Double-click an event to open edit flow.

## 5. Conflict Analysis Demo (All Conflict Types)

### 5.1 Trigger analysis

1. Click `Analyze Conflicts`.
2. Open Conflicts tab and review table rows.
3. Point out severity badges/colors.

### 5.2 Demonstrate each type live

Use table severity column to show at least one row of each:

1. `HARD_OVERLAP`
   - two events overlapping in the same room/time window
2. `PROFESSOR_OVERLAP`
   - same professor assigned to overlapping events in different rooms
3. `TIGHT_TRANSITION`
   - same professor with too-short inter-building gap

### 5.3 Explain alternatives

For rows with alternatives, show `Alternatives` column and explain room suggestions.

## 6. Conflict Resolution Demo

### 6.1 Manual resolution path

1. Double-click a conflict row in Conflicts tab.
2. Select one suggested room.
3. Confirm reassignment.
4. Re-run `Analyze Conflicts` and show impact.

### 6.2 Auto-resolve path

1. Click `Auto-Resolve Conflicts`.
2. Show status and toast feedback.
3. Re-run `Analyze Conflicts`.
4. Compare conflict count before vs after.

## 7. Show Updated Schedule After Resolution

After manual and/or auto-resolve:

1. Return to Schedule tab.
2. Locate moved event(s) and highlight updated room assignment(s).
3. Open Calendar tab and visually verify moved event placement.
4. Optionally use department filter to show impact by department.

This is the key proof step that the schedule changed in persisted data, not only in-memory UI.

## 8. Calendar Tab Demo

Demonstrate:

1. Week navigation with `Prev`, `Today`, `Next`.
2. Week jump using DatePicker.
3. Conflict-highlighted blocks.
4. Drag-and-drop event movement and post-drop validation flow.
5. Full vertical scroll from earliest to latest rendered hours.

## 9. Analytics Tab Demo

Demonstrate chart completeness and readability:

1. Scroll to bottom to show all analytics sections.
2. Show legends on pie and bar charts.
3. Show X/Y axis labels on bar charts.
4. Review:
   - Event Type Distribution
   - Peak Hours
   - Building Utilization
   - Department Activity
5. Confirm charts refresh after data changes.

## 10. Export Workflows

From sidebar:

1. `Export Schedule CSV`
2. `Export Conflict Report`

Show generated files and explain they reflect current post-resolution state.

## 11. End-of-Demo Checklist

Before ending, confirm:

1. all three conflict severities were demonstrated live
2. at least one conflict was resolved
3. updated schedule state was shown in both Schedule and Calendar
4. analytics rendered with legends and axes
5. no UI regression around scrolling, title text, or department filtering
