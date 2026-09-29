package de.sanniki.wakesleuth.ui.render

import android.content.Context
import androidx.annotation.StringRes
import de.sanniki.wakesleuth.DeviceFamily
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.domain.AlarmHint
import de.sanniki.wakesleuth.domain.CauseAssessment
import de.sanniki.wakesleuth.domain.CauseConfidenceLevel
import de.sanniki.wakesleuth.domain.CpuEvidence
import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.DetectionKind
import de.sanniki.wakesleuth.domain.DiagnosticError
import de.sanniki.wakesleuth.domain.DirectWakeReason
import de.sanniki.wakesleuth.domain.EventType
import de.sanniki.wakesleuth.domain.EvidenceType
import de.sanniki.wakesleuth.domain.ExpertSection
import de.sanniki.wakesleuth.domain.ExpertSignal
import de.sanniki.wakesleuth.domain.ExpertSnapshotEvent
import de.sanniki.wakesleuth.domain.ExpertSnapshotStatus
import de.sanniki.wakesleuth.domain.HintRelation
import de.sanniki.wakesleuth.domain.JobHint
import de.sanniki.wakesleuth.domain.MonitorStartEvent
import de.sanniki.wakesleuth.domain.MonitorStopEvent
import de.sanniki.wakesleuth.domain.NetworkMeasurement
import de.sanniki.wakesleuth.domain.NetworkMeasurementStatus
import de.sanniki.wakesleuth.domain.NetworkSessionEvent
import de.sanniki.wakesleuth.domain.NotificationCause
import de.sanniki.wakesleuth.domain.NotificationCauseKind
import de.sanniki.wakesleuth.domain.NotificationEvent
import de.sanniki.wakesleuth.domain.PowerEvent
import de.sanniki.wakesleuth.domain.PowerKeySignal
import de.sanniki.wakesleuth.domain.Proximity
import de.sanniki.wakesleuth.domain.ProximityState
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.ScreenOffEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.SnapshotClassification
import de.sanniki.wakesleuth.domain.SnapshotStatus
import de.sanniki.wakesleuth.domain.SnapshotTrigger
import de.sanniki.wakesleuth.domain.SystemSnapshot
import de.sanniki.wakesleuth.domain.SystemSnapshotEvent
import de.sanniki.wakesleuth.domain.UsbEvent
import de.sanniki.wakesleuth.domain.WakeLockHint
import de.sanniki.wakesleuth.domain.WakeLockKind
import de.sanniki.wakesleuth.domain.WakeLockTags
import de.sanniki.wakesleuth.domain.WakeReason
import de.sanniki.wakesleuth.domain.WakeReasonCategory
import de.sanniki.wakesleuth.domain.WakeReasonEvidence
import de.sanniki.wakesleuth.domain.primarySource
import de.sanniki.wakesleuth.domain.source
import java.util.Locale

/**
 * Renders the typed read model into the title and detail lines shown in
 * the event list, the timeline and both exports. It is the only place
 * that turns stored facts into sentences, so UI text and export text
 * always agree and nothing ever has to be parsed back.
 */
