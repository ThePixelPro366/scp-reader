package com.foundation.scpreader.network

import com.foundation.scpreader.data.ContentBlock
import com.foundation.scpreader.data.InlineSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Scrapes a rendered SCP wiki article (scp-wiki.wikidot.com/<slug>) into structured
 * [ContentBlock]s. We strip wiki chrome (rating widget, footer nav, license box, footnotes,
 * inline CSS/JS), then walk the remaining block-level nodes in document order so headings,
 * paragraphs, list items, addendum/collapsible content and figure images are preserved.
 */
class ScpScraper {

    data class Scraped(
        val blocks: List<ContentBlock>,
        val objectClass: String?,
        val excerpt: String,
        val imageUrl: String?,
        val crosslinks: List<Pair<String, String>> = emptyList(), // (url, anchor text)
    )

    suspend fun fetch(url: String): Scraped = withContext(Dispatchers.IO) {
        val httpsUrl = url.replaceFirst("http://", "https://")
        val doc = Jsoup.connect(httpsUrl)
            .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 SCPReader")
            .timeout(20_000)
            .followRedirects(true)
            .get()
        val content = doc.getElementById("page-content")
            ?: return@withContext Scraped(emptyList(), null, "", null)

        // Capture footnote definitions before we strip the footer chrome, so the numbered
        // references in the body have somewhere to point. Each entry reads like "1. <text>".
        val footnoteBlocks = content.select("div.footnote-footer").mapNotNull { fe ->
            val txt = fe.text().trim()
            if (txt.length < 2) null else ContentBlock.Paragraph(txt, inlineSpans(fe))
        }

        // Parse the ACS (Anomaly Classification System) bar, if present, before it's stripped.
        val acs = parseAcs(content)

        // Remove non-article chrome. Collapsibles are turned into structured blocks during the
        // walk, so we keep `.collapsible-block-link` (its text is the fold title) and drop only
        // the folded (duplicate) copy.
        content.select(
            "style, script, .code, .page-rate-widget-box, .creditRate, .rate-box-with-credit-button, " +
                ".licensebox, .license-area, .footer-wikiwalk-nav, #u-credit-view, .modal-container, " +
                ".collapsible-block-folded, .footnotes-footer, .info-container, .anomaly-class-bar, " +
                ".scp-image-caption-source, .authorlink-wrapper"
        ).remove()

        val blocks = ArrayList<ContentBlock>()
        var objectClass: String? = null
        var excerpt = ""
        var firstImage: String? = null
        val seen = HashSet<String>()

        fun addImage(container: Element, out: MutableList<ContentBlock>) {
            val img = container.selectFirst("img") ?: return
            val src = absImage(img) ?: return
            if (!seen.add("img:$src")) return
            if (firstImage == null) firstImage = src
            val caption = container.selectFirst(".scp-image-caption")?.text()?.trim().orEmpty()
            out.add(ContentBlock.Image(src, caption))
        }

        fun addText(el: Element, bullet: Boolean, out: MutableList<ContentBlock>) {
            val t = el.text().trim()
            if (t.length < 2 || isJunk(t) || !seen.add("p:$t")) return
            if (objectClass == null) objectClass = parseObjectClass(t)
            if (excerpt.isEmpty() && !isMetaLine(t) && t.length > 40) excerpt = t.take(180)
            var spans = inlineSpans(el)
            if (bullet) spans = listOf(InlineSpan("•  ")) + spans
            out.add(ContentBlock.Paragraph(if (bullet) "•  $t" else t, spans))
        }

        fun addQuote(el: Element, out: MutableList<ContentBlock>) {
            val t = el.text().trim()
            if (t.length < 2 || isJunk(t) || !seen.add("q:$t")) return
            out.add(ContentBlock.Quote(t, quoteSpans(el)))
        }

        // Recursive, document-order walk so images stay in their in-text position. Blocks are
        // appended to [out]; collapsible content recurses into its own list so it can be folded.
        fun walk(parent: Element, out: MutableList<ContentBlock>) {
            for (child in parent.children()) {
                val tag = child.tagName()
                when {
                    child.hasClass("collapsible-block") -> {
                        val title = child.selectFirst(".collapsible-block-link")?.text()?.trim()
                            ?.trimStart('+', '►', '▶', '»', ' ')?.trim()
                            ?.takeIf { it.isNotEmpty() } ?: "Show more"
                        val body = child.selectFirst(".collapsible-block-unfolded-text")
                            ?: child.selectFirst(".collapsible-block-content") ?: child
                        val inner = ArrayList<ContentBlock>()
                        walk(body, inner)
                        if (inner.isNotEmpty()) out.add(ContentBlock.Collapsible(title, inner))
                    }
                    tag == "img" -> { if (child.absUrl("src").startsWith("http")) { seen.add("img:${child.absUrl("src")}"); if (firstImage == null) firstImage = child.absUrl("src"); out.add(ContentBlock.Image(child.absUrl("src"), "")) } }
                    child.hasClass("scp-image-block") || (tag == "div" && child.selectFirst("img") != null && child.text().length < 140) -> addImage(child, out)
                    tag.length == 2 && tag[0] == 'h' -> child.text().trim().takeIf { it.isNotEmpty() }?.let { if (seen.add("h:$it")) out.add(ContentBlock.Heading(it, inlineSpans(child))) }
                    tag == "p" -> addText(child, bullet = false, out)
                    tag == "blockquote" || (tag == "div" && child.hasClass("blockquote")) -> addQuote(child, out)
                    tag == "ul" || tag == "ol" -> child.select("> li").forEach { addText(it, bullet = true, out) }
                    tag == "div" -> walk(child, out) // section wrapper — recurse to keep order
                    else -> {}
                }
            }
        }
        if (acs != null) blocks.add(acs)
        walk(content, blocks)

        // Surface footnotes as a trailing section so the in-text reference numbers resolve.
        if (footnoteBlocks.isNotEmpty()) {
            blocks.add(ContentBlock.Heading("Footnotes"))
            blocks.addAll(footnoteBlocks)
        }

        // Crosslinks: anchors within the body that point at other SCP articles.
        val selfSlug = url.substringAfterLast('/')
        val crosslinks = LinkedHashMap<String, String>()
        for (a in content.select("a[href]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = Regex("(scp-\\d+[a-z0-9-]*)", RegexOption.IGNORE_CASE).find(href) ?: continue
            val slug = m.groupValues[1].lowercase()
            if (slug == selfSlug || crosslinks.containsKey(slug)) continue
            val text = a.text().trim()
            crosslinks[slug] = text
            if (crosslinks.size >= 8) break
        }
        val links = crosslinks.map { (slug, text) -> "http://scp-wiki.wikidot.com/$slug" to text }
        Scraped(blocks, objectClass, excerpt, firstImage, links)
    }

    /**
     * Flatten an element's inline children into styled [InlineSpan]s, tracking bold/italic/
     * underline/strikethrough from both semantic tags (b, strong, i, em, u, ins, s, del…) and
     * inline `style` attributes (font-weight, font-style, text-decoration). Adjacent runs with
     * identical styling are merged.
     */
    private fun inlineSpans(el: Element): List<InlineSpan> {
        val raw = ArrayList<InlineSpan>()
        fun walk(node: Node, bold: Boolean, italic: Boolean, underline: Boolean, strike: Boolean, link: String?, redacted: Boolean) {
            when (node) {
                is TextNode -> {
                    val t = node.text()
                    if (t.isNotEmpty()) raw.add(InlineSpan(t, bold, italic, underline, strike, link, redacted))
                }
                is Element -> {
                    val tag = node.tagName()
                    val style = node.attr("style").lowercase()
                    val cls = node.className().lowercase()
                    val b = bold || tag == "b" || tag == "strong" || style.contains("font-weight: bold") || style.contains("font-weight:bold") || style.contains("font-weight: 7") || style.contains("font-weight: 8") || style.contains("font-weight: 9")
                    val i = italic || tag == "i" || tag == "em" || style.contains("font-style: italic") || style.contains("font-style:italic")
                    val u = underline || tag == "u" || tag == "ins" || style.contains("underline")
                    val s = strike || tag == "s" || tag == "strike" || tag == "del" || style.contains("line-through")
                    // Blacked-out runs: a "redacted/blackbox" class, or text colored to match a dark background.
                    val r = redacted || cls.contains("redact") || cls.contains("blackbox") || cls.contains("black-box") ||
                        (style.contains("background") && (style.contains("black") || style.contains("#000")) && style.contains("color"))
                    // A hyperlink whose href resolves to a real http(s) URL (skips javascript:/anchors).
                    val href = if (tag == "a") node.absUrl("href").takeIf { it.startsWith("http") } else null
                    for (ch in node.childNodes()) walk(ch, b, i, u, s, href ?: link, r)
                }
            }
        }
        walk(el, false, false, false, false, null, false)
        // Split literal censored markers ([REDACTED], [DATA EXPUNGED], █ runs) into redacted runs.
        val out = raw.flatMap { splitRedaction(it) }
        // Coalesce adjacent runs sharing the same styling.
        val merged = ArrayList<InlineSpan>()
        for (sp in out) {
            val last = merged.lastOrNull()
            if (last != null && last.bold == sp.bold && last.italic == sp.italic && last.underline == sp.underline && last.strike == sp.strike && last.link == sp.link && last.redacted == sp.redacted) {
                merged[merged.size - 1] = last.copy(text = last.text + sp.text)
            } else merged.add(sp)
        }
        // If nothing is styled, linked or redacted, drop spans — the plain `text` fallback is enough.
        return if (merged.none { it.bold || it.italic || it.underline || it.strike || it.link != null || it.redacted }) emptyList() else merged
    }

    private val redactionRegex = Regex("(\\[(?:REDACTED|DATA EXPUNGED|EXPUNGED|SCRUBBED)]|█+)", RegexOption.IGNORE_CASE)

    /** Split a span's text on literal censorship markers, flagging those pieces as redacted. */
    private fun splitRedaction(sp: InlineSpan): List<InlineSpan> {
        if (sp.redacted || !redactionRegex.containsMatchIn(sp.text)) return listOf(sp)
        val pieces = ArrayList<InlineSpan>()
        var last = 0
        for (m in redactionRegex.findAll(sp.text)) {
            if (m.range.first > last) pieces.add(sp.copy(text = sp.text.substring(last, m.range.first)))
            pieces.add(sp.copy(text = m.value, redacted = true))
            last = m.range.last + 1
        }
        if (last < sp.text.length) pieces.add(sp.copy(text = sp.text.substring(last)))
        return pieces
    }

    /**
     * Best-effort parse of the ACS anomaly-classification bar. Returns null unless at least one
     * class field is found, so articles without an ACS bar are unaffected.
     */
    private fun parseAcs(content: Element): ContentBlock.Acs? {
        val bar = content.selectFirst("div.anomaly-class-bar") ?: return null
        fun field(vararg sel: String): String? = sel.firstNotNullOfOrNull { s ->
            bar.selectFirst("$s .type-text")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        }
        val containment = field(".contain-class", ".object-class")
        val disruption = field(".disrupt-class")
        val risk = field(".risk-class")
        val secondary = field(".secondary-class")
        return if (containment == null && disruption == null && risk == null && secondary == null) null
        else ContentBlock.Acs(containment, disruption, risk, secondary)
    }

    /**
     * Spans for a `<blockquote>`. Inner block-level children (paragraphs, nested quotes, list
     * items) are joined with blank-line separators so the quote keeps its internal structure;
     * a quote with no block children is treated as a single run.
     */
    private fun quoteSpans(el: Element): List<InlineSpan> {
        val blockTags = setOf("p", "blockquote", "div", "li", "h1", "h2", "h3", "h4", "h5", "h6")
        val children = el.children().filter { it.tagName() in blockTags && it.text().isNotBlank() }
        if (children.isEmpty()) return inlineSpans(el)
        val out = ArrayList<InlineSpan>()
        children.forEachIndexed { idx, ch ->
            if (idx > 0) out.add(InlineSpan("\n\n"))
            out.addAll(inlineSpans(ch).ifEmpty { listOf(InlineSpan(ch.text())) })
        }
        return out
    }

    private fun absImage(img: Element): String? {
        val src = img.absUrl("src").ifBlank { img.attr("src") }
        return src.takeIf { it.startsWith("http") && !it.contains("data:") }
    }

    private fun parseObjectClass(text: String): String? {
        val m = Regex("Object Class[:\\s]+([A-Za-z-]+)", RegexOption.IGNORE_CASE).find(text) ?: return null
        return m.groupValues[1].replaceFirstChar { it.uppercase() }
    }

    private fun isMetaLine(text: String): Boolean {
        val l = text.lowercase()
        return l.startsWith("item #") || l.startsWith("object class") || l.startsWith("special containment") || l.startsWith("threat level")
    }

    /** Filter leftover wiki source/nav artifacts that occasionally slip through. */
    private fun isJunk(text: String): Boolean {
        val l = text.lowercase()
        return l.contains("[[include") || l.contains("%%content%%") || l.startsWith("[[") ||
            l.contains("licensebox-warning") || l.contains("{\$") || text.startsWith("« ") ||
            (text.contains("|") && text.contains("SCP-") && text.length < 40) ||
            l.startsWith("rating:") || l == "license" || l.contains("this page's licensebox")
    }
}
