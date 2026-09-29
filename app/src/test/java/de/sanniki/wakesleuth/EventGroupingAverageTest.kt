package de.sanniki.wakesleuth

import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.MonitorStopEvent
import de.sanniki.wakesleuth.domain.RecordedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventGroupingAverageTest {
    private fun cpu(
        id: Long,
        at: Long = 1_000,
        awakeMs: Long?,
    ) = CpuWakeupEvent(
        id = id,
        sessionId = 1,
        occurredAt = at,
        rawWakeReason = null,
        runningObserved = false,
        returnedToSleepAt = null,
        awakeMs = awakeMs,
        evidence = emptyList(),
    )

    private fun group(vararg events: CpuWakeupEvent): GroupedCpuEventListItem {
        val durations = events.mapNotNull { it.awakeMs }

        return GroupedCpuEventListItem(
            source = "com.example",
            groupKey = "app:com.example",
            events = events.toList(),
            totalDurationMillis = durations.takeIf { it.isNotEmpty() }?.sum(),
            longestDurationMillis = durations.maxOrNull(),
        )
    }

    @Test
    fun `average divides by events that have a duration`() {
        val item = group(cpu(1, awakeMs = 4_000), cpu(2, awakeMs = null), cpu(3, awakeMs = 2_000))

        assertEquals(2, item.durationCount)
        assertEquals(3_000L, item.averageDurationMillis)
    }

    @Test
    fun `average is unknown when no event has a duration`() {
        val item = group(cpu(1, awakeMs = null), cpu(2, awakeMs = null))

        assertEquals(0, item.durationCount)
        assertNull(item.averageDurationMillis)
    }

    @Test
    fun `events with the same timestamp are ordered by descending id`() {
        val events: List<RecordedEvent> = listOf(
            cpu(1, at = 5_000, awakeMs = 1),
            MonitorStopEvent(id = 3, sessionId = 1, occurredAt = 5_000, finalPollCompleted = null),
            cpu(2, at = 9_000, awakeMs = 1),
            cpu(4, at = 5_000, awakeMs = 1),
        )

        assertEquals(listOf(2L, 4L, 3L, 1L), events.sortedWith(NEWEST_EVENT_FIRST).map { it.id })
    }
}
