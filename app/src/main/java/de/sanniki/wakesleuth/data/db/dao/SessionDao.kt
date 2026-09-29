package de.sanniki.wakesleuth.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity
import de.sanniki.wakesleuth.data.db.entity.SessionSourceStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: MonitoringSessionEntity): Long

    @Update
    suspend fun update(session: MonitoringSessionEntity)

    @Query("SELECT * FROM monitoring_session WHERE id = :id")
    suspend fun byId(id: Long): MonitoringSessionEntity?

    /** The session that still accepts events (running or finalizing). */
    @Query(
        """
        SELECT * FROM monitoring_session
        WHERE finalized_at IS NULL
        ORDER BY started_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun recordingSession(): MonitoringSessionEntity?

    @Query(
        """
        SELECT * FROM monitoring_session
        WHERE finalized_at IS NULL
        ORDER BY started_at DESC, id DESC
        """,
    )
    suspend fun unfinalizedSessions(): List<MonitoringSessionEntity>

    @Query(
        """
        SELECT * FROM monitoring_session
        WHERE stop_requested_at IS NULL AND finalized_at IS NULL
        ORDER BY started_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun runningSession(): MonitoringSessionEntity?

    @Query(
        """
        SELECT * FROM monitoring_session
        ORDER BY started_at DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeLatestSession(): Flow<MonitoringSessionEntity?>

    @Query(
        """
        SELECT * FROM monitoring_session
        ORDER BY started_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun latestSession(): MonitoringSessionEntity?

    @Query(
        """
        SELECT * FROM monitoring_session
        WHERE finalized_at IS NOT NULL
        ORDER BY started_at DESC, id DESC
        """,
    )
    fun observeFinalizedSessions(): Flow<List<MonitoringSessionEntity>>

    @Query("UPDATE monitoring_session SET note = :note WHERE id = :id")
    suspend fun updateNote(
        id: Long,
        note: String?,
    ): Int

    @Query("DELETE FROM monitoring_session WHERE id = :id AND finalized_at IS NOT NULL")
    suspend fun deleteFinalized(id: Long): Int

    @Query("DELETE FROM monitoring_session WHERE finalized_at IS NOT NULL")
    suspend fun deleteAllFinalized()

    /** Keeps the newest [keep] finalized sessions; cascades to their data. */
    @Query(
        """
        DELETE FROM monitoring_session
        WHERE finalized_at IS NOT NULL
          AND id NOT IN (
            SELECT id FROM monitoring_session
            WHERE finalized_at IS NOT NULL
            ORDER BY started_at DESC, id DESC
            LIMIT :keep
          )
        """,
    )
    suspend fun applyRetention(keep: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSourceStats(stats: List<SessionSourceStatEntity>)

    @Query("DELETE FROM session_source_stat WHERE session_id = :sessionId")
    suspend fun deleteSourceStats(sessionId: Long)

    @Query(
        """
        SELECT s.* FROM session_source_stat s
        JOIN monitoring_session m ON m.id = s.session_id
        WHERE m.finalized_at IS NOT NULL
        """,
    )
    fun observeFinalizedSourceStats(): Flow<List<SessionSourceStatEntity>>
}
