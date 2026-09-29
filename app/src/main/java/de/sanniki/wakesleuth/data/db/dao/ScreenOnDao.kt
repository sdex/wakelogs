package de.sanniki.wakesleuth.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import de.sanniki.wakesleuth.data.db.entity.EventEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnAlarmHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnJobHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnNotificationCauseEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnWakeLockHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnWakeReasonEntity

/** Inserts return -1 when the unique guard already holds such a row. */
@Dao
interface ScreenOnDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWakeReason(entity: ScreenOnWakeReasonEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotificationCause(entity: ScreenOnNotificationCauseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWakeLockHint(entity: ScreenOnWakeLockHintEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlarmHint(entity: ScreenOnAlarmHintEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertJobHint(entity: ScreenOnJobHintEntity): Long

    /** Newest SCREEN_ON within the window that has no direct wake reason. */
    @Query(
        """
        SELECT * FROM event
        WHERE type = 'SCREEN_ON'
          AND occurred_at BETWEEN :from AND :to
          AND NOT EXISTS (SELECT 1 FROM screen_on_wake_reason w WHERE w.event_id = event.id)
        ORDER BY occurred_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun newestWithoutWakeReason(
        from: Long,
        to: Long,
    ): EventEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM screen_on_wake_reason WHERE event_id = :eventId)")
    suspend fun hasWakeReason(eventId: Long): Boolean
}
