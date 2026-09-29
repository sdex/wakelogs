package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "screen_on_job_hint",
    indices = [Index("event_id", "service_name", unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScreenOnJobHintEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "offset_ms")
    val offsetMs: Long,
    @ColumnInfo(name = "package_name")
    val packageName: String?,
    @ColumnInfo(name = "service_name")
    val serviceName: String,
    val prioritized: Boolean
)
