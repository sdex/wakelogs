package de.sanniki.wakesleuth.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import de.sanniki.wakesleuth.data.db.entity.EventEntity
import de.sanniki.wakesleuth.data.db.relation.EventWithPayload
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    /** "Clear events": sessions and their archive summary stay. */
    @Query("DELETE FROM event")
    suspend fun deleteAll()

    @Transaction
    @Query(
        """
        SELECT * FROM event
        WHERE session_id = :sessionId
        ORDER BY occurred_at DESC, id DESC
        """,
    )
    fun observeSessionTimeline(sessionId: Long): Flow<List<EventWithPayload>>

    /** Insertion order, as used by the technical export. */
    @Transaction
    @Query("SELECT * FROM event WHERE session_id = :sessionId ORDER BY id ASC")
    suspend fun sessionEventsInInsertOrder(sessionId: Long): List<EventWithPayload>

    @Transaction
    @Query(
        """
        SELECT * FROM event
        WHERE type = 'SCREEN_ON' AND occurred_at >= :from AND occurred_at < :to
        """,
    )
    fun observeScreenOnsBetween(
        from: Long,
        to: Long,
    ): Flow<List<EventWithPayload>>

    @Transaction
    @Query("SELECT * FROM event WHERE id = :id")
    suspend fun byId(id: Long): EventWithPayload?

    @Query(
        """
        SELECT * FROM event
        WHERE type = 'SCREEN_ON' AND occurred_at BETWEEN :from AND :to
        ORDER BY occurred_at DESC, id DESC
        """,
    )
    suspend fun screenOnsBetween(
        from: Long,
        to: Long,
    ): List<EventEntity>

    @Query("SELECT MAX(occurred_at) FROM event WHERE session_id = :sessionId")
    suspend fun lastOccurredAt(sessionId: Long): Long?

    /** Same CPU wakeup already recorded (same raw reason within 500 ms). */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM event e
            JOIN cpu_wakeup c ON c.event_id = e.id
            WHERE e.type = 'CPU_WAKEUP'
              AND e.occurred_at > :at - 500
              AND e.occurred_at < :at + 500
              AND c.raw_wake_reason IS :rawWakeReason
        )
        """,
    )
    suspend fun cpuWakeupExists(
        at: Long,
        rawWakeReason: String?,
    ): Boolean
}
