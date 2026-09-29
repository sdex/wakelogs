package de.sanniki.wakesleuth.data

import android.content.Context
import de.sanniki.wakesleuth.data.db.dao.PackageLabelDao
import de.sanniki.wakesleuth.data.db.entity.PackageLabelEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Package manager labels. The live label always wins; the stored copy is
 * only a fallback so that apps uninstalled since the recording keep a
 * readable name (plan decision 5).
 */
class PackageLabelStore(
    /** Live label of an installed package, null when not installed. */
    private val lookup: (String) -> String?,
    private val dao: PackageLabelDao,
    private val scope: CoroutineScope,
) {
    private val stored = ConcurrentHashMap<String, String>()

    /** Live labels; an empty string marks a package that is not installed. */
    private val live = ConcurrentHashMap<String, String>()

    @Volatile
    private var loaded = false

    suspend fun preload() {
        if (loaded) {
            return
        }

        dao.all().forEach { stored[it.packageName] = it.label }
        loaded = true
    }

    fun label(packageName: String): String? = liveLabel(packageName) ?: stored[packageName]

    fun liveLabel(packageName: String): String? =
        live
            .getOrPut(packageName) {
                lookup(packageName)?.trim().orEmpty()
            }.ifEmpty { null }

    /** Stores the current label of every installed package not yet stored. */
    fun remember(packageNames: Collection<String?>) {
        val candidates = packageNames
            .filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()

        if (candidates.isEmpty()) {
            return
        }

        scope.launch {
            preload()

            val now = System.currentTimeMillis()

            val changed = candidates.mapNotNull { packageName ->
                val label = liveLabel(packageName) ?: return@mapNotNull null

                if (stored[packageName] == label) {
                    null
                } else {
                    PackageLabelEntity(packageName, label, now)
                }
            }

            if (changed.isNotEmpty()) {
                runCatching { dao.upsert(changed) }.onSuccess { changed.forEach { stored[it.packageName] = it.label } }
            }
        }
    }

    companion object {
        /** Package manager lookup used on the device. */
        fun packageManagerLookup(context: Context): (String) -> String? {
            val packageManager = context.applicationContext.packageManager

            return { packageName ->
                runCatching {
                    val info = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(info).toString()
                }.getOrNull()
            }
        }
    }
}
