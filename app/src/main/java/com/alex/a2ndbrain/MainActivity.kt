package com.alex.a2ndbrain

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.capture.ClipboardCaptureManager
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import com.alex.a2ndbrain.ui.chat.CopilotViewModel
import com.alex.a2ndbrain.ui.home.HomeScreen
import com.alex.a2ndbrain.ui.home.HomeViewModel
import com.alex.a2ndbrain.ui.search.SearchScreen
import com.alex.a2ndbrain.ui.memories.MemoryScreen
import com.alex.a2ndbrain.ui.memories.MemoryViewModel
import com.alex.a2ndbrain.ui.notes.NotesScreen
import com.alex.a2ndbrain.ui.settings.SettingsViewModel
import com.alex.a2ndbrain.ui.theme.BrainTheme
import com.alex.a2ndbrain.ui.wellness.WellnessScreen
import com.alex.a2ndbrain.ui.wizard.PermissionWizardScreen
import com.alex.a2ndbrain.ui.wizard.WizardPermission
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    private val clipboardCaptureManager: ClipboardCaptureManager by inject()
    private val settingsManager: CaptureSettingsManager by inject()
    private val reflectionPicker: ReflectionManager by inject()
    private val digitalTimeManager: DigitalTimeManager by inject()
    private val nearbySyncManager: com.alex.a2ndbrain.core.sync.NearbySyncManager by inject()
    // Eagerly instantiated so it wires cloudSync into HealthRepository before first health read
    private val cloudHealthSyncManager: com.alex.a2ndbrain.core.sync.CloudHealthSyncManager by inject()

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.data)
    }

    override fun onStart() {
        super.onStart()
        if (hasSyncPermissions()) {
            nearbySyncManager.startSync()
        }
    }

    override fun onStop() {
        super.onStop()
        nearbySyncManager.stopSync()
    }

    private fun hasSyncPermissions(): Boolean {
        val permissions = mutableListOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return permissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun triggerGoogleSignInIfNeeded() {
        if (FirebaseApp.getApps(this).isEmpty()) return
        if (FirebaseAuth.getInstance().currentUser != null) return
        val webClientId = BuildConfig.FIREBASE_WEB_CLIENT_ID.takeIf { it.isNotEmpty() } ?: return
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleSignInLauncher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        lifecycleScope.launch {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
                val idToken = account.idToken ?: return@launch
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential).await()
                android.util.Log.d("MainActivity", "Firebase sign-in successful, scheduling immediate sync")
                com.alex.a2ndbrain.core.sync.CloudSyncWorker.runNow(this@MainActivity)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Firebase sign-in failed: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clipboardCaptureManager.captureCurrentClipboard()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.alex.a2ndbrain.core.sync.FirebaseInitializer.init(this)
        triggerGoogleSignInIfNeeded()
        reflectionPicker.schedulePeriodicReflection()
        digitalTimeManager.schedulePeriodicSync()
        nearbySyncManager.schedulePeriodicP2pSync()
        com.alex.a2ndbrain.core.sync.CloudSyncWorker.schedule(this)
        com.alex.a2ndbrain.core.usage.DistractionAlertWorker.schedule(this)
        com.alex.a2ndbrain.ui.widget.WidgetUpdateWorker.schedule(this)
        com.alex.a2ndbrain.ui.widget.WidgetUpdateWorker.runNow(this)

        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val themePreference by settingsViewModel.themePreference.collectAsStateWithLifecycle()
            val isDark = when (themePreference) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            BrainTheme(darkTheme = isDark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var setupCompleted by remember {
                        mutableStateOf(settingsManager.hasCompletedSetup())
                    }

                    if (!setupCompleted) {
                        val context = androidx.compose.ui.platform.LocalContext.current

                        val isNotificationListenerGranted = remember(setupCompleted) {
                            val cn = ComponentName(
                                this@MainActivity,
                                com.alex.a2ndbrain.core.capture.NotificationCaptureService::class.java
                            )
                            val flat = android.provider.Settings.Secure.getString(
                                context.contentResolver, "enabled_notification_listeners"
                            ) ?: ""
                            flat.contains(cn.flattenToString())
                        }
                        val isUsageAccessGranted = remember(setupCompleted) {
                            try {
                                val appOps =
                                    context.getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
                                appOps.checkOpNoThrow(
                                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                                    android.os.Process.myUid(), context.packageName
                                ) == android.app.AppOpsManager.MODE_ALLOWED
                            } catch (e: Exception) {
                                false
                            }
                        }

                        PermissionWizardScreen(
                            permissions = listOf(
                                WizardPermission(
                                    icon = Icons.Default.Notifications,
                                    iconTint = androidx.compose.ui.graphics.Color(0xFF5C6BC0),
                                    title = "Notification Access",
                                    description = "Captures notifications from your apps to build your daily memory stream.",
                                    isRequired = true,
                                    isGranted = isNotificationListenerGranted,
                                    actionLabel = "Enable",
                                    onAction = { startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
                                ),
                                WizardPermission(
                                    icon = Icons.Default.Schedule,
                                    iconTint = androidx.compose.ui.graphics.Color(0xFF26A69A),
                                    title = "Usage Access",
                                    description = "Tracks your screen time per app so the AI can spot digital patterns.",
                                    isRequired = true,
                                    isGranted = isUsageAccessGranted,
                                    actionLabel = "Enable",
                                    onAction = { startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                                ),
                                WizardPermission(
                                    icon = Icons.Default.Favorite,
                                    iconTint = androidx.compose.ui.graphics.Color(0xFFEF5350),
                                    title = "Health Connect",
                                    description = "Syncs steps, sleep, and heart rate from your smartwatch.",
                                    isRequired = false,
                                    isGranted = false,
                                    actionLabel = "Connect",
                                    onAction = {
                                        try {
                                            startActivity(android.content.Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"))
                                        } catch (e: Exception) {
                                            startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
                                        }
                                    }
                                ),
                                WizardPermission(
                                    icon = Icons.Default.LocationOn,
                                    iconTint = androidx.compose.ui.graphics.Color(0xFFFF7043),
                                    title = "Location & Bluetooth",
                                    description = "Required for P2P device sync between your phone and tablet.",
                                    isRequired = false,
                                    isGranted = hasSyncPermissions(),
                                    actionLabel = "Enable",
                                    onAction = {
                                        startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .apply {
                                                    data =
                                                        android.net.Uri.parse("package:${packageName}")
                                                })
                                    }
                                )
                            ),
                            onContinue = {
                                settingsManager.markSetupCompleted()
                                setupCompleted = true
                            }
                        )
                    } else {
                        // --- Permission wizard state ---
                        val settingsManagerCompose =
                            settingsManager // already injected above setContent

                        val nearbyPermissions = buildList {
                            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                                add(android.Manifest.permission.BLUETOOTH_SCAN)
                                add(android.Manifest.permission.BLUETOOTH_CONNECT)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
                            }
                        }

                        val requestNearbyLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestMultiplePermissions()
                        ) { /* re-check triggers via resumeKey */ }

                        val requestPostNotificationsLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { /* re-check triggers via resumeKey */ }

                        val requestCalendarLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { /* re-check triggers via resumeKey */ }

                        // Increment on every onResume so permission checks rerun
                        var resumeKey by remember { mutableIntStateOf(0) }
                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) resumeKey++
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                        }

                        val hasNotificationAccess =
                            remember(resumeKey) { settingsManagerCompose.isNotificationAccessGranted() }
                        val hasUsageAccess =
                            remember(resumeKey) { settingsManagerCompose.isUsageAccessGranted() }
                        val hasNearbyPerms = remember(resumeKey) { hasSyncPermissions() }
                        val hasPostNotifications = remember(resumeKey) {
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        }
                        val hasCalendarPermission = remember(resumeKey) {
                            checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                        }

                        var showWizard by remember {
                            mutableStateOf(!settingsManagerCompose.isNotificationAccessGranted() || !settingsManagerCompose.isUsageAccessGranted())
                        }
                        // --- end wizard state ---

                        val navViewModel: NavigationViewModel = koinViewModel()
                        val homeViewModel: HomeViewModel = koinViewModel()
                        val memoryViewModel: MemoryViewModel = koinViewModel()
                        val copilotViewModel: CopilotViewModel = koinViewModel()

                        // Hoisted Health Connect launcher (wizard + Home tab both use it)
                        val requestHealthPermissionLauncher = rememberLauncherForActivityResult(
                            contract = PermissionController.createRequestPermissionResultContract()
                        ) {
                            homeViewModel.checkHealthPermissionsAndSync()
                        }

                        val hasHealthConnect =
                            remember(resumeKey) { homeViewModel.healthPermissionsGranted.value }

                        val currentTab by navViewModel.currentTab.collectAsStateWithLifecycle()
                        val error by navViewModel.errorFlow.collectAsStateWithLifecycle()

                        // Refresh health data every time the app resumes so Zepp→Health Connect
                        // syncs are picked up without relaunching the app.
                        LaunchedEffect(resumeKey) {
                            if (resumeKey > 0) {
                                homeViewModel.checkHealthPermissionsAndSync()
                            }
                        }

                        // Automatically forward preset Copilot queries to the Copilot ViewModel
                        LaunchedEffect(Unit) {
                            navViewModel.presetCopilotQuery.collect { query ->
                                copilotViewModel.sendChatMessage(query)
                            }
                        }

                        LaunchedEffect(error) {
                            error?.let {
                                Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                                navViewModel.clearError()
                            }
                        }

                        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                        val useRail = configuration.screenWidthDp >= 600

                        val navTabs = listOf(
                            Triple("Today", Icons.Default.Home, AppTab.TODAY),
                            Triple("Feed", Icons.Default.Notifications, AppTab.FEED),
                            Triple("Wellness", Icons.Default.Favorite, AppTab.WELLNESS),
                            Triple("Notes", Icons.Default.Description, AppTab.NOTES)
                        )
                        val navSelectedColor = MaterialTheme.colorScheme.primary
                        val navUnselectedColor = MaterialTheme.colorScheme.secondary
                        val navIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)

                        val snackbarHostState = remember { SnackbarHostState() }

                        if (showWizard) {
                            PermissionWizardScreen(
                                permissions = listOf(
                                    WizardPermission(
                                        icon = Icons.Default.Notifications,
                                        iconTint = androidx.compose.ui.graphics.Color(0xFF1E88E5),
                                        title = "Notification Access",
                                        description = "Captures Gmail, Calendar, Todoist and other app alerts into your Brain feed.",
                                        isRequired = true,
                                        isGranted = hasNotificationAccess,
                                        actionLabel = "Open Settings",
                                        onAction = { startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
                                    ),
                                    WizardPermission(
                                        icon = Icons.Default.QueryStats,
                                        iconTint = androidx.compose.ui.graphics.Color(0xFF8E24AA),
                                        title = "Usage Access",
                                        description = "Powers the Digital Time dashboard with per-app screen time data.",
                                        isRequired = true,
                                        isGranted = hasUsageAccess,
                                        actionLabel = "Open Settings",
                                        onAction = { startActivity(android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                                    ),
                                    WizardPermission(
                                        icon = Icons.Default.NotificationsActive,
                                        iconTint = androidx.compose.ui.graphics.Color(0xFFF4511E),
                                        title = "Push Notifications",
                                        description = "Required to deliver push notifications.",
                                        isRequired = true,
                                        isGranted = hasPostNotifications,
                                        actionLabel = "Grant",
                                        onAction = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                requestPostNotificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                    ),
                                    WizardPermission(
                                        icon = Icons.Default.Bluetooth,
                                        iconTint = androidx.compose.ui.graphics.Color(0xFF00897B),
                                        title = "Nearby Device Sync",
                                        description = "Syncs screen time and meditation sessions with your other devices over P2P.",
                                        isRequired = false,
                                        isGranted = hasNearbyPerms,
                                        actionLabel = "Grant",
                                        onAction = { requestNearbyLauncher.launch(nearbyPermissions.toTypedArray()) }
                                    ),
                                    WizardPermission(
                                        icon = Icons.Default.Favorite,
                                        iconTint = androidx.compose.ui.graphics.Color(0xFFE53935),
                                        title = "Health Connect",
                                        description = "Reads steps, sleep and heart rate from your smartwatch via Health Connect.",
                                        isRequired = false,
                                        isGranted = hasHealthConnect,
                                        actionLabel = "Connect",
                                        onAction = {
                                            requestHealthPermissionLauncher.launch(
                                                homeViewModel.healthConnectManager.permissions
                                            )
                                        }
                                    ),
                                    WizardPermission(
                                        icon = Icons.Default.CalendarMonth,
                                        iconTint = androidx.compose.ui.graphics.Color(0xFF039BE5),
                                        title = "Calendar Access",
                                        description = "Shows today's Google Calendar events in your home timeline.",
                                        isRequired = false,
                                        isGranted = hasCalendarPermission,
                                        actionLabel = "Grant",
                                        onAction = {
                                            requestCalendarLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                                        }
                                    )
                                ),
                                onContinue = { showWizard = false }
                            )
                        } else {
                            Scaffold(
                                snackbarHost = { SnackbarHost(snackbarHostState) },
                                topBar = {
                                    if (!useRail) {
                                        TopAppBar(
                                            title = { Text("2ndBrain") },
                                            actions = {
                                                IconButton(onClick = { navViewModel.setTab(AppTab.SEARCH) }) {
                                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                                }
                                                IconButton(onClick = { navViewModel.setTab(AppTab.SETTINGS) }) {
                                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                                }
                                            }
                                        )
                                    }
                                },
                                bottomBar = {
                                    if (!useRail) {
                                        NavigationBar(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ) {
                                            navTabs.forEach { (label, icon, tab) ->
                                                NavigationBarItem(
                                                    icon = { Icon(icon, contentDescription = label) },
                                                    label = { Text(label) },
                                                    selected = currentTab == tab,
                                                    onClick = { navViewModel.setTab(tab) },
                                                    colors = NavigationBarItemDefaults.colors(
                                                        selectedIconColor = navSelectedColor,
                                                        unselectedIconColor = navUnselectedColor,
                                                        indicatorColor = navIndicatorColor
                                                    )
                                                )
                                            }
                                        }
                                    }
                                },
                                floatingActionButton = {
                                    if (!useRail && currentTab != AppTab.NOTES && currentTab != AppTab.COPILOT) {
                                        FloatingActionButton(onClick = { navViewModel.setTab(AppTab.COPILOT) }) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = "Co-pilot")
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    if (useRail) {
                                        NavigationRail(
                                            modifier = Modifier.fillMaxHeight(),
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            header = {
                                                FloatingActionButton(
                                                    onClick = { navViewModel.setTab(AppTab.COPILOT) },
                                                    modifier = Modifier.padding(vertical = 16.dp)
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = "Co-pilot")
                                                }
                                            }
                                        ) {
                                            navTabs.forEach { (label, icon, tab) ->
                                                NavigationRailItem(
                                                    icon = { Icon(icon, contentDescription = label) },
                                                    label = { Text(label) },
                                                    selected = currentTab == tab,
                                                    onClick = { navViewModel.setTab(tab) },
                                                    colors = NavigationRailItemDefaults.colors(
                                                        selectedIconColor = navSelectedColor,
                                                        unselectedIconColor = navUnselectedColor,
                                                        indicatorColor = navIndicatorColor
                                                    )
                                                )
                                            }

                                            Spacer(modifier = Modifier.weight(1f))

                                            NavigationRailItem(
                                                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                                label = { Text("Search") },
                                                selected = currentTab == AppTab.SEARCH,
                                                onClick = { navViewModel.setTab(AppTab.SEARCH) },
                                                colors = NavigationRailItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                                    unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                )
                                            )

                                            NavigationRailItem(
                                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                                label = { Text("Settings") },
                                                selected = currentTab == AppTab.SETTINGS,
                                                onClick = { navViewModel.setTab(AppTab.SETTINGS) },
                                                colors = NavigationRailItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                                    unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                )
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            when (currentTab) {
                                                AppTab.TODAY -> {
                                                    val healthMetrics by homeViewModel.healthMetricsToday.collectAsStateWithLifecycle()
                                                    val healthPermissionGranted by homeViewModel.healthPermissionsGranted.collectAsStateWithLifecycle()
                                                    val healthConnectAvailable =
                                                        homeViewModel.healthConnectManager.isAvailable()

                                                    val senseOfDayScore by homeViewModel.senseOfDayScore.collectAsStateWithLifecycle()
                                                    val senseOfDayContext by homeViewModel.senseOfDayContext.collectAsStateWithLifecycle()
                                                    val todayTimelineEvents by homeViewModel.todayTimelineEvents.collectAsStateWithLifecycle()
                                                    val tomorrowTimelineEvents by homeViewModel.tomorrowTimelineEvents.collectAsStateWithLifecycle()
                                                    val timelineConflicts by homeViewModel.timelineConflicts.collectAsStateWithLifecycle()
                                                    val inlineCopilotResponses by homeViewModel.inlineCopilotResponses.collectAsStateWithLifecycle()
                                                    val inlineCopilotLoading by homeViewModel.inlineCopilotLoading.collectAsStateWithLifecycle()

                                                    val allMemoriesForHome by homeViewModel.allMemoriesForHome.collectAsStateWithLifecycle()
                                                    val meditationSessions by homeViewModel.meditationSessions.collectAsStateWithLifecycle()
                                                    val meditationStreaks by homeViewModel.meditationStreaks.collectAsStateWithLifecycle()
                                                    val vaultNotes by homeViewModel.vaultNotes.collectAsStateWithLifecycle()
                                                    val consolidatedUsage by homeViewModel.consolidatedUsage.collectAsStateWithLifecycle()
                                                    val summaries by homeViewModel.summaries.collectAsStateWithLifecycle()
                                                    val lastRefreshedAt by homeViewModel.lastRefreshedAt.collectAsStateWithLifecycle()
                                                    val refreshIntervalMinutes by homeViewModel.refreshIntervalMinutes.collectAsStateWithLifecycle()
                                                    val unreadEmailCount by homeViewModel.unreadEmailCount.collectAsStateWithLifecycle()
                                                    val unreadMessageCount by homeViewModel.unreadMessageCount.collectAsStateWithLifecycle()
                                                    val meetingsTodayCount by homeViewModel.meetingsTodayCount.collectAsStateWithLifecycle()
                                                    val emailTriageResult by homeViewModel.emailTriageResult.collectAsStateWithLifecycle()
                                                    LaunchedEffect(Unit) {
                                                        homeViewModel.checkHealthPermissionsAndSync()
                                                        homeViewModel.loadVaultNotes()
                                                    }

                                                    HomeScreen(
                                                        memories = allMemoriesForHome,
                                                        meditationSessions = meditationSessions,
                                                        meditationStreaks = meditationStreaks,
                                                        latestReflection = summaries.firstOrNull(),
                                                        notes = vaultNotes,
                                                        consolidatedUsage = consolidatedUsage,
                                                        onNavigateToTab = { navViewModel.setTab(it) },
                                                        onNavigateToFeedWithFilter = { filter -> navViewModel.navigateToFeed(filter) },
                                                        healthMetrics = healthMetrics,
                                                        healthPermissionGranted = healthPermissionGranted,
                                                        healthConnectAvailable = healthConnectAvailable,
                                                        onConnectHealth = {
                                                            requestHealthPermissionLauncher.launch(
                                                                homeViewModel.healthConnectManager.permissions
                                                            )
                                                        },
                                                        senseOfDayScore = senseOfDayScore,
                                                        senseOfDayContext = senseOfDayContext,
                                                        todayTimelineEvents = todayTimelineEvents,
                                                        tomorrowTimelineEvents = tomorrowTimelineEvents,
                                                        timelineConflicts = timelineConflicts,
                                                        inlineCopilotResponses = inlineCopilotResponses,
                                                        inlineCopilotLoading = inlineCopilotLoading,
                                                        onDismissConflict = { id ->
                                                            homeViewModel.dismissConflict(
                                                                id
                                                            )
                                                        },
                                                        onDeepDiveCoPilotPrompt = { prompt ->
                                                            navViewModel.triggerCopilotQuery(
                                                                prompt
                                                            )
                                                        },
                                                        onResolveInline = { id, prompt ->
                                                            homeViewModel.resolveInlineCopilot(
                                                                id,
                                                                prompt
                                                            )
                                                        },
                                                        onAddManualEvent = { title, time ->
                                                            homeViewModel.addManualAgendaEvent(
                                                                title,
                                                                time
                                                            )
                                                        },
                                                        onRefreshHealth = { homeViewModel.checkHealthPermissionsAndSync() },
                                                        onDeepDiveCoPilot = { event ->
                                                            val query = """
                                                        Analyze my memories, notes, and habits for today's event: "${event.title}" scheduled at ${event.time} (${event.appName}). Please provide a cohesive context correlation summary to help me prepare.
                                                    """.trimIndent()
                                                            navViewModel.triggerCopilotQuery(query)
                                                        },
                                                        onDeleteManualEvent = { id ->
                                                            homeViewModel.deleteManualAgendaEvent(id)
                                                        },
                                                        lastRefreshedAt = lastRefreshedAt,
                                                        refreshIntervalMinutes = refreshIntervalMinutes,
                                                        unreadEmailCount = unreadEmailCount,
                                                        unreadMessageCount = unreadMessageCount,
                                                        meetingsTodayCount = meetingsTodayCount,
                                                        emailTriageResult = emailTriageResult,
                                                        onRefreshIntervalChange = { homeViewModel.setRefreshInterval(it) },
                                                        themePreference = themePreference,
                                                        onThemeToggle = {
                                                            val next = if (themePreference == "DARK") "LIGHT" else "DARK"
                                                            settingsViewModel.saveThemePreference(next)
                                                        }
                                                    )
                                                }

                                                AppTab.FEED -> {
                                                    val memories by memoryViewModel.memories.collectAsStateWithLifecycle()
                                                    val searchQuery by memoryViewModel.searchQuery.collectAsStateWithLifecycle()
                                                    val feedFilter by navViewModel.feedFilter.collectAsStateWithLifecycle()
                                                    var feedMonitoredApps by remember { mutableStateOf(settingsManager.getMonitoredApps()) }

                                                    MemoryScreen(
                                                        memories = memories,
                                                        searchQuery = searchQuery,
                                                        onSearchQueryChange = { memoryViewModel.setSearchQuery(it) },
                                                        onCaptureClipboard = { clipboardCaptureManager.captureCurrentClipboard() },
                                                        onMarkAsRead = { ids -> memoryViewModel.markMultipleAsRead(ids) },
                                                        onMarkAsUnread = { ids -> memoryViewModel.markMultipleAsUnread(ids) },
                                                        onClearAll = { memoryViewModel.clearAllMemories() },
                                                        monitoredApps = feedMonitoredApps,
                                                        onClearAppFilter = {
                                                            settingsManager.saveMonitoredApps(emptySet())
                                                            feedMonitoredApps = emptySet()
                                                            homeViewModel.refreshMonitoredApps()
                                                        },
                                                        initialFilter = feedFilter,
                                                        vaultUri = settingsManager.getObsidianVaultUri(),
                                                        onSaveVoiceNote = { text, audioPath ->
                                                            memoryViewModel.saveVoiceNote(text, audioPath, settingsManager.getObsidianVaultUri())
                                                        },
                                                        onDeepDiveCoPilot = { memory ->
                                                            val key = memory.packageName ?: memory.source
                                                            val appName = try {
                                                                val pm = packageManager
                                                                val appInfo = pm.getApplicationInfo(key, 0)
                                                                pm.getApplicationLabel(appInfo).toString()
                                                            } catch (e: Exception) {
                                                                if (key == "clipboard") "Clipboard"
                                                                else if (key == "voice") "Voice Memos"
                                                                else key
                                                            }
                                                            val query = """
                                                        Analyze my memories, notes, and habits for this captured log from "$appName": "${memory.title ?: "Untitled"}" - "${memory.content}". Please provide a cohesive context correlation summary to help me understand how this fits into my daily agenda and physical/cognitive trends.
                                                    """.trimIndent()
                                                            navViewModel.triggerCopilotQuery(query)
                                                        },
                                                        onNotesSelected = { navViewModel.setTab(AppTab.NOTES) }
                                                    )
                                                }

                                                AppTab.WELLNESS -> WellnessScreen(settingsManager = settingsManager)

                                                AppTab.NOTES -> NotesScreen(
                                                    settingsManager = settingsManager,
                                                    onCopilotClick = { navViewModel.setTab(AppTab.COPILOT) }
                                                )

                                                AppTab.SETTINGS -> {
                                                    val themePreference by settingsViewModel.themePreference.collectAsStateWithLifecycle()
                                                    val syncStatus by settingsViewModel.syncStatus.collectAsStateWithLifecycle()
                                                    AppCaptureSettingsScreen(
                                                        settingsManager = settingsManager,
                                                        onBack = {
                                                            navViewModel.setTab(AppTab.TODAY)
                                                            homeViewModel.refreshMonitoredApps()
                                                        },
                                                        onRestartService = {
                                                            val componentName =
                                                                android.content.ComponentName(
                                                                    this@MainActivity,
                                                                    com.alex.a2ndbrain.core.capture.NotificationCaptureService::class.java
                                                                )
                                                            packageManager.setComponentEnabledSetting(
                                                                componentName,
                                                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                                                PackageManager.DONT_KILL_APP
                                                            )
                                                            packageManager.setComponentEnabledSetting(
                                                                componentName,
                                                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                                                PackageManager.DONT_KILL_APP
                                                            )
                                                        },
                                                        onUnmonitoredAppRemoved = {
                                                            settingsViewModel.deleteUnmonitoredAppData(
                                                                it
                                                            )
                                                        },
                                                        syncStatus = syncStatus,
                                                        onStartSync = { force ->
                                                            settingsViewModel.startNearbySync(
                                                                force
                                                            )
                                                        },
                                                        onStopSync = { settingsViewModel.stopNearbySync() }
                                                    )
                                                }

                                                AppTab.COPILOT -> {
                                                    val chatMessages by copilotViewModel.chatMessages.collectAsStateWithLifecycle()
                                                    val chatIsThinking by copilotViewModel.chatIsThinking.collectAsStateWithLifecycle()
                                                    com.alex.a2ndbrain.ui.chat.BrainChatScreen(
                                                        messages = chatMessages,
                                                        isThinking = chatIsThinking,
                                                        onSendMessage = {
                                                            copilotViewModel.sendChatMessage(
                                                                it
                                                            )
                                                        }
                                                    )
                                                }

                                                AppTab.SEARCH -> {
                                                    SearchScreen(
                                                        onBack = { navViewModel.setTab(AppTab.TODAY) },
                                                        onMemorySelected = { memory ->
                                                            val pkg = memory.packageName ?: ""
                                                            try {
                                                                when {
                                                                    pkg == "com.google.android.gm" -> {
                                                                        val raw = (memory.content.split("\n")
                                                                            .map { it.trim() }.firstOrNull { it.isNotBlank() }
                                                                            ?: memory.title ?: "")
                                                                        // Strip Gmail search operators then keep first 3 significant words.
                                                                        // Short queries are more forgiving when notification text is truncated.
                                                                        val query = raw
                                                                            .replace(Regex("[&#+()\\[\\]{}|<>\"']"), " ")
                                                                            .split(Regex("\\s+"))
                                                                            .filter { it.length > 2 }
                                                                            .take(3)
                                                                            .joinToString(" ")
                                                                            .ifBlank { raw.take(30) }
                                                                        startActivity(android.content.Intent(
                                                                            android.content.Intent.ACTION_VIEW,
                                                                            android.net.Uri.parse("https://mail.google.com/mail/u/0/#search/${android.net.Uri.encode(query)}")
                                                                        ))
                                                                    }
                                                                    pkg.isNotEmpty() -> {
                                                                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                                                                        if (launchIntent != null) startActivity(launchIntent)
                                                                        else navViewModel.setTab(AppTab.FEED)
                                                                    }
                                                                    else -> navViewModel.setTab(AppTab.FEED)
                                                                }
                                                            } catch (e: Exception) {
                                                                navViewModel.setTab(AppTab.FEED)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } // end else (wizard hidden)
                    }
                }
            } // end else
        }
    }
}
