# 2ndBrain 🧠
2ndBrain is a personal productivity cockpit for Android. It captures high-signal data from your daily digital life—notifications, clipboard snippets, smartwatch logs, and app usage—into a private local store. It then leverages advanced AI to provide proactive advice, daily briefings, and insights into your routine.

## ✨ Key Features

### 📡 Smart Capture (Notifications & Clipboard)
- **Automatic Logging**: Captures and groups notifications from high-signal apps like Gmail, Calendar, and Todoist.
- **App-Specific Deep Linking**: Instantly jump from a capture back to the original email, calendar event, or task.
- **Strict Monitoring**: Choose exactly which apps are allowed to enter your "Brain" via the granular Setup menu.
- **Duplicate Merging**: Smartly merges repetitive notifications to keep your feed clean.
- **Smart Folders & Dynamic Tagging**: Dynamically tags incoming captures (e.g. `#Work`, `#Health`, `#Social`, `#Finance`, `#Reference`) and allows instant filtering via a glassmorphic top-level horizontal chip selector.
- **⚡ Real-Time Group Highlights**: Heuristically extracts high-signal values (such as physical steps or purchase dollars) and displays an aggregated group highlight (e.g. *"⚡ Logged 3 payments totaling $15.50."*).

### ⌚ Smartwatch & Physical Wellness (Health Connect)
- **Central Health Sync**: Integrates with Android **Health Connect** to seamlessly read smartwatch wellness data (e.g., Google Fit, Samsung Health, Zepp/Amazfit).
- **Physical Insights Dashboard**: Home cockpit renders steps walked today, sleep duration last night, and active heart rate zones (min/max/avg) in real-time.
- **Physical-Digital Correlation**: Correlates your sleep and physical activity metrics with your digital focus and routine tasks to compute your custom **Sense of Day Score** (HSV-mapped progress ring).

### ⏰ Dynamic Medication Reminders & Routine Alarms (Phase 2)
- **SQLite-Backed Habit Engine**: Dynamic scheduling and persistence of custom daily habits, medication times, and routines.
- **Proactive Notification Alarms**: Reminders triggered using exact alarms via Android's `AlarmManager` with automatic boot-rescheduling.
- **Dynamic Quick-Actions**: Tapping `[ Done ]` or `[ Take Meds ]` directly from the notification shade (or paired smartwatch) registers completion logs in the background without needing to launch the app.
- **Streak & Performance Visualizer**: Cockpit presents a weekly progress strip featuring **7 beautifully styled circular HSL progress rings** representing routine completion over the past 7 days.
- **AI Co-Pilot Integration**: Today's checked-off routines and alarms are automatically injected into the AI Reflection/Briefing prompt context so your co-pilot can track your physical habits.

### 🤖 AI Intelligence & Private Co-Pilot
- **💬 Interactive Co-Pilot Chat**: A beautiful sidebar-driven bubble log ([BrainChatScreen.kt](file:///Users/alexirazabal/AndroidStudioProjects/2ndBrain/app/src/main/java/com/alex/a2ndbrain/ui/chat/BrainChatScreen.kt)) to ask direct questions about your captures, clipboard logs, and daily usage. It pulls relevant DB context in real-time.
- **🔍 Concept-Expanding Semantic Search**: Intercepts queries (like "workout", "gmail", "spending") and matches their underlying semantic intent against smart folder tags.
- **Morning Briefings**: Generates a "game plan" between 4 AM - 11 AM based on upcoming meetings and yesterday's unfinished tasks.
- **Evening Reflections**: Analyzes how your day actually went by comparing your intended tasks with your actual screen time.
- **Conflict Detection**: Flags "Distraction Gaps" where significant time was spent on non-productive apps during busy work windows.
- **Customizable Models**: Dynamically downloads and runs local offline **LiteRT** models or connects securely to remote Gemini APIs directly configured inside Settings.

### 🕒 Digital Time & Habits
- **📊 Habit Correlation Engine**: An active insight block correlating distraction trends (like spending 60+ mins on social media apps) against focused productivity tool usage.
- **Cross-Device Sync**: Uses your **Obsidian Vault** as a private data bridge to synchronize usage stats across all your Android devices (conflict-free).
- **Consolidated View**: See total time spent per app across your entire device ecosystem.
- **Visual Charts**: Beautiful, modern bar charts showing your usage distribution.
- **Time Series Reports**: Toggle between Today, This Week, and This Month views.

### 🗒️ Obsidian Integration
- **Vault Explorer**: Navigate your Obsidian folders and read Markdown notes directly inside the app.
- **Deep Linking**: Open any note in the Obsidian app for editing with a single tap.
- **Quick Capture**: Create new notes in your vault with an automated timestamped template.

---

## 🎨 Modern Design
- **Craft-Inspired UI**: Minimalist aesthetic with hero headers and pastel accent coding.
- **Sidebar Navigation**: Efficient vertical `NavigationRail` for quick switching between Home, Feed, Brain, Notes, Time, Co-pilot, and Settings.
- **Responsive Feedback**: Real-time loading indicators for AI generation, data syncing, and offline thinking models.

---

## 🛠️ Project Structure
- `ui/chat/`: Interactive offline-first Q&A Co-Pilot bubble logs and thinking indicators.
- `ui/memories/`: The main notification feed, filter chips, and capture controls.
- `ui/reflection/`: AI history, dynamic model downloading, and briefing/reflection cards.
- `ui/usage/`: Digital Time dashboard, visual charts, habit correlation cards, and multi-device totals.
- `ui/notes/`: Obsidian vault explorer and markdown preview.
- `core/usage/`: Conflict-free sync engine and `UsageStatsManager` integration.
- `core/reflection/`: Gemini API, LiteRT on-device LLM picker, and briefing logic.
- `core/capture/`: Notification listener service and clipboard manager with heuristic auto-tagging.
- `core/health/`: Health Connect bridge for step count, sleep, and heart-rate tracking.

---

## 🚀 Build and Run
1. Open the project in Android Studio and sync Gradle.
2. Provide `gemini.api.key` (optional) in `local.properties` or the in-app Setup screen.
3. Every build automatically increments the version (e.g., `1.0.4`), visible in the Sidebar.
4. **Permissions**:
   - Enable **Notification Access** to capture events.
   - Enable **Usage Access** for Digital Time tracking.
   - Enable **Health Connect Permissions** inside the Home tab.
   - Select your **Obsidian Vault** folder in the Notes tab to enable syncing.

---
*Your private data stays local or in your own vault. No cloud servers required.*
