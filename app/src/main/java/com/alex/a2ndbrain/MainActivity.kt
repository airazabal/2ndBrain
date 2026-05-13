package com.alex.a2ndbrain

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.alex.a2ndbrain.ui.notes.NotesScreen
import com.alex.a2ndbrain.ui.reflection.ReflectionScreen
import com.alex.a2ndbrain.ui.usage.DigitalTimeScreen
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

                    Scaffold { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            NavigationRail(
                                modifier = Modifier.fillMaxHeight(),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                header = {
                                    // Optional: Add a small logo or profile icon here
                                }
                            ) {
                                NavigationRailItem(
                                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Notifications") },
                                    label = { Text("Notifications") },
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0 }
                                )
                                NavigationRailItem(
                                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Reflection") },
                                    label = { Text("Reflection") },
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 }
                                )
                                NavigationRailItem(
                                    icon = { Icon(Icons.Default.Description, contentDescription = "Notes") },
                                    label = { Text("Notes") },
                                    selected = currentTab == 2,
                                    onClick = { currentTab = 2 }
                                )
                                NavigationRailItem(
                                    icon = { Icon(Icons.Default.Schedule, contentDescription = "Digital Time") },
                                    label = { Text("Digital Time") },
                                    selected = currentTab == 3,
                                    onClick = { currentTab = 3 }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
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
                                        monitoredApps = settingsManager.getMonitoredApps()
                                    )

                                    1 -> ReflectionScreen(
                                        summaries = summaries,
                                        settingsManager = settingsManager,
                                        onGenerateReflection = {
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                reflectionManager.generateDailyReflection()
                                            }
                                        }
                                    )

                                    2 -> NotesScreen(settingsManager = settingsManager)
                                    3 -> DigitalTimeScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
