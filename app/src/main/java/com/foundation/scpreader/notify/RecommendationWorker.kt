package com.foundation.scpreader.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.foundation.scpreader.ScpApp
import java.util.concurrent.TimeUnit

/**
 * Periodically polls the backend for new friend recommendations and raises a local notification.
 *
 * There's no push infrastructure (no FCM), so this is a lightweight ~15-minute poll — the OS
 * minimum for periodic work. Foreground refreshes (opening the app / Friends tab) surface new
 * recommendations immediately; this worker covers the app-closed case.
 */
class RecommendationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as ScpApp).container.friendsRepository
        return runCatching {
            val recs = repo.recommendations()
            val maxId = recs.maxOfOrNull { it.id }?.toLong() ?: 0L
            val lastSeen = repo.lastSeenRecId()
            val fresh = recs.filter { it.id.toLong() > lastSeen }
            if (fresh.isNotEmpty()) {
                RecommendationNotifier.notify(applicationContext, fresh)
                repo.setLastSeenRecId(maxId)
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val WORK_NAME = "recommendation-poll"

        /** Schedule the recurring poll (idempotent — safe to call on every app start). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecommendationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
