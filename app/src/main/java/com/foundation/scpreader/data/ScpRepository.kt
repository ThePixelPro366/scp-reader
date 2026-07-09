package com.foundation.scpreader.data

import android.util.Log
import com.foundation.scpreader.database.BookmarkDao
import com.foundation.scpreader.database.BookmarkEntity
import com.foundation.scpreader.database.DlStatus
import com.foundation.scpreader.database.DownloadDao
import com.foundation.scpreader.database.DownloadEntity
import com.foundation.scpreader.database.RecentDao
import com.foundation.scpreader.database.RecentEntity
import com.foundation.scpreader.database.PlaybackPositionDao
import com.foundation.scpreader.database.PlaybackPositionEntity
import com.foundation.scpreader.database.SearchRecentDao
import com.foundation.scpreader.database.SearchRecentEntity
import com.foundation.scpreader.network.CromApi
import com.foundation.scpreader.network.ScpScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val DL_TAG = "ScpDownload"

/**
 * Offline-first repository. Live browse/search/article data comes from CROM + the wiki scraper;
 * the Room [DownloadDao] is the source of truth for saved content, and audio/article bodies are
 * cached to [filesDir]. Follows the skill's offline-first + reactive-stream principles.
 */
class ScpRepository(
    private val crom: CromApi,
    private val scraper: ScpScraper,
    private val narration: NarrationRepository,
    private val sponsor: com.foundation.scpreader.playback.SponsorBlockController,
    private val audioTrimmer: com.foundation.scpreader.playback.AudioTrimmer,
    private val dao: DownloadDao,
    private val bookmarkDao: BookmarkDao,
    private val recentDao: RecentDao,
    private val searchRecentDao: SearchRecentDao,
    private val playbackDao: PlaybackPositionDao,
    private val http: OkHttpClient,
    private val filesDir: File,
    private val scope: CoroutineScope,
    private val isUnmetered: () -> Boolean = { true },
) {
    // Download gates, driven by the corresponding settings/UI.
    @Volatile private var wifiOnly = false
    @Volatile private var paused = false
    fun setWifiOnly(enabled: Boolean) { wifiOnly = enabled }
    fun setPaused(value: Boolean) { paused = value }

    /** Set by AppState to surface background audio-download stage failures as a transient notice. */
    var onDownloadNotice: ((String) -> Unit)? = null
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }
    private val blocksSerializer = ListSerializer(ContentBlock.serializer())
    private val sponsorSerializer = ListSerializer(com.foundation.scpreader.playback.SkipSegment.serializer())

    /** SCP numbers that have narration available (YouTube ∪ Apple); drives the `podcast` flag. */
    val availability: StateFlow<Set<Int>> = narration.availability

    /** Tag vocabulary observed from loaded items, seeded with a fallback list, for autocomplete. */
    val tagVocab = MutableStateFlow(FALLBACK_TAG_VOCAB.toSortedSet().toList())
    private val seenTags = sortedSetOf<String>().apply { addAll(FALLBACK_TAG_VOCAB) }

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    private val downloadedUrls = MutableStateFlow<Set<String>>(emptySet())

    private data class DownloadJob(val url: String, val withAudio: Boolean, val categories: Set<String>)
    private val workChannel = Channel<DownloadJob>(Channel.UNLIMITED)
    private val defaultSponsorCategories = com.foundation.scpreader.playback.SponsorCategory.DEFAULT_ENABLED

    init {
        scope.launch { dao.observeAll().collect { list -> downloadedUrls.value = list.filter { it.status == DlStatus.DONE }.map { it.url }.toSet() } }
        startDownloadWorker()
    }

    // ---- browse / search ----
    suspend fun topRated(tag: String = "scp", after: String? = null): CromApi.Page {
        val page = crom.topRated(tag = tag, after = after)
        recordTags(page.items)
        return page.copy(items = decorate(page.items))
    }

    suspend fun search(query: String): List<ScpItem> {
        // Token search first (exact numbers, titles, words), then number-prefix matches so a
        // partial number like "scp 0" or "17" narrows the archive by rating instead of returning
        // nothing. Merge keeps the more-relevant token/exact hits ahead of prefix results.
        val merged = LinkedHashMap<String, ScpItem>()
        crom.search(query).forEach { merged[it.url] = it }
        Regex("\\d+").find(query)?.value?.let { digits ->
            crom.searchByNumberPrefix(digits).forEach { merged.putIfAbsent(it.url, it) }
        }
        val results = merged.values.toList()
        recordTags(results)
        return decorate(results)
    }

    /** Full article: served from the local download if present, otherwise scraped live. */
    suspend fun article(item: ScpItem): Article {
        dao.get(item.url)?.takeIf { it.status == DlStatus.DONE && it.blocksJson != null }?.let { saved ->
            return Article(decorate(listOf(item)).first(), decodeBlocks(saved.blocksJson!!))
        }
        val scraped = runCatching { scraper.fetch(item.url) }.getOrNull()
        val resolved = item.copy(
            objectClass = if (item.objectClass == "Unknown") scraped?.objectClass ?: item.objectClass else item.objectClass,
            excerpt = item.excerpt.ifEmpty { scraped?.excerpt.orEmpty() },
            imageUrl = item.imageUrl ?: scraped?.imageUrl,
            hasImage = item.hasImage || scraped?.imageUrl != null,
        )
        val blocks = scraped?.blocks?.takeIf { it.isNotEmpty() } ?: failureBlocks()
        val crosslinks = scraped?.crosslinks.orEmpty().map { (url, text) ->
            val slug = url.substringAfterLast('/')
            val num = if (slug.matches(Regex("scp-\\d+.*"))) "SCP-" + slug.removePrefix("scp-").uppercase() else slug.uppercase()
            ScpItem(url = url, number = num, title = text.ifBlank { num }, objectClass = "Unknown", typeLabel = "SCP", tags = emptyList())
        }
        return Article(decorate(listOf(resolved)).first(), blocks, crosslinks)
    }

    private fun failureBlocks(): List<ContentBlock> =
        listOf(ContentBlock.Paragraph("Couldn't load this article. Check your connection and try again."))

    // ---- narration (YouTube-primary, Apple-fallback) ----
    suspend fun episodes(): List<Episode> {
        narration.ensureSynced()
        return narration.orderedEpisodes()
    }

    fun episodeFor(item: ScpItem): Episode? {
        val n = itemNumber(item) ?: return null
        return narration.episodeFor(n)
    }

    /** Resolve a concrete playable source (local file → YouTube stream → Apple MP3). */
    suspend fun resolvePlayable(episode: Episode): Playable? = narration.resolvePlayable(episode)

    /** Drop a cached YouTube stream URL so the next resolve re-extracts (after an expiry error). */
    fun invalidateStream(videoId: String) = narration.invalidateStream(videoId)

    /** SponsorBlock skip segments for a YouTube video, filtered to the [enabled] categories. */
    suspend fun sponsorSegments(videoId: String, enabled: Set<String>) =
        sponsor.segmentsFor(videoId, enabled)

    private fun itemNumber(item: ScpItem): Int? = Regex("(\\d+)").find(item.number)?.value?.toIntOrNull()

    // ---- playback position (resume across restarts) ----
    /** Persist the last-known position for an episode, keyed by its stable [mediaId]. */
    fun savePlaybackPosition(mediaId: String, positionMs: Long, durationMs: Long) {
        if (mediaId.isBlank() || positionMs <= 0) return
        scope.launch {
            playbackDao.upsert(PlaybackPositionEntity(mediaId, positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0), now()))
        }
    }

    /**
     * The position to resume [mediaId] from. Returns 0 when nothing is saved, or when the saved
     * spot is within 5s of the end (so a finished episode restarts instead of resuming at the tail).
     */
    suspend fun resumePosition(mediaId: String): Long {
        val row = playbackDao.get(mediaId) ?: return 0
        if (row.durationMs > 0 && row.positionMs >= row.durationMs - 5_000) return 0
        return row.positionMs.coerceAtLeast(0)
    }

    // ---- downloads ----
    fun enqueue(item: ScpItem, withAudio: Boolean, categories: Set<String> = defaultSponsorCategories) {
        scope.launch { enqueueNow(item, withAudio, categories) }
    }

    private suspend fun enqueueNow(item: ScpItem, withAudio: Boolean, categories: Set<String> = defaultSponsorCategories) {
        val existing = dao.get(item.url)
        if (existing?.status == DlStatus.DONE) return
        dao.upsert(
            DownloadEntity(
                url = item.url, number = item.number, title = item.title, objectClass = item.objectClass,
                typeLabel = item.typeLabel, tagsCsv = item.tags.joinToString(","), rating = item.rating,
                imageUrl = item.imageUrl, blocksJson = null, audioPath = null, hasAudio = withAudio,
                sizeBytes = 0, status = DlStatus.QUEUED, progress = 0, updatedAt = now(),
            )
        )
        workChannel.send(DownloadJob(item.url, withAudio, categories))
    }

    /** Bulk-enqueue the top [count] highest-rated pages for [tag], paginating CROM. */
    fun enqueueTopRated(count: Int, tag: String) = scope.launch {
        var after: String? = null
        var added = 0
        while (added < count) {
            val page = runCatching { crom.topRated(tag = tag, after = after, first = 50) }.getOrNull() ?: break
            for (it in page.items) {
                if (added >= count) break
                enqueueNow(it, withAudio = false); added++
            }
            if (!page.hasNext) break
            after = page.endCursor
        }
    }

    /** Bulk-enqueue the entire branch for [tag] (capped for safety). */
    fun enqueueEntireWiki(tag: String, cap: Int = 3000) = scope.launch {
        var after: String? = null
        var added = 0
        while (added < cap) {
            val page = runCatching { crom.topRated(tag = tag, after = after, first = 50) }.getOrNull() ?: break
            page.items.forEach { enqueueNow(it, withAudio = false); added++ }
            if (!page.hasNext) break
            after = page.endCursor
        }
    }

    // ---- recently viewed / continue reading ----
    val recents: Flow<List<ScpItem>> = recentDao.observeAll().map { rows -> rows.map { it.toScpItem() } }
    /** Raw recents rows — exposes the reading-progress fraction the ScpItem projection drops. */
    val recentRows: Flow<List<RecentEntity>> = recentDao.observeAll()

    /** Record (or bump) an opened article, preserving any saved scroll offset & reading progress. */
    suspend fun recordRecent(item: ScpItem) {
        val prev = recentDao.get(item.url)
        recentDao.upsert(
            RecentEntity(
                url = item.url, number = item.number, title = item.title, objectClass = item.objectClass,
                typeLabel = item.typeLabel, tagsCsv = item.tags.joinToString(","), rating = item.rating,
                imageUrl = item.imageUrl, scroll = prev?.scroll ?: 0, progress = prev?.progress ?: 0f, updatedAt = now(),
            )
        )
    }

    suspend fun saveRecentScroll(url: String, scroll: Int, progress: Float) = recentDao.updateScroll(url, scroll, progress, now())
    suspend fun recentScroll(url: String): Int = recentDao.get(url)?.scroll ?: 0

    // ---- search-screen recents (articles opened from search only) ----
    val searchRecents: Flow<List<ScpItem>> = searchRecentDao.observeAll().map { rows -> rows.map { it.toScpItem() } }

    suspend fun recordSearchRecent(item: ScpItem) {
        searchRecentDao.upsert(
            SearchRecentEntity(
                url = item.url, number = item.number, title = item.title, objectClass = item.objectClass,
                typeLabel = item.typeLabel, tagsCsv = item.tags.joinToString(","), rating = item.rating,
                imageUrl = item.imageUrl, updatedAt = now(),
            )
        )
    }

    suspend fun clearSearchRecents() = searchRecentDao.clear()

    // ---- bookmarks ----
    val bookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()

    /** Toggle bookmark; returns the new state. Optionally auto-enqueues an offline download. */
    suspend fun toggleBookmark(item: ScpItem, autoDownload: Boolean, withAudio: Boolean): Boolean {
        if (bookmarkDao.isBookmarked(item.url)) { bookmarkDao.remove(item.url); return false }
        bookmarkDao.add(
            BookmarkEntity(
                url = item.url, number = item.number, title = item.title, objectClass = item.objectClass,
                typeLabel = item.typeLabel, tagsCsv = item.tags.joinToString(","), rating = item.rating,
                imageUrl = item.imageUrl, addedAt = now(),
            )
        )
        if (autoDownload) enqueueNow(item, withAudio)
        return true
    }

    private fun slugFor(n: Int) = if (n < 1000) "scp-%03d".format(n) else "scp-$n"
    private suspend fun fetchNumber(n: Int): ScpItem? =
        runCatching { crom.detail("http://scp-wiki.wikidot.com/${slugFor(n)}")?.first }.getOrNull()

    /** Fetch [count] random articles in parallel — powers the "Random entries" discover feed. */
    suspend fun randomItems(count: Int, exclude: Set<String> = emptySet()): List<ScpItem> = coroutineScope {
        val nums = HashSet<Int>()
        while (nums.size < count * 3) nums.add((2..8999).random())
        val fetched = nums.map { n -> async { fetchNumber(n) } }.awaitAll()
            .filterNotNull().filter { it.objectClass !in exclude }.take(count)
        recordTags(fetched)
        decorate(fetched)
    }

    /** Random SCPs drawn only from the pool that has narration available. */
    suspend fun randomNarratedItems(count: Int, exclude: Set<String> = emptySet()): List<ScpItem> = coroutineScope {
        narration.ensureSynced()
        val pool = availability.value.toList()
        if (pool.isEmpty()) return@coroutineScope emptyList()
        val fetched = pool.shuffled().take(count * 3).map { n -> async { fetchNumber(n) } }.awaitAll()
            .filterNotNull().filter { it.objectClass !in exclude }.take(count)
        recordTags(fetched)
        decorate(fetched)
    }

    /** A genuinely random SCP, respecting excluded object classes. */
    suspend fun randomItem(exclude: Set<String> = emptySet()): ScpItem? {
        repeat(12) {
            val item = fetchNumber((2..8999).random())
            if (item != null && item.objectClass !in exclude) return decorate(listOf(item)).first()
        }
        return null
    }

    /** Deterministic "SCP of the Day" seeded by the date, so it's stable within a day. */
    suspend fun scpOfDay(seed: Long, exclude: Set<String> = emptySet()): ScpItem? {
        val rng = java.util.Random(seed)
        repeat(12) {
            val item = fetchNumber(2 + rng.nextInt(8997))
            if (item != null && item.objectClass !in exclude) return decorate(listOf(item)).first()
        }
        return null
    }

    /** Highest-rated SCP created in the last week (for the Trending hero). */
    suspend fun trendingItem(): ScpItem? {
        val weekAgo = java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS).toString()
        val recent = runCatching { crom.topRated(tag = "scp", first = 1, createdAfter = weekAgo).items.firstOrNull() }.getOrNull()
        // Fall back to the all-time top page if nothing qualified in the past week.
        val item = recent ?: runCatching { crom.topRated(tag = "scp", first = 1).items.firstOrNull() }.getOrNull()
        return item?.let { decorate(listOf(it)).first() }
    }

    /** Gather a pool of pages for a tag by paginating CROM (used for tagged random selection). */
    private suspend fun collectPool(tag: String, max: Int): List<ScpItem> {
        val out = ArrayList<ScpItem>()
        var after: String? = null
        while (out.size < max) {
            val page = runCatching { crom.topRated(tag = tag, after = after, first = 50) }.getOrNull() ?: break
            out.addAll(page.items)
            if (!page.hasNext) break
            after = page.endCursor
        }
        return out
    }

    /**
     * Bulk random download. With a tag, pulls a shuffled pool for that tag. Untagged, SCPs use
     * true-random article numbers, while tales/GoI (which aren't numbered) pull a shuffled pool
     * of their [typeTag] so the selected type is honored.
     */
    fun enqueueRandom(count: Int, tags: List<String>, typeTag: String = "scp") = scope.launch {
        when {
            tags.isNotEmpty() ->
                collectPool(tags.first(), max = (count * 3).coerceAtMost(400))
                    .shuffled().take(count).forEach { enqueueNow(it, withAudio = false) }
            typeTag == "scp" -> {
                var added = 0
                var tries = 0
                while (added < count && tries < count * 5) {
                    tries++
                    randomItem()?.let { enqueueNow(it, withAudio = false); added++ }
                }
            }
            else ->
                collectPool(typeTag, max = (count * 3).coerceAtMost(400))
                    .shuffled().take(count).forEach { enqueueNow(it, withAudio = false) }
        }
    }

    suspend fun removeDownload(url: String) {
        dao.get(url)?.audioPath?.let { runCatching { File(it).delete() } }
        dao.delete(url)
    }

    private fun startDownloadWorker() = scope.launch {
        for (job in workChannel) {
            runCatching { process(job) }.onFailure { e ->
                Log.e(DL_TAG, "download failed for ${job.url}", e)
                onDownloadNotice?.invoke("Download failed")
                // Never leave a row spinning in ACTIVE/QUEUED: finalize it so the UI settles and
                // the reader falls back to a live scrape when its blocks weren't saved.
                runCatching { dao.get(job.url)?.let { dao.updateProgress(job.url, DlStatus.DONE, 100, now()) } }
            }
        }
    }

    private suspend fun process(job: DownloadJob) {
        // Hold the job while paused or (Wi-Fi-only and currently on a metered network).
        while (paused || (wifiOnly && !isUnmetered())) kotlinx.coroutines.delay(1500)
        val row = dao.get(job.url) ?: return
        val item = row.toItem()
        dao.updateProgress(job.url, DlStatus.ACTIVE, 5, now())

        val scraped = runCatching { scraper.fetch(job.url) }.getOrNull()
        val blocks = scraped?.blocks?.takeIf { it.isNotEmpty() } ?: failureBlocks()
        val blocksJson = json.encodeToString(blocksSerializer, blocks)
        var size = blocksJson.toByteArray().size.toLong()
        dao.updateProgress(job.url, DlStatus.ACTIVE, if (job.withAudio) 40 else 90, now())

        var audioPath: String? = null
        var mediaId: String? = null
        var source: String? = null
        var videoId: String? = null
        var sponsorRemovedJson: String? = null
        if (job.withAudio) {
            // Stage 1: pick + resolve a playable source (local/YouTube/Apple).
            val ep = itemNumber(item)?.let { narration.episodeFor(it) }
            val playable = ep?.let {
                runCatching { narration.resolvePlayable(it) }
                    .onFailure { e -> Log.e(DL_TAG, "audio resolve failed for ${item.number}", e) }
                    .getOrNull()
            }
            if (ep == null) {
                Log.w(DL_TAG, "audio: no narration episode for ${item.number}")
                onDownloadNotice?.invoke("No narration for ${item.number}")
            } else if (playable == null) {
                onDownloadNotice?.invoke("Audio extraction failed")
            } else {
                // Stage 2: fetch the audio bytes.
                val raw = File(filesDir, "audio/${slug(job.url)}.audio").apply { parentFile?.mkdirs() }
                val dl = runCatching { downloadFile(playable.uri, raw) }
                if (dl.getOrDefault(false)) {
                    audioPath = raw.absolutePath
                    // Key the row on the PRIMARY episode (what playback's episodeFor resolves to),
                    // not the fallback source we actually fetched — otherwise a YouTube→Apple
                    // fallback download would be stored under "pod:…" and never matched offline
                    // (playback looks it up by the YouTube "yt:…" mediaId).
                    mediaId = ep.mediaId
                    source = ep.source.name
                    videoId = ep.videoId
                    // Stage 3: physically remove enabled SponsorBlock segments (YouTube-sourced only),
                    // so the saved file is already clean and needs no live skipping offline. A trim
                    // failure is NON-FATAL: we keep the untrimmed audio and the download still succeeds.
                    val vid = playable.episode.videoId
                    if (playable.origin == com.foundation.scpreader.playback.PlaybackOrigin.YOUTUBE && vid != null) {
                        val segs = runCatching { sponsor.segmentsFor(vid, job.categories) }.getOrDefault(emptyList())
                        if (segs.isEmpty()) {
                            sponsorRemovedJson = "[]"   // YouTube source, nothing to cut → already clean
                        } else {
                            val totalMs = playable.episode.durationSec * 1000L
                            val keep = com.foundation.scpreader.playback.SponsorTrim.keepRanges(segs, totalMs)
                            val trimmed = File(filesDir, "audio/${slug(job.url)}.m4a")
                            val trim = if (keep.isNotEmpty()) {
                                runCatching { audioTrimmer.trim(raw.absolutePath, trimmed.absolutePath, keep) }
                            } else Result.success(false)
                            if (trim.getOrDefault(false)) {
                                raw.delete()
                                audioPath = trimmed.absolutePath
                                sponsorRemovedJson = json.encodeToString(sponsorSerializer, segs)
                            } else {
                                Log.w(DL_TAG, "audio trim failed for ${item.number}; keeping untrimmed", trim.exceptionOrNull())
                                onDownloadNotice?.invoke("Saved audio (trim unavailable)")
                                runCatching { trimmed.delete() }
                            }
                        }
                    }
                    size += File(audioPath).length()
                } else {
                    Log.e(DL_TAG, "audio byte download failed for ${item.number}", dl.exceptionOrNull())
                    onDownloadNotice?.invoke("Audio download failed")
                    runCatching { raw.delete() }
                }
            }
        }

        dao.upsert(
            row.copy(
                blocksJson = blocksJson, imageUrl = scraped?.imageUrl ?: row.imageUrl,
                objectClass = if (row.objectClass == "Unknown") scraped?.objectClass ?: row.objectClass else row.objectClass,
                audioPath = audioPath, hasAudio = audioPath != null, sizeBytes = size,
                mediaId = mediaId, source = source, videoId = videoId, sponsorSegmentsJson = sponsorRemovedJson,
                status = DlStatus.DONE, progress = 100, updatedAt = now(),
            )
        )
    }

    /** Streams [url] to [dest]; throws (with the HTTP code) on failure so callers can log the reason. */
    private fun downloadFile(url: String, dest: File): Boolean {
        val req = Request.Builder().url(url).header("User-Agent", "SCPReader/0.1").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} fetching audio stream")
            val body = resp.body ?: throw java.io.IOException("empty audio response body")
            body.byteStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
        return true
    }

    // ---- helpers ----
    fun decorate(items: List<ScpItem>): List<ScpItem> {
        val done = downloadedUrls.value
        val pods = availability.value
        return items.map { it ->
            val n = Regex("(\\d+)").find(it.number)?.value?.toIntOrNull()
            it.copy(downloaded = done.contains(it.url), podcast = it.podcast || (n != null && pods.contains(n)))
        }
    }

    private fun recordTags(items: List<ScpItem>) {
        var changed = false
        items.forEach { item -> item.contentTags.forEach { if (seenTags.add(it)) changed = true } }
        if (changed) tagVocab.value = seenTags.toList()
    }

    private fun decodeBlocks(s: String): List<ContentBlock> = runCatching { json.decodeFromString(blocksSerializer, s) }.getOrDefault(emptyList())
    private fun now() = System.currentTimeMillis()
    private fun slug(url: String) = url.substringAfterLast('/').ifBlank { url.hashCode().toString() }

    private fun DownloadEntity.toItem() = ScpItem(
        url = url, number = number, title = title, objectClass = objectClass, typeLabel = typeLabel,
        tags = tagsCsv.split(",").filter { it.isNotBlank() }, rating = rating, imageUrl = imageUrl,
        downloaded = status == DlStatus.DONE, hasImage = imageUrl != null,
    )
}

