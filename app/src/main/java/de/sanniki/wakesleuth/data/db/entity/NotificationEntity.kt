package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Third-party notification content is stored verbatim and never parsed.
 * The fingerprint is used to drop reposts of the same notification.
 */
@Entity(
    tableName = "notification",
    indices = [Index("fingerprint")],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NotificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "notification_key")
    val notificationKey: String?,
    val title: String?,
    val text: String?,
    val fingerprint: String
)
