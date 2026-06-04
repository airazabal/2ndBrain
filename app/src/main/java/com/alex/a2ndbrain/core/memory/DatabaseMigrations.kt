package com.alex.a2ndbrain.core.memory

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    // -----------------------------------------------------------------------
    // Template for next migration — copy this block when bumping version to 15
    // -----------------------------------------------------------------------
    // val MIGRATION_14_15 = object : Migration(14, 15) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         // Example: add a column
    //         // db.execSQL("ALTER TABLE memories ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    //
    //         // Example: create a new table
    //         // db.execSQL("""
    //         //     CREATE TABLE IF NOT EXISTS new_table (
    //         //         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    //         //         ...
    //         //     )
    //         // """)
    //     }
    // }
    // -----------------------------------------------------------------------

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS health_snapshots (
                    date TEXT NOT NULL PRIMARY KEY,
                    deviceId TEXT NOT NULL,
                    steps INTEGER NOT NULL DEFAULT 0,
                    sleepMinutes INTEGER NOT NULL DEFAULT 0,
                    minHeartRate INTEGER NOT NULL DEFAULT 0,
                    maxHeartRate INTEGER NOT NULL DEFAULT 0,
                    avgHeartRate INTEGER NOT NULL DEFAULT 0,
                    lastTimestamp INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habits ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE habits ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            // Back-fill lastModifiedAt from createdAt for existing rows
            db.execSQL("UPDATE habits SET lastModifiedAt = createdAt")
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habit_completions ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE habit_completions ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE habit_completions SET lastModifiedAt = completedAt")
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habits ADD COLUMN repeatUntil INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS habits")
            db.execSQL("DROP TABLE IF EXISTS habit_completions")
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Recreate health_snapshots with composite primary key (date, deviceId)
            // so each device's snapshot coexists rather than overwriting the other.
            db.execSQL("DROP TABLE IF EXISTS health_snapshots")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS health_snapshots (
                    date TEXT NOT NULL,
                    deviceId TEXT NOT NULL,
                    steps INTEGER NOT NULL DEFAULT 0,
                    sleepMinutes INTEGER NOT NULL DEFAULT 0,
                    minHeartRate INTEGER NOT NULL DEFAULT 0,
                    maxHeartRate INTEGER NOT NULL DEFAULT 0,
                    avgHeartRate INTEGER NOT NULL DEFAULT 0,
                    lastTimestamp INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(date, deviceId)
                )
            """.trimIndent())
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS exercise_sessions (
                    id TEXT NOT NULL PRIMARY KEY,
                    deviceId TEXT NOT NULL,
                    type TEXT NOT NULL,
                    durationMinutes INTEGER NOT NULL,
                    startedAt INTEGER NOT NULL,
                    notes TEXT NOT NULL,
                    date TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    lastModifiedAt INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_sessions_date ON exercise_sessions(date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_sessions_lastModifiedAt ON exercise_sessions(lastModifiedAt)")
        }
    }

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `todoist_completions` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `taskContent` TEXT NOT NULL, `completedAt` INTEGER NOT NULL, `date` TEXT NOT NULL, PRIMARY KEY(`id`))
            """.trimIndent())
        }
    }

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `sense_of_day_snapshots` (`date` TEXT NOT NULL, `score` INTEGER NOT NULL, `stepsProgress` REAL NOT NULL, `sleepProgress` REAL NOT NULL, `exerciseProgress` REAL NOT NULL, `focusProgress` REAL NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))
            """.trimIndent())
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23
    )
}
