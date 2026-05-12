# 2ndBrain
2ndBrain is an Android app that captures high-signal notifications and clipboard snippets into a local memory store, then generates daily reflections from that history.
## Project structure
- `app/src/main/java/com/alex/a2ndbrain/MainActivity.kt`: app entrypoint and top-level tab navigation.
- `app/src/main/java/com/alex/a2ndbrain/ui/memories/MemoryScreen.kt`: memory browsing UI, grouping, filters, and capture actions.
- `app/src/main/java/com/alex/a2ndbrain/ui/reflection/ReflectionScreen.kt`: reflection history UI and model selector.
- `app/src/main/java/com/alex/a2ndbrain/core/capture/*`: notification/clipboard capture and local settings.
- `app/src/main/java/com/alex/a2ndbrain/core/memory/*`: Room entities, DAO, and database setup.
- `app/src/main/java/com/alex/a2ndbrain/core/reflection/*`: reflection scheduling, worker execution, and Gemini integration.
## Build and run
1. Open the project in Android Studio and sync Gradle.
2. Provide `gemini.api.key` (optional) in `~/.gradle/gradle.properties` or project `gradle.properties`.
3. Build from terminal:
   - `./gradlew :app:assembleDebug`
4. Install and run on a device/emulator:
   - `./gradlew :app:installDebug`
## Runtime configuration
- Notification capture is enabled via system Notification Access settings.
- Monitored apps and Gemini key/model are configured in the in-app setup screens and persisted in shared preferences.
- Reflection summaries are generated on demand and via periodic WorkManager scheduling.
## Notes for maintainers
- UI is split by feature (`ui/memories`, `ui/reflection`) to keep files focused and easier to evolve.
- Reflection generation falls back to a local template when no Gemini key is available.
