package com.blackeye.klipnote

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service (dataSync): descarga yt-dlp + transcripción sin cortarse
 * al salir de la app o bloquear la pantalla.
 */
class KlipNoteWorkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var workJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS_PROGRESS -> {
                // Arranca FGS un instante para poder REMOVE una notificación huérfana (DETACH viejo).
                // No toca JobHub: solo limpia la notificación.
                val notif = KlipNoteNotify.progressNotification(this, "Limpiando…")
                if (Build.VERSION.SDK_INT >= 34) {
                    ServiceCompat.startForeground(
                        this,
                        KlipNoteNotify.NOTIF_PROGRESS_ID,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    @Suppress("DEPRECATION")
                    startForeground(KlipNoteNotify.NOTIF_PROGRESS_ID, notif)
                }
                dismissProgressForeground()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                SealDownloader.cancelActive()
                workJob?.cancel()
                KlipNoteJobHub.setCancelled()
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_START_LINK -> {
                if (workJob?.isActive == true) {
                    Log.w(TAG, "Ya hay un trabajo en curso")
                    return START_NOT_STICKY
                }
                val rawUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
                if (rawUrl.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: SealDownloader.LinkMode.TEXT_ONLY.name
                val mode = runCatching { SealDownloader.LinkMode.valueOf(modeName) }
                    .getOrDefault(SealDownloader.LinkMode.TEXT_ONLY)
                val kindName = intent.getStringExtra(EXTRA_KIND)
                    ?: SealDownloader.DownloadKind.AUDIO_M4A.name
                val kind = runCatching { SealDownloader.DownloadKind.valueOf(kindName) }
                    .getOrDefault(SealDownloader.DownloadKind.AUDIO_M4A)
                val languageMode = runCatching {
                    LanguageMode.valueOf(intent.getStringExtra(EXTRA_LANG_MODE) ?: LanguageMode.AUTO.name)
                }.getOrDefault(LanguageMode.AUTO)
                val forcedLang = intent.getStringExtra(EXTRA_FORCED_LANG)
                val diarize = intent.getBooleanExtra(EXTRA_DIARIZE, false)
                val routing = intent.getStringExtra(EXTRA_ROUTING) ?: "AUTO"
                val manualProvider = intent.getStringExtra(EXTRA_MANUAL_PROVIDER)

                startAsForeground("Descargando…")
                workJob = scope.launch {
                    runLinkJob(
                        rawUrl = rawUrl,
                        mode = mode,
                        kind = kind,
                        languageMode = languageMode,
                        forcedLang = forcedLang,
                        diarize = diarize,
                        routing = routing,
                        manualProvider = manualProvider
                    )
                    stopSelfSafely()
                }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(label: String) {
        KlipNoteNotify.ensureChannels(this)
        val notif = KlipNoteNotify.progressNotification(this, label)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                KlipNoteNotify.NOTIF_PROGRESS_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(KlipNoteNotify.NOTIF_PROGRESS_ID, notif)
        }
        KlipNoteJobHub.setRunning(label)
    }

    private suspend fun runLinkJob(
        rawUrl: String,
        mode: SealDownloader.LinkMode,
        kind: SealDownloader.DownloadKind,
        languageMode: LanguageMode,
        forcedLang: String?,
        diarize: Boolean,
        routing: String,
        manualProvider: String?
    ) {
        val app = applicationContext
        var artifact: SealDownloader.DownloadArtifact? = null
        try {
            publish("Descargando 0%…", 0)
            SealDownloader.initAsync(app)
            // Espera breve a que yt-dlp esté listo
            var waits = 0
            while (SealDownloader.ready.value == SealDownloader.ReadyState.IDLE && waits < 40) {
                kotlinx.coroutines.delay(250)
                waits++
            }
            if (SealDownloader.ready.value != SealDownloader.ReadyState.READY) {
                throw IllegalStateException(
                    SealDownloader.readyError.value ?: "yt-dlp no está listo"
                )
            }

            artifact = SealDownloader.download(
                context = app,
                rawInput = rawUrl,
                mode = mode,
                downloadKind = kind,
                onProgress = { label ->
                    val pct = Regex("""(\d+)%""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val pretty = when {
                        pct != null -> "Descargando $pct%…"
                        label.contains("Actualizando", ignoreCase = true) -> label
                        label.contains("Reintentando", ignoreCase = true) -> label
                        else -> label
                    }
                    publish(pretty, pct)
                }
            )

            val savedMedia = artifact.savedDisplayName?.let { name ->
                artifact.uri?.let { uri ->
                    if (uri.scheme == "content") {
                        SavedMediaInfo(uri = uri, displayName = name, isVideo = artifact.isVideo)
                    } else null
                }
            }

            if (mode == SealDownloader.LinkMode.DOWNLOAD_ONLY) {
                val msg = "✅ Tu ${if (artifact.isVideo) "video" else "audio"} está en Descargas/KlipNote"
                BillingManager.appendHistory(
                    app,
                    BillingManager.newHistoryEntry(
                        fileName = artifact.savedDisplayName ?: artifact.title,
                        durationSeconds = 0.0,
                        mode = languageMode,
                        diarize = false,
                        appCostUsd = 0.0,
                        balanceBefore = null,
                        balanceAfter = null,
                        transcript = "",
                        audioPath = null,
                        provider = "Descarga",
                        downloadUri = savedMedia?.uri?.toString(),
                        downloadDisplayName = savedMedia?.displayName,
                        downloadIsVideo = savedMedia?.isVideo ?: false
                    )
                )
                finishSuccess(
                    transcript = "",
                    meta = null,
                    savedMedia = savedMedia,
                    sourceLabel = artifact.title,
                    toastMessage = "Guardado en Descargas/KlipNote · ${artifact.savedDisplayName ?: artifact.title}",
                    notifTitle = "✅ Listo",
                    notifBody = msg
                )
                return
            }

            publish("Extrayendo audio…", null)
            val uri = artifact.uri ?: Uri.fromFile(artifact.file)
            var extracted: AudioExtractor.ExtractedAudio? = null
            var sttCompact: java.io.File? = null
            try {
                extracted = withContext(Dispatchers.IO) {
                    AudioExtractor.prepareAudio(app, uri)
                }
                val selection = if (routing == "MANUAL" && !manualProvider.isNullOrBlank()) {
                    val p = runCatching { SttProvider.valueOf(manualProvider) }.getOrNull()
                    if (p != null) RoutingSelection.Manual(p) else RoutingSelection.Auto
                } else {
                    RoutingSelection.Auto
                }
                // TEXT_ONLY ya baja audio liviano; Audio/Video+Texto guardan calidad y
                // comprimen una copia aparte solo para STT (comportamiento 1.5.1).
                val savesPublic = mode == SealDownloader.LinkMode.AUDIO_AND_TEXT ||
                    mode == SealDownloader.LinkMode.VIDEO_AND_TEXT
                val audioForStt = if (savesPublic) {
                    publish("Preparando audio liviano para transcribir…", null)
                    withContext(Dispatchers.IO) {
                        SealDownloader.compressAudioForStt(app, extracted!!.file)
                            .also { sttCompact = it }
                    }
                } else {
                    extracted!!.file
                }
                val mimeForStt = if (sttCompact != null) "audio/mp4" else extracted!!.mimeType
                publish("Transcribiendo…", null)

                var balanceBefore: Double? = null
                val dgKey = ApiKeyStore.getDeepgram(app)
                if (dgKey.isNotBlank()) {
                    balanceBefore = withContext(Dispatchers.IO) {
                        DeepgramBillingClient.fetchOfficialBalance(app, dgKey).balanceUsd
                    }
                }

                val result = withContext(Dispatchers.IO) {
                    SttRouter.transcribe(
                        context = app,
                        audioFile = audioForStt,
                        mimeType = mimeForStt,
                        mode = languageMode,
                        diarize = diarize,
                        deepgramBalanceUsd = balanceBefore,
                        selection = selection,
                        forcedLanguageCode = forcedLang,
                        savedInDownloads = savesPublic,
                        onStatus = { status -> publish(status, null) }
                    )
                }

                if (result.costUsd > 0.0) {
                    BillingManager.addAppSpent(app, result.costUsd)
                }
                BillingManager.recordLocalUsage(app, result.durationSeconds ?: 0.0)

                val persistedAudio = runCatching {
                    val dest = java.io.File(
                        app.filesDir,
                        "recordings/rec_${System.currentTimeMillis()}_${extracted.file.name}"
                    )
                    dest.parentFile?.mkdirs()
                    extracted.file.copyTo(dest, overwrite = true)
                    dest.absolutePath
                }.getOrNull()

                BillingManager.appendHistory(
                    app,
                    BillingManager.newHistoryEntry(
                        fileName = artifact.title,
                        durationSeconds = result.durationSeconds ?: 0.0,
                        mode = languageMode,
                        diarize = diarize,
                        appCostUsd = result.costUsd,
                        balanceBefore = balanceBefore,
                        balanceAfter = null,
                        transcript = result.transcript,
                        audioPath = persistedAudio,
                        provider = result.providerLabel,
                        downloadUri = savedMedia?.uri?.toString(),
                        downloadDisplayName = savedMedia?.displayName,
                        downloadIsVideo = savedMedia?.isVideo ?: false
                    )
                )

                if (result.transcript.isNotBlank()) {
                    copyToClipboard(app, result.transcript)
                }

                val toast = if (savedMedia != null) {
                    "Guion listo · archivo en Descargas/KlipNote"
                } else {
                    "Guion listo y copiado"
                }
                finishSuccess(
                    transcript = result.transcript,
                    meta = result,
                    savedMedia = savedMedia,
                    sourceLabel = artifact.title,
                    toastMessage = toast,
                    notifTitle = "✅ Listo: tu guion está copiado",
                    notifBody = if (savedMedia != null) {
                        "También guardamos el archivo en Descargas/KlipNote"
                    } else {
                        "Ábrelo en KlipNote para ver el texto"
                    }
                )
            } finally {
                AudioExtractor.cleanup(extracted)
                sttCompact?.delete()
            }
        } catch (e: com.yausername.youtubedl_android.YoutubeDL.CanceledException) {
            KlipNoteJobHub.setCancelled()
            dismissProgressForeground()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) {
                KlipNoteJobHub.setCancelled()
                dismissProgressForeground()
                return
            }
            val finalMsg = when {
                t is ManualRouteException -> t.message ?: "Error"
                artifact == null -> SealDownloader.friendlyDownloadError(t.message, rawUrl)
                else -> t.message?.takeIf { it.isNotBlank() } ?: "Error al transcribir"
            }
            KlipNoteJobHub.setFailed(finalMsg)
            dismissProgressForeground()
            KlipNoteNotify.showFinished(
                context = app,
                title = "KlipNote: error",
                body = finalMsg.take(120),
                success = false
            )
            if (KlipNoteJobHub.appInForeground) vibrateShort(app)
        } finally {
            artifact?.let { SealDownloader.cleanupArtifact(it) }
        }
    }

    private fun publish(label: String, progressPercent: Int?) {
        KlipNoteJobHub.setRunning(label)
        KlipNoteNotify.updateProgress(this, label, progressPercent)
        val notif = KlipNoteNotify.progressNotification(this, label, progressPercent)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(
                    this,
                    KlipNoteNotify.NOTIF_PROGRESS_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(KlipNoteNotify.NOTIF_PROGRESS_ID, notif)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo actualizar foreground: ${t.message}")
        }
    }

    private fun finishSuccess(
        transcript: String,
        meta: TranscriptionResult?,
        savedMedia: SavedMediaInfo?,
        sourceLabel: String,
        toastMessage: String,
        notifTitle: String,
        notifBody: String
    ) {
        KlipNoteJobHub.setSuccess(
            transcript = transcript,
            meta = meta,
            savedMedia = savedMedia,
            sourceLabel = sourceLabel,
            toastMessage = toastMessage
        )
        // Quitar FGS + notificación de progreso ANTES del aviso "Listo".
        dismissProgressForeground()
        KlipNoteNotify.showFinished(
            context = this,
            title = notifTitle,
            body = notifBody,
            success = true
        )
        if (KlipNoteJobHub.appInForeground) vibrateShort(this)
    }

    /** Nunca deja pegada la notificación de progreso (éxito, error o cancelar). */
    private fun dismissProgressForeground() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(/* removeNotification = */ true)
            }
        }
        KlipNoteNotify.clearProgress(this)
    }

    private fun stopSelfSafely() {
        dismissProgressForeground()
        stopSelf()
    }

    override fun onDestroy() {
        workJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KlipNoteWork"
        const val ACTION_START_LINK = "com.blackeye.klipnote.action.START_LINK"
        const val ACTION_CANCEL = "com.blackeye.klipnote.action.CANCEL"
        const val ACTION_DISMISS_PROGRESS = "com.blackeye.klipnote.action.DISMISS_PROGRESS"
        const val EXTRA_URL = "url"
        const val EXTRA_MODE = "mode"
        const val EXTRA_KIND = "kind"
        const val EXTRA_LANG_MODE = "lang_mode"
        const val EXTRA_FORCED_LANG = "forced_lang"
        const val EXTRA_DIARIZE = "diarize"
        const val EXTRA_ROUTING = "routing"
        const val EXTRA_MANUAL_PROVIDER = "manual_provider"

        fun startLinkJob(
            context: Context,
            url: String,
            mode: SealDownloader.LinkMode,
            kind: SealDownloader.DownloadKind,
            languageMode: LanguageMode,
            forcedLanguage: String?,
            diarize: Boolean,
            routingSelection: RoutingSelection
        ) {
            val intent = Intent(context, KlipNoteWorkService::class.java).apply {
                action = ACTION_START_LINK
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_KIND, kind.name)
                putExtra(EXTRA_LANG_MODE, languageMode.name)
                putExtra(EXTRA_FORCED_LANG, forcedLanguage)
                putExtra(EXTRA_DIARIZE, diarize)
                when (routingSelection) {
                    RoutingSelection.Auto -> putExtra(EXTRA_ROUTING, "AUTO")
                    is RoutingSelection.Manual -> {
                        putExtra(EXTRA_ROUTING, "MANUAL")
                        putExtra(EXTRA_MANUAL_PROVIDER, routingSelection.provider.name)
                    }
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, KlipNoteWorkService::class.java).setAction(ACTION_CANCEL)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Quita una notificación de progreso huérfana (p. ej. DETACH de versiones anteriores). */
        fun dismissOrphanProgress(context: Context) {
            if (KlipNoteJobHub.isBusy()) return
            val intent = Intent(context, KlipNoteWorkService::class.java)
                .setAction(ACTION_DISMISS_PROGRESS)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
