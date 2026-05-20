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

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        // Add MIGRATION_14_15 here once schema changes are made
    )
}
