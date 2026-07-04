package com.foundation.scpreader.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap

/** A resolved, directly-playable audio stream for a YouTube video (URL is ephemeral). */
data class ResolvedStream(val url: String, val mimeType: String?, val bitrate: Int)

/**
 * Chooses which audio track to play when YouTube serves several (auto-dubbed multi-language videos).
 * Priority: (1) ORIGINAL, (2) English, (3) untagged/single-track, (4) anything. Within the chosen
 * group the highest [bitrate] wins. Generic + predicate-driven so it can be unit-tested without
 * constructing NewPipe's AudioStream objects. Returns null only for an empty list.
 */
internal fun <T> pickPreferredTrack(
    items: List<T>,
    isOriginal: (T) -> Boolean,
    isEnglish: (T) -> Boolean,
    isUntagged: (T) -> Boolean,
    bitrate: (T) -> Int,
): T? {
    if (items.isEmpty()) return null
    val group = items.filter(isOriginal)
        .ifEmpty { items.filter(isEnglish) }
        .ifEmpty { items.filter(isUntagged) }
        .ifEmpty { items }
    return group.maxByOrNull(bitrate)
}

/** Applies [pickPreferredTrack] to NewPipe [AudioStream]s (playable, audio-only). */
internal fun selectAudioStream(streams: List<AudioStream>): AudioStream? {
    val playable = streams.filter { !it.content.isNullOrBlank() }
    return pickPreferredTrack(
        items = playable,
        isOriginal = { it.audioTrackType == AudioTrackType.ORIGINAL },
        isEnglish = {
            it.audioLocale?.language.equals("en", ignoreCase = true) ||
                it.audioTrackId?.startsWith("en", ignoreCase = true) == true
        },
        // Single-track / non-dubbed videos carry no language/track metadata at all.
        isUntagged = { it.audioTrackType == null && it.audioLocale == null && it.audioTrackId == null },
        bitrate = { it.averageBitrate },
    )
}

/**
 * Resolves a YouTube video id to a fresh audio-only stream URL via NewPipeExtractor.
 *
 * YouTube stream URLs expire (hours), so results are cached only for a short [ttlMillis] and always
 * re-resolved after that. A per-video [Mutex] provides single-flight: concurrent requests for the
 * same id wait for one extraction instead of hammering YouTube.
 */
class StreamResolver(private val ttlMillis: Long = 5 * 60 * 1000L) {

    private data class Cached(val stream: ResolvedStream, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Cached>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun fresh(videoId: String): ResolvedStream? =
        cache[videoId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.stream

    /** Drop any cached URL for [videoId] so the next resolve re-extracts (e.g. after an expiry error). */
    fun invalidate(videoId: String) { cache.remove(videoId) }

    /** Returns a playable audio stream for [videoId], or null if extraction fails / none exists. */
    suspend fun resolve(videoId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        fresh(videoId)?.let { return@withContext it }
        val lock = locks.getOrPut(videoId) { Mutex() }
        lock.withLock {
            fresh(videoId)?.let { return@withLock it }   // double-check after acquiring
            val resolved = runCatching {
                val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
                // Force the original/English track (avoid YouTube's auto-dubbed language tracks),
                // then the highest bitrate within it.
                selectAudioStream(info.audioStreams)
                    ?.let { ResolvedStream(it.content, it.format?.mimeType, it.averageBitrate) }
            }.getOrNull()
            if (resolved != null) cache[videoId] = Cached(resolved, System.currentTimeMillis() + ttlMillis)
            resolved
        }
    }

    /** Convenience for callers that only need the URL. */
    suspend fun resolveUrl(videoId: String): String? = resolve(videoId)?.url
}
