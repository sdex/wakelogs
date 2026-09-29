package de.sanniki.wakesleuth.ui.main

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import de.sanniki.wakesleuth.R

internal enum class MainSection(
    @StringRes val label: Int,
    @StringRes val shortLabel: Int,
) {
    OVERVIEW(label = R.string.main_section_overview, shortLabel = R.string.main_section_overview_short),
    ANALYSIS(label = R.string.main_section_analysis, shortLabel = R.string.main_section_analysis_short),
    SESSIONS(label = R.string.main_section_sessions, shortLabel = R.string.main_section_sessions_short),
    DIAGNOSTICS(label = R.string.main_section_diagnostics, shortLabel = R.string.main_section_diagnostics_short),
}
