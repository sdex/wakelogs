package de.sanniki.wakesleuth

import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkAppUsageEntity
import de.sanniki.wakesleuth.data.db.entity.SessionSourceStatEntity
import de.sanniki.wakesleuth.domain.SessionEndReason
import de.sanniki.wakesleuth.domain.SourceClassifier
import de.sanniki.wakesleuth.domain.SourceRef
import de.sanniki.wakesleuth.domain.saturatedSum
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver

/** Traffic of one app over a session; [name] is resolved at read time. */
data class ArchivedSessionApp(
    val source: SourceRef,
    val name: String,
    val totalBytes: Long,
    val rxBytes: Long,
    val txBytes: Long
)

/** Wakeup activity of one source over a session. */
data class ArchivedSessionSource(
    val source: SourceRef,
    val name: String,
    val cpuCount: Int,
    val displayCount: Int,
    val companionCount: Int,
    val longestCpuDurationMillis: Long?
)

/** UI model of a finalized session, assembled from the archive tables. */
data class ArchivedSession(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long,
    val durationMillis: Long,
    val endReason: SessionEndReason?,
    val displayWakeups: Int,
    val cpuWakeups: Int,
    val networkTotalBytes: Long,
    val networkRxBytes: Long,
    val networkTxBytes: Long,
    val networkActiveApps: Int,
    /** Every app with traffic, largest first. */
    val topApps: List<ArchivedSessionApp>,
    val sources: List<ArchivedSessionSource> = emptyList(),
    val note: String? = null
)

object SessionArchive {

    fun build(
        sessions: List<MonitoringSessionEntity>,
        stats: List<SessionSourceStatEntity>,
        usage: List<NetworkAppUsageEntity>,
        labels: SourceLabelResolver
    ): List<ArchivedSession> {
        val statsBySession = stats.groupBy { it.sessionId }
        val usageBySession = usage.groupBy { it.sessionId }

        return sessions
            .filter { it.finalizedAt != null }
            .map { session ->
                val start = session.startedAt
                val end = (session.stopRequestedAt ?: session.finalizedAt ?: start).coerceAtLeast(start)
                val apps = usageBySession[session.id].orEmpty()

                val topApps =
                    apps
                        .map { item ->
                            ArchivedSessionApp(
                                source =
                                    item.packageName
                                        ?.let { SourceClassifier.classify(it) }
                                        ?: SourceClassifier.forUid(item.uid),
                                name = labels.networkLabel(item.packageName, item.uid),
                                totalBytes = saturatedSum(item.rxBytes, item.txBytes),
                                rxBytes = item.rxBytes,
                                txBytes = item.txBytes
                            )
                        }
                        .sortedByDescending { it.totalBytes }

                val rx = saturatedSum(*apps.map { it.rxBytes }.toLongArray())
                val tx = saturatedSum(*apps.map { it.txBytes }.toLongArray())

                ArchivedSession(
                    id = session.id,
                    startMillis = start,
                    endMillis = end,
                    durationMillis = end - start,
                    endReason = session.endReason,
                    displayWakeups = session.displayWakeups ?: 0,
                    cpuWakeups = session.cpuWakeups ?: 0,
                    networkTotalBytes = saturatedSum(rx, tx),
                    networkRxBytes = rx,
                    networkTxBytes = tx,
                    networkActiveApps = apps.size,
                    topApps = topApps,
                    sources =
                        statsBySession[session.id]
                            .orEmpty()
                            .map { stat ->
                                val source =
                                    SourceRef(
                                        kind = stat.sourceKind,
                                        packageName = stat.packageName.ifEmpty { null },
                                        rawSource = stat.rawSource.ifEmpty { null }
                                    )

                                ArchivedSessionSource(
                                    source = source,
                                    name = labels.label(source),
                                    cpuCount = stat.cpuCount,
                                    displayCount = stat.displayCount,
                                    companionCount = stat.companionCount,
                                    longestCpuDurationMillis = stat.longestCpuAwakeMs
                                )
                            }
                            .sortedWith(
                                compareByDescending<ArchivedSessionSource> {
                                    it.cpuCount + it.displayCount
                                }.thenBy { it.name.lowercase() }
                            ),
                    note = session.note
                )
            }
            .sortedByDescending { it.startMillis }
    }
}
