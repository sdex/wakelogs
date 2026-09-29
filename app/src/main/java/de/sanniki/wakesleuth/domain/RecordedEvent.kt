package de.sanniki.wakesleuth.domain

import de.sanniki.wakesleuth.DeviceFamily

/*
 * Read model of one timeline row, assembled from the event table and its
 * payload tables. Everything shown to the user is rendered from these
 * facts; nothing here is display text except third-party content.
 */

data class Proximity(
    val state: ProximityState,
    val distanceCm: Float? = null
)

sealed interface RecordedEvent {
    val id: Long
    val sessionId: Long
    val occurredAt: Long
    val type: EventType
}

data class MonitorStartEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val proximity: Proximity?
) : RecordedEvent {
    override val type get() = EventType.MONITOR_START
}

data class MonitorStopEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val finalPollCompleted: Boolean?
) : RecordedEvent {
    override val type get() = EventType.MONITOR_STOP
}

data class ScreenOnEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val proximity: Proximity?,
    val wakeReason: DirectWakeReason?,
    val notificationCause: NotificationCause?,
    val wakeLockHints: List<WakeLockHint>,
    val alarmHints: List<AlarmHint>,
    val jobHints: List<JobHint>
) : RecordedEvent {
    override val type get() = EventType.SCREEN_ON
}

data class ScreenOffEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val proximity: Proximity?
) : RecordedEvent {
    override val type get() = EventType.SCREEN_OFF
}

data class PowerEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val connected: Boolean
) : RecordedEvent {
    override val type
        get() =
            if (connected) {
                EventType.POWER_CONNECTED
            } else {
                EventType.POWER_DISCONNECTED
            }
}

data class UsbDeviceInfo(
    val deviceId: Int?,
    val vendorId: Int?,
    val productId: Int?,
    val deviceName: String? = null,
    val manufacturerName: String? = null
)

data class UsbEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val attached: Boolean,
    val device: UsbDeviceInfo?
) : RecordedEvent {
    override val type
        get() =
            if (attached) {
                EventType.USB_ATTACHED
            } else {
                EventType.USB_DETACHED
            }
}

data class NotificationEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val packageName: String,
    val notificationKey: String?,
    val title: String?,
    val text: String?
) : RecordedEvent {
    override val type get() = EventType.NOTIFICATION
}

data class CpuWakeupEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val rawWakeReason: String?,
    val runningObserved: Boolean,
    val returnedToSleepAt: Long?,
    val awakeMs: Long?,
    /** In detection order; at most one entry is primary. */
    val evidence: List<CpuEvidence>
) : RecordedEvent {
    override val type get() = EventType.CPU_WAKEUP

    val primaryEvidence: CpuEvidence?
        get() = evidence.firstOrNull { it.isPrimary }
}

data class NetworkSessionEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val measurement: NetworkMeasurement?
) : RecordedEvent {
    override val type get() = EventType.NETWORK_SESSION
}

data class SystemSnapshotEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val snapshot: SystemSnapshot
) : RecordedEvent {
    override val type get() = EventType.SYSTEM_SNAPSHOT
}

data class ExpertSnapshotEvent(
    override val id: Long,
    override val sessionId: Long,
    override val occurredAt: Long,
    val snapshot: ExpertSnapshot,
    /** Needed to render the device specific activity sensor signal. */
    val deviceFamily: DeviceFamily
) : RecordedEvent {
    override val type get() = EventType.EXPERT_SNAPSHOT
}

/* ---------- SCREEN_ON payload ---------- */

data class DirectWakeReason(
    val reason: WakeReason,
    val evidence: WakeReasonEvidence,
    val powerKeySignal: PowerKeySignal?,
    /** Signed: wake time minus screen-on time. */
    val offsetMs: Long,
    val rawReason: String?,
    val rawDetails: String?,
    val rawTag: String?
)

data class NotificationCause(
    val notificationEventId: Long?,
    val packageName: String,
    /** Signed: negative when the notification arrived before screen-on. */
    val offsetMs: Long,
    /** Title of the linked notification, if it still exists. */
    val notificationTitle: String?
)

data class WakeLockHint(
    val offsetMs: Long,
    val tag: String,
    val packageName: String?,
    val uid: Int?
)

data class AlarmHint(
    val offsetMs: Long,
    val packageName: String?,
    val tag: String,
    val alarmWakeCount: Int?,
    val packageWakeups: Int?
)

data class JobHint(
    val offsetMs: Long,
    val packageName: String?,
    val serviceName: String,
    val prioritized: Boolean
)

/* ---------- CPU_WAKEUP payload ---------- */

data class CpuEvidence(
    val origin: EvidenceOrigin,
    val type: EvidenceType,
    val rawSource: String,
    val packageName: String?,
    val isPrimary: Boolean
)

/* ---------- Network ---------- */

data class NetworkAppUsage(
    val uid: Int,
    val packageName: String?,
    val rxBytes: Long,
    val txBytes: Long,
    val rxPackets: Long? = null,
    val txPackets: Long? = null
) {
    val totalBytes: Long
        get() = saturatedAdd(rxBytes, txBytes)
}

data class NetworkMeasurement(
    val sessionId: Long,
    val status: NetworkMeasurementStatus,
    val baselineCapturedAt: Long?,
    val measuredAt: Long?,
    val errorCode: DiagnosticError?,
    val errorDetail: String?,
    /** Sorted by total traffic, largest first. */
    val usage: List<NetworkAppUsage>
) {
    val durationMs: Long?
        get() =
            if (measuredAt != null && baselineCapturedAt != null) {
                (measuredAt - baselineCapturedAt).coerceAtLeast(0L)
            } else {
                null
            }

    val rxBytes: Long
        get() = usage.fold(0L) { total, item -> saturatedAdd(total, item.rxBytes) }

    val txBytes: Long
        get() = usage.fold(0L) { total, item -> saturatedAdd(total, item.txBytes) }

    val totalBytes: Long
        get() = saturatedAdd(rxBytes, txBytes)
}

/* ---------- Snapshots ---------- */

data class SystemSnapshot(
    val trigger: SnapshotTrigger,
    val status: SnapshotStatus,
    val errorDetail: String? = null,
    val wakefulness: String? = null,
    val interactive: Boolean? = null,
    val lowPowerMode: Boolean? = null,
    val deviceIdleMode: Boolean? = null,
    val lightDeviceIdleMode: Boolean? = null,
    val deepIdleState: String? = null,
    val lightIdleState: String? = null,
    val idleScreenOn: Boolean? = null,
    val idleCharging: Boolean? = null,
    val forceIdle: String? = null
)

data class ExpertSnapshot(
    val screenOnEventId: Long?,
    val status: ExpertSnapshotStatus,
    val errorDetail: String? = null,
    val locationAvailable: Boolean,
    val sensorsAvailable: Boolean,
    val networkAvailable: Boolean,
    val signals: Set<ExpertSignal>
)

fun saturatedAdd(a: Long, b: Long): Long {
    val result = a + b
    return if ((a xor result) and (b xor result) < 0) Long.MAX_VALUE else result
}

fun saturatedSum(vararg values: Long): Long =
    values.fold(0L) { total, value -> saturatedAdd(total, value) }
