package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import de.sanniki.wakesleuth.domain.SourceKind

/**
 * Archive summary per source, materialized at finalization so it survives
 * clearing the event list. Key columns use '' instead of NULL.
 */
@Entity(
    tableName = "session_source_stat",
    primaryKeys = ["session_id", "source_kind", "package_name", "raw_source"],
    foreignKeys = [
        ForeignKey(
            entity = MonitoringSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SessionSourceStatEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "source_kind")
    val sourceKind: SourceKind,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "raw_source")
    val rawSource: String,
    @ColumnInfo(name = "cpu_count")
    val cpuCount: Int,
    @ColumnInfo(name = "display_count")
    val displayCount: Int,
    @ColumnInfo(name = "companion_count")
    val companionCount: Int,
    @ColumnInfo(name = "longest_cpu_awake_ms")
    val longestCpuAwakeMs: Long?,
)
