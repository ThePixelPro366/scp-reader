package com.foundation.scpreader.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

private const val REPO = "ThePixelPro366/scp-reader"
private const val API_VERSION = "2022-11-28"

@Serializable
data class GithubAsset(
    val id: Long,
    val name: String,
    val url: String, // API asset URL — used with an unauthenticated request; works on a public repo
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

/** Outcome of an update check, covering every state the Settings screen and banner need to show. */
sealed interface UpdateCheckResult {
    data object Idle : UpdateCheckResult
    data object Checking : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object NoReleases : UpdateCheckResult
    data class Available(val release: GithubRelease, val asset: GithubAsset) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val percent: Int) : UpdateDownloadState
    data class ReadyToInstall(val file: File) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

/**
 * Checks ThePixelPro366/scp-reader's GitHub Releases for a newer version and downloads the release
 * APK asset. The repo is public, so every call is unauthenticated — no credentials are stored or
 * sent anywhere.
 */
class UpdateManager(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Fetches the latest release and compares its tag against [currentVersion]. */
    suspend fun checkForUpdate(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .build()
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 403 -> UpdateCheckResult.Error("GitHub rate-limited this check — try again later")
                    resp.code == 404 -> UpdateCheckResult.NoReleases
                    !resp.isSuccessful -> UpdateCheckResult.Error("GitHub returned HTTP ${resp.code}")
                    else -> {
                        val release = json.decodeFromString(GithubRelease.serializer(), resp.body?.string().orEmpty())
                        val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                        when {
                            !isNewer(currentVersion, release.tagName) -> UpdateCheckResult.UpToDate
                            asset == null -> UpdateCheckResult.Error("Latest release (${release.tagName}) has no APK asset")
                            else -> UpdateCheckResult.Available(release, asset)
                        }
                    }
                }
            }
        }.getOrElse { UpdateCheckResult.Error(it.message ?: "Update check failed") }
    }

    /**
     * Downloads [asset]'s binary to [dest] from its API `url` (not `browser_download_url`), with an
     * `application/octet-stream` Accept header; GitHub 302s to a pre-signed, unauthenticated blob URL
     * that OkHttp follows automatically.
     */
    suspend fun downloadAsset(asset: GithubAsset, dest: File, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(asset.url)
                .header("Accept", "application/octet-stream")
                .header("X-GitHub-Api-Version", API_VERSION)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} downloading update")
                val body = resp.body ?: throw IOException("empty update response body")
                val total = body.contentLength()
                dest.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Long = 0
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) onProgress(((read * 100) / total).toInt())
                        }
                    }
                }
            }
            true
        }

    /** Compares dotted version strings (an optional leading "v"/"V" is ignored either side). */
    private fun isNewer(current: String, latest: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split(".", "-", "+")
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(current)
        val b = parts(latest)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return y > x
        }
        return false
    }
}
