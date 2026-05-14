# 2ndBrain 🧠
2ndBrain is a personal productivity cockpit for Android. It captures high-signal data from your daily digital life—notifications, clipboard snippets, and app usage—into a private local store. It then leverages advanced AI to provide proactive advice, daily briefings, and insights into your routine.

## ✨ Key Features

### 📡 Smart Capture (Notifications & Clipboard)
- **Automatic Logging**: Captures and groups notifications from high-signal apps like Gmail, Calendar, and Todoist.
- **App-Specific Deep Linking**: Instantly jump from a capture back to the original email, calendar event, or task.
- **Strict Monitoring**: Choose exactly which apps are allowed to enter your "Brain" via the granular Setup menu.
- **Duplicate Merging**: Smartly merges repetitive notifications to keep your feed clean.

### 🤖 AI Intelligence (Gemini 3.1 / 2.5)
- **Morning Briefings**: Generates a "game plan" between 4 AM - 11 AM based on upcoming meetings and yesterday's unfinished tasks.
- **Evening Reflections**: Analyzes how your day actually went by comparing your intended tasks with your actual screen time.
- **Conflict Detection**: Flags "Distraction Gaps" where significant time was spent on non-productive apps during busy work windows.
- **Customizable Models**: Choose between Gemini 3.1 Pro, 3.1 Flash-Lite, or 2.5 Flash for your insights.

### 🕒 Digital Time (Screen Time Analysis)
- **Cross-Device Sync**: Uses your **Obsidian Vault** as a private data bridge to synchronize usage stats across all your Android devices (conflict-free).
- **Consolidated View**: See total time spent per app across your entire device ecosystem.
- **Visual Charts**: Beautiful, modern bar charts showing your usage distribution.
- **Time Series Reports**: Toggle between Today, This Week, and This Month views.

### 🗒️ Obsidian Integration
- **Vault Explorer**: Navigate your Obsidian folders and read Markdown notes directly inside the app.
- **Deep Linking**: Open any note in the Obsidian app for editing with a single tap.
- **Quick Capture**: Create new notes in your vault with an automated timestamped template.

## 🎨 Modern Design
- **Craft-Inspired UI**: Minimalist aesthetic with hero headers and pastel accent coding.
- **Sidebar Navigation**: Efficient vertical `NavigationRail` for quick switching between Feed, Brain, Notes, and Time.
- **Responsive Feedback**: Real-time loading indicators for AI generation and data syncing.

---

## 🛠️ Project Structure
- `ui/memories/`: The main notification feed and capture controls.
- `ui/reflection/`: AI history, model selection, and briefing/reflection cards.
- `ui/usage/`: Digital Time dashboard, visual charts, and multi-device totals.
- `ui/notes/`: Obsidian vault explorer and markdown preview.
- `core/usage/`: Conflict-free sync engine and `UsageStatsManager` integration.
- `core/reflection/`: Gemini API integration and "Morning/Evening" logic.
- `core/capture/`: Notification listener service and clipboard manager.

## 🚀 Build and Run
1. Open the project in Android Studio and sync Gradle.
2. Provide `gemini.api.key` (optional) in `local.properties` or the in-app Setup screen.
3. Every build automatically increments the version (e.g., `1.0.4`), visible in the Sidebar.
4. **Permissions**:
   - Enable **Notification Access** to capture events.
   - Enable **Usage Access** for Digital Time tracking.
   - Select your **Obsidian Vault** folder in the Notes tab to enable syncing.

---
*Your private data stays local or in your own vault. No cloud servers required.*
