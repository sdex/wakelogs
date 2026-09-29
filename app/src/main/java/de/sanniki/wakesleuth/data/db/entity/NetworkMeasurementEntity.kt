package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import de.sanniki.wakesleuth.domain.DiagnosticError
import de.sanniki.wakesleuth.domain.NetworkMeasurementStatus

@Entity(
    tableName = "network_measurement",
    indices = [Index("event_id")],
    foreignKeys = [
        ForeignKey(
            entity = MonitoringSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class NetworkMeasurementEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "event_id")
    val eventId: Long?,
    val status: NetworkMeasurementStatus,
    @ColumnInfo(name = "measured_at")
    val measuredAt: Long?,
    @ColumnInfo(name = "error_code")
    val errorCode: DiagnosticError? = null,
    @ColumnInfo(name = "error_detail")
    val errorDetail: String? = null,
)
