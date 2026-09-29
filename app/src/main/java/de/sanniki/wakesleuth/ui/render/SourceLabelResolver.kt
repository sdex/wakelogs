package de.sanniki.wakesleuth.ui.render

import android.content.Context
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.data.PackageLabelStore
import de.sanniki.wakesleuth.data.WakelogsData
import de.sanniki.wakesleuth.domain.SourceClassifier
import de.sanniki.wakesleuth.domain.SourceKind
import de.sanniki.wakesleuth.domain.SourceRef

/**
 * Display names of sources. Fixed system buckets come from string
 * resources, apps from the package manager (with the stored label as a
 * fallback for uninstalled apps).
 */
class SourceLabelResolver(
    private val context: Context,
    private val labels: PackageLabelStore,
) {
    fun label(source: SourceRef): String =
        when (source.kind) {
            SourceKind.APP -> {
                source.packageName?.let(labels::label)
                    ?: source.rawSource?.let(::technicalFallback)
                    ?: source.packageName
                    ?: context.getString(R.string.bg_source_unknown_system)
            }

            SourceKind.UID_ONLY -> {
                uidLabel(source.rawSource?.toIntOrNull())
            }

            SourceKind.UNKNOWN_SYSTEM -> {
                SourceClassifier
                    .technicalName(source.rawSource)
                    .ifBlank { context.getString(R.string.bg_source_unknown_system) }
            }

            SourceKind.PLAY_STORE -> {
                PLAY_STORE_NAME
            }

            SourceKind.TIME_TICK -> {
                TIME_TICK_NAME
            }

            else -> {
                context.getString(kindLabel(source.kind))
            }
        }

    /** "Label (package)", as shown for hints next to a screen-on. */
    fun labelWithPackage(packageName: String?): String {
        val pkg = packageName?.trim()?.takeIf { it.isNotEmpty() }
            ?: return context.getString(R.string.event_unknown)

        val source = SourceClassifier.classify(pkg)

        if (source.kind != SourceKind.APP) {
            return "${label(source)} ($pkg)"
        }

        return labels
            .label(pkg)
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it ($pkg)" }
            ?: pkg
    }

    /** App name as the package manager knows it, else the package. */
    fun appName(packageName: String): String = labels.label(packageName)?.takeIf { it.isNotBlank() } ?: packageName

    /** Name of a traffic entry: the app, or the system user of the UID. */
    fun networkLabel(
        packageName: String?,
        uid: Int,
    ): String =
        packageName?.takeIf { it.isNotBlank() }?.let(::appName)
            ?: uidLabel(uid)

    fun uidLabel(uid: Int?): String {
        if (uid != null && uid >= FIRST_APPLICATION_UID) {
            // An app UID without a resolvable package is not a system service.
            return context.getString(R.string.source_uid_app, uid)
        }

        return context.getString(
            when (uid) {
                1000 -> R.string.source_android_system
                1001 -> R.string.source_phone_service
                1002 -> R.string.source_bluetooth_system_service
                1010 -> R.string.source_wifi_system_service
                1013 -> R.string.source_media_service
                1016 -> R.string.source_vpn_system_service
                1019 -> R.string.source_drm_system_service
                1020 -> R.string.source_android_network_service
                else -> R.string.source_android_system_service
            },
        )
    }

    /** A package that is not installed falls back to its technical token. */
    private fun technicalFallback(rawSource: String): String? {
        val fallback = SourceClassifier.classifyRaw(rawSource)

        return when (fallback.kind) {
            SourceKind.APP -> fallback.packageName?.let(::appName)
            else -> label(fallback)
        }
    }

    private fun kindLabel(kind: SourceKind): Int =
        when (kind) {
            SourceKind.ANDROID_SYSTEM -> R.string.source_android_system

            SourceKind.SYSTEM_UI -> R.string.source_android_system_ui

            SourceKind.GOOGLE_PLAY_SERVICES -> R.string.source_google_play_services

            SourceKind.PHONE_SERVICE -> R.string.source_android_phone_service

            SourceKind.MMS_CELLULAR_SERVICE -> R.string.source_android_mms_cellular_service

            SourceKind.TELEPHONY_STORAGE -> R.string.source_android_telephony_storage

            SourceKind.GOOGLE_CELLULAR_IMS -> R.string.source_google_cellular_ims_service

            SourceKind.BLUETOOTH -> R.string.source_android_bluetooth_service

            SourceKind.NETWORK_STACK -> R.string.source_android_networkstack_service

            SourceKind.CONTACTS -> R.string.source_android_contacts

            SourceKind.CALENDAR -> R.string.source_android_calendar

            SourceKind.SAMSUNG_TELEPHONY_SIM -> R.string.source_samsung_telephony_sim_service

            SourceKind.SAMSUNG_OFFLINE_FINDING -> R.string.source_samsung_offline_finding

            SourceKind.SAMSUNG_SYSTEM_SERVICE -> R.string.source_samsung_system_service

            SourceKind.ONEPLUS_SYSTEM_SERVICE -> R.string.source_oneplus_system_service

            SourceKind.ONEPLUS_SCREEN_GESTURES -> R.string.source_oneplus_screen_gestures

            SourceKind.RADIO_NETWORK -> R.string.event_source_radio_network

            SourceKind.APP,
            SourceKind.PLAY_STORE,
            SourceKind.TIME_TICK,
            SourceKind.UID_ONLY,
            SourceKind.UNKNOWN_SYSTEM,
            -> R.string.bg_source_unknown_system
        }

    companion object {
        /** Android's `Process.FIRST_APPLICATION_UID`. */
        private const val FIRST_APPLICATION_UID = 10_000

        private const val PLAY_STORE_NAME = "Google Play Store"
        private const val TIME_TICK_NAME = "Android TIME_TICK"

        fun get(context: Context): SourceLabelResolver = SourceLabelResolver(context, WakelogsData.get(context).labels)
    }
}
