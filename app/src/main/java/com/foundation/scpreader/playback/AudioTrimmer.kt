package com.foundation.scpreader.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Physically removes segments from an audio file using Media3 Transformer: each KEEP range becomes a
 * clipped [EditedMediaItem]; concatenating them in one [EditedMediaItemSequence] drops the gaps
 * (the skip segments). Output is a single AAC/MP4 file. No FFmpeg — same media3 family as ExoPlayer.
 */
class AudioTrimmer(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Write [keepRanges] of [inputPath] to [outputPath] as one concatenated audio file.
     * Returns true on success. Transformer requires a Looper, so this runs on the main dispatcher.
     */
    suspend fun trim(inputPath: String, outputPath: String, keepRanges: List<Pair<Long, Long>>): Boolean {
        if (keepRanges.isEmpty()) return false
        val inputUri = Uri.fromFile(File(inputPath))
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val items = keepRanges.map { (start, end) ->
                    val clip = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(start)
                        .setEndPositionMs(end)
                        .build()
                    val mediaItem = MediaItem.Builder().setUri(inputUri).setClippingConfiguration(clip).build()
                    EditedMediaItem.Builder(mediaItem).build()
                }
                val composition = Composition.Builder(EditedMediaItemSequence(items)).build()
                val transformer = Transformer.Builder(appContext)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (cont.isActive) cont.resume(true)
                        }
                        override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()
                runCatching { transformer.start(composition, outputPath) }
                    .onFailure { if (cont.isActive) cont.resume(false) }
                cont.invokeOnCancellation { runCatching { transformer.cancel() } }
            }
        }
    }
}
