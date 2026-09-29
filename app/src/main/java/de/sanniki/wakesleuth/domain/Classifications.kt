package de.sanniki.wakesleuth.domain

/*
 * Pure classification rules derived from raw system tokens. They used to
 * be applied once at write time and baked into stored text; now they are
 * re-evaluated at read time from the stored raw values.
 */

enum class WakeLockKind {
    USER_PRESENCE,
    NETWORK_STATS,
    NOTIFICATION_PROCESSING,
    ALARM,
    BACKGROUND_JOB,
    APP_LAUNCH,
    AUDIO,
    SYNC,
    SEARCH_INDEXING,
    SCHEDULED_BACKGROUND_ACTION,
    PARTIAL_WAKELOCK
}

object WakeLockTags {

    fun kindOf(tag: String): WakeLockKind =
        when {
            tag.contains("UserPresent", ignoreCase = true) ->
                WakeLockKind.USER_PRESENCE

            tag.contains("NetworkStats", ignoreCase = true) ->
                WakeLockKind.NETWORK_STATS

            tag.contains("NotificationManagerService:post", ignoreCase = true) ->
                WakeLockKind.NOTIFICATION_PROCESSING

            tag.contains("*alarm*", ignoreCase = true) ->
                WakeLockKind.ALARM

            tag.contains("*job*", ignoreCase = true) ->
                WakeLockKind.BACKGROUND_JOB

            tag.contains("*launch*", ignoreCase = true) ->
                WakeLockKind.APP_LAUNCH

            tag.contains("AudioMix", ignoreCase = true) ||
                tag.contains("AudioIn", ignoreCase = true) ||
                tag.contains("ExoPlayer", ignoreCase = true) ->
                WakeLockKind.AUDIO

            tag.contains("SyncManager", ignoreCase = true) ||
                tag.contains("*sync*", ignoreCase = true) ->
                WakeLockKind.SYNC

            tag.contains("Icing", ignoreCase = true) ->
                WakeLockKind.SEARCH_INDEXING

            tag.contains("PendingIntentClient", ignoreCase = true) ->
                WakeLockKind.SCHEDULED_BACKGROUND_ACTION

            else ->
                WakeLockKind.PARTIAL_WAKELOCK
        }

    /**
     * Samsung's BatteryStats reports `PhoneWindowManager.mPowerKeyWakeLock`
     * for a power key press, which is much stronger than an ordinary
     * wakelock close to the screen-on.
     */
    fun isPowerKey(tag: String): Boolean =
        tag.contains("mPowerKeyWakeLock", ignoreCase = true)

    /** Tags that are consequences of the wake-up, never its trigger. */
    fun isKnownFollowUp(tag: String): Boolean =
        FOLLOW_UP_MARKERS.any { tag.contains(it, ignoreCase = true) }

    private val FOLLOW_UP_MARKERS =
        listOf(
            "UserPresent",
            "NotificationManagerService:post",
            "*launch*",
            "NfcService:",
            "*telephony",
            "dream:dream"
        )
}

object WakeReasons {

    /** Maps a PowerManager `WAKE_REASON_*` value and its details. */
    fun fromPowerManager(reason: String?, details: String?): WakeReason {
        val detailText = details.orEmpty()

        return when {
            reason == "WAKE_REASON_POWER_BUTTON" -> WakeReason.POWER_BUTTON
            detailText.contains("DoubleTap", ignoreCase = true) ||
                detailText.contains("blackGestureWake", ignoreCase = true) ->
                WakeReason.DOUBLE_TAP
            reason == "WAKE_REASON_GESTURE" -> WakeReason.GESTURE
            reason == "WAKE_REASON_LIFT" -> WakeReason.LIFT
            reason == "WAKE_REASON_PLUGGED_IN" -> WakeReason.PLUGGED_IN
            reason == "WAKE_REASON_WAKE_KEY" -> WakeReason.WAKE_KEY
            reason == "WAKE_REASON_WAKE_MOTION" -> WakeReason.WAKE_MOTION
            reason == "WAKE_REASON_APPLICATION" -> WakeReason.APPLICATION
            else -> WakeReason.OTHER
        }
    }
}

/** Kernel/BatteryStats wake reason bucket of a CPU wakeup. */
enum class WakeReasonCategory {
    FAILED_SUSPEND,
    SCHEDULED_SYSTEM_ALARM,
    QUALCOMM_RADIO,
    TIMER_SCHEDULER,
    SYSTEM_ACTIVITY,
    KERNEL_HARDWARE_INTERRUPT,
    KERNEL_SYSTEM_SIGNAL,
    HARDWARE_INTERRUPT_SIGNAL,
    WAKEUP_ALARM,
    UNSPECIFIED,

    /** No known bucket; the raw reason is shown as is. */
    OTHER;

