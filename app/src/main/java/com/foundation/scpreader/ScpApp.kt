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

    init {
        // Initialise YouTube extraction with our OkHttp-backed downloader (safe to call once, early).
        org.schabi.newpipe.extractor.NewPipe.init(com.foundation.scpreader.network.NewPipeDownloader(http))
    }

    /** Resolves YouTube video ids to fresh audio stream URLs (ephemeral, short-TTL cached). */
    val streamResolver = com.foundation.scpreader.playback.StreamResolver()

    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "scp-reader.db")
        .addMigrations(com.foundation.scpreader.database.MIGRATION_5_6)
        // 5->6 is preserved above; any older/unknown schema (pre-v5 dev builds) has no
        // hand-written path, so rebuild rather than crash on open.
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

    /** Narration source layer: YouTube (@scparchives) primary, Apple podcast fallback. */
    private val narration = com.foundation.scpreader.data.NarrationRepository(
        youTube = com.foundation.scpreader.network.YouTubeSource("UC4_byDtwX_vNI1TNnj0l59A"),
        podcast = PodcastApi(http),
        indexDao = db.narrationIndexDao(),
        downloadDao = db.downloadDao(),
        streamResolver = streamResolver,
        scope = appScope,
    )

    /** SponsorBlock: fetch/cache skip segments for YouTube videos. */
    private val sponsor = com.foundation.scpreader.playback.SponsorBlockController(
        api = com.foundation.scpreader.network.SponsorBlockApi(http),
        dao = db.sponsorSegmentDao(),
    )

    /** Media3 Transformer wrapper that physically removes SponsorBlock segments from downloads. */
    private val audioTrimmer = com.foundation.scpreader.playback.AudioTrimmer(appContext)

    val repository = ScpRepository(
        crom = CromApi(http),
        scraper = ScpScraper(),
        narration = narration,
        sponsor = sponsor,
        audioTrimmer = audioTrimmer,
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
        pc.onPositionPersist = { mediaId, positionMs, durationMs ->
            repository.savePlaybackPosition(mediaId, positionMs, durationMs)
        }
        // Start (or re-signal) the MediaSession service so the notification/media-button/
        // lock-screen integration is live whenever something is actually playing.
        pc.onPlaybackStarting = {
            androidx.core.content.ContextCompat.startForegroundService(
                appContext,
                android.content.Intent(appContext, com.foundation.scpreader.playback.PlaybackService::class.java),
            )
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
