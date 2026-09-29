package de.sanniki.wakesleuth.data

import de.sanniki.wakesleuth.DeviceFamily
import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity
import de.sanniki.wakesleuth.data.db.relation.EventWithPayload
import de.sanniki.wakesleuth.data.db.relation.NetworkMeasurementWithUsage
import de.sanniki.wakesleuth.domain.AlarmHint
import de.sanniki.wakesleuth.domain.CpuEvidence
import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.DirectWakeReason
import de.sanniki.wakesleuth.domain.EventType
import de.sanniki.wakesleuth.domain.ExpertSnapshot
import de.sanniki.wakesleuth.domain.ExpertSnapshotEvent
import de.sanniki.wakesleuth.domain.ExpertSnapshotStatus
import de.sanniki.wakesleuth.domain.JobHint
import de.sanniki.wakesleuth.domain.MonitorStartEvent
import de.sanniki.wakesleuth.domain.MonitorStopEvent
import de.sanniki.wakesleuth.domain.NetworkAppUsage
import de.sanniki.wakesleuth.domain.NetworkMeasurement
import de.sanniki.wakesleuth.domain.NetworkSessionEvent
import de.sanniki.wakesleuth.domain.NotificationCause
import de.sanniki.wakesleuth.domain.NotificationEvent
import de.sanniki.wakesleuth.domain.PowerEvent
import de.sanniki.wakesleuth.domain.Proximity
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.ScreenOffEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.SnapshotStatus
import de.sanniki.wakesleuth.domain.SnapshotTrigger
import de.sanniki.wakesleuth.domain.SystemSnapshot
import de.sanniki.wakesleuth.domain.SystemSnapshotEvent
import de.sanniki.wakesleuth.domain.UsbDeviceInfo
import de.sanniki.wakesleuth.domain.UsbEvent
import de.sanniki.wakesleuth.domain.WakeLockHint

