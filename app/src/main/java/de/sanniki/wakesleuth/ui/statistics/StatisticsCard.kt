package de.sanniki.wakesleuth.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ui.ScreenOnStatistics
import de.sanniki.wakesleuth.ui.common.PreviewSurface
import java.util.Calendar

internal fun startOfTodayMillis(): Long =
    Calendar
        .getInstance()
        .apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

internal fun startOfNextDayMillis(todayStartMillis: Long): Long =
    Calendar
        .getInstance()
        .apply {
            timeInMillis = todayStartMillis

            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

@Composable
internal fun StatisticsCard(statistics: ScreenOnStatistics) {
    val screenOnCount = statistics.total

    val hintedCount = statistics.withCause

    val unknownCount = statistics.unexplained

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.main_daily_overview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (
                screenOnCount == 0 && hintedCount == 0 && unknownCount == 0
            ) {
                Text(
                    text = stringResource(R.string.main_daily_no_events),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatisticValue(
                        value = screenOnCount.toString(),
                        label = stringResource(R.string.main_metric_screen_on),
                        modifier = Modifier.weight(1f),
                    )

                    StatisticValue(
                        value = hintedCount.toString(),
                        label = stringResource(R.string.main_metric_with_cause),
                        modifier = Modifier.weight(1f),
                    )

                    StatisticValue(
                        value = unknownCount.toString(),
                        label = stringResource(R.string.main_filter_unexplained),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticValue(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatisticsCardPreview() {
    PreviewSurface {
        StatisticsCard(statistics = ScreenOnStatistics(total = 12, withCause = 9, unexplained = 3))
    }
}
