# FacultySync Technical Reference

This document reflects the current implementation in the repository.

## 1. Platform

- Language: Java 25 (Gradle toolchain)
- UI: JavaFX (`controls`, `graphics`, `fxml`)
- Database: SQLite (`sqlite-jdbc`)
- Logging: SLF4J + Logback
- Build: Gradle wrapper
- Tests: JUnit 5

## 2. Core Commands

```bash
./gradlew build
./gradlew test
./gradlew run
./gradlew seedDb
```

`seedDb` is seed-only (no auto-resolve).

## 3. Startup and Data Initialization

Entry point: `edu.facultysync.App`

Application startup sequence:

1. Initialize database schema
2. Initialize notification service
3. `SeedData.seedIfEmpty(...)`
4. `SeedData.ensureIntentionalConflicts(...)`
5. Build `AppModule`
6. Build dashboard and show stage
7. Run update check asynchronously

Window title text is provided by `App.windowTitle()` and is currently `FacultySync`.

## 4. Dependency Wiring

`edu.facultysync.core.AppModule` provides shared instances:

- `DatabaseManager`
- `DataCache`
- `ConflictEngine`
- `AppEventBus`

This keeps view constructors focused and avoids duplicated service wiring.

## 5. Package Responsibilities

- `algo`: interval overlap data structures and queries
- `core`: app-level dependency composition
- `db`: schema, DAO layer, seed orchestration
- `events`: typed domain/UI event payloads
- `io`: CSV/report import-export implementations
- `model`: domain entities and conflict model
- `service`: conflict analysis, auto-resolution, caching, notifications
- `ui`: dashboard shell and tab view modules
- `util`: shared time policies and helpers

## 6. Conflict Model

Conflict severities:

- `HARD_OVERLAP`
- `PROFESSOR_OVERLAP`
- `TIGHT_TRANSITION`

`ConflictEngine` behavior:

1. Room overlap detection per room using `IntervalTree`
2. Professor overlap detection per professor using `IntervalTree`
3. Tight transition detection for adjacent professor events in different buildings

Tight transition threshold is configurable by:

- system property: `facultysync.tightTransitionMinutes`
- env var: `FACULTYSYNC_TIGHT_TRANSITION_MINUTES`

## 7. Auto-Resolve Strategy

`AutoResolver` processes actionable conflicts (`HARD_OVERLAP`, `TIGHT_TRANSITION`) by:

1. collecting available rooms for the event window/capacity
2. tentatively moving an event
3. re-running conflict analysis on working set
4. committing successful moves
5. backtracking when a tentative move still conflicts

Result contract:

- `totalConflicts`
- `resolved`
- `unresolvable`
- action log entries

## 8. Seed Pipeline

### Seed Entry Task

Gradle task: `seedDb`

Main class: `edu.facultysync.SeedDatabase`

Behavior:

1. initialize schema
2. seed reference and schedule data if DB is empty
3. re-apply intentional demo conflicts idempotently
4. print totals and conflict counts by severity

### Seed Data Coverage

Seed includes:

- 5 departments
- 9 professors
- 10 courses
- 10 locations
- 30+ events
- intentional conflicts spanning:
  - room overlaps
  - professor overlaps
  - tight transitions
  - coverage across all departments

## 9. UI Composition

`DashboardController` owns shell layout and tab orchestration:

- Home (`HomePage`)
- Schedule (`ScheduleView`)
- Conflicts (`ConflictView`)
- Calendar (`CalendarView`)
- Analytics (`AnalyticsView`)

Notable current UI behavior:

- sidebar wrapped in scroll pane
- department combo includes explicit no-filter option
- Import CSV button removed from sidebar actions
- Home quick-actions section removed
- analytics chart legends/axes visibility hardened

## 10. Async Refresh and Event Flow

Views refresh through async `Task` execution and typed event publishing.

Primary events:

- `DataChangedEvent`
- `CourseAddedEvent`

Pattern:

1. mutation succeeds
2. cache refresh
3. event bus publish
4. subscribers refresh UI state

## 11. Data and Time Representation

Persistence uses epoch millis for range query performance.

UI and logic paths use `java.time` APIs; conversion boundaries are explicit through utility methods.

## 12. Testing and Diagnostics

Test scope includes:

- DAO and schema constraints
- interval tree and conflict analysis
- auto-resolve behavior
- seed data integrity
- UI regression checks for sidebar/filter/home/analytics behaviors

Target quality gate:

- `./gradlew test` passes
- editor Problems view reports zero diagnostics

## 13. JPMS

Module descriptor: `src/main/java/module-info.java`

Current module exports the top-level app package and declares JavaFX, SQL, desktop, and logging dependencies.
