package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import de.sanniki.wakesleuth.domain.EventType
import de.sanniki.wakesleuth.domain.ProximityState

/** Timeline base row. Type specific facts live in the payload tables. */
@Entity(
    tableName = "event",
    indices = [
        Index("session_id", "occurred_at"),
        Index("type", "occurred_at")
    ],
    foreignKeys = [
        ForeignKey(
            entity = MonitoringSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    val type: EventType,
    @ColumnInfo(name = "proximity_state")
    val proximityState: ProximityState? = null,
    @ColumnInfo(name = "proximity_distance_cm")
    val proximityDistanceCm: Float? = null
)
