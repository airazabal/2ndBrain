package com.alex.a2ndbrain.core.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.alex.a2ndbrain.core.exercise.ExerciseDao
import com.alex.a2ndbrain.core.exercise.ExerciseSessionEntity
import com.alex.a2ndbrain.core.health.HealthDao
import com.alex.a2ndbrain.core.health.HealthSnapshotEntity
import com.alex.a2ndbrain.core.goals.GoalDao
import com.alex.a2ndbrain.core.goals.GoalEntity
import com.alex.a2ndbrain.core.habits.HabitCompletionEntity
import com.alex.a2ndbrain.core.habits.HabitEntity
import com.alex.a2ndbrain.core.habits.HabitsDao
import com.alex.a2ndbrain.core.mood.MoodDao
import com.alex.a2ndbrain.core.mood.MoodLogEntity
import com.alex.a2ndbrain.core.senseofday.SenseOfDaySnapshotDao
import com.alex.a2ndbrain.core.senseofday.SenseOfDaySnapshotEntity
import com.alex.a2ndbrain.core.todoist.TodoistCompletionEntity
import com.alex.a2ndbrain.core.todoist.TodoistDao
import com.alex.a2ndbrain.data.db.ConsolidatedMemoryDao
import com.alex.a2ndbrain.data.db.ConsolidatedMemoryEntity
import com.alex.a2ndbrain.data.db.EpisodicEventDao
import com.alex.a2ndbrain.data.db.EpisodicEventEntity

@Database(entities = [MemoryEntity::class, DailySummaryEntity::class, UsageStatEntity::class, HealthSnapshotEntity::class, ExerciseSessionEntity::class, TodoistCompletionEntity::class, SenseOfDaySnapshotEntity::class, MoodLogEntity::class, HabitEntity::class, HabitCompletionEntity::class, GoalEntity::class, ConsolidatedMemoryEntity::class, EpisodicEventEntity::class], version = 30, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun healthDao(): HealthDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun todoistDao(): TodoistDao
    abstract fun senseOfDaySnapshotDao(): SenseOfDaySnapshotDao
    abstract fun moodDao(): MoodDao
    abstract fun habitsDao(): HabitsDao
    abstract fun goalDao(): GoalDao
    abstract fun consolidatedMemoryDao(): ConsolidatedMemoryDao
    abstract fun episodicEventDao(): EpisodicEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "second_brain_db"
                )
                // Destructive migration allowed only from pre-v14 versions (data was
                // never persisted safely across those builds). v14 onward requires a
                // proper Migration object in DatabaseMigrations.ALL_MIGRATIONS.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}