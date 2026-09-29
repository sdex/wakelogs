package de.sanniki.wakesleuth.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceClassifierTokenTest {
    private fun kind(raw: String) = SourceClassifier.classifyRaw(raw).kind

    @Test
    fun `radio tokens match as whole words or prefixes`() {
        assertEquals(SourceKind.RADIO_NETWORK, kind("rmnet_data0"))
        assertEquals(SourceKind.RADIO_NETWORK, kind("RMNET0"))
        assertEquals(SourceKind.RADIO_NETWORK, kind("cellular"))
        assertEquals(SourceKind.RADIO_NETWORK, kind("radio-wakelock"))
    }

    @Test
    fun `substrings inside other words are not radio`() {
        assertEquals(SourceKind.UNKNOWN_SYSTEM, kind("radioactive"))
        assertEquals(SourceKind.UNKNOWN_SYSTEM, kind("cellularity"))
        assertEquals(SourceKind.UNKNOWN_SYSTEM, kind("myrmnet"))
    }

    @Test
    fun `vendor tokens are not shadowed by radio`() {
        assertEquals(SourceKind.ONEPLUS_SYSTEM_SERVICE, kind("oplus_radio_helper"))
        assertEquals(SourceKind.ONEPLUS_SYSTEM_SERVICE, kind("athena_cellular"))
        assertEquals(SourceKind.SAMSUNG_SYSTEM_SERVICE, kind("com.samsung.radio.service"))
    }
}
