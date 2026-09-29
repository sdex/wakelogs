package de.sanniki.wakesleuth.ui.monitor

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.sanniki.wakesleuth.WakeMonitorService

internal fun isNotificationAccessEnabled(context: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

internal fun startMonitoring(context: Context) {
    val intent = Intent(
        context,
        WakeMonitorService::class.java,
    ).apply {
        action = WakeMonitorService.ACTION_START
    }

    ContextCompat.startForegroundService(context, intent)
}

internal fun stopMonitoring(
    context: Context,
    requestedAtMillis: Long,
) {
    // A stop must never start the service (and with it a new session).
    if (!WakeMonitorService.isRunning) {
        return
    }

    val intent = Intent(
        context,
        WakeMonitorService::class.java,
    ).apply {
        action = WakeMonitorService.ACTION_STOP

        putExtra("stop_requested_at_millis", requestedAtMillis)
    }

    context.startService(intent)
}
