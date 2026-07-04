package com.foundation.scpreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.foundation.scpreader.AppState
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.Mono
import com.foundation.scpreader.ui.theme.LocalScpScheme

private const val SKIP_MS = 15_000L

/** Full-screen narration player: cover art, scrubbable seek bar, skip ±15s and prev/next track. */
@Composable
fun PlayerScreen(app: AppState) {
    val c = LocalScpScheme.current
    val state by app.player.state.collectAsStateWithLifecycle()
    val ep = state.episode

    // Local scrub state so dragging the seek bar is smooth and doesn't fight the 500ms ticker.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val liveFraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val sliderFraction = if (scrubbing) scrubFraction else liveFraction.coerceIn(0f, 1f)
    val shownPositionMs = if (scrubbing && state.durationMs > 0) (scrubFraction * state.durationMs).toLong() else state.positionMs

    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp),
        ) {
            // Top bar: collapse chevron.
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { app.closePlayer() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.KeyboardArrowDown, "Close player", Modifier.size(28.dp), tint = c.onSurface)
                }
                Text(
                    "NOW PLAYING", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp,
                    color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(44.dp))
            }

            Spacer(Modifier.weight(1f))

            // Cover art.
            Box(
                Modifier.fillMaxWidth(0.82f).aspectRatio(1f).align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(28.dp)).background(c.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (ep?.imageUrl != null) {
                    AsyncImage(model = ep.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(AppIcons.Headphones, null, Modifier.size(96.dp), tint = c.onPrimaryContainer.copy(alpha = 0.6f))
                }
            }

            Spacer(Modifier.height(32.dp))

            // Title.
            Text(
                ep?.title ?: "", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                lineHeight = 25.sp, modifier = Modifier.fillMaxWidth(),
            )

            // Source badge (debug): where this audio actually came from.
            com.foundation.scpreader.ui.components.playbackOriginLabel(state.origin, state.cleaned)?.let { label ->
                Spacer(Modifier.height(10.dp))
                Text(
                    label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(6.dp)).background(c.surfaceCHigh).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }

            // Transient notice (fallback reason / skipped sponsor).
            app.playerNotice?.let { n ->
                Spacer(Modifier.height(8.dp))
                Text(n, fontSize = 12.sp, color = c.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))

            // Seek bar.
            Slider(
                value = sliderFraction,
                onValueChange = { scrubbing = true; scrubFraction = it },
                onValueChangeFinished = {
                    if (state.durationMs > 0) app.player.seekTo((scrubFraction * state.durationMs).toLong())
                    scrubbing = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = c.primary,
                    activeTrackColor = c.primary,
                    inactiveTrackColor = c.surfaceCHighest,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            // SponsorBlock segment markers (YouTube streaming only; trimmed downloads have none).
            if (state.origin == com.foundation.scpreader.playback.PlaybackOrigin.YOUTUBE && state.segments.isNotEmpty()) {
                com.foundation.scpreader.ui.components.SegmentBar(
                    state.segments, state.durationMs, 5.dp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(shownPositionMs), fontFamily = Mono, fontSize = 12.sp, color = c.onSurfaceVariant)
                Text(if (state.buffering) "Buffering…" else fmt(state.durationMs), fontFamily = Mono, fontSize = 12.sp, color = c.onSurfaceVariant)
            }

            Spacer(Modifier.height(20.dp))

            // Transport controls.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CtrlButton(AppIcons.SkipPrevious, "Previous", 30.dp) { app.playerPrevious() }
                CtrlButton(AppIcons.FastRewind, "Back 15 seconds", 32.dp) { app.player.seekBy(-SKIP_MS) }

                // Big play/pause.
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(c.primary).clickable { app.player.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play", Modifier.size(40.dp), tint = c.onPrimary,
                    )
                }

                CtrlButton(AppIcons.FastForward, "Forward 15 seconds", 32.dp) { app.player.seekBy(SKIP_MS) }
                CtrlButton(AppIcons.SkipNext, "Next", 30.dp) { app.playerNext() }
            }

            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(12.dp))
        }
    }
}

@Composable
private fun CtrlButton(icon: ImageVector, desc: String, iconSize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val c = LocalScpScheme.current
    Box(
        Modifier.size(56.dp).clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, Modifier.size(iconSize), tint = c.onSurface)
    }
}

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
