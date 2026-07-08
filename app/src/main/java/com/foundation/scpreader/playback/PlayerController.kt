package com.foundation.scpreader.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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

/** Seek increment for hardware/notification seek-forward/back controls, matching the in-app
 *  ±15s skip buttons (see PlayerScreen's SKIP_MS). */
private const val SEEK_INCREMENT_MS = 15_000L

/** UI-facing playback state for the mini-player. */
data class PlaybackState(
    val episode: Episode? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val buffering: Boolean = false,
    val origin: PlaybackOrigin? = null,   // where the audio actually came from (for the debug badge)
    val segments: List<SkipSegment> = emptyList(), // active skip segments (for seek-bar markers)
    val cleaned: Boolean = false,         // a downloaded file that already had segments removed
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
    private val player: ExoPlayer = ExoPlayer.Builder(appContext)
        // handleAudioFocus=true makes ExoPlayer request focus on play and react to the system's
        // standard focus callbacks on its own: pause on permanent/transient loss (e.g. a call or
        // another app's playback), duck volume on transient-can-duck loss, resume on regain.
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            /* handleAudioFocus = */ true,
        )
        // Pause automatically when headphones/Bluetooth disconnect (ACTION_AUDIO_BECOMING_NOISY).
        .setHandleAudioBecomingNoisy(true)
        // Matches the in-app ±15s skip buttons, so hardware/notification seek buttons agree.
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                push()
                if (isPlaying) onPlaybackStarting?.invoke()
                // Persist on pause so a position is captured the moment playback halts.
                if (!isPlaying) persistNow()
            }
            override fun onPlaybackStateChanged(playbackState: Int) = push()
            override fun onPlayerError(error: PlaybackException) {
                // A YouTube stream URL can expire mid-playback. Re-resolve once and resume.
                val ep = _state.value.episode ?: return
                if (currentOrigin == PlaybackOrigin.YOUTUBE && !reResolveTried) {
                    reResolveTried = true
                    onSourceError?.invoke(ep, lastPositionMs)
                }
            }
        })
    }

    /** The live player, for [com.foundation.scpreader.playback.PlaybackService] to wrap in a
     *  MediaSession — same process, so the session reflects this instance directly. */
    val exoPlayer: ExoPlayer get() = player

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Set by the DI container to persist positions; (mediaId, positionMs, durationMs). */
    var onPositionPersist: ((String, Long, Long) -> Unit)? = null

    /** Invoked with the category each time a SponsorBlock segment is auto-skipped. */
    var onSponsorSkipped: ((String) -> Unit)? = null

    /** Invoked when a YouTube source errors out (e.g. expired URL); (episode, lastPositionMs). */
    var onSourceError: ((Episode, Long) -> Unit)? = null

    /** Invoked whenever playback starts, so the DI container can ensure the media-notification
     *  foreground service is running. Safe to invoke repeatedly (starting an already-running
     *  service is a no-op beyond delivering another onStartCommand). */
    var onPlaybackStarting: (() -> Unit)? = null

    private var currentOrigin: PlaybackOrigin? = null
    private var currentCleaned = false
    // Last good playback position, so a re-resolve after an expiry error can resume in place.
    @Volatile private var lastPositionMs = 0L
    @Volatile private var lastDurationMs = 0L
    // One re-resolve attempt per media load, to avoid an error→re-resolve→error loop.
    private var reResolveTried = false
    // SponsorBlock skip segments for the current media (guarded by [sponsorMediaId]).
    @Volatile private var sponsorSegments: List<SkipSegment> = emptyList()
    @Volatile private var sponsorMediaId: String? = null

    init {
        // Progress ticker on the main thread while something is loaded; also persists
        // position periodically (~every 5s) so an unexpected process death loses little,
        // and applies SponsorBlock skips.
        mainScope.launch {
            var ticks = 0
            while (true) {
                if (player.isPlaying || player.playbackState == Player.STATE_READY) push()
                if (player.isPlaying) {
                    maybeSkipSponsor()
                    if (++ticks >= 10) { persistNow(); ticks = 0 }
                }
                delay(500)
            }
        }
    }

    /** If the current position sits inside an enabled skip segment, jump past it. */
    private fun maybeSkipSponsor() {
        val segs = sponsorSegments
        if (segs.isEmpty()) return
        val dur = player.duration
        val pos = player.currentPosition
        // Use an effective end clamped to duration, and require >300ms left, so an outro that ends
        // at/after the media end doesn't trigger a repeated end-seek + repeated "skipped" toast.
        val seg = segs.firstOrNull {
            val end = if (dur > 0) minOf(it.endMs, dur) else it.endMs
            pos >= it.startMs && pos < end - 300
        } ?: return
        player.seekTo(if (dur > 0) seg.endMs.coerceAtMost(dur) else seg.endMs)
        onSponsorSkipped?.invoke(seg.category)
    }

    /** Apply skip segments for [mediaId] (ignored if the current media has since changed). */
    fun setSponsorSegments(mediaId: String, segments: List<SkipSegment>) {
        if (mediaId == sponsorMediaId) { sponsorSegments = segments; push() }
    }

    /**
     * Start [episode], playing from [playableUri] (a resolved local file or fresh stream URL).
     * Identity, resume and prev/next all key on [Episode.mediaId], not the ephemeral URI.
     */
    fun play(episode: Episode, playableUri: String, startPositionMs: Long = 0, origin: PlaybackOrigin? = null, cleaned: Boolean = false, forceReload: Boolean = false) {
        val current = _state.value.episode
        // Same media already loaded → just toggle, UNLESS this is a forced reload (expiry re-resolve).
        if (!forceReload && current?.mediaId == episode.mediaId) { togglePlayPause(); return }
        // Capture the outgoing episode's position before switching tracks (skip on self-reload).
        if (!forceReload) persistNow()
        currentOrigin = origin
        currentCleaned = cleaned
        // Fresh media gets a new retry budget; a forced reload keeps the "already tried" guard.
        if (!forceReload) { reResolveTried = false; lastPositionMs = 0L; lastDurationMs = 0L }
        // New media: clear old segments until the caller supplies this episode's.
        sponsorMediaId = episode.mediaId
        sponsorSegments = emptyList()
        val mediaItem = MediaItem.Builder()
            .setUri(playableUri)
            .setMediaId(episode.mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist("SCP Archives")
                    .apply { episode.imageUrl?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.playWhenReady = true
        _state.value = PlaybackState(episode = episode, isPlaying = true, positionMs = startPositionMs.coerceAtLeast(0), buffering = true, origin = origin, cleaned = cleaned)
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
        currentOrigin = null
        currentCleaned = false
        sponsorMediaId = null
        sponsorSegments = emptyList()
        _state.value = PlaybackState()
    }

    /** Snapshot the current episode's position to persistent storage, if any is loaded. */
    private fun persistNow() {
        val ep = _state.value.episode ?: return
        val pos = player.currentPosition
        // Prefer the last known real duration so a save made before duration was known doesn't
        // store 0 (which would defeat the near-end "restart instead of resume at tail" reset).
        val dur = player.duration.coerceAtLeast(0).takeIf { it > 0 } ?: lastDurationMs
        if (pos > 0) onPositionPersist?.invoke(ep.mediaId, pos, dur)
    }

    private fun push() {
        val ep = _state.value.episode ?: return
        if (player.currentPosition > 0) lastPositionMs = player.currentPosition
        if (player.duration > 0) lastDurationMs = player.duration
        _state.value = PlaybackState(
            episode = ep,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            buffering = player.playbackState == Player.STATE_BUFFERING,
            origin = currentOrigin,
            segments = sponsorSegments,
            cleaned = currentCleaned,
        )
    }
}
