package de.sanniki.wakesleuth

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private data class SleepAnalysisWindow(
    val startMillis: Long,
    val endMillis: Long,
    val ongoing: Boolean,
    val title: String
) {
    val durationMillis: Long
        get() =
            (endMillis - startMillis)
                .coerceAtLeast(0L)
}

private data class QuietPhase(
    val startMillis: Long,
    val endMillis: Long
) {
    val durationMillis: Long
        get() =
            (endMillis - startMillis)
                .coerceAtLeast(0L)
}

private data class HourActivity(
    val hour: Int,
    val displayWakeups: Int,
    val cpuWakeups: Int
) {
    val total: Int
        get() =
            displayWakeups +
                cpuWakeups
}

private data class NetworkSessionApp(
    val name: String,
    val total: String,
    val received: String?,
    val sent: String?
)

private data class NetworkSessionSummary(
    val duration: String?,
    val activeApps: Int?,
    val total: String?,
    val received: String?,
    val sent: String?,
    val topApps: List<NetworkSessionApp>,
    val message: String?
)

private enum class SuspicionLevel(
    @StringRes val labelRes: Int
) {
    LIKELY_INVOLVED(
        R.string.sleep_level_likely_involved
    ),
    TEMPORALLY_NOTICEABLE(
        R.string.sleep_level_temporally_noticeable
    ),
    COMPANION_ACTIVITY(
        R.string.sleep_level_companion_activity
    )
}

private data class SuspicionCandidate(
    val name: String,
    val level: SuspicionLevel,
    val explanation: String,
    val cpuOccurrences: Int,
    val networkTraffic: String?
)

private data class SleepAnalysisData(
    val window: SleepAnalysisWindow,
    val score: Int,
    val rating: String,
    val summary: String,
    val displayWakeups: Int,
    val explainedDisplayWakeups: Int,
    val unexplainedDisplayWakeups: Int,
    val cpuWakeups: Int,
    val totalWakeups: Int,
    val wakeupsPerHour: Double,
    val longestQuietPhase: QuietPhase?,
    val secondLongestQuietPhase: QuietPhase?,
    val averageQuietMillis: Long,
    val averageWakeDistanceMillis: Long?,
    val busiestHour: HourActivity?,
    val hourlyActivity: List<HourActivity>,
    val networkSession: NetworkSessionSummary?,
    val suspicionCandidates:
        List<SuspicionCandidate>,
    val hints: List<String>
)

@Composable
fun SleepReportCard(
    events: List<WakeEvent>,
    monitoring: Boolean,
    detailLevel: DetailLevel
) {
    val context =
        LocalContext.current

    val analysis =
        remember(
            events,
            monitoring
        ) {
            buildSleepAnalysis(
                context = context,
                events = events,
                monitoring = monitoring
            )
        }

    val expandedDetailSection =
        androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<String?>(null)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            SleepAnalysisHeader(
                analysis = analysis
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            if (
                analysis.window.durationMillis <
                60_000L
            ) {
                SleepAnalysisPendingCard(
                    monitoring = monitoring,
                    hasRecordedEvents =
                        events.isNotEmpty()
                )
            } else {
                SleepScoreCard(
                    score = analysis.score,
                    rating = analysis.rating,
                    summary = analysis.summary,
                    provisional =
                        analysis.window.durationMillis <
                            15L * 60L * 1_000L
                )

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                SleepPrimaryValues(
                    analysis = analysis
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                analysis.networkSession
                    ?.let { networkSession ->
                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )

                        SleepDetailSectionButton(
                            title =
                                stringResource(
                                    R.string.sleep_section_network
                                ),
                            summary =
                                buildString {
                                    append(
                                        networkSession.total
                                            ?: stringResource(
                                                R.string.sleep_no_traffic_data
                                            )
                                    )

                                    networkSession.activeApps
                                        ?.let { count ->
                                            append(" · ")

                                            append(
                                                pluralStringResource(
                                                    R.plurals.sleep_app_count,
                                                    count,
                                                    count
                                                )
                                            )
                                        }
                                },
                            expanded =
                                expandedDetailSection.value ==
                                    "network",
                            onToggle = {
                                expandedDetailSection.value =
                                    if (
                                        expandedDetailSection.value ==
                                        "network"
                                    ) {
                                        null
                                    } else {
                                        "network"
                                    }
                            }
                        )

                        if (
                            expandedDetailSection.value ==
                            "network"
                        ) {
                            Spacer(
                                modifier =
                                    Modifier.height(8.dp)
                            )

                            NetworkSessionSection(
                                summary =
                                    networkSession,
                                detailLevel =
                                    detailLevel
                            )
                        }
                    }

                if (
                    analysis
                        .suspicionCandidates
                        .isNotEmpty()
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    SleepDetailSectionButton(
                        title =
                            stringResource(
                                R.string.sleep_section_activity_classification
                            ),
                        summary =
                            pluralStringResource(
                                R.plurals.sleep_active_sources_classified,
                                analysis.suspicionCandidates.size,
                                analysis.suspicionCandidates.size
                            ),
                        expanded =
                            expandedDetailSection.value ==
                                "activity",
                        onToggle = {
                            expandedDetailSection.value =
                                if (
                                    expandedDetailSection.value ==
                                    "activity"
                                ) {
                                    null
                                } else {
                                    "activity"
                                }
                        }
                    )

                    if (
                        expandedDetailSection.value ==
                        "activity"
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )

                        SuspicionRatingSection(
                            candidates =
                                analysis
                                    .suspicionCandidates,
                            detailLevel =
                                detailLevel
                        )
                    }
                }

                if (
                    detailLevel !=
                    DetailLevel.SIMPLE
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    SleepDetailSectionButton(
                        title =
                            stringResource(
                                R.string.sleep_section_quiet_phases_history
                            ),
                        summary =
                            analysis.longestQuietPhase
                                ?.let { phase ->
                                    stringResource(
                                        R.string.sleep_longest_rest_summary,
                                        formatDurationCompact(
                                            phase.durationMillis
                                        )
                                    )
                                }
                                ?: stringResource(
                                    R.string.sleep_not_determinable
                                ),
                        expanded =
                            expandedDetailSection.value ==
                                "quiet",
                        onToggle = {
                            expandedDetailSection.value =
                                if (
                                    expandedDetailSection.value ==
                                    "quiet"
                                ) {
                                    null
                                } else {
                                    "quiet"
                                }
                        }
                    )

                    if (
                        expandedDetailSection.value ==
                        "quiet"
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )

                        QuietPhaseSection(
                            analysis = analysis
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        HorizontalDivider()

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        HourlyActivityChart(
                            activity =
                                analysis.hourlyActivity
                        )
                    }
                }

                if (
                    detailLevel ==
                    DetailLevel.EXPERT
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    SleepDetailSectionButton(
                        title =
                            stringResource(
                                R.string.sleep_technical_metrics
                            ),
                        summary =
                            pluralStringResource(
                                R.plurals.sleep_total_activities,
                                analysis.totalWakeups,
                                analysis.totalWakeups
                            ),
                        expanded =
                            expandedDetailSection.value ==
                                "technical",
                        onToggle = {
                            expandedDetailSection.value =
                                if (
                                    expandedDetailSection.value ==
                                    "technical"
                                ) {
                                    null
                                } else {
                                    "technical"
                                }
                        }
                    )

                    if (
                        expandedDetailSection.value ==
                        "technical"
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )

                        ExpertMetricsSection(
                            analysis = analysis
                        )
                    }
                }

                if (
                    analysis.hints.isNotEmpty()
                ) {
                    val visibleHints =
                        when (detailLevel) {
                            DetailLevel.SIMPLE ->
                                analysis.hints.take(1)

                            DetailLevel.NORMAL ->
                                analysis.hints.take(3)

                            DetailLevel.EXPERT ->
                                analysis.hints
                        }

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    SleepDetailSectionButton(
                        title =
                            stringResource(
                                R.string.sleep_section_hints
                            ),
                        summary =
                            pluralStringResource(
                                R.plurals.sleep_hint_count,
                                visibleHints.size,
                                visibleHints.size
                            ),
                        expanded =
                            expandedDetailSection.value ==
                                "hints",
                        onToggle = {
                            expandedDetailSection.value =
                                if (
                                    expandedDetailSection.value ==
                                    "hints"
                                ) {
                                    null
                                } else {
                                    "hints"
                                }
                        }
                    )

                    if (
                        expandedDetailSection.value ==
                        "hints"
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )

                        SleepHintsSection(
                            hints = visibleHints
                        )
                    }
                }
            

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text =
                        stringResource(
                            R.string.sleep_score_disclaimer
                        ),
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }
    }
}

