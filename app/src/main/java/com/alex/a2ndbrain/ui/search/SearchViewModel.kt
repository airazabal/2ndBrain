package com.alex.a2ndbrain.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
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
    val isLoading: Boolean = false
) {
    val isEmpty get() = memories.isEmpty() && summaries.isEmpty()
    val total get() = memories.size + summaries.size
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val memoryRepository: MemoryRepository
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
                val memories = memoryRepository.searchMemoriesSync(q).take(20)
                val summaries = memoryRepository.searchSummaries(q)
                emit(SearchResults(memories, summaries))
            }.flowOn(Dispatchers.IO)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, SearchResults())

    fun setQuery(q: String) { _query.value = q }
}
