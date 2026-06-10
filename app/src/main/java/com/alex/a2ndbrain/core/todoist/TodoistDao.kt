package com.alex.a2ndbrain.core.todoist

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: TodoistCompletionEntity)

    @Query("SELECT * FROM todoist_completions ORDER BY completedAt DESC")
    fun getAllCompletionsFlow(): Flow<List<TodoistCompletionEntity>>

    @Query("SELECT * FROM todoist_completions WHERE date >= :sinceDate ORDER BY completedAt DESC")
    fun getCompletionsSince(sinceDate: String): Flow<List<TodoistCompletionEntity>>

    @Query("SELECT COUNT(*) FROM todoist_completions WHERE date = :date")
    suspend fun getCountForDate(date: String): Int

    @Query("SELECT COUNT(*) FROM todoist_completions WHERE date >= :sinceDate")
    suspend fun getCountSince(sinceDate: String): Int

    @Query("SELECT COUNT(*) FROM todoist_completions")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM todoist_completions ORDER BY completedAt DESC")
    suspend fun getAllCompletions(): List<TodoistCompletionEntity>
}
