# FacultySync – Phase 2 Demo Guide

> University scheduling conflict detection and management system built with **Java 25**, **JavaFX 25**, and **SQLite**.

---

## Quick Start

```bash
# Build the project
./gradlew build

# Run the application
./gradlew run

# Run all tests
./gradlew test
```

The application launches with **30+ pre-seeded events**, **5 departments**, **9 professors**, **10 courses**, and **10 locations** — including **4 intentional scheduling conflicts** to demonstrate the conflict engine.

---

## Feature Walkthrough

### 1. Custom Title Bar

FacultySync uses an **undecorated window** with a custom title bar that provides:

- **Drag to move** – Click and drag anywhere on the title bar
- **Double-click to maximize/restore** – Toggle fullscreen
- **Window controls** – Minimize (—), Maximize (□), Close (✕)
- Dragging while maximized automatically restores the window

### 2. Home Page (Default Tab)

The landing page provides an at-a-glance overview:

| Section | Description |
|---------|-------------|
| **Hero Banner** | Gradient welcome banner with FacultySync branding |
| **Stat Cards** | Live counts for Events, Courses, Professors, Rooms, Departments |
| **Quick Actions** | Click-to-navigate cards: View Schedule, Detect Conflicts, Open Calendar, View Analytics |
| **Recent Activity** | Last 8 scheduled events with course info, location, and times |

Each stat card has a colored bottom border (blue/green/purple/orange/teal) and hover elevation effect.

### 3. Schedule Tab

Full event listing in a sortable table with columns:

- Course Code, Event Type, Location, Professor, Start, End, Duration
- **Auto-resize columns** using `CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN`
- **Empty state** – friendly message with instructions when no events exist
- **Department filter** – Use the sidebar combo to filter events by department

### 4. Conflict Detection

Click **⚠ Analyze Conflicts** in the sidebar to run the conflict engine:

- **HARD_OVERLAP** (red) – Same room, overlapping time slots
- **TIGHT_TRANSITION** (yellow) – Same professor, different buildings, gap < 15 minutes

Each conflict row shows:
- Severity indicator (color-coded rectangle)
- Human-readable description
- Available alternative rooms

**Double-click** a conflict row to open the room reassignment dialog.

### 5. Auto-Resolve Conflicts

Click **✨ Auto-Resolve** to automatically resolve HARD_OVERLAP conflicts:

- Uses a **backtracking algorithm** that:
  1. Identifies all hard overlaps
  2. For each conflict, tries available alternative rooms
  3. Verifies no new conflicts are introduced
  4. Backtracks if a move causes additional problems
- Shows a detailed results dialog with every action taken
- Toast notification with summary

### 6. Calendar View

A **Google Calendar-style weekly grid** showing all events:

| Feature | Description |
|---------|-------------|
| **Time grid** | 7 AM – 10 PM, hourly rows |
| **Navigation** | ◀ Previous / Today / Next ▶ week buttons |
| **Color coding** | Green = Lecture, Blue = Exam, Purple = Office Hours, Red = Conflict |
| **Drag & drop** | Drag events between cells to reschedule |
| **Tooltips** | Hover event blocks for full details (course, time, location, type) |
| **Legend** | Color key at the top of the calendar |

Events that participate in conflicts are highlighted in **red**.

### 7. Analytics Dashboard

Charts and insights powered by JavaFX `PieChart` and `BarChart`:

| Chart | Description |
|-------|-------------|
| **Event Type Distribution** | Pie chart: Lectures vs Exams vs Office Hours |
| **Peak Usage Hours** | Bar chart: Events per hour of day (7 AM – 10 PM) |
| **Building Utilization** | Pie chart: Percentage of events per building |
| **Department Activity** | Bar chart: Events per department |
| **Summary Cards** | Total events, locations, departments, utilization %, conflict count |

Empty state with guidance is shown when no data is available.

### 8. Toast Notifications

Non-blocking slide-in notifications appear in the top-right corner:

| Type | Color | Use Case |
|------|-------|----------|
| ✓ **Success** | Green | Import complete, event added, conflict resolved |
| ⚠ **Warning** | Yellow | Conflicts detected, no alternatives available |
| ✗ **Error** | Red | Import failed, database errors |
| ℹ **Info** | Blue | Data refreshed, status updates |

- Auto-dismiss after 4 seconds
- Click to dismiss early
- Maximum 5 toasts visible at once
- Animated slide-in and fade-out

### 9. Data Management

The sidebar provides buttons to add data interactively:

- **➕ Department** – Text input dialog
- **➕ Professor** – Name + department selection
- **➕ Course** – Code + professor + enrollment count
- **➕ Location** – Building, room number, capacity, projector checkbox
- **➕ Event** – Course, type, location, start/end datetime

### 10. Import/Export

| Action | Format | Description |
|--------|--------|-------------|
| **📥 Import CSV** | `.csv` | Import schedule from CSV with progress bar |
| **📤 Export Schedule** | `.csv` | Export current schedule as CSV |
| **📋 Export Conflicts** | `.txt` | Generate formatted conflict report |

