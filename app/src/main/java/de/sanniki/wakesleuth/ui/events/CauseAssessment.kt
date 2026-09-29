package de.sanniki.wakesleuth.ui.events

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.domain.CauseAssessment
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import de.sanniki.wakesleuth.domain.ScreenOnVerdict
import de.sanniki.wakesleuth.ui.common.PreviewSurface

internal enum class CauseConfidence(
    @StringRes val label: Int,
) {
    CONFIRMED(R.string.main_confidence_confirmed),
    PROBABLE(R.string.main_confidence_probable),
    POSSIBLE(R.string.main_confidence_possible),
    COMPANION(R.string.main_confidence_companion),
    UNRESOLVED(R.string.main_confidence_unresolved),
}

internal data class CauseAssessmentUi(
    val confidence: CauseConfidence,
    @StringRes val explanation: Int,
)

@Composable
internal fun CauseAssessmentCard(assessment: CauseAssessmentUi) {
    val containerColor = when (assessment.confidence) {
        CauseConfidence.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        CauseConfidence.PROBABLE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
        CauseConfidence.POSSIBLE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        CauseConfidence.COMPANION -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        CauseConfidence.UNRESOLVED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
    }

    val titleColor = when (assessment.confidence) {
        CauseConfidence.CONFIRMED,
        CauseConfidence.PROBABLE,
        -> MaterialTheme.colorScheme.primary

        CauseConfidence.POSSIBLE,
        CauseConfidence.COMPANION,
        -> MaterialTheme.colorScheme.onSurface

        CauseConfidence.UNRESOLVED -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Text(
                text = stringResource(assessment.confidence.label),
                color = titleColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = stringResource(assessment.explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp,
            )
        }
    }
}

/**
 * Detail text of an event card. Snapshots get a compact summary; every
 * other event shows the rendered detail lines.
 */

internal fun causeAssessmentFor(event: ScreenOnEvent): CauseAssessmentUi =
    when (CauseAssessment.verdictOf(event)) {
        ScreenOnVerdict.CONFIRMED -> {
            CauseAssessmentUi(confidence = CauseConfidence.CONFIRMED, explanation = R.string.main_assessment_confirmed)
        }

        ScreenOnVerdict.PROBABLE_NOTIFICATION -> {
            CauseAssessmentUi(confidence = CauseConfidence.PROBABLE, explanation = R.string.main_assessment_probable)
        }

        ScreenOnVerdict.POSSIBLE_NOTIFICATION -> {
            CauseAssessmentUi(
                confidence = CauseConfidence.POSSIBLE,
                explanation = R.string.main_assessment_possible_notification,
            )
        }

        ScreenOnVerdict.POSSIBLE_WAKEUP_ALARM -> {
            CauseAssessmentUi(
                confidence = CauseConfidence.POSSIBLE,
                explanation = R.string.main_assessment_possible_wakeup_alarm,
            )
        }

        ScreenOnVerdict.POSSIBLE_WAKELOCK -> {
            CauseAssessmentUi(
                confidence = CauseConfidence.POSSIBLE,
                explanation = R.string.main_assessment_possible_wakelock,
            )
        }

        ScreenOnVerdict.COMPANION -> {
            CauseAssessmentUi(confidence = CauseConfidence.COMPANION, explanation = R.string.main_assessment_companion)
        }

        ScreenOnVerdict.UNRESOLVED -> {
            CauseAssessmentUi(confidence = CauseConfidence.UNRESOLVED, explanation = R.string.main_assessment_unresolved)
        }
    }

@Preview(showBackground = true)
@Composable
private fun CauseAssessmentCardConfirmedPreview() {
    PreviewSurface {
        CauseAssessmentCard(CauseAssessmentUi(CauseConfidence.CONFIRMED, R.string.main_assessment_confirmed))
    }
}

@Preview(showBackground = true)
@Composable
private fun CauseAssessmentCardProbablePreview() {
    PreviewSurface {
        CauseAssessmentCard(CauseAssessmentUi(CauseConfidence.PROBABLE, R.string.main_assessment_probable))
    }
}
