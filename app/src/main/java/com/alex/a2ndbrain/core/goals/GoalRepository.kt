package com.alex.a2ndbrain.core.goals

import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun getActiveGoalsFlow(): Flow<List<Goal>>
    suspend fun getActiveGoals(): List<Goal>
    suspend fun getById(id: String): Goal?
    suspend fun upsert(goal: Goal)
    suspend fun delete(id: String)
}
