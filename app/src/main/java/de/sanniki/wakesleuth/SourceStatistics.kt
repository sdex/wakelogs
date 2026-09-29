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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private data class SourceStatisticsWindow(
    val startMillis: Long,
    val endMillis: Long,
    val ongoing: Boolean
)

private data class SourceStatisticsEntry(
    val source: String,
    val totalCount: Int,
    val displayCount: Int,
    val cpuCount: Int,
    val notificationCount: Int
)

@Composable
fun SourceStatisticsCard(
    events: List<WakeEvent>,
    monitoring: Boolean,
    detailLevel: DetailLevel
) {
    val context =
        LocalContext.current

    val window =
        remember(
            events,
            monitoring
        ) {
            calculateSourceStatisticsWindow(
                events = events,
                monitoring = monitoring
            )
        }

    val entries =
        remember(
            events,
            window
        ) {
            buildSourceStatistics(
                context = context,
                events = events,
                window = window
            )
        }

    val visibleCount =
        when (detailLevel) {
            DetailLevel.SIMPLE -> 3
            DetailLevel.NORMAL -> 5
            DetailLevel.EXPERT -> 8
        }

    val visibleEntries =
        entries.take(visibleCount)

    val detailsExpanded =
        remember {
            mutableStateOf(false)
        }

    val highestCount =
        max(
            1,
            visibleEntries.maxOfOrNull {
                it.totalCount
            } ?: 1
        )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
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
                        text =
                            stringResource(
                                R.string.stats_title
                            ),
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            formatSourceStatisticsWindow(
                                context,
                                window
                            ),
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
                            R.plurals.stats_source_count,
                            entries.size,
                            entries.size
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
                modifier = Modifier.height(12.dp)
            )

            if (entries.isNotEmpty()) {
                Text(
                    text =
                        stringResource(
                            R.string.stats_strongest_source,
                            sourceDisplayName(
                                context,
                                entries.first().source
                            )
                        ),
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )
            }

            OutlinedButton(
                onClick = {
                    detailsExpanded.value =
                        !detailsExpanded.value
                },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    androidx.compose.ui.graphics.Color(0xFF687181)
                ),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    disabledContentColor =
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                ),
            ) {
                Text(
                    if (detailsExpanded.value) {
                        stringResource(
                            R.string.stats_hide_details
                        )
                    } else {
                        stringResource(
                            R.string.stats_show_details
                        )
                    }
                )
            }

            if (detailsExpanded.value) {
                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                if (visibleEntries.isEmpty()) {
                    Text(
                        text =
                            stringResource(
                                R.string.stats_empty
                            ),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography
                                .bodyMedium
                    )
                } else {
                    visibleEntries.forEachIndexed {
                            index,
                            entry ->

                        SourceStatisticsRow(
                            position = index + 1,
                            entry = entry,
                            highestCount = highestCount,
                            detailLevel = detailLevel
                        )

                        if (
                            index <
                            visibleEntries.lastIndex
                        ) {
                            Spacer(
                                modifier =
                                    Modifier.height(10.dp)
                            )

                            HorizontalDivider()

                            Spacer(
                                modifier =
                                    Modifier.height(10.dp)
                            )
                        }
                    }
                }

                if (
                    detailLevel ==
                    DetailLevel.EXPERT &&
                    visibleEntries.isNotEmpty()
                ) {
                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    HorizontalDivider()

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.stats_ranking_disclaimer
                            ),
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
}

