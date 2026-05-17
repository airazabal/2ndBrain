package com.alex.a2ndbrain

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.capture.ClipboardCaptureManager
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import com.alex.a2ndbrain.ui.home.HomeScreen
import com.alex.a2ndbrain.ui.memories.MemoryScreen
import com.alex.a2ndbrain.ui.notes.NotesScreen
import com.alex.a2ndbrain.ui.reflection.ReflectionScreen
import com.alex.a2ndbrain.ui.theme.BrainTheme
import com.alex.a2ndbrain.ui.usage.DigitalTimeScreen
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    private val clipboardCaptureManager: ClipboardCaptureManager by lazy { ClipboardCaptureManager(this) }
    private val settingsManager: CaptureSettingsManager by inject()
    private val reflectionPicker: ReflectionManager by inject()
    private val digitalTimeManager: DigitalTimeManager by inject()

    override fun onResume() {
        super.onResume()
        clipboardCaptureManager.captureCurrentClipboard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        reflectionPicker.schedulePeriodicReflection()
        digitalTimeManager.schedulePeriodicSync()

        enableEdgeToEdge()
        setContent {
            BrainTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val viewModel: MainViewModel = koinViewModel()
                    
                    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
                    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                    
                    val pagedMemories = viewModel.pagedMemories.collectAsLazyPagingItems()
                    val allMemoriesForHome by viewModel.allMemoriesForHome.collectAsStateWithLifecycle()
                    
                    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
                    val usageStats by viewModel.usageStats.collectAsStateWithLifecycle()
                    val vaultNotes by viewModel.vaultNotes.collectAsStateWithLifecycle()
                    val error by viewModel.errorFlow.collectAsStateWithLifecycle()

                    LaunchedEffect(error) {
                        error?.let {
                            Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
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
                                val tabs = listOf(
                                    Triple("Home", Icons.Default.Home, 0),
                                    Triple("Feed", Icons.Default.Notifications, 1),
                                    Triple("Brain", Icons.Default.AutoAwesome, 2),
                                    Triple("Notes", Icons.Default.Description, 3),
                                    Triple("Time", Icons.Default.Schedule, 4)
                                )
                                
                                tabs.forEach { (label, icon, index) ->
                                    NavigationRailItem(
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = { Text(label) },
                                        selected = currentTab == index,
                                        onClick = { viewModel.setTab(index) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        )
                                    )
                                }
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
                                            memories = allMemoriesForHome,
                                            latestReflection = summaries.firstOrNull(),
                                            notes = vaultNotes,
                                            usageStats = usageStats,
                                            onNavigateToTab = { viewModel.setTab(it) }
                                        )

                                        1 -> MemoryScreen(
                                            pagedMemories = pagedMemories,
                                            searchQuery = searchQuery,
                                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                            onOpenSettings = {
                                                startActivity(Intent(this@MainActivity, AppCaptureSettingsActivity::class.java))
                                            },
                                            onCaptureClipboard = {
                                                clipboardCaptureManager.captureCurrentClipboard()
                                            },
                                            onMarkAsRead = { id -> viewModel.markAsRead(id) },
                                            onClearAll = { viewModel.clearAllMemories() },
                                            monitoredApps = settingsManager.getMonitoredApps()
                                        )

                                        2 -> ReflectionScreen(
                                            summaries = summaries,
                                            settingsManager = settingsManager,
                                            onGenerateReflection = { viewModel.generateReflection() },
                                            onClearAll = { viewModel.clearAllSummaries() },
                                            onDeleteSummary = { id -> viewModel.deleteSummary(id) }
                                        )

                                        3 -> NotesScreen(settingsManager = settingsManager)
                                        4 -> DigitalTimeScreen(digitalTimeManager = digitalTimeManager)
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
