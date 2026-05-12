package com.alex.a2ndbrain.core.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CaptureDebugStore {
    private val _lastEvent = MutableStateFlow<String>("Waiting for events...")
    val lastEvent = _lastEvent.asStateFlow()

    fun logEvent(message: String) {
        _lastEvent.value = "[${System.currentTimeMillis() % 100000}] $message"
    }
}
