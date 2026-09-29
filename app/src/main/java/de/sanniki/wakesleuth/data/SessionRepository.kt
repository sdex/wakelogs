package de.sanniki.wakesleuth.data

import de.sanniki.wakesleuth.DeviceFamily
import de.sanniki.wakesleuth.NetworkStatsDiagnostic
import de.sanniki.wakesleuth.NetworkTrafficEntry
import de.sanniki.wakesleuth.data.db.WakelogsDatabase
import de.sanniki.wakesleuth.data.db.entity.EventEntity
import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkAppUsageEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkBaselineEntryEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkMeasurementEntity
import de.sanniki.wakesleuth.data.db.entity.SessionSourceStatEntity
import de.sanniki.wakesleuth.domain.EventType
import de.sanniki.wakesleuth.domain.NetworkMeasurementStatus
import de.sanniki.wakesleuth.domain.Proximity
import de.sanniki.wakesleuth.domain.SessionEndReason
import de.sanniki.wakesleuth.domain.SessionSources
import kotlinx.coroutines.flow.Flow

/**
 * Session lifecycle: start, stop request, final network measurement,
 * finalization with the archive summary, retention and recovery of
 * sessions whose service died.
 */
class SessionRepository(
    private val writer: DatabaseWriter,
    private val labels: PackageLabelStore,
) {
    private val database: WakelogsDatabase get() = writer.database

    private val sessionDao get() = database.sessionDao()
    private val eventDao get() = database.eventDao()
    private val networkDao get() = database.networkDao()

    fun observeLatestSession(): Flow<MonitoringSessionEntity?> = sessionDao.observeLatestSession()

    /**
     * Reuses a session that is still running (service restarted after
     * process death); otherwise opens a new one with its MONITOR_START.
     */
    suspend fun startOrResume(
        at: Long,
        deviceFamily: DeviceFamily,
        proximity: Proximity?,
    ): Long =
        writer.transaction {
            sessionDao.runningSession()?.let { return@transaction it.id }

            val sessionId = sessionDao.insert(MonitoringSessionEntity(startedAt = at, deviceFamily = deviceFamily))

            eventDao.insert(
                EventEntity(
                    sessionId = sessionId,
                    occurredAt = at,
                    type = EventType.MONITOR_START,
                    proximityState = proximity?.state,
                    proximityDistanceCm = proximity?.distanceCm,
                ),
            )

            sessionId
        }

    suspend fun hasNetworkBaseline(sessionId: Long): Boolean = sessionDao.byId(sessionId)?.networkBaselineCapturedAt != null

    suspend fun saveNetworkBaseline(
        sessionId: Long,
        capturedAt: Long,
        entries: List<NetworkTrafficEntry>,
    ) {
        writer.transaction {
            val session = sessionDao.byId(sessionId) ?: return@transaction

            if (session.finalizedAt != null || session.networkBaselineCapturedAt != null) {
                return@transaction
            }

            networkDao.insertBaseline(
                entries
                    .distinctBy { it.uid }
                    .map {
                        NetworkBaselineEntryEntity(
                            sessionId = sessionId,
                            uid = it.uid,
                            packageName = it.packageName,
                            rxBytes = it.rxBytes.coerceAtLeast(0L),
                            txBytes = it.txBytes.coerceAtLeast(0L),
                            rxPackets = it.rxPackets,
                            txPackets = it.txPackets,
                        )
                    },
            )

            sessionDao.update(session.copy(networkBaselineCapturedAt = capturedAt))
        }
    }

    /** Marks the running session as stopping and logs MONITOR_STOP. */
    suspend fun requestStop(at: Long): Long? =
        writer.transaction {
            val session = sessionDao.runningSession() ?: return@transaction null

            sessionDao.update(session.copy(stopRequestedAt = at))

            eventDao.insert(EventEntity(sessionId = session.id, occurredAt = at, type = EventType.MONITOR_STOP))

            session.id
        }

    /**
     * Stores the traffic delta of every UID since the baseline and the
     * NETWORK_SESSION timeline row; the baseline is dropped afterwards.
     */
    suspend fun finishNetworkMeasurement(
        sessionId: Long,
        end: NetworkStatsDiagnostic?,
        at: Long,
    ) {
        val usagePackages = writer.transaction {
            val session = sessionDao.byId(sessionId) ?: return@transaction emptyList()
            val baseline = networkDao.baseline(sessionId)

            val status: NetworkMeasurementStatus
            val usage: List<NetworkAppUsageEntity>

            when {
                session.networkBaselineCapturedAt == null || baseline.isEmpty() -> {
                    status = NetworkMeasurementStatus.NO_BASELINE
                    usage = emptyList()
                }

                end == null || end.error != null -> {
                    status = NetworkMeasurementStatus.END_FAILED
                    usage = emptyList()
                }

                else -> {
                    status = NetworkMeasurementStatus.OK
                    usage = trafficDeltas(sessionId, baseline, end.entries)
                }
            }

            val eventId = eventDao.insert(
                EventEntity(sessionId = sessionId, occurredAt = at, type = EventType.NETWORK_SESSION),
            )

            networkDao.insertMeasurement(
                NetworkMeasurementEntity(
                    sessionId = sessionId,
                    eventId = eventId,
                    status = status,
                    measuredAt = at.takeIf { status == NetworkMeasurementStatus.OK },
                    errorCode = end?.errorCode.takeIf { status == NetworkMeasurementStatus.END_FAILED },
                    errorDetail = end?.errorDetail.takeIf { status == NetworkMeasurementStatus.END_FAILED },
                ),
            )

            networkDao.insertUsage(usage)
            networkDao.deleteBaseline(sessionId)

            usage.map { it.packageName }
        }

        labels.remember(usagePackages)
    }

    /**
     * Writes the archive summary, closes the session and applies the
     * retention. Safe to call twice; the second call does nothing.
     */
    suspend fun finalize(
        sessionId: Long,
        at: Long,
        endReason: SessionEndReason,
        finalPollCompleted: Boolean?,
    ) {
        writer.transaction {
            val session = sessionDao.byId(sessionId) ?: return@transaction

            if (session.finalizedAt != null) {
                return@transaction
            }

            val events = eventDao.sessionEventsInInsertOrder(sessionId).map(EventMapper::toDomain)

            val stats = SessionSources.aggregate(events).map {
                SessionSourceStatEntity(
                    sessionId = sessionId,
                    sourceKind = it.source.kind,
                    packageName = it.source.packageName.orEmpty(),
                    rawSource = it.source.rawSource.orEmpty(),
                    cpuCount = it.cpuCount,
                    displayCount = it.displayCount,
                    companionCount = it.companionCount,
                    longestCpuAwakeMs = it.longestCpuAwakeMs,
                )
            }

            sessionDao.deleteSourceStats(sessionId)
            sessionDao.insertSourceStats(stats)
            networkDao.deleteBaseline(sessionId)

            // An interrupted session never saw a stop; it ends with its
            // last recorded event.
            val end = session.stopRequestedAt
                ?: eventDao.lastOccurredAt(sessionId)
                ?: session.startedAt

            sessionDao.update(
                session.copy(
                    stopRequestedAt = end,
                    finalizedAt = at,
                    endReason = endReason,
                    finalPollCompleted = finalPollCompleted,
                    displayWakeups = events.count { it.type == EventType.SCREEN_ON },
                    cpuWakeups = events.count { it.type == EventType.CPU_WAKEUP },
                ),
            )

            sessionDao.applyRetention(MAX_SESSIONS)
        }
    }

    /**
     * Closes sessions left open by a service that is no longer running,
     * e.g. after the process was killed. Must only be called while the
     * monitor service is not running.
     */
    suspend fun recoverAbandonedSessions(now: Long) {
        sessionDao.unfinalizedSessions().forEach { session ->
            finalize(
                sessionId = session.id,
                at = now,
                endReason = if (session.stopRequestedAt == null) {
                    SessionEndReason.INTERRUPTED
                } else {
                    SessionEndReason.USER_STOP
                },
                finalPollCompleted = null,
            )
        }
    }

    suspend fun updateNote(
        sessionId: Long,
        note: String?,
    ): Boolean =
        writer.transaction {
            sessionDao.updateNote(id = sessionId, note = note?.trim()?.take(MAX_NOTE_LENGTH)?.ifBlank { null }) > 0
        }

    suspend fun deleteSession(sessionId: Long): Boolean =
        writer.transaction {
            sessionDao.deleteFinalized(sessionId) > 0
        }

    suspend fun clearArchive() {
        writer.transaction { sessionDao.deleteAllFinalized() }
    }

    private fun trafficDeltas(
        sessionId: Long,
        baseline: List<NetworkBaselineEntryEntity>,
        end: List<NetworkTrafficEntry>,
    ): List<NetworkAppUsageEntity> {
        val baselineByUid = baseline.associateBy { it.uid }

        return end
            .distinctBy { it.uid }
            .mapNotNull { entry ->
                val start = baselineByUid[entry.uid]
                val rx = (entry.rxBytes - (start?.rxBytes ?: 0L)).coerceAtLeast(0L)
                val tx = (entry.txBytes - (start?.txBytes ?: 0L)).coerceAtLeast(0L)

                if (rx + tx <= 0L) {
                    return@mapNotNull null
                }

                NetworkAppUsageEntity(
                    sessionId = sessionId,
                    uid = entry.uid,
                    packageName = entry.packageName ?: start?.packageName,
                    rxBytes = rx,
                    txBytes = tx,
                    rxPackets = packetDelta(entry.rxPackets, start, start?.rxPackets),
                    txPackets = packetDelta(entry.txPackets, start, start?.txPackets),
                )
            }
    }

    /** Counter delta; a UID without baseline row started from zero. */
    private fun packetDelta(
        end: Long?,
        baselineRow: NetworkBaselineEntryEntity?,
        start: Long?,
    ): Long? =
        when {
            end == null -> null
            baselineRow == null -> end.coerceAtLeast(0L)
            start == null -> null
            else -> (end - start).coerceAtLeast(0L)
        }

    companion object {
        const val MAX_SESSIONS = 20
        const val MAX_NOTE_LENGTH = 120
    }
}
