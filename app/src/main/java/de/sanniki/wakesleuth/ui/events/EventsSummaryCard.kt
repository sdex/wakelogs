package de.sanniki.wakesleuth.ui.events

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ui.common.PreviewSurface

@Composable
internal fun EventsSummaryCard(
    totalCount: Int,
    visibleCount: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.main_events_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (totalCount == visibleCount) {
                    stringResource(R.string.main_events_saved_count, totalCount)
                } else {
                    stringResource(
                        R.string.main_events_visible_count,
                        visibleCount,
                        totalCount,
                    )
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    onToggleExpanded()
                },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    androidx.compose.ui.graphics
                        .Color(0xFF687181),
                ),
                colors = androidx.compose.material3.ButtonDefaults
                    .outlinedButtonColors(
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
            ) {
                Text(
                    if (expanded) {
                        stringResource(R.string.main_events_hide)
                    } else {
                        stringResource(R.string.main_events_show)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EventsSummaryCardCollapsedPreview() {
    PreviewSurface {
        EventsSummaryCard(totalCount = 42, visibleCount = 42, expanded = false, onToggleExpanded = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun EventsSummaryCardFilteredPreview() {
    PreviewSurface {
        EventsSummaryCard(totalCount = 42, visibleCount = 7, expanded = true, onToggleExpanded = {})
    }
}
