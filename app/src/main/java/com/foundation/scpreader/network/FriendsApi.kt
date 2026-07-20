package com.foundation.scpreader.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin OkHttp client for the friends/recommendation backend (see /webserver).
 *
 * Identity is a device-generated [token]; there are no accounts. [baseUrl] is user-configurable
 * (Settings) so the server can move hosts without an app update. No secrets are held here — the
 * only credential is the per-device token, sent as a bearer header.
 */
class FriendsApi(
    private val client: OkHttpClient,
    private val baseUrl: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Serializable data class Friend(val friend_code: String, val name: String = "")
    @Serializable data class Rec(
        val id: Int = 0,
        val from_code: String = "",
        val from_name: String = "",
        val scp_url: String = "",
        val scp_number: String = "",
        val scp_title: String = "",
        val note: String = "",
        val created_at: Long = 0L,
    )

    @Serializable private data class RegisterReq(val token: String, val name: String = "")
    @Serializable private data class RegisterResp(val friend_code: String, val name: String = "")
    @Serializable private data class AddFriendReq(val friend_code: String)
    @Serializable private data class AddFriendResp(val friend: Friend)
    @Serializable private data class FriendsResp(val friends: List<Friend> = emptyList())
    @Serializable private data class RecsResp(val recommendations: List<Rec> = emptyList())
    @Serializable private data class RecommendReq(
        val friend_code: String,
        val scp_url: String,
        val scp_number: String,
        val scp_title: String,
        val note: String,
    )
    @Serializable private data class ErrorResp(val error: String = "")

    /** Trailing-slash-safe join of the configured base URL with an endpoint file. */
    private fun endpoint(path: String): String =
        baseUrl().trim().trimEnd('/') + "/" + path

    private fun bodyOf(text: String) = text.toRequestBody(jsonMedia)

    /** Extract the server's { "error": ... } message from a failed response, if present. */
    private fun errorMessage(raw: String, code: Int): String =
        runCatching { json.decodeFromString(ErrorResp.serializer(), raw).error }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "Request failed ($code)"

    /** Register (or re-confirm) this device's token; returns its shareable friend code. */
    suspend fun register(token: String, name: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(endpoint("register.php"))
            .post(bodyOf(json.encodeToString(RegisterReq.serializer(), RegisterReq(token, name))))
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException(errorMessage(raw, resp.code))
            json.decodeFromString(RegisterResp.serializer(), raw).friend_code
        }
    }

    suspend fun addFriend(token: String, friendCode: String): Friend = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(endpoint("add_friend.php"))
            .header("Authorization", "Bearer $token")
            .post(bodyOf(json.encodeToString(AddFriendReq.serializer(), AddFriendReq(friendCode))))
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException(errorMessage(raw, resp.code))
            json.decodeFromString(AddFriendResp.serializer(), raw).friend
        }
    }

    suspend fun removeFriend(token: String, friendCode: String): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(endpoint("remove_friend.php"))
            .header("Authorization", "Bearer $token")
            .post(bodyOf(json.encodeToString(AddFriendReq.serializer(), AddFriendReq(friendCode))))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException(errorMessage(resp.body?.string().orEmpty(), resp.code))
        }
    }

    suspend fun friends(token: String): List<Friend> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(endpoint("friends.php"))
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException(errorMessage(raw, resp.code))
            json.decodeFromString(FriendsResp.serializer(), raw).friends
        }
    }

    suspend fun recommendations(token: String): List<Rec> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(endpoint("recommendations.php"))
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException(errorMessage(raw, resp.code))
            json.decodeFromString(RecsResp.serializer(), raw).recommendations
        }
    }

    suspend fun recommend(
        token: String,
        friendCode: String,
        scpUrl: String,
        scpNumber: String,
        scpTitle: String,
        note: String,
    ): Unit = withContext(Dispatchers.IO) {
        val payload = RecommendReq(friendCode, scpUrl, scpNumber, scpTitle, note)
        val req = Request.Builder()
            .url(endpoint("recommend.php"))
            .header("Authorization", "Bearer $token")
            .post(bodyOf(json.encodeToString(RecommendReq.serializer(), payload)))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException(errorMessage(resp.body?.string().orEmpty(), resp.code))
        }
    }
}
