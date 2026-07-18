package de.sanniki.wakesleuth

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AccentColor(
    val label: String
) {
    BLUE("Leuchtblau"),
    CYAN("Cyanblau"),
    TEAL("Tiefes Türkis"),
    GREEN("Mittelgrün"),
    GOLD("Gold"),
    ORANGE("Sonnengelb"),
    RED("Korallrot"),
    PINK("Eisblau"),
    PURPLE("Kornblumenblau"),
    INDIGO("Salbeigrün")
}

enum class CardDensity(
    val label: String
) {
    COMPACT("Kompakt"),
    NORMAL("Normal"),
    COMFORTABLE("Großzügig")
}

enum class DetailLevel(
    val label: String
) {
    SIMPLE("Einfach"),
    NORMAL("Normal"),
    EXPERT("Experte")
}

enum class UiProfile(
    val label: String,
    val description: String
) {
    SIMPLE(
        "Einfach",
        "Start, Tagesübersicht und klare Hinweise"
    ),

    SLEEP(
        "Analyse",
        "Timeline, Schlafanalyse und Ursachenhinweise"
    ),

    DEVELOPER(
        "Experte",
        "Shizuku, Wakelocks, Alarme und Rohdetails"
    )
}

data class WakeSleuthUiSettings(
    val accentColor: AccentColor =
        AccentColor.BLUE,

    val cardDensity: CardDensity =
        CardDensity.COMFORTABLE,

    val detailLevel: DetailLevel =
        DetailLevel.SIMPLE,

    val showDailyStatistics: Boolean =
        true,

    val showNightAnalysis: Boolean =
        true,

    val showSourceStatistics: Boolean =
        false,

    val showShizukuDiagnostics: Boolean =
        false
)

object WakeSleuthUiSettingsStore {

    private const val PREFS_NAME =
        "wakesleuth_ui"

    fun load(
        context: Context
    ): WakeSleuthUiSettings {
        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        return WakeSleuthUiSettings(
            accentColor =
                enumValueOrDefault(
                    prefs.getString(
                        "accent_color",
                        null
                    ),
                    AccentColor.BLUE
                ),

            cardDensity =
                enumValueOrDefault(
                    prefs.getString(
                        "card_density",
                        null
                    ),
                    CardDensity.COMFORTABLE
                ),

            detailLevel =
                enumValueOrDefault(
                    prefs.getString(
                        "detail_level",
                        null
                    ),
                    DetailLevel.SIMPLE
                ),

            showDailyStatistics =
                prefs.getBoolean(
                    "show_daily_statistics",
                    true
                ),

            showNightAnalysis =
                prefs.getBoolean(
                    "show_night_analysis",
                    true
                ),

            showSourceStatistics =
                prefs.getBoolean(
                    "show_source_statistics",
                    false
                ),

            showShizukuDiagnostics =
                prefs.getBoolean(
                    "show_shizuku_diagnostics",
                    false
                )
        )
    }

    fun save(
        context: Context,
        settings: WakeSleuthUiSettings
    ) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                "accent_color",
                settings.accentColor.name
            )
            .putString(
                "card_density",
                settings.cardDensity.name
            )
            .putString(
                "detail_level",
                settings.detailLevel.name
            )
            .putBoolean(
                "show_daily_statistics",
                settings.showDailyStatistics
            )
            .putBoolean(
                "show_night_analysis",
                settings.showNightAnalysis
            )
            .putBoolean(
                "show_source_statistics",
                settings.showSourceStatistics
            )
            .putBoolean(
                "show_shizuku_diagnostics",
                settings.showShizukuDiagnostics
            )
            .apply()
    }

    private inline fun <
        reified T : Enum<T>
    > enumValueOrDefault(
        value: String?,
        defaultValue: T
    ): T {
        return runCatching {
            enumValueOf<T>(
                value ?: defaultValue.name
            )
        }.getOrDefault(defaultValue)
    }
}

val CardDensity.pageHorizontalPadding: Dp
    get() =
        when (this) {
            CardDensity.COMPACT -> 10.dp
            CardDensity.NORMAL -> 16.dp
            CardDensity.COMFORTABLE -> 20.dp
        }

