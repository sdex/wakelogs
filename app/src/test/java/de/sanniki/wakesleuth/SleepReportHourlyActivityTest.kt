package de.sanniki.wakesleuth

import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity
import de.sanniki.wakesleuth.domain.NetworkSessionEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SleepReportHourlyActivityTest {
    private val originalZone = TimeZone.getDefault()

    @Before
    fun useUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private fun utc(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Long =
        Calendar
            .getInstance(TimeZone.getTimeZone("UTC"))
            .apply {
                clear()
                set(2024, Calendar.JUNE, day, hour, minute, 0)
            }.timeInMillis

    @Test
    fun `sessions longer than a day keep every bucket`() {
        val buckets = buildHourlyBuckets(
            startMillis = utc(1, 22),
            endMillis = utc(3, 4),
            displayTimes = emptyList(),
            cpuTimes = emptyList(),
        )

        assertEquals(30, buckets.size)
        assertEquals(22, buckets.first().hour)
        assertEquals(3, buckets.last().hour)
    }

    @Test
    fun `tail of a long session lands in the last twelve buckets`() {
        val lateEvent = utc(3, 3, 30)

        val buckets = buildHourlyBuckets(
            startMillis = utc(1, 22),
            endMillis = utc(3, 4),
            displayTimes = listOf(lateEvent),
            cpuTimes = emptyList(),
        )

        val visible = buckets.takeLast(12)

        assertEquals(1, visible.last().displayWakeups)
        assertEquals(3, visible.last().hour)
    }

    @Test
    fun `end exactly on an hour boundary adds no empty trailing bucket`() {
        val buckets = buildHourlyBuckets(
            startMillis = utc(1, 10, 15),
            endMillis = utc(1, 12),
            displayTimes = emptyList(),
            cpuTimes = emptyList(),
        )

        assertEquals(listOf(10, 11), buckets.map { it.hour })
    }

    @Test
    fun `events are counted per kind and hour`() {
        val buckets = buildHourlyBuckets(
            startMillis = utc(1, 10),
            endMillis = utc(1, 12),
            displayTimes = listOf(utc(1, 10, 5), utc(1, 11, 59)),
            cpuTimes = listOf(utc(1, 10, 6), utc(1, 10, 7)),
        )

        assertEquals(1, buckets[0].displayWakeups)
        assertEquals(2, buckets[0].cpuWakeups)
        assertEquals(1, buckets[1].displayWakeups)
        assertEquals(0, buckets[1].cpuWakeups)
    }

    @Test
    fun `empty window yields no buckets`() {
        assertTrue(buildHourlyBuckets(utc(1, 10), utc(1, 10), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `spring forward skips the missing local hour`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))

        val start = Calendar
            .getInstance()
            .apply {
                clear()
                set(2024, Calendar.MARCH, 31, 0, 30, 0)
            }.timeInMillis

        val end = Calendar
            .getInstance()
            .apply {
                clear()
                set(2024, Calendar.MARCH, 31, 6, 0, 0)
            }.timeInMillis

        val buckets = buildHourlyBuckets(start, end, emptyList(), emptyList())

        assertEquals(listOf(0, 1, 3, 4, 5), buckets.map { it.hour })
    }

    @Test
    fun `busiest hour ties go to the earliest bucket not the lowest clock hour`() {
        // Window crosses midnight: bucket order is 23, 0, 1.
        val activity = listOf(
            HourActivity(hour = 23, displayWakeups = 2, cpuWakeups = 0),
            HourActivity(hour = 0, displayWakeups = 2, cpuWakeups = 0),
            HourActivity(hour = 1, displayWakeups = 1, cpuWakeups = 0),
        )

        assertEquals(23, busiestHourActivity(activity)?.hour)
    }

    @Test
    fun `busiest hour is null without activity`() {
        assertNull(busiestHourActivity(listOf(HourActivity(1, 0, 0))))
        assertNull(busiestHourActivity(emptyList()))
    }

    @Test
    fun `network event is restricted to the analysed session`() {
        val own = NetworkSessionEvent(id = 1, sessionId = 7, occurredAt = 1_000L, measurement = null)
        val ownLate = NetworkSessionEvent(id = 2, sessionId = 7, occurredAt = 5_000L, measurement = null)
        val other = NetworkSessionEvent(id = 3, sessionId = 8, occurredAt = 9_000L, measurement = null)
        val beforeWindow = NetworkSessionEvent(id = 4, sessionId = 7, occurredAt = 10L, measurement = null)

        val picked = latestNetworkSessionEvent(
            events = listOf(own, ownLate, other, beforeWindow),
            sessionId = 7,
            windowStartMillis = 500L,
        )

        assertEquals(2L, picked?.id)
    }

    @Test
    fun `network event is null when nothing matches`() {
        val other = NetworkSessionEvent(id = 3, sessionId = 8, occurredAt = 9_000L, measurement = null)

        assertNull(latestNetworkSessionEvent(listOf(other), sessionId = 7, windowStartMillis = 0L))
    }

    @Test
    fun `session running state follows stop and finalize timestamps`() {
        val base = MonitoringSessionEntity(startedAt = 1L, deviceFamily = DeviceFamily.GENERIC_ANDROID)

        assertTrue(isSessionRunning(base))
        assertFalse(isSessionRunning(base.copy(stopRequestedAt = 2L)))
        assertFalse(isSessionRunning(base.copy(finalizedAt = 2L)))
        assertFalse(isSessionRunning(null))
    }
}
