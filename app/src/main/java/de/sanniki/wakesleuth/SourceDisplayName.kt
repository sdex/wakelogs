package de.sanniki.wakesleuth

import android.content.Context

private val standaloneUidPattern =
    Regex(
        """^(?:(.+?)\s*·\s*)?uid\s+(\d+)$""",
        RegexOption.IGNORE_CASE
    )

fun sourceDisplayName(
    context: Context,
    rawName: String
): String {
    val cleaned =
        rawName.trim()

    val uidMatch =
        standaloneUidPattern
            .matchEntire(cleaned)
            ?.takeIf { match ->
                val prefix =
                    match.groupValues[1]

                prefix.isEmpty() ||
                    LocalizedText
                        .variants(
                            context,
                            R.string.source_system_service
                        )
                        .any {
                            it.equals(
                                prefix,
                                ignoreCase = true
                            )
                        }
            }

    val lower =
        cleaned.lowercase()

    return when {
        uidMatch != null -> {
            when (
                uidMatch.groupValues[2]
                    .toIntOrNull()
            ) {
                1000 ->
                    context.getString(
                        R.string.source_android_system
                    )

                1001 ->
                    context.getString(
                        R.string.source_phone_service
                    )

                1002 ->
                    context.getString(
                        R.string.source_bluetooth_system_service
                    )

                1010 ->
                    context.getString(
                        R.string.source_wifi_system_service
                    )

                1013 ->
                    context.getString(
                        R.string.source_media_service
                    )

                1016 ->
                    context.getString(
                        R.string.source_vpn_system_service
                    )

                1019 ->
                    context.getString(
                        R.string.source_drm_system_service
                    )

                1020 ->
                    context.getString(
                        R.string.source_android_network_service
                    )

                else ->
                    context.getString(
                        R.string.source_android_system_service
                    )
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
            context.getString(
                R.string.source_samsung_telephony_sim_service
            )

        lower.contains(
            "fmm-acquirewakelock"
        ) ||
            lower.contains(
                "offlinefindtask"
            ) ->
            context.getString(
                R.string.source_samsung_offline_finding
            )

        lower.contains(
            "oplusscreenoffgesturemanager"
        ) ||
            lower.contains(
                "manimcpulock"
            ) ->
            context.getString(
                R.string.source_oneplus_screen_gestures
            )

        else ->
            cleaned
    }
}

fun sourceDisplayName(
    context: Context,
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

    return sourceDisplayName(
        context,
        preferred
    )
}
