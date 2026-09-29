package de.sanniki.wakesleuth.ui.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.BackgroundJobDiagnostic
import de.sanniki.wakesleuth.NetworkStatsDiagnostic
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ShizukuState
import de.sanniki.wakesleuth.WakeLockDiagnostic
import de.sanniki.wakesleuth.WakeReasonDiagnostic
import de.sanniki.wakesleuth.WakeupAlarmDiagnostic
import de.sanniki.wakesleuth.domain.source
import de.sanniki.wakesleuth.ui.common.PreviewSurface
import de.sanniki.wakesleuth.ui.common.formatDuration
import de.sanniki.wakesleuth.ui.common.formatNetworkBytes
import de.sanniki.wakesleuth.ui.diagnostics.classifyWakeLockTag
import de.sanniki.wakesleuth.ui.diagnostics.compactAlarmTag
import de.sanniki.wakesleuth.ui.diagnostics.compactJobService
import de.sanniki.wakesleuth.ui.diagnostics.compactWakeLockTag
import de.sanniki.wakesleuth.ui.diagnostics.compactWakeReasonDetails
import de.sanniki.wakesleuth.ui.diagnostics.formatWakeLockTimestamp
import de.sanniki.wakesleuth.ui.diagnostics.readableWakeReason
import de.sanniki.wakesleuth.ui.diagnostics.resolveWakeLockSource
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver

