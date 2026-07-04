package com.foundation.scpreader

import com.foundation.scpreader.network.NewPipeDownloader
import com.foundation.scpreader.network.YouTubeSource
import com.foundation.scpreader.playback.StreamResolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe

/**
 * Phase 2 checkpoint (network integration test). Lists @scparchives uploads, prints title -> SCP
 * number mappings, and resolves a real stream for one discovered video. Pure-JVM (no device).
 */
class DiscoveryTest {

    companion object {
        private const val CHANNEL_ID = "UC4_byDtwX_vNI1TNnj0l59A" // SCP Archives

        @BeforeClass @JvmStatic
        fun setUp() {
            NewPipe.init(NewPipeDownloader(OkHttpClient()))
        }
    }

    @Test
    fun discoversAndMapsUploads() = runBlocking {
        val source = YouTubeSource(CHANNEL_ID)
        val episodes = source.fetchUploads(known = emptySet(), maxItems = 25)
        println("Discovered ${episodes.size} @scparchives uploads")
        episodes.take(12).forEach { e ->
            println("  video=${e.videoId}  scp=${e.scpNumber ?: "-"}  \"${e.title.take(60)}\"")
        }
        assertTrue("Expected to discover uploads", episodes.isNotEmpty())
        assertTrue("Expected most titles to map to an SCP number",
            episodes.count { it.scpNumber != null } >= episodes.size / 2)

        // Resolve a real stream for the first discovered video that maps to an SCP number.
        val target = episodes.firstOrNull { it.scpNumber != null && it.videoId != null } ?: episodes.first()
        val resolved = StreamResolver().resolve(target.videoId!!)
        println("Resolved ${target.videoId} (SCP-${target.scpNumber}) -> ${resolved?.url?.take(90)}…")
        assertTrue("Expected to resolve a stream for a discovered video", resolved?.url?.startsWith("http") == true)
    }
}
