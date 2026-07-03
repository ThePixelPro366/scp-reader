package com.foundation.scpreader.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.foundation.scpreader.data.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI-facing playback state for the mini-player. */
data class PlaybackState(
    val episode: Episode? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val buffering: Boolean = false,
) {
    val hasContent: Boolean get() = episode != null
}

/**
 * App-scoped ExoPlayer wrapper driving the persistent mini-player. Exposes a [StateFlow]
 * so Compose can render play/pause, progress and the current episode from any screen.
 */
class PlayerController(context: Context, scope: CoroutineScope) {

    private val appContext = context.applicationContext
    // ExoPlayer is single-threaded and must be touched only from the main thread.
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val player: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                push()
                // Persist on pause so a position is captured the moment playback halts.
                if (!isPlaying) persistNow()
            }
            override fun onPlaybackStateChanged(playbackState: Int) = push()
        })
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Set by the DI container to persist positions; (audioUrl, positionMs, durationMs). */
    var onPositionPersist: ((String, Long, Long) -> Unit)? = null

    init {
        // Progress ticker on the main thread while something is loaded; also persists
        // position periodically (~every 5s) so an unexpected process death loses little.
        mainScope.launch {
            var ticks = 0
            while (true) {
                if (player.isPlaying || player.playbackState == Player.STATE_READY) push()
                if (player.isPlaying && ++ticks >= 10) { persistNow(); ticks = 0 }
                delay(500)
            }
        }
    }

    fun play(episode: Episode, startPositionMs: Long = 0) {
        val current = _state.value.episode
        if (current?.audioUrl == episode.audioUrl) { togglePlayPause(); return }
        // Capture the outgoing episode's position before switching tracks.
        persistNow()
        player.setMediaItem(MediaItem.fromUri(episode.audioUrl))
        player.prepare()
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.playWhenReady = true
        _state.value = PlaybackState(episode = episode, isPlaying = true, positionMs = startPositionMs.coerceAtLeast(0), buffering = true)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    /** Seek to an absolute position, clamped to the loaded episode's duration. */
    fun seekTo(positionMs: Long) {
        if (_state.value.episode == null) return
        val dur = player.duration
        val target = if (dur > 0) positionMs.coerceIn(0, dur) else positionMs.coerceAtLeast(0)
        player.seekTo(target)
        push()
    }

    /** Seek by a relative delta (e.g. ±15s), clamped to the loaded episode's bounds. */
    fun seekBy(deltaMs: Long) {
        if (_state.value.episode == null) return
        seekTo(player.currentPosition + deltaMs)
    }

    fun stop() {
        persistNow()
        player.stop(); player.clearMediaItems()
        _state.value = PlaybackState()
    }

    /** Snapshot the current episode's position to persistent storage, if any is loaded. */
    private fun persistNow() {
        val ep = _state.value.episode ?: return
        val pos = player.currentPosition
        if (pos > 0) onPositionPersist?.invoke(ep.audioUrl, pos, player.duration.coerceAtLeast(0))
    }

    private fun push() {
        val ep = _state.value.episode ?: return
        _state.value = PlaybackState(
            episode = ep,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            buffering = player.playbackState == Player.STATE_BUFFERING,
        )
    }
}
