package de.sanniki.wakesleuth.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import de.sanniki.wakesleuth.data.db.entity.NetworkAppUsageEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkBaselineEntryEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(entries: List<NetworkBaselineEntryEntity>)

    @Query("SELECT * FROM network_baseline_entry WHERE session_id = :sessionId")
    suspend fun baseline(sessionId: Long): List<NetworkBaselineEntryEntity>

    @Query("DELETE FROM network_baseline_entry WHERE session_id = :sessionId")
    suspend fun deleteBaseline(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: NetworkMeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: List<NetworkAppUsageEntity>)

    @Query("SELECT * FROM network_measurement WHERE session_id = :sessionId")
    suspend fun measurement(sessionId: Long): NetworkMeasurementEntity?

    @Query(
        """
        SELECT u.* FROM network_app_usage u
        JOIN monitoring_session m ON m.id = u.session_id
        WHERE m.finalized_at IS NOT NULL
        """
    )
    fun observeFinalizedUsage(): Flow<List<NetworkAppUsageEntity>>
}
