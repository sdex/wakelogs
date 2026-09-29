package de.sanniki.wakesleuth.ui.diagnostics

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import de.sanniki.wakesleuth.BackgroundJobDiagnostic
import de.sanniki.wakesleuth.NetworkStatsDiagnostic
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.WakeLockDiagnostic
import de.sanniki.wakesleuth.WakeReasonDiagnostic
import de.sanniki.wakesleuth.WakeupAlarmDiagnostic
import de.sanniki.wakesleuth.ui.common.formatDuration
import de.sanniki.wakesleuth.ui.common.formatNetworkBytes
import de.sanniki.wakesleuth.ui.diagnostics.compactWakeLockTag
import de.sanniki.wakesleuth.ui.diagnostics.compactWakeReasonDetails
import de.sanniki.wakesleuth.ui.diagnostics.formatWakeLockTimestamp
import de.sanniki.wakesleuth.ui.diagnostics.resolveWakeLockSource
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver

internal fun buildManualDiagnosticsExport(
    context: Context,
    wakeLockDiagnostic: WakeLockDiagnostic?,
    wakeupAlarmDiagnostic: WakeupAlarmDiagnostic?,
    backgroundJobDiagnostic: BackgroundJobDiagnostic?,
    wakeReasonDiagnostic: WakeReasonDiagnostic?,
    networkStatsDiagnostic: NetworkStatsDiagnostic?,
): String {
    fun sourceFor(packageName: String?): String = resolveWakeLockSource(context = context, packageName = packageName)

    fun errorText(error: String?): String =
        error?.trim()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.main_diag_unknown_error)

    return buildString {
        appendLine("==================================================")
        appendLine(context.getString(R.string.main_diag_export_title))
        appendLine(context.getString(R.string.main_diag_export_intro))

        appendLine()
        appendLine(context.getString(R.string.main_diag_wakelocks))

        when {
            wakeLockDiagnostic == null -> {
                appendLine(context.getString(R.string.main_diag_status_not_run))
            }

            wakeLockDiagnostic.error != null -> {
                appendLine(context.getString(R.string.main_diag_status_failed))
                appendLine(context.getString(R.string.main_diag_error, errorText(wakeLockDiagnostic.error)))
            }

            else -> {
                appendLine(context.getString(R.string.main_diag_active_wakelocks, wakeLockDiagnostic.activeCount))

                if (
                    wakeLockDiagnostic.rawLastEntry == null
                ) {
                    appendLine(context.getString(R.string.main_diag_last_partial_wakelock_no_data))
                } else {
                    appendLine(context.getString(R.string.main_diag_last_partial_wakelock))
                    appendLine(
                        context.getString(R.string.main_diag_bullet_source, sourceFor(wakeLockDiagnostic.lastPackage)),
                    )
                    appendLine(
                        context.getString(
                            R.string.main_diag_bullet_time,
                            formatWakeLockTimestamp(context, wakeLockDiagnostic.lastTimestamp),
                        ),
                    )
                    appendLine(
                        context.getString(
                            R.string.main_diag_bullet_technical_tag,
                            compactWakeLockTag(context, wakeLockDiagnostic.lastTag),
                        ),
                    )
                }

                appendLine()
                appendLine(context.getString(R.string.main_diag_wakelock_history))

                if (
                    wakeLockDiagnostic.historyEntries.isEmpty()
                ) {
                    appendLine(context.getString(R.string.main_diag_bullet_no_data))
                } else {
                    val groupedEntries = wakeLockDiagnostic
                        .historyEntries
                        .groupBy { entry ->
                            entry.packageName.lowercase() +
                                "|" +
                                compactWakeLockTag(context, entry.tag).lowercase() +
                                "|" + entry.wakeLockType.lowercase()
                        }.values
                        .map { group ->
                            group.sortedByDescending { it.startTimestampMillis }
                        }.sortedByDescending { group ->
                            group.firstOrNull()?.startTimestampMillis
                                ?: 0L
                        }.take(10)

                    groupedEntries.forEach { group ->
                        val entry = group.first()

                        val finishedDurations = group.mapNotNull { it.durationMillis }

                        val durationText = when {
                            finishedDurations.isNotEmpty() &&
                                (
                                    finishedDurations.maxOrNull()
                                        ?: 0L
                                ) < 1_000L -> {
                                context.getString(R.string.main_diag_short_activity)
                            }

                            finishedDurations.isNotEmpty() -> {
                                formatDuration(context, finishedDurations.maxOrNull())
                            }

                            else -> {
                                context.getString(R.string.main_diag_end_not_in_window)
                            }
                        }

                        appendLine("• " + sourceFor(entry.packageName) + " · " + entry.wakeLockType)

                        appendLine(
                            "  " +
                                context.getString(
                                    R.string.main_diag_wakelock_count_start_duration,
                                    group.size,
                                    formatWakeLockTimestamp(context, entry.startTimestamp),
                                    durationText,
                                ),
                        )

                        appendLine(
                            "  " +
                                context.getString(
                                    R.string.main_diag_effect,
                                    if (
                                        group.any { it.causesWake }
                                    ) {
                                        context.getString(R.string.main_diag_effect_can_wake_display)
                                    } else {
                                        context.getString(R.string.main_diag_effect_no_display_wake)
                                    },
                                ),
                        )

                        appendLine(
                            "  " + context.getString(R.string.main_diag_tag, compactWakeLockTag(context, entry.tag)),
                        )
                    }
                }
            }
        }

        appendLine()
        appendLine(context.getString(R.string.main_diag_wakeup_alarms))

        when {
            wakeupAlarmDiagnostic == null -> {
                appendLine(context.getString(R.string.main_diag_status_not_run))
            }

            wakeupAlarmDiagnostic.error != null -> {
                appendLine(context.getString(R.string.main_diag_status_failed))
                appendLine(context.getString(R.string.main_diag_error, errorText(wakeupAlarmDiagnostic.error)))
            }

            wakeupAlarmDiagnostic.packageName == null -> {
                appendLine(context.getString(R.string.main_diag_no_data))
            }

            else -> {
                appendLine(context.getString(R.string.main_diag_source, sourceFor(wakeupAlarmDiagnostic.packageName)))
                appendLine(context.getString(R.string.main_diag_package, wakeupAlarmDiagnostic.packageName))
                appendLine(
                    context.getString(
                        R.string.main_diag_technical_tag,
                        wakeupAlarmDiagnostic.tag
                            ?: context.getString(R.string.main_not_available),
                    ),
                )
                appendLine(context.getString(R.string.main_diag_entry_wakeup_count, wakeupAlarmDiagnostic.wakeCount))
                appendLine(context.getString(R.string.main_diag_package_wakeups, wakeupAlarmDiagnostic.packageWakeups))
            }
        }

        appendLine()
        appendLine(context.getString(R.string.main_diag_background_jobs))

        when {
            backgroundJobDiagnostic == null -> {
                appendLine(context.getString(R.string.main_diag_status_not_run))
            }

            backgroundJobDiagnostic.error != null -> {
                appendLine(context.getString(R.string.main_diag_status_failed))
                appendLine(context.getString(R.string.main_diag_error, errorText(backgroundJobDiagnostic.error)))
            }

            backgroundJobDiagnostic.packageName == null -> {
                appendLine(context.getString(R.string.main_diag_no_data))
            }

            else -> {
                appendLine(context.getString(R.string.main_diag_source, sourceFor(backgroundJobDiagnostic.packageName)))
                appendLine(context.getString(R.string.main_diag_package, backgroundJobDiagnostic.packageName))

                if (
                    !backgroundJobDiagnostic.rawEntry.isNullOrBlank()
                ) {
                    appendLine(
                        context.getString(R.string.main_diag_technical_entry, backgroundJobDiagnostic.rawEntry.trim()),
                    )
                }
            }
        }

        appendLine()
        appendLine(context.getString(R.string.main_diag_wake_reason))

        when {
            wakeReasonDiagnostic == null -> {
                appendLine(context.getString(R.string.main_diag_status_not_run))
            }

            wakeReasonDiagnostic.error != null -> {
                appendLine(context.getString(R.string.main_diag_status_failed))
                appendLine(context.getString(R.string.main_diag_error, errorText(wakeReasonDiagnostic.error)))
            }

            wakeReasonDiagnostic.rawEntry == null -> {
                appendLine(context.getString(R.string.main_diag_no_data))
            }

            else -> {
                appendLine(
                    context.getString(
                        R.string.main_diag_time,
                        formatWakeLockTimestamp(context, wakeReasonDiagnostic.timestamp),
                    ),
                )
                appendLine(
                    context.getString(
                        R.string.main_diag_technical_reason,
                        wakeReasonDiagnostic.reason
                            ?: context.getString(R.string.main_unknown_lowercase),
                    ),
                )
                appendLine(
                    context.getString(
                        R.string.main_diag_details,
                        compactWakeReasonDetails(context, wakeReasonDiagnostic.details),
                    ),
                )
            }
        }

        appendLine()
        appendLine(context.getString(R.string.main_diag_network_since_boot))

        when {
            networkStatsDiagnostic == null -> {
                appendLine(context.getString(R.string.main_diag_status_not_run))
            }

            networkStatsDiagnostic.error != null -> {
                appendLine(context.getString(R.string.main_diag_status_failed))
                appendLine(context.getString(R.string.main_diag_error, errorText(networkStatsDiagnostic.error)))
            }

            networkStatsDiagnostic.entries.isEmpty() -> {
                appendLine(context.getString(R.string.main_diag_no_data))
            }

            else -> {
                appendLine(context.getString(R.string.main_diag_network_hint))

                networkStatsDiagnostic
                    .entries
                    .take(10)
                    .forEach { entry ->
                        val displayName = SourceLabelResolver.get(context).networkLabel(entry.packageName, entry.uid)

                        appendLine(
                            "• " +
                                context.getString(
                                    R.string.main_diag_network_total,
                                    displayName,
                                    formatNetworkBytes(entry.totalBytes),
                                ),
                        )

                        appendLine(
                            "  " +
                                context.getString(
                                    R.string.main_diag_network_received_sent,
                                    formatNetworkBytes(entry.rxBytes),
                                    formatNetworkBytes(entry.txBytes),
                                ),
                        )

                        appendLine(
                            "  " +
                                context.getString(
                                    R.string.main_diag_package_uid,
                                    entry.packageName
                                        ?: "UID ${entry.uid}",
                                ),
                        )
                    }
            }
        }

        appendLine()
        append("==================================================")
    }
}
