package de.sanniki.wakesleuth.ui.main

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import de.sanniki.wakesleuth.GroupedCpuEventListItem
import de.sanniki.wakesleuth.SingleEventListItem
import de.sanniki.wakesleuth.SleepReportCard
import de.sanniki.wakesleuth.SourceStatisticsCard
import de.sanniki.wakesleuth.WakeTimeline
import de.sanniki.wakesleuth.ui.events.ActionCard
import de.sanniki.wakesleuth.ui.events.EventCard
import de.sanniki.wakesleuth.ui.events.EventFilterBar
import de.sanniki.wakesleuth.ui.events.EventViewMode
import de.sanniki.wakesleuth.ui.events.EventViewModeBar
import de.sanniki.wakesleuth.ui.events.EventsSummaryCard
import de.sanniki.wakesleuth.ui.events.FilterEmptyCard
import de.sanniki.wakesleuth.ui.events.GroupedCpuEventCard

internal fun LazyListScope.analysisSection(
    state: WakeSleuthState,
    onIntent: (WakeSleuthIntent) -> Unit,
) {
    val settings = state.uiSettings
    val events = state.events

    if (settings.showNightAnalysis) {
        item {
            SleepReportCard(
                events = events.all,
                session = state.latestSession,
                monitoring = state.monitor.monitoring,
                detailLevel = settings.detailLevel,
            )
        }
    }

    if (settings.showSourceStatistics && events.all.isNotEmpty()) {
        item {
            SourceStatisticsCard(
                events = events.all,
                session = state.latestSession,
                detailLevel = settings.detailLevel,
            )
        }
    }

    if (events.all.isEmpty()) {
        return
    }

    item {
        EventsSummaryCard(
            totalCount = events.all.size,
            visibleCount = events.filtered.size,
            expanded = events.expanded,
            onToggleExpanded = { onIntent(WakeSleuthIntent.ToggleEventsClicked) },
        )
    }

    if (!events.expanded) {
        return
    }

    item {
        ActionCard(
            hasEvents = true,
            onExport = { onIntent(WakeSleuthIntent.ExportClicked) },
            onClear = { onIntent(WakeSleuthIntent.ClearEventsClicked) },
        )
    }

    item {
        EventFilterBar(
            selectedFilter = events.filter,
            onSelected = { onIntent(WakeSleuthIntent.EventFilterSelected(it)) },
        )
    }

    item {
        EventViewModeBar(
            selectedMode = events.viewMode,
            onSelected = { onIntent(WakeSleuthIntent.EventViewModeSelected(it)) },
        )
    }

    if (events.filtered.isEmpty()) {
        item { FilterEmptyCard(monitoring = state.monitor.monitoring, filter = events.filter) }

        return
    }

    when (events.viewMode) {
        EventViewMode.LIST -> {
            items(items = events.listItems, key = { it.stableKey }) { item ->
                when (item) {
                    is SingleEventListItem -> EventCard(event = item.event, detailLevel = settings.detailLevel)
                    is GroupedCpuEventListItem -> GroupedCpuEventCard(group = item, detailLevel = settings.detailLevel)
                }
            }
        }

        EventViewMode.TIMELINE -> {
            item { WakeTimeline(events = events.filtered, detailLevel = settings.detailLevel) }
        }
    }
}
