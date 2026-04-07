# FacultySync Demo Guide (Updated)

This guide walks through the current FacultySync user experience after the modernization pass.

## 1) Run the Application

### Build
./gradlew build

### Start UI
./gradlew run

### Run Tests
./gradlew test

## 2) What You See on Launch

- A custom undecorated window with branded title bar controls.
- Seeded sample data (departments, professors, courses, rooms, and events).
- Five main tabs:
  - Home
  - Schedule
  - Conflicts
  - Calendar
  - Analytics

## 3) Home Tab

Home provides the high-level operational snapshot:

- live counts for key entities
- quick action cards
- recent activity feed

It refreshes through the event bus when data changes.

## 4) Schedule Tab

Schedule is now owned by a dedicated view module.

- table-based event list
- context menu for edit/delete
- double-click to edit
- empty state guidance when no events exist

## 5) Conflicts Tab

Conflicts is now owned by a dedicated view module.

- conflict severity indicator
- conflict description
- room alternatives
- double-click to open reassignment flow

Conflict severities include:

- HARD_OVERLAP
- PROFESSOR_OVERLAP
- TIGHT_TRANSITION

## 6) Calendar Tab

Calendar uses java.time-backed week navigation and drag-drop scheduling.

- previous/next/today controls
- color-coded event cards by type
- tooltip details
- drag and drop with validation

## 7) Analytics Tab

Analytics provides operational charts and summary cards.

- event type distribution
- building usage breakdown
- hourly load trends
- department activity summaries

## 8) Data Management Workflows

From the left sidebar you can:

- import CSV
- export schedule
- export conflict report
- analyze conflicts
- auto-resolve conflicts
- manage entities (department, professor, course, location)
- add new entities and events

## 9) Event-Driven Refresh Flow

The UI now refreshes through typed events.

- CourseAddedEvent
- DataChangedEvent

Publishing occurs after successful mutations, allowing views to refresh independently.

## 10) Logging and Diagnostics

System.out/System.err usage has been replaced with SLF4J.

- default backend: Logback
- config file: src/main/resources/logback.xml

## 11) CSV Import Expectations

Expected columns:

course_code,event_type,building,room_number,start_datetime,end_datetime

Example:

CS101,Lecture,Science Hall,101,2026-03-01 09:00,2026-03-01 10:00

Importer behavior:

- validates rows
- records skipped rows and reasons
- supports progress callbacks

## 12) Architecture Snapshot

Main packages:

- edu.facultysync.algo
- edu.facultysync.core
- edu.facultysync.db
- edu.facultysync.events
- edu.facultysync.io
- edu.facultysync.model
- edu.facultysync.service
- edu.facultysync.ui
- edu.facultysync.util

## 13) Key Modernization Outcomes Demonstrated in the Demo

- immutable record-based core models for Department/Professor/Course
- java.time-based scheduling logic where legacy Calendar/Date was removed
- extracted ScheduleView and ConflictView for cleaner controller boundaries
- AppModule lightweight DI bootstrap
- SQL centralization through db/SqlQueries
- JPMS module descriptor in src/main/java/module-info.java

## 14) Troubleshooting Quick Notes

If run fails:

1. ensure Java toolchain matches build.gradle
2. run ./gradlew clean test
3. confirm database file permissions
4. inspect logs from configured Logback output

## 15) Verification

Recommended pre-demo check:

1. ./gradlew clean test
2. ./gradlew run
3. perform one mutation (add/edit/delete)
4. verify other views refresh via event-driven updates