val CardDensity.itemSpacing: Dp
    get() =
        when (this) {
            CardDensity.COMPACT -> 5.dp
            CardDensity.NORMAL -> 8.dp
            CardDensity.COMFORTABLE -> 12.dp
        }

fun settingsForProfile(
    profile: UiProfile,
    current: WakeSleuthUiSettings
): WakeSleuthUiSettings {
    return when (profile) {
        UiProfile.SLEEP ->
            current.copy(
                cardDensity =
                    CardDensity.NORMAL,
                detailLevel =
                    DetailLevel.NORMAL,
                showDailyStatistics = true,
                showNightAnalysis = true,
                showSourceStatistics = true,
                showShizukuDiagnostics = false
            )

        UiProfile.DEVELOPER ->
            current.copy(
                cardDensity =
                    CardDensity.COMPACT,
                detailLevel =
                    DetailLevel.EXPERT,
                showDailyStatistics = true,
                showNightAnalysis = true,
                showSourceStatistics = true,
                showShizukuDiagnostics = true
            )

        UiProfile.SIMPLE ->
            current.copy(
                cardDensity =
                    CardDensity.COMFORTABLE,
                detailLevel =
                    DetailLevel.SIMPLE,
                showDailyStatistics = true,
                showNightAnalysis = true,
                showSourceStatistics = false,
                showShizukuDiagnostics = false
            )
    }
}

