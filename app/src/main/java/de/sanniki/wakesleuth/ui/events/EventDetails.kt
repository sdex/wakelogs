package de.sanniki.wakesleuth.ui.events

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.domain.ExpertSection
import de.sanniki.wakesleuth.domain.ExpertSnapshotEvent
import de.sanniki.wakesleuth.domain.ExpertSnapshotStatus
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.SnapshotClassification
import de.sanniki.wakesleuth.domain.SnapshotStatus
import de.sanniki.wakesleuth.domain.SystemSnapshot
import de.sanniki.wakesleuth.domain.SystemSnapshotEvent
import de.sanniki.wakesleuth.ui.render.EventTextRenderer
import java.util.Locale

internal fun uiDetailsForEvent(
    context: Context,
    renderer: EventTextRenderer,
    event: RecordedEvent,
): String =
    when (event) {
        is ExpertSnapshotEvent -> {
            expertSnapshotUiDetails(context, renderer, event)
        }

        is SystemSnapshotEvent -> {
            systemSnapshotUiDetails(context, renderer, event.snapshot)
        }

        else -> {
            renderer.details(event)
        }
    }

private fun expertSnapshotUiDetails(
    context: Context,
    renderer: EventTextRenderer,
    event: ExpertSnapshotEvent,
): String {
    val snapshot = event.snapshot

    return buildString {
        appendLine(context.getString(R.string.main_ui_trigger, context.getString(R.string.main_metric_screen_on)))
        appendLine(context.getString(R.string.main_ui_expert_context_summary))

        if (snapshot.status != ExpertSnapshotStatus.OK) {
            appendLine()
            appendLine(
                context.getString(
                    R.string.service_diagnostic_error,
                    snapshot.errorDetail
                        ?: context.getString(R.string.main_unknown_lowercase),
                ),
            )
        }

        ExpertSection.entries.forEach { section ->
            val signals = snapshot.signals.filter { it.section == section }.sortedBy { it.ordinal }

            if (signals.isEmpty()) {
                return@forEach
            }

            appendLine()
            appendLine(
                context.getString(
                    when (section) {
                        ExpertSection.LOCATION -> R.string.main_ui_expert_section_location
                        ExpertSection.SENSORS -> R.string.main_ui_expert_section_sensors
                        ExpertSection.NETWORK -> R.string.main_ui_expert_section_network
                    },
                ),
            )

            signals.forEach { signal -> appendLine("• " + renderer.expertSignalLabel(signal, event.deviceFamily)) }
        }

        appendLine()
        append(context.getString(R.string.main_ui_expert_no_coordinates))
    }.trim()
}

private fun systemSnapshotUiDetails(
    context: Context,
    renderer: EventTextRenderer,
    snapshot: SystemSnapshot,
): String {
    val displayText = when (snapshot.idleScreenOn) {
        true -> context.getString(R.string.main_ui_display_on)
        false -> context.getString(R.string.main_ui_display_off)
        null -> context.getString(R.string.main_unknown_lowercase)
    }

    val idleText = readableIdleStateForUi(context, snapshot)

    val classification = if (snapshot.status == SnapshotStatus.OK) {
        renderer.snapshotClassificationLabel(SnapshotClassification.of(snapshot))
    } else {
        context.getString(R.string.main_ui_no_assessment)
    }

    return buildString {
        appendLine(context.getString(R.string.main_ui_trigger, renderer.snapshotTriggerLabel(snapshot.trigger)))
        appendLine(
            context.getString(
                R.string.main_ui_state,
                snapshot.wakefulness
                    ?: context.getString(R.string.main_unknown_lowercase),
            ),
        )
        appendLine(context.getString(R.string.main_ui_display, displayText))

        if (idleText != null) {
            appendLine(context.getString(R.string.main_ui_idle, idleText))
        }

        appendLine()
        append(classification)
    }.trim()
}

/** Doze state from the raw `mState` / `mLightState` tokens. */
private fun readableIdleStateForUi(
    context: Context,
    snapshot: SystemSnapshot,
): String? {
    val deep = snapshot.deepIdleState?.uppercase(Locale.ROOT)

    val light = snapshot.lightIdleState?.uppercase(Locale.ROOT)

    return when {
        SnapshotClassification.isIdleToken(deep) ||
            snapshot.deviceIdleMode == true -> {
            context.getString(R.string.main_idle_deep_doze)
        }

        deep == "INACTIVE" && light == "INACTIVE" -> {
            context.getString(R.string.main_idle_not_deep_yet)
        }

        light == "ACTIVE" || deep == "ACTIVE" -> {
            context.getString(R.string.main_idle_system_active)
        }

        SnapshotClassification.isIdleToken(light) -> {
            context.getString(R.string.main_idle_light_doze)
        }

        deep == null && light == null -> {
            null
        }

        else -> {
            listOfNotNull(
                snapshot.deepIdleState?.let { "Deep=$it" },
                snapshot.lightIdleState?.let { "Light=$it" },
            ).joinToString(", ")
        }
    }
}
