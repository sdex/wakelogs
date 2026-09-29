package de.sanniki.wakesleuth

import de.sanniki.wakesleuth.BackgroundWakeMonitor.CLOCK_SLACK_MILLIS
import de.sanniki.wakesleuth.BackgroundWakeMonitor.MAX_DEFER_MILLIS
import de.sanniki.wakesleuth.BackgroundWakeMonitor.WakeCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BackgroundWakeMonitorLogicTest {
    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Long =
        Calendar
            .getInstance()
            .apply {
                clear()
                set(year, month - 1, day, hour, minute, 0)
            }.timeInMillis

    private fun candidate(
        at: Long,
        sleepAt: Long? = null,
    ) = WakeCandidate(
        timestampMillis = at,
        wakeReason = null,
        runningObserved = true,
        lines = mutableListOf(),
        cpuSleepTimestampMillis = sleepAt,
    )

    @Test
    fun `timestamp uses the current year`() {
        val now = millis(2027, 6, 15, 12)

        assertEquals(millis(2027, 6, 15, 11, 30) + 123, BackgroundWakeMonitor.parseTimestamp("06-15 11:30:00.123", now))
    }

    @Test
    fun `december line in early january belongs to the previous year`() {
        val now = millis(2027, 1, 2, 10)

        assertEquals(millis(2026, 12, 31, 23, 59), BackgroundWakeMonitor.parseTimestamp("12-31 23:59:00.000", now))
    }

    @Test
    fun `leap day falls back to the previous leap year`() {
        val now = millis(2029, 3, 1)

        assertEquals(millis(2028, 2, 29, 8), BackgroundWakeMonitor.parseTimestamp("02-29 08:00:00.000", now))
    }

    @Test
    fun `invalid timestamp is rejected`() {
        assertNull(BackgroundWakeMonitor.parseTimestamp("13-45 99:00:00.000", millis(2027, 1, 2)))
    }

    @Test
    fun `safe cutoff never exceeds the present`() {
        val now = millis(2027, 1, 2, 10)
        val future = now + 200L * 24 * 60 * 60 * 1_000

        assertEquals(now + CLOCK_SLACK_MILLIS - 6_000L, BackgroundWakeMonitor.safeCutoff(future, now))
        assertEquals(now - 6_000L, BackgroundWakeMonitor.safeCutoff(now, now))
    }

    @Test
    fun `persisted timestamp in the future is healed`() {
        val now = millis(2027, 1, 2, 10)
        val cutoff = now - 6_000L

        assertEquals(cutoff, BackgroundWakeMonitor.healLastTimestamp(now + 10L * 24 * 60 * 60 * 1_000, cutoff, now))
        assertEquals(now - 60_000L, BackgroundWakeMonitor.healLastTimestamp(now - 60_000L, cutoff, now))
    }

    @Test
    fun `candidates before the session start are dropped`() {
        val now = 1_000_000L
        val batch = BackgroundWakeMonitor.batchCandidates(
            candidates = listOf(candidate(100_000, 101_000), candidate(500_000, 501_000)),
            sessionStartedAt = 400_000,
            now = now,
        )

        assertEquals(listOf(500_000L), batch.ready.map { it.timestampMillis })
        assertNull(batch.deferredFrom)
    }

    @Test
    fun `unresolved candidate defers itself and everything after it`() {
        val now = 1_000_000L
        val batch = BackgroundWakeMonitor.batchCandidates(
            candidates = listOf(candidate(900_000, 901_000), candidate(950_000), candidate(960_000, 961_000)),
            sessionStartedAt = 0,
            now = now,
        )

        assertEquals(listOf(900_000L), batch.ready.map { it.timestampMillis })
        assertEquals(950_000L, batch.deferredFrom)
    }

    @Test
    fun `candidate that never got a sleep line is eventually stored`() {
        val at = 100_000L
        val now = at + MAX_DEFER_MILLIS + 1

        val batch = BackgroundWakeMonitor.batchCandidates(listOf(candidate(at)), sessionStartedAt = 0, now = now)

        assertEquals(1, batch.ready.size)
        assertTrue(batch.deferredFrom == null)
    }
}
