# FacultySync – University Schedule & Conflict Manager

A JavaFX desktop application for managing university course schedules, detecting room conflicts, and resolving them with intelligent suggestions.

## Architecture

### Database (SQLite)
- **5 normalized tables**: `departments`, `professors`, `courses`, `locations`, `scheduled_events`
- **Epoch timestamps** for efficient overlap queries (`WHERE start_epoch < ? AND end_epoch > ?`)
- **PRAGMA foreign_keys = ON** for relational integrity
- **PRAGMA journal_mode = WAL** for concurrent read/write operations

### Algorithms
- **IntervalTree<T extends Schedulable>** – generic balanced interval tree for O(log N) overlap detection
- **ConflictEngine** – detects hard overlaps (same room) and tight transitions (professor moving between buildings with < 15 min gap)

### Concurrency
- All DB I/O runs on background threads using JavaFX `Task<V>`
- Progress bars bound to import/analysis tasks
- UI thread never blocks

### I/O
- **CSV Import** with `BufferedReader`/`FileReader`, handles missing data via Wrapper classes (`Integer`, `Long`)
- **Conflict Report Export** – formatted text file with alternatives
- **Schedule Export** – CSV output

### UI (JavaFX)
- `BorderPane` layout: left control panel + center tabbed view (Schedule / Conflicts)
- Color-coded conflict severity (red = hard overlap, yellow = tight transition)
- Actionable room reassignment dialogs with available alternatives

## Build & Run

```bash
gradle build      # Compile + run all tests
gradle run        # Launch the application
gradle test       # Run 172 automated tests
```

## Test Coverage (172 tests)

| Suite | Tests | Coverage |
|-------|-------|----------|
| ModelTest | 48 | All model classes, getters/setters, equals/hashCode, toString, EventType parsing |
| IntervalTreeTest | 27 | Empty/null trees, insert, overlap queries, edge cases, stress tests |
| DatabaseTest | 36 | Schema, PRAGMAs, all CRUD operations, constraints, FK violations |
| DataCacheTest | 14 | Cache loading, enrichment, null handling, refresh |
| ConflictEngineTest | 10 | Hard overlaps, tight transitions, online events, multi-overlap |
| IoTest | 19 | CSV import (valid/invalid/edge cases), report export, schedule export |

## Project Structure

```
src/main/java/edu/facultysync/
├── App.java                    # JavaFX application entry point
├── algo/
│   └── IntervalTree.java       # Generic interval tree for O(log N) overlap detection
├── db/
│   ├── DatabaseManager.java    # SQLite connection + schema + PRAGMAs
│   ├── DepartmentDAO.java      # CRUD for departments
│   ├── ProfessorDAO.java       # CRUD for professors
│   ├── CourseDAO.java          # CRUD for courses
│   ├── LocationDAO.java        # CRUD + available room queries
│   └── ScheduledEventDAO.java  # CRUD + time-range/overlap queries
├── io/
│   ├── CsvImporter.java        # CSV parsing with progress callback
│   └── ReportExporter.java     # Conflict report + schedule CSV export
├── model/
│   ├── ConflictResult.java     # Conflict detection result
│   ├── Course.java             # ORM: courses table
│   ├── Department.java         # ORM: departments table
│   ├── Location.java           # ORM: locations table
│   ├── Professor.java          # ORM: professors table
│   ├── Schedulable.java        # Interface for IntervalTree
│   └── ScheduledEvent.java     # ORM: scheduled_events table
├── service/
│   ├── ConflictEngine.java     # Overlap + transition conflict detection
│   └── DataCache.java          # In-memory HashMap cache for reference data
└── ui/
    └── DashboardController.java # Full JavaFX dashboard UI
```
