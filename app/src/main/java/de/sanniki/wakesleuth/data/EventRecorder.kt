package de.sanniki.wakesleuth.data

import de.sanniki.wakesleuth.BackgroundJobDiagnostic
import de.sanniki.wakesleuth.WakeLockDiagnostic
import de.sanniki.wakesleuth.WakeReasonDiagnostic
import de.sanniki.wakesleuth.WakeupAlarmDiagnostic
import de.sanniki.wakesleuth.data.db.WakelogsDatabase
import de.sanniki.wakesleuth.data.db.entity.CpuWakeupEntity
import de.sanniki.wakesleuth.data.db.entity.CpuWakeupEvidenceEntity
import de.sanniki.wakesleuth.data.db.entity.EventEntity
import de.sanniki.wakesleuth.data.db.entity.ExpertSnapshotEntity
import de.sanniki.wakesleuth.data.db.entity.ExpertSnapshotSignalEntity
import de.sanniki.wakesleuth.data.db.entity.NotificationEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnAlarmHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnJobHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnNotificationCauseEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnWakeLockHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnWakeReasonEntity
import de.sanniki.wakesleuth.data.db.entity.SystemSnapshotEntity
import de.sanniki.wakesleuth.data.db.entity.UsbDeviceEventEntity
import de.sanniki.wakesleuth.domain.CauseAssessment
import de.sanniki.wakesleuth.domain.CpuEvidenceRules
import de.sanniki.wakesleuth.domain.EventType
import de.sanniki.wakesleuth.domain.EvidenceOrigin
import de.sanniki.wakesleuth.domain.EvidenceType
import de.sanniki.wakesleuth.domain.ExpertSnapshot
import de.sanniki.wakesleuth.domain.PowerKeySignal
import de.sanniki.wakesleuth.domain.Proximity
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.SystemSnapshot
import de.sanniki.wakesleuth.domain.UsbDeviceInfo
import de.sanniki.wakesleuth.domain.WakeLockTags
import de.sanniki.wakesleuth.domain.WakeReason
import de.sanniki.wakesleuth.domain.WakeReasonEvidence
import de.sanniki.wakesleuth.domain.WakeReasons
import java.security.MessageDigest
import kotlin.math.abs

/** One BatteryStats token that was active around a CPU wakeup. */
data class EvidenceCandidate(
    val origin: EvidenceOrigin,
    val type: EvidenceType,
    val rawSource: String,
)

/** A grouped CPU wakeup as detected by the BatteryStats parser. */
data class CpuWakeupCandidate(
    val occurredAt: Long,
    val rawWakeReason: String?,
    val runningObserved: Boolean,
    val returnedToSleepAt: Long?,
    val evidence: List<EvidenceCandidate>,
)

/**
 * Write side of the event log; replaces `EventStore.add*` / `attach*`.
 *
 * Events are only recorded while a session accepts them (running or
 * finalizing); otherwise every call is a no-op. Every attach is one
 * transaction that finds its SCREEN_ON with the historic time windows
 * and checks its guard with a query instead of searching text.
 */
