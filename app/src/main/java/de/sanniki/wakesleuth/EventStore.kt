package de.sanniki.wakesleuth

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

data class NetworkSessionBaseline(
    val capturedAtMillis: Long,
    val entries: List<NetworkTrafficEntry>
)

object EventStore {

    private const val PREFS_NAME = "wakesleuth_events"
    private const val KEY_EVENTS = "events"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEY_MONITORING = "monitoring"
    private const val KEY_NETWORK_BASELINE =
        "network_session_baseline"
    private const val KEY_NETWORK_BASELINE_TIMESTAMP =
        "network_session_baseline_timestamp"
    private const val MAX_EVENTS = 300

    private const val LATE_NOTIFICATION_WINDOW_MILLIS = 5_000L
    private const val HIGH_CONFIDENCE_WINDOW_MILLIS = 3_000L

    private const val WAKELOCK_LINK_WINDOW_MILLIS = 5_000L
    private const val WAKEUP_ALARM_BEFORE_WINDOW_MILLIS = 5_000L
    private const val WAKEUP_ALARM_AFTER_TOLERANCE_MILLIS = 750L
    private const val BACKGROUND_JOB_BEFORE_WINDOW_MILLIS = 5_000L
    private const val BACKGROUND_JOB_AFTER_TOLERANCE_MILLIS = 750L
    private const val WAKE_REASON_BEFORE_WINDOW_MILLIS = 2_000L
    private const val WAKE_REASON_AFTER_WINDOW_MILLIS = 2_000L
    private const val SCREEN_EVENT_MATCH_WINDOW_MILLIS = 2_000L

    private val lock = Any()

    fun addEventAt(
        context: Context,
        timestamp: Long,
        type: String,
        title: String,
        details: String
    ) {
        synchronized(lock) {
            val preferences =
                preferences(context)

            val nextId =
                preferences.getLong(
                    KEY_NEXT_ID,
                    0L
                ) + 1L

            val events =
                readEvents(
                    context,
                    preferences.getString(
                        KEY_EVENTS,
                        null
                    )
                )

            val duplicate =
                events.any { event ->
                    event.type == type &&
                        kotlin.math.abs(
                            event.timestamp -
                                timestamp
                        ) < 500L &&
                        event.details == details
                }

            if (duplicate) {
                return
            }

            events.add(
                0,
                WakeEvent(
                    id = nextId,
                    timestamp = timestamp,
                    type = type,
                    title = title,
                    details = details
                )
            )

            saveEvents(
                context = context,
                events =
                    events.take(MAX_EVENTS),
                nextId = nextId
            )
        }
    }

    fun addEvent(
        context: Context,
        type: String,
        title: String,
        details: String
    ) {
        synchronized(lock) {
            val preferences = preferences(context)
            val nextId = preferences.getLong(KEY_NEXT_ID, 0L) + 1L

            val events = readEvents(
                context,
                preferences.getString(KEY_EVENTS, null)
            )

            events.add(
                0,
                WakeEvent(
                    id = nextId,
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    title = title,
                    details = details
                )
            )

            saveEvents(
                context = context,
                events = events.take(MAX_EVENTS),
                nextId = nextId
            )
        }
    }

    fun attachLateNotificationToScreenOn(
        context: Context,
        notification: RecentNotification
    ): Boolean {
        synchronized(lock) {
            val prefs = preferences(context)
            val events = readEvents(
                context,
                prefs.getString(KEY_EVENTS, null)
            )

            val eventIndex = events.indexOfFirst { event ->
                if (event.type != "SCREEN_ON") {
                    return@indexOfFirst false
                }

                val distance =
                    notification.timestamp - event.timestamp

                distance in 0..LATE_NOTIFICATION_WINDOW_MILLIS &&
                    LocalizedText.containsAny(
                        event.details,
                        context,
                        R.string.sleep_marker_cause_unknown
                    )
            }

            if (eventIndex < 0) {
                return false
            }

            val oldEvent = events[eventIndex]
            val distance =
                notification.timestamp - oldEvent.timestamp

            val confidence =
                if (distance <= HIGH_CONFIDENCE_WINDOW_MILLIS) {
                    context.getString(
                        R.string.event_confidence_high
                    )
                } else {
                    context.getString(
                        R.string.event_confidence_medium
                    )
                }

            val proximityLine = oldEvent.details
                .lineSequence()
                .firstOrNull {
                    LocalizedText.startsWithAny(
                        it,
                        context,
                        R.string.event_label_proximity_sensor
                    )
                }
                ?: unknownProximityLine(context)

            val preservedSystemHints =
                extractSystemHintSections(
                    context,
                    oldEvent.details
                )

            events[eventIndex] = oldEvent.copy(
                details = buildString {
                    appendLine(proximityLine)

                    appendLine(
                        labeledLine(
                            context,
                            R.string.event_label_cause_detected_later,
                            notification.appName
                        )
                    )

                    appendLine(
                        labeledLine(
                            context,
                            R.string.event_label_confidence,
                            confidence
                        )
                    )

                    append(
                        context.getString(
                            R.string.event_notification_arrived_after_screen_on,
                            formatAge(context, distance)
                        )
                    )

                    if (notification.title.isNotBlank()) {
                        appendLine()
                        append(
                            labeledLine(
                                context,
                                R.string.event_label_title,
                                notification.title
                            )
                        )
                    }

                    if (preservedSystemHints.isNotBlank()) {
                        appendLine()
                        appendLine()
                        append(
                            preservedSystemHints
                        )
                    }
                }
            )

            saveEvents(
                context = context,
                events = events,
                nextId = prefs.getLong(KEY_NEXT_ID, 0L)
            )

            return true
        }
    }

