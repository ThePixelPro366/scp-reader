package com.foundation.scpreader.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single archive entry (list-level view): SCP article, tale, or Group-of-Interest document.
 * Populated live from the CROM API. Heavy content (article body) is fetched on demand.
 */
data class ScpItem(
    val url: String,                 // canonical wikidot URL — stable unique id
    val number: String,              // display id, e.g. "SCP-173" or "TALE" / "GoI"
    val title: String,
    val objectClass: String,         // Safe | Euclid | Keter | Thaumiel | Neutralized | Unknown | ...
    val typeLabel: String,           // "SCP" | "Tale" | "GoI"
    val tags: List<String>,
    val rating: Int = 0,
    val hasImage: Boolean = false,
    val imageUrl: String? = null,
    val excerpt: String = "",
    val createdAt: String? = null,   // ISO-8601 creation time (for "Newest" sort); null if unknown
    val podcast: Boolean = false,    // derived: a matching narration episode exists
    val downloaded: Boolean = false, // derived from the local downloads table
) {
    val classShort: String get() = if (objectClass == "Unknown") typeLabel else objectClass
    val metaLine: String
        get() = typeLabel + " · " + objectClass + if (tags.isNotEmpty()) " · " + contentTags.take(2).joinToString(", ") else ""

    /** Tags with wiki plumbing (_licensebox, crom:*, scp, tale…) stripped for display. */
    val contentTags: List<String> get() = tags.filter { it.isNotBlank() && !it.startsWith("_") && !it.startsWith("crom:") && it !in NON_DISPLAY_TAGS }

    companion object {
        private val NON_DISPLAY_TAGS = setOf(
            "scp", "tale", "hub", "goi-format", "safe", "euclid", "keter", "thaumiel",
            "neutralized", "explained", "featured", "_cc",
        )
    }
}

/**
 * A run of text within a block carrying inline formatting. `text` is the plain content;
 * the flags mark wiki markup (**bold**, //italic//, __underline__, --strikethrough--).
 */
@Serializable
data class InlineSpan(
    val text: String,
    @SerialName("b") val bold: Boolean = false,
    @SerialName("i") val italic: Boolean = false,
    @SerialName("u") val underline: Boolean = false,
    @SerialName("s") val strike: Boolean = false,
    @SerialName("l") val link: String? = null,   // absolute href when this run is a hyperlink
    @SerialName("r") val redacted: Boolean = false, // censored run — rendered as a tap-to-reveal black bar
    @SerialName("fn") val footnote: String? = null, // footnote marker run — text is the number, this is its body
)

/** One pane of a wiki `[[tabview]]`: [label] is the tab title, [blocks] its content. */
@Serializable
data class TabPane(val label: String, val blocks: List<ContentBlock>)

/** A structured block of a rendered article, produced by the scraper. */
@Serializable
sealed interface ContentBlock {
    /** `text` is the plain fallback; `spans` (when present) carries inline markup for rendering. */
    @Serializable @SerialName("h") data class Heading(val text: String, val spans: List<InlineSpan> = emptyList()) : ContentBlock
    @Serializable @SerialName("p") data class Paragraph(val text: String, val spans: List<InlineSpan> = emptyList()) : ContentBlock
    /** A quoted passage (wiki `<blockquote>`); rendered as an indented, accented box. */
    @Serializable @SerialName("q") data class Quote(val text: String, val spans: List<InlineSpan> = emptyList()) : ContentBlock
    @Serializable @SerialName("img") data class Image(val url: String, val caption: String) : ContentBlock
    /** A wiki collapsible (`+ Show…`): [title] is the fold label, [blocks] the hidden content. */
    @Serializable @SerialName("c") data class Collapsible(val title: String, val blocks: List<ContentBlock>) : ContentBlock
    /** A wiki `[[tabview]]` (e.g. SCP-2317's "Iteration" tabs): exactly one [panes] entry shown at a time. */
    @Serializable @SerialName("tabs") data class Tabs(val panes: List<TabPane>) : ContentBlock
    /** Anomaly Classification System bar; any field may be absent depending on the article. */
    @Serializable @SerialName("acs") data class Acs(
        val containment: String? = null,
        val disruption: String? = null,
        val risk: String? = null,
        val secondary: String? = null,
    ) : ContentBlock
}

