package de.sanniki.wakesleuth

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun WakeTimeline(
    events: List<WakeEvent>,
    detailLevel: DetailLevel
) {
    val timelineEvents =
        remember(events) {
            events.sortedByDescending {
                it.timestamp
            }
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 16.dp
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.timeline_title),
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            stringResource(R.string.timeline_subtitle),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }

                Text(
                    text =
                        pluralStringResource(
                            R.plurals.timeline_event_count,
                            timelineEvents.size,
                            timelineEvents.size
                        ),
                    color =
                        MaterialTheme.colorScheme.primary,
                    style =
                        MaterialTheme.typography
                            .labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            timelineEvents.forEachIndexed {
                    index,
                    event ->

                val nextOlderEvent =
                    timelineEvents.getOrNull(
                        index + 1
                    )

                TimelineEventRow(
                    event = event,
                    detailLevel = detailLevel,
                    showLine =
                        index < timelineEvents.lastIndex
                )

                if (nextOlderEvent != null) {
                    TimelineGap(
                        durationMillis =
                            event.timestamp -
                                nextOlderEvent.timestamp
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: WakeEvent,
    detailLevel: DetailLevel,
    showLine: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(62.dp),
            horizontalAlignment =
                Alignment.End
        ) {
            Text(
                text =
                    SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.getDefault()
                    ).format(
                        Date(event.timestamp)
                    ),
                color =
                    MaterialTheme.colorScheme
                        .onSurface,
                style =
                    MaterialTheme.typography
                        .labelMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    SimpleDateFormat(
                        "dd.MM.",
                        Locale.getDefault()
                    ).format(
                        Date(event.timestamp)
                    ),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .labelSmall
            )
        }

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(
                        timelinePointColor(event)
                    )
            )

            if (showLine) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(
                            timelineLineHeight(
                                event = event,
                                detailLevel =
                                    detailLevel
                            )
                        )
                        .background(
                            MaterialTheme.colorScheme
                                .outlineVariant
                        )
                )
            }
        }

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        TimelineEventContent(
            event = event,
            detailLevel = detailLevel,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TimelineEventContent(
    event: WakeEvent,
    detailLevel: DetailLevel,
    modifier: Modifier = Modifier
) {
    val context =
        LocalContext.current

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                timelineContainerColor(event)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 10.dp
            )
        ) {
            Text(
                text =
                    timelineEventTypeLabel(
                        event.type
                    ),
                color =
                    MaterialTheme.colorScheme.primary,
                style =
                    MaterialTheme.typography
                        .labelSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = event.title,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            val summary =
                remember(
                    event.details,
                    detailLevel
                ) {
                    timelineSummary(
                        context = context,
                        event = event,
                        detailLevel =
                            detailLevel
                    )
                }

            if (summary.isNotBlank()) {
                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                if (
                    detailLevel ==
                    DetailLevel.EXPERT
                ) {
                    HorizontalDivider()

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )
                }

                Text(
                    text = summary,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }
    }
}

@Composable
private fun TimelineGap(
    durationMillis: Long
) {
    val safeDuration =
        abs(durationMillis)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 72.dp,
                top = 3.dp,
                bottom = 3.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = "↓",
            color =
                MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        Text(
            text =
                formatTimelineDuration(
                    safeDuration
                ),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .labelSmall
        )
    }
}

@Composable
private fun timelinePointColor(
    event: WakeEvent
) =
    when (event.type) {
        "SCREEN_ON",
        "CPU_WAKEUP",
        "NOTIFICATION" ->
            MaterialTheme.colorScheme.primary

        "MONITOR_START" ->
            MaterialTheme.colorScheme.tertiary

        "MONITOR_STOP" ->
            MaterialTheme.colorScheme.error

        else ->
            MaterialTheme.colorScheme
                .onSurfaceVariant
    }

@Composable
private fun timelineContainerColor(
    event: WakeEvent
) =
    when (event.type) {
        "SCREEN_ON",
        "CPU_WAKEUP" ->
            MaterialTheme.colorScheme
                .primaryContainer
                .copy(alpha = 0.32f)

        "MONITOR_START",
        "MONITOR_STOP" ->
            MaterialTheme.colorScheme
                .surfaceVariant
                .copy(alpha = 0.42f)

        else ->
            MaterialTheme.colorScheme.surface
    }

