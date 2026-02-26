<#
.SYNOPSIS
    FacultySync Release Script – builds, versions, and publishes a GitHub release.

.DESCRIPTION
    Automates the full release pipeline:
    1. Bumps the version (major/minor/patch) or uses an explicit version
    2. Updates version in build.gradle, App.java, and README.md
    3. Builds the standalone distribution zip
    4. Runs tests
    5. Commits, tags, and pushes to GitHub
    6. Creates a GitHub Release with the distribution zip attached

    Use -DryRun to preview all steps without modifying files, git, or GitHub.

.PARAMETER BumpType
    Semantic version bump type: major, minor, or patch.

.PARAMETER Version
    Explicit version string (e.g., "0.1.0"). Overrides BumpType.

.PARAMETER DryRun
    Preview mode – shows what would be done without making any changes.

.EXAMPLE
    .\release.ps1 -Version 0.1.0
    .\release.ps1 -BumpType patch
    .\release.ps1 -BumpType minor -DryRun
#>

param(
    [ValidateSet("major", "minor", "patch")]
    [string]$BumpType,

    [string]$Version,

    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

# --- Helpers ---

function Get-CurrentVersion {
    $content = Get-Content "build.gradle" -Raw
    if ($content -match "version\s*=\s*'([^']+)'") {
        return $Matches[1]
    }
    throw "Could not find version in build.gradle"
}

function Bump-Version {
    param([string]$Current, [string]$Type)
    $parts = $Current.Split('.')
    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    $patch = [int]$parts[2]

    switch ($Type) {
        "major" { $major++; $minor = 0; $patch = 0 }
        "minor" { $minor++; $patch = 0 }
        "patch" { $patch++ }
    }
    return "$major.$minor.$patch"
}

function Update-VersionInFiles {
    param([string]$OldVersion, [string]$NewVersion)

    # build.gradle
    $gradle = Get-Content "build.gradle" -Raw
    $gradle = $gradle -replace "version\s*=\s*'$([regex]::Escape($OldVersion))'", "version = '$NewVersion'"
    Set-Content "build.gradle" -Value $gradle -NoNewline

    # App.java
    $appFile = "src/main/java/edu/facultysync/App.java"
    $app = Get-Content $appFile -Raw
    $app = $app -replace "VERSION\s*=\s*`"$([regex]::Escape($OldVersion))`"", "VERSION = `"$NewVersion`""
    Set-Content $appFile -Value $app -NoNewline

    # README.md – update download link
    $readme = Get-Content "README.md" -Raw
    $readme = $readme -replace "v$([regex]::Escape($OldVersion))", "v$NewVersion"
    Set-Content "README.md" -Value $readme -NoNewline

    Write-Host "[OK] Updated version $OldVersion -> $NewVersion in build.gradle, App.java, README.md" -ForegroundColor Green
}

# --- Main ---

Write-Host "`n=== FacultySync Release Script ===" -ForegroundColor Cyan
if ($DryRun) { Write-Host "[DRY RUN] No files, git, or GitHub will be modified.`n" -ForegroundColor Magenta }

# Determine version
$currentVersion = Get-CurrentVersion
Write-Host "Current version: $currentVersion"

if ($Version) {
    $newVersion = $Version
} elseif ($BumpType) {
    $newVersion = Bump-Version -Current $currentVersion -Type $BumpType
} else {
    throw "Specify either -Version or -BumpType (major|minor|patch)"
}

Write-Host "New version:     $newVersion" -ForegroundColor Yellow

# Update version in source files
if ($newVersion -ne $currentVersion) {
    if ($DryRun) {
        Write-Host "[DRY RUN] Would update version $currentVersion -> $newVersion in build.gradle, App.java, README.md" -ForegroundColor Magenta
    } else {
        Update-VersionInFiles -OldVersion $currentVersion -NewVersion $newVersion
    }
}

# Build
Write-Host "`n--- Building distribution ---" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY RUN] Would run: gradle clean distZip2" -ForegroundColor Magenta
} else {
    & gradle clean distZip2 2>&1 | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }
}

$zipName = "FacultySync-$newVersion-windows.zip"
$zipPath = "build/distributions/$zipName"
if (-not $DryRun) {
    if (-not (Test-Path $zipPath)) {
        throw "Distribution zip not found: $zipPath"
    }
    Write-Host "[OK] Built $zipName ($([math]::Round((Get-Item $zipPath).Length / 1MB, 1)) MB)" -ForegroundColor Green
} else {
    Write-Host "[DRY RUN] Would produce: $zipPath" -ForegroundColor Magenta
}

# Run tests
Write-Host "`n--- Running tests ---" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY RUN] Would run: gradle test" -ForegroundColor Magenta
} else {
    & gradle test 2>&1 | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Tests failed" }
    Write-Host "[OK] All tests passed" -ForegroundColor Green
}

# Git commit and tag
Write-Host "`n--- Git operations ---" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY RUN] Would run:" -ForegroundColor Magenta
    Write-Host "  git add -A" -ForegroundColor Magenta
    Write-Host "  git commit -m `"release: v$newVersion`"" -ForegroundColor Magenta
    Write-Host "  git tag -a `"v$newVersion`" -m `"Release v$newVersion`"" -ForegroundColor Magenta
    Write-Host "  git push origin main --tags" -ForegroundColor Magenta
} else {
    git add -A
    git commit -m "release: v$newVersion" --allow-empty
    git tag -a "v$newVersion" -m "Release v$newVersion" -f
    git push origin main --tags -f
    Write-Host "[OK] Committed and pushed v$newVersion" -ForegroundColor Green
}

# Create GitHub Release
Write-Host "`n--- Creating GitHub Release ---" -ForegroundColor Cyan
$releaseNotes = @"
## FacultySync v$newVersion

### Features
- Custom undecorated title bar with drag-to-move, maximize, minimize, close, and edge-resize
- Native Windows notifications via SystemTray
- Auto-update checker from GitHub Releases
- 5-tab dashboard: Home, Schedule, Conflicts, Calendar, Analytics
- IntervalTree-based O(N log N) conflict detection
- Backtracking auto-resolve algorithm
- Google Calendar-style weekly view with drag-and-drop
- Analytics charts (PieChart + BarChart)
- Animated toast notification system
- CSV import/export with progress tracking
- Demo seed data: 5 departments, 9 professors, 10 courses, 10 locations, 30+ events

### Download
Download ``$zipName`` and extract to run FacultySync.
"@

if ($DryRun) {
    Write-Host "[DRY RUN] Would run: gh release create `"v$newVersion`" $zipPath --title `"FacultySync v$newVersion`" --latest" -ForegroundColor Magenta
    Write-Host "[DRY RUN] Release notes:" -ForegroundColor Magenta
    Write-Host $releaseNotes -ForegroundColor DarkGray
} else {
    gh release create "v$newVersion" $zipPath `
        --title "FacultySync v$newVersion" `
        --notes $releaseNotes `
        --latest

    if ($LASTEXITCODE -ne 0) { throw "GitHub release creation failed" }
    Write-Host "[OK] GitHub release v$newVersion created with $zipName attached" -ForegroundColor Green
}

Write-Host "`n=== Release v$newVersion complete! ===" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY RUN] No changes were made. Remove -DryRun to execute for real." -ForegroundColor Magenta
} else {
    Write-Host "View release: https://github.com/990aa/facultysync/releases/tag/v$newVersion"
}
Write-Host ""