@Composable
fun WakeSleuthSettingsScreen(
    settings: WakeSleuthUiSettings,
    onSettingsChanged:
        (WakeSleuthUiSettings) -> Unit,
    onBack: () -> Unit
) {
    val advancedDashboardOptionsExpanded =
        androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets =
            WindowInsets.safeDrawing
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    horizontal =
                        settings.cardDensity
                            .pageHorizontalPadding
                ),
            contentPadding =
                PaddingValues(
                    top = 16.dp,
                    bottom = 28.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    settings.cardDensity
                        .itemSpacing
                )
        ) {
            item {
                Card(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier =
                            Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = "Personalisierung",
                            style =
                                MaterialTheme.typography
                                    .headlineSmall,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(
                            text =
                                "Mach wakelogs zu deiner eigenen Analysezentrale.",
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        OutlinedButton(
                            onClick = onBack,
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text("Zurück zum Dashboard")
                        }
                    }
                }
            }

            item {
                val activeProfile =
                    matchingUiProfile(
                        settings
                    )

                SettingsSection(
                    title = "Ansichtsmodi"
                ) {
                    UiProfile.entries.forEach {
                            profile ->

                        ProfileChoiceButton(
                            profile = profile,
                            selected =
                                activeProfile == profile,
                            onClick = {
                                onSettingsChanged(
                                    settingsForProfile(
                                        profile,
                                        settings
                                    )
                                )
                            }
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Erscheinungsbild"
                ) {
                    ChoiceChips(
                        title = "Akzentfarbe",
                        values =
                            AccentColor.entries,
                        selected =
                            settings.accentColor,
                        label = {
                            it.label
                        },
                        onSelected = {
                            onSettingsChanged(
                                settings.copy(
                                    accentColor = it
                                )
                            )
                        }
                    )

                    ChoiceChips(
                        title = "Kartendarstellung",
                        values =
                            CardDensity.entries,
                        selected =
                            settings.cardDensity,
                        label = {
                            it.label
                        },
                        onSelected = {
                            onSettingsChanged(
                                settings.copy(
                                    cardDensity = it
                                )
                            )
                        }
                    )

                    ChoiceChips(
                        title = "Detailgrad",
                        values =
                            DetailLevel.entries,
                        selected =
                            settings.detailLevel,
                        label = {
                            it.label
                        },
                        onSelected = {
                            onSettingsChanged(
                                settings.copy(
                                    detailLevel = it
                                )
                            )
                        }
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Erweiterte Anzeigeoptionen"
                ) {
                    Text(
                        text =
                            "Feinsteuerung für einzelne Dashboard-Bereiche. Die Ansichtsmodi oben setzen diese Optionen automatisch.",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    OutlinedButton(
                        onClick = {
                            advancedDashboardOptionsExpanded.value =
                                !advancedDashboardOptionsExpanded.value
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (advancedDashboardOptionsExpanded.value) {
                                "Optionen ausblenden"
                            } else {
                                "Optionen anzeigen"
                            }
                        )
                    }

                    if (advancedDashboardOptionsExpanded.value) {
                        SettingsSwitchRow(
                            title = "Tagesübersicht",
                            description =
                                "Display-Aktivierungen und Zuordnungen",
                            checked =
                                settings.showDailyStatistics,
                            onCheckedChange = {
                                onSettingsChanged(
                                    settings.copy(
                                        showDailyStatistics =
                                            it
                                    )
                                )
                            }
                        )

                        HorizontalDivider()

                        SettingsSwitchRow(
                            title = "Nachtanalyse",
                            description =
                                "Auswertung des letzten Überwachungszeitraums",
                            checked =
                                settings.showNightAnalysis,
                            onCheckedChange = {
                                onSettingsChanged(
                                    settings.copy(
                                        showNightAnalysis =
                                            it
                                    )
                                )
                            }
                        )

                        HorizontalDivider()

                        SettingsSwitchRow(
                            title = "Aktivste Quellen",
                            description =
                                "Rangliste zeitlich zugeordneter Apps und Systemdienste",
                            checked =
                                settings.showSourceStatistics,
                            onCheckedChange = {
                                onSettingsChanged(
                                    settings.copy(
                                        showSourceStatistics =
                                            it
                                    )
                                )
                            }
                        )

                        HorizontalDivider()

                        SettingsSwitchRow(
                            title = "Shizuku-Systemdiagnose",
                            description =
                                "Wakelocks, Jobs, Alarme und Aufweckgründe",
                            checked =
                                settings.showShizukuDiagnostics,
                            onCheckedChange = {
                                onSettingsChanged(
                                    settings.copy(
                                        showShizukuDiagnostics =
                                            it
                                    )
                                )
                            }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onSettingsChanged(
                            WakeSleuthUiSettings()
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text("Standardeinstellungen")
                }
            }

            item {
            }
        }
    }
}

private fun matchingUiProfile(
    settings: WakeSleuthUiSettings
): UiProfile? {
    return UiProfile.entries.firstOrNull {
            profile ->

        val profileSettings =
            settingsForProfile(
                profile = profile,
                current = settings
            )

        settings.cardDensity ==
            profileSettings.cardDensity &&
            settings.detailLevel ==
                profileSettings.detailLevel &&
            settings.showDailyStatistics ==
                profileSettings.showDailyStatistics &&
            settings.showNightAnalysis ==
                profileSettings.showNightAnalysis &&
            settings.showSourceStatistics ==
                profileSettings.showSourceStatistics &&
            settings.showShizukuDiagnostics ==
                profileSettings.showShizukuDiagnostics
    }
}

@Composable
private fun ProfileChoiceButton(
    profile: UiProfile,
    selected: Boolean,
    onClick: () -> Unit
) {
    val preview =
        when (profile) {
            UiProfile.SIMPLE ->
                "Ruhig · wenig Technik · klare Hinweise"

            UiProfile.SLEEP ->
                "Timeline · Schlafanalyse · Ursachen"

            UiProfile.DEVELOPER ->
                "Shizuku · Wakelocks · Rohdetails"
        }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text =
                    if (selected) {
                        "${profile.label} · Aktiv"
                    } else {
                        profile.label
                    },
                fontWeight = FontWeight.Bold
            )

            Text(
                text = profile.description,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = preview,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .labelSmall
            )
        }
    }

    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    MaterialTheme.colorScheme
                        .primaryContainer,
                contentColor =
                    MaterialTheme.colorScheme
                        .onPrimaryContainer
            )
        ) {
            content()
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )

            content()
        }
    }
}

@Composable
private fun <T> ChoiceChips(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Column {
        Text(
            text = title,
            style =
                MaterialTheme.typography
                    .labelLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                ),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            values.forEach { value ->
                FilterChip(
                    selected =
                        selected == value,
                    onClick = {
                        onSelected(value)
                    },
                    label = {
                        Text(label(value))
                    }
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                text = description,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }

        Switch(
            checked = checked,
            onCheckedChange =
                onCheckedChange
        )
    }
}