private fun timelineLineHeight(
    event: WakeEvent,
    detailLevel: DetailLevel
) =
    when {
        detailLevel == DetailLevel.EXPERT &&
            event.details.isNotBlank() ->
            108.dp

        detailLevel == DetailLevel.NORMAL &&
            event.details.isNotBlank() ->
            78.dp

        else ->
            58.dp
    }

private fun timelineSummary(
    context: Context,
    event: WakeEvent,
    detailLevel: DetailLevel
): String {
    if (
        detailLevel == DetailLevel.SIMPLE ||
        event.details.isBlank()
    ) {
        return ""
    }

    if (detailLevel == DetailLevel.EXPERT) {
        return event.details.trim()
    }

    val preferredPrefixes =
        listOf(
            R.string.timeline_prefix_direct_wake_reason,
            R.string.timeline_prefix_likely_cause,
            R.string.timeline_prefix_possible_cause,
            R.string.timeline_prefix_later_detected_cause,
            R.string.timeline_prefix_possible_source,
            R.string.timeline_prefix_system_reason,
            R.string.timeline_prefix_cpu_awake_time,
            R.string.timeline_prefix_source,
            R.string.timeline_prefix_kind,
            R.string.timeline_prefix_cause
        ).flatMap {
            LocalizedText.variants(
                context,
                it
            )
        }

    val usefulLines =
        event.details
            .lineSequence()
            .map {
                it.trim()
            }
            .filter {
                it.isNotBlank()
            }
            .filter { line ->
                preferredPrefixes.any {
                        prefix ->

                    line.startsWith(prefix)
                }
            }
            .distinct()
            .take(4)
            .toList()

    if (usefulLines.isNotEmpty()) {
        return usefulLines.joinToString(
            separator = "\n"
        )
    }

    return event.details
        .lineSequence()
        .map {
            it.trim()
        }
        .filter {
            it.isNotBlank()
        }
        .take(3)
        .joinToString(
            separator = "\n"
        )
}

@Composable
private fun timelineEventTypeLabel(
    type: String
): String {
    return when (type) {
        "MONITOR_START" ->
            stringResource(R.string.timeline_type_monitor_start)

        "MONITOR_STOP" ->
            stringResource(R.string.timeline_type_monitor_stop)

        "SCREEN_ON" ->
            stringResource(R.string.timeline_type_screen_on)

        "SCREEN_OFF" ->
            stringResource(R.string.timeline_type_screen_off)

        "CPU_WAKEUP" ->
            stringResource(R.string.timeline_type_cpu_wakeup)

        "NOTIFICATION" ->
            stringResource(R.string.timeline_type_notification)

        "POWER_CONNECTED" ->
            stringResource(R.string.timeline_type_power_connected)

        "POWER_DISCONNECTED" ->
            stringResource(R.string.timeline_type_power_disconnected)

        "USB_ATTACHED" ->
            stringResource(R.string.timeline_type_usb_attached)

        "USB_DETACHED" ->
            stringResource(R.string.timeline_type_usb_detached)

        else ->
            type.replace(
                '_',
                ' '
            )
    }
}

@Composable
private fun formatTimelineDuration(
    millis: Long
): String {
    val totalSeconds =
        millis / 1_000L

    val days =
        totalSeconds / 86_400L

    val hours =
        totalSeconds %
            86_400L /
            3_600L

    val minutes =
        totalSeconds %
            3_600L /
            60L

    val seconds =
        totalSeconds % 60L

    return when {
        days > 0L ->
            stringResource(
                R.string.timeline_gap_days_hours,
                days,
                hours
            )

        hours > 0L ->
            stringResource(
                R.string.timeline_gap_hours_minutes,
                hours,
                minutes
            )

        minutes > 0L ->
            stringResource(
                R.string.timeline_gap_minutes_seconds,
                minutes,
                seconds
            )

        totalSeconds > 0L ->
            stringResource(
                R.string.timeline_gap_seconds,
                totalSeconds
            )

        millis > 0L ->
            stringResource(
                R.string.timeline_gap_millis,
                millis
            )

        else ->
            stringResource(R.string.timeline_gap_simultaneous)
    }
}
