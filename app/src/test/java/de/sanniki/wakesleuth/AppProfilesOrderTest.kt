package de.sanniki.wakesleuth

import de.sanniki.wakesleuth.domain.SourceKind
import de.sanniki.wakesleuth.domain.SourceRef
import org.junit.Assert.assertEquals
import org.junit.Test

class AppProfilesOrderTest {
    private fun app(
        pkg: String,
        name: String,
    ) = ArchivedSessionApp(
        source = SourceRef(SourceKind.APP, packageName = pkg),
        name = name,
        totalBytes = 10,
        rxBytes = 5,
        txBytes = 5,
    )

    @Test
    fun `equal profiles are ordered by name ignoring case`() {
        val session = ArchivedSession(
            id = 1,
            startMillis = 0,
            endMillis = 1_000,
            durationMillis = 1_000,
            endReason = null,
            displayWakeups = 0,
            cpuWakeups = 0,
            networkTotalBytes = 20,
            networkRxBytes = 10,
            networkTxBytes = 10,
            networkActiveApps = 2,
            topApps = listOf(app("com.b", "beta"), app("com.a", "Zeta"), app("com.c", "Alpha")),
        )

        assertEquals(listOf("Alpha", "beta", "Zeta"), buildAppProfiles(listOf(session)).map { it.name })
    }
}
