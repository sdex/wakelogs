package de.sanniki.wakesleuth.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.sanniki.wakesleuth.ArchivedSession
import de.sanniki.wakesleuth.SessionArchive
import de.sanniki.wakesleuth.WakeMonitorService
import de.sanniki.wakesleuth.data.WakelogsData
import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver
import kotlinx.coroutines.Dispatchers
import de.sanniki.wakesleuth.domain.CauseAssessment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScreenOnStatistics(
    val total: Int = 0,
    val withCause: Int = 0,
    val unexplained: Int = 0
)

/**
 * Screen state as flows from the database. Replaces the 600 ms polling
 * loop and every main-thread read of the old SharedPreferences stores.
 */
class WakelogsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val data = WakelogsData.get(application)

    private val labels = SourceLabelResolver(application, data.labels)

    /** Newest session: running, finalizing or the last finished one. */
    val latestSession: StateFlow<MonitoringSessionEntity?> =
        data.sessions
            .observeLatestSession()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Events of [latestSession], newest first. */
    val timeline: StateFlow<List<RecordedEvent>> =
        data.events
            .observeLatestSessionTimeline()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Finalized sessions, newest first. */
    val archive: StateFlow<List<ArchivedSession>> =
        combine(
            data.database.sessionDao().observeFinalizedSessions(),
            data.database.sessionDao().observeFinalizedSourceStats(),
            data.database.networkDao().observeFinalizedUsage()
        ) { sessions, stats, usage ->
            SessionArchive.build(sessions, stats, usage, labels)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Screen-on counts of a day, across sessions. */
    fun observeScreenOnStatistics(from: Long, to: Long): Flow<ScreenOnStatistics> =
        data.events
            .observeScreenOnsBetween(from, to)
            .map { screenOns ->
                ScreenOnStatistics(
                    total = screenOns.size,
                    withCause = screenOns.count { CauseAssessment.hasExplanationOrHint(it) },
                    unexplained = screenOns.count { CauseAssessment.isUnexplained(it) }
                )
            }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            data.labels.preload()

            // A session can only be open while the service runs; one left
            // open belongs to a process that was killed.
            if (!WakeMonitorService.isRunning) {
                data.sessions.recoverAbandonedSessions(System.currentTimeMillis())
            }
        }
    }

    fun clearEvents() {
        data.scope.launch { data.events.clearEvents() }
    }

    fun updateNote(sessionId: Long, note: String?) {
        data.scope.launch { data.sessions.updateNote(sessionId, note) }
    }

    fun deleteSession(sessionId: Long) {
        data.scope.launch { data.sessions.deleteSession(sessionId) }
    }

    fun clearArchive() {
        data.scope.launch { data.sessions.clearArchive() }
    }

    /** Events of the latest session in insertion order, for the export. */
    suspend fun exportEvents(): List<RecordedEvent> {
        val sessionId =
            data.database.sessionDao().latestSession()?.id
                ?: return emptyList()

        return data.events.sessionEventsInInsertOrder(sessionId)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
