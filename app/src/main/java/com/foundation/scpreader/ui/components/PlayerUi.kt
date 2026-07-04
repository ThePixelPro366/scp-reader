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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foundation.scpreader.AppState
import com.foundation.scpreader.playback.PlaybackOrigin
import com.foundation.scpreader.playback.SkipSegment
import com.foundation.scpreader.playback.SponsorCategory
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ep?.title ?: "", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        playbackOriginLabel(state.origin, state.cleaned)?.let { label ->
                            Text(
                                label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c.onPrimaryContainer,
                                modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(c.onPrimaryContainer.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    val notice = app.playerNotice
                    Text(
                        when {
                            notice != null -> notice
                            state.buffering -> "Buffering…"
                            else -> "${fmt(state.positionMs)} / ${fmt(state.durationMs)}"
                        },
                        fontSize = 12.sp, color = c.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { app.player.stop() }, contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, null, Modifier.size(22.dp), tint = c.onPrimaryContainer)
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(c.onPrimaryContainer.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(c.primary))
                if (state.origin == PlaybackOrigin.YOUTUBE) SegmentBar(state.segments, state.durationMs, 3.dp)
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

/** Human label for the audio source the current media resolved to (debug badge). */
fun playbackOriginLabel(origin: PlaybackOrigin?, cleaned: Boolean = false): String? = when (origin) {
    PlaybackOrigin.YOUTUBE -> "YouTube"
    PlaybackOrigin.PODCAST -> "Podcast"
    PlaybackOrigin.LOCAL -> if (cleaned) "Downloaded · ad-free" else "Downloaded"
    null -> null
}

/** SponsorBlock-style color for a segment category. */
fun segmentColor(category: String): Color = when (category) {
    SponsorCategory.SPONSOR -> Color(0xFF00D400)          // green
    SponsorCategory.SELF_PROMO -> Color(0xFFFFFF00)       // yellow
    SponsorCategory.INTERACTION -> Color(0xFF00D9D9)      // cyan
    SponsorCategory.INTRO -> Color(0xFF00A0FF)            // blue
    SponsorCategory.OUTRO -> Color(0xFF0247D3)            // deep blue
    SponsorCategory.PREVIEW -> Color(0xFF00B4C4)          // teal
    SponsorCategory.MUSIC_OFFTOPIC -> Color(0xFFFF9900)   // orange
    SponsorCategory.FILLER -> Color(0xFF7300FF)           // violet
    else -> Color(0xFFCCCCCC)
}

/** Draws SponsorBlock segment markers across a full-width timeline bar (color per category). */
@Composable
fun SegmentBar(segments: List<SkipSegment>, durationMs: Long, height: Dp, modifier: Modifier = Modifier) {
    if (segments.isEmpty() || durationMs <= 0) return
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val w = maxWidth
        segments.forEach { seg ->
            val startFrac = (seg.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val endFrac = (seg.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
            if (endFrac > startFrac) {
                Box(
                    Modifier.offset(x = w * startFrac)
                        .width((w * (endFrac - startFrac)).coerceAtLeast(2.dp))
                        .height(height)
                        .clip(RoundedCornerShape(1.dp))
                        .background(segmentColor(seg.category)),
                )
            }
        }
    }
}
