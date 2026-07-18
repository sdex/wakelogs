package de.sanniki.wakesleuth

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

data class BackgroundParserDiagnostics(
    val parsedLines: Int = 0,
    val wakeReasons: Int = 0,
    val runningStarts: Int = 0,
    val wakeLocks: Int = 0,
    val jobs: Int = 0,
    val syncs: Int = 0,
    val candidates: Int = 0,
    val eventsCreated: Int = 0,
    val lastPollMillis: Long = 0L
)

object BackgroundWakeMonitor {

    private const val PREFS_NAME =
        "wakesleuth_background_wakeups"

    private const val KEY_BASELINE_READY =
        "baseline_ready"

    private const val KEY_LAST_TIMESTAMP =
        "last_timestamp"

    private const val KEY_DIAG_PARSED_LINES =
        "diag_parsed_lines"

    private const val KEY_DIAG_WAKE_REASONS =
        "diag_wake_reasons"

    private const val KEY_DIAG_RUNNING_STARTS =
        "diag_running_starts"

    private const val KEY_DIAG_WAKE_LOCKS =
        "diag_wake_locks"

    private const val KEY_DIAG_JOBS =
        "diag_jobs"

    private const val KEY_DIAG_SYNCS =
        "diag_syncs"

    private const val KEY_DIAG_CANDIDATES =
        "diag_candidates"

    private const val KEY_DIAG_EVENTS_CREATED =
        "diag_events_created"

    private const val KEY_DIAG_LAST_POLL =
        "diag_last_poll"

    private const val KEY_RAW_DIAGNOSTIC_LINES =
        "raw_diagnostic_lines"

    private const val MAX_RAW_DIAGNOSTIC_LINES =
        250

    private const val HISTORY_LINES = 3000

    /*
     * Ein Kandidat wird erst verarbeitet, wenn genügend
     * Folgezeit vergangen ist. So landen Jobs und Syncs
     * nicht erst im nächsten Poll außerhalb des Ereignisses.
     */
    private const val SAFE_TAIL_MILLIS =
        6_000L

    private const val EVIDENCE_BEFORE_WINDOW_MILLIS =
        1_000L

    private const val EVIDENCE_AFTER_WINDOW_MILLIS =
        5_000L

    /*
     * Wake-Reason und +running dürfen bei Samsung und
     * OnePlus auf getrennten BatteryStats-Zeilen stehen.
     */
    private const val WAKE_GROUP_WINDOW_MILLIS =
        5_000L

    /*
     * Samsung schreibt Power-Key-Wakelock und +screen
     * häufig auf zwei direkt aufeinanderfolgende Zeilen.
     */
    private const val POWER_KEY_LOOKBACK_MILLIS =
        1_000L

    private val timestampRegex =
        Regex(
            """(?<timestamp>\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})"""
        )

    private val wakeReasonRegex =
        Regex(
            """wake_reason=(?<reason>\d+:"[^"]+"|\S+)"""
        )

    private val wakeLockRegex =
        Regex(
            """\+wake_lock=(?<source>\S+:"[^"]+"|\S+)"""
        )

    private val jobRegex =
        Regex(
            """\+job=(?<source>\S+:"[^"]+"|\S+)"""
        )

    private val syncRegex =
        Regex(
            """\+sync=(?<source>\S+:"[^"]+"|\S+)"""
        )

    suspend fun poll(
        context: Context
    ): Int {
        if (
            ShizukuDiagnostics.state() !=
            ShizukuState.RUNNING_GRANTED
        ) {
            return 0
        }

        val output =
            runCatching {
                ShizukuDiagnostics.runDiagnosticCommand(
                    context = context,
                    command =
                        "dumpsys batterystats --history " +
                            "| tail -n $HISTORY_LINES"
                )
            }.getOrNull()
                ?: return 0

        return processHistory(
            context = context,
            history = output
        )
    }

    fun rawDiagnosticLines(
        context: Context
    ): List<String> {
        val prefs =
            context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

        return prefs.getString(
            KEY_RAW_DIAGNOSTIC_LINES,
            ""
        )
            .orEmpty()
            .lineSequence()
            .map {
                it.trimEnd()
            }
            .filter {
                it.isNotBlank()
            }
            .toList()
    }

