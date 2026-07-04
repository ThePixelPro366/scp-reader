package com.foundation.scpreader.data

import android.net.Uri
import com.foundation.scpreader.database.DlStatus
import com.foundation.scpreader.database.DownloadDao
import com.foundation.scpreader.database.NarrationIndexDao
import com.foundation.scpreader.database.NarrationIndexEntity
import com.foundation.scpreader.network.PodcastApi
import com.foundation.scpreader.network.YouTubeSource
import com.foundation.scpreader.playback.PlaybackOrigin
import com.foundation.scpreader.playback.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * An episode paired with a concrete, directly-playable URI plus the [origin] it came from and, when
 * a YouTube attempt fell back to Apple, a short [fallbackReason] for the debug indicator.
 */
data class Playable(
    val episode: Episode,
    val uri: String,
    val origin: PlaybackOrigin,
    val fallbackReason: String? = null,
    val cleaned: Boolean = false,   // a downloaded file that already had its segments removed
)

/**
 * Merges narration sources: **YouTube (@scparchives) primary, Apple podcast fallback**, per SCP
 * number. [availability] (drives the headphones icon + audio-only filter) is the union of both.
 * YouTube discovery is cached in `narration_index` so startup doesn't re-scrape; a network refresh
 * runs in the background and updates state reactively.
 */
class NarrationRepository(
    private val youTube: YouTubeSource,
    private val podcast: PodcastApi,
    private val indexDao: NarrationIndexDao,
    private val downloadDao: DownloadDao,
    private val streamResolver: StreamResolver,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val _availability = MutableStateFlow<Set<Int>>(emptySet())
    /** SCP numbers with narration available from either source. */
    val availability: StateFlow<Set<Int>> = _availability.asStateFlow()

    @Volatile private var youtube: List<Episode> = emptyList()
    @Volatile private var apple: List<Episode> = emptyList()
    @Volatile private var synced = false
    private val syncMutex = Mutex()

    /** Load cached index + Apple feed once, then kick a background YouTube refresh. */
    suspend fun ensureSynced() {
        if (synced) return
        // Serialize so concurrent callers (init + first openReaderItem) don't double-scrape.
        syncMutex.withLock {
            if (synced) return
            youtube = runCatching { indexDao.getAll().map { it.toEpisode() } }.getOrDefault(emptyList())
            apple = runCatching { podcast.episodes() }.getOrDefault(emptyList())
            recompute()
            synced = true
            scope.launch { refreshFromNetwork() }
        }
    }

    private suspend fun refreshFromNetwork() {
        runCatching {
            val known = youtube.mapNotNull { it.videoId }.toSet()
            val fresh = youTube.fetchUploads(known)
            if (fresh.isNotEmpty()) {
                val ts = nowMs()
                indexDao.upsertAll(fresh.map { it.toIndexEntity(ts) })
                youtube = indexDao.getAll().map { it.toEpisode() }
                recompute()
            }
        }
    }

    private fun recompute() {
        _availability.value =
            (youtube.mapNotNull { it.scpNumber } + apple.mapNotNull { it.scpNumber }).toSet()
    }

    /** Newest-first list for prev/next navigation (YouTube primary, Apple if YouTube is empty). */
    fun orderedEpisodes(): List<Episode> =
        youtube.ifEmpty { apple }.sortedByDescending { it.publishedMillis }

    /** The preferred episode for an SCP number: YouTube first, else Apple. */
    fun episodeFor(scpNumber: Int): Episode? =
        youtube.firstOrNull { it.scpNumber == scpNumber } ?: apple.firstOrNull { it.scpNumber == scpNumber }

    /**
     * Resolve a concrete playable URI: local download → fresh YouTube stream → Apple MP3, with an
     * Apple fallback (same SCP number) if a YouTube video is missing or extraction fails.
     */
    /** Force the next YouTube resolution to re-extract (used after an expired-URL playback error). */
    fun invalidateStream(videoId: String) = streamResolver.invalidate(videoId)

    suspend fun resolvePlayable(episode: Episode): Playable? {
        downloadDao.getByMediaId(episode.mediaId)?.let { row ->
            if (row.status == DlStatus.DONE && !row.audioPath.isNullOrBlank()) {
                // A non-null sponsorSegmentsJson means the file was already trimmed at download time.
                val cleaned = row.sponsorSegmentsJson != null
                return Playable(episode, Uri.fromFile(File(row.audioPath!!)).toString(), PlaybackOrigin.LOCAL, cleaned = cleaned)
            }
        }
        return when (episode.source) {
            NarrationSource.YOUTUBE -> {
                val vid = episode.videoId
                val url = if (vid != null) streamResolver.resolveUrl(vid) else null
                if (url != null) {
                    Playable(episode, url, PlaybackOrigin.YOUTUBE)
                } else {
                    val reason = if (vid == null) "No video for this SCP" else "YouTube extraction failed"
                    episode.scpNumber
                        ?.let { n -> apple.firstOrNull { it.scpNumber == n } }
                        ?.let { Playable(it, it.audioUrl, PlaybackOrigin.PODCAST, "$reason — using podcast") }
                }
            }
            NarrationSource.PODCAST -> Playable(episode, episode.audioUrl, PlaybackOrigin.PODCAST)
        }
    }
}

private fun NarrationIndexEntity.toEpisode() = Episode(
    title = title,
    audioUrl = "",
    durationSec = durationSec,
    scpNumber = scpNumber,
    publishedMillis = publishedMillis,
    imageUrl = thumbnailUrl,
    source = runCatching { NarrationSource.valueOf(source) }.getOrDefault(NarrationSource.PODCAST),
    videoId = videoId,
)

private fun Episode.toIndexEntity(syncedAt: Long) = NarrationIndexEntity(
    mediaId = mediaId,
    scpNumber = scpNumber,
    source = source.name,
    videoId = videoId,
    title = title,
    durationSec = durationSec,
    publishedMillis = publishedMillis,
    thumbnailUrl = imageUrl,
    syncedAt = syncedAt,
)
