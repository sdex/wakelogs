package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import de.sanniki.wakesleuth.domain.ExpertSnapshotStatus

@Entity(
    tableName = "expert_snapshot",
    indices = [Index("screen_on_event_id")],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["screen_on_event_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class ExpertSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "screen_on_event_id")
    val screenOnEventId: Long?,
    val status: ExpertSnapshotStatus,
    @ColumnInfo(name = "error_detail")
    val errorDetail: String? = null,
    /** False when the dumpsys call failed, which is not the same as no hits. */
    @ColumnInfo(name = "location_available")
    val locationAvailable: Boolean,
    @ColumnInfo(name = "sensors_available")
    val sensorsAvailable: Boolean,
    @ColumnInfo(name = "network_available")
    val networkAvailable: Boolean
)