    fun diagnostics(
        context: Context
    ): BackgroundParserDiagnostics {
        val prefs =
            context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

        return BackgroundParserDiagnostics(
            parsedLines =
                prefs.getInt(
                    KEY_DIAG_PARSED_LINES,
                    0
                ),
            wakeReasons =
                prefs.getInt(
                    KEY_DIAG_WAKE_REASONS,
                    0
                ),
            runningStarts =
                prefs.getInt(
                    KEY_DIAG_RUNNING_STARTS,
                    0
                ),
            wakeLocks =
                prefs.getInt(
                    KEY_DIAG_WAKE_LOCKS,
                    0
                ),
            jobs =
                prefs.getInt(
                    KEY_DIAG_JOBS,
                    0
                ),
            syncs =
                prefs.getInt(
                    KEY_DIAG_SYNCS,
                    0
                ),
            candidates =
                prefs.getInt(
                    KEY_DIAG_CANDIDATES,
                    0
                ),
            eventsCreated =
                prefs.getInt(
                    KEY_DIAG_EVENTS_CREATED,
                    0
                ),
            lastPollMillis =
                prefs.getLong(
                    KEY_DIAG_LAST_POLL,
                    0L
                )
        )
    }

    private fun processHistory(
        context: Context,
        history: String
    ): Int {
        val prefs =
            context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

        val parsedLines =
            history.lineSequence()
                .mapNotNull(::parseHistoryLine)
                .sortedBy {
                    it.timestampMillis
                }
                .toList()

        if (parsedLines.isEmpty()) {
            saveDiagnostics(
                context = context,
                diagnostics =
                    BackgroundParserDiagnostics(
                        lastPollMillis =
                            System.currentTimeMillis()
                    )
            )

            return 0
        }

        val newestTimestamp =
            parsedLines.maxOf {
                it.timestampMillis
            }

        saveRawDiagnosticLines(
            context = context,
            parsedLines = parsedLines
        )

        val safeCutoff =
            newestTimestamp -
                SAFE_TAIL_MILLIS

        val fullDiagnostics =
            BackgroundParserDiagnostics(
                parsedLines =
                    parsedLines.size,
                wakeReasons =
                    parsedLines.count {
                        wakeReasonRegex
                            .containsMatchIn(it.raw)
                    },
                runningStarts =
                    parsedLines.count {
                        hasToken(
                            raw = it.raw,
                            token = "+running"
                        )
                    },
                wakeLocks =
                    parsedLines.count {
                        wakeLockRegex
                            .containsMatchIn(it.raw)
                    },
                jobs =
                    parsedLines.count {
                        jobRegex
                            .containsMatchIn(it.raw)
                    },
                syncs =
                    parsedLines.count {
                        syncRegex
                            .containsMatchIn(it.raw)
                    },
                lastPollMillis =
                    System.currentTimeMillis()
            )

        if (
            !prefs.getBoolean(
                KEY_BASELINE_READY,
                false
            )
        ) {
            prefs.edit()
                .putBoolean(
                    KEY_BASELINE_READY,
                    true
                )
                .putLong(
                    KEY_LAST_TIMESTAMP,
                    safeCutoff
                )
                .apply()

            saveDiagnostics(
                context = context,
                diagnostics =
                    fullDiagnostics
            )

            return 0
        }

        val previousTimestamp =
            prefs.getLong(
                KEY_LAST_TIMESTAMP,
                0L
            )

        /*
         * Der Bildschirmzustand wird anhand der gesamten
         * eingelesenen Historie rekonstruiert, nicht nur
         * anhand der neuen Zeilen.
         */
        var screenOn = false
        var screenStateKnown = false

        val wakeCandidates =
            mutableListOf<WakeCandidate>()

        parsedLines.forEach { line ->
            val screenTurnedOn =
                hasToken(
                    raw = line.raw,
                    token = "+screen"
                )

            val screenTurnedOff =
                hasToken(
                    raw = line.raw,
                    token = "-screen"
                )

            if (screenTurnedOn) {
                screenOn = true
                screenStateKnown = true
            } else if (screenTurnedOff) {
                screenOn = false
                screenStateKnown = true
            }

            if (
                line.timestampMillis <=
                    previousTimestamp ||
                line.timestampMillis >
                    safeCutoff
            ) {
                return@forEach
            }

            val wakeReason =
                wakeReasonRegex
                    .find(line.raw)
                    ?.groups
                    ?.get("reason")
                    ?.value
                    ?.trim()

            val runningStarted =
                hasToken(
                    raw = line.raw,
                    token = "+running"
                )

            /*
             * Samsung verteilt den Power-Tastendruck je nach
             * One-UI-Version auf mehrere BatteryStats-Zeilen:
             *
             * 1. PhoneWindowManager.mPowerKeyWakeLock
             *    oder screenwake=...POWER
             * 2. wenige Millisekunden später +screen
             *    beziehungsweise reason=KEY
             *
             * Deshalb wird beim Einschalten des Displays auch
             * das kleine Zeitfenster unmittelbar davor geprüft.
             */
            if (screenTurnedOn) {
                val powerEvidenceLines =
                    parsedLines
                        .asSequence()
                        .filter { evidenceLine ->
                            evidenceLine.timestampMillis >=
                                line.timestampMillis -
                                    POWER_KEY_LOOKBACK_MILLIS &&
                                evidenceLine.timestampMillis <=
                                    line.timestampMillis
                        }
                        .toList()

                val combinedPowerEvidence =
                    powerEvidenceLines
                        .joinToString(
                            separator = "\\n"
                        ) { evidenceLine ->
                            evidenceLine.raw
                        }

                val hasPowerKeyWakeLock =
                    combinedPowerEvidence.contains(
                        "PhoneWindowManager.mPowerKeyWakeLock",
                        ignoreCase = true
                    ) ||
                        combinedPowerEvidence.contains(
                            "mPowerKeyWakeLock",
                            ignoreCase = true
                        )

                val hasPmicPowerKey =
                    combinedPowerEvidence.contains(
                        "pmic_pwrkey",
                        ignoreCase = true
                    )

                val hasSamsungPolicyPower =
                    combinedPowerEvidence.contains(
                        "screenwake=",
                        ignoreCase = true
                    ) &&
                        combinedPowerEvidence.contains(
                            "android.policy:POWER",
                            ignoreCase = true
                        )

                val hasDisplayReasonKey =
                    combinedPowerEvidence.contains(
                        "display_state_changed=",
                        ignoreCase = true
                    ) &&
                        combinedPowerEvidence.contains(
                            "reason=KEY",
                            ignoreCase = true
                        )

                if (
                    hasPowerKeyWakeLock ||
                    hasPmicPowerKey ||
                    hasSamsungPolicyPower ||
                    hasDisplayReasonKey
                ) {
                    val technicalTag =
                        powerEvidenceLines
                            .asSequence()
                            .mapNotNull { evidenceLine ->
                                wakeLockRegex
                                    .find(
                                        evidenceLine.raw
                                    )
                                    ?.groups
                                    ?.get("source")
                                    ?.value
                                    ?.let(::cleanSource)
                            }
                            .firstOrNull { source ->
                                source.contains(
                                    "mPowerKeyWakeLock",
                                    ignoreCase = true
                                )
                            }

                    val technicalReason =
                        powerEvidenceLines
                            .asSequence()
                            .mapNotNull { evidenceLine ->
                                wakeReasonRegex
                                    .find(
                                        evidenceLine.raw
                                    )
                                    ?.groups
                                    ?.get("reason")
                                    ?.value
                                    ?.trim()
                            }
                            .firstOrNull { reason ->
                                reason.contains(
                                    "pmic_pwrkey",
                                    ignoreCase = true
                                )
                            }
                            ?: when {
                                hasSamsungPolicyPower ->
                                    "android.policy:POWER"

                                hasDisplayReasonKey ->
                                    "Display reason=KEY"

                                else ->
                                    "Samsung Power-Key"
                            }

                    EventStore
                        .attachBatteryStatsPowerKeyToScreenOn(
                            context = context,
                            powerKeyTimestamp =
                                line.timestampMillis,
                            technicalReason =
                                technicalReason,
                            technicalTag =
                                technicalTag
                        )
                }
            }

            /*
             * Kein CPU-Hintergrund-Wakeup, wenn die gleiche
             * Zeile das sichtbare Display bereits einschaltet.
             */
            val qualifies =
                screenStateKnown &&
                    !screenOn &&
                    !screenTurnedOn &&
                    (
                        wakeReason != null ||
                            runningStarted
                    )

            if (!qualifies) {
                return@forEach
            }

            val nearbyCandidate =
                wakeCandidates.lastOrNull()
                    ?.takeIf { candidate ->
                        line.timestampMillis -
                            candidate.timestampMillis <=
                            WAKE_GROUP_WINDOW_MILLIS
                    }

            if (nearbyCandidate != null) {
                nearbyCandidate.lines.add(line)

                if (
                    nearbyCandidate.wakeReason == null &&
                    wakeReason != null
                ) {
                    nearbyCandidate.wakeReason =
                        wakeReason
                }

                if (runningStarted) {
                    nearbyCandidate.runningObserved =
                        true
                }
            } else {
                wakeCandidates.add(
                    WakeCandidate(
                        timestampMillis =
                            line.timestampMillis,
                        wakeReason =
                            wakeReason,
                        runningObserved =
                            runningStarted,
                        lines =
                            mutableListOf(line)
                    )
                )
            }
        }

        wakeCandidates.forEach { candidate ->
            val nextCpuSleep =
                parsedLines.firstOrNull { line ->
                    line.timestampMillis >
                        candidate.timestampMillis &&
                        hasToken(
                            raw = line.raw,
                            token = "-running"
                        )
                }

            candidate.cpuSleepTimestampMillis =
                nextCpuSleep?.timestampMillis

            val evidenceEnd =
                minOf(
                    candidate.timestampMillis +
                        EVIDENCE_AFTER_WINDOW_MILLIS,
                    nextCpuSleep?.timestampMillis
                        ?: Long.MAX_VALUE
                )

            parsedLines
                .asSequence()
                .filter { line ->
                    line.timestampMillis >=
                        candidate.timestampMillis -
                            EVIDENCE_BEFORE_WINDOW_MILLIS &&
                        line.timestampMillis <=
                            evidenceEnd
                }
                .forEach { line ->
                    if (
                        candidate.lines.none {
                            it.raw == line.raw
                        }
                    ) {
                        candidate.lines.add(line)
                    }

                    val reason =
                        wakeReasonRegex
                            .find(line.raw)
                            ?.groups
                            ?.get("reason")
                            ?.value
                            ?.trim()

                    if (
                        candidate.wakeReason == null &&
                        reason != null
                    ) {
                        candidate.wakeReason =
                            reason
                    }

                    if (
                        hasToken(
                            raw = line.raw,
                            token = "+running"
                        )
                    ) {
                        candidate.runningObserved =
                            true
                    }
                }
        }

        var added = 0

        wakeCandidates.forEach { candidate ->
            if (
                shouldSkipVisibleWakeEvent(
                    candidate
                )
            ) {
                return@forEach
            }

            addGroupedWakeEvent(
                context = context,
                candidate = candidate
            )

            added += 1
        }

        prefs.edit()
            .putLong(
                KEY_LAST_TIMESTAMP,
                safeCutoff
            )
            .apply()

        saveDiagnostics(
            context = context,
            diagnostics =
                fullDiagnostics.copy(
                    candidates =
                        wakeCandidates.size,
                    eventsCreated = added
                )
        )

        return added
    }

