package com.foundation.scpreader

import com.foundation.scpreader.data.ContentBlock
import com.foundation.scpreader.network.ScpScraper
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM parser tests for [ScpScraper.parse]. The wiki markup shifts periodically, so these pin
 * the structure the scraper depends on (tables, headers, inline formatting) without hitting the network.
 */
class ScpScraperTest {

    private fun parse(bodyHtml: String) =
        ScpScraper().parse(
            Jsoup.parse("<html><body><div id=\"page-content\">$bodyHtml</div></body></html>"),
            "http://scp-wiki.wikidot.com/scp-3999",
        )

    @Test
    fun parsesWikiContentTableWithHeaderAndInlineFormatting() {
        val html = """
            <table class="wiki-content-table">
              <tr><th>Species</th><th>Notes</th></tr>
              <tr><td>Metal eating fungus</td><td>(<em>Trametes ferrium</em>) leaks corrosive fluid</td></tr>
            </table>
        """.trimIndent()
        val table = parse(html).blocks.filterIsInstance<ContentBlock.Table>().singleOrNull()
        requireNotNull(table) { "expected a Table block" }
        assertEquals(2, table.rows.size)
        assertTrue("first row should be a header row", table.rows[0].header)
        assertTrue("body row should not be a header row", !table.rows[1].header)
        assertEquals(2, table.rows[0].cells.size)
        assertEquals("Species", table.rows[0].cells[0].text)
        // The italic <em> run must survive as an inline span, not be flattened away.
        val notesCell = table.rows[1].cells[1]
        assertTrue("expected an italic span for <em>", notesCell.spans.any { it.italic })
    }

    @Test
    fun skipsListPagesNavigationTables() {
        val html = """
            <table style="width: 100%;">
              <tr><td><div class="list-pages-box"><div class="list-pages-item">SCP-0001</div></div></td></tr>
            </table>
        """.trimIndent()
        assertNull(
            "ListPages navigation tables should not render as a grid",
            parse(html).blocks.filterIsInstance<ContentBlock.Table>().firstOrNull(),
        )
    }
}
