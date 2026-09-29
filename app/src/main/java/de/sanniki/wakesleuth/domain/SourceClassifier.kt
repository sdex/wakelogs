package de.sanniki.wakesleuth.domain

import java.util.Locale

/**
 * Identity of an app or system source. Sources are compared by
 * [groupKey], never by a display label.
 */
data class SourceRef(
    val kind: SourceKind,
    val packageName: String? = null,
    /** Technical token (tag, BatteryStats source, UID) when useful. */
    val rawSource: String? = null,
) {
    /**
     * Sources with the same key are one row in statistics: an app by its
     * package, a fixed system bucket by its kind, everything else by the
     * normalized technical token.
     */
    val groupKey: String
        get() = when (kind) {
            SourceKind.APP -> {
                "app:" + (packageName ?: rawSource).orEmpty().lowercase(Locale.ROOT)
            }

            SourceKind.UID_ONLY -> {
                "uid:" + rawSource.orEmpty()
            }

            SourceKind.UNKNOWN_SYSTEM -> {
                "raw:" + SourceClassifier.technicalName(rawSource).lowercase(Locale.ROOT)
            }

            else -> {
                "kind:" + kind.name
            }
        }
}

/**
 * The single place that maps package names and technical tokens to a
 * [SourceKind]. Replaces the five hand-maintained label tables of the
 * text based storage.
 */
object SourceClassifier {
    /**
     * Package first; a raw token is only consulted when there is no
     * package or it names no specific bucket.
     */
    fun classify(
        packageName: String?,
        rawSource: String? = null,
    ): SourceRef {
        val pkg = packageName?.trim()?.takeIf { it.isNotEmpty() }
        val raw = rawSource?.trim()?.takeIf { it.isNotEmpty() }

        if (pkg != null) {
            val kind = kindOfPackage(pkg)
            return SourceRef(kind = kind, packageName = pkg, rawSource = raw)
        }

        if (raw != null) {
            return classifyRaw(raw)
        }

        return SourceRef(kind = SourceKind.UNKNOWN_SYSTEM)
    }

    /** Traffic of a UID without any package. */
    fun forUid(uid: Int): SourceRef = SourceRef(kind = SourceKind.UID_ONLY, rawSource = uid.toString())

    fun kindOfPackage(packageName: String): SourceKind {
        val value = packageName.lowercase(Locale.ROOT)

        return when {
            value == "android" || value == "system" -> {
                SourceKind.ANDROID_SYSTEM
            }

            value.startsWith("com.google.android.gms") -> {
                SourceKind.GOOGLE_PLAY_SERVICES
            }

            value.startsWith("com.android.vending") -> {
                SourceKind.PLAY_STORE
            }

            value.startsWith("com.android.systemui") -> {
                SourceKind.SYSTEM_UI
            }

            value.startsWith("com.android.stk2") -> {
                SourceKind.SAMSUNG_TELEPHONY_SIM
            }

            value.startsWith("com.android.mms.service") -> {
                SourceKind.MMS_CELLULAR_SERVICE
            }

            value.startsWith("com.android.phone") -> {
                SourceKind.PHONE_SERVICE
            }

            value.startsWith("com.android.providers.telephony") -> {
                SourceKind.TELEPHONY_STORAGE
            }

            value.startsWith("com.android.providers.contacts") -> {
                SourceKind.CONTACTS
            }

            value.startsWith("com.android.providers.calendar") -> {
                SourceKind.CALENDAR
            }

            value.startsWith("com.google.android.ims") -> {
                SourceKind.GOOGLE_CELLULAR_IMS
            }

            value.startsWith("com.android.bluetooth") -> {
                SourceKind.BLUETOOTH
            }

            value.startsWith("com.android.networkstack") -> {
                SourceKind.NETWORK_STACK
            }

            value.startsWith("com.samsung.") || value.startsWith("com.sec.") -> {
                SourceKind.SAMSUNG_SYSTEM_SERVICE
            }

            value.startsWith("com.oplus.") ||
                value.startsWith("com.coloros.") || value.startsWith("com.heytap.") -> {
                SourceKind.ONEPLUS_SYSTEM_SERVICE
            }

            else -> {
                SourceKind.APP
            }
        }
    }

    /** Classification of a technical token without a usable package. */
    fun classifyRaw(rawSource: String): SourceRef {
        val lower = rawSource.lowercase(Locale.ROOT)

        fun has(vararg needles: String) = needles.any { lower.contains(it) }

        fun bucket(kind: SourceKind) = SourceRef(kind = kind, rawSource = rawSource)

        return when {
            has("com.android.stk2", "telephony-sem-radio", "rilj_ack_wl") -> {
                bucket(SourceKind.SAMSUNG_TELEPHONY_SIM)
            }

            has("fmm-acquirewakelock", "offlinefindtask") -> {
                bucket(SourceKind.SAMSUNG_OFFLINE_FINDING)
            }

            has("oplusscreenoffgesturemanager", "manimcpulock") -> {
                bucket(SourceKind.ONEPLUS_SCREEN_GESTURES)
            }

            has("gmail-ls") -> {
                SourceRef(SourceKind.APP, packageName = "com.google.android.gm", rawSource = rawSource)
            }

            has("com.whatsapp") -> {
                SourceRef(SourceKind.APP, packageName = "com.whatsapp", rawSource = rawSource)
            }

            has(
                "com.google.android.gms",
                "com.google.android.location",
                "gms_scheduler",
                "callbackrunner",
                "cmwakelock",
                "gcoreflp",
                "networklocation",
                "fusedlocation",
                "gnsslocationprovider",
                "geofencer",
                "gmsalarm",
                "activity_detection",
            ) -> {
                bucket(SourceKind.GOOGLE_PLAY_SERVICES)
            }

            has("com.android.vending") -> {
                bucket(SourceKind.PLAY_STORE)
            }

            has("com.android.phone", "*telephony-radio*") -> {
                bucket(SourceKind.PHONE_SERVICE)
            }

            has("com.samsung.", "com.sec.") -> {
                bucket(SourceKind.SAMSUNG_SYSTEM_SERVICE)
            }

            has("oplus", "oneplus", "athena") -> {
                bucket(SourceKind.ONEPLUS_SYSTEM_SERVICE)
            }

            has(
                "ipa_client",
                "qrtr",
                "ipcc_",
                "qcom_rx",
                "wlan_wake_irq",
                "iwlan",
            ) || hasRadioToken(lower) -> {
                bucket(SourceKind.RADIO_NETWORK)
            }

            has("time_tick") -> {
                bucket(SourceKind.TIME_TICK)
            }

            has("systemui.aod", "aod.hide_time") -> {
                bucket(SourceKind.SYSTEM_UI)
            }

            has("android/com.android.server") -> {
                bucket(SourceKind.ANDROID_SYSTEM)
            }

            else -> {
                bucket(SourceKind.UNKNOWN_SYSTEM)
            }
        }
    }

    /**
     * `radio`, `cellular` and `rmnet*` only count as whole words of the
     * token (split at anything that is not a letter or digit), so names
     * like "radioactive" or "cellularity" stay unclassified.
     */
    private fun hasRadioToken(lowerRawSource: String): Boolean =
        lowerRawSource
            .split(NON_ALPHANUMERIC)
            .any { it == "radio" || it == "cellular" || it.startsWith("rmnet") }

    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

    /**
     * Readable part of an unknown technical token (component before the
     * first `/`), or empty when nothing is left.
     */
    fun technicalName(rawSource: String?): String =
        rawSource
            .orEmpty()
            .substringBefore('/')
            .trim()
}
