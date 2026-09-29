package de.sanniki.wakesleuth.ui.common

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import de.sanniki.wakesleuth.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatDuration(
    context: Context,
    millis: Long?,
): String {
    val value = millis ?: return context.getString(R.string.main_duration_unknown)

    val totalSeconds = value / 1_000L
    val days = totalSeconds / 86_400L
    val hours = totalSeconds % 86_400L / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L

    return when {
        days > 0L -> {
            context.getString(R.string.main_duration_days_hours, days, hours)
        }

        hours > 0L -> {
            context.getString(R.string.main_duration_hours_minutes, hours, minutes)
        }

        minutes > 0L -> {
            context.getString(R.string.main_duration_minutes_seconds, minutes, seconds)
        }

        else -> {
            context.getString(R.string.main_duration_seconds, seconds)
        }
    }
}

internal fun formatNetworkBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)

    return when {
        value >= 1_073_741_824L -> {
            String.format(Locale.getDefault(), "%.1f GB", value / 1_073_741_824.0)
        }

        value >= 1_048_576L -> {
            String.format(Locale.getDefault(), "%.1f MB", value / 1_048_576.0)
        }

        value >= 1024L -> {
            String.format(Locale.getDefault(), "%.1f KB", value / 1024.0)
        }

        else -> {
            "$value B"
        }
    }
}

internal fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("dd.MM. · HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

internal fun formatComparisonDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) /
        1_000L

    val minutes = totalSeconds / 60L

    val seconds = totalSeconds % 60L

    return if (minutes > 0L) {
        "$minutes:${seconds.toString().padStart(2, '0')} min"
    } else {
        "$seconds s"
    }
}
