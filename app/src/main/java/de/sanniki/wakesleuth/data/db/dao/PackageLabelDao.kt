package de.sanniki.wakesleuth.data.db.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import de.sanniki.wakesleuth.data.db.entity.PackageLabelEntity

@Dao
interface PackageLabelDao {

    @Upsert
    suspend fun upsert(labels: List<PackageLabelEntity>)

    @Query("SELECT * FROM package_label")
    suspend fun all(): List<PackageLabelEntity>
}
