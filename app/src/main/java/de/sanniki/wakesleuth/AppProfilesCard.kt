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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun AppProfilesCard(
    sessions: List<ArchivedSession>,
    detailLevel: DetailLevel,
) {
    val profiles = remember(sessions) { buildAppProfiles(sessions) }

    if (
        sessions.isEmpty() || profiles.isEmpty()
    ) {
        return
    }

    val expanded = remember { mutableStateOf(false) }

    val visibleProfiles = profiles.take(
        when (detailLevel) {
            DetailLevel.SIMPLE -> 3
            DetailLevel.NORMAL -> 5
            DetailLevel.EXPERT -> 8
        },
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.profiles_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.profiles_sources_across_sessions,
                    pluralStringResource(R.plurals.profiles_source_count, profiles.size, profiles.size),
                    pluralStringResource(R.plurals.profiles_session_count, sessions.size, sessions.size),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    expanded.value = !expanded.value
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (expanded.value) {
                        stringResource(R.string.profiles_hide)
                    } else {
                        stringResource(R.string.profiles_show)
                    },
                )
            }

            if (expanded.value) {
                Spacer(modifier = Modifier.height(14.dp))

                visibleProfiles
                    .forEachIndexed {
                        index,
                        profile,
                        ->

                        AppProfileEntry(profile = profile, detailLevel = detailLevel)

                        if (
                            index <
                            visibleProfiles.lastIndex
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.profiles_older_sessions_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun AppProfileEntry(
    profile: AppProfileData,
    detailLevel: DetailLevel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = appProfileSummary(profile),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        AppProfileTrendCard(profile = profile)

        Spacer(modifier = Modifier.height(8.dp))

        AppProfileValueRow(
            label = stringResource(R.string.profiles_sessions),
            value = stringResource(R.string.profiles_sessions_value, profile.sessionsSeen, profile.totalSessions),
        )

        AppProfileValueRow(
            label = stringResource(R.string.profiles_network_total),
            value = formatAppProfileBytes(profile.networkTotalBytes),
        )

        if (
            profile.networkSessionCount > 0
        ) {
            AppProfileValueRow(
                label = stringResource(R.string.profiles_network_per_session),
                value = formatAppProfileBytes(profile.averageNetworkBytes),
            )
        }

        AppProfileValueRow(
            label = stringResource(R.string.profiles_cpu_attributions),
            value = profile.cpuCount.toString(),
        )

        AppProfileValueRow(
            label = stringResource(R.string.profiles_screen_attributions),
            value = profile.displayCount.toString(),
        )

        if (
            detailLevel != DetailLevel.SIMPLE
        ) {
            AppProfileValueRow(
                label = stringResource(R.string.profiles_companion_activity),
                value = profile.companionCount.toString(),
            )

            profile.longestCpuDurationMillis
                ?.let { duration ->
                    AppProfileValueRow(
                        label = stringResource(R.string.profiles_longest_cpu_awake_time),
                        value = formatAppProfileDuration(duration),
                    )
                }
        }
    }
}

@Composable
private fun appProfileSummary(profile: AppProfileData): String =
    when {
        profile.displayCount > 0 && profile.companionCount <
            profile.displayCount -> {
            stringResource(R.string.profiles_summary_display)
        }

        profile.cpuCount >= 3 -> {
            stringResource(R.string.profiles_summary_cpu)
        }

        profile.companionCount > 0 -> {
            stringResource(R.string.profiles_summary_companion)
        }

        profile.networkTotalBytes > 0L -> {
            stringResource(R.string.profiles_summary_network)
        }

        else -> {
            stringResource(R.string.profiles_summary_technical)
        }
    }

@Composable
private fun AppProfileTrendCard(profile: AppProfileData) {
    val title = when (profile.trend) {
        AppProfileTrend.MORE_ACTIVE -> stringResource(R.string.profiles_trend_more_active)
        AppProfileTrend.LESS_ACTIVE -> stringResource(R.string.profiles_trend_less_active)
        AppProfileTrend.STABLE -> stringResource(R.string.profiles_trend_stable)
        AppProfileTrend.NOT_ENOUGH_DATA -> stringResource(R.string.profiles_trend_not_enough_data)
    }

    val explanation = when (profile.trend) {
        AppProfileTrend.MORE_ACTIVE -> stringResource(R.string.profiles_trend_more_active_explanation)
        AppProfileTrend.LESS_ACTIVE -> stringResource(R.string.profiles_trend_less_active_explanation)
        AppProfileTrend.STABLE -> stringResource(R.string.profiles_trend_stable_explanation)
        AppProfileTrend.NOT_ENOUGH_DATA -> stringResource(R.string.profiles_trend_not_enough_data_explanation)
    }

    val titleColor = when (profile.trend) {
        AppProfileTrend.MORE_ACTIVE -> MaterialTheme.colorScheme.error

        AppProfileTrend.LESS_ACTIVE -> MaterialTheme.colorScheme.primary

        AppProfileTrend.STABLE,
        AppProfileTrend.NOT_ENOUGH_DATA,
        -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = explanation,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AppProfileValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = value,
            modifier = Modifier.weight(1.1f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

private fun formatAppProfileBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)

    return when {
        safe >=
            1024L * 1024L * 1024L -> {
            String.format(Locale.getDefault(), "%.1f GB", safe / (1024.0 * 1024.0 * 1024.0))
        }

        safe >= 1024L * 1024L -> {
            String.format(Locale.getDefault(), "%.1f MB", safe / (1024.0 * 1024.0))
        }

        safe >= 1024L -> {
            String.format(Locale.getDefault(), "%.1f KB", safe / 1024.0)
        }

        else -> {
            "$safe B"
        }
    }
}

private fun formatAppProfileDuration(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)

    return when {
        safe < 1_000L -> {
            "$safe ms"
        }

        safe < 60_000L -> {
            String.format(Locale.getDefault(), "%.1f s", safe / 1_000.0)
        }

        else -> {
            "${safe / 60_000L} min " + "${safe % 60_000L / 1_000L} s"
        }
    }
}
