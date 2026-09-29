package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey

/** Traffic delta of one UID over a session; every UID with traffic. */
@Entity(
    tableName = "network_app_usage",
    primaryKeys = ["session_id", "uid"],
    foreignKeys = [
        ForeignKey(
            entity = NetworkMeasurementEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NetworkAppUsageEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    val uid: Int,
    @ColumnInfo(name = "package_name")
    val packageName: String?,
    @ColumnInfo(name = "rx_bytes")
    val rxBytes: Long,
    @ColumnInfo(name = "tx_bytes")
    val txBytes: Long,
    @ColumnInfo(name = "rx_packets")
    val rxPackets: Long?,
    @ColumnInfo(name = "tx_packets")
    val txPackets: Long?
)
