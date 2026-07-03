package com.foundation.scpreader.network

import com.foundation.scpreader.data.ScpItem
import com.foundation.scpreader.data.Taxonomy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

/**
 * Client for the community CROM GraphQL API (api.crom.avn.sh), which indexes the SCP wiki
 * with structured titles, ratings and tags. Used for the browse feed, search and autocomplete.
 */
class CromApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val endpoint = "https://api.crom.avn.sh/graphql"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val enBranch = "http://scp-wiki.wikidot.com"

    // ---- GraphQL wire models ----
    @Serializable private data class Envelope<T>(val data: T? = null)
    @Serializable private data class WikidotInfo(
        val title: String? = null,
        val rating: Int? = null,
        val tags: List<String> = emptyList(),
        val source: String? = null,
        val createdAt: String? = null,
    )
    @Serializable private data class Node(val url: String, val wikidotInfo: WikidotInfo? = null)
    @Serializable private data class Edge(val node: Node)
    @Serializable private data class PageInfo(val endCursor: String? = null, val hasNextPage: Boolean = false)
    @Serializable private data class Pages(val edges: List<Edge> = emptyList(), val pageInfo: PageInfo = PageInfo())
    @Serializable private data class PagesData(val pages: Pages? = null)
    @Serializable private data class SearchData(val searchPages: List<Node> = emptyList())
    @Serializable private data class PageData(val page: Node? = null)

    data class Page(val items: List<ScpItem>, val endCursor: String?, val hasNext: Boolean)

    /**
     * Top-rated EN-branch pages, optionally restricted to a wikidot tag (e.g. "scp", "tale").
     * When [createdAfter] (an ISO-8601 instant) is given, only pages created on or after that
     * time are considered — used to surface the highest-rated pages of the last week.
     */
    suspend fun topRated(tag: String = "scp", after: String? = null, first: Int = 20, createdAfter: String? = null): Page = withContext(Dispatchers.IO) {
        val afterArg = if (after != null) ", after: \"$after\"" else ""
        val createdArg = if (createdAfter != null) ", createdAt: {gte: \"$createdAfter\"}" else ""
        val query = """
            { pages(sort: {key: RATING, order: DESC}, first: $first$afterArg,
                filter: { url: {startsWith: "$enBranch"}, wikidotInfo: { tags: {eq: "$tag"}$createdArg } }) {
                edges { node { url wikidotInfo { title rating tags } } }
                pageInfo { endCursor hasNextPage }
              } }
        """.trimIndent()
        val data = post(query, PagesData.serializer())
        val pages = data?.pages
        Page(
            items = pages?.edges?.mapNotNull { it.node.toItem() }.orEmpty(),
            endCursor = pages?.pageInfo?.endCursor,
            hasNext = pages?.pageInfo?.hasNextPage ?: false,
        )
    }

    /** Free-text search across the wiki (title / number). Matches complete tokens only. */
    suspend fun search(queryText: String): List<ScpItem> = withContext(Dispatchers.IO) {
        val escaped = queryText.replace("\\", "").replace("\"", "")
        val query = """{ searchPages(query: "$escaped", filter: { anyBaseUrl: "$enBranch" }) { url wikidotInfo { title rating tags createdAt } } }"""
        post(query, SearchData.serializer())?.searchPages
            ?.filter { it.url.startsWith(enBranch) }
            ?.mapNotNull { it.toItem() }
            .orEmpty()
    }

    /**
     * Prefix search by SCP number, top-rated first — lets a partial number ("17", "0") narrow the
     * archive the way the wiki search does, which the token-based [search] can't. For 1–2 digit
     * input we also try the zero-padded slug (e.g. "17" → scp-017) so the exact small number shows.
     */
    suspend fun searchByNumberPrefix(digits: String, first: Int = 40): List<ScpItem> = withContext(Dispatchers.IO) {
        val prefixes = buildList {
            add("scp-$digits")
            if (digits.length in 1..2) add("scp-" + digits.padStart(3, '0'))
        }.distinct()
        val out = LinkedHashMap<String, ScpItem>()
        for (p in prefixes) {
            val query = """
                { pages(sort: {key: RATING, order: DESC}, first: $first,
                    filter: { url: {startsWith: "$enBranch/$p"} }) {
                    edges { node { url wikidotInfo { title rating tags createdAt } } }
                  } }
            """.trimIndent()
            post(query, PagesData.serializer())?.pages?.edges?.forEach { e ->
                e.node.toItem()?.let { out.putIfAbsent(it.url, it) }
            }
        }
        out.values.toList()
    }

    /** Fetch a single page's metadata + wikidot source markup (used as a scrape fallback). */
    suspend fun detail(url: String): Pair<ScpItem, String?>? = withContext(Dispatchers.IO) {
        val query = """{ page(url: "${url.replace("\"", "")}") { url wikidotInfo { title rating tags source createdAt } } }"""
        val node = post(query, PageData.serializer())?.page ?: return@withContext null
        val item = node.toItem() ?: return@withContext null
        item to node.wikidotInfo?.source
    }

    private fun <T> post(query: String, serializer: KSerializer<T>): T? {
        val bodyJson = json.encodeToString(RequestPayload.serializer(), RequestPayload(query))
        val request = Request.Builder().url(endpoint).post(bodyJson.toRequestBody(jsonMedia)).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string() ?: return null
            return json.decodeFromString(Envelope.serializer(serializer), text).data
        }
    }

    @Serializable private data class RequestPayload(val query: String)

    private fun Node.toItem(): ScpItem? {
        val info = wikidotInfo ?: return null
        val title = info.title?.takeIf { it.isNotBlank() } ?: return null
        val tags = info.tags
        val slug = url.substringAfterLast('/')
        val type = Taxonomy.typeLabel(tags)
        val number = when {
            slug.matches(Regex("scp-\\d+.*")) -> "SCP-" + slug.removePrefix("scp-").uppercase()
            type == "Tale" -> "TALE"
            type == "GoI" -> "GoI"
            else -> slug.uppercase()
        }
        return ScpItem(
            url = url,
            number = number,
            title = title,
            objectClass = Taxonomy.objectClass(tags),
            typeLabel = type,
            tags = tags,
            rating = info.rating ?: 0,
            createdAt = info.createdAt,
        )
    }
}
