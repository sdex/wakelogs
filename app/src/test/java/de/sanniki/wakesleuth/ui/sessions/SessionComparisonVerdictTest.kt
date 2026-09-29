package de.sanniki.wakesleuth.ui.sessions

import de.sanniki.wakesleuth.ArchivedSession
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionComparisonVerdictTest {
    private fun session(
        durationMillis: Long,
        display: Int,
        cpu: Int,
    ) = ArchivedSession(
        id = 1,
        startMillis = 0,
        endMillis = durationMillis,
        durationMillis = durationMillis,
        endReason = null,
        displayWakeups = display,
        cpuWakeups = cpu,
        networkTotalBytes = 0,
        networkRxBytes = 0,
        networkTxBytes = 0,
        networkActiveApps = 0,
        topApps = emptyList(),
    )

    private val hour = 3_600_000L

    @Test
    fun `rates are compared for sessions of a sensible length`() {
        assertEquals(
            SessionComparisonVerdict.MUCH_CALMER,
            classifySessionComparison(session(hour, 2, 2), session(hour, 5, 5)),
        )
        assertEquals(
            SessionComparisonVerdict.MORE_INTERRUPTIONS,
            classifySessionComparison(session(hour, 10, 0), session(hour, 5, 0)),
        )
    }

    @Test
    fun `very short sessions fall back to the deltas`() {
        // One second: the naive rate would be 3600 wakeups per hour.
        assertEquals(
            SessionComparisonVerdict.SLIGHTLY_MORE_ACTIVITY,
            classifySessionComparison(session(1_000, 1, 0), session(hour, 0, 0)),
        )
        assertEquals(
            SessionComparisonVerdict.SLIGHTLY_CALMER,
            classifySessionComparison(session(hour, 0, 1), session(1_000, 3, 0)),
        )
        assertEquals(
            SessionComparisonVerdict.SIMILAR,
            classifySessionComparison(session(30_000, 2, 1), session(30_000, 2, 1)),
        )
    }

    @Test
    fun `no wakeups at all is reported as such`() {
        assertEquals(
            SessionComparisonVerdict.NO_WAKEUPS,
            classifySessionComparison(session(hour, 0, 0), session(1_000, 0, 0)),
        )
    }
}
