package com.alex.a2ndbrain.core.di

import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.reflection.ModelDownloader
import com.alex.a2ndbrain.core.reflection.ModelPicker
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.reflection.GeminiAgent
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.health.HealthDao
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.health.HealthRepositoryImpl
import com.alex.a2ndbrain.core.sync.CloudHealthSyncManager
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.memory.MemoryRepositoryImpl
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.alex.a2ndbrain.core.usage.UsageRepositoryImpl
import com.alex.a2ndbrain.core.meditation.MeditationRepository
import com.alex.a2ndbrain.core.meditation.ZendenceMeditationRepository
import com.alex.a2ndbrain.core.agents.HealthAgent
import com.alex.a2ndbrain.core.agents.MemoryAgent
import com.alex.a2ndbrain.core.agents.ModelRouter
import com.alex.a2ndbrain.core.agents.OrchestratorAgent
import com.alex.a2ndbrain.core.agents.ReflectionAgent
import com.alex.a2ndbrain.core.agents.SessionMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

import com.alex.a2ndbrain.core.capture.ClipboardCaptureManager
import com.alex.a2ndbrain.core.exercise.ExerciseDao
import com.alex.a2ndbrain.core.exercise.ExerciseRepository
import com.alex.a2ndbrain.core.exercise.ExerciseRepositoryImpl
import com.alex.a2ndbrain.core.senseofday.SenseOfDayHistoryRepository
import com.alex.a2ndbrain.core.senseofday.SenseOfDayHistoryRepositoryImpl
import com.alex.a2ndbrain.core.senseofday.SenseOfDaySnapshotDao
import com.alex.a2ndbrain.core.todoist.TodoistDao
import com.alex.a2ndbrain.core.todoist.TodoistRepository
import com.alex.a2ndbrain.core.todoist.TodoistRepositoryImpl
import com.alex.a2ndbrain.core.todoist.TodoistStatsRepository
import com.alex.a2ndbrain.core.todoist.TodoistStatsRepositoryImpl
import com.alex.a2ndbrain.core.domain.BuildBrainContextUseCase
import com.alex.a2ndbrain.core.domain.ChatWithCopilotUseCase
import com.alex.a2ndbrain.core.domain.GenerateWeeklyInsightUseCase
import com.alex.a2ndbrain.core.domain.GetWeeklyHealthTrendsUseCase
import com.alex.a2ndbrain.core.domain.RetrieveMemoriesUseCase
import com.alex.a2ndbrain.ui.exercise.ExerciseViewModel
import com.alex.a2ndbrain.ui.todoist.TodoistViewModel
import com.alex.a2ndbrain.ui.trends.SenseOfDayTrendsViewModel

val appModule = module {
    // Database and Dao
    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().memoryDao() }
    single<HealthDao> { get<AppDatabase>().healthDao() }
    single<ExerciseDao> { get<AppDatabase>().exerciseDao() }
    single<TodoistDao> { get<AppDatabase>().todoistDao() }
    single<SenseOfDaySnapshotDao> { get<AppDatabase>().senseOfDaySnapshotDao() }

    // Repositories
    single<MemoryRepository> { MemoryRepositoryImpl(get()) }
    single<ExerciseRepository> { ExerciseRepositoryImpl(get(), androidContext()) }
    single<TodoistStatsRepository> { TodoistStatsRepositoryImpl(get()) }
    single<SenseOfDayHistoryRepository> { SenseOfDayHistoryRepositoryImpl(get()) }
    single<UsageRepository> { UsageRepositoryImpl(get()) }

    // Managers
    single { CaptureSettingsManager(androidContext()) }
    single { ClipboardCaptureManager(androidContext(), get()) }
    single { DigitalTimeManager(androidContext(), get(), get()) }
    single { GeminiAgent(get()) }
    single { ReflectionManager(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
    single { ModelDownloader(androidContext(), get()) }
    single { HealthConnectManager(androidContext()) }
    single<HealthRepository> { HealthRepositoryImpl(get(), get()) }
    // Wire cloud sync back into the repository after both are created (breaks circular dep)
    single {
        CloudHealthSyncManager(androidContext(), get<HealthRepository>()).also {
            get<HealthRepository>().setCloudSync(it)
        }
    }
    single<MeditationRepository> { ZendenceMeditationRepository(androidContext()) }
    single<TodoistRepository> { TodoistRepositoryImpl(get()) }
    single { com.alex.a2ndbrain.core.sync.NearbySyncManager(androidContext(), get(), get(), get(), get(), get(), get()) }

    // Agent layer
    single { MemoryAgent(get()) }
    single { HealthAgent(get(), get()) }
    single { ReflectionAgent() }
    single { ModelPicker(androidContext()) }
    single { ModelRouter(get(), get(), get()) }
    single { OrchestratorAgent(get(), get(), get(), get(), get(), get()) }
    // factory = new SessionMemory per Copilot session (not a global singleton)
    factory { SessionMemory() }

    // Use cases
    single { ChatWithCopilotUseCase(get()) }
    single { BuildBrainContextUseCase(get()) }
    single { RetrieveMemoriesUseCase(get()) }
    single { GetWeeklyHealthTrendsUseCase(get()) }
    single { GenerateWeeklyInsightUseCase(get()) }

    // ViewModels
    viewModel { com.alex.a2ndbrain.NavigationViewModel() }
    viewModel { ExerciseViewModel(get()) }
    viewModel { TodoistViewModel(get()) }
    viewModel { SenseOfDayTrendsViewModel(get()) }
    viewModel { com.alex.a2ndbrain.ui.home.HomeViewModel(get(), get(), get(), get(), androidContext(), get()) }
    viewModel { com.alex.a2ndbrain.ui.home.HomeTasksViewModel(get(), get(), androidContext()) }
    viewModel { com.alex.a2ndbrain.ui.home.GrandCentralViewModel(get(), get(), get(), androidContext()) }
    viewModel { com.alex.a2ndbrain.ui.home.WellnessViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { com.alex.a2ndbrain.ui.health.HealthViewModel(get(), get()) }
    viewModel { com.alex.a2ndbrain.ui.memories.MemoryViewModel(get(), get(), androidContext()) }
    viewModel { com.alex.a2ndbrain.ui.reflection.ReflectionViewModel(get(), get(), get(), get(), get(), androidContext()) }
    viewModel { com.alex.a2ndbrain.ui.chat.CopilotViewModel(get(), get()) }
    viewModel { com.alex.a2ndbrain.ui.settings.SettingsViewModel(get(), get(), get(), get(), get(), androidContext()) }
    viewModel { com.alex.a2ndbrain.ui.usage.DigitalTimeViewModel(get(), androidContext()) }
    viewModel { com.alex.a2ndbrain.ui.search.SearchViewModel(get()) }

    // Application-wide CoroutineScope for critical background tasks
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
