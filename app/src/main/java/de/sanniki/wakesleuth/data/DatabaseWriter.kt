package de.sanniki.wakesleuth.data

import androidx.room3.TransactionScope
import androidx.room3.withWriteTransaction
import de.sanniki.wakesleuth.data.db.WakelogsDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes all writes of the app. Each call is one immediate write
 * transaction; the mutex keeps the order of "insert SCREEN_ON, then
 * attach" style sequences that are launched one after the other.
 *
 * Never nest [transaction] calls: the mutex is not reentrant.
 */
class DatabaseWriter(
    val database: WakelogsDatabase
) {
    private val mutex = Mutex()

    suspend fun <R> transaction(block: suspend TransactionScope<R>.() -> R): R =
        mutex.withLock {
            database.withWriteTransaction(block)
        }
}
