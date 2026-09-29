package de.sanniki.wakesleuth.ui.sessions

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.ArchivedSession
import de.sanniki.wakesleuth.ArchivedSessionApp
import de.sanniki.wakesleuth.DetailLevel
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ui.common.PreviewSamples
import de.sanniki.wakesleuth.ui.common.PreviewSurface
import de.sanniki.wakesleuth.ui.common.formatComparisonDuration
import de.sanniki.wakesleuth.ui.common.formatNetworkBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SessionComparisonData(
    val latest: ArchivedSession,
    val previous: ArchivedSession,
    val summary: String,
)

@Composable
internal fun SessionComparisonCard(
    sessions: List<ArchivedSession>,
    detailLevel: DetailLevel,
) {
    val context = LocalContext.current

    val comparison = remember(sessions) {
        if (sessions.size < 2) {
            null
        } else {
            val latest = sessions[0]

            val previous = sessions[1]

            SessionComparisonData(
                latest = latest,
                previous = previous,
                summary = buildSessionComparisonSummary(context = context, latest = latest, previous = previous),
            )
        }
    }

    if (comparison == null) {
        return
    }

    val latest = comparison.latest

    val previous = comparison.previous

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.main_session_comparison),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.main_session_comparison_versus,
                    formatComparisonSessionTime(latest.startMillis),
                    formatComparisonSessionTime(previous.startMillis),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                ),
            ) {
                Text(
                    text = comparison.summary,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SessionComparisonHeader()

            HorizontalDivider()

            SessionComparisonRow(
                label = stringResource(R.string.main_metric_duration),
                latest = formatComparisonDuration(latest.durationMillis),
                previous = formatComparisonDuration(previous.durationMillis),
                change = formatDurationChange(latest.durationMillis - previous.durationMillis),
                changeValue = (latest.durationMillis - previous.durationMillis).toDouble(),
            )

            SessionComparisonRow(
                label = stringResource(R.string.main_metric_screen_on),
                latest = latest.displayWakeups.toString(),
                previous = previous.displayWakeups.toString(),
                change = formatCountChange(latest.displayWakeups - previous.displayWakeups),
                changeValue = (latest.displayWakeups - previous.displayWakeups).toDouble(),
                meaning = ComparisonChangeMeaning.LOWER_IS_BETTER,
            )

            SessionComparisonRow(
                label = stringResource(R.string.main_metric_cpu_wakes),
                latest = latest.cpuWakeups.toString(),
                previous = previous.cpuWakeups.toString(),
                change = formatCountChange(latest.cpuWakeups - previous.cpuWakeups),
                changeValue = (latest.cpuWakeups - previous.cpuWakeups).toDouble(),
                meaning = ComparisonChangeMeaning.LOWER_IS_BETTER,
            )

            SessionComparisonRow(
                label = stringResource(R.string.main_metric_network),
                latest = formatNetworkBytes(latest.networkTotalBytes),
                previous = formatNetworkBytes(previous.networkTotalBytes),
                change = formatNetworkChange(latest.networkTotalBytes - previous.networkTotalBytes),
                changeValue = (latest.networkTotalBytes - previous.networkTotalBytes).toDouble(),
            )

            if (
                detailLevel != DetailLevel.SIMPLE
            ) {
                val latestNetworkPerMinute =
                    networkBytesPerMinute(bytes = latest.networkTotalBytes, durationMillis = latest.durationMillis)

                val previousNetworkPerMinute =
                    networkBytesPerMinute(bytes = previous.networkTotalBytes, durationMillis = previous.durationMillis)

                SessionComparisonRow(
                    label = stringResource(R.string.main_metric_network_per_minute),
                    latest = formatNetworkBytes(latestNetworkPerMinute),
                    previous = formatNetworkBytes(previousNetworkPerMinute),
                    change = formatNetworkChange(latestNetworkPerMinute - previousNetworkPerMinute),
                    changeValue = (latestNetworkPerMinute - previousNetworkPerMinute).toDouble(),
                )
                SessionComparisonRow(
                    label = stringResource(R.string.main_metric_active_apps),
                    latest = latest.networkActiveApps.toString(),
                    previous = previous.networkActiveApps.toString(),
                    change = formatCountChange(latest.networkActiveApps - previous.networkActiveApps),
                    changeValue = (latest.networkActiveApps - previous.networkActiveApps).toDouble(),
                )
            }

            if (
                detailLevel == DetailLevel.EXPERT
            ) {
                SessionComparisonRow(
                    label = stringResource(R.string.main_metric_received),
                    latest = formatNetworkBytes(latest.networkRxBytes),
                    previous = formatNetworkBytes(previous.networkRxBytes),
                    change = formatNetworkChange(latest.networkRxBytes - previous.networkRxBytes),
                    changeValue = (latest.networkRxBytes - previous.networkRxBytes).toDouble(),
                )

                SessionComparisonRow(
                    label = stringResource(R.string.main_metric_sent),
                    latest = formatNetworkBytes(latest.networkTxBytes),
                    previous = formatNetworkBytes(previous.networkTxBytes),
                    change = formatNetworkChange(latest.networkTxBytes - previous.networkTxBytes),
                    changeValue = (latest.networkTxBytes - previous.networkTxBytes).toDouble(),
                )
            }

            val latestTopApp = latest.topApps.firstOrNull()

            val previousTopApp = previous.topApps.firstOrNull()

            if (
                latestTopApp != null || previousTopApp != null
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.main_most_active_apps),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                SessionTopAppRow(label = stringResource(R.string.main_latest_session), app = latestTopApp)

                SessionTopAppRow(label = stringResource(R.string.main_previous_session), app = previousTopApp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.main_session_comparison_disclaimer),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SessionComparisonHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.main_comparison_metric),
            modifier = Modifier.weight(1.25f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )

        Text(
            text = stringResource(R.string.main_comparison_now),
            modifier = Modifier.weight(0.8f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )

        Text(
            text = stringResource(R.string.main_comparison_before),
            modifier = Modifier.weight(0.8f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )

        Text(
            text = stringResource(R.string.main_comparison_change),
            modifier = Modifier.weight(0.9f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )
    }
}

private enum class ComparisonChangeMeaning {
    LOWER_IS_BETTER,
    NEUTRAL,
}

@Composable
private fun SessionComparisonRow(
    label: String,
    latest: String,
    previous: String,
    change: String,
    changeValue: Double = 0.0,
    meaning: ComparisonChangeMeaning = ComparisonChangeMeaning.NEUTRAL,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1.25f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = latest,
            modifier = Modifier.weight(0.8f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = previous,
            modifier = Modifier.weight(0.8f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val changeColor = when {
            changeValue == 0.0 -> {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            meaning == ComparisonChangeMeaning.LOWER_IS_BETTER && changeValue < 0.0 -> {
                MaterialTheme.colorScheme.primary
            }

            meaning == ComparisonChangeMeaning.LOWER_IS_BETTER && changeValue > 0.0 -> {
                MaterialTheme.colorScheme.error
            }

            else -> {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        }

        Text(
            text = change,
            modifier = Modifier.weight(0.9f),
            color = changeColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun SessionTopAppRow(
    label: String,
    app: ArchivedSessionApp?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )

        Spacer(modifier = Modifier.height(3.dp))

        if (app == null) {
            Text(
                text = stringResource(R.string.main_no_data_short),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                text = app.name,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = formatNetworkBytes(app.totalBytes),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun buildSessionComparisonSummary(
    context: Context,
    latest: ArchivedSession,
    previous: ArchivedSession,
): String =
    context.getString(
        when (classifySessionComparison(latest, previous)) {
            SessionComparisonVerdict.NO_WAKEUPS -> R.string.main_comparison_summary_no_wakeups
            SessionComparisonVerdict.MUCH_CALMER -> R.string.main_comparison_summary_much_calmer
            SessionComparisonVerdict.MORE_INTERRUPTIONS -> R.string.main_comparison_summary_more_interruptions
            SessionComparisonVerdict.SLIGHTLY_CALMER -> R.string.main_comparison_summary_slightly_calmer
            SessionComparisonVerdict.SLIGHTLY_MORE_ACTIVITY -> R.string.main_comparison_summary_slightly_more_activity
            SessionComparisonVerdict.SIMILAR -> R.string.main_comparison_summary_similar
        },
    )

internal enum class SessionComparisonVerdict {
    NO_WAKEUPS,
    MUCH_CALMER,
    MORE_INTERRUPTIONS,
    SLIGHTLY_CALMER,
    SLIGHTLY_MORE_ACTIVITY,
    SIMILAR,
}

/** Shorter sessions give meaningless wakeups-per-hour rates; below this only the deltas are compared. */
internal const val MIN_RATE_COMPARISON_DURATION_MILLIS = 60_000L

internal fun classifySessionComparison(
    latest: ArchivedSession,
    previous: ArchivedSession,
): SessionComparisonVerdict {
    val displayDelta = latest.displayWakeups - previous.displayWakeups

    val cpuDelta = latest.cpuWakeups - previous.cpuWakeups

    val latestWakeups = latest.displayWakeups + latest.cpuWakeups

    val previousWakeups = previous.displayWakeups + previous.cpuWakeups

    if (latestWakeups == 0 && previousWakeups == 0) {
        return SessionComparisonVerdict.NO_WAKEUPS
    }

    val comparableDurations = latest.durationMillis >= MIN_RATE_COMPARISON_DURATION_MILLIS &&
        previous.durationMillis >= MIN_RATE_COMPARISON_DURATION_MILLIS

    if (comparableDurations) {
        val latestWakeRate = latestWakeups / (latest.durationMillis / 3_600_000.0)

        val previousWakeRate = previousWakeups / (previous.durationMillis / 3_600_000.0)

        if (latestWakeRate < previousWakeRate * 0.75) {
            return SessionComparisonVerdict.MUCH_CALMER
        }

        if (latestWakeRate > previousWakeRate * 1.25) {
            return SessionComparisonVerdict.MORE_INTERRUPTIONS
        }
    }

    return when {
        displayDelta < 0 || cpuDelta < 0 -> SessionComparisonVerdict.SLIGHTLY_CALMER
        displayDelta > 0 || cpuDelta > 0 -> SessionComparisonVerdict.SLIGHTLY_MORE_ACTIVITY
        else -> SessionComparisonVerdict.SIMILAR
    }
}

private fun formatComparisonSessionTime(timestamp: Long): String = SimpleDateFormat("dd.MM. · HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun formatDurationChange(deltaMillis: Long): String {
    if (
        kotlin.math.abs(deltaMillis) <
        1_000L
    ) {
        return stringResource(R.string.main_change_equal)
    }

    val prefix = if (deltaMillis > 0L) {
        "+"
    } else {
        "−"
    }

    return prefix + formatComparisonDuration(kotlin.math.abs(deltaMillis))
}

@Composable
private fun formatCountChange(delta: Int): String =
    when {
        delta > 0 -> {
            "+$delta"
        }

        delta < 0 -> {
            "−${kotlin.math.abs(delta)}"
        }

        else -> {
            stringResource(R.string.main_change_equal)
        }
    }

internal fun networkBytesPerMinute(
    bytes: Long,
    durationMillis: Long,
): Long {
    if (
        bytes <= 0L || durationMillis <= 0L
    ) {
        return 0L
    }

    val minutes = durationMillis / 60_000.0

    if (
        minutes <= 0.0 || !minutes.isFinite()
    ) {
        return 0L
    }

    val result = bytes / minutes

    if (
        !result.isFinite() || result <= 0.0 || result >
        Long.MAX_VALUE.toDouble()
    ) {
        return 0L
    }

    return result.toLong()
}

@Composable
private fun formatNetworkChange(deltaBytes: Long): String =
    when {
        deltaBytes > 0L -> {
            "+" + formatNetworkBytes(deltaBytes)
        }

        deltaBytes < 0L -> {
            "−" + formatNetworkBytes(kotlin.math.abs(deltaBytes))
        }

        else -> {
            stringResource(R.string.main_change_equal)
        }
    }

@Preview(showBackground = true)
@Composable
private fun SessionComparisonCardPreview() {
    PreviewSurface {
        SessionComparisonCard(sessions = PreviewSamples.archivedSessions, detailLevel = DetailLevel.NORMAL)
    }
}
