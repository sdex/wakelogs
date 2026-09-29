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
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.sanniki.wakesleuth.domain.AlarmHint
import de.sanniki.wakesleuth.domain.CauseAssessment
import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.EventType
import de.sanniki.wakesleuth.domain.ExpertSection
import de.sanniki.wakesleuth.domain.ExpertSnapshotEvent
import de.sanniki.wakesleuth.domain.ExpertSnapshotStatus
import de.sanniki.wakesleuth.domain.HintRelation
import de.sanniki.wakesleuth.domain.JobHint
import de.sanniki.wakesleuth.domain.NotificationCauseKind
import de.sanniki.wakesleuth.domain.NotificationEvent
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.ScreenOffEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.ScreenOnVerdict
import de.sanniki.wakesleuth.domain.SnapshotClassification
import de.sanniki.wakesleuth.domain.SnapshotStatus
import de.sanniki.wakesleuth.domain.SystemSnapshot
import de.sanniki.wakesleuth.domain.SystemSnapshotEvent
import de.sanniki.wakesleuth.domain.WakeLockHint
import de.sanniki.wakesleuth.domain.WakeLockTags
import de.sanniki.wakesleuth.domain.WakeReasons
import de.sanniki.wakesleuth.ui.ScreenOnStatistics
import de.sanniki.wakesleuth.ui.WakelogsViewModel
import de.sanniki.wakesleuth.ui.render.EventTextRenderer
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver
import de.sanniki.wakesleuth.ui.render.rememberEventTextRenderer
import de.sanniki.wakesleuth.ui.render.rememberSourceLabelResolver
import de.sanniki.wakesleuth.ui.theme.WakesleuthTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class MainSection(
    @StringRes val label: Int,
    @StringRes val shortLabel: Int,
) {
    OVERVIEW(label = R.string.main_section_overview, shortLabel = R.string.main_section_overview_short),
    ANALYSIS(label = R.string.main_section_analysis, shortLabel = R.string.main_section_analysis_short),
    SESSIONS(label = R.string.main_section_sessions, shortLabel = R.string.main_section_sessions_short),
    DIAGNOSTICS(label = R.string.main_section_diagnostics, shortLabel = R.string.main_section_diagnostics_short),
}

private enum class EventFilter(
    @StringRes val label: Int,
) {
    ALL(R.string.main_filter_all),
    DISPLAY(R.string.main_filter_display),
    BACKGROUND(R.string.main_filter_background),
    NOTIFICATIONS(R.string.main_filter_notifications),
    UNKNOWN(R.string.main_filter_unexplained),
}

private enum class EventViewMode(
    @StringRes val label: Int,
) {
    LIST(R.string.main_view_mode_list),
    TIMELINE(R.string.main_view_mode_timeline),
}

private enum class CauseConfidence(
    @StringRes val label: Int,
) {
    CONFIRMED(R.string.main_confidence_confirmed),
    PROBABLE(R.string.main_confidence_probable),
    POSSIBLE(R.string.main_confidence_possible),
    COMPANION(R.string.main_confidence_companion),
    UNRESOLVED(R.string.main_confidence_unresolved),
}

private data class CauseAssessmentUi(
    val confidence: CauseConfidence,
    @StringRes val explanation: Int,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        setContent {
            var uiSettings by remember { mutableStateOf(WakeSleuthUiSettingsStore.load(this)) }

            WakesleuthTheme(
                accentColor = uiSettings.accentColor,
            ) {
                WakeSleuthScreen(
                    uiSettings = uiSettings,
                    onUiSettingsChanged = {
                        uiSettings = it

                        WakeSleuthUiSettingsStore.save(this, it)
                    },
                )
            }
        }
    }
}

