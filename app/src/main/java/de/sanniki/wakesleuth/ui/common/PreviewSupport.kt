package de.sanniki.wakesleuth.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.ui.theme.WakesleuthTheme

/** Themed background shared by all `@Preview` functions. */
@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) {
    WakesleuthTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}
