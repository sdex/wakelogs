package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import de.sanniki.wakesleuth.domain.ExpertSignal

@Entity(
    tableName = "expert_snapshot_signal",
    primaryKeys = ["event_id", "signal"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExpertSnapshotSignalEntity(
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    val signal: ExpertSignal
)