    private fun shouldSkipVisibleWakeEvent(
        candidate: WakeCandidate
    ): Boolean {
        val durationMillis =
            candidate.cpuSleepTimestampMillis
                ?.minus(
                    candidate.timestampMillis
                )
                ?.takeIf {
                    it >= 0L
                }

        val combinedRaw =
            candidate.lines
                .joinToString(
                    separator = "\n"
                ) {
                    it.raw
                }

        val hasPowerKey =
            combinedRaw.contains(
                "pmic_pwrkey",
                ignoreCase = true
            ) ||
                combinedRaw.contains(
                    "PhoneWindowManager.mPowerKeyWakeLock",
                    ignoreCase = true
                )

        /*
         * Ein Power-Tastendruck gehört zum sichtbaren
         * Display-Ereignis und ist kein eigenständiger
         * CPU-Hintergrund-Wakeup.
         */
        if (hasPowerKey) {
            return true
        }

        val wakeLocks =
            candidate.lines.any { line ->
                wakeLockRegex.containsMatchIn(
                    line.raw
                )
            }

        val jobs =
            candidate.lines.any { line ->
                jobRegex.containsMatchIn(
                    line.raw
                )
            }

        val syncs =
            candidate.lines.any { line ->
                syncRegex.containsMatchIn(
                    line.raw
                )
            }

        val reason =
            candidate.wakeReason
                .orEmpty()

        val pureTimerActivity =
            reason.contains(
                "timerfd",
                ignoreCase = true
            ) &&
                !wakeLocks &&
                !jobs &&
                !syncs

        /*
         * Sehr kurze, quellenlose Timerimpulse sind technisch
         * real, bringen in der sichtbaren Ereignisliste aber
         * kaum Erkenntnis und erzeugen auf Samsung viel Rauschen.
         */
        return pureTimerActivity &&
            durationMillis != null &&
            durationMillis < 250L
    }