    companion object {

        /** The part of `123:"reason"` that names the wake source. */
        fun reasonText(rawReason: String): String =
            rawReason.substringAfter(':', rawReason).trim('"')

        fun of(rawReason: String): WakeReasonCategory {
            val reason = reasonText(rawReason)

            return when {
                reason.contains("failed to suspend", ignoreCase = true) ->
                    FAILED_SUSPEND

                reason.contains("pm8xxx_rtc_alarm", ignoreCase = true) ->
                    SCHEDULED_SYSTEM_ALARM

                reason.contains("qcom_rx_wakelock", ignoreCase = true) ||
                    reason.contains("qrtr_ws", ignoreCase = true) ->
                    QUALCOMM_RADIO

                reason.contains("timerfd", ignoreCase = true) ->
                    TIMER_SCHEDULER

                reason.contains("userspace-abort", ignoreCase = true) ->
                    SYSTEM_ACTIVITY

                reason.contains("NO_SUSPEND", ignoreCase = true) &&
                    reason.contains("IRQ", ignoreCase = true) ->
                    KERNEL_HARDWARE_INTERRUPT

                reason.contains("NO_SUSPEND", ignoreCase = true) ->
                    KERNEL_SYSTEM_SIGNAL

                reason.contains("IRQ", ignoreCase = true) ->
                    HARDWARE_INTERRUPT_SIGNAL

                reason.contains("alarm", ignoreCase = true) ->
                    WAKEUP_ALARM

                reason.isBlank() ->
                    UNSPECIFIED

                else ->
                    OTHER
            }
        }
    }
}

/** How a CPU wakeup was detected in the BatteryStats history. */
enum class DetectionKind {
    REASON_AND_CPU_START,
    REASON_ONLY,
    CPU_START_ONLY,
    BATTERYSTATS_ACTIVITY;

    companion object {
        fun of(hasWakeReason: Boolean, runningObserved: Boolean): DetectionKind =
            when {
                hasWakeReason && runningObserved -> REASON_AND_CPU_START
                hasWakeReason -> REASON_ONLY
                runningObserved -> CPU_START_ONLY
                else -> BATTERYSTATS_ACTIVITY
            }
    }
}

object CpuEvidenceRules {

    fun typeOfWakeLock(source: String): EvidenceType =
        when {
            source.contains("alarm", ignoreCase = true) ->
                EvidenceType.WAKEUP_ALARM

            source.contains("SyncManager", ignoreCase = true) ||
                source.contains("*sync*", ignoreCase = true) ->
                EvidenceType.SYNC

            source.contains("*job*", ignoreCase = true) ->
                EvidenceType.JOB_WAKELOCK

            else ->
                EvidenceType.PARTIAL_WAKELOCK
        }

    fun typeOfJob(source: String): EvidenceType =
        when {
            source.contains("SyncManager", ignoreCase = true) ->
                EvidenceType.SYNC

            source.contains("androidx.work", ignoreCase = true) ||
                source.contains("SystemJobService", ignoreCase = true) ->
                EvidenceType.WORKMANAGER

            else ->
                EvidenceType.JOBSCHEDULER
        }

    /**
     * Index of the evidence chosen as "possible source": the first entry
     * of the highest priority type (see [EvidenceType] order).
     */
    fun primaryIndex(types: List<EvidenceType>): Int? =
        types.withIndex().minByOrNull { it.value.ordinal }?.index

    private val PACKAGE_PATTERN =
        Regex("""([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+)""")

    /** First package-like token of a BatteryStats source, if any. */
    fun extractPackageName(rawSource: String): String? =
        PACKAGE_PATTERN.find(rawSource)?.groupValues?.getOrNull(1)
}

enum class SnapshotClassification {
    ACTIVE,
    DEEP_IDLE,
    LIGHT_IDLE,
    VENDOR_SPECIFIC,
    UNCLEAR;

    companion object {
        fun of(snapshot: SystemSnapshot): SnapshotClassification {
            val wake = snapshot.wakefulness.orEmpty()

            return when {
                snapshot.interactive == true ||
                    wake.contains("Awake", ignoreCase = true) ->
                    ACTIVE

                snapshot.deviceIdleMode == true ||
                    snapshot.deepIdleState?.contains("IDLE", ignoreCase = true) == true ->
                    DEEP_IDLE

                snapshot.lightDeviceIdleMode == true ||
                    snapshot.lightIdleState?.contains("IDLE", ignoreCase = true) == true ->
                    LIGHT_IDLE

                wake.contains("Asleep", ignoreCase = true) ||
                    wake.contains("Dozing", ignoreCase = true) ->
                    VENDOR_SPECIFIC

                else ->
                    UNCLEAR
            }
        }
    }
}
