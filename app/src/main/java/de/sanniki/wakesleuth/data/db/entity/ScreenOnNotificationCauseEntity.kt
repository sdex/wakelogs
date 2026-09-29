package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Notification matched to a SCREEN_ON. The offset is signed: negative
 * means the notification arrived before the screen turned on.
 */
@Entity(
    tableName = "screen_on_notification_cause",
    indices = [Index("notification_event_id")],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["notification_event_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class ScreenOnNotificationCauseEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "notification_event_id")
    val notificationEventId: Long?,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "offset_ms")
    val offsetMs: Long,
)
