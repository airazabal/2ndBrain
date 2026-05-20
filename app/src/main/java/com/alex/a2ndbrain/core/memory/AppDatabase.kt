package com.alex.a2ndbrain.core.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MemoryEntity::class, DailySummaryEntity::class, UsageStatEntity::class, HabitEntity::class, HabitCompletionEntity::class], version = 14, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun habitsDao(): HabitsDao

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