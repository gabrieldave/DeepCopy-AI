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
        enforceFreeSizeLimit: Boolean = true,
        languageCode: String? = null
    ): TranscriptionResult {
        require(apiKey.isNotBlank()) { context.getString(R.string.error_groq_key_empty) }
        if (enforceFreeSizeLimit && file.length() > MAX_FREE_BYTES) {
            throw SttProviderException(
                provider = SttProvider.GROQ_FREE,
                httpCode = null,
                kind = SttFailureKind.SIZE_SKIP,
                message = context.getString(R.string.error_groq_size_skip)
            )
        }

        val media = resolveMime(file, mimeType).toMediaTypeOrNull()
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(media))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
        // Whisper: omit language for auto-detect; ignore "multi"
        val forced = languageCode?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("multi", ignoreCase = true)
        }
        if (forced != null) {
            bodyBuilder.addFormDataPart("language", forced.lowercase())
        }
        val body = bodyBuilder.build()

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
                    context = context,
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
                label = SttProvider.GROQ_FREE.displayName(context),
                detail = HealthUi.noKey(context)
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
            val limit = BillingManager.getGroqLimitSeconds(context)
            val used = BillingManager.getGroqUsedTodaySeconds(context)
            val live = BillingManager.hasGroqLiveQuota(context)
            // result confirma que el tier gratis aceptó el micro-WAV
            result.transcript
            ProviderStatus(
                provider = SttProvider.GROQ_FREE,
                health = ProviderHealth.ACTIVE_FREE,
                label = SttProvider.GROQ_FREE.displayName(context),
                detail = HealthUi.active(
                    context,
                    BillingManager.formatGroqQuotaDetail(
                        context = context,
                        remainingSeconds = remaining,
                        limitSeconds = limit,
                        usedTodaySeconds = used,
                        hasLiveQuota = live
                    )
                ),
                remainingSeconds = remaining
            )
        } catch (e: SttProviderException) {
            mapFailure(e, context)
        } catch (e: Exception) {
            ProviderStatus(
                provider = SttProvider.GROQ_FREE,
                health = ProviderHealth.UNKNOWN,
                label = SttProvider.GROQ_FREE.displayName(context),
                detail = e.message?.take(120) ?: HealthUi.genericError(context)
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
            label = SttProvider.GROQ_FREE.displayName(context),
            detail = e.message?.take(160) ?: e.kind.name,
            remainingSeconds = BillingManager.getGroqRemainingSeconds(context)
        )
    }

    private fun applyQuota(context: Context, quota: GroqQuotaHeaders, raw: String) {
        val duration = runCatching {
            JSONObject(raw).optDouble("duration", Double.NaN)
        }.getOrNull()?.takeIf { !it.isNaN() && it > 0.0 }

        if (quota.remainingAudioSeconds != null) {
            // Cabeceras reales de ventana (~7200s): remaining + limit juntos.
            BillingManager.setGroqRemainingFromHeaders(
                context = context,
                remaining = quota.remainingAudioSeconds,
                limit = quota.limitAudioSeconds,
                resetSeconds = quota.resetAudioSeconds
            )
            if (duration != null) {
                BillingManager.addGroqUsedSeconds(context, duration)
            }
            return
        }
        if (duration != null) {
            BillingManager.consumeGroqSeconds(context, duration)
        }
    }

    private fun parseQuotaHeaders(response: okhttp3.Response): GroqQuotaHeaders =
        GroqQuotaHeaders(
            remainingAudioSeconds = response.header("x-ratelimit-remaining-audio-seconds")
                ?.toDoubleOrNull(),
            limitAudioSeconds = response.header("x-ratelimit-limit-audio-seconds")
                ?.toDoubleOrNull(),
            resetAudioSeconds = response.header("x-ratelimit-reset-audio-seconds")
                ?.toDoubleOrNull()
                ?: response.header("x-ratelimit-reset-requests")?.toDoubleOrNull()
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

}
