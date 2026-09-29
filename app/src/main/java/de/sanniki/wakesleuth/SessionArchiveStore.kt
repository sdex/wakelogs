package de.sanniki.wakesleuth

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject

data class ArchivedSessionApp(
    val name: String,
    val totalBytes: Long
)

data class ArchivedSessionSource(
    val name: String,
    val cpuCount: Int,
    val displayCount: Int,
    val companionCount: Int,
    val longestCpuDurationMillis: Long?
)

data class ArchivedSession(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long,
    val durationMillis: Long,
    val displayWakeups: Int,
    val cpuWakeups: Int,
    val networkTotalBytes: Long,
    val networkRxBytes: Long,
    val networkTxBytes: Long,
    val networkActiveApps: Int,
    val topApps: List<ArchivedSessionApp>,
    val sources: List<ArchivedSessionSource> =
        emptyList(),
    val note: String? = null
)

object SessionArchiveStore {

    private const val PREFS_NAME =
        "wakesleuth_session_archive"

    private const val KEY_SESSIONS =
        "sessions"

    private const val MAX_SESSIONS =
        20

    private val lock = Any()

    fun captureLatestSession(
        context: Context
    ): ArchivedSession? {
        val events =
            EventStore.getEvents(context)
                .sortedBy {
                    it.timestamp
                }

        val latestStart =
            events.lastOrNull {
                it.type == "MONITOR_START"
            } ?: return null

        val stop =
            events.firstOrNull { event ->
                event.type == "MONITOR_STOP" &&
                    event.timestamp >=
                        latestStart.timestamp
            } ?: return null

        /*
         * Die Sitzungsdauer endet am Zeitpunkt des
         * Stopp-Tastendrucks. Technische Abschlussdaten
         * dürfen trotzdem noch zur Sitzung gehören.
         */
        val finalizationEndMillis =
            events.lastOrNull { event ->
                event.timestamp >=
                    latestStart.timestamp
            }
                ?.timestamp
                ?: stop.timestamp

        val sessionEvents =
            events.filter { event ->
                event.timestamp >=
                    latestStart.timestamp &&
                    event.timestamp <=
                        finalizationEndMillis
            }

        val networkEvent =
            sessionEvents.lastOrNull {
                it.type == "NETWORK_SESSION"
            }

        val network =
            parseNetworkSession(
                context,
                networkEvent?.details
            )

        val session =
            ArchivedSession(
                id = latestStart.timestamp,
                startMillis =
                    latestStart.timestamp,
                endMillis =
                    stop.timestamp,
                durationMillis =
                    (
                        stop.timestamp -
                            latestStart.timestamp
                    ).coerceAtLeast(0L),
                displayWakeups =
                    sessionEvents.count {
                        it.type == "SCREEN_ON"
                    },
                cpuWakeups =
                    sessionEvents.count {
                        it.type == "CPU_WAKEUP"
                    },
                networkTotalBytes =
                    network.totalBytes,
                networkRxBytes =
                    network.rxBytes,
                networkTxBytes =
                    network.txBytes,
                networkActiveApps =
                    network.activeApps,
                topApps =
                    network.topApps,
                sources =
                    buildArchivedSessionSources(
                        context,
                        sessionEvents
                    ),
                note = null
            )

        saveOrReplace(
            context = context,
            session = session
        )

        return session
    }

