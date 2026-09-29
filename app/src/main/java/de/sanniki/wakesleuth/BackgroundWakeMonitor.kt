package de.sanniki.wakesleuth

import android.content.Context
import de.sanniki.wakesleuth.data.CpuWakeupCandidate
import de.sanniki.wakesleuth.data.EvidenceCandidate
import de.sanniki.wakesleuth.data.WakelogsData
import de.sanniki.wakesleuth.domain.CpuEvidenceRules
import de.sanniki.wakesleuth.domain.EvidenceOrigin
import de.sanniki.wakesleuth.domain.EvidenceType
import de.sanniki.wakesleuth.domain.PowerKeySignal
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class BackgroundParserDiagnostics(
    val parsedLines: Int = 0,
    val wakeReasons: Int = 0,
    val runningStarts: Int = 0,
    val wakeLocks: Int = 0,
    val jobs: Int = 0,
    val syncs: Int = 0,
    val candidates: Int = 0,
    val eventsCreated: Int = 0,
    val lastPollMillis: Long = 0L,
)

object BackgroundWakeMonitor {
    private const val PREFS_NAME = "wakesleuth_background_wakeups"

    private const val KEY_BASELINE_READY = "baseline_ready"

    private const val KEY_LAST_TIMESTAMP = "last_timestamp"

    /** Session the persisted baseline belongs to. */
    private const val KEY_BASELINE_SESSION_ID = "baseline_session_id"

    private const val KEY_DIAG_PARSED_LINES = "diag_parsed_lines"

    private const val KEY_DIAG_WAKE_REASONS = "diag_wake_reasons"

    private const val KEY_DIAG_RUNNING_STARTS = "diag_running_starts"

    private const val KEY_DIAG_WAKE_LOCKS = "diag_wake_locks"

    private const val KEY_DIAG_JOBS = "diag_jobs"

    private const val KEY_DIAG_SYNCS = "diag_syncs"

    private const val KEY_DIAG_CANDIDATES = "diag_candidates"

    private const val KEY_DIAG_EVENTS_CREATED = "diag_events_created"

    private const val KEY_DIAG_LAST_POLL = "diag_last_poll"

    private const val KEY_RAW_DIAGNOSTIC_LINES = "raw_diagnostic_lines"

    private const val MAX_RAW_DIAGNOSTIC_LINES = 250

    private const val HISTORY_LINES = 3000

    private const val NO_SESSION = -1L

    /*
     * Ein Kandidat wird erst verarbeitet, wenn genügend
     * Folgezeit vergangen ist. So landen Jobs und Syncs
     * nicht erst im nächsten Poll außerhalb des Ereignisses.
     */
    private const val SAFE_TAIL_MILLIS = 6_000L

    private const val EVIDENCE_BEFORE_WINDOW_MILLIS = 1_000L

    private const val EVIDENCE_AFTER_WINDOW_MILLIS = 5_000L

    /*
     * Wake-Reason und +running dürfen bei Samsung und
     * OnePlus auf getrennten BatteryStats-Zeilen stehen.
     */
    private const val WAKE_GROUP_WINDOW_MILLIS = 5_000L

    /*
     * Samsung schreibt Power-Key-Wakelock und +screen
     * häufig auf zwei direkt aufeinanderfolgende Zeilen.
     */
    private const val POWER_KEY_LOOKBACK_MILLIS = 1_000L

    /*
     * BatteryStats-Zeilen tragen kein Jahr. Zeitstempel, die mehr als
     * diese Toleranz in der Zukunft liegen, gehören zum Vorjahr.
     */
    internal const val FUTURE_SLACK_MILLIS = 24 * 60 * 60 * 1_000L

    /** Tolerance for clock differences between the history and this process. */
    internal const val CLOCK_SLACK_MILLIS = 60_000L

    /*
     * Ein Kandidat ohne -running wird höchstens so lange zurückgehalten;
     * danach wird er ohne Schlafzeitpunkt gespeichert.
     */
    internal const val MAX_DEFER_MILLIS = 5 * 60 * 1_000L

    private val pollMutex = Mutex()

    private val timestampRegex =
        Regex(
            """(?<timestamp>\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})""",
        )

    private val wakeReasonRegex =
        Regex(
            """wake_reason=(?<reason>\d+:"[^"]+"|\S+)""",
        )

