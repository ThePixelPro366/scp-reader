package com.foundation.scpreader.network

import com.foundation.scpreader.data.Episode
import com.foundation.scpreader.data.NarrationSource
import com.foundation.scpreader.data.parseScpNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Keyless discovery of the @scparchives uploads via NewPipeExtractor's channel tab extractor.
 * Pure-JVM (no Android imports) so it can be exercised in a plain unit test.
 */
class YouTubeSource(private val channelId: String) {

    private val channelUrl = "https://www.youtube.com/channel/$channelId"

    /**
     * Fetch uploads newest-first as [Episode]s (source = YOUTUBE). Skips ids already in [known] and,
     * since uploads are newest-first, stops early once a whole page yields nothing new (incremental
     * sync). [maxItems] bounds the first, full sync. Returns only NEW episodes.
     */
    suspend fun fetchUploads(known: Set<String>, maxItems: Int = 1000): List<Episode> = withContext(Dispatchers.IO) {
        val out = ArrayList<Episode>()
        runCatching {
            val service = ServiceList.YouTube
            val channel = ChannelInfo.getInfo(service, channelUrl)
            val videosTab = channel.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                ?: return@runCatching
            val tab = ChannelTabInfo.getInfo(service, videosTab)

            var items = tab.relatedItems
            var nextPage: Page? = tab.nextPage
            while (out.size < maxItems) {
                var newThisPage = 0
                for (info in items) {
                    val stream = info as? StreamInfoItem ?: continue
                    val vid = videoId(stream.url) ?: continue
                    if (vid in known) continue
                    newThisPage++
                    out.add(stream.toEpisode(vid))
                    if (out.size >= maxItems) break
                }
                // Caught up: an all-known page during an incremental sync means we're done.
                if (known.isNotEmpty() && newThisPage == 0) break
                val next = nextPage ?: break
                val page = ChannelTabInfo.getMoreItems(service, videosTab, next)
                items = page.items
                nextPage = page.nextPage
            }
        }
        out
    }

    private fun StreamInfoItem.toEpisode(vid: String) = Episode(
        title = name.orEmpty(),
        audioUrl = "",                                   // resolved on demand (ephemeral)
        durationSec = duration.toInt().coerceAtLeast(0),
        scpNumber = parseScpNumber(name.orEmpty()),
        publishedMillis = runCatching { uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() }.getOrNull() ?: 0L,
        imageUrl = thumbnails.lastOrNull()?.url,
        source = NarrationSource.YOUTUBE,
        videoId = vid,
    )

    private fun videoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
            ?: url.substringAfterLast('/', "").takeIf { it.length == 11 }
    }
}
