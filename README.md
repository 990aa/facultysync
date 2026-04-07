# FacultySync - University Schedule and Conflict Manager

FacultySync is a JavaFX desktop application for university schedule operations:

- manage departments, professors, courses, locations, and scheduled events
- detect schedule conflicts with explainable severity levels
- resolve conflicts manually or with automatic room reassignment
- monitor schedule activity through calendar and analytics views

## Download

Latest release: [v0.8.0](https://github.com/990aa/facultysync/releases/latest)

## Core Features

### Scheduling and Data Management

- Sidebar actions for:
  - Export Schedule CSV
  - Export Conflict Report
  - Analyze Conflicts
  - Auto-Resolve Conflicts
  - Manage Departments, Professors, Courses, Locations
  - Add Department, Professor, Course, Location, Event
  - Refresh Data
- Schedule table with edit/delete context menu and double-click edit
- Department filter with explicit `No Filter (All Departments)` option

### Conflict Detection and Resolution

Conflict detection includes all three conflict types:

- `HARD_OVERLAP`: same room, overlapping time
- `PROFESSOR_OVERLAP`: same professor, overlapping time
- `TIGHT_TRANSITION`: same professor, different buildings, gap below threshold

Resolution options:

- Manual reassignment from the Conflicts tab (double-click conflict row)
- Auto-resolve through backtracking room reassignment (`AutoResolver`)

### UI and Navigation

- Custom undecorated title bar (drag, maximize, resize, controls)
- Title text is `FacultySync` (no version suffix)
- Five-tab dashboard:
  - Home (summary cards + recent events)
  - Schedule
  - Conflicts
  - Calendar
  - Analytics
- Sidebar and major content views are vertically scrollable

### Calendar and Analytics

- Weekly calendar with:
  - Previous/Today/Next navigation
  - DatePicker week jump
  - drag-and-drop room reassignment
  - conflict highlighting
- Analytics dashboard with complete chart rendering:
  - legends visible
  - labeled X/Y axes on bar charts
  - event type, peak hours, building utilization, and department activity charts

## Demo Data Seeding

FacultySync seeds data on app startup when the DB is empty, and restores intentional demo conflicts on startup.

For command-line seeding only (no auto-resolve), use:

```bash
./gradlew seedDb
```

What `seedDb` does:

1. Initializes schema
2. Seeds demo entities if the DB is empty
3. Restores intentional demo conflicts (idempotent upsert)
4. Prints entity totals and detected conflict counts by severity

This command is the recommended pre-demo step.

## Build and Run

```bash
./gradlew build
./gradlew test
./gradlew run
```

## Distribution

```bash
./gradlew distZip2
```

Output:

- `build/distributions/FacultySync-<version>-windows.zip`

## Architecture Snapshot

### Main Packages

- `edu.facultysync.algo` - interval tree overlap logic
- `edu.facultysync.core` - dependency wiring (`AppModule`)
- `edu.facultysync.db` - schema, DAOs, seed routines
- `edu.facultysync.events` - typed event bus payloads
- `edu.facultysync.io` - export and CSV IO services
- `edu.facultysync.model` - domain models (`Department`, `Professor`, `Course` are records)
- `edu.facultysync.service` - cache, conflict engine, auto-resolver, notifications
- `edu.facultysync.ui` - JavaFX views/controllers
- `edu.facultysync.util` - time and shared utility policies

### Data Layer

- SQLite with WAL mode + foreign keys
- Normalized entities: departments, professors, courses, locations, scheduled_events
- Epoch millis persistence with `java.time` conversion at boundaries

### Event-Driven Refresh

- `DataChangedEvent` and `CourseAddedEvent`
- Views subscribe through `AppEventBus` and refresh asynchronously

## Project Structure

```text
src/main/java/edu/facultysync/
├── App.java
├── SeedDatabase.java
├── UpdateChecker.java
├── algo/
├── core/
├── db/
├── events/
├── io/
├── model/
├── service/
└── ui/
```

## Testing

Run all tests:

```bash
./gradlew test
```

Current suite includes model, DAO, algorithm, service, IO, seed, and JavaFX UI regression tests.

## License

MIT License. See `LICENSE`.
