package de.sanniki.wakesleuth

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.annotation.StringRes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

data class WakeLockHistoryEntry(
    val startTimestamp: String,
    val startTimestampMillis: Long,
    val endTimestamp: String?,
    val endTimestampMillis: Long?,
    val durationMillis: Long?,
    val packageName: String,
    val tag: String,
    val wakeLockType: String,
    val causesWake: Boolean,
    val stillActive: Boolean
)

data class WakeLockDiagnostic(
    val activeCount: Int,
    val lastTimestamp: String?,
    val lastPackage: String?,
    val lastTag: String?,
    val rawLastEntry: String?,
    val lastTimestampMillis: Long? = null,
    val historyEntries: List<WakeLockHistoryEntry> = emptyList(),
    val error: String? = null
)

data class WakeupAlarmDiagnostic(
    val packageName: String?,
    val tag: String?,
    val ageMillis: Long?,
    val wakeCount: Int,
    val packageWakeups: Int,
    val triggerTimestampMillis: Long? = null,
    val error: String? = null
)

data class BackgroundJobDiagnostic(
    val packageName: String?,
    val serviceName: String?,
    val ageMillis: Long?,
    val prioritized: Boolean,
    val rawEntry: String?,
    val triggerTimestampMillis: Long? = null,
    val error: String? = null
)

data class WakeReasonDiagnostic(
    val reason: String?,
    val details: String?,
    val timestamp: String?,
    val timestampMillis: Long? = null,
    val rawEntry: String?,
    val error: String? = null
)

data class NetworkTrafficEntry(
    val uid: Int,
    val packageName: String?,
    val appLabel: String?,
    val rxBytes: Long,
    val txBytes: Long,
    val totalBytes: Long
)

data class NetworkStatsDiagnostic(
    val entries: List<NetworkTrafficEntry>,
    val error: String? = null
)

private data class ParsedWakeupAlarm(
    val packageName: String,
    val tag: String,
    val ageMillis: Long,
    val wakeCount: Int,
    val packageWakeups: Int
)

enum class ShizukuState {
    RUNNING_GRANTED,
    RUNNING_DENIED,
    NOT_RUNNING
}

private data class ParsedWakeLock(
    val rawEntry: String,
    val timestamp: String,
    val timestampMillis: Long,
    val packageName: String,
    val tag: String
)

private data class RawWakeLockHistoryLine(
    val timestamp: String,
    val timestampMillis: Long,
    val packageName: String,
    val action: String,
    val tag: String,
    val wakeLockType: String,
    val causesWake: Boolean
)

object ShizukuDiagnostics {

    const val REQUEST_CODE = 6201

    private const val USER_SERVICE_TAG =
        "wakesleuth_shell"

    private const val USER_SERVICE_VERSION = 1

    private const val MATCH_WINDOW_MILLIS = 5_000L

    @Volatile
    private var remoteService: IWakeSleuthShell? = null

    @Volatile
    private var binding = false

    private var pendingConnection =
        CompletableDeferred<IWakeSleuthShell>()

