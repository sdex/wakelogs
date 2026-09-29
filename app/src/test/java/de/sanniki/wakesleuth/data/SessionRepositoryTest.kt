package de.sanniki.wakesleuth.data

import de.sanniki.wakesleuth.NetworkStatsDiagnostic
import de.sanniki.wakesleuth.NetworkTrafficEntry
import de.sanniki.wakesleuth.domain.DiagnosticError
import de.sanniki.wakesleuth.domain.EventType
import de.sanniki.wakesleuth.domain.EvidenceOrigin
import de.sanniki.wakesleuth.domain.EvidenceType
import de.sanniki.wakesleuth.domain.NetworkMeasurementStatus
import de.sanniki.wakesleuth.domain.NetworkSessionEvent
import de.sanniki.wakesleuth.domain.SessionEndReason
import de.sanniki.wakesleuth.domain.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest : DatabaseTest() {
    private fun traffic(
        uid: Int,
        pkg: String?,
        rx: Long,
        tx: Long,
        rxPackets: Long? = null,
    ) = NetworkTrafficEntry(uid, pkg, null, rx, tx, rx + tx, rxPackets, null)

    @Test
    fun `stop, network measurement and finalization`() =
        runBlocking {
            val session = startSession()

            sessions.saveNetworkBaseline(
                sessionId = session,
                capturedAt = START + 1_000,
                entries = listOf(traffic(10_100, "com.chat", 1_000, 500, rxPackets = 10), traffic(1_000, null, 0, 0)),
            )

            recorder.recordScreenOn(START + 10_000, null)
            recorder.recordCpuWakeup(
                CpuWakeupCandidate(
                    occurredAt = START + 20_000,
                    rawWakeReason = null,
                    runningObserved = true,
                    returnedToSleepAt = START + 22_000,
                    evidence = listOf(EvidenceCandidate(EvidenceOrigin.JOB, EvidenceType.JOBSCHEDULER, "com.chat/.Job")),
                ),
            )

            assertEquals(session, sessions.requestStop(START + 30_000))
            assertNull(sessions.requestStop(START + 31_000))

            sessions.finishNetworkMeasurement(
                sessionId = session,
                end = NetworkStatsDiagnostic(
                    entries = listOf(
                        traffic(10_100, "com.chat", 3_000, 700, rxPackets = 25),
                        traffic(10_200, "com.new", 100, 0),
                        traffic(1_000, null, 0, 0),
                    ),
                ),
                at = START + 37_000,
            )

            sessions.finalize(session, START + 38_000, SessionEndReason.USER_STOP, finalPollCompleted = true)

            val stored = database.sessionDao().byId(session)!!
            assertEquals(START + 30_000, stored.stopRequestedAt)
            assertEquals(1, stored.displayWakeups)
            assertEquals(1, stored.cpuWakeups)
            assertEquals(SessionEndReason.USER_STOP, stored.endReason)
            assertTrue(database.networkDao().baseline(session).isEmpty())

            val measurement = timeline(session).filterIsInstance<NetworkSessionEvent>().single().measurement!!
            assertEquals(NetworkMeasurementStatus.OK, measurement.status)
            assertEquals(36_000L, measurement.durationMs)
            assertEquals(listOf(10_100, 10_200), measurement.usage.map { it.uid })
            assertEquals(2_000L, measurement.usage.first().rxBytes)
            assertEquals(15L, measurement.usage.first().rxPackets)
            assertEquals(2_300L, measurement.totalBytes)

            val stats = database.sessionDao().observeFinalizedSourceStats().first()
            val chat = stats.single()
            assertEquals(SourceKind.APP, chat.sourceKind)
            assertEquals("com.chat", chat.packageName)
            assertEquals(1, chat.cpuCount)
            assertEquals(2_000L, chat.longestCpuAwakeMs)
        }

    @Test
    fun `failed or missing measurements are typed`() =
        runBlocking {
            val first = startSession()
            sessions.requestStop(START + 1_000)
            sessions.finishNetworkMeasurement(first, end = null, at = START + 2_000)
            assertEquals(NetworkMeasurementStatus.NO_BASELINE, database.networkDao().measurement(first)?.status)
            sessions.finalize(first, START + 3_000, SessionEndReason.USER_STOP, true)

            val second = startSession(START + 10_000)
            sessions.saveNetworkBaseline(second, START + 10_000, listOf(traffic(10_100, "com.chat", 1, 1)))
            sessions.requestStop(START + 11_000)
            sessions.finishNetworkMeasurement(
                second,
                end = NetworkStatsDiagnostic(
                    entries = emptyList(),
                    error = "localized",
                    errorCode = DiagnosticError.TIMEOUT,
                    errorDetail = "timed out",
                ),
                at = START + 12_000,
            )

            val failed = database.networkDao().measurement(second)!!
            assertEquals(NetworkMeasurementStatus.END_FAILED, failed.status)
            assertEquals(DiagnosticError.TIMEOUT, failed.errorCode)
            assertEquals("timed out", failed.errorDetail)
        }

    @Test
    fun `abandoned sessions end with their last event`() =
        runBlocking {
            val session = startSession()
            recorder.recordScreenOn(START + 45_000, null)

            sessions.recoverAbandonedSessions(START + 999_000)

            val stored = database.sessionDao().byId(session)!!
            assertEquals(SessionEndReason.INTERRUPTED, stored.endReason)
            assertEquals(START + 45_000, stored.stopRequestedAt)
            assertEquals(START + 999_000, stored.finalizedAt)
            assertNull(recorder.recordScreenOn(START + 1_000_000, null))
        }

    @Test
    fun `retention keeps twenty sessions and cascades to their events`() =
        runBlocking {
            val ids = (0 until 22).map { index ->
                val at = START + index * 100_000L
                val id = startSession(at)
                recorder.recordScreenOn(at + 1_000, null)
                sessions.requestStop(at + 2_000)
                sessions.finalize(id, at + 3_000, SessionEndReason.USER_STOP, true)
                id
            }

            val remaining = database
                .sessionDao()
                .observeFinalizedSessions()
                .first()
                .map { it.id }
            assertEquals(SessionRepository.MAX_SESSIONS, remaining.size)
            assertEquals(ids.takeLast(SessionRepository.MAX_SESSIONS).reversed(), remaining)
            assertNull(database.sessionDao().byId(ids.first()))
            assertTrue(timeline(ids.first()).isEmpty())
            assertEquals(
                listOf(EventType.MONITOR_START, EventType.SCREEN_ON, EventType.MONITOR_STOP),
                timeline(ids.last()).map { it.type },
            )
        }

    @Test
    fun `notes are trimmed and limited`() =
        runBlocking {
            val session = startSession()
            sessions.requestStop(START + 1)
            sessions.finalize(session, START + 2, SessionEndReason.USER_STOP, true)

            assertTrue(sessions.updateNote(session, "  " + "x".repeat(200) + "  "))
            assertEquals(
                120,
                database
                    .sessionDao()
                    .byId(session)
                    ?.note
                    ?.length,
            )

            assertTrue(sessions.updateNote(session, "   "))
            assertNull(database.sessionDao().byId(session)?.note)

            assertTrue(sessions.deleteSession(session))
            assertNull(database.sessionDao().byId(session))
        }
}
