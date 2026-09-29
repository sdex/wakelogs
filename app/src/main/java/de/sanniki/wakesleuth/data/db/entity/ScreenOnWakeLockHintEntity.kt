package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "screen_on_wakelock_hint",
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
data class ScreenOnWakeLockHintEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "offset_ms")
    val offsetMs: Long,
    val tag: String,
    @ColumnInfo(name = "package_name")
    val packageName: String?,
    val uid: Int? = null
)
