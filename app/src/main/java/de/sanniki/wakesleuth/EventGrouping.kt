package de.sanniki.wakesleuth

import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.primarySource
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver

sealed interface EventListItem {
    val stableKey: String
    val newestTimestamp: Long
}

data class SingleEventListItem(
    val event: RecordedEvent,
) : EventListItem {
    override val stableKey: String = "event_${event.id}"

    override val newestTimestamp: Long = event.occurredAt
}

data class GroupedCpuEventListItem(
    /** Display name of the source; null when the wakeups are unattributed. */
    val source: String?,
    val groupKey: String,
    val events: List<CpuWakeupEvent>,
    val totalDurationMillis: Long?,
    val longestDurationMillis: Long?,
) : EventListItem {
    override val stableKey: String = "cpu_group_" + groupKey + "_" + events.joinToString("_") { it.id.toString() }

    override val newestTimestamp: Long = events.maxOf { it.occurredAt }
}

/**
 * Collapses two or more CPU wakeups of the same source into one list
 * item. Sources are compared by their identity, not by their label.
 */
fun buildGroupedEventList(
    labels: SourceLabelResolver,
    events: List<RecordedEvent>,
): List<EventListItem> {
    val cpuGroups = events
        .filterIsInstance<CpuWakeupEvent>()
        .groupBy {
            it.primarySource()?.groupKey ?: UNATTRIBUTED_KEY
        }.filterValues {
            it.size >= 2
        }

    val groupedEventIds = cpuGroups.values
        .flatten()
        .map { it.id }
        .toSet()

    val items = mutableListOf<EventListItem>()

    events
        .filterNot {
            it.id in groupedEventIds
        }.forEach { event ->
            items.add(SingleEventListItem(event = event))
        }

    cpuGroups.forEach { (groupKey, groupedEvents) ->
        val durations = groupedEvents.mapNotNull { it.awakeMs }

        items.add(
            GroupedCpuEventListItem(
                source = groupedEvents
                    .first()
                    .primarySource()
                    ?.let(labels::label),
                groupKey = groupKey,
                events = groupedEvents.sortedByDescending {
                    it.occurredAt
                },
                totalDurationMillis = durations
                    .takeIf {
                        it.isNotEmpty()
                    }?.sum(),
                longestDurationMillis = durations.maxOrNull(),
            ),
        )
    }

    return items.sortedByDescending { it.newestTimestamp }
}

private const val UNATTRIBUTED_KEY = "unattributed"