    private fun addGroupedWakeEvent(
        context: Context,
        candidate: WakeCandidate
    ) {
        val sortedLines =
            candidate.lines
                .sortedBy {
                    it.timestampMillis
                }

        val wakeLocks =
            sortedLines.mapNotNull { line ->
                wakeLockRegex
                    .find(line.raw)
                    ?.groups
                    ?.get("source")
                    ?.value
                    ?.let(::cleanSource)
            }.distinct()

        val jobs =
            sortedLines.mapNotNull { line ->
                jobRegex
                    .find(line.raw)
                    ?.groups
                    ?.get("source")
                    ?.value
                    ?.let(::cleanSource)
            }.distinct()

        val syncs =
            sortedLines.mapNotNull { line ->
                syncRegex
                    .find(line.raw)
                    ?.groups
                    ?.get("source")
                    ?.value
                    ?.let(::cleanSource)
            }.distinct()

        val evidence =
            buildList {
                wakeLocks.forEach { source ->
                    add(
                        BackgroundEvidence(
                            type =
                                classifyWakeLock(
                                    source
                                ),
                            rawSource = source
                        )
                    )
                }

                jobs.forEach { source ->
                    add(
                        BackgroundEvidence(
                            type =
                                classifyJob(source),
                            rawSource = source
                        )
                    )
                }

                syncs.forEach { source ->
                    add(
                        BackgroundEvidence(
                            type =
                                "Synchronisierung",
                            rawSource = source
                        )
                    )
                }
            }.distinctBy {
                it.type to it.rawSource
            }

        val resolvedEvidence =
            evidence.map { item ->
                ResolvedEvidence(
                    type = item.type,
                    source =
                        resolveReadableSource(
                            context = context,
                            rawSource =
                                item.rawSource
                        ),
                    technicalSource =
                        item.rawSource
                )
            }

        val possibleSource =
            choosePossibleSource(
                resolvedEvidence
            )

        val cpuAwakeDurationMillis =
            candidate.cpuSleepTimestampMillis
                ?.minus(
                    candidate.timestampMillis
                )
                ?.takeIf {
                    it >= 0L
                }

        val title =
            if (possibleSource != null) {
                "CPU-Wakeup · " +
                    possibleSource.source
            } else {
                "CPU im Hintergrund aufgeweckt"
            }

        val wakeReasonText =
            candidate.wakeReason
                ?.let(::readableWakeReason)
                ?: if (
                    candidate.runningObserved
                ) {
                    "CPU-Aktivität erkannt; " +
                        "kein Wake-Reason übermittelt"
                } else {
                    "Nicht näher bezeichnet"
                }

        EventStore.addEventAt(
            context = context,
            timestamp =
                candidate.timestampMillis,
            type = "CPU_WAKEUP",
            title = title,
            details = buildString {
                appendLine(
                    "Display: blieb ausgeschaltet"
                )

                appendLine(
                    "Systemgrund: $wakeReasonText"
                )

                appendLine(
                    "Erkennung: " +
                        buildDetectionDescription(
                            candidate
                        )
                )

                appendLine(
                    "CPU-Wachzeit: " +
                        formatCpuAwakeDuration(
                            cpuAwakeDurationMillis
                        )
                )

                appendLine(
                    "Rückkehr in Ruhezustand: " +
                        if (
                            cpuAwakeDurationMillis != null
                        ) {
                            "erkannt"
                        } else {
                            "im aktuellen Verlauf nicht ermittelt"
                        }
                )

                if (possibleSource != null) {
                    appendLine(
                        "Mögliche Quelle: " +
                            possibleSource.source
                    )

                    appendLine(
                        "Aktivität: " +
                            possibleSource.type
                    )

                    appendLine(
                        "Einordnung: zeitlich zugeordnet, " +
                            "nicht als alleinige Ursache bewiesen"
                    )
                } else {
                    appendLine(
                        "Mögliche Quelle: " +
                            "nicht eindeutig zuordenbar"
                    )
                }

                appendLine()
                appendLine(
                    "Zugehörige Aktivitäten:"
                )

                if (resolvedEvidence.isEmpty()) {
                    appendLine(
                        "• Keine App- oder Dienstaktivität " +
                            "im kurzen Zeitfenster gefunden"
                    )
                } else {
                    resolvedEvidence.forEach { item ->
                        appendLine(
                            "• ${item.type}: " +
                                item.source
                        )
                    }
                }

                appendLine()

                if (candidate.wakeReason != null) {
                    appendLine(
                        "Technischer Wake-Reason: " +
                            candidate.wakeReason
                    )
                } else {
                    appendLine(
                        "Technischer Wake-Reason: " +
                            "von BatteryStats nicht geliefert"
                    )
                }

                val technicalSources =
                    resolvedEvidence
                        .map {
                            it.technicalSource
                        }
                        .distinct()

                if (technicalSources.isNotEmpty()) {
                    appendLine(
                        "Technische Quellen:"
                    )

                    technicalSources.forEach {
                        appendLine("• $it")
                    }
                }

                append(
                    "Datenquelle: Android BatteryStats-Historie"
                )
            }
        )
    }

