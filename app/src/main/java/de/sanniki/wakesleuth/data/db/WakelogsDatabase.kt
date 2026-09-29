package de.sanniki.wakesleuth.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import de.sanniki.wakesleuth.data.db.dao.CpuWakeupDao
import de.sanniki.wakesleuth.data.db.dao.EventDao
import de.sanniki.wakesleuth.data.db.dao.NetworkDao
import de.sanniki.wakesleuth.data.db.dao.NotificationDao
import de.sanniki.wakesleuth.data.db.dao.PackageLabelDao
import de.sanniki.wakesleuth.data.db.dao.ScreenOnDao
import de.sanniki.wakesleuth.data.db.dao.SessionDao
import de.sanniki.wakesleuth.data.db.dao.SnapshotDao
import de.sanniki.wakesleuth.data.db.entity.CpuWakeupEntity
import de.sanniki.wakesleuth.data.db.entity.CpuWakeupEvidenceEntity
import de.sanniki.wakesleuth.data.db.entity.EventEntity
import de.sanniki.wakesleuth.data.db.entity.ExpertSnapshotEntity
import de.sanniki.wakesleuth.data.db.entity.ExpertSnapshotSignalEntity
import de.sanniki.wakesleuth.data.db.entity.MonitoringSessionEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkAppUsageEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkBaselineEntryEntity
import de.sanniki.wakesleuth.data.db.entity.NetworkMeasurementEntity
import de.sanniki.wakesleuth.data.db.entity.NotificationEntity
import de.sanniki.wakesleuth.data.db.entity.PackageLabelEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnAlarmHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnJobHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnNotificationCauseEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnWakeLockHintEntity
import de.sanniki.wakesleuth.data.db.entity.ScreenOnWakeReasonEntity
import de.sanniki.wakesleuth.data.db.entity.SessionSourceStatEntity
import de.sanniki.wakesleuth.data.db.entity.SystemSnapshotEntity
import de.sanniki.wakesleuth.data.db.entity.UsbDeviceEventEntity

/**
 * `wakelogs.db`: typed facts only. No display text and no serialized
 * structures; see docs/room3-migration-plan.md for the rules.
 */
@Database(
    entities = [
        MonitoringSessionEntity::class,
        SessionSourceStatEntity::class,
        EventEntity::class,
        ScreenOnWakeReasonEntity::class,
        ScreenOnNotificationCauseEntity::class,
        ScreenOnWakeLockHintEntity::class,
        ScreenOnAlarmHintEntity::class,
        ScreenOnJobHintEntity::class,
        NotificationEntity::class,
        CpuWakeupEntity::class,
        CpuWakeupEvidenceEntity::class,
        NetworkMeasurementEntity::class,
        NetworkAppUsageEntity::class,
        NetworkBaselineEntryEntity::class,
        SystemSnapshotEntity::class,
        ExpertSnapshotEntity::class,
        ExpertSnapshotSignalEntity::class,
        UsbDeviceEventEntity::class,
        PackageLabelEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WakelogsDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun eventDao(): EventDao

    abstract fun screenOnDao(): ScreenOnDao

    abstract fun notificationDao(): NotificationDao

    abstract fun cpuWakeupDao(): CpuWakeupDao

    abstract fun networkDao(): NetworkDao

    abstract fun snapshotDao(): SnapshotDao

    abstract fun packageLabelDao(): PackageLabelDao

    companion object {
        const val FILE_NAME = "wakelogs.db"

        fun build(context: Context): WakelogsDatabase =
            Room
                .databaseBuilder(context.applicationContext, WakelogsDatabase::class.java, FILE_NAME)
                // Platform SQLite, no native libraries (plan decision 3).
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}
