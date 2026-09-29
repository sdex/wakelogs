package de.sanniki.wakesleuth.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import de.sanniki.wakesleuth.data.db.entity.NotificationEntity
import de.sanniki.wakesleuth.data.db.relation.RecentNotificationRow

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: NotificationEntity)

    @Query(
        """
        SELECT e.id AS event_id, e.occurred_at AS occurred_at, n.package_name AS package_name
        FROM event e
        JOIN notification n ON n.event_id = e.id
        WHERE e.occurred_at BETWEEN :from AND :to
        ORDER BY e.occurred_at DESC, e.id DESC
        LIMIT 1
        """,
    )
    suspend fun newestBetween(
        from: Long,
        to: Long,
    ): RecentNotificationRow?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM notification n
            JOIN event e ON e.id = n.event_id
            WHERE n.fingerprint = :fingerprint AND e.occurred_at >= :since
        )
        """,
    )
    suspend fun fingerprintSeenSince(
        fingerprint: String,
        since: Long,
    ): Boolean
}
