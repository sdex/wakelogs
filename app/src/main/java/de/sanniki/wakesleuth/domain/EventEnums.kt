package de.sanniki.wakesleuth.domain

/*
 * Typed facts stored in the database. Every enum is persisted by its
 * `name`, so constants must never be renamed without a schema migration.
 * Nothing here carries user-visible text; labels are resolved at render
 * time from string resources.
 */

enum class EventType {
    MONITOR_START,
    MONITOR_STOP,
    SCREEN_ON,
    SCREEN_OFF,
    POWER_CONNECTED,
    POWER_DISCONNECTED,
    USB_ATTACHED,
    USB_DETACHED,
    NOTIFICATION,
    CPU_WAKEUP,
    NETWORK_SESSION,
    SYSTEM_SNAPSHOT,
    EXPERT_SNAPSHOT,
}

enum class ProximityState {
    NEAR,
    FAR,
    NO_READING,
    NOT_PRESENT,
    REGISTRATION_FAILED,
    NOT_AVAILABLE,
}

enum class SessionEndReason {
    USER_STOP,
    INTERRUPTED,
}

/** Direct screen wake reason, derived from `WAKE_REASON_*` and details. */
enum class WakeReason {
    POWER_BUTTON,
    DOUBLE_TAP,
    GESTURE,
    LIFT,
    PLUGGED_IN,
    WAKE_KEY,
    WAKE_MOTION,
    APPLICATION,
    OTHER,
}

/** Which system source proved the direct wake reason. */
enum class WakeReasonEvidence {
    POWER_MANAGER_LOG,
    BATTERYSTATS_POWER_KEY,
    POWER_KEY_WAKELOCK,
}

/** The BatteryStats / wakelock signal that identified a power key press. */
enum class PowerKeySignal {
    PMIC_PWRKEY,
    POLICY_POWER,
    DISPLAY_REASON_KEY,
    POWER_KEY_WAKELOCK,
}

/** BatteryStats history token a CPU wakeup evidence was read from. */
enum class EvidenceOrigin {
    WAKELOCK,
    JOB,
    SYNC,
}

/**
 * Evidence classification of a CPU wakeup. The declaration order is the
 * priority used to choose the primary ("possible source") evidence.
 */
enum class EvidenceType {
    SYNC,
    WORKMANAGER,
    JOBSCHEDULER,
    WAKEUP_ALARM,
    JOB_WAKELOCK,
    PARTIAL_WAKELOCK,
}

enum class NetworkMeasurementStatus {
    OK,
    NO_BASELINE,
    END_FAILED,
}

enum class DiagnosticError {
    SHIZUKU_UNAVAILABLE,
    PERMISSION_DENIED,
    SHELL_FAILED,
    TIMEOUT,
    UNKNOWN,
}

enum class SnapshotTrigger {
    AFTER_SCREEN_ON,
    AFTER_SCREEN_OFF,
    START_PROBE,
}

enum class SnapshotStatus {
    OK,
    SHIZUKU_UNAVAILABLE,
    ERROR,
    TIMEOUT_OR_EMPTY,
}

enum class ExpertSnapshotStatus {
    OK,
    ERROR,
    SHIZUKU_UNAVAILABLE,
}

enum class ExpertSection {
    LOCATION,
    SENSORS,
    NETWORK,
}

enum class ExpertSignal(
    val section: ExpertSection,
) {
    FUSED_LOCATION(ExpertSection.LOCATION),
    NETWORK_LOCATION(ExpertSection.LOCATION),
    GNSS_LOCATION(ExpertSection.LOCATION),
    ACTIVITY_RECOGNITION(ExpertSection.LOCATION),
    GEOFENCING(ExpertSection.LOCATION),
    WEATHER_PASSIVE_LOCATION(ExpertSection.LOCATION),
    OPLUS_LOCATION_SERVICES(ExpertSection.LOCATION),
    PROXIMITY_WAKEUP(ExpertSection.SENSORS),
    PICK_UP_DETECTION(ExpertSection.SENSORS),
    AOD_LIGHT_WAKEUP(ExpertSection.SENSORS),
    ACTIVITY_SENSOR(ExpertSection.SENSORS),
    STEP_SENSORS(ExpertSection.SENSORS),
    SIGNIFICANT_MOTION(ExpertSection.SENSORS),
    WIFI_CONNECTED(ExpertSection.NETWORK),
    CELLULAR_IMS(ExpertSection.NETWORK),
    TELEPHONY_REQUESTS(ExpertSection.NETWORK),
    QUALCOMM_NETWORK_OPTIMIZATION(ExpertSection.NETWORK),
}

/**
 * Bucket a source belongs to. Only [APP] is resolved through the package
 * manager; every other kind has a fixed string resource.
 */
enum class SourceKind {
    APP,
    ANDROID_SYSTEM,
    SYSTEM_UI,
    GOOGLE_PLAY_SERVICES,
    PLAY_STORE,
    PHONE_SERVICE,
    MMS_CELLULAR_SERVICE,
    TELEPHONY_STORAGE,
    GOOGLE_CELLULAR_IMS,
    BLUETOOTH,
    NETWORK_STACK,
    CONTACTS,
    CALENDAR,
    SAMSUNG_TELEPHONY_SIM,
    SAMSUNG_OFFLINE_FINDING,
    SAMSUNG_SYSTEM_SERVICE,
    ONEPLUS_SYSTEM_SERVICE,
    ONEPLUS_SCREEN_GESTURES,
    RADIO_NETWORK,
    TIME_TICK,
    UID_ONLY,
    UNKNOWN_SYSTEM,
}
