package de.sanniki.wakesleuth.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotIdleStateTest {
    private fun snapshot(
        deep: String? = null,
        light: String? = null,
    ) = SystemSnapshot(
        trigger = SnapshotTrigger.AFTER_SCREEN_OFF,
        status = SnapshotStatus.OK,
        wakefulness = "Asleep",
        deepIdleState = deep,
        lightIdleState = light,
    )

    @Test
    fun `transitional doze states are not idle`() {
        assertEquals(SnapshotClassification.VENDOR_SPECIFIC, SnapshotClassification.of(snapshot(deep = "IDLE_PENDING")))
        assertEquals(SnapshotClassification.VENDOR_SPECIFIC, SnapshotClassification.of(snapshot(light = "PRE_IDLE")))
        assertEquals(SnapshotClassification.VENDOR_SPECIFIC, SnapshotClassification.of(snapshot(deep = "INACTIVE")))
    }

    @Test
    fun `idle states match exactly and ignore case`() {
        assertEquals(SnapshotClassification.DEEP_IDLE, SnapshotClassification.of(snapshot(deep = "idle")))
        assertEquals(SnapshotClassification.DEEP_IDLE, SnapshotClassification.of(snapshot(deep = "IDLE_MAINTENANCE")))
        assertEquals(SnapshotClassification.LIGHT_IDLE, SnapshotClassification.of(snapshot(light = " IDLE ")))
    }
}
