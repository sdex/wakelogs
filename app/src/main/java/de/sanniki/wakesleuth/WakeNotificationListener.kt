package de.sanniki.wakesleuth

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WakeNotificationListener :
    NotificationListenerService() {

    override fun onNotificationPosted(
        sbn: StatusBarNotification?
    ) {
        val item = sbn ?: return

        if (item.packageName == packageName) {
            return
        }

        val notification = item.notification
        val flags = notification.flags

        val isForegroundService =
            flags and
                Notification.FLAG_FOREGROUND_SERVICE != 0

        val isOngoing =
            flags and
                Notification.FLAG_ONGOING_EVENT != 0

        val isGroupSummary =
            flags and
                Notification.FLAG_GROUP_SUMMARY != 0

        if (
            isForegroundService ||
            isOngoing ||
            isGroupSummary
        ) {
            return
        }

        val extras = notification.extras

        val title = extras
            .getCharSequence(
                Notification.EXTRA_TITLE
            )
            ?.toString()
            .orEmpty()
            .trim()
            .take(MAX_TEXT_LENGTH)

        val text = extras
            .getCharSequence(
                Notification.EXTRA_TEXT
            )
            ?.toString()
            .orEmpty()
            .trim()
            .take(MAX_TEXT_LENGTH)

        val appName = runCatching {
            val info =
                packageManager.getApplicationInfo(
                    item.packageName,
                    0
                )

            packageManager
                .getApplicationLabel(info)
                .toString()
        }.getOrElse {
            item.packageName
        }

        val duplicate =
            NotificationStore.isDuplicate(
                context = this,
                packageName = item.packageName,
                title = title,
                text = text,
                notificationKey = item.key
            )

        if (duplicate) {
            return
        }

        val recentNotification =
            RecentNotification(
                timestamp =
                    System.currentTimeMillis(),
                packageName = item.packageName,
                appName = appName,
                title = title,
                text = text
            )

        NotificationStore.save(
            context = this,
            notification = recentNotification
        )

        if (!EventStore.isMonitoring(this)) {
            return
        }

        EventStore.addEvent(
            context = this,
            type = "NOTIFICATION",
            title =
                getString(
                    R.string.service_notification_event_title,
                    appName
                ),
            details = buildString {
                if (title.isNotBlank()) {
                    appendLine(getString(R.string.service_notification_event_title_line, title))
                }

                if (text.isNotBlank()) {
                    appendLine(getString(R.string.service_notification_event_text_line, text))
                }

                if (
                    title.isBlank() &&
                    text.isBlank()
                ) {
                    appendLine(
                        getString(
                            R.string.service_notification_event_no_content
                        )
                    )
                }

                append(
                    getString(
                        R.string.service_notification_event_package,
                        item.packageName
                    )
                )
            }
        )

        EventStore.attachLateNotificationToScreenOn(
            context = this,
            notification = recentNotification
        )
    }

    companion object {
        private const val MAX_TEXT_LENGTH = 300
    }
}