    private val wakeLockRegex =
        Regex(
            """\+wake_lock=(?<source>\S+:"[^"]+"|\S+)""",
        )

    private val jobRegex =
        Regex(
            """\+job=(?<source>\S+:"[^"]+"|\S+)""",
        )

    private val syncRegex =
        Regex(
            """\+sync=(?<source>\S+:"[^"]+"|\S+)""",
        )

    suspend fun poll(context: Context): Int =
        // The periodic poll and the final poll must never interleave.
        pollMutex.withLock { pollLocked(context) }

    private suspend fun pollLocked(context: Context): Int {
        if (
            ShizukuDiagnostics.state() != ShizukuState.RUNNING_GRANTED
        ) {
            return 0
        }

        val output = runCatching {
            ShizukuDiagnostics.runDiagnosticCommand(
                context = context,
                command = "dumpsys batterystats --history " + "| tail -n $HISTORY_LINES",
            )
        }.getOrNull()
            ?: return 0

        return processHistory(context = context, history = output)
    }

    fun rawDiagnosticLines(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return prefs
            .getString(KEY_RAW_DIAGNOSTIC_LINES, "")
            .orEmpty()
            .lineSequence()
            .map {
                it.trimEnd()
            }.filter {
                it.isNotBlank()
            }.toList()
    }

    fun diagnostics(context: Context): BackgroundParserDiagnostics {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return BackgroundParserDiagnostics(
            parsedLines = prefs.getInt(KEY_DIAG_PARSED_LINES, 0),
            wakeReasons = prefs.getInt(KEY_DIAG_WAKE_REASONS, 0),
            runningStarts = prefs.getInt(KEY_DIAG_RUNNING_STARTS, 0),
            wakeLocks = prefs.getInt(KEY_DIAG_WAKE_LOCKS, 0),
            jobs = prefs.getInt(KEY_DIAG_JOBS, 0),
            syncs = prefs.getInt(KEY_DIAG_SYNCS, 0),
            candidates = prefs.getInt(KEY_DIAG_CANDIDATES, 0),
            eventsCreated = prefs.getInt(KEY_DIAG_EVENTS_CREATED, 0),
            lastPollMillis = prefs.getLong(KEY_DIAG_LAST_POLL, 0L),
        )
    }

    private suspend fun processHistory(
        context: Context,
        history: String,
        now: Long = System.currentTimeMillis(),
    ): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val parsedLines = history
            .lineSequence()
            .mapNotNull { parseHistoryLine(it, now) }
            .sortedBy {
                it.timestampMillis
            }.toList()

        if (parsedLines.isEmpty()) {
            saveDiagnostics(
                context = context,
                diagnostics = BackgroundParserDiagnostics(lastPollMillis = System.currentTimeMillis()),
            )

            return 0
        }

        val newestTimestamp = parsedLines.maxOf { it.timestampMillis }

        saveRawDiagnosticLines(context = context, parsedLines = parsedLines)

        val safeCutoff = safeCutoff(newestTimestamp = newestTimestamp, now = now)

        val fullDiagnostics = BackgroundParserDiagnostics(
            parsedLines = parsedLines.size,
            wakeReasons = parsedLines.count {
                wakeReasonRegex.containsMatchIn(it.raw)
            },
            runningStarts = parsedLines.count {
                hasToken(raw = it.raw, token = "+running")
            },
            wakeLocks = parsedLines.count {
                wakeLockRegex.containsMatchIn(it.raw)
            },
            jobs = parsedLines.count {
                jobRegex.containsMatchIn(it.raw)
            },
            syncs = parsedLines.count {
                syncRegex.containsMatchIn(it.raw)
            },
            lastPollMillis = System.currentTimeMillis(),
        )

        val data = WakelogsData.get(context)
        val session = data.database.sessionDao().recordingSession()

        if (session == null) {
            // Nothing can be recorded; keep the position so no wakeup is skipped.
            saveDiagnostics(context = context, diagnostics = fullDiagnostics)

            return 0
        }

        // The baseline belongs to one session; a new session must not
        // import the history that predates it.
        if (prefs.getLong(KEY_BASELINE_SESSION_ID, NO_SESSION) != session.id) {
            prefs
                .edit()
                .putLong(KEY_BASELINE_SESSION_ID, session.id)
                .putBoolean(KEY_BASELINE_READY, false)
                .remove(KEY_LAST_TIMESTAMP)
                .apply()
        }

