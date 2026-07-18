package de.sanniki.wakesleuth

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
    val label: String
) {
    LIKELY_INVOLVED(
        "Wahrscheinlich beteiligt"
    ),
    TEMPORALLY_NOTICEABLE(
        "Zeitlich auffällig"
    ),
    COMPANION_ACTIVITY(
        "Aktiv · kein Wakeup-Bezug"
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
    val analysis =
        remember(
            events,
            monitoring
        ) {
            buildSleepAnalysis(
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
                            title = "Netzwerk",
                            summary =
                                buildString {
                                    append(
                                        networkSession.total
                                            ?: "Keine Verkehrsdaten"
                                    )

                                    networkSession.activeApps
                                        ?.let { count ->
                                            append(
                                                " · $count Apps"
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
                            "Aktivitäts-Einordnung",
                        summary =
                            "${analysis.suspicionCandidates.size} aktive Quellen eingeordnet",
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
                            "Ruhephasen und Verlauf",
                        summary =
                            analysis.longestQuietPhase
                                ?.let { phase ->
                                    "Längste Ruhe: " +
                                        formatDurationCompact(
                                            phase.durationMillis
                                        )
                                }
                                ?: "Noch nicht ermittelbar",
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
                            "Technische Kennzahlen",
                        summary =
                            "${analysis.totalWakeups} Aktivitäten insgesamt",
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
                            "Hinweise und Einordnung",
                        summary =
                            "${visibleHints.size} Hinweise",
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
                        "Der Ruhe-Score bewertet ausschließlich die technische Geräteaktivität und nicht deinen persönlichen Schlaf.",
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
                            "Analyse wird vorbereitet"

                        hasRecordedEvents ->
                            "Analyse noch zu kurz"

                        else ->
                            "Noch keine Analyse vorhanden"
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
                            "Für erste Ergebnisse mindestens 1 Minute aufzeichnen."

                        hasRecordedEvents ->
                            "Der aufgezeichnete Zeitraum reicht noch nicht für eine Auswertung aus."

                        else ->
                            "Starte eine Analyse, um die Geräteaktivität auszuwerten."
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
                text = "Geräteruhe-Analyse",
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
                                "Vorläufig · $rating"
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
                                "Kurze Messung: " +
                                    summary
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
                                "vorläufig"
                            } else {
                                "von 100"
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
                label = "Display an",
                modifier = Modifier.weight(1f)
            )

            SleepMetricCard(
                value =
                    analysis.cpuWakeups
                        .toString(),
                label = "CPU-Wakes",
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
                        "in " +
                            formatDurationPrecise(
                                analysis.window
                                    .durationMillis
                            )
                    } else {
                        "pro Stunde"
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
                    "Stundenwert ab 15 Minuten Messdauer.",
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
                    "Netzwerk während der Sitzung",
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
                        label = "Datenverkehr",
                        value = total
                    )
                }

            summary.activeApps
                ?.let { activeApps ->
                    SleepValueRow(
                        label = "Aktive Apps",
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
                            label = "Empfangen",
                            value = received
                        )
                    }

                summary.sent
                    ?.let { sent ->
                        SleepValueRow(
                            label = "Gesendet",
                            value = sent
                        )
                    }

                summary.duration
                    ?.let { duration ->
                        SleepValueRow(
                            label = "Messdauer",
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
                        if (
                            visibleApps.size == 1
                        ) {
                            "Aktivste App"
                        } else {
                            "Aktivste Apps"
                        },
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
                                            "Empfangen: $it"
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
                                            "Gesendet: $it"
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
                    "Datenverkehr zeigt App-Aktivität, beweist aber keinen direkten Zusammenhang mit einem Wakeup.",
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
                text = "Aktivitäts-Einordnung",
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
                    "Einordnung aus CPU-Wakeups und Netzwerkaktivität derselben Sitzung.",
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
                            candidate.level.label,
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
                                        "CPU-Zuordnungen: " +
                                            candidate
                                                .cpuOccurrences
                                    )
                                }

                                candidate
                                    .networkTraffic
                                    ?.let { traffic ->
                                        if (isNotEmpty()) {
                                            append(" · ")
                                        }

                                        append(
                                            "Netzwerk: " +
                                                traffic
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
                                    "Weniger anzeigen"
                                } else {
                                    "Weitere $hiddenCount anzeigen"
                                }
                        )
                    }
            }

            Spacer(
                modifier = Modifier.height(9.dp)
            )

            Text(
                text =
                    "Netzwerkaktivität zeigt Nutzung oder Hintergrundverkehr. Ein Wakeup-Bezug wird nur bei passenden CPU-Hinweisen angenommen.",
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
        text = "Ruhephasen",
        style =
            MaterialTheme.typography
                .titleSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(
        modifier = Modifier.height(10.dp)
    )

    SleepValueRow(
        label = "Längste Ruhephase",
        value =
            analysis.longestQuietPhase
                ?.let {
                    formatDurationCompact(
                        it.durationMillis
                    )
                }
                ?: "Noch nicht ermittelbar"
    )

    analysis.secondLongestQuietPhase
        ?.let { phase ->
            SleepValueRow(
                label =
                    "Zweitlängste Ruhephase",
                value =
                    formatDurationCompact(
                        phase.durationMillis
                    )
            )
        }

    SleepValueRow(
        label = "Durchschnittliche Ruhephase",
        value =
            if (
                analysis.averageQuietMillis >
                0L
            ) {
                formatDurationCompact(
                    analysis.averageQuietMillis
                )
            } else {
                "Noch nicht ermittelbar"
            }
    )

    analysis.longestQuietPhase
        ?.let { phase ->
            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text =
                    "Längste Phase: " +
                        formatTime(
                            phase.startMillis
                        ) +
                        " – " +
                        formatTime(
                            phase.endMillis
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
        text = "Aktivität im Verlauf",
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
            "Höhere Balken bedeuten mehr Display- oder CPU-Aktivität.",
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
                "Für die grafische Auswertung sind noch keine Aktivitäten vorhanden.",
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
        text = "Technische Kennzahlen",
        style =
            MaterialTheme.typography
                .titleSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(
        modifier = Modifier.height(8.dp)
    )

    SleepValueRow(
        label = "Zugeordnete Display-Wakeups",
        value =
            analysis
                .explainedDisplayWakeups
                .toString()
    )

    SleepValueRow(
        label = "Ungeklärte Display-Wakeups",
        value =
            analysis
                .unexplainedDisplayWakeups
                .toString()
    )

    SleepValueRow(
        label = "Aktivitäten insgesamt",
        value =
            analysis.totalWakeups
                .toString()
    )

    SleepValueRow(
        label =
            "Mittlerer Abstand zwischen Aktivitäten",
        value =
            analysis.averageWakeDistanceMillis
                ?.let {
                    formatDurationCompact(it)
                }
                ?: "Noch nicht ermittelbar"
    )

    if (
        analysis.window.durationMillis <
        15L * 60L * 1_000L
    ) {
        SleepValueRow(
            label = "Kurzer Messzeitraum",
            value =
                analysis.totalWakeups
                    .toString() +
                    " Ereignisse in " +
                    formatDurationPrecise(
                        analysis.window
                            .durationMillis
                    )
        )
    } else {
        analysis.busiestHour
            ?.let { hour ->
                SleepValueRow(
                    label = "Aktivste Stunde",
                    value =
                        hour.hour
                            .toString()
                            .padStart(
                                2,
                                '0'
                            ) +
                            ":00–" +
                            (
                                (hour.hour + 1) % 24
                            )
                                .toString()
                                .padStart(
                                    2,
                                    '0'
                                ) +
                            ":00 · " +
                            hour.total +
                            " Ereignisse"
                )
            }
    }
}

@Composable
private fun SleepHintsSection(
    hints: List<String>
) {
    Text(
        text = "Einordnung",
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
    events: List<WakeEvent>,
    monitoring: Boolean
): SleepAnalysisData {
    val window =
        calculateSleepAnalysisWindow(
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
                    it.details
                )
            }

    val suspicionCandidates =
        buildSuspicionCandidates(
            relevantEvents =
                relevantEvents,
            networkSession =
                networkSession
        )

    val explainedDisplay =
        displayEvents.count {
            hasTechnicalExplanation(it)
        }

    val unexplainedDisplay =
        displayEvents.count {
            isTechnicallyUnexplained(it)
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
            score
        )

    val summary =
        sleepScoreSummary(
            score = score,
            totalWakeups = totalWakeups,
            displayWakeups =
                displayEvents.size
        )

    val hints =
        buildSleepHints(
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
                    event
                )
            }
            .map {
                normalizeSuspicionSource(it)
            }
            .filter {
                it.isNotBlank() &&
                    !it.equals(
                        "Nicht eindeutig zuordenbar",
                        ignoreCase = true
                    )
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
                    "Die Quelle wurde mehrfach bei CPU-Wakeups erkannt und verursachte zusätzlich Datenverkehr."

                cpuCount >= 2 ->
                    "Die Quelle wurde in dieser Sitzung mehrfach zeitlich einem CPU-Wakeup zugeordnet."

                matchingNetwork != null ->
                    "Die Quelle wurde einem CPU-Wakeup zugeordnet und war außerdem im Netzwerk aktiv."

                else ->
                    "Die Quelle wurde einmal im kurzen Zeitfenster eines CPU-Wakeups erkannt."
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
                    candidate.name,
                    app.name
                )
            }

        if (!alreadyIncluded) {
            result.add(
                SuspicionCandidate(
                    name =
                        sourceDisplayName(
                            app.name
                        ),
                    level =
                        SuspicionLevel
                            .COMPANION_ACTIVITY,
                    explanation =
                        "Die App war während der Sitzung aktiv, wurde aber keinem CPU-Wakeup zugeordnet.",
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
    event: WakeEvent
): String? {
    return event.details
        .lineSequence()
        .map {
            it.trim()
        }
        .firstOrNull {
            it.startsWith(
                "Mögliche Quelle:"
            )
        }
        ?.substringAfter(":")
        ?.trim()
        ?.ifBlank { null }
}

private fun normalizeSuspicionSource(
    source: String
): String {
    val cleaned =
        source
            .trim()
            .trimStart('*')
            .trimEnd('*')
            .removePrefix(
                "CPU-Wakeup · "
            )
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
            "Systemdienst · UID " +
                uidMatch.groupValues[1]

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
            "Google-Dienste"

        lower.contains("whatsapp") ->
            "WhatsApp"

        lower.contains("oplus") ||
            lower.contains("oneplus") ||
            lower.contains("athena") ->
            "OnePlus-System"

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
    first: String,
    second: String
): Boolean {
    val a =
        normalizeComparisonSource(first)

    val b =
        normalizeComparisonSource(second)

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
    value: String
): String {
    return normalizeSuspicionSource(value)
        .lowercase(
            Locale.getDefault()
        )
        .replace(
            Regex("""[^a-z0-9äöüß]+"""),
            ""
        )
}

private fun parseNetworkSessionSummary(
    details: String
): NetworkSessionSummary {
    val lines =
        details.lines()
            .map {
                it.trim()
            }

    fun valueAfter(
        prefix: String
    ): String? {
        return lines
            .firstOrNull {
                it.startsWith(prefix)
            }
            ?.substringAfter(prefix)
            ?.trim()
            ?.ifBlank { null }
    }

    val trafficLine =
        valueAfter("Gesamt:")

    val total =
        trafficLine
            ?.substringBefore("·")
            ?.trim()
            ?.ifBlank { null }

    val received =
        trafficLine
            ?.substringAfter(
                "Empfangen:",
                ""
            )
            ?.substringBefore("·")
            ?.trim()
            ?.ifBlank { null }

    val sent =
        trafficLine
            ?.substringAfter(
                "Gesendet:",
                ""
            )
            ?.trim()
            ?.ifBlank { null }

    val activeApps =
        valueAfter(
            "Apps mit Datenverkehr:"
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
                            it.startsWith(
                                "Empfangen:"
                            )
                        }
                        ?.substringAfter(
                            "Empfangen:"
                        )
                        ?.substringBefore("·")
                        ?.trim()
                        ?.ifBlank { null }

                val appSent =
                    transferLine
                        .takeIf {
                            it.contains(
                                "Gesendet:"
                            )
                        }
                        ?.substringAfter(
                            "Gesendet:"
                        )
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
            details.contains(
                "Keine Ausgangsmessung verfügbar",
                ignoreCase = true
            ) ->
                "Für diese Sitzung war keine Ausgangsmessung verfügbar."

            details.contains(
                "Endmessung fehlgeschlagen",
                ignoreCase = true
            ) ->
                lines.firstOrNull {
                    it.contains(
                        "Endmessung fehlgeschlagen",
                        ignoreCase = true
                    )
                }

            details.contains(
                "Keine App mit messbarem Datenverkehr",
                ignoreCase = true
            ) ->
                "Keine App verursachte messbaren Datenverkehr."

            else ->
                null
        }

    return NetworkSessionSummary(
        duration =
            valueAfter("Messdauer:"),
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
                    "Laufende Geräteruhe-Analyse"
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
                "Letzte Geräteruhe-Analyse"
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
                "Aufgezeichneter Zeitraum"
        )
    }

    return SleepAnalysisWindow(
        startMillis = now,
        endMillis = now,
        ongoing = false,
        title =
            "Noch keine Analyse vorhanden"
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
    score: Int
): String {
    return when {
        score >= 97 ->
            "Ausgezeichnet ruhig"

        score >= 90 ->
            "Sehr ruhig"

        score >= 80 ->
            "Ruhig"

        score >= 65 ->
            "Normal aktiv"

        score >= 50 ->
            "Unruhig"

        else ->
            "Stark unterbrochen"
    }
}

private fun sleepScoreSummary(
    score: Int,
    totalWakeups: Int,
    displayWakeups: Int
): String {
    return when {
        totalWakeups == 0 ->
            "Im ausgewerteten Zeitraum wurde keine relevante Aktivität erfasst."

        score >= 90 ->
            "Das Gerät blieb über weite Strecken ruhig."

        score >= 80 ->
            "Nur wenige stärkere Unterbrechungen wurden erkannt."

        score >= 65 ->
            "Die Aktivität liegt in einem unauffälligen Bereich."

        score >= 50 &&
            displayWakeups == 0 ->
            "Mehrere Hintergrundaktivitäten unterbrachen die Ruhephasen."

        score >= 50 ->
            "Mehrere Display- und Hintergrundaktivitäten wurden erkannt."

        else ->
            "Der Zeitraum enthielt viele oder dicht aufeinanderfolgende Aktivitäten."
    }
}

private fun buildSleepHints(
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
            "Für eine belastbarere Einordnung sollte die Überwachung länger als 30 Minuten laufen."
        )
    }

    if (
        displayWakeups == 0
    ) {
        hints.add(
            "Das Display blieb während des ausgewerteten Zeitraums ausgeschaltet."
        )
    } else if (
        displayWakeups <= 2
    ) {
        hints.add(
            "Es wurden nur wenige Display-Aktivierungen erkannt."
        )
    } else {
        hints.add(
            "Mehrere Display-Aktivierungen unterbrachen die Geräte-Ruhe."
        )
    }

    if (
        unexplainedDisplayWakeups > 0
    ) {
        hints.add(
            "$unexplainedDisplayWakeups Display-Aktivierungen sind noch nicht eindeutig zugeordnet."
        )
    }

    if (
        cpuWakeups == 0
    ) {
        hints.add(
            "Keine CPU-Hintergrund-Wakeups wurden erfasst."
        )
    } else if (shortSession) {
        hints.add(
            "In der kurzen Messung wurden " +
                cpuWakeups +
                " CPU-Aktivitäten erfasst."
        )
    } else if (
        wakeupsPerHour <= 3.0
    ) {
        hints.add(
            "Die Hintergrundaktivität war insgesamt gering."
        )
    } else if (
        wakeupsPerHour <= 8.0
    ) {
        hints.add(
            "Mehrere kurze Hintergrundaktivitäten wurden erkannt."
        )
    } else {
        hints.add(
            "Die CPU wurde im Durchschnitt häufig aus dem Ruhezustand geholt."
        )
    }

    if (
        longestQuietMillis >=
        2L * 60L * 60L * 1_000L
    ) {
        hints.add(
            "Es gab mindestens eine längere ununterbrochene Ruhephase."
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
                "Die meiste Aktivität trat zwischen $start:00 und $end:00 Uhr auf."
            )
        }

    return hints.distinct()
}

private fun hasTechnicalExplanation(
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

    return details.contains(
        "Direkter Aufweckgrund:"
    ) ||
        details.contains(
            "Wahrscheinliche Ursache:"
        ) ||
        details.contains(
            "Mögliche Ursache:"
        ) ||
        details.contains(
            "Nachträglich erkannte Ursache:"
        ) ||
        details.contains(
            "Systemhinweis:"
        ) ||
        details.contains(
            "Wakeup-Alarm-Hinweis:"
        ) ||
        details.contains(
            "Hintergrundjob-Hinweis:"
        )
}

private fun isTechnicallyUnexplained(
    event: WakeEvent
): Boolean {
    return event.type ==
        "SCREEN_ON" &&
        event.details.contains(
            "Ursache: noch unbekannt"
        ) &&
        !hasTechnicalExplanation(event)
}

private fun formatSleepWindow(
    window: SleepAnalysisWindow
): String {
    val formatter =
        SimpleDateFormat(
            "dd.MM. · HH:mm",
            Locale.getDefault()
        )

    return buildString {
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

        if (window.ongoing) {
            append(" · läuft")
        }
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
            "${minutes} Min ${seconds} Sek"

        minutes > 0L ->
            "${minutes} Min"

        else ->
            "${seconds} Sek"
    }
}

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
            "${days} T ${hours} Std"

        hours > 0L ->
            "${hours} Std ${minutes} Min"

        totalMinutes > 0L ->
            "${totalMinutes} Min"

        safeMillis >= 1_000L ->
            "${safeMillis / 1_000L} Sek"

        else ->
            "0 Sek"
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
