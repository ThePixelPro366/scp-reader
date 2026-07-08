package com.foundation.scpreader.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.DefaultMediaNotificationProvider
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

    // AppContainer starts us via startForegroundService() the moment playback begins, so Android
    // requires startForeground() with a notification within ~5s or it kills the app with a
    // RemoteServiceException. Media3 only posts its media notification once the session has ready
    // metadata, which can land later than that (e.g. while a YouTube stream URL is still
    // resolving). So we post a minimal placeholder here immediately, reusing Media3's own
    // notification id + channel so its real media notification later replaces this one in place —
    // no duplicate, and nothing lingers once the session takes over or playback stops.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, placeholderNotification())
        return super.onStartCommand(intent, flags, startId)
    }

    private fun placeholderNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("SCP Reader")
            .setContentText("Loading…")
            .setOngoing(true)
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
