package com.foundation.scpreader.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** App-wide access to the active [ScpScheme]. */
val LocalScpScheme = staticCompositionLocalOf<ScpScheme> {
    error("No ScpScheme provided")
}

@Composable
fun ProvideScpScheme(scheme: ScpScheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScpScheme provides scheme, content = content)
}
