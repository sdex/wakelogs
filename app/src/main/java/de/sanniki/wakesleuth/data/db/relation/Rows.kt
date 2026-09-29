package de.sanniki.wakesleuth.data.db.relation

import androidx.room3.ColumnInfo

/** Newest notification in a time window, for the screen-on cause. */
data class RecentNotificationRow(
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "package_name")
    val packageName: String
)
