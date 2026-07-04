package com.foundation.scpreader.network

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * NewPipeExtractor [Downloader] backed by the app's shared OkHttp client, so YouTube extraction
 * reuses the same connection pool/timeouts as the rest of the app (no extra HTTP stack).
 */
class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()

        val body = when {
            data != null -> data.toRequestBody(null, 0, data.size)
            // OkHttp requires a body for these verbs; NewPipe only ever uses GET/POST.
            httpMethod == "POST" || httpMethod == "PUT" || httpMethod == "PATCH" -> ByteArray(0).toRequestBody(null)
            else -> null
        }

        val builder = okhttp3.Request.Builder().method(httpMethod, body).url(url)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", url)
            val responseBody = resp.body?.string().orEmpty()
            val latestUrl = resp.request.url.toString()
            return Response(resp.code, resp.message, resp.headers.toMultimap(), responseBody, latestUrl)
        }
    }
}
