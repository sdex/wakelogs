package de.sanniki.wakesleuth.data

import de.sanniki.wakesleuth.BackgroundJobDiagnostic
import de.sanniki.wakesleuth.WakeLockDiagnostic
import de.sanniki.wakesleuth.WakeReasonDiagnostic
import de.sanniki.wakesleuth.WakeupAlarmDiagnostic
import de.sanniki.wakesleuth.domain.CauseAssessment
import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.EvidenceOrigin
import de.sanniki.wakesleuth.domain.EvidenceType
import de.sanniki.wakesleuth.domain.HintRelation
import de.sanniki.wakesleuth.domain.MonitorStartEvent
import de.sanniki.wakesleuth.domain.NotificationCauseKind
import de.sanniki.wakesleuth.domain.NotificationEvent
import de.sanniki.wakesleuth.domain.PowerKeySignal
import de.sanniki.wakesleuth.domain.ProximityState
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.WakeReason
import de.sanniki.wakesleuth.domain.WakeReasonEvidence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRecorderTest : DatabaseTest() {

    private val t = START + 60_000

    @Test
    fun `nothing is recorded without an open session`() = runBlocking {
        assertNull(recorder.recordScreenOn(t, null))
        assertNull(recorder.recordNotification(t, "com.chat", "k", "Hi", null))
    }

    @Test
    fun `start creates the session once and logs MONITOR_START`() = runBlocking {
        val first = startSession()
        val second = startSession(START + 5_000)

        assertEquals(first, second)

        val start = single<MonitorStartEvent>(first)
        assertEquals(START, start.occurredAt)
        assertEquals(ProximityState.FAR, start.proximity?.state)
    }

    @Test
    fun `screen-on takes a notification of the last ten seconds as cause`() = runBlocking {
        val session = startSession()

        recorder.recordNotification(t - 2_000, "com.chat", "k1", "Hi", "text")
        val screenOnId = recorder.recordScreenOn(t, null)!!

        val screenOn = single<ScreenOnEvent>(session)
        val cause = screenOn.notificationCause!!
        assertEquals(screenOnId, screenOn.id)
        assertEquals(-2_000L, cause.offsetMs)
        assertEquals("Hi", cause.notificationTitle)
        assertEquals(NotificationCauseKind.PROBABLE, CauseAssessment.kindOf(cause))
    }

    @Test
    fun `old notifications are not a cause`() = runBlocking {
        val session = startSession()

        recorder.recordNotification(t - 11_000, "com.chat", "k1", "Hi", null)
        recorder.recordScreenOn(t, null)

        assertNull(single<ScreenOnEvent>(session).notificationCause)
    }

    @Test
    fun `late notification explains an unexplained screen-on`() = runBlocking {
        val session = startSession()

        recorder.recordScreenOn(t, null)
        recorder.recordNotification(t + 1_500, "com.chat", "k1", "Hi", null)

        val cause = single<ScreenOnEvent>(session).notificationCause!!
        assertEquals(1_500L, cause.offsetMs)
        assertEquals(NotificationCauseKind.LATER_DETECTED, CauseAssessment.kindOf(cause))
    }

    @Test
    fun `late notification does not replace a direct wake reason`() = runBlocking {
        val session = startSession()

        recorder.recordScreenOn(t, null)
        recorder.attachWakeReason(t, wakeReason(t - 30))
        recorder.recordNotification(t + 1_500, "com.chat", "k1", "Hi", null)

        assertNull(single<ScreenOnEvent>(session).notificationCause)
    }

    @Test
    fun `reposted notifications are dropped`() = runBlocking {
        val session = startSession()

        assertNotNull(recorder.recordNotification(t, "com.chat", "k1", "Hi", "text"))
        assertNull(recorder.recordNotification(t + 2_000, "com.chat", "k1", "Hi", "text"))
        assertNotNull(recorder.recordNotification(t + 6_000, "com.chat", "k1", "Hi", "text"))
        assertNotNull(recorder.recordNotification(t + 6_500, "com.chat", "k1", "Other", "text"))

        assertEquals(3, timeline(session).count { it is NotificationEvent })
    }

    @Test
    fun `wake reason attaches once inside its window`() = runBlocking {
        val session = startSession()
        recorder.recordScreenOn(t, null)

        assertFalse(recorder.attachWakeReason(t, wakeReason(t - 2_500)))
        assertTrue(recorder.attachWakeReason(t, wakeReason(t - 40)))
        assertFalse(recorder.attachWakeReason(t, wakeReason(t - 20)))

        val reason = single<ScreenOnEvent>(session).wakeReason!!
        assertEquals(WakeReason.POWER_BUTTON, reason.reason)
        assertEquals(WakeReasonEvidence.POWER_MANAGER_LOG, reason.evidence)
        assertEquals(-40L, reason.offsetMs)
        assertEquals("WAKE_REASON_POWER_BUTTON", reason.rawReason)
    }

    @Test
    fun `BatteryStats power key keeps its real offset`() = runBlocking {
        val session = startSession()
        recorder.recordScreenOn(t, null)

        assertTrue(
            recorder.attachBatteryStatsPowerKey(
                powerKeyAt = t - 350,
                signal = PowerKeySignal.PMIC_PWRKEY,
                rawReason = "123:\"pmic_pwrkey\"",
                rawTag = null
            )
        )

        val reason = single<ScreenOnEvent>(session).wakeReason!!
        assertEquals(-350L, reason.offsetMs)
        assertEquals(WakeReasonEvidence.BATTERYSTATS_POWER_KEY, reason.evidence)
        assertEquals(PowerKeySignal.PMIC_PWRKEY, reason.powerKeySignal)
    }

    @Test
    fun `wakelock hints are deduplicated and relabelled by a later wake reason`() = runBlocking {
        val session = startSession()
        recorder.recordScreenOn(t, null)

        assertTrue(recorder.attachWakeLockHint(t, wakeLock(t - 900, "GCM_CONN")))
        assertFalse(recorder.attachWakeLockHint(t, wakeLock(t - 800, "GCM_CONN")))

        var screenOn = single<ScreenOnEvent>(session)
        assertEquals(1, screenOn.wakeLockHints.size)
        assertEquals(HintRelation.POSSIBLE_TRIGGER, CauseAssessment.relationOf(screenOn, screenOn.wakeLockHints.single()))

        recorder.attachBatteryStatsPowerKey(t, PowerKeySignal.POLICY_POWER, null, null)

        screenOn = single<ScreenOnEvent>(session)
        assertEquals(HintRelation.COMPANION, CauseAssessment.relationOf(screenOn, screenOn.wakeLockHints.single()))
    }

    @Test
    fun `power key wakelock becomes the direct wake reason`() = runBlocking {
        val session = startSession()
        recorder.recordScreenOn(t, null)

        assertTrue(recorder.attachWakeLockHint(t, wakeLock(t + 10, "PhoneWindowManager.mPowerKeyWakeLock")))

        val screenOn = single<ScreenOnEvent>(session)
        assertEquals(WakeReasonEvidence.POWER_KEY_WAKELOCK, screenOn.wakeReason?.evidence)
        assertTrue(screenOn.wakeLockHints.isEmpty())
    }

    @Test
    fun `alarm and job hints respect their windows`() = runBlocking {
        val session = startSession()
        recorder.recordScreenOn(t, null)

        assertFalse(recorder.attachWakeupAlarmHint(t, alarm(t + 1_000)))
        assertTrue(recorder.attachWakeupAlarmHint(t, alarm(t - 1_000)))
        assertFalse(recorder.attachWakeupAlarmHint(t, alarm(t - 500)))
        assertFalse(recorder.attachBackgroundJobHint(t, job(t - 6_000)))
        assertTrue(recorder.attachBackgroundJobHint(t, job(t - 200)))

        val screenOn = single<ScreenOnEvent>(session)
        assertEquals("com.clock.ALARM", screenOn.alarmHints.single().tag)
        assertEquals(-1_000L, screenOn.alarmHints.single().offsetMs)
        assertEquals(-200L, screenOn.jobHints.single().offsetMs)
    }

    @Test
    fun `cpu wakeups keep evidence, primary source and exact awake time`() = runBlocking {
        val session = startSession()

        val candidate =
            CpuWakeupCandidate(
                occurredAt = t,
                rawWakeReason = "1:\"qcom_rx_wakelock\"",
                runningObserved = true,
                returnedToSleepAt = t + 90_500,
                evidence = listOf(
                    EvidenceCandidate(EvidenceOrigin.WAKELOCK, EvidenceType.PARTIAL_WAKELOCK, "RILJ_ACK_WL"),
                    EvidenceCandidate(EvidenceOrigin.JOB, EvidenceType.WORKMANAGER, "com.mail/androidx.work.impl.X"),
                    EvidenceCandidate(EvidenceOrigin.JOB, EvidenceType.WORKMANAGER, "com.mail/androidx.work.impl.X")
                )
            )

        assertNotNull(recorder.recordCpuWakeup(candidate))
        assertNull(recorder.recordCpuWakeup(candidate.copy(occurredAt = t + 300)))
        assertNotNull(recorder.recordCpuWakeup(candidate.copy(occurredAt = t + 300, rawWakeReason = null)))

        val wakeup = timeline(session).filterIsInstance<CpuWakeupEvent>().first()
        assertEquals(90_500L, wakeup.awakeMs)
        assertEquals(2, wakeup.evidence.size)
        assertEquals("com.mail", wakeup.primaryEvidence?.packageName)
        assertEquals(EvidenceType.WORKMANAGER, wakeup.primaryEvidence?.type)
    }

    @Test
    fun `clearing events keeps the session`() = runBlocking {
        val session = startSession()
        recorder.recordScreenOn(t, null)

        events.clearEvents()

        assertTrue(timeline(session).isEmpty())
        assertNotNull(database.sessionDao().byId(session))
    }

    private fun wakeReason(at: Long) =
        WakeReasonDiagnostic(
            reason = "WAKE_REASON_POWER_BUTTON",
            details = "android.policy:POWER",
            timestamp = null,
            timestampMillis = at,
            rawEntry = "raw"
        )

    private fun wakeLock(at: Long, tag: String) =
        WakeLockDiagnostic(
            activeCount = 1,
            lastTimestamp = null,
            lastPackage = "com.chat",
            lastTag = tag,
            rawLastEntry = "raw",
            lastTimestampMillis = at
        )

    private fun alarm(at: Long) =
        WakeupAlarmDiagnostic(
            packageName = "com.clock",
            tag = "*walarm*:com.clock.ALARM",
            ageMillis = null,
            wakeCount = 4,
            packageWakeups = 9,
            triggerTimestampMillis = at
        )

    private fun job(at: Long) =
        BackgroundJobDiagnostic(
            packageName = "com.sync",
            serviceName = "com.sync/.SyncJob",
            ageMillis = null,
            prioritized = true,
            rawEntry = "raw",
            triggerTimestampMillis = at
        )
}
