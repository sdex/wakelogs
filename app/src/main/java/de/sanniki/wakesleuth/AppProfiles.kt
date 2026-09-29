package de.sanniki.wakesleuth

import android.content.Context

enum class AppProfileTrend {
    MORE_ACTIVE,
    LESS_ACTIVE,
    STABLE,
    NOT_ENOUGH_DATA
}

data class AppProfileData(
    val name: String,
    val sessionsSeen: Int,
    val totalSessions: Int,
    val networkTotalBytes: Long,
    val networkSessionCount: Int,
    val cpuCount: Int,
    val displayCount: Int,
    val companionCount: Int,
    val longestCpuDurationMillis: Long?,
    val trend: AppProfileTrend,
    val recentActivityScore: Double,
    val previousActivityScore: Double
) {
    val averageNetworkBytes: Long
        get() =
            if (networkSessionCount > 0) {
                networkTotalBytes /
                    networkSessionCount
            } else {
                0L
            }
}

fun buildAppProfiles(
    context: Context,
    sessions: List<ArchivedSession>
): List<AppProfileData> {
    data class MutableProfile(
        val sessionIds:
            MutableSet<Long> =
            linkedSetOf(),
        val networkSessionIds:
            MutableSet<Long> =
            linkedSetOf(),
        var networkTotalBytes: Long = 0L,
        var cpuCount: Int = 0,
        var displayCount: Int = 0,
        var companionCount: Int = 0,
        var longestCpuDurationMillis:
            Long? = null
    )

    val profiles =
        linkedMapOf<String, MutableProfile>()

    sessions.forEach { session ->
        session.topApps.forEach { app ->
            val name =
                sourceDisplayName(
                    context,
                    app.name
                )

            val item =
                profiles.getOrPut(name) {
                    MutableProfile()
                }

            item.sessionIds.add(
                session.id
            )

            item.networkSessionIds.add(
                session.id
            )

            item.networkTotalBytes +=
                app.totalBytes
                    .coerceAtLeast(0L)
        }

        session.sources.forEach { source ->
            val name =
                sourceDisplayName(
                    context,
                    source.name
                )

            val item =
                profiles.getOrPut(name) {
                    MutableProfile()
                }

            item.sessionIds.add(
                session.id
            )

            item.cpuCount +=
                source.cpuCount

            item.displayCount +=
                source.displayCount

            item.companionCount +=
                source.companionCount

            source.longestCpuDurationMillis
                ?.let { duration ->
                    item.longestCpuDurationMillis =
                        maxOf(
                            item
                                .longestCpuDurationMillis
                                ?: 0L,
                            duration
                        )
                }
        }
    }

    return profiles.map {
            entry ->

        val trendData =
            calculateAppProfileTrend(
                context = context,
                name = entry.key,
                sessions = sessions
            )

        AppProfileData(
            name = entry.key,
            sessionsSeen =
                entry.value
                    .sessionIds.size,
            totalSessions =
                sessions.size,
            networkTotalBytes =
                entry.value
                    .networkTotalBytes,
            networkSessionCount =
                entry.value
                    .networkSessionIds.size,
            cpuCount =
                entry.value.cpuCount,
            displayCount =
                entry.value.displayCount,
            companionCount =
                entry.value
                    .companionCount,
            longestCpuDurationMillis =
                entry.value
                    .longestCpuDurationMillis,
            trend =
                trendData.trend,
            recentActivityScore =
                trendData.recentScore,
            previousActivityScore =
                trendData.previousScore
        )
    }
        .filter {
            it.sessionsSeen > 0
        }
        .sortedWith(
            compareByDescending<
                AppProfileData
            > {
                it.cpuCount * 100 +
                    it.displayCount * 60 +
                    it.sessionsSeen * 10
            }.thenByDescending {
                it.networkTotalBytes
            }.thenBy {
                it.name
            }
        )
}

private data class AppProfileTrendData(
    val trend: AppProfileTrend,
    val recentScore: Double,
    val previousScore: Double
)

private fun calculateAppProfileTrend(
    context: Context,
    name: String,
    sessions: List<ArchivedSession>
): AppProfileTrendData {
    if (sessions.size < 4) {
        return AppProfileTrendData(
            trend =
                AppProfileTrend
                    .NOT_ENOUGH_DATA,
            recentScore = 0.0,
            previousScore = 0.0
        )
    }

    val ordered =
        sessions.sortedByDescending {
            it.startMillis
        }

    val comparisonSize =
        minOf(
            5,
            ordered.size / 2
        )

    if (comparisonSize < 2) {
        return AppProfileTrendData(
            trend =
                AppProfileTrend
                    .NOT_ENOUGH_DATA,
            recentScore = 0.0,
            previousScore = 0.0
        )
    }

    val recent =
        ordered.take(
            comparisonSize
        )

    val previous =
        ordered
            .drop(comparisonSize)
            .take(comparisonSize)

    if (previous.size < comparisonSize) {
        return AppProfileTrendData(
            trend =
                AppProfileTrend
                    .NOT_ENOUGH_DATA,
            recentScore = 0.0,
            previousScore = 0.0
        )
    }

    val recentScore =
        recent
            .map {
                appActivityScore(
                    context = context,
                    session = it,
                    name = name
                )
            }
            .average()

    val previousScore =
        previous
            .map {
                appActivityScore(
                    context = context,
                    session = it,
                    name = name
                )
            }
            .average()

    val trend =
        when {
            recentScore == 0.0 &&
                previousScore == 0.0 ->
                AppProfileTrend.STABLE

            previousScore == 0.0 &&
                recentScore > 0.0 ->
                AppProfileTrend.MORE_ACTIVE

            recentScore == 0.0 &&
                previousScore > 0.0 ->
                AppProfileTrend.LESS_ACTIVE

            recentScore >=
                previousScore * 1.35 &&
                recentScore -
                    previousScore >= 1.0 ->
                AppProfileTrend.MORE_ACTIVE

            recentScore <=
                previousScore * 0.65 &&
                previousScore -
                    recentScore >= 1.0 ->
                AppProfileTrend.LESS_ACTIVE

            else ->
                AppProfileTrend.STABLE
        }

    return AppProfileTrendData(
        trend = trend,
        recentScore = recentScore,
        previousScore = previousScore
    )
}

private fun appActivityScore(
    context: Context,
    session: ArchivedSession,
    name: String
): Double {
    val normalizedName =
        sourceDisplayName(
            context,
            name
        )

    val source =
        session.sources
            .firstOrNull {
                sourceDisplayName(
                    context,
                    it.name
                ).equals(
                    normalizedName,
                    ignoreCase = true
                )
            }

    val network =
        session.topApps
            .firstOrNull {
                sourceDisplayName(
                    context,
                    it.name
                ).equals(
                    normalizedName,
                    ignoreCase = true
                )
            }

    val cpuScore =
        (
            source?.cpuCount
                ?: 0
        ) * 3.0

    val displayScore =
        (
            source?.displayCount
                ?: 0
        ) * 4.0

    val companionScore =
        (
            source?.companionCount
                ?: 0
        ) * 0.5

    val networkScore =
        when {
            network == null ->
                0.0

            network.totalBytes >=
                50L * 1024L * 1024L ->
                2.0

            network.totalBytes >=
                5L * 1024L * 1024L ->
                1.0

            network.totalBytes > 0L ->
                0.5

            else ->
                0.0
        }

    return cpuScore +
        displayScore +
        companionScore +
        networkScore
}