CSV format:
```
course_code,event_type,building,room_number,start_datetime,end_datetime
CS101,Lecture,Science Hall,101,2026-03-01 09:00,2026-03-01 10:00
```

---

## Architecture

```
src/main/java/edu/facultysync/
├── App.java                    # Application entry point
├── db/
│   ├── DatabaseManager.java    # SQLite connection (WAL, FK)
│   ├── DepartmentDAO.java      # CRUD for departments
│   ├── ProfessorDAO.java       # CRUD for professors
│   ├── CourseDAO.java          # CRUD for courses
│   ├── LocationDAO.java       # CRUD + findAvailable()
│   ├── ScheduledEventDAO.java # CRUD + findOverlapping()
│   └── SeedData.java          # Demo data seeding
├── model/
│   ├── Department.java
│   ├── Professor.java
│   ├── Course.java
│   ├── Location.java
│   ├── ScheduledEvent.java    # Implements Schedulable
│   ├── ConflictResult.java    # HARD_OVERLAP / TIGHT_TRANSITION
│   └── Schedulable.java       # Interface for IntervalTree
├── service/
│   ├── DataCache.java         # In-memory reference data cache
│   ├── ConflictEngine.java    # O(N log N) conflict detection
│   └── AutoResolver.java      # Backtracking auto-resolve
├── algo/
│   └── IntervalTree.java      # Balanced BST for overlap queries
├── io/
│   ├── CsvImporter.java       # CSV → Database
│   └── ReportExporter.java    # Database → CSV/TXT
└── ui/
    ├── DashboardController.java # Main layout (5 tabs + sidebar)
    ├── CustomTitleBar.java      # Undecorated window title bar
    ├── HomePage.java            # Welcome page with stats
    ├── CalendarView.java        # Weekly grid with drag-drop
    ├── AnalyticsView.java       # Charts & insights
    └── ToastNotification.java   # Animated notifications
```

### Key Algorithms

- **IntervalTree** – Balanced BST providing O(log N) interval overlap queries for room conflict detection
- **Backtracking AutoResolver** – Tries alternative rooms, verifies no new conflicts, backtracks on failure
- **ConflictEngine** – Two-pass analysis: room-based overlaps (IntervalTree) + professor-based tight transitions (sorted scan)

### Database

- **SQLite** with WAL journal mode and foreign keys enabled
- **6 tables**: departments, professors, courses, locations, scheduled_events
- **Constraints**: UNIQUE on department names and course codes, CHECK on end > start, FK cascades

---

## Seed Data Summary

| Entity | Count | Details |
|--------|-------|---------|
| Departments | 5 | CS, Mathematics, Physics, Engineering, Business |
| Professors | 9 | Famous scientists: Turing, Dijkstra, Knuth, Euler, Gauss, Newton, Feynman, Tesla, Drucker |
| Courses | 10 | CS101–CS401, MATH101–201, PHYS101–201, ENG101, BUS101 |
| Locations | 10 | Across 5 buildings: Science A, Science B, Engineering Hall, Business Center, Library |
| Events | 30+ | Lectures (Mon–Fri), Exams (Week 2), Office Hours |

### Intentional Conflicts

1. **HARD_OVERLAP #1** – CS201 and MATH201 in Science Building A Room 201, Monday overlapping times
2. **HARD_OVERLAP #2** – ENG101 and PHYS101 in Engineering Hall 100, Wednesday overlapping times
3. **HARD_OVERLAP #3** – CS101 Office Hours and PHYS101 Office Hours in Library Seminar-1, Monday same time
4. **TIGHT_TRANSITION** – Dr. Turing teaching CS101 in Science Building A 101 then CS401 in Engineering Hall 100, Thursday with only 5-minute gap

---

## Testing

```bash
./gradlew test
```

Test suites:

| Suite | Tests | Coverage |
|-------|-------|----------|
| `DatabaseTest` | 25+ | Schema, all 5 DAOs, constraints, cascade deletes |
| `ModelTest` | 30+ | All 6 model classes, equals/hashCode, toString, EventType parsing |
| `IntervalTreeTest` | 20+ | Insert, query, findAllOverlaps, edge cases, stress test (1000 intervals) |
| `ConflictEngineTest` | 10+ | Hard overlaps, tight transitions, alternatives, online events |
| `DataCacheTest` | 12+ | Cache loading, enrichment, null handling, refresh |
| `IoTest` | 12+ | CSV import/export, progress callbacks, edge cases, special chars |
| `AutoResolverTest` | 10+ | Resolution, backtracking, unresolvable cases, online events |
| `SeedDataTest` | 15+ | Idempotency, entity counts, integrity, intentional conflicts |

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 25 |
| UI Framework | JavaFX 25 (Controls, Graphics) |
| Database | SQLite 3.45.1 (via sqlite-jdbc) |
| Build Tool | Gradle with org.openjfx.javafxplugin |
| Testing | JUnit Jupiter 5.10.2 |
| Charts | JavaFX PieChart & BarChart (javafx.controls) |
