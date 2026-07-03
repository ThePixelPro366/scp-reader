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
            override fun onIsPlayingChanged(isPlaying: Boolean) = push()
            override fun onPlaybackStateChanged(playbackState: Int) = push()
        })
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    init {
        // Progress ticker on the main thread while something is loaded.
        mainScope.launch {
            while (true) {
                if (player.isPlaying || player.playbackState == Player.STATE_READY) push()
                delay(500)
            }
        }
    }

    fun play(episode: Episode) {
        val current = _state.value.episode
        if (current?.audioUrl == episode.audioUrl) { togglePlayPause(); return }
        player.setMediaItem(MediaItem.fromUri(episode.audioUrl))
        player.prepare()
        player.playWhenReady = true
        _state.value = PlaybackState(episode = episode, isPlaying = true, buffering = true)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun stop() {
        player.stop(); player.clearMediaItems()
        _state.value = PlaybackState()
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
