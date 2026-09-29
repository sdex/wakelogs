package de.sanniki.wakesleuth

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AccentColor(
    @StringRes val labelRes: Int,
) {
    BLUE(R.string.settings_accent_blue),
    CYAN(R.string.settings_accent_cyan),
    TEAL(R.string.settings_accent_teal),
    GREEN(R.string.settings_accent_green),
    GOLD(R.string.settings_accent_gold),
    ORANGE(R.string.settings_accent_orange),
    RED(R.string.settings_accent_red),
    PINK(R.string.settings_accent_pink),
    PURPLE(R.string.settings_accent_purple),
    INDIGO(R.string.settings_accent_indigo),
}

enum class CardDensity(
    @StringRes val labelRes: Int,
) {
    COMPACT(R.string.settings_density_compact),
    NORMAL(R.string.settings_density_normal),
    COMFORTABLE(R.string.settings_density_comfortable),
}

enum class DetailLevel(
    @StringRes val labelRes: Int,
) {
    SIMPLE(R.string.settings_level_simple),
    NORMAL(R.string.settings_level_normal),
    EXPERT(R.string.settings_level_expert),
}

enum class UiProfile(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    SIMPLE(R.string.settings_level_simple, R.string.settings_profile_simple_description),

    SLEEP(R.string.settings_profile_analysis, R.string.settings_profile_sleep_description),

    DEVELOPER(R.string.settings_level_expert, R.string.settings_profile_developer_description),
}

data class WakeSleuthUiSettings(
    val accentColor: AccentColor = AccentColor.BLUE,
    val cardDensity: CardDensity = CardDensity.COMFORTABLE,
    val detailLevel: DetailLevel = DetailLevel.SIMPLE,
    val showDailyStatistics: Boolean = true,
    val showNightAnalysis: Boolean = true,
    val showSourceStatistics: Boolean = false,
    val showShizukuDiagnostics: Boolean = false,
)

object WakeSleuthUiSettingsStore {
    private const val PREFS_NAME = "wakesleuth_ui"

    fun load(context: Context): WakeSleuthUiSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return WakeSleuthUiSettings(
            accentColor = enumValueOrDefault(prefs.getString("accent_color", null), AccentColor.BLUE),
            cardDensity = enumValueOrDefault(prefs.getString("card_density", null), CardDensity.COMFORTABLE),
            detailLevel = enumValueOrDefault(prefs.getString("detail_level", null), DetailLevel.SIMPLE),
            showDailyStatistics = prefs.getBoolean("show_daily_statistics", true),
            showNightAnalysis = prefs.getBoolean("show_night_analysis", true),
            showSourceStatistics = prefs.getBoolean("show_source_statistics", false),
            showShizukuDiagnostics = prefs.getBoolean("show_shizuku_diagnostics", false),
        )
    }

    fun save(
        context: Context,
        settings: WakeSleuthUiSettings,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                "accent_color",
                settings.accentColor.name,
            ).putString(
                "card_density",
                settings.cardDensity.name,
            ).putString(
                "detail_level",
                settings.detailLevel.name,
            ).putBoolean(
                "show_daily_statistics",
                settings.showDailyStatistics,
            ).putBoolean(
                "show_night_analysis",
                settings.showNightAnalysis,
            ).putBoolean(
                "show_source_statistics",
                settings.showSourceStatistics,
            ).putBoolean(
                "show_shizuku_diagnostics",
                settings.showShizukuDiagnostics,
            ).apply()
    }

    private inline fun <
        reified T : Enum<T>,
    > enumValueOrDefault(
        value: String?,
        defaultValue: T,
    ): T =
        runCatching {
            enumValueOf<T>(value ?: defaultValue.name)
        }.getOrDefault(defaultValue)
}

val CardDensity.pageHorizontalPadding: Dp
    get() = when (this) {
        CardDensity.COMPACT -> 10.dp
        CardDensity.NORMAL -> 16.dp
        CardDensity.COMFORTABLE -> 20.dp
    }

val CardDensity.itemSpacing: Dp
    get() = when (this) {
        CardDensity.COMPACT -> 5.dp
        CardDensity.NORMAL -> 8.dp
        CardDensity.COMFORTABLE -> 12.dp
    }

fun settingsForProfile(
    profile: UiProfile,
    current: WakeSleuthUiSettings,
): WakeSleuthUiSettings =
    when (profile) {
        UiProfile.SLEEP -> {
            current.copy(
                cardDensity = CardDensity.NORMAL,
                detailLevel = DetailLevel.NORMAL,
                showDailyStatistics = true,
                showNightAnalysis = true,
                showSourceStatistics = true,
                showShizukuDiagnostics = false,
            )
        }

        UiProfile.DEVELOPER -> {
            current.copy(
                cardDensity = CardDensity.COMPACT,
                detailLevel = DetailLevel.EXPERT,
                showDailyStatistics = true,
                showNightAnalysis = true,
                showSourceStatistics = true,
                showShizukuDiagnostics = true,
            )
        }

        UiProfile.SIMPLE -> {
            current.copy(
                cardDensity = CardDensity.COMFORTABLE,
                detailLevel = DetailLevel.SIMPLE,
                showDailyStatistics = true,
                showNightAnalysis = true,
                showSourceStatistics = false,
                showShizukuDiagnostics = false,
            )
        }
    }

