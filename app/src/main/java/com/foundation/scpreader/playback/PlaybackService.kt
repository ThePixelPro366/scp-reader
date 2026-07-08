package com.foundation.scpreader.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.foundation.scpreader.MainActivity
import com.foundation.scpreader.ScpApp

/**
 * Wraps the app-scoped [PlayerController]'s ExoPlayer (owned by [AppContainer], same process) in a
 * [MediaSession] so the OS surfaces a real MediaStyle "now playing" notification — lock screen,
 * quick-settings media controls and the phone's now-playing island (Pixel/Samsung), like Spotify.
 * There's no separate player here; the session mirrors whatever [PlayerController] is doing, so all
 * existing playback logic (SponsorBlock skipping, resume, offline files) is untouched.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var sessionActivity: PendingIntent? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as ScpApp).container.player.exoPlayer
        // Tapping the notification / now-playing island opens OUR app on the full-screen player
        // (the ACTION_OPEN_PLAYER extra is read by MainActivity), not the system's default music app.
        sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PLAYER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Skip-back / skip-forward 15s buttons that mirror the in-app player. They carry the standard
        // COMMAND_SEEK_BACK/FORWARD player commands, so Media3 dispatches them straight to the player,
        // which honours the 15s seek increments PlayerController configures — identical to the on-screen
        // ±15s controls. Shown in both the notification and the system media island.
        val seekBack = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
            .setDisplayName("Back 15 seconds")
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .build()
        val seekForward = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_15)
            .setDisplayName("Forward 15 seconds")
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .build()
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity!!)
            .setCustomLayout(listOf(seekBack, seekForward))
            .build()
        // Media3 drives the rich MediaStyle notification (transport actions + async-loaded artwork
        // from the media item's artworkUri via the session's BitmapLoader) through this provider.
        // It reuses the same channel/notification id as our startForeground placeholder below, so
        // Media3's notification replaces the placeholder in place rather than stacking a second one.
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build())
    }

    // AppContainer starts us via startForegroundService() the moment playback begins, so Android
    // requires startForeground() with a notification within ~5s or it kills the app with a
    // RemoteServiceException. Media3 only posts its own notification once the session/metadata is
    // ready, which can land later (e.g. while a YouTube stream URL resolves). So we post an
    // immediate placeholder — but a *MediaStyle* one bound to the MediaSession's platform token, so
    // the OS recognizes it as media (lock screen / island / media resumption) from the first frame
    // instead of a plain "Loading" notification. Media3 then swaps in its full transport-control
    // version once the player is ready.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, mediaStyleNotification())
        return super.onStartCommand(intent, flags, startId)
    }

    @Suppress("DEPRECATION") // pre-O Notification.Builder(context) constructor
    private fun mediaStyleNotification(): Notification {
        val channelId = DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Playback", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val session = mediaSession
        // Pull the real title/artist straight off the player so the placeholder already shows the
        // episode, not "Loading". PlayerController sets these on the MediaItem before prepare().
        val metadata = session?.player?.mediaMetadata
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(metadata?.title ?: "SCP Archives")
            .setContentText(metadata?.artist ?: "SCP Archives")
            .setOngoing(true)
        // So even this brief placeholder opens our player screen if tapped (Media3's own
        // notification uses the session activity automatically once it takes over).
        sessionActivity?.let { builder.setContentIntent(it) }
        if (session != null) {
            // Binding the session token is what makes the OS treat this as a media notification and
            // route lock-screen / island transport controls to the session (and thus the player).
            builder.setStyle(Notification.MediaStyle().setMediaSession(session.platformToken))
        }
        return builder.build()
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
