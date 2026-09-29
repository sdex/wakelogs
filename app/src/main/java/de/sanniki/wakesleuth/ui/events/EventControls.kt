package de.sanniki.wakesleuth.ui.events

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ui.common.PreviewSurface

internal enum class EventFilter(
    @StringRes val label: Int,
) {
    ALL(R.string.main_filter_all),
    DISPLAY(R.string.main_filter_display),
    BACKGROUND(R.string.main_filter_background),
    NOTIFICATIONS(R.string.main_filter_notifications),
    UNKNOWN(R.string.main_filter_unexplained),
}

internal enum class EventViewMode(
    @StringRes val label: Int,
) {
    LIST(R.string.main_view_mode_list),
    TIMELINE(R.string.main_view_mode_timeline),
}

@Composable
internal fun ActionCard(
    hasEvents: Boolean,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedButton(
            onClick = onExport,
            enabled = hasEvents,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.25.dp, MaterialTheme.colorScheme.primary),
            colors = androidx.compose.material3.ButtonDefaults
                .outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                ),
        ) {
            Text(
                text = stringResource(R.string.main_export_technical_report),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        }

        androidx.compose.material3.TextButton(
            onClick = onClear,
            enabled = hasEvents,
            modifier = Modifier.fillMaxWidth(0.72f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.main_clear_list),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasEvents) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
internal fun EventFilterBar(
    selectedFilter: EventFilter,
    onSelected: (EventFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EventFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = {
                    onSelected(filter)
                },
                label = {
                    Text(text = stringResource(filter.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
internal fun EventViewModeBar(
    selectedMode: EventViewMode,
    onSelected: (EventViewMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EventViewMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = {
                    onSelected(mode)
                },
                label = {
                    Text(text = stringResource(mode.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
internal fun FilterEmptyCard(
    monitoring: Boolean,
    filter: EventFilter,
) {
    val title = when (filter) {
        EventFilter.UNKNOWN -> {
            stringResource(R.string.main_empty_unexplained_title)
        }

        EventFilter.BACKGROUND -> {
            stringResource(R.string.main_empty_background_title)
        }

        EventFilter.NOTIFICATIONS -> {
            stringResource(R.string.main_empty_notifications_title)
        }

        EventFilter.DISPLAY -> {
            stringResource(R.string.main_empty_display_title)
        }

        EventFilter.ALL -> {
            if (monitoring) {
                stringResource(R.string.main_empty_all_monitoring_title)
            } else {
                stringResource(R.string.main_empty_all_title)
            }
        }
    }

    val text = when (filter) {
        EventFilter.UNKNOWN -> {
            stringResource(R.string.main_empty_unexplained_text)
        }

        EventFilter.BACKGROUND -> {
            stringResource(R.string.main_empty_background_text)
        }

        EventFilter.NOTIFICATIONS -> {
            stringResource(R.string.main_empty_notifications_text)
        }

        EventFilter.DISPLAY -> {
            stringResource(R.string.main_empty_display_text)
        }

        EventFilter.ALL -> {
            if (monitoring) {
                stringResource(R.string.main_empty_all_monitoring_text)
            } else {
                stringResource(R.string.main_empty_all_text)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(6.dp))

            Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionCardPreview() {
    PreviewSurface {
        ActionCard(hasEvents = true, onExport = {}, onClear = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun EventFilterBarPreview() {
    PreviewSurface {
        EventFilterBar(selectedFilter = EventFilter.BACKGROUND, onSelected = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun EventViewModeBarPreview() {
    PreviewSurface {
        EventViewModeBar(selectedMode = EventViewMode.LIST, onSelected = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun FilterEmptyCardPreview() {
    PreviewSurface {
        FilterEmptyCard(monitoring = true, filter = EventFilter.UNKNOWN)
    }
}
