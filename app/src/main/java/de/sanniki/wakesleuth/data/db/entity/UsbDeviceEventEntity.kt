package de.sanniki.wakesleuth.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey

@Entity(
    tableName = "usb_device_event",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UsbDeviceEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "device_id")
    val deviceId: Int?,
    @ColumnInfo(name = "vendor_id")
    val vendorId: Int?,
    @ColumnInfo(name = "product_id")
    val productId: Int?,
    @ColumnInfo(name = "device_name")
    val deviceName: String? = null,
    @ColumnInfo(name = "manufacturer_name")
    val manufacturerName: String? = null
)
