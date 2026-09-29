package de.sanniki.wakesleuth.ui.main

import androidx.compose.foundation.lazy.LazyListScope
import de.sanniki.wakesleuth.ui.monitor.MonitorCard
import de.sanniki.wakesleuth.ui.setup.SetupStatusCard
import de.sanniki.wakesleuth.ui.statistics.StatisticsCard

internal fun LazyListScope.overviewSection(
    state: WakeSleuthState,
    onIntent: (WakeSleuthIntent) -> Unit,
) {
    item {
        SetupStatusCard(
            notificationAccessEnabled = state.setup.notificationAccessEnabled,
            notificationsAllowed = state.setup.notificationsAllowed,
            shizukuState = state.setup.shizukuState,
            onOpenNotificationAccess = { onIntent(WakeSleuthIntent.OpenNotificationAccessClicked) },
            onRequestNotifications = { onIntent(WakeSleuthIntent.RequestNotificationsClicked) },
            onRequestShizukuPermission = { onIntent(WakeSleuthIntent.RequestShizukuPermissionClicked) },
            onOpenShizuku = { onIntent(WakeSleuthIntent.OpenShizukuClicked) },
        )
    }

    item {
        MonitorCard(
            monitoring = state.monitor.monitoring,
            finalizing = state.monitor.finalizing,
            sessionDurationMillis = state.monitor.sessionDurationMillis,
            onStart = { onIntent(WakeSleuthIntent.StartMonitoringClicked) },
            onStop = { onIntent(WakeSleuthIntent.StopMonitoringClicked) },
        )
    }

    if (state.uiSettings.showDailyStatistics) {
        item { StatisticsCard(state.dailyStatistics) }
    }
}