@Composable
private fun SourceStatisticsRow(
    position: Int,
    entry: SourceStatisticsEntry,
    highestCount: Int,
    detailLevel: DetailLevel
) {
    val fraction =
        (
            entry.totalCount.toFloat() /
                highestCount.toFloat()
        ).coerceIn(
            0.06f,
            1f
        )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = "$position.",
                modifier = Modifier.width(28.dp),
                color =
                    MaterialTheme.colorScheme.primary,
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                val displayName =
                    sourceDisplayName(
                        LocalContext.current,
                        entry.source
                    )

                Text(
                    text = displayName,
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                    fontWeight =
                        FontWeight.SemiBold,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis
                )

                if (
                    detailLevel ==
                    DetailLevel.EXPERT &&
                    displayName != entry.source
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(2.dp)
                    )

                    Text(
                        text = entry.source,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography
                                .labelSmall,
                        maxLines = 2,
                        overflow =
                            TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            Text(
                text = entry.totalCount.toString(),
                color =
                    MaterialTheme.colorScheme.primary,
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(
            modifier = Modifier.height(7.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(
                    RoundedCornerShape(50)
                )
                .background(
                    MaterialTheme.colorScheme
                        .surfaceVariant
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(7.dp)
                    .clip(
                        RoundedCornerShape(50)
                    )
                    .background(
                        MaterialTheme.colorScheme
                            .primary
                    )
            )
        }

        if (
            detailLevel !=
            DetailLevel.SIMPLE
        ) {
            Spacer(
                modifier = Modifier.height(7.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                SourceCountLabel(
                    label =
                        stringResource(
                            R.string.stats_label_display
                        ),
                    value = entry.displayCount
                )

                SourceCountLabel(
                    label = "CPU",
                    value = entry.cpuCount
                )

                if (
                    detailLevel ==
                    DetailLevel.EXPERT
                ) {
                    SourceCountLabel(
                        label =
                            stringResource(
                                R.string.stats_label_notifications
                            ),
                        value =
                            entry.notificationCount
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceCountLabel(
    label: String,
    value: Int
) {
    Text(
        text = "$label: $value",
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
        style =
            MaterialTheme.typography
                .labelSmall
    )
}

private fun buildSourceStatistics(
    context: Context,
    events: List<WakeEvent>,
    window: SourceStatisticsWindow
): List<SourceStatisticsEntry> {
    data class MutableCounts(
        var display: Int = 0,
        var cpu: Int = 0,
        var notifications: Int = 0
    )

    val counts =
        linkedMapOf<String, MutableCounts>()

    events
        .asSequence()
        .filter { event ->
            event.timestamp >=
                window.startMillis &&
                event.timestamp <=
                    window.endMillis
        }
        .forEach { event ->
            val sources =
                extractStatisticsSources(
                    context,
                    event
                )

            sources.forEach { source ->
                val item =
                    counts.getOrPut(source) {
                        MutableCounts()
                    }

                when (event.type) {
                    "SCREEN_ON" ->
                        item.display += 1

                    "CPU_WAKEUP" ->
                        item.cpu += 1

                    "NOTIFICATION" ->
                        item.notifications += 1
                }
            }
        }

    return counts
        .map { entry ->
            SourceStatisticsEntry(
                source = entry.key,
                totalCount =
                    entry.value.display +
                        entry.value.cpu +
                        entry.value.notifications,
                displayCount =
                    entry.value.display,
                cpuCount =
                    entry.value.cpu,
                notificationCount =
                    entry.value.notifications
            )
        }
        .filter {
            it.totalCount > 0
        }
        .sortedWith(
            compareByDescending<
                SourceStatisticsEntry
            > {
                it.totalCount
            }.thenByDescending {
                it.displayCount
            }.thenByDescending {
                it.cpuCount
            }.thenBy {
                it.source.lowercase(
                    Locale.ROOT
                )
            }
        )
}

private fun extractStatisticsSources(
    context: Context,
    event: WakeEvent
): Set<String> {
    val acceptedPrefixes =
        listOf(
            R.string.timeline_prefix_likely_cause,
            R.string.timeline_prefix_possible_cause,
            R.string.timeline_prefix_later_detected_cause,
            R.string.timeline_prefix_possible_source,
            R.string.timeline_prefix_source
        ).flatMap {
            LocalizedText.variants(
                context,
                it
            )
        }

    val ignoredValues =
        listOf(
            R.string.bg_possible_source_ambiguous,
            R.string.event_unknown,
            R.string.event_none,
            R.string.stats_value_not_determined,
            R.string.bg_unspecified
        ).flatMap {
            LocalizedText.variants(
                context,
                it
            )
        }.toSet()

    val notificationTitles =
        LocalizedText.variants(
            context,
            R.string.stats_notification
        )

    val sources =
        event.details
            .lineSequence()
            .map {
                it.trim()
            }
            .mapNotNull { line ->
                acceptedPrefixes
                    .firstOrNull { prefix ->
                        line.startsWith(prefix)
                    }
                    ?.let { prefix ->
                        line.substringAfter(prefix)
                            .trim()
                    }
            }
            .map(::normalizeStatisticsSource)
            .filter {
                it.isNotBlank()
            }
            .filterNot { value ->
                ignoredValues.any {
                    value.equals(
                        it,
                        ignoreCase = true
                    )
                }
            }
            .toMutableSet()

    if (
        event.type == "CPU_WAKEUP"
    ) {
        event.title
            .substringAfter(
                "·",
                ""
            )
            .trim()
            .takeIf {
                it.isNotBlank()
            }
            ?.let(::normalizeStatisticsSource)
            ?.let {
                sources.add(it)
            }
    }

    if (
        event.type == "NOTIFICATION" &&
        sources.isEmpty()
    ) {
        event.title
            .substringAfter(
                "·",
                event.title
            )
            .trim()
            .takeIf { title ->
                title.isNotBlank() &&
                    notificationTitles.none {
                        title.equals(
                            it,
                            ignoreCase = true
                        )
                    }
            }
            ?.let(::normalizeStatisticsSource)
            ?.let {
                sources.add(it)
            }
    }

    return sources
}

private fun normalizeStatisticsSource(
    raw: String
): String {
    return raw
        .removePrefix("App: ")
        .substringBefore(" (")
        .substringBefore(" · ")
        .trim()
        .replace(
            Regex("""\s+"""),
            " "
        )
}

private fun calculateSourceStatisticsWindow(
    events: List<WakeEvent>,
    monitoring: Boolean
): SourceStatisticsWindow {
    val now =
        System.currentTimeMillis()

    val sorted =
        events.sortedBy {
            it.timestamp
        }

    val latestStart =
        sorted.lastOrNull {
            it.type == "MONITOR_START"
        }

    if (latestStart != null) {
        if (monitoring) {
            return SourceStatisticsWindow(
                startMillis =
                    latestStart.timestamp,
                endMillis = now,
                ongoing = true
            )
        }

        val stop =
            sorted.firstOrNull { event ->
                event.type ==
                    "MONITOR_STOP" &&
                    event.timestamp >=
                        latestStart.timestamp
            }

        val finalEvent =
            sorted.lastOrNull { event ->
                event.timestamp >=
                    latestStart.timestamp
            }

        return SourceStatisticsWindow(
            startMillis =
                latestStart.timestamp,
            endMillis =
                stop?.timestamp
                    ?: finalEvent?.timestamp
                    ?: latestStart.timestamp,
            ongoing = false
        )
    }

    if (sorted.isNotEmpty()) {
        return SourceStatisticsWindow(
            startMillis =
                sorted.first().timestamp,
            endMillis =
                sorted.last().timestamp,
            ongoing = false
        )
    }

    return SourceStatisticsWindow(
        startMillis = now,
        endMillis = now,
        ongoing = false
    )
}

private fun formatSourceStatisticsWindow(
    context: Context,
    window: SourceStatisticsWindow
): String {
    val formatter =
        SimpleDateFormat(
            "dd.MM. HH:mm",
            Locale.getDefault()
        )

    val range =
        buildString {
            append(
                formatter.format(
                    Date(window.startMillis)
                )
            )

            append(" – ")

            append(
                formatter.format(
                    Date(window.endMillis)
                )
            )
        }

    return if (window.ongoing) {
        context.getString(
            R.string.stats_window_ongoing,
            range
        )
    } else {
        range
    }
}
