package com.foundation.scpreader.playback

import com.foundation.scpreader.database.SponsorSegmentDao
import com.foundation.scpreader.database.SponsorSegmentEntity
import com.foundation.scpreader.network.SponsorBlockApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Provides SponsorBlock skip segments for a YouTube video, cached in `sponsor_segments` so repeat
 * plays (and offline, once a download stores its own copy) don't re-hit the network. Segments are
 * fetched for all categories and filtered to the user's enabled set at request time.
 */
class SponsorBlockController(
    private val api: SponsorBlockApi,
    private val dao: SponsorSegmentDao,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SkipSegment.serializer())

    /** Enabled skip segments for [videoId], sorted by start time. */
    suspend fun segmentsFor(videoId: String, enabled: Set<String>): List<SkipSegment> =
        cachedOrFetch(videoId).filter { it.category in enabled }.sortedBy { it.startMs }

    private suspend fun cachedOrFetch(videoId: String): List<SkipSegment> {
        dao.get(videoId)?.let { cached ->
            return runCatching { json.decodeFromString(serializer, cached.segmentsJson) }.getOrDefault(emptyList())
        }
        val fetched = api.fetch(videoId)
        runCatching { dao.upsert(SponsorSegmentEntity(videoId, json.encodeToString(serializer, fetched), nowMs())) }
        return fetched
    }
}
