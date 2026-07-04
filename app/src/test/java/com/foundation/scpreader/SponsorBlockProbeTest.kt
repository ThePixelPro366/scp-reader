package com.foundation.scpreader

import com.foundation.scpreader.network.NewPipeDownloader
import com.foundation.scpreader.network.SponsorBlockApi
import com.foundation.scpreader.network.YouTubeSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe

/**
 * Phase 3 checkpoint (network integration test). Scans @scparchives uploads and reports which have
 * crowd-sourced SponsorBlock segments, printing categories/timestamps. Pure-JVM, no device.
 */
class SponsorBlockProbeTest {

    companion object {
        private const val CHANNEL_ID = "UC4_byDtwX_vNI1TNnj0l59A"

        @BeforeClass @JvmStatic
        fun setUp() {
            NewPipe.init(NewPipeDownloader(OkHttpClient()))
        }
    }

    @Test
    fun probeSponsorSegmentsForUploads() = runBlocking {
        val http = OkHttpClient()
        val uploads = YouTubeSource(CHANNEL_ID).fetchUploads(known = emptySet(), maxItems = 150)
        val api = SponsorBlockApi(http)
        var checked = 0
        var hits = 0
        for (ep in uploads) {
            val vid = ep.videoId ?: continue
            checked++
            val segs = api.fetch(vid)
            if (segs.isNotEmpty()) {
                hits++
                println("HIT ${vid} (SCP-${ep.scpNumber}) \"${ep.title.take(45)}\"")
                segs.forEach { s ->
                    println("     ${s.category}  ${s.startMs / 1000}s..${s.endMs / 1000}s")
                }
                if (hits >= 6) break
            }
        }
        println("SponsorBlock probe: $hits/$checked checked uploads have skip segments")
        assertTrue("Expected to have checked uploads", checked > 0)
    }
}
