package de.sanniki.wakesleuth.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSourcesTest {

    private fun cpu(id: Long, packageName: String?, raw: String, awake: Long?) =
        CpuWakeupEvent(
            id = id,
            sessionId = 1,
            occurredAt = id * 1_000,
            rawWakeReason = null,
            runningObserved = true,
            returnedToSleepAt = null,
            awakeMs = awake,
            evidence = listOf(
                CpuEvidence(EvidenceOrigin.WAKELOCK, EvidenceType.PARTIAL_WAKELOCK, raw, packageName, isPrimary = true)
            )
        )

    @Test
    fun `aggregates per source identity`() {
        val screenOn =
            ScreenOnEvent(
                id = 10,
                sessionId = 1,
                occurredAt = 50_000,
                proximity = null,
                wakeReason = null,
                notificationCause = NotificationCause(9, "com.chat", -1_000, null),
                wakeLockHints = listOf(WakeLockHint(200, "tag", "com.chat", null)),
                alarmHints = emptyList(),
                jobHints = emptyList()
            )

        val unattributed =
            cpu(4, null, "x", 10).copy(evidence = emptyList())

        val stats =
            SessionSources.aggregate(
                listOf(
                    cpu(1, "com.chat", "com.chat/.Worker", 1_200),
                    cpu(2, "com.chat", "com.chat/.Other", 3_400),
                    cpu(3, "com.google.android.gms", "*alarm*:gms", null),
                    unattributed,
                    screenOn
                )
            )

        val chat = stats.single { it.source.packageName == "com.chat" }
        assertEquals(2, chat.cpuCount)
        assertEquals(1, chat.displayCount)
        // The companion wakelock and the notification cause are the same
        // source, so the screen-on counts once and marks it companion.
        assertEquals(1, chat.companionCount)
        assertEquals(3_400L, chat.longestCpuAwakeMs)

        val gms = stats.single { it.source.kind == SourceKind.GOOGLE_PLAY_SERVICES }
        assertEquals(1, gms.cpuCount)
        assertEquals(null, gms.source.packageName)
        assertEquals(2, stats.size)
    }
}
