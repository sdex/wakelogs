package de.sanniki.wakesleuth.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import de.sanniki.wakesleuth.AccentColor

private fun darkPrimary(
    accentColor: AccentColor
): Color {
    return when (accentColor) {
        AccentColor.BLUE ->
            WakeBlueDark

        AccentColor.CYAN ->
            WakeCyanDark

        AccentColor.TEAL ->
            WakeTealDark

        AccentColor.GREEN ->
            WakeGreenDark

        AccentColor.GOLD ->
            WakeGoldDark

        AccentColor.ORANGE ->
            WakeYellowDark

        AccentColor.RED ->
            WakeRedDark

        AccentColor.PINK ->
            WakeIceBlueDark

        AccentColor.PURPLE ->
            WakeCornflowerDark

        AccentColor.INDIGO ->
            WakeSageDark
    }
}

private fun darkOnPrimary(
    accentColor: AccentColor
): Color {
    return when (accentColor) {
        AccentColor.BLUE,
        AccentColor.CYAN,
        AccentColor.TEAL,
        AccentColor.GREEN,
        AccentColor.RED,
        AccentColor.PURPLE,
        AccentColor.INDIGO ->
            Color(0xFF071018)

        AccentColor.GOLD,
        AccentColor.ORANGE,
        AccentColor.PINK ->
            Color(0xFF101318)
    }
}

@Composable
fun WakesleuthTheme(
    accentColor: AccentColor = AccentColor.BLUE,
    content: @Composable () -> Unit
) {
    val primary =
        darkPrimary(accentColor)

    val onPrimary =
        darkOnPrimary(accentColor)

    val colorScheme =
        darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer =
                    primary.copy(alpha = 0.24f),
                onPrimaryContainer = DarkText,

                background = DarkBackground,
                onBackground = DarkText,

                surface = DarkSurface,
                onSurface = DarkText,

                surfaceVariant = DarkSurfaceVariant,
                onSurfaceVariant = DarkSecondaryText,

                surfaceContainerLowest =
                    DarkSurfaceLowest,
                surfaceContainerLow =
                    DarkSurfaceLow,
                surfaceContainer =
                    DarkSurface,
                surfaceContainerHigh =
                    DarkSurfaceHigh,
                surfaceContainerHighest =
                    DarkSurfaceHighest,

                surfaceDim = DarkBackground,
                surfaceBright =
                    DarkSurfaceHighest,

                outline = DarkOutline,
                outlineVariant =
                    DarkOutlineVariant            )

    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity =
                view.context as? Activity
                    ?: return@SideEffect

            val window = activity.window

            window.statusBarColor =
                Color.Transparent.toArgb()

            window.navigationBarColor =
                colorScheme.background.toArgb()

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                window.isNavigationBarContrastEnforced =
                    false
            }

            WindowCompat.getInsetsController(
                window,
                view
            ).apply {
                isAppearanceLightStatusBars =
                    false

                isAppearanceLightNavigationBars =
                    false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
