package de.sanniki.wakesleuth.ui.main

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.BuildConfig
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ui.common.PreviewSurface
import de.sanniki.wakesleuth.ui.main.MainSection

@Composable
internal fun wakelogsBottomNavigation(
    selectedSection: MainSection,
    onSectionSelected: (MainSection) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        MainSection.entries.forEach { section ->

            NavigationBarItem(
                selected = selectedSection == section,
                onClick = {
                    onSectionSelected(section)
                },
                icon = {
                    Icon(
                        imageVector = when (section) {
                            MainSection.OVERVIEW -> Icons.Filled.Home
                            MainSection.ANALYSIS -> Icons.Filled.Analytics
                            MainSection.SESSIONS -> Icons.Filled.History
                            MainSection.DIAGNOSTICS -> Icons.Filled.Build
                        },
                        contentDescription = stringResource(section.label),
                    )
                },
                label = {
                    Text(
                        text = stringResource(section.label),
                        maxLines = 1,
                        fontWeight = if (
                            selectedSection == section
                        ) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
internal fun CurrentSectionHeader(section: MainSection) {
    if (
        section == MainSection.OVERVIEW
    ) {
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        if (
            section != MainSection.OVERVIEW
        ) {
            Text(
                text = stringResource(section.label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(2.dp))
        }

        Text(
            text = when (section) {
                MainSection.OVERVIEW -> stringResource(R.string.main_section_overview_subtitle)
                MainSection.ANALYSIS -> stringResource(R.string.main_section_analysis_subtitle)
                MainSection.SESSIONS -> stringResource(R.string.main_section_sessions_subtitle)
                MainSection.DIAGNOSTICS -> stringResource(R.string.main_section_diagnostics_subtitle)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeaderToolbar(onOpenSettings: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.main_header_version, BuildConfig.VERSION_NAME),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.main_settings),
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BottomNavigationPreview() {
    PreviewSurface {
        wakelogsBottomNavigation(selectedSection = MainSection.ANALYSIS, onSectionSelected = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun CurrentSectionHeaderPreview() {
    PreviewSurface {
        CurrentSectionHeader(section = MainSection.SESSIONS)
    }
}

@Preview(showBackground = true)
@Composable
private fun HeaderToolbarPreview() {
    PreviewSurface {
        HeaderToolbar(onOpenSettings = {})
    }
}
