package com.alex.a2ndbrain.core.goals

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalRepositoryImpl(private val dao: GoalDao) : GoalRepository {

    override fun getActiveGoalsFlow(): Flow<List<Goal>> =
        dao.getActiveGoalsFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveGoals(): List<Goal> =
        dao.getActiveGoals().map { it.toDomain() }

    override suspend fun getById(id: String): Goal? =
        dao.getById(id)?.toDomain()

    override suspend fun upsert(goal: Goal) = dao.upsert(goal.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}
