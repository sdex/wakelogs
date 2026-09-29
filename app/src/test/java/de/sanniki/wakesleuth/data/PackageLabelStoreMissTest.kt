package de.sanniki.wakesleuth.data

import de.sanniki.wakesleuth.data.db.dao.PackageLabelDao
import de.sanniki.wakesleuth.data.db.entity.PackageLabelEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageLabelStoreMissTest {
    private val dao = object : PackageLabelDao {
        override suspend fun upsert(labels: List<PackageLabelEntity>) = Unit

        override suspend fun all(): List<PackageLabelEntity> = emptyList()
    }

    @Test
    fun `a package installed later gets its label`() {
        val installed = mutableMapOf<String, String>()
        val store = PackageLabelStore({ installed[it] }, dao, CoroutineScope(Job() + Dispatchers.Unconfined))

        assertNull(store.liveLabel("com.late"))

        installed["com.late"] = "  Late App "

        assertEquals("Late App", store.liveLabel("com.late"))
        assertEquals("Late App", store.label("com.late"))
    }

    @Test
    fun `found labels are looked up only once`() {
        var lookups = 0
        val store = PackageLabelStore(
            lookup = {
                lookups += 1
                "App"
            },
            dao = dao,
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
        )

        store.liveLabel("com.app")
        store.liveLabel("com.app")

        assertEquals(1, lookups)
    }
}
