package de.sanniki.wakesleuth.ui.diagnostics

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.WakeReasonDiagnostic
import de.sanniki.wakesleuth.domain.WakeLockTags
import de.sanniki.wakesleuth.domain.WakeReasons
import de.sanniki.wakesleuth.parseLogTimestampMillis
import de.sanniki.wakesleuth.ui.render.EventTextRenderer
import de.sanniki.wakesleuth.ui.render.SourceLabelResolver
import java.text.SimpleDateFormat
import java.util.Locale

internal fun readableWakeReason(
    context: Context,
    diagnostic: WakeReasonDiagnostic,
): String {
    if (diagnostic.reason.isNullOrBlank()) {
        return context.getString(R.string.main_unknown)
    }

    return EventTextRenderer(context)
        .wakeReasonLabel(
            reason = WakeReasons.fromPowerManager(diagnostic.reason, diagnostic.details),
            rawReason = diagnostic.reason,
        )
}

internal fun compactWakeReasonDetails(
    context: Context,
    details: String?,
): String {
    val value = details?.trim().orEmpty()

    if (value.isBlank()) {
        return context.getString(R.string.main_none)
    }

    return if (value.length <= 90) {
        value
    } else {
        value.take(87) + "…"
    }
}

internal fun compactJobService(
    context: Context,
    serviceName: String?,
): String {
    val value = serviceName?.trim().orEmpty()

    if (value.isBlank()) {
        return context.getString(R.string.main_unknown_lowercase)
    }

    return if (value.length <= 72) {
        value
    } else {
        value.take(69) + "…"
    }
}

internal fun compactAlarmTag(
    context: Context,
    tag: String?,
): String {
    val value = tag
        ?.removePrefix("*walarm*:")
        ?.trim()
        .orEmpty()

    if (value.isBlank()) {
        return context.getString(R.string.main_unknown_lowercase)
    }

    return if (value.length <= 72) {
        value
    } else {
        value.take(69) + "…"
    }
}

internal fun compactWakeLockTag(
    context: Context,
    tag: String?,
): String {
    val value = tag?.trim().orEmpty()

    if (value.isBlank()) {
        return context.getString(R.string.main_unknown_lowercase)
    }

    if (value.length <= 72) {
        return value
    }

    return value.take(69) + "…"
}

internal fun resolveWakeLockSource(
    context: Context,
    packageName: String?,
): String {
    if (packageName.isNullOrBlank()) {
        return context.getString(R.string.main_unknown)
    }

    return SourceLabelResolver.get(context).labelWithPackage(packageName.trim())
}

internal fun classifyWakeLockTag(
    context: Context,
    tag: String?,
): String {
    if (tag.isNullOrBlank()) {
        return context.getString(R.string.main_kind_unknown_partial_wakelock)
    }

    return EventTextRenderer(context).wakeLockKindLabel(WakeLockTags.kindOf(tag))
}

internal fun formatWakeLockTimestamp(
    context: Context,
    rawTimestamp: String?,
): String {
    val raw = rawTimestamp?.trim().orEmpty()

    if (raw.isBlank()) {
        return context.getString(R.string.main_unknown)
    }

    return formatWakeLockTimestampOrRaw(raw = raw, now = System.currentTimeMillis())
}

/**
 * Formats a year-less log timestamp using the year the shared parser
 * infers (year rollover and Feb 29 included). Falls back to [raw] when
 * it cannot be parsed.
 */
internal fun formatWakeLockTimestampOrRaw(
    raw: String,
    now: Long,
    locale: Locale = Locale.getDefault(),
): String {
    val millis = parseLogTimestampMillis(raw, now)
        ?: return raw

    return SimpleDateFormat("dd.MM.yyyy · HH:mm:ss", locale).format(millis)
}
