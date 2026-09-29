package de.sanniki.wakesleuth.data

import de.sanniki.wakesleuth.data.db.WakelogsDatabase
import de.sanniki.wakesleuth.domain.RecordedEvent
import de.sanniki.wakesleuth.domain.ScreenOnEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Read side of the event log. */
class EventRepository(
    private val writer: DatabaseWriter
) {
    private val database: WakelogsDatabase get() = writer.database

    /**
     * Timeline of the newest session (running or finished), newest event
     * first. Re-emits on every change of the session's events or payload.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLatestSessionTimeline(): Flow<List<RecordedEvent>> =
        database.sessionDao()
            .observeLatestSession()
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { sessionId ->
                if (sessionId == null) {
                    flowOf(emptyList())
                } else {
                    observeSessionTimeline(sessionId)
                }
            }

    fun observeSessionTimeline(sessionId: Long): Flow<List<RecordedEvent>> =
        database.eventDao()
            .observeSessionTimeline(sessionId)
            .map { rows -> rows.map(EventMapper::toDomain) }
            .flowOn(Dispatchers.Default)

    /** Screen-ons in [from, to), across sessions. */
    fun observeScreenOnsBetween(from: Long, to: Long): Flow<List<ScreenOnEvent>> =
        database.eventDao()
            .observeScreenOnsBetween(from, to)
            .map { rows -> rows.map(EventMapper::toDomain).filterIsInstance<ScreenOnEvent>() }
            .flowOn(Dispatchers.Default)

    /** Events of a session in insertion order, for the technical export. */
    suspend fun sessionEventsInInsertOrder(sessionId: Long): List<RecordedEvent> =
        database.eventDao()
            .sessionEventsInInsertOrder(sessionId)
            .map(EventMapper::toDomain)

    /** "Clear events". The session archive and its summary stay. */
    suspend fun clearEvents() {
        writer.transaction {
            database.eventDao().deleteAll()
        }
    }
}
