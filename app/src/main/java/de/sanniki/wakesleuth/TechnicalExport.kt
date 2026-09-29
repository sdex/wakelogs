package de.sanniki.wakesleuth

import android.content.Context
import de.sanniki.wakesleuth.domain.CauseAssessment
import de.sanniki.wakesleuth.domain.CpuWakeupEvent
import de.sanniki.wakesleuth.domain.ExpertSection
import de.sanniki.wakesleuth.domain.ExpertSnapshotEvent
import de.sanniki.wakesleuth.domain.ExpertSnapshotStatus
import de.sanniki.wakesleuth.domain.NotificationEvent
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.SourceKind
import de.sanniki.wakesleuth.domain.SourceRef
import de.sanniki.wakesleuth.domain.primarySource
import de.sanniki.wakesleuth.domain.source
import de.sanniki.wakesleuth.domain.sources
import de.sanniki.wakesleuth.ui.render.EventTextRenderer
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain text report of one session for sharing. Event lines come from
 * the same [EventTextRenderer] as the UI; summary and highlights are
 * computed from the typed facts.
 */
object TechnicalExport {
    private const val SEPARATOR = "--------------------------------------------------"

    private const val FREQUENT_SOURCES = 3

    fun build(
        context: Context,
        events: List<RecordedEvent>,
        monitoring: Boolean,
    ): String {
        val renderer = EventTextRenderer(context)
        val labels = SourceLabelResolver.get(context)

        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

        val facts = ExportFacts.of(events)

        return buildString {
            appendLine("wakelogs v${BuildConfig.VERSION_NAME} · dernikiausd")
            appendLine(context.getString(R.string.event_export_title))
            appendLine()
            appendLine(context.getString(R.string.event_export_created, formatter.format(Date())))
            appendLine(
                context.getString(
                    R.string.event_export_monitoring,
                    if (monitoring) {
                        context.getString(R.string.event_export_monitoring_active)
                    } else {
                        context.getString(R.string.event_export_monitoring_stopped)
                    },
                ),
            )
            appendLine(context.getString(R.string.event_export_stored_events, events.size))

            appendLine()
            appendLine(context.getString(R.string.event_export_summary_heading))
            summary(context, renderer, labels, facts).forEach { appendLine(it) }

            appendLine()
            appendLine(context.getString(R.string.event_export_highlights_heading))
            highlights(context, renderer, labels, facts).forEach { appendLine("• $it") }

            appendParserDiagnostics(context, formatter)

            appendLine()
            appendLine(SEPARATOR)

            if (events.isEmpty()) {
                appendLine(context.getString(R.string.event_export_no_events))
            } else {
                events.forEach { event ->
                    appendLine(formatter.format(Date(event.occurredAt)))

                    val typeLabel = EventTextRenderer.typeLabel(event.type)?.let(context::getString)
                        ?: event.type.name

                    appendLine("$typeLabel – " + renderer.title(event))

                    val details = exportDetails(context, renderer, event)

                    if (details.isNotBlank()) {
                        appendLine(details)
                    }

                    appendLine(SEPARATOR)
                }
            }
        }
    }

    /** Aggregates the report needs, computed once from the events. */
    private class ExportFacts(
        val screenOns: List<ScreenOnEvent>,
        val cpuWakeups: List<CpuWakeupEvent>,
        val notificationCount: Int,
        val frequentSources: List<Pair<SourceRef, Int>>,
    ) {
        val powerButtonWakeups: Int = screenOns.count(CauseAssessment::isPowerButton)

        val longestCpuWakeup: CpuWakeupEvent? =
            cpuWakeups.filter { it.awakeMs != null }.maxByOrNull { it.awakeMs ?: 0L }

        val unattributedCpuWakeups: Int = cpuWakeups.count { it.primaryEvidence == null }

        val lockGlowWakeups: Int = screenOns.count { screenOn ->
            screenOn.wakeLockHints.any { it.tag.contains(LOCK_GLOW, ignoreCase = true) } ||
                screenOn.wakeReason?.rawTag?.contains(LOCK_GLOW, ignoreCase = true) == true
        }

        companion object {
            private const val LOCK_GLOW = "LockGlow"

            fun of(events: List<RecordedEvent>): ExportFacts {
                val cpuWakeups = events.filterIsInstance<CpuWakeupEvent>()
                val screenOns = events.filterIsInstance<ScreenOnEvent>()

                // Every source an event is linked to counts once for it.
                val perEvent = cpuWakeups.map { event ->
                    (listOfNotNull(event.primarySource()) + event.evidence.map { it.source() })
                } +
                    screenOns.map { event -> event.sources().map { it.source } }

                val frequent = perEvent
                    .flatMap { sources -> sources.distinctBy { it.groupKey } }
                    .groupBy { it.groupKey }
                    .map { (_, sources) -> sources.first() to sources.size }
                    .sortedWith(
                        compareByDescending<Pair<SourceRef, Int>> { it.second }.thenBy { it.first.groupKey },
                    ).take(FREQUENT_SOURCES)

                return ExportFacts(
                    screenOns = screenOns,
                    cpuWakeups = cpuWakeups,
                    notificationCount = events.count { it is NotificationEvent },
                    frequentSources = frequent,
                )
            }
        }
    }

