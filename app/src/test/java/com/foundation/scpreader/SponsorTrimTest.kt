package com.foundation.scpreader

import com.foundation.scpreader.playback.SkipSegment
import com.foundation.scpreader.playback.SponsorTrim
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-JVM check of the keep-range math that drives the offline trim. */
class SponsorTrimTest {

    @Test
    fun computesKeepRangesForRealEpisode() {
        // SCP-3838: sponsor 82s..185s, selfpromo 1033s..1075s; total ~1200s.
        val segments = listOf(
            SkipSegment("sponsor", 82_000, 185_000),
            SkipSegment("selfpromo", 1_033_000, 1_075_000),
        )
        val keep = SponsorTrim.keepRanges(segments, totalMs = 1_200_000)
        assertEquals(
            listOf(0L to 82_000L, 185_000L to 1_033_000L, 1_075_000L to 1_200_000L),
            keep,
        )
    }

    @Test
    fun mergesOverlappingAndDropsIntroAtZero() {
        val segments = listOf(
            SkipSegment("selfpromo", 0, 148_000),
            SkipSegment("intro", 140_000, 177_000), // overlaps previous
            SkipSegment("sponsor", 696_000, 714_000),
        )
        val keep = SponsorTrim.keepRanges(segments, totalMs = 900_000)
        // First kept range starts after the merged 0..177s block.
        assertEquals(listOf(177_000L to 696_000L, 714_000L to 900_000L), keep)
    }

    @Test
    fun noSegmentsMeansNothingToTrim() {
        assertEquals(emptyList<Pair<Long, Long>>(), SponsorTrim.keepRanges(emptyList(), 1_000_000))
    }
}