@Composable
fun WakeSleuthSettingsScreen(
    settings: WakeSleuthUiSettings,
    onSettingsChanged: (WakeSleuthUiSettings) -> Unit,
    onBack: () -> Unit,
) {
    val advancedDashboardOptionsExpanded = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = settings.cardDensity.pageHorizontalPadding),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(settings.cardDensity.itemSpacing),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_back_to_dashboard))
                        }
                    }
                }
            }

            item {
                val activeProfile = matchingUiProfile(settings)

                SettingsSection(
                    title = stringResource(R.string.settings_view_modes),
                ) {
                    UiProfile.entries.forEach { profile ->

                        ProfileChoiceButton(
                            profile = profile,
                            selected = activeProfile == profile,
                            onClick = {
                                onSettingsChanged(settingsForProfile(profile, settings))
                            },
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_appearance),
                ) {
                    ChoiceChips(
                        title = stringResource(R.string.settings_accent_color),
                        values = AccentColor.entries,
                        selected = settings.accentColor,
                        labelRes = {
                            it.labelRes
                        },
                        onSelected = {
                            onSettingsChanged(settings.copy(accentColor = it))
                        },
                    )

                    ChoiceChips(
                        title = stringResource(R.string.settings_card_layout),
                        values = CardDensity.entries,
                        selected = settings.cardDensity,
                        labelRes = {
                            it.labelRes
                        },
                        onSelected = {
                            onSettingsChanged(settings.copy(cardDensity = it))
                        },
                    )

                    ChoiceChips(
                        title = stringResource(R.string.settings_detail_level),
                        values = DetailLevel.entries,
                        selected = settings.detailLevel,
                        labelRes = {
                            it.labelRes
                        },
                        onSelected = {
                            onSettingsChanged(settings.copy(detailLevel = it))
                        },
                    )
                }
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_advanced_display_options),
                ) {
                    Text(
                        text = stringResource(R.string.settings_advanced_display_options_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    OutlinedButton(
                        onClick = {
                            advancedDashboardOptionsExpanded.value = !advancedDashboardOptionsExpanded.value
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (advancedDashboardOptionsExpanded.value) {
                                stringResource(R.string.settings_hide_options)
                            } else {
                                stringResource(R.string.settings_show_options)
                            },
                        )
                    }

                    if (advancedDashboardOptionsExpanded.value) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_daily_overview),
                            description = stringResource(R.string.settings_daily_overview_description),
                            checked = settings.showDailyStatistics,
                            onCheckedChange = {
                                onSettingsChanged(settings.copy(showDailyStatistics = it))
                            },
                        )

                        HorizontalDivider()

                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_night_analysis),
                            description = stringResource(R.string.settings_night_analysis_description),
                            checked = settings.showNightAnalysis,
                            onCheckedChange = {
                                onSettingsChanged(settings.copy(showNightAnalysis = it))
                            },
                        )

                        HorizontalDivider()

                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_most_active_sources),
                            description = stringResource(R.string.settings_most_active_sources_description),
                            checked = settings.showSourceStatistics,
                            onCheckedChange = {
                                onSettingsChanged(settings.copy(showSourceStatistics = it))
                            },
                        )

                        HorizontalDivider()

                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_shizuku_diagnostics),
                            description = stringResource(R.string.settings_shizuku_diagnostics_description),
                            checked = settings.showShizukuDiagnostics,
                            onCheckedChange = {
                                onSettingsChanged(settings.copy(showShizukuDiagnostics = it))
                            },
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onSettingsChanged(WakeSleuthUiSettings())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_restore_defaults))
                }
            }

            item {
            }
        }
    }
}

private fun matchingUiProfile(settings: WakeSleuthUiSettings): UiProfile? =
    UiProfile.entries.firstOrNull { profile ->

        val profileSettings = settingsForProfile(profile = profile, current = settings)

        settings.cardDensity == profileSettings.cardDensity &&
            settings.detailLevel ==
            profileSettings.detailLevel &&
            settings.showDailyStatistics ==
            profileSettings.showDailyStatistics &&
            settings.showNightAnalysis ==
            profileSettings.showNightAnalysis &&
            settings.showSourceStatistics ==
            profileSettings.showSourceStatistics &&
            settings.showShizukuDiagnostics == profileSettings.showShizukuDiagnostics
    }

@Composable
private fun ProfileChoiceButton(
    profile: UiProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val preview = when (profile) {
        UiProfile.SIMPLE -> stringResource(R.string.settings_profile_simple_preview)
        UiProfile.SLEEP -> stringResource(R.string.settings_profile_sleep_preview)
        UiProfile.DEVELOPER -> stringResource(R.string.settings_profile_developer_preview)
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (selected) {
                    stringResource(R.string.settings_profile_active, stringResource(profile.labelRes))
                } else {
                    stringResource(profile.labelRes)
                },
                fontWeight = FontWeight.Bold,
            )

            Text(text = stringResource(profile.descriptionRes), style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = preview,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            content()
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            content()
        }
    }
}

@Composable
private fun <T> ChoiceChips(
    title: String,
    values: List<T>,
    selected: T,
    labelRes: (T) -> Int,
    onSelected: (T) -> Unit,
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = {
                        onSelected(value)
                    },
                    label = {
                        Text(stringResource(labelRes(value)))
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        ) {
            Text(text = title, fontWeight = FontWeight.SemiBold)

            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
