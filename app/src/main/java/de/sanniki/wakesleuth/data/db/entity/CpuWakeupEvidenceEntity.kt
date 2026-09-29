package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import de.sanniki.wakesleuth.domain.EvidenceOrigin
import de.sanniki.wakesleuth.domain.EvidenceType

@Entity(
    tableName = "cpu_wakeup_evidence",
    indices = [
        Index("event_id", "evidence_type", "raw_source", unique = true),
        Index("is_primary", "package_name")
    ],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CpuWakeupEvidenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    val origin: EvidenceOrigin,
    @ColumnInfo(name = "evidence_type")
    val evidenceType: EvidenceType,
    /** BatteryStats token with `*job*r/` style prefixes removed. */
    @ColumnInfo(name = "raw_source")
    val rawSource: String,
    @ColumnInfo(name = "package_name")
    val packageName: String?,
    /** The evidence chosen as possible source; none means unattributed. */
    @ColumnInfo(name = "is_primary")
    val isPrimary: Boolean
)
