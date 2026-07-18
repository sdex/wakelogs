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
                prefs.getString(KEY_EVENTS, null)
            )

            val eventIndex = events.indexOfFirst { event ->
                if (event.type != "SCREEN_ON") {
                    return@indexOfFirst false
                }

                val distance =
                    notification.timestamp - event.timestamp

                distance in 0..LATE_NOTIFICATION_WINDOW_MILLIS &&
                    event.details.contains(
                        "Ursache: noch unbekannt"
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
                    "hoch"
                } else {
                    "mittel"
                }

            val proximityLine = oldEvent.details
                .lineSequence()
                .firstOrNull {
                    it.startsWith("Näherungssensor:")
                }
                ?: "Näherungssensor: unbekannt"

            val preservedSystemHints =
                extractSystemHintSections(
                    oldEvent.details
                )

            events[eventIndex] = oldEvent.copy(
                details = buildString {
                    appendLine(proximityLine)

                    appendLine(
                        "Nachträglich erkannte Ursache: " +
                            notification.appName
                    )

                    appendLine(
                        "Sicherheit: $confidence"
                    )

                    append(
                        "Hinweis kam ${formatAge(distance)} " +
                            "nach Display an"
                    )

                    if (notification.title.isNotBlank()) {
                        appendLine()
                        append(
                            "Titel: ${notification.title}"
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
                        !event.details.contains(
                            "Direkter Aufweckgrund:"
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
                        line.startsWith(
                            "Näherungssensor:"
                        )
                    }
                    ?: "Näherungssensor: unbekannt"

            val preservedHints =
                extractSystemHintSections(
                    oldEvent.details
                )
                    .replace(
                        "Systemhinweis: möglicher Auslöser",
                        "Begleitaktivität: Wakelock"
                    )
                    .replace(
                        "Wakeup-Alarm-Hinweis: möglicher Auslöser",
                        "Begleitaktivität: Wakeup-Alarm"
                    )

            events[eventIndex] =
                oldEvent.copy(
                    details = buildString {
                        appendLine(
                            proximityLine
                        )

                        appendLine(
                            "Direkter Aufweckgrund: Power-Taste"
                        )

                        appendLine(
                            "Sicherheit: direkt aus Samsung BatteryStats"
                        )

                        appendLine(
                            "Zeitabstand: zeitgleich mit Display an"
                        )

                        appendLine(
                            "Technischer Grund: " +
                                technicalReason
                        )

                        technicalTag
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let { tag ->
                                append(
                                    "Technischer Tag: " +
                                        compactWakeLockTag(
                                            tag
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
                        !event.details.contains(
                            "Direkter Aufweckgrund:"
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
                        line.startsWith(
                            "Näherungssensor:"
                        )
                    }
                    ?: "Näherungssensor: unbekannt"

            val existingSystemHints =
                extractSystemHintSections(
                    oldEvent.details
                )

            val directionText =
                when {
                    distance < 0L ->
                        "${formatAge(-distance)} vor Display an"

                    distance > 0L ->
                        "${formatAge(distance)} nach Display an"

                    else ->
                        "zeitgleich mit Display an"
                }

            val technicalReason =
                diagnostic.reason
                    ?: "unbekannt"

            val technicalDetails =
                diagnostic.details
                    ?.takeIf { it.isNotBlank() }
                    ?: "keine"

            events[eventIndex] =
                oldEvent.copy(
                    details = buildString {
                        appendLine(proximityLine)

                        appendLine(
                            "Direkter Aufweckgrund: " +
                                readableWakeReason(
                                    reason =
                                        technicalReason,
                                    details =
                                        technicalDetails
                                )
                        )

                        appendLine(
                            "Sicherheit: direkt vom PowerManager"
                        )

                        appendLine(
                            "Zeitabstand: $directionText"
                        )

                        appendLine(
                            "Technischer Grund: " +
                                technicalReason
                        )

                        append(
                            "Details: " +
                                compactWakeReasonDetails(
                                    technicalDetails
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
                    ?: "unbekannt"

            val rawTag =
                diagnostic.lastTag
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "unbekannt"

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
                    oldEvent.details.contains(
                        "Direkter Aufweckgrund:"
                    )
                ) {
                    return false
                }

                val cleanedDetails =
                    removeUnknownCauseLine(
                        oldEvent.details
                    )

                val proximityLine =
                    oldEvent.details
                        .lineSequence()
                        .firstOrNull { line ->
                            line.startsWith(
                                "Näherungssensor:"
                            )
                        }
                        ?: "Näherungssensor: unbekannt"

                val existingNotificationLines =
                    cleanedDetails
                        .lineSequence()
                        .filterNot { line ->
                            line.startsWith(
                                "Näherungssensor:"
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
                                "Direkter Aufweckgrund: Power-Taste"
                            )

                            appendLine(
                                "Sicherheit: direkter System-Wakelock"
                            )

                            appendLine(
                                "Zeitabstand: " +
                                    formatScreenRelationship(
                                        distance
                                    )
                            )

                            appendLine(
                                "Technischer Grund: " +
                                    "PhoneWindowManager Power-Key"
                            )

                            append(
                                "Technischer Tag: " +
                                    compactWakeLockTag(
                                        rawTag
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
                    rawTag
                )

            val directWakeReasonKnown =
                oldEvent.details.contains(
                    "Direkter Aufweckgrund:"
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
                    "Begleitaktivität: Wakelock"
                } else {
                    "Systemhinweis: möglicher Auslöser"
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
                        oldEvent.details
                    ).trimEnd()
                }

            /*
             * Gleiche technische Aktivität nicht mehrfach
             * an denselben Display-Wakeup anhängen.
             */
            if (
                oldEvent.details.contains(
                    "Technischer Tag: " +
                        compactWakeLockTag(
                            rawTag
                        )
                )
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
                            "Quelle: $sourceName"
                        )

                        appendLine(
                            "Art: $wakeLockKind"
                        )

                        appendLine(
                            "Zeitabstand: " +
                                formatScreenRelationship(
                                    distance
                                )
                        )

                        append(
                            "Technischer Tag: " +
                                compactWakeLockTag(
                                    rawTag
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
                        !event.details.contains(
                            "Wakeup-Alarm-Hinweis:"
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
                oldEvent.details.contains(
                    "Direkter Aufweckgrund:"
                )

            val relationship =
                when {
                    directWakeReasonKnown ->
                        "Begleitaktivität"

                    distance < 0L ->
                        "möglicher Auslöser"

                    distance == 0L ->
                        "zeitgleiches Systemereignis"

                    else ->
                        "enger zeitlicher Zusammenhang"
                }

            val directionText =
                when {
                    distance < 0L ->
                        "${formatAge(-distance)} vor Display an"

                    distance > 0L ->
                        "${formatAge(distance)} nach Display an"

                    else ->
                        "zeitgleich mit Display an"
                }

            val readableTag =
                alarmTag
                    .removePrefix("*walarm*:")
                    .ifBlank {
                        alarmTag
                    }

            val cleanedDetails =
                removeUnknownCauseLine(
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
                                "Begleitaktivität: Wakeup-Alarm"
                            } else {
                                "Wakeup-Alarm-Hinweis: " +
                                    relationship
                            }
                        )

                        appendLine(
                            "Quelle: $sourceName"
                        )

                        appendLine(
                            "Zeitabstand: " +
                                directionText
                        )

                        appendLine(
                            "Alarm-Wakeups seit Statistikstart: " +
                                diagnostic.wakeCount
                        )

                        append(
                            "Technischer Tag: " +
                                compactWakeLockTag(
                                    readableTag
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
                        !event.details.contains(
                            "Hintergrundjob-Hinweis:"
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
                oldEvent.details.contains(
                    "Direkter Aufweckgrund:"
                )

            val directionText =
                when {
                    distance < 0L ->
                        "${formatAge(-distance)} vor Display an"

                    distance > 0L ->
                        "${formatAge(distance)} nach Display an"

                    else ->
                        "zeitgleich mit Display an"
                }

            val startType =
                if (diagnostic.prioritized) {
                    "priorisiert"
                } else {
                    "regulär"
                }

            val cleanedDetails =
                removeUnknownCauseLine(
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
                                "Begleitaktivität: Hintergrundjob"
                            } else {
                                "Hintergrundjob-Hinweis: " +
                                    "zeitlicher Zusammenhang"
                            }
                        )

                        appendLine(
                            "Quelle: $sourceName"
                        )

                        appendLine(
                            "Zeitabstand: $directionText"
                        )

                        appendLine(
                            "Starttyp: $startType"
                        )

                        append(
                            "Dienst: " +
                                compactWakeLockTag(
                                    serviceName
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
        details: String
    ): String {
        return details
            .lineSequence()
            .filterNot { line ->
                line.trim() ==
                    "Ursache: noch unbekannt"
            }
            .joinToString("\n")
            .trimEnd()
    }

    private fun extractSystemHintSections(
        details: String
    ): String {
        val lines = details.lines()
        val result = mutableListOf<String>()
        var collecting = false

        lines.forEach { line ->
            val trimmed = line.trim()

            if (
                trimmed.startsWith(
                    "Systemhinweis:"
                ) ||
                trimmed.startsWith(
                    "Wakeup-Alarm-Hinweis:"
                ) ||
                trimmed.startsWith(
                    "Hintergrundjob-Hinweis:"
                ) ||
                trimmed.startsWith(
                    "Begleitaktivität:"
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
        distance: Long
    ): String {
        return when {
            distance < 0L ->
                "${formatAge(-distance)} vor Display an"

            distance > 0L ->
                "${formatAge(distance)} nach Display an"

            else ->
                "zeitgleich mit Display an"
        }
    }

    private data class WakeLockAssessment(
        val title: String
    )

    private fun assessWakeLockRelationship(
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
                    "wahrscheinliches Folgeereignis"
            )
        }

        return when {
            distance < 0L ->
                WakeLockAssessment(
                    title = "möglicher Auslöser"
                )

            distance <= 1_000L ->
                WakeLockAssessment(
                    title =
                        "enger zeitlicher Zusammenhang"
                )

            else ->
                WakeLockAssessment(
                    title =
                        "wahrscheinliches Folgeereignis"
                )
        }
    }

    private fun classifyWakeLockTag(
        tag: String
    ): String {
        return when {
            tag.contains(
                "UserPresent",
                ignoreCase = true
            ) ->
                "Reaktion auf Benutzerpräsenz"

            tag.contains(
                "NetworkStats",
                ignoreCase = true
            ) ->
                "Netzwerkstatistik"

            tag.contains(
                "NotificationManagerService:post",
                ignoreCase = true
            ) ->
                "Benachrichtigungsverarbeitung"

            tag.contains(
                "*alarm*",
                ignoreCase = true
            ) ->
                "Alarm"

            tag.contains(
                "*job*",
                ignoreCase = true
            ) ->
                "Hintergrundjob"

            tag.contains(
                "*launch*",
                ignoreCase = true
            ) ->
                "App-Start"

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
                "Audio-Wiedergabe oder Aufnahme"

            tag.contains(
                "SyncManager",
                ignoreCase = true
            ) ||
            tag.contains(
                "*sync*",
                ignoreCase = true
            ) ->
                "Synchronisierung"

            tag.contains(
                "Icing",
                ignoreCase = true
            ) ->
                "Suche oder Inhaltsindexierung"

            tag.contains(
                "PendingIntentClient",
                ignoreCase = true
            ) ->
                "Geplante Hintergrundaktion"

            else ->
                "Partial Wakelock"
        }
    }

    private fun resolveWakeLockSource(
        context: Context,
        packageName: String
    ): String {
        readableSystemSource(packageName)?.let {
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
        packageName: String
    ): String? {
        val value = packageName.lowercase(
            Locale.ROOT
        )

        return when {
            value == "android" ||
                value == "system" ->
                "Android-System"

            value.contains(
                "com.android.mms.service"
            ) ->
                "Android MMS-/Mobilfunkdienst"

            value.contains(
                "com.android.stk2"
            ) ->
                "Samsung Telefonie-/SIM-Dienst"

            value.contains(
                "com.android.phone"
            ) ->
                "Android Telefoniedienst"

            value.contains(
                "com.android.providers.telephony"
            ) ->
                "Android Telefonie-Datenspeicher"

            value.contains(
                "com.google.android.ims"
            ) ->
                "Google Mobilfunk-/IMS-Dienst"

            value.contains(
                "com.android.systemui"
            ) ->
                "Android Systemoberfläche"

            value.contains(
                "com.android.bluetooth"
            ) ->
                "Android Bluetooth-Dienst"

            value.contains(
                "com.android.networkstack"
            ) ->
                "Android Netzwerkdienst"

            value.contains(
                "com.google.android.gms"
            ) ->
                "Google Play-Dienste"

            else -> null
        }
    }

    private fun readableWakeReason(
        reason: String,
        details: String
    ): String {
        return when {
            reason ==
                "WAKE_REASON_POWER_BUTTON" ->
                "Power-Taste"

            details.contains(
                "DoubleTap",
                ignoreCase = true
            ) ||
            details.contains(
                "blackGestureWake",
                ignoreCase = true
            ) ->
                "Doppeltipp auf das ausgeschaltete Display"

            reason ==
                "WAKE_REASON_GESTURE" ->
                "Bildschirmgeste"

            reason ==
                "WAKE_REASON_LIFT" ->
                "Anheben des Geräts"

            reason ==
                "WAKE_REASON_PLUGGED_IN" ->
                "Stromversorgung verbunden"

            reason ==
                "WAKE_REASON_WAKE_KEY" ->
                "Aufwecktaste"

            reason ==
                "WAKE_REASON_WAKE_MOTION" ->
                "Bewegungs- oder Sensorsignal"

            reason ==
                "WAKE_REASON_APPLICATION" ->
                "App oder Systemfunktion"

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
            appendLine("Lokaler Ereignisexport")
            appendLine()
            appendLine(
                "Export erstellt: ${
                    formatter.format(Date())
                }"
            )
            appendLine(
                "Überwachung: ${
                    if (isMonitoring(context)) {
                        "aktiv"
                    } else {
                        "gestoppt"
                    }
                }"
            )
            appendLine(
                "Gespeicherte Ereignisse: ${events.size}"
            )

            val exportHighlights =
                buildExportHighlights(events)

            val exportSummary =
                buildExportSummary(
                    events = events,
                    highlights = exportHighlights
                )

            appendLine()
            appendLine("Kurzfazit")

            exportSummary.forEach { line ->
                appendLine(line)
            }

            appendLine()
            appendLine("Auffälligkeiten")

            exportHighlights.forEach { highlight ->
                appendLine("• $highlight")
            }

            val parserDiagnostics =
                BackgroundWakeMonitor.diagnostics(
                    context
                )

            appendLine()
            appendLine("Hintergrund-Parserdiagnose")
            appendLine(
                "BatteryStats-Zeilen: " +
                    parserDiagnostics.parsedLines
            )
            appendLine(
                "Wake-Reasons: " +
                    parserDiagnostics.wakeReasons
            )
            appendLine(
                "CPU-Starts (+running): " +
                    parserDiagnostics.runningStarts
            )
            appendLine(
                "Wakelocks: " +
                    parserDiagnostics.wakeLocks
            )
            appendLine(
                "Jobs: " +
                    parserDiagnostics.jobs
            )
            appendLine(
                "Synchronisierungen: " +
                    parserDiagnostics.syncs
            )
            appendLine(
                "Erkannte Kandidaten im letzten Poll: " +
                    parserDiagnostics.candidates
            )
            appendLine(
                "Erzeugte CPU-Wakeups im letzten Poll: " +
                    parserDiagnostics.eventsCreated
            )

            if (
                parserDiagnostics.lastPollMillis > 0L
            ) {
                appendLine(
                    "Letzter Parserlauf: " +
                        formatter.format(
                            Date(
                                parserDiagnostics
                                    .lastPollMillis
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
                "Hintergrund-Rohdaten"
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
                    "Keine neuen Rohdaten im letzten Parserlauf."
                )
            } else if (rawDiagnosticLines.isEmpty()) {
                appendLine(
                    "Keine passenden BatteryStats-Rohzeilen gespeichert."
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
                    "Noch keine Ereignisse gespeichert."
                )
            } else {
                events.forEach { event ->
                    appendLine(
                        formatter.format(
                            Date(event.timestamp)
                        )
                    )

                    appendLine(
                        "${eventLabel(event.type)} – " +
                            event.title
                    )

                    val exportDetails =
                        exportDetailsForEvent(event)

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
            title: String
        ): List<String> {
            val start =
                lines.indexOfFirst { line ->
                    line.trim() == title
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
                        trimmed != "Standort / Bewegung" &&
                        trimmed != "Sensorik" &&
                        trimmed != "Funk / Netzwerk" &&
                        !trimmed.startsWith("Hinweis:")
                }
                .filter { line ->
                    line.trim().startsWith("•")
                }
        }

        val locationItems =
            sectionItems("Standort / Bewegung")

        val sensorItems =
            sectionItems("Sensorik")

        val networkItems =
            sectionItems("Funk / Netzwerk")

        val locationText =
            if (locationItems.isEmpty()) {
                "keine auffälligen Hinweise"
            } else {
                locationItems
                    .take(3)
                    .joinToString(", ") { item ->
                        item.removePrefix("•").trim()
                    }
            }

        val sensorText =
            if (sensorItems.isEmpty()) {
                "keine auffälligen Hinweise"
            } else {
                sensorItems
                    .take(3)
                    .joinToString(", ") { item ->
                        item.removePrefix("•").trim()
                    }
            }

        val networkText =
            if (networkItems.isEmpty()) {
                "keine auffälligen Hinweise"
            } else {
                networkItems
                    .take(3)
                    .joinToString(", ") { item ->
                        item.removePrefix("•").trim()
                    }
            }

        return buildString {
            appendLine("Auslöser: Display an")
            appendLine("Datenquelle: Shizuku Kompaktdiagnose")
            appendLine()
            appendLine("Expertenkontext kurz:")
            appendLine("• Standort / Bewegung: $locationText")
            appendLine("• Sensorik: $sensorText")
            appendLine("• Funk / Netzwerk: $networkText")
            appendLine()
            append(
                "Hinweis: Experten-Rohdaten werden im Export " +
                    "bewusst verdichtet; Standortkoordinaten " +
                    "werden nicht exportiert."
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
                    event.details.contains(
                        "Power-Taste",
                        ignoreCase = true
                    )
            }

        when {
            screenOnEvents.isEmpty() ->
                summary.add(
                    "In diesem Export wurden keine Display-Weckungen gespeichert."
                )

            powerButtonWakeups == screenOnEvents.size ->
                summary.add(
                    "Das Display wurde in diesem Lauf nicht verdächtig von Apps geweckt."
                )

            powerButtonWakeups > 0 ->
                summary.add(
                    "Ein Teil der Display-Weckungen wurde klar als Power-Taste erkannt."
                )

            else ->
                summary.add(
                    "Es gab Display-Weckungen ohne eindeutige Power-Tasten-Erkennung."
                )
        }

        if (
            screenOnEvents.isNotEmpty() &&
            powerButtonWakeups == screenOnEvents.size
        ) {
            summary.add(
                "Alle Display-Weckungen wurden als Power-Taste erkannt."
            )
        }

        val frequentSourcesLine =
            highlights.firstOrNull { line ->
                line.startsWith(
                    "Häufige technische Quellen:"
                )
            }

        when {
            frequentSourcesLine?.contains(
                "Funk/Netzwerk",
                ignoreCase = true
            ) == true ->
                summary.add(
                    "Im Hintergrund gab es viele Funk-/Netzwerk-Aktivitäten."
                )

            frequentSourcesLine != null ->
                summary.add(
                    "Im Hintergrund wurden wiederkehrende technische Aktivitäten erkannt."
                )
        }

        val longestWakeupLine =
            highlights.firstOrNull { line ->
                line.startsWith(
                    "Längster CPU-Wakeup:"
                )
            }

        if (longestWakeupLine != null) {
            summary.add(
                "Auffälligster längerer CPU-Wakeup: " +
                    longestWakeupLine
                        .removePrefix(
                            "Längster CPU-Wakeup: "
                        ) +
                    "."
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
                if (lockGlowWakeups == 1) {
                    "Eine Display-Weckung wurde durch LockGlow ausgelöst."
                } else {
                    "$lockGlowWakeups Display-Weckungen wurden durch LockGlow ausgelöst."
                }
            )
        }

        val notificationCount =
            events.count { event ->
                event.type == "NOTIFICATION"
            }

        if (notificationCount > 0) {
            summary.add(
                "Benachrichtigungen wurden erfasst, aber nicht automatisch als Hauptauslöser gewertet."
            )
        }

        if (summary.isEmpty()) {
            summary.add(
                "Keine besonderen Muster in den gespeicherten Ereignissen erkannt."
            )
        }

        return summary
    }

    private fun buildExportHighlights(
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
                    event.details
                )?.let { durationMillis ->
                    event to durationMillis
                }
            }.maxByOrNull { pair ->
                pair.second
            }

        if (longestCpuWakeup != null) {
            highlights.add(
                "Längster CPU-Wakeup: " +
                    cleanExportTitle(
                        longestCpuWakeup.first.title
                    ) +
                    " · " +
                    formatExportDuration(
                        longestCpuWakeup.second
                    )
            )
        }

        val frequentSources =
            events.flatMap { event ->
                extractExportSources(event)
            }
                .map { source ->
                    compactExportSource(source)
                }
                .filter { source ->
                    source.isNotBlank() &&
                        !source.equals(
                            "nicht eindeutig zuordenbar",
                            ignoreCase = true
                        ) &&
                        !source.equals(
                            "unbekannte Quelle",
                            ignoreCase = true
                        )
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
                "Häufige technische Quellen: " +
                    frequentSources.joinToString(", ") {
                            pair ->
                        "${pair.first} (${pair.second}×)"
                    }
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
                    event.details.contains(
                        "Power-Taste",
                        ignoreCase = true
                    )
            }

        if (screenOnEvents.isNotEmpty()) {
            if (
                powerButtonWakeups ==
                screenOnEvents.size
            ) {
                highlights.add(
                    "Display-Weckungen: alle " +
                        screenOnEvents.size +
                        " als Power-Taste erkannt"
                )
            } else if (powerButtonWakeups > 0) {
                highlights.add(
                    "Display-Weckungen: " +
                        powerButtonWakeups +
                        " von " +
                        screenOnEvents.size +
                        " als Power-Taste erkannt"
                )
            } else {
                highlights.add(
                    "Display-Weckungen: " +
                        screenOnEvents.size +
                        " erkannt, ohne eindeutige Power-Taste"
                )
            }
        }

        val notificationCount =
            events.count { event ->
                event.type == "NOTIFICATION"
            }

        if (notificationCount > 0) {
            highlights.add(
                "Benachrichtigungen: " +
                    notificationCount +
                    " erkannt, aber nicht automatisch als Hauptauslöser gewertet"
            )
        }

        val unclearCpuWakeups =
            cpuEvents.count { event ->
                event.details.contains(
                    "nicht eindeutig zuordenbar",
                    ignoreCase = true
                )
            }

        if (unclearCpuWakeups > 0) {
            highlights.add(
                "Nicht eindeutig zugeordnete CPU-Wakeups: " +
                    unclearCpuWakeups
            )
        }

        if (highlights.isEmpty()) {
            highlights.add(
                "Keine besonderen Muster in den gespeicherten Ereignissen erkannt."
            )
        }

        return highlights
    }

    private fun parseCpuWakeDurationMillis(
        details: String
    ): Long? {
        val line =
            details.lines().firstOrNull { value ->
                value.trim().startsWith(
                    "CPU-Wachzeit:"
                )
            } ?: return null

        val value =
            line.substringAfter(":")
                .trim()

        if (
            value.contains(
                "nicht ermittelbar",
                ignoreCase = true
            )
        ) {
            return null
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

            value.contains("Sekunde") ->
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
        event: WakeEvent
    ): String {
        val title =
            event.title

        val titleLooksUnknown =
            title.contains(
                "CPU im Hintergrund aufgeweckt",
                ignoreCase = true
            ) ||
                title.contains(
                    "unbekannte Quelle",
                    ignoreCase = true
                ) ||
                title.contains(
                    "nicht eindeutig",
                    ignoreCase = true
                )

        if (
            titleLooksUnknown &&
            looksLikeNetworkRadioSource(
                event.details
            )
        ) {
            return "Funk/Netzwerk"
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
        title: String
    ): String {
        return title
            .removePrefix("CPU-Wakeup · ")
            .removePrefix("CPU im Hintergrund aufgeweckt")
            .ifBlank {
                "unbekannte Quelle"
            }
    }

    private fun extractExportSources(
        event: WakeEvent
    ): List<String> {
        val sources =
            mutableListOf<String>()

        var inTechnicalSources =
            false

        event.details.lines().forEach { rawLine ->
            val line =
                rawLine.trim()

            if (line == "Technische Quellen:") {
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
                line.startsWith("Mögliche Quelle:") ->
                    sources.add(
                        line.substringAfter(":").trim()
                    )

                line.startsWith("Quelle:") ->
                    sources.add(
                        line.substringAfter(":").trim()
                    )

                line.startsWith("Technischer Tag:") ->
                    sources.add(
                        line.substringAfter(":").trim()
                    )
            }
        }

        return sources
    }

    private fun compactExportSource(
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
                "Samsung Telefonie-/SIM-Dienst"

            lower.contains("fmm-acquirewakelock") ||
                lower.contains("offlinefindtask") ->
                "Samsung Offline-Suche"

            lower.contains("com.android.phone") ||
                lower.contains("android telefoniedienst") ||
                lower.contains("*telephony-radio*") ->
                "Android Telefoniedienst"

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
                "Funk/Netzwerk"

            lower.contains("time_tick") ->
                "Android TIME_TICK"

            lower.contains("systemui.aod") ||
                lower.contains("aod.hide_time") ||
                lower.contains("oplusscreenoffgesture") ->
                "Android Systemoberfläche"

            lower.contains("com.google.android.gms") ||
                lower.contains("google play-dienste") ||
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
                "Google-Dienste"

            lower.contains("whatsapp") ->
                "WhatsApp"

            lower.contains("oplus") ||
                lower.contains("oneplus") ||
                lower.contains("athena") ->
                "OnePlus-System"

            else ->
                cleaned
                    .removePrefix("CPU-Wakeup · ")
                    .removePrefix("Wakeup-Alarm: ")
                    .removePrefix("Partial Wakelock: ")
                    .substringBefore("/androidx.work.impl")
                    .substringBefore(":android")
                    .substringBefore(" (")
                    .take(80)
                    .trim()
        }
    }



    private fun formatAge(milliseconds: Long): String {
        return String.format(
            Locale.getDefault(),
            "%.1f Sekunden",
            milliseconds / 1000.0
        )
    }

    private fun eventLabel(type: String): String {
        return when (type) {
            "MONITOR_START" -> "MONITOR"
            "MONITOR_STOP" -> "MONITOR"
            "NETWORK_SESSION" ->
                "NETZWERK-SITZUNG"
            "SCREEN_ON" -> "DISPLAY AN"
            "SCREEN_OFF" -> "DISPLAY AUS"
            "POWER_CONNECTED" -> "STROM VERBUNDEN"
            "POWER_DISCONNECTED" -> "STROM GETRENNT"
            "USB_ATTACHED" -> "USB VERBUNDEN"
            "USB_DETACHED" -> "USB GETRENNT"
            "NOTIFICATION" -> "BENACHRICHTIGUNG"
            "CPU_WAKEUP" -> "CPU-HINTERGRUND-WAKEUP"
            else -> type
        }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private fun readEvents(
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
                            "Unbekanntes Ereignis"
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
