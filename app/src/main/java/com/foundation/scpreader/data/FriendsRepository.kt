package com.foundation.scpreader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foundation.scpreader.network.FriendsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID

/** Default backend URL — the /webserver deploy; overridable in Settings. */
const val DEFAULT_FRIENDS_SERVER = "https://thepixelpro.de/scpBackend/"

private val Context.friendsStore by preferencesDataStore(name = "friends")

/**
 * Owns the device's friend identity and talks to the [FriendsApi].
 *
 * Identity is a random token generated once on this device and persisted via DataStore — there
 * are no accounts or passwords. The shareable friend code is minted by the server on first
 * registration. The server URL is user-configurable so hosting can change without an app update;
 * it's cached in memory so the API's per-request base-URL lookup stays cheap.
 */
class FriendsRepository(
    private val context: Context,
    http: OkHttpClient,
    scope: CoroutineScope,
) {
    private object Keys {
        val token = stringPreferencesKey("device_token")
        val friendCode = stringPreferencesKey("friend_code")
        val serverUrl = stringPreferencesKey("server_url")
        val lastSeenRec = longPreferencesKey("last_seen_rec_id")
    }

    @Volatile private var serverUrlCache = DEFAULT_FRIENDS_SERVER
    private val api = FriendsApi(http) { serverUrlCache }

    init {
        // Warm the in-memory server URL from persistence so the first API call uses the saved host.
        scope.launch { serverUrlCache = context.friendsStore.data.first()[Keys.serverUrl] ?: DEFAULT_FRIENDS_SERVER }
    }

    val serverUrlFlow = context.friendsStore.data.map { it[Keys.serverUrl] ?: DEFAULT_FRIENDS_SERVER }

    suspend fun setServerUrl(url: String) {
        val clean = url.trim()
        serverUrlCache = clean.ifEmpty { DEFAULT_FRIENDS_SERVER }
        context.friendsStore.edit { it[Keys.serverUrl] = clean }
    }

    /** The persisted device token, generating and storing one on first access. */
    private suspend fun token(): String {
        val existing = context.friendsStore.data.first()[Keys.token]
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        context.friendsStore.edit { it[Keys.token] = fresh }
        return fresh
    }

    suspend fun cachedFriendCode(): String? = context.friendsStore.data.first()[Keys.friendCode]

    /**
     * Ensure this device is registered and return its friend code. Idempotent: registers on first
     * run, otherwise re-confirms with the server (which returns the existing code).
     */
    suspend fun ensureRegistered(): String {
        val code = api.register(token(), "")
        context.friendsStore.edit { it[Keys.friendCode] = code }
        return code
    }

    suspend fun addFriend(friendCode: String): FriendsApi.Friend =
        api.addFriend(token(), friendCode.trim().uppercase())

    suspend fun removeFriend(friendCode: String) = api.removeFriend(token(), friendCode.trim().uppercase())

    /** Highest recommendation id already surfaced to the user (0 = none seen yet). */
    suspend fun lastSeenRecId(): Long = context.friendsStore.data.first()[Keys.lastSeenRec] ?: 0L

    suspend fun setLastSeenRecId(id: Long) {
        context.friendsStore.edit { it[Keys.lastSeenRec] = id }
    }

    suspend fun friends(): List<FriendsApi.Friend> = api.friends(token())

    suspend fun recommendations(): List<FriendsApi.Rec> = api.recommendations(token())

    suspend fun recommend(friendCode: String, item: ScpItem, note: String) {
        api.recommend(token(), friendCode, item.url, item.number, item.title, note)
    }
}
