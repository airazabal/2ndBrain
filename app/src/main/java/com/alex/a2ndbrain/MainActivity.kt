package com.alex.a2ndbrain

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.capture.ClipboardCaptureManager
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.ui.memories.MemoryScreen
import com.alex.a2ndbrain.ui.reflection.ReflectionScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var clipboardCaptureManager: ClipboardCaptureManager
    private lateinit var settingsManager: CaptureSettingsManager
    private lateinit var reflectionManager: ReflectionManager

    override fun onResume() {
        super.onResume()
        clipboardCaptureManager.captureCurrentClipboard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = CaptureSettingsManager(this)
        clipboardCaptureManager = ClipboardCaptureManager(this)
        reflectionManager = ReflectionManager(this)

        reflectionManager.schedulePeriodicReflection()

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var currentTab by remember { mutableIntStateOf(0) }
                    val database = AppDatabase.getDatabase(this)
                    val memories by database.memoryDao().getAllMemories().collectAsState(initial = emptyList())
                    val summaries by database.memoryDao().getAllSummaries().collectAsState(initial = emptyList())

                    var searchQuery by remember { mutableStateOf("") }
                    val filteredMemories = if (searchQuery.isEmpty()) {
                        memories
                    } else {
                        memories.filter {
                            it.content.contains(searchQuery, ignoreCase = true) ||
                                (it.title ?: "").contains(searchQuery, ignoreCase = true)
                        }
                    }

                    Scaffold(bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Search, contentDescription = "Memories") },
                                label = { Text("Memories") },
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Notifications, contentDescription = "Reflection") },
                                label = { Text("Reflection") },
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 }
                            )
                        }
                    }) { innerPadding ->
                        when (currentTab) {
                            0 -> MemoryScreen(
                                memories = filteredMemories,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                onOpenSettings = {
                                    startActivity(Intent(this@MainActivity, AppCaptureSettingsActivity::class.java))
                                },
                                onCaptureClipboard = {
                                    clipboardCaptureManager.captureCurrentClipboard()
                                },
                                onMarkAsRead = { id ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        database.memoryDao().markAsRead(id)
                                    }
                                },
                                onClearAll = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        database.clearAllTables()
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
                            )

                            1 -> ReflectionScreen(
                                summaries = summaries,
                                settingsManager = settingsManager,
                                onGenerateReflection = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        reflectionManager.generateDailyReflection()
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}