package de.sanniki.wakesleuth.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOnQueryOrderTest : DatabaseTest() {
    @Test
    fun `screen-ons of a range come newest first`() =
        runBlocking {
            startSession()
            recorder.recordScreenOn(START + 3_000, null)
            recorder.recordScreenOn(START + 1_000, null)
            recorder.recordScreenOn(START + 2_000, null)
            recorder.recordScreenOn(START + 50_000, null)

            val times = database
                .eventDao()
                .observeScreenOnsBetween(START, START + 10_000)
                .first()
                .map { it.event.occurredAt }

            assertEquals(listOf(START + 3_000, START + 2_000, START + 1_000), times)
        }
}
