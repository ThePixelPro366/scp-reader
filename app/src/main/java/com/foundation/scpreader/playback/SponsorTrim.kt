package com.foundation.scpreader.playback

/** Computes the audio ranges to KEEP when physically removing SponsorBlock segments. */
object SponsorTrim {

    /**
     * Given skip [segments] and the total media length [totalMs], returns the (start,end) ranges to
     * keep — i.e. the complement of the merged segments over [0, totalMs]. Overlapping segments are
     * merged; sub-200ms slivers are dropped. Empty result means "nothing worth trimming".
     */
    fun keepRanges(segments: List<SkipSegment>, totalMs: Long): List<Pair<Long, Long>> {
        if (segments.isEmpty() || totalMs <= 0) return emptyList()
        val merged = mergeOverlaps(segments.sortedBy { it.startMs }, totalMs)
        val keep = ArrayList<Pair<Long, Long>>()
        var cursor = 0L
        for ((s, e) in merged) {
            if (s > cursor) keep.add(cursor to s)
            cursor = maxOf(cursor, e)
        }
        if (cursor < totalMs) keep.add(cursor to totalMs)
        return keep.filter { it.second - it.first > 200 }
    }

    private fun mergeOverlaps(sorted: List<SkipSegment>, totalMs: Long): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        for (seg in sorted) {
            val s = seg.startMs.coerceIn(0, totalMs)
            val e = seg.endMs.coerceIn(0, totalMs)
            if (e <= s) continue
            val last = out.lastOrNull()
            if (last != null && s <= last.second) {
                out[out.size - 1] = last.first to maxOf(last.second, e)
            } else {
                out.add(s to e)
            }
        }
        return out
    }
}
