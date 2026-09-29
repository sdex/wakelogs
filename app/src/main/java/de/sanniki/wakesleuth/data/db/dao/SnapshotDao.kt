package de.sanniki.wakesleuth.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import de.sanniki.wakesleuth.data.db.entity.ExpertSnapshotEntity
import de.sanniki.wakesleuth.data.db.entity.ExpertSnapshotSignalEntity
import de.sanniki.wakesleuth.data.db.entity.SystemSnapshotEntity
import de.sanniki.wakesleuth.data.db.entity.UsbDeviceEventEntity

@Dao
interface SnapshotDao {

    @Insert
    suspend fun insertSystemSnapshot(snapshot: SystemSnapshotEntity)

    @Insert
    suspend fun insertExpertSnapshot(snapshot: ExpertSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpertSignals(signals: List<ExpertSnapshotSignalEntity>)

    @Insert
    suspend fun insertUsbDevice(device: UsbDeviceEventEntity)
}
