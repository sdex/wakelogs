package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import de.sanniki.wakesleuth.DeviceFamily
import de.sanniki.wakesleuth.domain.SessionEndReason

/**
 * One monitoring run. A session is running while [stopRequestedAt] is
 * null and still accepts events until [finalizedAt] is set.
 */
@Entity(
    tableName = "monitoring_session",
    indices = [Index("started_at")]
)
data class MonitoringSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "stop_requested_at")
    val stopRequestedAt: Long? = null,
    @ColumnInfo(name = "finalized_at")
    val finalizedAt: Long? = null,
    @ColumnInfo(name = "end_reason")
    val endReason: SessionEndReason? = null,
    @ColumnInfo(name = "final_poll_completed")
    val finalPollCompleted: Boolean? = null,
    @ColumnInfo(name = "device_family")
    val deviceFamily: DeviceFamily,
    @ColumnInfo(name = "network_baseline_captured_at")
    val networkBaselineCapturedAt: Long? = null,
    @ColumnInfo(name = "display_wakeups")
    val displayWakeups: Int? = null,
    @ColumnInfo(name = "cpu_wakeups")
    val cpuWakeups: Int? = null,
    val note: String? = null
)
