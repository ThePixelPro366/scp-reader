package com.foundation.scpreader

import com.foundation.scpreader.playback.pickPreferredTrack
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-JVM check of the audio-track selection (original/English over auto-dubbed language tracks). */
class AudioTrackSelectionTest {

    // Mirrors the AudioStream fields we key on: track type, locale language, track id, bitrate.
    private data class Track(
        val name: String,
        val type: String?,      // "ORIGINAL" | "DUBBED" | null
        val locale: String?,    // "en", "es", … | null
        val trackId: String?,   // "en.4", "es.3", … | null
        val bitrate: Int,
    )

    private fun pick(vararg tracks: Track): Track? = pickPreferredTrack(
        items = tracks.toList(),
        isOriginal = { it.type == "ORIGINAL" },
        isEnglish = { it.locale.equals("en", true) || it.trackId?.startsWith("en", true) == true },
        isUntagged = { it.type == null && it.locale == null && it.trackId == null },
        bitrate = { it.bitrate },
    )

    @Test fun prefersOriginalOverDubs() {
        val chosen = pick(
            Track("es-dub", "DUBBED", "es", "es.3", 160),
            Track("en-original", "ORIGINAL", "en", "en.4", 128),
            Track("de-dub", "DUBBED", "de", "de.3", 160),
        )
        assertEquals("en-original", chosen?.name)
    }

    @Test fun fallsBackToEnglishWhenNoOriginalFlag() {
        val chosen = pick(
            Track("es", "DUBBED", "es", "es.3", 160),
            Track("en", "DUBBED", "en", "en.3", 140),
        )
        assertEquals("en", chosen?.name)
    }

    @Test fun singleUntaggedTrackStillWorks() {
        val chosen = pick(Track("only", null, null, null, 128))
        assertEquals("only", chosen?.name)
    }

    @Test fun untaggedPreferredOverForeignDubsWhenNoOriginalOrEnglish() {
        val chosen = pick(
            Track("es", "DUBBED", "es", "es.3", 160),
            Track("untagged", null, null, null, 96),
        )
        assertEquals("untagged", chosen?.name)
    }

    @Test fun fallbackToHighestBitrateWhenAllForeignDubs() {
        val chosen = pick(
            Track("es", "DUBBED", "es", "es.3", 128),
            Track("de", "DUBBED", "de", "de.3", 160),
        )
        assertEquals("de", chosen?.name) // no original/English/untagged → highest bitrate
    }

    @Test fun highestBitrateWithinEnglishGroup() {
        val chosen = pick(
            Track("en-low", "ORIGINAL", "en", "en.3", 128),
            Track("en-high", "ORIGINAL", "en", "en.4", 160),
        )
        assertEquals("en-high", chosen?.name)
    }
}
