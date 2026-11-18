package com.blackeye.klipnote

import android.content.Context
import android.util.Base64
import androidx.annotation.Keep
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Cliente Inworld STT (docs vivas docs.inworld.ai):
 * - POST https://api.inworld.ai/stt/v1/transcribe (multipart sync)
 * - modelId: inworld/inworld-stt-1
 * - Auth: Authorization: Basic <base64(key_id:secret)> (Portal Copy key as-is)
 * - Límite sync: ~16 MiB request (config + file)
 */
object InworldClient {

    private const val URL = "https://api.inworld.ai/stt/v1/transcribe"
    const val MODEL_ID = "inworld/inworld-stt-1"
    /** Margen bajo 16 MiB oficiales (config + audio juntos). */
    const val MAX_SYNC_BYTES = 15L * 1024L * 1024L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    @Keep
    data class InworldUsage(
        val transcribedAudioMs: Long?
    )

    fun transcribe(
        context: Context,
        apiKey: String,
        file: File,
        mimeType: String,
        languageHint: String? = null
    ): TranscriptionResult {
        require(apiKey.isNotBlank()) { "API Key Inworld vacía" }
        if (file.length() > MAX_SYNC_BYTES) {
            throw SttProviderException(
                provider = SttProvider.INWORLD,
                httpCode = 413,
                kind = SttFailureKind.SIZE_SKIP,
                message = "Archivo >15MB (límite sync Inworld ~16MiB) → Saltando a Nivel 3"
            )
        }

        val credential = normalizeCredential(apiKey)
        val encoding = resolveEncoding(file, mimeType)
        val config = JSONObject().apply {
            put("modelId", MODEL_ID)
            put("audioEncoding", encoding)
            if (!languageHint.isNullOrBlank()) put("language", languageHint)
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            // Docs: transcribeConfig ANTES de file
            .addPart(
                MultipartBody.Part.createFormData(
                    "transcribeConfig",
                    null,
                    config.toString().toRequestBody("application/json".toMediaType())
                )
            )
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody(resolveMime(file, mimeType).toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(URL)
            .addHeader("Authorization", "Basic $credential")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 413) {
                    throw SttProviderException(
                        provider = SttProvider.INWORLD,
                        httpCode = 413,
                        kind = SttFailureKind.SIZE_SKIP,
                        message = "HTTP 413 Payload Too Large → Saltando a Nivel 3"
                    )
                }
                throw SttProviderException.fromHttp(
                    provider = SttProvider.INWORLD,
                    code = response.code,
                    body = raw,
                    retryAfter = response.header("Retry-After")?.toLongOrNull()
                )
            }
            val parsed = parseResponse(raw)
            val duration = parsed.durationSeconds ?: 0.0
            if (duration > 0.0) {
                BillingManager.consumeInworldSeconds(context, duration)
            }
            return parsed
        }
    }

    fun healthCheck(context: Context, apiKey: String, probeFile: File): ProviderStatus {
        if (apiKey.isBlank()) {
            return ProviderStatus(
                provider = SttProvider.INWORLD,
                health = ProviderHealth.NO_KEY,
                label = "Inworld Free",
                detail = "SIN KEY"
            )
        }
        val remaining = BillingManager.getInworldRemainingSeconds(context)
        return try {
            transcribe(
                context = context,
                apiKey = apiKey,
                file = probeFile,
                mimeType = "audio/wav",
                languageHint = "en"
            )
            ProviderStatus(
                provider = SttProvider.INWORLD,
                health = if (remaining > 0) ProviderHealth.ACTIVE_FREE else ProviderHealth.PAUSED_LIMIT,
                label = "Inworld Free",
                detail = if (remaining > 0) {
                    "ACTIVO · ${String.format("%.0fm", remaining / 60.0)} bolsa"
                } else {
                    "BOLSA AGOTADA"
                },
                remainingSeconds = remaining
            )
        } catch (e: SttProviderException) {
            val health = when (e.kind) {
                SttFailureKind.PROMO_ENDED -> ProviderHealth.OFFER_ENDED
                SttFailureKind.COOLDOWN -> ProviderHealth.PAUSED_LIMIT
                SttFailureKind.UNAUTHORIZED -> ProviderHealth.OFFER_ENDED
                SttFailureKind.SIZE_SKIP -> ProviderHealth.ACTIVE_FREE
                else -> ProviderHealth.UNKNOWN
            }
            ProviderStatus(
                provider = SttProvider.INWORLD,
                health = health,
                label = "Inworld Free",
                detail = e.message?.take(160) ?: e.kind.name,
                remainingSeconds = remaining
            )
        } catch (e: Exception) {
            ProviderStatus(
                provider = SttProvider.INWORLD,
                health = ProviderHealth.UNKNOWN,
                label = "Inworld Free",
                detail = e.message?.take(120) ?: "Error",
                remainingSeconds = remaining
            )
        }
    }

    /**
     * Portal entrega Base64 listo. Limpia prefijos Basic/Bearer si el usuario los pega.
     * No re-codifica en Base64.
     */
    fun normalizeCredential(raw: String): String {
        var key = raw.trim().trim('"').trim('\'')
        if (key.startsWith("Basic ", ignoreCase = true)) {
            key = key.substring(6).trim()
        } else if (key.startsWith("Bearer ", ignoreCase = true)) {
            key = key.substring(7).trim()
        }
        // Si el usuario pegó key_id:secret en texto plano, codificar una vez.
        if (key.contains(':') && !looksLikeBase64(key)) {
            key = Base64.encodeToString(key.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        return key
    }

    private fun looksLikeBase64(value: String): Boolean {
        if (value.length < 8 || value.length % 4 != 0) return false
        return value.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
    }

    private fun parseResponse(raw: String): TranscriptionResult {
        val root = JSONObject(raw)
        val transcription = root.optJSONObject("transcription")
        val text = transcription?.optString("transcript").orEmpty().trim()
        val usage = root.optJSONObject("usage")
        val ms = when {
            usage != null && usage.has("transcribedAudioMs") && !usage.isNull("transcribedAudioMs") ->
                usage.optLong("transcribedAudioMs")
            else -> -1L
        }
        val duration = if (ms > 0) ms / 1000.0 else null
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        return TranscriptionResult(
            transcript = text,
            durationSeconds = duration,
            detectedLanguage = null,
            wordCount = words,
            provider = SttProvider.INWORLD,
            providerLabel = "Inworld STT",
            costUsd = 0.0
        )
    }

    private fun resolveEncoding(file: File, mimeType: String): String {
        val name = file.name.lowercase()
        val mime = mimeType.lowercase()
        return when {
            name.endsWith(".wav") || mime.contains("wav") || mime.contains("linear") -> "LINEAR16"
            name.endsWith(".mp3") || mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
            name.endsWith(".flac") || mime.contains("flac") -> "FLAC"
            name.endsWith(".ogg") || name.endsWith(".opus") || mime.contains("ogg") ||
                mime.contains("opus") -> "OGG_OPUS"
            name.endsWith(".webm") || mime.contains("webm") -> "WEBM"
            name.endsWith(".m4a") || name.endsWith(".aac") ||
                (mime.contains("mp4") && !mime.contains("video")) -> "AUTO_DETECT"
            name.endsWith(".mp4") || name.endsWith(".mov") || mime.startsWith("video/") -> "MP4"
            else -> "AUTO_DETECT"
        }
    }

    private fun resolveMime(file: File, mimeType: String): String {
        val lower = mimeType.lowercase()
        val name = file.name.lowercase()
        return when {
            name.endsWith(".wav") || lower.contains("wav") -> "audio/wav"
            name.endsWith(".mp3") || lower.contains("mpeg") -> "audio/mpeg"
            name.endsWith(".m4a") || lower.contains("m4a") || lower.contains("mp4") -> "audio/mp4"
            lower.startsWith("audio/") || lower.startsWith("video/") -> lower
            else -> "application/octet-stream"
        }
    }
}
