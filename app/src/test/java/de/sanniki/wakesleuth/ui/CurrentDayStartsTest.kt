package de.sanniki.wakesleuth.ui

import de.sanniki.wakesleuth.ui.main.currentDayStarts
import de.sanniki.wakesleuth.ui.main.startOfDayMillis
import de.sanniki.wakesleuth.ui.statistics.startOfNextDayMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentDayStartsTest {
    private fun millis(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Long =
        Calendar
            .getInstance()
            .apply {
                clear()
                set(2027, Calendar.MARCH, day, hour, minute, 0)
            }.timeInMillis

    @Test
    fun `start of day is midnight`() {
        assertEquals(millis(10, 0), startOfDayMillis(millis(10, 17, 45)))
    }

    @Test
    fun `flow emits again when the date changes`() =
        runTest {
            val base = millis(10, 23, 30)
            val emitted = mutableListOf<Long>()

            val job = launch { currentDayStarts(now = { base + testScheduler.currentTime }).collect { emitted += it } }

            runCurrent()
            assertEquals(listOf(millis(10, 0)), emitted)

            advanceTimeBy(29 * 60_000L)
            runCurrent()
            assertEquals(listOf(millis(10, 0)), emitted)

            advanceTimeBy(2 * 60_000L)
            runCurrent()
            assertEquals(listOf(millis(10, 0), millis(11, 0)), emitted)
            assertEquals(startOfNextDayMillis(millis(10, 0)), emitted.last())

            job.cancel()
        }
}
