package com.foundation.scpreader.network

import com.foundation.scpreader.data.ContentBlock
import com.foundation.scpreader.data.InlineSpan
import com.foundation.scpreader.data.TabPane
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

        // Map each footnote id ("footnote-1") to its body text, keyed before we strip the footer
        // chrome, so inline `sup.footnoteref` markers (handled in inlineSpans()) can carry their
        // own content for a tap-to-reveal popup instead of just a bare, dead-looking number.
        // Body text is often wrapped in <em>/<a>/etc (e.g. "1. <em>full sentence</em> - signature"),
        // so we clone + drop the leading "<a>1</a>" anchor and read .text() (not ownText(), which
        // only sees direct text nodes and silently drops anything inside a child element).
        val footnoteText = content.select("div.footnote-footer[id]").associate { fe ->
            val body = fe.clone().apply { selectFirst("a")?.remove() }.text().trim().removePrefix(".").trim()
            fe.id() to body
        }
        // Also keep the numbered list as a trailing section, matching the site's own footnotes
        // block at the foot of the article (each entry reads like "1. <text>").
        val footnoteBlocks = content.select("div.footnote-footer").mapNotNull { fe ->
            val txt = fe.text().trim()
            if (txt.length < 2) null else ContentBlock.Paragraph(txt, inlineSpans(fe, footnoteText))
        }

        // Parse the ACS (Anomaly Classification System) bar, if present, before it's stripped.
        val acs = parseAcs(content)

        // Remove non-article chrome. Collapsibles are turned into structured blocks during the
        // walk, using the title text from `.collapsible-block-folded` (kept — see below).
        content.select(
            "style, script, .code, .page-rate-widget-box, .creditRate, .rate-box-with-credit-button, " +
                ".licensebox, .license-area, .footer-wikiwalk-nav, #u-credit-view, .modal-container, " +
                ".footnotes-footer, .info-container, .anomaly-class-bar, .anom-bar-container, " +
                ".scp-image-caption-source, .authorlink-wrapper"
        ).remove()

        val blocks = ArrayList<ContentBlock>()
        var objectClass: String? = null
        var excerpt = ""
        var firstImage: String? = null
        val seen = HashSet<String>()

        // Recognized leading fold-state glyphs Wikidot skins use ahead of a collapsible/addendum
        // title — trimmed so our own expand/collapse chevron icon isn't shown doubled-up with one
        // baked into the text.
        val foldGlyphs = "+-−►▶▷◁◀▲▼△▽»«"

        // The title lives in `.collapsible-block-folded` (the initially-visible fold prompt) —
        // NOT `.collapsible-block-unfolded-link`'s "hide block" text, which sits right next to the
        // real content and would be picked up by an unscoped `.collapsible-block-link` lookup.
        fun collapsibleTitle(cb: Element): String =
            cb.selectFirst(".collapsible-block-folded .collapsible-block-link")?.text()?.trim()
                ?.trimStart(*foldGlyphs.toCharArray())?.trim()?.takeIf { it.isNotEmpty() } ?: "Show more"

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
            var spans = inlineSpans(el, footnoteText)
            // inlineSpans() returns emptyList() for unstyled text (the plain `text` fallback is
            // enough on its own) — but prepending the bullet marker would make `spans` non-empty
            // and short-circuit that fallback in markup(), rendering just the bullet with no text.
            if (bullet) spans = listOf(InlineSpan("•  ")) + spans.ifEmpty { listOf(InlineSpan(t)) }
            out.add(ContentBlock.Paragraph(if (bullet) "•  $t" else t, spans))
        }

        // Declared as a lateinit var (assigned just below) rather than a local fun: addQuote() and
        // walk() each need to call the other (a collapsible nested inside a blockquote recurses back
        // through walk()), and Kotlin local funs can't forward-reference one another.
        lateinit var walk: (Element, MutableList<ContentBlock>) -> Unit

        fun addQuote(el: Element, out: MutableList<ContentBlock>) {
            // A collapsible (or, rarely, a tabview) sometimes sits *inside* a blockquote — e.g.
            // SCP-1548's in-universe transcripts. quoteSpans()/inlineSpans() only flatten inline
            // runs, so left in place these would flatten BOTH the fold-state text and the hidden
            // content into permanently-visible plain text. Pull them out into their own sibling
            // blocks (so they get the normal collapse/expand treatment) before reading the quote's
            // own text.
            val nested = ArrayList<ContentBlock>()
            // Only top-level matches — a collapsible nested inside another one is already picked
            // up when that outer collapsible's own content is walked, so selecting it again here
            // would duplicate it as an extra, wrongly-flattened top-level sibling.
            el.select(".collapsible-block").filter { cb -> cb.parents().none { it.hasClass("collapsible-block") } }.forEach { cb ->
                val inner = ArrayList<ContentBlock>()
                walk(cb.selectFirst(".collapsible-block-content") ?: cb, inner)
                if (inner.isNotEmpty()) nested.add(ContentBlock.Collapsible(collapsibleTitle(cb), inner))
                cb.remove()
            }
            val t = el.text().trim()
            if (t.length >= 2 && !isJunk(t) && seen.add("q:$t")) out.add(ContentBlock.Quote(t, quoteSpans(el, footnoteText)))
            out.addAll(nested)
        }

        // Recursive, document-order walk so images stay in their in-text position. Blocks are
        // appended to [out]; collapsible/tab content recurses into its own list so it can be folded.
        walk = fun(parent: Element, out: MutableList<ContentBlock>) {
            for (child in parent.children()) {
                val tag = child.tagName()
                when {
                    child.hasClass("collapsible-block") -> {
                        val body = child.selectFirst(".collapsible-block-content") ?: child
                        val inner = ArrayList<ContentBlock>()
                        walk(body, inner)
                        if (inner.isNotEmpty()) out.add(ContentBlock.Collapsible(collapsibleTitle(child), inner))
                    }
                    // Wikidot `[[tabview]]` (YUI tabs) — e.g. SCP-2317's "Iteration" tabs. Every pane
                    // is present in the DOM (later ones `display:none`); represent all of them and let
                    // the reader show exactly one at a time instead of flattening them all into one
                    // continuous, seemingly "auto-expanded" wall of text.
                    child.hasClass("yui-navset") -> {
                        val labels = child.select("> ul.yui-nav > li").map { it.text().trim() }
                        val paneEls = child.select("> div.yui-content > div")
                        val panes = labels.zip(paneEls).mapNotNull { (label, paneEl) ->
                            val inner = ArrayList<ContentBlock>()
                            walk(paneEl, inner)
                            if (inner.isNotEmpty()) TabPane(label.ifEmpty { "Tab" }, inner) else null
                        }
                        if (panes.isNotEmpty()) out.add(ContentBlock.Tabs(panes))
                    }
                    tag == "img" -> { if (child.absUrl("src").startsWith("http")) { seen.add("img:${child.absUrl("src")}"); if (firstImage == null) firstImage = child.absUrl("src"); out.add(ContentBlock.Image(child.absUrl("src"), "")) } }
                    child.hasClass("scp-image-block") || (tag == "div" && child.selectFirst("img") != null && child.text().length < 140) -> addImage(child, out)
                    tag.length == 2 && tag[0] == 'h' -> child.text().trim().takeIf { it.isNotEmpty() }?.let { if (seen.add("h:$it")) out.add(ContentBlock.Heading(it, inlineSpans(child, footnoteText))) }
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

        // Newer articles state the class only in the ACS bar (no "Object Class:" body line), so fall
        // back to the parsed containment class. Any value flows to classBadgeRes(), which resolves it
        // to the matching badge or the "?" unknown badge.
        if (objectClass == null) {
            objectClass = acs?.containment?.trim()?.takeIf { it.isNotEmpty() }
                ?.lowercase()?.replaceFirstChar { it.uppercase() }
        }

        // Surface footnotes as a trailing section so the in-text reference numbers resolve.
        if (footnoteBlocks.isNotEmpty()) {
            blocks.add(ContentBlock.Heading("Footnotes"))
            blocks.addAll(footnoteBlocks)
        }

        // Crosslinks: anchors within the body that link to another SCP article's wiki *page*.
        // We require the href to resolve to a bare scp-wiki.wikidot.com/scp-XXXX page rather than
        // just matching "scp-\d+" anywhere in the URL — image thumbnails link to their full-size
        // file on scp-wiki.wdfiles.com/local--files/scp-XXXX/..., where XXXX is just the asset's
        // storage directory (often reused/shared) and unrelated to any article the page mentions.
        val selfSlug = url.substringAfterLast('/').lowercase()
        // Resolve crosslinks within the SAME branch wiki the article came from, so links on a
        // non-EN page point back into that branch rather than the English wiki.
        val branchHost = url.substringAfter("://").substringBefore('/').lowercase()
        val pageLinkRegex = Regex("^${Regex.escape(branchHost)}/(scp-\\d+[a-z0-9-]*)/?$", RegexOption.IGNORE_CASE)
        val crosslinks = LinkedHashMap<String, String>()
        for (a in content.select("a[href]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val hostAndPath = href.substringAfter("://").substringBefore('?').substringBefore('#')
            val m = pageLinkRegex.find(hostAndPath) ?: continue
            val slug = m.groupValues[1].lowercase()
            if (slug == selfSlug || crosslinks.containsKey(slug)) continue
            val text = a.text().trim()
            crosslinks[slug] = text
            if (crosslinks.size >= 8) break
        }
        val links = crosslinks.map { (slug, text) -> "http://$branchHost/$slug" to text }
        Scraped(blocks, objectClass, excerpt, firstImage, links)
    }

    /**
     * Flatten an element's inline children into styled [InlineSpan]s, tracking bold/italic/
     * underline/strikethrough from both semantic tags (b, strong, i, em, u, ins, s, del…) and
     * inline `style` attributes (font-weight, font-style, text-decoration). Adjacent runs with
     * identical styling are merged. [footnotes] resolves `sup.footnoteref` markers (id -> body
     * text, from the footer divs collected in [fetch]) so they render as tap-to-reveal, not bare
     * numbers indistinguishable from surrounding text.
     */
    private fun inlineSpans(el: Element, footnotes: Map<String, String> = emptyMap()): List<InlineSpan> {
        val raw = ArrayList<InlineSpan>()
        fun walk(node: Node, bold: Boolean, italic: Boolean, underline: Boolean, strike: Boolean, link: String?, redacted: Boolean) {
            when (node) {
                is TextNode -> {
                    val t = node.text()
                    if (t.isNotEmpty()) raw.add(InlineSpan(t, bold, italic, underline, strike, link, redacted))
                }
                is Element -> {
                    val tag = node.tagName()
                    // Footnote reference marker: `<sup class="footnoteref"><a id="footnoteref-N">N</a></sup>`.
                    // Emit one dedicated span carrying the resolved footnote body instead of recursing —
                    // recursing would just render the bare number "N" as plain, non-interactive text.
                    if (tag == "sup" && node.hasClass("footnoteref")) {
                        val a = node.selectFirst("a")
                        val marker = (a ?: node).text().trim()
                        val noteId = a?.id()?.removePrefix("footnoteref-")?.let { "footnote-$it" }
                        if (marker.isNotEmpty()) raw.add(InlineSpan(marker, footnote = noteId?.let { footnotes[it] } ?: "…"))
                        return
                    }
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
        // Coalesce adjacent runs sharing the same styling. `footnote` must match too, or a footnote
        // marker could absorb (or be absorbed by) neighboring plain text of otherwise-identical style.
        val merged = ArrayList<InlineSpan>()
        for (sp in out) {
            val last = merged.lastOrNull()
            if (last != null && last.bold == sp.bold && last.italic == sp.italic && last.underline == sp.underline && last.strike == sp.strike && last.link == sp.link && last.redacted == sp.redacted && last.footnote == sp.footnote) {
                merged[merged.size - 1] = last.copy(text = last.text + sp.text)
            } else merged.add(sp)
        }
        // If nothing is styled, linked, redacted or a footnote, drop spans — the plain `text` fallback is enough.
        return if (merged.none { it.bold || it.italic || it.underline || it.strike || it.link != null || it.redacted || it.footnote != null }) emptyList() else merged
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
        // The current ACS component ("anom-bar") wraps everything in `.anom-bar-container`; older
        // skins used `.anomaly-class-bar`/`.anomaly-bar`. Each field's value sits in a `.class-text`
        // node (older skins: `.type-text`), labelled by a sibling `.class-category`.
        val bar = content.selectFirst(".anom-bar-container, .anomaly-class-bar, .anomaly-bar") ?: return null
        fun field(vararg sel: String): String? = sel.firstNotNullOfOrNull { s ->
            (bar.selectFirst("$s .class-text") ?: bar.selectFirst("$s .type-text"))
                ?.text()?.trim()?.takeIf { it.isNotEmpty() }
        }
        // Empty/placeholder values mean the field is unset — treat as absent. Besides "none"/"n/a"/
        // "-", this drops unfilled ACS-template residue authors leave in place: Wikidot variables
        // like "{$disruption-class}" (SCP-8840) and default tokens like "class_here" / "secondary-
        // class" (SCP-7939). Comparison is separator-insensitive so "class_here"/"class-here" all
        // match; real class names (safe, euclid, cernunnos, esoteric, …) never normalise into this set.
        val acsPlaceholders = setOf(
            "classhere", "containmentclass", "objectclass", "secondaryclass",
            "disruptionclass", "riskclass", "esotericclass",
        )
        fun clean(v: String?): String? {
            val t = v?.trim().orEmpty()
            if (t.isBlank() || t == "-" || t.equals("none", true) || t.equals("n/a", true) || t.contains("{\$")) return null
            val norm = t.lowercase().filter { it.isLetterOrDigit() }
            if (norm.isEmpty() || norm in acsPlaceholders) return null
            return t
        }
        val containment = clean(field(".contain-class", ".object-class"))
        val disruption = clean(field(".disrupt-class"))
        val risk = clean(field(".risk-class"))
        val secondary = clean(field(".second-class", ".secondary-class"))
        return if (containment == null && disruption == null && risk == null && secondary == null) null
        else ContentBlock.Acs(containment, disruption, risk, secondary)
    }

    /**
     * Spans for a `<blockquote>`. Inner block-level children (paragraphs, nested quotes, list
     * items) are joined with blank-line separators so the quote keeps its internal structure;
     * a quote with no block children is treated as a single run.
     */
    private fun quoteSpans(el: Element, footnotes: Map<String, String> = emptyMap()): List<InlineSpan> {
        val blockTags = setOf("p", "blockquote", "div", "li", "h1", "h2", "h3", "h4", "h5", "h6")
        val children = el.children().filter { it.tagName() in blockTags && it.text().isNotBlank() }
        if (children.isEmpty()) return inlineSpans(el, footnotes)
        val out = ArrayList<InlineSpan>()
        children.forEachIndexed { idx, ch ->
            if (idx > 0) out.add(InlineSpan("\n\n"))
            out.addAll(inlineSpans(ch, footnotes).ifEmpty { listOf(InlineSpan(ch.text())) })
        }
        return out
    }

    private fun absImage(img: Element): String? {
        val src = img.absUrl("src").ifBlank { img.attr("src") }
        return src.takeIf { it.startsWith("http") && !it.contains("data:") }
    }

    /**
     * Pull the object/containment class from a body line like "Object Class: Keter" or the newer
     * "Containment Class: Euclid". Case-insensitive; captures a single class token (letters/hyphen),
     * so esoteric classes (Thaumiel, Cernunnos, Ticonderoga, …) resolve just like the main ones.
     */
    private fun parseObjectClass(text: String): String? {
        val m = Regex("(?:Object|Containment)\\s*Class[:\\s]+([A-Za-z][A-Za-z-]+)", RegexOption.IGNORE_CASE).find(text) ?: return null
        return m.groupValues[1].lowercase().replaceFirstChar { it.uppercase() }
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
