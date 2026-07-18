package de.sanniki.wakesleuth

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.sanniki.wakesleuth.ui.theme.WakesleuthTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import rikka.shizuku.Shizuku

private enum class MainSection(
    val label: String,
    val shortLabel: String
) {
    OVERVIEW(
        label = "Übersicht",
        shortLabel = "Ü"
    ),
    ANALYSIS(
        label = "Analyse",
        shortLabel = "A"
    ),
    SESSIONS(
        label = "Sitzungen",
        shortLabel = "S"
    ),
    DIAGNOSTICS(
        label = "Diagnose",
        shortLabel = "D"
    )
}

private enum class EventFilter(
    val label: String
) {
    ALL("Alle"),
    DISPLAY("Display"),
    BACKGROUND("Hintergrund"),
    NOTIFICATIONS("Hinweise"),
    UNKNOWN("Ungeklärt")
}

private enum class EventViewMode(
    val label: String
) {
    LIST("Liste"),
    TIMELINE("Zeitstrahl")
}

private enum class CauseConfidence(
    val label: String
) {
    CONFIRMED("Bestätigt"),
    PROBABLE("Wahrscheinlich"),
    POSSIBLE("Möglicher Zusammenhang"),
    COMPANION("Begleitaktivität"),
    UNRESOLVED("Ungeklärt")
}

