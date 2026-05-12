package com.alex.a2ndbrain.core.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CaptureDebugStore {
    private val _lastEvent = MutableStateFlow<String>("Waiting for events...")
    val lastEvent = _lastEvent.asStateFlow()

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events = _events.asStateFlow()

    fun logEvent(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val formattedMessage = "[$timestamp] $message"
        _lastEvent.value = formattedMessage
        _events.value = (listOf(formattedMessage) + _events.value).take(50)
    }
}
