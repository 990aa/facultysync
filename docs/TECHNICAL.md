# FacultySync Technical Reference (Updated)

This document describes the current implementation after the architecture modernization pass.

## 1. Platform and Build

- Language: Java (toolchain set in build.gradle)
- UI: JavaFX controls/graphics
- Database: SQLite via JDBC
- CSV handling: in-project parser in CsvImporter
- Logging: SLF4J + Logback
- Eventing: internal typed AppEventBus
- Build: Gradle wrapper
- Tests: JUnit 5

Core commands:

- ./gradlew clean build
- ./gradlew test
- ./gradlew run
- ./gradlew seedAndResolve

## 2. Module System (JPMS)

Module descriptor: src/main/java/module-info.java

Current approach:

- explicit requires for JavaFX, SQL, desktop, and logging
- only top-level app package is exported

This keeps internal implementation packages encapsulated while preserving runtime event dispatch.

## 3. Package Layout and Responsibilities

- edu.facultysync.algo
  - Interval indexing and overlap query algorithm
- edu.facultysync.core
  - Lightweight dependency composition (AppModule)
- edu.facultysync.db
  - DB manager, DAOs, seed routines, SQL constant catalog
- edu.facultysync.events
  - Cross-view domain/UI events
- edu.facultysync.io
  - CSV import and report export
- edu.facultysync.model
  - Domain objects (immutable and mutable legacy entities)
- edu.facultysync.service
  - Cache, conflict engine, auto-resolver, notifications
- edu.facultysync.ui
  - JavaFX presentation layer and view modules
- edu.facultysync.util
  - Shared utility helpers (time policies/formatting)

Package-level JavaDoc files were added for each package to improve discoverability.

## 4. Startup and Dependency Wiring

Entry point: edu.facultysync.App

Startup flow:

1. initialize schema
2. initialize native notifications
3. seed demo data if empty and restore intentional conflicts
4. construct AppModule
5. construct DashboardController with shared dependencies
6. show stage and start async update check

AppModule responsibilities:

- owns shared DatabaseManager
- builds and refreshes DataCache
- constructs ConflictEngine
- provides named EventBus for UI synchronization

This design keeps controller constructors small and avoids manual service wiring in many classes.

## 5. Domain Model Modernization

Immutable records introduced for high-churn reference entities:

- Department
- Professor
- Course

Compatibility strategy:

- retained legacy getter names (getX style) for existing call sites/tests
- added withX copy methods for immutable updates
- preserved identity-based equals/hashCode behavior via IDs

Result:

- no mutable setter side-effects on these entities
- safer updates in manager dialogs and DAO flows

## 6. Persistence Layer and Query Abstraction

SQL constants are centralized in:

- src/main/java/edu/facultysync/db/SqlQueries.java

DAO classes now consume constants rather than embedding long raw SQL strings repeatedly:

- DepartmentDAO
- ProfessorDAO
- CourseDAO
- LocationDAO
- ScheduledEventDAO

Benefits:

- easier query review and auditing
- reduced copy/paste drift between methods
- cleaner DAO methods focused on binding/mapping logic

## 7. Time Handling Modernization

Legacy Date/Calendar usage in core scheduling paths was removed in favor of java.time usage.

Current policy:

- persistent storage remains epoch millis for performant range queries
- UI/service logic uses java.time APIs for date math and week navigation
- conversion boundaries are explicit and centralized through utility methods

Outcome:

- safer time arithmetic
- clearer timezone behavior
- lower risk of mutable date object misuse

## 8. Conflict Analysis and Resolution

ConflictEngine:

- hard room overlap detection using interval logic
- professor overlap/tight-transition detection
- alternative room generation through DAO capacity/time queries

AutoResolver:

- attempts safe room moves
- validates candidate moves against conflict rules
- backtracks when a tentative move introduces a new hard conflict

This keeps conflict handling deterministic and explainable.

## 9. UI Modularization

DashboardController remains the shell orchestrator but now delegates major tab content to dedicated modules:

- ScheduleView
- ConflictView
- HomePage
- CalendarView
- AnalyticsView

Schedule and Conflict tabs were extracted to reduce controller complexity and make view responsibilities explicit.

## 10. Event-Driven View Refresh

Event bus payloads:

- DataChangedEvent
- CourseAddedEvent

Bus implementation:

- edu.facultysync.events.AppEventBus (in-process, typed subscribe/post API)

Pattern:

1. mutation succeeds in DAO/service call
2. cache refresh occurs
3. event is posted
4. subscribing views refresh their local state

This avoids tight coupling where every mutation path manually calls every view refresh method.

## 11. Logging and Diagnostics

Console printing was replaced with structured logging.

- API: SLF4J
- backend: Logback
- config: src/main/resources/logback.xml

Impacted areas include startup/update checks, notification service diagnostics, and seed/resolve CLI flows.

## 12. UI Styling and Lint Cleanliness

The JavaFX stylesheet was adjusted to clear compatibility diagnostics from editor tooling.

Examples:

- added cursor fallback next to -fx-cursor rules
- added fill fallback next to -fx-fill usage

This keeps Problems view clean without changing JavaFX behavior.

## 13. Quality Gates and Current Status

Validation performed after changes:

- full test suite passes via ./gradlew test
- source-root Problems scan (main/test/resources) reports no active problems

Representative test groups validated:

- DB and schema tests
- model tests
- algorithm tests
- conflict and resolver tests
- IO import/export tests
- cache and UI component tests

## 14. Known Runtime Warnings Outside Editor Problems

Some test-runner JVM warnings can still appear from external/runtime modules (for example JavaFX module flags and SQLite native-access notices). These are runtime environment warnings, not source Problems, and can be tuned further in Gradle JVM args if strict warning-free console output is required.

## 15. Guidance for Further Modernization

Recommended next increments:

1. continue immutable migration for Location and ScheduledEvent (with compatibility adapters)
2. replace string reason codes in DataChangedEvent with a typed enum
3. split DashboardController action handlers into dedicated command/service classes
4. add architectural tests to enforce package boundaries and prevent controller bloat
5. generate API-level JavaDoc site as part of CI build
