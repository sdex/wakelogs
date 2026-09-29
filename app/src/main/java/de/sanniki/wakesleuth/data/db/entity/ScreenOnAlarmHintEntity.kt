package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "screen_on_alarm_hint",
    indices = [Index("event_id", "tag", unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScreenOnAlarmHintEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    /** Approximate: derived from the alarm's age at read time. */
    @ColumnInfo(name = "offset_ms")
    val offsetMs: Long,
    @ColumnInfo(name = "package_name")
    val packageName: String?,
    /** Alarm tag with the `*walarm*:` prefix removed. */
    val tag: String,
    @ColumnInfo(name = "alarm_wake_count")
    val alarmWakeCount: Int?,
    @ColumnInfo(name = "package_wakeups")
    val packageWakeups: Int?
)
