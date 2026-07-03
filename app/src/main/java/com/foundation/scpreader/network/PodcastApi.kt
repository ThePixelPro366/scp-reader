package com.foundation.scpreader.network

import com.foundation.scpreader.data.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Resolves the SCP Archives podcast via the iTunes Lookup API, then parses its RSS feed
 * into [Episode]s. Episode titles are matched to SCP numbers so the reader can offer narration.
 */
class PodcastApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    // "SCP Archives" on Apple Podcasts.
    private val collectionId = 1453436915L
    // Fallback feed if the lookup ever fails.
    private val fallbackFeed = "https://feeds.simplecast.com/b7dgNp7A"

    @Serializable private data class LookupResult(val feedUrl: String? = null, val artworkUrl600: String? = null)
    @Serializable private data class Lookup(val results: List<LookupResult> = emptyList())

    suspend fun feedUrl(): String = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("https://itunes.apple.com/lookup?id=$collectionId").build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                json.decodeFromString(Lookup.serializer(), body).results.firstOrNull()?.feedUrl
            }
        }.getOrNull() ?: fallbackFeed
    }

    suspend fun episodes(): List<Episode> = withContext(Dispatchers.IO) {
        val url = feedUrl()
        val req = Request.Builder().url(url).header("User-Agent", "SCPReader/0.1").build()
        client.newCall(req).execute().use { resp ->
            val stream = resp.body?.byteStream() ?: return@withContext emptyList()
            parseRss(stream)
        }
    }

    private fun parseRss(stream: java.io.InputStream): List<Episode> {
        val episodes = ArrayList<Episode>()
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        parser.setInput(stream, null)

        var inItem = false
        var title = ""
        var audioUrl = ""
        var durationSec = 0
        var pubMillis = 0L
        var text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "item" -> { inItem = true; title = ""; audioUrl = ""; durationSec = 0; pubMillis = 0 }
                        "enclosure" -> if (inItem) audioUrl = parser.getAttributeValue(null, "url").orEmpty()
                    }
                    text = StringBuilder()
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase()
                    val value = text.toString().trim()
                    if (inItem) {
                        when (name) {
                            "title" -> if (title.isEmpty()) title = value
                            "itunes:duration", "duration" -> durationSec = parseDuration(value)
                            "pubdate" -> pubMillis = parsePubDate(value)
                            "item" -> {
                                if (audioUrl.isNotEmpty()) {
                                    episodes.add(
                                        Episode(
                                            title = title,
                                            audioUrl = audioUrl,
                                            durationSec = durationSec,
                                            scpNumber = parseScpNumber(title),
                                            publishedMillis = pubMillis,
                                        )
                                    )
                                }
                                inItem = false
                            }
                        }
                    }
                    text = StringBuilder()
                }
            }
            event = parser.next()
        }
        return episodes
    }

    private fun parseScpNumber(title: String): Int? =
        Regex("SCP[- ]?0*(\\d{1,5})", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toIntOrNull()

    private fun parseDuration(raw: String): Int {
        if (raw.isBlank()) return 0
        return if (raw.contains(":")) {
            raw.split(":").map { it.toIntOrNull() ?: 0 }.fold(0) { acc, v -> acc * 60 + v }
        } else raw.toIntOrNull() ?: 0
    }

    private fun parsePubDate(raw: String): Long = runCatching {
        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH).parse(raw)?.time ?: 0L
    }.getOrDefault(0L)
}