@Composable
private fun SleepDetailSectionButton(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (expanded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    androidx.compose.ui.graphics.Color(
                        0xFF687181
                    )
                }
            ),
        colors =
            androidx.compose.material3.ButtonDefaults
                .outlinedButtonColors(
                    containerColor =
                        if (expanded) {
                            MaterialTheme.colorScheme
                                .primary
                                .copy(alpha = 0.16f)
                        } else {
                            androidx.compose.ui.graphics
                                .Color.Transparent
                        },
                    contentColor =
                        MaterialTheme.colorScheme
                            .onSurface
                )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Spacer(
                    modifier =
                        Modifier.height(2.dp)
                )

                Text(
                    text = summary,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography
                            .labelSmall,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )
            }

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Text(
                text =
                    if (expanded) {
                        "−"
                    } else {
                        "+"
                    },
                color =
                    MaterialTheme.colorScheme
                        .primary,
                style =
                    MaterialTheme.typography
                        .titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SleepAnalysisPendingCard(
    monitoring: Boolean,
    hasRecordedEvents: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text =
                    when {
                        monitoring ->
                            stringResource(
                                R.string.sleep_pending_preparing
                            )

                        hasRecordedEvents ->
                            stringResource(
                                R.string.sleep_pending_too_short
                            )

                        else ->
                            stringResource(
                                R.string.sleep_no_analysis_yet
                            )
                    },
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text =
                    when {
                        monitoring ->
                            stringResource(
                                R.string.sleep_pending_preparing_hint
                            )

                        hasRecordedEvents ->
                            stringResource(
                                R.string.sleep_pending_too_short_hint
                            )

                        else ->
                            stringResource(
                                R.string.sleep_pending_start_hint
                            )
                    },
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )

            if (monitoring) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(
                            RoundedCornerShape(50)
                        )
                        .background(
                            MaterialTheme.colorScheme
                                .primary
                                .copy(alpha = 0.45f)
                        )
                )
            }
        }
    }
}

@Composable
private fun SleepAnalysisHeader(
    analysis: SleepAnalysisData
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text =
                    stringResource(
                        R.string.sleep_header_title
                    ),
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = analysis.window.title,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )

            Text(
                text =
                    formatSleepWindow(
                        analysis.window
                    ),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }

        Text(
            text =
                formatDurationCompact(
                    analysis.window
                        .durationMillis
                ),
            color =
                MaterialTheme.colorScheme.primary,
            style =
                MaterialTheme.typography
                    .titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SleepScoreCard(
    score: Int,
    rating: String,
    summary: String,
    provisional: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .primaryContainer
                    .copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            if (provisional) {
                                stringResource(
                                    R.string.sleep_provisional_rating,
                                    rating
                                )
                            } else {
                                rating
                            },
                        color =
                            MaterialTheme.colorScheme
                                .onPrimaryContainer,
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            if (provisional) {
                                stringResource(
                                    R.string.sleep_short_measurement_summary,
                                    summary
                                )
                            } else {
                                summary
                            },
                        color =
                            MaterialTheme.colorScheme
                                .onPrimaryContainer,
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }

                Spacer(
                    modifier = Modifier.width(14.dp)
                )

                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    Text(
                        text =
                            if (provisional) {
                                "–"
                            } else {
                                score.toString()
                            },
                        color =
                            MaterialTheme.colorScheme
                                .primary,
                        style =
                            MaterialTheme.typography
                                .headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            if (provisional) {
                                stringResource(
                                    R.string.sleep_provisional
                                )
                            } else {
                                stringResource(
                                    R.string.sleep_score_of_100
                                )
                            },
                        color =
                            MaterialTheme.colorScheme
                                .onPrimaryContainer,
                        style =
                            MaterialTheme.typography
                                .labelSmall
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(
                        RoundedCornerShape(50)
                    )
                    .background(
                        MaterialTheme.colorScheme
                            .surfaceVariant
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            if (provisional) {
                                0f
                            } else {
                                score
                                    .coerceIn(
                                        0,
                                        100
                                    ) /
                                    100f
                            }
                        )
                        .height(9.dp)
                        .clip(
                            RoundedCornerShape(50)
                        )
                        .background(
                            MaterialTheme.colorScheme
                                .primary
                        )
                )
            }
        }
    }
}