    private fun longestCpuWakeupText(
        context: Context,
        renderer: EventTextRenderer,
        event: CpuWakeupEvent,
    ): String =
        context.getString(
            R.string.event_highlight_longest_cpu_wakeup,
            renderer.cpuSourceLabel(event) ?: context.getString(R.string.event_unknown_source),
            exportDuration(event.awakeMs ?: 0L),
        )

    private fun highlights(
        context: Context,
        renderer: EventTextRenderer,
        labels: SourceLabelResolver,
        facts: ExportFacts,
    ): List<String> {
        val highlights = mutableListOf<String>()

        facts.longestCpuWakeup?.let { highlights += longestCpuWakeupText(context, renderer, it) }

        if (facts.frequentSources.isNotEmpty()) {
            highlights += context.getString(
                R.string.event_highlight_frequent_sources,
                facts.frequentSources.joinToString(", ") { (source, count) ->
                    "${labels.label(source)} ($count×)"
                },
            )
        }

        val screenOnCount = facts.screenOns.size

        if (screenOnCount > 0) {
            highlights += when {
                facts.powerButtonWakeups == screenOnCount -> {
                    context.getString(R.string.event_highlight_screen_wakeups_all_power_button, screenOnCount)
                }

                facts.powerButtonWakeups > 0 -> {
                    context.getString(
                        R.string.event_highlight_screen_wakeups_some_power_button,
                        facts.powerButtonWakeups,
                        screenOnCount,
                    )
                }

                else -> {
                    context.getString(R.string.event_highlight_screen_wakeups_no_power_button, screenOnCount)
                }
            }
        }

        if (facts.notificationCount > 0) {
            highlights += context.getString(R.string.event_highlight_notifications, facts.notificationCount)
        }

        if (facts.unattributedCpuWakeups > 0) {
            highlights +=
                context.getString(R.string.event_highlight_unattributed_cpu_wakeups, facts.unattributedCpuWakeups)
        }

        if (highlights.isEmpty()) {
            highlights += context.getString(R.string.event_no_special_patterns)
        }

        return highlights
    }

    private fun summary(
        context: Context,
        renderer: EventTextRenderer,
        labels: SourceLabelResolver,
        facts: ExportFacts,
    ): List<String> {
        val summary = mutableListOf<String>()
        val screenOnCount = facts.screenOns.size

        summary += context.getString(
            when {
                screenOnCount == 0 -> R.string.event_summary_no_screen_wakeups
                facts.powerButtonWakeups == screenOnCount -> R.string.event_summary_not_woken_by_apps
                facts.powerButtonWakeups > 0 -> R.string.event_summary_some_power_button
                else -> R.string.event_summary_no_clear_power_button
            },
        )

        if (screenOnCount > 0 && facts.powerButtonWakeups == screenOnCount) {
            summary += context.getString(R.string.event_summary_all_power_button)
        }

        when {
            facts.frequentSources.any { it.first.kind == SourceKind.RADIO_NETWORK } -> {
                summary += context.getString(R.string.event_summary_much_radio_network)
            }

            facts.frequentSources.isNotEmpty() -> {
                summary += context.getString(R.string.event_summary_recurring_technical)
            }
        }

        facts.longestCpuWakeup?.let { event ->
            summary += context.getString(
                R.string.event_summary_most_notable_cpu_wakeup,
                (renderer.cpuSourceLabel(event) ?: context.getString(R.string.event_unknown_source)) +
                    " · " + exportDuration(event.awakeMs ?: 0L),
            )
        }

        if (facts.lockGlowWakeups > 0) {
            summary += context.resources.getQuantityString(
                R.plurals.event_summary_lockglow_wakeups,
                facts.lockGlowWakeups,
                facts.lockGlowWakeups,
            )
        }

        if (facts.notificationCount > 0) {
            summary += context.getString(R.string.event_summary_notifications_not_main_trigger)
        }

        return summary
    }

