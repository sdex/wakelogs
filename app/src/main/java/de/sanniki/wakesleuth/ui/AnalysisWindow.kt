package de.sanniki.wakesleuth.ui

import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity

/**
 * Time range the analysis cards look at: the bounds of the newest
 * session, running up to now while monitoring is still active.
 */
data class AnalysisWindow(
    val startMillis: Long,
    val endMillis: Long,
    val ongoing: Boolean,
) {
    val durationMillis: Long
        get() = (endMillis - startMillis).coerceAtLeast(0L)

    operator fun contains(timestamp: Long): Boolean = timestamp in startMillis..endMillis

    companion object {
        fun of(
            session: MonitoringSessionEntity?,
            now: Long,
        ): AnalysisWindow? {
            session ?: return null

            val running = session.stopRequestedAt == null && session.finalizedAt == null

            return AnalysisWindow(
                startMillis = session.startedAt,
                endMillis = if (running) {
                    now
                } else {
                    (session.stopRequestedAt ?: session.finalizedAt ?: session.startedAt)
                        .coerceAtLeast(session.startedAt)
                },
                ongoing = running,
            )
        }
    }
}
