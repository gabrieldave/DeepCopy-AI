package com.blackeye.klipnote

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Extrae el track de audio de un video (sin re-encode) a un .m4a temporal.
 * Si el codec no es soportado por MediaMuxer (p.ej. Opus/WebM) o ya es audio,
 * copia/usa el stream original.
 */
object AudioExtractor {

    private const val TAG = "AudioExtractor"
    private const val BUFFER_SIZE = 1024 * 1024

    data class ExtractedAudio(
        val file: File,
        val mimeType: String,
        val isTemp: Boolean
    )

    fun prepareAudio(context: Context, uri: Uri): ExtractedAudio {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase()
            ?: guessMimeFromUri(uri)
            ?: "application/octet-stream"

        if (mime.startsWith("audio/")) {
            return copyToCache(context, uri, mime, preferredExtForMime(mime))
        }

        // Intentar demux del track de audio a MPEG-4 (.m4a)
        val tempOut = File(context.cacheDir, "klipnote_audio_${System.currentTimeMillis()}.m4a")
        try {
            extractAudioTrack(context, uri, tempOut)
            return ExtractedAudio(
                file = tempOut,
                mimeType = "audio/mp4",
                isTemp = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a stream original: ${e.message}")
            if (tempOut.exists()) tempOut.delete()
            return copyToCache(context, uri, mime, preferredExtForMime(mime))
        }
    }

    private fun extractAudioTrack(context: Context, uri: Uri, outFile: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: extractor.setDataSource(context, uri, null)

            var audioTrackIndex = -1
            var audioFormat: android.media.MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            require(audioTrackIndex >= 0 && audioFormat != null) {
                context.getString(R.string.error_no_audio_track)
            }

            val codecMime = audioFormat.getString(android.media.MediaFormat.KEY_MIME) ?: ""
            // MediaMuxer MPEG-4 soporta AAC / AMR típicos; Opus/Vorbis suelen fallar
            if (codecMime.contains("opus", ignoreCase = true) ||
                codecMime.contains("vorbis", ignoreCase = true) ||
                codecMime.contains("flac", ignoreCase = true)
            ) {
                throw IllegalStateException(
                    context.getString(R.string.error_codec_unsupported, codecMime)
                )
            }

            extractor.selectTrack(audioTrackIndex)
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = extractor.sampleTime
                info.flags = bufferInfoFlagsFromExtractor(extractor.sampleFlags)
                muxer.writeSampleData(muxerTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }

    /**
     * MediaExtractor.sampleFlags y MediaCodec.BufferInfo.flags no son el mismo conjunto.
     * Solo se traducen banderas compatibles conocidas.
     */
    private fun bufferInfoFlagsFromExtractor(sampleFlags: Int): Int {
        var flags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        // SAMPLE_FLAG_PARTIAL_FRAME is API 28+; value is 2 when present.
        if (Build.VERSION.SDK_INT >= 28 &&
            sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0
        ) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }

    private fun copyToCache(
        context: Context,
        uri: Uri,
        mime: String,
        ext: String
    ): ExtractedAudio {
        val out = File(context.cacheDir, "klipnote_raw_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output ->
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                }
            }
        } ?: error("No se pudo abrir el archivo compartido")
        return ExtractedAudio(
            file = out,
            mimeType = mime.ifBlank { "application/octet-stream" },
            isTemp = true
        )
    }

    private fun preferredExtForMime(mime: String): String = when {
        mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
        mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "m4a"
        mime.contains("wav") -> "wav"
        mime.contains("ogg") || mime.contains("opus") -> "ogg"
        mime.contains("webm") -> "webm"
        mime.contains("3gpp") -> "3gp"
        mime.contains("flac") -> "flac"
        else -> "bin"
    }

    private fun guessMimeFromUri(uri: Uri): String? {
        val name = uri.lastPathSegment ?: return null
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    fun cleanup(extracted: ExtractedAudio?) {
        if (extracted?.isTemp == true) {
            runCatching { extracted.file.delete() }
        }
    }
}
