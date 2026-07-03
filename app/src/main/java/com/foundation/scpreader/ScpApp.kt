package com.foundation.scpreader

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.Room
import com.foundation.scpreader.data.ScpRepository
import com.foundation.scpreader.data.SettingsStore
import com.foundation.scpreader.database.AppDatabase
import com.foundation.scpreader.network.CromApi
import com.foundation.scpreader.network.PodcastApi
import com.foundation.scpreader.network.ScpScraper
import com.foundation.scpreader.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual DI container (single module, no Hilt) — holds app-scoped singletons. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "scp-reader.db")
        .fallbackToDestructiveMigration()
        .build()

    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)

    /** True on an unmetered network (Wi-Fi/Ethernet) — used to honor the "Wi-Fi only" setting. */
    private fun isUnmetered(): Boolean {
        val net = connectivity?.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    val settingsStore = SettingsStore(appContext)

    val repository = ScpRepository(
        crom = CromApi(http),
        scraper = ScpScraper(),
        podcast = PodcastApi(http),
        dao = db.downloadDao(),
        bookmarkDao = db.bookmarkDao(),
        recentDao = db.recentDao(),
        searchRecentDao = db.searchRecentDao(),
        playbackDao = db.playbackPositionDao(),
        http = http,
        filesDir = context.filesDir,
        scope = appScope,
        isUnmetered = ::isUnmetered,
    )

    val player = PlayerController(context, appScope).also { pc ->
        // Persist playback position so an episode resumes where it was left off.
        pc.onPositionPersist = { audioUrl, positionMs, durationMs ->
            repository.savePlaybackPosition(audioUrl, positionMs, durationMs)
        }
    }
}

class ScpApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
