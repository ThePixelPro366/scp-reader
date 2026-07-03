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

        // Remove non-article chrome. Collapsible *content* is kept (addenda live there);
        // only the fold/unfold links are dropped.
        content.select(
            "style, script, .code, .page-rate-widget-box, .creditRate, .rate-box-with-credit-button, " +
                ".licensebox, .license-area, .footer-wikiwalk-nav, #u-credit-view, .modal-container, " +
                ".collapsible-block-link, .collapsible-block-folded, .footnotes-footer, .info-container, " +
                ".scp-image-caption-source, .authorlink-wrapper"
        ).remove()

        val blocks = ArrayList<ContentBlock>()
        var objectClass: String? = null
        var excerpt = ""
        var firstImage: String? = null
        val seen = HashSet<String>()

        fun addImage(container: Element) {
            val img = container.selectFirst("img") ?: return
            val src = absImage(img) ?: return
            if (!seen.add("img:$src")) return
            if (firstImage == null) firstImage = src
            val caption = container.selectFirst(".scp-image-caption")?.text()?.trim().orEmpty()
            blocks.add(ContentBlock.Image(src, caption))
        }

        fun addText(el: Element, bullet: Boolean) {
            val t = el.text().trim()
            if (t.length < 2 || isJunk(t) || !seen.add("p:$t")) return
            if (objectClass == null) objectClass = parseObjectClass(t)
            if (excerpt.isEmpty() && !isMetaLine(t) && t.length > 40) excerpt = t.take(180)
            var spans = inlineSpans(el)
            if (bullet) spans = listOf(InlineSpan("•  ")) + spans
            blocks.add(ContentBlock.Paragraph(if (bullet) "•  $t" else t, spans))
        }

        fun addQuote(el: Element) {
            val t = el.text().trim()
            if (t.length < 2 || isJunk(t) || !seen.add("q:$t")) return
            blocks.add(ContentBlock.Quote(t, quoteSpans(el)))
        }

        // Recursive, document-order walk so images stay in their in-text position.
        fun walk(parent: Element) {
            for (child in parent.children()) {
                val tag = child.tagName()
                when {
                    tag == "img" -> { if (child.absUrl("src").startsWith("http")) { seen.add("img:${child.absUrl("src")}"); if (firstImage == null) firstImage = child.absUrl("src"); blocks.add(ContentBlock.Image(child.absUrl("src"), "")) } }
                    child.hasClass("scp-image-block") || (tag == "div" && child.selectFirst("img") != null && child.text().length < 140) -> addImage(child)
                    tag.length == 2 && tag[0] == 'h' -> child.text().trim().takeIf { it.isNotEmpty() }?.let { if (seen.add("h:$it")) blocks.add(ContentBlock.Heading(it, inlineSpans(child))) }
                    tag == "p" -> addText(child, bullet = false)
                    tag == "blockquote" || (tag == "div" && child.hasClass("blockquote")) -> addQuote(child)
                    tag == "ul" || tag == "ol" -> child.select("> li").forEach { addText(it, bullet = true) }
                    tag == "div" -> walk(child) // section / collapsible content — recurse to keep order
                    else -> {}
                }
            }
        }
        walk(content)

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
        val out = ArrayList<InlineSpan>()
        fun walk(node: Node, bold: Boolean, italic: Boolean, underline: Boolean, strike: Boolean, link: String?) {
            when (node) {
                is TextNode -> {
                    val t = node.text()
                    if (t.isNotEmpty()) out.add(InlineSpan(t, bold, italic, underline, strike, link))
                }
                is Element -> {
                    val tag = node.tagName()
                    val style = node.attr("style").lowercase()
                    val b = bold || tag == "b" || tag == "strong" || style.contains("font-weight: bold") || style.contains("font-weight:bold") || style.contains("font-weight: 7") || style.contains("font-weight: 8") || style.contains("font-weight: 9")
                    val i = italic || tag == "i" || tag == "em" || style.contains("font-style: italic") || style.contains("font-style:italic")
                    val u = underline || tag == "u" || tag == "ins" || style.contains("underline")
                    val s = strike || tag == "s" || tag == "strike" || tag == "del" || style.contains("line-through")
                    // A hyperlink whose href resolves to a real http(s) URL (skips javascript:/anchors).
                    val href = if (tag == "a") node.absUrl("href").takeIf { it.startsWith("http") } else null
                    for (ch in node.childNodes()) walk(ch, b, i, u, s, href ?: link)
                }
            }
        }
        walk(el, false, false, false, false, null)
        // Coalesce adjacent runs sharing the same styling.
        val merged = ArrayList<InlineSpan>()
        for (sp in out) {
            val last = merged.lastOrNull()
            if (last != null && last.bold == sp.bold && last.italic == sp.italic && last.underline == sp.underline && last.strike == sp.strike && last.link == sp.link) {
                merged[merged.size - 1] = last.copy(text = last.text + sp.text)
            } else merged.add(sp)
        }
        // If nothing is styled or linked, drop spans entirely — the plain `text` fallback is enough.
        return if (merged.none { it.bold || it.italic || it.underline || it.strike || it.link != null }) emptyList() else merged
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