    private fun buildDetectionDescription(
        candidate: WakeCandidate
    ): String {
        return when {
            candidate.wakeReason != null &&
                candidate.runningObserved ->
                "Wake-Reason und CPU-Start"

            candidate.wakeReason != null ->
                "Wake-Reason ohne separaten CPU-Start-Eintrag"

            candidate.runningObserved ->
                "CPU-Start ohne übermittelten Wake-Reason"

            else ->
                "BatteryStats-Aktivität"
        }
    }

    private fun choosePossibleSource(
        evidence: List<ResolvedEvidence>
    ): ResolvedEvidence? {
        return evidence.firstOrNull {
            it.type == "Synchronisierung"
        } ?: evidence.firstOrNull {
            it.type == "WorkManager"
        } ?: evidence.firstOrNull {
            it.type == "JobScheduler"
        } ?: evidence.firstOrNull {
            it.type == "Wakeup-Alarm"
        } ?: evidence.firstOrNull {
            it.type == "Job-Wakelock"
        } ?: evidence.firstOrNull {
            it.type == "Partial Wakelock"
        } ?: evidence.firstOrNull()
    }

    private fun classifyJob(
        source: String
    ): String {
        return when {
            source.contains(
                "SyncManager",
                ignoreCase = true
            ) ->
                "Synchronisierung"

            source.contains(
                "androidx.work",
                ignoreCase = true
            ) ||
            source.contains(
                "SystemJobService",
                ignoreCase = true
            ) ->
                "WorkManager"

            else ->
                "JobScheduler"
        }
    }

