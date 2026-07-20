package com.foundation.scpreader.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.foundation.scpreader.MainActivity
import com.foundation.scpreader.R
import com.foundation.scpreader.network.FriendsApi

/** Posts local notifications when friends recommend new SCPs (polled in the background). */
object RecommendationNotifier {
    private const val CHANNEL_ID = "recommendations"
    private const val NOTIFICATION_ID = 4711

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Friend recommendations", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifies you when a friend recommends an SCP"
                }
            )
        }
    }

    /** Show one notification summarizing [fresh] recommendations. No-op if the list is empty. */
    fun notify(context: Context, fresh: List<FriendsApi.Rec>) {
        if (fresh.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)

        val newest = fresh.first()
        val title = if (fresh.size == 1) {
            "${newest.from_name.ifBlank { newest.from_code }} recommended an SCP"
        } else {
            "${fresh.size} new SCP recommendations"
        }
        val text = if (fresh.size == 1) {
            (newest.scp_title.ifBlank { newest.scp_number }) +
                if (newest.note.isNotBlank()) " — “${newest.note}”" else ""
        } else {
            "From " + fresh.map { it.from_name.ifBlank { it.from_code } }.distinct().take(3).joinToString(", ")
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_FRIENDS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scp_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}
