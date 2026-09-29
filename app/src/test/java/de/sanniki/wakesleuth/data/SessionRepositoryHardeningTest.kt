package de.sanniki.wakesleuth.data

import de.sanniki.wakesleuth.NetworkStatsDiagnostic
import de.sanniki.wakesleuth.NetworkTrafficEntry
import de.sanniki.wakesleuth.domain.NetworkMeasurementStatus
import de.sanniki.wakesleuth.domain.NetworkSessionEvent
import de.sanniki.wakesleuth.domain.SessionEndReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRepositoryHardeningTest : DatabaseTest() {
    private fun traffic(
        uid: Int,
        rx: Long,
        tx: Long,
        rxPackets: Long? = null,
    ) = NetworkTrafficEntry(uid, "pkg.$uid", null, rx, tx, rx + tx, rxPackets, null)

    private fun measurement(sessionId: Long) = timeline(sessionId).filterIsInstance<NetworkSessionEvent>().single().measurement!!

    @Test
    fun `second network measurement does not replace the stored result`() =
        runBlocking {
            val session = startSession()
            sessions.saveNetworkBaseline(session, START + 1_000, listOf(traffic(10_100, 100, 100)))
            sessions.requestStop(START + 10_000)

            val end = NetworkStatsDiagnostic(entries = listOf(traffic(10_100, 600, 300)))
            sessions.finishNetworkMeasurement(session, end, START + 11_000)
            // The baseline is gone now; a naive second run would write NO_BASELINE.
            sessions.finishNetworkMeasurement(session, end = null, at = START + 12_000)

            val stored = measurement(session)
            assertEquals(NetworkMeasurementStatus.OK, stored.status)
            assertEquals(listOf(500L), stored.usage.map { it.rxBytes })
            assertEquals(1, timeline(session).filterIsInstance<NetworkSessionEvent>().size)
        }

    @Test
    fun `network measurement is ignored for a finalized session`() =
        runBlocking {
            val session = startSession()
            sessions.requestStop(START + 10_000)
            sessions.finalize(session, START + 11_000, SessionEndReason.USER_STOP, finalPollCompleted = true)

            sessions.finishNetworkMeasurement(session, end = null, at = START + 12_000)

            assertNull(database.networkDao().measurement(session))
        }

    @Test
    fun `counter reset counts the end value instead of zero`() =
        runBlocking {
            val session = startSession()
            sessions.saveNetworkBaseline(
                session,
                START + 1_000,
                listOf(traffic(10_100, rx = 5_000, tx = 2_000, rxPackets = 50)),
            )
            sessions.requestStop(START + 10_000)

            sessions.finishNetworkMeasurement(
                session,
                NetworkStatsDiagnostic(entries = listOf(traffic(10_100, rx = 300, tx = 2_500, rxPackets = 7))),
                START + 11_000,
            )

            val usage = measurement(session).usage.single()
            assertEquals(300L, usage.rxBytes)
            assertEquals(500L, usage.txBytes)
            assertEquals(7L, usage.rxPackets)
        }

    @Test
    fun `recovery leaves a session claimed by a newer service alone`() =
        runBlocking {
            val session = startSession(START + 5_000)

            // The old service started before the claim; it must not close the session.
            sessions.recoverAbandonedSessions(now = START + 9_000, ownerStartedAt = START)
            assertNull(database.sessionDao().byId(session)!!.finalizedAt)

            // The owner itself (or the process after a kill) does close it.
            sessions.recoverAbandonedSessions(now = START + 9_000, ownerStartedAt = START + 5_000)
            assertEquals(SessionEndReason.INTERRUPTED, database.sessionDao().byId(session)!!.endReason)
        }

    @Test
    fun `resuming a session moves its claim to the newer service`() =
        runBlocking {
            val session = startSession(START)
            val resumed = startSession(START + 8_000)
            assertEquals(session, resumed)

            sessions.recoverAbandonedSessions(now = START + 9_000, ownerStartedAt = START)

            assertNull(database.sessionDao().byId(session)!!.finalizedAt)
        }

    @Test
    fun `stopping sessions are finalized but running ones are kept`() =
        runBlocking {
            val stopping = startSession(START)
            sessions.requestStop(START + 1_000)
            val running = startSession(START + 2_000)

            sessions.finalizeStoppingSessions(START + 3_000)

            val closed = database.sessionDao().byId(stopping)!!
            assertEquals(SessionEndReason.USER_STOP, closed.endReason)
            assertNotNull(closed.finalizedAt)
            assertNull(database.sessionDao().byId(running)!!.finalizedAt)
        }
}
