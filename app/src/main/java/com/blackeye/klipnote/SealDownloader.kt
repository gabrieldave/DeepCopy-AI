package com.blackeye.klipnote

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Envoltorio Seal-style de yt-dlp / FFmpeg / aria2c (io.github.junkfood02.youtubedl-android 0.17.3).
 */
object SealDownloader {
    private const val TAG = "SealDownloader"
    private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    private val AUDIO_FORMAT =
        """ba[ext=m4a][abr<=96]/ba[ext=m4a]/ba/b"""
    private val VIDEO_FORMAT =
        """bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b"""

    enum class ReadyState { IDLE, READY, ERROR }

    enum class LinkMode {
        TEXT_ONLY,
        AUDIO_AND_TEXT,
        VIDEO_AND_TEXT,
        DOWNLOAD_ONLY
    }

    enum class DownloadKind { AUDIO_M4A, VIDEO_MP4 }

    data class DownloadArtifact(
        val file: File,
        val uri: Uri?,
        val title: String,
        val isVideo: Boolean,
        val deleteAfterUse: Boolean
    )

    private val initialized = AtomicBoolean(false)
    private val _ready = MutableStateFlow(ReadyState.IDLE)
    val ready: StateFlow<ReadyState> = _ready.asStateFlow()

    private val _readyError = MutableStateFlow<String?>(null)
    val readyError: StateFlow<String?> = _readyError.asStateFlow()

    private val activeProcessId = AtomicReference<String?>(null)

    fun extractFirstUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val match = URL_REGEX.find(raw.trim()) ?: return null
        return match.value.trim().trimEnd(')', ']', '>', '"', '\'', ',', '.', ';')
    }

    fun initAsync(context: Context) {
        if (initialized.get()) return
        val app = context.applicationContext
        Thread {
            try {
                YoutubeDL.init(app)
                FFmpeg.init(app)
                Aria2c.init(app)
                initialized.set(true)
                _ready.value = ReadyState.READY
                _readyError.value = null
                Log.i(TAG, "YoutubeDL/FFmpeg/Aria2c listos")
            } catch (t: Throwable) {
                Log.e(TAG, "Init falló", t)
                _ready.value = ReadyState.ERROR
                _readyError.value = t.message ?: t.javaClass.simpleName
            }
        }.start()
    }

    fun cancelActive() {
        val id = activeProcessId.getAndSet(null) ?: return
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
    }

    suspend fun updateYtDlpNightly(context: Context): String = withContext(Dispatchers.IO) {
        ensureReady()
        val status = YoutubeDL.getInstance().updateYoutubeDL(
            context.applicationContext,
            YoutubeDL.UpdateChannel.NIGHTLY
        )
        when (status) {
            YoutubeDL.UpdateStatus.DONE -> "ACTUALIZADO"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "YA ESTÁ AL DÍA"
            null -> "Sin respuesta de actualización"
        }
    }

    suspend fun download(
        context: Context,
        rawInput: String,
        mode: LinkMode,
        downloadKind: DownloadKind = DownloadKind.AUDIO_M4A,
        onProgress: (String) -> Unit
    ): DownloadArtifact = withContext(Dispatchers.IO) {
        ensureReady()
        val url = extractFirstUrl(rawInput)
            ?: throw IllegalArgumentException("No hay URL http(s) en el texto")

        val wantVideo = when (mode) {
            LinkMode.VIDEO_AND_TEXT -> true
            LinkMode.DOWNLOAD_ONLY -> downloadKind == DownloadKind.VIDEO_MP4
            else -> false
        }
        val savePublic = mode != LinkMode.TEXT_ONLY
        val workDir = File(context.cacheDir, "ydl_${System.currentTimeMillis()}").also {
            it.mkdirs()
        }
        val outTemplate = File(workDir, "%(title).180B [%(id)s].%(ext)s").absolutePath
        val processId = UUID.randomUUID().toString()
        activeProcessId.set(processId)

        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("-o", outTemplate)
            if (wantVideo) {
                addOption("-f", VIDEO_FORMAT)
                addOption("--merge-output-format", "mp4")
            } else {
                addOption("-x")
                addOption("-f", AUDIO_FORMAT)
                addOption("--audio-format", "m4a")
            }
            addOption("--newline")
        }

        try {
            onProgress("Descargando: 0% • preparando…")
            YoutubeDL.getInstance().execute(request, processId) { progress, eta, _line ->
                val pct = progress.coerceIn(0f, 100f).toInt()
                val etaLabel = if (eta > 0) " • ETA ${eta}s" else ""
                onProgress("Descargando: $pct%$etaLabel")
            }
        } catch (e: YoutubeDL.CanceledException) {
            workDir.deleteRecursively()
            throw e
        } finally {
            activeProcessId.compareAndSet(processId, null)
        }

        val produced = workDir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".part") }
            .maxByOrNull { it.length() }
            ?: throw IllegalStateException("yt-dlp no generó archivo")

        val title = produced.nameWithoutExtension
            .replace(Regex("""\s\[[^\]]+]$"""), "")
            .ifBlank { "klipnote_media" }
            .take(120)

        if (!savePublic) {
            return@withContext DownloadArtifact(
                file = produced,
                uri = Uri.fromFile(produced),
                title = title,
                isVideo = wantVideo,
                deleteAfterUse = true
            )
        }

        val mime = if (wantVideo) "video/mp4" else "audio/mp4"
        val displayName = sanitizeFileName(title) + if (wantVideo) ".mp4" else ".m4a"
        val publicUri = saveToDownloadsKlipNote(context, produced, displayName, mime)
        DownloadArtifact(
            file = produced,
            uri = publicUri ?: Uri.fromFile(produced),
            title = title,
            isVideo = wantVideo,
            deleteAfterUse = false
        )
    }

    fun cleanupArtifact(artifact: DownloadArtifact) {
        if (!artifact.deleteAfterUse) return
        runCatching { artifact.file.delete() }
        runCatching { artifact.file.parentFile?.deleteRecursively() }
    }

    private fun ensureReady() {
        if (_ready.value == ReadyState.READY && initialized.get()) return
        throw IllegalStateException(
            _readyError.value ?: "YoutubeDL aún no está listo. Espera unos segundos."
        )
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "klipnote" }
        return cleaned.take(100)
    }

    private fun saveToDownloadsKlipNote(
        context: Context,
        source: File,
        displayName: String,
        mime: String
    ): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val collection = if (mime.startsWith("video/")) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                // Prefer Downloads tree for both.
                val downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/KlipNote"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(downloads, values)
                    ?: resolver.insert(collection, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { inp -> inp.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "KlipNote"
                )
                if (!dir.exists()) dir.mkdirs()
                val dest = File(dir, displayName)
                source.copyTo(dest, overwrite = true)
                Uri.fromFile(dest)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo guardar en Downloads/KlipNote", t)
            null
        }
    }
}