class EventTextRenderer(
    private val context: Context,
    private val sources: SourceLabelResolver = SourceLabelResolver.get(context)
) {

    fun title(event: RecordedEvent): String =
        when (event) {
            is MonitorStartEvent -> string(R.string.service_event_monitor_start)
            is MonitorStopEvent -> string(R.string.service_event_monitor_stop)
            is ScreenOnEvent -> string(R.string.service_event_screen_on)
            is ScreenOffEvent -> string(R.string.service_event_screen_off)
            is PowerEvent ->
                if (event.connected) {
                    string(R.string.service_event_power_connected)
                } else {
                    string(R.string.service_event_power_disconnected)
                }
            is UsbEvent ->
                if (event.attached) {
                    string(R.string.service_event_usb_attached)
                } else {
                    string(R.string.service_event_usb_detached)
                }
            is NotificationEvent ->
                string(R.string.service_notification_event_title, sources.appName(event.packageName))
            is CpuWakeupEvent -> cpuTitle(event)
            is NetworkSessionEvent -> string(R.string.service_event_network_session)
            is SystemSnapshotEvent -> string(R.string.service_event_system_snapshot)
            is ExpertSnapshotEvent -> string(R.string.service_event_expert_snapshot)
        }

    fun details(event: RecordedEvent): String =
        when (event) {
            is MonitorStartEvent ->
                string(R.string.service_event_monitor_start_details, proximityLabel(event.proximity))

            is MonitorStopEvent ->
                if (event.finalPollCompleted == false) {
                    string(R.string.service_event_monitor_stop_details_incomplete)
                } else {
                    string(R.string.service_event_monitor_stop_details_complete)
                }

            is ScreenOnEvent -> screenOnDetails(event)

            is ScreenOffEvent ->
                string(R.string.service_proximity_line, proximityLabel(event.proximity))

            is PowerEvent ->
                if (event.connected) {
                    string(R.string.service_event_power_connected_details)
                } else {
                    string(R.string.service_event_power_disconnected_details)
                }

            is UsbEvent -> {
                val device = event.device

                if (device == null) {
                    string(R.string.service_usb_no_info)
                } else {
                    string(
                        R.string.service_usb_details,
                        device.deviceId ?: 0,
                        device.vendorId ?: 0,
                        device.productId ?: 0
                    )
                }
            }

            is NotificationEvent -> notificationDetails(event)
            is CpuWakeupEvent -> cpuDetails(event)
            is NetworkSessionEvent -> networkDetails(event.measurement)
            is SystemSnapshotEvent -> systemSnapshotDetails(event.snapshot)
            is ExpertSnapshotEvent -> expertSnapshotDetails(event)
        }

    /**
     * The most telling lines of an event for compact views: cause and
     * sources for a screen-on, reason, duration and source for a CPU
     * wakeup, otherwise the first detail lines.
     */
    fun summaryLines(event: RecordedEvent, max: Int = 4): List<String> =
        when (event) {
            is ScreenOnEvent ->
                buildList {
                    event.wakeReason?.let {
                        add(labeled(R.string.event_label_direct_wake_reason, wakeReasonLabel(it)))
                    }
                    event.notificationCause?.let {
                        add(notificationCauseLines(it).first())
                    }
                    if (CauseAssessment.isUnexplained(event)) {
                        add(string(R.string.sleep_marker_cause_unknown))
                    }
                    event.wakeLockHints.forEach { hint ->
                        add(labeled(R.string.event_label_source, sources.labelWithPackage(hint.packageName)))
                        add(labeled(R.string.event_label_kind, wakeLockKindLabel(WakeLockTags.kindOf(hint.tag))))
                    }
                    event.alarmHints.forEach { hint ->
                        add(labeled(R.string.event_label_source, sources.labelWithPackage(hint.packageName)))
                    }
                    event.jobHints.forEach { hint ->
                        add(labeled(R.string.event_label_source, sources.labelWithPackage(hint.packageName)))
                    }
                }
                    .distinct()
                    .take(max)

            is CpuWakeupEvent ->
                listOf(
                    string(R.string.bg_detail_system_reason, cpuSystemReason(event)),
                    string(R.string.bg_detail_cpu_awake_time, cpuAwakeDuration(event.awakeMs)),
                    string(
                        R.string.bg_detail_possible_source,
                        cpuSourceLabel(event) ?: string(R.string.bg_possible_source_ambiguous)
                    )
                )

            else ->
                details(event)
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(3)
        }

    /* ---------------- labels ---------------- */

    fun proximityLabel(proximity: Proximity?): String =
        string(
            when (proximity?.state) {
                ProximityState.NEAR -> R.string.service_proximity_covered
                ProximityState.FAR -> R.string.service_proximity_clear
                ProximityState.NO_READING -> R.string.service_proximity_no_reading
                ProximityState.NOT_PRESENT -> R.string.service_proximity_not_present
                ProximityState.REGISTRATION_FAILED -> R.string.service_proximity_registration_failed
                ProximityState.NOT_AVAILABLE, null -> R.string.service_proximity_not_available
            }
        )

    fun wakeReasonLabel(wakeReason: DirectWakeReason): String =
        wakeReasonLabel(wakeReason.reason, wakeReason.rawReason)

    fun wakeReasonLabel(reason: WakeReason, rawReason: String?): String =
        when (reason) {
            WakeReason.POWER_BUTTON -> string(R.string.event_power_button)
            WakeReason.DOUBLE_TAP -> string(R.string.event_wake_reason_double_tap)
            WakeReason.GESTURE -> string(R.string.event_wake_reason_gesture)
            WakeReason.LIFT -> string(R.string.event_wake_reason_lift)
            WakeReason.PLUGGED_IN -> string(R.string.event_wake_reason_plugged_in)
            WakeReason.WAKE_KEY -> string(R.string.event_wake_reason_wake_key)
            WakeReason.WAKE_MOTION -> string(R.string.event_wake_reason_motion)
            WakeReason.APPLICATION -> string(R.string.event_wake_reason_application)
            WakeReason.OTHER -> rawReason ?: string(R.string.event_unknown)
        }

    fun wakeReasonEvidenceLabel(evidence: WakeReasonEvidence): String =
        string(
            when (evidence) {
                WakeReasonEvidence.POWER_MANAGER_LOG -> R.string.event_confidence_power_manager
                WakeReasonEvidence.BATTERYSTATS_POWER_KEY -> R.string.event_confidence_samsung_batterystats
                WakeReasonEvidence.POWER_KEY_WAKELOCK -> R.string.event_confidence_system_wakelock
            }
        )

    /** Technical token that proved the wake reason. */
    fun technicalWakeReason(wakeReason: DirectWakeReason): String =
        when (wakeReason.powerKeySignal) {
            PowerKeySignal.PMIC_PWRKEY -> wakeReason.rawReason ?: PMIC_POWER_KEY
            PowerKeySignal.POLICY_POWER -> POLICY_POWER
            PowerKeySignal.DISPLAY_REASON_KEY -> DISPLAY_REASON_KEY
            PowerKeySignal.POWER_KEY_WAKELOCK ->
                if (wakeReason.evidence == WakeReasonEvidence.POWER_KEY_WAKELOCK) {
                    WINDOW_MANAGER_POWER_KEY
                } else {
                    SAMSUNG_POWER_KEY
                }
            null -> wakeReason.rawReason ?: string(R.string.event_unknown)
        }

    fun wakeLockKindLabel(kind: WakeLockKind): String =
        string(
            when (kind) {
                WakeLockKind.USER_PRESENCE -> R.string.event_kind_user_presence
                WakeLockKind.NETWORK_STATS -> R.string.event_kind_network_stats
                WakeLockKind.NOTIFICATION_PROCESSING -> R.string.event_kind_notification_processing
                WakeLockKind.ALARM -> R.string.event_kind_alarm
                WakeLockKind.BACKGROUND_JOB -> R.string.event_kind_background_job
                WakeLockKind.APP_LAUNCH -> R.string.event_kind_app_launch
                WakeLockKind.AUDIO -> R.string.event_kind_audio
                WakeLockKind.SYNC -> R.string.event_kind_sync
                WakeLockKind.SEARCH_INDEXING -> R.string.event_kind_search_indexing
                WakeLockKind.SCHEDULED_BACKGROUND_ACTION -> R.string.event_kind_scheduled_background_action
                WakeLockKind.PARTIAL_WAKELOCK -> R.string.event_kind_partial_wakelock
            }
        )

    fun evidenceTypeLabel(type: EvidenceType): String =
        when (type) {
            EvidenceType.SYNC -> string(R.string.bg_type_sync)
            EvidenceType.WORKMANAGER -> WORK_MANAGER
            EvidenceType.JOBSCHEDULER -> JOB_SCHEDULER
            EvidenceType.WAKEUP_ALARM -> string(R.string.bg_type_wakeup_alarm)
            EvidenceType.JOB_WAKELOCK -> string(R.string.bg_type_job_wakelock)
            EvidenceType.PARTIAL_WAKELOCK -> string(R.string.bg_type_partial_wakelock)
        }

    fun wakeReasonCategoryLabel(rawReason: String): String =
        when (WakeReasonCategory.of(rawReason)) {
            WakeReasonCategory.FAILED_SUSPEND -> string(R.string.bg_wake_reason_failed_suspend)
            WakeReasonCategory.SCHEDULED_SYSTEM_ALARM -> string(R.string.bg_wake_reason_scheduled_system_alarm)
            WakeReasonCategory.QUALCOMM_RADIO -> string(R.string.bg_wake_reason_qualcomm_radio)
            WakeReasonCategory.TIMER_SCHEDULER -> string(R.string.bg_wake_reason_timer_scheduler)
            WakeReasonCategory.SYSTEM_ACTIVITY -> string(R.string.bg_wake_reason_system_activity)
            WakeReasonCategory.KERNEL_HARDWARE_INTERRUPT -> string(R.string.bg_wake_reason_kernel_hardware_interrupt)
            WakeReasonCategory.KERNEL_SYSTEM_SIGNAL -> string(R.string.bg_wake_reason_kernel_system_signal)
            WakeReasonCategory.HARDWARE_INTERRUPT_SIGNAL -> string(R.string.bg_wake_reason_hardware_interrupt_signal)
            WakeReasonCategory.WAKEUP_ALARM -> string(R.string.bg_type_wakeup_alarm)
            WakeReasonCategory.UNSPECIFIED -> string(R.string.bg_unspecified)
            WakeReasonCategory.OTHER -> WakeReasonCategory.reasonText(rawReason)
        }

    /** "System reason" of a CPU wakeup. */
    fun cpuSystemReason(event: CpuWakeupEvent): String =
        event.rawWakeReason?.let(::wakeReasonCategoryLabel)
            ?: if (event.runningObserved) {
                string(R.string.bg_wake_reason_cpu_activity_no_reason)
            } else {
                string(R.string.bg_unspecified)
            }

    fun detectionLabel(event: CpuWakeupEvent): String =
        string(
            when (DetectionKind.of(event.rawWakeReason != null, event.runningObserved)) {
                DetectionKind.REASON_AND_CPU_START -> R.string.bg_detection_reason_and_cpu_start
                DetectionKind.REASON_ONLY -> R.string.bg_detection_reason_only
                DetectionKind.CPU_START_ONLY -> R.string.bg_detection_cpu_start_only
                DetectionKind.BATTERYSTATS_ACTIVITY -> R.string.bg_detection_batterystats_activity
            }
        )

    fun cpuAwakeDuration(durationMillis: Long?): String {
        val value = durationMillis ?: return string(R.string.bg_duration_not_determinable)

        return when {
            value < 1_000L -> "$value ms"
            value < 60_000L -> string(R.string.bg_duration_seconds, value / 1_000.0)
            else -> string(R.string.bg_duration_minutes_seconds, value / 60_000L, value % 60_000L / 1_000L)
        }
    }

    /** Label of the source a CPU wakeup is attributed to, if any. */
    fun cpuSourceLabel(event: CpuWakeupEvent): String? =
        event.primarySource()?.let(sources::label)

    fun evidenceSourceLabel(evidence: CpuEvidence): String =
        sources.label(evidence.source())

    fun snapshotTriggerLabel(trigger: SnapshotTrigger): String =
        string(
            when (trigger) {
                SnapshotTrigger.AFTER_SCREEN_ON -> R.string.service_snapshot_reason_after_screen_on
                SnapshotTrigger.AFTER_SCREEN_OFF -> R.string.service_snapshot_reason_after_screen_off
                SnapshotTrigger.START_PROBE -> R.string.service_snapshot_reason_start_probe
            }
        )

    fun snapshotClassificationLabel(classification: SnapshotClassification): String =
        string(
            when (classification) {
                SnapshotClassification.ACTIVE -> R.string.service_classify_active
                SnapshotClassification.DEEP_IDLE -> R.string.service_classify_deep_idle
                SnapshotClassification.LIGHT_IDLE -> R.string.service_classify_light_idle
                SnapshotClassification.VENDOR_SPECIFIC -> R.string.service_classify_vendor_specific
                SnapshotClassification.UNCLEAR -> R.string.service_classify_unclear
            }
        )

    fun expertSectionLabel(section: ExpertSection): String =
        string(
            when (section) {
                ExpertSection.LOCATION -> R.string.shizuku_snapshot_section_location
                ExpertSection.SENSORS -> R.string.shizuku_snapshot_section_sensors
                ExpertSection.NETWORK -> R.string.shizuku_snapshot_section_network
            }
        )

    fun expertSignalLabel(signal: ExpertSignal, deviceFamily: DeviceFamily): String =
        string(
            when (signal) {
                ExpertSignal.FUSED_LOCATION -> R.string.shizuku_hint_fused_location
                ExpertSignal.NETWORK_LOCATION -> R.string.shizuku_hint_network_location
                ExpertSignal.GNSS_LOCATION -> R.string.shizuku_hint_gnss_location
                ExpertSignal.ACTIVITY_RECOGNITION -> R.string.shizuku_hint_activity_recognition
                ExpertSignal.GEOFENCING -> R.string.shizuku_hint_geofencing
                ExpertSignal.WEATHER_PASSIVE_LOCATION -> R.string.shizuku_hint_weather_passive_location
                ExpertSignal.OPLUS_LOCATION_SERVICES -> R.string.shizuku_hint_oplus_location_services
                ExpertSignal.PROXIMITY_WAKEUP -> R.string.shizuku_hint_proximity_wakeup
                ExpertSignal.PICK_UP_DETECTION -> R.string.shizuku_hint_pick_up_detection
                ExpertSignal.AOD_LIGHT_WAKEUP -> R.string.shizuku_hint_aod_light_wakeup
                ExpertSignal.ACTIVITY_SENSOR ->
                    when (deviceFamily) {
                        DeviceFamily.ONEPLUS -> R.string.shizuku_hint_oplus_activity_sensor
                        DeviceFamily.SAMSUNG -> R.string.shizuku_hint_samsung_activity_detection
                        DeviceFamily.GENERIC_ANDROID -> R.string.shizuku_hint_activity_detection
                    }
                ExpertSignal.STEP_SENSORS -> R.string.shizuku_hint_step_sensors
                ExpertSignal.SIGNIFICANT_MOTION -> R.string.shizuku_hint_significant_motion
                ExpertSignal.WIFI_CONNECTED -> R.string.shizuku_hint_wifi_connected
                ExpertSignal.CELLULAR_IMS -> R.string.shizuku_hint_cellular_ims
                ExpertSignal.TELEPHONY_REQUESTS -> R.string.shizuku_hint_telephony_requests
                ExpertSignal.QUALCOMM_NETWORK_OPTIMIZATION -> R.string.shizuku_hint_qualcomm_network_optimization
            }
        )

    fun diagnosticErrorLabel(error: DiagnosticError?, detail: String?): String {
        val base =
            when (error) {
                DiagnosticError.SHIZUKU_UNAVAILABLE -> string(R.string.shizuku_error_not_running)
                DiagnosticError.PERMISSION_DENIED -> string(R.string.shizuku_error_permission_missing)
                DiagnosticError.SHELL_FAILED ->
                    return string(R.string.shizuku_error_shell_command_failed, detail.orEmpty())
                DiagnosticError.TIMEOUT, DiagnosticError.UNKNOWN, null ->
                    detail ?: string(R.string.shizuku_error_unknown_netstats)
            }

        return base
    }

    /** Signed offset relative to the screen-on, as a sentence part. */
    fun screenRelation(offsetMillis: Long): String =
        when {
            offsetMillis < 0L -> string(R.string.event_time_before_screen_on, seconds(-offsetMillis))
            offsetMillis > 0L -> string(R.string.event_time_after_screen_on, seconds(offsetMillis))
            else -> string(R.string.event_time_simultaneous_with_screen_on)
        }

    /** Signed offset as a short chain label, e.g. "−1.2 s". */
    fun signedSeconds(offsetMillis: Long): String {
        val value = String.format(Locale.getDefault(), "%.1f s", kotlin.math.abs(offsetMillis) / 1_000.0)

        return when {
            offsetMillis < 0L -> "−$value"
            offsetMillis > 0L -> "+$value"
            else -> value
        }
    }

    fun hintSectionTitle(screenOn: ScreenOnEvent, hint: WakeLockHint): String =
        if (CauseAssessment.relationOf(screenOn, hint) == HintRelation.COMPANION) {
            string(R.string.event_section_companion_wakelock)
        } else {
            string(R.string.event_section_system_hint_possible_trigger)
        }

    fun hintSectionTitle(screenOn: ScreenOnEvent, hint: AlarmHint): String =
        string(
            when (CauseAssessment.relationOf(screenOn, hint)) {
                HintRelation.COMPANION -> R.string.event_section_companion_wakeup_alarm
                HintRelation.POSSIBLE_TRIGGER -> R.string.event_section_wakeup_alarm_possible_trigger
                HintRelation.SIMULTANEOUS -> R.string.event_section_wakeup_alarm_simultaneous
                else -> R.string.event_section_wakeup_alarm_close_relation
            }
        )

    fun hintSectionTitle(screenOn: ScreenOnEvent, hint: JobHint): String =
        if (CauseAssessment.relationOf(screenOn, hint) == HintRelation.COMPANION) {
            string(R.string.event_section_companion_background_job)
        } else {
            string(R.string.event_section_background_job_time_relation)
        }

    fun sourceLabel(packageName: String?): String =
        sources.labelWithPackage(packageName)

    /* ---------------- details ---------------- */

    private fun screenOnDetails(event: ScreenOnEvent): String {
        val sections = mutableListOf<List<String>>()

        val head = mutableListOf<String>()
        head += string(R.string.service_proximity_line, proximityLabel(event.proximity))

        event.wakeReason?.let { head += wakeReasonLines(it) }
        event.notificationCause?.let { head += notificationCauseLines(it) }

        if (CauseAssessment.isUnexplained(event)) {
            head += string(R.string.sleep_marker_cause_unknown)
        }

        sections += head

        event.wakeLockHints.forEach { hint ->
            sections += listOf(
                hintSectionTitle(event, hint),
                labeled(R.string.event_label_source, sources.labelWithPackage(hint.packageName)),
                labeled(R.string.event_label_kind, wakeLockKindLabel(WakeLockTags.kindOf(hint.tag))),
                labeled(R.string.event_label_time_offset, screenRelation(hint.offsetMs)),
                labeled(R.string.event_label_technical_tag, compact(hint.tag.ifBlank { string(R.string.event_unknown) }))
            )
        }

        event.alarmHints.forEach { hint ->
            sections += listOfNotNull(
                hintSectionTitle(event, hint),
                labeled(R.string.event_label_source, sources.labelWithPackage(hint.packageName)),
                labeled(R.string.event_label_time_offset, screenRelation(hint.offsetMs)),
                hint.alarmWakeCount?.let {
                    labeled(R.string.event_label_alarm_wakeups_since_stats_start, it.toString())
                },
                labeled(R.string.event_label_technical_tag, compact(hint.tag))
            )
        }

        event.jobHints.forEach { hint ->
            sections += listOf(
                hintSectionTitle(event, hint),
                labeled(R.string.event_label_source, sources.labelWithPackage(hint.packageName)),
                labeled(R.string.event_label_time_offset, screenRelation(hint.offsetMs)),
                labeled(
                    R.string.event_label_start_type,
                    if (hint.prioritized) {
                        string(R.string.event_start_type_prioritized)
                    } else {
                        string(R.string.event_start_type_regular)
                    }
                ),
                labeled(R.string.event_label_service, compact(hint.serviceName))
            )
        }

        return sections.joinToString("\n\n") { it.joinToString("\n") }
    }

    private fun wakeReasonLines(wakeReason: DirectWakeReason): List<String> =
        listOfNotNull(
            labeled(R.string.event_label_direct_wake_reason, wakeReasonLabel(wakeReason)),
            labeled(R.string.event_label_confidence, wakeReasonEvidenceLabel(wakeReason.evidence)),
            labeled(R.string.event_label_time_offset, screenRelation(wakeReason.offsetMs)),
            labeled(R.string.event_label_technical_reason, technicalWakeReason(wakeReason)),
            if (wakeReason.evidence == WakeReasonEvidence.POWER_MANAGER_LOG) {
                labeled(
                    R.string.event_label_details,
                    compact(wakeReason.rawDetails ?: string(R.string.event_none))
                )
            } else {
                wakeReason.rawTag?.let { labeled(R.string.event_label_technical_tag, compact(it)) }
            }
        )

    private fun notificationCauseLines(cause: NotificationCause): List<String> {
        val app = sources.appName(cause.packageName)
        val offset = kotlin.math.abs(cause.offsetMs)

        return when (CauseAssessment.kindOf(cause)) {
            NotificationCauseKind.PROBABLE ->
                listOf(
                    string(R.string.service_cause_probable, app),
                    string(R.string.service_confidence_high),
                    string(R.string.service_time_offset, string(R.string.bg_duration_seconds, offset / 1000.0))
                )

            NotificationCauseKind.POSSIBLE ->
                listOf(
                    string(R.string.service_cause_possible, app),
                    string(R.string.service_confidence_medium),
                    string(R.string.service_time_offset, string(R.string.bg_duration_seconds, offset / 1000.0))
                )

            NotificationCauseKind.LATER_DETECTED ->
                listOfNotNull(
                    labeled(R.string.event_label_cause_detected_later, app),
                    labeled(
                        R.string.event_label_confidence,
                        when (CauseAssessment.confidenceOf(cause)) {
                            CauseConfidenceLevel.HIGH -> string(R.string.event_confidence_high)
                            CauseConfidenceLevel.MEDIUM -> string(R.string.event_confidence_medium)
                        }
                    ),
                    string(R.string.event_notification_arrived_after_screen_on, seconds(offset)),
                    cause.notificationTitle
                        ?.takeIf { it.isNotBlank() }
                        ?.let { labeled(R.string.event_label_title, it) }
                )
        }
    }

    private fun notificationDetails(event: NotificationEvent): String =
        buildList {
            event.title?.takeIf { it.isNotBlank() }?.let {
                add(string(R.string.service_notification_event_title_line, it))
            }
            event.text?.takeIf { it.isNotBlank() }?.let {
                add(string(R.string.service_notification_event_text_line, it))
            }
            if (event.title.isNullOrBlank() && event.text.isNullOrBlank()) {
                add(string(R.string.service_notification_event_no_content))
            }
            add(string(R.string.service_notification_event_package, event.packageName))
        }.joinToString("\n")

    private fun cpuTitle(event: CpuWakeupEvent): String =
        cpuSourceLabel(event)
            ?.let { string(R.string.bg_title_cpu_wakeup_source, it) }
            ?: string(R.string.bg_title_cpu_woken_background)

    private fun cpuDetails(event: CpuWakeupEvent): String =
        buildList {
            add(string(R.string.bg_detail_display_stayed_off))
            add(string(R.string.bg_detail_system_reason, cpuSystemReason(event)))
            add(string(R.string.bg_detail_detection, detectionLabel(event)))
            add(string(R.string.bg_detail_cpu_awake_time, cpuAwakeDuration(event.awakeMs)))
            add(
                string(
                    R.string.bg_detail_return_to_sleep,
                    if (event.awakeMs != null) {
                        string(R.string.bg_return_to_sleep_detected)
                    } else {
                        string(R.string.bg_return_to_sleep_not_determined)
                    }
                )
            )

            val primary = event.primaryEvidence

            if (primary != null) {
                add(string(R.string.bg_detail_possible_source, evidenceSourceLabel(primary)))
                add(string(R.string.bg_detail_activity, evidenceTypeLabel(primary.type)))
                add(string(R.string.bg_detail_assessment_correlated))
            } else {
                add(string(R.string.bg_detail_possible_source, string(R.string.bg_possible_source_ambiguous)))
            }

            add("")
            add(string(R.string.bg_detail_related_activities))

            if (event.evidence.isEmpty()) {
                add(string(R.string.bg_detail_no_related_activity))
            } else {
                event.evidence.forEach {
                    add(string(R.string.bg_detail_evidence_item, evidenceTypeLabel(it.type), evidenceSourceLabel(it)))
                }
            }

            add("")
            add(
                string(
                    R.string.bg_detail_technical_wake_reason,
                    event.rawWakeReason ?: string(R.string.bg_technical_wake_reason_missing)
                )
            )

            val technicalSources = event.evidence.map { it.rawSource }.distinct()

            if (technicalSources.isNotEmpty()) {
                add(string(R.string.bg_detail_technical_sources))
                technicalSources.forEach { add("• $it") }
            }

            add(string(R.string.bg_detail_data_source))
        }.joinToString("\n")

    private fun networkDetails(measurement: NetworkMeasurement?): String {
        if (measurement == null || measurement.status == NetworkMeasurementStatus.NO_BASELINE) {
            return string(R.string.service_network_no_baseline)
        }

        if (measurement.status == NetworkMeasurementStatus.END_FAILED) {
            return string(
                R.string.service_network_end_failed,
                diagnosticErrorLabel(measurement.errorCode, measurement.errorDetail)
            )
        }

        return buildList {
            add(string(R.string.service_network_duration, sessionDuration(measurement.durationMs ?: 0L)))
            add(string(R.string.service_network_apps_with_traffic, measurement.usage.size))
            add(
                string(
                    R.string.service_network_total,
                    bytes(measurement.totalBytes),
                    bytes(measurement.rxBytes),
                    bytes(measurement.txBytes)
                )
            )

            if (measurement.usage.isEmpty()) {
                add(string(R.string.service_network_no_traffic))
            } else {
                add("")
                add(string(R.string.service_network_top_apps))

                measurement.usage.take(NETWORK_TOP_APPS).forEach { usage ->
                    add("• " + sources.networkLabel(usage.packageName, usage.uid) + " · " + bytes(usage.totalBytes))
                    add("  " + string(R.string.service_network_app_transfer, bytes(usage.rxBytes), bytes(usage.txBytes)))
                    add("  " + string(R.string.service_network_app_package, usage.packageName ?: "UID ${usage.uid}"))
                }
            }
        }.joinToString("\n")
    }

    private fun systemSnapshotDetails(snapshot: SystemSnapshot): String {
        val trigger = string(R.string.service_snapshot_trigger, snapshotTriggerLabel(snapshot.trigger))
        val source = string(R.string.service_snapshot_source_background)

        return when (snapshot.status) {
            SnapshotStatus.SHIZUKU_UNAVAILABLE ->
                listOf(trigger, string(R.string.service_snapshot_status_shizuku_unavailable), source)

            SnapshotStatus.ERROR ->
                listOf(
                    trigger,
                    string(R.string.service_snapshot_status_error),
                    string(R.string.service_snapshot_error, snapshot.errorDetail ?: string(R.string.service_unknown)),
                    source
                )

            SnapshotStatus.TIMEOUT_OR_EMPTY ->
                listOf(trigger, string(R.string.service_snapshot_status_timeout), source)

            SnapshotStatus.OK ->
                listOf(
                    trigger,
                    "Power: " + compactValues(
                        snapshot.wakefulness?.let { "Wakefulness=$it" },
                        snapshot.interactive?.let { "Interactive=$it" },
                        snapshot.lowPowerMode?.let { "PowerSave=$it" }
                    ),
                    "DeviceIdle: " + compactValues(
                        snapshot.deepIdleState?.let { "Deep=$it" },
                        snapshot.lightIdleState?.let { "Light=$it" },
                        snapshot.deviceIdleMode?.let { "DeepMode=$it" },
                        snapshot.lightDeviceIdleMode?.let { "LightMode=$it" }
                    ),
                    string(
                        R.string.service_snapshot_conditions,
                        compactValues(
                            snapshot.idleScreenOn?.let { "ScreenOn=$it" },
                            snapshot.idleCharging?.let { "Charging=$it" },
                            snapshot.forceIdle?.let { "ForceIdle=$it" }
                        )
                    ),
                    string(
                        R.string.service_snapshot_classification,
                        snapshotClassificationLabel(SnapshotClassification.of(snapshot))
                    ),
                    source
                )
        }.joinToString("\n")
    }

    private fun expertSnapshotDetails(event: ExpertSnapshotEvent): String {
        val snapshot = event.snapshot

        val header =
            string(R.string.service_snapshot_trigger, string(R.string.service_snapshot_reason_screen_on)) + "\n" +
                string(R.string.service_snapshot_source_compact) + "\n\n"

        if (snapshot.status != ExpertSnapshotStatus.OK) {
            return header + string(
                R.string.service_diagnostic_error,
                snapshot.errorDetail ?: string(R.string.service_unknown)
            )
        }

        val body =
            ExpertSection.entries.joinToString("\n\n") { section ->
                val items =
                    snapshot.signals
                        .filter { it.section == section }
                        .sortedBy { it.ordinal }
                        .map { "• " + expertSignalLabel(it, event.deviceFamily) }

                expertSectionLabel(section) + "\n" +
                    items.ifEmpty { listOf(string(R.string.shizuku_snapshot_no_hits)) }.joinToString("\n")
            }

        return header + body + "\n\n" + string(R.string.shizuku_snapshot_note)
    }

    /* ---------------- formatting ---------------- */

    fun bytes(value: Long): String {
        val safe = value.coerceAtLeast(0L)

        return when {
            safe >= 1024L * 1024L * 1024L ->
                String.format(Locale.getDefault(), "%.1f GB", safe / (1024.0 * 1024.0 * 1024.0))
            safe >= 1024L * 1024L ->
                String.format(Locale.getDefault(), "%.1f MB", safe / (1024.0 * 1024.0))
            safe >= 1024L ->
                String.format(Locale.getDefault(), "%.1f KB", safe / 1024.0)
            else -> "$safe B"
        }
    }

    fun sessionDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = totalSeconds % 3_600L / 60L
        val seconds = totalSeconds % 60L

        return when {
            hours > 0L -> "$hours h $minutes min"
            minutes > 0L -> "$minutes min $seconds s"
            else -> "$seconds s"
        }
    }

    private fun seconds(milliseconds: Long): String =
        string(R.string.event_age_seconds, milliseconds / 1000.0)

    private fun compactValues(vararg values: String?): String =
        values
            .filterNotNull()
            .ifEmpty { listOf(string(R.string.service_snapshot_no_compact_values)) }
            .joinToString(", ")

    private fun labeled(@StringRes labelId: Int, value: String): String =
        string(labelId) + " " + value

    private fun compact(value: String): String =
        if (value.length <= 110) value else value.take(107) + "…"

    private fun string(@StringRes id: Int, vararg args: Any): String =
        context.getString(id, *args)

    companion object {
        const val NETWORK_TOP_APPS = 15

        private const val WORK_MANAGER = "WorkManager"
        private const val JOB_SCHEDULER = "JobScheduler"
        private const val PMIC_POWER_KEY = "pmic_pwrkey"
        private const val POLICY_POWER = "android.policy:POWER"
        private const val DISPLAY_REASON_KEY = "Display reason=KEY"
        private const val SAMSUNG_POWER_KEY = "Samsung Power-Key"
        private const val WINDOW_MANAGER_POWER_KEY = "PhoneWindowManager Power-Key"

        /** Export/UI label of the event type (upper case, as before). */
        @StringRes
        fun typeLabel(type: EventType): Int? =
            when (type) {
                EventType.MONITOR_START, EventType.MONITOR_STOP -> R.string.event_type_monitor
                EventType.NETWORK_SESSION -> R.string.event_type_network_session
                EventType.SCREEN_ON -> R.string.event_type_screen_on
                EventType.SCREEN_OFF -> R.string.event_type_screen_off
                EventType.POWER_CONNECTED -> R.string.event_type_power_connected
                EventType.POWER_DISCONNECTED -> R.string.event_type_power_disconnected
                EventType.USB_ATTACHED -> R.string.event_type_usb_attached
                EventType.USB_DETACHED -> R.string.event_type_usb_detached
                EventType.NOTIFICATION -> R.string.event_type_notification
                EventType.CPU_WAKEUP -> R.string.event_type_cpu_wakeup
                EventType.SYSTEM_SNAPSHOT, EventType.EXPERT_SNAPSHOT -> null
            }
    }
}
