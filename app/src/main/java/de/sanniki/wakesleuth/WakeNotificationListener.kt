package de.sanniki.wakesleuth

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import de.sanniki.wakesleuth.data.WakelogsData
import kotlinx.coroutines.launch

class WakeNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return

        if (item.packageName == packageName) {
            return
        }

        val notification = item.notification
        val flags = notification.flags

        val isForegroundService = flags and
            Notification.FLAG_FOREGROUND_SERVICE != 0

        val isOngoing = flags and
            Notification.FLAG_ONGOING_EVENT != 0

        val isGroupSummary = flags and
            Notification.FLAG_GROUP_SUMMARY != 0

        if (
            isForegroundService || isOngoing || isGroupSummary
        ) {
            return
        }

        val extras = notification.extras

        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()
            .trim()
            .take(MAX_TEXT_LENGTH)

        val text = extras
            .getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            .orEmpty()
            .trim()
            .take(MAX_TEXT_LENGTH)

        val postedAt = System.currentTimeMillis()

        // Recording only happens while a session is open; the recorder
        // drops the call otherwise, so no separate monitoring flag is
        // needed here.
        val data = WakelogsData.get(this)

        data.scope.launch {
            data.recorder.recordNotification(
                at = postedAt,
                packageName = item.packageName,
                notificationKey = item.key,
                title = title,
                text = text,
            )
        }
    }

    companion object {
        private const val MAX_TEXT_LENGTH = 300
    }
}
