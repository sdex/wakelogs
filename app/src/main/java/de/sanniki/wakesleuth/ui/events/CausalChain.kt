package de.sanniki.wakesleuth.ui.events

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.domain.CauseAssessment
import de.sanniki.wakesleuth.domain.HintRelation
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.WakeLockTags
import de.sanniki.wakesleuth.domain.source
import de.sanniki.wakesleuth.ui.common.PreviewSurface
import de.sanniki.wakesleuth.ui.render.EventTextRenderer
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver

internal data class CausalChainStep(
    val offsetMillis: Long,
    val timingText: String,
    val title: String,
    val source: String,
    val companionActivity: Boolean = false,
)

@Composable
internal fun CausalChainView(steps: List<CausalChainStep>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.main_chain_title),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            val causeSteps = steps.filterNot { it.companionActivity }

            val companionSteps = steps.filter { it.companionActivity }

            causeSteps.forEachIndexed {
                index,
                step,
                ->

                Text(
                    text = step.timingText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(text = step.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)

                if (step.source.isNotBlank()) {
                    Text(
                        text = step.source,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (index < causeSteps.lastIndex) {
                    Text(
                        text = "↓",
                        modifier = Modifier.padding(vertical = 3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (companionSteps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.main_chain_companion_activities),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                companionSteps.forEach { step ->
                    Text(
                        text = step.timingText,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (step.source.isNotBlank()) {
                        Text(
                            text = step.source,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * Steps that led to a screen-on, in time order: the direct wake reason,
 * the notification cause and every hint, each with its exact offset.
 * Hints that only accompanied the wake-up are listed separately.
 */

internal fun buildCausalChain(
    context: Context,
    renderer: EventTextRenderer,
    event: ScreenOnEvent,
): List<CausalChainStep> {
    val steps = mutableListOf<CausalChainStep>()

    event.wakeReason?.let { wakeReason ->
        steps.add(
            CausalChainStep(
                offsetMillis = wakeReason.offsetMs,
                timingText = renderer.signedSeconds(wakeReason.offsetMs),
                title = context.getString(R.string.main_chain_trigger),
                source = renderer.wakeReasonLabel(wakeReason),
            ),
        )
    }

    event.notificationCause?.let { cause ->
        steps.add(
            CausalChainStep(
                offsetMillis = cause.offsetMs,
                timingText = renderer.signedSeconds(cause.offsetMs),
                title = context.getString(R.string.main_chain_notification),
                source = SourceLabelResolver.get(context).appName(cause.packageName),
            ),
        )
    }

    event.wakeLockHints.forEach { hint ->
        steps.add(
            CausalChainStep(
                offsetMillis = hint.offsetMs,
                timingText = renderer.signedSeconds(hint.offsetMs),
                title = renderer.wakeLockKindLabel(WakeLockTags.kindOf(hint.tag)),
                source = renderer.sourceLabel(hint.packageName),
                companionActivity = isChainCompanion(CauseAssessment.relationOf(event, hint), hint.offsetMs),
            ),
        )
    }

    event.alarmHints.forEach { hint ->
        steps.add(
            CausalChainStep(
                offsetMillis = hint.offsetMs,
                timingText = renderer.signedSeconds(hint.offsetMs),
                title = context.getString(R.string.main_chain_wakeup_alarm),
                source = renderer.sourceLabel(hint.packageName),
                companionActivity = isChainCompanion(CauseAssessment.relationOf(event, hint), hint.offsetMs),
            ),
        )
    }

    event.jobHints.forEach { hint ->
        steps.add(
            CausalChainStep(
                offsetMillis = hint.offsetMs,
                timingText = renderer.signedSeconds(hint.offsetMs),
                title = context.getString(R.string.main_kind_background_job),
                source = renderer.sourceLabel(hint.packageName),
                companionActivity = isChainCompanion(CauseAssessment.relationOf(event, hint), hint.offsetMs),
            ),
        )
    }

    if (steps.isEmpty()) {
        return emptyList()
    }

    steps.add(
        CausalChainStep(
            offsetMillis = 0L,
            timingText = renderer.signedSeconds(0L),
            title = context.getString(R.string.main_chain_screen_turned_on),
            source = "",
            companionActivity = false,
        ),
    )

    return steps
        .distinctBy {
            listOf(it.offsetMillis, it.title, it.source)
        }.sortedWith(
            compareBy<CausalChainStep> {
                it.companionActivity
            }.thenBy {
                it.offsetMillis
            }.thenBy {
                it.title
            },
        )
}

/**
 * Accompanying activity that happened before the screen-on still belongs
 * into the chain; only simultaneous or later activity is listed apart.
 */

private fun isChainCompanion(
    relation: HintRelation,
    offsetMillis: Long,
): Boolean = relation == HintRelation.COMPANION && offsetMillis >= 0L

@Preview(showBackground = true)
@Composable
private fun CausalChainViewPreview() {
    PreviewSurface {
        CausalChainView(
            steps = listOf(
                CausalChainStep(offsetMillis = -8_000L, timingText = "-8 s", title = "Alarm fired", source = "Messenger"),
                CausalChainStep(offsetMillis = -2_000L, timingText = "-2 s", title = "Notification posted", source = "Messenger"),
                CausalChainStep(
                    offsetMillis = 0L,
                    timingText = "0 s",
                    title = "Screen on",
                    source = "Display",
                    companionActivity = true,
                ),
            ),
        )
    }
}
