package de.sanniki.wakesleuth

import android.content.Context
import java.security.MessageDigest

data class RecentNotification(
    val timestamp: Long,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String
)

object NotificationStore {

    private const val PREFS_NAME = "wakesleuth_notifications"

    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_PACKAGE = "package"
    private const val KEY_APP = "app"
    private const val KEY_TITLE = "title"
    private const val KEY_TEXT = "text"

    private const val KEY_LAST_FINGERPRINT = "last_fingerprint"
    private const val KEY_LAST_FINGERPRINT_TIME =
        "last_fingerprint_time"

    private const val DUPLICATE_WINDOW_MILLIS = 5_000L

    fun save(
        context: Context,
        notification: RecentNotification
    ) {
        preferences(context).edit()
            .putLong(KEY_TIMESTAMP, notification.timestamp)
            .putString(KEY_PACKAGE, notification.packageName)
            .putString(KEY_APP, notification.appName)
            .putString(KEY_TITLE, notification.title)
            .putString(KEY_TEXT, notification.text)
            .apply()
    }

    fun getRecent(context: Context): RecentNotification? {
        val prefs = preferences(context)
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)

        if (timestamp <= 0L) {
            return null
        }

        return RecentNotification(
            timestamp = timestamp,
            packageName = prefs
                .getString(KEY_PACKAGE, "")
                .orEmpty(),
            appName = prefs
                .getString(KEY_APP, "")
                .orEmpty(),
            title = prefs
                .getString(KEY_TITLE, "")
                .orEmpty(),
            text = prefs
                .getString(KEY_TEXT, "")
                .orEmpty()
        )
    }

    fun isDuplicate(
        context: Context,
        packageName: String,
        title: String,
        text: String,
        notificationKey: String
    ): Boolean {
        val now = System.currentTimeMillis()

        val fingerprint = createFingerprint(
            packageName = packageName,
            title = title,
            text = text,
            notificationKey = notificationKey
        )

        val prefs = preferences(context)
        val previousFingerprint = prefs
            .getString(KEY_LAST_FINGERPRINT, "")
            .orEmpty()

        val previousTime = prefs.getLong(
            KEY_LAST_FINGERPRINT_TIME,
            0L
        )

        val duplicate =
            fingerprint == previousFingerprint &&
                now - previousTime in 0..DUPLICATE_WINDOW_MILLIS

        prefs.edit()
            .putString(KEY_LAST_FINGERPRINT, fingerprint)
            .putLong(KEY_LAST_FINGERPRINT_TIME, now)
            .apply()

        return duplicate
    }

    private fun createFingerprint(
        packageName: String,
        title: String,
        text: String,
        notificationKey: String
    ): String {
        val source = buildString {
            append(packageName)
            append('|')
            append(notificationKey)
            append('|')
            append(title)
            append('|')
            append(text)
        }

        return MessageDigest
            .getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { byte ->
                "%02x".format(byte)
            }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
}
