package de.sanniki.wakesleuth.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.sanniki.wakesleuth.AppProfilesCard
import de.sanniki.wakesleuth.BackgroundJobDiagnostic
import de.sanniki.wakesleuth.DetailLevel
import de.sanniki.wakesleuth.GroupedCpuEventListItem
import de.sanniki.wakesleuth.NetworkStatsDiagnostic
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ShizukuDiagnostics
import de.sanniki.wakesleuth.ShizukuState
import de.sanniki.wakesleuth.SingleEventListItem
import de.sanniki.wakesleuth.SleepReportCard
import de.sanniki.wakesleuth.SourceStatisticsCard
import de.sanniki.wakesleuth.TechnicalExport
import de.sanniki.wakesleuth.WakeLockDiagnostic
import de.sanniki.wakesleuth.WakeReasonDiagnostic
import de.sanniki.wakesleuth.WakeSleuthSettingsScreen
import de.sanniki.wakesleuth.WakeSleuthUiSettings
import de.sanniki.wakesleuth.WakeTimeline
import de.sanniki.wakesleuth.WakeupAlarmDiagnostic
import de.sanniki.wakesleuth.buildGroupedEventList
import de.sanniki.wakesleuth.domain.CauseAssessment
import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.NotificationEvent
import de.sanniki.wakesleuth.domain.ScreenOffEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.itemSpacing
import de.sanniki.wakesleuth.pageHorizontalPadding
import de.sanniki.wakesleuth.ui.ScreenOnStatistics
import de.sanniki.wakesleuth.ui.WakelogsViewModel
import de.sanniki.wakesleuth.ui.diagnostics.ShizukuProfileHintCard
import de.sanniki.wakesleuth.ui.diagnostics.ShizukuWakeLockCard
import de.sanniki.wakesleuth.ui.diagnostics.buildManualDiagnosticsExport
import de.sanniki.wakesleuth.ui.events.ActionCard
import de.sanniki.wakesleuth.ui.events.EventCard
import de.sanniki.wakesleuth.ui.events.EventFilter
import de.sanniki.wakesleuth.ui.events.EventFilterBar
import de.sanniki.wakesleuth.ui.events.EventViewMode
import de.sanniki.wakesleuth.ui.events.EventViewModeBar
import de.sanniki.wakesleuth.ui.events.EventsSummaryCard
import de.sanniki.wakesleuth.ui.events.FilterEmptyCard
import de.sanniki.wakesleuth.ui.events.GroupedCpuEventCard
import de.sanniki.wakesleuth.ui.main.CurrentSectionHeader
import de.sanniki.wakesleuth.ui.main.HeaderToolbar
import de.sanniki.wakesleuth.ui.main.MainSection
import de.sanniki.wakesleuth.ui.main.wakelogsBottomNavigation
import de.sanniki.wakesleuth.ui.monitor.MonitorCard
import de.sanniki.wakesleuth.ui.monitor.isNotificationAccessEnabled
import de.sanniki.wakesleuth.ui.monitor.startMonitoring
import de.sanniki.wakesleuth.ui.monitor.stopMonitoring
import de.sanniki.wakesleuth.ui.render.rememberSourceLabelResolver
import de.sanniki.wakesleuth.ui.sessions.SessionComparisonCard
import de.sanniki.wakesleuth.ui.sessions.SessionHistoryCard
import de.sanniki.wakesleuth.ui.setup.SetupStatusCard
import de.sanniki.wakesleuth.ui.statistics.StatisticsCard
import de.sanniki.wakesleuth.ui.statistics.startOfNextDayMillis
import de.sanniki.wakesleuth.ui.statistics.startOfTodayMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WakeSleuthScreen(
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

    BackHandler {
        when {
            showSettings -> {
                showSettings = false
            }

            selectedMainSection != MainSection.OVERVIEW -> {
                navigationScope.launch { pagerState.animateScrollToPage(0) }
            }

            else -> {
                (context as? ComponentActivity)?.finish()
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
        topBar = {
            HeaderToolbar(onOpenSettings = { showSettings = true })
        },
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
                            EventsSummaryCard(
                                totalCount = events.size,
                                visibleCount = filteredEvents.size,
                                expanded = eventSectionExpanded,
                                onToggleExpanded = { eventSectionExpanded = !eventSectionExpanded },
                            )
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
