# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Deploy

```bash
# Build debug APK
./gradlew assembleDebug

# Build and install on all connected ADB devices
./gradlew installDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.alex.a2ndbrain.MeditationManagerTest"

# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

**Version auto-increment**: Every `./gradlew` build increments `app/version.properties`. Do not manually edit that file.

## Configuration

- `gradle.properties` — `gemini.model` controls which Gemini model is used at build time (current: `gemini-2.5-flash`)
- `local.properties` — `gemini.api.key` for Gemini API access. This can also be set at runtime via the in-app Settings screen (stored encrypted via `CaptureSettingsManager`)
- The Gemini API key in `local.properties` is **not** injected into the build; it is read at runtime from `EncryptedSharedPreferences`

## Architecture

### Dependency Injection
All dependencies are wired in a **single Koin module**: `core/di/AppModule.kt`. Every ViewModel, Repository, Manager, and the Room database instance is declared there. When adding a new injectable class, register it in that file — there are no other DI modules.

### Room Database
- Single database: `second_brain_db`, class `AppDatabase`, currently at **schema version 14**
- 5 entities: `MemoryEntity`, `UsageStatEntity`, `HabitEntity`, `HabitCompletionEntity`, `DailySummaryEntity`
- All DAOs go through the single `MemoryDao` and `HabitsDao` interfaces
- **Migration protocol**: When bumping `version` in `@Database`, add a `Migration` object to `DatabaseMigrations.ALL_MIGRATIONS`. Never use `fallbackToDestructiveMigration()` — it is intentionally restricted to versions 1–13 only. The schema snapshot lives in `app/schemas/`

### Data Flow Pattern
UI screens receive only plain data and lambda callbacks — no ViewModel references are passed into composables. ViewModels expose `StateFlow` for state and `SharedFlow` for one-shot events (e.g., `habitUncompleted` for undo snackbars). All DB operations run on `Dispatchers.IO`. Use `suspend` DAO methods for one-shot reads; reserve `Flow`-returning DAO methods for reactive UI binding only — never call `.first()` on a Room Flow inside a coroutine.

### AI Inference (`core/reflection/`)
`ReflectionManager.runChatInference()` is the single entry point for all AI calls. It delegates to `ModelPicker`, which chooses between:
1. **On-device** (LiteRT/Qwen via `ModelDownloader`) for short, simple queries
2. **Cloud** (`GeminiAgent`) for complex queries — tries `gemini-2.5-flash` → `1.5-flash` → `1.5-pro` in sequence on failure

`CopilotViewModel` builds the prompt context dynamically via keyword routing (health/habits/usage/meditation sections are included only when relevant) to avoid overloading on-device models.

### P2P Sync (`core/sync/`)
`NearbySyncManager` uses Google Nearby Connections (P2P_STAR strategy). Each device advertises and discovers simultaneously. A tie-breaker (`localDeviceId < remoteDeviceId`) prevents both sides from initiating simultaneously. Sync payload contains **only the local device's own** usage stats (filtered by `deviceId`) plus meditation sessions. On receive, `importUsageStats()` applies last-timestamp-wins conflict resolution — it never blindly replaces a newer local record with an older incoming one.

### Notification Capture
`NotificationCaptureService` is a `NotificationListenerService` — it requires the user to grant **Notification Access** in system settings. It auto-tags captures with `#Health`, `#Work`, `#Finance`, `#Social`, or `#Reference` based on keyword matching in `MemoryEntity.create()`.

### HomeViewModel
The most complex ViewModel in the project. It combines 5 reactive sources (`allMemoriesForHome`, `vaultNotes`, `activeHabitsToday`, `completedHabitIdsToday`, `monitoredAppsState`) via `combine()` to build `todayTimelineEvents`. The deduplication logic inside that pipeline uses two `HashMap`s for O(n) performance — do not revert it to a list scan.

### Obsidian Integration
Uses `DocumentFile` + `ContentResolver` (no direct filesystem access). Time patterns are extracted from note lines via `findTimePattern()` in `HomeViewModel`, which supports three formats: `H:MM [AM/PM]`, `H AM/PM`, and 4-digit military time with explicit `at`/`@` prefix (e.g., `at 1430`).

### WorkManager Background Jobs
| Worker | Schedule | Purpose |
|--------|----------|---------|
| `UsageSyncWorker` | Every 4h | Collect local usage stats |
| `P2pSyncWorker` | Every 15m | Trigger Nearby Connections sync |
| `ReflectionWorker` | Every 6h | Generate AI briefing/reflection |

WorkManager tasks are scheduled from `MainActivity.onCreate()` via the respective manager classes.

## Key Permissions Required at Runtime
- `BIND_NOTIFICATION_LISTENER_SERVICE` — notification capture (system settings, not a runtime permission)
- `PACKAGE_USAGE_STATS` — digital time tracking (system settings)
- Health Connect permissions — requested from `HomeViewModel.checkHealthPermissionsAndSync()`
- `ACCESS_FINE_LOCATION` + Bluetooth permissions — Nearby Connections P2P sync
