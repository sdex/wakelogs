package de.sanniki.wakesleuth.ui.main

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.SessionArchive
import de.sanniki.wakesleuth.ShizukuDiagnostics
import de.sanniki.wakesleuth.ShizukuState
import de.sanniki.wakesleuth.TechnicalExport
import de.sanniki.wakesleuth.WakeMonitorService
import de.sanniki.wakesleuth.WakeSleuthUiSettingsStore
import de.sanniki.wakesleuth.buildGroupedEventList
import de.sanniki.wakesleuth.data.WakelogsData
import de.sanniki.wakesleuth.domain.CauseAssessment
import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.NotificationEvent
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.ScreenOffEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.ui.common.MviViewModel
import de.sanniki.wakesleuth.ui.diagnostics.buildManualDiagnosticsExport
import de.sanniki.wakesleuth.ui.events.EventFilter
import de.sanniki.wakesleuth.ui.events.EventViewMode
import de.sanniki.wakesleuth.ui.monitor.isNotificationAccessEnabled
import de.sanniki.wakesleuth.ui.monitor.startMonitoring
import de.sanniki.wakesleuth.ui.monitor.stopMonitoring
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver
import de.sanniki.wakesleuth.ui.statistics.ScreenOnStatistics
import de.sanniki.wakesleuth.ui.statistics.startOfNextDayMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Owns the main screen: reads the database, the permissions and Shizuku into
 * one [WakeSleuthState], performs every [WakeSleuthIntent], and asks the UI for
 * what only it can do (launchers, toasts, finishing) through [WakeSleuthEvent].
 */
