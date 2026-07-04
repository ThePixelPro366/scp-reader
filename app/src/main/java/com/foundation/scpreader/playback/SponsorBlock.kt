package com.foundation.scpreader.playback

import kotlinx.serialization.Serializable

/** A time range to auto-skip during playback (SponsorBlock), in milliseconds. */
@Serializable
data class SkipSegment(val category: String, val startMs: Long, val endMs: Long)

/** Which concrete source the currently-playing audio came from (for the debug badge). */
enum class PlaybackOrigin { LOCAL, YOUTUBE, PODCAST }

/** SponsorBlock segment categories and their user-facing labels/defaults. */
object SponsorCategory {
    const val SPONSOR = "sponsor"
    const val SELF_PROMO = "selfpromo"
    const val INTERACTION = "interaction"
    const val INTRO = "intro"
    const val OUTRO = "outro"
    const val PREVIEW = "preview"
    const val MUSIC_OFFTOPIC = "music_offtopic"
    const val FILLER = "filler"

    /** All categories, in display order. */
    val ALL = listOf(SPONSOR, SELF_PROMO, INTERACTION, INTRO, OUTRO, PREVIEW, MUSIC_OFFTOPIC, FILLER)

    /** Skipped by default. */
    val DEFAULT_ENABLED = setOf(SPONSOR, SELF_PROMO, INTERACTION)

    fun label(category: String): String = when (category) {
        SPONSOR -> "Sponsor"
        SELF_PROMO -> "Self-promotion"
        INTERACTION -> "Interaction reminder"
        INTRO -> "Intro / intermission"
        OUTRO -> "Outro / endcards"
        PREVIEW -> "Preview / recap"
        MUSIC_OFFTOPIC -> "Non-narration section"
        FILLER -> "Filler / tangent"
        else -> category
    }
}