class EventRecorder(
    private val writer: DatabaseWriter,
    private val labels: PackageLabelStore,
) {
    private val database: WakelogsDatabase get() = writer.database

    private val sessionDao get() = database.sessionDao()
    private val eventDao get() = database.eventDao()
    private val screenOnDao get() = database.screenOnDao()
    private val notificationDao get() = database.notificationDao()

    /** Returns the new event id, or null when no session is recording. */
    suspend fun recordScreenOn(
        at: Long,
        proximity: Proximity?,
    ): Long? =
        writer.transaction {
            val sessionId = recordingSessionId() ?: return@transaction null

            val eventId = insertEvent(sessionId, at, EventType.SCREEN_ON, proximity)

            val recent = notificationDao.newestBetween(from = at - CauseAssessment.POSSIBLE_CAUSE_WINDOW_MILLIS, to = at)

            if (recent != null) {
                screenOnDao.insertNotificationCause(
                    ScreenOnNotificationCauseEntity(
                        eventId = eventId,
                        notificationEventId = recent.eventId,
                        packageName = recent.packageName,
                        offsetMs = recent.occurredAt - at,
                    ),
                )
            }

            eventId
        }

    suspend fun recordScreenOff(
        at: Long,
        proximity: Proximity?,
    ) {
        recordPlain(at, EventType.SCREEN_OFF, proximity)
    }

    suspend fun recordPower(
        at: Long,
        connected: Boolean,
    ) {
        recordPlain(
            at = at,
            type = if (connected) EventType.POWER_CONNECTED else EventType.POWER_DISCONNECTED,
            proximity = null,
        )
    }

    suspend fun recordUsb(
        at: Long,
        attached: Boolean,
        device: UsbDeviceInfo?,
    ) {
        writer.transaction {
            val sessionId = recordingSessionId() ?: return@transaction

            val eventId = insertEvent(
                sessionId = sessionId,
                at = at,
                type = if (attached) EventType.USB_ATTACHED else EventType.USB_DETACHED,
                proximity = null,
            )

            if (device != null) {
                database.snapshotDao().insertUsbDevice(
                    UsbDeviceEventEntity(
                        eventId = eventId,
                        deviceId = device.deviceId,
                        vendorId = device.vendorId,
                        productId = device.productId,
                        deviceName = device.deviceName,
                        manufacturerName = device.manufacturerName,
                    ),
                )
            }
        }
    }

    /**
     * Stores a posted notification unless the same one was seen within
     * [DUPLICATE_WINDOW_MILLIS], then links it to a screen-on that turned
     * on shortly before and is still unexplained.
     */
    suspend fun recordNotification(
        at: Long,
        packageName: String,
        notificationKey: String?,
        title: String?,
        text: String?,
    ): Long? {
        val fingerprint = fingerprint(
            packageName = packageName,
            notificationKey = notificationKey.orEmpty(),
            title = title.orEmpty(),
            text = text.orEmpty(),
        )

        val eventId = writer.transaction {
            val sessionId = recordingSessionId() ?: return@transaction null

            if (notificationDao.fingerprintSeenSince(fingerprint, at - DUPLICATE_WINDOW_MILLIS)) {
                return@transaction null
            }

            val eventId = insertEvent(sessionId, at, EventType.NOTIFICATION, null)

            notificationDao.insert(
                NotificationEntity(
                    eventId = eventId,
                    packageName = packageName,
                    notificationKey = notificationKey,
                    title = title?.takeIf { it.isNotBlank() },
                    text = text?.takeIf { it.isNotBlank() },
                    fingerprint = fingerprint,
                ),
            )

            attachLateNotification(notificationEventId = eventId, at = at, packageName = packageName)

            eventId
        }

        if (eventId != null) {
            labels.remember(listOf(packageName))
        }

        return eventId
    }

    /** Direct wake reason from the PowerManager log. */
    suspend fun attachWakeReason(
        screenOnAt: Long,
        diagnostic: WakeReasonDiagnostic,
    ): Boolean {
        val reason = diagnostic.reason?.takeIf { it.isNotBlank() } ?: return false
        val wakeAt = diagnostic.timestampMillis ?: return false

        if (diagnostic.error != null || diagnostic.rawEntry.isNullOrBlank()) {
            return false
        }

        val distance = wakeAt - screenOnAt

        if (distance < -WAKE_REASON_BEFORE_WINDOW_MILLIS || distance > WAKE_REASON_AFTER_WINDOW_MILLIS) {
            return false
        }

        return writer.transaction {
            val target = screenOnDao.newestWithoutWakeReason(
                from = screenOnAt - SCREEN_EVENT_MATCH_WINDOW_MILLIS,
                to = screenOnAt + SCREEN_EVENT_MATCH_WINDOW_MILLIS,
            ) ?: return@transaction false

            screenOnDao.insertWakeReason(
                ScreenOnWakeReasonEntity(
                    eventId = target.id,
                    reason = WakeReasons.fromPowerManager(reason, diagnostic.details),
                    evidence = WakeReasonEvidence.POWER_MANAGER_LOG,
                    offsetMs = wakeAt - target.occurredAt,
                    rawReason = reason,
                    rawDetails = diagnostic.details?.takeIf { it.isNotBlank() },
                ),
            ) != -1L
        }
    }

    /** Power key press found in the BatteryStats history (mostly Samsung). */
    suspend fun attachBatteryStatsPowerKey(
        powerKeyAt: Long,
        signal: PowerKeySignal,
        rawReason: String?,
        rawTag: String?,
    ): Boolean =
        writer.transaction {
            val target = screenOnDao.newestWithoutWakeReason(
                from = powerKeyAt - SCREEN_EVENT_MATCH_WINDOW_MILLIS,
                to = powerKeyAt + SCREEN_EVENT_MATCH_WINDOW_MILLIS,
            ) ?: return@transaction false

            screenOnDao.insertWakeReason(
                ScreenOnWakeReasonEntity(
                    eventId = target.id,
                    reason = WakeReason.POWER_BUTTON,
                    evidence = WakeReasonEvidence.BATTERYSTATS_POWER_KEY,
                    powerKeySignal = signal,
                    offsetMs = powerKeyAt - target.occurredAt,
                    rawReason = rawReason?.takeIf { it.isNotBlank() },
                    rawTag = rawTag?.takeIf { it.isNotBlank() },
                ),
            ) != -1L
        }

    /**
     * Wakelock close to a screen-on. The power key wakelock is a direct
     * wake reason; everything else is a hint whose relation is derived.
     */
    suspend fun attachWakeLockHint(
        screenOnAt: Long,
        diagnostic: WakeLockDiagnostic,
    ): Boolean {
        val wakeLockAt = diagnostic.lastTimestampMillis ?: return false

        if (diagnostic.error != null || diagnostic.rawLastEntry == null) {
            return false
        }

        if (abs(wakeLockAt - screenOnAt) > WAKELOCK_LINK_WINDOW_MILLIS) {
            return false
        }

        val packageName = diagnostic.lastPackage?.takeIf { it.isNotBlank() }
        val tag = diagnostic.lastTag?.takeIf { it.isNotBlank() }.orEmpty()

        val attached = writer.transaction {
            val target = newestScreenOnAround(screenOnAt) ?: return@transaction false

            if (WakeLockTags.isPowerKey(tag)) {
                if (screenOnDao.hasWakeReason(target.id)) {
                    return@transaction false
                }

                return@transaction screenOnDao.insertWakeReason(
                    ScreenOnWakeReasonEntity(
                        eventId = target.id,
                        reason = WakeReason.POWER_BUTTON,
                        evidence = WakeReasonEvidence.POWER_KEY_WAKELOCK,
                        powerKeySignal = PowerKeySignal.POWER_KEY_WAKELOCK,
                        offsetMs = wakeLockAt - target.occurredAt,
                        rawTag = tag,
                    ),
                ) != -1L
            }

            screenOnDao.insertWakeLockHint(
                ScreenOnWakeLockHintEntity(
                    eventId = target.id,
                    offsetMs = wakeLockAt - target.occurredAt,
                    tag = tag,
                    packageName = packageName,
                ),
            ) != -1L
        }

        if (attached) {
            labels.remember(listOf(packageName))
        }

        return attached
    }

    suspend fun attachWakeupAlarmHint(
        screenOnAt: Long,
        diagnostic: WakeupAlarmDiagnostic,
    ): Boolean {
        val packageName = diagnostic.packageName?.takeIf { it.isNotBlank() } ?: return false
        val rawTag = diagnostic.tag?.takeIf { it.isNotBlank() } ?: return false
        val alarmAt = diagnostic.triggerTimestampMillis ?: return false

        if (diagnostic.error != null) {
            return false
        }

        // Negative: before the screen-on. A small positive value counts as
        // nearly simultaneous, but not as proof.
        val distance = alarmAt - screenOnAt

        if (distance < -WAKEUP_ALARM_BEFORE_WINDOW_MILLIS || distance > WAKEUP_ALARM_AFTER_TOLERANCE_MILLIS) {
            return false
        }

        val attached = writer.transaction {
            val target = newestScreenOnAround(screenOnAt) ?: return@transaction false

            screenOnDao.insertAlarmHint(
                ScreenOnAlarmHintEntity(
                    eventId = target.id,
                    offsetMs = alarmAt - target.occurredAt,
                    packageName = packageName,
                    tag = rawTag.removePrefix("*walarm*:").ifBlank { rawTag },
                    alarmWakeCount = diagnostic.wakeCount,
                    packageWakeups = diagnostic.packageWakeups,
                ),
            ) != -1L
        }

        if (attached) {
            labels.remember(listOf(packageName))
        }

        return attached
    }

    suspend fun attachBackgroundJobHint(
        screenOnAt: Long,
        diagnostic: BackgroundJobDiagnostic,
    ): Boolean {
        val packageName = diagnostic.packageName?.takeIf { it.isNotBlank() } ?: return false
        val serviceName = diagnostic.serviceName?.takeIf { it.isNotBlank() } ?: return false
        val jobAt = diagnostic.triggerTimestampMillis ?: return false

        if (diagnostic.error != null) {
            return false
        }

        val distance = jobAt - screenOnAt

        if (distance < -BACKGROUND_JOB_BEFORE_WINDOW_MILLIS || distance > BACKGROUND_JOB_AFTER_TOLERANCE_MILLIS) {
            return false
        }

        val attached = writer.transaction {
            val target = newestScreenOnAround(screenOnAt) ?: return@transaction false

            screenOnDao.insertJobHint(
                ScreenOnJobHintEntity(
                    eventId = target.id,
                    offsetMs = jobAt - target.occurredAt,
                    packageName = packageName,
                    serviceName = serviceName,
                    prioritized = diagnostic.prioritized,
                ),
            ) != -1L
        }

        if (attached) {
            labels.remember(listOf(packageName))
        }

        return attached
    }

    /** Returns the event id, or null when not recorded or a duplicate. */
    suspend fun recordCpuWakeup(candidate: CpuWakeupCandidate): Long? {
        val evidence = candidate.evidence.distinctBy { it.type to it.rawSource }

        val primaryIndex = CpuEvidenceRules.primaryIndex(evidence.map { it.type })

        val packages = evidence.map { CpuEvidenceRules.extractPackageName(it.rawSource) }

        val eventId = writer.transaction {
            val sessionId = recordingSessionId() ?: return@transaction null

            if (eventDao.cpuWakeupExists(candidate.occurredAt, candidate.rawWakeReason)) {
                return@transaction null
            }

            val eventId = insertEvent(sessionId, candidate.occurredAt, EventType.CPU_WAKEUP, null)

            database.cpuWakeupDao().insert(
                CpuWakeupEntity(
                    eventId = eventId,
                    rawWakeReason = candidate.rawWakeReason,
                    runningObserved = candidate.runningObserved,
                    returnedToSleepAt = candidate.returnedToSleepAt,
                    awakeMs = candidate.returnedToSleepAt?.minus(candidate.occurredAt)?.takeIf { it >= 0L },
                ),
            )

            database.cpuWakeupDao().insertEvidence(
                evidence.mapIndexed { index, item ->
                    CpuWakeupEvidenceEntity(
                        eventId = eventId,
                        origin = item.origin,
                        evidenceType = item.type,
                        rawSource = item.rawSource,
                        packageName = packages[index],
                        isPrimary = index == primaryIndex,
                    )
                },
            )

            eventId
        }

        if (eventId != null) {
            labels.remember(packages)
        }

        return eventId
    }

    suspend fun recordSystemSnapshot(
        at: Long,
        snapshot: SystemSnapshot,
    ) {
        writer.transaction {
            val sessionId = recordingSessionId() ?: return@transaction

            val eventId = insertEvent(sessionId, at, EventType.SYSTEM_SNAPSHOT, null)

            database.snapshotDao().insertSystemSnapshot(
                SystemSnapshotEntity(
                    eventId = eventId,
                    trigger = snapshot.trigger,
                    status = snapshot.status,
                    errorDetail = snapshot.errorDetail,
                    wakefulness = snapshot.wakefulness,
                    interactive = snapshot.interactive,
                    lowPowerMode = snapshot.lowPowerMode,
                    deviceIdleMode = snapshot.deviceIdleMode,
                    lightDeviceIdleMode = snapshot.lightDeviceIdleMode,
                    deepIdleState = snapshot.deepIdleState,
                    lightIdleState = snapshot.lightIdleState,
                    idleScreenOn = snapshot.idleScreenOn,
                    idleCharging = snapshot.idleCharging,
                    forceIdle = snapshot.forceIdle,
                ),
            )
        }
    }

    suspend fun recordExpertSnapshot(
        at: Long,
        snapshot: ExpertSnapshot,
    ) {
        writer.transaction {
            val sessionId = recordingSessionId() ?: return@transaction

            val eventId = insertEvent(sessionId, at, EventType.EXPERT_SNAPSHOT, null)

            database.snapshotDao().insertExpertSnapshot(
                ExpertSnapshotEntity(
                    eventId = eventId,
                    screenOnEventId = snapshot.screenOnEventId,
                    status = snapshot.status,
                    errorDetail = snapshot.errorDetail,
                    locationAvailable = snapshot.locationAvailable,
                    sensorsAvailable = snapshot.sensorsAvailable,
                    networkAvailable = snapshot.networkAvailable,
                ),
            )

            database.snapshotDao().insertExpertSignals(snapshot.signals.map { ExpertSnapshotSignalEntity(eventId, it) })
        }
    }

    private suspend fun recordPlain(
        at: Long,
        type: EventType,
        proximity: Proximity?,
    ) {
        writer.transaction<Unit> {
            val sessionId = recordingSessionId() ?: return@transaction
            insertEvent(sessionId, at, type, proximity)
        }
    }

    /**
     * Links a notification to a screen-on 0–5 s before it that has no
     * cause yet and is still unexplained.
     */
    private suspend fun attachLateNotification(
        notificationEventId: Long,
        at: Long,
        packageName: String,
    ) {
        val candidates = eventDao.screenOnsBetween(from = at - LATE_NOTIFICATION_WINDOW_MILLIS, to = at)

        for (candidate in candidates) {
            val screenOn = eventDao.byId(candidate.id)?.let(EventMapper::toDomain) as? ScreenOnEvent
                ?: continue

            if (screenOn.notificationCause != null || !CauseAssessment.isUnexplained(screenOn)) {
                continue
            }

            screenOnDao.insertNotificationCause(
                ScreenOnNotificationCauseEntity(
                    eventId = screenOn.id,
                    notificationEventId = notificationEventId,
                    packageName = packageName,
                    offsetMs = at - screenOn.occurredAt,
                ),
            )

            return
        }
    }

    private suspend fun newestScreenOnAround(at: Long): EventEntity? =
        eventDao
            .screenOnsBetween(
                from = at - SCREEN_EVENT_MATCH_WINDOW_MILLIS,
                to = at + SCREEN_EVENT_MATCH_WINDOW_MILLIS,
            ).firstOrNull()

    private suspend fun recordingSessionId(): Long? = sessionDao.recordingSession()?.id

    private suspend fun insertEvent(
        sessionId: Long,
        at: Long,
        type: EventType,
        proximity: Proximity?,
    ): Long =
        eventDao.insert(
            EventEntity(
                sessionId = sessionId,
                occurredAt = at,
                type = type,
                proximityState = proximity?.state,
                proximityDistanceCm = proximity?.distanceCm,
            ),
        )

    private fun fingerprint(
        packageName: String,
        notificationKey: String,
        title: String,
        text: String,
    ): String {
        val source = "$packageName|$notificationKey|$title|$text"

        return MessageDigest
            .getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DUPLICATE_WINDOW_MILLIS = 5_000L
        const val LATE_NOTIFICATION_WINDOW_MILLIS = 5_000L
        const val WAKELOCK_LINK_WINDOW_MILLIS = 5_000L
        const val WAKEUP_ALARM_BEFORE_WINDOW_MILLIS = 5_000L
        const val WAKEUP_ALARM_AFTER_TOLERANCE_MILLIS = 750L
        const val BACKGROUND_JOB_BEFORE_WINDOW_MILLIS = 5_000L
        const val BACKGROUND_JOB_AFTER_TOLERANCE_MILLIS = 750L
        const val WAKE_REASON_BEFORE_WINDOW_MILLIS = 2_000L
        const val WAKE_REASON_AFTER_WINDOW_MILLIS = 2_000L
        const val SCREEN_EVENT_MATCH_WINDOW_MILLIS = 2_000L
    }
}