        if (
            !prefs.getBoolean(KEY_BASELINE_READY, false)
        ) {
            prefs
                .edit()
                .putBoolean(KEY_BASELINE_READY, true)
                .putLong(KEY_LAST_TIMESTAMP, safeCutoff)
                .apply()

            saveDiagnostics(context = context, diagnostics = fullDiagnostics)

            return 0
        }

        val previousTimestamp = healLastTimestamp(
            stored = prefs.getLong(KEY_LAST_TIMESTAMP, 0L),
            safeCutoff = safeCutoff,
            now = now,
        )

        /*
         * Der Bildschirmzustand wird anhand der gesamten
         * eingelesenen Historie rekonstruiert, nicht nur
         * anhand der neuen Zeilen.
         */
        var screenOn = false
        var screenStateKnown = false

        val wakeCandidates = mutableListOf<WakeCandidate>()

        parsedLines.forEach { line ->
            val screenTurnedOn = hasToken(raw = line.raw, token = "+screen")

            val screenTurnedOff = hasToken(raw = line.raw, token = "-screen")

            if (screenTurnedOn) {
                screenOn = true
                screenStateKnown = true
            } else if (screenTurnedOff) {
                screenOn = false
                screenStateKnown = true
            }

            if (
                line.timestampMillis <= previousTimestamp || line.timestampMillis >
                safeCutoff
            ) {
                return@forEach
            }

            val wakeReason = wakeReasonRegex
                .find(line.raw)
                ?.groups
                ?.get("reason")
                ?.value
                ?.trim()

            val runningStarted = hasToken(raw = line.raw, token = "+running")

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
                val powerEvidenceLines = parsedLines
                    .asSequence()
                    .filter { evidenceLine ->
                        evidenceLine.timestampMillis >= line.timestampMillis -
                            POWER_KEY_LOOKBACK_MILLIS && evidenceLine.timestampMillis <= line.timestampMillis
                    }.toList()

                val combinedPowerEvidence = powerEvidenceLines
                    .joinToString(
                        separator = "\\n",
                    ) { evidenceLine ->
                        evidenceLine.raw
                    }

                val hasPowerKeyWakeLock =
                    combinedPowerEvidence.contains("PhoneWindowManager.mPowerKeyWakeLock", ignoreCase = true) ||
                        combinedPowerEvidence.contains("mPowerKeyWakeLock", ignoreCase = true)

                val hasPmicPowerKey = combinedPowerEvidence.contains("pmic_pwrkey", ignoreCase = true)

                val hasSamsungPolicyPower = combinedPowerEvidence.contains("screenwake=", ignoreCase = true) &&
                    combinedPowerEvidence.contains("android.policy:POWER", ignoreCase = true)

                val hasDisplayReasonKey = combinedPowerEvidence.contains("display_state_changed=", ignoreCase = true) &&
                    combinedPowerEvidence.contains("reason=KEY", ignoreCase = true)

                if (
                    hasPowerKeyWakeLock || hasPmicPowerKey || hasSamsungPolicyPower || hasDisplayReasonKey
                ) {
                    val technicalTag = powerEvidenceLines
                        .asSequence()
                        .mapNotNull { evidenceLine ->
                            wakeLockRegex
                                .find(evidenceLine.raw)
                                ?.groups
                                ?.get("source")
                                ?.value
                                ?.let(::cleanSource)
                        }.firstOrNull { source ->
                            source.contains("mPowerKeyWakeLock", ignoreCase = true)
                        }

                    val pmicReason = powerEvidenceLines
                        .asSequence()
                        .mapNotNull { evidenceLine ->
                            wakeReasonRegex
                                .find(evidenceLine.raw)
                                ?.groups
                                ?.get("reason")
                                ?.value
                                ?.trim()
                        }.firstOrNull { reason ->
                            reason.contains("pmic_pwrkey", ignoreCase = true)
                        }

                    val signal = when {
                        pmicReason != null -> {
                            PowerKeySignal.PMIC_PWRKEY
                        }

                        hasSamsungPolicyPower -> {
                            PowerKeySignal.POLICY_POWER
                        }

                        hasDisplayReasonKey -> {
                            PowerKeySignal.DISPLAY_REASON_KEY
                        }

                        else -> {
                            PowerKeySignal.POWER_KEY_WAKELOCK
                        }
                    }

                    WakelogsData
                        .get(context)
                        .recorder
                        .attachBatteryStatsPowerKey(
                            powerKeyAt = line.timestampMillis,
                            signal = signal,
                            rawReason = pmicReason,
                            rawTag = technicalTag,
                        )
                }
            }