/** Maps a stored search-recent row to a list item. */
fun SearchRecentEntity.toScpItem() = ScpItem(
    url = url, number = number, title = title, objectClass = objectClass, typeLabel = typeLabel,
    tags = tagsCsv.split(",").filter { it.isNotBlank() }, rating = rating, imageUrl = imageUrl, hasImage = imageUrl != null,
)

/** Maps a stored recently-viewed row to a list item. */
fun RecentEntity.toScpItem() = ScpItem(
    url = url, number = number, title = title, objectClass = objectClass, typeLabel = typeLabel,
    tags = tagsCsv.split(",").filter { it.isNotBlank() }, rating = rating, imageUrl = imageUrl, hasImage = imageUrl != null,
)

/** Maps a stored bookmark row to a list item. */
fun BookmarkEntity.toScpItem() = ScpItem(
    url = url, number = number, title = title, objectClass = objectClass, typeLabel = typeLabel,
    tags = tagsCsv.split(",").filter { it.isNotBlank() }, rating = rating, imageUrl = imageUrl, hasImage = imageUrl != null,
)

/** Maps a stored download row to the list-item shape used by Library/Downloads UIs. */
fun DownloadEntity.toScpItem(podcast: Boolean = false) = ScpItem(
    url = url, number = number, title = title, objectClass = objectClass, typeLabel = typeLabel,
    tags = tagsCsv.split(",").filter { it.isNotBlank() }, rating = rating, imageUrl = imageUrl,
    downloaded = status == DlStatus.DONE, hasImage = imageUrl != null, podcast = podcast || hasAudio,
)