@Composable
private fun WakeSleuthScreen(
    uiSettings: WakeSleuthUiSettings,
    onUiSettingsChanged: (WakeSleuthUiSettings) -> Unit,
    viewModel: WakelogsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var appIsResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(
        lifecycleOwner,
    ) {
        val observer = LifecycleEventObserver {
            _,
            event,
            ->

            appIsResumed = when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    true
                }

                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY,
                -> {
                    false
                }

                else -> {
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showSettings by remember { mutableStateOf(false) }

    val mainSections = MainSection.entries

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = {
            mainSections.size
        },
    )

    val navigationScope = rememberCoroutineScope()

    val selectedMainSection = mainSections[pagerState.currentPage]

    var lastBackPressMillis by remember { mutableStateOf(0L) }

    BackHandler {
        when {
            showSettings -> {
                showSettings = false
            }

            selectedMainSection != MainSection.OVERVIEW -> {
                navigationScope.launch { pagerState.animateScrollToPage(0) }
            }

            else -> {
                val now = System.currentTimeMillis()

                if (
                    now - lastBackPressMillis <= 2_000L
                ) {
                    val activity = context as? ComponentActivity

                    activity?.finish()
                } else {
                    lastBackPressMillis = now

                    Toast
                        .makeText(
                            context,
                            resources.getString(R.string.main_toast_press_back_again),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }

    val uiPreferences = remember { context.getSharedPreferences("wakesleuth_ui", Context.MODE_PRIVATE) }

    val events by
        viewModel.timeline.collectAsStateWithLifecycle()

    val latestSession by
        viewModel.latestSession.collectAsStateWithLifecycle()

    val archivedSessions by
        viewModel.archive.collectAsStateWithLifecycle()

    val recordingSession = latestSession?.takeIf { it.finalizedAt == null }

    val monitoring = recordingSession != null

    val currentSessionStartMillis = recordingSession?.startedAt

    val stopRequestedAtMillis = recordingSession?.stopRequestedAt

    var currentSessionDurationMillis by remember { mutableStateOf(0L) }

    LaunchedEffect(
        monitoring,
        appIsResumed,
        currentSessionStartMillis,
        stopRequestedAtMillis,
    ) {
        if (
            !monitoring || !appIsResumed || currentSessionStartMillis == null || stopRequestedAtMillis != null
        ) {
            if (!monitoring) {
                currentSessionDurationMillis = 0L
            }

            return@LaunchedEffect
        }

        while (isActive) {
            currentSessionDurationMillis = (System.currentTimeMillis() - currentSessionStartMillis).coerceAtLeast(0L)

            delay(1_000L)
        }
    }

    var listenerEnabled by remember { mutableStateOf(isNotificationAccessEnabled(context)) }

    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var pendingExportText by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    val labels = rememberSourceLabelResolver()

    val todayStartMillis = remember { startOfTodayMillis() }

    val dailyStatistics by
        remember(todayStartMillis) {
            viewModel.observeScreenOnStatistics(from = todayStartMillis, to = startOfNextDayMillis(todayStartMillis))
        }.collectAsStateWithLifecycle(
            ScreenOnStatistics(),
        )

    var shizukuState by remember { mutableStateOf(ShizukuDiagnostics.state()) }

    DisposableEffect(Unit) {
        val binderReceivedListener = Shizuku.OnBinderReceivedListener { shizukuState = ShizukuDiagnostics.state() }

        val binderDeadListener = Shizuku.OnBinderDeadListener { shizukuState = ShizukuState.NOT_RUNNING }

        val permissionResultListener = Shizuku.OnRequestPermissionResultListener {
            requestCode,
            grantResult,
            ->

            if (
                requestCode == ShizukuDiagnostics.REQUEST_CODE
            ) {
                shizukuState = ShizukuDiagnostics.state()
            }
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)

        Shizuku.addBinderDeadListener(binderDeadListener)

        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        shizukuState = ShizukuDiagnostics.state()

        onDispose {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)

            Shizuku.removeBinderDeadListener(binderDeadListener)

            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }
    }

    var wakeLockDiagnostic by remember { mutableStateOf<WakeLockDiagnostic?>(null) }

    var wakeLockLoading by remember { mutableStateOf(false) }

    var wakeupAlarmDiagnostic by remember { mutableStateOf<WakeupAlarmDiagnostic?>(null) }

    var wakeupAlarmLoading by remember { mutableStateOf(false) }

    var backgroundJobDiagnostic by remember { mutableStateOf<BackgroundJobDiagnostic?>(null) }

    var backgroundJobLoading by remember { mutableStateOf(false) }

    var wakeReasonDiagnostic by remember { mutableStateOf<WakeReasonDiagnostic?>(null) }

    var wakeReasonLoading by remember { mutableStateOf(false) }

    var networkStatsDiagnostic by remember { mutableStateOf<NetworkStatsDiagnostic?>(null) }

    var networkStatsLoading by remember { mutableStateOf(false) }

    var selectedEventViewMode by remember {
        mutableStateOf(
            runCatching {
                EventViewMode.valueOf(
                    uiPreferences.getString("event_view_mode", EventViewMode.LIST.name) ?: EventViewMode.LIST.name,
                )
            }.getOrDefault(
                EventViewMode.LIST,
            ),
        )
    }

    var eventSectionExpanded by remember { mutableStateOf(false) }

    var selectedFilter by remember {
        mutableStateOf(
            runCatching {
                EventFilter.valueOf(
                    uiPreferences.getString("event_filter", EventFilter.ALL.name) ?: EventFilter.ALL.name,
                )
            }.getOrDefault(EventFilter.ALL),
        )
    }

    val filteredEvents = remember(
        events,
        selectedFilter,
    ) {
        when (selectedFilter) {
            EventFilter.ALL -> events
            EventFilter.DISPLAY -> events.filter { it is ScreenOnEvent || it is ScreenOffEvent }
            EventFilter.BACKGROUND -> events.filter { it is CpuWakeupEvent }
            EventFilter.NOTIFICATIONS -> events.filter { it is NotificationEvent }
            EventFilter.UNKNOWN -> events.filter { it is ScreenOnEvent && CauseAssessment.isUnexplained(it) }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        val succeeded = runCatching {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: error(resources.getString(R.string.main_error_file_open_failed))

            outputStream.bufferedWriter().use { writer -> writer.write(pendingExportText) }
        }.isSuccess

        Toast
            .makeText(
                context,
                if (succeeded) {
                    resources.getString(R.string.main_toast_export_saved)
                } else {
                    resources.getString(R.string.main_toast_export_failed)
                },
                Toast.LENGTH_LONG,
            ).show()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = granted || Build.VERSION.SDK_INT < 33

        startMonitoring(context)

        if (
            !granted && Build.VERSION.SDK_INT >= 33
        ) {
            Toast
                .makeText(
                    context,
                    resources.getString(R.string.main_toast_monitoring_notification_hidden),
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

    val setupNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = granted || Build.VERSION.SDK_INT < 33

        if (
            !granted && Build.VERSION.SDK_INT >= 33
        ) {
            Toast
                .makeText(
                    context,
                    resources.getString(R.string.main_toast_notifications_denied),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    // Permissions and Shizuku can change in other apps; they are read
    // again whenever this screen comes back to the foreground.
    LaunchedEffect(appIsResumed) {
        if (!appIsResumed) {
            return@LaunchedEffect
        }

        listenerEnabled = isNotificationAccessEnabled(context)

        notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        shizukuState = ShizukuDiagnostics.state()
    }

    if (showSettings) {
        WakeSleuthSettingsScreen(
            settings = uiSettings,
            onSettingsChanged = onUiSettingsChanged,
            onBack = {
                showSettings = false
            },
        )

        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            wakelogsBottomNavigation(
                selectedSection = selectedMainSection,
                onSectionSelected = { section ->

                    val targetPage = mainSections.indexOf(section)

                    if (
                        targetPage >= 0 && targetPage != pagerState.currentPage
                    ) {
                        navigationScope.launch { pagerState.animateScrollToPage(targetPage) }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = uiSettings.cardDensity.pageHorizontalPadding,
                        top = 16.dp,
                        end = uiSettings.cardDensity.pageHorizontalPadding,
                    ),
            ) {
                HeaderCard(
                    onOpenSettings = {
                        showSettings = true
                    },
                )
            }

            Spacer(modifier = Modifier.height(uiSettings.cardDensity.itemSpacing))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                beyondViewportPageCount = 1,
                key = { page ->
                    mainSections[page].name
                },
            ) { page ->
                val pageSection = mainSections[page]

                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize().padding(horizontal = uiSettings.cardDensity.pageHorizontalPadding),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(uiSettings.cardDensity.itemSpacing),
                ) {
                    item { CurrentSectionHeader(section = pageSection) }

                    if (
                        pageSection == MainSection.OVERVIEW
                    ) {
                        item {
                            SetupStatusCard(
                                notificationAccessEnabled = listenerEnabled,
                                notificationsAllowed = notificationsAllowed,
                                shizukuState = shizukuState,
                                onOpenNotificationAccess = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                onRequestNotifications = {
                                    if (
                                        Build.VERSION.SDK_INT >= 33
                                    ) {
                                        setupNotificationPermissionLauncher
                                            .launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onRequestShizukuPermission = {
                                    ShizukuDiagnostics.requestPermission()
                                },
                                onOpenShizuku = {
                                    val launchIntent =
                                        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")

                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    } else {
                                        Toast
                                            .makeText(
                                                context,
                                                resources.getString(R.string.main_toast_shizuku_app_not_found),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                    }
                                },
                            )
                        }

                        item {
                            MonitorCard(
                                monitoring = monitoring,
                                finalizing = stopRequestedAtMillis != null,
                                sessionDurationMillis = stopRequestedAtMillis
                                    ?.let { stopMillis ->
                                        currentSessionStartMillis
                                            ?.let { startMillis -> (stopMillis - startMillis).coerceAtLeast(0L) }
                                    }
                                    ?: currentSessionDurationMillis,
                                onStart = {
                                    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat
                                            .checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED

                                    if (needsPermission) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        startMonitoring(context)
                                    }
                                },
                                onStop = {
                                    val requestedAtMillis = System.currentTimeMillis()

                                    currentSessionDurationMillis = currentSessionStartMillis
                                        ?.let { startMillis -> (requestedAtMillis - startMillis).coerceAtLeast(0L) }
                                        ?: currentSessionDurationMillis

                                    stopMonitoring(context = context, requestedAtMillis = requestedAtMillis)
                                },
                            )
                        }
                    }

                    if (
                        pageSection == MainSection.DIAGNOSTICS
                    ) {
                        item {
                            if (
                                uiSettings.detailLevel == DetailLevel.EXPERT
                            ) {
                                ShizukuWakeLockCard(
                                    state = shizukuState,
                                    diagnostic = wakeLockDiagnostic,
                                    loading = wakeLockLoading,
                                    alarmDiagnostic = wakeupAlarmDiagnostic,
                                    alarmLoading = wakeupAlarmLoading,
                                    jobDiagnostic = backgroundJobDiagnostic,
                                    jobLoading = backgroundJobLoading,
                                    wakeReasonDiagnostic = wakeReasonDiagnostic,
                                    wakeReasonLoading = wakeReasonLoading,
                                    networkStatsDiagnostic = networkStatsDiagnostic,
                                    networkStatsLoading = networkStatsLoading,
                                    onRequestPermission = {
                                        ShizukuDiagnostics.requestPermission()
                                    },
                                    onCheck = {
                                        wakeLockLoading = true

                                        coroutineScope.launch {
                                            wakeLockDiagnostic = ShizukuDiagnostics.readWakeLocks(context)

                                            wakeLockLoading = false
                                        }
                                    },
                                    onAlarmCheck = {
                                        wakeupAlarmLoading = true

                                        coroutineScope.launch {
                                            wakeupAlarmDiagnostic = ShizukuDiagnostics.readWakeupAlarms(context)

                                            wakeupAlarmLoading = false
                                        }
                                    },
                                    onJobCheck = {
                                        backgroundJobLoading = true

                                        coroutineScope.launch {
                                            backgroundJobDiagnostic = ShizukuDiagnostics.readBackgroundJobs(context)

                                            backgroundJobLoading = false
                                        }
                                    },
                                    onWakeReasonCheck = {
                                        wakeReasonLoading = true

                                        coroutineScope.launch {
                                            wakeReasonDiagnostic = ShizukuDiagnostics.readWakeReason(context)

                                            wakeReasonLoading = false
                                        }
                                    },
                                    onNetworkStatsCheck = {
                                        networkStatsLoading = true

                                        coroutineScope.launch {
                                            networkStatsDiagnostic = ShizukuDiagnostics.readNetworkStats(context)

                                            networkStatsLoading = false
                                        }
                                    },
                                )
                            } else {
                                ShizukuProfileHintCard(
                                    state = shizukuState,
                                    detailLevel = uiSettings.detailLevel,
                                    onRequestPermission = {
                                        ShizukuDiagnostics.requestPermission()
                                    },
                                )
                            }
                        }
                    }

                    if (
                        pageSection == MainSection.OVERVIEW && uiSettings.showDailyStatistics
                    ) {
                        item { StatisticsCard(dailyStatistics) }
                    }

                    if (
                        pageSection == MainSection.ANALYSIS && uiSettings.showNightAnalysis
                    ) {
                        item {
                            SleepReportCard(
                                events = events,
                                session = latestSession,
                                monitoring = monitoring,
                                detailLevel = uiSettings.detailLevel,
                            )
                        }
                    }

                    if (
                        pageSection == MainSection.SESSIONS && uiSettings.showNightAnalysis
                    ) {
                        item {
                            SessionComparisonCard(sessions = archivedSessions, detailLevel = uiSettings.detailLevel)
                        }

                        item { AppProfilesCard(sessions = archivedSessions, detailLevel = uiSettings.detailLevel) }

                        item {
                            SessionHistoryCard(
                                sessions = archivedSessions,
                                detailLevel = uiSettings.detailLevel,
                                onSaveNote = viewModel::updateNote,
                                onDelete = viewModel::deleteSession,
                                onDeleteAll = viewModel::clearArchive,
                            )
                        }
                    }

                    if (
                        pageSection == MainSection.ANALYSIS && uiSettings.showSourceStatistics && events.isNotEmpty()
                    ) {
                        item {
                            SourceStatisticsCard(
                                events = events,
                                session = latestSession,
                                detailLevel = uiSettings.detailLevel,
                            )
                        }
                    }

                    if (
                        pageSection == MainSection.ANALYSIS && events.isNotEmpty()
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.main_events_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = if (events.size == filteredEvents.size) {
                                            stringResource(R.string.main_events_saved_count, events.size)
                                        } else {
                                            stringResource(
                                                R.string.main_events_visible_count,
                                                filteredEvents.size,
                                                events.size,
                                            )
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedButton(
                                        onClick = {
                                            eventSectionExpanded = !eventSectionExpanded
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            androidx.compose.ui.graphics
                                                .Color(0xFF687181),
                                        ),
                                        colors = androidx.compose.material3.ButtonDefaults
                                            .outlinedButtonColors(
                                                contentColor = androidx.compose.ui.graphics.Color.White,
                                            ),
                                    ) {
                                        Text(
                                            if (eventSectionExpanded) {
                                                stringResource(R.string.main_events_hide)
                                            } else {
                                                stringResource(R.string.main_events_show)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (
                        pageSection == MainSection.ANALYSIS && eventSectionExpanded && events.isNotEmpty()
                    ) {
                        item {
                            ActionCard(
                                hasEvents = events.isNotEmpty() ||
                                    wakeLockDiagnostic != null ||
                                    wakeupAlarmDiagnostic != null ||
                                    backgroundJobDiagnostic != null ||
                                    wakeReasonDiagnostic != null || networkStatsDiagnostic != null,
                                onExport = {
                                    coroutineScope.launch {
                                        val exportEvents = viewModel.exportEvents()

                                        pendingExportText = buildString {
                                            appendLine(resources.getString(R.string.main_export_kind_technical_report))
                                            appendLine()

                                            append(
                                                TechnicalExport.build(
                                                    context = context,
                                                    events = exportEvents,
                                                    monitoring = monitoring,
                                                ),
                                            )

                                            appendLine()
                                            appendLine()

                                            append(
                                                buildManualDiagnosticsExport(
                                                    context = context,
                                                    wakeLockDiagnostic = wakeLockDiagnostic,
                                                    wakeupAlarmDiagnostic = wakeupAlarmDiagnostic,
                                                    backgroundJobDiagnostic = backgroundJobDiagnostic,
                                                    wakeReasonDiagnostic = wakeReasonDiagnostic,
                                                    networkStatsDiagnostic = networkStatsDiagnostic,
                                                ),
                                            )
                                        }

                                        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

                                        exportLauncher.launch(
                                            resources.getString(
                                                R.string.main_export_technical_report_file_name,
                                                formatter.format(Date()),
                                            ),
                                        )
                                    }
                                },
                                onClear = {
                                    viewModel.clearEvents()

                                    Toast
                                        .makeText(
                                            context,
                                            resources.getString(R.string.main_toast_events_cleared),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                            )
                        }

                        item {
                            EventFilterBar(
                                selectedFilter = selectedFilter,
                                onSelected = { filter ->
                                    selectedFilter = filter

                                    uiPreferences.edit().putString("event_filter", filter.name).apply()
                                },
                            )
                        }

                        item {
                            EventViewModeBar(
                                selectedMode = selectedEventViewMode,
                                onSelected = { mode ->
                                    selectedEventViewMode = mode

                                    uiPreferences.edit().putString("event_view_mode", mode.name).apply()
                                },
                            )
                        }

                        if (filteredEvents.isEmpty()) {
                            item { FilterEmptyCard(monitoring = monitoring, filter = selectedFilter) }
                        } else {
                            when (
                                selectedEventViewMode
                            ) {
                                EventViewMode.LIST -> {
                                    val groupedListItems = buildGroupedEventList(labels, filteredEvents)

                                    items(
                                        items = groupedListItems,
                                        key = {
                                            it.stableKey
                                        },
                                    ) { item ->
                                        when (item) {
                                            is SingleEventListItem -> {
                                                EventCard(event = item.event, detailLevel = uiSettings.detailLevel)
                                            }

                                            is GroupedCpuEventListItem -> {
                                                GroupedCpuEventCard(group = item, detailLevel = uiSettings.detailLevel)
                                            }
                                        }
                                    }
                                }

                                EventViewMode.TIMELINE -> {
                                    item { WakeTimeline(events = filteredEvents, detailLevel = uiSettings.detailLevel) }
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
    onSectionSelected: (MainSection) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        MainSection.entries.forEach { section ->

            NavigationBarItem(
                selected = selectedSection == section,
                onClick = {
                    onSectionSelected(section)
                },
                icon = {
                    Icon(
                        imageVector = when (section) {
                            MainSection.OVERVIEW -> Icons.Filled.Home
                            MainSection.ANALYSIS -> Icons.Filled.Analytics
                            MainSection.SESSIONS -> Icons.Filled.History
                            MainSection.DIAGNOSTICS -> Icons.Filled.Build
                        },
                        contentDescription = stringResource(section.label),
                    )
                },
                label = {
                    Text(
                        text = stringResource(section.label),
                        maxLines = 1,
                        fontWeight = if (
                            selectedSection == section
                        ) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun CurrentSectionHeader(section: MainSection) {
    if (
        section == MainSection.OVERVIEW
    ) {
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        if (
            section != MainSection.OVERVIEW
        ) {
            Text(
                text = stringResource(section.label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(2.dp))
        }

        Text(
            text = when (section) {
                MainSection.OVERVIEW -> stringResource(R.string.main_section_overview_subtitle)
                MainSection.ANALYSIS -> stringResource(R.string.main_section_analysis_subtitle)
                MainSection.SESSIONS -> stringResource(R.string.main_section_sessions_subtitle)
                MainSection.DIAGNOSTICS -> stringResource(R.string.main_section_diagnostics_subtitle)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HeaderCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "wakelogs",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(1.dp))

                Text(
                    text = stringResource(R.string.main_header_version, BuildConfig.VERSION_NAME, "dernikiausd"),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TextButton(
                onClick = onOpenSettings,
            ) {
                Text(
                    text = stringResource(R.string.main_settings),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
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
    onOpenShizuku: () -> Unit,
) {
    val shizukuReady = shizukuState == ShizukuState.RUNNING_GRANTED

    val setupComplete = notificationAccessEnabled && notificationsAllowed && shizukuReady

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = if (setupComplete) {
                    stringResource(R.string.main_setup_complete)
                } else {
                    stringResource(R.string.main_setup_required)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (setupComplete) {
                Text(
                    text = stringResource(R.string.main_setup_all_access_active),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (!notificationAccessEnabled) {
                    SetupRequirementRow(
                        title = stringResource(R.string.main_setup_notification_access_title),
                        description = stringResource(R.string.main_setup_notification_access_description),
                        buttonText = stringResource(R.string.main_setup_notification_access_button),
                        onClick = onOpenNotificationAccess,
                    )
                }

                if (!notificationsAllowed) {
                    SetupRequirementRow(
                        title = stringResource(R.string.main_setup_post_notifications_title),
                        description = stringResource(R.string.main_setup_post_notifications_description),
                        buttonText = stringResource(R.string.main_setup_post_notifications_button),
                        onClick = onRequestNotifications,
                    )
                }

                when (shizukuState) {
                    ShizukuState.RUNNING_GRANTED -> {
                        Unit
                    }

                    ShizukuState.RUNNING_DENIED -> {
                        SetupRequirementRow(
                            title = stringResource(R.string.main_setup_shizuku_permission_title),
                            description = stringResource(R.string.main_setup_shizuku_permission_description),
                            buttonText = stringResource(R.string.main_setup_shizuku_permission_button),
                            onClick = onRequestShizukuPermission,
                        )
                    }

                    ShizukuState.NOT_RUNNING -> {
                        SetupRequirementRow(
                            title = stringResource(R.string.main_setup_shizuku_not_running_title),
                            description = stringResource(R.string.main_setup_shizuku_not_running_description),
                            buttonText = stringResource(R.string.main_setup_shizuku_not_running_button),
                            onClick = onOpenShizuku,
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
    onClick: () -> Unit,
) {
    Spacer(modifier = Modifier.height(12.dp))

    Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)

    Text(
        text = description,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )

    Spacer(modifier = Modifier.height(7.dp))

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
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
                            },
                        ),
                )

                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                ) {
                    Text(
                        text = when {
                            finalizing -> {
                                stringResource(R.string.main_monitor_finalizing)
                            }

                            monitoring -> {
                                stringResource(R.string.main_monitor_running)
                            }

                            else -> {
                                stringResource(R.string.main_monitor_ready)
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = stringResource(R.string.main_monitor_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    if (monitoring) {
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(
                                R.string.main_monitor_session_duration,
                                formatLiveSessionDuration(LocalContext.current, sessionDurationMillis),
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onStart,
                    enabled = !monitoring && !finalizing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Text(text = stringResource(R.string.main_monitor_start), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onStop,
                    enabled = monitoring && !finalizing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Text(text = stringResource(R.string.main_monitor_stop), fontWeight = FontWeight.Bold)
                }
            }

            if (monitoring) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (finalizing) {
                        stringResource(R.string.main_monitor_finalizing_hint)
                    } else {
                        stringResource(R.string.main_monitor_running_hint)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
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
    networkStatsDiagnostic: NetworkStatsDiagnostic?,
): String {
    fun sourceFor(packageName: String?): String = resolveWakeLockSource(context = context, packageName = packageName)

    fun errorText(error: String?): String =
        error?.trim()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.main_diag_unknown_error)

    return buildString {
        appendLine("==================================================")
        appendLine(context.getString(R.string.main_diag_export_title))
        appendLine(context.getString(R.string.main_diag_export_intro))

        appendLine()
        appendLine(context.getString(R.string.main_diag_wakelocks))

        when {
            wakeLockDiagnostic == null -> {
                appendLine(context.getString(R.string.main_diag_status_not_run))
            }

            wakeLockDiagnostic.error != null -> {
                appendLine(context.getString(R.string.main_diag_status_failed))
                appendLine(context.getString(R.string.main_diag_error, errorText(wakeLockDiagnostic.error)))
            }

            else -> {
                appendLine(context.getString(R.string.main_diag_active_wakelocks, wakeLockDiagnostic.activeCount))

                if (
                    wakeLockDiagnostic.rawLastEntry == null
                ) {
                    appendLine(context.getString(R.string.main_diag_last_partial_wakelock_no_data))
                } else {
                    appendLine(context.getString(R.string.main_diag_last_partial_wakelock))
                    appendLine(
                        context.getString(R.string.main_diag_bullet_source, sourceFor(wakeLockDiagnostic.lastPackage)),
                    )
                    appendLine(
                        context.getString(
                            R.string.main_diag_bullet_time,
                            formatWakeLockTimestamp(context, wakeLockDiagnostic.lastTimestamp),
                        ),
                    )
                    appendLine(
                        context.getString(
                            R.string.main_diag_bullet_technical_tag,
                            compactWakeLockTag(context, wakeLockDiagnostic.lastTag),
                        ),
                    )
                }

                appendLine()
                appendLine(context.getString(R.string.main_diag_wakelock_history))

                if (
                    wakeLockDiagnostic.historyEntries.isEmpty()
                ) {
                    appendLine(context.getString(R.string.main_diag_bullet_no_data))
                } else {
                    val groupedEntries = wakeLockDiagnostic
                        .historyEntries
                        .groupBy { entry ->
                            entry.packageName.lowercase() +
                                "|" +
                                compactWakeLockTag(context, entry.tag).lowercase() +
                                "|" + entry.wakeLockType.lowercase()
                        }.values
                        .map { group ->
                            group.sortedByDescending { it.startTimestampMillis }
                        }.sortedByDescending { group ->
                            group.firstOrNull()?.startTimestampMillis
                                ?: 0L
                        }.take(10)

                    groupedEntries.forEach { group ->
                        val entry = group.first()

                        val finishedDurations = group.mapNotNull { it.durationMillis }

                        val durationText = when {
                            finishedDurations.isNotEmpty() &&
                                (
                                    finishedDurations.maxOrNull()
                                        ?: 0L
                                ) < 1_000L -> {
                                context.getString(R.string.main_diag_short_activity)
                            }

                            finishedDurations.isNotEmpty() -> {
                                formatDuration(context, finishedDurations.maxOrNull())
                            }

                            else -> {
                                context.getString(R.string.main_diag_end_not_in_window)
                            }
                        }

                        appendLine("• " + sourceFor(entry.packageName) + " · " + entry.wakeLockType)

                        appendLine(
                            "  " +
                                context.getString(
                                    R.string.main_diag_wakelock_count_start_duration,
                                    group.size,
                                    formatWakeLockTimestamp(context, entry.startTimestamp),
                                    durationText,
                                ),
                        )

                        appendLine(
                            "  " +
                                context.getString(
                                    R.string.main_diag_effect,
                                    if (
                                        group.any { it.causesWake }
                                    ) {
                                        context.getString(R.string.main_diag_effect_can_wake_display)
                                    } else {
                                        context.getString(R.string.main_diag_effect_no_display_wake)
                                    },
                                ),
                        )

                        appendLine(
                            "  " + context.getString(R.string.main_diag_tag, compactWakeLockTag(context, entry.tag)),
                        )
                    }
                }
            }
        }

        appendLine()
        appendLine(context.getString(R.string.main_diag_wakeup_alarms))

        when {
            wakeupAlarmDiagnostic == null -> {
                appendLine(context.getString(R.string.main_diag_status_not_run))
            }

            wakeupAlarmDiagnostic.error != null -> {
                appendLine(context.getString(R.string.main_diag_status_failed))
                appendLine(context.getString(R.string.main_diag_error, errorText(wakeupAlarmDiagnostic.error)))
            }

            wakeupAlarmDiagnostic.packageName == null -> {
                appendLine(context.getString(R.string.main_diag_no_data))
            }

            else -> {
                appendLine(context.getString(R.string.main_diag_source, sourceFor(wakeupAlarmDiagnostic.packageName)))
                appendLine(context.getString(R.string.main_diag_package, wakeupAlarmDiagnostic.packageName))
                appendLine(
                    context.getString(
                        R.string.main_diag_technical_tag,
                        wakeupAlarmDiagnostic.tag
                            ?: context.getString(R.string.main_not_available),
                    ),
                )
                appendLine(context.getString(R.string.main_diag_entry_wakeup_count, wakeupAlarmDiagnostic.wakeCount))
                appendLine(context.getString(R.string.main_diag_package_wakeups, wakeupAlarmDiagnostic.packageWakeups))
            }
        }

        appendLine()
        appendLine(context.getString(R.string.main_diag_background_jobs))

        when {
            backgroundJobDiagnostic == null -> {
                appendLine(context.getString(R.string.main_diag_status_not_run))
            }

            backgroundJobDiagnostic.error != null -> {
                appendLine(context.getString(R.string.main_diag_status_failed))
                appendLine(context.getString(R.string.main_diag_error, errorText(backgroundJobDiagnostic.error)))
            }

            backgroundJobDiagnostic.packageName == null -> {
                appendLine(context.getString(R.string.main_diag_no_data))
            }

            else -> {
                appendLine(context.getString(R.string.main_diag_source, sourceFor(backgroundJobDiagnostic.packageName)))
                appendLine(context.getString(R.string.main_diag_package, backgroundJobDiagnostic.packageName))

                if (
                    !backgroundJobDiagnostic.rawEntry.isNullOrBlank()
                ) {
                    appendLine(
                        context.getString(R.string.main_diag_technical_entry, backgroundJobDiagnostic.rawEntry.trim()),
                    )
                }
            }
        }

        appendLine()
        appendLine(context.getString(R.string.main_diag_wake_reason))

        when {
            wakeReasonDiagnostic == null -> {
                appendLine(context.getString(R.string.main_diag_status_not_run))
            }

            wakeReasonDiagnostic.error != null -> {
                appendLine(context.getString(R.string.main_diag_status_failed))
                appendLine(context.getString(R.string.main_diag_error, errorText(wakeReasonDiagnostic.error)))
            }

            wakeReasonDiagnostic.rawEntry == null -> {
                appendLine(context.getString(R.string.main_diag_no_data))
            }

            else -> {
                appendLine(
                    context.getString(
                        R.string.main_diag_time,
                        formatWakeLockTimestamp(context, wakeReasonDiagnostic.timestamp),
                    ),
                )
                appendLine(
                    context.getString(
                        R.string.main_diag_technical_reason,
                        wakeReasonDiagnostic.reason
                            ?: context.getString(R.string.main_unknown_lowercase),
                    ),
                )
                appendLine(
                    context.getString(
                        R.string.main_diag_details,
                        compactWakeReasonDetails(context, wakeReasonDiagnostic.details),
                    ),
                )
            }
        }

        appendLine()
        appendLine(context.getString(R.string.main_diag_network_since_boot))

        when {
            networkStatsDiagnostic == null -> {
                appendLine(context.getString(R.string.main_diag_status_not_run))
            }

            networkStatsDiagnostic.error != null -> {
                appendLine(context.getString(R.string.main_diag_status_failed))
                appendLine(context.getString(R.string.main_diag_error, errorText(networkStatsDiagnostic.error)))
            }

            networkStatsDiagnostic.entries.isEmpty() -> {
                appendLine(context.getString(R.string.main_diag_no_data))
            }

            else -> {
                appendLine(context.getString(R.string.main_diag_network_hint))

                networkStatsDiagnostic
                    .entries
                    .take(10)
                    .forEach { entry ->
                        val displayName = SourceLabelResolver.get(context).networkLabel(entry.packageName, entry.uid)

                        appendLine(
                            "• " +
                                context.getString(
                                    R.string.main_diag_network_total,
                                    displayName,
                                    formatNetworkBytes(entry.totalBytes),
                                ),
                        )

                        appendLine(
                            "  " +
                                context.getString(
                                    R.string.main_diag_network_received_sent,
                                    formatNetworkBytes(entry.rxBytes),
                                    formatNetworkBytes(entry.txBytes),
                                ),
                        )

                        appendLine(
                            "  " +
                                context.getString(
                                    R.string.main_diag_package_uid,
                                    entry.packageName
                                        ?: "UID ${entry.uid}",
                                ),
                        )
                    }
            }
        }

        appendLine()
        append("==================================================")
    }
}

@Composable
private fun ShizukuProfileHintCard(
    state: ShizukuState,
    detailLevel: DetailLevel,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current

    val deviceProfile = androidx.compose.runtime.remember { DeviceProfile.detect(context) }

    val measurementQualityExpanded = remember { mutableStateOf(false) }

    val profileIdentity = (
        deviceProfile.profileLabel + " " + deviceProfile.platformLabel + " " + deviceProfile.manufacturer
    ).lowercase(
        Locale.getDefault(),
    )

    val isSamsungProfile = profileIdentity.contains("samsung")

    val isOnePlusProfile =
        profileIdentity.contains("oneplus") || profileIdentity.contains("oplus") || profileIdentity.contains("oxygenos")

    val isSimple = detailLevel == DetailLevel.SIMPLE

    val statusText = when (state) {
        ShizukuState.RUNNING_GRANTED -> stringResource(R.string.main_shizuku_active)
        ShizukuState.RUNNING_DENIED -> stringResource(R.string.main_shizuku_permission_missing)
        ShizukuState.NOT_RUNNING -> stringResource(R.string.main_setup_shizuku_not_running_title)
    }

    val description = when {
        isSimple && state == ShizukuState.RUNNING_GRANTED -> {
            stringResource(R.string.main_shizuku_hint_simple_granted)
        }

        isSimple -> {
            stringResource(R.string.main_shizuku_hint_simple_limited)
        }

        state == ShizukuState.RUNNING_GRANTED -> {
            stringResource(R.string.main_shizuku_hint_granted)
        }

        state == ShizukuState.RUNNING_DENIED -> {
            stringResource(R.string.main_shizuku_hint_denied)
        }

        else -> {
            stringResource(R.string.main_shizuku_hint_not_running)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = if (isSimple) {
                    stringResource(R.string.main_system_analysis)
                } else {
                    stringResource(R.string.main_shizuku_system_analysis)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusText,
                color = when (state) {
                    ShizukuState.RUNNING_GRANTED -> Color(0xFF35A853)
                    ShizukuState.RUNNING_DENIED -> MaterialTheme.colorScheme.error
                    ShizukuState.NOT_RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = deviceProfile.profileLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = deviceProfile.manufacturer + " " + deviceProfile.model + " · " + deviceProfile.platformLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme
                        .colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.52f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.main_measurement_quality),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = when {
                                    isOnePlusProfile -> {
                                        stringResource(R.string.main_measurement_quality_oneplus)
                                    }

                                    isSamsungProfile -> {
                                        stringResource(R.string.main_measurement_quality_samsung)
                                    }

                                    else -> {
                                        stringResource(R.string.main_measurement_quality_generic)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = if (
                                measurementQualityExpanded.value
                            ) {
                                "▲"
                            } else {
                                "▼"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    OutlinedButton(
                        onClick = {
                            measurementQualityExpanded.value = !measurementQualityExpanded.value
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (
                                measurementQualityExpanded.value
                            ) {
                                stringResource(R.string.main_measurement_quality_hide)
                            } else {
                                stringResource(R.string.main_measurement_quality_show)
                            },
                        )
                    }

                    if (
                        measurementQualityExpanded.value
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))

                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(8.dp))

                        MeasurementQualityRow(
                            label = stringResource(R.string.main_quality_display_activity),
                            quality = R.string.main_quality_good,
                        )

                        MeasurementQualityRow(
                            label = stringResource(R.string.main_quality_display_wake_reason),
                            quality = when {
                                isOnePlusProfile -> {
                                    R.string.main_quality_good
                                }

                                isSamsungProfile -> {
                                    R.string.main_quality_limited
                                }

                                else -> {
                                    R.string.main_quality_device_dependent
                                }
                            },
                        )

                        MeasurementQualityRow(
                            label = stringResource(R.string.main_quality_background_activity),
                            quality = when {
                                isOnePlusProfile -> {
                                    R.string.main_quality_good
                                }

                                isSamsungProfile -> {
                                    R.string.main_quality_limited
                                }

                                else -> {
                                    R.string.main_quality_device_dependent
                                }
                            },
                        )

                        MeasurementQualityRow(
                            label = stringResource(R.string.main_quality_network_activity),
                            quality = R.string.main_quality_good,
                        )

                        MeasurementQualityRow(
                            label = stringResource(R.string.main_quality_session_comparison),
                            quality = R.string.main_quality_good,
                        )
                    }
                }
            }

            if (
                state == ShizukuState.RUNNING_DENIED
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.main_setup_shizuku_permission_button))
                }
            }
        }
    }
}

@Composable
private fun MeasurementQualityRow(
    label: String,
    @StringRes quality: Int,
) {
    val qualityColor = when (quality) {
        R.string.main_quality_good -> {
            Color(0xFF35A853)
        }

        R.string.main_quality_limited -> {
            MaterialTheme.colorScheme.error
        }

        else -> {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = stringResource(quality),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = qualityColor,
            textAlign = TextAlign.End,
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
    onNetworkStatsCheck: () -> Unit,
) {
    val context = LocalContext.current

    val diagnosticsExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val visibleDiagnosticPanel = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.main_shizuku_system_diagnostics),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (state) {
                    ShizukuState.RUNNING_GRANTED -> stringResource(R.string.main_shizuku_ready_diagnostics_available)
                    ShizukuState.RUNNING_DENIED -> stringResource(R.string.main_shizuku_running_permission_missing)
                    ShizukuState.NOT_RUNNING -> stringResource(R.string.main_shizuku_not_running)
                },
                color = when (state) {
                    ShizukuState.RUNNING_GRANTED -> Color(0xFF35A853)
                    ShizukuState.RUNNING_DENIED -> MaterialTheme.colorScheme.error
                    ShizukuState.NOT_RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(14.dp))

            when (state) {
                ShizukuState.RUNNING_GRANTED -> {
                    Button(
                        onClick = {
                            diagnosticsExpanded.value = !diagnosticsExpanded.value

                            if (!diagnosticsExpanded.value) {
                                visibleDiagnosticPanel.value = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            if (diagnosticsExpanded.value) {
                                stringResource(R.string.main_diagnostics_hide)
                            } else {
                                stringResource(R.string.main_diagnostics_open)
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (diagnosticsExpanded.value) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value == "wakelocks"
                                ) {
                                    visibleDiagnosticPanel.value = null
                                } else {
                                    visibleDiagnosticPanel.value = "wakelocks"
                                    onCheck()
                                }
                            },
                            enabled =
                                !loading && !alarmLoading && !jobLoading && !wakeReasonLoading && !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (visibleDiagnosticPanel.value == "wakelocks") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics
                                        .Color(0xFF687181)
                                },
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (visibleDiagnosticPanel.value == "wakelocks") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                contentColor = if (visibleDiagnosticPanel.value == "wakelocks") {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    androidx.compose.ui.graphics.Color.White
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                when {
                                    loading -> {
                                        stringResource(R.string.main_checking)
                                    }

                                    visibleDiagnosticPanel.value ==
                                        "wakelocks" -> {
                                        stringResource(R.string.main_wakelocks_hide)
                                    }

                                    else -> {
                                        stringResource(R.string.main_wakelocks_check)
                                    }
                                },
                            )
                        }

                        if (
                            diagnostic != null && visibleDiagnosticPanel.value == "wakelocks"
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            if (diagnostic.error != null) {
                                Text(
                                    text = diagnostic.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.main_diag_active_wakelocks, diagnostic.activeCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                if (
                                    diagnostic.rawLastEntry == null
                                ) {
                                    Text(
                                        text = stringResource(R.string.main_diag_no_data),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else {
                                    val source =
                                        resolveWakeLockSource(context = context, packageName = diagnostic.lastPackage)

                                    val kind = classifyWakeLockTag(context, diagnostic.lastTag)

                                    val timestamp = formatWakeLockTimestamp(context, diagnostic.lastTimestamp)

                                    Text(
                                        text = stringResource(R.string.main_last_partial_wakelock),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = stringResource(R.string.main_diag_source, source),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Text(
                                        text = stringResource(R.string.main_kind, kind),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Text(
                                        text = stringResource(R.string.main_diag_time, timestamp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Text(
                                        text = stringResource(
                                            R.string.main_diag_technical_tag,
                                            compactWakeLockTag(context, diagnostic.lastTag),
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        if (
                            diagnostic != null &&
                            diagnostic.error == null &&
                            diagnostic.historyEntries.isNotEmpty() && visibleDiagnosticPanel.value == "wakelocks"
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = stringResource(R.string.main_wakelock_history_recent),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val groupedHistoryEntries = diagnostic.historyEntries
                                .groupBy { entry ->
                                    entry.packageName.lowercase() +
                                        "|" +
                                        compactWakeLockTag(context, entry.tag).lowercase() +
                                        "|" + entry.wakeLockType.lowercase()
                                }.values
                                .map { group ->
                                    group.sortedByDescending { it.startTimestampMillis }
                                }.sortedByDescending { group ->
                                    group.firstOrNull()?.startTimestampMillis
                                        ?: 0L
                                }.take(5)

                            groupedHistoryEntries
                                .forEachIndexed { index, group ->
                                    val entry = group.first()

                                    val count = group.size

                                    val source =
                                        resolveWakeLockSource(context = context, packageName = entry.packageName)

                                    val hasFinishedEntry = group.any { !it.stillActive }

                                    val longestDuration = group
                                        .mapNotNull {
                                            it.durationMillis
                                        }.maxOrNull()

                                    val durationText = when {
                                        hasFinishedEntry &&
                                            (longestDuration ?: 0L) < 1_000L -> {
                                            stringResource(R.string.main_diag_short_activity)
                                        }

                                        hasFinishedEntry -> {
                                            formatDuration(context, longestDuration)
                                        }

                                        else -> {
                                            stringResource(R.string.main_diag_end_not_in_window)
                                        }
                                    }

                                    val startText = stringResource(
                                        R.string.main_wakelock_start_duration,
                                        formatWakeLockTimestamp(context, entry.startTimestamp),
                                        durationText,
                                    )

                                    Text(
                                        text = source + " · " + entry.wakeLockType,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Text(
                                        text = if (count > 1) {
                                            stringResource(R.string.main_count_prefixed, count, startText)
                                        } else {
                                            startText
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Text(
                                        text = if (
                                            group.any { it.causesWake }
                                        ) {
                                            stringResource(
                                                R.string.main_wakelock_display_wake_tag,
                                                compactWakeLockTag(context, entry.tag),
                                            )
                                        } else {
                                            stringResource(
                                                R.string.main_diag_tag,
                                                compactWakeLockTag(context, entry.tag),
                                            )
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )

                                    if (
                                        index <
                                        groupedHistoryEntries.lastIndex
                                    ) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value == "alarms"
                                ) {
                                    visibleDiagnosticPanel.value = null
                                } else {
                                    visibleDiagnosticPanel.value = "alarms"
                                    onAlarmCheck()
                                }
                            },
                            enabled =
                                !loading && !alarmLoading && !jobLoading && !wakeReasonLoading && !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (visibleDiagnosticPanel.value == "alarms") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics
                                        .Color(0xFF687181)
                                },
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (visibleDiagnosticPanel.value == "alarms") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                contentColor = if (visibleDiagnosticPanel.value == "alarms") {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    androidx.compose.ui.graphics.Color.White
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                when {
                                    alarmLoading -> {
                                        stringResource(R.string.main_wakeup_alarms_checking)
                                    }

                                    visibleDiagnosticPanel.value ==
                                        "alarms" -> {
                                        stringResource(R.string.main_wakeup_alarms_hide)
                                    }

                                    else -> {
                                        stringResource(R.string.main_wakeup_alarms_check)
                                    }
                                },
                            )
                        }

                        if (
                            alarmDiagnostic != null && visibleDiagnosticPanel.value == "alarms"
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            if (alarmDiagnostic.error != null) {
                                Text(
                                    text = alarmDiagnostic.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (
                                alarmDiagnostic.packageName == null
                            ) {
                                Text(
                                    text = stringResource(R.string.main_diag_no_data),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                val source =
                                    resolveWakeLockSource(context = context, packageName = alarmDiagnostic.packageName)

                                Text(
                                    text = stringResource(R.string.main_last_wakeup_alarm),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(R.string.main_diag_source, source),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_last_ago,
                                        formatDuration(context, alarmDiagnostic.ageMillis),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_wakeups_since_stats_start,
                                        alarmDiagnostic.wakeCount,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_diag_technical_tag,
                                        compactAlarmTag(context, alarmDiagnostic.tag),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value == "jobs"
                                ) {
                                    visibleDiagnosticPanel.value = null
                                } else {
                                    visibleDiagnosticPanel.value = "jobs"
                                    onJobCheck()
                                }
                            },
                            enabled =
                                !loading && !alarmLoading && !jobLoading && !wakeReasonLoading && !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (visibleDiagnosticPanel.value == "jobs") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics
                                        .Color(0xFF687181)
                                },
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (visibleDiagnosticPanel.value == "jobs") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                contentColor = if (visibleDiagnosticPanel.value == "jobs") {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    androidx.compose.ui.graphics.Color.White
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                when {
                                    jobLoading -> {
                                        stringResource(R.string.main_background_jobs_checking)
                                    }

                                    visibleDiagnosticPanel.value ==
                                        "jobs" -> {
                                        stringResource(R.string.main_background_jobs_hide)
                                    }

                                    else -> {
                                        stringResource(R.string.main_background_jobs_check)
                                    }
                                },
                            )
                        }

                        if (
                            jobDiagnostic != null && visibleDiagnosticPanel.value == "jobs"
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            if (jobDiagnostic.error != null) {
                                Text(
                                    text = jobDiagnostic.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (
                                jobDiagnostic.packageName == null
                            ) {
                                Text(
                                    text = stringResource(R.string.main_diag_no_data),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                val source =
                                    resolveWakeLockSource(context = context, packageName = jobDiagnostic.packageName)

                                Text(
                                    text = stringResource(R.string.main_last_started_background_job),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(R.string.main_diag_source, source),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_started_ago,
                                        formatDuration(context, jobDiagnostic.ageMillis),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_start_type,
                                        if (
                                            jobDiagnostic.prioritized
                                        ) {
                                            stringResource(R.string.main_start_type_prioritized)
                                        } else {
                                            stringResource(R.string.main_start_type_regular)
                                        },
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_service,
                                        compactJobService(context, jobDiagnostic.serviceName),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value == "wake_reason"
                                ) {
                                    visibleDiagnosticPanel.value = null
                                } else {
                                    visibleDiagnosticPanel.value = "wake_reason"
                                    onWakeReasonCheck()
                                }
                            },
                            enabled =
                                !loading && !alarmLoading && !jobLoading && !wakeReasonLoading && !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (visibleDiagnosticPanel.value == "wake_reason") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics
                                        .Color(0xFF687181)
                                },
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (visibleDiagnosticPanel.value == "wake_reason") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                contentColor = if (visibleDiagnosticPanel.value == "wake_reason") {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    androidx.compose.ui.graphics.Color.White
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                when {
                                    wakeReasonLoading -> {
                                        stringResource(R.string.main_wake_reason_checking)
                                    }

                                    visibleDiagnosticPanel.value ==
                                        "wake_reason" -> {
                                        stringResource(R.string.main_wake_reason_hide)
                                    }

                                    else -> {
                                        stringResource(R.string.main_wake_reason_check)
                                    }
                                },
                            )
                        }

                        if (
                            wakeReasonDiagnostic != null && visibleDiagnosticPanel.value == "wake_reason"
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            if (
                                wakeReasonDiagnostic.error != null
                            ) {
                                Text(
                                    text = wakeReasonDiagnostic.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (
                                wakeReasonDiagnostic.rawEntry == null
                            ) {
                                Text(
                                    text = stringResource(R.string.main_diag_no_data),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.main_last_direct_wake_reason),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(
                                        R.string.main_cause,
                                        readableWakeReason(context, wakeReasonDiagnostic),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_diag_time,
                                        formatWakeLockTimestamp(context, wakeReasonDiagnostic.timestamp),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_diag_technical_reason,
                                        wakeReasonDiagnostic.reason
                                            ?: stringResource(R.string.main_unknown_lowercase),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_diag_details,
                                        compactWakeReasonDetails(context, wakeReasonDiagnostic.details),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value == "network"
                                ) {
                                    visibleDiagnosticPanel.value = null
                                } else {
                                    visibleDiagnosticPanel.value = "network"
                                    onNetworkStatsCheck()
                                }
                            },
                            enabled =
                                !loading && !alarmLoading && !jobLoading && !wakeReasonLoading && !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (visibleDiagnosticPanel.value == "network") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics
                                        .Color(0xFF687181)
                                },
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (visibleDiagnosticPanel.value == "network") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                contentColor = if (visibleDiagnosticPanel.value == "network") {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    androidx.compose.ui.graphics.Color.White
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                when {
                                    networkStatsLoading -> {
                                        stringResource(R.string.main_network_checking)
                                    }

                                    visibleDiagnosticPanel.value ==
                                        "network" -> {
                                        stringResource(R.string.main_network_hide)
                                    }

                                    else -> {
                                        stringResource(R.string.main_network_check)
                                    }
                                },
                            )
                        }

                        if (
                            networkStatsDiagnostic != null && visibleDiagnosticPanel.value == "network"
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            if (networkStatsDiagnostic.error != null) {
                                Text(
                                    text = networkStatsDiagnostic.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (
                                networkStatsDiagnostic.entries.isEmpty()
                            ) {
                                Text(
                                    text = stringResource(R.string.main_diag_no_data),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.main_diag_network_since_boot),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(R.string.main_diag_network_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                networkStatsDiagnostic.entries
                                    .take(5)
                                    .forEachIndexed { index, entry ->
                                        val source = entry.appLabel
                                            ?: if (
                                                entry.packageName != null
                                            ) {
                                                resolveWakeLockSource(
                                                    context = context,
                                                    packageName = entry.packageName,
                                                )
                                            } else {
                                                SourceLabelResolver.get(context).uidLabel(entry.uid)
                                            }

                                        Text(
                                            text = source + " · " + formatNetworkBytes(entry.totalBytes),
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall,
                                        )

                                        Text(
                                            text = stringResource(
                                                R.string.main_diag_network_received_sent,
                                                formatNetworkBytes(entry.rxBytes),
                                                formatNetworkBytes(entry.txBytes),
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                        )

                                        Text(
                                            text = (entry.packageName ?: "UID " + entry.uid),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )

                                        if (
                                            index <
                                            networkStatsDiagnostic.entries.take(5).lastIndex
                                        ) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                            }
                        }
                    }
                }

                ShizukuState.RUNNING_DENIED -> {
                    OutlinedButton(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.main_shizuku_allow))
                    }
                }

                ShizukuState.NOT_RUNNING -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.main_shizuku_start))
                    }
                }
            }
        }
    }
}

private data class SessionComparisonData(
    val latest: ArchivedSession,
    val previous: ArchivedSession,
    val summary: String,
)

@Composable
private fun SessionComparisonCard(
    sessions: List<ArchivedSession>,
    detailLevel: DetailLevel,
) {
    val context = LocalContext.current

    val comparison = remember(sessions) {
        if (sessions.size < 2) {
            null
        } else {
            val latest = sessions[0]

            val previous = sessions[1]

            SessionComparisonData(
                latest = latest,
                previous = previous,
                summary = buildSessionComparisonSummary(context = context, latest = latest, previous = previous),
            )
        }
    }

    if (comparison == null) {
        return
    }

    val latest = comparison.latest

    val previous = comparison.previous

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.main_session_comparison),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.main_session_comparison_versus,
                    formatComparisonSessionTime(latest.startMillis),
                    formatComparisonSessionTime(previous.startMillis),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                ),
            ) {
                Text(
                    text = comparison.summary,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SessionComparisonHeader()

            HorizontalDivider()

            SessionComparisonRow(
                label = stringResource(R.string.main_metric_duration),
                latest = formatComparisonDuration(latest.durationMillis),
                previous = formatComparisonDuration(previous.durationMillis),
                change = formatDurationChange(latest.durationMillis - previous.durationMillis),
                changeValue = (latest.durationMillis - previous.durationMillis).toDouble(),
            )

            SessionComparisonRow(
                label = stringResource(R.string.main_metric_screen_on),
                latest = latest.displayWakeups.toString(),
                previous = previous.displayWakeups.toString(),
                change = formatCountChange(latest.displayWakeups - previous.displayWakeups),
                changeValue = (latest.displayWakeups - previous.displayWakeups).toDouble(),
                meaning = ComparisonChangeMeaning.LOWER_IS_BETTER,
            )

            SessionComparisonRow(
                label = stringResource(R.string.main_metric_cpu_wakes),
                latest = latest.cpuWakeups.toString(),
                previous = previous.cpuWakeups.toString(),
                change = formatCountChange(latest.cpuWakeups - previous.cpuWakeups),
                changeValue = (latest.cpuWakeups - previous.cpuWakeups).toDouble(),
                meaning = ComparisonChangeMeaning.LOWER_IS_BETTER,
            )

            SessionComparisonRow(
                label = stringResource(R.string.main_metric_network),
                latest = formatNetworkBytes(latest.networkTotalBytes),
                previous = formatNetworkBytes(previous.networkTotalBytes),
                change = formatNetworkChange(latest.networkTotalBytes - previous.networkTotalBytes),
                changeValue = (latest.networkTotalBytes - previous.networkTotalBytes).toDouble(),
            )

            if (
                detailLevel != DetailLevel.SIMPLE
            ) {
                val latestNetworkPerMinute =
                    networkBytesPerMinute(bytes = latest.networkTotalBytes, durationMillis = latest.durationMillis)

                val previousNetworkPerMinute =
                    networkBytesPerMinute(bytes = previous.networkTotalBytes, durationMillis = previous.durationMillis)

                SessionComparisonRow(
                    label = stringResource(R.string.main_metric_network_per_minute),
                    latest = formatNetworkBytes(latestNetworkPerMinute),
                    previous = formatNetworkBytes(previousNetworkPerMinute),
                    change = formatNetworkChange(latestNetworkPerMinute - previousNetworkPerMinute),
                    changeValue = (latestNetworkPerMinute - previousNetworkPerMinute).toDouble(),
                )
                SessionComparisonRow(
                    label = stringResource(R.string.main_metric_active_apps),
                    latest = latest.networkActiveApps.toString(),
                    previous = previous.networkActiveApps.toString(),
                    change = formatCountChange(latest.networkActiveApps - previous.networkActiveApps),
                    changeValue = (latest.networkActiveApps - previous.networkActiveApps).toDouble(),
                )
            }

            if (
                detailLevel == DetailLevel.EXPERT
            ) {
                SessionComparisonRow(
                    label = stringResource(R.string.main_metric_received),
                    latest = formatNetworkBytes(latest.networkRxBytes),
                    previous = formatNetworkBytes(previous.networkRxBytes),
                    change = formatNetworkChange(latest.networkRxBytes - previous.networkRxBytes),
                    changeValue = (latest.networkRxBytes - previous.networkRxBytes).toDouble(),
                )

                SessionComparisonRow(
                    label = stringResource(R.string.main_metric_sent),
                    latest = formatNetworkBytes(latest.networkTxBytes),
                    previous = formatNetworkBytes(previous.networkTxBytes),
                    change = formatNetworkChange(latest.networkTxBytes - previous.networkTxBytes),
                    changeValue = (latest.networkTxBytes - previous.networkTxBytes).toDouble(),
                )
            }

            val latestTopApp = latest.topApps.firstOrNull()

            val previousTopApp = previous.topApps.firstOrNull()

            if (
                latestTopApp != null || previousTopApp != null
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.main_most_active_apps),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                SessionTopAppRow(label = stringResource(R.string.main_latest_session), app = latestTopApp)

                SessionTopAppRow(label = stringResource(R.string.main_previous_session), app = previousTopApp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.main_session_comparison_disclaimer),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SessionComparisonHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.main_comparison_metric),
            modifier = Modifier.weight(1.25f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )

        Text(
            text = stringResource(R.string.main_comparison_now),
            modifier = Modifier.weight(0.8f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )

        Text(
            text = stringResource(R.string.main_comparison_before),
            modifier = Modifier.weight(0.8f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )

        Text(
            text = stringResource(R.string.main_comparison_change),
            modifier = Modifier.weight(0.9f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )
    }
}

private enum class ComparisonChangeMeaning {
    LOWER_IS_BETTER,
    NEUTRAL,
}

@Composable
private fun SessionComparisonRow(
    label: String,
    latest: String,
    previous: String,
    change: String,
    changeValue: Double = 0.0,
    meaning: ComparisonChangeMeaning = ComparisonChangeMeaning.NEUTRAL,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1.25f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = latest,
            modifier = Modifier.weight(0.8f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = previous,
            modifier = Modifier.weight(0.8f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val changeColor = when {
            changeValue == 0.0 -> {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            meaning == ComparisonChangeMeaning.LOWER_IS_BETTER && changeValue < 0.0 -> {
                MaterialTheme.colorScheme.primary
            }

            meaning == ComparisonChangeMeaning.LOWER_IS_BETTER && changeValue > 0.0 -> {
                MaterialTheme.colorScheme.error
            }

            else -> {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        }

        Text(
            text = change,
            modifier = Modifier.weight(0.9f),
            color = changeColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun SessionTopAppRow(
    label: String,
    app: ArchivedSessionApp?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )

        Spacer(modifier = Modifier.height(3.dp))

        if (app == null) {
            Text(
                text = stringResource(R.string.main_no_data_short),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                text = app.name,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = formatNetworkBytes(app.totalBytes),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun buildSessionComparisonSummary(
    context: Context,
    latest: ArchivedSession,
    previous: ArchivedSession,
): String {
    val displayDelta = latest.displayWakeups -
        previous.displayWakeups

    val cpuDelta = latest.cpuWakeups -
        previous.cpuWakeups

    val latestDurationHours = latest.durationMillis.coerceAtLeast(1L) /
        3_600_000.0

    val previousDurationHours = previous.durationMillis.coerceAtLeast(1L) /
        3_600_000.0

    val latestWakeRate = (latest.displayWakeups + latest.cpuWakeups) / latestDurationHours

    val previousWakeRate = (previous.displayWakeups + previous.cpuWakeups) / previousDurationHours

    return when {
        latest.displayWakeups == 0 &&
            latest.cpuWakeups == 0 &&
            previous.displayWakeups == 0 &&
            previous.cpuWakeups == 0 -> {
            context.getString(R.string.main_comparison_summary_no_wakeups)
        }

        latestWakeRate <
            previousWakeRate * 0.75 -> {
            context.getString(R.string.main_comparison_summary_much_calmer)
        }

        latestWakeRate >
            previousWakeRate * 1.25 -> {
            context.getString(R.string.main_comparison_summary_more_interruptions)
        }

        displayDelta < 0 || cpuDelta < 0 -> {
            context.getString(R.string.main_comparison_summary_slightly_calmer)
        }

        displayDelta > 0 || cpuDelta > 0 -> {
            context.getString(R.string.main_comparison_summary_slightly_more_activity)
        }

        else -> {
            context.getString(R.string.main_comparison_summary_similar)
        }
    }
}

private fun formatComparisonSessionTime(timestamp: Long): String = SimpleDateFormat("dd.MM. · HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatComparisonDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) /
        1_000L

    val minutes = totalSeconds / 60L

    val seconds = totalSeconds % 60L

    return if (minutes > 0L) {
        "$minutes:${seconds.toString().padStart(2, '0')} min"
    } else {
        "$seconds s"
    }
}

@Composable
private fun formatDurationChange(deltaMillis: Long): String {
    if (
        kotlin.math.abs(deltaMillis) <
        1_000L
    ) {
        return stringResource(R.string.main_change_equal)
    }

    val prefix = if (deltaMillis > 0L) {
        "+"
    } else {
        "−"
    }

    return prefix + formatComparisonDuration(kotlin.math.abs(deltaMillis))
}

@Composable
private fun formatCountChange(delta: Int): String =
    when {
        delta > 0 -> {
            "+$delta"
        }

        delta < 0 -> {
            "−${kotlin.math.abs(delta)}"
        }

        else -> {
            stringResource(R.string.main_change_equal)
        }
    }

private fun networkBytesPerMinute(
    bytes: Long,
    durationMillis: Long,
): Long {
    if (
        bytes <= 0L || durationMillis <= 0L
    ) {
        return 0L
    }

    val minutes = durationMillis / 60_000.0

    if (
        minutes <= 0.0 || !minutes.isFinite()
    ) {
        return 0L
    }

    val result = bytes / minutes

    if (
        !result.isFinite() || result <= 0.0 || result >
        Long.MAX_VALUE.toDouble()
    ) {
        return 0L
    }

    return result.toLong()
}

@Composable
private fun formatNetworkChange(deltaBytes: Long): String =
    when {
        deltaBytes > 0L -> {
            "+" + formatNetworkBytes(deltaBytes)
        }

        deltaBytes < 0L -> {
            "−" + formatNetworkBytes(kotlin.math.abs(deltaBytes))
        }

        else -> {
            stringResource(R.string.main_change_equal)
        }
    }

@Composable
private fun SessionHistoryCard(
    sessions: List<ArchivedSession>,
    detailLevel: DetailLevel,
    onSaveNote: (sessionId: Long, note: String?) -> Unit,
    onDelete: (sessionId: Long) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val showDeleteAllDialog = remember { androidx.compose.runtime.mutableStateOf(false) }

    if (sessions.isEmpty()) {
        return
    }

    val expanded = remember { androidx.compose.runtime.mutableStateOf(false) }

    val visibleSessions = if (expanded.value) {
        sessions
    } else {
        sessions.take(3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.main_session_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = pluralStringResource(R.plurals.main_sessions_saved, sessions.size, sessions.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(14.dp))

            visibleSessions.forEachIndexed {
                index,
                session,
                ->

                SessionHistoryEntry(
                    session = session,
                    detailLevel = detailLevel,
                    onSaveNote = { note ->
                        onSaveNote(session.id, note)
                    },
                    onDelete = {
                        onDelete(session.id)
                    },
                )

                if (
                    index <
                    visibleSessions.lastIndex
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (sessions.size > 3) {
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        expanded.value = !expanded.value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        androidx.compose.ui.graphics
                            .Color(0xFF687181),
                    ),
                    colors =
                        ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color.White),
                ) {
                    Text(
                        text = if (expanded.value) {
                            stringResource(R.string.main_sessions_show_less)
                        } else {
                            stringResource(R.string.main_sessions_show_all)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            androidx.compose.material3
                .OutlinedButton(
                    onClick = {
                        showDeleteAllDialog.value = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = stringResource(R.string.main_sessions_delete_all))
                }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.main_sessions_storage_limit),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    if (showDeleteAllDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showDeleteAllDialog.value = false
            },
            title = {
                Text(text = stringResource(R.string.main_sessions_delete_all_title))
            },
            text = {
                Text(text = stringResource(R.string.main_sessions_delete_all_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog.value = false

                        expanded.value = false

                        onDeleteAll()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = stringResource(R.string.main_delete_all))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog.value = false
                    },
                ) {
                    Text(text = stringResource(R.string.main_cancel))
                }
            },
        )
    }
}

@Composable
private fun SessionHistoryEntry(
    session: ArchivedSession,
    detailLevel: DetailLevel,
    onSaveNote: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    val resources = LocalResources.current

    val pendingSessionExport = remember(session.id) { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val sessionExportLauncher = androidx.activity.compose
        .rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts
                .CreateDocument("text/plain"),
        ) { uri ->
            val exportText = pendingSessionExport.value

            if (
                uri != null && exportText != null
            ) {
                val succeeded = runCatching {
                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use { writer -> writer.write(exportText) }
                        ?: error(resources.getString(R.string.main_error_output_file_open_failed))
                }.isSuccess

                android.widget.Toast
                    .makeText(
                        context,
                        if (succeeded) {
                            resources.getString(R.string.main_toast_session_export_saved)
                        } else {
                            resources.getString(R.string.main_toast_session_export_failed)
                        },
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
            }

            pendingSessionExport.value = null
        }

    val expanded = remember(session.id) { androidx.compose.runtime.mutableStateOf(false) }

    val showDeleteDialog = remember(session.id) { androidx.compose.runtime.mutableStateOf(false) }

    val showNoteDialog = remember(session.id) { androidx.compose.runtime.mutableStateOf(false) }

    val normalizedNote =
        session.note?.trim()?.takeUnless { note -> note.isBlank() || note.equals("null", ignoreCase = true) }

    val noteDraft = remember(
        session.id,
        session.note,
    ) {
        androidx.compose.runtime.mutableStateOf(normalizedNote.orEmpty())
    }

    val topApp = session.topApps.firstOrNull()

    Card(
        onClick = {
            expanded.value = !expanded.value
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = formatSessionHistoryDate(session.startMillis),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = formatSessionHistoryTimeRange(
                            startMillis = session.startMillis,
                            endMillis = session.endMillis,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    normalizedNote
                        ?.let { note ->
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = note,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = formatComparisonDuration(session.durationMillis),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = if (expanded.value) {
                            stringResource(R.string.main_details_hide)
                        } else {
                            stringResource(R.string.main_details_show)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionHistoryMetric(
                    value = session.displayWakeups.toString(),
                    label = stringResource(R.string.main_metric_display),
                    modifier = Modifier.weight(1f),
                )

                SessionHistoryMetric(
                    value = session.cpuWakeups.toString(),
                    label = stringResource(R.string.main_metric_cpu),
                    modifier = Modifier.weight(1f),
                )

                SessionHistoryMetric(
                    value = formatNetworkBytes(session.networkTotalBytes),
                    label = stringResource(R.string.main_metric_network),
                    modifier = Modifier.weight(1.2f),
                )
            }

            topApp?.let { app ->
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.main_most_active_app),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = app.name,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = formatNetworkBytes(app.totalBytes),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }

            if (expanded.value) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(10.dp))

                SessionHistoryValueRow(
                    label = stringResource(R.string.main_metric_active_apps),
                    value = session.networkActiveApps.toString(),
                )

                SessionHistoryValueRow(
                    label = stringResource(R.string.main_metric_received),
                    value = formatNetworkBytes(session.networkRxBytes),
                )

                SessionHistoryValueRow(
                    label = stringResource(R.string.main_metric_sent),
                    value = formatNetworkBytes(session.networkTxBytes),
                )

                SessionHistoryValueRow(
                    label = stringResource(R.string.main_metric_network_per_minute),
                    value = formatNetworkBytes(
                        networkBytesPerMinute(
                            bytes = session.networkTotalBytes,
                            durationMillis = session.durationMillis,
                        ),
                    ),
                )

                if (
                    detailLevel == DetailLevel.EXPERT && session.topApps.size > 1
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.main_more_active_apps),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    session.topApps
                        .drop(1)
                        .take(4)
                        .forEach { app ->
                            SessionHistoryValueRow(label = app.name, value = formatNetworkBytes(app.totalBytes))
                        }
                }

                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.material3
                    .OutlinedButton(
                        onClick = {
                            pendingSessionExport.value = buildSessionExportText(context, session)

                            sessionExportLauncher.launch(buildSessionExportFileName(context, session))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.main_session_summary))
                    }

                Spacer(modifier = Modifier.height(6.dp))

                androidx.compose.material3
                    .OutlinedButton(
                        onClick = {
                            noteDraft.value = normalizedNote.orEmpty()

                            showNoteDialog.value = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (
                                normalizedNote == null
                            ) {
                                stringResource(R.string.main_note_add)
                            } else {
                                stringResource(R.string.main_note_edit)
                            },
                        )
                    }

                Spacer(modifier = Modifier.height(4.dp))

                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showDeleteDialog.value = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.main_session_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
            }
        }
    }

    if (showNoteDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showNoteDialog.value = false
            },
            title = {
                Text(
                    text = if (
                        normalizedNote == null
                    ) {
                        stringResource(R.string.main_note_add)
                    } else {
                        stringResource(R.string.main_note_edit)
                    },
                )
            },
            text = {
                Column {
                    androidx.compose.material3
                        .OutlinedTextField(
                            value = noteDraft.value,
                            onValueChange = { value ->
                                noteDraft.value = value.take(120)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(stringResource(R.string.main_note))
                            },
                            placeholder = {
                                Text(stringResource(R.string.main_note_placeholder))
                            },
                            supportingText = {
                                Text(noteDraft.value.length.toString() + " / 120")
                            },
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                }
            },
            confirmButton = {
                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showNoteDialog.value = false

                            onSaveNote(noteDraft.value)
                        },
                    ) {
                        Text(stringResource(R.string.main_save))
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
                                    showNoteDialog.value = false

                                    noteDraft.value = ""

                                    onSaveNote(null)
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.main_remove),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                    }

                    androidx.compose.material3
                        .TextButton(
                            onClick = {
                                showNoteDialog.value = false
                            },
                        ) {
                            Text(stringResource(R.string.main_cancel))
                        }
                }
            },
        )
    }

    if (showDeleteDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showDeleteDialog.value = false
            },
            title = {
                Text(text = stringResource(R.string.main_session_delete_title))
            },
            text = {
                Text(text = stringResource(R.string.main_session_delete_message))
            },
            confirmButton = {
                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showDeleteDialog.value = false
                            onDelete()
                        },
                    ) {
                        Text(text = stringResource(R.string.main_delete), color = MaterialTheme.colorScheme.error)
                    }
            },
            dismissButton = {
                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showDeleteDialog.value = false
                        },
                    ) {
                        Text(stringResource(R.string.main_cancel))
                    }
            },
        )
    }
}

@Composable
private fun SessionHistoryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SessionHistoryValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = value,
            modifier = Modifier.weight(1.45f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

private fun buildSessionExportFileName(
    context: Context,
    session: ArchivedSession,
): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(session.startMillis))

    return context.getString(R.string.main_session_export_file_name, timestamp)
}

private fun buildSessionExportText(
    context: Context,
    session: ArchivedSession,
): String =
    buildString {
        appendLine("wakelogs v${BuildConfig.VERSION_NAME} · dernikiausd")
        appendLine(context.getString(R.string.main_export_kind_session_summary))
        appendLine()

        appendLine(context.getString(R.string.main_export_start, formatSessionExportTimestamp(session.startMillis)))

        appendLine(context.getString(R.string.main_export_end, formatSessionExportTimestamp(session.endMillis)))

        appendLine(context.getString(R.string.main_export_duration, formatComparisonDuration(session.durationMillis)))

        session.note
            ?.trim()
            ?.takeUnless { note ->
                note.isBlank() || note.equals("null", ignoreCase = true)
            }?.let { note ->
                appendLine(context.getString(R.string.main_export_note, note))
            }

        appendLine()
        appendLine(context.getString(R.string.main_export_technical_activity))

        appendLine(context.getString(R.string.main_export_display_wakeups, session.displayWakeups))

        appendLine(context.getString(R.string.main_export_cpu_wakeups, session.cpuWakeups))

        appendLine()
        appendLine(context.getString(R.string.main_metric_network))

        appendLine(context.getString(R.string.main_export_total, formatNetworkBytes(session.networkTotalBytes)))

        appendLine(context.getString(R.string.main_export_received, formatNetworkBytes(session.networkRxBytes)))

        appendLine(context.getString(R.string.main_export_sent, formatNetworkBytes(session.networkTxBytes)))

        appendLine(
            context.getString(
                R.string.main_export_per_minute,
                formatNetworkBytes(
                    networkBytesPerMinute(bytes = session.networkTotalBytes, durationMillis = session.durationMillis),
                ),
            ),
        )

        appendLine(context.getString(R.string.main_export_active_apps, session.networkActiveApps))

        appendLine()
        appendLine(context.getString(R.string.main_most_active_apps))

        if (session.topApps.isEmpty()) {
            appendLine(context.getString(R.string.main_export_no_app_data))
        } else {
            session.topApps.take(SESSION_EXPORT_TOP_APPS).forEachIndexed {
                index,
                app,
                ->

                appendLine("${index + 1}. " + app.name + " · " + formatNetworkBytes(app.totalBytes))
            }
        }

        appendLine()
        appendLine(context.getString(R.string.main_export_network_hint))
    }

private const val SESSION_EXPORT_TOP_APPS = 10

private fun formatSessionExportTimestamp(timestamp: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

private fun formatSessionHistoryDate(timestamp: Long): String = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))

private fun formatSessionHistoryTimeRange(
    startMillis: Long,
    endMillis: Long,
): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    return formatter.format(Date(startMillis)) + " – " + formatter.format(Date(endMillis))
}

private fun startOfTodayMillis(): Long =
    Calendar
        .getInstance()
        .apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

private fun startOfNextDayMillis(todayStartMillis: Long): Long =
    Calendar
        .getInstance()
        .apply {
            timeInMillis = todayStartMillis

            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

@Composable
private fun StatisticsCard(statistics: ScreenOnStatistics) {
    val screenOnCount = statistics.total

    val hintedCount = statistics.withCause

    val unknownCount = statistics.unexplained

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.main_daily_overview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (
                screenOnCount == 0 && hintedCount == 0 && unknownCount == 0
            ) {
                Text(
                    text = stringResource(R.string.main_daily_no_events),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatisticValue(
                        value = screenOnCount.toString(),
                        label = stringResource(R.string.main_metric_screen_on),
                        modifier = Modifier.weight(1f),
                    )

                    StatisticValue(
                        value = hintedCount.toString(),
                        label = stringResource(R.string.main_metric_with_cause),
                        modifier = Modifier.weight(1f),
                    )

                    StatisticValue(
                        value = unknownCount.toString(),
                        label = stringResource(R.string.main_filter_unexplained),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticValue(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ActionCard(
    hasEvents: Boolean,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedButton(
            onClick = onExport,
            enabled = hasEvents,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.25.dp, MaterialTheme.colorScheme.primary),
            colors = androidx.compose.material3.ButtonDefaults
                .outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                ),
        ) {
            Text(
                text = stringResource(R.string.main_export_technical_report),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        }

        androidx.compose.material3.TextButton(
            onClick = onClear,
            enabled = hasEvents,
            modifier = Modifier.fillMaxWidth(0.72f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.main_clear_list),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasEvents) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun EventFilterBar(
    selectedFilter: EventFilter,
    onSelected: (EventFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EventFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = {
                    onSelected(filter)
                },
                label = {
                    Text(text = stringResource(filter.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
private fun EventViewModeBar(
    selectedMode: EventViewMode,
    onSelected: (EventViewMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EventViewMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = {
                    onSelected(mode)
                },
                label = {
                    Text(text = stringResource(mode.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
private fun FilterEmptyCard(
    monitoring: Boolean,
    filter: EventFilter,
) {
    val title = when (filter) {
        EventFilter.UNKNOWN -> {
            stringResource(R.string.main_empty_unexplained_title)
        }

        EventFilter.BACKGROUND -> {
            stringResource(R.string.main_empty_background_title)
        }

        EventFilter.NOTIFICATIONS -> {
            stringResource(R.string.main_empty_notifications_title)
        }

        EventFilter.DISPLAY -> {
            stringResource(R.string.main_empty_display_title)
        }

        EventFilter.ALL -> {
            if (monitoring) {
                stringResource(R.string.main_empty_all_monitoring_title)
            } else {
                stringResource(R.string.main_empty_all_title)
            }
        }
    }

    val text = when (filter) {
        EventFilter.UNKNOWN -> {
            stringResource(R.string.main_empty_unexplained_text)
        }

        EventFilter.BACKGROUND -> {
            stringResource(R.string.main_empty_background_text)
        }

        EventFilter.NOTIFICATIONS -> {
            stringResource(R.string.main_empty_notifications_text)
        }

        EventFilter.DISPLAY -> {
            stringResource(R.string.main_empty_display_text)
        }

        EventFilter.ALL -> {
            if (monitoring) {
                stringResource(R.string.main_empty_all_monitoring_text)
            } else {
                stringResource(R.string.main_empty_all_text)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(6.dp))

            Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupedCpuEventCard(
    group: GroupedCpuEventListItem,
    detailLevel: DetailLevel,
) {
    val expanded = remember(
        group.stableKey,
    ) {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.main_grouped_cpu_activity),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = group.source
                            ?: stringResource(R.string.grouping_source_ambiguous),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    text = group.events.size.toString() + "×",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            GroupedEventValueRow(
                label = stringResource(R.string.main_grouped_similar_events),
                value = group.events.size.toString(),
            )

            GroupedEventValueRow(
                label = stringResource(R.string.main_grouped_time_range),
                value = formatGroupedEventRange(group.events),
            )

            group.totalDurationMillis
                ?.let { duration ->
                    GroupedEventValueRow(
                        label = stringResource(R.string.main_grouped_total_cpu_awake_time),
                        value = formatGroupedCpuDuration(duration),
                    )
                }

            group.longestDurationMillis
                ?.let { duration ->
                    GroupedEventValueRow(
                        label = stringResource(R.string.main_grouped_longest_operation),
                        value = formatGroupedCpuDuration(duration),
                    )
                }

            val averageDurationMillis = group.totalDurationMillis
                ?.takeIf {
                    group.events.isNotEmpty()
                }?.div(
                    group.events.size.toLong(),
                )

            averageDurationMillis
                ?.let { duration ->
                    GroupedEventValueRow(
                        label = stringResource(R.string.main_grouped_average),
                        value = formatGroupedCpuDuration(duration),
                    )
                }

            Spacer(modifier = Modifier.height(10.dp))

            val classification = classifyGroupedCpuActivity(
                count = group.events.size,
                totalDurationMillis = group.totalDurationMillis,
                longestDurationMillis = group.longestDurationMillis,
                averageDurationMillis = averageDurationMillis,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme
                        .colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.58f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = stringResource(classification.title),
                        color = classification.color(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = stringResource(classification.explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.main_grouped_cpu_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    expanded.value = !expanded.value
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (expanded.value) {
                        stringResource(R.string.main_single_events_hide)
                    } else {
                        stringResource(R.string.main_single_events_show)
                    },
                )
            }

            if (expanded.value) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(10.dp))

                group.events
                    .forEachIndexed {
                        index,
                        event,
                        ->

                        EventCard(event = event, detailLevel = detailLevel)

                        if (
                            index <
                            group.events.lastIndex
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
            }
        }
    }
}

private data class GroupedCpuClassification(
    @StringRes val title: Int,
    @StringRes val explanation: Int,
    val level: Int,
)

@Composable
private fun GroupedCpuClassification.color() =
    when (level) {
        0 -> {
            Color(0xFF35A853)
        }

        1 -> {
            MaterialTheme.colorScheme.primary
        }

        else -> {
            MaterialTheme.colorScheme.error
        }
    }

private fun classifyGroupedCpuActivity(
    count: Int,
    totalDurationMillis: Long?,
    longestDurationMillis: Long?,
    averageDurationMillis: Long?,
): GroupedCpuClassification {
    if (
        totalDurationMillis == null || longestDurationMillis == null || averageDurationMillis == null
    ) {
        return GroupedCpuClassification(
            title = R.string.main_grouped_class_incomplete_title,
            explanation = R.string.main_grouped_class_incomplete_text,
            level = 1,
        )
    }

    return when {
        longestDurationMillis >=
            30_000L ||
            averageDurationMillis >=
            15_000L ||
            totalDurationMillis >=
            90_000L -> {
            GroupedCpuClassification(
                title = R.string.main_grouped_class_long_title,
                explanation = R.string.main_grouped_class_long_text,
                level = 2,
            )
        }

        longestDurationMillis >=
            5_000L ||
            averageDurationMillis >=
            2_500L ||
            totalDurationMillis >=
            20_000L ||
            count >= 10 -> {
            GroupedCpuClassification(
                title = R.string.main_grouped_class_notable_title,
                explanation = R.string.main_grouped_class_notable_text,
                level = 1,
            )
        }

        else -> {
            GroupedCpuClassification(
                title = R.string.main_grouped_class_short_title,
                explanation = R.string.main_grouped_class_short_text,
                level = 0,
            )
        }
    }
}

@Composable
private fun GroupedEventValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = value,
            modifier = Modifier.weight(1.15f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

private fun formatGroupedEventRange(events: List<RecordedEvent>): String {
    val oldest = events.minOf { it.occurredAt }

    val newest = events.maxOf { it.occurredAt }

    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    return if (oldest == newest) {
        formatter.format(Date(newest))
    } else {
        formatter.format(Date(oldest)) + " – " + formatter.format(Date(newest))
    }
}

private fun formatGroupedCpuDuration(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)

    return when {
        safeMillis < 1_000L -> {
            "$safeMillis ms"
        }

        safeMillis < 60_000L -> {
            String.format(Locale.getDefault(), "%.1f s", safeMillis / 1_000.0)
        }

        else -> {
            val minutes = safeMillis / 60_000L

            val seconds = safeMillis %
                60_000L /
                1_000L

            "$minutes min $seconds s"
        }
    }
}

@Composable
private fun EventCard(
    event: RecordedEvent,
    detailLevel: DetailLevel,
) {
    val context = LocalContext.current

    val renderer = rememberEventTextRenderer()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = eventTypeLabel(context, event.type),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = formatTimestamp(event.occurredAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = renderer.title(event),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            if (event is ScreenOnEvent) {
                Spacer(modifier = Modifier.height(7.dp))

                CauseAssessmentCard(assessment = causeAssessmentFor(event))

                val causalChain = remember(event, renderer) { buildCausalChain(context, renderer, event) }

                if (causalChain.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    CausalChainView(steps = causalChain)
                }
            }

            if (
                detailLevel != DetailLevel.SIMPLE
            ) {
                val details = remember(event, renderer) { uiDetailsForEvent(context, renderer, event) }

                if (details.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = details,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

private data class CausalChainStep(
    val offsetMillis: Long,
    val timingText: String,
    val title: String,
    val source: String,
    val companionActivity: Boolean = false,
)

@Composable
private fun CausalChainView(steps: List<CausalChainStep>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.main_chain_title),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            val causeSteps = steps.filterNot { it.companionActivity }

            val companionSteps = steps.filter { it.companionActivity }

            causeSteps.forEachIndexed {
                index,
                step,
                ->

                Text(
                    text = step.timingText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(text = step.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)

                if (step.source.isNotBlank()) {
                    Text(
                        text = step.source,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (index < causeSteps.lastIndex) {
                    Text(
                        text = "↓",
                        modifier = Modifier.padding(vertical = 3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (companionSteps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.main_chain_companion_activities),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                companionSteps.forEach { step ->
                    Text(
                        text = step.timingText,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (step.source.isNotBlank()) {
                        Text(
                            text = step.source,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * Steps that led to a screen-on, in time order: the direct wake reason,
 * the notification cause and every hint, each with its exact offset.
 * Hints that only accompanied the wake-up are listed separately.
 */
private fun buildCausalChain(
    context: Context,
    renderer: EventTextRenderer,
    event: ScreenOnEvent,
): List<CausalChainStep> {
    val steps = mutableListOf<CausalChainStep>()

    event.wakeReason?.let { wakeReason ->
        steps.add(
            CausalChainStep(
                offsetMillis = wakeReason.offsetMs,
                timingText = renderer.signedSeconds(wakeReason.offsetMs),
                title = context.getString(R.string.main_chain_trigger),
                source = renderer.wakeReasonLabel(wakeReason),
            ),
        )
    }

    event.notificationCause?.let { cause ->
        steps.add(
            CausalChainStep(
                offsetMillis = cause.offsetMs,
                timingText = renderer.signedSeconds(cause.offsetMs),
                title = context.getString(R.string.main_chain_notification),
                source = SourceLabelResolver.get(context).appName(cause.packageName),
            ),
        )
    }

    event.wakeLockHints.forEach { hint ->
        steps.add(
            CausalChainStep(
                offsetMillis = hint.offsetMs,
                timingText = renderer.signedSeconds(hint.offsetMs),
                title = renderer.wakeLockKindLabel(WakeLockTags.kindOf(hint.tag)),
                source = renderer.sourceLabel(hint.packageName),
                companionActivity = isChainCompanion(CauseAssessment.relationOf(event, hint), hint.offsetMs),
            ),
        )
    }

    event.alarmHints.forEach { hint ->
        steps.add(
            CausalChainStep(
                offsetMillis = hint.offsetMs,
                timingText = renderer.signedSeconds(hint.offsetMs),
                title = context.getString(R.string.main_chain_wakeup_alarm),
                source = renderer.sourceLabel(hint.packageName),
                companionActivity = isChainCompanion(CauseAssessment.relationOf(event, hint), hint.offsetMs),
            ),
        )
    }

    event.jobHints.forEach { hint ->
        steps.add(
            CausalChainStep(
                offsetMillis = hint.offsetMs,
                timingText = renderer.signedSeconds(hint.offsetMs),
                title = context.getString(R.string.main_kind_background_job),
                source = renderer.sourceLabel(hint.packageName),
                companionActivity = isChainCompanion(CauseAssessment.relationOf(event, hint), hint.offsetMs),
            ),
        )
    }

    if (steps.isEmpty()) {
        return emptyList()
    }

    steps.add(
        CausalChainStep(
            offsetMillis = 0L,
            timingText = renderer.signedSeconds(0L),
            title = context.getString(R.string.main_chain_screen_turned_on),
            source = "",
            companionActivity = false,
        ),
    )

    return steps
        .distinctBy {
            listOf(it.offsetMillis, it.title, it.source)
        }.sortedWith(
            compareBy<CausalChainStep> {
                it.companionActivity
            }.thenBy {
                it.offsetMillis
            }.thenBy {
                it.title
            },
        )
}

/**
 * Accompanying activity that happened before the screen-on still belongs
 * into the chain; only simultaneous or later activity is listed apart.
 */
private fun isChainCompanion(
    relation: HintRelation,
    offsetMillis: Long,
): Boolean = relation == HintRelation.COMPANION && offsetMillis >= 0L

@Composable
private fun CauseAssessmentCard(assessment: CauseAssessmentUi) {
    val containerColor = when (assessment.confidence) {
        CauseConfidence.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        CauseConfidence.PROBABLE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
        CauseConfidence.POSSIBLE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        CauseConfidence.COMPANION -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        CauseConfidence.UNRESOLVED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
    }

    val titleColor = when (assessment.confidence) {
        CauseConfidence.CONFIRMED,
        CauseConfidence.PROBABLE,
        -> MaterialTheme.colorScheme.primary

        CauseConfidence.POSSIBLE,
        CauseConfidence.COMPANION,
        -> MaterialTheme.colorScheme.onSurface

        CauseConfidence.UNRESOLVED -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Text(
                text = stringResource(assessment.confidence.label),
                color = titleColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = stringResource(assessment.explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp,
            )
        }
    }
}

/**
 * Detail text of an event card. Snapshots get a compact summary; every
 * other event shows the rendered detail lines.
 */
private fun uiDetailsForEvent(
    context: Context,
    renderer: EventTextRenderer,
    event: RecordedEvent,
): String =
    when (event) {
        is ExpertSnapshotEvent -> {
            expertSnapshotUiDetails(context, renderer, event)
        }

        is SystemSnapshotEvent -> {
            systemSnapshotUiDetails(context, renderer, event.snapshot)
        }

        else -> {
            renderer.details(event)
        }
    }

private fun expertSnapshotUiDetails(
    context: Context,
    renderer: EventTextRenderer,
    event: ExpertSnapshotEvent,
): String {
    val snapshot = event.snapshot

    return buildString {
        appendLine(context.getString(R.string.main_ui_trigger, context.getString(R.string.main_metric_screen_on)))
        appendLine(context.getString(R.string.main_ui_expert_context_summary))

        if (snapshot.status != ExpertSnapshotStatus.OK) {
            appendLine()
            appendLine(
                context.getString(
                    R.string.service_diagnostic_error,
                    snapshot.errorDetail
                        ?: context.getString(R.string.main_unknown_lowercase),
                ),
            )
        }

        ExpertSection.entries.forEach { section ->
            val signals = snapshot.signals.filter { it.section == section }.sortedBy { it.ordinal }

            if (signals.isEmpty()) {
                return@forEach
            }

            appendLine()
            appendLine(
                context.getString(
                    when (section) {
                        ExpertSection.LOCATION -> R.string.main_ui_expert_section_location
                        ExpertSection.SENSORS -> R.string.main_ui_expert_section_sensors
                        ExpertSection.NETWORK -> R.string.main_ui_expert_section_network
                    },
                ),
            )

            signals.forEach { signal -> appendLine("• " + renderer.expertSignalLabel(signal, event.deviceFamily)) }
        }

        appendLine()
        append(context.getString(R.string.main_ui_expert_no_coordinates))
    }.trim()
}

private fun systemSnapshotUiDetails(
    context: Context,
    renderer: EventTextRenderer,
    snapshot: SystemSnapshot,
): String {
    val displayText = when (snapshot.idleScreenOn) {
        true -> context.getString(R.string.main_ui_display_on)
        false -> context.getString(R.string.main_ui_display_off)
        null -> context.getString(R.string.main_unknown_lowercase)
    }

    val idleText = readableIdleStateForUi(context, snapshot)

    val classification = if (snapshot.status == SnapshotStatus.OK) {
        renderer.snapshotClassificationLabel(SnapshotClassification.of(snapshot))
    } else {
        context.getString(R.string.main_ui_no_assessment)
    }

    return buildString {
        appendLine(context.getString(R.string.main_ui_trigger, renderer.snapshotTriggerLabel(snapshot.trigger)))
        appendLine(
            context.getString(
                R.string.main_ui_state,
                snapshot.wakefulness
                    ?: context.getString(R.string.main_unknown_lowercase),
            ),
        )
        appendLine(context.getString(R.string.main_ui_display, displayText))

        if (idleText != null) {
            appendLine(context.getString(R.string.main_ui_idle, idleText))
        }

        appendLine()
        append(classification)
    }.trim()
}

/** Doze state from the raw `mState` / `mLightState` tokens. */
private fun readableIdleStateForUi(
    context: Context,
    snapshot: SystemSnapshot,
): String? {
    val deep = snapshot.deepIdleState?.uppercase(Locale.ROOT)

    val light = snapshot.lightIdleState?.uppercase(Locale.ROOT)

    return when {
        deep?.startsWith("IDLE") == true ||
            snapshot.deviceIdleMode == true -> {
            context.getString(R.string.main_idle_deep_doze)
        }

        deep == "INACTIVE" && light == "INACTIVE" -> {
            context.getString(R.string.main_idle_not_deep_yet)
        }

        light == "ACTIVE" || deep == "ACTIVE" -> {
            context.getString(R.string.main_idle_system_active)
        }

        light?.startsWith("IDLE") == true -> {
            context.getString(R.string.main_idle_light_doze)
        }

        deep == null && light == null -> {
            null
        }

        else -> {
            listOfNotNull(
                snapshot.deepIdleState?.let { "Deep=$it" },
                snapshot.lightIdleState?.let { "Light=$it" },
            ).joinToString(", ")
        }
    }
}

private fun causeAssessmentFor(event: ScreenOnEvent): CauseAssessmentUi =
    when (CauseAssessment.verdictOf(event)) {
        ScreenOnVerdict.CONFIRMED -> {
            CauseAssessmentUi(confidence = CauseConfidence.CONFIRMED, explanation = R.string.main_assessment_confirmed)
        }

        ScreenOnVerdict.PROBABLE_NOTIFICATION -> {
            CauseAssessmentUi(confidence = CauseConfidence.PROBABLE, explanation = R.string.main_assessment_probable)
        }

        ScreenOnVerdict.POSSIBLE_NOTIFICATION -> {
            CauseAssessmentUi(
                confidence = CauseConfidence.POSSIBLE,
                explanation = R.string.main_assessment_possible_notification,
            )
        }

        ScreenOnVerdict.POSSIBLE_WAKEUP_ALARM -> {
            CauseAssessmentUi(
                confidence = CauseConfidence.POSSIBLE,
                explanation = R.string.main_assessment_possible_wakeup_alarm,
            )
        }

        ScreenOnVerdict.POSSIBLE_WAKELOCK -> {
            CauseAssessmentUi(
                confidence = CauseConfidence.POSSIBLE,
                explanation = R.string.main_assessment_possible_wakelock,
            )
        }

        ScreenOnVerdict.COMPANION -> {
            CauseAssessmentUi(confidence = CauseConfidence.COMPANION, explanation = R.string.main_assessment_companion)
        }

        ScreenOnVerdict.UNRESOLVED -> {
            CauseAssessmentUi(confidence = CauseConfidence.UNRESOLVED, explanation = R.string.main_assessment_unresolved)
        }
    }

private fun readableWakeReason(
    context: Context,
    diagnostic: WakeReasonDiagnostic,
): String {
    if (diagnostic.reason.isNullOrBlank()) {
        return context.getString(R.string.main_unknown)
    }

    return EventTextRenderer(context)
        .wakeReasonLabel(
            reason = WakeReasons.fromPowerManager(diagnostic.reason, diagnostic.details),
            rawReason = diagnostic.reason,
        )
}

private fun compactWakeReasonDetails(
    context: Context,
    details: String?,
): String {
    val value = details?.trim().orEmpty()

    if (value.isBlank()) {
        return context.getString(R.string.main_none)
    }

    return if (value.length <= 90) {
        value
    } else {
        value.take(87) + "…"
    }
}

private fun compactJobService(
    context: Context,
    serviceName: String?,
): String {
    val value = serviceName?.trim().orEmpty()

    if (value.isBlank()) {
        return context.getString(R.string.main_unknown_lowercase)
    }

    return if (value.length <= 72) {
        value
    } else {
        value.take(69) + "…"
    }
}

private fun compactAlarmTag(
    context: Context,
    tag: String?,
): String {
    val value = tag
        ?.removePrefix("*walarm*:")
        ?.trim()
        .orEmpty()

    if (value.isBlank()) {
        return context.getString(R.string.main_unknown_lowercase)
    }

    return if (value.length <= 72) {
        value
    } else {
        value.take(69) + "…"
    }
}

private fun formatDuration(
    context: Context,
    millis: Long?,
): String {
    val value = millis ?: return context.getString(R.string.main_duration_unknown)

    val totalSeconds = value / 1_000L
    val days = totalSeconds / 86_400L
    val hours = totalSeconds % 86_400L / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L

    return when {
        days > 0L -> {
            context.getString(R.string.main_duration_days_hours, days, hours)
        }

        hours > 0L -> {
            context.getString(R.string.main_duration_hours_minutes, hours, minutes)
        }

        minutes > 0L -> {
            context.getString(R.string.main_duration_minutes_seconds, minutes, seconds)
        }

        else -> {
            context.getString(R.string.main_duration_seconds, seconds)
        }
    }
}

private fun formatNetworkBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)

    return when {
        value >= 1_073_741_824L -> {
            String.format(Locale.getDefault(), "%.1f GB", value / 1_073_741_824.0)
        }

        value >= 1_048_576L -> {
            String.format(Locale.getDefault(), "%.1f MB", value / 1_048_576.0)
        }

        value >= 1024L -> {
            String.format(Locale.getDefault(), "%.1f KB", value / 1024.0)
        }

        else -> {
            "$value B"
        }
    }
}

private fun compactWakeLockTag(
    context: Context,
    tag: String?,
): String {
    val value = tag?.trim().orEmpty()

    if (value.isBlank()) {
        return context.getString(R.string.main_unknown_lowercase)
    }

    if (value.length <= 72) {
        return value
    }

    return value.take(69) + "…"
}

private fun resolveWakeLockSource(
    context: Context,
    packageName: String?,
): String {
    if (packageName.isNullOrBlank()) {
        return context.getString(R.string.main_unknown)
    }

    return SourceLabelResolver.get(context).labelWithPackage(packageName.trim())
}

private fun classifyWakeLockTag(
    context: Context,
    tag: String?,
): String {
    if (tag.isNullOrBlank()) {
        return context.getString(R.string.main_kind_unknown_partial_wakelock)
    }

    return EventTextRenderer(context).wakeLockKindLabel(WakeLockTags.kindOf(tag))
}

private fun formatWakeLockTimestamp(
    context: Context,
    rawTimestamp: String?,
): String {
    val raw = rawTimestamp?.trim().orEmpty()

    if (raw.isBlank()) {
        return context.getString(R.string.main_unknown)
    }

    return runCatching {
        val currentYear = java.util.Calendar
            .getInstance()
            .get(java.util.Calendar.YEAR)

        val parser = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.US,
        ).apply {
            isLenient = false
        }

        val parsedDate =
            parser.parse("$currentYear-$raw") ?: error(context.getString(R.string.main_error_timestamp_unreadable))

        SimpleDateFormat("dd.MM.yyyy · HH:mm:ss", Locale.getDefault()).format(parsedDate)
    }.getOrDefault(raw)
}

private fun eventTypeLabel(
    context: Context,
    type: EventType,
): String =
    when (type) {
        EventType.SCREEN_ON -> context.getString(R.string.main_event_type_screen_on)

        EventType.SCREEN_OFF -> context.getString(R.string.main_event_type_screen_off)

        EventType.CPU_WAKEUP -> context.getString(R.string.main_event_type_background)

        EventType.SYSTEM_SNAPSHOT -> context.getString(R.string.main_event_type_system)

        EventType.EXPERT_SNAPSHOT -> context.getString(R.string.main_event_type_expert)

        EventType.NOTIFICATION -> context.getString(R.string.main_event_type_notification)

        EventType.MONITOR_START,
        EventType.MONITOR_STOP,
        -> context.getString(R.string.main_event_type_monitor)

        EventType.POWER_CONNECTED,
        EventType.POWER_DISCONNECTED,
        EventType.USB_ATTACHED,
        EventType.USB_DETACHED,
        EventType.NETWORK_SESSION,
        -> type.name.replace("_", " ")
    }

private fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("dd.MM. · HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

private fun formatLiveSessionDuration(
    context: Context,
    durationMillis: Long,
): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) /
        1_000L

    val days = totalSeconds /
        86_400L

    val hours = (totalSeconds % 86_400L) /
        3_600L

    val minutes = (totalSeconds % 3_600L) /
        60L

    val seconds = totalSeconds %
        60L

    return when {
        days > 0L -> {
            context.getString(R.string.main_live_duration_days, days, hours, minutes, seconds)
        }

        hours > 0L -> {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }

        else -> {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}

private fun isNotificationAccessEnabled(context: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun startMonitoring(context: Context) {
    val intent = Intent(
        context,
        WakeMonitorService::class.java,
    ).apply {
        action = WakeMonitorService.ACTION_START
    }

    ContextCompat.startForegroundService(context, intent)
}

private fun stopMonitoring(
    context: Context,
    requestedAtMillis: Long,
) {
    val intent = Intent(
        context,
        WakeMonitorService::class.java,
    ).apply {
        action = WakeMonitorService.ACTION_STOP

        putExtra("stop_requested_at_millis", requestedAtMillis)
    }

    context.startService(intent)
}