    fun getSessions(
        context: Context
    ): List<ArchivedSession> {
        synchronized(lock) {
            val raw =
                preferences(context)
                    .getString(
                        KEY_SESSIONS,
                        null
                    )

            if (raw.isNullOrBlank()) {
                return emptyList()
            }

            return runCatching {
                val array =
                    JSONArray(raw)

                buildList {
                    for (
                        index in 0 until array.length()
                    ) {
                        val item =
                            array.optJSONObject(index)
                                ?: continue

                        val topAppsArray =
                            item.optJSONArray(
                                "topApps"
                            )

                        val topApps =
                            buildList {
                                if (
                                    topAppsArray != null
                                ) {
                                    for (
                                        appIndex in
                                        0 until
                                            topAppsArray
                                                .length()
                                    ) {
                                        val app =
                                            topAppsArray
                                                .optJSONObject(
                                                    appIndex
                                                )
                                                ?: continue

                                        val name =
                                            app.optString(
                                                "name"
                                            ).trim()

                                        if (
                                            name.isBlank()
                                        ) {
                                            continue
                                        }

                                        add(
                                            ArchivedSessionApp(
                                                name = name,
                                                totalBytes =
                                                    app.optLong(
                                                        "totalBytes",
                                                        0L
                                                    )
                                                        .coerceAtLeast(
                                                            0L
                                                        )
                                            )
                                        )
                                    }
                                }
                            }

                        val sourceArray =
                            item.optJSONArray(
                                "sources"
                            )

                        val sources =
                            buildList {
                                if (sourceArray != null) {
                                    for (
                                        sourceIndex in
                                        0 until
                                            sourceArray.length()
                                    ) {
                                        val source =
                                            sourceArray
                                                .optJSONObject(
                                                    sourceIndex
                                                )
                                                ?: continue

                                        val name =
                                            source.optString(
                                                "name"
                                            ).trim()

                                        if (name.isBlank()) {
                                            continue
                                        }

                                        val longest =
                                            source.optLong(
                                                "longestCpuDurationMillis",
                                                -1L
                                            )

                                        add(
                                            ArchivedSessionSource(
                                                name = name,
                                                cpuCount =
                                                    source.optInt(
                                                        "cpuCount",
                                                        0
                                                    ).coerceAtLeast(0),
                                                displayCount =
                                                    source.optInt(
                                                        "displayCount",
                                                        0
                                                    ).coerceAtLeast(0),
                                                companionCount =
                                                    source.optInt(
                                                        "companionCount",
                                                        0
                                                    ).coerceAtLeast(0),
                                                longestCpuDurationMillis =
                                                    longest
                                                        .takeIf {
                                                            it >= 0L
                                                        }
                                            )
                                        )
                                    }
                                }
                            }

                        val startMillis =
                            item.optLong(
                                "startMillis",
                                0L
                            )

                        val endMillis =
                            item.optLong(
                                "endMillis",
                                0L
                            )

                        if (
                            startMillis <= 0L ||
                            endMillis <
                                startMillis
                        ) {
                            continue
                        }

                        add(
                            ArchivedSession(
                                id =
                                    item.optLong(
                                        "id",
                                        startMillis
                                    ),
                                startMillis =
                                    startMillis,
                                endMillis =
                                    endMillis,
                                durationMillis =
                                    item.optLong(
                                        "durationMillis",
                                        endMillis -
                                            startMillis
                                    )
                                        .coerceAtLeast(
                                            0L
                                        ),
                                displayWakeups =
                                    item.optInt(
                                        "displayWakeups",
                                        0
                                    )
                                        .coerceAtLeast(
                                            0
                                        ),
                                cpuWakeups =
                                    item.optInt(
                                        "cpuWakeups",
                                        0
                                    )
                                        .coerceAtLeast(
                                            0
                                        ),
                                networkTotalBytes =
                                    item.optLong(
                                        "networkTotalBytes",
                                        0L
                                    )
                                        .coerceAtLeast(
                                            0L
                                        ),
                                networkRxBytes =
                                    item.optLong(
                                        "networkRxBytes",
                                        0L
                                    )
                                        .coerceAtLeast(
                                            0L
                                        ),
                                networkTxBytes =
                                    item.optLong(
                                        "networkTxBytes",
                                        0L
                                    )
                                        .coerceAtLeast(
                                            0L
                                        ),
                                networkActiveApps =
                                    item.optInt(
                                        "networkActiveApps",
                                        0
                                    )
                                        .coerceAtLeast(
                                            0
                                        ),
                                topApps =
                                    topApps,
                                sources =
                                    sources,
                                note =
                                    item.optString(
                                        "note",
                                        ""
                                    )
                                        .trim()
                                        .ifBlank {
                                            null
                                        }
                            )
                        )
                    }
                }
                    .sortedByDescending {
                        it.startMillis
                    }
            }.getOrElse {
                emptyList()
            }
        }
    }

    fun updateNote(
        context: Context,
        sessionId: Long,
        note: String?
    ): Boolean {
        synchronized(lock) {
            val sessions =
                getSessions(context)

            var changed =
                false

            val updated =
                sessions.map { session ->
                    if (
                        session.id ==
                        sessionId
                    ) {
                        changed = true

                        session.copy(
                            note =
                                note
                                    ?.trim()
                                    ?.take(120)
                                    ?.ifBlank {
                                        null
                                    }
                        )
                    } else {
                        session
                    }
                }

            if (!changed) {
                return false
            }

            saveSessions(
                context = context,
                sessions = updated
            )

            return true
        }
    }

    fun deleteSession(
        context: Context,
        sessionId: Long
    ): Boolean {
        synchronized(lock) {
            val existing =
                getSessions(context)

            val remaining =
                existing.filterNot {
                    it.id == sessionId
                }

            if (
                remaining.size ==
                existing.size
            ) {
                return false
            }

            saveSessions(
                context = context,
                sessions = remaining
            )

            return true
        }
    }