    private fun StringBuilder.appendParserDiagnostics(
        context: Context,
        formatter: SimpleDateFormat,
    ) {
        val diagnostics = BackgroundWakeMonitor.diagnostics(context)

        appendLine()
        appendLine(context.getString(R.string.event_export_parser_diagnostics_heading))
        appendLine(context.getString(R.string.event_export_batterystats_lines, diagnostics.parsedLines))
        appendLine(context.getString(R.string.event_export_wake_reasons, diagnostics.wakeReasons))
        appendLine(context.getString(R.string.event_export_cpu_starts, diagnostics.runningStarts))
        appendLine(context.getString(R.string.event_export_wakelocks, diagnostics.wakeLocks))
        appendLine(context.getString(R.string.event_export_jobs, diagnostics.jobs))
        appendLine(context.getString(R.string.event_export_syncs, diagnostics.syncs))
        appendLine(context.getString(R.string.event_export_candidates_last_poll, diagnostics.candidates))
        appendLine(context.getString(R.string.event_export_cpu_wakeups_last_poll, diagnostics.eventsCreated))

        if (diagnostics.lastPollMillis > 0L) {
            appendLine(
                context.getString(
                    R.string.event_export_last_parser_run,
                    formatter.format(Date(diagnostics.lastPollMillis)),
                ),
            )
        }

        val rawLines = BackgroundWakeMonitor.rawDiagnosticLines(context)

        appendLine()
        appendLine(context.getString(R.string.event_export_raw_data_heading))

        val parserFoundFreshRawData = diagnostics.parsedLines > 0 ||
            diagnostics.wakeReasons > 0 ||
            diagnostics.runningStarts > 0 ||
            diagnostics.wakeLocks > 0 ||
            diagnostics.jobs > 0 || diagnostics.syncs > 0 || diagnostics.candidates > 0 || diagnostics.eventsCreated > 0

        when {
            !parserFoundFreshRawData -> {
                appendLine(context.getString(R.string.event_export_no_new_raw_data))
            }

            rawLines.isEmpty() -> {
                appendLine(context.getString(R.string.event_export_no_raw_lines))
            }

            else -> {
                rawLines.forEach { appendLine(it) }
            }
        }
    }

    /** Expert snapshots are condensed to three lines in the export. */
    private fun exportDetails(
        context: Context,
        renderer: EventTextRenderer,
        event: RecordedEvent,
    ): String {
        if (event !is ExpertSnapshotEvent || event.snapshot.status != ExpertSnapshotStatus.OK) {
            return renderer.details(event)
        }

        fun section(section: ExpertSection): String =
            event.snapshot.signals
                .filter { it.section == section }
                .sortedBy { it.ordinal }
                .take(3)
                .joinToString(", ") { renderer.expertSignalLabel(it, event.deviceFamily) }
                .ifEmpty { context.getString(R.string.event_expert_no_notable_hints) }

        return buildString {
            appendLine(
                context.getString(
                    R.string.service_snapshot_trigger,
                    context.getString(R.string.service_snapshot_reason_screen_on),
                ),
            )
            appendLine(context.getString(R.string.service_snapshot_source_compact))
            appendLine()
            appendLine(context.getString(R.string.event_expert_context_heading))
            appendLine(context.getString(R.string.event_expert_location_line, section(ExpertSection.LOCATION)))
            appendLine(context.getString(R.string.event_expert_sensors_line, section(ExpertSection.SENSORS)))
            appendLine(context.getString(R.string.event_expert_network_line, section(ExpertSection.NETWORK)))
            appendLine()
            append(context.getString(R.string.event_expert_export_note))
        }
    }

    private fun exportDuration(milliseconds: Long): String =
        if (milliseconds >= 1000L) {
            String.format(Locale.getDefault(), "%.1f s", milliseconds / 1000.0)
        } else {
            "$milliseconds ms"
        }
}
