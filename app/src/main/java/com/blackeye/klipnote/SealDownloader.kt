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
 * Envoltorio Seal-style de yt-dlp / FFmpeg / aria2c (io.github.junkfood02.youtubedl-android 0.18.1).
 *
 * Flags audio alineadas con junkfood02/Seal DownloadUtil:
 * `--no-playlist`, `--no-mtime`, `-x`, `--audio-format m4a`,
 * TEXT_ONLY: mono 16 kHz ~32 kbps; otros modos: calidad normal,
 * cookies por sitio, y QuickJS + `--remote-components ejs:github` para YouTube.
 */
object SealDownloader {
    private const val TAG = "SealDownloader"
    private const val PREFS = "seal_downloader"
    private const val KEY_LAST_YTDLP_UPDATE_MS = "last_ytdlp_nightly_ms"
    /** yt-dlp se actualiza solo cada 3 días (y al instante si una descarga falla por cambios del sitio). */
    private const val AUTO_UPDATE_INTERVAL_MS = 3L * 24 * 60 * 60 * 1000

    private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    private val VIDEO_FORMAT =
        """bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b"""

    private val TRACKING_QUERY_KEYS = setOf(
        "igsh", "igshid", "fbclid", "gclid", "si", "feature", "reels_share", "s"
    )

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
        val deleteAfterUse: Boolean,
        /** Nombre con extensión en Descargas/KlipNote, si se guardó en público. */
        val savedDisplayName: String? = null
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
        val clipped = match.value.trim().trimEnd(')', ']', '>', '"', '\'', ',', '.', ';')
        return cleanMediaUrl(clipped)
    }

    /** Limpia tracking de Instagram reel/reels/p (igsh, utm_*, etc.). */
    fun cleanMediaUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path.orEmpty().lowercase()
            val isIg = host.contains("instagram.com") || host == "instagr.am" ||
                host.endsWith("cdninstagram.com")
            val isIgMediaPath = path.contains("/reel") || path.contains("/reels") ||
                path.matches(Regex(".*/p/[^/]+.*"))
            if (!isIg || (!isIgMediaPath && uri.query.isNullOrBlank())) {
                return trimmed
            }
            val builder = uri.buildUpon().clearQuery()
            for (name in uri.queryParameterNames) {
                val key = name.lowercase()
                if (key in TRACKING_QUERY_KEYS || key.startsWith("utm_")) continue
                for (value in uri.getQueryParameters(name)) {
                    builder.appendQueryParameter(name, value)
                }
            }
            builder.build().toString()
        } catch (_: Throwable) {
            trimmed
        }
    }

    private const val LOGIN_MARKER_INTERNAL = "needs sign-in"

    fun looksLikeLoginRequired(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val m = message.lowercase()
        return m.contains(LOGIN_MARKER_INTERNAL) ||
            m.contains("pide iniciar sesión") ||
            m.contains("login required") ||
            m.contains("sign in to confirm") ||
            m.contains("use --cookies") ||
            m.contains("--cookies-from-browser") ||
            (m.contains("cookies") && m.contains("login")) ||
            (m.contains("rate-limit") && m.contains("login")) ||
            m.contains("requested content is not available") ||
            m.contains("this content isn't available")
    }

    /**
     * Convierte errores técnicos de yt-dlp en mensajes que entiende cualquier persona.
     * Siempre agrega el detalle técnico al final (corto) para reportes en GitHub.
     */
    fun friendlyDownloadError(context: Context, raw: String?, url: String?): String {
        val msg = raw.orEmpty()
        val m = msg.lowercase()
        val site = CookieStore.siteForUrl(url)
        val siteName = site?.label ?: context.getString(R.string.site_fallback)
        val loginMarker = context.getString(R.string.login_marker_user)
        val friendly = when {
            looksLikeLoginRequired(msg) ->
                context.getString(R.string.login_download_error, siteName)
            m.contains("unsupported url") ->
                context.getString(R.string.download_error_unsupported)
            m.contains("private video") || m.contains("is private") || m.contains("video unavailable") ||
                m.contains("has been removed") || m.contains("not available in your country") ->
                context.getString(R.string.download_error_unavailable)
            m.contains("unable to resolve host") || m.contains("timed out") ||
                m.contains("network is unreachable") || m.contains("connection reset") ->
                context.getString(R.string.download_error_network)
            else ->
                context.getString(R.string.download_error_site_changed, siteName)
        }
        val detail = msg.lineSequence().firstOrNull { it.contains("ERROR", ignoreCase = true) }
            ?: msg.lineSequence().firstOrNull()
            ?: ""
        return if (detail.isBlank()) {
            friendly
        } else {
            "$friendly\n\n${context.getString(R.string.technical_detail, detail.trim().take(180))}"
        }
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
                maybeAutoUpdateYtDlpSilent(app)
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
        ensureReady(context)
        val status = YoutubeDL.getInstance().updateYoutubeDL(
            context.applicationContext,
            YoutubeDL.UpdateChannel.NIGHTLY
        )
        markYtDlpUpdated(context.applicationContext)
        when (status) {
            YoutubeDL.UpdateStatus.DONE -> context.getString(R.string.ytdlp_update_done)
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> context.getString(R.string.ytdlp_update_up_to_date)
            null -> context.getString(R.string.ytdlp_update_no_response)
        }
    }

    suspend fun download(
        context: Context,
        rawInput: String,
        mode: LinkMode,
        downloadKind: DownloadKind = DownloadKind.AUDIO_M4A,
        onProgress: (String) -> Unit
    ): DownloadArtifact = withContext(Dispatchers.IO) {
        ensureReady(context)
        val url = extractFirstUrl(rawInput)
            ?: throw IllegalArgumentException(context.getString(R.string.error_no_http_url))

        try {
            downloadOnce(context, url, mode, downloadKind, onProgress)
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (t: Throwable) {
            val msg = t.message.orEmpty()
            if (!needsYtDlpHeal(msg)) throw t
            Log.w(TAG, "Auto-heal yt-dlp NIGHTLY tras: $msg")
            onProgress(context.getString(R.string.ytdlp_auto_heal))
            runCatching { updateYtDlpNightly(context) }
            onProgress(context.getString(R.string.download_retry))
            downloadOnce(context, url, mode, downloadKind, onProgress)
        }
    }

    private fun downloadOnce(
        context: Context,
        url: String,
        mode: LinkMode,
        downloadKind: DownloadKind,
        onProgress: (String) -> Unit
    ): DownloadArtifact {
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

        val compressForStt = mode == LinkMode.TEXT_ONLY
        val request = buildRequest(context, url, outTemplate, wantVideo, compressForStt)

        try {
            onProgress(context.getString(R.string.status_downloading_percent, 0))
            YoutubeDL.getInstance().execute(request, processId) { progress, eta, _line ->
                val pct = progress.coerceIn(0f, 100f).toInt()
                val etaLabel = if (eta > 0) {
                    context.getString(R.string.download_progress_eta, eta)
                } else {
                    ""
                }
                onProgress(
                    context.getString(R.string.status_downloading_percent, pct) + etaLabel
                )
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
            ?: throw IllegalStateException(context.getString(R.string.error_ytdlp_no_output))

        val title = produced.nameWithoutExtension
            .replace(Regex("""\s\[[^\]]+]$"""), "")
            .ifBlank { "klipnote_media" }
            .take(120)

        if (!savePublic) {
            return DownloadArtifact(
                file = produced,
                uri = Uri.fromFile(produced),
                title = title,
                isVideo = wantVideo,
                deleteAfterUse = true,
                savedDisplayName = null
            )
        }

        val mime = if (wantVideo) "video/mp4" else "audio/mp4"
        val displayName = sanitizeFileName(title) + if (wantVideo) ".mp4" else ".m4a"
        val publicUri = saveToDownloadsKlipNote(context, produced, displayName, mime)
        return DownloadArtifact(
            file = produced,
            uri = publicUri ?: Uri.fromFile(produced),
            title = title,
            isVideo = wantVideo,
            deleteAfterUse = false,
            savedDisplayName = displayName
        )
    }

    private fun buildRequest(
        context: Context,
        url: String,
        outTemplate: String,
        wantVideo: Boolean,
        compressForStt: Boolean
    ): YoutubeDLRequest {
        return YoutubeDLRequest(url).apply {
            // Mirror Seal downloadVideo / audio path
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("-o", outTemplate)
            if (wantVideo) {
                addOption("-f", VIDEO_FORMAT)
                addOption("--merge-output-format", "mp4")
            } else {
                // Seal addOptionsForAudioDownloads: -x + --audio-format m4a
                // (sin -f rígido ba[ext=m4a] que rompe IG muxed MP4)
                addOption("-x")
                addOption("--audio-format", "m4a")
                if (compressForStt) {
                    // Solo TEXT_ONLY: audio liviano para STT (Groq ≤24 MB).
                    // ~32 kbps mono 16 kHz ≈ 9 MB por 40 min. No baja calidad en modos
                    // que guardan el archivo para el usuario.
                    addOption("--audio-quality", "32K")
                    addOption(
                        "--postprocessor-args",
                        "ExtractAudio+ffmpeg:-ac 1 -ar 16000 -b:a 32k"
                    )
                } else {
                    addOption("--audio-quality", "5")
                }
            }
            addOption("--newline")

            // Seal / youtubedl-android 0.18.x: YouTube exige runtime JS.
            // La librería 0.18.1 ya inyecta --js-runtimes quickjs:<libqjs.so> en execute().
            // Seal además pide EJS desde GitHub (igual que JsRuntimeConfig.applyJsRuntimeConfig).
            applyJsRuntimeLikeSeal(context, this)

            CookieStore.cookiesFileFor(context, url)?.let { cookies ->
                addOption("--cookies", cookies.absolutePath)
                CookieStore.userAgent(context)?.let { ua ->
                    if (ua.isNotEmpty()) {
                        addOption("--add-header", "User-Agent:$ua")
                    }
                }
            }
        }
    }

    /**
     * Réplica del cableado de Seal (JsRuntimeConfig): QuickJS + EJS remoto.
     * No fijamos player_client a mano; yt-dlp elige el cliente.
     */
    private fun applyJsRuntimeLikeSeal(context: Context, request: YoutubeDLRequest) {
        val qjs = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")
        if (qjs.isFile) {
            // Por si una build futura no lo inyecta sola; 0.18.1 ya lo hace en execute().
            if (!request.hasOption("--js-runtimes")) {
                request.addOption("--js-runtimes", "quickjs:${qjs.absolutePath}")
            }
        } else {
            Log.w(TAG, "libqjs.so no encontrado en nativeLibraryDir; YouTube puede fallar sin JS runtime")
        }
        if (!request.hasOption("--remote-components")) {
            request.addOption("--remote-components", "ejs:github")
        }
    }

    fun cleanupArtifact(artifact: DownloadArtifact) {
        if (!artifact.deleteAfterUse) return
        runCatching { artifact.file.delete() }
        runCatching { artifact.file.parentFile?.deleteRecursively() }
    }

    /**
     * Copia liviana para STT (mono / 16 kHz / ~32 kbps) con el `libffmpeg.so` de
     * youtubedl-android. La librería no expone FFmpeg.execute; se invoca el binario nativo.
     * El archivo [input] (calidad completa del usuario) no se modifica.
     */
    fun compressAudioForStt(context: Context, input: File): File {
        require(input.isFile && input.length() > 0L) {
            context.getString(R.string.error_compress_no_input)
        }
        ensureReady(context)
        val app = context.applicationContext
        val out = File(app.cacheDir, "stt_compact_${System.currentTimeMillis()}.m4a")
        val ffmpegBin = File(app.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        require(ffmpegBin.isFile) {
            context.getString(R.string.error_ffmpeg_missing)
        }
        val packages = File(app.noBackupFilesDir, "youtubedl-android/packages")
        val ldParts = listOf(
            File(packages, "ffmpeg/usr/lib"),
            File(packages, "python/usr/lib"),
            File(packages, "aria2c/usr/lib"),
            File(app.applicationInfo.nativeLibraryDir)
        ).filter { it.isDirectory }.map { it.absolutePath }
        val cmd = listOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", input.absolutePath,
            "-vn",
            "-ac", "1",
            "-ar", "16000",
            "-c:a", "aac",
            "-b:a", "32k",
            out.absolutePath
        )
        Log.i(TAG, "STT compress: ${input.length() / 1024} KB → ${out.name}")
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        val env = pb.environment()
        val existingLd = env["LD_LIBRARY_PATH"].orEmpty()
        env["LD_LIBRARY_PATH"] = (ldParts + existingLd).filter { it.isNotBlank() }.joinToString(":")
        val process = pb.start()
        val log = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        if (code != 0 || !out.isFile || out.length() == 0L) {
            runCatching { out.delete() }
            throw IllegalStateException(
                context.getString(
                    R.string.error_compress_failed,
                    code,
                    log.lineSequence().lastOrNull { it.isNotBlank() }?.take(160).orEmpty()
                )
            )
        }
        Log.i(TAG, "STT compress OK: ${out.length() / 1024} KB")
        return out
    }

    private fun needsYtDlpHeal(message: String): Boolean {
        val m = message.lowercase()
        return m.contains("unable to extract") ||
            m.contains("confirm you are on the latest version") ||
            m.contains("metadata extraction failed") ||
            m.contains("shared data") ||
            m.contains("http error 403") ||
            m.contains("signature") ||
            m.contains("nsig") ||
            m.contains("javascript") ||
            m.contains("requested format is not available") ||
            m.contains("no video formats found")
    }

    private fun maybeAutoUpdateYtDlpSilent(app: Context) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_YTDLP_UPDATE_MS, 0L)
        val now = System.currentTimeMillis()
        if (last != 0L && now - last < AUTO_UPDATE_INTERVAL_MS) return
        try {
            Log.i(TAG, "Auto-update yt-dlp NIGHTLY (first init / 3d)")
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.NIGHTLY)
            markYtDlpUpdated(app)
        } catch (t: Throwable) {
            Log.w(TAG, "Auto-update yt-dlp falló (no bloquea)", t)
        }
    }

    private fun markYtDlpUpdated(app: Context) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_YTDLP_UPDATE_MS, System.currentTimeMillis())
            .apply()
    }

    private fun ensureReady(context: Context) {
        if (_ready.value == ReadyState.READY && initialized.get()) return
        throw IllegalStateException(
            _readyError.value ?: context.getString(R.string.error_ytdlp_not_ready)
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
