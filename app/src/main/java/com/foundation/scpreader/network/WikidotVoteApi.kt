package com.foundation.scpreader.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Casts the signed-in user's own vote on the SCP wiki, on their behalf, one tap at a time — this is
 * the account owner's normal action, just issued from the app. Wikidot's rating is a plus/minus
 * (`type:"PM"`) AJAX action: login on www.wikidot.com yields a `WIKIDOT_SESSION_ID` that also
 * authorises the scp-wiki subdomain; the CSRF is a double-submit `wikidot_token7` (cookie == param,
 * any value). Re-rating = cancel the current vote, then rate again.
 */
class WikidotVoteApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val ua = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 SCPReader"

    @Serializable
    private data class Resp(
        val status: String? = null,
        val points: Int? = null,
        val body: String? = null,
        val message: String? = null,
    )

    /** Outcome of a rate/cancel call. */
    sealed interface RateResult {
        /** Applied; [points] is the page's new total when the server returned it. */
        data class Ok(val points: Int?) : RateResult
        /** The server refused because a vote already exists; [current] is the user's existing value. */
        data class AlreadyVoted(val current: Int?) : RateResult
        /** The session is missing/expired — the caller should prompt a re-login. */
        data object NeedLogin : RateResult
        data class Error(val message: String) : RateResult
    }

    /** Log in with the user's own credentials; returns the `WIKIDOT_SESSION_ID` on success. */
    suspend fun login(user: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Seed a CSRF token from a plain GET (double-submit: this same value goes in cookie + param).
            val seedReq = Request.Builder().url("https://www.wikidot.com/").header("User-Agent", ua).build()
            val token = client.newCall(seedReq).execute().use { cookie(it, "wikidot_token7") } ?: randomToken()
            val form = FormBody.Builder()
                .add("login", user).add("password", password)
                .add("action", "Login2Action").add("event", "login")
                .add("callbackIndex", "0").add("moduleName", "Empty")
                .add("wikidot_token7", token).build()
            val req = Request.Builder()
                .url("https://www.wikidot.com/ajax-module-connector.php")
                .header("User-Agent", ua).header("Referer", "https://www.wikidot.com/")
                .header("Cookie", "wikidot_token7=$token")
                .post(form).build()
            client.newCall(req).execute().use { resp ->
                val session = cookie(resp, "WIKIDOT_SESSION_ID")
                val parsed = runCatching { json.decodeFromString(Resp.serializer(), resp.body?.string().orEmpty()) }.getOrNull()
                if (parsed?.status == "ok" && !session.isNullOrBlank()) session
                else throw IllegalStateException("Login failed — check your username and password")
            }
        }
    }

    suspend fun rate(session: String, pageId: String, points: Int): RateResult =
        post(session, pageId, event = "ratePage", points = points)

    suspend fun cancel(session: String, pageId: String): RateResult =
        post(session, pageId, event = "cancelVote", points = null)

    private suspend fun post(session: String, pageId: String, event: String, points: Int?): RateResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = randomToken()
                val form = FormBody.Builder()
                    .add("pageId", pageId)
                    .apply { if (points != null) add("points", points.toString()) }
                    .add("action", "RateAction").add("event", event)
                    .add("moduleName", "Empty").add("wikidot_token7", token).build()
                val req = Request.Builder()
                    .url("https://scp-wiki.wikidot.com/ajax-module-connector.php")
                    .header("User-Agent", ua).header("Referer", "https://scp-wiki.wikidot.com/")
                    .header("Cookie", "WIKIDOT_SESSION_ID=$session; wikidot_token7=$token")
                    .post(form).build()
                client.newCall(req).execute().use { resp ->
                    val parsed = json.decodeFromString(Resp.serializer(), resp.body?.string().orEmpty())
                    val note = (parsed.body ?: parsed.message).orEmpty()
                    when {
                        parsed.status == "ok" -> RateResult.Ok(parsed.points)
                        parsed.status == "already_voted" -> RateResult.AlreadyVoted(currentRating(note))
                        note.contains("sign in", true) || note.contains("create a wikidot account", true) -> RateResult.NeedLogin
                        else -> RateResult.Error(parsed.status ?: "Vote failed")
                    }
                }
            }.getOrElse { RateResult.Error(it.message ?: "Vote failed") }
        }

    /** Pull the user's existing rating out of an already_voted prompt body ("current rating is: +1"). */
    private fun currentRating(body: String): Int? =
        Regex("current rating is:\\s*<b>\\s*([+-]?\\d+)", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.get(1)?.toIntOrNull()

    /** Read a Set-Cookie value by name from a response; null if absent or a deletion. */
    private fun cookie(resp: okhttp3.Response, name: String): String? =
        resp.headers("Set-Cookie").firstNotNullOfOrNull { h ->
            if (!h.startsWith("$name=")) null
            else h.substringAfter('=').substringBefore(';').takeIf { it.isNotBlank() && it != "deleted" }
        }

    private fun randomToken(): String {
        val cs = "0123456789abcdef"
        return buildString { repeat(16) { append(cs.random()) } }
    }
}
