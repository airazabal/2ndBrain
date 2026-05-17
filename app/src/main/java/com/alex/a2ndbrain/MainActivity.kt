package com.alex.a2ndbrain

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.capture.ClipboardCaptureManager
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import com.alex.a2ndbrain.ui.home.HomeScreen
import com.alex.a2ndbrain.ui.memories.MemoryScreen
import com.alex.a2ndbrain.ui.notes.NotesScreen
import com.alex.a2ndbrain.ui.reflection.ReflectionScreen
import com.alex.a2ndbrain.ui.theme.BrainTheme
import com.alex.a2ndbrain.ui.usage.DigitalTimeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var clipboardCaptureManager: ClipboardCaptureManager
    private lateinit var settingsManager: CaptureSettingsManager
    private lateinit var reflectionPicker: ReflectionManager
    private lateinit var digitalTimeManager: DigitalTimeManager

    override fun onResume() {
        super.onResume()
        clipboardCaptureManager.captureCurrentClipboard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = CaptureSettingsManager(this)
        clipboardCaptureManager = ClipboardCaptureManager(this)
        reflectionPicker = ReflectionManager(this)
        digitalTimeManager = DigitalTimeManager(this)

        reflectionPicker.schedulePeriodicReflection()
        digitalTimeManager.schedulePeriodicSync()

        enableEdgeToEdge()
        setContent {
            BrainTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var currentTab by remember { mutableIntStateOf(0) }
                    val database = AppDatabase.getDatabase(this)
                    val memories by database.memoryDao().getAllMemories().collectAsState(initial = emptyList())
                    val summaries by database.memoryDao().getAllSummaries().collectAsState(initial = emptyList())
                    
                    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
                    val usageStats by database.memoryDao().getUsageStatsForDate(today).collectAsState(initial = emptyList())
                    
                    var vaultNotes by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
                    val vaultUri = settingsManager.getObsidianVaultUri()
                    
                    LaunchedEffect(vaultUri) {
                        if (vaultUri.isNotBlank()) {
                            val root = DocumentFile.fromTreeUri(this@MainActivity, android.net.Uri.parse(vaultUri))
                            vaultNotes = root?.listFiles()
                                ?.filter { it.isFile && it.name?.endsWith(".md") == true }
                                ?.sortedByDescending { it.lastModified() } ?: emptyList()
                        }
                    }

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
                                containerColor = MaterialTheme.colorScheme.surface,
                                header = {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "v${BuildConfig.VERSION_NAME}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            ) {
                                NavigationRailItem(
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Home") },
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0 },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                )
                                NavigationRailItem(
                                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Notifications") },
                                    label = { Text("Feed") },
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                )
                                NavigationRailItem(
                                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Reflection") },
                                    label = { Text("Brain") },
                                    selected = currentTab == 2,
                                    onClick = { currentTab = 2 },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                )
                                NavigationRailItem(
                                    icon = { Icon(Icons.Default.Description, contentDescription = "Notes") },
                                    label = { Text("Notes") },
                                    selected = currentTab == 3,
                                    onClick = { currentTab = 3 },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                )
                                NavigationRailItem(
                                    icon = { Icon(Icons.Default.Schedule, contentDescription = "Digital Time") },
                                    label = { Text("Time") },
                                    selected = currentTab == 4,
                                    onClick = { currentTab = 4 },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                // Hero Header
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp, vertical = 24.dp)
                                ) {
                                    Text(
                                        text = when(currentTab) {
                                            0 -> "Welcome to your 2ndBrain"
                                            1 -> "Your daily stream of captures"
                                            2 -> "Reflections & daily insights"
                                            3 -> "Your space for ideas & thoughts"
                                            else -> "Understanding your routine"
                                        },
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Black,
                                        lineHeight = 44.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    when (currentTab) {
                                        0 -> HomeScreen(
                                            memories = memories,
                                            latestReflection = summaries.firstOrNull(),
                                            notes = vaultNotes,
                                            usageStats = usageStats,
                                            onNavigateToTab = { currentTab = it }
                                        )

                                        1 -> MemoryScreen(
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

                                        2 -> ReflectionScreen(
                                            summaries = summaries,
                                            settingsManager = settingsManager,
                                            onGenerateReflection = {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    val error = reflectionPicker.generateDailyReflection()
                                                    if (error != null) {
                                                        // Show error toast on main thread
                                                        withContext(Dispatchers.Main) {
                                                            android.widget.Toast.makeText(this@MainActivity, error, android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            },
                                            onClearAll = {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    database.memoryDao().deleteAllSummaries()
                                                }
                                            },
                                            onDeleteSummary = { id ->
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    database.memoryDao().deleteSummary(id)
                                                }
                                            }
                                        )

                                        3 -> NotesScreen(settingsManager = settingsManager)
                                        4 -> DigitalTimeScreen()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
