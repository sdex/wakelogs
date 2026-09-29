package de.sanniki.wakesleuth.ui.events

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sanniki.wakesleuth.DetailLevel
import de.sanniki.wakesleuth.GroupedCpuEventListItem
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.source
import de.sanniki.wakesleuth.ui.common.PreviewSamples
import de.sanniki.wakesleuth.ui.common.PreviewSurface
import de.sanniki.wakesleuth.ui.events.EventCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun GroupedCpuEventCard(
    group: GroupedCpuEventListItem,
    detailLevel: DetailLevel,
) {
    val expanded = remember(
        group.stableKey,
    ) {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.main_grouped_cpu_activity),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = group.source
                            ?: stringResource(R.string.grouping_source_ambiguous),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    text = group.events.size.toString() + "×",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            GroupedEventValueRow(
                label = stringResource(R.string.main_grouped_similar_events),
                value = group.events.size.toString(),
            )

            GroupedEventValueRow(
                label = stringResource(R.string.main_grouped_time_range),
                value = formatGroupedEventRange(group.events),
            )

            group.totalDurationMillis
                ?.let { duration ->
                    GroupedEventValueRow(
                        label = stringResource(R.string.main_grouped_total_cpu_awake_time),
                        value = formatGroupedCpuDuration(duration),
                    )
                }

            group.longestDurationMillis
                ?.let { duration ->
                    GroupedEventValueRow(
                        label = stringResource(R.string.main_grouped_longest_operation),
                        value = formatGroupedCpuDuration(duration),
                    )
                }

            val averageDurationMillis = group.averageDurationMillis

            averageDurationMillis
                ?.let { duration ->
                    GroupedEventValueRow(
                        label = stringResource(R.string.main_grouped_average),
                        value = formatGroupedCpuDuration(duration),
                    )
                }

            Spacer(modifier = Modifier.height(10.dp))

            val classification = classifyGroupedCpuActivity(
                count = group.events.size,
                totalDurationMillis = group.totalDurationMillis,
                longestDurationMillis = group.longestDurationMillis,
                averageDurationMillis = averageDurationMillis,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme
                        .colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.58f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = stringResource(classification.title),
                        color = classification.color(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = stringResource(classification.explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.main_grouped_cpu_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    expanded.value = !expanded.value
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (expanded.value) {
                        stringResource(R.string.main_single_events_hide)
                    } else {
                        stringResource(R.string.main_single_events_show)
                    },
                )
            }

            if (expanded.value) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(10.dp))

                group.events
                    .forEachIndexed {
                        index,
                        event,
                        ->

                        EventCard(event = event, detailLevel = detailLevel)

                        if (
                            index <
                            group.events.lastIndex
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
            }
        }
    }
}

internal data class GroupedCpuClassification(
    @StringRes val title: Int,
    @StringRes val explanation: Int,
    val level: Int,
)

@Composable
internal fun GroupedCpuClassification.color() =
    when (level) {
        0 -> {
            Color(0xFF35A853)
        }

        1 -> {
            MaterialTheme.colorScheme.primary
        }

        else -> {
            MaterialTheme.colorScheme.error
        }
    }

private fun classifyGroupedCpuActivity(
    count: Int,
    totalDurationMillis: Long?,
    longestDurationMillis: Long?,
    averageDurationMillis: Long?,
): GroupedCpuClassification {
    if (
        totalDurationMillis == null || longestDurationMillis == null || averageDurationMillis == null
    ) {
        return GroupedCpuClassification(
            title = R.string.main_grouped_class_incomplete_title,
            explanation = R.string.main_grouped_class_incomplete_text,
            level = 1,
        )
    }

    return when {
        longestDurationMillis >=
            30_000L ||
            averageDurationMillis >=
            15_000L ||
            totalDurationMillis >=
            90_000L -> {
            GroupedCpuClassification(
                title = R.string.main_grouped_class_long_title,
                explanation = R.string.main_grouped_class_long_text,
                level = 2,
            )
        }

        longestDurationMillis >=
            5_000L ||
            averageDurationMillis >=
            2_500L ||
            totalDurationMillis >=
            20_000L ||
            count >= 10 -> {
            GroupedCpuClassification(
                title = R.string.main_grouped_class_notable_title,
                explanation = R.string.main_grouped_class_notable_text,
                level = 1,
            )
        }

        else -> {
            GroupedCpuClassification(
                title = R.string.main_grouped_class_short_title,
                explanation = R.string.main_grouped_class_short_text,
                level = 0,
            )
        }
    }
}

@Composable
private fun GroupedEventValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = value,
            modifier = Modifier.weight(1.15f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

private fun formatGroupedEventRange(events: List<RecordedEvent>): String {
    val oldest = events.minOf { it.occurredAt }

    val newest = events.maxOf { it.occurredAt }

    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    return if (oldest == newest) {
        formatter.format(Date(newest))
    } else {
        formatter.format(Date(oldest)) + " – " + formatter.format(Date(newest))
    }
}

private fun formatGroupedCpuDuration(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)

    return when {
        safeMillis < 1_000L -> {
            "$safeMillis ms"
        }

        safeMillis < 60_000L -> {
            String.format(Locale.getDefault(), "%.1f s", safeMillis / 1_000.0)
        }

        else -> {
            val minutes = safeMillis / 60_000L

            val seconds = safeMillis %
                60_000L /
                1_000L

            "$minutes min $seconds s"
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupedCpuEventCardPreview() {
    PreviewSurface {
        GroupedCpuEventCard(
            group = GroupedCpuEventListItem(
                source = "Messenger",
                groupKey = "com.example.messenger",
                events = PreviewSamples.cpuWakeupEvents,
                totalDurationMillis = 12_000L,
                longestDurationMillis = 4_000L,
            ),
            detailLevel = DetailLevel.NORMAL,
        )
    }
}