    private fun classifyWakeLock(
        source: String
    ): String {
        return when {
            source.contains(
                "*alarm*",
                ignoreCase = true
            ) ||
            source.contains(
                "alarm",
                ignoreCase = true
            ) ->
                "Wakeup-Alarm"

            source.contains(
                "SyncManager",
                ignoreCase = true
            ) ||
            source.contains(
                "*sync*",
                ignoreCase = true
            ) ->
                "Synchronisierung"

            source.contains(
                "*job*",
                ignoreCase = true
            ) ->
                "Job-Wakelock"

            else ->
                "Partial Wakelock"
        }
    }

    private fun resolveReadableSource(
        context: Context,
        rawSource: String
    ): String {
        val packageName =
            extractPackageName(
                rawSource
            )

        if (packageName == null) {
            return readableTechnicalSource(
                rawSource
            )
        }

        readableKnownPackage(
            packageName
        )?.let {
            return it
        }

        val appName =
            runCatching {
                val info =
                    context.packageManager
                        .getApplicationInfo(
                            packageName,
                            0
                        )

                context.packageManager
                    .getApplicationLabel(info)
                    .toString()
                    .trim()
            }.getOrNull()

        return if (
            appName.isNullOrBlank()
        ) {
            readableTechnicalSource(
                rawSource
            )
        } else {
            appName
        }
    }

