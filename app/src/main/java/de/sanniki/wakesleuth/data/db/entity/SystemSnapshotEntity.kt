package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import de.sanniki.wakesleuth.domain.SnapshotStatus
import de.sanniki.wakesleuth.domain.SnapshotTrigger

/** Compact `dumpsys power` / `dumpsys deviceidle` state; raw tokens verbatim. */
@Entity(
    tableName = "system_snapshot",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SystemSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    val trigger: SnapshotTrigger,
    val status: SnapshotStatus,
    @ColumnInfo(name = "error_detail")
    val errorDetail: String? = null,
    val wakefulness: String? = null,
    val interactive: Boolean? = null,
    @ColumnInfo(name = "low_power_mode")
    val lowPowerMode: Boolean? = null,
    @ColumnInfo(name = "device_idle_mode")
    val deviceIdleMode: Boolean? = null,
    @ColumnInfo(name = "light_device_idle_mode")
    val lightDeviceIdleMode: Boolean? = null,
    @ColumnInfo(name = "deep_idle_state")
    val deepIdleState: String? = null,
    @ColumnInfo(name = "light_idle_state")
    val lightIdleState: String? = null,
    @ColumnInfo(name = "idle_screen_on")
    val idleScreenOn: Boolean? = null,
    @ColumnInfo(name = "idle_charging")
    val idleCharging: Boolean? = null,
    @ColumnInfo(name = "force_idle")
    val forceIdle: String? = null,
)
