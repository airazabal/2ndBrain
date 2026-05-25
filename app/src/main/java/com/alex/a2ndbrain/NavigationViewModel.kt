package com.alex.a2ndbrain

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class NavigationViewModel : ViewModel() {
    private val _currentTab = MutableStateFlow(AppTab.TODAY)
    val currentTab = _currentTab.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()

    private val _presetCopilotQuery = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val presetCopilotQuery = _presetCopilotQuery.asSharedFlow()

    private val _feedFilter = MutableStateFlow("All")
    val feedFilter = _feedFilter.asStateFlow()

    fun setTab(tab: AppTab) {
        if (tab != AppTab.FEED) _feedFilter.value = "All"
        _currentTab.value = tab
    }

    fun navigateToFeed(filter: String = "All") {
        _feedFilter.value = filter
        _currentTab.value = AppTab.FEED
    }

    fun triggerCopilotQuery(query: String) {
        _presetCopilotQuery.tryEmit(query)
        _currentTab.value = AppTab.COPILOT // Switch to Co-Pilot tab
    }

    fun setError(message: String) {
        _errorFlow.value = message
    }

    fun clearError() {
        _errorFlow.value = null
    }
}