/** Full article detail: the list item, its scraped body, and any crosslinked articles. */
data class Article(
    val item: ScpItem,
    val blocks: List<ContentBlock>,
    val crosslinks: List<ScpItem> = emptyList(),
)

/** Where a narration comes from. YouTube (@scparchives) is primary; the Apple feed is fallback. */
enum class NarrationSource { YOUTUBE, PODCAST }

/**
 * A narration episode. [audioUrl] is a *resolved / playable* URI and may be ephemeral (YouTube
 * stream URLs expire) — it is NOT a stable identity. Use [mediaId] as the stable key for playback
 * identity, resume position, downloads and SponsorBlock lookups.
 */
data class Episode(
    val title: String,
    val audioUrl: String,    // resolved playable URI (direct MP3 for podcast; ephemeral for YouTube)
    val durationSec: Int,
    val scpNumber: Int?,     // parsed from the title when present, e.g. 173
    val publishedMillis: Long,
    val imageUrl: String? = null,
    val source: NarrationSource = NarrationSource.PODCAST,
    val videoId: String? = null,   // YouTube video id when source == YOUTUBE
    val localPath: String? = null, // local audio file once downloaded
) {
    /** Stable, source-tagged identity. YouTube: "yt:<videoId>"; podcast: "pod:<audioUrl>". */
    val mediaId: String
        get() = when (source) {
            NarrationSource.YOUTUBE -> "yt:" + (videoId ?: audioUrl)
            NarrationSource.PODCAST -> "pod:" + audioUrl
        }
}

/** Object-class + type derivation from a page's wikidot tags. */
object Taxonomy {
    private val CLASS_TAGS = linkedMapOf(
        "safe" to "Safe", "euclid" to "Euclid", "keter" to "Keter", "thaumiel" to "Thaumiel",
        "neutralized" to "Neutralized", "explained" to "Explained", "pending" to "Pending",
        "apollyon" to "Apollyon", "archon" to "Archon",
        // Esoteric / secondary object classes (wikidot tags), each with dedicated badge art.
        "cernunnos" to "Cernunnos", "hiemal" to "Hiemal", "tiamat" to "Tiamat",
        "ticonderoga" to "Ticonderoga", "uncontained" to "Uncontained",
        "decommissioned" to "Decommissioned", "anomalous" to "Anomalous",
        "maksur" to "Maksur", "zeno" to "Zeno",
    )

    fun objectClass(tags: List<String>): String {
        for ((tag, label) in CLASS_TAGS) if (tags.contains(tag)) return label
        return "Unknown"
    }

    fun typeLabel(tags: List<String>): String = when {
        tags.contains("tale") -> "Tale"
        tags.contains("goi-format") || tags.contains("hub") -> "GoI"
        tags.contains("scp") -> "SCP"
        else -> "Tale"
    }
}

/** Fallback tag vocabulary for autocomplete before the live list has been indexed. */
val FALLBACK_TAG_VOCAB = listOf(
    "humanoid", "human", "alive", "animal", "amorphous", "antimemetic", "aquatic", "arachnid", "auditory", "autonomous",
    "benevolent", "biological", "building", "clockwork", "cognitohazard", "compulsion", "contagion", "corrosive",
    "electromagnetic", "extradimensional", "fire", "gaseous", "hostile", "insect", "liquid", "location", "mechanical",
    "medical", "memory-affecting", "mimetic", "mind-affecting", "ontokinetic", "plant", "predatory", "radioactive",
    "reality-bending", "religious", "reptilian", "sapient", "self-repairing", "sensory", "sentient", "structure",
    "teleportation", "temporal", "toxic", "transfiguration", "vibration", "visual", "weapon",
)
