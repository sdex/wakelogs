package de.sanniki.wakesleuth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import de.sanniki.wakesleuth.ui.main.WakeSleuthScreen
import de.sanniki.wakesleuth.ui.theme.WakesleuthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        setContent {
            var uiSettings by remember { mutableStateOf(WakeSleuthUiSettingsStore.load(this)) }

            WakesleuthTheme(
                accentColor = uiSettings.accentColor,
            ) {
                WakeSleuthScreen(
                    uiSettings = uiSettings,
                    onUiSettingsChanged = {
                        uiSettings = it

                        WakeSleuthUiSettingsStore.save(this, it)
                    },
                )
            }
        }
    }
}