@Composable
internal fun ShizukuWakeLockCard(
    state: ShizukuState,
    diagnostic: WakeLockDiagnostic?,
    loading: Boolean,
    alarmDiagnostic: WakeupAlarmDiagnostic?,
    alarmLoading: Boolean,
    jobDiagnostic: BackgroundJobDiagnostic?,
    jobLoading: Boolean,
    wakeReasonDiagnostic: WakeReasonDiagnostic?,
    wakeReasonLoading: Boolean,
    networkStatsDiagnostic: NetworkStatsDiagnostic?,
    networkStatsLoading: Boolean,
    onRequestPermission: () -> Unit,
    onCheck: () -> Unit,
    onAlarmCheck: () -> Unit,
    onJobCheck: () -> Unit,
    onWakeReasonCheck: () -> Unit,
    onNetworkStatsCheck: () -> Unit,
) {
    val context = LocalContext.current

    val diagnosticsExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val visibleDiagnosticPanel = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.main_shizuku_system_diagnostics),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (state) {
                    ShizukuState.RUNNING_GRANTED -> stringResource(R.string.main_shizuku_ready_diagnostics_available)
                    ShizukuState.RUNNING_DENIED -> stringResource(R.string.main_shizuku_running_permission_missing)
                    ShizukuState.NOT_RUNNING -> stringResource(R.string.main_shizuku_not_running)
                },
                color = when (state) {
                    ShizukuState.RUNNING_GRANTED -> Color(0xFF35A853)
                    ShizukuState.RUNNING_DENIED -> MaterialTheme.colorScheme.error
                    ShizukuState.NOT_RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(14.dp))

            when (state) {
                ShizukuState.RUNNING_GRANTED -> {
                    Button(
                        onClick = {
                            diagnosticsExpanded.value = !diagnosticsExpanded.value

                            if (!diagnosticsExpanded.value) {
                                visibleDiagnosticPanel.value = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            if (diagnosticsExpanded.value) {
                                stringResource(R.string.main_diagnostics_hide)
                            } else {
                                stringResource(R.string.main_diagnostics_open)
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (diagnosticsExpanded.value) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value == "wakelocks"
                                ) {
                                    visibleDiagnosticPanel.value = null
                                } else {
                                    visibleDiagnosticPanel.value = "wakelocks"
                                    onCheck()
                                }
                            },
                            enabled =
                                !loading && !alarmLoading && !jobLoading && !wakeReasonLoading && !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (visibleDiagnosticPanel.value == "wakelocks") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics
                                        .Color(0xFF687181)
                                },
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (visibleDiagnosticPanel.value == "wakelocks") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                contentColor = if (visibleDiagnosticPanel.value == "wakelocks") {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    androidx.compose.ui.graphics.Color.White
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                when {
                                    loading -> {
                                        stringResource(R.string.main_checking)
                                    }

                                    visibleDiagnosticPanel.value ==
                                        "wakelocks" -> {
                                        stringResource(R.string.main_wakelocks_hide)
                                    }

                                    else -> {
                                        stringResource(R.string.main_wakelocks_check)
                                    }
                                },
                            )
                        }

                        if (
                            diagnostic != null && visibleDiagnosticPanel.value == "wakelocks"
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            if (diagnostic.error != null) {
                                Text(
                                    text = diagnostic.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.main_diag_active_wakelocks, diagnostic.activeCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                if (
                                    diagnostic.rawLastEntry == null
                                ) {
                                    Text(
                                        text = stringResource(R.string.main_diag_no_data),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else {
                                    val source =
                                        resolveWakeLockSource(context = context, packageName = diagnostic.lastPackage)

                                    val kind = classifyWakeLockTag(context, diagnostic.lastTag)

                                    val timestamp = formatWakeLockTimestamp(context, diagnostic.lastTimestamp)

                                    Text(
                                        text = stringResource(R.string.main_last_partial_wakelock),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = stringResource(R.string.main_diag_source, source),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Text(
                                        text = stringResource(R.string.main_kind, kind),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Text(
                                        text = stringResource(R.string.main_diag_time, timestamp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Text(
                                        text = stringResource(
                                            R.string.main_diag_technical_tag,
                                            compactWakeLockTag(context, diagnostic.lastTag),
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        if (
                            diagnostic != null &&
                            diagnostic.error == null &&
                            diagnostic.historyEntries.isNotEmpty() && visibleDiagnosticPanel.value == "wakelocks"
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = stringResource(R.string.main_wakelock_history_recent),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val groupedHistoryEntries = diagnostic.historyEntries
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
                                }.take(5)

                            groupedHistoryEntries
                                .forEachIndexed { index, group ->
                                    val entry = group.first()

                                    val count = group.size

                                    val source =
                                        resolveWakeLockSource(context = context, packageName = entry.packageName)

                                    val hasFinishedEntry = group.any { !it.stillActive && !it.endUnknown }

                                    val longestDuration = group
                                        .mapNotNull {
                                            it.durationMillis
                                        }.maxOrNull()

                                    val durationText = when {
                                        hasFinishedEntry &&
                                            (longestDuration ?: 0L) < 1_000L -> {
                                            stringResource(R.string.main_diag_short_activity)
                                        }

                                        hasFinishedEntry -> {
                                            formatDuration(context, longestDuration)
                                        }

                                        else -> {
                                            stringResource(R.string.main_diag_end_not_in_window)
                                        }
                                    }

                                    val startText = stringResource(
                                        R.string.main_wakelock_start_duration,
                                        formatWakeLockTimestamp(context, entry.startTimestamp),
                                        durationText,
                                    )

                                    Text(
                                        text = source + " · " + entry.wakeLockType,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Text(
                                        text = if (count > 1) {
                                            stringResource(R.string.main_count_prefixed, count, startText)
                                        } else {
                                            startText
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )

                                    Text(
                                        text = if (
                                            group.any { it.causesWake }
                                        ) {
                                            stringResource(
                                                R.string.main_wakelock_display_wake_tag,
                                                compactWakeLockTag(context, entry.tag),
                                            )
                                        } else {
                                            stringResource(
                                                R.string.main_diag_tag,
                                                compactWakeLockTag(context, entry.tag),
                                            )
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )

                                    if (
                                        index <
                                        groupedHistoryEntries.lastIndex
                                    ) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value == "alarms"
                                ) {
                                    visibleDiagnosticPanel.value = null
                                } else {
                                    visibleDiagnosticPanel.value = "alarms"
                                    onAlarmCheck()
                                }
                            },
                            enabled =
                                !loading && !alarmLoading && !jobLoading && !wakeReasonLoading && !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (visibleDiagnosticPanel.value == "alarms") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics
                                        .Color(0xFF687181)
                                },
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (visibleDiagnosticPanel.value == "alarms") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                contentColor = if (visibleDiagnosticPanel.value == "alarms") {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    androidx.compose.ui.graphics.Color.White
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                when {
                                    alarmLoading -> {
                                        stringResource(R.string.main_wakeup_alarms_checking)
                                    }

                                    visibleDiagnosticPanel.value ==
                                        "alarms" -> {
                                        stringResource(R.string.main_wakeup_alarms_hide)
                                    }

                                    else -> {
                                        stringResource(R.string.main_wakeup_alarms_check)
                                    }
                                },
                            )
                        }

                        if (
                            alarmDiagnostic != null && visibleDiagnosticPanel.value == "alarms"
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            if (alarmDiagnostic.error != null) {
                                Text(
                                    text = alarmDiagnostic.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (
                                alarmDiagnostic.packageName == null
                            ) {
                                Text(
                                    text = stringResource(R.string.main_diag_no_data),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                val source =
                                    resolveWakeLockSource(context = context, packageName = alarmDiagnostic.packageName)

                                Text(
                                    text = stringResource(R.string.main_last_wakeup_alarm),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(R.string.main_diag_source, source),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_last_ago,
                                        formatDuration(context, alarmDiagnostic.ageMillis),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_wakeups_since_stats_start,
                                        alarmDiagnostic.wakeCount,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_diag_technical_tag,
                                        compactAlarmTag(context, alarmDiagnostic.tag),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value == "jobs"
                                ) {
                                    visibleDiagnosticPanel.value = null
                                } else {
                                    visibleDiagnosticPanel.value = "jobs"
                                    onJobCheck()
                                }
                            },
                            enabled =
                                !loading && !alarmLoading && !jobLoading && !wakeReasonLoading && !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (visibleDiagnosticPanel.value == "jobs") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics
                                        .Color(0xFF687181)
                                },
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (visibleDiagnosticPanel.value == "jobs") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                contentColor = if (visibleDiagnosticPanel.value == "jobs") {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    androidx.compose.ui.graphics.Color.White
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                when {
                                    jobLoading -> {
                                        stringResource(R.string.main_background_jobs_checking)
                                    }

                                    visibleDiagnosticPanel.value ==
                                        "jobs" -> {
                                        stringResource(R.string.main_background_jobs_hide)
                                    }

                                    else -> {
                                        stringResource(R.string.main_background_jobs_check)
                                    }
                                },
                            )
                        }

                        if (
                            jobDiagnostic != null && visibleDiagnosticPanel.value == "jobs"
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            if (jobDiagnostic.error != null) {
                                Text(
                                    text = jobDiagnostic.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (
                                jobDiagnostic.packageName == null
                            ) {
                                Text(
                                    text = stringResource(R.string.main_diag_no_data),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                val source =
                                    resolveWakeLockSource(context = context, packageName = jobDiagnostic.packageName)

                                Text(
                                    text = stringResource(R.string.main_last_started_background_job),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(R.string.main_diag_source, source),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_started_ago,
                                        formatDuration(context, jobDiagnostic.ageMillis),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_start_type,
                                        if (
                                            jobDiagnostic.prioritized
                                        ) {
                                            stringResource(R.string.main_start_type_prioritized)
                                        } else {
                                            stringResource(R.string.main_start_type_regular)
                                        },
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_service,
                                        compactJobService(context, jobDiagnostic.serviceName),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value == "wake_reason"
                                ) {
                                    visibleDiagnosticPanel.value = null
                                } else {
                                    visibleDiagnosticPanel.value = "wake_reason"
                                    onWakeReasonCheck()
                                }
                            },
                            enabled =
                                !loading && !alarmLoading && !jobLoading && !wakeReasonLoading && !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (visibleDiagnosticPanel.value == "wake_reason") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics
                                        .Color(0xFF687181)
                                },
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (visibleDiagnosticPanel.value == "wake_reason") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                contentColor = if (visibleDiagnosticPanel.value == "wake_reason") {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    androidx.compose.ui.graphics.Color.White
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                when {
                                    wakeReasonLoading -> {
                                        stringResource(R.string.main_wake_reason_checking)
                                    }

                                    visibleDiagnosticPanel.value ==
                                        "wake_reason" -> {
                                        stringResource(R.string.main_wake_reason_hide)
                                    }

                                    else -> {
                                        stringResource(R.string.main_wake_reason_check)
                                    }
                                },
                            )
                        }

                        if (
                            wakeReasonDiagnostic != null && visibleDiagnosticPanel.value == "wake_reason"
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            if (
                                wakeReasonDiagnostic.error != null
                            ) {
                                Text(
                                    text = wakeReasonDiagnostic.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (
                                wakeReasonDiagnostic.rawEntry == null
                            ) {
                                Text(
                                    text = stringResource(R.string.main_diag_no_data),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.main_last_direct_wake_reason),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(
                                        R.string.main_cause,
                                        readableWakeReason(context, wakeReasonDiagnostic),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_diag_time,
                                        formatWakeLockTimestamp(context, wakeReasonDiagnostic.timestamp),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_diag_technical_reason,
                                        wakeReasonDiagnostic.reason
                                            ?: stringResource(R.string.main_unknown_lowercase),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = stringResource(
                                        R.string.main_diag_details,
                                        compactWakeReasonDetails(context, wakeReasonDiagnostic.details),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (
                                    visibleDiagnosticPanel.value == "network"
                                ) {
                                    visibleDiagnosticPanel.value = null
                                } else {
                                    visibleDiagnosticPanel.value = "network"
                                    onNetworkStatsCheck()
                                }
                            },
                            enabled =
                                !loading && !alarmLoading && !jobLoading && !wakeReasonLoading && !networkStatsLoading,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (visibleDiagnosticPanel.value == "network") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics
                                        .Color(0xFF687181)
                                },
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (visibleDiagnosticPanel.value == "network") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                contentColor = if (visibleDiagnosticPanel.value == "network") {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    androidx.compose.ui.graphics.Color.White
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                when {
                                    networkStatsLoading -> {
                                        stringResource(R.string.main_network_checking)
                                    }

                                    visibleDiagnosticPanel.value ==
                                        "network" -> {
                                        stringResource(R.string.main_network_hide)
                                    }

                                    else -> {
                                        stringResource(R.string.main_network_check)
                                    }
                                },
                            )
                        }

                        if (
                            networkStatsDiagnostic != null && visibleDiagnosticPanel.value == "network"
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))

                            if (networkStatsDiagnostic.error != null) {
                                Text(
                                    text = networkStatsDiagnostic.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (
                                networkStatsDiagnostic.entries.isEmpty()
                            ) {
                                Text(
                                    text = stringResource(R.string.main_diag_no_data),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.main_diag_network_since_boot),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(R.string.main_diag_network_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                networkStatsDiagnostic.entries
                                    .take(5)
                                    .forEachIndexed { index, entry ->
                                        val source = entry.appLabel
                                            ?: if (
                                                entry.packageName != null
                                            ) {
                                                resolveWakeLockSource(
                                                    context = context,
                                                    packageName = entry.packageName,
                                                )
                                            } else {
                                                SourceLabelResolver.get(context).uidLabel(entry.uid)
                                            }

                                        Text(
                                            text = source + " · " + formatNetworkBytes(entry.totalBytes),
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall,
                                        )

                                        Text(
                                            text = stringResource(
                                                R.string.main_diag_network_received_sent,
                                                formatNetworkBytes(entry.rxBytes),
                                                formatNetworkBytes(entry.txBytes),
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                        )

                                        Text(
                                            text = (entry.packageName ?: "UID " + entry.uid),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )

                                        if (
                                            index <
                                            networkStatsDiagnostic.entries.take(5).lastIndex
                                        ) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                            }
                        }
                    }
                }

                ShizukuState.RUNNING_DENIED -> {
                    OutlinedButton(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.main_shizuku_allow))
                    }
                }

                ShizukuState.NOT_RUNNING -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.main_shizuku_start))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuWakeLockCardGrantedPreview() {
    PreviewSurface {
        ShizukuWakeLockCard(
            state = ShizukuState.RUNNING_GRANTED,
            diagnostic = null,
            loading = false,
            alarmDiagnostic = null,
            alarmLoading = false,
            jobDiagnostic = null,
            jobLoading = false,
            wakeReasonDiagnostic = null,
            wakeReasonLoading = false,
            networkStatsDiagnostic = null,
            networkStatsLoading = false,
            onRequestPermission = {},
            onCheck = {},
            onAlarmCheck = {},
            onJobCheck = {},
            onWakeReasonCheck = {},
            onNetworkStatsCheck = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuWakeLockCardDeniedPreview() {
    PreviewSurface {
        ShizukuWakeLockCard(
            state = ShizukuState.RUNNING_DENIED,
            diagnostic = null,
            loading = false,
            alarmDiagnostic = null,
            alarmLoading = false,
            jobDiagnostic = null,
            jobLoading = false,
            wakeReasonDiagnostic = null,
            wakeReasonLoading = false,
            networkStatsDiagnostic = null,
            networkStatsLoading = false,
            onRequestPermission = {},
            onCheck = {},
            onAlarmCheck = {},
            onJobCheck = {},
            onWakeReasonCheck = {},
            onNetworkStatsCheck = {},
        )
    }
}
