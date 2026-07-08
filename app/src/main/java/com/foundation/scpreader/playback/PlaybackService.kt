package com.foundation.scpreader.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.foundation.scpreader.MainActivity
import com.foundation.scpreader.ScpApp

/**
 * Wraps the app-scoped [PlayerController]'s ExoPlayer (owned by [AppContainer], same process) in
 * a [MediaSession] so the system can surface a media-style notification, route hardware/Bluetooth
 * media-button events to it, and keep playback alive while the app is backgrounded. There's no
 * separate player instance here — this session mirrors whatever [PlayerController] is already
 * doing, so all existing playback logic (SponsorBlock skipping, resume, offline files) is
 * untouched.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as ScpApp).container.player.exoPlayer
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // Playback is app-scoped and long-lived; only tear the service down if nothing is queued
    // to play. Otherwise, let it keep running so audio survives the task being swiped away.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession
        if (session == null || session.player.mediaItemCount == 0 || !session.player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
