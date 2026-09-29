package de.sanniki.wakesleuth.domain

/** Per-source activity of one session, as kept in the archive. */
data class SessionSourceStat(
    val source: SourceRef,
    val cpuCount: Int,
    val displayCount: Int,
    val companionCount: Int,
    val longestCpuAwakeMs: Long?
)

/** Source a CPU wakeup is attributed to: its primary evidence. */
fun CpuWakeupEvent.primarySource(): SourceRef? =
    primaryEvidence?.let { SourceClassifier.classify(it.packageName, it.rawSource) }

fun CpuEvidence.source(): SourceRef =
    SourceClassifier.classify(packageName, rawSource)

/** A source seen around a screen-on and whether it only accompanied it. */
data class ScreenOnSource(
    val source: SourceRef,
    val companion: Boolean
)

/**
 * Every source linked to a screen-on: the notification cause and all
 * hints with a known package. A source counts once per screen-on and is
 * companion activity if any of its hints only accompanied the wake-up.
 */
fun ScreenOnEvent.sources(): List<ScreenOnSource> {
    val result = linkedMapOf<String, ScreenOnSource>()

    fun add(source: SourceRef, companion: Boolean) {
        val existing = result[source.groupKey]
        result[source.groupKey] =
            ScreenOnSource(
                source = existing?.source ?: source,
                companion = (existing?.companion ?: false) || companion
            )
    }

    notificationCause?.let {
        add(SourceClassifier.classify(it.packageName), companion = false)
    }

    wakeLockHints.forEach { hint ->
        val packageName = hint.packageName ?: return@forEach
        add(
            SourceClassifier.classify(packageName, hint.tag),
            companion = CauseAssessment.relationOf(this, hint) == HintRelation.COMPANION
        )
    }

    alarmHints.forEach { hint ->
        val packageName = hint.packageName ?: return@forEach
        add(
            SourceClassifier.classify(packageName, hint.tag),
            companion = CauseAssessment.relationOf(this, hint) == HintRelation.COMPANION
        )
    }

    jobHints.forEach { hint ->
        val packageName = hint.packageName ?: return@forEach
        add(
            SourceClassifier.classify(packageName, hint.serviceName),
            companion = CauseAssessment.relationOf(this, hint) == HintRelation.COMPANION
        )
    }

    return result.values.toList()
}

object SessionSources {

    /**
     * Aggregates display and CPU wakeups per source, merged by
     * [SourceRef.groupKey]. The stored reference keeps only what the key
     * needs, so it is stable across sessions.
     */
    fun aggregate(events: List<RecordedEvent>): List<SessionSourceStat> {
        class Counts(val source: SourceRef) {
            var cpu = 0
            var display = 0
            var companion = 0
            var longest: Long? = null
        }

        val counts = linkedMapOf<String, Counts>()

        fun counts(source: SourceRef): Counts =
            counts.getOrPut(source.groupKey) { Counts(canonical(source)) }

        events.forEach { event ->
            when (event) {
                is CpuWakeupEvent -> {
                    val source = event.primarySource() ?: return@forEach
                    val item = counts(source)
                    item.cpu++
                    event.awakeMs?.let { awake ->
                        item.longest = maxOf(item.longest ?: 0L, awake)
                    }
                }

                is ScreenOnEvent ->
                    event.sources().forEach { linked ->
                        val item = counts(linked.source)
                        item.display++
                        if (linked.companion) {
                            item.companion++
                        }
                    }

                else -> Unit
            }
        }

        return counts.values
            .filter { it.cpu > 0 || it.display > 0 }
            .map {
                SessionSourceStat(
                    source = it.source,
                    cpuCount = it.cpu,
                    displayCount = it.display,
                    companionCount = it.companion,
                    longestCpuAwakeMs = it.longest
                )
            }
    }

    /** Reduces a reference to the fields its group key is built from. */
    fun canonical(source: SourceRef): SourceRef =
        when (source.kind) {
            SourceKind.APP ->
                SourceRef(SourceKind.APP, packageName = source.packageName ?: source.rawSource)

            SourceKind.UID_ONLY ->
                SourceRef(SourceKind.UID_ONLY, rawSource = source.rawSource)

            SourceKind.UNKNOWN_SYSTEM ->
                SourceRef(
                    SourceKind.UNKNOWN_SYSTEM,
                    rawSource = SourceClassifier.technicalName(source.rawSource)
                )

            else ->
                SourceRef(source.kind)
        }
}
