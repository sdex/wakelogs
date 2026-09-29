package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Last known package manager label of a package. Only used as a fallback
 * so that apps uninstalled since keep a readable name.
 */
@Entity(tableName = "package_label")
data class PackageLabelEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    val label: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
