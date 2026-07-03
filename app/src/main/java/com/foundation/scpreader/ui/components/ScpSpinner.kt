package com.foundation.scpreader.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.foundation.scpreader.R
import com.foundation.scpreader.ui.theme.LocalScpScheme

/** SCP containment-cog loading spinner (Lottie). Picks a light/dark variant to stay visible. */
@Composable
fun ScpSpinner(size: Int = 72, dark: Boolean? = null) {
    val isDark = dark ?: LocalScpScheme.current.surface.luminanceIsDark()
    val res = if (isDark) R.raw.spinner_dark else R.raw.spinner_light
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(size.dp))
}

/**
 * Pull-to-refresh spinner whose ring draws on from the pull [fraction] (0→full circle),
 * then spins continuously once [refreshing]. Mirrors the stock determinate→indeterminate flow.
 */
@Composable
fun ScpPullSpinner(fraction: Float, refreshing: Boolean, size: Int = 46, dark: Boolean? = null) {
    val isDark = dark ?: LocalScpScheme.current.surface.luminanceIsDark()
    val res = if (isDark) R.raw.spinner_pull_dark else R.raw.spinner_pull_light
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val loop by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever, isPlaying = refreshing)
    val progress = if (refreshing) loop else fraction.coerceIn(0f, 1f)
    LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(size.dp))
}

private fun androidx.compose.ui.graphics.Color.luminanceIsDark(): Boolean =
    (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5