    private fun extractPackageName(
        rawSource: String
    ): String? {
        return Regex(
            """([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+){1,})"""
        ).find(rawSource)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun readableKnownPackage(
        packageName: String
    ): String? {
        val value =
            packageName.lowercase(
                Locale.ROOT
            )

        return when {
            value == "android" ->
                "Android-System"

            value == "com.whatsapp" ->
                "WhatsApp"

            value == "com.google.android.gm" ->
                "Gmail"

            value.startsWith(
                "com.google.android.gms"
            ) ->
                "Google Play-Dienste"

            value.startsWith(
                "com.android.vending"
            ) ->
                "Google Play Store"

            value.startsWith(
                "com.android.systemui"
            ) ->
                "Android Systemoberfläche"

            value == "com.android.stk2" ->
                "Samsung Telefonie-/SIM-Dienst"

            value.startsWith(
                "com.android.phone"
            ) ->
                "Android Telefoniedienst"

            value.startsWith(
                "com.android.providers.contacts"
            ) ->
                "Android Kontakte"

            value.startsWith(
                "com.android.providers.calendar"
            ) ->
                "Android Kalender"

            value.startsWith(
                "com.samsung."
            ) ||
            value.startsWith(
                "com.sec."
            ) ->
                "Samsung-Systemdienst"

            value.startsWith(
                "com.oplus."
            ) ||
            value.startsWith(
                "com.coloros."
            ) ||
            value.startsWith(
                "com.heytap."
            ) ->
                "OnePlus-Systemdienst"

            else ->
                null
        }
    }

    private fun readableTechnicalSource(
        rawSource: String
    ): String {
        return when {
            rawSource.contains(
                "com.android.stk2",
                ignoreCase = true
            ) ||
                rawSource.contains(
                    "telephony-sem-radio",
                    ignoreCase = true
                ) ||
                rawSource.contains(
                    "RILJ_ACK_WL",
                    ignoreCase = true
                ) ->
                "Samsung Telefonie-/SIM-Dienst"

            rawSource.contains(
                "FMM-acquireWakeLock",
                ignoreCase = true
            ) ||
                rawSource.contains(
                    "OfflineFindTask",
                    ignoreCase = true
                ) ->
                "Samsung Offline-Suche"

            rawSource.contains(
                "gmail-ls",
                ignoreCase = true
            ) ->
                "Gmail"

            rawSource.contains(
                "com.whatsapp",
                ignoreCase = true
            ) ->
                "WhatsApp"

            rawSource.contains(
                "com.google.android.gms",
                ignoreCase = true
            ) ->
                "Google Play-Dienste"

            rawSource.contains(
                "com.android.vending",
                ignoreCase = true
            ) ->
                "Google Play Store"

            rawSource.contains(
                "android/com.android.server",
                ignoreCase = true
            ) ->
                "Android-System"

            rawSource.contains(
                "com.samsung.",
                ignoreCase = true
            ) ||
            rawSource.contains(
                "com.sec.",
                ignoreCase = true
            ) ->
                "Samsung-Systemdienst"

            else ->
                rawSource
                    .substringBefore('/')
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: "Unbekannte Systemquelle"
        }
    }

    private fun formatCpuAwakeDuration(
        durationMillis: Long?
    ): String {
        val value =
            durationMillis
                ?: return "nicht ermittelbar"

        return when {
            value < 1_000L ->
                "${value} ms"

            value < 60_000L ->
                String.format(
                    Locale.getDefault(),
                    "%.1f Sekunden",
                    value / 1_000.0
                )

            else -> {
                val minutes =
                    value / 60_000L

                val seconds =
                    value % 60_000L / 1_000L

                "${minutes} Min ${seconds} Sek"
            }
        }
    }

    private fun readableWakeReason(
        rawReason: String
    ): String {
        val reason =
            rawReason
                .substringAfter(
                    ':',
                    rawReason
                )
                .trim('"')

        return when {
            reason.contains(
                "failed to suspend",
                ignoreCase = true
            ) ->
                "Fehlgeschlagener Ruhezustandsversuch"

            reason.contains(
                "pm8xxx_rtc_alarm",
                ignoreCase = true
            ) ->
                "Geplanter Systemalarm"

            reason.contains(
                "qcom_rx_wakelock",
                ignoreCase = true
            ) ||
                reason.contains(
                    "qrtr_ws",
                    ignoreCase = true
                ) ->
                "Qualcomm Funk-/Modemaktivität"

            reason.contains(
                "timerfd",
                ignoreCase = true
            ) ->
                "Android Timer-/Scheduler-Aktivität"

            reason.contains(
                "userspace-abort",
                ignoreCase = true
            ) ->
                "Android-Systemaktivität"

            reason.contains(
                "NO_SUSPEND",
                ignoreCase = true
            ) &&
                reason.contains(
                    "IRQ",
                    ignoreCase = true
                ) ->
                "Kernel- oder Hardware-Interrupt"

            reason.contains(
                "NO_SUSPEND",
                ignoreCase = true
            ) ->
                "Kernel-/Systemsignal"

            reason.contains(
                "IRQ",
                ignoreCase = true
            ) ->
                "Hardware- oder Interrupt-Signal"

            reason.contains(
                "alarm",
                ignoreCase = true
            ) ->
                "Wakeup-Alarm"

            reason.isBlank() ->
                "Nicht näher bezeichnet"

            else ->
                reason
        }
    }

    private fun cleanSource(
        raw: String
    ): String {
        return raw
            .substringAfter(
                ':',
                raw
            )
            .trim('"')
            .removePrefix("*job*r/")
            .removePrefix("*job*e/")
            .removePrefix("*job*/")
            .removePrefix("*sync*/")
    }

    private fun hasToken(
        raw: String,
        token: String
    ): Boolean {
        val pattern =
            Regex(
                """(^|\s)${Regex.escape(token)}(?=\s|$)"""
            )

        return pattern.containsMatchIn(raw)
    }

    private fun parseHistoryLine(
        raw: String
    ): ParsedHistoryLine? {
        val timestamp =
            timestampRegex
                .find(raw)
                ?.groups
                ?.get("timestamp")
                ?.value
                ?: return null

        val timestampMillis =
            parseTimestamp(timestamp)
                ?: return null

        return ParsedHistoryLine(
            timestampMillis =
                timestampMillis,
            raw = raw
        )
    }

    private fun parseTimestamp(
        value: String
    ): Long? {
        return runCatching {
            val year =
                Calendar.getInstance()
                    .get(Calendar.YEAR)

            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
            ).apply {
                isLenient = false
            }.parse(
                "$year-$value"
            )?.time
        }.getOrNull()
    }

