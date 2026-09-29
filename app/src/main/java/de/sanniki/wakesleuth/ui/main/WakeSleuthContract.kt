package de.sanniki.wakesleuth.ui.main

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import de.sanniki.wakesleuth.ArchivedSession
import de.sanniki.wakesleuth.BackgroundJobDiagnostic
import de.sanniki.wakesleuth.EventListItem
import de.sanniki.wakesleuth.NetworkStatsDiagnostic
import de.sanniki.wakesleuth.ShizukuState
import de.sanniki.wakesleuth.WakeLockDiagnostic
import de.sanniki.wakesleuth.WakeReasonDiagnostic
import de.sanniki.wakesleuth.WakeSleuthUiSettings
import de.sanniki.wakesleuth.WakeupAlarmDiagnostic
import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.ui.events.EventFilter
import de.sanniki.wakesleuth.ui.events.EventViewMode
import de.sanniki.wakesleuth.ui.statistics.ScreenOnStatistics

/** Everything the main screen draws. */
@Immutable
internal data class WakeSleuthState(
    val uiSettings: WakeSleuthUiSettings = WakeSleuthUiSettings(),
    val selectedSection: MainSection = MainSection.OVERVIEW,
    val showSettings: Boolean = false,
    val setup: SetupState = SetupState(),
    val monitor: MonitorState = MonitorState(),
    val latestSession: MonitoringSessionEntity? = null,
    val dailyStatistics: ScreenOnStatistics = ScreenOnStatistics(),
    val events: EventsState = EventsState(),
    val archivedSessions: List<ArchivedSession> = emptyList(),
    val diagnostics: DiagnosticsState = DiagnosticsState(),
)

@Immutable
internal data class SetupState(
    val notificationAccessEnabled: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val shizukuState: ShizukuState = ShizukuState.NOT_RUNNING,
)

@Immutable
internal data class MonitorState(
    val monitoring: Boolean = false,
    /** Stop was requested and the session is being written out. */
    val finalizing: Boolean = false,
    val sessionDurationMillis: Long = 0L,
    /** Start of the running session, the origin of the live duration. */
    val sessionStartMillis: Long? = null,
)

@Immutable
internal data class EventsState(
    /** Every event of the latest session, newest first. */
    val all: List<RecordedEvent> = emptyList(),
    /** [all] with [filter] applied. */
    val filtered: List<RecordedEvent> = emptyList(),
    /** [filtered] with CPU wakeups of one source collapsed, for the list view. */
    val listItems: List<EventListItem> = emptyList(),
    val filter: EventFilter = EventFilter.ALL,
    val viewMode: EventViewMode = EventViewMode.LIST,
    val expanded: Boolean = false,
)

/** Results of the manual Shizuku checks; null until a check has run. */
@Immutable
internal data class DiagnosticsState(
    val wakeLock: WakeLockDiagnostic? = null,
    val wakeLockLoading: Boolean = false,
    val wakeupAlarm: WakeupAlarmDiagnostic? = null,
    val wakeupAlarmLoading: Boolean = false,
    val backgroundJob: BackgroundJobDiagnostic? = null,
    val backgroundJobLoading: Boolean = false,
    val wakeReason: WakeReasonDiagnostic? = null,
    val wakeReasonLoading: Boolean = false,
    val networkStats: NetworkStatsDiagnostic? = null,
    val networkStatsLoading: Boolean = false,
) {
    val hasResults: Boolean
        get() = wakeLock != null ||
            wakeupAlarm != null ||
            backgroundJob != null ||
            wakeReason != null ||
            networkStats != null
}

internal enum class DiagnosticKind {
    WAKE_LOCKS,
    WAKEUP_ALARMS,
    BACKGROUND_JOBS,
    WAKE_REASON,
    NETWORK_STATS,
}

internal sealed interface WakeSleuthIntent {
    // Lifecycle and navigation
    data object Resumed : WakeSleuthIntent

    data object Paused : WakeSleuthIntent

    data object BackPressed : WakeSleuthIntent

    data class SectionSelected(
        val section: MainSection,
    ) : WakeSleuthIntent

    data object OpenSettingsClicked : WakeSleuthIntent

    data object SettingsBackClicked : WakeSleuthIntent

    data class SettingsChanged(
        val settings: WakeSleuthUiSettings,
    ) : WakeSleuthIntent

    // Setup
    data object OpenNotificationAccessClicked : WakeSleuthIntent

    data object RequestNotificationsClicked : WakeSleuthIntent

    data object RequestShizukuPermissionClicked : WakeSleuthIntent

    data object OpenShizukuClicked : WakeSleuthIntent

    data class NotificationPermissionResult(
        val granted: Boolean,
    ) : WakeSleuthIntent

    // Monitoring
    data object StartMonitoringClicked : WakeSleuthIntent

    data object StopMonitoringClicked : WakeSleuthIntent

    // Events
    data object ToggleEventsClicked : WakeSleuthIntent

    data class EventFilterSelected(
        val filter: EventFilter,
    ) : WakeSleuthIntent

    data class EventViewModeSelected(
        val mode: EventViewMode,
    ) : WakeSleuthIntent

    data object ExportClicked : WakeSleuthIntent

    data class ExportDestinationChosen(
        val uri: Uri?,
    ) : WakeSleuthIntent

    data object ClearEventsClicked : WakeSleuthIntent

    // Sessions
    data class SessionNoteSaved(
        val sessionId: Long,
        val note: String?,
    ) : WakeSleuthIntent

    data class SessionDeleted(
        val sessionId: Long,
    ) : WakeSleuthIntent

    data object ArchiveCleared : WakeSleuthIntent

    // Diagnostics
    data class DiagnosticCheckClicked(
        val kind: DiagnosticKind,
    ) : WakeSleuthIntent
}

internal sealed interface WakeSleuthEvent {
    data class ShowToast(
        @StringRes val message: Int,
        val long: Boolean = false,
    ) : WakeSleuthEvent

    data object RequestNotificationPermission : WakeSleuthEvent

    data class StartActivity(
        val intent: Intent,
    ) : WakeSleuthEvent

    /** Open the document picker; the choice comes back as `ExportDestinationChosen`. */
    data class CreateExportDocument(
        val fileName: String,
    ) : WakeSleuthEvent

    data object Finish : WakeSleuthEvent
}
