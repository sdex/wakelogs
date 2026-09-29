package de.sanniki.wakesleuth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationsTest {

    @Test
    fun `kernel wake reasons are bucketed`() {
        assertEquals(WakeReasonCategory.FAILED_SUSPEND, WakeReasonCategory.of("Abort: Pending Wakeup Sources: failed to suspend"))
        assertEquals(WakeReasonCategory.SCHEDULED_SYSTEM_ALARM, WakeReasonCategory.of("123:\"pm8xxx_rtc_alarm\""))
        assertEquals(WakeReasonCategory.QUALCOMM_RADIO, WakeReasonCategory.of("200:\"qcom_rx_wakelock\""))
        assertEquals(WakeReasonCategory.TIMER_SCHEDULER, WakeReasonCategory.of("0:\"timerfd\""))
        assertEquals(WakeReasonCategory.KERNEL_HARDWARE_INTERRUPT, WakeReasonCategory.of("NO_SUSPEND IRQ 55"))
        assertEquals(WakeReasonCategory.KERNEL_SYSTEM_SIGNAL, WakeReasonCategory.of("NO_SUSPEND"))
        assertEquals(WakeReasonCategory.UNSPECIFIED, WakeReasonCategory.of("1:\"\""))
        assertEquals(WakeReasonCategory.OTHER, WakeReasonCategory.of("7:\"gpio_keys\""))
        assertEquals("gpio_keys", WakeReasonCategory.reasonText("7:\"gpio_keys\""))
    }

    @Test
    fun `power manager reasons`() {
        assertEquals(WakeReason.POWER_BUTTON, WakeReasons.fromPowerManager("WAKE_REASON_POWER_BUTTON", null))
        assertEquals(WakeReason.DOUBLE_TAP, WakeReasons.fromPowerManager("WAKE_REASON_GESTURE", "blackGestureWake"))
        assertEquals(WakeReason.GESTURE, WakeReasons.fromPowerManager("WAKE_REASON_GESTURE", "x"))
        assertEquals(WakeReason.OTHER, WakeReasons.fromPowerManager("WAKE_REASON_UNKNOWN", null))
    }

    @Test
    fun `wakelock tags`() {
        assertEquals(WakeLockKind.USER_PRESENCE, WakeLockTags.kindOf("*UserPresent*"))
        assertEquals(WakeLockKind.BACKGROUND_JOB, WakeLockTags.kindOf("*job*/com.foo/.Job"))
        assertEquals(WakeLockKind.PARTIAL_WAKELOCK, WakeLockTags.kindOf("SomethingElse"))
        assertTrue(WakeLockTags.isPowerKey("PhoneWindowManager.mPowerKeyWakeLock"))
        assertTrue(WakeLockTags.isKnownFollowUp("NotificationManagerService:post:com.x"))
        assertFalse(WakeLockTags.isKnownFollowUp("GCM_CONN"))
    }

    @Test
    fun `primary evidence follows the type priority`() {
        val types =
            listOf(
                EvidenceType.PARTIAL_WAKELOCK,
                EvidenceType.WAKEUP_ALARM,
                EvidenceType.WORKMANAGER,
                EvidenceType.SYNC,
                EvidenceType.SYNC
            )

        assertEquals(3, CpuEvidenceRules.primaryIndex(types))
        assertEquals(null, CpuEvidenceRules.primaryIndex(emptyList()))
        assertEquals(EvidenceType.WAKEUP_ALARM, CpuEvidenceRules.typeOfWakeLock("*alarm*:com.foo"))
        assertEquals(EvidenceType.JOB_WAKELOCK, CpuEvidenceRules.typeOfWakeLock("*job*/com.foo"))
        assertEquals(EvidenceType.WORKMANAGER, CpuEvidenceRules.typeOfJob("com.foo/androidx.work.impl.X"))
        assertEquals(EvidenceType.JOBSCHEDULER, CpuEvidenceRules.typeOfJob("com.foo/.MyJob"))
    }

    @Test
    fun `snapshot classification`() {
        fun snapshot(
            wakefulness: String? = null,
            interactive: Boolean? = null,
            deep: String? = null,
            light: String? = null
        ) = SystemSnapshot(
            trigger = SnapshotTrigger.AFTER_SCREEN_OFF,
            status = SnapshotStatus.OK,
            wakefulness = wakefulness,
            interactive = interactive,
            deepIdleState = deep,
            lightIdleState = light
        )

        assertEquals(SnapshotClassification.ACTIVE, SnapshotClassification.of(snapshot(interactive = true)))
        assertEquals(SnapshotClassification.DEEP_IDLE, SnapshotClassification.of(snapshot("Asleep", deep = "IDLE")))
        assertEquals(SnapshotClassification.LIGHT_IDLE, SnapshotClassification.of(snapshot("Asleep", light = "IDLE")))
        assertEquals(SnapshotClassification.VENDOR_SPECIFIC, SnapshotClassification.of(snapshot("Dozing")))
        assertEquals(SnapshotClassification.UNCLEAR, SnapshotClassification.of(snapshot()))
    }
}