@Composable
private fun SleepPrimaryValues(
    analysis: SleepAnalysisData
) {
    val shortSession =
        analysis.window.durationMillis <
            15L * 60L * 1_000L

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            SleepMetricCard(
                value =
                    analysis.displayWakeups
                        .toString(),
                label =
                    stringResource(
                        R.string.sleep_metric_screen_on
                    ),
                modifier = Modifier.weight(1f)
            )

            SleepMetricCard(
                value =
                    analysis.cpuWakeups
                        .toString(),
                label =
                    stringResource(
                        R.string.sleep_metric_cpu_wakes
                    ),
                modifier = Modifier.weight(1f)
            )

            SleepMetricCard(
                value =
                    if (shortSession) {
                        analysis.totalWakeups
                            .toString()
                    } else {
                        formatDecimal(
                            analysis.wakeupsPerHour
                        )
                    },
                label =
                    if (shortSession) {
                        stringResource(
                            R.string.sleep_metric_in_duration,
                            formatDurationPrecise(
                                analysis.window
                                    .durationMillis
                            )
                        )
                    } else {
                        stringResource(
                            R.string.sleep_metric_per_hour
                        )
                    },
                modifier = Modifier.weight(1f)
            )
        }

        if (shortSession) {
            Spacer(
                modifier = Modifier.height(7.dp)
            )

            Text(
                text =
                    stringResource(
                        R.string.sleep_hourly_value_note
                    ),
                modifier = Modifier.fillMaxWidth(),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .labelSmall,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun SleepMetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 6.dp,
                    vertical = 12.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color =
                    MaterialTheme.colorScheme.primary,
                style =
                    MaterialTheme.typography
                        .titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = label,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NetworkSessionSection(
    summary: NetworkSessionSummary,
    detailLevel: DetailLevel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.48f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text =
                    stringResource(
                        R.string.sleep_network_during_session
                    ),
                style =
                    MaterialTheme.typography
                        .titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            summary.total
                ?.let { total ->
                    SleepValueRow(
                        label =
                            stringResource(
                                R.string.sleep_data_traffic
                            ),
                        value = total
                    )
                }

            summary.activeApps
                ?.let { activeApps ->
                    SleepValueRow(
                        label =
                            stringResource(
                                R.string.sleep_active_apps
                            ),
                        value =
                            activeApps.toString()
                    )
                }

            if (
                detailLevel !=
                DetailLevel.SIMPLE
            ) {
                summary.received
                    ?.let { received ->
                        SleepValueRow(
                            label =
                                stringResource(
                                    R.string.sleep_received
                                ),
                            value = received
                        )
                    }

                summary.sent
                    ?.let { sent ->
                        SleepValueRow(
                            label =
                                stringResource(
                                    R.string.sleep_sent
                                ),
                            value = sent
                        )
                    }

                summary.duration
                    ?.let { duration ->
                        SleepValueRow(
                            label =
                                stringResource(
                                    R.string.sleep_measurement_duration
                                ),
                            value = duration
                        )
                    }
            }

            val visibleApps =
                when (detailLevel) {
                    DetailLevel.SIMPLE ->
                        summary.topApps.take(1)

                    DetailLevel.NORMAL ->
                        summary.topApps.take(3)

                    DetailLevel.EXPERT ->
                        summary.topApps.take(5)
                }

            if (visibleApps.isNotEmpty()) {
                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text =
                        pluralStringResource(
                            R.plurals.sleep_most_active_apps,
                            visibleApps.size
                        ),
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography
                            .labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                visibleApps.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            text =
                                sourceDisplayName(
                                    LocalContext.current,
                                    app.name
                                ),
                            modifier =
                                Modifier.weight(1f),
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis
                        )

                        Spacer(
                            modifier =
                                Modifier.width(10.dp)
                        )

                        Text(
                            text = app.total,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    if (
                        detailLevel ==
                        DetailLevel.EXPERT &&
                        (
                            app.received != null ||
                                app.sent != null
                        )
                    ) {
                        Text(
                            text = buildString {
                                app.received
                                    ?.let {
                                        append(
                                            stringResource(
                                                R.string.sleep_received_value,
                                                it
                                            )
                                        )
                                    }

                                if (
                                    app.received != null &&
                                    app.sent != null
                                ) {
                                    append(" · ")
                                }

                                app.sent
                                    ?.let {
                                        append(
                                            stringResource(
                                                R.string.sleep_sent_value,
                                                it
                                            )
                                        )
                                    }
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            summary.message
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let { message ->
                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text = message,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            Text(
                text =
                    stringResource(
                        R.string.sleep_network_traffic_disclaimer
                    ),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .labelSmall
            )
        }
    }
}

@Composable
private fun SuspicionRatingSection(
    candidates: List<SuspicionCandidate>,
    detailLevel: DetailLevel
) {
    val showAllSources =
        androidx.compose.runtime.remember(
            candidates
        ) {
            androidx.compose.runtime
                .mutableStateOf(false)
        }

    val compactLimit = 3

    val visibleCandidates =
        if (showAllSources.value) {
            candidates
        } else {
            candidates.take(compactLimit)
        }

    val hiddenCount =
        (candidates.size -
            visibleCandidates.size)
            .coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.48f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text =
                    stringResource(
                        R.string.sleep_section_activity_classification
                    ),
                style =
                    MaterialTheme.typography
                        .titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text =
                    stringResource(
                        R.string.sleep_activity_classification_intro
                    ),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            visibleCandidates
                .forEachIndexed {
                        index,
                        candidate ->

                    Text(
                        text = candidate.name,
                        modifier =
                            Modifier.fillMaxWidth(),
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(2.dp)
                    )

                    Text(
                        text =
                            stringResource(
                                candidate.level.labelRes
                            ),
                        modifier =
                            Modifier.fillMaxWidth(),
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            candidate.explanation,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                    if (
                        detailLevel ==
                        DetailLevel.EXPERT
                    ) {
                        val technicalText =
                            buildString {
                                if (
                                    candidate
                                        .cpuOccurrences >
                                    0
                                ) {
                                    append(
                                        stringResource(
                                            R.string.sleep_cpu_attributions,
                                            candidate
                                                .cpuOccurrences
                                        )
                                    )
                                }

                                candidate
                                    .networkTraffic
                                    ?.let { traffic ->
                                        if (isNotEmpty()) {
                                            append(" · ")
                                        }

                                        append(
                                            stringResource(
                                                R.string.sleep_network_value,
                                                traffic
                                            )
                                        )
                                    }
                            }

                        if (
                            technicalText
                                .isNotBlank()
                        ) {
                            Spacer(
                                modifier =
                                    Modifier.height(2.dp)
                            )

                            Text(
                                text =
                                    technicalText,
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall
                            )
                        }
                    }

                    if (
                        index <
                        visibleCandidates.lastIndex
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(9.dp)
                        )

                        HorizontalDivider()

                        Spacer(
                            modifier =
                                Modifier.height(9.dp)
                        )
                    }
                }

            if (candidates.size > compactLimit) {
                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                androidx.compose.material3
                    .OutlinedButton(
                        onClick = {
                            showAllSources.value =
                                !showAllSources.value
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text =
                                if (showAllSources.value) {
                                    stringResource(
                                        R.string.sleep_show_less
                                    )
                                } else {
                                    pluralStringResource(
                                        R.plurals.sleep_show_more,
                                        hiddenCount,
                                        hiddenCount
                                    )
                                }
                        )
                    }
            }

            Spacer(
                modifier = Modifier.height(9.dp)
            )

            Text(
                text =
                    stringResource(
                        R.string.sleep_activity_classification_disclaimer
                    ),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .labelSmall
            )
        }
    }
}

@Composable
private fun QuietPhaseSection(
    analysis: SleepAnalysisData
) {
    Text(
        text =
            stringResource(
                R.string.sleep_quiet_phases
            ),
        style =
            MaterialTheme.typography
                .titleSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(
        modifier = Modifier.height(10.dp)
    )

    SleepValueRow(
        label =
            stringResource(
                R.string.sleep_longest_quiet_phase
            ),
        value =
            analysis.longestQuietPhase
                ?.let {
                    formatDurationCompact(
                        it.durationMillis
                    )
                }
                ?: stringResource(
                    R.string.sleep_not_determinable
                )
    )

    analysis.secondLongestQuietPhase
        ?.let { phase ->
            SleepValueRow(
                label =
                    stringResource(
                        R.string.sleep_second_longest_quiet_phase
                    ),
                value =
                    formatDurationCompact(
                        phase.durationMillis
                    )
            )
        }

    SleepValueRow(
        label =
            stringResource(
                R.string.sleep_average_quiet_phase
            ),
        value =
            if (
                analysis.averageQuietMillis >
                0L
            ) {
                formatDurationCompact(
                    analysis.averageQuietMillis
                )
            } else {
                stringResource(
                    R.string.sleep_not_determinable
                )
            }
    )

    analysis.longestQuietPhase
        ?.let { phase ->
            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text =
                    stringResource(
                        R.string.sleep_longest_phase_range,
                        formatTime(
                            phase.startMillis
                        ),
                        formatTime(
                            phase.endMillis
                        )
                    ),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }
}

@Composable
private fun HourlyActivityChart(
    activity: List<HourActivity>
) {
    Text(
        text =
            stringResource(
                R.string.sleep_activity_history
            ),
        style =
            MaterialTheme.typography
                .titleSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(
        modifier = Modifier.height(4.dp)
    )

    Text(
        text =
            stringResource(
                R.string.sleep_activity_history_hint
            ),
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
        style =
            MaterialTheme.typography
                .bodySmall
    )

    Spacer(
        modifier = Modifier.height(12.dp)
    )

    if (activity.isEmpty()) {
        Text(
            text =
                stringResource(
                    R.string.sleep_activity_history_empty
                ),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .bodySmall
        )

        return
    }

    val visible =
        activity.takeLast(12)

    val highest =
        max(
            1,
            visible.maxOfOrNull {
                it.total
            } ?: 1
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 94.dp),
        horizontalArrangement =
            Arrangement.spacedBy(5.dp),
        verticalAlignment =
            Alignment.Bottom
    ) {
        visible.forEach { item ->
            val height =
                if (item.total == 0) {
                    5.dp
                } else {
                    (
                        14 +
                            54 *
                            (
                                item.total.toFloat() /
                                    highest.toFloat()
                            )
                    ).dp
                }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.Bottom
            ) {
                Text(
                    text =
                        if (item.total > 0) {
                            item.total.toString()
                        } else {
                            ""
                        },
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography
                            .labelSmall,
                    maxLines = 1
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(
                            RoundedCornerShape(
                                topStart = 5.dp,
                                topEnd = 5.dp
                            )
                        )
                        .background(
                            if (
                                item.total > 0
                            ) {
                                MaterialTheme
                                    .colorScheme
                                    .primary
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                            }
                        )
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        item.hour
                            .toString()
                            .padStart(
                                2,
                                '0'
                            ),
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography
                            .labelSmall
                )
            }
        }
    }
}

@Composable
private fun ExpertMetricsSection(
    analysis: SleepAnalysisData
) {
    Text(
        text =
            stringResource(
                R.string.sleep_technical_metrics
            ),
        style =
            MaterialTheme.typography
                .titleSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(
        modifier = Modifier.height(8.dp)
    )

    SleepValueRow(
        label =
            stringResource(
                R.string.sleep_attributed_display_wakeups
            ),
        value =
            analysis
                .explainedDisplayWakeups
                .toString()
    )

    SleepValueRow(
        label =
            stringResource(
                R.string.sleep_unexplained_display_wakeups
            ),
        value =
            analysis
                .unexplainedDisplayWakeups
                .toString()
    )

    SleepValueRow(
        label =
            stringResource(
                R.string.sleep_total_activities_label
            ),
        value =
            analysis.totalWakeups
                .toString()
    )

    SleepValueRow(
        label =
            stringResource(
                R.string.sleep_average_activity_distance
            ),
        value =
            analysis.averageWakeDistanceMillis
                ?.let {
                    formatDurationCompact(it)
                }
                ?: stringResource(
                    R.string.sleep_not_determinable
                )
    )

    if (
        analysis.window.durationMillis <
        15L * 60L * 1_000L
    ) {
        SleepValueRow(
            label =
                stringResource(
                    R.string.sleep_short_measurement_period
                ),
            value =
                pluralStringResource(
                    R.plurals.sleep_events_in_duration,
                    analysis.totalWakeups,
                    analysis.totalWakeups,
                    formatDurationPrecise(
                        analysis.window
                            .durationMillis
                    )
                )
        )
    } else {
        analysis.busiestHour
            ?.let { hour ->
                SleepValueRow(
                    label =
                        stringResource(
                            R.string.sleep_busiest_hour
                        ),
                    value =
                        pluralStringResource(
                            R.plurals.sleep_busiest_hour_value,
                            hour.total,
                            hour.hour
                                .toString()
                                .padStart(
                                    2,
                                    '0'
                                ),
                            (
                                (hour.hour + 1) % 24
                            )
                                .toString()
                                .padStart(
                                    2,
                                    '0'
                                ),
                            hour.total
                        )
                )
            }
    }
}

@Composable
private fun SleepHintsSection(
    hints: List<String>
) {
    Text(
        text =
            stringResource(
                R.string.sleep_classification
            ),
        style =
            MaterialTheme.typography
                .titleSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(
        modifier = Modifier.height(8.dp)
    )

    hints.forEach { hint ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment =
                Alignment.Top
        ) {
            Text(
                text = "•",
                color =
                    MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Text(
                text = hint,
                modifier = Modifier.weight(1f),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }
    }
}

@Composable
private fun SleepValueRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme.typography
                    .bodyMedium
        )

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        Text(
            text = value,
            color =
                MaterialTheme.colorScheme.primary,
            style =
                MaterialTheme.typography
                    .bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

private fun buildSleepAnalysis(
    context: Context,
    events: List<WakeEvent>,
    monitoring: Boolean
): SleepAnalysisData {
    val window =
        calculateSleepAnalysisWindow(
            context = context,
            events = events,
            monitoring = monitoring
        )

    val relevantEvents =
        events
            .asSequence()
            .filter { event ->
                event.timestamp >=
                    window.startMillis &&
                    event.timestamp <=
                        window.endMillis
            }
            .sortedBy {
                it.timestamp
            }
            .toList()

    val displayEvents =
        relevantEvents.filter {
            it.type == "SCREEN_ON"
        }

    val cpuEvents =
        relevantEvents.filter {
            it.type == "CPU_WAKEUP"
        }

    val wakeEvents =
        relevantEvents.filter {
            it.type == "SCREEN_ON" ||
                it.type == "CPU_WAKEUP"
        }

    val networkSession =
        relevantEvents
            .lastOrNull {
                it.type ==
                    "NETWORK_SESSION"
            }
            ?.let {
                parseNetworkSessionSummary(
                    context,
                    it.details
                )
            }

    val suspicionCandidates =
        buildSuspicionCandidates(
            context = context,
            relevantEvents =
                relevantEvents,
            networkSession =
                networkSession
        )

    val explainedDisplay =
        displayEvents.count {
            hasTechnicalExplanation(
                context,
                it
            )
        }

    val unexplainedDisplay =
        displayEvents.count {
            isTechnicallyUnexplained(
                context,
                it
            )
        }

    val quietPhases =
        buildQuietPhases(
            window = window,
            wakeEvents = wakeEvents
        )

    val sortedQuietPhases =
        quietPhases.sortedByDescending {
            it.durationMillis
        }

    val averageQuietMillis =
        if (quietPhases.isNotEmpty()) {
            quietPhases
                .map {
                    it.durationMillis
                }
                .average()
                .roundToInt()
                .toLong()
        } else {
            0L
        }

    val averageWakeDistance =
        wakeEvents
            .zipWithNext()
            .map {
                (first, second) ->

                second.timestamp -
                    first.timestamp
            }
            .takeIf {
                it.isNotEmpty()
            }
            ?.average()
            ?.roundToInt()
            ?.toLong()

    val hourlyActivity =
        buildHourlyActivity(
            window = window,
            displayEvents = displayEvents,
            cpuEvents = cpuEvents
        )

    val busiestHour =
        hourlyActivity
            .filter {
                it.total > 0
            }
            .maxWithOrNull(
                compareBy<HourActivity> {
                    it.total
                }.thenByDescending {
                    it.hour
                }
            )

    val durationHours =
        window.durationMillis
            .toDouble()
            .div(
                3_600_000.0
            )

    val totalWakeups =
        displayEvents.size +
            cpuEvents.size

    val wakeupsPerHour =
        if (durationHours > 0.0) {
            totalWakeups /
                durationHours
        } else {
            0.0
        }

    val score =
        calculateTechnicalSleepScore(
            window = window,
            displayWakeups =
                displayEvents.size,
            cpuWakeups =
                cpuEvents.size,
            unexplainedDisplayWakeups =
                unexplainedDisplay,
            longestQuietMillis =
                sortedQuietPhases
                    .firstOrNull()
                    ?.durationMillis
                    ?: 0L,
            wakeupsPerHour =
                wakeupsPerHour
        )

    val rating =
        sleepScoreRating(
            context,
            score
        )

    val summary =
        sleepScoreSummary(
            context = context,
            score = score,
            totalWakeups = totalWakeups,
            displayWakeups =
                displayEvents.size
        )

    val hints =
        buildSleepHints(
            context = context,
            window = window,
            displayWakeups =
                displayEvents.size,
            cpuWakeups =
                cpuEvents.size,
            unexplainedDisplayWakeups =
                unexplainedDisplay,
            longestQuietMillis =
                sortedQuietPhases
                    .firstOrNull()
                    ?.durationMillis
                    ?: 0L,
            wakeupsPerHour =
                wakeupsPerHour,
            busiestHour =
                busiestHour
        )

    return SleepAnalysisData(
        window = window,
        score = score,
        rating = rating,
        summary = summary,
        displayWakeups =
            displayEvents.size,
        explainedDisplayWakeups =
            explainedDisplay,
        unexplainedDisplayWakeups =
            unexplainedDisplay,
        cpuWakeups =
            cpuEvents.size,
        totalWakeups =
            totalWakeups,
        wakeupsPerHour =
            wakeupsPerHour,
        longestQuietPhase =
            sortedQuietPhases
                .getOrNull(0),
        secondLongestQuietPhase =
            sortedQuietPhases
                .getOrNull(1),
        averageQuietMillis =
            averageQuietMillis,
        averageWakeDistanceMillis =
            averageWakeDistance,
        busiestHour =
            busiestHour,
        hourlyActivity =
            hourlyActivity,
        networkSession =
            networkSession,
        suspicionCandidates =
            suspicionCandidates,
        hints = hints
    )
}

private fun buildSuspicionCandidates(
    context: Context,
    relevantEvents: List<WakeEvent>,
    networkSession: NetworkSessionSummary?
): List<SuspicionCandidate> {
    val cpuSources =
        relevantEvents
            .filter {
                it.type == "CPU_WAKEUP"
            }
            .mapNotNull { event ->
                extractCpuPossibleSource(
                    context,
                    event
                )
            }
            .map {
                normalizeSuspicionSource(
                    context,
                    it
                )
            }
            .filter { source ->
                source.isNotBlank() &&
                    LocalizedText
                        .variants(
                            context,
                            R.string.bg_possible_source_ambiguous
                        )
                        .none {
                            source.equals(
                                it,
                                ignoreCase = true
                            )
                        }
            }

    val cpuCounts =
        cpuSources
            .groupingBy { it }
            .eachCount()

    val networkApps =
        networkSession
            ?.topApps
            .orEmpty()

    val result =
        mutableListOf<SuspicionCandidate>()

    cpuCounts.forEach {
            (cpuSource, cpuCount) ->

        val matchingNetwork =
            networkApps.firstOrNull {
                sourcesLikelyMatch(
                    context,
                    cpuSource,
                    it.name
                )
            }

        val level =
            when {
                cpuCount >= 2 ->
                    SuspicionLevel
                        .LIKELY_INVOLVED

                matchingNetwork != null ->
                    SuspicionLevel
                        .LIKELY_INVOLVED

                else ->
                    SuspicionLevel
                        .TEMPORALLY_NOTICEABLE
            }

        val explanation =
            when {
                cpuCount >= 2 &&
                    matchingNetwork != null ->
                    context.getString(
                        R.string.sleep_explanation_cpu_repeated_network
                    )

                cpuCount >= 2 ->
                    context.getString(
                        R.string.sleep_explanation_cpu_repeated
                    )

                matchingNetwork != null ->
                    context.getString(
                        R.string.sleep_explanation_cpu_network
                    )

                else ->
                    context.getString(
                        R.string.sleep_explanation_cpu_once
                    )
            }

        result.add(
            SuspicionCandidate(
                name = cpuSource,
                level = level,
                explanation = explanation,
                cpuOccurrences =
                    cpuCount,
                networkTraffic =
                    matchingNetwork?.total
            )
        )
    }

    networkApps.forEach { app ->
        val alreadyIncluded =
            result.any { candidate ->
                sourcesLikelyMatch(
                    context,
                    candidate.name,
                    app.name
                )
            }

        if (!alreadyIncluded) {
            result.add(
                SuspicionCandidate(
                    name =
                        sourceDisplayName(
                            context,
                            app.name
                        ),
                    level =
                        SuspicionLevel
                            .COMPANION_ACTIVITY,
                    explanation =
                        context.getString(
                            R.string.sleep_explanation_network_only
                        ),
                    cpuOccurrences = 0,
                    networkTraffic =
                        app.total
                )
            )
        }
    }

    return result
        .distinctBy {
            normalizeComparisonSource(
                context,
                it.name
            )
        }
        .sortedWith(
            compareBy<SuspicionCandidate> {
                when (it.level) {
                    SuspicionLevel
                        .LIKELY_INVOLVED ->
                        0

                    SuspicionLevel
                        .TEMPORALLY_NOTICEABLE ->
                        1

                    SuspicionLevel
                        .COMPANION_ACTIVITY ->
                        2
                }
            }.thenByDescending {
                it.cpuOccurrences
            }
        )
}

private fun extractCpuPossibleSource(
    context: Context,
    event: WakeEvent
): String? {
    return event.details
        .lineSequence()
        .map {
            it.trim()
        }
        .firstOrNull {
            LocalizedText.startsWithAny(
                it,
                context,
                R.string.sleep_label_possible_source
            )
        }
        ?.substringAfter(":")
        ?.trim()
        ?.ifBlank { null }
}

private fun normalizeSuspicionSource(
    context: Context,
    source: String
): String {
    val cleaned =
        LocalizedText
            .removeAnyPrefix(
                source
                    .trim()
                    .trimStart('*')
                    .trimEnd('*'),
                context,
                R.string.sleep_label_cpu_wakeup_prefix
            )
            .trimStart()
            .removePrefix(
                "Wakeup-Alarm: "
            )
            .removePrefix(
                "Partial Wakelock: "
            )
            .trim()

    val lower =
        cleaned.lowercase(
            Locale.getDefault()
        )

    val uidMatch =
        Regex(
            """^uid\s+(\d+)$""",
            RegexOption.IGNORE_CASE
        ).matchEntire(cleaned)

    return when {
        uidMatch != null ->
            context.getString(
                R.string.sleep_system_service_uid,
                uidMatch.groupValues[1]
            )

        lower.contains(
            "com.google.android.gms"
        ) ||
            lower.contains(
                "com.google.android.location"
            ) ||
            lower.contains(
                "activity_detection"
            ) ||
            lower.contains(
                "callbackrunner"
            ) ||
            lower.contains(
                "gms"
            ) ->
            context.getString(
                R.string.sleep_google_services
            )

        lower.contains("whatsapp") ->
            "WhatsApp"

        lower.contains("oplus") ||
            lower.contains("oneplus") ||
            lower.contains("athena") ->
            context.getString(
                R.string.sleep_oneplus_system
            )

        lower.contains("samsung browser") ||
            lower.contains(
                "com.sec.android.app.sbrowser"
            ) ->
            "Samsung Browser"

        lower.contains("revanced") ||
            lower.contains(
                "anddea.yrf.tube"
            ) ->
            "ReVanced Advanced"

        else ->
            cleaned
                .substringBefore(
                    "/androidx.work.impl"
                )
                .substringBefore(":android")
                .substringBefore(" (")
                .take(80)
                .trim()
    }
}

private fun sourcesLikelyMatch(
    context: Context,
    first: String,
    second: String
): Boolean {
    val a =
        normalizeComparisonSource(
            context,
            first
        )

    val b =
        normalizeComparisonSource(
            context,
            second
        )

    if (
        a.isBlank() ||
        b.isBlank()
    ) {
        return false
    }

    if (a == b) {
        return true
    }

    return (
        a.length >= 5 &&
        b.contains(a)
    ) || (
        b.length >= 5 &&
        a.contains(b)
    )
}

private fun normalizeComparisonSource(
    context: Context,
    value: String
): String {
    return normalizeSuspicionSource(
        context,
        value
    )
        .lowercase(
            Locale.getDefault()
        )
        .replace(
            Regex("""[^a-z0-9äöüß]+"""),
            ""
        )
}

private fun parseNetworkSessionSummary(
    context: Context,
    details: String
): NetworkSessionSummary {
    val lines =
        details.lines()
            .map {
                it.trim()
            }

    fun valueAfter(
        @StringRes prefixRes: Int
    ): String? {
        return lines
            .firstOrNull {
                LocalizedText.startsWithAny(
                    it,
                    context,
                    prefixRes
                )
            }
            ?.let {
                LocalizedText.removeAnyPrefix(
                    it,
                    context,
                    prefixRes
                )
            }
            ?.trim()
            ?.ifBlank { null }
    }

    val trafficLine =
        valueAfter(
            R.string.sleep_label_total
        )

    val total =
        trafficLine
            ?.substringBefore("·")
            ?.trim()
            ?.ifBlank { null }

    val received =
        trafficLine
            ?.let {
                LocalizedText.substringAfterAny(
                    it,
                    context,
                    R.string.sleep_label_received,
                    missing = ""
                )
            }
            ?.substringBefore("·")
            ?.trim()
            ?.ifBlank { null }

    val sent =
        trafficLine
            ?.let {
                LocalizedText.substringAfterAny(
                    it,
                    context,
                    R.string.sleep_label_sent,
                    missing = ""
                )
            }
            ?.trim()
            ?.ifBlank { null }

    val activeApps =
        valueAfter(
            R.string.sleep_label_apps_with_traffic
        )?.toIntOrNull()

    val topApps =
        buildList {
            lines.forEachIndexed {
                    index,
                    line ->

                if (!line.startsWith("• ")) {
                    return@forEachIndexed
                }

                val headline =
                    line.removePrefix("• ")
                        .trim()

                val separator =
                    headline.lastIndexOf(" · ")

                if (separator <= 0) {
                    return@forEachIndexed
                }

                val name =
                    headline
                        .substring(
                            0,
                            separator
                        )
                        .trim()

                val appTotal =
                    headline
                        .substring(
                            separator + 3
                        )
                        .trim()

                val transferLine =
                    lines.getOrNull(
                        index + 1
                    ).orEmpty()

                val appReceived =
                    transferLine
                        .takeIf {
                            LocalizedText.startsWithAny(
                                it,
                                context,
                                R.string.sleep_label_received
                            )
                        }
                        ?.let {
                            LocalizedText.substringAfterAny(
                                it,
                                context,
                                R.string.sleep_label_received
                            )
                        }
                        ?.substringBefore("·")
                        ?.trim()
                        ?.ifBlank { null }

                val appSent =
                    transferLine
                        .takeIf {
                            LocalizedText.containsAny(
                                it,
                                context,
                                R.string.sleep_label_sent
                            )
                        }
                        ?.let {
                            LocalizedText.substringAfterAny(
                                it,
                                context,
                                R.string.sleep_label_sent
                            )
                        }
                        ?.trim()
                        ?.ifBlank { null }

                if (
                    name.isNotBlank() &&
                    appTotal.isNotBlank()
                ) {
                    add(
                        NetworkSessionApp(
                            name = name,
                            total = appTotal,
                            received =
                                appReceived,
                            sent = appSent
                        )
                    )
                }
            }
        }

    val message =
        when {
            LocalizedText.containsAny(
                details,
                context,
                R.string.sleep_marker_no_baseline,
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.sleep_network_no_baseline_message
                )

            LocalizedText.containsAny(
                details,
                context,
                R.string.sleep_marker_end_measurement_failed,
                ignoreCase = true
            ) ->
                lines.firstOrNull {
                    LocalizedText.containsAny(
                        it,
                        context,
                        R.string.sleep_marker_end_measurement_failed,
                        ignoreCase = true
                    )
                }

            LocalizedText.containsAny(
                details,
                context,
                R.string.sleep_marker_no_app_traffic,
                ignoreCase = true
            ) ->
                context.getString(
                    R.string.sleep_network_no_app_traffic_message
                )

            else ->
                null
        }

    return NetworkSessionSummary(
        duration =
            valueAfter(
                R.string.sleep_label_measurement_duration
            ),
        activeApps =
            activeApps,
        total = total,
        received = received,
        sent = sent,
        topApps = topApps,
        message = message
    )
}

private fun calculateSleepAnalysisWindow(
    context: Context,
    events: List<WakeEvent>,
    monitoring: Boolean
): SleepAnalysisWindow {
    val now =
        System.currentTimeMillis()

    val sorted =
        events.sortedBy {
            it.timestamp
        }

    val latestStart =
        sorted.lastOrNull {
            it.type == "MONITOR_START"
        }

    if (latestStart != null) {
        if (monitoring) {
            return SleepAnalysisWindow(
                startMillis =
                    latestStart.timestamp,
                endMillis = now,
                ongoing = true,
                title =
                    context.getString(
                        R.string.sleep_window_running_analysis
                    )
            )
        }

        val stopAfterStart =
            sorted.firstOrNull { event ->
                event.type ==
                    "MONITOR_STOP" &&
                    event.timestamp >=
                        latestStart.timestamp
            }

        val finalSessionEvent =
            sorted.lastOrNull { event ->
                event.timestamp >=
                    latestStart.timestamp
            }

        return SleepAnalysisWindow(
            startMillis =
                latestStart.timestamp,
            endMillis =
                stopAfterStart?.timestamp
                    ?: finalSessionEvent
                        ?.timestamp
                    ?: latestStart.timestamp,
            ongoing = false,
            title =
                context.getString(
                    R.string.sleep_window_last_analysis
                )
        )
    }

    if (sorted.isNotEmpty()) {
        return SleepAnalysisWindow(
            startMillis =
                sorted.first().timestamp,
            endMillis =
                sorted.last().timestamp,
            ongoing = false,
            title =
                context.getString(
                    R.string.sleep_window_recorded_period
                )
        )
    }

    return SleepAnalysisWindow(
        startMillis = now,
        endMillis = now,
        ongoing = false,
        title =
            context.getString(
                R.string.sleep_no_analysis_yet
            )
    )
}

private fun buildQuietPhases(
    window: SleepAnalysisWindow,
    wakeEvents: List<WakeEvent>
): List<QuietPhase> {
    if (
        window.endMillis <=
        window.startMillis
    ) {
        return emptyList()
    }

    val timestamps =
        wakeEvents
            .map {
                it.timestamp
            }
            .filter {
                it in
                    window.startMillis..
                        window.endMillis
            }
            .distinct()
            .sorted()

    val boundaries =
        buildList {
            add(window.startMillis)
            addAll(timestamps)
            add(window.endMillis)
        }

    return boundaries
        .zipWithNext()
        .map {
            (start, end) ->

            QuietPhase(
                startMillis = start,
                endMillis = end
            )
        }
        .filter {
            it.durationMillis > 0L
        }
}

private fun buildHourlyActivity(
    window: SleepAnalysisWindow,
    displayEvents: List<WakeEvent>,
    cpuEvents: List<WakeEvent>
): List<HourActivity> {
    if (
        window.endMillis <=
        window.startMillis
    ) {
        return emptyList()
    }

    val result =
        mutableListOf<HourActivity>()

    val cursor =
        Calendar.getInstance().apply {
            timeInMillis =
                window.startMillis

            set(
                Calendar.MINUTE,
                0
            )

            set(
                Calendar.SECOND,
                0
            )

            set(
                Calendar.MILLISECOND,
                0
            )
        }

    var hourStart =
        cursor.timeInMillis

    while (
        hourStart <=
        window.endMillis &&
        result.size < 24
    ) {
        val hourEnd =
            hourStart +
                3_600_000L

        val hour =
            Calendar.getInstance()
                .apply {
                    timeInMillis =
                        hourStart
                }
                .get(
                    Calendar.HOUR_OF_DAY
                )

        val displayCount =
            displayEvents.count {
                it.timestamp >=
                    hourStart &&
                    it.timestamp <
                        hourEnd
            }

        val cpuCount =
            cpuEvents.count {
                it.timestamp >=
                    hourStart &&
                    it.timestamp <
                        hourEnd
            }

        result.add(
            HourActivity(
                hour = hour,
                displayWakeups =
                    displayCount,
                cpuWakeups =
                    cpuCount
            )
        )

        hourStart =
            hourEnd
    }

    return result
}

private fun calculateTechnicalSleepScore(
    window: SleepAnalysisWindow,
    displayWakeups: Int,
    cpuWakeups: Int,
    unexplainedDisplayWakeups: Int,
    longestQuietMillis: Long,
    wakeupsPerHour: Double
): Int {
    if (
        window.durationMillis <
        60_000L
    ) {
        return 100
    }

    var score =
        100.0

    score -=
        displayWakeups * 7.0

    score -=
        unexplainedDisplayWakeups *
            4.0

    score -=
        cpuWakeups * 0.7

    score -=
        when {
            wakeupsPerHour <= 1.0 ->
                0.0

            wakeupsPerHour <= 3.0 ->
                (
                    wakeupsPerHour -
                        1.0
                ) * 2.0

            wakeupsPerHour <= 6.0 ->
                4.0 +
                    (
                        wakeupsPerHour -
                            3.0
                    ) * 3.0

            else ->
                13.0 +
                    (
                        wakeupsPerHour -
                            6.0
                    ) * 4.0
        }

    val quietRatio =
        longestQuietMillis
            .toDouble()
            .div(
                window.durationMillis
                    .toDouble()
            )
            .coerceIn(
                0.0,
                1.0
            )

    score +=
        when {
            quietRatio >= 0.75 ->
                8.0

            quietRatio >= 0.50 ->
                5.0

            quietRatio >= 0.30 ->
                2.0

            else ->
                0.0
        }

    return score
        .roundToInt()
        .coerceIn(
            0,
            100
        )
}

private fun sleepScoreRating(
    context: Context,
    score: Int
): String {
    return when {
        score >= 97 ->
            context.getString(
                R.string.sleep_rating_excellent
            )

        score >= 90 ->
            context.getString(
                R.string.sleep_rating_very_quiet
            )

        score >= 80 ->
            context.getString(
                R.string.sleep_rating_quiet
            )

        score >= 65 ->
            context.getString(
                R.string.sleep_rating_normal
            )

        score >= 50 ->
            context.getString(
                R.string.sleep_rating_restless
            )

        else ->
            context.getString(
                R.string.sleep_rating_heavily_interrupted
            )
    }
}

private fun sleepScoreSummary(
    context: Context,
    score: Int,
    totalWakeups: Int,
    displayWakeups: Int
): String {
    return when {
        totalWakeups == 0 ->
            context.getString(
                R.string.sleep_summary_no_activity
            )

        score >= 90 ->
            context.getString(
                R.string.sleep_summary_quiet
            )

        score >= 80 ->
            context.getString(
                R.string.sleep_summary_few_interruptions
            )

        score >= 65 ->
            context.getString(
                R.string.sleep_summary_unremarkable
            )

        score >= 50 &&
            displayWakeups == 0 ->
            context.getString(
                R.string.sleep_summary_background
            )

        score >= 50 ->
            context.getString(
                R.string.sleep_summary_display_background
            )

        else ->
            context.getString(
                R.string.sleep_summary_dense
            )
    }
}

private fun buildSleepHints(
    context: Context,
    window: SleepAnalysisWindow,
    displayWakeups: Int,
    cpuWakeups: Int,
    unexplainedDisplayWakeups: Int,
    longestQuietMillis: Long,
    wakeupsPerHour: Double,
    busiestHour: HourActivity?
): List<String> {
    val hints =
        mutableListOf<String>()

    val shortSession =
        window.durationMillis <
            15L * 60L * 1_000L

    if (
        window.durationMillis <
        30L * 60L * 1_000L
    ) {
        hints.add(
            context.getString(
                R.string.sleep_hint_run_longer
            )
        )
    }

    if (
        displayWakeups == 0
    ) {
        hints.add(
            context.getString(
                R.string.sleep_hint_display_off
            )
        )
    } else if (
        displayWakeups <= 2
    ) {
        hints.add(
            context.getString(
                R.string.sleep_hint_few_display
            )
        )
    } else {
        hints.add(
            context.getString(
                R.string.sleep_hint_many_display
            )
        )
    }

    if (
        unexplainedDisplayWakeups > 0
    ) {
        hints.add(
            context.resources.getQuantityString(
                R.plurals.sleep_hint_unexplained_display,
                unexplainedDisplayWakeups,
                unexplainedDisplayWakeups
            )
        )
    }

    if (
        cpuWakeups == 0
    ) {
        hints.add(
            context.getString(
                R.string.sleep_hint_no_cpu
            )
        )
    } else if (shortSession) {
        hints.add(
            context.resources.getQuantityString(
                R.plurals.sleep_hint_short_cpu,
                cpuWakeups,
                cpuWakeups
            )
        )
    } else if (
        wakeupsPerHour <= 3.0
    ) {
        hints.add(
            context.getString(
                R.string.sleep_hint_low_background
            )
        )
    } else if (
        wakeupsPerHour <= 8.0
    ) {
        hints.add(
            context.getString(
                R.string.sleep_hint_several_background
            )
        )
    } else {
        hints.add(
            context.getString(
                R.string.sleep_hint_frequent_cpu
            )
        )
    }

    if (
        longestQuietMillis >=
        2L * 60L * 60L * 1_000L
    ) {
        hints.add(
            context.getString(
                R.string.sleep_hint_long_quiet
            )
        )
    }

    busiestHour
        ?.takeIf {
            !shortSession &&
                it.total >= 3
        }
        ?.let { hour ->
            val start =
                hour.hour
                    .toString()
                    .padStart(
                        2,
                        '0'
                    )

            val end =
                (
                    (hour.hour + 1) % 24
                )
                    .toString()
                    .padStart(
                        2,
                        '0'
                    )

            hints.add(
                context.getString(
                    R.string.sleep_hint_busiest_hours,
                    start,
                    end
                )
            )
        }

    return hints.distinct()
}

private fun hasTechnicalExplanation(
    context: Context,
    event: WakeEvent
): Boolean {
    if (
        event.type !=
        "SCREEN_ON"
    ) {
        return false
    }

    val details =
        event.details

    return listOf(
        R.string.sleep_label_direct_wake_reason,
        R.string.sleep_label_likely_cause,
        R.string.sleep_label_possible_cause,
        R.string.sleep_label_later_detected_cause,
        R.string.sleep_label_system_hint,
        R.string.sleep_label_wakeup_alarm_hint,
        R.string.sleep_label_background_job_hint
    ).any {
        LocalizedText.containsAny(
            details,
            context,
            it
        )
    }
}

private fun isTechnicallyUnexplained(
    context: Context,
    event: WakeEvent
): Boolean {
    return event.type ==
        "SCREEN_ON" &&
        LocalizedText.containsAny(
            event.details,
            context,
            R.string.sleep_marker_cause_unknown
        ) &&
        !hasTechnicalExplanation(
            context,
            event
        )
}

@Composable
private fun formatSleepWindow(
    window: SleepAnalysisWindow
): String {
    val formatter =
        SimpleDateFormat(
            "dd.MM. · HH:mm",
            Locale.getDefault()
        )

    val range =
        buildString {
            append(
                formatter.format(
                    Date(window.startMillis)
                )
            )

            if (
                window.startMillis !=
                window.endMillis
            ) {
                append(" – ")

                append(
                    formatter.format(
                        Date(window.endMillis)
                    )
                )
            }
        }

    return if (window.ongoing) {
        stringResource(
            R.string.sleep_window_running,
            range
        )
    } else {
        range
    }
}

private fun formatTime(
    timestamp: Long
): String {
    return SimpleDateFormat(
        "HH:mm",
        Locale.getDefault()
    ).format(
        Date(timestamp)
    )
}

@Composable
private fun formatDurationPrecise(
    millis: Long
): String {
    val safeSeconds =
        millis.coerceAtLeast(0L) /
            1_000L

    val minutes =
        safeSeconds /
            60L

    val seconds =
        safeSeconds %
            60L

    return when {
        minutes > 0L &&
            seconds > 0L ->
            stringResource(
                R.string.sleep_duration_minutes_seconds,
                minutes,
                seconds
            )

        minutes > 0L ->
            stringResource(
                R.string.sleep_duration_minutes,
                minutes
            )

        else ->
            stringResource(
                R.string.sleep_duration_seconds,
                seconds
            )
    }
}

@Composable
private fun formatDurationCompact(
    millis: Long
): String {
    val safeMillis =
        millis.coerceAtLeast(0L)

    val totalMinutes =
        safeMillis /
            60_000L

    val days =
        totalMinutes /
            1_440L

    val hours =
        (
            totalMinutes %
                1_440L
        ) /
            60L

    val minutes =
        totalMinutes %
            60L

    return when {
        days > 0L ->
            stringResource(
                R.string.sleep_duration_days_hours,
                days,
                hours
            )

        hours > 0L ->
            stringResource(
                R.string.sleep_duration_hours_minutes,
                hours,
                minutes
            )

        totalMinutes > 0L ->
            stringResource(
                R.string.sleep_duration_minutes,
                totalMinutes
            )

        safeMillis >= 1_000L ->
            stringResource(
                R.string.sleep_duration_seconds,
                safeMillis / 1_000L
            )

        else ->
            stringResource(
                R.string.sleep_duration_seconds,
                0L
            )
    }
}

private fun formatDecimal(
    value: Double
): String {
    return String.format(
        Locale.getDefault(),
        "%.1f",
        value
    )
}
