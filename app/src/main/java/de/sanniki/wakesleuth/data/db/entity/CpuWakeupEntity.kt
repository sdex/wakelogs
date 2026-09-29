package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey

@Entity(
    tableName = "cpu_wakeup",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CpuWakeupEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "raw_wake_reason")
    val rawWakeReason: String?,
    @ColumnInfo(name = "running_observed")
    val runningObserved: Boolean,
    @ColumnInfo(name = "returned_to_sleep_at")
    val returnedToSleepAt: Long?,
    @ColumnInfo(name = "awake_ms")
    val awakeMs: Long?
)
