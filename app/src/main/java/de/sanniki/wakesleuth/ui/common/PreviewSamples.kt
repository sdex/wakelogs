package de.sanniki.wakesleuth.ui.common

import de.sanniki.wakesleuth.ArchivedSession
import de.sanniki.wakesleuth.ArchivedSessionApp
import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.NotificationEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.SessionEndReason
import de.sanniki.wakesleuth.domain.SourceKind
import de.sanniki.wakesleuth.domain.SourceRef

/** Fixed sample data for `@Preview` functions. */
internal object PreviewSamples {
    private const val BASE_TIME = 1_735_689_600_000L

    private const val HOUR = 3_600_000L

    val screenOnEvent = ScreenOnEvent(
        id = 1,
        sessionId = 1,
        occurredAt = BASE_TIME,
        proximity = null,
        wakeReason = null,
        notificationCause = null,
        wakeLockHints = emptyList(),
        alarmHints = emptyList(),
        jobHints = emptyList(),
    )

    val notificationEvent = NotificationEvent(
        id = 2,
        sessionId = 1,
        occurredAt = BASE_TIME + 5_000L,
        packageName = "com.example.messenger",
        notificationKey = null,
        title = "New message",
        text = "Are you awake?",
    )

    val cpuWakeupEvents = List(3) { index ->
        CpuWakeupEvent(
            id = 10L + index,
            sessionId = 1,
            occurredAt = BASE_TIME + index * 120_000L,
            rawWakeReason = null,
            runningObserved = true,
            returnedToSleepAt = BASE_TIME + index * 120_000L + 4_000L,
            awakeMs = 4_000L,
            evidence = emptyList(),
        )
    }

    val archivedSessions = listOf(
        archivedSession(id = 2, startMillis = BASE_TIME + 24 * HOUR, displayWakeups = 4, cpuWakeups = 31),
        archivedSession(id = 1, startMillis = BASE_TIME, displayWakeups = 9, cpuWakeups = 58),
    )

    private fun archivedSession(
        id: Long,
        startMillis: Long,
        displayWakeups: Int,
        cpuWakeups: Int,
    ) = ArchivedSession(
        id = id,
        startMillis = startMillis,
        endMillis = startMillis + 8 * HOUR,
        durationMillis = 8 * HOUR,
        endReason = SessionEndReason.USER_STOP,
        displayWakeups = displayWakeups,
        cpuWakeups = cpuWakeups,
        networkTotalBytes = 48_000_000L,
        networkRxBytes = 40_000_000L,
        networkTxBytes = 8_000_000L,
        networkActiveApps = 1,
        topApps = listOf(
            ArchivedSessionApp(
                source = SourceRef(kind = SourceKind.APP, packageName = "com.example.messenger"),
                name = "Messenger",
                totalBytes = 48_000_000L,
                rxBytes = 40_000_000L,
                txBytes = 8_000_000L,
            ),
        ),
        note = null,
    )
}
