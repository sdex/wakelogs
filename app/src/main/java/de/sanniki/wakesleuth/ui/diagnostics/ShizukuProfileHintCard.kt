package de.sanniki.wakesleuth.ui.diagnostics

import androidx.annotation.StringRes
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.DetailLevel
import de.sanniki.wakesleuth.DeviceProfile
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ShizukuState
import de.sanniki.wakesleuth.ui.common.PreviewSurface
import java.util.Locale

@Composable
internal fun ShizukuProfileHintCard(
    state: ShizukuState,
    detailLevel: DetailLevel,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current

    val deviceProfile = androidx.compose.runtime.remember { DeviceProfile.detect(context) }

    val measurementQualityExpanded = remember { mutableStateOf(false) }

    val profileIdentity = (
        deviceProfile.profileLabel + " " + deviceProfile.platformLabel + " " + deviceProfile.manufacturer
    ).lowercase(
        Locale.ROOT,
    )

    val isSamsungProfile = profileIdentity.contains("samsung")

    val isOnePlusProfile =
        profileIdentity.contains("oneplus") || profileIdentity.contains("oplus") || profileIdentity.contains("oxygenos")

    val isSimple = detailLevel == DetailLevel.SIMPLE

    val statusText = when (state) {
        ShizukuState.RUNNING_GRANTED -> stringResource(R.string.main_shizuku_active)
        ShizukuState.RUNNING_DENIED -> stringResource(R.string.main_shizuku_permission_missing)
        ShizukuState.NOT_RUNNING -> stringResource(R.string.main_setup_shizuku_not_running_title)
    }

    val description = when {
        isSimple && state == ShizukuState.RUNNING_GRANTED -> {
            stringResource(R.string.main_shizuku_hint_simple_granted)
        }

        isSimple -> {
            stringResource(R.string.main_shizuku_hint_simple_limited)
        }

        state == ShizukuState.RUNNING_GRANTED -> {
            stringResource(R.string.main_shizuku_hint_granted)
        }

        state == ShizukuState.RUNNING_DENIED -> {
            stringResource(R.string.main_shizuku_hint_denied)
        }

        else -> {
            stringResource(R.string.main_shizuku_hint_not_running)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = if (isSimple) {
                    stringResource(R.string.main_system_analysis)
                } else {
                    stringResource(R.string.main_shizuku_system_analysis)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusText,
                color = when (state) {
                    ShizukuState.RUNNING_GRANTED -> Color(0xFF35A853)
                    ShizukuState.RUNNING_DENIED -> MaterialTheme.colorScheme.error
                    ShizukuState.NOT_RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = deviceProfile.profileLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = deviceProfile.manufacturer + " " + deviceProfile.model + " · " + deviceProfile.platformLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme
                        .colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.52f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.main_measurement_quality),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = when {
                                    isOnePlusProfile -> {
                                        stringResource(R.string.main_measurement_quality_oneplus)
                                    }

                                    isSamsungProfile -> {
                                        stringResource(R.string.main_measurement_quality_samsung)
                                    }

                                    else -> {
                                        stringResource(R.string.main_measurement_quality_generic)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = if (
                                measurementQualityExpanded.value
                            ) {
                                "▲"
                            } else {
                                "▼"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    OutlinedButton(
                        onClick = {
                            measurementQualityExpanded.value = !measurementQualityExpanded.value
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (
                                measurementQualityExpanded.value
                            ) {
                                stringResource(R.string.main_measurement_quality_hide)
                            } else {
                                stringResource(R.string.main_measurement_quality_show)
                            },
                        )
                    }

                    if (
                        measurementQualityExpanded.value
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))

                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(8.dp))

                        MeasurementQualityRow(
                            label = stringResource(R.string.main_quality_display_activity),
                            quality = R.string.main_quality_good,
                        )

                        MeasurementQualityRow(
                            label = stringResource(R.string.main_quality_display_wake_reason),
                            quality = when {
                                isOnePlusProfile -> {
                                    R.string.main_quality_good
                                }

                                isSamsungProfile -> {
                                    R.string.main_quality_limited
                                }

                                else -> {
                                    R.string.main_quality_device_dependent
                                }
                            },
                        )

                        MeasurementQualityRow(
                            label = stringResource(R.string.main_quality_background_activity),
                            quality = when {
                                isOnePlusProfile -> {
                                    R.string.main_quality_good
                                }

                                isSamsungProfile -> {
                                    R.string.main_quality_limited
                                }

                                else -> {
                                    R.string.main_quality_device_dependent
                                }
                            },
                        )

                        MeasurementQualityRow(
                            label = stringResource(R.string.main_quality_network_activity),
                            quality = R.string.main_quality_good,
                        )

                        MeasurementQualityRow(
                            label = stringResource(R.string.main_quality_session_comparison),
                            quality = R.string.main_quality_good,
                        )
                    }
                }
            }

            if (
                state == ShizukuState.RUNNING_DENIED
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.main_setup_shizuku_permission_button))
                }
            }
        }
    }
}

@Composable
private fun MeasurementQualityRow(
    label: String,
    @StringRes quality: Int,
) {
    val qualityColor = when (quality) {
        R.string.main_quality_good -> {
            Color(0xFF35A853)
        }

        R.string.main_quality_limited -> {
            MaterialTheme.colorScheme.error
        }

        else -> {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = stringResource(quality),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = qualityColor,
            textAlign = TextAlign.End,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuProfileHintCardNormalPreview() {
    PreviewSurface {
        ShizukuProfileHintCard(state = ShizukuState.RUNNING_DENIED, detailLevel = DetailLevel.NORMAL, onRequestPermission = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuProfileHintCardSimplePreview() {
    PreviewSurface {
        ShizukuProfileHintCard(state = ShizukuState.NOT_RUNNING, detailLevel = DetailLevel.SIMPLE, onRequestPermission = {})
    }
}
