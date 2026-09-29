package de.sanniki.wakesleuth.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationConfidenceTest {
    private fun cause(offset: Long) = NotificationCause(1, "com.chat", offset, null)

    @Test
    fun `notification after the screen-on is never high confidence`() {
        assertEquals(CauseConfidenceLevel.MEDIUM, CauseAssessment.confidenceOf(cause(1)))
        assertEquals(CauseConfidenceLevel.MEDIUM, CauseAssessment.confidenceOf(cause(3_000)))
    }

    @Test
    fun `notification shortly before the screen-on is high confidence`() {
        assertEquals(CauseConfidenceLevel.HIGH, CauseAssessment.confidenceOf(cause(0)))
        assertEquals(CauseConfidenceLevel.HIGH, CauseAssessment.confidenceOf(cause(-3_000)))
        assertEquals(CauseConfidenceLevel.MEDIUM, CauseAssessment.confidenceOf(cause(-3_001)))
    }
}
