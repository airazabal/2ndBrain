package com.alex.a2ndbrain

import android.app.Application
import com.alex.a2ndbrain.core.di.appModule
import com.alex.a2ndbrain.core.reflection.CircadianInsightManager
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.sync.NearbySyncManager
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SecondBrainApplication : Application() {

    // Process-scoped coroutine scope — lives as long as the process does.
    // SupervisorJob ensures one failing schedule call doesn't cancel the rest.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SecondBrainApplication)
            modules(appModule)
        }
        scheduleBackgroundWork()
    }

    // Moved out of MainActivity.onCreate() so:
    // 1. These calls run once per process, not on every Activity recreation.
    // 2. They execute on Dispatchers.IO, keeping the main thread free to
    //    render setContent {} immediately after Activity starts.
    private fun scheduleBackgroundWork() {
        appScope.launch {
            get<ReflectionManager>().scheduleWorkers()
            get<CircadianInsightManager>().scheduleWeeklyAnalysis()
            get<DigitalTimeManager>().schedulePeriodicSync()
            get<NearbySyncManager>().schedulePeriodicP2pSync()
            com.alex.a2ndbrain.core.sync.CloudSyncWorker.schedule(this@SecondBrainApplication)
            com.alex.a2ndbrain.core.usage.DistractionAlertWorker.schedule(this@SecondBrainApplication)
            com.alex.a2ndbrain.core.todoist.TodoistReminderWorker.schedule(this@SecondBrainApplication)
            com.alex.a2ndbrain.core.todoist.TodoistReminderWorker.runNow(this@SecondBrainApplication)
            com.alex.a2ndbrain.core.mood.MoodReminderWorker.schedule(this@SecondBrainApplication)
            com.alex.a2ndbrain.ui.widget.WidgetUpdateWorker.schedule(this@SecondBrainApplication)
            com.alex.a2ndbrain.ui.widget.WidgetUpdateWorker.runNow(this@SecondBrainApplication)
        }
    }
}
