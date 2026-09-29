package de.sanniki.wakesleuth

import de.sanniki.wakesleuth.domain.NotificationCause
import de.sanniki.wakesleuth.domain.NotificationEvent
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.WakeLockHint
import de.sanniki.wakesleuth.ui.AnalysisWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SourceActivityCountTest {
    private val window = AnalysisWindow(startMillis = 0, endMillis = 100_000, ongoing = false)

    private fun screenOn(
        id: Long,
        cause: NotificationCause? = null,
        wakeLocks: List<WakeLockHint> = emptyList(),
    ) = ScreenOnEvent(
        id = id,
        sessionId = 1,
        occurredAt = 10_000,
        proximity = null,
        wakeReason = null,
        notificationCause = cause,
        wakeLockHints = wakeLocks,
        alarmHints = emptyList(),
        jobHints = emptyList(),
    )

    private fun notification(
        id: Long,
        pkg: String,
    ) = NotificationEvent(
        id = id,
        sessionId = 1,
        occurredAt = 9_500,
        packageName = pkg,
        notificationKey = null,
        title = null,
        text = null,
    )

    private fun count(
        events: List<RecordedEvent>,
        pkg: String,
    ) = countSourceActivity(events, window).firstOrNull { it.source.packageName == pkg }

    @Test
    fun `companion-only sources are not counted as display wakeups`() {
        val events = listOf(screenOn(1, wakeLocks = listOf(WakeLockHint(500, "sync", "com.sync", null))))

        assertNull(count(events, "com.sync"))
    }

    @Test
    fun `notification that caused the screen-on is counted once in the total`() {
        val events = listOf(
            notification(id = 5, pkg = "com.chat"),
            screenOn(1, cause = NotificationCause(5, "com.chat", -500, null)),
        )

        val entry = count(events, "com.chat")!!

        assertEquals(1, entry.display)
        assertEquals(1, entry.notifications)
        assertEquals(1, entry.total)
    }

    @Test
    fun `other notifications of the same package still add up`() {
        val events = listOf(
            notification(id = 5, pkg = "com.chat"),
            notification(id = 6, pkg = "com.chat"),
            screenOn(1, cause = NotificationCause(5, "com.chat", -500, null)),
        )

        val entry = count(events, "com.chat")!!

        assertEquals(2, entry.notifications)
        assertEquals(2, entry.total)
    }
}
