package de.sanniki.wakesleuth.data

import android.content.Context
import de.sanniki.wakesleuth.data.db.WakelogsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide data objects. The database and everything writing to it
 * must be singletons, because the service, the notification listener and
 * the activity share one process and one write order.
 */
class WakelogsData private constructor(
    context: Context,
) {
    /**
     * Application scope for writes that must outlive the caller, e.g. a
     * broadcast receiver or a service that is being destroyed. Runs one
     * coroutine at a time so launches keep their order.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    val database: WakelogsDatabase = WakelogsDatabase.build(context)

    private val writer = DatabaseWriter(database)

    val labels = PackageLabelStore(
        lookup = PackageLabelStore.packageManagerLookup(context),
        dao = database.packageLabelDao(),
        scope = scope,
    )

    val recorder = EventRecorder(writer, labels)

    val sessions = SessionRepository(writer, labels)

    val events = EventRepository(writer)

    companion object {
        /**
         * SharedPreferences of the text based storage. There is no data
         * migration; they are deleted on the first start of this version.
         */
        private val LEGACY_PREFERENCES =
            listOf("wakesleuth_events", "wakesleuth_session_archive", "wakesleuth_notifications")

        @Volatile
        private var instance: WakelogsData? = null

        fun get(context: Context): WakelogsData =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(context: Context): WakelogsData {
            LEGACY_PREFERENCES.forEach { context.deleteSharedPreferences(it) }
            return WakelogsData(context)
        }
    }
}
