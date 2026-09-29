package de.sanniki.wakesleuth.ui.main

import androidx.compose.foundation.lazy.LazyListScope
import de.sanniki.wakesleuth.AppProfilesCard
import de.sanniki.wakesleuth.ui.sessions.SessionComparisonCard
import de.sanniki.wakesleuth.ui.sessions.SessionHistoryCard

internal fun LazyListScope.sessionsSection(
    state: WakeSleuthState,
    onIntent: (WakeSleuthIntent) -> Unit,
) {
    if (!state.uiSettings.showNightAnalysis) {
        return
    }

    val detailLevel = state.uiSettings.detailLevel

    item { SessionComparisonCard(sessions = state.archivedSessions, detailLevel = detailLevel) }

    item { AppProfilesCard(sessions = state.archivedSessions, detailLevel = detailLevel) }

    item {
        SessionHistoryCard(
            sessions = state.archivedSessions,
            detailLevel = detailLevel,
            onSaveNote = { sessionId, note -> onIntent(WakeSleuthIntent.SessionNoteSaved(sessionId, note)) },
            onDelete = { sessionId -> onIntent(WakeSleuthIntent.SessionDeleted(sessionId)) },
            onDeleteAll = { onIntent(WakeSleuthIntent.ArchiveCleared) },
        )
    }
}
