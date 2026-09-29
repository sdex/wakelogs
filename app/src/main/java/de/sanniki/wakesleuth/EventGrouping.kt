package de.sanniki.wakesleuth

import android.content.Context
import java.util.Locale

sealed interface EventListItem {
    val stableKey: String
    val newestTimestamp: Long
}

data class SingleEventListItem(
    val event: WakeEvent
) : EventListItem {
    override val stableKey: String =
        "event_${event.id}"

    override val newestTimestamp: Long =
        event.timestamp
}

data class GroupedCpuEventListItem(
    val source: String,
    val events: List<WakeEvent>,
    val totalDurationMillis: Long?,
    val longestDurationMillis: Long?
) : EventListItem {
    override val stableKey: String =
        "cpu_group_" +
            source.lowercase(Locale.ROOT) +
            "_" +
            events.joinToString("_") {
                it.id.toString()
            }

    override val newestTimestamp: Long =
        events.maxOf {
            it.timestamp
        }
}

fun buildGroupedEventList(
    context: Context,
    events: List<WakeEvent>
): List<EventListItem> {
    val cpuGroups =
        events
            .filter {
                it.type == "CPU_WAKEUP"
            }
            .groupBy {
                cpuGroupingSource(
                    context,
                    it
                )
            }

    val groupedEventIds =
        cpuGroups
            .filterValues {
                it.size >= 2
            }
            .values
            .flatten()
            .map {
                it.id
            }
            .toSet()

    val items =
        mutableListOf<EventListItem>()

    events
        .filterNot {
            it.id in groupedEventIds
        }
        .forEach { event ->
            items.add(
                SingleEventListItem(
                    event = event
                )
            )
        }

    cpuGroups
        .filterValues {
            it.size >= 2
        }
        .forEach {
                source,
                groupedEvents ->

            val durations =
                groupedEvents.mapNotNull {
                    parseCpuDurationMillisForGrouping(
                        context,
                        it.details
                    )
                }

            items.add(
                GroupedCpuEventListItem(
                    source = source,
                    events =
                        groupedEvents.sortedByDescending {
                            it.timestamp
                        },
                    totalDurationMillis =
                        durations
                            .takeIf {
                                it.isNotEmpty()
                            }
                            ?.sum(),
                    longestDurationMillis =
                        durations.maxOrNull()
                )
            )
        }

    return items.sortedByDescending {
        it.newestTimestamp
    }
}

private fun cpuGroupingSource(
    context: Context,
    event: WakeEvent
): String {
    val detailsSource =
        event.details
            .lineSequence()
            .map {
                it.trim()
            }
            .firstOrNull {
                LocalizedText.startsWithAny(
                    it,
                    context,
                    R.string.timeline_prefix_possible_source
                )
            }
            ?.substringAfter(":")
            ?.trim()
            ?.takeUnless { value ->
                value.isBlank() ||
                    LocalizedText
                        .variants(
                            context,
                            R.string.bg_possible_source_ambiguous
                        )
                        .any {
                            value.equals(
                                it,
                                ignoreCase = true
                            )
                        }
            }

    if (detailsSource != null) {
        return sourceDisplayName(
            context,
            detailsSource
        )
    }

    val titleSource =
        event.title
            .substringAfter(
                "·",
                ""
            )
            .trim()
            .takeUnless {
                it.isBlank()
            }

    return titleSource
        ?.let {
            sourceDisplayName(
                context,
                it
            )
        }
        ?: context.getString(
            R.string.grouping_source_ambiguous
        )
}

private fun parseCpuDurationMillisForGrouping(
    context: Context,
    details: String
): Long? {
    val value =
        details
            .lineSequence()
            .map {
                it.trim()
            }
            .firstOrNull {
                LocalizedText.startsWithAny(
                    it,
                    context,
                    R.string.timeline_prefix_cpu_awake_time
                )
            }
            ?.substringAfter(":")
            ?.trim()
            ?: return null

    val normalized =
        value.replace(
            ',',
            '.'
        )

    val number =
        Regex(
            """(\d+(?:\.\d+)?)"""
        )
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?: return null

    return when {
        normalized.contains(
            " ms",
            ignoreCase = true
        ) ->
            number.toLong()

        normalized.contains(
            "Sek",
            ignoreCase = true
        ) ||
            normalized.contains(
                "sec",
                ignoreCase = true
            ) ->
            (number * 1_000.0).toLong()

        normalized.contains(
            "Min",
            ignoreCase = true
        ) ->
            (number * 60_000.0).toLong()

        else ->
            null
    }
}
