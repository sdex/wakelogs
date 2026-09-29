package de.sanniki.wakesleuth.ui.monitor

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ui.common.PreviewSurface
import java.util.Locale

@Composable
internal fun MonitorCard(
    monitoring: Boolean,
    finalizing: Boolean,
    sessionDurationMillis: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (monitoring) {
                                Color(0xFF35A853)
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                                    .copy(alpha = 0.35f)
                            },
                        ),
                )

                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                ) {
                    Text(
                        text = when {
                            finalizing -> {
                                stringResource(R.string.main_monitor_finalizing)
                            }

                            monitoring -> {
                                stringResource(R.string.main_monitor_running)
                            }

                            else -> {
                                stringResource(R.string.main_monitor_ready)
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = stringResource(R.string.main_monitor_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    if (monitoring) {
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(
                                R.string.main_monitor_session_duration,
                                formatLiveSessionDuration(LocalContext.current, sessionDurationMillis),
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onStart,
                    enabled = !monitoring && !finalizing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Text(text = stringResource(R.string.main_monitor_start), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onStop,
                    enabled = monitoring && !finalizing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Text(text = stringResource(R.string.main_monitor_stop), fontWeight = FontWeight.Bold)
                }
            }

            if (monitoring) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (finalizing) {
                        stringResource(R.string.main_monitor_finalizing_hint)
                    } else {
                        stringResource(R.string.main_monitor_running_hint)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun formatLiveSessionDuration(
    context: Context,
    durationMillis: Long,
): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) /
        1_000L

    val days = totalSeconds /
        86_400L

    val hours = (totalSeconds % 86_400L) /
        3_600L

    val minutes = (totalSeconds % 3_600L) /
        60L

    val seconds = totalSeconds %
        60L

    return when {
        days > 0L -> {
            context.getString(R.string.main_live_duration_days, days, hours, minutes, seconds)
        }

        hours > 0L -> {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }

        else -> {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonitorCardIdlePreview() {
    PreviewSurface {
        MonitorCard(monitoring = false, finalizing = false, sessionDurationMillis = 0L, onStart = {}, onStop = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun MonitorCardRecordingPreview() {
    PreviewSurface {
        MonitorCard(monitoring = true, finalizing = false, sessionDurationMillis = 754_000L, onStart = {}, onStop = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun MonitorCardFinalizingPreview() {
    PreviewSurface {
        MonitorCard(monitoring = true, finalizing = true, sessionDurationMillis = 754_000L, onStart = {}, onStop = {})
    }
}
