package com.blackeye.klipnote

import android.content.Context
import androidx.annotation.Keep
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object GroqClient {

    private const val URL = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val MAX_FREE_BYTES = 24L * 1024L * 1024L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    @Keep
    data class GroqQuotaHeaders(
        val remainingAudioSeconds: Double?,
        val limitAudioSeconds: Double?,
        val resetAudioSeconds: Double?
    )

    fun transcribe(
        context: Context,
        apiKey: String,
        file: File,
        mimeType: String,
        model: String = "whisper-large-v3-turbo",
        enforceFreeSizeLimit: Boolean = true
    ): TranscriptionResult {
        require(apiKey.isNotBlank()) { "API Key Groq vacía" }
        if (enforceFreeSizeLimit && file.length() > MAX_FREE_BYTES) {
            throw SttProviderException(
                provider = SttProvider.GROQ_FREE,
                httpCode = null,
                kind = SttFailureKind.SIZE_SKIP,
                message = "Archivo >24MB → Saltando a Nivel 2"
            )
        }

        val media = resolveMime(file, mimeType).toMediaTypeOrNull()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(media))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .build()

        val request = Request.Builder()
            .url(URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val quota = parseQuotaHeaders(response)
            applyQuota(context, quota, raw)

            if (!response.isSuccessful) {
                val retryAfter = response.header("Retry-After")?.toLongOrNull()
                throw SttProviderException.fromHttp(
                    provider = if (enforceFreeSizeLimit) SttProvider.GROQ_FREE else SttProvider.PAID_FALLBACK,
                    code = response.code,
                    body = raw,
                    retryAfter = retryAfter
                )
            }
            return parseVerboseJson(raw, if (enforceFreeSizeLimit) SttProvider.GROQ_FREE else SttProvider.PAID_FALLBACK)
        }
    }

    /** Micro-check de oferta: mismo endpoint, captura 402/401/429 y headers de cuota. */
    fun healthCheck(context: Context, apiKey: String, probeFile: File): ProviderStatus {
        if (apiKey.isBlank()) {
            return ProviderStatus(
                provider = SttProvider.GROQ_FREE,
                health = ProviderHealth.NO_KEY,
                label = "Groq Free",
                detail = "Sin key"
            )
        }
        return try {
            val result = transcribe(
                context = context,
                apiKey = apiKey,
                file = probeFile,
                mimeType = "audio/wav",
                enforceFreeSizeLimit = true
            )
            val remaining = BillingManager.getGroqRemainingSeconds(context)
            // result confirma que el tier gratis aceptó el micro-WAV
            result.transcript
            ProviderStatus(
                provider = SttProvider.GROQ_FREE,
                health = ProviderHealth.ACTIVE_FREE,
                label = "Groq Free",
                detail = "ACTIVO (Gratis) · ${formatHours(remaining)} hoy",
                remainingSeconds = remaining
            )
        } catch (e: SttProviderException) {
            mapFailure(e, context)
        } catch (e: Exception) {
            ProviderStatus(
                provider = SttProvider.GROQ_FREE,
                health = ProviderHealth.UNKNOWN,
                label = "Groq Free",
                detail = e.message?.take(120) ?: "Error"
            )
        }
    }

    private fun mapFailure(e: SttProviderException, context: Context): ProviderStatus {
        val health = when (e.kind) {
            SttFailureKind.PROMO_ENDED -> ProviderHealth.OFFER_ENDED
            SttFailureKind.COOLDOWN -> ProviderHealth.PAUSED_LIMIT
            SttFailureKind.UNAUTHORIZED -> ProviderHealth.OFFER_ENDED
            SttFailureKind.SIZE_SKIP -> ProviderHealth.ACTIVE_FREE
            else -> ProviderHealth.UNKNOWN
        }
        return ProviderStatus(
            provider = SttProvider.GROQ_FREE,
            health = health,
            label = "Groq Free",
            detail = e.message?.take(160) ?: e.kind.name,
            remainingSeconds = BillingManager.getGroqRemainingSeconds(context)
        )
    }

    private fun applyQuota(context: Context, quota: GroqQuotaHeaders, raw: String) {
        if (quota.remainingAudioSeconds != null) {
            BillingManager.setGroqRemainingFromHeaders(
                context = context,
                remaining = quota.remainingAudioSeconds,
                limit = quota.limitAudioSeconds,
                resetSeconds = quota.resetAudioSeconds
            )
            return
        }
        val duration = runCatching {
            JSONObject(raw).optDouble("duration", Double.NaN)
        }.getOrNull()?.takeIf { !it.isNaN() } ?: return
        BillingManager.consumeGroqSeconds(context, duration)
    }

    private fun parseQuotaHeaders(response: okhttp3.Response): GroqQuotaHeaders =
        GroqQuotaHeaders(
            remainingAudioSeconds = response.header("x-ratelimit-remaining-audio-seconds")
                ?.toDoubleOrNull(),
            limitAudioSeconds = response.header("x-ratelimit-limit-audio-seconds")
                ?.toDoubleOrNull(),
            resetAudioSeconds = response.header("x-ratelimit-reset-audio-seconds")
                ?.toDoubleOrNull()
        )

    private fun parseVerboseJson(raw: String, provider: SttProvider): TranscriptionResult {
        val root = JSONObject(raw)
        val text = root.optString("text").orEmpty().trim()
        val duration = root.optDouble("duration", Double.NaN).takeIf { !it.isNaN() }
        val language = root.optString("language").takeIf { it.isNotBlank() }
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        return TranscriptionResult(
            transcript = text,
            durationSeconds = duration,
            detectedLanguage = language,
            wordCount = words,
            provider = provider,
            providerLabel = if (provider == SttProvider.GROQ_FREE) "Groq Free" else "Groq Paid",
            costUsd = if (provider == SttProvider.GROQ_FREE) 0.0 else (duration ?: 0.0) / 3600.0 * 0.04
        )
    }

    private fun resolveMime(file: File, mimeType: String): String {
        val lower = mimeType.lowercase()
        val name = file.name.lowercase()
        return when {
            name.endsWith(".wav") || lower.contains("wav") -> "audio/wav"
            name.endsWith(".mp3") || lower.contains("mpeg") || lower.contains("mp3") -> "audio/mpeg"
            name.endsWith(".m4a") || lower.contains("mp4") || lower.contains("m4a") -> "audio/mp4"
            lower.startsWith("audio/") -> lower
            else -> "application/octet-stream"
        }
    }

    private fun formatHours(seconds: Double): String =
        String.format("%.1fh", seconds / 3600.0)
}
