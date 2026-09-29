package de.sanniki.wakesleuth.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.WakeSleuthSettingsScreen
import de.sanniki.wakesleuth.itemSpacing
import de.sanniki.wakesleuth.pageHorizontalPadding
import de.sanniki.wakesleuth.ui.common.PreviewSurface

/** The main screen for a given [state]; every user action leaves as a [WakeSleuthIntent]. */
@Composable
internal fun WakeSleuthContent(
    state: WakeSleuthState,
    onIntent: (WakeSleuthIntent) -> Unit,
) {
    if (state.showSettings) {
        WakeSleuthSettingsScreen(
            settings = state.uiSettings,
            onSettingsChanged = { onIntent(WakeSleuthIntent.SettingsChanged(it)) },
            onBack = { onIntent(WakeSleuthIntent.SettingsBackClicked) },
        )

        return
    }

    val sections = MainSection.entries
    val density = state.uiSettings.cardDensity

    val pagerState = rememberPagerState(
        initialPage = state.selectedSection.ordinal,
        pageCount = { sections.size },
    )

    // The state owns the selected section; the pager follows it and reports swipes back.
    LaunchedEffect(state.selectedSection) {
        val target = state.selectedSection.ordinal

        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            onIntent(WakeSleuthIntent.SectionSelected(sections[page]))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            HeaderToolbar(onOpenSettings = { onIntent(WakeSleuthIntent.OpenSettingsClicked) })
        },
        bottomBar = {
            wakelogsBottomNavigation(
                selectedSection = state.selectedSection,
                onSectionSelected = { onIntent(WakeSleuthIntent.SectionSelected(it)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            Spacer(modifier = Modifier.height(density.itemSpacing))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                beyondViewportPageCount = 1,
                key = { page -> sections[page].name },
            ) { page ->
                val section = sections[page]

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = density.pageHorizontalPadding),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(density.itemSpacing),
                ) {
                    item { CurrentSectionHeader(section = section) }

                    when (section) {
                        MainSection.OVERVIEW -> overviewSection(state, onIntent)
                        MainSection.ANALYSIS -> analysisSection(state, onIntent)
                        MainSection.SESSIONS -> sessionsSection(state, onIntent)
                        MainSection.DIAGNOSTICS -> diagnosticsSection(state, onIntent)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun WakeSleuthContentOverviewPreview() {
    PreviewSurface {
        WakeSleuthContent(state = WakeSleuthState(), onIntent = {})
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun WakeSleuthContentSessionsPreview() {
    PreviewSurface {
        WakeSleuthContent(
            state = WakeSleuthState(selectedSection = MainSection.SESSIONS),
            onIntent = {},
        )
    }
}
