package de.sanniki.wakesleuth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CauseAssessmentTest {
    private fun screenOn(
        wakeReason: DirectWakeReason? = null,
        cause: NotificationCause? = null,
        wakeLocks: List<WakeLockHint> = emptyList(),
        alarms: List<AlarmHint> = emptyList(),
        jobs: List<JobHint> = emptyList(),
    ) = ScreenOnEvent(
        id = 1,
        sessionId = 1,
        occurredAt = 10_000,
        proximity = null,
        wakeReason = wakeReason,
        notificationCause = cause,
        wakeLockHints = wakeLocks,
        alarmHints = alarms,
        jobHints = jobs,
    )

    private val powerButton = DirectWakeReason(
        reason = WakeReason.POWER_BUTTON,
        evidence = WakeReasonEvidence.POWER_MANAGER_LOG,
        powerKeySignal = null,
        offsetMs = -40,
        rawReason = "WAKE_REASON_POWER_BUTTON",
        rawDetails = null,
        rawTag = null,
    )

    private fun cause(offset: Long) = NotificationCause(1, "com.chat", offset, null)

    private fun wakeLock(
        offset: Long,
        tag: String = "GCM",
    ) = WakeLockHint(offset, tag, "com.chat", null)

    @Test
    fun `nothing known is unresolved and unexplained`() {
        val event = screenOn()
        assertEquals(ScreenOnVerdict.UNRESOLVED, CauseAssessment.verdictOf(event))
        assertTrue(CauseAssessment.isUnexplained(event))
    }

    @Test
    fun `direct wake reason wins and turns hints into companions`() {
        val event = screenOn(wakeReason = powerButton, wakeLocks = listOf(wakeLock(-1_000)))
        assertEquals(ScreenOnVerdict.CONFIRMED, CauseAssessment.verdictOf(event))
        assertEquals(HintRelation.COMPANION, CauseAssessment.relationOf(event, event.wakeLockHints.single()))
        assertTrue(CauseAssessment.isPowerButton(event))
    }

    @Test
    fun `notification cause kinds and confidence`() {
        assertEquals(NotificationCauseKind.PROBABLE, CauseAssessment.kindOf(cause(-2_000)))
        assertEquals(NotificationCauseKind.POSSIBLE, CauseAssessment.kindOf(cause(-7_000)))
        assertEquals(NotificationCauseKind.LATER_DETECTED, CauseAssessment.kindOf(cause(1_500)))
        assertEquals(CauseConfidenceLevel.MEDIUM, CauseAssessment.confidenceOf(cause(1_500)))
        assertEquals(CauseConfidenceLevel.MEDIUM, CauseAssessment.confidenceOf(cause(4_500)))

        assertEquals(ScreenOnVerdict.PROBABLE_NOTIFICATION, CauseAssessment.verdictOf(screenOn(cause = cause(-500))))
        assertEquals(ScreenOnVerdict.POSSIBLE_NOTIFICATION, CauseAssessment.verdictOf(screenOn(cause = cause(-8_000))))
    }

    @Test
    fun `wakelocks before the screen-on are possible triggers`() {
        val before = screenOn(wakeLocks = listOf(wakeLock(-800)))
        assertEquals(HintRelation.POSSIBLE_TRIGGER, CauseAssessment.relationOf(before, before.wakeLockHints.single()))
        assertEquals(ScreenOnVerdict.POSSIBLE_WAKELOCK, CauseAssessment.verdictOf(before))
        assertFalse(CauseAssessment.isUnexplained(before))

        val after = screenOn(wakeLocks = listOf(wakeLock(300)))
        assertEquals(HintRelation.COMPANION, CauseAssessment.relationOf(after, after.wakeLockHints.single()))
        assertEquals(ScreenOnVerdict.COMPANION, CauseAssessment.verdictOf(after))
        assertTrue(CauseAssessment.isUnexplained(after))

        val followUp = screenOn(wakeLocks = listOf(wakeLock(-800, "*launch*")))
        assertEquals(HintRelation.COMPANION, CauseAssessment.relationOf(followUp, followUp.wakeLockHints.single()))
    }

    @Test
    fun `alarm and job relations`() {
        fun alarm(offset: Long) = AlarmHint(offset, "com.clock", "ALARM", 3, 5)

        val event = screenOn(alarms = listOf(alarm(-100), alarm(0), alarm(200)))
        assertEquals(
            listOf(HintRelation.POSSIBLE_TRIGGER, HintRelation.SIMULTANEOUS, HintRelation.CLOSE_RELATION),
            event.alarmHints.map { CauseAssessment.relationOf(event, it) },
        )
        assertEquals(ScreenOnVerdict.POSSIBLE_WAKEUP_ALARM, CauseAssessment.verdictOf(event))

        val job = screenOn(jobs = listOf(JobHint(-100, "com.foo", "com.foo/.Job", false)))
        assertEquals(HintRelation.TIME_RELATION, CauseAssessment.relationOf(job, job.jobHints.single()))
        assertEquals(ScreenOnVerdict.COMPANION, CauseAssessment.verdictOf(job))
        assertFalse(CauseAssessment.isUnexplained(job))
    }
}
