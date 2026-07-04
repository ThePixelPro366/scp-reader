package com.foundation.scpreader.network

import com.foundation.scpreader.playback.SkipSegment
import com.foundation.scpreader.playback.SponsorCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * SponsorBlock API client. Uses the privacy-preserving hash-prefix endpoint: only the first 4 hex
 * chars of SHA-256(videoID) are sent, and the matching video is filtered client-side. Returns
 * skip-action segments only. Keyed to YouTube video ids.
 */
class SponsorBlockApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://sponsor.ajay.app"

    @Serializable private data class SegDto(val category: String, val actionType: String? = null, val segment: List<Double> = emptyList())
    @Serializable private data class VideoDto(val videoID: String = "", val segments: List<SegDto> = emptyList())

    /** Fetch skip segments for [videoId]; empty on any error or if none are submitted. */
    suspend fun fetch(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        runCatching {
            val prefix = sha256Hex(videoId).substring(0, 4)
            val categories = enc(json.encodeToString(SponsorCategory.ALL))
            val actionTypes = enc("[\"skip\"]")
            val url = "$base/api/skipSegments/$prefix?categories=$categories&actionTypes=$actionTypes"
            val req = Request.Builder().url(url).header("User-Agent", "SCPReader/0.1").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                json.decodeFromString(ListSerializer(VideoDto.serializer()), body)
                    .filter { it.videoID == videoId }
                    .flatMap { it.segments }
                    .filter { (it.actionType == null || it.actionType == "skip") && it.segment.size >= 2 }
                    .map { SkipSegment(it.category, (it.segment[0] * 1000).toLong(), (it.segment[1] * 1000).toLong()) }
                    .filter { it.endMs > it.startMs }
            }
        }.getOrDefault(emptyList())
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
