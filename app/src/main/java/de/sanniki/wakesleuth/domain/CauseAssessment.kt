package de.sanniki.wakesleuth.domain

/**
 * How a hint relates to its screen-on. Never stored: it depends on the
 * other facts of the same screen-on (a direct wake reason turns every
 * hint into accompanying activity).
 */
enum class HintRelation {
    COMPANION,
    POSSIBLE_TRIGGER,
    SIMULTANEOUS,
    CLOSE_RELATION,
    TIME_RELATION
}

enum class NotificationCauseKind {
    /** Notification up to 3 s before screen-on. */
    PROBABLE,

    /** Notification 3–10 s before screen-on. */
    POSSIBLE,

    /** Notification arrived shortly after the screen was already on. */
    LATER_DETECTED
}

enum class CauseConfidenceLevel {
    HIGH,
    MEDIUM
}

/** Overall verdict for a screen-on, strongest evidence first. */
enum class ScreenOnVerdict {
    CONFIRMED,
    PROBABLE_NOTIFICATION,
    POSSIBLE_NOTIFICATION,
    POSSIBLE_WAKEUP_ALARM,
    POSSIBLE_WAKELOCK,
    COMPANION,
    UNRESOLVED
}

object CauseAssessment {

    const val HIGH_CONFIDENCE_WINDOW_MILLIS = 3_000L
    const val POSSIBLE_CAUSE_WINDOW_MILLIS = 10_000L

    fun kindOf(cause: NotificationCause): NotificationCauseKind =
        when {
            cause.offsetMs > 0L -> NotificationCauseKind.LATER_DETECTED
            -cause.offsetMs <= HIGH_CONFIDENCE_WINDOW_MILLIS -> NotificationCauseKind.PROBABLE
            else -> NotificationCauseKind.POSSIBLE
        }

    fun confidenceOf(cause: NotificationCause): CauseConfidenceLevel =
        if (kotlin.math.abs(cause.offsetMs) <= HIGH_CONFIDENCE_WINDOW_MILLIS) {
            CauseConfidenceLevel.HIGH
        } else {
            CauseConfidenceLevel.MEDIUM
        }

    /**
     * Only wakelocks before the screen-on may be called a possible
     * trigger; simultaneous or later ones accompany the wake-up.
     */
    fun relationOf(screenOn: ScreenOnEvent, hint: WakeLockHint): HintRelation =
        if (
            screenOn.wakeReason != null ||
            hint.offsetMs >= 0L ||
            WakeLockTags.isKnownFollowUp(hint.tag)
        ) {
            HintRelation.COMPANION
        } else {
            HintRelation.POSSIBLE_TRIGGER
        }

    fun relationOf(screenOn: ScreenOnEvent, hint: AlarmHint): HintRelation =
        when {
            screenOn.wakeReason != null -> HintRelation.COMPANION
            hint.offsetMs < 0L -> HintRelation.POSSIBLE_TRIGGER
            hint.offsetMs == 0L -> HintRelation.SIMULTANEOUS
            else -> HintRelation.CLOSE_RELATION
        }

    /**
     * A job start right before the screen-on can matter in time, but it
     * does not prove the job turned the display on.
     */
    fun relationOf(screenOn: ScreenOnEvent, hint: JobHint): HintRelation =
        if (screenOn.wakeReason != null) {
            HintRelation.COMPANION
        } else {
            HintRelation.TIME_RELATION
        }

    fun hasNonCompanionHint(screenOn: ScreenOnEvent): Boolean =
        screenOn.wakeLockHints.any { relationOf(screenOn, it) != HintRelation.COMPANION } ||
            screenOn.alarmHints.any { relationOf(screenOn, it) != HintRelation.COMPANION } ||
            screenOn.jobHints.any { relationOf(screenOn, it) != HintRelation.COMPANION }

    /** A cause or at least a hint that is more than accompanying activity. */
    fun hasExplanationOrHint(screenOn: ScreenOnEvent): Boolean =
        screenOn.wakeReason != null ||
            screenOn.notificationCause != null ||
            hasNonCompanionHint(screenOn)

    fun isUnexplained(screenOn: ScreenOnEvent): Boolean =
        !hasExplanationOrHint(screenOn)

    fun verdictOf(screenOn: ScreenOnEvent): ScreenOnVerdict {
        if (screenOn.wakeReason != null) {
            return ScreenOnVerdict.CONFIRMED
        }

        screenOn.notificationCause?.let { cause ->
            return when (confidenceOf(cause)) {
                CauseConfidenceLevel.HIGH -> ScreenOnVerdict.PROBABLE_NOTIFICATION
                CauseConfidenceLevel.MEDIUM -> ScreenOnVerdict.POSSIBLE_NOTIFICATION
            }
        }

        if (screenOn.alarmHints.any { relationOf(screenOn, it) != HintRelation.COMPANION }) {
            return ScreenOnVerdict.POSSIBLE_WAKEUP_ALARM
        }

        if (screenOn.wakeLockHints.any { relationOf(screenOn, it) == HintRelation.POSSIBLE_TRIGGER }) {
            return ScreenOnVerdict.POSSIBLE_WAKELOCK
        }

        if (
            screenOn.wakeLockHints.isNotEmpty() ||
            screenOn.alarmHints.isNotEmpty() ||
            screenOn.jobHints.isNotEmpty()
        ) {
            return ScreenOnVerdict.COMPANION
        }

        return ScreenOnVerdict.UNRESOLVED
    }

    fun isPowerButton(screenOn: ScreenOnEvent): Boolean =
        screenOn.wakeReason?.reason == WakeReason.POWER_BUTTON
}
