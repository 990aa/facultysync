# FacultySync — Technical Implementation Reference

> Comprehensive technical documentation for answering implementation, design, and architecture questions.

---

## Table of Contents

1. [Build System & Gradle/Gradlew](#1-build-system--gradlegradlew)
2. [JavaFX Application Lifecycle](#2-javafx-application-lifecycle)
3. [Custom Window Management](#3-custom-window-management)
4. [Application Icon Pipeline](#4-application-icon-pipeline)
5. [IntervalTree Algorithm](#5-intervaltree-algorithm)
6. [Conflict Detection Engine](#6-conflict-detection-engine)
7. [Backtracking Auto-Resolver](#7-backtracking-auto-resolver)
8. [Database Strategy (SQLite)](#8-database-strategy-sqlite)
9. [Data Access Objects (DAO Pattern)](#9-data-access-objects-dao-pattern)
10. [In-Memory Caching (DataCache)](#10-in-memory-caching-datacache)
11. [UI/UX Design](#11-uiux-design)
12. [Toast Notification System](#12-toast-notification-system)
13. [Native Windows Notifications](#13-native-windows-notifications)
14. [CSV Import/Export Pipeline](#14-csv-importexport-pipeline)
15. [Auto-Update Checker](#15-auto-update-checker)
16. [Concurrency Model](#16-concurrency-model)
17. [Testing Strategy](#17-testing-strategy)
18. [CI/CD Pipeline](#18-cicd-pipeline)
19. [Release Automation](#19-release-automation)
20. [CSS Theming Architecture](#20-css-theming-architecture)
21. [Seed Data Generator](#21-seed-data-generator)
22. [Key Design Decisions & Trade-offs](#22-key-design-decisions--trade-offs)

---

## 1. Build System & Gradle/Gradlew

### Gradle Wrapper (`gradlew`)

FacultySync uses the **Gradle Wrapper** (`gradlew` / `gradlew.bat`) to ensure reproducible builds without requiring a global Gradle installation. The wrapper is configured in `gradle/wrapper/gradle-wrapper.properties` and pins **Gradle 9.3.1**.

**Why `./gradlew` instead of `gradle`:**
- Ensures all developers and CI use the exact same Gradle version
- No need to install Gradle on the machine — the wrapper downloads it automatically
- Eliminates "works on my machine" version discrepancies

### `build.gradle` Configuration

```groovy
plugins {
    id 'application'                              // Provides run, installDist, distZip tasks
    id 'org.openjfx.javafxplugin' version '0.1.0' // Manages JavaFX module path
}

javafx {
    version = "25"
    modules = ['javafx.controls', 'javafx.fxml', 'javafx.graphics']
}
```

**Key Gradle tasks:**

| Task | Command | Purpose |
|------|---------|---------|
| `run` | `./gradlew run` | Launch the JavaFX application |
| `test` | `./gradlew test` | Run all JUnit 5 test suites |
| `build` | `./gradlew build` | Compile + test + assemble JARs |
| `distZip2` | `./gradlew distZip2` | Create standalone `.zip` distribution |
| `installDist` | `./gradlew installDist` | Install to `build/install/` for jpackage |
| `seedAndResolve` | `./gradlew seedAndResolve` | CLI seed + conflict analysis |
| `clean` | `./gradlew clean` | Remove all build artifacts |

### Application Plugin

The `application` plugin sets `mainClass = 'edu.facultysync.App'`, enabling:
- `./gradlew run` to launch the app
- `installDist` to create a platform-specific distribution with launch scripts
- Distribution includes all dependency JARs in `lib/` and generated `bin/facultysync.bat`

### JavaFX Plugin

The `org.openjfx.javafxplugin` handles:
- Downloading platform-specific JavaFX SDK natives (Windows x64)
- Setting `--module-path` and `--add-modules` on the JVM command line
- Resolving `javafx.controls`, `javafx.fxml`, and `javafx.graphics` modules from Maven Central

### Test JVM Configuration

```groovy
test {
    useJUnitPlatform()
    jvmArgs += [
        '--add-exports', 'javafx.graphics/com.sun.javafx.application=ALL-UNNAMED',
        '--add-opens', 'javafx.graphics/com.sun.glass.ui=ALL-UNNAMED'
    ]
}
```

These flags are required because:
- `--add-exports javafx.graphics/com.sun.javafx.application` allows `Platform.startup()` to be called from test code (it's an internal API)
- `--add-opens javafx.graphics/com.sun.glass.ui` allows reflective access for headless JavaFX testing

### Custom Distribution Task

```groovy
tasks.register('distZip2', Zip) {
    archiveFileName = "FacultySync-${project.version}-windows.zip"
    from(tasks.named('installDist').map { it.destinationDir })
}
```

This creates a standalone distribution with:
- All dependency JARs (`sqlite-jdbc`, JavaFX natives)
- Generated launch scripts (`bin/facultysync.bat`)
- Named with the version for GitHub Releases

---

## 2. JavaFX Application Lifecycle

### Entry Point: `App.java`

```
main(String[] args)
  └── Application.launch(args)
        └── JavaFX Toolkit initialized
              └── start(Stage primaryStage)
                    ├── DatabaseManager.initializeSchema()
                    ├── NotificationService.initialize()
                    ├── SeedData.seedIfEmpty()
                    ├── Stage setup (UNDECORATED)
                    ├── Set application icon from app-icon.png
                    ├── Build DashboardController
                    ├── Build CustomTitleBar
                    ├── Create Scene (visual-bounds-aware sizing)
                    ├── Load CSS stylesheet
                    ├── Center window within visual bounds
                    ├── Install resize handlers
                    ├── stage.show()
                    └── Platform.runLater → UpdateChecker.checkForUpdates()
```

### Window Sizing Strategy

The application uses `Screen.getPrimary().getVisualBounds()` — which returns the usable screen area **excluding** the OS taskbar — to calculate the initial window size:

```java
Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
double initWidth = Math.min(1280, screenBounds.getWidth() * 0.82);
double initHeight = Math.min(800, screenBounds.getHeight() * 0.85);
```

This ensures:
- The window never covers the Windows taskbar at the bottom
- On small screens (e.g., 1366×768), the window is proportionally smaller
- On large screens, it caps at 1280×800 for optimal readability
- The window is centered: `stageX = screenMinX + (screenWidth - initWidth) / 2`

### Shutdown Sequence

```java
@Override
public void stop() {
    NotificationService.shutdown();  // Remove system tray icon
    if (dbManager != null) dbManager.close();  // Close SQLite connection
}
```

The `stop()` method is called automatically by the JavaFX runtime when the application exits (via `Platform.exit()` or window close).

---

## 3. Custom Window Management

### Why Undecorated?

The application uses `StageStyle.UNDECORATED` to remove the native window title bar:
- Allows a fully custom dark-themed title bar matching the sidebar aesthetic
- Provides branded window controls (minimize, maximize, close)
- Enables custom behaviors: drag-to-move, double-click-to-maximize, 8-direction edge resize

### CustomTitleBar Architecture

`CustomTitleBar` extends `HBox` (horizontal layout) with fixed 40px height:

```
┌──────────────────────────────────────────────────────────────────┐
│ [Icon] [Title Label]     ← spacer →     [─] [□] [✕]           │
│  22px   "FacultySync v0.2.0"            min  max  close        │
└──────────────────────────────────────────────────────────────────┘
```

**Components:**
1. **Icon** – ImageView loaded from `/app-icon.png` on the classpath (22×22px, aspect ratio preserved). Falls back to Unicode emoji `🎓` if the image is unavailable.
2. **Title Label** – Displays app name + version with CSS class `.title-bar-label`
3. **Spacer** – `Region` with `HBox.setHgrow(spacer, Priority.ALWAYS)` pushes buttons right
4. **Window Controls** – Three 46×40px buttons with Unicode glyphs:
   - Minimize: `\u2013` (en dash) → calls `stage.setIconified(true)`
   - Maximize: `\u25A1`/`\u25A3` (white/filled square) → calls `toggleMaximize(stage)`
   - Close: `\u2715` (multiplication X) → calls `stage.close()` + `Platform.exit()`

### Drag-to-Move

```java
setOnMousePressed(event -> {
    xOffset = event.getSceneX();   // Capture offset from scene origin
    yOffset = event.getSceneY();
});

setOnMouseDragged(event -> {
    stage.setX(event.getScreenX() - xOffset);  // Move stage
    stage.setY(event.getScreenY() - yOffset);
});
```

When dragged while maximized, the window **restores to its previous size** centered on the cursor, providing a natural "drag-off" behavior.

### Maximize Toggle

Uses `Screen.getPrimary().getVisualBounds()` (NOT `getBounds()`) to respect the taskbar:

```java
var screen = Screen.getPrimary().getVisualBounds();
stage.setX(screen.getMinX());      // Left edge of usable area
stage.setY(screen.getMinY());      // Top of usable area (usually 0)
stage.setWidth(screen.getWidth()); // Full width excluding taskbar
stage.setHeight(screen.getHeight()); // Full height excluding taskbar
```

Previous window position/size is stored in `prevX`, `prevY`, `prevW`, `prevH` for restoration.

### 8-Direction Edge Resize

Resize handlers are installed as **event filters** on the scene root (not the title bar):

```java
root.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
    // Determine which edge/corner the cursor is near
    // Set cursor to NW_RESIZE, N_RESIZE, etc.
});

root.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
    // Resize the stage based on cursor type and delta movement
});
```

The resize margin is 6px. Event filters are used instead of `setOnMouseMoved` to avoid conflicts with the title bar's drag handlers. The resize is disabled when maximized.

---

## 4. Application Icon Pipeline

### Runtime Icon (JavaFX)

The app icon is loaded from the classpath at startup:

```java
InputStream iconStream = getClass().getResourceAsStream("/app-icon.png");
primaryStage.getIcons().add(new Image(iconStream));
```

This sets the icon for:
- **Window switcher** (Alt+Tab)
- **Windows taskbar** button
- **Title bar** (if the stage were decorated — we use custom title bar instead)

### Title Bar Icon

The `CustomTitleBar` displays the icon as an `ImageView`:

```java
ImageView iconImage = new ImageView(new Image(iconStream, 22, 22, true, true));
```

Parameters: width=22, height=22, preserveRatio=true, smooth=true.

### CI/CD Icon (MSI Installer)

The GitHub Actions workflow converts `app-icon.png` to `.ico` format using .NET:

```powershell
Add-Type -AssemblyName System.Drawing
$bitmap = [System.Drawing.Bitmap]::FromFile("app-icon.png")
$resized = New-Object System.Drawing.Bitmap($bitmap, 256, 256)
$icon = [System.Drawing.Icon]::FromHandle($resized.GetHicon())
$stream = [System.IO.File]::Create("build/app-icon.ico")
$icon.Save($stream)
```

The `.ico` is then passed to `jpackage --icon` for the MSI installer and Windows shortcut.

### File Locations

| Context | File | Format | Size |
|---------|------|--------|------|
| Repository root | `app-icon.png` | PNG | Original |
| Classpath resource | `src/main/resources/app-icon.png` | PNG | Copy of above |
| CI build artifact | `build/app-icon.ico` | ICO | 256×256 converted |

---

## 5. IntervalTree Algorithm

### Data Structure

`IntervalTree<T extends Schedulable>` is an augmented Binary Search Tree where:
- Each node stores an interval `[start, end)` from a `Schedulable` object
- Each node maintains `maxEnd` — the maximum `end` value in its entire subtree
- The tree is built as a **balanced BST** from sorted input using median-based construction

### Construction: `O(N log N)`

```
buildBalanced(sorted, lo, hi):
    if lo > hi: return null
    mid = (lo + hi) / 2
    node = new Node(sorted[mid])
    node.left  = buildBalanced(sorted, lo, mid - 1)
    node.right = buildBalanced(sorted, mid + 1, hi)
    node.maxEnd = max(node.end, left.maxEnd, right.maxEnd)
    return node
```

1. Sort input by start time: `O(N log N)`
2. Build balanced tree using median splits: `O(N)`
3. Total: `O(N log N)`

### Overlap Query: `O(log N + k)`

Two intervals `[a_start, a_end)` and `[b_start, b_end)` overlap when:

$$a_{start} < b_{end} \quad \text{AND} \quad a_{end} > b_{start}$$

The query algorithm prunes subtrees using `maxEnd`:

```
queryOverlaps(node, start, end):
    if node is null: return
    if node.maxEnd ≤ start: return        ← PRUNE: no interval in this subtree can overlap
    queryOverlaps(node.left, start, end)   ← search left
    if node.start < end AND node.end > start:
        add node to results               ← overlap found
    if node.start ≥ end: return           ← PRUNE: no right child can overlap
    queryOverlaps(node.right, start, end)
```

**Complexity:** `O(log N + k)` where `k` is the number of overlapping results. The `maxEnd` pruning eliminates entire subtrees that cannot contain overlapping intervals.

### Pairwise Overlap Detection: `findAllOverlaps()`

1. In-order traversal → sorted list of all `N` intervals
2. For each interval, query the tree for overlaps
3. Deduplicate pairs: only keep `(a, b)` where `a.start ≤ b.start`

**Complexity:** `O(N log N + P)` where `P` is the total number of pairwise overlaps.

### Schedulable Interface

```java
public interface Schedulable {
    long getStart();
    long getEnd();
}
```

Implemented by `ScheduledEvent`, allowing the IntervalTree to be generic and reusable for any time-interval data.

### Insert Operation

Single-interval insertion for dynamic updates:

```java
public void insert(T interval) {
    root = insert(root, interval);
}
```

Inserts using BST property on `start` values, then updates `maxEnd` along the insertion path. Complexity: `O(h)` where `h` is the tree height.

---

## 6. Conflict Detection Engine

### Two-Pass Architecture

The `ConflictEngine` performs conflict detection in two passes:

**Pass 1 — Room-based `HARD_OVERLAP` detection:**

```
1. Group all events by loc_id (skip null = online events)
2. For each location:
   a. Build IntervalTree from the location's events
   b. Call findAllOverlaps() to detect pairwise time overlaps
   c. For each overlapping pair → create ConflictResult(HARD_OVERLAP)
   d. Query LocationDAO.findAvailable() for alternative rooms
```

**Pass 2 — Professor-based `TIGHT_TRANSITION` detection:**

```
1. Group events by professor (via course → prof_id mapping)
2. For each professor:
   a. Sort events chronologically by start time
   b. For consecutive events (a, b):
      - gap = b.start - a.end
      - If 0 ≤ gap < 15 minutes AND different buildings → TIGHT_TRANSITION
```

### Threshold Constants

```java
private static final long TIGHT_TRANSITION_THRESHOLD_MS = 15 * 60_000; // 15 minutes
```

A gap of 0–14 minutes between events in different buildings is flagged because a professor physically cannot move between buildings quickly enough.

### Alternative Room Suggestions

For each `HARD_OVERLAP`, the engine queries:

```sql
SELECT * FROM locations
WHERE capacity >= ?
  AND loc_id NOT IN (
    SELECT DISTINCT loc_id FROM scheduled_events
    WHERE loc_id IS NOT NULL AND start_epoch < ? AND end_epoch > ?
  )
ORDER BY capacity
```

This finds rooms that:
1. Meet the minimum enrollment capacity
2. Are not booked during the conflicting time range
3. Are ordered by capacity (smallest suitable room first)

### ConflictResult Model

```java
public class ConflictResult {
    enum Severity { HARD_OVERLAP, TIGHT_TRANSITION }

    ScheduledEvent eventA, eventB;  // The two conflicting events
    Severity severity;               // Type of conflict
    String description;              // Human-readable explanation
    List<Location> availableAlternatives;  // Suggested rooms
}
```

---

## 7. Backtracking Auto-Resolver

### Algorithm

The `AutoResolver` implements a **constrained backtracking search** for room reassignment:

```
resolveAll():
    conflicts ← detectAll().filter(HARD_OVERLAP)
    for each conflict (A, B):
        if B already moved: skip
        alternatives ← findAvailable(B.start, B.end, minCapacity) - B.currentRoom
        for each room r in alternatives:
            TENTATIVE: B.locId ← r.locId; persist to DB
            newConflicts ← re-run full conflict analysis
            if B is NOT in any HARD_OVERLAP:
                ACCEPT → break (success)
            else:
                BACKTRACK: B.locId ← originalLocId; persist to DB
        if no alternative worked:
            mark as UNRESOLVABLE
```

### Correctness Guarantee

The re-analysis after each tentative move ensures:
- No **secondary conflicts** are introduced by the reassignment
- The full IntervalTree is rebuilt for accurate detection
- Only moves that strictly improve the conflict state are accepted

### Complexity

$$O(C \times A \times N \log N)$$

Where:
- $C$ = number of HARD_OVERLAP conflicts
- $A$ = maximum number of alternative rooms per conflict
- $N \log N$ = cost of full re-analysis (IntervalTree construction + query)

For typical university schedules ($C < 10$, $A < 20$, $N < 100$), this runs in under 200ms.

### ResolveResult

```java
public static class ResolveResult {
    int totalConflicts;   // How many HARD_OVERLAPs were found
    int resolved;         // Successfully relocated
    int unresolvable;     // No valid alternative exists
    List<String> actions; // Human-readable log of each action
}
```

Actions are formatted as:
- `RESOLVED: CS201 Lecture moved from Science A 201 to Library 301`
- `UNRESOLVABLE: ENG101 Lecture — no alternative rooms available`

---

## 8. Database Strategy (SQLite)

### Why SQLite?

- **Zero configuration** – no server installation, no port conflicts
- **Single file** – `facultysync.db` in the working directory
- **Portable** – the database file can be copied/shared
- **Embedded** – accessed via JDBC (`xerial/sqlite-jdbc`), bundled in the JAR

### Connection Management

`DatabaseManager` maintains a **single shared connection** with lazy initialization:

```java
public synchronized Connection getConnection() throws SQLException {
    if (connection == null || connection.isClosed()) {
        connection = DriverManager.getConnection(url);
        applyPragmas(connection);
    }
    return connection;
}
```

The `synchronized` keyword ensures thread safety since JavaFX background tasks may access the database concurrently.

### PRAGMAs

```sql
PRAGMA foreign_keys = ON;    -- Enforce FK constraints (disabled by default in SQLite)
PRAGMA journal_mode = WAL;   -- Write-Ahead Logging for concurrent readers
```

**WAL Mode** allows:
- Multiple readers simultaneously
- One writer without blocking readers
- Better performance than the default rollback journal

**Foreign Keys** are OFF by default in SQLite and must be enabled per-connection.

### Schema Design

5 normalized tables with referential integrity:

```
departments ─┐
             │ 1:N
professors ──┤
             │ 1:N
courses ─────┤
             │ 1:N
scheduled_events
             │ N:1
locations ───┘
```

**Key constraints:**
- `ON DELETE CASCADE` on professors → departments, courses → professors, scheduled_events → courses
- `ON DELETE SET NULL` on scheduled_events → locations (allows "online" events with no room)
- `CHECK(end_epoch > start_epoch)` prevents invalid time ranges
- `UNIQUE(building, room_number)` prevents duplicate rooms
- `UNIQUE` on `department.name` and `course.course_code`

### Indexing Strategy

```sql
CREATE INDEX idx_events_time ON scheduled_events(start_epoch, end_epoch);
CREATE INDEX idx_events_loc  ON scheduled_events(loc_id);
```

- `idx_events_time` – covers the overlap query pattern: `WHERE start_epoch < ? AND end_epoch > ?`
- `idx_events_loc` – covers location-based filtering and the `findAvailable()` subquery

### Epoch Timestamps

All times are stored as **epoch milliseconds** (`long`):
- Efficient range comparisons (`<`, `>`) without date parsing
- Timezone-agnostic storage
- Direct use in `System.currentTimeMillis()` and Java `Instant`

---

## 9. Data Access Objects (DAO Pattern)

### Pattern Overview

Each table has a corresponding DAO class providing CRUD operations:

| DAO | Table | Key Methods |
|-----|-------|-------------|
| `DepartmentDAO` | departments | `insert`, `findAll`, `findById`, `delete` |
| `ProfessorDAO` | professors | `insert`, `findAll`, `findById`, `delete` |
| `CourseDAO` | courses | `insert`, `findAll`, `findById`, `findByCode`, `delete` |
| `LocationDAO` | locations | `insert`, `findAll`, `findById`, `findAvailable`, `update`, `delete` |
| `ScheduledEventDAO` | scheduled_events | `insert`, `findAll`, `findById`, `findByTimeRange`, `findByLocation`, `findByCourse`, `findOverlapping`, `update`, `delete` |

### PreparedStatement Pattern

All DAOs use `PreparedStatement` to prevent SQL injection:

```java
String sql = "INSERT INTO courses (course_code, prof_id, enrollment_count) VALUES (?, ?, ?)";
try (PreparedStatement ps = dbManager.getConnection()
        .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
    ps.setString(1, course.getCourseCode());
    ps.setInt(2, course.getProfId());
    // ...
    ps.executeUpdate();
    try (ResultSet keys = ps.getGeneratedKeys()) {
        if (keys.next()) course.setCourseId(keys.getInt(1));
    }
}
```

### Null Handling

Nullable columns (e.g., `loc_id`, `capacity`) use wrapper types and explicit null checks:

```java
if (event.getLocId() != null) ps.setInt(2, event.getLocId());
else ps.setNull(2, Types.INTEGER);
```

On reads:
```java
int locId = rs.getInt("loc_id");
e.setLocId(rs.wasNull() ? null : locId);
```

### Specialized Queries

**`LocationDAO.findAvailable(startEpoch, endEpoch, minCapacity)`:**
Uses a subquery to exclude booked rooms, ordered by capacity (smallest suitable first).

**`ScheduledEventDAO.findOverlapping(locId, startEpoch, endEpoch)`:**
Finds all events in a specific room that overlap a time range — core query for conflict detection.

**`ScheduledEventDAO.findByTimeRange(startEpoch, endEpoch)`:**
Retrieves all events that overlap a calendar week for the CalendarView.

---

## 10. In-Memory Caching (DataCache)

### Purpose

The `DataCache` maintains `HashMap<Integer, T>` for all reference entities:
- Avoids hundreds of redundant `SELECT` queries during UI rendering
- Provides `O(1)` lookups by primary key
- Pre-enriches `ScheduledEvent` objects with display names

### Cache Maps

```java
Map<Integer, Location> locationCache
Map<Integer, Course> courseCache
Map<Integer, Professor> professorCache
Map<Integer, Department> departmentCache
```

### Refresh Strategy

`cache.refresh()` performs a **full reload** from the database:
1. Clear all four maps
2. `SELECT *` from each table
3. Populate maps with `put(entity.getId(), entity)`

Called:
- At startup (before rendering)
- After any data modification (add event, import CSV, auto-resolve)
- Before conflict analysis (to ensure freshness)

### Event Enrichment

```java
public void enrich(ScheduledEvent event) {
    Course c = getCourse(event.getCourseId());
    if (c != null) {
        event.setCourseCode(c.getCourseCode());           // e.g., "CS101"
        Professor p = getProfessor(c.getProfId());
        if (p != null) event.setProfessorName(p.getName()); // e.g., "Dr. Turing"
    }
    Location l = getLocation(event.getLocId());
    if (l != null) event.setLocationName(l.getDisplayName()); // e.g., "Science A 101"
}
```

This populates **transient display fields** on `ScheduledEvent` that are not persisted in the database but are needed for UI table columns, calendar blocks, and conflict descriptions.

---

## 11. UI/UX Design

### Layout Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    CustomTitleBar (40px)                      │
├──────────┬───────────────────────────────────────────────────┤
│          │                                                   │
│  Sidebar │              TabPane (5 tabs)                     │
│  (200px) │  ┌─────┬──────────┬──────────┬────────┬────────┐ │
│          │  │Home │ Schedule │Conflicts │Calendar│Analytics│ │
│  Logo    │  └─────┴──────────┴──────────┴────────┴────────┘ │
│  Nav     │                                                   │
│  Actions │              Content Area                         │
│  Filter  │                                                   │
│          │                                                   │
├──────────┴───────────────────────────────────────────────────┤
│                    Status Bar (25px)                          │
└──────────────────────────────────────────────────────────────┘
```

### DashboardController (845 lines)

The main layout controller manages:
- **Left sidebar** (`VBox`, 200px, `#2c3e50` dark background):
  - App logo + subtitle
  - Navigation buttons (tabs)
  - Department filter (`ComboBox`)
  - Data entry buttons (add department, professor, course, location, event)
  - Action buttons (analyze conflicts, auto-resolve)
  - Import/export buttons
- **Center content** (`TabPane` with 5 tabs)
- **Status bar** (`HBox` at bottom with event/conflict counts)

### Tab Views

| Tab | Class | Key JavaFX Components |
|-----|-------|-----------------------|
| **Home** | `HomePage` | ScrollPane, VBox, FlowPane (stat cards), GridPane (quick actions), VBox (activity list) |
| **Schedule** | DashboardController inline | TableView with 7 columns, CONSTRAINED_RESIZE_POLICY |
| **Conflicts** | DashboardController inline | TableView with color-coded rows, double-click dialog |
| **Calendar** | `CalendarView` | GridPane (7×16), ScrollPane, drag-and-drop handlers |
| **Analytics** | `AnalyticsView` | PieChart (2), BarChart (2), FlowPane (summary cards) |

### HomePage Design

- **Hero Banner**: LinearGradient `#1abc9c → #16a085` with drop shadow
- **Stat Cards**: 5 cards in a FlowPane with colored bottom borders:
  - Events (blue), Courses (green), Professors (purple), Rooms (orange), Departments (teal)
- **Quick Actions**: 4 clickable cards linking to other tabs
- **Recent Activity**: Last 8 events with course info & timestamps

### CalendarView Design

Google Calendar-style weekly grid:
- **Time axis**: 7 AM – 10 PM (16 hours, 1-hour rows)
- **Day axis**: Monday – Sunday
- **Navigation**: Previous/Today/Next week buttons
- **Event blocks**: Color-coded by event type, positioned by time
  - Green = Lecture, Blue = Exam, Purple = Office Hours, Red = Conflict
- **Drag-and-drop**: 
  - `setOnDragDetected` → `startDragAndDrop(TransferMode.MOVE)`
  - `setOnDragOver` → accept if cell is empty
  - `setOnDragDropped` → update event's start/end time, persist to DB

### AnalyticsView Design

4 charts in a 2×2 grid:
1. **Event Type Distribution** (PieChart): Lectures vs Exams vs Office Hours
2. **Peak Usage Hours** (BarChart): Events per hour of day
3. **Building Utilization** (PieChart): Percentage of events per building
4. **Department Activity** (BarChart): Events per department

5 summary cards above the charts showing totals.

---

## 12. Toast Notification System

### Architecture

`ToastNotification` provides in-app slide-in notifications:

```java
public static void show(StackPane container, String message, Type type)
```

**Types:** `SUCCESS` (green), `WARNING` (yellow), `ERROR` (red), `INFO` (blue)

### Animation Pipeline

```
1. Create HBox with icon + message + close button
2. Position at top-right with TranslateTransition (slide from right)
3. Show for 4 seconds
4. FadeTransition (opacity 1.0 → 0.0 over 300ms)
5. Remove from parent
```

### Stacking

Maximum 5 toasts visible simultaneously. Each new toast is added to a VBox in the top-right corner of the scene's StackPane root. Older toasts are shifted down automatically by VBox layout.

### Usage Points

Toasts are shown for:
- CSV import completion (success + count)
- Conflict analysis completion (warning if conflicts found, success if clean)
- Auto-resolve results (resolved count + unresolvable count)
- Data entry operations (event added, department created, etc.)
- Errors (file not found, database errors)

---

## 13. Native Windows Notifications

### SystemTray Integration

`NotificationService` uses `java.awt.SystemTray` for OS-level notifications:

```java
SystemTray tray = SystemTray.getSystemTray();
trayIcon = new TrayIcon(createTrayImage(), "FacultySync");
tray.add(trayIcon);
```

### Tray Icon Generation

A 16×16 icon is generated programmatically:
```java
BufferedImage img = new BufferedImage(16, 16, TYPE_INT_ARGB);
Graphics2D g = img.createGraphics();
g.setColor(new Color(44, 62, 80));   // Dark background
g.fillRoundRect(0, 0, 16, 16, 4, 4);
g.setColor(new Color(26, 188, 156)); // Teal "F"
g.setFont(new Font("SansSerif", Font.BOLD, 12));
g.drawString("F", 3, 13);
```

### Notification Types

```java
trayIcon.displayMessage(title, message, MessageType.INFO);
trayIcon.displayMessage(title, message, MessageType.WARNING);
trayIcon.displayMessage(title, message, MessageType.ERROR);
```

Shown for: conflict alerts, update availability, import completion.

### Graceful Degradation

If `SystemTray.isSupported()` returns false (e.g., on Linux/macOS), the notification system silently disables itself. All `notify()` calls become no-ops.

---

## 14. CSV Import/Export Pipeline

### Import: `CsvImporter`

**CSV Format:**
```
course_code,event_type,building,room_number,start_datetime,end_datetime
CS101,Lecture,Science Hall,101,2026-03-01 09:00,2026-03-01 10:00
```

**Processing pipeline:**
1. Read file with `BufferedReader` + `FileReader`
2. Skip header row
3. For each data row:
   a. Split by comma
   b. Look up `course_code` in database → get `course_id` (skip if not found)
   c. Look up or create location by `(building, room_number)`
   d. Parse datetime: supports `yyyy-MM-dd HH:mm` and raw epoch milliseconds
   e. Create `ScheduledEvent` and insert via `ScheduledEventDAO`
4. Report progress via callback for UI progress bar

### Export: `ReportExporter`

**Schedule CSV:**
```
event_id,course_code,event_type,location,start,end,professor
1,CS101,Lecture,Science A 101,2026-03-01 09:00,2026-03-01 10:00,Dr. Turing
```

**Conflict Report (TXT):**
```
=== FacultySync Conflict Report ===
Generated: 2026-02-27 12:00

--- Conflict #1 [HARD_OVERLAP] ---
Room Science A 201 is double-booked:
  CS201 Lecture overlaps with MATH201 Lecture
Available alternatives:
  - Library 301 (capacity: 80)
  - Engineering 202 (capacity: 60)
```

---

## 15. Auto-Update Checker

### Flow

```
1. Spawn daemon thread (won't prevent app exit)
2. HTTP GET → https://api.github.com/repos/990aa/facultysync/releases/latest
3. Parse JSON response → extract tag_name (e.g., "v0.2.0")
4. Regex extract version numbers → compare semantically
5. If newer: show JavaFX Alert dialog + native notification
6. "Update" button → open browser to releases page
```

### Version Comparison

```java
// Compare major.minor.patch numerically
int[] current = parseVersion("0.1.1");  // [0, 1, 1]
int[] remote  = parseVersion("0.2.0");  // [0, 2, 0]
// Compare element by element
```

### Timeout & Error Handling

- 5-second connection timeout
- All exceptions silently caught (network unavailable, API rate limit)
- Runs on `Platform.runLater()` for UI updates

---

## 16. Concurrency Model

### Thread Architecture

```
JavaFX Application Thread (UI)
  ├── Handles all UI events and rendering
  ├── Scene graph modifications MUST happen here
  └── Never blocked by I/O operations

Background Threads (via JavaFX Task<V>)
  ├── CSV Import (with progress callback)
  ├── Conflict Analysis
  ├── Auto-Resolve
  └── Data Cache Refresh

Daemon Thread
  └── Update Checker (HTTP request to GitHub API)
```

### JavaFX Task Pattern

```java
Task<List<ConflictResult>> analysisTask = new Task<>() {
    @Override
    protected List<ConflictResult> call() throws Exception {
        return conflictEngine.analyzeAll();
    }
};
analysisTask.setOnSucceeded(e -> {
    // Update UI on FX thread
    conflictsTable.getItems().setAll(analysisTask.getValue());
});
new Thread(analysisTask).start();
```

### Progress Binding

Import tasks report progress that is bound to a UI ProgressBar:

```java
updateProgress(currentRow, totalRows);
// In UI:
progressBar.progressProperty().bind(task.progressProperty());
```

---

## 17. Testing Strategy

### Framework

- **JUnit Jupiter 5.10.2** with `@TestInstance(PER_CLASS)` for shared state
- **In-memory SQLite** (`jdbc:sqlite::memory:`) for isolated database tests
- **JavaFX `Platform.startup()`** for headless UI component testing
- **`@TestMethodOrder(OrderAnnotation.class)`** for ordered execution when state is shared

### Test Suites

| Suite | Tests | Focus | Technique |
|-------|-------|-------|-----------|
| `IntervalTreeTest` | 27 | Algorithm correctness | Unit: empty trees, single/multi intervals, edge cases, 1000-interval stress test |
| `DatabaseTest` | 36 | Schema + CRUD | Integration: in-memory SQLite, all 5 DAOs, FK violations, cascade deletes |
| `SeedDataTest` | 15+ | Seed integrity | Integration: verify entity counts, FK relationships, intentional conflicts present |
| `ModelTest` | 48 | POJOs | Unit: all getters/setters, equals/hashCode, toString, EventType.fromString |
| `IoTest` | 19 | CSV I/O | Integration: valid/invalid imports, missing fields, special characters, export format |
| `DataCacheTest` | 14 | Cache correctness | Integration: load, enrich, null handling, refresh |
| `ConflictEngineTest` | 10 | Detection accuracy | Integration: HARD_OVERLAP, TIGHT_TRANSITION, online events, multi-overlap |
| `AutoResolverTest` | 10+ | Resolution logic | Integration: successful resolution, backtracking, unresolvable detection |
| `CustomTitleBarTest` | 26 | Title bar UI | JavaFX headless: structure, IDs, dimensions, icon, maximize/restore, visual bounds |

### Headless JavaFX Testing

```java
@BeforeAll
void initFxAndBuildBar() throws Exception {
    CountDownLatch startupLatch = new CountDownLatch(1);
    try {
        Platform.startup(startupLatch::countDown);  // Boot JavaFX toolkit
    } catch (IllegalStateException e) {
        startupLatch.countDown(); // Already started
    }
    // All assertions run on Platform.runLater() via helper method
}
```

The `onFx(Runnable)` helper ensures all JavaFX assertions run on the FX Application Thread:

```java
private void onFx(Runnable action) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> error = new AtomicReference<>();
    Platform.runLater(() -> {
        try { action.run(); }
        catch (Throwable t) { error.set(t); }
        finally { latch.countDown(); }
    });
    latch.await(10, TimeUnit.SECONDS);
    if (error.get() != null) fail(error.get());
}
```

### Key Test Scenarios

**Window not covering taskbar:**
```java
@Test
void maximize_usesVisualBounds() {
    Rectangle2D visual = Screen.getPrimary().getVisualBounds();
    titleBar.getMaximizeButton().fire();
    assertTrue(stage.getWidth() <= visual.getWidth() + 1);
    assertTrue(stage.getHeight() <= visual.getHeight() + 1);
}
```

**Maximize/restore cycle:**
```java
@Test
void maximize_restore_returnsToPreviousSize() {
    double origW = stage.getWidth(), origH = stage.getHeight();
    titleBar.getMaximizeButton().fire();  // maximize
    titleBar.getMaximizeButton().fire();  // restore
    assertEquals(origW, stage.getWidth(), 2);
    assertEquals(origH, stage.getHeight(), 2);
}
```

**IntervalTree stress test:**
```java
@Test
void stressTest_1000Intervals() {
    // Insert 1000 random intervals, verify all overlaps are detected
}
```

---

## 18. CI/CD Pipeline

### GitHub Actions Workflow (`.github/workflows/build.yml`)

Triggered on tag push matching `v*`:

```yaml
on:
  push:
    tags: ['v*']
```

### Pipeline Steps

```
1. Checkout code
2. Setup Java 25 (Temurin)
3. Extract version from tag (v0.2.0 → 0.2.0)
4. ./gradlew clean build distZip2 -x test  (build artifacts)
5. ./gradlew test  (run all tests)
6. Convert app-icon.png → .ico via .NET
7. jpackage → MSI installer (with WiX Toolset)
   - Type: MSI
   - Icon: build/app-icon.ico
   - Main class: edu.facultysync.App
   - Options: --win-dir-chooser --win-menu --win-shortcut
8. Upload to GitHub Release:
   - FacultySync-{version}-windows.zip
   - FacultySync-{version}.msi
```

### jpackage Configuration

```bash
jpackage \
  --type msi \
  --name FacultySync \
  --app-version {ver} \
  --vendor "Abdul Ahad" \
  --icon build/app-icon.ico \
  --input build/install/facultysync/lib \
  --main-jar facultysync-{ver}.jar \
  --main-class edu.facultysync.App \
  --win-dir-chooser --win-menu --win-shortcut
```

Produces a Windows MSI installer with:
- Start Menu shortcut
- Desktop shortcut
- Custom install directory chooser
- Application icon in Add/Remove Programs

---

## 19. Release Automation

### `release.ps1` Script

Automates the full release pipeline from local machine:

```
1. Read current version from build.gradle
2. Bump version (major/minor/patch) or use explicit
3. Update version in: build.gradle, App.java, README.md
4. Build: ./gradlew clean distZip2
5. Test: ./gradlew test
6. Git: add → commit → tag → push
7. GitHub: gh release create with zip attached
```

### Version Bumping

```powershell
function Bump-Version($Current, $Type) {
    $parts = $Current.Split('.')
    switch ($Type) {
        "major" { $major++; $minor = 0; $patch = 0 }
        "minor" { $minor++; $patch = 0 }
        "patch" { $patch++ }
    }
}
```

### Files Updated by Release Script

| File | Field Updated |
|------|---------------|
| `build.gradle` | `version = '0.2.0'` |
| `App.java` | `VERSION = "0.2.0"` |
| `README.md` | Download link `v0.2.0` |

### Dry Run

```powershell
.\release.ps1 -BumpType minor -DryRun
```

Shows all actions that would be taken without modifying any files, git, or GitHub.

---

## 20. CSS Theming Architecture

### File: `src/main/resources/style.css` (746 lines)

### Color Palette

| Variable | Hex | Usage |
|----------|-----|-------|
| Dark navy | `#2c3e50` | Sidebar, title bar background |
| Teal accent | `#1abc9c` | Accent color, hero gradient, buttons |
| Light gray | `#ecf0f1` | Tab headers, status bar |
| Content bg | `#f5f7fa` | Main content area background |
| Text dark | `#2c3e50` | Primary text color |
| Text muted | `#7f8c8d` | Secondary text, timestamps |
| Success green | `#27ae60` | Success toasts, lecture events |
| Warning yellow | `#f39c12` | Warning toasts, tight transitions |
| Error red | `#e74c3c` | Close button hover, error toasts, conflicts |
| Info blue | `#3498db` | Info toasts, exam events, selection |
| Purple | `#9b59b6` | Office hours events, resolve button |

### CSS Structure

```
style.css
├── Global / Root
├── Custom Title Bar (.custom-title-bar, .title-btn, .title-btn-close:hover)
├── Left Sidebar (.left-panel, .sidebar-btn)
├── Action Buttons (.primary-btn, .warning-btn, .resolve-btn)
├── Tab Pane (.main-tab-pane)
├── Table View (column headers, alternating rows, selection)
├── Status Bar
├── Progress Bar
├── Conflict Summary
├── Empty State
├── Scrollbar
├── Home Page (hero, stat cards, action cards, activity)
├── Calendar View (grid, navigation, event blocks, drag target)
├── Analytics View (chart wrappers, summary cards)
└── Toast Notifications (success/warning/error/info variants)
```

### Defensive Loading

```java
URL cssUrl = getClass().getResource("/style.css");
if (cssUrl != null) {
    scene.getStylesheets().add(cssUrl.toExternalForm());
} else {
    System.err.println("Warning: style.css not found on classpath.");
}
```

The null-check prevents `NullPointerException` during testing or when running from an IDE that doesn't include resources.

---

## 21. Seed Data Generator

### `SeedData.seedIfEmpty(DatabaseManager)`

Checks if the database has any departments. If empty, populates:

| Entity | Count | Examples |
|--------|-------|---------|
| Departments | 5 | Computer Science, Mathematics, Physics, Engineering, Business |
| Professors | 9 | Turing, Dijkstra, Knuth, Euler, Gauss, Newton, Feynman, Tesla, Drucker |
| Courses | 10 | CS101–CS401, MATH101–201, PHYS101–201, ENG101, BUS101 |
| Locations | 10 | 5 buildings × 2 rooms each |
| Events | 30+ | Lectures (Mon–Fri), Exams (Week 2), Office Hours |

### Intentional Conflicts (for Demo)

4 conflicts are seeded deliberately:
1. **HARD_OVERLAP #1**: CS201 and MATH201 in Science A 201, Monday overlapping
2. **HARD_OVERLAP #2**: ENG101 and PHYS101 in Engineering Hall 100, Wednesday overlapping
3. **HARD_OVERLAP #3**: CS101 Office Hours and PHYS101 Office Hours in Library Seminar-1, same time
4. **TIGHT_TRANSITION**: Dr. Turing teaching CS101 (Science A 101) then CS401 (Engineering Hall 100), Thursday with 5-minute gap

### Idempotency

`seedIfEmpty()` only runs when `departments` table is empty, preventing duplicate data on subsequent launches.

### CLI Seed + Analysis

`SeedAndResolve` class provides command-line seed and analysis:

```bash
./gradlew seedAndResolve
```

Output:
```
Seeding database...
Database seeded: 5 departments, 9 professors, 10 courses, 10 locations, 30+ events
Running conflict analysis...
Detected 4 conflicts (3 HARD_OVERLAP, 1 TIGHT_TRANSITION)
Running auto-resolve...
Resolved: 2 | Unresolvable: 1
```

---

## 22. Key Design Decisions & Trade-offs

### 1. Undecorated Window vs. Native Decorations

**Decision:** Use `StageStyle.UNDECORATED` with custom title bar.

**Pros:**
- Full control over appearance and branding
- Consistent dark theme across title bar and sidebar
- Custom maximize behavior respecting visual bounds

**Cons:**
- Must implement drag-to-move, resize, minimize/maximize/close manually
- Requires additional testing for window management
- May not respect all OS accessibility features

**Mitigation:** Comprehensive tests verify all window behaviors. Visual bounds (`getVisualBounds()`) are used instead of `getBounds()` to ensure the Windows taskbar is never covered.

### 2. SQLite vs. H2/PostgreSQL

**Decision:** SQLite with single-file storage.

**Pros:**
- Zero configuration — no database server needed
- Portable — single `facultysync.db` file
- Proven reliability (billions of deployments)

**Cons:**
- Single-writer limitation (mitigated by WAL mode)
- No multi-user support
- Limited concurrent write performance

### 3. IntervalTree vs. Brute-Force Detection

**Decision:** Custom `IntervalTree<T extends Schedulable>` implementation.

**Pros:**
- `O(log N + k)` query vs. `O(N²)` brute force
- Generic — works with any `Schedulable` type
- Elegant pruning via `maxEnd` augmentation

**Cons:**
- More complex implementation
- Overkill for very small datasets (< 10 events)

**Justification:** Scales to real university schedules with thousands of events. The balanced build ensures optimal tree height.

### 4. Backtracking vs. ILP/GA for Resolution

**Decision:** Simple backtracking room reassignment.

**Pros:**
- Deterministic — same input always produces same output
- Easy to explain and verify
- Fast for typical conflict counts

**Cons:**
- Only handles room reassignment (not time-slot optimization)
- Exponential worst case (mitigated by bounded alternatives)

**Justification:** The sub-problem of room reassignment is tractable (`C × A` search space). Full timetabling optimization is NP-complete and beyond the project scope.

### 5. JavaFX `Task<V>` vs. `CompletableFuture`

**Decision:** Use JavaFX `Task<V>` for background work.

**Pros:**
- Built-in `progress` and `message` properties bind directly to UI
- `setOnSucceeded`/`setOnFailed` automatically run on FX thread
- Cancellation support

**Cons:**
- JavaFX-specific (not portable to non-JavaFX code)

### 6. Epoch Milliseconds vs. ISO 8601 Strings

**Decision:** Store times as `long` epoch milliseconds.

**Pros:**
- Efficient integer comparisons (`<`, `>`) in SQL and Java
- No timezone issues — UTC-agnostic
- Direct mapping to `System.currentTimeMillis()` and `Instant`

**Cons:**
- Not human-readable in the database
- Requires conversion for display

---

*Technical reference document for FacultySync v0.2.0 — February 2026.*
*Author: Abdul Ahad (Reg. No. 245805010), Manipal Institute of Technology.*
