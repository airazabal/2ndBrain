package com.alex.a2ndbrain.core.goals

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM goals WHERE isActive = 1 ORDER BY createdAt ASC")
    fun getActiveGoalsFlow(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE isActive = 1 ORDER BY createdAt ASC")
    suspend fun getActiveGoals(): List<GoalEntity>
}