internal class WakeSleuthViewModel(
    application: Application,
) : MviViewModel<WakeSleuthState, WakeSleuthIntent, WakeSleuthEvent>(initialState(application)) {
    private val app = application

    private val data = WakelogsData.get(application)

    private val labels = SourceLabelResolver(application, data.labels)

    private val uiPreferences = application.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)

    private val resumed = MutableStateFlow(false)

    /** The export text waits here while the user picks a file for it. */
    private var pendingExportText = ""

    /** Set while the notification permission is being asked for on the way to a start. */
    private var startAfterPermission = false

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener { refreshShizukuState() }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener { setShizukuState(ShizukuState.NOT_RUNNING) }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == ShizukuDiagnostics.REQUEST_CODE) {
            refreshShizukuState()
        }
    }

    init {
        val createdAt = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            data.labels.preload()

            // A session can only be open while the service runs; one left
            // open belongs to a process that was killed.
            if (!WakeMonitorService.isRunning) {
                // A service started after this point owns its session.
                data.sessions.recoverAbandonedSessions(System.currentTimeMillis(), ownerStartedAt = createdAt)
            }
        }

        observeLatestSession()
        observeTimeline()
        observeArchive()
        observeDailyStatistics()
        tickSessionDuration()
        listenToShizuku()
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onIntent(intent: WakeSleuthIntent) {
        when (intent) {
            WakeSleuthIntent.Resumed -> {
                onResumed()
            }

            WakeSleuthIntent.Paused -> {
                resumed.value = false
            }

            WakeSleuthIntent.BackPressed -> {
                onBackPressed()
            }

            is WakeSleuthIntent.SectionSelected -> {
                updateState { it.copy(selectedSection = intent.section) }
            }

            WakeSleuthIntent.OpenSettingsClicked -> {
                updateState { it.copy(showSettings = true) }
            }

            WakeSleuthIntent.SettingsBackClicked -> {
                updateState { it.copy(showSettings = false) }
            }

            is WakeSleuthIntent.SettingsChanged -> {
                onSettingsChanged(intent)
            }

            WakeSleuthIntent.OpenNotificationAccessClicked -> {
                sendEvent(WakeSleuthEvent.StartActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
            }

            WakeSleuthIntent.RequestNotificationsClicked -> {
                onRequestNotifications()
            }

            WakeSleuthIntent.RequestShizukuPermissionClicked -> {
                ShizukuDiagnostics.requestPermission()
            }

            WakeSleuthIntent.OpenShizukuClicked -> {
                onOpenShizuku()
            }

            is WakeSleuthIntent.NotificationPermissionResult -> {
                onNotificationPermissionResult(intent.granted)
            }

            WakeSleuthIntent.StartMonitoringClicked -> {
                onStartMonitoring()
            }

            WakeSleuthIntent.StopMonitoringClicked -> {
                onStopMonitoring()
            }

            WakeSleuthIntent.ToggleEventsClicked -> {
                updateEvents { it.copy(expanded = !it.expanded) }
            }

            is WakeSleuthIntent.EventFilterSelected -> {
                uiPreferences.edit().putString(KEY_EVENT_FILTER, intent.filter.name).apply()
                updateEvents { it.copy(filter = intent.filter) }
            }

            is WakeSleuthIntent.EventViewModeSelected -> {
                uiPreferences.edit().putString(KEY_EVENT_VIEW_MODE, intent.mode.name).apply()
                updateEvents { it.copy(viewMode = intent.mode) }
            }

            WakeSleuthIntent.ExportClicked -> {
                onExport()
            }

            is WakeSleuthIntent.ExportDestinationChosen -> {
                onExportDestinationChosen(intent)
            }

            WakeSleuthIntent.ClearEventsClicked -> {
                data.scope.launch { data.events.clearEvents() }
                sendEvent(WakeSleuthEvent.ShowToast(R.string.main_toast_events_cleared))
            }

            is WakeSleuthIntent.SessionNoteSaved -> {
                data.scope.launch { data.sessions.updateNote(intent.sessionId, intent.note) }
            }

            is WakeSleuthIntent.SessionDeleted -> {
                data.scope.launch { data.sessions.deleteSession(intent.sessionId) }
            }

            WakeSleuthIntent.ArchiveCleared -> {
                data.scope.launch { data.sessions.clearArchive() }
            }

            is WakeSleuthIntent.DiagnosticCheckClicked -> {
                onDiagnosticCheck(intent.kind)
            }
        }
    }

    // ---------- Data sources ----------

    private fun observeLatestSession() {
        viewModelScope.launch {
            data.sessions.observeLatestSession().collect { session ->
                val recording = session?.takeIf { it.finalizedAt == null }
                val startMillis = recording?.startedAt
                val stopMillis = recording?.stopRequestedAt

                updateState { state ->
                    state.copy(
                        latestSession = session,
                        monitor = MonitorState(
                            monitoring = recording != null,
                            finalizing = stopMillis != null,
                            sessionStartMillis = startMillis,
                            sessionDurationMillis = when {
                                recording == null -> 0L
                                stopMillis != null -> (stopMillis - startMillis!!).coerceAtLeast(0L)
                                else -> state.monitor.sessionDurationMillis
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun observeTimeline() {
        viewModelScope.launch {
            data.events
                .observeLatestSessionTimeline()
                .flowOn(Dispatchers.Default)
                .collect { events -> updateEvents { it.copy(all = events) } }
        }
    }

    private fun observeArchive() {
        viewModelScope.launch {
            combine(
                data.database.sessionDao().observeFinalizedSessions(),
                data.database.sessionDao().observeFinalizedSourceStats(),
                data.database.networkDao().observeFinalizedUsage(),
            ) { sessions, stats, usage ->
                SessionArchive.build(sessions, stats, usage, labels)
            }.flowOn(Dispatchers.Default)
                .collect { archive -> updateState { it.copy(archivedSessions = archive) } }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDailyStatistics() {
        viewModelScope.launch {
            // The day bounds follow the clock: at midnight the query restarts for the new day.
            currentDayStarts()
                .distinctUntilChanged()
                .flatMapLatest { dayStart -> data.events.observeScreenOnsBetween(dayStart, startOfNextDayMillis(dayStart)) }
                .map { screenOns ->
                    ScreenOnStatistics(
                        total = screenOns.size,
                        withCause = screenOns.count { CauseAssessment.hasExplanationOrHint(it) },
                        unexplained = screenOns.count { CauseAssessment.isUnexplained(it) },
                    )
                }.collect { statistics -> updateState { it.copy(dailyStatistics = statistics) } }
        }
    }

    /** Advances the live duration once a second while a session runs and the app is in front. */
    private fun tickSessionDuration() {
        val runningSince = state
            .map { it.monitor }
            .map { monitor -> monitor.sessionStartMillis.takeIf { monitor.monitoring && !monitor.finalizing } }
            .distinctUntilChanged()

        viewModelScope.launch {
            combine(resumed, runningSince) { isResumed, startMillis -> startMillis.takeIf { isResumed } }
                .distinctUntilChanged()
                .collectLatest { startMillis ->
                    while (startMillis != null) {
                        updateState {
                            it.copy(
                                monitor = it.monitor.copy(
                                    sessionDurationMillis = (System.currentTimeMillis() - startMillis).coerceAtLeast(0L),
                                ),
                            )
                        }
                        delay(TICK_MILLIS)
                    }
                }
        }
    }

    private fun listenToShizuku() {
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
    }

    // ---------- Intents ----------

    /** Permissions and Shizuku can change in other apps; read them again whenever the app returns. */
    private fun onResumed() {
        resumed.value = true

        updateState {
            it.copy(
                setup = SetupState(
                    notificationAccessEnabled = isNotificationAccessEnabled(app),
                    notificationsAllowed = notificationsAllowed(app),
                    shizukuState = ShizukuDiagnostics.state(),
                ),
            )
        }
    }

    private fun onBackPressed() {
        val current = state.value

        when {
            current.showSettings -> {
                updateState { it.copy(showSettings = false) }
            }

            current.selectedSection != MainSection.OVERVIEW -> {
                updateState { it.copy(selectedSection = MainSection.OVERVIEW) }
            }

            else -> {
                sendEvent(WakeSleuthEvent.Finish)
            }
        }
    }

    private fun onSettingsChanged(intent: WakeSleuthIntent.SettingsChanged) {
        WakeSleuthUiSettingsStore.save(app, intent.settings)
        updateState { it.copy(uiSettings = intent.settings) }
    }

    private fun onRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            startAfterPermission = false
            sendEvent(WakeSleuthEvent.RequestNotificationPermission)
        }
    }

    private fun onOpenShizuku() {
        val launchIntent = app.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)

        if (launchIntent != null) {
            sendEvent(WakeSleuthEvent.StartActivity(launchIntent))
        } else {
            sendEvent(WakeSleuthEvent.ShowToast(R.string.main_toast_shizuku_app_not_found, long = true))
        }
    }

    private fun onNotificationPermissionResult(granted: Boolean) {
        val denied = !granted && Build.VERSION.SDK_INT >= 33

        updateState {
            it.copy(setup = it.setup.copy(notificationsAllowed = granted || Build.VERSION.SDK_INT < 33))
        }

        if (startAfterPermission) {
            startAfterPermission = false
            startMonitoring(app)

            if (denied) {
                sendEvent(WakeSleuthEvent.ShowToast(R.string.main_toast_monitoring_notification_hidden, long = true))
            }
        } else if (denied) {
            sendEvent(WakeSleuthEvent.ShowToast(R.string.main_toast_notifications_denied))
        }
    }

    private fun onStartMonitoring() {
        if (notificationsAllowed(app)) {
            startMonitoring(app)
        } else {
            startAfterPermission = true
            sendEvent(WakeSleuthEvent.RequestNotificationPermission)
        }
    }

    private fun onStopMonitoring() {
        val requestedAtMillis = System.currentTimeMillis()

        updateState { state ->
            val startMillis = state.monitor.sessionStartMillis ?: return@updateState state

            state.copy(
                monitor = state.monitor.copy(
                    sessionDurationMillis = (requestedAtMillis - startMillis).coerceAtLeast(0L),
                ),
            )
        }

        stopMonitoring(context = app, requestedAtMillis = requestedAtMillis)
    }

    private fun onExport() {
        viewModelScope.launch {
            val current = state.value
            val exportEvents = exportEvents()

            // The report can be large; assemble it off the main thread.
            pendingExportText = withContext(Dispatchers.Default) {
                buildString {
                    appendLine(app.getString(R.string.main_export_kind_technical_report))
                    appendLine()

                    append(
                        TechnicalExport.build(
                            context = app,
                            events = exportEvents,
                            monitoring = current.monitor.monitoring,
                        ),
                    )

                    appendLine()
                    appendLine()

                    append(
                        buildManualDiagnosticsExport(
                            context = app,
                            wakeLockDiagnostic = current.diagnostics.wakeLock,
                            wakeupAlarmDiagnostic = current.diagnostics.wakeupAlarm,
                            backgroundJobDiagnostic = current.diagnostics.backgroundJob,
                            wakeReasonDiagnostic = current.diagnostics.wakeReason,
                            networkStatsDiagnostic = current.diagnostics.networkStats,
                        ),
                    )
                }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

            sendEvent(
                WakeSleuthEvent.CreateExportDocument(
                    app.getString(R.string.main_export_technical_report_file_name, timestamp),
                ),
            )
        }
    }

    private fun onExportDestinationChosen(intent: WakeSleuthIntent.ExportDestinationChosen) {
        val uri = intent.uri ?: return

        viewModelScope.launch {
            val succeeded = withContext(Dispatchers.IO) {
                runCatching {
                    val outputStream = app.contentResolver.openOutputStream(uri)
                        ?: error(app.getString(R.string.main_error_file_open_failed))

                    outputStream.bufferedWriter().use { writer -> writer.write(pendingExportText) }
                }.isSuccess
            }

            sendEvent(
                WakeSleuthEvent.ShowToast(
                    message = if (succeeded) R.string.main_toast_export_saved else R.string.main_toast_export_failed,
                    long = true,
                ),
            )
        }
    }

    /** Events of the latest session in insertion order, for the export. */
    private suspend fun exportEvents(): List<RecordedEvent> {
        val sessionId = data.database
            .sessionDao()
            .latestSession()
            ?.id
            ?: return emptyList()

        return data.events.sessionEventsInInsertOrder(sessionId)
    }

    private fun onDiagnosticCheck(kind: DiagnosticKind) {
        updateDiagnostics { it.loading(kind) }

        viewModelScope.launch {
            when (kind) {
                DiagnosticKind.WAKE_LOCKS -> {
                    val result = ShizukuDiagnostics.readWakeLocks(app)
                    updateDiagnostics { it.copy(wakeLock = result, wakeLockLoading = false) }
                }

                DiagnosticKind.WAKEUP_ALARMS -> {
                    val result = ShizukuDiagnostics.readWakeupAlarms(app)
                    updateDiagnostics { it.copy(wakeupAlarm = result, wakeupAlarmLoading = false) }
                }

                DiagnosticKind.BACKGROUND_JOBS -> {
                    val result = ShizukuDiagnostics.readBackgroundJobs(app)
                    updateDiagnostics { it.copy(backgroundJob = result, backgroundJobLoading = false) }
                }

                DiagnosticKind.WAKE_REASON -> {
                    val result = ShizukuDiagnostics.readWakeReason(app)
                    updateDiagnostics { it.copy(wakeReason = result, wakeReasonLoading = false) }
                }

                DiagnosticKind.NETWORK_STATS -> {
                    val result = ShizukuDiagnostics.readNetworkStats(app)
                    updateDiagnostics { it.copy(networkStats = result, networkStatsLoading = false) }
                }
            }
        }
    }

    // ---------- State helpers ----------

    private fun refreshShizukuState() = setShizukuState(ShizukuDiagnostics.state())

    private fun setShizukuState(shizukuState: ShizukuState) {
        updateState { it.copy(setup = it.setup.copy(shizukuState = shizukuState)) }
    }

    private fun updateDiagnostics(reduce: (DiagnosticsState) -> DiagnosticsState) {
        updateState { it.copy(diagnostics = reduce(it.diagnostics)) }
    }

    private fun DiagnosticsState.loading(kind: DiagnosticKind): DiagnosticsState =
        when (kind) {
            DiagnosticKind.WAKE_LOCKS -> copy(wakeLockLoading = true)
            DiagnosticKind.WAKEUP_ALARMS -> copy(wakeupAlarmLoading = true)
            DiagnosticKind.BACKGROUND_JOBS -> copy(backgroundJobLoading = true)
            DiagnosticKind.WAKE_REASON -> copy(wakeReasonLoading = true)
            DiagnosticKind.NETWORK_STATS -> copy(networkStatsLoading = true)
        }

    /** Applies [reduce] and re-derives the filtered events and list items from the result. */
    private fun updateEvents(reduce: (EventsState) -> EventsState) {
        updateState { it.copy(events = reduce(it.events).derived()) }
    }

    private fun EventsState.derived(): EventsState {
        val filtered = when (filter) {
            EventFilter.ALL -> all
            EventFilter.DISPLAY -> all.filter { it is ScreenOnEvent || it is ScreenOffEvent }
            EventFilter.BACKGROUND -> all.filter { it is CpuWakeupEvent }
            EventFilter.NOTIFICATIONS -> all.filter { it is NotificationEvent }
            EventFilter.UNKNOWN -> all.filter { it is ScreenOnEvent && CauseAssessment.isUnexplained(it) }
        }

        // Grouping is only worth its cost while the list is on screen.
        val listItems = if (expanded && viewMode == EventViewMode.LIST) {
            buildGroupedEventList(labels, filtered)
        } else {
            emptyList()
        }

        return copy(filtered = filtered, listItems = listItems)
    }

    companion object {
        private const val UI_PREFS_NAME = "wakesleuth_ui"
        private const val KEY_EVENT_FILTER = "event_filter"
        private const val KEY_EVENT_VIEW_MODE = "event_view_mode"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val TICK_MILLIS = 1_000L

        private fun initialState(application: Application): WakeSleuthState {
            val prefs = application.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)

            return WakeSleuthState(
                uiSettings = WakeSleuthUiSettingsStore.load(application),
                setup = SetupState(
                    notificationAccessEnabled = isNotificationAccessEnabled(application),
                    notificationsAllowed = notificationsAllowed(application),
                    shizukuState = ShizukuDiagnostics.state(),
                ),
                events = EventsState(
                    filter = runCatching {
                        EventFilter.valueOf(prefs.getString(KEY_EVENT_FILTER, null).orEmpty())
                    }.getOrDefault(EventFilter.ALL),
                    viewMode = runCatching {
                        EventViewMode.valueOf(prefs.getString(KEY_EVENT_VIEW_MODE, null).orEmpty())
                    }.getOrDefault(EventViewMode.LIST),
                ),
            )
        }

        private fun notificationsAllowed(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WakeSleuthViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
            }
        }
    }
}

/** Start of the calendar day containing [at]. */
internal fun startOfDayMillis(at: Long): Long =
    Calendar
        .getInstance()
        .apply {
            timeInMillis = at
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

/** Emits the start of the current day, and again at every date change. */
internal fun currentDayStarts(now: () -> Long = System::currentTimeMillis): Flow<Long> =
    flow {
        while (true) {
            val dayStart = startOfDayMillis(now())

            emit(dayStart)

            delay((startOfNextDayMillis(dayStart) - now()).coerceAtLeast(1L))
        }
    }