    fun attachBatteryStatsPowerKeyToScreenOn(
        context: Context,
        powerKeyTimestamp: Long,
        technicalReason: String,
        technicalTag: String?
    ): Boolean {
        synchronized(lock) {
            val prefs =
                preferences(context)

            val events =
                readEvents(
                    context,
                    prefs.getString(
                        KEY_EVENTS,
                        null
                    )
                )

            val eventIndex =
                events.indexOfFirst { event ->
                    event.type == "SCREEN_ON" &&
                        kotlin.math.abs(
                            event.timestamp -
                                powerKeyTimestamp
                        ) <=
                        SCREEN_EVENT_MATCH_WINDOW_MILLIS &&
                        !LocalizedText.containsAny(
                            event.details,
                            context,
                            R.string.event_label_direct_wake_reason
                        )
                }

            if (eventIndex < 0) {
                return false
            }

            val oldEvent =
                events[eventIndex]

            val proximityLine =
                oldEvent.details
                    .lineSequence()
                    .firstOrNull { line ->
                        LocalizedText.startsWithAny(
                            line,
                            context,
                            R.string.event_label_proximity_sensor
                        )
                    }
                    ?: unknownProximityLine(context)

            val preservedHints =
                extractSystemHintSections(
                    context,
                    oldEvent.details
                )
                    .let { hints ->
                        replaceAnyVariant(
                            text = hints,
                            context = context,
                            fromId =
                                R.string.event_section_system_hint_possible_trigger,
                            toId =
                                R.string.event_section_companion_wakelock
                        )
                    }
                    .let { hints ->
                        replaceAnyVariant(
                            text = hints,
                            context = context,
                            fromId =
                                R.string.event_section_wakeup_alarm_possible_trigger,
                            toId =
                                R.string.event_section_companion_wakeup_alarm
                        )
                    }

            events[eventIndex] =
                oldEvent.copy(
                    details = buildString {
                        appendLine(
                            proximityLine
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_direct_wake_reason,
                                context.getString(
                                    R.string.event_power_button
                                )
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_confidence,
                                context.getString(
                                    R.string.event_confidence_samsung_batterystats
                                )
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_time_offset,
                                context.getString(
                                    R.string.event_time_simultaneous_with_screen_on
                                )
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_technical_reason,
                                technicalReason
                            )
                        )

                        technicalTag
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let { tag ->
                                append(
                                    labeledLine(
                                        context,
                                        R.string.event_label_technical_tag,
                                        compactWakeLockTag(
                                            tag
                                        )
                                    )
                                )
                            }

                        if (
                            preservedHints.isNotBlank()
                        ) {
                            appendLine()
                            appendLine()
                            append(
                                preservedHints
                            )
                        }
                    }
                )

            saveEvents(
                context = context,
                events = events,
                nextId =
                    prefs.getLong(
                        KEY_NEXT_ID,
                        0L
                    )
            )

            return true
        }
    }

    fun attachWakeReasonToScreenOn(
        context: Context,
        screenOnTimestamp: Long,
        diagnostic: WakeReasonDiagnostic
    ): Boolean {
        if (
            diagnostic.error != null ||
            diagnostic.reason.isNullOrBlank() ||
            diagnostic.rawEntry.isNullOrBlank()
        ) {
            return false
        }

        val wakeTimestamp =
            diagnostic.timestampMillis
                ?: return false

        val distance =
            wakeTimestamp - screenOnTimestamp

        if (
            distance <
                -WAKE_REASON_BEFORE_WINDOW_MILLIS ||
            distance >
                WAKE_REASON_AFTER_WINDOW_MILLIS
        ) {
            return false
        }

        synchronized(lock) {
            val prefs = preferences(context)

            val events = readEvents(
                context,
                prefs.getString(
                    KEY_EVENTS,
                    null
                )
            )

            val eventIndex =
                events.indexOfFirst { event ->
                    event.type == "SCREEN_ON" &&
                        kotlin.math.abs(
                            event.timestamp -
                                screenOnTimestamp
                        ) <=
                        SCREEN_EVENT_MATCH_WINDOW_MILLIS &&
                        !LocalizedText.containsAny(
                            event.details,
                            context,
                            R.string.event_label_direct_wake_reason
                        )
                }

            if (eventIndex < 0) {
                return false
            }

            val oldEvent = events[eventIndex]

            val proximityLine =
                oldEvent.details
                    .lineSequence()
                    .firstOrNull { line ->
                        LocalizedText.startsWithAny(
                            line,
                            context,
                            R.string.event_label_proximity_sensor
                        )
                    }
                    ?: unknownProximityLine(context)

            val existingSystemHints =
                extractSystemHintSections(
                    context,
                    oldEvent.details
                )

            val directionText =
                when {
                    distance < 0L ->
                        context.getString(
                            R.string.event_time_before_screen_on,
                            formatAge(context, -distance)
                        )

                    distance > 0L ->
                        context.getString(
                            R.string.event_time_after_screen_on,
                            formatAge(context, distance)
                        )

                    else ->
                        context.getString(
                            R.string.event_time_simultaneous_with_screen_on
                        )
                }

            val technicalReason =
                diagnostic.reason
                    ?: context.getString(
                        R.string.event_unknown
                    )

            val technicalDetails =
                diagnostic.details
                    ?.takeIf { it.isNotBlank() }
                    ?: context.getString(
                        R.string.event_none
                    )

            events[eventIndex] =
                oldEvent.copy(
                    details = buildString {
                        appendLine(proximityLine)

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_direct_wake_reason,
                                readableWakeReason(
                                    context = context,
                                    reason =
                                        technicalReason,
                                    details =
                                        technicalDetails
                                )
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_confidence,
                                context.getString(
                                    R.string.event_confidence_power_manager
                                )
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_time_offset,
                                directionText
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_technical_reason,
                                technicalReason
                            )
                        )

                        append(
                            labeledLine(
                                context,
                                R.string.event_label_details,
                                compactWakeReasonDetails(
                                    technicalDetails
                                )
                            )
                        )

                        if (
                            existingSystemHints.isNotBlank()
                        ) {
                            appendLine()
                            appendLine()
                            append(
                                existingSystemHints
                            )
                        }
                    }
                )

            saveEvents(
                context = context,
                events = events,
                nextId = prefs.getLong(
                    KEY_NEXT_ID,
                    0L
                )
            )

            return true
        }
    }

    fun attachWakeLockHintToScreenOn(
        context: Context,
        screenOnTimestamp: Long,
        diagnostic: WakeLockDiagnostic
    ): Boolean {
        val wakeLockTimestamp =
            diagnostic.lastTimestampMillis
                ?: return false

        if (
            diagnostic.error != null ||
            diagnostic.rawLastEntry == null
        ) {
            return false
        }

        val distance =
            wakeLockTimestamp -
                screenOnTimestamp

        if (
            kotlin.math.abs(distance) >
            WAKELOCK_LINK_WINDOW_MILLIS
        ) {
            return false
        }

        synchronized(lock) {
            val prefs =
                preferences(context)

            val events =
                readEvents(
                    context,
                    prefs.getString(
                        KEY_EVENTS,
                        null
                    )
                )

            val eventIndex =
                events.indexOfFirst { event ->
                    event.type == "SCREEN_ON" &&
                        kotlin.math.abs(
                            event.timestamp -
                                screenOnTimestamp
                        ) <=
                        SCREEN_EVENT_MATCH_WINDOW_MILLIS
                }

            if (eventIndex < 0) {
                return false
            }

            val oldEvent =
                events[eventIndex]

            val rawPackage =
                diagnostic.lastPackage
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: context.getString(
                        R.string.event_unknown
                    )

            val rawTag =
                diagnostic.lastTag
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: context.getString(
                        R.string.event_unknown
                    )

            /*
             * Samsungs BatteryStats meldet beim Drücken
             * der Einschalttaste typischerweise:
             *
             * PhoneWindowManager.mPowerKeyWakeLock
             *
             * Dieser technische Tag ist deutlich stärker
             * als ein gewöhnlicher zeitnaher Wakelock.
             */
            if (
                isPowerKeyWakeLockTag(
                    rawTag
                )
            ) {
                if (
                    LocalizedText.containsAny(
                        oldEvent.details,
                        context,
                        R.string.event_label_direct_wake_reason
                    )
                ) {
                    return false
                }

                val cleanedDetails =
                    removeUnknownCauseLine(
                        context,
                        oldEvent.details
                    )

                val proximityLine =
                    oldEvent.details
                        .lineSequence()
                        .firstOrNull { line ->
                            LocalizedText.startsWithAny(
                                line,
                                context,
                                R.string.event_label_proximity_sensor
                            )
                        }
                        ?: unknownProximityLine(context)

                val existingNotificationLines =
                    cleanedDetails
                        .lineSequence()
                        .filterNot { line ->
                            LocalizedText.startsWithAny(
                                line,
                                context,
                                R.string.event_label_proximity_sensor
                            )
                        }
                        .joinToString("\n")
                        .trim()

                events[eventIndex] =
                    oldEvent.copy(
                        details = buildString {
                            appendLine(
                                proximityLine
                            )

                            appendLine(
                                labeledLine(
                                    context,
                                    R.string.event_label_direct_wake_reason,
                                    context.getString(
                                        R.string.event_power_button
                                    )
                                )
                            )

                            appendLine(
                                labeledLine(
                                    context,
                                    R.string.event_label_confidence,
                                    context.getString(
                                        R.string.event_confidence_system_wakelock
                                    )
                                )
                            )

                            appendLine(
                                labeledLine(
                                    context,
                                    R.string.event_label_time_offset,
                                    formatScreenRelationship(
                                        context,
                                        distance
                                    )
                                )
                            )

                            appendLine(
                                labeledLine(
                                    context,
                                    R.string.event_label_technical_reason,
                                    "PhoneWindowManager Power-Key"
                                )
                            )

                            append(
                                labeledLine(
                                    context,
                                    R.string.event_label_technical_tag,
                                    compactWakeLockTag(
                                        rawTag
                                    )
                                )
                            )

                            if (
                                existingNotificationLines
                                    .isNotBlank()
                            ) {
                                appendLine()
                                appendLine()
                                append(
                                    existingNotificationLines
                                )
                            }
                        }
                    )

                saveEvents(
                    context = context,
                    events = events,
                    nextId =
                        prefs.getLong(
                            KEY_NEXT_ID,
                            0L
                        )
                )

                return true
            }

            val sourceName =
                resolveWakeLockSource(
                    context = context,
                    packageName =
                        rawPackage
                )

            val wakeLockKind =
                classifyWakeLockTag(
                    context,
                    rawTag
                )

            val directWakeReasonKnown =
                LocalizedText.containsAny(
                    oldEvent.details,
                    context,
                    R.string.event_label_direct_wake_reason
                )

            /*
             * Nur Wakelocks vor SCREEN_ON dürfen als
             * mögliche Ursache bezeichnet werden.
             *
             * Zeitgleiche oder spätere Wakelocks sind
             * Begleitaktivitäten des Aufweckvorgangs.
             */
            val companionActivity =
                directWakeReasonKnown ||
                    distance >= 0L ||
                    isKnownFollowUpWakeLockTag(
                        rawTag
                    )

            val sectionTitle =
                if (companionActivity) {
                    context.getString(
                        R.string.event_section_companion_wakelock
                    )
                } else {
                    context.getString(
                        R.string.event_section_system_hint_possible_trigger
                    )
                }

            /*
             * Eine ungeklärte Ursache darf nur entfernt
             * werden, wenn wirklich ein möglicher Auslöser
             * vor SCREEN_ON gefunden wurde.
             */
            val baseDetails =
                if (companionActivity) {
                    oldEvent.details
                        .trimEnd()
                } else {
                    removeUnknownCauseLine(
                        context,
                        oldEvent.details
                    ).trimEnd()
                }

            /*
             * Gleiche technische Aktivität nicht mehrfach
             * an denselben Display-Wakeup anhängen.
             */
            if (
                LocalizedText.variants(
                    context,
                    R.string.event_label_technical_tag
                ).any { label ->
                    oldEvent.details.contains(
                        "$label " +
                            compactWakeLockTag(
                                rawTag
                            )
                    )
                }
            ) {
                return false
            }

            events[eventIndex] =
                oldEvent.copy(
                    details = buildString {
                        append(
                            baseDetails
                        )

                        if (
                            baseDetails.isNotBlank()
                        ) {
                            appendLine()
                            appendLine()
                        }

                        appendLine(
                            sectionTitle
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_source,
                                sourceName
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_kind,
                                wakeLockKind
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_time_offset,
                                formatScreenRelationship(
                                    context,
                                    distance
                                )
                            )
                        )

                        append(
                            labeledLine(
                                context,
                                R.string.event_label_technical_tag,
                                compactWakeLockTag(
                                    rawTag
                                )
                            )
                        )
                    }
                )

            saveEvents(
                context = context,
                events = events,
                nextId =
                    prefs.getLong(
                        KEY_NEXT_ID,
                        0L
                    )
            )

            return true
        }
    }

    fun attachWakeupAlarmHintToScreenOn(
        context: Context,
        screenOnTimestamp: Long,
        diagnostic: WakeupAlarmDiagnostic
    ): Boolean {
        if (
            diagnostic.error != null ||
            diagnostic.packageName.isNullOrBlank() ||
            diagnostic.tag.isNullOrBlank()
        ) {
            return false
        }

        val alarmTimestamp =
            diagnostic.triggerTimestampMillis
                ?: return false

        /*
         * Negativ: Alarm war vor SCREEN_ON.
         * Ein kleiner positiver Wert wird als nahezu
         * zeitgleich toleriert, aber nicht als Beweis.
         */
        val distance =
            alarmTimestamp - screenOnTimestamp

        if (
            distance <
                -WAKEUP_ALARM_BEFORE_WINDOW_MILLIS ||
            distance >
                WAKEUP_ALARM_AFTER_TOLERANCE_MILLIS
        ) {
            return false
        }

        synchronized(lock) {
            val prefs = preferences(context)

            val events = readEvents(
                context,
                prefs.getString(
                    KEY_EVENTS,
                    null
                )
            )

            val eventIndex =
                events.indexOfFirst { event ->
                    event.type == "SCREEN_ON" &&
                        kotlin.math.abs(
                            event.timestamp -
                                screenOnTimestamp
                        ) <=
                        SCREEN_EVENT_MATCH_WINDOW_MILLIS &&
                        !LocalizedText.containsAny(
                            event.details,
                            context,
                            R.string.event_label_wakeup_alarm_hint
                        )
                }

            if (eventIndex < 0) {
                return false
            }

            val oldEvent = events[eventIndex]

            val packageName =
                diagnostic.packageName
                    ?: return false

            val alarmTag =
                diagnostic.tag
                    ?: return false

            val sourceName =
                resolveWakeLockSource(
                    context = context,
                    packageName =
                        packageName
                )

            val directWakeReasonKnown =
                LocalizedText.containsAny(
                    oldEvent.details,
                    context,
                    R.string.event_label_direct_wake_reason
                )

            val relationshipLine =
                when {
                    directWakeReasonKnown ->
                        context.getString(
                            R.string.event_section_wakeup_alarm_companion
                        )

                    distance < 0L ->
                        context.getString(
                            R.string.event_section_wakeup_alarm_possible_trigger
                        )

                    distance == 0L ->
                        context.getString(
                            R.string.event_section_wakeup_alarm_simultaneous
                        )

                    else ->
                        context.getString(
                            R.string.event_section_wakeup_alarm_close_relation
                        )
                }

            val directionText =
                when {
                    distance < 0L ->
                        context.getString(
                            R.string.event_time_before_screen_on,
                            formatAge(context, -distance)
                        )

                    distance > 0L ->
                        context.getString(
                            R.string.event_time_after_screen_on,
                            formatAge(context, distance)
                        )

                    else ->
                        context.getString(
                            R.string.event_time_simultaneous_with_screen_on
                        )
                }

            val readableTag =
                alarmTag
                    .removePrefix("*walarm*:")
                    .ifBlank {
                        alarmTag
                    }

            val cleanedDetails =
                removeUnknownCauseLine(
                    context,
                    oldEvent.details
                )

            events[eventIndex] =
                oldEvent.copy(
                    details = buildString {
                        append(
                            cleanedDetails.trimEnd()
                        )

                        appendLine()
                        appendLine()

                        appendLine(
                            if (directWakeReasonKnown) {
                                context.getString(
                                    R.string.event_section_companion_wakeup_alarm
                                )
                            } else {
                                relationshipLine
                            }
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_source,
                                sourceName
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_time_offset,
                                directionText
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_alarm_wakeups_since_stats_start,
                                diagnostic.wakeCount.toString()
                            )
                        )

                        append(
                            labeledLine(
                                context,
                                R.string.event_label_technical_tag,
                                compactWakeLockTag(
                                    readableTag
                                )
                            )
                        )
                    }
                )

            saveEvents(
                context = context,
                events = events,
                nextId = prefs.getLong(
                    KEY_NEXT_ID,
                    0L
                )
            )

            return true
        }
    }

    fun attachBackgroundJobHintToScreenOn(
        context: Context,
        screenOnTimestamp: Long,
        diagnostic: BackgroundJobDiagnostic
    ): Boolean {
        if (
            diagnostic.error != null ||
            diagnostic.packageName.isNullOrBlank() ||
            diagnostic.serviceName.isNullOrBlank()
        ) {
            return false
        }

        val jobTimestamp =
            diagnostic.triggerTimestampMillis
                ?: return false

        /*
         * Ein Jobstart unmittelbar vor SCREEN_ON kann
         * zeitlich relevant sein. Er beweist aber nicht,
         * dass dieser Job das sichtbare Display aktiviert hat.
         */
        val distance =
            jobTimestamp - screenOnTimestamp

        if (
            distance <
                -BACKGROUND_JOB_BEFORE_WINDOW_MILLIS ||
            distance >
                BACKGROUND_JOB_AFTER_TOLERANCE_MILLIS
        ) {
            return false
        }

        synchronized(lock) {
            val prefs = preferences(context)

            val events = readEvents(
                context,
                prefs.getString(
                    KEY_EVENTS,
                    null
                )
            )

            val eventIndex =
                events.indexOfFirst { event ->
                    event.type == "SCREEN_ON" &&
                        kotlin.math.abs(
                            event.timestamp -
                                screenOnTimestamp
                        ) <=
                        SCREEN_EVENT_MATCH_WINDOW_MILLIS &&
                        !LocalizedText.containsAny(
                            event.details,
                            context,
                            R.string.event_label_background_job_hint
                        )
                }

            if (eventIndex < 0) {
                return false
            }

            val oldEvent = events[eventIndex]

            val packageName =
                diagnostic.packageName
                    ?: return false

            val serviceName =
                diagnostic.serviceName
                    ?: return false

            val sourceName =
                resolveWakeLockSource(
                    context = context,
                    packageName =
                        packageName
                )

            val directWakeReasonKnown =
                LocalizedText.containsAny(
                    oldEvent.details,
                    context,
                    R.string.event_label_direct_wake_reason
                )

            val directionText =
                when {
                    distance < 0L ->
                        context.getString(
                            R.string.event_time_before_screen_on,
                            formatAge(context, -distance)
                        )

                    distance > 0L ->
                        context.getString(
                            R.string.event_time_after_screen_on,
                            formatAge(context, distance)
                        )

                    else ->
                        context.getString(
                            R.string.event_time_simultaneous_with_screen_on
                        )
                }

            val startType =
                if (diagnostic.prioritized) {
                    context.getString(
                        R.string.event_start_type_prioritized
                    )
                } else {
                    context.getString(
                        R.string.event_start_type_regular
                    )
                }

            val cleanedDetails =
                removeUnknownCauseLine(
                    context,
                    oldEvent.details
                )

            events[eventIndex] =
                oldEvent.copy(
                    details = buildString {
                        append(
                            cleanedDetails.trimEnd()
                        )

                        appendLine()
                        appendLine()

                        appendLine(
                            if (directWakeReasonKnown) {
                                context.getString(
                                    R.string.event_section_companion_background_job
                                )
                            } else {
                                context.getString(
                                    R.string.event_section_background_job_time_relation
                                )
                            }
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_source,
                                sourceName
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_time_offset,
                                directionText
                            )
                        )

                        appendLine(
                            labeledLine(
                                context,
                                R.string.event_label_start_type,
                                startType
                            )
                        )

                        append(
                            labeledLine(
                                context,
                                R.string.event_label_service,
                                compactWakeLockTag(
                                    serviceName
                                )
                            )
                        )
                    }
                )

            saveEvents(
                context = context,
                events = events,
                nextId = prefs.getLong(
                    KEY_NEXT_ID,
                    0L
                )
            )

            return true
        }
    }

    private fun removeUnknownCauseLine(
        context: Context,
        details: String
    ): String {
        return details
            .lineSequence()
            .filterNot { line ->
                LocalizedText.equalsAny(
                    line.trim(),
                    context,
                    R.string.sleep_marker_cause_unknown
                )
            }
            .joinToString("\n")
            .trimEnd()
    }

    private fun labeledLine(
        context: Context,
        labelId: Int,
        value: String
    ): String {
        return context.getString(labelId) +
            " " +
            value
    }

    private fun unknownProximityLine(
        context: Context
    ): String {
        return labeledLine(
            context,
            R.string.event_label_proximity_sensor,
            context.getString(
                R.string.event_unknown
            )
        )
    }

    private fun replaceAnyVariant(
        text: String,
        context: Context,
        fromId: Int,
        toId: Int
    ): String {
        val replacement =
            context.getString(toId)

        return LocalizedText.variants(
            context,
            fromId
        ).fold(text) { result, variant ->
            result.replace(
                variant,
                replacement
            )
        }
    }

    private fun formatPrefixVariants(
        context: Context,
        formatId: Int
    ): List<String> {
        return LocalizedText.variants(
            context,
            formatId
        ).map { format ->
            format
                .substringBefore("%1\$s")
                .trimEnd()
        }
    }

    private fun extractSystemHintSections(
        context: Context,
        details: String
    ): String {
        val lines = details.lines()
        val result = mutableListOf<String>()
        var collecting = false

        lines.forEach { line ->
            val trimmed = line.trim()

            if (
                LocalizedText.startsWithAny(
                    trimmed,
                    context,
                    R.string.event_label_system_hint
                ) ||
                LocalizedText.startsWithAny(
                    trimmed,
                    context,
                    R.string.event_label_wakeup_alarm_hint
                ) ||
                LocalizedText.startsWithAny(
                    trimmed,
                    context,
                    R.string.event_label_background_job_hint
                ) ||
                LocalizedText.startsWithAny(
                    trimmed,
                    context,
                    R.string.event_label_companion_activity
                )
            ) {
                collecting = true

                if (
                    result.isNotEmpty() &&
                    result.last().isNotBlank()
                ) {
                    result.add("")
                }
            }

            if (collecting) {
                result.add(line)
            }
        }

        return result
            .joinToString("\n")
            .trim()
    }

    private fun isPowerKeyWakeLockTag(
        tag: String
    ): Boolean {
        return tag.contains(
            "PhoneWindowManager.mPowerKeyWakeLock",
            ignoreCase = true
        ) ||
            tag.contains(
                "mPowerKeyWakeLock",
                ignoreCase = true
            )
    }

    private fun isKnownFollowUpWakeLockTag(
        tag: String
    ): Boolean {
        return tag.contains(
            "UserPresent",
            ignoreCase = true
        ) ||
            tag.contains(
                "NotificationManagerService:post",
                ignoreCase = true
            ) ||
            tag.contains(
                "*launch*",
                ignoreCase = true
            ) ||
            tag.contains(
                "NfcService:",
                ignoreCase = true
            ) ||
            tag.contains(
                "*telephony",
                ignoreCase = true
            ) ||
            tag.contains(
                "dream:dream",
                ignoreCase = true
            )
    }

    private fun formatScreenRelationship(
        context: Context,
        distance: Long
    ): String {
        return when {
            distance < 0L ->
                context.getString(
                    R.string.event_time_before_screen_on,
                    formatAge(context, -distance)
                )

            distance > 0L ->
                context.getString(
                    R.string.event_time_after_screen_on,
                    formatAge(context, distance)
                )

            else ->
                context.getString(
                    R.string.event_time_simultaneous_with_screen_on
                )
        }
    }

    private data class WakeLockAssessment(
        val title: String
    )

    private fun assessWakeLockRelationship(
        context: Context,
        distance: Long,
        tag: String
    ): WakeLockAssessment {
        val isUserPresent =
            tag.contains(
                "UserPresent",
                ignoreCase = true
            )

        val isNotificationPost =
            tag.contains(
                "NotificationManagerService:post",
                ignoreCase = true
            )

        val isLaunch =
            tag.contains(
                "*launch*",
                ignoreCase = true
            )

        if (
            isUserPresent ||
            isNotificationPost ||
            (
                isLaunch &&
                distance >= 0L
            )
        ) {
            return WakeLockAssessment(
                title =
                    context.getString(
                        R.string.event_relation_likely_follow_up
                    )
            )
        }

        return when {
            distance < 0L ->
                WakeLockAssessment(
                    title = context.getString(
                        R.string.event_relation_possible_trigger
                    )
                )

            distance <= 1_000L ->
                WakeLockAssessment(
                    title =
                        context.getString(
                            R.string.event_relation_close
                        )
                )

            else ->
                WakeLockAssessment(
                    title =
                        context.getString(
                            R.string.event_relation_likely_follow_up
                        )
                )
        }
    }

    private fun classifyWakeLockTag(
        context: Context,
        tag: String
    ): String {
        return when {
            tag.contains(
                "UserPresent",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_kind_user_presence
                )

            tag.contains(
                "NetworkStats",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_kind_network_stats
                )

            tag.contains(
                "NotificationManagerService:post",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_kind_notification_processing
                )

            tag.contains(
                "*alarm*",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_kind_alarm
                )

            tag.contains(
                "*job*",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_kind_background_job
                )

            tag.contains(
                "*launch*",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_kind_app_launch
                )

            tag.contains(
                "AudioMix",
                ignoreCase = true
            ) ||
            tag.contains(
                "AudioIn",
                ignoreCase = true
            ) ||
            tag.contains(
                "ExoPlayer",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_kind_audio
                )

            tag.contains(
                "SyncManager",
                ignoreCase = true
            ) ||
            tag.contains(
                "*sync*",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_kind_sync
                )

            tag.contains(
                "Icing",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_kind_search_indexing
                )

            tag.contains(
                "PendingIntentClient",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_kind_scheduled_background_action
                )

            else ->
                context.getString(
                    R.string.event_kind_partial_wakelock
                )
        }
    }

    private fun resolveWakeLockSource(
        context: Context,
        packageName: String
    ): String {
        readableSystemSource(context, packageName)?.let {
            return "$it ($packageName)"
        }

        return runCatching {
            val applicationInfo =
                context.packageManager
                    .getApplicationInfo(
                        packageName,
                        0
                    )

            val appName =
                context.packageManager
                    .getApplicationLabel(
                        applicationInfo
                    )
                    .toString()
                    .trim()

            if (appName.isBlank()) {
                packageName
            } else {
                "$appName ($packageName)"
            }
        }.getOrDefault(packageName)
    }

    private fun readableSystemSource(
        context: Context,
        packageName: String
    ): String? {
        val value = packageName.lowercase(
            Locale.ROOT
        )

        return when {
            value == "android" ||
                value == "system" ->
                context.getString(
                    R.string.source_android_system
                )

            value.contains(
                "com.android.mms.service"
            ) ->
                context.getString(
                    R.string.source_android_mms_cellular_service
                )

            value.contains(
                "com.android.stk2"
            ) ->
                context.getString(
                    R.string.source_samsung_telephony_sim_service
                )

            value.contains(
                "com.android.phone"
            ) ->
                context.getString(
                    R.string.source_android_phone_service
                )

            value.contains(
                "com.android.providers.telephony"
            ) ->
                context.getString(
                    R.string.source_android_telephony_storage
                )

            value.contains(
                "com.google.android.ims"
            ) ->
                context.getString(
                    R.string.source_google_cellular_ims_service
                )

            value.contains(
                "com.android.systemui"
            ) ->
                context.getString(
                    R.string.source_android_system_ui
                )

            value.contains(
                "com.android.bluetooth"
            ) ->
                context.getString(
                    R.string.source_android_bluetooth_service
                )

            value.contains(
                "com.android.networkstack"
            ) ->
                context.getString(
                    R.string.source_android_networkstack_service
                )

            value.contains(
                "com.google.android.gms"
            ) ->
                context.getString(
                    R.string.source_google_play_services
                )

            else -> null
        }
    }

    private fun readableWakeReason(
        context: Context,
        reason: String,
        details: String
    ): String {
        return when {
            reason ==
                "WAKE_REASON_POWER_BUTTON" ->
                context.getString(
                    R.string.event_power_button
                )

            details.contains(
                "DoubleTap",
                ignoreCase = true
            ) ||
            details.contains(
                "blackGestureWake",
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.event_wake_reason_double_tap
                )

            reason ==
                "WAKE_REASON_GESTURE" ->
                context.getString(
                    R.string.event_wake_reason_gesture
                )

            reason ==
                "WAKE_REASON_LIFT" ->
                context.getString(
                    R.string.event_wake_reason_lift
                )

            reason ==
                "WAKE_REASON_PLUGGED_IN" ->
                context.getString(
                    R.string.event_wake_reason_plugged_in
                )

            reason ==
                "WAKE_REASON_WAKE_KEY" ->
                context.getString(
                    R.string.event_wake_reason_wake_key
                )

            reason ==
                "WAKE_REASON_WAKE_MOTION" ->
                context.getString(
                    R.string.event_wake_reason_motion
                )

            reason ==
                "WAKE_REASON_APPLICATION" ->
                context.getString(
                    R.string.event_wake_reason_application
                )

            else ->
                reason
        }
    }

    private fun compactWakeReasonDetails(
        details: String
    ): String {
        val value = details.trim()

        if (value.length <= 110) {
            return value
        }

        return value.take(107) + "…"
    }

    private fun compactWakeLockTag(
        tag: String
    ): String {
        if (tag.length <= 110) {
            return tag
        }

        return tag.take(107) + "…"
    }

    fun getEvents(context: Context): List<WakeEvent> {
        synchronized(lock) {
            return readEvents(
                context,
                preferences(context).getString(KEY_EVENTS, null)
            )
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            preferences(context).edit()
                .remove(KEY_EVENTS)
                .apply()
        }
    }

    fun saveNetworkSessionBaseline(
        context: Context,
        capturedAtMillis: Long,
        entries: List<NetworkTrafficEntry>
    ) {
        val array = JSONArray()

        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("uid", entry.uid)
                    put(
                        "packageName",
                        entry.packageName
                            ?: JSONObject.NULL
                    )
                    put(
                        "appLabel",
                        entry.appLabel
                            ?: JSONObject.NULL
                    )
                    put("rxBytes", entry.rxBytes)
                    put("txBytes", entry.txBytes)
                    put("totalBytes", entry.totalBytes)
                }
            )
        }

        preferences(context).edit()
            .putLong(
                KEY_NETWORK_BASELINE_TIMESTAMP,
                capturedAtMillis
            )
            .putString(
                KEY_NETWORK_BASELINE,
                array.toString()
            )
            .apply()
    }

    fun getNetworkSessionBaseline(
        context: Context
    ): NetworkSessionBaseline? {
        val prefs = preferences(context)

        val capturedAtMillis =
            prefs.getLong(
                KEY_NETWORK_BASELINE_TIMESTAMP,
                0L
            )

        val raw =
            prefs.getString(
                KEY_NETWORK_BASELINE,
                null
            )

        if (
            capturedAtMillis <= 0L ||
            raw.isNullOrBlank()
        ) {
            return null
        }

        val entries =
            runCatching {
                val array = JSONArray(raw)

                buildList {
                    for (
                        index in 0 until array.length()
                    ) {
                        val item =
                            array.optJSONObject(index)
                                ?: continue

                        val uid =
                            item.optInt("uid", -1)

                        if (uid < 0) {
                            continue
                        }

                        val packageName =
                            if (
                                item.isNull("packageName")
                            ) {
                                null
                            } else {
                                item.optString(
                                    "packageName"
                                ).ifBlank { null }
                            }

                        val appLabel =
                            if (
                                item.isNull("appLabel")
                            ) {
                                null
                            } else {
                                item.optString(
                                    "appLabel"
                                ).ifBlank { null }
                            }

                        val rxBytes =
                            item.optLong(
                                "rxBytes",
                                0L
                            ).coerceAtLeast(0L)

                        val txBytes =
                            item.optLong(
                                "txBytes",
                                0L
                            ).coerceAtLeast(0L)

                        val totalBytes =
                            runCatching {
                                Math.addExact(
                                    rxBytes,
                                    txBytes
                                )
                            }.getOrNull()
                                ?: continue

                        add(
                            NetworkTrafficEntry(
                                uid = uid,
                                packageName =
                                    packageName,
                                appLabel =
                                    appLabel,
                                rxBytes =
                                    rxBytes,
                                txBytes =
                                    txBytes,
                                totalBytes =
                                    totalBytes
                            )
                        )
                    }
                }
            }.getOrNull()
                ?: return null

        return NetworkSessionBaseline(
            capturedAtMillis =
                capturedAtMillis,
            entries =
                entries
        )
    }

    fun clearNetworkSessionBaseline(
        context: Context
    ) {
        preferences(context).edit()
            .remove(KEY_NETWORK_BASELINE)
            .remove(
                KEY_NETWORK_BASELINE_TIMESTAMP
            )
            .apply()
    }

    fun setMonitoring(
        context: Context,
        monitoring: Boolean
    ) {
        preferences(context).edit()
            .putBoolean(KEY_MONITORING, monitoring)
            .apply()
    }

    fun isMonitoring(context: Context): Boolean {
        return preferences(context)
            .getBoolean(KEY_MONITORING, false)
    }

    fun buildExport(context: Context): String {
        val events = getEvents(context)

        val formatter = SimpleDateFormat(
            "dd.MM.yyyy HH:mm:ss",
            Locale.getDefault()
        )

        return buildString {
            appendLine("wakelogs v${BuildConfig.VERSION_NAME} · dernikiausd")
            appendLine(
                context.getString(
                    R.string.event_export_title
                )
            )
            appendLine()
            appendLine(
                context.getString(
                    R.string.event_export_created,
                    formatter.format(Date())
                )
            )
            appendLine(
                context.getString(
                    R.string.event_export_monitoring,
                    if (isMonitoring(context)) {
                        context.getString(
                            R.string.event_export_monitoring_active
                        )
                    } else {
                        context.getString(
                            R.string.event_export_monitoring_stopped
                        )
                    }
                )
            )
            appendLine(
                context.getString(
                    R.string.event_export_stored_events,
                    events.size
                )
            )

            val exportHighlights =
                buildExportHighlights(context, events)

            val exportSummary =
                buildExportSummary(
                    context = context,
                    events = events,
                    highlights = exportHighlights
                )

            appendLine()
            appendLine(
                context.getString(
                    R.string.event_export_summary_heading
                )
            )

            exportSummary.forEach { line ->
                appendLine(line)
            }

            appendLine()
            appendLine(
                context.getString(
                    R.string.event_export_highlights_heading
                )
            )

            exportHighlights.forEach { highlight ->
                appendLine("• $highlight")
            }

            val parserDiagnostics =
                BackgroundWakeMonitor.diagnostics(
                    context
                )

            appendLine()
            appendLine(
                context.getString(
                    R.string.event_export_parser_diagnostics_heading
                )
            )
            appendLine(
                context.getString(
                    R.string.event_export_batterystats_lines,
                    parserDiagnostics.parsedLines
                )
            )
            appendLine(
                context.getString(
                    R.string.event_export_wake_reasons,
                    parserDiagnostics.wakeReasons
                )
            )
            appendLine(
                context.getString(
                    R.string.event_export_cpu_starts,
                    parserDiagnostics.runningStarts
                )
            )
            appendLine(
                context.getString(
                    R.string.event_export_wakelocks,
                    parserDiagnostics.wakeLocks
                )
            )
            appendLine(
                context.getString(
                    R.string.event_export_jobs,
                    parserDiagnostics.jobs
                )
            )
            appendLine(
                context.getString(
                    R.string.event_export_syncs,
                    parserDiagnostics.syncs
                )
            )
            appendLine(
                context.getString(
                    R.string.event_export_candidates_last_poll,
                    parserDiagnostics.candidates
                )
            )
            appendLine(
                context.getString(
                    R.string.event_export_cpu_wakeups_last_poll,
                    parserDiagnostics.eventsCreated
                )
            )

            if (
                parserDiagnostics.lastPollMillis > 0L
            ) {
                appendLine(
                    context.getString(
                        R.string.event_export_last_parser_run,
                        formatter.format(
                            Date(
                                parserDiagnostics
                                    .lastPollMillis
                            )
                        )
                    )
                )
            }

            val rawDiagnosticLines =
                BackgroundWakeMonitor
                    .rawDiagnosticLines(
                        context
                    )

            appendLine()
            appendLine(
                context.getString(
                    R.string.event_export_raw_data_heading
                )
            )

            val parserFoundFreshRawData =
                parserDiagnostics.parsedLines > 0 ||
                    parserDiagnostics.wakeReasons > 0 ||
                    parserDiagnostics.runningStarts > 0 ||
                    parserDiagnostics.wakeLocks > 0 ||
                    parserDiagnostics.jobs > 0 ||
                    parserDiagnostics.syncs > 0 ||
                    parserDiagnostics.candidates > 0 ||
                    parserDiagnostics.eventsCreated > 0

            if (!parserFoundFreshRawData) {
                appendLine(
                    context.getString(
                        R.string.event_export_no_new_raw_data
                    )
                )
            } else if (rawDiagnosticLines.isEmpty()) {
                appendLine(
                    context.getString(
                        R.string.event_export_no_raw_lines
                    )
                )
            } else {
                rawDiagnosticLines.forEach { line ->
                    appendLine(line)
                }
            }

            appendLine()
            appendLine(
                "--------------------------------------------------"
            )

            if (events.isEmpty()) {
                appendLine(
                    context.getString(
                        R.string.event_export_no_events
                    )
                )
            } else {
                events.forEach { event ->
                    appendLine(
                        formatter.format(
                            Date(event.timestamp)
                        )
                    )

                    appendLine(
                        "${eventLabel(context, event.type)} – " +
                            event.title
                    )

                    val exportDetails =
                        exportDetailsForEvent(context, event)

                    if (exportDetails.isNotBlank()) {
                        appendLine(exportDetails)
                    }

                    appendLine(
                        "--------------------------------------------------"
                    )
                }
            }
        }
    }

    private fun exportDetailsForEvent(
        context: Context,
        event: WakeEvent
    ): String {
        if (event.type != "EXPERT_SNAPSHOT") {
            return event.details
        }

        val lines =
            event.details
                .lineSequence()
                .map { it.trimEnd() }
                .toList()

        fun sectionItems(
            titleId: Int
        ): List<String> {
            val start =
                lines.indexOfFirst { line ->
                    LocalizedText.equalsAny(
                        line.trim(),
                        context,
                        titleId
                    )
                }

            if (start < 0) {
                return emptyList()
            }

            return lines
                .drop(start + 1)
                .takeWhile { line ->
                    val trimmed =
                        line.trim()

                    trimmed.isNotBlank() &&
                        !LocalizedText.equalsAny(
                            trimmed,
                            context,
                            R.string.shizuku_snapshot_section_location
                        ) &&
                        !LocalizedText.equalsAny(
                            trimmed,
                            context,
                            R.string.shizuku_snapshot_section_sensors
                        ) &&
                        !LocalizedText.equalsAny(
                            trimmed,
                            context,
                            R.string.shizuku_snapshot_section_network
                        ) &&
                        !LocalizedText.startsWithAny(
                            trimmed,
                            context,
                            R.string.shizuku_snapshot_note_label
                        )
                }
                .filter { line ->
                    line.trim().startsWith("•")
                }
        }

        val locationItems =
            sectionItems(
                R.string.shizuku_snapshot_section_location
            )

        val sensorItems =
            sectionItems(
                R.string.shizuku_snapshot_section_sensors
            )

        val networkItems =
            sectionItems(
                R.string.shizuku_snapshot_section_network
            )

        val locationText =
            if (locationItems.isEmpty()) {
                context.getString(
                    R.string.event_expert_no_notable_hints
                )
            } else {
                locationItems
                    .take(3)
                    .joinToString(", ") { item ->
                        item.removePrefix("•").trim()
                    }
            }

        val sensorText =
            if (sensorItems.isEmpty()) {
                context.getString(
                    R.string.event_expert_no_notable_hints
                )
            } else {
                sensorItems
                    .take(3)
                    .joinToString(", ") { item ->
                        item.removePrefix("•").trim()
                    }
            }

        val networkText =
            if (networkItems.isEmpty()) {
                context.getString(
                    R.string.event_expert_no_notable_hints
                )
            } else {
                networkItems
                    .take(3)
                    .joinToString(", ") { item ->
                        item.removePrefix("•").trim()
                    }
            }

        return buildString {
            appendLine(
                context.getString(
                    R.string.service_snapshot_trigger,
                    context.getString(
                        R.string.service_snapshot_reason_screen_on
                    )
                )
            )
            appendLine(
                context.getString(
                    R.string.service_snapshot_source_compact
                )
            )
            appendLine()
            appendLine(
                context.getString(
                    R.string.event_expert_context_heading
                )
            )
            appendLine(
                context.getString(
                    R.string.event_expert_location_line,
                    locationText
                )
            )
            appendLine(
                context.getString(
                    R.string.event_expert_sensors_line,
                    sensorText
                )
            )
            appendLine(
                context.getString(
                    R.string.event_expert_network_line,
                    networkText
                )
            )
            appendLine()
            append(
                context.getString(
                    R.string.event_expert_export_note
                )
            )
        }
    }

    private fun saveEvents(
        context: Context,
        events: List<WakeEvent>,
        nextId: Long
    ) {
        val jsonArray = JSONArray()

        events.take(MAX_EVENTS).forEach { event ->
            jsonArray.put(
                JSONObject().apply {
                    put("id", event.id)
                    put("timestamp", event.timestamp)
                    put("type", event.type)
                    put("title", event.title)
                    put("details", event.details)
                }
            )
        }

        preferences(context).edit()
            .putLong(KEY_NEXT_ID, nextId)
            .putString(KEY_EVENTS, jsonArray.toString())
            .apply()
    }

    private fun buildExportSummary(
        context: Context,
        events: List<WakeEvent>,
        highlights: List<String>
    ): List<String> {
        val summary =
            mutableListOf<String>()

        val screenOnEvents =
            events.filter { event ->
                event.type == "SCREEN_ON"
            }

        val powerButtonWakeups =
            screenOnEvents.count { event ->
                event.details.contains(
                    "WAKE_REASON_POWER_BUTTON",
                    ignoreCase = true
                ) ||
                    LocalizedText.containsAny(
                        event.details,
                        context,
                        R.string.event_power_button,
                        ignoreCase = true
                    )
            }

        when {
            screenOnEvents.isEmpty() ->
                summary.add(
                    context.getString(
                        R.string.event_summary_no_screen_wakeups
                    )
                )

            powerButtonWakeups == screenOnEvents.size ->
                summary.add(
                    context.getString(
                        R.string.event_summary_not_woken_by_apps
                    )
                )

            powerButtonWakeups > 0 ->
                summary.add(
                    context.getString(
                        R.string.event_summary_some_power_button
                    )
                )

            else ->
                summary.add(
                    context.getString(
                        R.string.event_summary_no_clear_power_button
                    )
                )
        }

        if (
            screenOnEvents.isNotEmpty() &&
            powerButtonWakeups == screenOnEvents.size
        ) {
            summary.add(
                context.getString(
                    R.string.event_summary_all_power_button
                )
            )
        }

        val frequentSourcesLine =
            highlights.firstOrNull { line ->
                line.startsWith(
                    context.getString(
                        R.string.event_highlight_frequent_sources_label
                    )
                )
            }

        when {
            frequentSourcesLine?.contains(
                context.getString(
                    R.string.event_source_radio_network
                ),
                ignoreCase = true
            ) == true ->
                summary.add(
                    context.getString(
                        R.string.event_summary_much_radio_network
                    )
                )

            frequentSourcesLine != null ->
                summary.add(
                    context.getString(
                        R.string.event_summary_recurring_technical
                    )
                )
        }

        val longestWakeupLine =
            highlights.firstOrNull { line ->
                line.startsWith(
                    context.getString(
                        R.string.event_highlight_longest_cpu_wakeup_label
                    )
                )
            }

        if (longestWakeupLine != null) {
            summary.add(
                context.getString(
                    R.string.event_summary_most_notable_cpu_wakeup,
                    longestWakeupLine
                        .removePrefix(
                            context.getString(
                                R.string.event_highlight_longest_cpu_wakeup_label
                            )
                        )
                        .trimStart()
                )
            )
        }

        val lockGlowWakeups =
            screenOnEvents.count { event ->
                event.details.contains(
                    "LockGlow:NotificationWakeLock",
                    ignoreCase = true
                ) ||
                    event.details.contains(
                        "LockGlow",
                        ignoreCase = true
                    )
            }

        if (lockGlowWakeups > 0) {
            summary.add(
                context.resources.getQuantityString(
                    R.plurals.event_summary_lockglow_wakeups,
                    lockGlowWakeups,
                    lockGlowWakeups
                )
            )
        }

        val notificationCount =
            events.count { event ->
                event.type == "NOTIFICATION"
            }

        if (notificationCount > 0) {
            summary.add(
                context.getString(
                    R.string.event_summary_notifications_not_main_trigger
                )
            )
        }

        if (summary.isEmpty()) {
            summary.add(
                context.getString(
                    R.string.event_no_special_patterns
                )
            )
        }

        return summary
    }

    private fun buildExportHighlights(
        context: Context,
        events: List<WakeEvent>
    ): List<String> {
        val highlights =
            mutableListOf<String>()

        val cpuEvents =
            events.filter { event ->
                event.type == "CPU_WAKEUP"
            }

        val longestCpuWakeup =
            cpuEvents.mapNotNull { event ->
                parseCpuWakeDurationMillis(
                    context,
                    event.details
                )?.let { durationMillis ->
                    event to durationMillis
                }
            }.maxByOrNull { pair ->
                pair.second
            }

        if (longestCpuWakeup != null) {
            highlights.add(
                context.getString(
                    R.string.event_highlight_longest_cpu_wakeup,
                    cleanExportTitle(
                        context,
                        longestCpuWakeup.first.title
                    ),
                    formatExportDuration(
                        longestCpuWakeup.second
                    )
                )
            )
        }

        val frequentSources =
            events.flatMap { event ->
                extractExportSources(context, event)
            }
                .map { source ->
                    compactExportSource(context, source)
                }
                .filter { source ->
                    source.isNotBlank() &&
                        LocalizedText.variants(
                            context,
                            R.string.bg_possible_source_ambiguous
                        ).none { variant ->
                            source.equals(
                                variant,
                                ignoreCase = true
                            )
                        } &&
                        LocalizedText.variants(
                            context,
                            R.string.event_unknown_source
                        ).none { variant ->
                            source.equals(
                                variant,
                                ignoreCase = true
                            )
                        }
                }
                .groupingBy { source ->
                    source
                }
                .eachCount()
                .toList()
                .sortedWith(
                    compareByDescending<Pair<String, Int>> {
                        it.second
                    }.thenBy {
                        it.first
                    }
                )
                .take(3)

        if (frequentSources.isNotEmpty()) {
            highlights.add(
                context.getString(
                    R.string.event_highlight_frequent_sources,
                    frequentSources.joinToString(", ") {
                            pair ->
                        "${pair.first} (${pair.second}×)"
                    }
                )
            )
        }

        val screenOnEvents =
            events.filter { event ->
                event.type == "SCREEN_ON"
            }

        val powerButtonWakeups =
            screenOnEvents.count { event ->
                event.details.contains(
                    "WAKE_REASON_POWER_BUTTON",
                    ignoreCase = true
                ) ||
                    LocalizedText.containsAny(
                        event.details,
                        context,
                        R.string.event_power_button,
                        ignoreCase = true
                    )
            }

        if (screenOnEvents.isNotEmpty()) {
            if (
                powerButtonWakeups ==
                screenOnEvents.size
            ) {
                highlights.add(
                    context.getString(
                        R.string.event_highlight_screen_wakeups_all_power_button,
                        screenOnEvents.size
                    )
                )
            } else if (powerButtonWakeups > 0) {
                highlights.add(
                    context.getString(
                        R.string.event_highlight_screen_wakeups_some_power_button,
                        powerButtonWakeups,
                        screenOnEvents.size
                    )
                )
            } else {
                highlights.add(
                    context.getString(
                        R.string.event_highlight_screen_wakeups_no_power_button,
                        screenOnEvents.size
                    )
                )
            }
        }

        val notificationCount =
            events.count { event ->
                event.type == "NOTIFICATION"
            }

        if (notificationCount > 0) {
            highlights.add(
                context.getString(
                    R.string.event_highlight_notifications,
                    notificationCount
                )
            )
        }

        val unclearCpuWakeups =
            cpuEvents.count { event ->
                LocalizedText.containsAny(
                    event.details,
                    context,
                    R.string.bg_possible_source_ambiguous,
                    ignoreCase = true
                )
            }

        if (unclearCpuWakeups > 0) {
            highlights.add(
                context.getString(
                    R.string.event_highlight_unattributed_cpu_wakeups,
                    unclearCpuWakeups
                )
            )
        }

        if (highlights.isEmpty()) {
            highlights.add(
                context.getString(
                    R.string.event_no_special_patterns
                )
            )
        }

        return highlights
    }

    private fun parseCpuWakeDurationMillis(
        context: Context,
        details: String
    ): Long? {
        val labels =
            formatPrefixVariants(
                context,
                R.string.bg_detail_cpu_awake_time
            )

        val line =
            details.lines().firstOrNull { value ->
                labels.any { label ->
                    value.trim().startsWith(
                        label
                    )
                }
            } ?: return null

        val value =
            line.substringAfter(":")
                .trim()

        if (
            LocalizedText.containsAny(
                value,
                context,
                R.string.bg_duration_not_determinable,
                ignoreCase = true
            )
        ) {
            return null
        }

        val minutesMatch =
            Regex(
                """(\d+)\s+(\S+)\s+(\d+)"""
            ).find(value)

        if (
            minutesMatch != null &&
            LocalizedText.variants(
                context,
                R.string.event_minutes_word
            ).any { word ->
                word.equals(
                    minutesMatch.groupValues[2],
                    ignoreCase = true
                )
            }
        ) {
            val minutes =
                minutesMatch.groupValues[1]
                    .toLongOrNull()
                    ?: return null

            val seconds =
                minutesMatch.groupValues[3]
                    .toLongOrNull()
                    ?: return null

            return minutes * 60_000L +
                seconds * 1_000L
        }

        val number =
            Regex(
                """(\d+(?:[,.]\d+)?)"""
            ).find(value)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(",", ".")
                ?.toDoubleOrNull()
                ?: return null

        return when {
            value.contains("ms") ->
                number.roundToLong()

            LocalizedText.containsAny(
                value,
                context,
                R.string.event_seconds_word,
                ignoreCase = true
            ) ->
                (number * 1000.0).roundToLong()

            else ->
                null
        }
    }

    private fun formatExportDuration(
        milliseconds: Long
    ): String {
        return if (milliseconds >= 1000L) {
            String.format(
                Locale.getDefault(),
                "%.1f s",
                milliseconds / 1000.0
            )
        } else {
            "$milliseconds ms"
        }
    }

    private fun chooseExportWakeupTitle(
        context: Context,
        event: WakeEvent
    ): String {
        val title =
            event.title

        val titleLooksUnknown =
            LocalizedText.containsAny(
                title,
                context,
                R.string.bg_title_cpu_woken_background,
                ignoreCase = true
            ) ||
                LocalizedText.containsAny(
                    title,
                    context,
                    R.string.event_unknown_source,
                    ignoreCase = true
                ) ||
                LocalizedText.containsAny(
                    title,
                    context,
                    R.string.event_not_clearly,
                    ignoreCase = true
                )

        if (
            titleLooksUnknown &&
            looksLikeNetworkRadioSource(
                event.details
            )
        ) {
            return context.getString(
                R.string.event_source_radio_network
            )
        }

        if (
            title.contains(
                "LockGlow",
                ignoreCase = true
            ) ||
            event.details.contains(
                "LockGlow:NotificationWakeLock",
                ignoreCase = true
            )
        ) {
            return "LockGlow"
        }

        return title
    }

    private fun looksLikeNetworkRadioSource(
        text: String
    ): Boolean {
        val lower =
            text.lowercase(Locale.getDefault())

        return lower.contains("ipa_client") ||
            lower.contains("rmnet") ||
            lower.contains("qrtr") ||
            lower.contains("ipcc_") ||
            lower.contains("qcom_rx") ||
            lower.contains("wlan_wake_irq") ||
            lower.contains("rilj_ack_wl") ||
            lower.contains("telephony-radio") ||
            lower.contains("cellular") ||
            lower.contains("radio")
    }

    private fun cleanExportTitle(
        context: Context,
        title: String
    ): String {
        return removeAnyFormatPrefix(
            title,
            context,
            R.string.bg_title_cpu_wakeup_source
        )
            .let { value ->
                LocalizedText.removeAnyPrefix(
                    value,
                    context,
                    R.string.bg_title_cpu_woken_background
                )
            }
            .ifBlank {
                context.getString(
                    R.string.event_unknown_source
                )
            }
    }

    private fun removeAnyFormatPrefix(
        text: String,
        context: Context,
        formatId: Int
    ): String {
        val prefix =
            formatPrefixVariants(
                context,
                formatId
            ).firstOrNull { value ->
                value.isNotEmpty() &&
                    text.startsWith(value)
            }
                ?: return text

        return text
            .removePrefix(prefix)
            .trimStart()
    }

    private fun extractExportSources(
        context: Context,
        event: WakeEvent
    ): List<String> {
        val possibleSourceLabels =
            formatPrefixVariants(
                context,
                R.string.bg_detail_possible_source
            )

        val sources =
            mutableListOf<String>()

        var inTechnicalSources =
            false

        event.details.lines().forEach { rawLine ->
            val line =
                rawLine.trim()

            if (
                LocalizedText.equalsAny(
                    line,
                    context,
                    R.string.bg_detail_technical_sources
                )
            ) {
                inTechnicalSources = true
                return@forEach
            }

            if (inTechnicalSources) {
                if (line.startsWith("• ")) {
                    sources.add(
                        line.removePrefix("• ")
                    )
                    return@forEach
                }

                if (line.isBlank()) {
                    inTechnicalSources = false
                }
            }

            when {
                possibleSourceLabels.any { label ->
                    line.startsWith(label)
                } ->
                    sources.add(
                        line.substringAfter(":").trim()
                    )

                LocalizedText.startsWithAny(
                    line,
                    context,
                    R.string.event_label_source
                ) ->
                    sources.add(
                        line.substringAfter(":").trim()
                    )

                LocalizedText.startsWithAny(
                    line,
                    context,
                    R.string.event_label_technical_tag
                ) ->
                    sources.add(
                        line.substringAfter(":").trim()
                    )
            }
        }

        return sources
    }

    private fun compactExportSource(
        context: Context,
        source: String
    ): String {
        val cleaned =
            source
                .trim()
                .trimStart('•')
                .trim()
                .removeSurrounding("\"")
                .removeSuffix(",...")
                .removeSuffix("...")
                .removeSuffix(",")
                .trim()

        val lower =
            cleaned.lowercase(Locale.getDefault())

        return when {
            lower.contains("com.android.stk2") ||
                lower.contains("telephony-sem-radio") ||
                lower.contains("rilj_ack_wl") ->
                context.getString(
                    R.string.source_samsung_telephony_sim_service
                )

            lower.contains("fmm-acquirewakelock") ||
                lower.contains("offlinefindtask") ->
                context.getString(
                    R.string.source_samsung_offline_finding
                )

            lower.contains("com.android.phone") ||
                LocalizedText.containsAny(
                    lower,
                    context,
                    R.string.bg_source_android_phone_service,
                    ignoreCase = true
                ) ||
                lower.contains("*telephony-radio*") ->
                context.getString(
                    R.string.source_android_phone_service
                )

            lower.contains("ipa_client") ||
                lower.contains("rmnet") ||
                lower.contains("qrtr") ||
                lower.contains("ipcc_") ||
                lower.contains("qcom_rx") ||
                lower.contains("wlan_wake_irq") ||
                lower.contains("iwlan") ||
                lower.contains("rilj_ack_wl") ||
                lower.contains("cellular") ||
                lower.contains("radio") ->
                context.getString(
                    R.string.event_source_radio_network
                )

            lower.contains("time_tick") ->
                "Android TIME_TICK"

            lower.contains("systemui.aod") ||
                lower.contains("aod.hide_time") ||
                lower.contains("oplusscreenoffgesture") ->
                context.getString(
                    R.string.source_android_system_ui
                )

            lower.contains("com.google.android.gms") ||
                LocalizedText.containsAny(
                    lower,
                    context,
                    R.string.bg_source_google_play_services,
                    ignoreCase = true
                ) ||
                lower.contains("gms_scheduler") ||
                lower.contains("callbackrunner") ||
                lower.contains("cmwakelock") ||
                lower.contains("gcoreflp") ||
                lower.contains("networklocation") ||
                lower.contains("fusedlocation") ||
                lower.contains("gnsslocationprovider") ||
                lower.contains("geofencer") ||
                lower.contains("gmsalarm") ||
                lower.contains("com.google.android.location") ||
                lower.contains("activity_detection") ->
                context.getString(
                    R.string.event_source_google_services
                )

            lower.contains("whatsapp") ->
                "WhatsApp"

            lower.contains("oplus") ||
                lower.contains("oneplus") ||
                lower.contains("athena") ->
                context.getString(
                    R.string.event_source_oneplus_system
                )

            else ->
                removeAnyFormatPrefix(
                    cleaned,
                    context,
                    R.string.bg_title_cpu_wakeup_source
                )
                    .let { value ->
                        removeAnyTypePrefix(
                            value,
                            context,
                            R.string.bg_type_wakeup_alarm
                        )
                    }
                    .let { value ->
                        removeAnyTypePrefix(
                            value,
                            context,
                            R.string.bg_type_partial_wakelock
                        )
                    }
                    .substringBefore("/androidx.work.impl")
                    .substringBefore(":android")
                    .substringBefore(" (")
                    .take(80)
                    .trim()
        }
    }



    private fun removeAnyTypePrefix(
        text: String,
        context: Context,
        typeId: Int
    ): String {
        val prefix =
            LocalizedText.variants(
                context,
                typeId
            )
                .map { type ->
                    "$type: "
                }
                .firstOrNull { value ->
                    text.startsWith(value)
                }
                ?: return text

        return text.removePrefix(prefix)
    }

    private fun formatAge(
        context: Context,
        milliseconds: Long
    ): String {
        return context.getString(
            R.string.event_age_seconds,
            milliseconds / 1000.0
        )
    }

    private fun eventLabel(
        context: Context,
        type: String
    ): String {
        val labelId =
            when (type) {
                "MONITOR_START" -> R.string.event_type_monitor
                "MONITOR_STOP" -> R.string.event_type_monitor
                "NETWORK_SESSION" ->
                    R.string.event_type_network_session
                "SCREEN_ON" -> R.string.event_type_screen_on
                "SCREEN_OFF" -> R.string.event_type_screen_off
                "POWER_CONNECTED" -> R.string.event_type_power_connected
                "POWER_DISCONNECTED" -> R.string.event_type_power_disconnected
                "USB_ATTACHED" -> R.string.event_type_usb_attached
                "USB_DETACHED" -> R.string.event_type_usb_detached
                "NOTIFICATION" -> R.string.event_type_notification
                "CPU_WAKEUP" -> R.string.event_type_cpu_wakeup
                else -> return type
            }

        return context.getString(labelId)
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private fun readEvents(
        context: Context,
        rawJson: String?
    ): MutableList<WakeEvent> {
        if (rawJson.isNullOrBlank()) {
            return mutableListOf()
        }

        return runCatching {
            val array = JSONArray(rawJson)
            val result = mutableListOf<WakeEvent>()

            for (index in 0 until array.length()) {
                val item =
                    array.optJSONObject(index) ?: continue

                result.add(
                    WakeEvent(
                        id = item.optLong(
                            "id",
                            index.toLong()
                        ),
                        timestamp = item.optLong(
                            "timestamp",
                            System.currentTimeMillis()
                        ),
                        type = item.optString(
                            "type",
                            "UNKNOWN"
                        ),
                        title = item.optString(
                            "title",
                            context.getString(
                                R.string.event_unknown_event
                            )
                        ),
                        details = item.optString(
                            "details",
                            ""
                        )
                    )
                )
            }

            result
        }.getOrElse {
            mutableListOf()
        }
    }
}
