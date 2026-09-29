package de.sanniki.wakesleuth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

class WakeMonitorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var proximityState = ""
    private var receiverRegistered = false
    private var explicitStop = false
    private var stopInProgress = false

    private val diagnosticScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val currentIntent = intent ?: return

            when (currentIntent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    val now = System.currentTimeMillis()
                    val recent = NotificationStore.getRecent(this@WakeMonitorService)
                    val ageMillis = recent?.let { now - it.timestamp }

                    val causeText = when {
                        recent == null || ageMillis == null ||
                            ageMillis < 0L || ageMillis > 10_000L -> {
                            getString(R.string.sleep_marker_cause_unknown)
                        }

                        ageMillis <= 3_000L -> {
                            buildString {
                                appendLine(getString(R.string.service_cause_probable, recent.appName))
                                appendLine(getString(R.string.service_confidence_high))
                                append(getString(R.string.service_time_offset, formatAge(ageMillis)))
                            }
                        }

                        else -> {
                            buildString {
                                appendLine(getString(R.string.service_cause_possible, recent.appName))
                                appendLine(getString(R.string.service_confidence_medium))
                                append(getString(R.string.service_time_offset, formatAge(ageMillis)))
                            }
                        }
                    }

                    logEvent(
                        type = "SCREEN_ON",
                        title = getString(R.string.service_event_screen_on),
                        details = buildString {
                            appendLine(getString(R.string.service_proximity_line, proximityState))
                            append(causeText)
                        }
                    )

        captureLightSystemSnapshot(
            reason = getString(R.string.service_snapshot_reason_after_screen_on),
            delayMillis = 0L
        )

        captureCompactExpertSnapshot(
            reason = getString(R.string.service_snapshot_reason_screen_on),
            delayMillis = 700L
        )


                    inspectSystemSourcesAroundScreenOn(
                        screenOnTimestamp = now
                    )
                }

                Intent.ACTION_SCREEN_OFF -> {
                    logEvent(
                        type = "SCREEN_OFF",
                        title = getString(R.string.service_event_screen_off),
                        details = getString(R.string.service_proximity_line, proximityState)
                    )

        captureLightSystemSnapshot(
            reason = getString(R.string.service_snapshot_reason_after_screen_off),
            delayMillis = 2_000L
        )

                }

                Intent.ACTION_POWER_CONNECTED -> {
                    logEvent(
                        type = "POWER_CONNECTED",
                        title = getString(R.string.service_event_power_connected),
                        details = getString(R.string.service_event_power_connected_details)
                    )
                }

                Intent.ACTION_POWER_DISCONNECTED -> {
                    logEvent(
                        type = "POWER_DISCONNECTED",
                        title = getString(R.string.service_event_power_disconnected),
                        details = getString(R.string.service_event_power_disconnected_details)
                    )
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = readUsbDevice(currentIntent)

                    logEvent(
                        type = "USB_ATTACHED",
                        title = getString(R.string.service_event_usb_attached),
                        details = usbDescription(device)
                    )
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = readUsbDevice(currentIntent)

                    logEvent(
                        type = "USB_DETACHED",
                        title = getString(R.string.service_event_usb_detached),
                        details = usbDescription(device)
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        proximityState = getString(R.string.service_proximity_not_available)

        isRunning = true
        EventStore.setMonitoring(this, true)

        createNotificationChannel()
        startAsForeground()
        registerSystemEvents()
        registerProximitySensor()
        startBackgroundWakeMonitoring()

        logEvent(
            type = "MONITOR_START",
            title = getString(R.string.service_event_monitor_start),
            details = getString(
                R.string.service_event_monitor_start_details,
                proximityState
            )
        )

        captureNetworkSessionBaseline()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (stopInProgress) {
                    return START_NOT_STICKY
                }

                explicitStop = true
                stopInProgress = true

                val stopRequestedAtMillis =
                    intent.getLongExtra(
                        "stop_requested_at_millis",
                        System.currentTimeMillis()
                    )
                        .coerceAtMost(
                            System.currentTimeMillis()
                        )

                Toast.makeText(
                    applicationContext,
                    getString(R.string.service_toast_finishing),
                    Toast.LENGTH_LONG
                ).show()

                updateForegroundStatus(
                    text =
                        getString(R.string.service_notification_finishing),
                    includeStopAction = false
                )

                diagnosticScope.launch {
                    val finalPollCompleted =
                        withTimeoutOrNull(
                            FINALIZATION_TIMEOUT_MILLIS
                        ) {
                            /*
                             * BackgroundWakeMonitor hält die
                             * jüngsten BatteryStats-Zeilen kurz
                             * zurück. Diese Wartezeit lässt den
                             * Sicherheits-Nachlauf ausreifen.
                             */
                            delay(
                                FINAL_POLL_DELAY_MILLIS
                            )

                            BackgroundWakeMonitor.poll(
                                applicationContext
                            )

                            true
                        } ?: false

                    finishNetworkSession()

                    EventStore.addEventAt(
                        context =
                            this@WakeMonitorService,
                        timestamp =
                            stopRequestedAtMillis,
                        type = "MONITOR_STOP",
                        title = getString(R.string.service_event_monitor_stop),
                        details =
                            if (finalPollCompleted) {
                                getString(
                                    R.string.service_event_monitor_stop_details_complete
                                )
                            } else {
                                getString(
                                    R.string.service_event_monitor_stop_details_incomplete
                                )
                            }
                    )

                    SessionArchiveStore
                        .captureLatestSession(
                            this@WakeMonitorService
                        )

                    EventStore.setMonitoring(
                        this@WakeMonitorService,
                        false
                    )

                    isRunning = false

                    stopForeground(
                        STOP_FOREGROUND_REMOVE
                    )

                    stopSelf()
                }

                return START_NOT_STICKY
            }

            else -> {
                EventStore.setMonitoring(this, true)
            }
        }

        return START_STICKY
    }

    private fun captureNetworkSessionBaseline() {
        if (
            EventStore.getNetworkSessionBaseline(
                this
            ) != null
        ) {
            return
        }

        diagnosticScope.launch {
            val snapshot =
                ShizukuDiagnostics.readNetworkStats(
                    context =
                        this@WakeMonitorService,
                    maxEntries = null
                )

            if (
                snapshot.error == null &&
                snapshot.entries.isNotEmpty()
            ) {
                EventStore.saveNetworkSessionBaseline(
                    context =
                        this@WakeMonitorService,
                    capturedAtMillis =
                        System.currentTimeMillis(),
                    entries =
                        snapshot.entries
                )
            }
        }
    }

    private suspend fun finishNetworkSession() {
        val baseline =
            EventStore.getNetworkSessionBaseline(
                this
            )

        if (baseline == null) {
            logEvent(
                type = "NETWORK_SESSION",
                title =
                    getString(R.string.service_event_network_session),
                details =
                    getString(R.string.service_network_no_baseline)
            )

            EventStore.clearNetworkSessionBaseline(
                this
            )
            return
        }

        val endSnapshot =
            ShizukuDiagnostics.readNetworkStats(
                context = this,
                maxEntries = null
            )

        if (endSnapshot.error != null) {
            logEvent(
                type = "NETWORK_SESSION",
                title =
                    getString(R.string.service_event_network_session),
                details =
                    getString(
                        R.string.service_network_end_failed,
                        endSnapshot.error
                    )
            )

            EventStore.clearNetworkSessionBaseline(
                this
            )
            return
        }

        val baselineByUid =
            baseline.entries.associateBy {
                it.uid
            }

        val deltas =
            endSnapshot.entries
                .mapNotNull { endEntry ->
                    val startEntry =
                        baselineByUid[endEntry.uid]

                    val rxDelta =
                        (
                            endEntry.rxBytes -
                                (
                                    startEntry
                                        ?.rxBytes
                                        ?: 0L
                                )
                        ).coerceAtLeast(0L)

                    val txDelta =
                        (
                            endEntry.txBytes -
                                (
                                    startEntry
                                        ?.txBytes
                                        ?: 0L
                                )
                        ).coerceAtLeast(0L)

                    val totalDelta =
                        runCatching {
                            Math.addExact(
                                rxDelta,
                                txDelta
                            )
                        }.getOrNull()
                            ?: return@mapNotNull null

                    if (totalDelta <= 0L) {
                        return@mapNotNull null
                    }

                    NetworkTrafficEntry(
                        uid =
                            endEntry.uid,
                        packageName =
                            endEntry.packageName
                                ?: startEntry
                                    ?.packageName,
                        appLabel =
                            endEntry.appLabel
                                ?: startEntry
                                    ?.appLabel,
                        rxBytes =
                            rxDelta,
                        txBytes =
                            txDelta,
                        totalBytes =
                            totalDelta
                    )
                }
                .sortedByDescending {
                    it.totalBytes
                }

        val finishedAt =
            System.currentTimeMillis()

        val durationMillis =
            (
                finishedAt -
                    baseline.capturedAtMillis
            ).coerceAtLeast(0L)

        val totalRx =
            deltas.fold(0L) { total, entry ->
                runCatching {
                    Math.addExact(
                        total,
                        entry.rxBytes
                    )
                }.getOrDefault(total)
            }

        val totalTx =
            deltas.fold(0L) { total, entry ->
                runCatching {
                    Math.addExact(
                        total,
                        entry.txBytes
                    )
                }.getOrDefault(total)
            }

        val totalTraffic =
            runCatching {
                Math.addExact(
                    totalRx,
                    totalTx
                )
            }.getOrDefault(0L)

        val details =
            buildString {
                appendLine(
                    getString(
                        R.string.service_network_duration,
                        formatSessionDuration(
                            durationMillis
                        )
                    )
                )

                appendLine(
                    getString(
                        R.string.service_network_apps_with_traffic,
                        deltas.size
                    )
                )

                appendLine(
                    getString(
                        R.string.service_network_total,
                        formatSessionBytes(
                            totalTraffic
                        ),
                        formatSessionBytes(
                            totalRx
                        ),
                        formatSessionBytes(
                            totalTx
                        )
                    )
                )

                if (deltas.isEmpty()) {
                    append(
                        getString(
                            R.string.service_network_no_traffic
                        )
                    )
                } else {
                    appendLine()
                    appendLine(
                        getString(
                            R.string.service_network_top_apps
                        )
                    )

                    deltas.take(15)
                        .forEachIndexed {
                                index,
                                entry ->

                            val displayName =
                                sourceDisplayName(
                                    context =
                                        this@WakeMonitorService,
                                    appLabel =
                                        entry.appLabel,
                                    packageName =
                                        entry.packageName,
                                    uid = entry.uid
                                )

                            appendLine(
                                "• " +
                                    displayName +
                                    " · " +
                                    formatSessionBytes(
                                        entry.totalBytes
                                    )
                            )

                            appendLine(
                                "  " +
                                    getString(
                                        R.string.service_network_app_transfer,
                                        formatSessionBytes(
                                            entry.rxBytes
                                        ),
                                        formatSessionBytes(
                                            entry.txBytes
                                        )
                                    )
                            )

                            append(
                                "  " +
                                    getString(
                                        R.string.service_network_app_package,
                                        entry.packageName
                                            ?: "UID ${entry.uid}"
                                    )
                            )

                            if (
                                index <
                                deltas
                                    .take(15)
                                    .lastIndex
                            ) {
                                appendLine()
                            }
                        }
                }
            }

        logEvent(
            type = "NETWORK_SESSION",
            title =
                getString(R.string.service_event_network_session),
            details = details
        )

        EventStore.clearNetworkSessionBaseline(
            this
        )
    }

    private fun formatSessionDuration(
        milliseconds: Long
    ): String {
        val totalSeconds =
            milliseconds / 1_000L

        val hours =
            totalSeconds / 3_600L

        val minutes =
            (
                totalSeconds % 3_600L
            ) / 60L

        val seconds =
            totalSeconds % 60L

        return when {
            hours > 0L ->
                "${hours} h ${minutes} min"

            minutes > 0L ->
                "${minutes} min ${seconds} s"

            else ->
                "${seconds} s"
        }
    }

    private fun formatSessionBytes(
        bytes: Long
    ): String {
        val safeBytes =
            bytes.coerceAtLeast(0L)

        return when {
            safeBytes >=
                1024L * 1024L * 1024L ->
                String.format(
                    java.util.Locale.getDefault(),
                    "%.1f GB",
                    safeBytes /
                        (
                            1024.0 *
                                1024.0 *
                                1024.0
                        )
                )

            safeBytes >=
                1024L * 1024L ->
                String.format(
                    java.util.Locale.getDefault(),
                    "%.1f MB",
                    safeBytes /
                        (
                            1024.0 *
                                1024.0
                        )
                )

            safeBytes >= 1024L ->
                String.format(
                    java.util.Locale.getDefault(),
                    "%.1f KB",
                    safeBytes / 1024.0
                )

            else ->
                "$safeBytes B"
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching {
                unregisterReceiver(eventReceiver)
            }
            receiverRegistered = false
        }

        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }

        diagnosticScope.cancel()

        isRunning = false
        EventStore.setMonitoring(this, false)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        val currentEvent = event ?: return

        if (currentEvent.sensor.type != Sensor.TYPE_PROXIMITY) {
            return
        }

        val measuredValue = currentEvent.values.firstOrNull() ?: return
        val near = measuredValue < currentEvent.sensor.maximumRange

        proximityState = if (near) {
            getString(R.string.service_proximity_covered)
        } else {
            getString(R.string.service_proximity_clear)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerSystemEvents() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        ContextCompat.registerReceiver(
            this,
            eventReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        receiverRegistered = true
    }

    private fun registerProximitySensor() {
        sensorManager = getSystemService(SensorManager::class.java)
        proximitySensor = sensorManager.getDefaultSensor(
            Sensor.TYPE_PROXIMITY
        )

        val sensor = proximitySensor

        if (sensor == null) {
            proximityState = getString(R.string.service_proximity_not_present)
            return
        }

        proximityState = getString(R.string.service_proximity_no_reading)

        val registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        if (!registered) {
            proximityState = getString(R.string.service_proximity_registration_failed)
        }
    }

    private fun inspectSystemSourcesAroundScreenOn(
        screenOnTimestamp: Long
    ) {
        if (
            ShizukuDiagnostics.state() !=
            ShizukuState.RUNNING_GRANTED
        ) {
            return
        }

        diagnosticScope.launch {
            /*
             * Systemprotokolle werden leicht verzögert
             * aktualisiert. Deshalb kurze Wartezeit.
             */
            delay(1_200L)

            /*
             * Der PowerManager-Wake-Reason ist die stärkste
             * verfügbare Quelle und wird daher zuerst geprüft.
             */
            val wakeReasonDiagnostic =
                ShizukuDiagnostics.readWakeReason(
                    applicationContext
                )

            EventStore.attachWakeReasonToScreenOn(
                context = applicationContext,
                screenOnTimestamp =
                    screenOnTimestamp,
                diagnostic =
                    wakeReasonDiagnostic
            )

            val wakeLockDiagnostic =
                ShizukuDiagnostics.readWakeLocks(
                    context = applicationContext,
                    referenceTimestamp =
                        screenOnTimestamp
                )

            EventStore.attachWakeLockHintToScreenOn(
                context = applicationContext,
                screenOnTimestamp =
                    screenOnTimestamp,
                diagnostic =
                    wakeLockDiagnostic
            )

            val alarmDiagnostic =
                ShizukuDiagnostics.readWakeupAlarms(
                    applicationContext
                )

            EventStore.attachWakeupAlarmHintToScreenOn(
                context = applicationContext,
                screenOnTimestamp =
                    screenOnTimestamp,
                diagnostic =
                    alarmDiagnostic
            )

            val backgroundJobDiagnostic =
                ShizukuDiagnostics.readBackgroundJobs(
                    applicationContext
                )

            EventStore.attachBackgroundJobHintToScreenOn(
                context = applicationContext,
                screenOnTimestamp =
                    screenOnTimestamp,
                diagnostic =
                    backgroundJobDiagnostic
            )
        }
    }

    private fun captureCompactExpertSnapshot(
        reason: String,
        delayMillis: Long
    ) {
        if (
            ShizukuDiagnostics.state() !=
            ShizukuState.RUNNING_GRANTED
        ) {
            return
        }

        diagnosticScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }

            val details =
                runCatching {
                    ShizukuDiagnostics.readCompactExpertSnapshot(
                        applicationContext
                    )
                }.getOrElse { error ->
                    getString(
                        R.string.service_diagnostic_error,
                        error.message ?: error.javaClass.simpleName
                    )
                }

            logEvent(
                type = "EXPERT_SNAPSHOT",
                title = getString(R.string.service_event_expert_snapshot),
                details =
                    getString(R.string.service_snapshot_trigger, reason) + "\n" +
                        getString(R.string.service_snapshot_source_compact) + "\n\n" +
                        details
            )
        }
    }

    private fun captureLightSystemSnapshot(
        reason: String,
        delayMillis: Long
    ) {
        diagnosticScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }

            if (
                ShizukuDiagnostics.state() !=
                ShizukuState.RUNNING_GRANTED
            ) {
                logEvent(
                    type = "SYSTEM_SNAPSHOT",
                    title = getString(R.string.service_event_system_snapshot),
                    details =
                        getString(R.string.service_snapshot_trigger, reason) + "\n" +
                            getString(R.string.service_snapshot_status_shizuku_unavailable) + "\n" +
                            getString(R.string.service_snapshot_source_background)
                )
                return@launch
            }

            val outputResult =
                runCatching {
                    withTimeoutOrNull(3_000L) {
                        ShizukuDiagnostics.runDiagnosticCommand(
                            applicationContext,
                            "echo __POWER__; " +
                                "dumpsys power " +
                                "| grep -E 'mWakefulness=|mInteractive=|mLowPowerModeEnabled=|mDeviceIdleMode=|mLightDeviceIdleMode=' " +
                                "| head -n 20; " +
                                "echo __DEVICEIDLE__; " +
                                "dumpsys deviceidle " +
                                "| grep -E 'mState=|mLightState=|mScreenOn=|mCharging=|mForceIdle=' " +
                                "| head -n 20"
                        )
                    }
                }

            val output =
                outputResult.getOrNull()

            if (!outputResult.isSuccess) {
                logEvent(
                    type = "SYSTEM_SNAPSHOT",
                    title = getString(R.string.service_event_system_snapshot),
                    details =
                        getString(R.string.service_snapshot_trigger, reason) + "\n" +
                            getString(R.string.service_snapshot_status_error) + "\n" +
                            getString(
                                R.string.service_snapshot_error,
                                outputResult.exceptionOrNull()?.message
                                    ?: getString(R.string.service_unknown)
                            ) +
                            "\n" +
                            getString(R.string.service_snapshot_source_background)
                )
                return@launch
            }

            if (output.isNullOrBlank()) {
                logEvent(
                    type = "SYSTEM_SNAPSHOT",
                    title = getString(R.string.service_event_system_snapshot),
                    details =
                        getString(R.string.service_snapshot_trigger, reason) + "\n" +
                            getString(R.string.service_snapshot_status_timeout) + "\n" +
                            getString(R.string.service_snapshot_source_background)
                )
                return@launch
            }

            logEvent(
                type = "SYSTEM_SNAPSHOT",
                title = getString(R.string.service_event_system_snapshot),
                details =
                    buildLightSystemSnapshotDetails(
                        reason = reason,
                        output = output
                    )
            )
        }
    }

    private fun buildLightSystemSnapshotDetails(
        reason: String,
        output: String
    ): String {
        val lines =
            output.lines()
                .map { line ->
                    line.trim()
                }
                .filter { line ->
                    line.isNotBlank() &&
                        line != "__POWER__" &&
                        line != "__DEVICEIDLE__"
                }

        val wakefulness =
            findSnapshotValue(
                lines,
                "mWakefulness="
            )

        val interactive =
            findSnapshotValue(
                lines,
                "mInteractive="
            )

        val powerSave =
            findSnapshotValue(
                lines,
                "mLowPowerModeEnabled="
            )

        val deepMode =
            findSnapshotValue(
                lines,
                "mDeviceIdleMode="
            )

        val lightMode =
            findSnapshotValue(
                lines,
                "mLightDeviceIdleMode="
            )

        val deepState =
            findSnapshotValue(
                lines,
                "mState="
            )

        val lightState =
            findSnapshotValue(
                lines,
                "mLightState="
            )

        val screenOn =
            findSnapshotValue(
                lines,
                "mScreenOn="
            )

        val charging =
            findSnapshotValue(
                lines,
                "mCharging="
            )

        val forceIdle =
            findSnapshotValue(
                lines,
                "mForceIdle="
            )

        return buildString {
            appendLine(getString(R.string.service_snapshot_trigger, reason))

            appendLine(
                "Power: " +
                    listOfNotNull(
                        wakefulness?.let {
                            "Wakefulness=$it"
                        },
                        interactive?.let {
                            "Interactive=$it"
                        },
                        powerSave?.let {
                            "PowerSave=$it"
                        }
                    ).ifEmpty {
                        listOf(getString(R.string.service_snapshot_no_compact_values))
                    }.joinToString(", ")
            )

            appendLine(
                "DeviceIdle: " +
                    listOfNotNull(
                        deepState?.let {
                            "Deep=$it"
                        },
                        lightState?.let {
                            "Light=$it"
                        },
                        deepMode?.let {
                            "DeepMode=$it"
                        },
                        lightMode?.let {
                            "LightMode=$it"
                        }
                    ).ifEmpty {
                        listOf(getString(R.string.service_snapshot_no_compact_values))
                    }.joinToString(", ")
            )

            appendLine(
                getString(
                    R.string.service_snapshot_conditions,
                    listOfNotNull(
                        screenOn?.let {
                            "ScreenOn=$it"
                        },
                        charging?.let {
                            "Charging=$it"
                        },
                        forceIdle?.let {
                            "ForceIdle=$it"
                        }
                    ).ifEmpty {
                        listOf(getString(R.string.service_snapshot_no_compact_values))
                    }.joinToString(", ")
                )
            )

            appendLine(
                getString(
                    R.string.service_snapshot_classification,
                    classifyLightSystemSnapshot(
                        wakefulness = wakefulness,
                        interactive = interactive,
                        deepMode = deepMode,
                        lightMode = lightMode,
                        deepState = deepState,
                        lightState = lightState
                    )
                )
            )

            appendLine(getString(R.string.service_snapshot_source_background))
        }.trim()
    }

    private fun findSnapshotValue(
        lines: List<String>,
        key: String
    ): String? {
        lines.forEach { line ->
            val index =
                line.indexOf(key)

            if (index >= 0) {
                return line.substring(
                    index + key.length
                )
                    .trim()
                    .substringBefore(" ")
                    .substringBefore(",")
            }
        }

        return null
    }

    private fun classifyLightSystemSnapshot(
        wakefulness: String?,
        interactive: String?,
        deepMode: String?,
        lightMode: String?,
        deepState: String?,
        lightState: String?
    ): String {
        val wake =
            wakefulness.orEmpty()

        val active =
            interactive == "true" ||
                wake.contains(
                    "Awake",
                    ignoreCase = true
                )

        if (active) {
            return getString(R.string.service_classify_active)
        }

        if (
            deepMode == "true" ||
            deepState?.contains(
                "IDLE",
                ignoreCase = true
            ) == true
        ) {
            return getString(R.string.service_classify_deep_idle)
        }

        if (
            lightMode == "true" ||
            lightState?.contains(
                "IDLE",
                ignoreCase = true
            ) == true
        ) {
            return getString(R.string.service_classify_light_idle)
        }

        if (
            wake.contains(
                "Asleep",
                ignoreCase = true
            ) ||
            wake.contains(
                "Dozing",
                ignoreCase = true
            )
        ) {
            return getString(R.string.service_classify_vendor_specific)
        }

        return getString(R.string.service_classify_unclear)
    }

    private fun startBackgroundWakeMonitoring() {
        captureLightSystemSnapshot(
            reason = getString(R.string.service_snapshot_reason_start_probe),
            delayMillis = 5_000L
        )

        diagnosticScope.launch {
            while (true) {
                runCatching {
                    BackgroundWakeMonitor.poll(
                        applicationContext
                    )
                }

                delay(
                    BACKGROUND_WAKE_POLL_INTERVAL_MILLIS
                )
            }
        }
    }

    private fun startAsForeground() {
        val serviceType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            serviceType
        )
    }

    private fun createNotification(
        contentText: String = getString(R.string.service_notification_running),
        includeStopAction: Boolean = true
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, WakeMonitorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val builder =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_stat_wakesleuth
                )
                .setContentTitle("wakelogs")
                .setContentText(contentText)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(
                    NotificationCompat.CATEGORY_SERVICE
                )
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )

        if (includeStopAction) {
            builder.addAction(
                R.drawable.ic_stat_wakesleuth,
                getString(R.string.service_notification_stop_action),
                stopIntent
            )
        }

        return builder.build()
    }

    private fun updateForegroundStatus(
        text: String,
        includeStopAction: Boolean
    ) {
        getSystemService(
            NotificationManager::class.java
        ).notify(
            NOTIFICATION_ID,
            createNotification(
                contentText = text,
                includeStopAction =
                    includeStopAction
            )
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                getString(R.string.service_channel_description)
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun formatAge(milliseconds: Long): String {
        return getString(
            R.string.bg_duration_seconds,
            milliseconds / 1000.0
        )
    }

    private fun logEvent(
        type: String,
        title: String,
        details: String
    ) {
        EventStore.addEvent(
            context = applicationContext,
            type = type,
            title = title,
            details = details
        )
    }

    @Suppress("DEPRECATION")
    private fun readUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(
                UsbManager.EXTRA_DEVICE,
                UsbDevice::class.java
            )
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    private fun usbDescription(device: UsbDevice?): String {
        if (device == null) {
            return getString(R.string.service_usb_no_info)
        }

        return getString(
            R.string.service_usb_details,
            device.deviceId,
            device.vendorId,
            device.productId
        )
    }

    companion object {
        const val ACTION_START =
            "de.sanniki.wakesleuth.action.START"

        const val ACTION_STOP =
            "de.sanniki.wakesleuth.action.STOP"

        private const val CHANNEL_ID =
            "wakesleuth_monitor"

        private const val NOTIFICATION_ID = 4101

        private const val BACKGROUND_WAKE_POLL_INTERVAL_MILLIS =
            60_000L

        /*
         * Der Parser hält die jüngsten Zeilen sechs Sekunden
         * zurück. Vor dem letzten Poll warten wir geringfügig
         * länger, damit diese Daten vollständig vorliegen.
         */
        private const val FINAL_POLL_DELAY_MILLIS =
            6_500L

        /*
         * Einschließlich Wartezeit darf der Abschluss maximal
         * zehn Sekunden dauern. Danach wird sicher beendet.
         */
        private const val FINALIZATION_TIMEOUT_MILLIS =
            10_000L

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
