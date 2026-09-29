package de.sanniki.wakesleuth.ui.main

import androidx.compose.foundation.lazy.LazyListScope
import de.sanniki.wakesleuth.DetailLevel
import de.sanniki.wakesleuth.ui.diagnostics.ShizukuProfileHintCard
import de.sanniki.wakesleuth.ui.diagnostics.ShizukuWakeLockCard

internal fun LazyListScope.diagnosticsSection(
    state: WakeSleuthState,
    onIntent: (WakeSleuthIntent) -> Unit,
) {
    val diagnostics = state.diagnostics

    item {
        if (state.uiSettings.detailLevel == DetailLevel.EXPERT) {
            ShizukuWakeLockCard(
                state = state.setup.shizukuState,
                diagnostic = diagnostics.wakeLock,
                loading = diagnostics.wakeLockLoading,
                alarmDiagnostic = diagnostics.wakeupAlarm,
                alarmLoading = diagnostics.wakeupAlarmLoading,
                jobDiagnostic = diagnostics.backgroundJob,
                jobLoading = diagnostics.backgroundJobLoading,
                wakeReasonDiagnostic = diagnostics.wakeReason,
                wakeReasonLoading = diagnostics.wakeReasonLoading,
                networkStatsDiagnostic = diagnostics.networkStats,
                networkStatsLoading = diagnostics.networkStatsLoading,
                onRequestPermission = { onIntent(WakeSleuthIntent.RequestShizukuPermissionClicked) },
                onCheck = { onIntent(WakeSleuthIntent.DiagnosticCheckClicked(DiagnosticKind.WAKE_LOCKS)) },
                onAlarmCheck = { onIntent(WakeSleuthIntent.DiagnosticCheckClicked(DiagnosticKind.WAKEUP_ALARMS)) },
                onJobCheck = { onIntent(WakeSleuthIntent.DiagnosticCheckClicked(DiagnosticKind.BACKGROUND_JOBS)) },
                onWakeReasonCheck = { onIntent(WakeSleuthIntent.DiagnosticCheckClicked(DiagnosticKind.WAKE_REASON)) },
                onNetworkStatsCheck = {
                    onIntent(WakeSleuthIntent.DiagnosticCheckClicked(DiagnosticKind.NETWORK_STATS))
                },
            )
        } else {
            ShizukuProfileHintCard(
                state = state.setup.shizukuState,
                detailLevel = state.uiSettings.detailLevel,
                onRequestPermission = { onIntent(WakeSleuthIntent.RequestShizukuPermissionClicked) },
            )
        }
    }
}
