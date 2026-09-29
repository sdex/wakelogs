package de.sanniki.wakesleuth.ui.events

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sanniki.wakesleuth.DetailLevel
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.domain.EventType
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.ui.common.PreviewSamples
import de.sanniki.wakesleuth.ui.common.PreviewSurface
import de.sanniki.wakesleuth.ui.common.formatTimestamp
import de.sanniki.wakesleuth.ui.events.CausalChainView
import de.sanniki.wakesleuth.ui.events.CauseAssessmentCard
import de.sanniki.wakesleuth.ui.events.buildCausalChain
import de.sanniki.wakesleuth.ui.events.causeAssessmentFor
import de.sanniki.wakesleuth.ui.events.uiDetailsForEvent
import de.sanniki.wakesleuth.ui.render.rememberEventTextRenderer

@Composable
internal fun EventCard(
    event: RecordedEvent,
    detailLevel: DetailLevel,
) {
    val context = LocalContext.current

    val renderer = rememberEventTextRenderer()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = eventTypeLabel(context, event.type),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = formatTimestamp(event.occurredAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = renderer.title(event),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            if (event is ScreenOnEvent) {
                Spacer(modifier = Modifier.height(7.dp))

                CauseAssessmentCard(assessment = causeAssessmentFor(event))

                val causalChain = remember(event, renderer) { buildCausalChain(context, renderer, event) }

                if (causalChain.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    CausalChainView(steps = causalChain)
                }
            }

            if (
                detailLevel != DetailLevel.SIMPLE
            ) {
                val details = remember(event, renderer) { uiDetailsForEvent(context, renderer, event) }

                if (details.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = details,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

private fun eventTypeLabel(
    context: Context,
    type: EventType,
): String =
    when (type) {
        EventType.SCREEN_ON -> context.getString(R.string.main_event_type_screen_on)

        EventType.SCREEN_OFF -> context.getString(R.string.main_event_type_screen_off)

        EventType.CPU_WAKEUP -> context.getString(R.string.main_event_type_background)

        EventType.SYSTEM_SNAPSHOT -> context.getString(R.string.main_event_type_system)

        EventType.EXPERT_SNAPSHOT -> context.getString(R.string.main_event_type_expert)

        EventType.NOTIFICATION -> context.getString(R.string.main_event_type_notification)

        EventType.MONITOR_START,
        EventType.MONITOR_STOP,
        -> context.getString(R.string.main_event_type_monitor)

        EventType.POWER_CONNECTED,
        EventType.POWER_DISCONNECTED,
        EventType.USB_ATTACHED,
        EventType.USB_DETACHED,
        EventType.NETWORK_SESSION,
        -> type.name.replace("_", " ")
    }

@Preview(showBackground = true)
@Composable
private fun ScreenOnEventCardPreview() {
    PreviewSurface {
        EventCard(event = PreviewSamples.screenOnEvent, detailLevel = DetailLevel.NORMAL)
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationEventCardPreview() {
    PreviewSurface {
        EventCard(event = PreviewSamples.notificationEvent, detailLevel = DetailLevel.NORMAL)
    }
}

@Preview(showBackground = true)
@Composable
private fun CpuWakeupEventCardPreview() {
    PreviewSurface {
        EventCard(event = PreviewSamples.cpuWakeupEvents.first(), detailLevel = DetailLevel.EXPERT)
    }
}
