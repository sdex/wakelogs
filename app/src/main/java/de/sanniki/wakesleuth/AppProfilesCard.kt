package de.sanniki.wakesleuth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun AppProfilesCard(
    events: List<WakeEvent>,
    detailLevel: DetailLevel
) {
    val context =
        androidx.compose.ui.platform
            .LocalContext.current

    val sessions =
        remember(events) {
            SessionArchiveStore
                .getSessions(context)
                .sortedByDescending {
                    it.startMillis
                }
        }

    val profiles =
        remember(sessions) {
            buildAppProfiles(
                sessions
            )
        }

    if (
        sessions.isEmpty() ||
        profiles.isEmpty()
    ) {
        return
    }

    val expanded =
        remember {
            mutableStateOf(false)
        }

    val visibleProfiles =
        profiles.take(
            when (detailLevel) {
                DetailLevel.SIMPLE -> 3
                DetailLevel.NORMAL -> 5
                DetailLevel.EXPERT -> 8
            }
        )

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface
            )
    ) {
        Column(
            modifier =
                Modifier.padding(18.dp)
        ) {
            Text(
                text =
                    "App-Steckbriefe",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    profiles.size
                        .toString() +
                        if (
                            profiles.size == 1
                        ) {
                            " Quelle über " +
                                sessions.size +
                                " Sitzungen"
                        } else {
                            " Quellen über " +
                                sessions.size +
                                " Sitzungen"
                        },
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            OutlinedButton(
                onClick = {
                    expanded.value =
                        !expanded.value
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    text =
                        if (expanded.value) {
                            "Steckbriefe ausblenden"
                        } else {
                            "Steckbriefe anzeigen"
                        }
                )
            }

            if (expanded.value) {
                Spacer(
                    modifier =
                        Modifier.height(14.dp)
                )

                visibleProfiles
                    .forEachIndexed {
                            index,
                            profile ->

                        AppProfileEntry(
                            profile =
                                profile,
                            detailLevel =
                                detailLevel
                        )

                        if (
                            index <
                            visibleProfiles.lastIndex
                        ) {
                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )

                            HorizontalDivider()

                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )
                        }
                    }

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Text(
                    text =
                        "Ältere Sitzungen enthalten zunächst nur Netzwerkdaten. CPU-, Display- und Begleitwerte werden mit neuen Messungen schrittweise ergänzt.",
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
    }
}

@Composable
private fun AppProfileEntry(
    profile: AppProfileData,
    detailLevel: DetailLevel
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Text(
            text = profile.name,
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
            fontWeight =
                FontWeight.Bold,
            maxLines = 2,
            overflow =
                TextOverflow.Ellipsis
        )

        Spacer(
            modifier =
                Modifier.height(5.dp)
        )

        Text(
            text =
                appProfileSummary(
                    profile
                ),
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme
                    .typography
                    .bodySmall
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        AppProfileTrendCard(
            profile = profile
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        AppProfileValueRow(
            label =
                "Sitzungen",
            value =
                "${profile.sessionsSeen} von " +
                    profile.totalSessions
        )

        AppProfileValueRow(
            label =
                "Netzwerk gesamt",
            value =
                formatAppProfileBytes(
                    profile
                        .networkTotalBytes
                )
        )

        if (
            profile.networkSessionCount > 0
        ) {
            AppProfileValueRow(
                label =
                    "Ø Netzwerk / Sitzung",
                value =
                    formatAppProfileBytes(
                        profile
                            .averageNetworkBytes
                    )
            )
        }

        AppProfileValueRow(
            label =
                "CPU-Zuordnungen",
            value =
                profile.cpuCount
                    .toString()
        )

        AppProfileValueRow(
            label =
                "Display-Zuordnungen",
            value =
                profile.displayCount
                    .toString()
        )

        if (
            detailLevel !=
            DetailLevel.SIMPLE
        ) {
            AppProfileValueRow(
                label =
                    "Davon Begleitaktivität",
                value =
                    profile.companionCount
                        .toString()
            )

            profile.longestCpuDurationMillis
                ?.let { duration ->
                    AppProfileValueRow(
                        label =
                            "Längste CPU-Wachzeit",
                        value =
                            formatAppProfileDuration(
                                duration
                            )
                    )
                }
        }
    }
}

private fun appProfileSummary(
    profile: AppProfileData
): String {
    return when {
        profile.displayCount > 0 &&
            profile.companionCount <
                profile.displayCount ->
            "Mehrfach bei Display-Ereignissen zugeordnet. Die Vertrauensstufe der einzelnen Ereignisse bleibt entscheidend."

        profile.cpuCount >= 3 ->
            "Regelmäßig als mögliche CPU-Quelle erkannt; noch kein Beweis für problematischen Akkuverbrauch."

        profile.companionCount > 0 ->
            "Wiederholt als Begleitaktivität sichtbar, bislang nicht als direkter Hauptauslöser bestätigt."

        profile.networkTotalBytes > 0L ->
            "In mehreren Sitzungen mit Netzwerkverkehr sichtbar, ohne automatisch einen Wakeup-Zusammenhang zu beweisen."

        else ->
            "Technische Quelle aus gespeicherten Sitzungen."
    }
}

@Composable
private fun AppProfileTrendCard(
    profile: AppProfileData
) {
    val title =
        when (profile.trend) {
            AppProfileTrend.MORE_ACTIVE ->
                "Häufiger aktiv"

            AppProfileTrend.LESS_ACTIVE ->
                "Seltener aktiv"

            AppProfileTrend.STABLE ->
                "Ähnlicher Verlauf"

            AppProfileTrend.NOT_ENOUGH_DATA ->
                "Noch nicht genug Sitzungen"
        }

    val explanation =
        when (profile.trend) {
            AppProfileTrend.MORE_ACTIVE ->
                "In den jüngeren Sitzungen wurde diese Quelle häufiger oder stärker erfasst."

            AppProfileTrend.LESS_ACTIVE ->
                "In den jüngeren Sitzungen wurde diese Quelle seltener oder schwächer erfasst."

            AppProfileTrend.STABLE ->
                "Zwischen den jüngeren und älteren Sitzungen besteht kein deutlicher Unterschied."

            AppProfileTrend.NOT_ENOUGH_DATA ->
                "Für einen belastbaren Vergleich werden mindestens vier gespeicherte Sitzungen benötigt."
        }

    val titleColor =
        when (profile.trend) {
            AppProfileTrend.MORE_ACTIVE ->
                MaterialTheme
                    .colorScheme
                    .error

            AppProfileTrend.LESS_ACTIVE ->
                MaterialTheme
                    .colorScheme
                    .primary

            AppProfileTrend.STABLE,
            AppProfileTrend.NOT_ENOUGH_DATA ->
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(11.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.52f)
            )
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = 11.dp,
                    vertical = 9.dp
                )
        ) {
            Text(
                text = title,
                color = titleColor,
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(2.dp)
            )

            Text(
                text = explanation,
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
    }
}

@Composable
private fun AppProfileValueRow(
    label: String,
    value: String
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        verticalAlignment =
            Alignment.Top
    ) {
        Text(
            text = label,
            modifier =
                Modifier.weight(1f),
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            style =
                MaterialTheme
                    .typography
                    .bodySmall
        )

        Spacer(
            modifier =
                Modifier.width(12.dp)
        )

        Text(
            text = value,
            modifier =
                Modifier.weight(1.1f),
            color =
                MaterialTheme
                    .colorScheme
                    .primary,
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            fontWeight =
                FontWeight.SemiBold,
            textAlign =
                TextAlign.End
        )
    }
}

private fun formatAppProfileBytes(
    bytes: Long
): String {
    val safe =
        bytes.coerceAtLeast(0L)

    return when {
        safe >=
            1024L * 1024L * 1024L ->
            String.format(
                Locale.getDefault(),
                "%.1f GB",
                safe /
                    (
                        1024.0 *
                            1024.0 *
                            1024.0
                    )
            )

        safe >=
            1024L * 1024L ->
            String.format(
                Locale.getDefault(),
                "%.1f MB",
                safe /
                    (
                        1024.0 *
                            1024.0
                    )
            )

        safe >= 1024L ->
            String.format(
                Locale.getDefault(),
                "%.1f KB",
                safe / 1024.0
            )

        else ->
            "$safe B"
    }
}

private fun formatAppProfileDuration(
    millis: Long
): String {
    val safe =
        millis.coerceAtLeast(0L)

    return when {
        safe < 1_000L ->
            "$safe ms"

        safe < 60_000L ->
            String.format(
                Locale.getDefault(),
                "%.1f s",
                safe / 1_000.0
            )

        else ->
            "${safe / 60_000L} min " +
                "${safe % 60_000L / 1_000L} s"
    }
}
