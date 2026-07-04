package com.foundation.scpreader

import com.foundation.scpreader.network.NewPipeDownloader
import com.foundation.scpreader.playback.StreamResolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe

/**
 * Phase 1 checkpoint (network integration test). NewPipeExtractor is pure-JVM, so this resolves a
 * real @scparchives audio stream without a device/emulator. Requires network access to YouTube.
 */
class StreamResolverTest {

    companion object {
        @BeforeClass @JvmStatic
        fun setUp() {
            NewPipe.init(NewPipeDownloader(OkHttpClient()))
        }
    }

    @Test
    fun resolvesRealScpArchivesAudioStream() = runBlocking {
        // A few recent @scparchives (UC4_byDtwX_vNI1TNnj0l59A) uploads; try until one resolves,
        // so a single unavailable/age-gated video doesn't fail the check.
        val candidates = listOf("KbNIqbfAU6I", "NrXGTnOwTyc", "_ewAD6BqRv0", "zQEtiSkQ4dM", "FrcOMmbV6nM")
        val resolver = StreamResolver()
        var resolvedUrl: String? = null
        for (id in candidates) {
            val r = runCatching { resolver.resolve(id) }.getOrNull()
            if (r != null && r.url.startsWith("http")) {
                println("Resolved $id -> bitrate=${r.bitrate} mime=${r.mimeType} url=${r.url.take(90)}…")
                resolvedUrl = r.url
                break
            } else {
                println("Could not resolve $id")
            }
        }
        assertTrue("Expected to resolve at least one @scparchives audio stream URL", resolvedUrl != null)
    }
}
