package de.sanniki.wakesleuth.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.sanniki.wakesleuth.DeviceFamily
import de.sanniki.wakesleuth.data.db.WakelogsDatabase
import de.sanniki.wakesleuth.domain.Proximity
import de.sanniki.wakesleuth.domain.ProximityState
import de.sanniki.wakesleuth.domain.RecordedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

/** In-memory database on the host JVM with the bundled SQLite driver. */
abstract class DatabaseTest {
    protected lateinit var database: WakelogsDatabase
    protected lateinit var recorder: EventRecorder
    protected lateinit var sessions: SessionRepository
    protected lateinit var events: EventRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun openDatabase() {
        database = Room
            .inMemoryDatabaseBuilder<WakelogsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        val writer = DatabaseWriter(database)
        val labels = PackageLabelStore({ null }, database.packageLabelDao(), scope)

        recorder = EventRecorder(writer, labels)
        sessions = SessionRepository(writer, labels)
        events = EventRepository(writer)
    }

    @After
    fun closeDatabase() {
        scope.cancel()
        database.close()
    }

    protected fun startSession(at: Long = START): Long =
        runBlocking {
            sessions.startOrResume(
                at = at,
                deviceFamily = DeviceFamily.GENERIC_ANDROID,
                proximity = Proximity(ProximityState.FAR, 5f),
            )
        }

    protected fun timeline(sessionId: Long): List<RecordedEvent> = runBlocking { events.sessionEventsInInsertOrder(sessionId) }

    protected inline fun <reified T : RecordedEvent> single(sessionId: Long): T = timeline(sessionId).filterIsInstance<T>().single()

    companion object {
        const val START = 1_700_000_000_000L
    }
}
