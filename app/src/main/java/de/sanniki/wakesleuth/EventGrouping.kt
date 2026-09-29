package de.sanniki.wakesleuth

import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.primarySource
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver

sealed interface EventListItem {
    val stableKey: String
    val newestTimestamp: Long

    /** Tiebreaker for items with the same [newestTimestamp]. */
    val newestId: Long
}

/** Newest first; events with the same timestamp keep a stable order by id. */
internal val NEWEST_EVENT_FIRST: Comparator<RecordedEvent> =
    compareByDescending<RecordedEvent> { it.occurredAt }.thenByDescending { it.id }

private val NEWEST_ITEM_FIRST: Comparator<EventListItem> =
    compareByDescending<EventListItem> { it.newestTimestamp }.thenByDescending { it.newestId }

data class SingleEventListItem(
    val event: RecordedEvent,
) : EventListItem {
    override val stableKey: String = "event_${event.id}"

    override val newestTimestamp: Long = event.occurredAt

    override val newestId: Long = event.id
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

    override val newestId: Long = events.maxOf { it.id }

    /** Number of events that have a known duration; the divisor of [averageDurationMillis]. */
    val durationCount: Int = events.count { it.awakeMs != null }

    /** Average over the events with a known duration only, null when there is none. */
    val averageDurationMillis: Long? = totalDurationMillis
        ?.takeIf { durationCount > 0 }
        ?.div(durationCount.toLong())
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
                events = groupedEvents.sortedWith(NEWEST_EVENT_FIRST),
                totalDurationMillis = durations
                    .takeIf {
                        it.isNotEmpty()
                    }?.sum(),
                longestDurationMillis = durations.maxOrNull(),
            ),
        )
    }

    return items.sortedWith(NEWEST_ITEM_FIRST)
}

private const val UNATTRIBUTED_KEY = "unattributed"
