package com.alex.a2ndbrain.core.di

import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.reflection.ModelDownloader
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.MainViewModel
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    // Database and Dao
    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().memoryDao() }

    // Repositories
    single { MemoryRepository(get()) }
    single { UsageRepository(get()) }

    // Managers
    single { CaptureSettingsManager(androidContext()) }
    single { DigitalTimeManager(androidContext(), get(), get()) }
    single { ReflectionManager(androidContext()) }
    single { ModelDownloader(androidContext(), get()) }
    single { HealthConnectManager(androidContext()) }

    // ViewModels
    factory { MainViewModel(get(), get(), get(), get(), get(), androidContext()) }

    // Application-wide CoroutineScope for critical background tasks
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
