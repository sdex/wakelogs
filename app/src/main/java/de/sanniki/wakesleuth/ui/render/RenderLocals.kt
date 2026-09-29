package de.sanniki.wakesleuth.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Renderer bound to the current resources (locale, configuration). */
@Composable
fun rememberEventTextRenderer(): EventTextRenderer {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    return remember(context, configuration) {
        EventTextRenderer(context)
    }
}

@Composable
fun rememberSourceLabelResolver(): SourceLabelResolver {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    return remember(context, configuration) {
        SourceLabelResolver.get(context)
    }
}