    fun clear(
        context: Context
    ) {
        synchronized(lock) {
            preferences(context)
                .edit()
                .remove(KEY_SESSIONS)
                .apply()
        }
    }

    private fun saveOrReplace(
        context: Context,
        session: ArchivedSession
    ) {
        synchronized(lock) {
            val sessions =
                getSessions(context)
                    .filterNot {
                        it.id == session.id
                    }
                    .toMutableList()

            sessions.add(
                0,
                session
            )

            saveSessions(
                context = context,
                sessions = sessions
            )
        }
    }

    private fun saveSessions(
        context: Context,
        sessions: List<ArchivedSession>
    ) {
        val array =
            JSONArray()

        sessions
            .sortedByDescending {
                it.startMillis
            }
            .take(MAX_SESSIONS)
            .forEach { item ->
                val topApps =
                    JSONArray()

                item.topApps.forEach { app ->
                    topApps.put(
                        JSONObject().apply {
                            put(
                                "name",
                                app.name
                            )
                            put(
                                "totalBytes",
                                app.totalBytes
                            )
                        }
                    )
                }

                val sources =
                    JSONArray()

                item.sources.forEach { source ->
                    sources.put(
                        JSONObject().apply {
                            put(
                                "name",
                                source.name
                            )
                            put(
                                "cpuCount",
                                source.cpuCount
                            )
                            put(
                                "displayCount",
                                source.displayCount
                            )
                            put(
                                "companionCount",
                                source.companionCount
                            )

                            if (
                                source
                                    .longestCpuDurationMillis !=
                                null
                            ) {
                                put(
                                    "longestCpuDurationMillis",
                                    source
                                        .longestCpuDurationMillis
                                )
                            }
                        }
                    )
                }

                array.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put(
                            "startMillis",
                            item.startMillis
                        )
                        put(
                            "endMillis",
                            item.endMillis
                        )
                        put(
                            "durationMillis",
                            item.durationMillis
                        )
                        put(
                            "displayWakeups",
                            item.displayWakeups
                        )
                        put(
                            "cpuWakeups",
                            item.cpuWakeups
                        )
                        put(
                            "networkTotalBytes",
                            item.networkTotalBytes
                        )
                        put(
                            "networkRxBytes",
                            item.networkRxBytes
                        )
                        put(
                            "networkTxBytes",
                            item.networkTxBytes
                        )
                        put(
                            "networkActiveApps",
                            item.networkActiveApps
                        )
                        put(
                            "topApps",
                            topApps
                        )

                        put(
                            "sources",
                            sources
                        )

                        put(
                            "note",
                            item.note
                                ?: JSONObject.NULL
                        )
                    }
                )
            }

        preferences(context)
            .edit()
            .putString(
                KEY_SESSIONS,
                array.toString()
            )
            .apply()
    }

    private data class MutableArchivedSource(
        var cpuCount: Int = 0,
        var displayCount: Int = 0,
        var companionCount: Int = 0,
        var longestCpuDurationMillis: Long? = null
    )

    private fun buildArchivedSessionSources(
        context: Context,
        events: List<WakeEvent>
    ): List<ArchivedSessionSource> {
        val values =
            linkedMapOf<
                String,
                MutableArchivedSource
            >()

        events.forEach { event ->
            if (
                event.type != "CPU_WAKEUP" &&
                event.type != "SCREEN_ON"
            ) {
                return@forEach
            }

            val sources =
                extractArchivedSources(
                    context,
                    event
                )

            val duration =
                if (
                    event.type ==
                    "CPU_WAKEUP"
                ) {
                    parseArchivedCpuDuration(
                        context,
                        event.details
                    )
                } else {
                    null
                }

            sources.forEach {
                    source,
                    companion ->

                val item =
                    values.getOrPut(source) {
                        MutableArchivedSource()
                    }

                when (event.type) {
                    "CPU_WAKEUP" ->
                        item.cpuCount++

                    "SCREEN_ON" ->
                        item.displayCount++
                }

                if (companion) {
                    item.companionCount++
                }

                if (duration != null) {
                    item.longestCpuDurationMillis =
                        maxOf(
                            item
                                .longestCpuDurationMillis
                                ?: 0L,
                            duration
                        )
                }
            }
        }

        return values.map {
                entry ->

            ArchivedSessionSource(
                name = entry.key,
                cpuCount =
                    entry.value.cpuCount,
                displayCount =
                    entry.value.displayCount,
                companionCount =
                    entry.value.companionCount,
                longestCpuDurationMillis =
                    entry.value
                        .longestCpuDurationMillis
            )
        }
            .filter {
                it.cpuCount > 0 ||
                    it.displayCount > 0
            }
            .sortedWith(
                compareByDescending<
                    ArchivedSessionSource
                > {
                    it.cpuCount +
                        it.displayCount
                }.thenBy {
                    it.name.lowercase()
                }
            )
    }

    private fun extractArchivedSources(
        context: Context,
        event: WakeEvent
    ): Map<String, Boolean> {
        val result =
            linkedMapOf<String, Boolean>()

        val lines =
            event.details
                .lineSequence()
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .toList()

        var companionSection =
            false

        lines.forEach { line ->
            when {
                startsWithLabel(
                    line,
                    context,
                    R.string.event_label_companion_activity
                ) -> {
                    companionSection = true
                }

                startsWithLabel(
                    line,
                    context,
                    R.string.event_label_direct_wake_reason
                ) ||
                startsWithLabel(
                    line,
                    context,
                    R.string.timeline_prefix_likely_cause
                ) ||
                startsWithLabel(
                    line,
                    context,
                    R.string.timeline_prefix_possible_cause
                ) ||
                startsWithLabel(
                    line,
                    context,
                    R.string.event_label_cause_detected_later
                ) ||
                startsWithLabel(
                    line,
                    context,
                    R.string.event_label_wakeup_alarm_hint
                ) ||
                startsWithLabel(
                    line,
                    context,
                    R.string.event_label_background_job_hint
                ) ||
                startsWithLabel(
                    line,
                    context,
                    R.string.event_label_system_hint
                ) -> {
                    companionSection = false

                    val inlineSource =
                        line.substringAfter(
                            ":",
                            ""
                        )
                            .trim()
                            .takeIf {
                                startsWithLabel(
                                    line,
                                    context,
                                    R.string.timeline_prefix_likely_cause
                                ) ||
                                startsWithLabel(
                                    line,
                                    context,
                                    R.string.timeline_prefix_possible_cause
                                ) ||
                                startsWithLabel(
                                    line,
                                    context,
                                    R.string.event_label_cause_detected_later
                                )
                            }

                    inlineSource
                        ?.let {
                            normalizeArchivedSource(
                                context,
                                it
                            )
                        }
                        ?.let { source ->
                            result[source] =
                                false
                        }
                }

                startsWithLabel(
                    line,
                    context,
                    R.string.timeline_prefix_possible_source
                ) -> {
                    normalizeArchivedSource(
                        context,
                        line.substringAfter(":")
                    )?.let { source ->
                        result[source] =
                            false
                    }
                }

                startsWithLabel(
                    line,
                    context,
                    R.string.timeline_prefix_source
                ) -> {
                    normalizeArchivedSource(
                        context,
                        line.substringAfter(":")
                    )?.let { source ->
                        val existing =
                            result[source]

                        result[source] =
                            existing == true ||
                                companionSection
                    }
                }
            }
        }

        return result
    }

    private fun normalizeArchivedSource(
        context: Context,
        raw: String
    ): String? {
        val value =
            raw
                .removePrefix("App: ")
                .substringBefore(" (")
                .substringBefore(" · ")
                .trim()
                .replace(
                    Regex("""\s+"""),
                    " "
                )

        if (
            value.isBlank() ||
            equalsLabel(
                value,
                context,
                R.string.bg_possible_source_ambiguous
            ) ||
            equalsLabel(
                value,
                context,
                R.string.event_unknown
            ) ||
            equalsLabel(
                value,
                context,
                R.string.event_none
            )
        ) {
            return null
        }

        return sourceDisplayName(
            context,
            value
        )
    }

    private fun parseArchivedCpuDuration(
        context: Context,
        details: String
    ): Long? {
        val value =
            details
                .lineSequence()
                .map {
                    it.trim()
                }
                .firstOrNull {
                    LocalizedText.startsWithAny(
                        it,
                        context,
                        R.string.timeline_prefix_cpu_awake_time
                    )
                }
                ?.substringAfter(":")
                ?.trim()
                ?: return null

        val normalized =
            value.replace(
                ',',
                '.'
            )

        val number =
            Regex(
                """(\d+(?:\.\d+)?)"""
            )
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
                ?: return null

        return when {
            normalized.contains(
                "ms",
                ignoreCase = true
            ) ->
                number.toLong()

            normalized.contains(
                "Sek",
                ignoreCase = true
            ) ||
                normalized.contains(
                    "sec",
                    ignoreCase = true
                ) ->
                (number * 1_000.0)
                    .toLong()

            normalized.contains(
                "Min",
                ignoreCase = true
            ) ->
                (number * 60_000.0)
                    .toLong()

            else ->
                null
        }
    }

    private data class ParsedNetworkSession(
        val totalBytes: Long,
        val rxBytes: Long,
        val txBytes: Long,
        val activeApps: Int,
        val topApps:
            List<ArchivedSessionApp>
    )

    private fun parseNetworkSession(
        context: Context,
        details: String?
    ): ParsedNetworkSession {
        if (details.isNullOrBlank()) {
            return ParsedNetworkSession(
                totalBytes = 0L,
                rxBytes = 0L,
                txBytes = 0L,
                activeApps = 0,
                topApps = emptyList()
            )
        }

        val lines =
            details.lines()
                .map {
                    it.trim()
                }

        val totalLine =
            lines.firstOrNull {
                LocalizedText.startsWithAny(
                    it,
                    context,
                    R.string.sleep_label_total
                )
            }

        val totalBytes =
            parseFormattedBytes(
                totalLine
                    ?.let {
                        LocalizedText.removeAnyPrefix(
                            it,
                            context,
                            R.string.sleep_label_total
                        )
                    }
                    ?.substringBefore("·")
            )

        val rxBytes =
            parseFormattedBytes(
                totalLine
                    ?.let {
                        LocalizedText.substringAfterAny(
                            it,
                            context,
                            R.string.sleep_label_received,
                            missing = ""
                        )
                    }
                    ?.substringBefore("·")
            )

        val txBytes =
            parseFormattedBytes(
                totalLine
                    ?.let {
                        LocalizedText.substringAfterAny(
                            it,
                            context,
                            R.string.sleep_label_sent,
                            missing = ""
                        )
                    }
            )

        val activeApps =
            lines.firstOrNull {
                LocalizedText.startsWithAny(
                    it,
                    context,
                    R.string.sleep_label_apps_with_traffic
                )
            }
                ?.substringAfter(":")
                ?.trim()
                ?.toIntOrNull()
                ?: 0

        val topApps =
            lines.mapNotNull { line ->
                if (!line.startsWith("• ")) {
                    return@mapNotNull null
                }

                val content =
                    line.removePrefix("• ")
                        .trim()

                val separator =
                    content.lastIndexOf(" · ")

                if (separator <= 0) {
                    return@mapNotNull null
                }

                val name =
                    content.substring(
                        0,
                        separator
                    ).trim()

                val bytes =
                    parseFormattedBytes(
                        content.substring(
                            separator + 3
                        )
                    )

                if (
                    name.isBlank() ||
                    bytes <= 0L
                ) {
                    return@mapNotNull null
                }

                ArchivedSessionApp(
                    name = name,
                    totalBytes = bytes
                )
            }
                .take(10)

        return ParsedNetworkSession(
            totalBytes =
                totalBytes,
            rxBytes =
                rxBytes,
            txBytes =
                txBytes,
            activeApps =
                activeApps.coerceAtLeast(0),
            topApps =
                topApps
        )
    }

    private fun parseFormattedBytes(
        raw: String?
    ): Long {
        val value =
            raw
                ?.trim()
                ?.replace(",", ".")
                ?: return 0L

        val number =
            Regex(
                """(\d+(?:\.\d+)?)"""
            )
                .find(value)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
                ?: return 0L

        val multiplier =
            when {
                value.contains(
                    "GB",
                    ignoreCase = true
                ) ->
                    1024.0 *
                        1024.0 *
                        1024.0

                value.contains(
                    "MB",
                    ignoreCase = true
                ) ->
                    1024.0 *
                        1024.0

                value.contains(
                    "KB",
                    ignoreCase = true
                ) ->
                    1024.0

                else ->
                    1.0
            }

        val result =
            number * multiplier

        if (
            !result.isFinite() ||
            result <= 0.0 ||
            result >
                Long.MAX_VALUE.toDouble()
        ) {
            return 0L
        }

        return result.toLong()
    }

    private fun startsWithLabel(
        line: String,
        context: Context,
        @StringRes id: Int
    ): Boolean =
        LocalizedText
            .variants(
                context,
                id
            )
            .any {
                line.startsWith(
                    it,
                    ignoreCase = true
                )
            }

    private fun equalsLabel(
        value: String,
        context: Context,
        @StringRes id: Int
    ): Boolean =
        LocalizedText
            .variants(
                context,
                id
            )
            .any {
                value.equals(
                    it,
                    ignoreCase = true
                )
            }

    private fun preferences(
        context: Context
    ) =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
}