/** Assembles the typed read model from a timeline row and its payload. */
object EventMapper {
    fun toDomain(row: EventWithPayload): RecordedEvent {
        val event = row.event
        val proximity = event.proximityState?.let { Proximity(it, event.proximityDistanceCm) }

        return when (event.type) {
            EventType.MONITOR_START -> {
                MonitorStartEvent(event.id, event.sessionId, event.occurredAt, proximity)
            }

            EventType.MONITOR_STOP -> {
                MonitorStopEvent(
                    id = event.id,
                    sessionId = event.sessionId,
                    occurredAt = event.occurredAt,
                    finalPollCompleted = row.session?.finalPollCompleted,
                )
            }

            EventType.SCREEN_ON -> {
                ScreenOnEvent(
                    id = event.id,
                    sessionId = event.sessionId,
                    occurredAt = event.occurredAt,
                    proximity = proximity,
                    wakeReason = row.wakeReason?.let {
                        DirectWakeReason(
                            reason = it.reason,
                            evidence = it.evidence,
                            powerKeySignal = it.powerKeySignal,
                            offsetMs = it.offsetMs,
                            rawReason = it.rawReason,
                            rawDetails = it.rawDetails,
                            rawTag = it.rawTag,
                        )
                    },
                    notificationCause = row.notificationCause?.let {
                        NotificationCause(
                            notificationEventId = it.cause.notificationEventId,
                            packageName = it.cause.packageName,
                            offsetMs = it.cause.offsetMs,
                            notificationTitle = it.notification?.title,
                        )
                    },
                    wakeLockHints = row.wakeLockHints
                        .sortedBy { it.id }
                        .map { WakeLockHint(it.offsetMs, it.tag, it.packageName, it.uid) },
                    alarmHints = row.alarmHints
                        .sortedBy { it.id }
                        .map {
                            AlarmHint(
                                offsetMs = it.offsetMs,
                                packageName = it.packageName,
                                tag = it.tag,
                                alarmWakeCount = it.alarmWakeCount,
                                packageWakeups = it.packageWakeups,
                            )
                        },
                    jobHints = row.jobHints
                        .sortedBy { it.id }
                        .map { JobHint(it.offsetMs, it.packageName, it.serviceName, it.prioritized) },
                )
            }

            EventType.SCREEN_OFF -> {
                ScreenOffEvent(event.id, event.sessionId, event.occurredAt, proximity)
            }

            EventType.POWER_CONNECTED,
            EventType.POWER_DISCONNECTED,
            -> {
                PowerEvent(
                    id = event.id,
                    sessionId = event.sessionId,
                    occurredAt = event.occurredAt,
                    connected = event.type == EventType.POWER_CONNECTED,
                )
            }

            EventType.USB_ATTACHED,
            EventType.USB_DETACHED,
            -> {
                UsbEvent(
                    id = event.id,
                    sessionId = event.sessionId,
                    occurredAt = event.occurredAt,
                    attached = event.type == EventType.USB_ATTACHED,
                    device = row.usbDevice?.let {
                        UsbDeviceInfo(
                            deviceId = it.deviceId,
                            vendorId = it.vendorId,
                            productId = it.productId,
                            deviceName = it.deviceName,
                            manufacturerName = it.manufacturerName,
                        )
                    },
                )
            }

            EventType.NOTIFICATION -> {
                NotificationEvent(
                    id = event.id,
                    sessionId = event.sessionId,
                    occurredAt = event.occurredAt,
                    packageName = row.notification?.packageName.orEmpty(),
                    notificationKey = row.notification?.notificationKey,
                    title = row.notification?.title,
                    text = row.notification?.text,
                )
            }

            EventType.CPU_WAKEUP -> {
                CpuWakeupEvent(
                    id = event.id,
                    sessionId = event.sessionId,
                    occurredAt = event.occurredAt,
                    rawWakeReason = row.cpuWakeup?.rawWakeReason,
                    runningObserved = row.cpuWakeup?.runningObserved ?: false,
                    returnedToSleepAt = row.cpuWakeup?.returnedToSleepAt,
                    awakeMs = row.cpuWakeup?.awakeMs,
                    evidence = row.cpuEvidence
                        .sortedBy { it.id }
                        .map {
                            CpuEvidence(
                                origin = it.origin,
                                type = it.evidenceType,
                                rawSource = it.rawSource,
                                packageName = it.packageName,
                                isPrimary = it.isPrimary,
                            )
                        },
                )
            }

            EventType.NETWORK_SESSION -> {
                NetworkSessionEvent(
                    id = event.id,
                    sessionId = event.sessionId,
                    occurredAt = event.occurredAt,
                    measurement = row.networkMeasurement?.let {
                        toDomain(it, row.session)
                    },
                )
            }

            EventType.SYSTEM_SNAPSHOT -> {
                SystemSnapshotEvent(
                    id = event.id,
                    sessionId = event.sessionId,
                    occurredAt = event.occurredAt,
                    snapshot = row.systemSnapshot?.let {
                        SystemSnapshot(
                            trigger = it.trigger,
                            status = it.status,
                            errorDetail = it.errorDetail,
                            wakefulness = it.wakefulness,
                            interactive = it.interactive,
                            lowPowerMode = it.lowPowerMode,
                            deviceIdleMode = it.deviceIdleMode,
                            lightDeviceIdleMode = it.lightDeviceIdleMode,
                            deepIdleState = it.deepIdleState,
                            lightIdleState = it.lightIdleState,
                            idleScreenOn = it.idleScreenOn,
                            idleCharging = it.idleCharging,
                            forceIdle = it.forceIdle,
                        )
                    } ?: SystemSnapshot(
                        trigger = SnapshotTrigger.AFTER_SCREEN_ON,
                        status = SnapshotStatus.TIMEOUT_OR_EMPTY,
                    ),
                )
            }

            EventType.EXPERT_SNAPSHOT -> {
                ExpertSnapshotEvent(
                    id = event.id,
                    sessionId = event.sessionId,
                    occurredAt = event.occurredAt,
                    snapshot = row.expertSnapshot?.let {
                        ExpertSnapshot(
                            screenOnEventId = it.screenOnEventId,
                            status = it.status,
                            errorDetail = it.errorDetail,
                            locationAvailable = it.locationAvailable,
                            sensorsAvailable = it.sensorsAvailable,
                            networkAvailable = it.networkAvailable,
                            signals = row.expertSignals.map { signal -> signal.signal }.toSet(),
                        )
                    } ?: ExpertSnapshot(
                        screenOnEventId = null,
                        status = ExpertSnapshotStatus.ERROR,
                        locationAvailable = false,
                        sensorsAvailable = false,
                        networkAvailable = false,
                        signals = emptySet(),
                    ),
                    deviceFamily = row.session?.deviceFamily ?: DeviceFamily.GENERIC_ANDROID,
                )
            }
        }
    }

    fun toDomain(
        row: NetworkMeasurementWithUsage,
        session: MonitoringSessionEntity?,
    ): NetworkMeasurement =
        NetworkMeasurement(
            sessionId = row.measurement.sessionId,
            status = row.measurement.status,
            baselineCapturedAt = session?.networkBaselineCapturedAt,
            measuredAt = row.measurement.measuredAt,
            errorCode = row.measurement.errorCode,
            errorDetail = row.measurement.errorDetail,
            usage = row.usage
                .map {
                    NetworkAppUsage(
                        uid = it.uid,
                        packageName = it.packageName,
                        rxBytes = it.rxBytes,
                        txBytes = it.txBytes,
                        rxPackets = it.rxPackets,
                        txPackets = it.txPackets,
                    )
                }.sortedWith(
                    compareByDescending<NetworkAppUsage> { it.totalBytes }.thenBy { it.uid },
                ),
        )
}
