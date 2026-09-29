package de.sanniki.wakesleuth.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattingUnitsTest {
    @Test
    fun `byte sizes use binary unit names`() {
        assertEquals("0 B", formatBinaryBytes(-5, Locale.US))
        assertEquals("1023 B", formatBinaryBytes(1023, Locale.US))
        assertEquals("1.0 KiB", formatBinaryBytes(1024, Locale.US))
        assertEquals("1.5 MiB", formatBinaryBytes(1_572_864, Locale.US))
        assertEquals("2.0 GiB", formatBinaryBytes(2L * 1_073_741_824L, Locale.US))
    }

    @Test
    fun `comparison duration covers seconds to days`() {
        assertEquals("0 s", formatComparisonDuration(-1_000))
        assertEquals("59 s", formatComparisonDuration(59_999))
        assertEquals("1:05 min", formatComparisonDuration(65_000))
        assertEquals("1 h 1 min", formatComparisonDuration(3_660_000))
        assertEquals("1 d 2 h", formatComparisonDuration(26L * 3_600_000L))
    }
}
