package de.sanniki.wakesleuth

private val standaloneUidPattern =
    Regex(
        """^(?:systemdienst\s*·\s*)?uid\s+(\d+)$""",
        RegexOption.IGNORE_CASE
    )

fun sourceDisplayName(
    rawName: String
): String {
    val cleaned =
        rawName.trim()

    val uidMatch =
        standaloneUidPattern
            .matchEntire(cleaned)

    val lower =
        cleaned.lowercase()

    return when {
        uidMatch != null -> {
            when (
                uidMatch.groupValues[1]
                    .toIntOrNull()
            ) {
                1000 ->
                    "Android-System"

                1001 ->
                    "Telefoniedienst"

                1002 ->
                    "Bluetooth-Systemdienst"

                1010 ->
                    "WLAN-Systemdienst"

                1013 ->
                    "Mediendienst"

                1016 ->
                    "VPN-Systemdienst"

                1019 ->
                    "DRM-Systemdienst"

                1020 ->
                    "Android-Netzwerkdienst"

                else ->
                    "Android-Systemdienst"
            }
        }

        lower.contains(
            "com.android.stk2"
        ) ||
            lower.contains(
                "telephony-sem-radio"
            ) ||
            lower.contains(
                "rilj_ack_wl"
            ) ->
            "Samsung Telefonie-/SIM-Dienst"

        lower.contains(
            "fmm-acquirewakelock"
        ) ||
            lower.contains(
                "offlinefindtask"
            ) ->
            "Samsung Offline-Suche"

        lower.contains(
            "oplusscreenoffgesturemanager"
        ) ||
            lower.contains(
                "manimcpulock"
            ) ->
            "OnePlus Bildschirmgesten"

        else ->
            cleaned
    }
}

fun sourceDisplayName(
    appLabel: String?,
    packageName: String?,
    uid: Int
): String {
    val preferred =
        appLabel
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }
            ?: packageName
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
            ?: "UID $uid"

    return sourceDisplayName(preferred)
}