private data class CauseAssessment(
    val confidence: CauseConfidence,
    val explanation: String
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!WakeMonitorService.isRunning) {
            EventStore.setMonitoring(this, false)
        }

        setContent {
            var uiSettings by remember {
                mutableStateOf(
                    WakeSleuthUiSettingsStore.load(
                        this
                    )
                )
            }

            WakesleuthTheme(
                accentColor =
                    uiSettings.accentColor
            ) {
                WakeSleuthScreen(
                    uiSettings = uiSettings,
                    onUiSettingsChanged = {
                        uiSettings = it

                        WakeSleuthUiSettingsStore.save(
                            this,
                            it
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun WakeSleuthScreen(
    uiSettings: WakeSleuthUiSettings,
    onUiSettingsChanged:
        (WakeSleuthUiSettings) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner =
        LocalLifecycleOwner.current

    var appIsResumed by remember {
        mutableStateOf(
            lifecycleOwner.lifecycle
                .currentState
                .isAtLeast(
                    Lifecycle.State.RESUMED
                )
        )
    }

    DisposableEffect(
        lifecycleOwner
    ) {
        val observer =
            LifecycleEventObserver {
                    _,
                    event ->

                appIsResumed =
                    when (event) {
                        Lifecycle.Event.ON_RESUME ->
                            true

                        Lifecycle.Event.ON_PAUSE,
                        Lifecycle.Event.ON_STOP,
                        Lifecycle.Event.ON_DESTROY ->
                            false

                        else ->
                            lifecycleOwner.lifecycle
                                .currentState
                                .isAtLeast(
                                    Lifecycle.State.RESUMED
                                )
                    }
            }

        lifecycleOwner.lifecycle
            .addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle
                .removeObserver(observer)
        }
    }

    var showSettings by remember {
        mutableStateOf(false)
    }

    val mainSections =
        MainSection.entries

    val pagerState =
        rememberPagerState(
            initialPage = 0,
            pageCount = {
                mainSections.size
            }
        )

    val navigationScope =
        rememberCoroutineScope()

    val selectedMainSection =
        mainSections[
            pagerState.currentPage
        ]

    var lastBackPressMillis by remember {
        mutableStateOf(0L)
    }

    BackHandler {
        when {
            showSettings -> {
                showSettings = false
            }

            selectedMainSection !=
                MainSection.OVERVIEW -> {
                navigationScope.launch {
                    pagerState.animateScrollToPage(
                        0
                    )
                }
            }

            else -> {
                val now =
                    System.currentTimeMillis()

                if (
                    now - lastBackPressMillis <=
                        2_000L
                ) {
                    val activity =
                        context as? ComponentActivity

                    activity?.finish()
                } else {
                    lastBackPressMillis = now

                    Toast.makeText(
                        context,
                        "Zum Beenden erneut zurück",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val uiPreferences = remember {
        context.getSharedPreferences(
            "wakesleuth_ui",
            Context.MODE_PRIVATE
        )
    }

    var events by remember {
        mutableStateOf(EventStore.getEvents(context))
    }

    var monitoring by remember {
        mutableStateOf(WakeMonitorService.isRunning)
    }

    val currentSessionStartMillis =
        remember(
            events,
            monitoring
        ) {
            if (!monitoring) {
                null
            } else {
                events
                    .filter {
                        it.type ==
                            "MONITOR_START"
                    }
                    .maxByOrNull {
                        it.timestamp
                    }
                    ?.timestamp
            }
        }

    var currentSessionDurationMillis by remember {
        mutableStateOf(0L)
    }

    var stopRequestedAtMillis by remember {
        mutableStateOf<Long?>(null)
    }

    LaunchedEffect(
        monitoring,
        appIsResumed,
        currentSessionStartMillis,
        stopRequestedAtMillis
    ) {
        if (
            !monitoring ||
            !appIsResumed ||
            currentSessionStartMillis == null ||
            stopRequestedAtMillis != null
        ) {
            if (!monitoring) {
                currentSessionDurationMillis =
                    0L

                stopRequestedAtMillis =
                    null
            }

            return@LaunchedEffect
        }

        while (isActive) {
            currentSessionDurationMillis =
                (
                    System.currentTimeMillis() -
                        currentSessionStartMillis
                ).coerceAtLeast(0L)

            delay(1_000L)
        }
    }

    var listenerEnabled by remember {
        mutableStateOf(
            isNotificationAccessEnabled(context)
        )
    }

    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var pendingExportText by remember {
        mutableStateOf("")
    }

    val coroutineScope = rememberCoroutineScope()

    var shizukuState by remember {
        mutableStateOf(ShizukuDiagnostics.state())
    }

    DisposableEffect(Unit) {
        val binderReceivedListener =
            Shizuku.OnBinderReceivedListener {
                shizukuState =
                    ShizukuDiagnostics.state()
            }

        val binderDeadListener =
            Shizuku.OnBinderDeadListener {
                shizukuState =
                    ShizukuState.NOT_RUNNING
            }

        val permissionResultListener =
            Shizuku.OnRequestPermissionResultListener {
                    requestCode,
                    grantResult ->

                if (
                    requestCode ==
                    ShizukuDiagnostics.REQUEST_CODE
                ) {
                    shizukuState =
                        ShizukuDiagnostics.state()
                }
            }

        Shizuku.addBinderReceivedListenerSticky(
            binderReceivedListener
        )

        Shizuku.addBinderDeadListener(
            binderDeadListener
        )

        Shizuku.addRequestPermissionResultListener(
            permissionResultListener
        )

        shizukuState =
            ShizukuDiagnostics.state()

        onDispose {
            Shizuku.removeBinderReceivedListener(
                binderReceivedListener
            )

            Shizuku.removeBinderDeadListener(
                binderDeadListener
            )

            Shizuku.removeRequestPermissionResultListener(
                permissionResultListener
            )
        }
    }

    var wakeLockDiagnostic by remember {
        mutableStateOf<WakeLockDiagnostic?>(null)
    }

    var wakeLockLoading by remember {
        mutableStateOf(false)
    }

    var wakeupAlarmDiagnostic by remember {
        mutableStateOf<WakeupAlarmDiagnostic?>(null)
    }

    var wakeupAlarmLoading by remember {
        mutableStateOf(false)
    }

    var backgroundJobDiagnostic by remember {
        mutableStateOf<BackgroundJobDiagnostic?>(null)
    }

    var backgroundJobLoading by remember {
        mutableStateOf(false)
    }

    var wakeReasonDiagnostic by remember {
        mutableStateOf<WakeReasonDiagnostic?>(null)
    }

    var wakeReasonLoading by remember {
        mutableStateOf(false)
    }

    var networkStatsDiagnostic by remember {
        mutableStateOf<NetworkStatsDiagnostic?>(null)
    }

    var networkStatsLoading by remember {
        mutableStateOf(false)
    }

    var selectedEventViewMode by remember {
        mutableStateOf(
            runCatching {
                EventViewMode.valueOf(
                    uiPreferences.getString(
                        "event_view_mode",
                        EventViewMode.LIST.name
                    ) ?: EventViewMode.LIST.name
                )
            }.getOrDefault(
                EventViewMode.LIST
            )
        )
    }

    var eventSectionExpanded by remember {
        mutableStateOf(false)
    }

    var selectedFilter by remember {
        mutableStateOf(
            runCatching {
                EventFilter.valueOf(
                    uiPreferences.getString(
                        "event_filter",
                        EventFilter.ALL.name
                    ) ?: EventFilter.ALL.name
                )
            }.getOrDefault(EventFilter.ALL)
        )
    }

    val filteredEvents = remember(
        events,
        selectedFilter
    ) {
        when (selectedFilter) {
            EventFilter.ALL -> events

            EventFilter.DISPLAY -> {
                events.filter {
                    it.type == "SCREEN_ON" ||
                        it.type == "SCREEN_OFF"
                }
            }

            EventFilter.BACKGROUND -> {
                events.filter {
                    it.type == "CPU_WAKEUP"
                }
            }

            EventFilter.NOTIFICATIONS -> {
                events.filter {
                    it.type == "NOTIFICATION"
                }
            }

            EventFilter.UNKNOWN -> {
                events.filter {
                    it.type == "SCREEN_ON" &&
                        isUnexplainedScreenOn(it)
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract =
            ActivityResultContracts.CreateDocument(
                "text/plain"
            )
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        val succeeded = runCatching {
            val outputStream =
                context.contentResolver
                    .openOutputStream(uri)
                    ?: error(
                        "Datei konnte nicht geöffnet werden."
                    )

            outputStream.bufferedWriter().use {
                writer ->
                writer.write(pendingExportText)
            }
        }.isSuccess

        Toast.makeText(
            context,
            if (succeeded) {
                "wakelogs-Export gespeichert"
            } else {
                "Export konnte nicht gespeichert werden"
            },
            Toast.LENGTH_LONG
        ).show()
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { granted ->
            notificationsAllowed =
                granted ||
                    Build.VERSION.SDK_INT < 33

            startMonitoring(context)

            if (
                !granted &&
                Build.VERSION.SDK_INT >= 33
            ) {
                Toast.makeText(
                    context,
                    "Überwachung läuft. Die Dauerbenachrichtigung kann ausgeblendet sein.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    val setupNotificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { granted ->
            notificationsAllowed =
                granted ||
                    Build.VERSION.SDK_INT < 33

            if (
                !granted &&
                Build.VERSION.SDK_INT >= 33
            ) {
                Toast.makeText(
                    context,
                    "Benachrichtigungen wurden nicht erlaubt.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    LaunchedEffect(Unit) {
        while (isActive) {
            events = EventStore.getEvents(context)
            monitoring = WakeMonitorService.isRunning
            listenerEnabled =
                isNotificationAccessEnabled(context)

            notificationsAllowed =
                Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

            shizukuState = ShizukuDiagnostics.state()

            delay(600)
        }
    }

    if (showSettings) {
        WakeSleuthSettingsScreen(
            settings = uiSettings,
            onSettingsChanged =
                onUiSettingsChanged,
            onBack = {
                showSettings = false
            }
        )

        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            wakelogsBottomNavigation(
                selectedSection =
                    selectedMainSection,
                onSectionSelected = {
                        section ->

                    val targetPage =
                        mainSections.indexOf(
                            section
                        )

                    if (
                        targetPage >= 0 &&
                        targetPage !=
                            pagerState.currentPage
                    ) {
                        navigationScope.launch {
                            pagerState.animateScrollToPage(
                                targetPage
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start =
                            uiSettings.cardDensity
                                .pageHorizontalPadding,
                        top = 16.dp,
                        end =
                            uiSettings.cardDensity
                                .pageHorizontalPadding
                    )
            ) {
                HeaderCard(
                    onOpenSettings = {
                        showSettings = true
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(
                    uiSettings.cardDensity
                        .itemSpacing
                )
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = 1,
            key = { page ->
                mainSections[page].name
            }
        ) { page ->
            val pageSection =
                mainSections[page]

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                
                .padding(
                    horizontal =
                        uiSettings.cardDensity
                            .pageHorizontalPadding
                ),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = 24.dp
            ),
            verticalArrangement =
                Arrangement.spacedBy(
                    uiSettings.cardDensity
                        .itemSpacing
                )
        ) {
            item {
                CurrentSectionHeader(
                    section =
                        pageSection
                )
            }

            if (
                pageSection ==
                MainSection.OVERVIEW
            ) {
                item {
                    SetupStatusCard(
                        notificationAccessEnabled =
                            listenerEnabled,
                        notificationsAllowed =
                            notificationsAllowed,
                        shizukuState =
                            shizukuState,
                        onOpenNotificationAccess = {
                            context.startActivity(
                                Intent(
                                    Settings
                                        .ACTION_NOTIFICATION_LISTENER_SETTINGS
                                )
                            )
                        },
                        onRequestNotifications = {
                            if (
                                Build.VERSION.SDK_INT >= 33
                            ) {
                                setupNotificationPermissionLauncher
                                    .launch(
                                        Manifest.permission
                                            .POST_NOTIFICATIONS
                                    )
                            }
                        },
                        onRequestShizukuPermission = {
                            ShizukuDiagnostics.requestPermission()
                        },
                        onOpenShizuku = {
                            val launchIntent =
                                context.packageManager
                                    .getLaunchIntentForPackage(
                                        "moe.shizuku.privileged.api"
                                    )

                            if (launchIntent != null) {
                                context.startActivity(
                                    launchIntent
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    "Shizuku-App wurde nicht gefunden.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }

                item {
                    MonitorCard(
                    monitoring = monitoring,
                    finalizing =
                        stopRequestedAtMillis != null,
                    sessionDurationMillis =
                        stopRequestedAtMillis
                            ?.let { stopMillis ->
                                currentSessionStartMillis
                                    ?.let { startMillis ->
                                        (
                                            stopMillis -
                                                startMillis
                                        ).coerceAtLeast(0L)
                                    }
                            }
                            ?: currentSessionDurationMillis,
                    onStart = {
                        val needsPermission =
                            Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat
                                    .checkSelfPermission(
                                        context,
                                        Manifest.permission
                                            .POST_NOTIFICATIONS
                                    ) !=
                                PackageManager
                                    .PERMISSION_GRANTED

                        if (needsPermission) {
                            notificationPermissionLauncher
                                .launch(
                                    Manifest.permission
                                        .POST_NOTIFICATIONS
                                )
                        } else {
                            startMonitoring(context)
                        }
                    },
                        onStop = {
                            val requestedAtMillis =
                                System.currentTimeMillis()

                            stopRequestedAtMillis =
                                requestedAtMillis

                            currentSessionDurationMillis =
                                currentSessionStartMillis
                                    ?.let { startMillis ->
                                        (
                                            requestedAtMillis -
                                                startMillis
                                        ).coerceAtLeast(0L)
                                    }
                                    ?: currentSessionDurationMillis

                            stopMonitoring(
                                context = context,
                                requestedAtMillis =
                                    requestedAtMillis
                            )
                        }
                    )
                }
            }

            if (
                pageSection ==
                    MainSection.DIAGNOSTICS
            ) {
                item {
                    if (
                        uiSettings.detailLevel ==
                            DetailLevel.EXPERT
                    ) {
                        ShizukuWakeLockCard(
                            state = shizukuState,
                        diagnostic = wakeLockDiagnostic,
                        loading = wakeLockLoading,
                        alarmDiagnostic =
                            wakeupAlarmDiagnostic,
                        alarmLoading =
                            wakeupAlarmLoading,
                        jobDiagnostic =
                            backgroundJobDiagnostic,
                        jobLoading =
                            backgroundJobLoading,
                        wakeReasonDiagnostic =
                            wakeReasonDiagnostic,
                        wakeReasonLoading =
                            wakeReasonLoading,
                        networkStatsDiagnostic =
                            networkStatsDiagnostic,
                        networkStatsLoading =
                            networkStatsLoading,
                        onRequestPermission = {
                            ShizukuDiagnostics.requestPermission()
                        },
                        onCheck = {
                            wakeLockLoading = true
    
                            coroutineScope.launch {
                                wakeLockDiagnostic =
                                    ShizukuDiagnostics
                                        .readWakeLocks(context)
    
                                wakeLockLoading = false
                            }
                        },
                        onAlarmCheck = {
                            wakeupAlarmLoading = true
    
                            coroutineScope.launch {
                                wakeupAlarmDiagnostic =
                                    ShizukuDiagnostics
                                        .readWakeupAlarms(context)
    
                                wakeupAlarmLoading = false
                            }
                        },
                        onJobCheck = {
                            backgroundJobLoading = true
    
                            coroutineScope.launch {
                                backgroundJobDiagnostic =
                                    ShizukuDiagnostics
                                        .readBackgroundJobs(
                                            context
                                        )
    
                                backgroundJobLoading = false
                            }
                        },
                        onWakeReasonCheck = {
                            wakeReasonLoading = true
    
                            coroutineScope.launch {
                                wakeReasonDiagnostic =
                                    ShizukuDiagnostics
                                        .readWakeReason(
                                            context
                                        )
    
                                wakeReasonLoading = false
                            }
                        },
                        onNetworkStatsCheck = {
                            networkStatsLoading = true

                            coroutineScope.launch {
                                networkStatsDiagnostic =
                                    ShizukuDiagnostics
                                        .readNetworkStats(
                                            context
                                        )

                                networkStatsLoading = false
                            }
                        }
                    )
                    } else {
                        ShizukuProfileHintCard(
                            state = shizukuState,
                            detailLevel =
                                uiSettings.detailLevel,
                            onRequestPermission = {
                                ShizukuDiagnostics
                                    .requestPermission()
                            }
                        )
                    }
                }
            }

            if (
                pageSection ==
                    MainSection.OVERVIEW &&
                uiSettings.showDailyStatistics
            ) {
                item {
                    StatisticsCard(events)
                }
            }

            if (
                pageSection ==
                    MainSection.ANALYSIS &&
                uiSettings.showNightAnalysis
            ) {
                item {
                    SleepReportCard(
                        events = events,
                        monitoring = monitoring,
                        detailLevel =
                            uiSettings.detailLevel
                    )
                }
            }

            if (
                pageSection ==
                    MainSection.SESSIONS &&
                uiSettings.showNightAnalysis
            ) {
                item {
                    SessionComparisonCard(
                        events = events,
                        detailLevel =
                            uiSettings.detailLevel
                    )
                }

                item {
                    AppProfilesCard(
                        events = events,
                        detailLevel =
                            uiSettings.detailLevel
                    )
                }

                item {
                    SessionHistoryCard(
                        events = events,
                        detailLevel =
                            uiSettings.detailLevel
                    )
                }
            }

            if (
                pageSection ==
                    MainSection.ANALYSIS &&
                uiSettings.showSourceStatistics &&
                events.isNotEmpty()
            ) {
                item {
                    SourceStatisticsCard(
                        events = events,
                        monitoring = monitoring,
                        detailLevel =
                            uiSettings.detailLevel
                    )
                }
            }

            if (
                pageSection ==
                    MainSection.ANALYSIS &&
                events.isNotEmpty()
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Text(
                                text = "Ereignisse",
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        Text(
                            text =
                                if (events.size == filteredEvents.size) {
                                    "${events.size} gespeichert"
                                } else {
                                    "${filteredEvents.size} von ${events.size} sichtbar"
                                },
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme.typography.bodySmall
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                eventSectionExpanded =
                                    !eventSectionExpanded
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                androidx.compose.ui.graphics.Color(0xFF687181)
                            ),
                            colors =
                                androidx.compose.material3.ButtonDefaults
                                    .outlinedButtonColors(
                                        contentColor =
                                            androidx.compose.ui.graphics.Color.White
                                    )
                        ) {
                            Text(
                                if (eventSectionExpanded) {
                                    "Ereignisse ausblenden"
                                } else {
                                    "Ereignisse anzeigen"
                                }
                            )
                            }
                        }
                    }
                }
            }

            if (
                pageSection ==
                    MainSection.ANALYSIS &&
                eventSectionExpanded &&
                events.isNotEmpty()
            ) {
                item {
                    ActionCard(
                        hasEvents =
                            events.isNotEmpty() ||
                                wakeLockDiagnostic != null ||
                                wakeupAlarmDiagnostic != null ||
                                backgroundJobDiagnostic != null ||
                                wakeReasonDiagnostic != null ||
                                networkStatsDiagnostic != null,
                        onExport = {
                            pendingExportText =
                                buildString {
                                    appendLine(
                                        "Art des Exports: Technischer Bericht"
                                    )
                                    appendLine()

                                    append(
                                        EventStore.buildExport(
                                            context
                                        )
                                    )

                                    appendLine()
                                    appendLine()

                                    append(
                                        buildManualDiagnosticsExport(
                                            context =
                                                context,
                                            wakeLockDiagnostic =
                                                wakeLockDiagnostic,
                                            wakeupAlarmDiagnostic =
                                                wakeupAlarmDiagnostic,
                                            backgroundJobDiagnostic =
                                                backgroundJobDiagnostic,
                                            wakeReasonDiagnostic =
                                                wakeReasonDiagnostic,
                                            networkStatsDiagnostic =
                                                networkStatsDiagnostic
                                        )
                                    )
                                }

                            val formatter =
                                SimpleDateFormat(
                                    "yyyyMMdd_HHmmss",
                                    Locale.US
                                )

                            exportLauncher.launch(
                                "wakelogs_technischer_bericht_${
                                    formatter.format(Date())
                                }.txt"
                            )
                        },
                        onClear = {
                            EventStore.clear(context)
                            events = emptyList()

                            Toast.makeText(
                                context,
                                "Ereignisliste geleert",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                item {
                    EventFilterBar(
                        selectedFilter = selectedFilter,
                        onSelected = { filter ->
                            selectedFilter = filter

                            uiPreferences.edit()
                                .putString(
                                    "event_filter",
                                    filter.name
                                )
                                .apply()
                        }
                    )
                }

                item {
                    EventViewModeBar(
                        selectedMode =
                            selectedEventViewMode,
                        onSelected = { mode ->
                            selectedEventViewMode =
                                mode

                            uiPreferences.edit()
                                .putString(
                                    "event_view_mode",
                                    mode.name
                                )
                                .apply()
                        }
                    )
                }

                if (filteredEvents.isEmpty()) {
                    item {
                        FilterEmptyCard(
                            monitoring = monitoring,
                            filter = selectedFilter
                        )
                    }
                } else {
                    when (
                        selectedEventViewMode
                    ) {
                        EventViewMode.LIST -> {
                            val groupedListItems =
                                buildGroupedEventList(
                                    filteredEvents
                                )

                            items(
                                items =
                                    groupedListItems,
                                key = {
                                    it.stableKey
                                }
                            ) { item ->
                                when (item) {
                                    is SingleEventListItem ->
                                        EventCard(
                                            event =
                                                item.event,
                                            detailLevel =
                                                uiSettings
                                                    .detailLevel
                                        )

                                    is GroupedCpuEventListItem ->
                                        GroupedCpuEventCard(
                                            group = item,
                                            detailLevel =
                                                uiSettings
                                                    .detailLevel
                                        )
                                }
                            }
                        }

                        EventViewMode.TIMELINE -> {
                            item {
                                WakeTimeline(
                                    events =
                                        filteredEvents,
                                    detailLevel =
                                        uiSettings
                                            .detailLevel
                                )
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

@Composable
private fun wakelogsBottomNavigation(
    selectedSection: MainSection,
    onSectionSelected:
        (MainSection) -> Unit
) {
    NavigationBar(
        containerColor =
            MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        MainSection.entries.forEach {
                section ->

            NavigationBarItem(
                selected =
                    selectedSection ==
                        section,
                onClick = {
                    onSectionSelected(
                        section
                    )
                },
                icon = {
                    Icon(
                        imageVector =
                            when (section) {
                                MainSection.OVERVIEW ->
                                    Icons.Filled.Home

                                MainSection.ANALYSIS ->
                                    Icons.Filled.Analytics

                                MainSection.SESSIONS ->
                                    Icons.Filled.History

                                MainSection.DIAGNOSTICS ->
                                    Icons.Filled.Build
                            },
                        contentDescription =
                            section.label
                    )
                },
                label = {
                    Text(
                        text =
                            section.label,
                        maxLines = 1,
                        fontWeight =
                            if (
                                selectedSection ==
                                section
                            ) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            }
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor =
                            MaterialTheme
                                .colorScheme
                                .onPrimaryContainer,
                        selectedTextColor =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        indicatorColor =
                            MaterialTheme
                                .colorScheme
                                .primaryContainer,
                        unselectedIconColor =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        unselectedTextColor =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun CurrentSectionHeader(
    section: MainSection
) {
    if (
        section ==
            MainSection.OVERVIEW
    ) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 4.dp,
                vertical = 2.dp
            )
    ) {
        if (
            section !=
            MainSection.OVERVIEW
        ) {
            Text(
                text = section.label,
                style =
                    MaterialTheme.typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(2.dp)
            )
        }

        Text(
            text =
                when (section) {
                    MainSection.OVERVIEW ->
                        "Aktueller Gerätestatus"

                    MainSection.ANALYSIS ->
                        "Auswertung, Quellen und Ereignisse"

                    MainSection.SESSIONS ->
                        "Vergleich und gespeicherte Messungen"

                    MainSection.DIAGNOSTICS ->
                        "Shizuku und technische Systemprüfungen"
                },
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .bodySmall
        )
    }
}

@Composable
private fun HeaderCard(
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 13.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "wakelogs",
                    color =
                        MaterialTheme.colorScheme
                            .onPrimary,
                    style =
                        MaterialTheme.typography
                            .headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(1.dp)
                )

                Text(
                    text =
                        "Version ${BuildConfig.VERSION_NAME} · dernikiausd",
                    color =
                        MaterialTheme.colorScheme
                            .onPrimary.copy(
                                alpha = 0.68f
                            ),
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(
                onClick = onOpenSettings
            ) {
                Text(
                    text = "Einstellungen",
                    color =
                        MaterialTheme.colorScheme
                            .onPrimary,
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SetupStatusCard(
    notificationAccessEnabled: Boolean,
    notificationsAllowed: Boolean,
    shizukuState: ShizukuState,
    onOpenNotificationAccess: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    val shizukuReady =
        shizukuState ==
            ShizukuState.RUNNING_GRANTED

    val setupComplete =
        notificationAccessEnabled &&
            notificationsAllowed &&
            shizukuReady

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme
                        .primaryContainer
                        .copy(alpha = 0.30f)
            ),
        border =
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color =
                    MaterialTheme.colorScheme
                        .primary
                        .copy(alpha = 0.45f)
            )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 14.dp
            )
        ) {
            Text(
                text =
                    if (setupComplete) {
                        "Einrichtung vollständig"
                    } else {
                        "Einrichtung erforderlich"
                    },
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme
                        .onPrimaryContainer
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            if (setupComplete) {
                Text(
                    text =
                        "Alle benötigten Zugriffe sind aktiv.",
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            } else {
                if (!notificationAccessEnabled) {
                    SetupRequirementRow(
                        title =
                            "Benachrichtigungszugriff",
                        description =
                            "Erkennt eingehende Benachrichtigungen.",
                        buttonText =
                            "Zugriff aktivieren",
                        onClick =
                            onOpenNotificationAccess
                    )
                }

                if (!notificationsAllowed) {
                    SetupRequirementRow(
                        title =
                            "Benachrichtigungen senden",
                        description =
                            "Zeigt die laufende Überwachung an.",
                        buttonText =
                            "Erlauben",
                        onClick =
                            onRequestNotifications
                    )
                }

                when (shizukuState) {
                    ShizukuState.RUNNING_GRANTED -> Unit

                    ShizukuState.RUNNING_DENIED -> {
                        SetupRequirementRow(
                            title =
                                "Shizuku-Berechtigung",
                            description =
                                "Ermöglicht die vollständige Systemanalyse.",
                            buttonText =
                                "Berechtigung erteilen",
                            onClick =
                                onRequestShizukuPermission
                        )
                    }

                    ShizukuState.NOT_RUNNING -> {
                        SetupRequirementRow(
                            title =
                                "Shizuku nicht aktiv",
                            description =
                                "Die Ursachenanalyse ist eingeschränkt.",
                            buttonText =
                                "Shizuku öffnen",
                            onClick =
                                onOpenShizuku
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupRequirementRow(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Spacer(
        modifier = Modifier.height(12.dp)
    )

    Text(
        text = title,
        fontWeight = FontWeight.SemiBold,
        style =
            MaterialTheme.typography
                .bodyMedium
    )

    Text(
        text = description,
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
        style =
            MaterialTheme.typography
                .bodySmall
    )

    Spacer(
        modifier = Modifier.height(7.dp)
    )

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(buttonText)
    }
}

@Composable
private fun MonitorCard(
    monitoring: Boolean,
    finalizing: Boolean,
    sessionDurationMillis: Long,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (monitoring) {
                                Color(0xFF35A853)
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                                    .copy(alpha = 0.35f)
                            }
                        )
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text =
                            when {
                                finalizing ->
                                    "Messung wird abgeschlossen"

                                monitoring ->
                                    "Messung läuft"

                                else ->
                                    "Bereit für eine Messung"
                            },
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            "Erkennt Display-Aktivität, CPU-Wakeups und Hintergrundereignisse.",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography
                                .bodyMedium
                    )

                    if (monitoring) {
                        Spacer(
                            modifier =
                                Modifier.height(6.dp)
                        )

                        Text(
                            text =
                                "Sitzungsdauer · " +
                                    formatLiveSessionDuration(
                                        sessionDurationMillis
                                    ),
                            color =
                                MaterialTheme.colorScheme
                                    .primary,
                            style =
                                MaterialTheme.typography
                                    .bodyMedium,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStart,
                    enabled =
                        !monitoring &&
                            !finalizing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            MaterialTheme.colorScheme.primary,
                        contentColor =
                            MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor =
                            MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor =
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = "Start",
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onStop,
                    enabled =
                        monitoring &&
                            !finalizing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            MaterialTheme.colorScheme.error,
                        contentColor =
                            MaterialTheme.colorScheme.onError,
                        disabledContainerColor =
                            MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor =
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = "Stopp",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (monitoring) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text =
                        if (finalizing) {
                            "Der Timer ist angehalten. Letzte System- und Netzwerkdaten werden noch übernommen."
                        } else {
                            "Die Dauerbenachrichtigung hält den Diagnosemonitor zuverlässig aktiv."
                        },
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }
    }
}

private fun buildManualDiagnosticsExport(
    context: Context,
    wakeLockDiagnostic: WakeLockDiagnostic?,
    wakeupAlarmDiagnostic: WakeupAlarmDiagnostic?,
    backgroundJobDiagnostic: BackgroundJobDiagnostic?,
    wakeReasonDiagnostic: WakeReasonDiagnostic?,
    networkStatsDiagnostic: NetworkStatsDiagnostic?
): String {
    fun sourceFor(
        packageName: String?
    ): String {
        return resolveWakeLockSource(
            context = context,
            packageName = packageName
        )
    }

    fun errorText(
        error: String?
    ): String {
        return error
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Unbekannter Diagnosefehler"
    }

    return buildString {
        appendLine(
            "=================================================="
        )
        appendLine("Manuelle Shizuku-Systemdiagnose")
        appendLine(
            "Die folgenden Werte stammen aus manuell " +
                "ausgeführten Prüfungen."
        )

        appendLine()
        appendLine("Wakelocks")

        when {
            wakeLockDiagnostic == null -> {
                appendLine("Status: Nicht ausgeführt")
            }

            wakeLockDiagnostic.error != null -> {
                appendLine(
                    "Status: Prüfung fehlgeschlagen"
                )
                appendLine(
                    "Fehler: " +
                        errorText(
                            wakeLockDiagnostic.error
                        )
                )
            }

            else -> {
                appendLine(
                    "Aktuell aktive Wakelocks: " +
                        wakeLockDiagnostic.activeCount
                )

                if (
                    wakeLockDiagnostic.rawLastEntry == null
                ) {
                    appendLine(
                        "Letzter Partial-Wakelock: " +
                            "Keine Daten gefunden."
                    )
                } else {
                    appendLine(
                        "Letzter Partial-Wakelock:"
                    )
                    appendLine(
                        "• Quelle: " +
                            sourceFor(
                                wakeLockDiagnostic.lastPackage
                            )
                    )
                    appendLine(
                        "• Zeitpunkt: " +
                            formatWakeLockTimestamp(
                                wakeLockDiagnostic.lastTimestamp
                            )
                    )
                    appendLine(
                        "• Technischer Tag: " +
                            compactWakeLockTag(
                                wakeLockDiagnostic.lastTag
                            )
                    )
                }

                appendLine()
                appendLine(
                    "Wakelock-Verlauf:"
                )

                if (
                    wakeLockDiagnostic
                        .historyEntries
                        .isEmpty()
                ) {
                    appendLine(
                        "• Keine Daten gefunden."
                    )
                } else {
                    val groupedEntries =
                        wakeLockDiagnostic
                            .historyEntries
                            .groupBy { entry ->
                                entry.packageName
                                    .lowercase() +
                                    "|" +
                                    compactWakeLockTag(
                                        entry.tag
                                    ).lowercase() +
                                    "|" +
                                    entry.wakeLockType
                                        .lowercase()
                            }
                            .values
                            .map { group ->
                                group.sortedByDescending {
                                    it.startTimestampMillis
                                }
                            }
                            .sortedByDescending { group ->
                                group.firstOrNull()
                                    ?.startTimestampMillis
                                    ?: 0L
                            }
                            .take(10)

                    groupedEntries.forEach { group ->
                        val entry = group.first()

                        val finishedDurations =
                            group.mapNotNull {
                                it.durationMillis
                            }

                        val durationText =
                            when {
                                finishedDurations
                                    .isNotEmpty() &&
                                    (
                                        finishedDurations
                                            .maxOrNull()
                                            ?: 0L
                                    ) < 1_000L ->
                                    "kurze Aktivität"

                                finishedDurations
                                    .isNotEmpty() ->
                                    formatDuration(
                                        finishedDurations
                                            .maxOrNull()
                                    )

                                else ->
                                    "Ende nicht im Ausschnitt"
                            }

                        appendLine(
                            "• " +
                                sourceFor(
                                    entry.packageName
                                ) +
                                " · " +
                                entry.wakeLockType
                        )

                        appendLine(
                            "  Anzahl: " +
                                group.size +
                                " · Start: " +
                                formatWakeLockTimestamp(
                                    entry.startTimestamp
                                ) +
                                " · Dauer: " +
                                durationText
                        )

                        appendLine(
                            "  Wirkung: " +
                                if (
                                    group.any {
                                        it.causesWake
                                    }
                                ) {
                                    "kann das Display aufwecken"
                                } else {
                                    "keine direkte " +
                                        "Display-Aufweckwirkung erkannt"
                                }
                        )

                        appendLine(
                            "  Tag: " +
                                compactWakeLockTag(
                                    entry.tag
                                )
                        )
                    }
                }
            }
        }

        appendLine()
        appendLine("Wakeup-Alarme")

        when {
            wakeupAlarmDiagnostic == null -> {
                appendLine("Status: Nicht ausgeführt")
            }

            wakeupAlarmDiagnostic.error != null -> {
                appendLine(
                    "Status: Prüfung fehlgeschlagen"
                )
                appendLine(
                    "Fehler: " +
                        errorText(
                            wakeupAlarmDiagnostic.error
                        )
                )
            }

            wakeupAlarmDiagnostic.packageName == null -> {
                appendLine(
                    "Keine Daten gefunden."
                )
            }

            else -> {
                appendLine(
                    "Quelle: " +
                        sourceFor(
                            wakeupAlarmDiagnostic.packageName
                        )
                )
                appendLine(
                    "Paket: " +
                        wakeupAlarmDiagnostic.packageName
                )
                appendLine(
                    "Technischer Tag: " +
                        (
                            wakeupAlarmDiagnostic.tag
                                ?: "nicht verfügbar"
                        )
                )
                appendLine(
                    "Wakeup-Anzahl des Eintrags: " +
                        wakeupAlarmDiagnostic.wakeCount
                )
                appendLine(
                    "Wakeups des Pakets: " +
                        wakeupAlarmDiagnostic.packageWakeups
                )
            }
        }

        appendLine()
        appendLine("Hintergrundjobs")

        when {
            backgroundJobDiagnostic == null -> {
                appendLine("Status: Nicht ausgeführt")
            }

            backgroundJobDiagnostic.error != null -> {
                appendLine(
                    "Status: Prüfung fehlgeschlagen"
                )
                appendLine(
                    "Fehler: " +
                        errorText(
                            backgroundJobDiagnostic.error
                        )
                )
            }

            backgroundJobDiagnostic.packageName == null -> {
                appendLine(
                    "Keine Daten gefunden."
                )
            }

            else -> {
                appendLine(
                    "Quelle: " +
                        sourceFor(
                            backgroundJobDiagnostic.packageName
                        )
                )
                appendLine(
                    "Paket: " +
                        backgroundJobDiagnostic.packageName
                )

                if (
                    !backgroundJobDiagnostic
                        .rawEntry
                        .isNullOrBlank()
                ) {
                    appendLine(
                        "Technischer Eintrag: " +
                            backgroundJobDiagnostic
                                .rawEntry
                                ?.trim()
                    )
                }
            }
        }

        appendLine()
        appendLine("Aufweckgrund")

        when {
            wakeReasonDiagnostic == null -> {
                appendLine("Status: Nicht ausgeführt")
            }

            wakeReasonDiagnostic.error != null -> {
                appendLine(
                    "Status: Prüfung fehlgeschlagen"
                )
                appendLine(
                    "Fehler: " +
                        errorText(
                            wakeReasonDiagnostic.error
                        )
                )
            }

            wakeReasonDiagnostic.rawEntry == null -> {
                appendLine(
                    "Keine Daten gefunden."
                )
            }

            else -> {
                appendLine(
                    "Zeitpunkt: " +
                        formatWakeLockTimestamp(
                            wakeReasonDiagnostic.timestamp
                        )
                )
                appendLine(
                    "Technischer Grund: " +
                        (
                            wakeReasonDiagnostic.reason
                                ?: "unbekannt"
                        )
                )
                appendLine(
                    "Details: " +
                        compactWakeReasonDetails(
                            wakeReasonDiagnostic.details
                        )
                )
            }
        }

        appendLine()
        appendLine("Netzwerkaktivität seit Boot")

        when {
            networkStatsDiagnostic == null -> {
                appendLine("Status: Nicht ausgeführt")
            }

            networkStatsDiagnostic.error != null -> {
                appendLine(
                    "Status: Prüfung fehlgeschlagen"
                )
                appendLine(
                    "Fehler: " +
                        errorText(
                            networkStatsDiagnostic.error
                        )
                )
            }

            networkStatsDiagnostic.entries.isEmpty() -> {
                appendLine(
                    "Keine Daten gefunden."
                )
            }

            else -> {
                appendLine(
                    "Hinweis: Zeigt Datenverkehr seit dem Gerätestart, " +
                        "aber keinen direkten Wakeup-Auslöser."
                )

                networkStatsDiagnostic
                    .entries
                    .take(10)
                    .forEach { entry ->
                        val displayName =
                            sourceDisplayName(
                                appLabel =
                                    entry.appLabel,
                                packageName =
                                    entry.packageName,
                                uid = entry.uid
                            )

                        appendLine(
                            "• " +
                                displayName +
                                " · Gesamt: " +
                                formatNetworkBytes(
                                    entry.totalBytes
                                )
                        )

                        appendLine(
                            "  Empfangen: " +
                                formatNetworkBytes(
                                    entry.rxBytes
                                ) +
                                " · Gesendet: " +
                                formatNetworkBytes(
                                    entry.txBytes
                                )
                        )

                        appendLine(
                            "  Paket/UID: " +
                                (
                                    entry.packageName
                                        ?: "UID ${entry.uid}"
                                )
                        )
                    }
            }
        }

        appendLine()
        append(
            "=================================================="
        )
    }
}

@Composable
private fun ShizukuProfileHintCard(
    state: ShizukuState,
    detailLevel: DetailLevel,
    onRequestPermission: () -> Unit
) {
    val deviceProfile =
        androidx.compose.runtime.remember {
            DeviceProfile.detect()
        }

    val measurementQualityExpanded =
        remember {
            mutableStateOf(false)
        }

    val profileIdentity =
        (
            deviceProfile.profileLabel +
                " " +
                deviceProfile.platformLabel +
                " " +
                deviceProfile.manufacturer
        ).lowercase(
            Locale.getDefault()
        )

    val isSamsungProfile =
        profileIdentity.contains(
            "samsung"
        )

    val isOnePlusProfile =
        profileIdentity.contains(
            "oneplus"
        ) ||
            profileIdentity.contains(
                "oplus"
            ) ||
            profileIdentity.contains(
                "oxygenos"
            )

    val isSimple =
        detailLevel ==
            DetailLevel.SIMPLE

    val statusText =
        when (state) {
            ShizukuState.RUNNING_GRANTED ->
                "Shizuku aktiv"

            ShizukuState.RUNNING_DENIED ->
                "Shizuku-Berechtigung fehlt"

            ShizukuState.NOT_RUNNING ->
                "Shizuku nicht aktiv"
        }

    val description =
        when {
            isSimple &&
                state ==
                    ShizukuState.RUNNING_GRANTED ->
                "Erweiterte Systemanalyse verfügbar. Technische Prüfungen im Expertenmodus."

            isSimple ->
                "Die Systemanalyse ist derzeit eingeschränkt."

            state ==
                ShizukuState.RUNNING_GRANTED ->
                "Ursachenhinweise und erweiterte Systemdaten sind verfügbar."

            state ==
                ShizukuState.RUNNING_DENIED ->
                "Für Ursachenhinweise und erweiterte Systemdaten wird die Shizuku-Berechtigung benötigt."

            else ->
                "Für Ursachenhinweise und erweiterte Systemdaten muss Shizuku gestartet werden."
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme
                        .primaryContainer
                        .copy(alpha = 0.24f)
            ),
        border =
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color =
                    MaterialTheme.colorScheme
                        .primary
                        .copy(alpha = 0.35f)
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text =
                    if (isSimple) {
                        "Systemanalyse"
                    } else {
                        "Shizuku-Systemanalyse"
                    },
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme
                        .onPrimaryContainer
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = statusText,
                color =
                    when (state) {
                        ShizukuState.RUNNING_GRANTED ->
                            Color(0xFF35A853)

                        ShizukuState.RUNNING_DENIED ->
                            MaterialTheme.colorScheme.error

                        ShizukuState.NOT_RUNNING ->
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    },
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = description,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodyMedium
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            HorizontalDivider(
                color =
                    MaterialTheme.colorScheme
                        .outlineVariant
                        .copy(alpha = 0.65f)
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    deviceProfile.profileLabel,
                style =
                    MaterialTheme.typography
                        .titleSmall,
                fontWeight =
                    FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme
                        .primary
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    deviceProfile.manufacturer +
                        " " +
                        deviceProfile.model +
                        " · " +
                        deviceProfile.platformLabel,
                style =
                    MaterialTheme.typography
                        .bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(14.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                                .copy(alpha = 0.52f)
                    )
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            horizontal = 13.dp,
                            vertical = 11.dp
                        )
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                text =
                                    "Messqualität",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelMedium,
                                fontWeight =
                                    FontWeight.Bold,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(2.dp)
                            )

                            Text(
                                text =
                                    when {
                                        isOnePlusProfile ->
                                            "Für dieses Geräteprofil weitgehend gut"

                                        isSamsungProfile ->
                                            "Gut nutzbar, bei direkten Wake-Reasons eingeschränkt"

                                        else ->
                                            "Allgemeines Android-Profil mit geräteabhängiger Abdeckung"
                                    },
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        }

                        Text(
                            text =
                                if (
                                    measurementQualityExpanded
                                        .value
                                ) {
                                    "▲"
                                } else {
                                    "▼"
                                },
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(7.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            measurementQualityExpanded
                                .value =
                                !measurementQualityExpanded
                                    .value
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text =
                                if (
                                    measurementQualityExpanded
                                        .value
                                ) {
                                    "Messqualität ausblenden"
                                } else {
                                    "Messqualität anzeigen"
                                }
                        )
                    }

                    if (
                        measurementQualityExpanded
                            .value
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )

                        HorizontalDivider()

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )

                        MeasurementQualityRow(
                            label =
                                "Display-Aktivität",
                            quality =
                                "gut"
                        )

                        MeasurementQualityRow(
                            label =
                                "Warum das Display aufwachte",
                            quality =
                                when {
                                    isOnePlusProfile ->
                                        "gut"

                                    isSamsungProfile ->
                                        "eingeschränkt"

                                    else ->
                                        "geräteabhängig"
                                }
                        )

                        MeasurementQualityRow(
                            label =
                                "Hintergrundaktivität",
                            quality =
                                when {
                                    isOnePlusProfile ->
                                        "gut"

                                    isSamsungProfile ->
                                        "eingeschränkt"

                                    else ->
                                        "geräteabhängig"
                                }
                        )

                        MeasurementQualityRow(
                            label =
                                "Netzwerkaktivität",
                            quality =
                                "gut"
                        )

                        MeasurementQualityRow(
                            label =
                                "Vergleich mehrerer Messungen",
                            quality =
                                "gut"
                        )


                    }
                }
            }


            if (
                state ==
                    ShizukuState.RUNNING_DENIED
            ) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                OutlinedButton(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Berechtigung erteilen")
                }
            }
        }
    }
}

@Composable
private fun MeasurementQualityRow(
    label: String,
    quality: String
) {
    val qualityColor =
        when (quality.lowercase(
            Locale.getDefault()
        )) {
            "gut" ->
                Color(0xFF35A853)

            "eingeschränkt" ->
                MaterialTheme
                    .colorScheme
                    .error

            else ->
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier =
                Modifier.weight(1f),
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            modifier =
                Modifier.width(12.dp)
        )

        Text(
            text = quality,
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            fontWeight =
                FontWeight.SemiBold,
            color =
                qualityColor,
            textAlign =
                TextAlign.End
        )
    }
}

@Composable
private fun ShizukuWakeLockCard(
    state: ShizukuState,
    diagnostic: WakeLockDiagnostic?,
    loading: Boolean,
    alarmDiagnostic: WakeupAlarmDiagnostic?,
    alarmLoading: Boolean,
    jobDiagnostic: BackgroundJobDiagnostic?,
    jobLoading: Boolean,
    wakeReasonDiagnostic: WakeReasonDiagnostic?,
    wakeReasonLoading: Boolean,
    networkStatsDiagnostic: NetworkStatsDiagnostic?,
    networkStatsLoading: Boolean,
    onRequestPermission: () -> Unit,
    onCheck: () -> Unit,
    onAlarmCheck: () -> Unit,
    onJobCheck: () -> Unit,
    onWakeReasonCheck: () -> Unit,
    onNetworkStatsCheck: () -> Unit
) {
    val diagnosticsExpanded = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    val visibleDiagnosticPanel =
        androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<String?>(null)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Shizuku-Systemdiagnose",
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = when (state) {
                    ShizukuState.RUNNING_GRANTED ->
                        "Bereit · Systemdiagnose verfügbar"

                    ShizukuState.RUNNING_DENIED ->
                        "Shizuku läuft · Berechtigung fehlt"

                    ShizukuState.NOT_RUNNING ->
                        "Shizuku läuft nicht"
                },
                color = when (state) {
                    ShizukuState.RUNNING_GRANTED ->
                        Color(0xFF35A853)

                    ShizukuState.RUNNING_DENIED ->
                        MaterialTheme.colorScheme.error

                    ShizukuState.NOT_RUNNING ->
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            when (state) {
                ShizukuState.RUNNING_GRANTED -> {
                    Button(
                        onClick = {
                            diagnosticsExpanded.value =
                                !diagnosticsExpanded.value

                            if (!diagnosticsExpanded.value) {
                                visibleDiagnosticPanel.value =
                                    null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    MaterialTheme.colorScheme
                                        .primary,
                                contentColor =
                                    MaterialTheme.colorScheme
                                        .onPrimary
                            )
                    ) {
                        Text(
                            if (diagnosticsExpanded.value) {
                                "Diagnose ausblenden"
                            } else {
                                "Diagnose öffnen"
                            },
                            fontWeight =
                                FontWeight.SemiBold
                        )
                    }

                    if (diagnosticsExpanded.value) {
                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value ==
                                    "wakelocks"
                                ) {
                                    visibleDiagnosticPanel.value =
                                        null
                                } else {
                                    visibleDiagnosticPanel.value =
                                        "wakelocks"
                                    onCheck()
                                }
                            },
                            enabled =
                                !loading &&
                                    !alarmLoading &&
                                    !jobLoading &&
                                    !wakeReasonLoading &&
                                    !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (visibleDiagnosticPanel.value == "wakelocks") {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.ui.graphics.Color(
                                            0xFF687181
                                        )
                                    }
                                ),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor =
                                        if (visibleDiagnosticPanel.value == "wakelocks") {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            androidx.compose.ui.graphics.Color
                                                .Transparent
                                        },
                                    contentColor =
                                        if (visibleDiagnosticPanel.value == "wakelocks") {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            androidx.compose.ui.graphics.Color.White
                                        },
                                    disabledContainerColor =
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.45f)
                                )
                        ) {
                            Text(
                                when {
                                    loading ->
                                        "Prüfe …"

                                    visibleDiagnosticPanel.value ==
                                        "wakelocks" ->
                                        "Wakelocks ausblenden"

                                    else ->
                                        "Wakelocks prüfen"
                                }
                            )
                        }

            if (
                diagnostic != null &&
                visibleDiagnosticPanel.value == "wakelocks"
            ) {
                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                if (diagnostic.error != null) {
                    Text(
                        text = diagnostic.error,
                        color =
                            MaterialTheme.colorScheme.error,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text =
                            "Aktuell aktive Wakelocks: " +
                                diagnostic.activeCount,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    if (
                        diagnostic.rawLastEntry == null
                    ) {
                        Text(
                            text =
                                "Keine Daten gefunden.",
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    } else {
                        val context =
                            LocalContext.current

                        val source =
                            resolveWakeLockSource(
                                context = context,
                                packageName =
                                    diagnostic.lastPackage
                            )

                        val kind =
                            classifyWakeLockTag(
                                diagnostic.lastTag
                            )

                        val timestamp =
                            formatWakeLockTimestamp(
                                diagnostic.lastTimestamp
                            )

                        Text(
                            text = "Letzter Partial-Wakelock",
                            fontWeight = FontWeight.SemiBold,
                            style =
                                MaterialTheme.typography.bodySmall
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        Text(
                            text = "Quelle: $source",
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = "Art: $kind",
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = "Zeitpunkt: $timestamp",
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text =
                                "Technischer Tag: " +
                                    compactWakeLockTag(
                                        diagnostic.lastTag
                                    ),
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (
                diagnostic != null &&
                diagnostic.error == null &&
                diagnostic.historyEntries.isNotEmpty() &&
                visibleDiagnosticPanel.value == "wakelocks"
            ) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Text(
                    text = "Wakelock-Verlauf (letzte Logeinträge)",
                    fontWeight = FontWeight.SemiBold,
                    style =
                        MaterialTheme.typography.bodySmall
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                val groupedHistoryEntries =
                    diagnostic.historyEntries
                        .groupBy { entry ->
                            entry.packageName.lowercase() +
                                "|" +
                                compactWakeLockTag(
                                    entry.tag
                                ).lowercase() +
                                "|" +
                                entry.wakeLockType.lowercase()
                        }
                        .values
                        .map { group ->
                            group.sortedByDescending {
                                it.startTimestampMillis
                            }
                        }
                        .sortedByDescending { group ->
                            group.firstOrNull()
                                ?.startTimestampMillis
                                ?: 0L
                        }
                        .take(5)

                groupedHistoryEntries
                    .forEachIndexed { index, group ->
                        val entry =
                            group.first()

                        val count =
                            group.size

                        val context =
                            LocalContext.current

                        val source =
                            resolveWakeLockSource(
                                context = context,
                                packageName =
                                    entry.packageName
                            )

                        val hasFinishedEntry =
                            group.any {
                                !it.stillActive
                            }

                        val longestDuration =
                            group.mapNotNull {
                                it.durationMillis
                            }.maxOrNull()

                        val durationText =
                            when {
                                hasFinishedEntry &&
                                    (longestDuration ?: 0L) < 1_000L ->
                                    "kurze Aktivität"

                                hasFinishedEntry ->
                                    formatDuration(
                                        longestDuration
                                    )

                                else ->
                                    "Ende nicht im Ausschnitt"
                            }

                        val countText =
                            if (count > 1) {
                                "${count}× · "
                            } else {
                                ""
                            }

                        Text(
                            text =
                                source +
                                    " · " +
                                    entry.wakeLockType,
                            fontWeight =
                                FontWeight.SemiBold,
                            style =
                                MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text =
                                countText +
                                    "Start: " +
                                    formatWakeLockTimestamp(
                                        entry.startTimestamp
                                    ) +
                                    " · " +
                                    durationText,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text =
                                (
                                    if (
                                        group.any {
                                            it.causesWake
                                        }
                                    ) {
                                        "Display-Aufweckwirkung · "
                                    } else {
                                        ""
                                    }
                                ) +
                                    "Tag: " +
                                    compactWakeLockTag(
                                        entry.tag
                                    ),
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (
                            index <
                            groupedHistoryEntries.lastIndex
                        ) {
                            Spacer(
                                modifier =
                                    Modifier.height(8.dp)
                            )
                        }
                    }
            }


                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value ==
                                    "alarms"
                                ) {
                                    visibleDiagnosticPanel.value =
                                        null
                                } else {
                                    visibleDiagnosticPanel.value =
                                        "alarms"
                                    onAlarmCheck()
                                }
                            },
                            enabled =
                                !loading &&
                                    !alarmLoading &&
                                    !jobLoading &&
                                    !wakeReasonLoading &&
                                    !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (visibleDiagnosticPanel.value == "alarms") {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.ui.graphics.Color(
                                            0xFF687181
                                        )
                                    }
                                ),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor =
                                        if (visibleDiagnosticPanel.value == "alarms") {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            androidx.compose.ui.graphics.Color
                                                .Transparent
                                        },
                                    contentColor =
                                        if (visibleDiagnosticPanel.value == "alarms") {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            androidx.compose.ui.graphics.Color.White
                                        },
                                    disabledContainerColor =
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.45f)
                                )
                        ) {
                            Text(
                                when {
                                    alarmLoading ->
                                        "Prüfe Wakeup-Alarme …"

                                    visibleDiagnosticPanel.value ==
                                        "alarms" ->
                                        "Wakeup-Alarme ausblenden"

                                    else ->
                                        "Wakeup-Alarme prüfen"
                                }
                            )
                        }

            if (
                alarmDiagnostic != null &&
                visibleDiagnosticPanel.value == "alarms"
            ) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                if (alarmDiagnostic.error != null) {
                    Text(
                        text =
                            alarmDiagnostic.error,
                        color =
                            MaterialTheme.colorScheme.error,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                } else if (
                    alarmDiagnostic.packageName == null
                ) {
                    Text(
                        text =
                            "Keine Daten gefunden.",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                } else {
                    val context =
                        LocalContext.current

                    val source =
                        resolveWakeLockSource(
                            context = context,
                            packageName =
                                alarmDiagnostic.packageName
                        )

                    Text(
                        text =
                            "Letzter Wakeup-Alarm",
                        fontWeight =
                            FontWeight.SemiBold,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = "Quelle: $source",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text =
                            "Zuletzt: vor " +
                                formatDuration(
                                    alarmDiagnostic.ageMillis
                                ),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text =
                            "Seit Statistikstart: " +
                                alarmDiagnostic.wakeCount +
                                " Wakeups",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text =
                            "Technischer Tag: " +
                                compactAlarmTag(
                                    alarmDiagnostic.tag
                                ),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }


                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value ==
                                    "jobs"
                                ) {
                                    visibleDiagnosticPanel.value =
                                        null
                                } else {
                                    visibleDiagnosticPanel.value =
                                        "jobs"
                                    onJobCheck()
                                }
                            },
                            enabled =
                                !loading &&
                                    !alarmLoading &&
                                    !jobLoading &&
                                    !wakeReasonLoading &&
                                    !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (visibleDiagnosticPanel.value == "jobs") {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.ui.graphics.Color(
                                            0xFF687181
                                        )
                                    }
                                ),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor =
                                        if (visibleDiagnosticPanel.value == "jobs") {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            androidx.compose.ui.graphics.Color
                                                .Transparent
                                        },
                                    contentColor =
                                        if (visibleDiagnosticPanel.value == "jobs") {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            androidx.compose.ui.graphics.Color.White
                                        },
                                    disabledContainerColor =
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.45f)
                                )
                        ) {
                            Text(
                                when {
                                    jobLoading ->
                                        "Prüfe Hintergrundjobs …"

                                    visibleDiagnosticPanel.value ==
                                        "jobs" ->
                                        "Hintergrundjobs ausblenden"

                                    else ->
                                        "Hintergrundjobs prüfen"
                                }
                            )
                        }

            if (
                jobDiagnostic != null &&
                visibleDiagnosticPanel.value == "jobs"
            ) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                if (jobDiagnostic.error != null) {
                    Text(
                        text = jobDiagnostic.error,
                        color =
                            MaterialTheme.colorScheme.error,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                } else if (
                    jobDiagnostic.packageName == null
                ) {
                    Text(
                        text =
                            "Keine Daten gefunden.",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                } else {
                    val context =
                        LocalContext.current

                    val source =
                        resolveWakeLockSource(
                            context = context,
                            packageName =
                                jobDiagnostic.packageName
                        )

                    Text(
                        text =
                            "Letzter gestarteter Hintergrundjob",
                        fontWeight =
                            FontWeight.SemiBold,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = "Quelle: $source",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text =
                            "Gestartet: vor " +
                                formatDuration(
                                    jobDiagnostic.ageMillis
                                ),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text =
                            "Starttyp: " +
                                if (
                                    jobDiagnostic.prioritized
                                ) {
                                    "priorisiert"
                                } else {
                                    "regulär"
                                },
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text =
                            "Dienst: " +
                                compactJobService(
                                    jobDiagnostic.serviceName
                                ),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }


                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value ==
                                    "wake_reason"
                                ) {
                                    visibleDiagnosticPanel.value =
                                        null
                                } else {
                                    visibleDiagnosticPanel.value =
                                        "wake_reason"
                                    onWakeReasonCheck()
                                }
                            },
                            enabled =
                                !loading &&
                                    !alarmLoading &&
                                    !jobLoading &&
                                    !wakeReasonLoading &&
                                    !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (visibleDiagnosticPanel.value == "wake_reason") {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.ui.graphics.Color(
                                            0xFF687181
                                        )
                                    }
                                ),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor =
                                        if (visibleDiagnosticPanel.value == "wake_reason") {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            androidx.compose.ui.graphics.Color
                                                .Transparent
                                        },
                                    contentColor =
                                        if (visibleDiagnosticPanel.value == "wake_reason") {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            androidx.compose.ui.graphics.Color.White
                                        },
                                    disabledContainerColor =
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.45f)
                                )
                        ) {
                            Text(
                                when {
                                    wakeReasonLoading ->
                                        "Prüfe Aufweckgrund …"

                                    visibleDiagnosticPanel.value ==
                                        "wake_reason" ->
                                        "Aufweckgrund ausblenden"

                                    else ->
                                        "Aufweckgrund prüfen"
                                }
                            )
                        }

            if (
                wakeReasonDiagnostic != null &&
                visibleDiagnosticPanel.value == "wake_reason"
            ) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                if (
                    wakeReasonDiagnostic.error != null
                ) {
                    Text(
                        text =
                            wakeReasonDiagnostic.error,
                        color =
                            MaterialTheme.colorScheme.error,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                } else if (
                    wakeReasonDiagnostic.rawEntry == null
                ) {
                    Text(
                        text =
                            "Keine Daten gefunden.",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text =
                            "Letzter direkter Aufweckgrund",
                        fontWeight =
                            FontWeight.SemiBold,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            "Ursache: " +
                                readableWakeReason(
                                    wakeReasonDiagnostic
                                ),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text =
                            "Zeitpunkt: " +
                                formatWakeLockTimestamp(
                                    wakeReasonDiagnostic
                                        .timestamp
                                ),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text =
                            "Technischer Grund: " +
                                (
                                    wakeReasonDiagnostic
                                        .reason
                                        ?: "unbekannt"
                                ),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text =
                            "Details: " +
                                compactWakeReasonDetails(
                                    wakeReasonDiagnostic
                                        .details
                                ),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }


                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value ==
                                    "network"
                                ) {
                                    visibleDiagnosticPanel.value =
                                        null
                                } else {
                                    visibleDiagnosticPanel.value =
                                        "network"
                                    onNetworkStatsCheck()
                                }
                            },
                            enabled =
                                !loading &&
                                    !alarmLoading &&
                                    !jobLoading &&
                                    !wakeReasonLoading &&
                                    !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (visibleDiagnosticPanel.value == "network") {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.ui.graphics.Color(
                                            0xFF687181
                                        )
                                    }
                                ),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor =
                                        if (visibleDiagnosticPanel.value == "network") {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            androidx.compose.ui.graphics.Color
                                                .Transparent
                                        },
                                    contentColor =
                                        if (visibleDiagnosticPanel.value == "network") {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            androidx.compose.ui.graphics.Color.White
                                        },
                                    disabledContainerColor =
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.45f)
                                )
                        ) {
                            Text(
                                when {
                                    networkStatsLoading ->
                                        "Prüfe Netzwerkaktivität …"

                                    visibleDiagnosticPanel.value ==
                                        "network" ->
                                        "Netzwerkaktivität ausblenden"

                                    else ->
                                        "Netzwerkaktivität prüfen"
                                }
                            )
                        }

            if (
                networkStatsDiagnostic != null &&
                visibleDiagnosticPanel.value == "network"
            ) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                if (networkStatsDiagnostic.error != null) {
                    Text(
                        text =
                            networkStatsDiagnostic.error,
                        color =
                            MaterialTheme.colorScheme.error,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                } else if (
                    networkStatsDiagnostic.entries.isEmpty()
                ) {
                    Text(
                        text =
                            "Keine Daten gefunden.",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text =
                            "Netzwerkaktivität seit Boot",
                        fontWeight =
                            FontWeight.SemiBold,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            "Hinweis: Zeigt Datenverkehr seit dem Gerätestart, " +
                                "aber keinen direkten Wakeup-Auslöser.",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    networkStatsDiagnostic.entries
                        .take(5)
                        .forEachIndexed { index, entry ->
                            val context =
                                LocalContext.current

                            val source =
                                entry.appLabel
                                    ?: if (
                                        entry.packageName != null
                                    ) {
                                        resolveWakeLockSource(
                                            context = context,
                                            packageName =
                                                entry.packageName
                                        )
                                    } else {
                                        sourceDisplayName(
                                            "UID " +
                                                entry.uid
                                        )
                                    }

                            Text(
                                text =
                                    source +
                                        " · " +
                                        formatNetworkBytes(
                                            entry.totalBytes
                                        ),
                                fontWeight =
                                    FontWeight.SemiBold,
                                style =
                                    MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text =
                                    "Empfangen: " +
                                        formatNetworkBytes(
                                            entry.rxBytes
                                        ) +
                                        " · Gesendet: " +
                                        formatNetworkBytes(
                                            entry.txBytes
                                        ),
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                                style =
                                    MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text =
                                    (
                                        entry.packageName
                                            ?: "UID " + entry.uid
                                    ),
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                                style =
                                    MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (
                                index <
                                networkStatsDiagnostic.entries
                                    .take(5)
                                    .lastIndex
                            ) {
                                Spacer(
                                    modifier =
                                        Modifier.height(8.dp)
                                )
                            }
                        }
                }
            }

                    }
                }

                ShizukuState.RUNNING_DENIED -> {
                    OutlinedButton(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Shizuku erlauben")
                    }
                }

                ShizukuState.NOT_RUNNING -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Shizuku starten")
                    }
                }
            }
        }
    }
}

private data class SessionComparisonData(
    val latest: ArchivedSession,
    val previous: ArchivedSession,
    val summary: String
)

@Composable
private fun SessionComparisonCard(
    events: List<WakeEvent>,
    detailLevel: DetailLevel
) {
    val context =
        LocalContext.current

    val comparison =
        remember(events) {
            val sessions =
                SessionArchiveStore
                    .getSessions(context)
                    .sortedByDescending {
                        it.startMillis
                    }

            if (sessions.size < 2) {
                null
            } else {
                val latest =
                    sessions[0]

                val previous =
                    sessions[1]

                SessionComparisonData(
                    latest = latest,
                    previous = previous,
                    summary =
                        buildSessionComparisonSummary(
                            latest = latest,
                            previous = previous
                        )
                )
            }
        }

    if (comparison == null) {
        return
    }

    val latest =
        comparison.latest

    val previous =
        comparison.previous

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Sitzungsvergleich",
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text =
                    formatComparisonSessionTime(
                        latest.startMillis
                    ) +
                        " gegenüber " +
                        formatComparisonSessionTime(
                            previous.startMillis
                        ),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme
                            .primaryContainer
                            .copy(alpha = 0.48f)
                )
            ) {
                Text(
                    text = comparison.summary,
                    modifier =
                        Modifier.padding(14.dp),
                    color =
                        MaterialTheme.colorScheme
                            .onPrimaryContainer,
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            SessionComparisonHeader()

            HorizontalDivider()

            SessionComparisonRow(
                label = "Dauer",
                latest =
                    formatComparisonDuration(
                        latest.durationMillis
                    ),
                previous =
                    formatComparisonDuration(
                        previous.durationMillis
                    ),
                change =
                    formatDurationChange(
                        latest.durationMillis -
                            previous.durationMillis
                    ),
                changeValue =
                    (
                        latest.durationMillis -
                            previous.durationMillis
                    ).toDouble()
            )

            SessionComparisonRow(
                label = "Display an",
                latest =
                    latest.displayWakeups
                        .toString(),
                previous =
                    previous.displayWakeups
                        .toString(),
                change =
                    formatCountChange(
                        latest.displayWakeups -
                            previous.displayWakeups
                    ),
                changeValue =
                    (
                        latest.displayWakeups -
                            previous.displayWakeups
                    ).toDouble(),
                meaning =
                    ComparisonChangeMeaning
                        .LOWER_IS_BETTER
            )

            SessionComparisonRow(
                label = "CPU-Wakes",
                latest =
                    latest.cpuWakeups
                        .toString(),
                previous =
                    previous.cpuWakeups
                        .toString(),
                change =
                    formatCountChange(
                        latest.cpuWakeups -
                            previous.cpuWakeups
                    ),
                changeValue =
                    (
                        latest.cpuWakeups -
                            previous.cpuWakeups
                    ).toDouble(),
                meaning =
                    ComparisonChangeMeaning
                        .LOWER_IS_BETTER
            )

            SessionComparisonRow(
                label = "Netzwerk",
                latest =
                    formatNetworkBytes(
                        latest.networkTotalBytes
                    ),
                previous =
                    formatNetworkBytes(
                        previous.networkTotalBytes
                    ),
                change =
                    formatNetworkChange(
                        latest.networkTotalBytes -
                            previous.networkTotalBytes
                    ),
                changeValue =
                    (
                        latest.networkTotalBytes -
                            previous.networkTotalBytes
                    ).toDouble()
            )

            if (
                detailLevel !=
                DetailLevel.SIMPLE
            ) {
                val latestNetworkPerMinute =
                    networkBytesPerMinute(
                        bytes =
                            latest.networkTotalBytes,
                        durationMillis =
                            latest.durationMillis
                    )

                val previousNetworkPerMinute =
                    networkBytesPerMinute(
                        bytes =
                            previous.networkTotalBytes,
                        durationMillis =
                            previous.durationMillis
                    )

                SessionComparisonRow(
                    label = "Netzwerk / Min",
                    latest =
                        formatNetworkBytes(
                            latestNetworkPerMinute
                        ),
                    previous =
                        formatNetworkBytes(
                            previousNetworkPerMinute
                        ),
                    change =
                        formatNetworkChange(
                            latestNetworkPerMinute -
                                previousNetworkPerMinute
                        ),
                    changeValue =
                        (
                            latestNetworkPerMinute -
                                previousNetworkPerMinute
                        ).toDouble()
                )
                SessionComparisonRow(
                    label = "Aktive Apps",
                    latest =
                        latest.networkActiveApps
                            .toString(),
                    previous =
                        previous.networkActiveApps
                            .toString(),
                    change =
                        formatCountChange(
                            latest.networkActiveApps -
                                previous.networkActiveApps
                        ),
                    changeValue =
                        (
                            latest.networkActiveApps -
                                previous.networkActiveApps
                        ).toDouble()
                )
            }

            if (
                detailLevel ==
                DetailLevel.EXPERT
            ) {
                SessionComparisonRow(
                    label = "Empfangen",
                    latest =
                        formatNetworkBytes(
                            latest.networkRxBytes
                        ),
                    previous =
                        formatNetworkBytes(
                            previous.networkRxBytes
                        ),
                    change =
                        formatNetworkChange(
                            latest.networkRxBytes -
                                previous.networkRxBytes
                        ),
                    changeValue =
                        (
                            latest.networkRxBytes -
                                previous.networkRxBytes
                        ).toDouble()
                )

                SessionComparisonRow(
                    label = "Gesendet",
                    latest =
                        formatNetworkBytes(
                            latest.networkTxBytes
                        ),
                    previous =
                        formatNetworkBytes(
                            previous.networkTxBytes
                        ),
                    change =
                        formatNetworkChange(
                            latest.networkTxBytes -
                                previous.networkTxBytes
                        ),
                    changeValue =
                        (
                            latest.networkTxBytes -
                                previous.networkTxBytes
                        ).toDouble()
                )
            }

            val latestTopApp =
                latest.topApps.firstOrNull()

            val previousTopApp =
                previous.topApps.firstOrNull()

            if (
                latestTopApp != null ||
                previousTopApp != null
            ) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "Aktivste Apps",
                    style =
                        MaterialTheme.typography
                            .titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                SessionTopAppRow(
                    label = "Letzte Sitzung",
                    app = latestTopApp
                )

                SessionTopAppRow(
                    label = "Vorherige Sitzung",
                    app = previousTopApp
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text =
                    "Die Sitzungen werden anhand technischer Aktivität verglichen. Unterschiedliche Nutzung und Laufzeiten beeinflussen die Werte.",
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .labelSmall
            )
        }
    }
}

@Composable
private fun SessionComparisonHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                bottom = 6.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = "Kennzahl",
            modifier = Modifier.weight(1.25f),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .labelSmall
        )

        Text(
            text = "Jetzt",
            modifier = Modifier.weight(0.8f),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .labelSmall,
            textAlign = TextAlign.End
        )

        Text(
            text = "Vorher",
            modifier = Modifier.weight(0.8f),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .labelSmall,
            textAlign = TextAlign.End
        )

        Text(
            text = "Änderung",
            modifier = Modifier.weight(0.9f),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .labelSmall,
            textAlign = TextAlign.End
        )
    }
}

private enum class ComparisonChangeMeaning {
    LOWER_IS_BETTER,
    NEUTRAL
}

@Composable
private fun SessionComparisonRow(
    label: String,
    latest: String,
    previous: String,
    change: String,
    changeValue: Double = 0.0,
    meaning: ComparisonChangeMeaning =
        ComparisonChangeMeaning.NEUTRAL
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1.25f),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .bodySmall
        )

        Text(
            text = latest,
            modifier = Modifier.weight(0.8f),
            color =
                MaterialTheme.colorScheme
                    .primary,
            style =
                MaterialTheme.typography
                    .bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = previous,
            modifier = Modifier.weight(0.8f),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .bodySmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val changeColor =
            when {
                changeValue == 0.0 ->
                    MaterialTheme.colorScheme
                        .onSurfaceVariant

                meaning ==
                    ComparisonChangeMeaning
                        .LOWER_IS_BETTER &&
                    changeValue < 0.0 ->
                    MaterialTheme.colorScheme
                        .primary

                meaning ==
                    ComparisonChangeMeaning
                        .LOWER_IS_BETTER &&
                    changeValue > 0.0 ->
                    MaterialTheme.colorScheme
                        .error

                else ->
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            }

        Text(
            text = change,
            modifier = Modifier.weight(0.9f),
            color = changeColor,
            style =
                MaterialTheme.typography
                    .bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    HorizontalDivider(
        color =
            MaterialTheme.colorScheme
                .outlineVariant
                .copy(alpha = 0.45f)
    )
}

@Composable
private fun SessionTopAppRow(
    label: String,
    app: ArchivedSessionApp?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .labelMedium
        )

        Spacer(
            modifier = Modifier.height(3.dp)
        )

        if (app == null) {
            Text(
                text = "Keine Daten",
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        } else {
            Text(
                text =
                    sourceDisplayName(
                        app.name
                    ),
                modifier = Modifier.fillMaxWidth(),
                color =
                    MaterialTheme.colorScheme
                        .onSurface,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            Text(
                text =
                    formatNetworkBytes(
                        app.totalBytes
                    ),
                modifier = Modifier.fillMaxWidth(),
                color =
                    MaterialTheme.colorScheme
                        .primary,
                style =
                    MaterialTheme.typography
                        .bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }
    }
}

private fun buildSessionComparisonSummary(
    latest: ArchivedSession,
    previous: ArchivedSession
): String {
    val displayDelta =
        latest.displayWakeups -
            previous.displayWakeups

    val cpuDelta =
        latest.cpuWakeups -
            previous.cpuWakeups

    val latestDurationHours =
        latest.durationMillis
            .coerceAtLeast(1L) /
            3_600_000.0

    val previousDurationHours =
        previous.durationMillis
            .coerceAtLeast(1L) /
            3_600_000.0

    val latestWakeRate =
        (
            latest.displayWakeups +
                latest.cpuWakeups
        ) / latestDurationHours

    val previousWakeRate =
        (
            previous.displayWakeups +
                previous.cpuWakeups
        ) / previousDurationHours

    return when {
        latest.displayWakeups == 0 &&
            latest.cpuWakeups == 0 &&
            previous.displayWakeups == 0 &&
            previous.cpuWakeups == 0 ->
            "Beide Sitzungen verliefen ohne erfasste Display- oder CPU-Wakeups."

        latestWakeRate <
            previousWakeRate * 0.75 ->
            "Die letzte Sitzung war technisch deutlich ruhiger als die vorherige."

        latestWakeRate >
            previousWakeRate * 1.25 ->
            "Die letzte Sitzung enthielt mehr technische Unterbrechungen als die vorherige."

        displayDelta < 0 ||
            cpuDelta < 0 ->
            "Die letzte Sitzung war etwas ruhiger als die vorherige."

        displayDelta > 0 ||
            cpuDelta > 0 ->
            "Die letzte Sitzung zeigte etwas mehr Aktivität als die vorherige."

        else ->
            "Beide Sitzungen zeigten einen ähnlichen technischen Verlauf."
    }
}

private fun formatComparisonSessionTime(
    timestamp: Long
): String {
    return SimpleDateFormat(
        "dd.MM. · HH:mm",
        Locale.getDefault()
    ).format(
        Date(timestamp)
    )
}

private fun formatComparisonDuration(
    millis: Long
): String {
    val totalSeconds =
        millis.coerceAtLeast(0L) /
            1_000L

    val minutes =
        totalSeconds / 60L

    val seconds =
        totalSeconds % 60L

    return if (minutes > 0L) {
        "${minutes}:${seconds.toString().padStart(2, '0')} min"
    } else {
        "${seconds} s"
    }
}

private fun formatDurationChange(
    deltaMillis: Long
): String {
    if (
        kotlin.math.abs(deltaMillis) <
        1_000L
    ) {
        return "gleich"
    }

    val prefix =
        if (deltaMillis > 0L) {
            "+"
        } else {
            "−"
        }

    return prefix +
        formatComparisonDuration(
            kotlin.math.abs(deltaMillis)
        )
}

private fun formatCountChange(
    delta: Int
): String {
    return when {
        delta > 0 ->
            "+$delta"

        delta < 0 ->
            "−${kotlin.math.abs(delta)}"

        else ->
            "gleich"
    }
}

private fun networkBytesPerMinute(
    bytes: Long,
    durationMillis: Long
): Long {
    if (
        bytes <= 0L ||
        durationMillis <= 0L
    ) {
        return 0L
    }

    val minutes =
        durationMillis / 60_000.0

    if (
        minutes <= 0.0 ||
        !minutes.isFinite()
    ) {
        return 0L
    }

    val result =
        bytes / minutes

    if (
        !result.isFinite() ||
        result <= 0.0 ||
        result >
            Long.MAX_VALUE.toDouble()
    ) {
        return 0L
    }

    return result.toLong()
}

private fun formatNetworkChange(
    deltaBytes: Long
): String {
    return when {
        deltaBytes > 0L ->
            "+" +
                formatNetworkBytes(
                    deltaBytes
                )

        deltaBytes < 0L ->
            "−" +
                formatNetworkBytes(
                    kotlin.math.abs(
                        deltaBytes
                    )
                )

        else ->
            "gleich"
    }
}

@Composable
private fun SessionHistoryCard(
    events: List<WakeEvent>,
    detailLevel: DetailLevel
) {
    val context =
        LocalContext.current

    val archiveRefresh =
        remember {
            androidx.compose.runtime
                .mutableIntStateOf(0)
        }

    val showDeleteAllDialog =
        remember {
            androidx.compose.runtime
                .mutableStateOf(false)
        }

    val sessions =
        remember(
            events,
            archiveRefresh.intValue
        ) {
            SessionArchiveStore
                .getSessions(context)
                .sortedByDescending {
                    it.startMillis
                }
        }

    if (sessions.isEmpty()) {
        return
    }

    val expanded =
        remember {
            androidx.compose.runtime
                .mutableStateOf(false)
        }

    val visibleSessions =
        if (expanded.value) {
            sessions
        } else {
            sessions.take(3)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Sitzungsverlauf",
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text =
                    sessions.size.toString() +
                        if (sessions.size == 1) {
                            " Sitzung gespeichert"
                        } else {
                            " Sitzungen gespeichert"
                        },
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            visibleSessions.forEachIndexed {
                    index,
                    session ->

                SessionHistoryEntry(
                    session = session,
                    detailLevel = detailLevel,
                    onSaveNote = { note ->
                        if (
                            SessionArchiveStore
                                .updateNote(
                                    context = context,
                                    sessionId =
                                        session.id,
                                    note = note
                                )
                        ) {
                            archiveRefresh
                                .intValue++
                        }
                    },
                    onDelete = {
                        if (
                            SessionArchiveStore
                                .deleteSession(
                                    context = context,
                                    sessionId =
                                        session.id
                                )
                        ) {
                            archiveRefresh
                                .intValue++
                        }
                    }
                )

                if (
                    index <
                    visibleSessions.lastIndex
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    HorizontalDivider()

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )
                }
            }

            if (sessions.size > 3) {
                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                OutlinedButton(
                    onClick = {
                        expanded.value =
                            !expanded.value
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    border =
                        androidx.compose.foundation
                            .BorderStroke(
                                1.dp,
                                androidx.compose.ui
                                    .graphics.Color(
                                        0xFF687181
                                    )
                            ),
                    colors =
                        ButtonDefaults
                            .outlinedButtonColors(
                                contentColor =
                                    androidx.compose.ui
                                        .graphics.Color
                                        .White
                            )
                ) {
                    Text(
                        text =
                            if (expanded.value) {
                                "Weniger Sitzungen anzeigen"
                            } else {
                                "Alle Sitzungen anzeigen"
                            }
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            androidx.compose.material3
                .OutlinedButton(
                    onClick = {
                        showDeleteAllDialog.value =
                            true
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    border =
                        androidx.compose.foundation
                            .BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme
                                    .error
                            ),
                    colors =
                        ButtonDefaults
                            .outlinedButtonColors(
                                contentColor =
                                    MaterialTheme.colorScheme
                                        .error
                            )
                ) {
                    Text(
                        text =
                            "Alle Sitzungen löschen"
                    )
                }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text =
                    "wakelogs speichert höchstens die letzten 20 Sitzungen lokal auf dem Gerät.",
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .labelSmall
            )
        }
    }

    if (showDeleteAllDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showDeleteAllDialog.value =
                    false
            },
            title = {
                Text(
                    text =
                        "Alle Sitzungen löschen?"
                )
            },
            text = {
                Text(
                    text =
                        "Wirklich alle gespeicherten Sitzungen löschen? Diese Aktion kann nicht rückgängig gemacht werden."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog.value =
                            false

                        expanded.value =
                            false

                        SessionArchiveStore.clear(
                            context
                        )

                        archiveRefresh
                            .intValue++
                    },
                    colors =
                        ButtonDefaults
                            .textButtonColors(
                                contentColor =
                                    MaterialTheme.colorScheme
                                        .error
                            )
                ) {
                    Text(
                        text = "Alle löschen"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog.value =
                            false
                    }
                ) {
                    Text(
                        text = "Abbrechen"
                    )
                }
            }
        )
    }
}

@Composable
private fun SessionHistoryEntry(
    session: ArchivedSession,
    detailLevel: DetailLevel,
    onSaveNote: (String?) -> Unit,
    onDelete: () -> Unit
) {
    val context =
        LocalContext.current

    val pendingSessionExport =
        remember(session.id) {
            androidx.compose.runtime
                .mutableStateOf<String?>(null)
        }

    val sessionExportLauncher =
        androidx.activity.compose
            .rememberLauncherForActivityResult(
                contract =
                    androidx.activity.result.contract
                        .ActivityResultContracts
                        .CreateDocument(
                            "text/plain"
                        )
            ) { uri ->
                val exportText =
                    pendingSessionExport.value

                if (
                    uri != null &&
                    exportText != null
                ) {
                    val succeeded =
                        runCatching {
                            context.contentResolver
                                .openOutputStream(uri)
                                ?.bufferedWriter()
                                ?.use { writer ->
                                    writer.write(
                                        exportText
                                    )
                                }
                                ?: error(
                                    "Ausgabedatei konnte nicht geöffnet werden."
                                )
                        }.isSuccess

                    android.widget.Toast
                        .makeText(
                            context,
                            if (succeeded) {
                                "Sitzungsexport gespeichert"
                            } else {
                                "Sitzungsexport fehlgeschlagen"
                            },
                            android.widget.Toast.LENGTH_LONG
                        )
                        .show()
                }

                pendingSessionExport.value =
                    null
            }

    val expanded =
        remember(session.id) {
            androidx.compose.runtime
                .mutableStateOf(false)
        }

    val showDeleteDialog =
        remember(session.id) {
            androidx.compose.runtime
                .mutableStateOf(false)
        }

    val showNoteDialog =
        remember(session.id) {
            androidx.compose.runtime
                .mutableStateOf(false)
        }

    val normalizedNote =
        session.note
            ?.trim()
            ?.takeUnless { note ->
                note.isBlank() ||
                    note.equals(
                        "null",
                        ignoreCase = true
                    )
            }

    val noteDraft =
        remember(
            session.id,
            session.note
        ) {
            androidx.compose.runtime
                .mutableStateOf(
                    normalizedNote.orEmpty()
                )
        }

    val topApp =
        session.topApps.firstOrNull()

    Card(
        onClick = {
            expanded.value =
                !expanded.value
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.42f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.Top
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            formatSessionHistoryDate(
                                session.startMillis
                            ),
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(2.dp)
                    )

                    Text(
                        text =
                            formatSessionHistoryTimeRange(
                                startMillis =
                                    session.startMillis,
                                endMillis =
                                    session.endMillis
                            ),
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                    normalizedNote
                        ?.let { note ->
                            Spacer(
                                modifier =
                                    Modifier.height(4.dp)
                            )

                            Text(
                                text = note,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primary,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                        }
                }

                Spacer(
                    modifier =
                        Modifier.width(10.dp)
                )

                Column(
                    horizontalAlignment =
                        Alignment.End
                ) {
                    Text(
                        text =
                            formatComparisonDuration(
                                session.durationMillis
                            ),
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            if (expanded.value) {
                                "Details ausblenden"
                            } else {
                                "Details anzeigen"
                            },
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                SessionHistoryMetric(
                    value =
                        session.displayWakeups
                            .toString(),
                    label = "Display",
                    modifier =
                        Modifier.weight(1f)
                )

                SessionHistoryMetric(
                    value =
                        session.cpuWakeups
                            .toString(),
                    label = "CPU",
                    modifier =
                        Modifier.weight(1f)
                )

                SessionHistoryMetric(
                    value =
                        formatNetworkBytes(
                            session
                                .networkTotalBytes
                        ),
                    label = "Netzwerk",
                    modifier =
                        Modifier.weight(1.2f)
                )
            }

            topApp?.let { app ->
                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Text(
                    text = "Aktivste App",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall
                )

                Spacer(
                    modifier =
                        Modifier.height(2.dp)
                )

                Text(
                    text =
                        sourceDisplayName(
                            app.name
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    text =
                        formatNetworkBytes(
                            app.totalBytes
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.End
                )
            }

            if (expanded.value) {
                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                SessionHistoryValueRow(
                    label = "Aktive Apps",
                    value =
                        session
                            .networkActiveApps
                            .toString()
                )

                SessionHistoryValueRow(
                    label = "Empfangen",
                    value =
                        formatNetworkBytes(
                            session
                                .networkRxBytes
                        )
                )

                SessionHistoryValueRow(
                    label = "Gesendet",
                    value =
                        formatNetworkBytes(
                            session
                                .networkTxBytes
                        )
                )

                SessionHistoryValueRow(
                    label = "Netzwerk / Min",
                    value =
                        formatNetworkBytes(
                            networkBytesPerMinute(
                                bytes =
                                    session
                                        .networkTotalBytes,
                                durationMillis =
                                    session
                                        .durationMillis
                            )
                        )
                )

                if (
                    detailLevel ==
                    DetailLevel.EXPERT &&
                    session.topApps.size > 1
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    Text(
                        text =
                            "Weitere aktive Apps",
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )

                    session.topApps
                        .drop(1)
                        .take(4)
                        .forEach { app ->
                            SessionHistoryValueRow(
                                label =
                                    sourceDisplayName(
                                        app.name
                                    ),
                                value =
                                    formatNetworkBytes(
                                        app.totalBytes
                                    )
                            )
                        }
                }

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                androidx.compose.material3
                    .OutlinedButton(
                        onClick = {
                            pendingSessionExport.value =
                                buildSessionExportText(
                                    session
                                )

                            sessionExportLauncher.launch(
                                buildSessionExportFileName(
                                    session
                                )
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text =
                                "Sitzungszusammenfassung"
                        )
                    }

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                androidx.compose.material3
                    .OutlinedButton(
                        onClick = {
                            noteDraft.value =
                                normalizedNote.orEmpty()

                            showNoteDialog.value =
                                true
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text =
                                if (
                                    normalizedNote == null
                                ) {
                                    "Notiz hinzufügen"
                                } else {
                                    "Notiz bearbeiten"
                                }
                        )
                    }

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showDeleteDialog.value =
                                true
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text =
                                "Sitzung löschen",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )
                    }
            }
        }
    }

    if (showNoteDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showNoteDialog.value =
                    false
            },
            title = {
                Text(
                    text =
                        if (
                            normalizedNote == null
                        ) {
                            "Notiz hinzufügen"
                        } else {
                            "Notiz bearbeiten"
                        }
                )
            },
            text = {
                Column {
                    androidx.compose.material3
                        .OutlinedTextField(
                            value =
                                noteDraft.value,
                            onValueChange = { value ->
                                noteDraft.value =
                                    value.take(120)
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    "Notiz"
                                )
                            },
                            placeholder = {
                                Text(
                                    "z. B. Disney+, Nachtmessung oder Flugmodus-Test"
                                )
                            },
                            supportingText = {
                                Text(
                                    noteDraft.value
                                        .length
                                        .toString() +
                                        " / 120"
                                )
                            },
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4
                        )
                }
            },
            confirmButton = {
                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showNoteDialog.value =
                                false

                            onSaveNote(
                                noteDraft.value
                            )
                        }
                    ) {
                        Text("Speichern")
                    }
            },
            dismissButton = {
                Row {
                    if (
                        normalizedNote != null
                    ) {
                        androidx.compose.material3
                            .TextButton(
                                onClick = {
                                    showNoteDialog
                                        .value =
                                        false

                                    noteDraft.value =
                                        ""

                                    onSaveNote(null)
                                }
                            ) {
                                Text(
                                    text =
                                        "Entfernen",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .error
                                )
                            }
                    }

                    androidx.compose.material3
                        .TextButton(
                            onClick = {
                                showNoteDialog.value =
                                    false
                            }
                        ) {
                            Text("Abbrechen")
                        }
                }
            }
        )
    }

    if (showDeleteDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showDeleteDialog.value =
                    false
            },
            title = {
                Text(
                    text =
                        "Sitzung löschen?"
                )
            },
            text = {
                Text(
                    text =
                        "Diese archivierte Sitzung wird dauerhaft vom Gerät entfernt."
                )
            },
            confirmButton = {
                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showDeleteDialog.value =
                                false
                            onDelete()
                        }
                    ) {
                        Text(
                            text = "Löschen",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )
                    }
            },
            dismissButton = {
                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showDeleteDialog.value =
                                false
                        }
                    ) {
                        Text("Abbrechen")
                    }
            }
        )
    }
}

@Composable
private fun SessionHistoryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 5.dp,
                    vertical = 9.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color =
                    MaterialTheme.colorScheme
                        .primary,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis
            )

            Text(
                text = label,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SessionHistoryValueRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment =
            Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .bodySmall
        )

        Spacer(
            modifier = Modifier.width(12.dp)
        )

        Text(
            text = value,
            modifier = Modifier.weight(1.45f),
            color =
                MaterialTheme.colorScheme
                    .primary,
            style =
                MaterialTheme.typography
                    .bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

private fun buildSessionExportFileName(
    session: ArchivedSession
): String {
    val timestamp =
        SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(
            Date(session.startMillis)
        )

    return "wakelogs_sitzungszusammenfassung_$timestamp.txt"
}

private fun buildSessionExportText(
    session: ArchivedSession
): String {
    return buildString {
        appendLine("wakelogs v${BuildConfig.VERSION_NAME} · dernikiausd")
        appendLine("Art des Exports: Sitzungszusammenfassung")
        appendLine()

        appendLine(
            "Beginn: " +
                formatSessionExportTimestamp(
                    session.startMillis
                )
        )

        appendLine(
            "Ende: " +
                formatSessionExportTimestamp(
                    session.endMillis
                )
        )

        appendLine(
            "Dauer: " +
                formatComparisonDuration(
                    session.durationMillis
                )
        )

        session.note
            ?.trim()
            ?.takeUnless { note ->
                note.isBlank() ||
                    note.equals(
                        "null",
                        ignoreCase = true
                    )
            }
            ?.let { note ->
                appendLine(
                    "Notiz: $note"
                )
            }

        appendLine()
        appendLine("Technische Aktivität")

        appendLine(
            "Display-Wakeups: " +
                session.displayWakeups
        )

        appendLine(
            "CPU-Wakeups: " +
                session.cpuWakeups
        )

        appendLine()
        appendLine("Netzwerk")

        appendLine(
            "Gesamt: " +
                formatNetworkBytes(
                    session.networkTotalBytes
                )
        )

        appendLine(
            "Empfangen: " +
                formatNetworkBytes(
                    session.networkRxBytes
                )
        )

        appendLine(
            "Gesendet: " +
                formatNetworkBytes(
                    session.networkTxBytes
                )
        )

        appendLine(
            "Pro Minute: " +
                formatNetworkBytes(
                    networkBytesPerMinute(
                        bytes =
                            session.networkTotalBytes,
                        durationMillis =
                            session.durationMillis
                    )
                )
        )

        appendLine(
            "Aktive Apps: " +
                session.networkActiveApps
        )

        appendLine()
        appendLine("Aktivste Apps")

        if (session.topApps.isEmpty()) {
            appendLine(
                "Keine App-Daten gespeichert."
            )
        } else {
            session.topApps.forEachIndexed {
                    index,
                    app ->

                appendLine(
                    "${index + 1}. " +
                        sourceDisplayName(
                            app.name
                        ) +
                        " · " +
                        formatNetworkBytes(
                            app.totalBytes
                        )
                )
            }
        }

        appendLine()
        appendLine(
            "Hinweis: Netzwerkaktivität zeigt Nutzung oder Hintergrundverkehr, beweist aber keinen direkten Wakeup-Zusammenhang."
        )
    }
}

private fun formatSessionExportTimestamp(
    timestamp: Long
): String {
    return SimpleDateFormat(
        "dd.MM.yyyy HH:mm:ss",
        Locale.getDefault()
    ).format(
        Date(timestamp)
    )
}

private fun formatSessionHistoryDate(
    timestamp: Long
): String {
    return SimpleDateFormat(
        "EEEE, dd.MM.yyyy",
        Locale.getDefault()
    ).format(
        Date(timestamp)
    )
}

private fun formatSessionHistoryTimeRange(
    startMillis: Long,
    endMillis: Long
): String {
    val formatter =
        SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        )

    return formatter.format(
        Date(startMillis)
    ) +
        " – " +
        formatter.format(
            Date(endMillis)
        )
}

private fun startOfTodayMillis(): Long {
    return Calendar.getInstance()
        .apply {
            set(
                Calendar.HOUR_OF_DAY,
                0
            )
            set(
                Calendar.MINUTE,
                0
            )
            set(
                Calendar.SECOND,
                0
            )
            set(
                Calendar.MILLISECOND,
                0
            )
        }
        .timeInMillis
}

private fun startOfNextDayMillis(
    todayStartMillis: Long
): Long {
    return Calendar.getInstance()
        .apply {
            timeInMillis =
                todayStartMillis

            add(
                Calendar.DAY_OF_YEAR,
                1
            )
        }
        .timeInMillis
}

@Composable
private fun StatisticsCard(
    events: List<WakeEvent>
) {
    val todayStartMillis =
        remember {
            startOfTodayMillis()
        }

    val tomorrowStartMillis =
        remember(todayStartMillis) {
            startOfNextDayMillis(
                todayStartMillis
            )
        }

    val screenOnEvents =
        events.filter { event ->
            event.type == "SCREEN_ON" &&
                event.timestamp >=
                    todayStartMillis &&
                event.timestamp <
                    tomorrowStartMillis
        }

    val hintedCount =
        screenOnEvents.count {
            hasExplanationOrHint(it)
        }

    val unknownCount =
        screenOnEvents.count {
            isUnexplainedScreenOn(it)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Tagesübersicht",
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            if (
                screenOnEvents.isEmpty() &&
                hintedCount == 0 &&
                unknownCount == 0
            ) {
                Text(
                    text =
                        "Heute noch keine Ereignisse erfasst.",
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography
                            .bodyMedium
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    StatisticValue(
                        value =
                            screenOnEvents.size.toString(),
                        label = "Display an",
                        modifier = Modifier.weight(1f)
                    )

                    StatisticValue(
                        value =
                            hintedCount.toString(),
                        label = "Mit Ursache",
                        modifier = Modifier.weight(1f)
                    )

                    StatisticValue(
                        value =
                            unknownCount.toString(),
                        label = "Ungeklärt",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private data class NightWindow(
    val startMillis: Long,
    val endMillis: Long,
    val ongoing: Boolean,
    val title: String
)

private enum class NightWakeCategory {
    POWER_BUTTON,
    DOUBLE_TAP,
    NOTIFICATION,
    WAKEUP_ALARM,
    OTHER_EXPLAINED,
    UNEXPLAINED
}

@Composable
private fun NightAnalysisCard(
    events: List<WakeEvent>,
    monitoring: Boolean
) {
    val nightWindow =
        remember(
            events,
            monitoring
        ) {
            calculateNightWindow(
                events = events,
                monitoring = monitoring
            )
        }

    val screenOnEvents =
        events.filter { event ->
            event.type == "SCREEN_ON" &&
                event.timestamp >=
                    nightWindow.startMillis &&
                event.timestamp <=
                    nightWindow.endMillis
        }

    val categories =
        screenOnEvents.groupingBy {
            nightWakeCategory(it)
        }.eachCount()

    val powerButtonCount =
        categories[
            NightWakeCategory.POWER_BUTTON
        ] ?: 0

    val doubleTapCount =
        categories[
            NightWakeCategory.DOUBLE_TAP
        ] ?: 0

    val notificationCount =
        categories[
            NightWakeCategory.NOTIFICATION
        ] ?: 0

    val alarmCount =
        categories[
            NightWakeCategory.WAKEUP_ALARM
        ] ?: 0

    val otherExplainedCount =
        categories[
            NightWakeCategory.OTHER_EXPLAINED
        ] ?: 0

    val unexplainedCount =
        categories[
            NightWakeCategory.UNEXPLAINED
        ] ?: 0

    val backgroundWakeCount =
        events.count { event ->
            event.type == "CPU_WAKEUP" &&
                event.timestamp >=
                    nightWindow.startMillis &&
                event.timestamp <=
                    nightWindow.endMillis
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = nightWindow.title,
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text =
                    formatNightWindow(
                        nightWindow
                    ),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography.bodySmall
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                StatisticValue(
                    value =
                        screenOnEvents.size.toString(),
                    label = "Display an",
                    modifier = Modifier.weight(1f)
                )

                StatisticValue(
                    value =
                        (
                            screenOnEvents.size -
                                unexplainedCount
                        ).toString(),
                    label = "Zugeordnet",
                    modifier = Modifier.weight(1f)
                )

                StatisticValue(
                    value =
                        unexplainedCount.toString(),
                    label = "Ungeklärt",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            HorizontalDivider()

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            NightAnalysisRow(
                label = "Power-Taste",
                value = powerButtonCount
            )

            NightAnalysisRow(
                label = "Doppeltipp",
                value = doubleTapCount
            )

            NightAnalysisRow(
                label = "Benachrichtigungen",
                value = notificationCount
            )

            NightAnalysisRow(
                label = "Wakeup-Alarme",
                value = alarmCount
            )

            NightAnalysisRow(
                label = "Andere Hinweise",
                value = otherExplainedCount
            )

            NightAnalysisRow(
                label = "Ungeklärt",
                value = unexplainedCount
            )

            NightAnalysisRow(
                label = "CPU-Wakeups im Hintergrund",
                value = backgroundWakeCount
            )

            if (
                screenOnEvents.isEmpty() &&
                backgroundWakeCount == 0
            ) {
                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Text(
                    text =
                        "In diesem Überwachungszeitraum wurden keine Display- oder CPU-Wakeups erfasst.",
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun NightAnalysisRow(
    label: String,
    value: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography.bodyMedium
        )

        Text(
            text = value.toString(),
            color =
                MaterialTheme.colorScheme.primary,
            style =
                MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun calculateNightWindow(
    events: List<WakeEvent>,
    monitoring: Boolean
): NightWindow {
    val now =
        System.currentTimeMillis()

    val sortedEvents =
        events.sortedBy {
            it.timestamp
        }

    val latestMonitorStart =
        sortedEvents.lastOrNull {
            it.type == "MONITOR_START"
        }

    if (latestMonitorStart != null) {
        val stopAfterStart =
            sortedEvents.firstOrNull { event ->
                event.type == "MONITOR_STOP" &&
                    event.timestamp >=
                        latestMonitorStart.timestamp
            }

        if (monitoring) {
            return NightWindow(
                startMillis =
                    latestMonitorStart.timestamp,
                endMillis = now,
                ongoing = true,
                title =
                    "Laufende Überwachung"
            )
        }

        val lastSessionEvent =
            sortedEvents.lastOrNull { event ->
                event.timestamp >=
                    latestMonitorStart.timestamp
            }

        val endTimestamp =
            stopAfterStart?.timestamp
                ?: lastSessionEvent?.timestamp
                ?: latestMonitorStart.timestamp

        return NightWindow(
            startMillis =
                latestMonitorStart.timestamp,
            endMillis =
                endTimestamp,
            ongoing = false,
            title =
                "Letzte Überwachung"
        )
    }

    if (sortedEvents.isNotEmpty()) {
        return NightWindow(
            startMillis =
                sortedEvents.first().timestamp,
            endMillis =
                sortedEvents.last().timestamp,
            ongoing = false,
            title =
                "Aufgezeichneter Zeitraum"
        )
    }

    return NightWindow(
        startMillis = now,
        endMillis = now,
        ongoing = false,
        title = "Noch keine Überwachung"
    )
}

private fun formatNightWindow(
    window: NightWindow
): String {
    val formatter =
        SimpleDateFormat(
            "dd.MM. HH:mm",
            Locale.getDefault()
        )

    return if (window.ongoing) {
        "Seit " +
            formatter.format(
                Date(window.startMillis)
            )
    } else if (
        window.startMillis ==
            window.endMillis
    ) {
        formatter.format(
            Date(window.startMillis)
        )
    } else {
        buildString {
            append(
                formatter.format(
                    Date(window.startMillis)
                )
            )

            append(" – ")

            append(
                formatter.format(
                    Date(window.endMillis)
                )
            )
        }
    }
}

private fun nightWakeCategory(
    event: WakeEvent
): NightWakeCategory {
    val details = event.details

    return when {
        details.contains(
            "Direkter Aufweckgrund: Power-Taste"
        ) ->
            NightWakeCategory.POWER_BUTTON

        details.contains(
            "Direkter Aufweckgrund: Doppeltipp"
        ) ->
            NightWakeCategory.DOUBLE_TAP

        details.contains(
            "Wahrscheinliche Ursache:"
        ) ||
        details.contains(
            "Mögliche Ursache:"
        ) ||
        details.contains(
            "Nachträglich erkannte Ursache:"
        ) ->
            NightWakeCategory.NOTIFICATION

        details.contains(
            "Wakeup-Alarm-Hinweis:"
        ) ->
            NightWakeCategory.WAKEUP_ALARM

        isUnexplainedScreenOn(event) ->
            NightWakeCategory.UNEXPLAINED

        else ->
            NightWakeCategory.OTHER_EXPLAINED
    }
}

@Composable
private fun StatisticValue(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant.copy(
                        alpha = 0.58f
                    )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color =
                    MaterialTheme.colorScheme.primary,
                style =
                    MaterialTheme.typography
                        .headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = label,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }
    }
}

@Composable
private fun ActionCard(
    hasEvents: Boolean,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        OutlinedButton(
            onClick = onExport,
            enabled = hasEvents,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            shape =
                RoundedCornerShape(16.dp),
            border =
                androidx.compose.foundation.BorderStroke(
                    1.25.dp,
                    MaterialTheme.colorScheme.primary
                ),
            colors =
                androidx.compose.material3.ButtonDefaults
                    .outlinedButtonColors(
                        contentColor =
                            MaterialTheme.colorScheme.primary,
                        containerColor =
                            MaterialTheme.colorScheme
                                .primaryContainer
                                .copy(alpha = 0.18f),
                        disabledContentColor =
                            MaterialTheme.colorScheme.onSurface
                                .copy(alpha = 0.45f)
                    )
        ) {
            Text(
                text =
                    "Technischen Bericht exportieren",
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
                fontWeight =
                    FontWeight.SemiBold
            )
        }

        androidx.compose.material3.TextButton(
            onClick = onClear,
            enabled = hasEvents,
            modifier =
                Modifier
                    .fillMaxWidth(0.72f)
                    .heightIn(min = 48.dp),
            shape =
                RoundedCornerShape(14.dp),
            contentPadding =
                PaddingValues(
                    horizontal = 20.dp,
                    vertical = 10.dp
                )
        ) {
            Text(
                text = "Liste leeren",
                style =
                    MaterialTheme.typography.bodyMedium,
                fontWeight =
                    FontWeight.SemiBold,
                color =
                    if (hasEvents) {
                        MaterialTheme.colorScheme.error
                            .copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.38f)
                    }
            )
        }

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )
    }
}

@Composable
private fun EventFilterBar(
    selectedFilter: EventFilter,
    onSelected: (EventFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {
        EventFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = {
                    onSelected(filter)
                },
                label = {
                    Text(
                        text = filter.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}



@Composable
private fun EventViewModeBar(
    selectedMode: EventViewMode,
    onSelected: (EventViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {
        EventViewMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = {
                    onSelected(mode)
                },
                label = {
                    Text(
                        text = mode.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}



@Composable
private fun FilterEmptyCard(
    monitoring: Boolean,
    filter: EventFilter
) {
    val title =
        when (filter) {
            EventFilter.UNKNOWN ->
                "Keine ungeklärten Ereignisse"

            EventFilter.BACKGROUND ->
                "Keine Hintergrund-Wakeups"

            EventFilter.NOTIFICATIONS ->
                "Keine Hinweise vorhanden"

            EventFilter.DISPLAY ->
                "Keine Display-Ereignisse"

            EventFilter.ALL ->
                if (monitoring) {
                    "Überwachung aktiv …"
                } else {
                    "Noch keine Ereignisse"
                }
        }

    val text =
        when (filter) {
            EventFilter.UNKNOWN ->
                "Alle bisher erfassten Display-Aktivierungen konnten erklärt werden."

            EventFilter.BACKGROUND ->
                "Noch keine CPU-Aktivierung bei ausgeschaltetem Display erfasst."

            EventFilter.NOTIFICATIONS ->
                "Noch keine passende Benachrichtigung wurde erfasst."

            EventFilter.DISPLAY ->
                "Schalte das Display aus und wieder ein."

            EventFilter.ALL ->
                if (monitoring) {
                    "wakelogs wartet auf neue Ereignisse."
                } else {
                    "Starte die Überwachung, damit wakelogs Ereignisse sammeln kann."
                }
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = text,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupedCpuEventCard(
    group: GroupedCpuEventListItem,
    detailLevel: DetailLevel
) {
    val expanded =
        remember(
            group.stableKey
        ) {
            mutableStateOf(false)
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer
                        .copy(alpha = 0.26f)
            )
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 12.dp
                )
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            "GEBÜNDELTE CPU-AKTIVITÄT",
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            group.source,
                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }

                Text(
                    text =
                        group.events.size
                            .toString() +
                            "×",
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            GroupedEventValueRow(
                label =
                    "Ähnliche Ereignisse",
                value =
                    group.events.size
                        .toString()
            )

            GroupedEventValueRow(
                label =
                    "Zeitraum",
                value =
                    formatGroupedEventRange(
                        group.events
                    )
            )

            group.totalDurationMillis
                ?.let { duration ->
                    GroupedEventValueRow(
                        label =
                            "Gesamte CPU-Wachzeit",
                        value =
                            formatGroupedCpuDuration(
                                duration
                            )
                    )
                }

            group.longestDurationMillis
                ?.let { duration ->
                    GroupedEventValueRow(
                        label =
                            "Längster Vorgang",
                        value =
                            formatGroupedCpuDuration(
                                duration
                            )
                    )
                }

            val averageDurationMillis =
                group.totalDurationMillis
                    ?.takeIf {
                        group.events.isNotEmpty()
                    }
                    ?.div(
                        group.events.size
                            .toLong()
                    )

            averageDurationMillis
                ?.let { duration ->
                    GroupedEventValueRow(
                        label =
                            "Durchschnitt",
                        value =
                            formatGroupedCpuDuration(
                                duration
                            )
                    )
                }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            val classification =
                classifyGroupedCpuActivity(
                    count =
                        group.events.size,
                    totalDurationMillis =
                        group.totalDurationMillis,
                    longestDurationMillis =
                        group.longestDurationMillis,
                    averageDurationMillis =
                        averageDurationMillis
                )

            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                                .copy(alpha = 0.58f)
                    )
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            horizontal = 11.dp,
                            vertical = 9.dp
                        )
                ) {
                    Text(
                        text =
                            classification.title,
                        color =
                            classification.color(),
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            classification.explanation,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    "Mehrere CPU-Wakeups derselben Quelle wurden für eine ruhigere Darstellung zusammengefasst.",
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            OutlinedButton(
                onClick = {
                    expanded.value =
                        !expanded.value
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    text =
                        if (expanded.value) {
                            "Einzelereignisse ausblenden"
                        } else {
                            "Einzelereignisse anzeigen"
                        }
                )
            }

            if (expanded.value) {
                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                group.events
                    .forEachIndexed {
                            index,
                            event ->

                        EventCard(
                            event = event,
                            detailLevel =
                                detailLevel
                        )

                        if (
                            index <
                            group.events.lastIndex
                        ) {
                            Spacer(
                                modifier =
                                    Modifier.height(8.dp)
                            )
                        }
                    }
            }
        }
    }
}

private data class GroupedCpuClassification(
    val title: String,
    val explanation: String,
    val level: Int
)

@Composable
private fun GroupedCpuClassification.color() =
    when (level) {
        0 ->
            Color(0xFF35A853)

        1 ->
            MaterialTheme
                .colorScheme
                .primary

        else ->
            MaterialTheme
                .colorScheme
                .error
    }

private fun classifyGroupedCpuActivity(
    count: Int,
    totalDurationMillis: Long?,
    longestDurationMillis: Long?,
    averageDurationMillis: Long?
): GroupedCpuClassification {
    if (
        totalDurationMillis == null ||
        longestDurationMillis == null ||
        averageDurationMillis == null
    ) {
        return GroupedCpuClassification(
            title =
                "Technisch nicht vollständig bewertbar",
            explanation =
                "Für mindestens einen Vorgang konnte keine vollständige CPU-Wachzeit ermittelt werden.",
            level = 1
        )
    }

    return when {
        longestDurationMillis >=
            30_000L ||
            averageDurationMillis >=
                15_000L ||
            totalDurationMillis >=
                90_000L ->
            GroupedCpuClassification(
                title =
                    "Länger andauernde Aktivität",
                explanation =
                    "Mindestens ein Wakeup oder die gesamte Gruppe hielt die CPU vergleichsweise lange aktiv. Das ist noch kein Beweis für problematischen Akkuverbrauch.",
                level = 2
            )

        longestDurationMillis >=
            5_000L ||
            averageDurationMillis >=
                2_500L ||
            totalDurationMillis >=
                20_000L ||
            count >= 10 ->
            GroupedCpuClassification(
                title =
                    "Beachtenswerte Aktivität",
                explanation =
                    "Die Gruppe enthält mehrere oder etwas längere CPU-Wakeups. Ein Vergleich mit weiteren Sitzungen ist sinnvoll.",
                level = 1
            )

        else ->
            GroupedCpuClassification(
                title =
                    "Kurz und überwiegend unauffällig",
                explanation =
                    "Die einzelnen Vorgänge waren kurz. Solche Hintergrundaktivitäten sind bei Android häufig normal.",
                level = 0
            )
    }
}

@Composable
private fun GroupedEventValueRow(
    label: String,
    value: String
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 2.dp
                ),
        verticalAlignment =
            Alignment.Top
    ) {
        Text(
            text = label,
            modifier =
                Modifier.weight(1f),
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme
                    .typography
                    .bodySmall
        )

        Spacer(
            modifier =
                Modifier.width(12.dp)
        )

        Text(
            text = value,
            modifier =
                Modifier.weight(1.15f),
            color =
                MaterialTheme
                    .colorScheme
                    .primary,
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            fontWeight =
                FontWeight.SemiBold,
            textAlign =
                TextAlign.End
        )
    }
}

private fun formatGroupedEventRange(
    events: List<WakeEvent>
): String {
    val oldest =
        events.minOf {
            it.timestamp
        }

    val newest =
        events.maxOf {
            it.timestamp
        }

    val formatter =
        SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        )

    return if (oldest == newest) {
        formatter.format(
            Date(newest)
        )
    } else {
        formatter.format(
            Date(oldest)
        ) +
            " – " +
            formatter.format(
                Date(newest)
            )
    }
}

private fun formatGroupedCpuDuration(
    millis: Long
): String {
    val safeMillis =
        millis.coerceAtLeast(0L)

    return when {
        safeMillis < 1_000L ->
            "$safeMillis ms"

        safeMillis < 60_000L ->
            String.format(
                Locale.getDefault(),
                "%.1f s",
                safeMillis / 1_000.0
            )

        else -> {
            val minutes =
                safeMillis / 60_000L

            val seconds =
                safeMillis %
                    60_000L /
                    1_000L

            "${minutes} min ${seconds} s"
        }
    }
}

@Composable
private fun EventCard(
    event: WakeEvent,
    detailLevel: DetailLevel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 10.dp
            )
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    text = eventTypeLabel(event.type),
                    color =
                        MaterialTheme.colorScheme.primary,
                    style =
                        MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatTimestamp(
                        event.timestamp
                    ),
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography.labelSmall
                )
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = event.title,
                style =
                    MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            val causeAssessment =
                causeAssessmentFor(event)

            if (causeAssessment != null) {
                Spacer(
                    modifier = Modifier.height(7.dp)
                )

                CauseAssessmentCard(
                    assessment =
                        causeAssessment
                )
            }

            val causalChain =
                remember(event.details) {
                    buildCausalChain(event)
                }

            if (causalChain.isNotEmpty()) {
                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                CausalChainView(
                    steps = causalChain
                )
            }

            if (
                event.details.isNotBlank() &&
                detailLevel != DetailLevel.SIMPLE
            ) {
                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Text(
                    text = uiDetailsForEvent(event),
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

private data class CausalChainStep(
    val offsetMillis: Long,
    val timingText: String,
    val title: String,
    val source: String,
    val companionActivity: Boolean = false
)

@Composable
private fun CausalChainView(
    steps: List<CausalChainStep>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant.copy(
                        alpha = 0.42f
                    )
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 10.dp
            )
        ) {
            Text(
                text = "Ursachenkette",
                color =
                    MaterialTheme.colorScheme.primary,
                style =
                    MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            val causeSteps =
                steps.filterNot {
                    it.companionActivity
                }

            val companionSteps =
                steps.filter {
                    it.companionActivity
                }

            causeSteps.forEachIndexed {
                    index,
                    step ->

                Text(
                    text = step.timingText,
                    color =
                        MaterialTheme.colorScheme.primary,
                    style =
                        MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = step.title,
                    style =
                        MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (step.source.isNotBlank()) {
                    Text(
                        text = step.source,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }

                if (index < causeSteps.lastIndex) {
                    Text(
                        text = "↓",
                        modifier =
                            Modifier.padding(
                                vertical = 3.dp
                            ),
                        color =
                            MaterialTheme.colorScheme.primary,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (companionSteps.isNotEmpty()) {
                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "Begleitaktivitäten nach dem Aufwecken",
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                companionSteps.forEach { step ->
                    Text(
                        text = step.timingText,
                        color =
                            MaterialTheme.colorScheme.primary,
                        style =
                            MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = step.title,
                        style =
                            MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (step.source.isNotBlank()) {
                        Text(
                            text = step.source,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )
                }
            }
        }
    }
}

private fun buildCausalChain(
    event: WakeEvent
): List<CausalChainStep> {
    if (event.type != "SCREEN_ON") {
        return emptyList()
    }

    val lines =
        event.details
            .lines()
            .map { it.trim() }

    val steps =
        mutableListOf<CausalChainStep>()

    fun valueAfter(
        startIndex: Int,
        prefix: String
    ): String? {
        val end =
            lines.indices
                .drop(startIndex + 1)
                .firstOrNull { index ->
                    isCausalSectionStart(
                        lines[index]
                    )
                }
                ?: lines.size

        return lines
            .subList(
                startIndex + 1,
                end
            )
            .firstOrNull {
                it.startsWith(prefix)
            }
            ?.substringAfter(prefix)
            ?.trim()
    }

    lines.forEachIndexed {
            index,
            line ->

        when {
            line.startsWith(
                "Direkter Aufweckgrund:"
            ) -> {
                val title =
                    line.substringAfter(":")
                        .trim()

                val timing =
                    valueAfter(
                        index,
                        "Zeitabstand:"
                    ) ?: "zeitlich direkt zugeordnet"

                steps.add(
                    CausalChainStep(
                        offsetMillis =
                            parseCausalOffset(
                                timing
                            ),
                        timingText =
                            readableCausalTiming(
                                timing
                            ),
                        title =
                            "Auslöser",
                        source = title
                    )
                )
            }

            line.startsWith(
                "Wahrscheinliche Ursache:"
            ) ||
            line.startsWith(
                "Mögliche Ursache:"
            ) -> {
                val source =
                    line.substringAfter(":")
                        .trim()

                val timing =
                    valueAfter(
                        index,
                        "Zeitabstand:"
                    ) ?: "zeitlich zugeordnet"

                steps.add(
                    CausalChainStep(
                        offsetMillis =
                            parseCausalOffset(
                                timing
                            ),
                        timingText =
                            readableCausalTiming(
                                timing
                            ),
                        title =
                            "Benachrichtigung",
                        source = source
                    )
                )
            }

            line.startsWith(
                "Nachträglich erkannte Ursache:"
            ) -> {
                val source =
                    line.substringAfter(":")
                        .trim()

                val timingLine =
                    lines
                        .drop(index + 1)
                        .firstOrNull {
                            it.startsWith(
                                "Hinweis kam "
                            )
                        }
                        ?.removePrefix(
                            "Hinweis kam "
                        )
                        ?: "nach Display an"

                steps.add(
                    CausalChainStep(
                        offsetMillis =
                            parseCausalOffset(
                                timingLine
                            ),
                        timingText =
                            readableCausalTiming(
                                timingLine
                            ),
                        title =
                            "Benachrichtigung",
                        source = source
                    )
                )
            }

            line.startsWith(
                "Systemhinweis:"
            ) ||
            line ==
                "Begleitaktivität: Wakelock" -> {
                val source =
                    valueAfter(
                        index,
                        "Quelle:"
                    ).orEmpty()

                val kind =
                    valueAfter(
                        index,
                        "Art:"
                    ) ?: "Wakelock"

                val timing =
                    valueAfter(
                        index,
                        "Zeitabstand:"
                    ) ?: "zeitlich zugeordnet"

                steps.add(
                    CausalChainStep(
                        offsetMillis =
                            parseCausalOffset(
                                timing
                            ),
                        timingText =
                            readableCausalTiming(
                                timing
                            ),
                        title = kind,
                        source = source,
                        companionActivity =
                            line.startsWith(
                                "Begleitaktivität:"
                            ) &&
                                parseCausalOffset(
                                    timing
                                ) >= 0L
                    )
                )
            }

            line.startsWith(
                "Wakeup-Alarm-Hinweis:"
            ) ||
            line ==
                "Begleitaktivität: Wakeup-Alarm" -> {
                val source =
                    valueAfter(
                        index,
                        "Quelle:"
                    ).orEmpty()

                val timing =
                    valueAfter(
                        index,
                        "Zeitabstand:"
                    ) ?: "zeitlich zugeordnet"

                steps.add(
                    CausalChainStep(
                        offsetMillis =
                            parseCausalOffset(
                                timing
                            ),
                        timingText =
                            readableCausalTiming(
                                timing
                            ),
                        title = "Wakeup-Alarm",
                        source = source,
                        companionActivity =
                            line.startsWith(
                                "Begleitaktivität:"
                            ) &&
                                parseCausalOffset(
                                    timing
                                ) >= 0L
                    )
                )
            }

            line.startsWith(
                "Hintergrundjob-Hinweis:"
            ) ||
            line ==
                "Begleitaktivität: Hintergrundjob" -> {
                val source =
                    valueAfter(
                        index,
                        "Quelle:"
                    ).orEmpty()

                val timing =
                    valueAfter(
                        index,
                        "Zeitabstand:"
                    ) ?: "zeitlich zugeordnet"

                steps.add(
                    CausalChainStep(
                        offsetMillis =
                            parseCausalOffset(
                                timing
                            ),
                        timingText =
                            readableCausalTiming(
                                timing
                            ),
                        title = "Hintergrundjob",
                        source = source,
                        companionActivity =
                            line.startsWith(
                                "Begleitaktivität:"
                            ) &&
                                parseCausalOffset(
                                    timing
                                ) >= 0L
                    )
                )
            }
        }
    }

    if (steps.isEmpty()) {
        return emptyList()
    }

    steps.add(
        CausalChainStep(
            offsetMillis = 0L,
            timingText = "0,0 s",
            title = "Display eingeschaltet",
            source = "",
            companionActivity = false
        )
    )

    return steps
        .distinctBy {
            listOf(
                it.offsetMillis,
                it.title,
                it.source
            )
        }
        .sortedWith(
            compareBy<CausalChainStep> {
                it.companionActivity
            }.thenBy {
                it.offsetMillis
            }.thenBy {
                it.title
            }
        )
}

private fun isCausalSectionStart(
    line: String
): Boolean {
    return line.startsWith(
        "Direkter Aufweckgrund:"
    ) ||
        line.startsWith(
            "Wahrscheinliche Ursache:"
        ) ||
        line.startsWith(
            "Mögliche Ursache:"
        ) ||
        line.startsWith(
            "Nachträglich erkannte Ursache:"
        ) ||
        line.startsWith(
            "Systemhinweis:"
        ) ||
        line.startsWith(
            "Wakeup-Alarm-Hinweis:"
        ) ||
        line.startsWith(
            "Hintergrundjob-Hinweis:"
        ) ||
        line.startsWith(
            "Begleitaktivität:"
        )
}

private fun parseCausalOffset(
    text: String
): Long {
    val normalized =
        text.replace(
            ',',
            '.'
        )

    if (
        normalized.contains(
            "zeitgleich",
            ignoreCase = true
        )
    ) {
        return 0L
    }

    val match =
        Regex(
            """(\d+(?:\.\d+)?)\s+Sekunden"""
        ).find(normalized)

    val milliseconds =
        match
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.times(1_000.0)
            ?.toLong()
            ?: 0L

    return when {
        normalized.contains(
            "vor Display an",
            ignoreCase = true
        ) ->
            -milliseconds

        normalized.contains(
            "nach Display an",
            ignoreCase = true
        ) ->
            milliseconds

        else ->
            0L
    }
}

private fun readableCausalTiming(
    text: String
): String {
    val offset =
        parseCausalOffset(text)

    if (offset == 0L) {
        return "0,0 s"
    }

    val normalized =
        text.replace(
            '.',
            ','
        )

    return when {
        normalized.contains(
            "vor Display an",
            ignoreCase = true
        ) -> {
            val value =
                normalized
                    .substringBefore(
                        " Sekunden"
                    )
                    .substringAfterLast(' ')
                    .trim()

            "−$value s"
        }

        normalized.contains(
            "nach Display an",
            ignoreCase = true
        ) -> {
            val value =
                normalized
                    .substringBefore(
                        " Sekunden"
                    )
                    .substringAfterLast(' ')
                    .trim()

            "+$value s"
        }

        normalized.contains(
            "zeitgleich",
            ignoreCase = true
        ) ->
            "0,0 s"

        else ->
            "zeitlich zugeordnet"
    }
}

@Composable
private fun CauseAssessmentCard(
    assessment: CauseAssessment
) {
    val containerColor =
        when (assessment.confidence) {
            CauseConfidence.CONFIRMED ->
                MaterialTheme.colorScheme
                    .primaryContainer
                    .copy(alpha = 0.72f)

            CauseConfidence.PROBABLE ->
                MaterialTheme.colorScheme
                    .primaryContainer
                    .copy(alpha = 0.48f)

            CauseConfidence.POSSIBLE ->
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.72f)

            CauseConfidence.COMPANION ->
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.52f)

            CauseConfidence.UNRESOLVED ->
                MaterialTheme.colorScheme
                    .errorContainer
                    .copy(alpha = 0.42f)
        }

    val titleColor =
        when (assessment.confidence) {
            CauseConfidence.CONFIRMED,
            CauseConfidence.PROBABLE ->
                MaterialTheme.colorScheme.primary

            CauseConfidence.POSSIBLE,
            CauseConfidence.COMPANION ->
                MaterialTheme.colorScheme
                    .onSurface

            CauseConfidence.UNRESOLVED ->
                MaterialTheme.colorScheme.error
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    containerColor
            )
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = 11.dp,
                    vertical = 9.dp
                )
        ) {
            Text(
                text =
                    assessment.confidence.label,
                color = titleColor,
                style =
                    MaterialTheme.typography
                        .labelMedium,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )

            Text(
                text =
                    assessment.explanation,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall,
                lineHeight = 16.sp
            )
        }
    }
}

private fun uiDetailsForEvent(
    event: WakeEvent
): String {
    if (event.type == "EXPERT_SNAPSHOT") {
        val lines =
            event.details
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()

        fun findLine(
            prefix: String
        ): String? {
            return lines.firstOrNull { line ->
                line.startsWith(prefix)
            }
        }

        val location =
            findLine("• Standort / Bewegung:")
                ?.removePrefix("• Standort / Bewegung:")
                ?.trim()
                ?.ifBlank { null }

        val sensor =
            findLine("• Sensorik:")
                ?.removePrefix("• Sensorik:")
                ?.trim()
                ?.ifBlank { null }

        val network =
            findLine("• Funk / Netzwerk:")
                ?.removePrefix("• Funk / Netzwerk:")
                ?.trim()
                ?.ifBlank { null }

        return buildString {
            appendLine("Auslöser: Display an")
            appendLine("Expertenkontext kurz")

            if (location != null) {
                appendLine()
                appendLine("Standort / Bewegung")
                appendLine("• $location")
            }

            if (sensor != null) {
                appendLine()
                appendLine("Sensorik")
                appendLine("• $sensor")
            }

            if (network != null) {
                appendLine()
                appendLine("Funk / Netzwerk")
                appendLine("• $network")
            }

            appendLine()
            append("Keine Standortkoordinaten im Export.")
        }.trim()
    }

    if (event.type == "SYSTEM_SNAPSHOT") {
        val lines =
            event.details
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()

        fun valueAfter(
            prefix: String
        ): String? {
            return lines.firstOrNull { line ->
                line.startsWith(prefix)
            }?.removePrefix(prefix)
                ?.trim()
                ?.ifBlank { null }
        }

        val trigger =
            valueAfter("Auslöser:")
                ?: "Systemprüfung"

        val wakefulness =
            lines.firstOrNull { line ->
                line.startsWith("Power:")
            }
                ?.substringAfter("Wakefulness=", "")
                ?.substringBefore(",")
                ?.trim()
                ?.ifBlank { null }
                ?: "unbekannt"

        val screenOn =
            lines.firstOrNull { line ->
                line.startsWith("Rahmenzustand:")
            }
                ?.substringAfter("ScreenOn=", "")
                ?.substringBefore(",")
                ?.trim()
                ?.ifBlank { null }

        val displayText =
            when (screenOn) {
                "true" -> "an"
                "false" -> "aus"
                else -> "unbekannt"
            }

        val idleLine =
            lines.firstOrNull { line ->
                line.startsWith("DeviceIdle:")
            }

        val idleText =
            readableIdleStateForUi(
                idleLine
            )

        val classification =
            valueAfter("Einordnung:")
                ?: "Keine Einordnung verfügbar."

        return buildString {
            appendLine("Auslöser: $trigger")
            appendLine("Zustand: $wakefulness")
            appendLine("Display: $displayText")

            if (idleText != null) {
                appendLine("Idle: $idleText")
            }

            appendLine()
            append(classification)
        }.trim()
    }

    return event.details
}



private fun readableIdleStateForUi(
    idleLine: String?
): String? {
    if (idleLine.isNullOrBlank()) {
        return null
    }

    val lower =
        idleLine.lowercase(Locale.getDefault())

    return when {
        lower.contains("deep=idle") ||
            lower.contains("deepmode=true") ->
            "tiefer Doze aktiv"

        lower.contains("deep=inactive") &&
            lower.contains("light=inactive") ->
            "noch nicht tief im Doze"

        lower.contains("light=active") ||
            lower.contains("deep=active") ->
            "System aktiv"

        lower.contains("light=idle") ->
            "leichter Doze aktiv"

        else ->
            idleLine
                .replace("DeviceIdle:", "")
                .trim()
                .take(80)
                .ifBlank { null }
    }
}

private fun causeAssessmentFor(
    event: WakeEvent
): CauseAssessment? {
    if (event.type != "SCREEN_ON") {
        return null
    }

    val details =
        event.details

    val hasDirectWakeReason =
        details.contains(
            "Direkter Aufweckgrund:",
            ignoreCase = true
        )

    if (hasDirectWakeReason) {
        return CauseAssessment(
            confidence =
                CauseConfidence.CONFIRMED,
            explanation =
                "Ein direkter Aufweckgrund wurde vom System erkannt."
        )
    }

    val hasStrongNotification =
        details.contains(
            "Wahrscheinliche Ursache:",
            ignoreCase = true
        ) ||
            (
                details.contains(
                    "Nachträglich erkannte Ursache:",
                    ignoreCase = true
                ) &&
                details.contains(
                    "Sicherheit: hoch",
                    ignoreCase = true
                )
            )

    if (hasStrongNotification) {
        return CauseAssessment(
            confidence =
                CauseConfidence.PROBABLE,
            explanation =
                "Ein starker zeitlicher und technischer Hinweis spricht für diese Ursache."
        )
    }

    val hasMediumNotification =
        details.contains(
            "Mögliche Ursache:",
            ignoreCase = true
        ) ||
            (
                details.contains(
                    "Nachträglich erkannte Ursache:",
                    ignoreCase = true
                ) &&
                details.contains(
                    "Sicherheit: mittel",
                    ignoreCase = true
                )
            )

    if (hasMediumNotification) {
        return CauseAssessment(
            confidence =
                CauseConfidence.POSSIBLE,
            explanation =
                "Die Aktivität passt zeitlich zum Display-Aufwecken, ist aber nicht direkt bestätigt."
        )
    }

    val hasWakeupAlarm =
        details.contains(
            "Wakeup-Alarm-Hinweis:",
            ignoreCase = true
        )

    if (hasWakeupAlarm) {
        return CauseAssessment(
            confidence =
                CauseConfidence.POSSIBLE,
            explanation =
                "Ein Wakeup-Alarm lag zeitlich nahe am Display-Aufwecken. Das beweist noch keinen direkten Zusammenhang."
        )
    }

    val hasStrongWakeLock =
        details.contains(
            "Systemhinweis: möglicher Auslöser",
            ignoreCase = true
        ) ||
            details.contains(
                "Systemhinweis: enger zeitlicher Zusammenhang",
                ignoreCase = true
            )

    if (hasStrongWakeLock) {
        return CauseAssessment(
            confidence =
                CauseConfidence.POSSIBLE,
            explanation =
                "Ein System-Wakelock trat in engem zeitlichen Zusammenhang auf, wurde aber nicht als direkter Auslöser bestätigt."
        )
    }

    val hasCompanionActivity =
        details.contains(
            "Begleitaktivität:",
            ignoreCase = true
        ) ||
            details.contains(
                "Hintergrundjob-Hinweis:",
                ignoreCase = true
            ) ||
            details.contains(
                "Systemhinweis: wahrscheinliches Folgeereignis",
                ignoreCase = true
            )

    if (hasCompanionActivity) {
        return CauseAssessment(
            confidence =
                CauseConfidence.COMPANION,
            explanation =
                "Eine technische Aktivität trat zeitnah auf, ist aber nur als Begleitaktivität eingeordnet."
        )
    }

    return CauseAssessment(
        confidence =
            CauseConfidence.UNRESOLVED,
        explanation =
            "Für dieses Display-Aufwecken wurde kein ausreichend belastbarer Auslöser gefunden."
    )
}


private fun hasExplanationOrHint(
    event: WakeEvent
): Boolean {
    if (event.type != "SCREEN_ON") {
        return false
    }

    val details = event.details

    val hasNotificationCause =
        details.contains(
            "Wahrscheinliche Ursache:"
        ) ||
        details.contains(
            "Mögliche Ursache:"
        ) ||
        details.contains(
            "Nachträglich erkannte Ursache:"
        )

    val hasSystemHint =
        details.contains(
            "Systemhinweis:"
        ) ||
        details.contains(
            "Wakeup-Alarm-Hinweis:"
        ) ||
        details.contains(
            "Hintergrundjob-Hinweis:"
        ) ||
        details.contains(
            "Direkter Aufweckgrund:"
        )

    return hasNotificationCause ||
        hasSystemHint
}

private fun isUnexplainedScreenOn(
    event: WakeEvent
): Boolean {
    if (event.type != "SCREEN_ON") {
        return false
    }

    return event.details.contains(
        "Ursache: noch unbekannt"
    ) &&
        !hasExplanationOrHint(event)
}

private fun readableWakeReason(
    diagnostic: WakeReasonDiagnostic
): String {
    val reason =
        diagnostic.reason.orEmpty()

    val details =
        diagnostic.details.orEmpty()

    return when {
        reason ==
            "WAKE_REASON_POWER_BUTTON" ->
            "Power-Taste"

        details.contains(
            "DoubleTap",
            ignoreCase = true
        ) ||
        details.contains(
            "blackGestureWake",
            ignoreCase = true
        ) ->
            "Doppeltipp auf das ausgeschaltete Display"

        reason ==
            "WAKE_REASON_GESTURE" ->
            "Bildschirmgeste"

        reason ==
            "WAKE_REASON_LIFT" ->
            "Anheben des Geräts"

        reason ==
            "WAKE_REASON_PLUGGED_IN" ->
            "Stromversorgung verbunden"

        reason ==
            "WAKE_REASON_APPLICATION" ->
            "App oder Systemfunktion"

        reason ==
            "WAKE_REASON_WAKE_KEY" ->
            "Aufwecktaste"

        reason ==
            "WAKE_REASON_WAKE_MOTION" ->
            "Bewegungs- oder Sensorsignal"

        reason.isNotBlank() ->
            reason

        else ->
            "Unbekannt"
    }
}

private fun compactWakeReasonDetails(
    details: String?
): String {
    val value =
        details
            ?.trim()
            .orEmpty()

    if (value.isBlank()) {
        return "keine"
    }

    return if (value.length <= 90) {
        value
    } else {
        value.take(87) + "…"
    }
}

private fun compactJobService(
    serviceName: String?
): String {
    val value =
        serviceName
            ?.trim()
            .orEmpty()

    if (value.isBlank()) {
        return "unbekannt"
    }

    return if (value.length <= 72) {
        value
    } else {
        value.take(69) + "…"
    }
}

private fun compactAlarmTag(
    tag: String?
): String {
    val value = tag
        ?.removePrefix("*walarm*:")
        ?.trim()
        .orEmpty()

    if (value.isBlank()) {
        return "unbekannt"
    }

    return if (value.length <= 72) {
        value
    } else {
        value.take(69) + "…"
    }
}

private fun formatDuration(
    millis: Long?
): String {
    val value = millis ?: return "unbekannter Zeit"

    val totalSeconds = value / 1_000L
    val days = totalSeconds / 86_400L
    val hours =
        totalSeconds % 86_400L / 3_600L
    val minutes =
        totalSeconds % 3_600L / 60L
    val seconds =
        totalSeconds % 60L

    return when {
        days > 0L ->
            "${days} T ${hours} Std"

        hours > 0L ->
            "${hours} Std ${minutes} Min"

        minutes > 0L ->
            "${minutes} Min ${seconds} Sek"

        else ->
            "${seconds} Sek"
    }
}

private fun formatNetworkBytes(
    bytes: Long
): String {
    val value =
        bytes.coerceAtLeast(0L)

    return when {
        value >= 1_073_741_824L ->
            String.format(
                Locale.getDefault(),
                "%.1f GB",
                value / 1_073_741_824.0
            )

        value >= 1_048_576L ->
            String.format(
                Locale.getDefault(),
                "%.1f MB",
                value / 1_048_576.0
            )

        value >= 1024L ->
            String.format(
                Locale.getDefault(),
                "%.1f KB",
                value / 1024.0
            )

        else ->
            "$value B"
    }
}

private fun compactWakeLockTag(
    tag: String?
): String {
    val value = tag
        ?.trim()
        .orEmpty()

    if (value.isBlank()) {
        return "unbekannt"
    }

    if (value.length <= 72) {
        return value
    }

    return value.take(69) + "…"
}

private fun resolveWakeLockSource(
    context: Context,
    packageName: String?
): String {
    val rawName = packageName
        ?.trim()
        .orEmpty()

    if (rawName.isBlank()) {
        return "Unbekannt"
    }

    readableSystemSource(rawName)?.let {
        return "$it ($rawName)"
    }

    return runCatching {
        val applicationInfo =
            context.packageManager
                .getApplicationInfo(
                    rawName,
                    0
                )

        val appName =
            context.packageManager
                .getApplicationLabel(
                    applicationInfo
                )
                .toString()
                .trim()

        if (appName.isBlank()) {
            rawName
        } else {
            "$appName ($rawName)"
        }
    }.getOrDefault(rawName)
}

private fun readableSystemSource(
    packageName: String
): String? {
    val value = packageName.lowercase(
        Locale.ROOT
    )

    return when {
        value == "android" ||
            value == "system" ->
            "Android-System"

        value.contains(
            "com.android.mms.service"
        ) ->
            "Android MMS-/Mobilfunkdienst"

        value.contains(
            "com.android.phone"
        ) ->
            "Android Telefoniedienst"

        value.contains(
            "com.android.providers.telephony"
        ) ->
            "Android Telefonie-Datenspeicher"

        value.contains(
            "com.google.android.ims"
        ) ->
            "Google Mobilfunk-/IMS-Dienst"

        value.contains(
            "com.android.systemui"
        ) ->
            "Android Systemoberfläche"

        value.contains(
            "com.android.bluetooth"
        ) ->
            "Android Bluetooth-Dienst"

        value.contains(
            "com.android.networkstack"
        ) ->
            "Android Netzwerkdienst"

        value.contains(
            "com.google.android.gms"
        ) ->
            "Google Play-Dienste"

        else -> null
    }
}

private fun classifyWakeLockTag(
    tag: String?
): String {
    val value = tag
        ?.trim()
        .orEmpty()

    if (value.isBlank()) {
        return "Unbekannter Partial Wakelock"
    }

    return when {
        value.contains(
            "NetworkStats",
            ignoreCase = true
        ) ->
            "Netzwerkstatistik"

        value.contains(
            "*alarm*",
            ignoreCase = true
        ) ->
            "Alarm"

        value.contains(
            "*job*",
            ignoreCase = true
        ) ->
            "Hintergrundjob"

        value.contains(
            "*launch*",
            ignoreCase = true
        ) ->
            "App-Start"

        value.contains(
            "AudioMix",
            ignoreCase = true
        ) ||
        value.contains(
            "AudioIn",
            ignoreCase = true
        ) ||
        value.contains(
            "ExoPlayer",
            ignoreCase = true
        ) ->
            "Audio-Wiedergabe oder Aufnahme"

        value.contains(
            "SyncManager",
            ignoreCase = true
        ) ||
        value.contains(
            "*sync*",
            ignoreCase = true
        ) ->
            "Synchronisierung"

        value.contains(
            "Icing",
            ignoreCase = true
        ) ->
            "Suche oder Inhaltsindexierung"

        value.contains(
            "NotificationManagerService",
            ignoreCase = true
        ) ->
            "Benachrichtigungsverarbeitung"

        value.contains(
            "PendingIntentClient",
            ignoreCase = true
        ) ->
            "Geplante Hintergrundaktion"

        else ->
            "Partial Wakelock"
    }
}

private fun formatWakeLockTimestamp(
    rawTimestamp: String?
): String {
    val raw = rawTimestamp
        ?.trim()
        .orEmpty()

    if (raw.isBlank()) {
        return "Unbekannt"
    }

    return runCatching {
        val currentYear =
            java.util.Calendar
                .getInstance()
                .get(
                    java.util.Calendar.YEAR
                )

        val parser =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
            ).apply {
                isLenient = false
            }

        val parsedDate =
            parser.parse(
                "$currentYear-$raw"
            ) ?: error(
                "Zeitstempel nicht lesbar"
            )

        SimpleDateFormat(
            "dd.MM.yyyy · HH:mm:ss",
            Locale.getDefault()
        ).format(parsedDate)
    }.getOrDefault(raw)
}

private fun eventTypeLabel(
    type: String
): String {
    return when (type) {
        "SCREEN_ON" ->
            "DISPLAY AN"

        "SCREEN_OFF" ->
            "DISPLAY AUS"

        "CPU_WAKEUP" ->
            "HINTERGRUND"

        "SYSTEM_SNAPSHOT" ->
            "SYSTEM"

        "EXPERT_SNAPSHOT" ->
            "EXPERTE"

        "NOTIFICATION" ->
            "HINWEIS"

        "MONITOR_START",
        "MONITOR_STOP" ->
            "MONITOR"

        else ->
            type
                .replace("_", " ")
                .uppercase(Locale.getDefault())
    }
}


private fun formatTimestamp(
    timestamp: Long
): String {
    return SimpleDateFormat(
        "dd.MM. · HH:mm:ss",
        Locale.getDefault()
    ).format(Date(timestamp))
}

private fun formatLiveSessionDuration(
    durationMillis: Long
): String {
    val totalSeconds =
        durationMillis
            .coerceAtLeast(0L) /
            1_000L

    val days =
        totalSeconds /
            86_400L

    val hours =
        (
            totalSeconds %
                86_400L
        ) /
            3_600L

    val minutes =
        (
            totalSeconds %
                3_600L
        ) /
            60L

    val seconds =
        totalSeconds %
            60L

    return when {
        days > 0L ->
            String.format(
                Locale.getDefault(),
                "%d T · %02d:%02d:%02d",
                days,
                hours,
                minutes,
                seconds
            )

        hours > 0L ->
            String.format(
                Locale.getDefault(),
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
            )

        else ->
            String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds
            )
    }
}

private fun isNotificationAccessEnabled(
    context: Context
): Boolean {
    return NotificationManagerCompat
        .getEnabledListenerPackages(context)
        .contains(context.packageName)
}

private fun startMonitoring(
    context: Context
) {
    val intent = Intent(
        context,
        WakeMonitorService::class.java
    ).apply {
        action =
            WakeMonitorService.ACTION_START
    }

    ContextCompat.startForegroundService(
        context,
        intent
    )
}

private fun stopMonitoring(
    context: Context,
    requestedAtMillis: Long
) {
    val intent = Intent(
        context,
        WakeMonitorService::class.java
    ).apply {
        action =
            WakeMonitorService.ACTION_STOP

        putExtra(
            "stop_requested_at_millis",
            requestedAtMillis
        )
    }

    context.startService(intent)
}
