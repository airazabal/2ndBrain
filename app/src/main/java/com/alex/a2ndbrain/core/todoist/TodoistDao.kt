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

    @Query("SELECT COUNT(*) FROM todoist_completions WHERE date = :date AND status = 'COMPLETED'")
    suspend fun getCountForDate(date: String): Int

    @Query("SELECT COUNT(*) FROM todoist_completions WHERE date >= :sinceDate AND status = 'COMPLETED'")
    suspend fun getCountSince(sinceDate: String): Int

    @Query("SELECT COUNT(*) FROM todoist_completions WHERE status = 'COMPLETED'")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM todoist_completions WHERE date = :date AND status = 'MISSED'")
    suspend fun getMissedCountForDate(date: String): Int

    @Query("SELECT COUNT(*) FROM todoist_completions WHERE date >= :sinceDate AND status = 'MISSED'")
    suspend fun getMissedCountSince(sinceDate: String): Int

    @Query("SELECT * FROM todoist_completions ORDER BY completedAt DESC")
    suspend fun getAllCompletions(): List<TodoistCompletionEntity>
}