            /*
             * Kein CPU-Hintergrund-Wakeup, wenn die gleiche
             * Zeile das sichtbare Display bereits einschaltet.
             */
            val qualifies = screenStateKnown && !screenOn && !screenTurnedOn && (wakeReason != null || runningStarted)

            if (!qualifies) {
                return@forEach
            }

            val nearbyCandidate = wakeCandidates
                .lastOrNull()
                ?.takeIf { candidate ->
                    line.timestampMillis -
                        candidate.timestampMillis <= WAKE_GROUP_WINDOW_MILLIS
                }

            if (nearbyCandidate != null) {
                nearbyCandidate.lines.add(line)

                if (
                    nearbyCandidate.wakeReason == null && wakeReason != null
                ) {
                    nearbyCandidate.wakeReason = wakeReason
                }

                if (runningStarted) {
                    nearbyCandidate.runningObserved = true
                }
            } else {
                wakeCandidates.add(
                    WakeCandidate(
                        timestampMillis = line.timestampMillis,
                        wakeReason = wakeReason,
                        runningObserved = runningStarted,
                        lines = mutableListOf(line),
                    ),
                )
            }
        }

        wakeCandidates.forEach { candidate ->
            val nextCpuSleep = parsedLines.firstOrNull { line ->
                line.timestampMillis >
                    candidate.timestampMillis && hasToken(raw = line.raw, token = "-running")
            }

            candidate.cpuSleepTimestampMillis = nextCpuSleep?.timestampMillis

            val evidenceEnd = minOf(
                candidate.timestampMillis + EVIDENCE_AFTER_WINDOW_MILLIS,
                nextCpuSleep?.timestampMillis
                    ?: Long.MAX_VALUE,
            )

            parsedLines
                .asSequence()
                .filter { line ->
                    line.timestampMillis >= candidate.timestampMillis -
                        EVIDENCE_BEFORE_WINDOW_MILLIS && line.timestampMillis <= evidenceEnd
                }.forEach { line ->
                    if (
                        candidate.lines.none { it.raw == line.raw }
                    ) {
                        candidate.lines.add(line)
                    }

                    val reason = wakeReasonRegex
                        .find(line.raw)
                        ?.groups
                        ?.get("reason")
                        ?.value
                        ?.trim()

                    if (
                        candidate.wakeReason == null && reason != null
                    ) {
                        candidate.wakeReason = reason
                    }

                    if (
                        hasToken(raw = line.raw, token = "+running")
                    ) {
                        candidate.runningObserved = true
                    }
                }
        }

        val batch = batchCandidates(
            candidates = wakeCandidates,
            sessionStartedAt = session.startedAt,
            now = now,
        )

        var added = 0

        // Position up to which everything is settled; unresolved or
        // unrecorded candidates hold it back so the next poll sees them again.
        var nextTimestamp = safeCutoff

        batch.deferredFrom?.let { nextTimestamp = minOf(nextTimestamp, it - 1L) }

        for (candidate in batch.ready) {
            if (
                shouldSkipVisibleWakeEvent(candidate)
            ) {
                continue
            }

            val eventId = data.recorder.recordCpuWakeup(toCpuWakeupCandidate(candidate))

            if (eventId != null) {
                added += 1
            }
        }

        prefs.edit().putLong(KEY_LAST_TIMESTAMP, nextTimestamp).apply()

        saveDiagnostics(
            context = context,
            diagnostics = fullDiagnostics.copy(candidates = wakeCandidates.size, eventsCreated = added),
        )

        return added
    }

    /**
     * Result of [batchCandidates]: candidates that can be recorded now and
     * the timestamp of the oldest candidate that still waits for its
     * `-running` line, if any (it and everything after it is deferred).
     */
    internal data class CandidateBatch(
        val ready: List<WakeCandidate>,
        val deferredFrom: Long?,
    )

    /**
     * Drops candidates that started before the session, and defers those
     * whose CPU sleep line has not shown up yet (up to [MAX_DEFER_MILLIS]).
     */
    internal fun batchCandidates(
        candidates: List<WakeCandidate>,
        sessionStartedAt: Long,
        now: Long,
    ): CandidateBatch {
        val ready = mutableListOf<WakeCandidate>()

        for (candidate in candidates.sortedBy { it.timestampMillis }) {
            if (candidate.timestampMillis < sessionStartedAt) {
                continue
            }

            val unresolved = candidate.cpuSleepTimestampMillis == null &&
                now - candidate.timestampMillis < MAX_DEFER_MILLIS

            if (unresolved) {
                return CandidateBatch(ready = ready, deferredFrom = candidate.timestampMillis)
            }

            ready.add(candidate)
        }

        return CandidateBatch(ready = ready, deferredFrom = null)
    }

    /** Newest settled timestamp; never beyond the present, whatever the history claims. */
    internal fun safeCutoff(
        newestTimestamp: Long,
        now: Long,
    ): Long = minOf(newestTimestamp, now + CLOCK_SLACK_MILLIS) - SAFE_TAIL_MILLIS

    /** A persisted position in the future would stop the polling for good. */
    internal fun healLastTimestamp(
        stored: Long,
        safeCutoff: Long,
        now: Long,
    ): Long =
        if (stored > now + CLOCK_SLACK_MILLIS) {
            minOf(stored, safeCutoff)
        } else {
            stored
        }

    private fun shouldSkipVisibleWakeEvent(candidate: WakeCandidate): Boolean {
        val durationMillis = candidate.cpuSleepTimestampMillis
            ?.minus(
                candidate.timestampMillis,
            )?.takeIf {
                it >= 0L
            }

        val combinedRaw = candidate.lines
            .joinToString(
                separator = "\n",
            ) {
                it.raw
            }

        val hasPowerKey = combinedRaw.contains("pmic_pwrkey", ignoreCase = true) ||
            combinedRaw.contains("PhoneWindowManager.mPowerKeyWakeLock", ignoreCase = true)

        /*
         * Ein Power-Tastendruck gehört zum sichtbaren
         * Display-Ereignis und ist kein eigenständiger
         * CPU-Hintergrund-Wakeup.
         */
        if (hasPowerKey) {
            return true
        }

        val wakeLocks = candidate.lines.any { line -> wakeLockRegex.containsMatchIn(line.raw) }

        val jobs = candidate.lines.any { line -> jobRegex.containsMatchIn(line.raw) }

        val syncs = candidate.lines.any { line -> syncRegex.containsMatchIn(line.raw) }

        val reason = candidate.wakeReason.orEmpty()

        val pureTimerActivity = reason.contains("timerfd", ignoreCase = true) && !wakeLocks && !jobs && !syncs

        /*
         * Sehr kurze, quellenlose Timerimpulse sind technisch
         * real, bringen in der sichtbaren Ereignisliste aber
         * kaum Erkenntnis und erzeugen auf Samsung viel Rauschen.
         */
        return pureTimerActivity && durationMillis != null && durationMillis < 250L
    }

    private fun toCpuWakeupCandidate(candidate: WakeCandidate): CpuWakeupCandidate {
        val sortedLines = candidate.lines.sortedBy { it.timestampMillis }

        fun sources(regex: Regex): List<String> =
            sortedLines
                .mapNotNull { line ->
                    regex
                        .find(line.raw)
                        ?.groups
                        ?.get("source")
                        ?.value
                        ?.let(::cleanSource)
                }.distinct()

        val evidence = buildList {
            sources(wakeLockRegex).forEach { source ->
                add(
                    EvidenceCandidate(
                        origin = EvidenceOrigin.WAKELOCK,
                        type = CpuEvidenceRules.typeOfWakeLock(source),
                        rawSource = source,
                    ),
                )
            }

            sources(jobRegex).forEach { source ->
                add(
                    EvidenceCandidate(
                        origin = EvidenceOrigin.JOB,
                        type = CpuEvidenceRules.typeOfJob(source),
                        rawSource = source,
                    ),
                )
            }

            sources(syncRegex).forEach { source ->
                add(EvidenceCandidate(origin = EvidenceOrigin.SYNC, type = EvidenceType.SYNC, rawSource = source))
            }
        }

        return CpuWakeupCandidate(
            occurredAt = candidate.timestampMillis,
            rawWakeReason = candidate.wakeReason,
            runningObserved = candidate.runningObserved,
            returnedToSleepAt = candidate.cpuSleepTimestampMillis,
            evidence = evidence,
        )
    }

    private fun cleanSource(raw: String): String =
        raw
            .substringAfter(':', raw)
            .trim('"')
            .removePrefix("*job*r/")
            .removePrefix("*job*e/")
            .removePrefix("*job*/")
            .removePrefix("*sync*/")

    private fun hasToken(
        raw: String,
        token: String,
    ): Boolean {
        val pattern =
            Regex(
                """(^|\s)${Regex.escape(token)}(?=\s|$)""",
            )

        return pattern.containsMatchIn(raw)
    }

    internal fun parseHistoryLine(
        raw: String,
        now: Long = System.currentTimeMillis(),
    ): ParsedHistoryLine? {
        val timestamp = timestampRegex
            .find(raw)
            ?.groups
            ?.get("timestamp")
            ?.value
            ?: return null

        val timestampMillis = parseTimestamp(timestamp, now)
            ?: return null

        return ParsedHistoryLine(timestampMillis = timestampMillis, raw = raw)
    }

    /**
     * History lines carry no year. The current year is used unless that
     * puts the time more than a day into the future (a December line seen
     * in early January), then the previous year applies.
     */
    internal fun parseTimestamp(
        value: String,
        now: Long = System.currentTimeMillis(),
    ): Long? {
        val year = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)

        // The previous year also covers 29 February in a non-leap year.
        return listOf(year, year - 1)
            .firstNotNullOfOrNull { candidateYear ->
                runCatching {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                        .apply { isLenient = false }
                        .parse("$candidateYear-$value")
                        ?.time
                }.getOrNull()?.takeIf { it <= now + FUTURE_SLACK_MILLIS }
            }
    }

    private fun saveRawDiagnosticLines(
        context: Context,
        parsedLines: List<ParsedHistoryLine>,
    ) {
        val interestingLines = parsedLines
            .filter { line ->
                isRawDiagnosticLine(line.raw)
            }.takeLast(
                MAX_RAW_DIAGNOSTIC_LINES,
            ).map { line ->
                line.raw.trim()
            }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RAW_DIAGNOSTIC_LINES, interestingLines.joinToString(separator = "\n"))
            .apply()
    }

    private fun isRawDiagnosticLine(raw: String): Boolean {
        val value = raw.lowercase(Locale.ROOT)

        return value.contains("wake_reason=") ||
            hasToken(raw = raw, token = "+running") ||
            hasToken(raw = raw, token = "-running") ||
            value.contains("+wake_lock=") ||
            value.contains("-wake_lock=") ||
            value.contains("+job=") ||
            value.contains("-job=") ||
            value.contains("+sync=") ||
            value.contains("-sync=") ||
            value.contains("alarm") || hasToken(raw = raw, token = "+screen") || hasToken(raw = raw, token = "-screen")
    }

    private fun saveDiagnostics(
        context: Context,
        diagnostics: BackgroundParserDiagnostics,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(
                KEY_DIAG_PARSED_LINES,
                diagnostics.parsedLines,
            ).putInt(
                KEY_DIAG_WAKE_REASONS,
                diagnostics.wakeReasons,
            ).putInt(
                KEY_DIAG_RUNNING_STARTS,
                diagnostics.runningStarts,
            ).putInt(
                KEY_DIAG_WAKE_LOCKS,
                diagnostics.wakeLocks,
            ).putInt(
                KEY_DIAG_JOBS,
                diagnostics.jobs,
            ).putInt(
                KEY_DIAG_SYNCS,
                diagnostics.syncs,
            ).putInt(
                KEY_DIAG_CANDIDATES,
                diagnostics.candidates,
            ).putInt(
                KEY_DIAG_EVENTS_CREATED,
                diagnostics.eventsCreated,
            ).putLong(
                KEY_DIAG_LAST_POLL,
                diagnostics.lastPollMillis,
            ).apply()
    }

    internal data class WakeCandidate(
        val timestampMillis: Long,
        var wakeReason: String?,
        var runningObserved: Boolean,
        val lines: MutableList<ParsedHistoryLine>,
        var cpuSleepTimestampMillis: Long? = null,
    )

    internal data class ParsedHistoryLine(
        val timestampMillis: Long,
        val raw: String,
    )
}
