package com.foundation.scpreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foundation.scpreader.AppState
import com.foundation.scpreader.ui.theme.LocalScpScheme

/** Persistent mini-player shown above the bottom nav whenever narration is loaded. */
@Composable
fun MiniPlayer(app: AppState) {
    val c = LocalScpScheme.current
    val state by app.player.state.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = state.hasContent,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val ep = state.episode
        val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        Column(Modifier.fillMaxWidth().background(c.primaryContainer)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(c.primary).clickable { app.player.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                        null, Modifier.size(22.dp), tint = c.onPrimary,
                    )
                }
                Column(Modifier.weight(1f).clickable { app.openPlayer() }) {
                    Text(ep?.title ?: "", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (state.buffering) "Buffering…" else "${fmt(state.positionMs)} / ${fmt(state.durationMs)}",
                        fontSize = 12.sp, color = c.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { app.player.stop() }, contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, null, Modifier.size(22.dp), tint = c.onPrimaryContainer)
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(c.onPrimaryContainer.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(c.primary))
            }
        }
    }
}

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
