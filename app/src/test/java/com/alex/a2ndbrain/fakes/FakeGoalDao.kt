package com.alex.a2ndbrain.fakes

import com.alex.a2ndbrain.core.goals.GoalDao
import com.alex.a2ndbrain.core.goals.GoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGoalDao : GoalDao {

    private val _goals = MutableStateFlow<List<GoalEntity>>(emptyList())
    val upserted: MutableList<GoalEntity> = mutableListOf()
    val deleted: MutableList<String> = mutableListOf()

    override suspend fun upsert(goal: GoalEntity) {
        upserted += goal
        val existing = _goals.value.indexOfFirst { it.id == goal.id }
        _goals.value = if (existing >= 0) {
            _goals.value.toMutableList().also { it[existing] = goal }
        } else {
            _goals.value + goal
        }
    }

    override suspend fun delete(id: String) {
        deleted += id
        _goals.value = _goals.value.filter { it.id != id }
    }

    override fun getActiveGoalsFlow(): Flow<List<GoalEntity>> = _goals

    override suspend fun getActiveGoals(): List<GoalEntity> = _goals.value

    override suspend fun getById(id: String): GoalEntity? = _goals.value.find { it.id == id }

    fun seed(vararg goals: GoalEntity) {
        _goals.value = goals.toList()
    }
}
