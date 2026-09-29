package de.sanniki.wakesleuth.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import de.sanniki.wakesleuth.data.db.entity.CpuWakeupEntity
import de.sanniki.wakesleuth.data.db.entity.CpuWakeupEvidenceEntity

@Dao
interface CpuWakeupDao {
    @Insert
    suspend fun insert(wakeup: CpuWakeupEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvidence(evidence: List<CpuWakeupEvidenceEntity>)
}
