package de.sanniki.wakesleuth.diagnostics

import de.sanniki.wakesleuth.DeviceFamily
import de.sanniki.wakesleuth.RawWakeLockHistoryLine
import de.sanniki.wakesleuth.ShizukuDiagnostics
import de.sanniki.wakesleuth.buildExpertSnapshot
import de.sanniki.wakesleuth.domain.ExpertSignal
import de.sanniki.wakesleuth.domain.ExpertSnapshotStatus
import de.sanniki.wakesleuth.pairWakeLockHistory
import de.sanniki.wakesleuth.parseActiveWakeLockTags
import de.sanniki.wakesleuth.parseLogTimestampMillis
import de.sanniki.wakesleuth.resolveUidPackages
import de.sanniki.wakesleuth.ui.diagnostics.formatWakeLockTimestampOrRaw
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ShizukuDiagnosticsParsingTest {
    private val originalZone = TimeZone.getDefault()

    @Before
    fun useUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
    ): Long =
        Calendar
            .getInstance()
            .apply {
                clear()
                set(year, month - 1, day, hour, 0, 0)
            }.timeInMillis

    private fun line(
        action: String,
        at: Long,
        tag: String = "tag",
        uid: Int = 10001,
        pkg: String = "com.example",
    ) = RawWakeLockHistoryLine(
        timestamp = "raw-$at",
        timestampMillis = at,
        packageName = pkg,
        action = action,
        tag = tag,
        wakeLockType = "partial",
        causesWake = false,
        uid = uid,
    )

    // ---- timestamps ----

    @Test
    fun `december entry in january resolves to previous year`() {
        val now = millis(2025, 1, 2)

        assertEquals(millis(2024, 12, 31, 23), parseLogTimestampMillis("12-31 23:00:00.000", now))
    }

    @Test
    fun `feb 29 resolves to the last leap year`() {
        val now = millis(2025, 3, 10)

        assertEquals(millis(2024, 2, 29, 12), parseLogTimestampMillis("02-29 12:00:00.000", now))
    }

    @Test
    fun `garbage timestamp is null`() {
        assertNull(parseLogTimestampMillis("not a time", millis(2025, 3, 10)))
        assertNull(parseLogTimestampMillis(null, millis(2025, 3, 10)))
    }

    @Test
    fun `formatted timestamp uses the inferred year`() {
        val now = millis(2025, 1, 2)

        assertEquals(
            "31.12.2024 · 23:00:00",
            formatWakeLockTimestampOrRaw("12-31 23:00:00.000", now, Locale.US),
        )
    }

    @Test
    fun `formatted timestamp handles feb 29`() {
        val now = millis(2025, 3, 10)

        assertEquals(
            "29.02.2024 · 12:00:00",
            formatWakeLockTimestampOrRaw("02-29 12:00:00.000", now, Locale.US),
        )
    }

    @Test
    fun `unparseable timestamp is returned as is`() {
        assertEquals("oops", formatWakeLockTimestampOrRaw("oops", millis(2025, 3, 10), Locale.US))
    }

    // ---- wakelock history ----

    @Test
    fun `identical acquires are both kept`() {
        val entries = pairWakeLockHistory(
            lines = listOf(line("ACQ", 1_000L), line("ACQ", 1_000L), line("REL", 2_000L), line("REL", 3_000L)),
            activeTags = emptySet(),
        )

        assertEquals(2, entries.size)
        assertEquals(setOf(1_000L, 2_000L), entries.mapNotNull { it.durationMillis }.toSet())
    }

    @Test
    fun `release of another uid does not close the acquire`() {
        val entries = pairWakeLockHistory(
            lines = listOf(line("ACQ", 1_000L, uid = 10001), line("REL", 2_000L, uid = 10002)),
            activeTags = emptySet(),
        )

        assertEquals(1, entries.size)
        assertNull(entries.single().endTimestampMillis)
    }

    @Test
    fun `unmatched acquire is active only when the dump lists the lock`() {
        val lines = listOf(line("ACQ", 1_000L, tag = "Sync"))

        val corroborated = pairWakeLockHistory(lines, activeTags = setOf("sync")).single()

        assertTrue(corroborated.stillActive)
        assertFalse(corroborated.endUnknown)

        val rotated = pairWakeLockHistory(lines, activeTags = emptySet()).single()

        assertFalse(rotated.stillActive)
        assertTrue(rotated.endUnknown)
    }

    @Test
    fun `active tags come from the wake locks block only`() {
        val dump =
            """
            Wake Locks: size=2
              PARTIAL_WAKE_LOCK              'Sync Adapter' ACQ=-5m (uid=10001 pid=1 ws=null)
              PARTIAL_WAKE_LOCK              'Other' ACQ=-1s (uid=10002 pid=2 ws=null)

            Partial Wakelock Log:
              'NotActive' ACQ=-9m
            """.trimIndent().lines()

        assertEquals(setOf("sync adapter", "other"), parseActiveWakeLockTags(dump))
    }

    // ---- shared uid ----

    @Test
    fun `shared uid lists all visible packages`() {
        val result = resolveUidPackages(
            packages = listOf("com.b", "com.a", "com.b", "com.android.shell"),
            ignoredPackages = setOf("com.android.shell"),
        )

        assertEquals("com.b", result.primary)
        assertEquals(listOf("com.a", "com.b"), result.sharedPackages)
    }

    @Test
    fun `single package uid is not shared`() {
        val result = resolveUidPackages(listOf("com.a"), emptySet())

        assertEquals("com.a", result.primary)
        assertTrue(result.sharedPackages.isEmpty())
    }

    @Test
    fun `netstats command does not truncate to a few hundred lines`() {
        assertFalse(ShizukuDiagnostics.NETSTATS_COMMAND.contains("head -n 260"))
        assertTrue(ShizukuDiagnostics.NETSTATS_COMMAND.contains("head -n 5000"))
    }

    // ---- expert snapshot ----

    @Test
    fun `all dumps missing is an error`() {
        val snapshot = buildExpertSnapshot(DeviceFamily.GENERIC_ANDROID, null, null, null)

        assertEquals(ExpertSnapshotStatus.ERROR, snapshot.status)
        assertNotNull(snapshot.errorDetail)
        assertTrue(snapshot.signals.isEmpty())
    }

    @Test
    fun `partial dumps still produce an ok snapshot`() {
        val snapshot = buildExpertSnapshot(DeviceFamily.GENERIC_ANDROID, "fused_location_provider: on", null, null)

        assertEquals(ExpertSnapshotStatus.OK, snapshot.status)
        assertTrue(snapshot.locationAvailable)
        assertFalse(snapshot.sensorsAvailable)
        assertTrue(ExpertSignal.FUSED_LOCATION in snapshot.signals)
    }

    @Test
    fun `generic words do not raise signals`() {
        val snapshot = buildExpertSnapshot(
            deviceFamily = DeviceFamily.GENERIC_ANDROID,
            locationRaw = "Recent activity of the weather app; geofence count 0",
            sensorRaw = "aod mode off, activity log",
            connectivityRaw = "iface wlan0 rmnet_data0 MOBILE data disabled cne qti",
        )

        assertTrue(snapshot.signals.isEmpty())
    }

    @Test
    fun `specific identifiers raise their signals`() {
        val snapshot = buildExpertSnapshot(
            deviceFamily = DeviceFamily.GENERIC_ANDROID,
            locationRaw = "provider activity_recognition_provider enabled; com.coloros.weather.service",
            sensorRaw = "lux_aod sensor active",
            connectivityRaw = "Transports: WIFI\nTransports: CELLULAR",
        )

        assertEquals(
            setOf(
                ExpertSignal.ACTIVITY_RECOGNITION,
                ExpertSignal.WEATHER_PASSIVE_LOCATION,
                ExpertSignal.AOD_LIGHT_WAKEUP,
                ExpertSignal.WIFI_CONNECTED,
                ExpertSignal.CELLULAR_IMS,
            ),
            snapshot.signals,
        )
    }
}
