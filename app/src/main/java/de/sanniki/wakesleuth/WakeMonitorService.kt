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
import de.sanniki.wakesleuth.data.WakelogsData
import de.sanniki.wakesleuth.domain.ExpertSnapshot
import de.sanniki.wakesleuth.domain.ExpertSnapshotStatus
import de.sanniki.wakesleuth.domain.Proximity
import de.sanniki.wakesleuth.domain.ProximityState
import de.sanniki.wakesleuth.domain.SessionEndReason
import de.sanniki.wakesleuth.domain.SnapshotStatus
import de.sanniki.wakesleuth.domain.SnapshotTrigger
import de.sanniki.wakesleuth.domain.SystemSnapshot
import de.sanniki.wakesleuth.domain.UsbDeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class WakeMonitorService :
    Service(),
    SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null

    @Volatile
    private var proximity = Proximity(ProximityState.NOT_AVAILABLE)
    private var receiverRegistered = false
    private var explicitStop = false
    private var stopInProgress = false
    private var sessionStart: Job? = null

    private val data by lazy { WakelogsData.get(this) }

    private val recorder get() = data.recorder

    private val diagnosticScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /*
     * Plain event writes go through the app's single-lane scope, so that
     * events arriving back to back keep their order and still complete
     * while the service is being destroyed. Delayed diagnostics use
     * diagnosticScope and end with the service.
     */
    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context?,
            intent: Intent?,
        ) {
            val currentIntent = intent ?: return
            val now = System.currentTimeMillis()

            when (currentIntent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    val proximityAtScreenOn = proximity

                    data.scope.launch {
                        val screenOnEventId = recorder.recordScreenOn(at = now, proximity = proximityAtScreenOn)

                        captureLightSystemSnapshot(trigger = SnapshotTrigger.AFTER_SCREEN_ON, delayMillis = 0L)

                        captureCompactExpertSnapshot(screenOnEventId = screenOnEventId, delayMillis = 700L)

                        inspectSystemSourcesAroundScreenOn(screenOnTimestamp = now)
                    }
                }

                Intent.ACTION_SCREEN_OFF -> {
                    val proximityAtScreenOff = proximity

                    data.scope.launch { recorder.recordScreenOff(now, proximityAtScreenOff) }

                    captureLightSystemSnapshot(trigger = SnapshotTrigger.AFTER_SCREEN_OFF, delayMillis = 2_000L)
                }

                Intent.ACTION_POWER_CONNECTED -> {
                    data.scope.launch { recorder.recordPower(now, connected = true) }
                }

                Intent.ACTION_POWER_DISCONNECTED -> {
                    data.scope.launch { recorder.recordPower(now, connected = false) }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = readUsbDevice(currentIntent)

                    data.scope.launch { recorder.recordUsb(now, attached = true, device = device) }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = readUsbDevice(currentIntent)

                    data.scope.launch { recorder.recordUsb(now, attached = false, device = device) }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        isRunning = true

        createNotificationChannel()
        startAsForeground()
        registerSystemEvents()
        registerProximitySensor()

        val startedAt = System.currentTimeMillis()
        val proximityAtStart = proximity

        sessionStart = diagnosticScope.launch {
            val sessionId = data.sessions.startOrResume(
                at = startedAt,
                deviceFamily = DeviceProfile.detect(this@WakeMonitorService).family,
                proximity = proximityAtStart,
            )

            startBackgroundWakeMonitoring()
            captureNetworkSessionBaseline(sessionId)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (stopInProgress) {
                    return START_NOT_STICKY
                }

                explicitStop = true
                stopInProgress = true

                val stopRequestedAtMillis = intent
                    .getLongExtra(
                        "stop_requested_at_millis",
                        System.currentTimeMillis(),
                    ).coerceAtMost(
                        System.currentTimeMillis(),
                    )

                Toast
                    .makeText(applicationContext, getString(R.string.service_toast_finishing), Toast.LENGTH_LONG)
                    .show()

                updateForegroundStatus(
                    text = getString(R.string.service_notification_finishing),
                    includeStopAction = false,
                )

                diagnosticScope.launch {
                    // A stop right after the start must not overtake the
                    // creation of the session it is meant to end.
                    sessionStart?.join()

                    val sessionId = data.sessions.requestStop(stopRequestedAtMillis)

                    val finalPollCompleted =
                        withTimeoutOrNull(
                            FINALIZATION_TIMEOUT_MILLIS,
                        ) {
                            /*
                             * BackgroundWakeMonitor hält die
                             * jüngsten BatteryStats-Zeilen kurz
                             * zurück. Diese Wartezeit lässt den
                             * Sicherheits-Nachlauf ausreifen.
                             */
                            delay(FINAL_POLL_DELAY_MILLIS)

                            BackgroundWakeMonitor.poll(applicationContext)

                            true
                        } ?: false

                    if (sessionId != null) {
                        finishNetworkSession(sessionId)

                        data.sessions.finalize(
                            sessionId = sessionId,
                            at = System.currentTimeMillis(),
                            endReason = SessionEndReason.USER_STOP,
                            finalPollCompleted = finalPollCompleted,
                        )
                    }

                    isRunning = false

                    stopForeground(STOP_FOREGROUND_REMOVE)

                    stopSelf()
                }

                return START_NOT_STICKY
            }

            else -> {
                Unit
            }
        }

        return START_STICKY
    }

    private suspend fun captureNetworkSessionBaseline(
        sessionId: Long,
    ) {
        if (data.sessions.hasNetworkBaseline(sessionId)) {
            return
        }

        val snapshot = ShizukuDiagnostics.readNetworkStats(context = this@WakeMonitorService, maxEntries = null)

        if (
            snapshot.error == null && snapshot.entries.isNotEmpty()
        ) {
            data.sessions.saveNetworkBaseline(
                sessionId = sessionId,
                capturedAt = System.currentTimeMillis(),
                entries = snapshot.entries,
            )
        }
    }

    private suspend fun finishNetworkSession(
        sessionId: Long,
    ) {
        val endSnapshot = if (data.sessions.hasNetworkBaseline(sessionId)) {
            ShizukuDiagnostics.readNetworkStats(context = this, maxEntries = null)
        } else {
            null
        }

        data.sessions.finishNetworkMeasurement(
            sessionId = sessionId,
            end = endSnapshot,
            at = System.currentTimeMillis(),
        )
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(eventReceiver) }
            receiverRegistered = false
        }

        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }

        diagnosticScope.cancel()

        isRunning = false

        if (!explicitStop) {
            // Destroyed without a stop request: close the session so it
            // does not stay "running" in the database.
            data.scope.launch { data.sessions.recoverAbandonedSessions(System.currentTimeMillis()) }
        }

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

        proximity = Proximity(
            state = if (near) {
                ProximityState.NEAR
            } else {
                ProximityState.FAR
            },
            distanceCm = measuredValue,
        )
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) = Unit

    private fun registerSystemEvents() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        ContextCompat.registerReceiver(this, eventReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        receiverRegistered = true
    }

    private fun registerProximitySensor() {
        sensorManager = getSystemService(SensorManager::class.java)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        val sensor = proximitySensor

        if (sensor == null) {
            proximity = Proximity(ProximityState.NOT_PRESENT)
            return
        }

        proximity = Proximity(ProximityState.NO_READING)

        val registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)

        if (!registered) {
            proximity = Proximity(ProximityState.REGISTRATION_FAILED)
        }
    }

    private fun inspectSystemSourcesAroundScreenOn(
        screenOnTimestamp: Long,
    ) {
        if (
            ShizukuDiagnostics.state() != ShizukuState.RUNNING_GRANTED
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
            val wakeReasonDiagnostic = ShizukuDiagnostics.readWakeReason(applicationContext)

            recorder.attachWakeReason(screenOnAt = screenOnTimestamp, diagnostic = wakeReasonDiagnostic)

            val wakeLockDiagnostic =
                ShizukuDiagnostics.readWakeLocks(context = applicationContext, referenceTimestamp = screenOnTimestamp)

            recorder.attachWakeLockHint(screenOnAt = screenOnTimestamp, diagnostic = wakeLockDiagnostic)

            val alarmDiagnostic = ShizukuDiagnostics.readWakeupAlarms(applicationContext)

            recorder.attachWakeupAlarmHint(screenOnAt = screenOnTimestamp, diagnostic = alarmDiagnostic)

            val backgroundJobDiagnostic = ShizukuDiagnostics.readBackgroundJobs(applicationContext)

            recorder.attachBackgroundJobHint(screenOnAt = screenOnTimestamp, diagnostic = backgroundJobDiagnostic)
        }
    }

    private fun captureCompactExpertSnapshot(
        screenOnEventId: Long?,
        delayMillis: Long,
    ) {
        if (
            ShizukuDiagnostics.state() != ShizukuState.RUNNING_GRANTED
        ) {
            return
        }

        diagnosticScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }

            val snapshot = runCatching {
                ShizukuDiagnostics.readCompactExpertSnapshot(applicationContext)
            }.getOrElse { error ->
                ExpertSnapshot(
                    screenOnEventId = null,
                    status = ExpertSnapshotStatus.ERROR,
                    errorDetail = error.message
                        ?: error.javaClass.simpleName,
                    locationAvailable = false,
                    sensorsAvailable = false,
                    networkAvailable = false,
                    signals = emptySet(),
                )
            }

            recorder.recordExpertSnapshot(
                at = System.currentTimeMillis(),
                snapshot = snapshot.copy(screenOnEventId = screenOnEventId),
            )
        }
    }

    private fun captureLightSystemSnapshot(
        trigger: SnapshotTrigger,
        delayMillis: Long,
    ) {
        diagnosticScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }

            val capturedAt = System.currentTimeMillis()

            if (
                ShizukuDiagnostics.state() != ShizukuState.RUNNING_GRANTED
            ) {
                recorder.recordSystemSnapshot(
                    at = capturedAt,
                    snapshot = SystemSnapshot(trigger = trigger, status = SnapshotStatus.SHIZUKU_UNAVAILABLE),
                )
                return@launch
            }

            val outputResult = runCatching {
                withTimeoutOrNull(3_000L) {
                    ShizukuDiagnostics.runDiagnosticCommand(
                        applicationContext,
                        "echo __POWER__; " +
                            "dumpsys power " +
                            "| grep -E 'mWakefulness=|mInteractive=|mLowPowerModeEnabled=|mDeviceIdleMode=|mLightDeviceIdleMode=' " +
                            "| head -n 20; " +
                            "echo __DEVICEIDLE__; " +
                            "dumpsys deviceidle " +
                            "| grep -E 'mState=|mLightState=|mScreenOn=|mCharging=|mForceIdle=' " + "| head -n 20",
                    )
                }
            }

            val output = outputResult.getOrNull()

            val snapshot = when {
                !outputResult.isSuccess -> {
                    SystemSnapshot(
                        trigger = trigger,
                        status = SnapshotStatus.ERROR,
                        errorDetail = outputResult.exceptionOrNull()?.message,
                    )
                }

                output.isNullOrBlank() -> {
                    SystemSnapshot(trigger = trigger, status = SnapshotStatus.TIMEOUT_OR_EMPTY)
                }

                else -> {
                    parseLightSystemSnapshot(trigger = trigger, output = output)
                }
            }

            recorder.recordSystemSnapshot(at = capturedAt, snapshot = snapshot)
        }
    }

    private fun parseLightSystemSnapshot(
        trigger: SnapshotTrigger,
        output: String,
    ): SystemSnapshot {
        val lines = output
            .lines()
            .map { line ->
                line.trim()
            }.filter { line ->
                line.isNotBlank() && line != "__POWER__" && line != "__DEVICEIDLE__"
            }

        fun value(key: String): String? = findSnapshotValue(lines, key)

        fun flag(key: String): Boolean? = value(key)?.toBooleanStrictOrNull()

        return SystemSnapshot(
            trigger = trigger,
            status = SnapshotStatus.OK,
            wakefulness = value("mWakefulness="),
            interactive = flag("mInteractive="),
            lowPowerMode = flag("mLowPowerModeEnabled="),
            deviceIdleMode = flag("mDeviceIdleMode="),
            lightDeviceIdleMode = flag("mLightDeviceIdleMode="),
            deepIdleState = value("mState="),
            lightIdleState = value("mLightState="),
            idleScreenOn = flag("mScreenOn="),
            idleCharging = flag("mCharging="),
            forceIdle = value("mForceIdle="),
        )
    }

    private fun findSnapshotValue(
        lines: List<String>,
        key: String,
    ): String? {
        lines.forEach { line ->
            val index = line.indexOf(key)

            if (index >= 0) {
                return line
                    .substring(index + key.length)
                    .trim()
                    .substringBefore(" ")
                    .substringBefore(",")
            }
        }

        return null
    }

    private fun startBackgroundWakeMonitoring() {
        captureLightSystemSnapshot(trigger = SnapshotTrigger.START_PROBE, delayMillis = 5_000L)

        diagnosticScope.launch {
            while (true) {
                runCatching { BackgroundWakeMonitor.poll(applicationContext) }

                delay(BACKGROUND_WAKE_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun startAsForeground() {
        val serviceType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), serviceType)
    }

    private fun createNotification(
        contentText: String = getString(R.string.service_notification_running),
        includeStopAction: Boolean = true,
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, WakeMonitorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wakesleuth)
            .setContentTitle("wakelogs")
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (includeStopAction) {
            builder.addAction(
                R.drawable.ic_stat_wakesleuth,
                getString(R.string.service_notification_stop_action),
                stopIntent,
            )
        }

        return builder.build()
    }

    private fun updateForegroundStatus(
        text: String,
        includeStopAction: Boolean,
    ) {
        getSystemService(
            NotificationManager::class.java,
        ).notify(
            NOTIFICATION_ID,
            createNotification(contentText = text, includeStopAction = includeStopAction),
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.service_channel_description)
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun readUsbDevice(intent: Intent): UsbDeviceInfo? {
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
            ?: return null

        return UsbDeviceInfo(
            deviceId = device.deviceId,
            vendorId = device.vendorId,
            productId = device.productId,
            deviceName = device.deviceName,
            manufacturerName = device.manufacturerName,
        )
    }

    companion object {
        const val ACTION_START = "de.sanniki.wakesleuth.action.START"

        const val ACTION_STOP = "de.sanniki.wakesleuth.action.STOP"

        private const val CHANNEL_ID = "wakesleuth_monitor"

        private const val NOTIFICATION_ID = 4101

        private const val BACKGROUND_WAKE_POLL_INTERVAL_MILLIS = 60_000L

        /*
         * Der Parser hält die jüngsten Zeilen sechs Sekunden
         * zurück. Vor dem letzten Poll warten wir geringfügig
         * länger, damit diese Daten vollständig vorliegen.
         */
        private const val FINAL_POLL_DELAY_MILLIS = 6_500L

        /*
         * Einschließlich Wartezeit darf der Abschluss maximal
         * zehn Sekunden dauern. Danach wird sicher beendet.
         */
        private const val FINALIZATION_TIMEOUT_MILLIS = 10_000L

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
