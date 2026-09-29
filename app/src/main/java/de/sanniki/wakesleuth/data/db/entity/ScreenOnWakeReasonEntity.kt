package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import de.sanniki.wakesleuth.domain.PowerKeySignal
import de.sanniki.wakesleuth.domain.WakeReason
import de.sanniki.wakesleuth.domain.WakeReasonEvidence

/** At most one direct wake reason per SCREEN_ON. */
@Entity(
    tableName = "screen_on_wake_reason",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ScreenOnWakeReasonEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    val reason: WakeReason,
    val evidence: WakeReasonEvidence,
    @ColumnInfo(name = "power_key_signal")
    val powerKeySignal: PowerKeySignal? = null,
    /** Signed: wake time minus screen-on time. */
    @ColumnInfo(name = "offset_ms")
    val offsetMs: Long,
    @ColumnInfo(name = "raw_reason")
    val rawReason: String? = null,
    @ColumnInfo(name = "raw_details")
    val rawDetails: String? = null,
    @ColumnInfo(name = "raw_tag")
    val rawTag: String? = null,
)
