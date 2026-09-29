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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity
import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.NotificationEvent
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.SourceClassifier
import de.sanniki.wakesleuth.domain.SourceRef
import de.sanniki.wakesleuth.domain.primarySource
import de.sanniki.wakesleuth.domain.sources
import de.sanniki.wakesleuth.ui.AnalysisWindow
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver
import de.sanniki.wakesleuth.ui.render.rememberSourceLabelResolver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private data class SourceStatisticsEntry(
    val source: SourceRef,
    val name: String,
    val totalCount: Int,
    val displayCount: Int,
    val cpuCount: Int,
    val notificationCount: Int,
)

@Composable
fun SourceStatisticsCard(
    events: List<RecordedEvent>,
    session: MonitoringSessionEntity?,
    detailLevel: DetailLevel,
) {
    val context = LocalContext.current

    val labels = rememberSourceLabelResolver()

    val window = remember(
        events,
        session,
    ) {
        AnalysisWindow.of(
            session = session,
            now = System.currentTimeMillis(),
        ) ?: AnalysisWindow(
            startMillis = events.minOfOrNull { it.occurredAt } ?: 0L,
            endMillis = events.maxOfOrNull { it.occurredAt } ?: 0L,
            ongoing = false,
        )
    }

    val entries = remember(
        events,
        window,
        labels,
    ) {
        buildSourceStatistics(labels = labels, events = events, window = window)
    }

    val visibleCount = when (detailLevel) {
        DetailLevel.SIMPLE -> 3
        DetailLevel.NORMAL -> 5
        DetailLevel.EXPERT -> 8
    }

    val visibleEntries = entries.take(visibleCount)

    val detailsExpanded = remember { mutableStateOf(false) }

    val highestCount = max(
        1,
        visibleEntries.maxOfOrNull {
            it.totalCount
        } ?: 1,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.stats_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = formatSourceStatisticsWindow(context, window),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text = pluralStringResource(R.plurals.stats_source_count, entries.size, entries.size),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (entries.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.stats_strongest_source, entries.first().name),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick = {
                    detailsExpanded.value = !detailsExpanded.value
                },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    androidx.compose.ui.graphics
                        .Color(0xFF687181),
                ),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                ),
            ) {
                Text(
                    if (detailsExpanded.value) {
                        stringResource(R.string.stats_hide_details)
                    } else {
                        stringResource(R.string.stats_show_details)
                    },
                )
            }

            if (detailsExpanded.value) {
                Spacer(modifier = Modifier.height(14.dp))

                if (visibleEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stats_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    visibleEntries.forEachIndexed {
                        index,
                        entry,
                        ->

                        SourceStatisticsRow(
                            position = index + 1,
                            entry = entry,
                            highestCount = highestCount,
                            detailLevel = detailLevel,
                        )

                        if (
                            index <
                            visibleEntries.lastIndex
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                if (
                    detailLevel == DetailLevel.EXPERT && visibleEntries.isNotEmpty()
                ) {
                    Spacer(modifier = Modifier.height(14.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.stats_ranking_disclaimer),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceStatisticsRow(
    position: Int,
    entry: SourceStatisticsEntry,
    highestCount: Int,
    detailLevel: DetailLevel,
) {
    val fraction = (entry.totalCount.toFloat() / highestCount.toFloat()).coerceIn(0.06f, 1f)

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$position.",
                modifier = Modifier.width(28.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                val displayName = entry.name

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                val technicalName = entry.source.packageName
                    ?: entry.source.rawSource

                if (
                    detailLevel == DetailLevel.EXPERT && technicalName != null && displayName != technicalName
                ) {
                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = technicalName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = entry.totalCount.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        if (
            detailLevel != DetailLevel.SIMPLE
        ) {
            Spacer(modifier = Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SourceCountLabel(label = stringResource(R.string.stats_label_display), value = entry.displayCount)

                SourceCountLabel(label = "CPU", value = entry.cpuCount)

                if (
                    detailLevel == DetailLevel.EXPERT
                ) {
                    SourceCountLabel(
                        label = stringResource(R.string.stats_label_notifications),
                        value = entry.notificationCount,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceCountLabel(
    label: String,
    value: Int,
) {
    Text(
        text = "$label: $value",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
    )
}

/**
 * Counts, per source, the screen-ons, CPU wakeups and notifications it is
 * linked to within the window. Sources are merged by identity.
 */
private fun buildSourceStatistics(
    labels: SourceLabelResolver,
    events: List<RecordedEvent>,
    window: AnalysisWindow,
): List<SourceStatisticsEntry> {
    class Counts(
        val source: SourceRef,
    ) {
        var display = 0
        var cpu = 0
        var notifications = 0
    }

    val counts = linkedMapOf<String, Counts>()

    fun counts(source: SourceRef): Counts =
        counts.getOrPut(source.groupKey) {
            Counts(source)
        }

    events
        .asSequence()
        .filter { event ->
            event.occurredAt in window
        }.forEach { event ->
            when (event) {
                is ScreenOnEvent -> {
                    event.sources().forEach { counts(it.source).display += 1 }
                }

                is CpuWakeupEvent -> {
                    event.primarySource()?.let { counts(it).cpu += 1 }
                }

                is NotificationEvent -> {
                    counts(SourceClassifier.classify(event.packageName)).notifications += 1
                }

                else -> {}
            }
        }

    return counts.values
        .map { item ->
            SourceStatisticsEntry(
                source = item.source,
                name = labels.label(item.source),
                totalCount = item.display + item.cpu + item.notifications,
                displayCount = item.display,
                cpuCount = item.cpu,
                notificationCount = item.notifications,
            )
        }.filter {
            it.totalCount > 0
        }.sortedWith(
            compareByDescending<
                SourceStatisticsEntry,
            > {
                it.totalCount
            }.thenByDescending {
                it.displayCount
            }.thenByDescending {
                it.cpuCount
            }.thenBy {
                it.name.lowercase(Locale.ROOT)
            },
        )
}

private fun formatSourceStatisticsWindow(
    context: Context,
    window: AnalysisWindow,
): String {
    val formatter = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault())

    val range = buildString {
        append(formatter.format(Date(window.startMillis)))

        append(" – ")

        append(formatter.format(Date(window.endMillis)))
    }

    return if (window.ongoing) {
        context.getString(R.string.stats_window_ongoing, range)
    } else {
        range
    }
}