    private val serviceConnection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?
            ) {
                val service =
                    IWakeSleuthShell.Stub.asInterface(
                        binder
                    )

                remoteService = service
                binding = false

                if (!pendingConnection.isCompleted) {
                    pendingConnection.complete(service)
                }
            }

            override fun onServiceDisconnected(
                name: ComponentName?
            ) {
                remoteService = null
                binding = false
                pendingConnection =
                    CompletableDeferred()
            }
        }

    fun state(): ShizukuState {
        if (
            !runCatching {
                Shizuku.pingBinder()
            }.getOrDefault(false)
        ) {
            return ShizukuState.NOT_RUNNING
        }

        return if (
            runCatching {
                Shizuku.checkSelfPermission()
            }.getOrDefault(
                PackageManager.PERMISSION_DENIED
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            ShizukuState.RUNNING_GRANTED
        } else {
            ShizukuState.RUNNING_DENIED
        }
    }

    fun requestPermission() {
        if (
            runCatching {
                Shizuku.pingBinder()
            }.getOrDefault(false)
        ) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    suspend fun readWakeLocks(
        context: Context,
        referenceTimestamp: Long? = null
    ): WakeLockDiagnostic =
        withContext(Dispatchers.IO) {

            if (
                state() ==
                ShizukuState.NOT_RUNNING
            ) {
                return@withContext errorResult(
                    context.getString(
                        R.string.shizuku_error_not_running
                    )
                )
            }

            if (
                state() !=
                ShizukuState.RUNNING_GRANTED
            ) {
                return@withContext errorResult(
                    context.getString(
                        R.string.shizuku_error_permission_missing
                    )
                )
            }

            runCatching {
                val service =
                    getOrBindService(context)

                val output =
                    service.runCommand(
                        "timeout 5s dumpsys power"
                    )

                parsePowerDump(
                    context = context,
                    output = output,
                    ownPackageName = context.packageName,
                    referenceTimestamp =
                        referenceTimestamp
                )
            }.getOrElse { throwable ->
                remoteService = null

                errorResult(
                    failureMessage(
                        context = context,
                        throwable = throwable,
                        fallback =
                            R.string.shizuku_error_unknown
                    )
                )
            }
        }

    suspend fun readWakeupAlarms(
        context: Context
    ): WakeupAlarmDiagnostic =
        withContext(Dispatchers.IO) {

            if (
                state() ==
                ShizukuState.NOT_RUNNING
            ) {
                return@withContext alarmErrorResult(
                    context.getString(
                        R.string.shizuku_error_not_running
                    )
                )
            }

            if (
                state() !=
                ShizukuState.RUNNING_GRANTED
            ) {
                return@withContext alarmErrorResult(
                    context.getString(
                        R.string.shizuku_error_permission_missing
                    )
                )
            }

            runCatching {
                val service =
                    getOrBindService(context)

                val output =
                    service.runCommand(
                        "timeout 5s dumpsys alarm"
                    )

                parseAlarmDump(
                    context = context,
                    output = output,
                    ownPackageName =
                        context.packageName
                )
            }.getOrElse { throwable ->
                remoteService = null

                alarmErrorResult(
                    failureMessage(
                        context = context,
                        throwable = throwable,
                        fallback =
                            R.string.shizuku_error_unknown_alarm
                    )
                )
            }
        }

    suspend fun readBackgroundJobs(
        context: Context
    ): BackgroundJobDiagnostic =
        withContext(Dispatchers.IO) {

            if (
                state() ==
                ShizukuState.NOT_RUNNING
            ) {
                return@withContext jobErrorResult(
                    context.getString(
                        R.string.shizuku_error_not_running
                    )
                )
            }

            if (
                state() !=
                ShizukuState.RUNNING_GRANTED
            ) {
                return@withContext jobErrorResult(
                    context.getString(
                        R.string.shizuku_error_permission_missing
                    )
                )
            }

            runCatching {
                val service =
                    getOrBindService(context)

                /*
                 * Der vollständige JobScheduler-Dump kann mehrere
                 * Megabyte groß sein und damit die Binder-Grenze
                 * überschreiten. Für unsere Diagnose werden nur
                 * historische START- und START-P-Zeilen benötigt.
                 */
                val output =
                    service.runCommand(
                        "timeout 5s dumpsys jobscheduler " +
                            "| grep -E 'START(-P)?:' " +
                            "| tail -n 250"
                    )

                parseJobSchedulerDump(
                    output = output,
                    ownPackageName =
                        context.packageName
                )
            }.getOrElse { throwable ->
                remoteService = null

                jobErrorResult(
                    failureMessage(
                        context = context,
                        throwable = throwable,
                        fallback =
                            R.string.shizuku_error_unknown_jobscheduler
                    )
                )
            }
        }

    suspend fun readNetworkStats(
        context: Context,
        maxEntries: Int? = 8
    ): NetworkStatsDiagnostic =
        withContext(Dispatchers.IO) {

            if (
                state() ==
                ShizukuState.NOT_RUNNING
            ) {
                return@withContext networkStatsErrorResult(
                    context.getString(
                        R.string.shizuku_error_not_running
                    )
                )
            }

            if (
                state() !=
                ShizukuState.RUNNING_GRANTED
            ) {
                return@withContext networkStatsErrorResult(
                    context.getString(
                        R.string.shizuku_error_permission_missing
                    )
                )
            }

            runCatching {
                val service =
                    getOrBindService(context)

                /*
                 * Der komplette netstats-Dump ist groß.
                 * Für die Diagnose genügt die UID-Traffic-Tabelle.
                 */
                val output =
                    service.runCommand(
                        "echo __NETSTATS__; " +
                            "timeout 6s dumpsys netstats " +
                            "| sed -n '/mAppUidStatsMap:/,/mStatsMapA:/p' " +
                            "| head -n 260; " +
                            "echo __PACKAGES__; " +
                            "timeout 5s cmd package list packages -U"
                    )

                parseNetworkStatsDump(
                    output = output,
                    context = context,
                    maxEntries = maxEntries
                )
            }.getOrElse { throwable ->
                remoteService = null

                networkStatsErrorResult(
                    failureMessage(
                        context = context,
                        throwable = throwable,
                        fallback =
                            R.string.shizuku_error_unknown_netstats
                    )
                )
            }
        }

    suspend fun readWakeReason(
        context: Context
    ): WakeReasonDiagnostic =
        withContext(Dispatchers.IO) {

            if (
                state() ==
                ShizukuState.NOT_RUNNING
            ) {
                return@withContext wakeReasonErrorResult(
                    context.getString(
                        R.string.shizuku_error_not_running
                    )
                )
            }

            if (
                state() !=
                ShizukuState.RUNNING_GRANTED
            ) {
                return@withContext wakeReasonErrorResult(
                    context.getString(
                        R.string.shizuku_error_permission_missing
                    )
                )
            }

            runCatching {
                val service =
                    getOrBindService(context)

                /*
                 * Nur direkte PowerManager-Aufweckzeilen
                 * abrufen. Dadurch bleibt die Binder-Antwort
                 * klein, auch wenn Logcat sehr groß ist.
                 */
                val output =
                    service.runCommand(
                        "timeout 5s logcat -d -b system " +
                            "-v threadtime " +
                            "| grep -E " +
                            "'(PowerManagerService|PowerGroup): " +
                            "Waking up' " +
                            "| tail -n 50"
                    )

                parseWakeReasonLog(output)
            }.getOrElse { throwable ->
                remoteService = null

                wakeReasonErrorResult(
                    failureMessage(
                        context = context,
                        throwable = throwable,
                        fallback =
                            R.string.shizuku_error_unknown_wake_reason
                    )
                )
            }
        }

    suspend fun runDiagnosticCommand(
        context: Context,
        command: String
    ): String =
        withContext(Dispatchers.IO) {
            if (
                state() !=
                ShizukuState.RUNNING_GRANTED
            ) {
                throw IllegalStateException(
                    context.getString(
                        R.string.shizuku_error_diagnostics_unavailable
                    )
                )
            }

            runCatching {
                getOrBindService(context)
                    .runCommand(command)
            }.getOrElse { throwable ->
                remoteService = null

                throw IllegalStateException(
                    failureMessage(
                        context = context,
                        throwable = throwable,
                        fallback =
                            R.string.shizuku_error_diagnostic_command_failed
                    ),
                    throwable
                )
            }
        }

    private suspend fun getOrBindService(
        context: Context
    ): IWakeSleuthShell {
        remoteService?.let {
            return it
        }

        synchronized(this) {
            remoteService?.let {
                return it
            }

            if (!binding) {
                binding = true

                if (
                    pendingConnection.isCompleted
                ) {
                    pendingConnection =
                        CompletableDeferred()
                }

                val args =
                    Shizuku.UserServiceArgs(
                        ComponentName(
                            context,
                            WakeSleuthUserService::class.java
                        )
                    )
                        .daemon(false)
                        .processNameSuffix(
                            "wakesleuth"
                        )
                        .debuggable(
                            BuildConfig.DEBUG
                        )
                        .version(
                            USER_SERVICE_VERSION
                        )
                        .tag(
                            USER_SERVICE_TAG
                        )

                try {
                    Shizuku.bindUserService(
                        args,
                        serviceConnection
                    )
                } catch (throwable: Throwable) {
                    binding = false
                    throw throwable
                }
            }
        }

        return try {
            withTimeout(10_000L) {
                pendingConnection.await()
            }
        } catch (
            throwable: TimeoutCancellationException
        ) {
            binding = false

            throw IllegalStateException(
                context.getString(
                    R.string.shizuku_error_user_service_not_responding
                )
            )
        }
    }

    private fun failureMessage(
        context: Context,
        throwable: Throwable,
        @StringRes fallback: Int
    ): String {
        val message =
            throwable.message
                ?: return context.getString(
                    fallback
                )

        if (
            !message.startsWith(
                WakeSleuthUserService.EXIT_CODE_ERROR_PREFIX
            )
        ) {
            return message
        }

        return context.getString(
            R.string.shizuku_error_shell_command_failed,
            message.removePrefix(
                WakeSleuthUserService.EXIT_CODE_ERROR_PREFIX
            )
        )
    }

    private fun parsePowerDump(
        context: Context,
        output: String,
        ownPackageName: String,
        referenceTimestamp: Long?
    ): WakeLockDiagnostic {
        val lines = output.lines()

        val activeCount = lines
            .firstOrNull { line ->
                line.trim().startsWith(
                    "Wake Locks: size="
                )
            }
            ?.substringAfter("size=")
            ?.trim()
            ?.toIntOrNull()
            ?: 0

        val logStart =
            lines.indexOfFirst { line ->
                line.trim() ==
                    "Partial Wakelock Log:"
            }

        val parsedEntries =
            if (logStart >= 0) {
                lines.drop(logStart + 1)
                    .mapNotNull { line ->
                        parseWakeLockLine(
                            line = line.trim(),
                            ownPackageName =
                                ownPackageName
                        )
                    }
            } else {
                emptyList()
            }

        val selectedEntry =
            selectBestEntry(
                entries = parsedEntries,
                referenceTimestamp =
                    referenceTimestamp
            )

        val historyEntries =
            parseWakeLockHistory(
                context = context,
                lines = lines,
                ownPackageName =
                    ownPackageName
            )

        return WakeLockDiagnostic(
            activeCount = activeCount,
            lastTimestamp =
                selectedEntry?.timestamp,
            lastPackage =
                selectedEntry?.packageName,
            lastTag =
                selectedEntry?.tag,
            rawLastEntry =
                selectedEntry?.rawEntry,
            lastTimestampMillis =
                selectedEntry?.timestampMillis,
            historyEntries =
                historyEntries
        )
    }

    private fun parseWakeLockHistory(
        context: Context,
        lines: List<String>,
        ownPackageName: String
    ): List<WakeLockHistoryEntry> {
        val partialStart =
            lines.indexOfFirst { line ->
                line.trim() ==
                    "Partial Wakelock Log:"
            }

        val fullStart =
            lines.indexOfFirst { line ->
                line.trim() ==
                    "Full Wakelock Log:"
            }

        val historyLines =
            buildList {
                if (partialStart >= 0) {
                    val end =
                        if (
                            fullStart > partialStart
                        ) {
                            fullStart
                        } else {
                            lines.size
                        }

                    addAll(
                        lines.subList(
                            partialStart + 1,
                            end
                        )
                    )
                }

                if (fullStart >= 0) {
                    val endOffset =
                        lines.drop(fullStart + 1)
                            .indexOfFirst { line ->
                                val trimmed =
                                    line.trim()

                                trimmed.endsWith(":") &&
                                    !trimmed.matches(
                                        WAKELOCK_HISTORY_LINE_REGEX
                                    )
                            }

                    val end =
                        if (endOffset >= 0) {
                            fullStart + 1 +
                                endOffset
                        } else {
                            lines.size
                        }

                    addAll(
                        lines.subList(
                            fullStart + 1,
                            end
                        )
                    )
                }
            }

        val parsed =
            historyLines.mapNotNull { rawLine ->
                parseWakeLockHistoryLine(
                    context = context,
                    line = rawLine.trim(),
                    ownPackageName =
                        ownPackageName
                )
            }
                .distinctBy { entry ->
                    entry.timestampMillis.toString() +
                        "|" +
                        entry.packageName.lowercase(
                            Locale.ROOT
                        ) +
                        "|" +
                        entry.action +
                        "|" +
                        entry.tag.lowercase(
                            Locale.ROOT
                        ) +
                        "|" +
                        entry.wakeLockType
                }
                .sortedBy {
                    it.timestampMillis
                }

        data class OpenWakeLock(
            val line: RawWakeLockHistoryLine
        )

        val openByKey =
            mutableMapOf<
                String,
                ArrayDeque<OpenWakeLock>
            >()

        val completed =
            mutableListOf<WakeLockHistoryEntry>()

        parsed.forEach { entry ->
            val key =
                entry.packageName.lowercase(
                    Locale.ROOT
                ) +
                    "|" +
                    entry.tag.lowercase(
                        Locale.ROOT
                    )

            if (entry.action == "ACQ") {
                openByKey
                    .getOrPut(key) {
                        ArrayDeque()
                    }
                    .addLast(
                        OpenWakeLock(entry)
                    )
            } else {
                val queue =
                    openByKey[key]

                val start =
                    if (
                        queue != null &&
                        queue.isNotEmpty()
                    ) {
                        queue.removeFirst().line
                    } else {
                        null
                    }

                if (start != null) {
                    val durationMillis =
                        entry.timestampMillis -
                            start.timestampMillis

                    if (
                        durationMillis >= 0L &&
                        durationMillis <=
                            7L * 24L * 60L * 60L * 1000L
                    ) {
                        completed.add(
                            WakeLockHistoryEntry(
                                startTimestamp =
                                    start.timestamp,
                                startTimestampMillis =
                                    start.timestampMillis,
                                endTimestamp =
                                    entry.timestamp,
                                endTimestampMillis =
                                    entry.timestampMillis,
                                durationMillis =
                                    durationMillis,
                                packageName =
                                    start.packageName,
                                tag =
                                    start.tag,
                                wakeLockType =
                                    start.wakeLockType,
                                causesWake =
                                    start.causesWake,
                                stillActive =
                                    false
                            )
                        )
                    }
                }
            }
        }

        openByKey.values
            .flatMap { queue ->
                queue.map {
                    it.line
                }
            }
            .forEach { start ->
                completed.add(
                    WakeLockHistoryEntry(
                        startTimestamp =
                            start.timestamp,
                        startTimestampMillis =
                            start.timestampMillis,
                        endTimestamp = null,
                        endTimestampMillis = null,
                        durationMillis = null,
                        packageName =
                            start.packageName,
                        tag =
                            start.tag,
                        wakeLockType =
                            start.wakeLockType,
                        causesWake =
                            start.causesWake,
                        stillActive =
                            true
                    )
                )
            }

        return completed
            .sortedByDescending {
                it.startTimestampMillis
            }
            .take(30)
    }

    private fun parseWakeLockHistoryLine(
        context: Context,
        line: String,
        ownPackageName: String
    ): RawWakeLockHistoryLine? {
        val match =
            WAKELOCK_HISTORY_LINE_REGEX
                .matchEntire(line)
                ?: return null

        val timestamp =
            match.groups["timestamp"]
                ?.value
                ?: return null

        val timestampMillis =
            parseTimestampMillis(timestamp)
                ?: return null

        val packageName =
            match.groups["package"]
                ?.value
                ?.trim()
                .orEmpty()

        val action =
            match.groups["action"]
                ?.value
                ?: return null

        val rawTail =
            match.groups["tail"]
                ?.value
                ?.trim()
                .orEmpty()

        if (
            packageName.equals(
                ownPackageName,
                ignoreCase = true
            ) ||
            rawTail.contains(
                ownPackageName,
                ignoreCase = true
            )
        ) {
            return null
        }

        val flags =
            if (
                action == "ACQ" &&
                rawTail.endsWith(")") &&
                rawTail.contains(" (")
            ) {
                rawTail.substringAfterLast(" (")
                    .removeSuffix(")")
                    .trim()
            } else {
                ""
            }

        val tag =
            if (flags.isNotBlank()) {
                rawTail.substringBeforeLast(" (")
                    .trim()
            } else {
                rawTail
            }

        val lowerFlags =
            flags.lowercase(Locale.ROOT)

        val wakeLockType =
            when {
                lowerFlags.contains(
                    "screen-bright"
                ) ->
                    context.getString(
                        R.string.shizuku_wakelock_type_screen_bright
                    )

                lowerFlags.contains(
                    "screen-dim"
                ) ->
                    context.getString(
                        R.string.shizuku_wakelock_type_screen_dim
                    )

                lowerFlags.contains("full") ->
                    context.getString(
                        R.string.shizuku_wakelock_type_full
                    )

                lowerFlags.contains("partial") ->
                    context.getString(
                        R.string.shizuku_wakelock_type_partial
                    )

                else ->
                    context.getString(
                        R.string.shizuku_wakelock_type_unknown
                    )
            }

        return RawWakeLockHistoryLine(
            timestamp = timestamp,
            timestampMillis =
                timestampMillis,
            packageName =
                packageName,
            action =
                action,
            tag =
                tag,
            wakeLockType =
                wakeLockType,
            causesWake =
                lowerFlags.contains(
                    "acq-causes-wake"
                )
        )
    }

    private fun parseWakeLockLine(
        line: String,
        ownPackageName: String
    ): ParsedWakeLock? {
        val match =
            ENTRY_REGEX.matchEntire(line)
                ?: return null

        val timestamp = match
            .groups["timestamp"]
            ?.value
            ?: return null

        val packageName = match
            .groups["package"]
            ?.value
            ?.trim()
            .orEmpty()

        val tag = match
            .groups["tag"]
            ?.value
            ?.trim()
            .orEmpty()

        val isOwnWakeLock =
            packageName.equals(
                ownPackageName,
                ignoreCase = true
            ) ||
            tag.contains(
                ownPackageName,
                ignoreCase = true
            )

        if (isOwnWakeLock) {
            return null
        }

        val timestampMillis =
            parseTimestampMillis(timestamp)
                ?: return null

        return ParsedWakeLock(
            rawEntry = line,
            timestamp = timestamp,
            timestampMillis =
                timestampMillis,
            packageName = packageName,
            tag = tag
        )
    }

    private fun selectBestEntry(
        entries: List<ParsedWakeLock>,
        referenceTimestamp: Long?
    ): ParsedWakeLock? {
        if (entries.isEmpty()) {
            return null
        }

        if (referenceTimestamp == null) {
            return entries.maxByOrNull {
                it.timestampMillis
            }
        }

        val nearbyEntries =
            entries.filter { entry ->
                abs(
                    entry.timestampMillis -
                        referenceTimestamp
                ) <= MATCH_WINDOW_MILLIS
            }

        if (nearbyEntries.isEmpty()) {
            return null
        }

        /*
         * Ein Wakelock vor SCREEN_ON kann das Gerät
         * geweckt haben. Ein Eintrag danach ist häufiger
         * eine Reaktion auf das bereits aktive Display.
         */
        val entriesBeforeOrAt =
            nearbyEntries.filter { entry ->
                entry.timestampMillis <=
                    referenceTimestamp
            }

        if (entriesBeforeOrAt.isNotEmpty()) {
            return entriesBeforeOrAt.minByOrNull {
                referenceTimestamp -
                    it.timestampMillis
            }
        }

        return nearbyEntries.minByOrNull {
            it.timestampMillis -
                referenceTimestamp
        }
    }

    private fun parseTimestampMillis(
        rawTimestamp: String?
    ): Long? {
        val raw =
            rawTimestamp
                ?.trim()
                .orEmpty()

        if (raw.isBlank()) {
            return null
        }

        val now = System.currentTimeMillis()

        val currentYear =
            Calendar.getInstance().get(
                Calendar.YEAR
            )

        val parser =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
            ).apply {
                isLenient = false
            }

        val candidates =
            listOf(
                currentYear - 1,
                currentYear,
                currentYear + 1
            ).mapNotNull { year ->
                runCatching {
                    parser.parse(
                        "$year-$raw"
                    )?.time
                }.getOrNull()
            }

        /*
         * Android-Zeilen enthalten meist keinen Jahreswert.
         * Rund um Silvester darf ein Dezember-Eintrag daher
         * nicht versehentlich dem kommenden Jahr zugeordnet
         * werden. Gewählt wird das zeitlich plausibelste Datum.
         */
        return candidates
            .filter { timestamp ->
                timestamp <=
                    now + 24L * 60L * 60L * 1000L
            }
            .minByOrNull { timestamp ->
                kotlin.math.abs(
                    now - timestamp
                )
            }
    }

    private fun parseAlarmDump(
        context: Context,
        output: String,
        ownPackageName: String
    ): WakeupAlarmDiagnostic {
        val lines = output.lines()

        val statsStart =
            lines.indexOfFirst { line ->
                line.trim() == "Alarm Stats:"
            }

        if (statsStart < 0) {
            return alarmErrorResult(
                context.getString(
                    R.string.shizuku_error_alarm_stats_not_found
                )
            )
        }

        var currentPackage: String? = null
        var currentPackageWakeups = 0
        var pendingAgeMillis: Long? = null
        var pendingWakeCount = 0

        val candidates =
            mutableListOf<ParsedWakeupAlarm>()

        lines.drop(statsStart + 1)
            .forEach { rawLine ->
                val line = rawLine.trim()

                val packageMatch =
                    PACKAGE_STATS_REGEX
                        .matchEntire(line)

                if (packageMatch != null) {
                    currentPackage =
                        packageMatch
                            .groups["package"]
                            ?.value

                    currentPackageWakeups =
                        packageMatch
                            .groups["wakeups"]
                            ?.value
                            ?.toIntOrNull()
                            ?: 0

                    pendingAgeMillis = null
                    pendingWakeCount = 0

                    return@forEach
                }

                val alarmMatch =
                    ALARM_STATS_REGEX
                        .matchEntire(line)

                if (alarmMatch != null) {
                    pendingWakeCount =
                        alarmMatch
                            .groups["wakes"]
                            ?.value
                            ?.toIntOrNull()
                            ?: 0

                    pendingAgeMillis =
                        parseRelativeAgeMillis(
                            alarmMatch
                                .groups["last"]
                                ?.value
                        )

                    return@forEach
                }

                if (
                    line.startsWith("*walarm*:") &&
                    pendingWakeCount > 0 &&
                    pendingAgeMillis != null
                ) {
                    val packageName =
                        currentPackage
                            ?.trim()
                            .orEmpty()

                    val isOwnAlarm =
                        packageName.equals(
                            ownPackageName,
                            ignoreCase = true
                        ) ||
                        line.contains(
                            ownPackageName,
                            ignoreCase = true
                        )

                    if (
                        packageName.isNotBlank() &&
                        !isOwnAlarm
                    ) {
                        candidates.add(
                            ParsedWakeupAlarm(
                                packageName =
                                    packageName,
                                tag = line,
                                ageMillis =
                                    pendingAgeMillis!!,
                                wakeCount =
                                    pendingWakeCount,
                                packageWakeups =
                                    currentPackageWakeups
                            )
                        )
                    }

                    pendingAgeMillis = null
                    pendingWakeCount = 0
                }
            }

        val latest =
            candidates.minByOrNull {
                it.ageMillis
            }

        if (latest == null) {
            return WakeupAlarmDiagnostic(
                packageName = null,
                tag = null,
                ageMillis = null,
                wakeCount = 0,
                packageWakeups = 0
            )
        }

        val snapshotTimestamp =
            System.currentTimeMillis()

        return WakeupAlarmDiagnostic(
            packageName =
                latest.packageName,
            tag =
                latest.tag,
            ageMillis =
                latest.ageMillis,
            wakeCount =
                latest.wakeCount,
            packageWakeups =
                latest.packageWakeups,
            triggerTimestampMillis =
                snapshotTimestamp -
                    latest.ageMillis
        )
    }

    private fun parseJobSchedulerDump(
        output: String,
        ownPackageName: String
    ): BackgroundJobDiagnostic {
        val snapshotTimestamp =
            System.currentTimeMillis()

        val candidates =
            output.lineSequence()
                .mapNotNull { rawLine ->
                    val line = rawLine.trim()

                    val match =
                        JOB_HISTORY_REGEX
                            .matchEntire(line)
                            ?: return@mapNotNull null

                    val ageMillis =
                        parseRelativeAgeMillis(
                            match.groups["age"]
                                ?.value
                        ) ?: return@mapNotNull null

                    val target =
                        match.groups["target"]
                            ?.value
                            ?.trim()
                            .orEmpty()

                    val ownerPart =
                        target.substringBefore("/")

                    val packageName =
                        if (
                            ownerPart.startsWith("@")
                        ) {
                            ownerPart
                                .substringAfterLast("@")
                        } else {
                            ownerPart
                        }.trim()

                    if (
                        packageName.isBlank() ||
                        packageName.equals(
                            ownPackageName,
                            ignoreCase = true
                        ) ||
                        target.contains(
                            ownPackageName,
                            ignoreCase = true
                        )
                    ) {
                        return@mapNotNull null
                    }

                    val serviceName =
                        target.substringAfter(
                            "/",
                            missingDelimiterValue =
                                target
                        )

                    BackgroundJobDiagnostic(
                        packageName =
                            packageName,
                        serviceName =
                            serviceName,
                        ageMillis =
                            ageMillis,
                        prioritized =
                            match.groups["action"]
                                ?.value
                                ?.endsWith("-P")
                                == true,
                        rawEntry =
                            line,
                        triggerTimestampMillis =
                            snapshotTimestamp -
                                ageMillis
                    )
                }
                .toList()

        return candidates.minByOrNull {
            it.ageMillis ?: Long.MAX_VALUE
        } ?: BackgroundJobDiagnostic(
            packageName = null,
            serviceName = null,
            ageMillis = null,
            prioritized = false,
            rawEntry = null
        )
    }

    private fun parseNetworkStatsDump(
        output: String,
        context: Context,
        maxEntries: Int?
    ): NetworkStatsDiagnostic {
        val packageManager =
            context.packageManager

        val ownPackageName =
            context.packageName

        val packageUidMap =
            output.lineSequence()
                .mapNotNull { rawLine ->
                    val line =
                        rawLine.trim()

                    val match =
                        PACKAGE_UID_LINE_REGEX
                            .matchEntire(line)
                            ?: return@mapNotNull null

                    val packageName =
                        match.groups["package"]
                            ?.value
                            ?.trim()
                            ?: return@mapNotNull null

                    val uid =
                        match.groups["uid"]
                            ?.value
                            ?.toIntOrNull()
                            ?: return@mapNotNull null

                    uid to packageName
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second }
                )

        val netstatsPart =
            output.substringBefore(
                "__PACKAGES__"
            )

        val ignoredPackages =
            setOf(
                ownPackageName,
                "com.android.shell"
            )

        val ignoredUids =
            setOf(
                0,
                2000
            )

        val entries =
            netstatsPart.lineSequence()
                .mapNotNull { rawLine ->
                    val line =
                        rawLine.trim()

                    val match =
                        NETSTATS_UID_LINE_REGEX
                            .matchEntire(line)
                            ?: return@mapNotNull null

                    val uid =
                        match.groups["uid"]
                            ?.value
                            ?.toIntOrNull()
                            ?: return@mapNotNull null

                    if (uid in ignoredUids) {
                        return@mapNotNull null
                    }

                    val rxBytes =
                        match.groups["rxBytes"]
                            ?.value
                            ?.toLongOrNull()
                            ?: 0L

                    val txBytes =
                        match.groups["txBytes"]
                            ?.value
                            ?.toLongOrNull()
                            ?: 0L

                    val packagesFromManager =
                        runCatching {
                            packageManager
                                .getPackagesForUid(uid)
                                ?.toList()
                                .orEmpty()
                        }.getOrDefault(emptyList())

                    val packagesFromShell =
                        packageUidMap[uid]
                            .orEmpty()

                    val allPackages =
                        (
                            packagesFromManager +
                                packagesFromShell
                        ).distinct()

                    val packageName =
                        allPackages.firstOrNull { packageName ->
                            ignoredPackages.none { ignored ->
                                packageName.equals(
                                    ignored,
                                    ignoreCase = true
                                )
                            }
                        } ?: allPackages.firstOrNull()

                    if (
                        packageName != null &&
                        ignoredPackages.any { ignored ->
                            packageName.equals(
                                ignored,
                                ignoreCase = true
                            )
                        }
                    ) {
                        return@mapNotNull null
                    }

                    if (
                        rxBytes < 0L ||
                        txBytes < 0L
                    ) {
                        return@mapNotNull null
                    }

                    val totalBytes =
                        runCatching {
                            Math.addExact(
                                rxBytes,
                                txBytes
                            )
                        }.getOrNull()
                            ?: return@mapNotNull null

                    if (totalBytes <= 0L) {
                        return@mapNotNull null
                    }

                    val appLabel =
                        packageName?.let { resolvedPackage ->
                            runCatching {
                                val appInfo =
                                    packageManager.getApplicationInfo(
                                        resolvedPackage,
                                        0
                                    )

                                packageManager
                                    .getApplicationLabel(appInfo)
                                    .toString()
                                    .trim()
                                    .ifBlank { null }
                            }.getOrNull()
                        }

                    NetworkTrafficEntry(
                        uid = uid,
                        packageName = packageName,
                        appLabel = appLabel,
                        rxBytes = rxBytes,
                        txBytes = txBytes,
                        totalBytes = totalBytes
                    )
                }
                .sortedByDescending {
                    it.totalBytes
                }
                .toList()
                .let { sortedEntries ->
                    if (maxEntries == null) {
                        sortedEntries
                    } else {
                        sortedEntries.take(
                            maxEntries.coerceAtLeast(0)
                        )
                    }
                }

        return NetworkStatsDiagnostic(
            entries = entries
        )
    }

    private fun parseWakeReasonLog(
        output: String
    ): WakeReasonDiagnostic {
        val parsed =
            output.lineSequence()
                .mapNotNull { rawLine ->
                    val line = rawLine.trim()

                    val match =
                        WAKE_REASON_REGEX.find(line)
                            ?: return@mapNotNull null

                    val timestamp =
                        match.groups["timestamp"]
                            ?.value
                            ?.trim()
                            ?: return@mapNotNull null

                    val reason =
                        match.groups["reason"]
                            ?.value
                            ?.trim()
                            .orEmpty()

                    val details =
                        match.groups["details"]
                            ?.value
                            ?.trim()
                            .orEmpty()

                    val timestampMillis =
                        parseTimestampMillis(
                            timestamp
                        ) ?: return@mapNotNull null

                    WakeReasonDiagnostic(
                        reason =
                            reason.ifBlank { null },
                        details =
                            details.ifBlank { null },
                        timestamp =
                            timestamp,
                        timestampMillis =
                            timestampMillis,
                        rawEntry =
                            line
                    )
                }
                .maxByOrNull {
                    it.timestampMillis ?: Long.MIN_VALUE
                }

        return parsed ?: WakeReasonDiagnostic(
            reason = null,
            details = null,
            timestamp = null,
            rawEntry = null
        )
    }

    private fun parseRelativeAgeMillis(
        rawValue: String?
    ): Long? {
        val value = rawValue
            ?.trim()
            ?.removePrefix("-")
            .orEmpty()

        if (value.isBlank()) {
            return null
        }

        val match =
            RELATIVE_TIME_REGEX
                .matchEntire(value)
                ?: return null

        val days =
            match.groups["days"]
                ?.value
                ?.toLongOrNull()
                ?: 0L

        val hours =
            match.groups["hours"]
                ?.value
                ?.toLongOrNull()
                ?: 0L

        val minutes =
            match.groups["minutes"]
                ?.value
                ?.toLongOrNull()
                ?: 0L

        val seconds =
            match.groups["seconds"]
                ?.value
                ?.toLongOrNull()
                ?: 0L

        val millis =
            match.groups["millis"]
                ?.value
                ?.toLongOrNull()
                ?: 0L

        val totalMillis =
            runCatching {
                Math.addExact(
                    Math.addExact(
                        Math.multiplyExact(
                            days,
                            86_400_000L
                        ),
                        Math.multiplyExact(
                            hours,
                            3_600_000L
                        )
                    ),
                    Math.addExact(
                        Math.addExact(
                            Math.multiplyExact(
                                minutes,
                                60_000L
                            ),
                            Math.multiplyExact(
                                seconds,
                                1_000L
                            )
                        ),
                        millis
                    )
                )
            }.getOrNull()
                ?: return null

        return totalMillis
            .takeIf { value ->
                value in 0L..
                    365L * 24L * 60L * 60L * 1000L
            }
    }

    private fun networkStatsErrorResult(
        message: String
    ): NetworkStatsDiagnostic {
        return NetworkStatsDiagnostic(
            entries = emptyList(),
            error = message
        )
    }

    private fun wakeReasonErrorResult(
        message: String
    ): WakeReasonDiagnostic {
        return WakeReasonDiagnostic(
            reason = null,
            details = null,
            timestamp = null,
            rawEntry = null,
            error = message
        )
    }

    private fun jobErrorResult(
        message: String
    ): BackgroundJobDiagnostic {
        return BackgroundJobDiagnostic(
            packageName = null,
            serviceName = null,
            ageMillis = null,
            prioritized = false,
            rawEntry = null,
            error = message
        )
    }

    private fun alarmErrorResult(
        message: String
    ): WakeupAlarmDiagnostic {
        return WakeupAlarmDiagnostic(
            packageName = null,
            tag = null,
            ageMillis = null,
            wakeCount = 0,
            packageWakeups = 0,
            error = message
        )
    }

    private fun errorResult(
        message: String
    ): WakeLockDiagnostic {
        return WakeLockDiagnostic(
            activeCount = 0,
            lastTimestamp = null,
            lastPackage = null,
            lastTag = null,
            rawLastEntry = null,
            error = message
        )
    }

    private val NETSTATS_UID_LINE_REGEX = Regex(
        """(?<uid>\d+)\s+(?<rxBytes>\d+)\s+(?<rxPackets>\d+)\s+(?<txBytes>\d+)\s+(?<txPackets>\d+)"""
    )

    private val PACKAGE_UID_LINE_REGEX = Regex(
        """package:(?<package>\S+)\s+uid:(?<uid>\d+)"""
    )

    private val WAKE_REASON_REGEX = Regex(
        """(?<timestamp>\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}).*?PowerManagerService:\s+Waking up from.*?reason=(?<reason>[A-Z0-9_]+),\s+details=(?<details>[^)]+)\)"""
    )

    private val JOB_HISTORY_REGEX = Regex(
        """-(?<age>\S+)\s+(?<action>START(?:-P)?):\s+#\S+\s+(?<target>\S+)(?:\s+.*)?"""
    )

    private val PACKAGE_STATS_REGEX = Regex(
        """(?:\S+:)?(?<package>[A-Za-z0-9._:$-]+)\s+.+,\s+(?<wakeups>\d+)\s+wakeups:"""
    )

    private val ALARM_STATS_REGEX = Regex(
        """\+\S+\s+(?<wakes>\d+)\s+wakes\s+\d+\s+alarms,\s+last\s+(?<last>-\S+):"""
    )

    private val RELATIVE_TIME_REGEX = Regex(
        """(?:(?<days>\d+)d)?(?:(?<hours>\d+)h)?(?:(?<minutes>\d+)m)?(?:(?<seconds>\d+)s)?(?:(?<millis>\d+)ms)?"""
    )

    private val ENTRY_REGEX = Regex(
        """(?<timestamp>\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+-\s+\d+\s+\((?<package>[^)]+)\)\s+-\s+ACQ\s+(?<tag>.+?)\s+\(partial\)"""
    )

    private val WAKELOCK_HISTORY_LINE_REGEX =
        Regex(
            """(?<timestamp>\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+-\s+\d+\s+\((?<package>[^)]+)\)\s+-\s+(?<action>ACQ|REL)\s+(?<tail>.+)"""
        )
    suspend fun readCompactExpertSnapshot(
        context: Context
    ): String {
        val deviceFamily =
            DeviceProfile.detect(context).family

        fun hasAny(
            text: String,
            vararg needles: String
        ): Boolean {
            val lower =
                text.lowercase(Locale.getDefault())

            return needles.any {
                lower.contains(
                    it.lowercase(Locale.getDefault())
                )
            }
        }

        fun compactLineList(
            lines: List<String>,
            maxItems: Int = 8
        ): String {
            val cleaned =
                lines
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(maxItems)

            return if (cleaned.isEmpty()) {
                context.getString(
                    R.string.shizuku_snapshot_no_hits
                )
            } else {
                cleaned.joinToString(
                    separator = "\n"
                ) { "• $it" }
            }
        }

        val locationRaw =
            runCatching {
                runDiagnosticCommand(
                    context = context,
                    command = "timeout 3s dumpsys location"
                )
            }.getOrElse { "" }

        val sensorRaw =
            runCatching {
                runDiagnosticCommand(
                    context = context,
                    command = "timeout 3s dumpsys sensorservice"
                )
            }.getOrElse { "" }

        val connectivityRaw =
            runCatching {
                runDiagnosticCommand(
                    context = context,
                    command = "timeout 3s dumpsys connectivity"
                )
            }.getOrElse { "" }

        val locationHints =
            mutableListOf<String>()

        if (
            hasAny(
                locationRaw,
                "fused_location_provider",
                "fused provider",
                "FusedLocationService"
            )
        ) {
            locationHints.add(
                context.getString(
                    R.string.shizuku_hint_fused_location
                )
            )
        }

        if (
            hasAny(
                locationRaw,
                "network_location_provider",
                "NetworkLocationService",
                "network provider"
            )
        ) {
            locationHints.add(
                context.getString(
                    R.string.shizuku_hint_network_location
                )
            )
        }

        if (
            hasAny(
                locationRaw,
                "gnss_location_provider",
                "GnssService",
                "gps provider"
            )
        ) {
            locationHints.add(
                context.getString(
                    R.string.shizuku_hint_gnss_location
                )
            )
        }

        if (
            hasAny(
                locationRaw,
                "activity_recognition_provider",
                "ALARM_WAKEUP_ACTIVITY_DETECTION",
                "activity"
            )
        ) {
            locationHints.add(
                context.getString(
                    R.string.shizuku_hint_activity_recognition
                )
            )
        }

        if (
            hasAny(
                locationRaw,
                "geofencer_provider",
                "geofence",
                "Geofencer"
            )
        ) {
            locationHints.add(
                context.getString(
                    R.string.shizuku_hint_geofencing
                )
            )
        }

        if (
            hasAny(
                locationRaw,
                "com.coloros.weather",
                "weather"
            )
        ) {
            locationHints.add(
                context.getString(
                    R.string.shizuku_hint_weather_passive_location
                )
            )
        }

        if (
            deviceFamily ==
                DeviceFamily.ONEPLUS &&
            hasAny(
                locationRaw,
                "com.oplus.nas",
                "com.oplus.nhs",
                "OplusLBS",
                "SensorNotificationService"
            )
        ) {
            locationHints.add(
                context.getString(
                    R.string.shizuku_hint_oplus_location_services
                )
            )
        }

        val sensorHints =
            mutableListOf<String>()

        if (
            hasAny(
                sensorRaw,
                "Proximity Sensor Wakeup",
                "android.sensor.proximity"
            )
        ) {
            sensorHints.add(
                context.getString(
                    R.string.shizuku_hint_proximity_wakeup
                )
            )
        }

        if (
            hasAny(
                sensorRaw,
                "pick_up_motion",
                "tilt_detector"
            )
        ) {
            sensorHints.add(
                context.getString(
                    R.string.shizuku_hint_pick_up_detection
                )
            )
        }

        if (
            hasAny(
                sensorRaw,
                "lux_aod",
                "aod"
            )
        ) {
            sensorHints.add(
                context.getString(
                    R.string.shizuku_hint_aod_light_wakeup
                )
            )
        }

        if (
            hasAny(
                sensorRaw,
                "oplus_activity_recognition",
                "activity_recognition"
            )
        ) {
            sensorHints.add(
                when (deviceFamily) {
                    DeviceFamily.ONEPLUS ->
                        context.getString(
                            R.string.shizuku_hint_oplus_activity_sensor
                        )

                    DeviceFamily.SAMSUNG ->
                        context.getString(
                            R.string.shizuku_hint_samsung_activity_detection
                        )

                    DeviceFamily.GENERIC_ANDROID ->
                        context.getString(
                            R.string.shizuku_hint_activity_detection
                        )
                }
            )
        }

        if (
            hasAny(
                sensorRaw,
                "pedometer_minute",
                "step_counter",
                "step_detector"
            )
        ) {
            sensorHints.add(
                context.getString(
                    R.string.shizuku_hint_step_sensors
                )
            )
        }

        if (
            hasAny(
                sensorRaw,
                "significant_motion",
                "motion_detect"
            )
        ) {
            sensorHints.add(
                context.getString(
                    R.string.shizuku_hint_significant_motion
                )
            )
        }

        val networkHints =
            mutableListOf<String>()

        if (
            hasAny(
                connectivityRaw,
                "WIFI CONNECTED",
                "Transports: WIFI",
                "wlan0"
            )
        ) {
            networkHints.add(
                context.getString(
                    R.string.shizuku_hint_wifi_connected
                )
            )
        }

        if (
            hasAny(
                connectivityRaw,
                "MOBILE",
                "CELLULAR",
                "rmnet"
            )
        ) {
            networkHints.add(
                context.getString(
                    R.string.shizuku_hint_cellular_ims
                )
            )
        }

        if (
            hasAny(
                connectivityRaw,
                "com.android.phone",
                "TelephonyNetworkSpecifier"
            )
        ) {
            networkHints.add(
                context.getString(
                    R.string.shizuku_hint_telephony_requests
                )
            )
        }

        if (
            hasAny(
                connectivityRaw,
                "com.qualcomm",
                "qti",
                "cne"
            )
        ) {
            networkHints.add(
                context.getString(
                    R.string.shizuku_hint_qualcomm_network_optimization
                )
            )
        }

        return buildString {
            appendLine(
                context.getString(
                    R.string.shizuku_snapshot_section_location
                )
            )
            appendLine(
                compactLineList(locationHints)
            )
            appendLine()
            appendLine(
                context.getString(
                    R.string.shizuku_snapshot_section_sensors
                )
            )
            appendLine(
                compactLineList(sensorHints)
            )
            appendLine()
            appendLine(
                context.getString(
                    R.string.shizuku_snapshot_section_network
                )
            )
            appendLine(
                compactLineList(networkHints)
            )
            appendLine()
            appendLine(
                context.getString(
                    R.string.shizuku_snapshot_note
                )
            )
        }
    }



}
