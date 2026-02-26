# FacultySync – University Schedule & Conflict Manager

A modern JavaFX desktop application for managing university course schedules, detecting room conflicts, and resolving them with intelligent suggestions. Features a custom undecorated UI, native Windows notifications, automatic updates from GitHub, and a comprehensive analytics dashboard.

## Download

> **Latest Release:** [v0.1.0](https://github.com/990aa/facultysync/releases/latest)

Download the standalone Windows distribution from the [Releases](https://github.com/990aa/facultysync/releases) page. No JDK installation required.

## Features

### Core
- **Schedule Management** – Import/export CSV schedules, add events/courses/professors/locations/departments interactively
- **Conflict Detection** – O(N log N) detection using IntervalTree: hard room overlaps + tight professor transitions (< 15 min between buildings)
- **Auto-Resolve** – Backtracking algorithm that reassigns conflicting events to available rooms, verifying no new conflicts are introduced

### UI
- **Custom Title Bar** – Undecorated window with drag-to-move, double-click maximize, minimize/maximize/close, and edge-resize on all borders
- **5-Tab Dashboard** – Home (stats + quick actions), Schedule, Conflicts, Calendar, Analytics
- **Weekly Calendar** – Google Calendar-style grid with color-coded event blocks and drag-and-drop rescheduling
- **Analytics Dashboard** – Pie/bar charts for event distribution, peak hours, building utilization, and department activity
- **Toast Notifications** – Animated slide-in/fade-out notifications (success/warning/error/info)

### System Integration
- **Windows Notifications** – Native OS toast notifications via `SystemTray` for imports, conflicts, auto-resolve results, and update alerts
- **Auto-Update** – Checks GitHub Releases API on startup and prompts user to update when a newer version is available
- **Version Display** – Current version shown in the title bar

### Data
- **Demo Seed Data** – 5 departments, 9 professors, 10 courses, 10 locations, 30+ events with 4 intentional conflicts
- **SQLite Database** – WAL journal mode, foreign keys, indexed for fast overlap queries

## Architecture

### Database (SQLite)
- **5 normalized tables**: `departments`, `professors`, `courses`, `locations`, `scheduled_events`
- **Epoch timestamps** for efficient overlap queries (`WHERE start_epoch < ? AND end_epoch > ?`)
- **PRAGMA foreign_keys = ON** for relational integrity
- **PRAGMA journal_mode = WAL** for concurrent read/write operations

### Algorithms
- **IntervalTree<T extends Schedulable>** – generic balanced interval tree for O(log N) overlap detection
- **ConflictEngine** – detects hard overlaps (same room) and tight transitions (professor moving between buildings with < 15 min gap)
- **AutoResolver** – backtracking algorithm: tries alternatives, verifies no new conflicts, backtracks on failure

### Concurrency
- All DB I/O runs on background threads using JavaFX `Task<V>`
- Progress bars bound to import/analysis tasks
- UI thread never blocks
- Update checking runs on a daemon thread

### I/O
- **CSV Import** with `BufferedReader`/`FileReader`, handles missing data via Wrapper classes (`Integer`, `Long`)
- **Conflict Report Export** – formatted text file with alternatives
- **Schedule Export** – CSV output

## Build & Run

```bash
gradle build      # Compile + run all tests
gradle run        # Launch the application
gradle test       # Run all automated tests
```

## Test Coverage

| Suite | Tests | Coverage |
|-------|-------|----------|
| ModelTest | 48 | All model classes, getters/setters, equals/hashCode, toString, EventType parsing |
| IntervalTreeTest | 27 | Empty/null trees, insert, overlap queries, edge cases, stress tests |
| DatabaseTest | 36 | Schema, PRAGMAs, all CRUD operations, constraints, FK violations |
| DataCacheTest | 14 | Cache loading, enrichment, null handling, refresh |
| ConflictEngineTest | 10 | Hard overlaps, tight transitions, online events, multi-overlap |
| IoTest | 19 | CSV import (valid/invalid/edge cases), report export, schedule export |
| AutoResolverTest | 10+ | Resolution, backtracking, unresolvable cases, online events |
| SeedDataTest | 15+ | Idempotency, entity counts, integrity, intentional conflicts |

## Project Structure

```
src/main/java/edu/facultysync/
├── App.java                      # Application entry point + version constant
├── UpdateChecker.java            # GitHub auto-update checker
├── algo/
│   └── IntervalTree.java         # Balanced BST for O(log N) overlap queries
├── db/
│   ├── DatabaseManager.java      # SQLite connection + schema + PRAGMAs
│   ├── DepartmentDAO.java        # CRUD for departments
│   ├── ProfessorDAO.java         # CRUD for professors
│   ├── CourseDAO.java            # CRUD for courses
│   ├── LocationDAO.java          # CRUD + findAvailable()
│   ├── ScheduledEventDAO.java    # CRUD + time-range/overlap queries
│   └── SeedData.java             # Demo data seeding (30+ events, 4 conflicts)
├── io/
│   ├── CsvImporter.java          # CSV → Database with progress callback
│   └── ReportExporter.java       # Database → CSV/TXT export
├── model/
│   ├── ConflictResult.java       # Conflict detection result (HARD_OVERLAP / TIGHT_TRANSITION)
│   ├── Course.java               # ORM: courses table
│   ├── Department.java           # ORM: departments table
│   ├── Location.java             # ORM: locations table
│   ├── Professor.java            # ORM: professors table
│   ├── Schedulable.java          # Interface for IntervalTree
│   └── ScheduledEvent.java       # ORM: scheduled_events table
├── service/
│   ├── ConflictEngine.java       # IntervalTree-based conflict detection
│   ├── DataCache.java            # In-memory HashMap cache for reference data
│   ├── AutoResolver.java         # Backtracking auto-resolve algorithm
│   └── NotificationService.java  # Native Windows system tray notifications
└── ui/
    ├── DashboardController.java  # Main layout (5 tabs + sidebar)
    ├── CustomTitleBar.java       # Undecorated window title bar with resize
    ├── HomePage.java             # Welcome page with stats + quick actions
    ├── CalendarView.java         # Weekly calendar grid with drag-drop
    ├── AnalyticsView.java        # Charts & insights dashboard
    └── ToastNotification.java    # Animated toast notification system
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 25 |
| UI Framework | JavaFX 25 (Controls, Graphics) |
| Database | SQLite 3.45.1 (via sqlite-jdbc) |
| Build Tool | Gradle with org.openjfx.javafxplugin |
| Testing | JUnit Jupiter 5.10.2 |
| Charts | JavaFX PieChart & BarChart |
| Notifications | java.awt.SystemTray (Windows native) |

## Release

The project includes a PowerShell release script that automates:
1. Building the standalone distribution (via `gradle jpackage`)
2. Semantic version bumping (major/minor/patch)
3. Creating a GitHub release with the distribution attached
4. Updating the README download link

```powershell
# Release with version bump
.\release.ps1 -BumpType patch    # 0.1.0 → 0.1.1
.\release.ps1 -BumpType minor    # 0.1.0 → 0.2.0
.\release.ps1 -BumpType major    # 0.1.0 → 1.0.0
.\release.ps1 -Version 0.1.0     # Explicit version
```

## License

MIT License – see [LICENSE](LICENSE) for details.
