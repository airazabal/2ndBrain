package com.alex.a2ndbrain.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.HabitEntity
import com.alex.a2ndbrain.core.memory.HabitsDao
import com.alex.a2ndbrain.core.memory.MemoryDao
import com.alex.a2ndbrain.core.memory.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class SearchResults(
    val memories: List<MemoryEntity> = emptyList(),
    val summaries: List<DailySummaryEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val isLoading: Boolean = false
) {
    val isEmpty get() = memories.isEmpty() && summaries.isEmpty() && habits.isEmpty()
    val total get() = memories.size + summaries.size + habits.size
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val memoryDao: MemoryDao,
    private val habitsDao: HabitsDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    val results = _query
        .debounce(300)
        .flatMapLatest { q ->
            flow {
                if (q.length < 2) {
                    emit(SearchResults())
                    return@flow
                }
                emit(SearchResults(isLoading = true))
                val memories = memoryDao.searchMemoriesSync(q).take(20)
                val summaries = memoryDao.searchSummaries(q)
                val habits = habitsDao.getAllHabitsSync()
                    .filter { it.name.contains(q, ignoreCase = true) }
                emit(SearchResults(memories, summaries, habits))
            }.flowOn(Dispatchers.IO)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, SearchResults())

    fun setQuery(q: String) { _query.value = q }
}
