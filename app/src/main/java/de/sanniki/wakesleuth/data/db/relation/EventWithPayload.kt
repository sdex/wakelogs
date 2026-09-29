package de.sanniki.wakesleuth.data.db.relation

import androidx.room3.Embedded
import androidx.room3.Relation
import de.sanniki.wakesleuth.data.db.entity.CpuWakeupEntity
import de.sanniki.wakesleuth.data.db.entity.CpuWakeupEvidenceEntity
import de.sanniki.wakesleuth.data.db.entity.EventEntity
import de.sanniki.wakesleuth.data.db.entity.ExpertSnapshotEntity
import de.sanniki.wakesleuth.data.db.entity.ExpertSnapshotSignalEntity
import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkAppUsageEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkMeasurementEntity
import de.sanniki.wakesleuth.data.db.entity.NotificationEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnAlarmHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnJobHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnNotificationCauseEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnWakeLockHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnWakeReasonEntity
import de.sanniki.wakesleuth.data.db.entity.SystemSnapshotEntity
import de.sanniki.wakesleuth.data.db.entity.UsbDeviceEventEntity

/**
 * One timeline row with every payload it may carry. Only the relations
 * matching [EventEntity.type] are filled; Room loads each relation with
 * one IN-query for the whole result, and a Flow over this re-emits on a
 * change in any of the tables.
 */
data class EventWithPayload(
    @Embedded
    val event: EventEntity,
    @Relation(parentColumns = ["session_id"], entityColumns = ["id"])
    val session: MonitoringSessionEntity?,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val wakeReason: ScreenOnWakeReasonEntity?,
    @Relation(entity = ScreenOnNotificationCauseEntity::class, parentColumns = ["id"], entityColumns = ["event_id"])
    val notificationCause: NotificationCauseWithNotification?,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val wakeLockHints: List<ScreenOnWakeLockHintEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val alarmHints: List<ScreenOnAlarmHintEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val jobHints: List<ScreenOnJobHintEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val notification: NotificationEntity?,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val cpuWakeup: CpuWakeupEntity?,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val cpuEvidence: List<CpuWakeupEvidenceEntity>,
    @Relation(entity = NetworkMeasurementEntity::class, parentColumns = ["id"], entityColumns = ["event_id"])
    val networkMeasurement: NetworkMeasurementWithUsage?,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val systemSnapshot: SystemSnapshotEntity?,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val expertSnapshot: ExpertSnapshotEntity?,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val expertSignals: List<ExpertSnapshotSignalEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val usbDevice: UsbDeviceEventEntity?,
)

data class NotificationCauseWithNotification(
    @Embedded
    val cause: ScreenOnNotificationCauseEntity,
    @Relation(parentColumns = ["notification_event_id"], entityColumns = ["event_id"])
    val notification: NotificationEntity?,
)

data class NetworkMeasurementWithUsage(
    @Embedded
    val measurement: NetworkMeasurementEntity,
    @Relation(parentColumns = ["session_id"], entityColumns = ["session_id"])
    val usage: List<NetworkAppUsageEntity>,
)