    private fun saveRawDiagnosticLines(
        context: Context,
        parsedLines: List<ParsedHistoryLine>
    ) {
        val interestingLines =
            parsedLines
                .filter { line ->
                    isRawDiagnosticLine(
                        line.raw
                    )
                }
                .takeLast(
                    MAX_RAW_DIAGNOSTIC_LINES
                )
                .map { line ->
                    line.raw.trim()
                }

        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_RAW_DIAGNOSTIC_LINES,
                interestingLines.joinToString(
                    separator = "\n"
                )
            )
            .apply()
    }

    private fun isRawDiagnosticLine(
        raw: String
    ): Boolean {
        val value =
            raw.lowercase(
                Locale.ROOT
            )

        return value.contains(
            "wake_reason="
        ) ||
            hasToken(
                raw = raw,
                token = "+running"
            ) ||
            hasToken(
                raw = raw,
                token = "-running"
            ) ||
            value.contains(
                "+wake_lock="
            ) ||
            value.contains(
                "-wake_lock="
            ) ||
            value.contains(
                "+job="
            ) ||
            value.contains(
                "-job="
            ) ||
            value.contains(
                "+sync="
            ) ||
            value.contains(
                "-sync="
            ) ||
            value.contains(
                "alarm"
            ) ||
            hasToken(
                raw = raw,
                token = "+screen"
            ) ||
            hasToken(
                raw = raw,
                token = "-screen"
            )
    }

    private fun saveDiagnostics(
        context: Context,
        diagnostics: BackgroundParserDiagnostics
    ) {
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putInt(
                KEY_DIAG_PARSED_LINES,
                diagnostics.parsedLines
            )
            .putInt(
                KEY_DIAG_WAKE_REASONS,
                diagnostics.wakeReasons
            )
            .putInt(
                KEY_DIAG_RUNNING_STARTS,
                diagnostics.runningStarts
            )
            .putInt(
                KEY_DIAG_WAKE_LOCKS,
                diagnostics.wakeLocks
            )
            .putInt(
                KEY_DIAG_JOBS,
                diagnostics.jobs
            )
            .putInt(
                KEY_DIAG_SYNCS,
                diagnostics.syncs
            )
            .putInt(
                KEY_DIAG_CANDIDATES,
                diagnostics.candidates
            )
            .putInt(
                KEY_DIAG_EVENTS_CREATED,
                diagnostics.eventsCreated
            )
            .putLong(
                KEY_DIAG_LAST_POLL,
                diagnostics.lastPollMillis
            )
            .apply()
    }

    private data class WakeCandidate(
        val timestampMillis: Long,
        var wakeReason: String?,
        var runningObserved: Boolean,
        val lines:
            MutableList<ParsedHistoryLine>,
        var cpuSleepTimestampMillis: Long? = null
    )

    private data class ParsedHistoryLine(
        val timestampMillis: Long,
        val raw: String
    )

    private data class BackgroundEvidence(
        val type: String,
        val rawSource: String
    )

    private data class ResolvedEvidence(
        val type: String,
        val source: String,
        val technicalSource: String
    )
}
