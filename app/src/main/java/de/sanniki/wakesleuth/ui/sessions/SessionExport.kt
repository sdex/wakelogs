package de.sanniki.wakesleuth.ui.sessions

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import de.sanniki.wakesleuth.ArchivedSession
import de.sanniki.wakesleuth.BuildConfig
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ui.common.formatComparisonDuration
import de.sanniki.wakesleuth.ui.common.formatNetworkBytes
import de.sanniki.wakesleuth.ui.sessions.networkBytesPerMinute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun buildSessionExportFileName(
    context: Context,
    session: ArchivedSession,
): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(session.startMillis))

    return context.getString(R.string.main_session_export_file_name, timestamp)
}

internal fun buildSessionExportText(
    context: Context,
    session: ArchivedSession,
): String =
    buildString {
        appendLine("wakelogs v${BuildConfig.VERSION_NAME} · dernikiausd")
        appendLine(context.getString(R.string.main_export_kind_session_summary))
        appendLine()

        appendLine(context.getString(R.string.main_export_start, formatSessionExportTimestamp(session.startMillis)))

        appendLine(context.getString(R.string.main_export_end, formatSessionExportTimestamp(session.endMillis)))

        appendLine(context.getString(R.string.main_export_duration, formatComparisonDuration(session.durationMillis)))

        session.note
            ?.trim()
            ?.takeUnless { note ->
                note.isBlank() || note.equals("null", ignoreCase = true)
            }?.let { note ->
                appendLine(context.getString(R.string.main_export_note, note))
            }

        appendLine()
        appendLine(context.getString(R.string.main_export_technical_activity))

        appendLine(context.getString(R.string.main_export_display_wakeups, session.displayWakeups))

        appendLine(context.getString(R.string.main_export_cpu_wakeups, session.cpuWakeups))

        appendLine()
        appendLine(context.getString(R.string.main_metric_network))

        appendLine(context.getString(R.string.main_export_total, formatNetworkBytes(session.networkTotalBytes)))

        appendLine(context.getString(R.string.main_export_received, formatNetworkBytes(session.networkRxBytes)))

        appendLine(context.getString(R.string.main_export_sent, formatNetworkBytes(session.networkTxBytes)))

        appendLine(
            context.getString(
                R.string.main_export_per_minute,
                formatNetworkBytes(
                    networkBytesPerMinute(bytes = session.networkTotalBytes, durationMillis = session.durationMillis),
                ),
            ),
        )

        appendLine(context.getString(R.string.main_export_active_apps, session.networkActiveApps))

        appendLine()
        appendLine(context.getString(R.string.main_most_active_apps))

        if (session.topApps.isEmpty()) {
            appendLine(context.getString(R.string.main_export_no_app_data))
        } else {
            session.topApps.take(SESSION_EXPORT_TOP_APPS).forEachIndexed {
                index,
                app,
                ->

                appendLine("${index + 1}. " + app.name + " · " + formatNetworkBytes(app.totalBytes))
            }
        }

        appendLine()
        appendLine(context.getString(R.string.main_export_network_hint))
    }

private const val SESSION_EXPORT_TOP_APPS = 10

private fun formatSessionExportTimestamp(timestamp: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
