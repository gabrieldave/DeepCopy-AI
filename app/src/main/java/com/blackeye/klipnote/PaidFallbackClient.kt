package com.blackeye.klipnote

import android.content.Context
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object PaidFallbackClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    fun transcribe(
        context: Context,
        apiKey: String,
        endpoint: String,
        engine: PaidFallbackEngine,
        file: File,
        mimeType: String,
        groqModel: String = "whisper-large-v3-turbo",
        languageCode: String? = null
    ): TranscriptionResult {
        require(apiKey.isNotBlank()) { "API Key de respaldo pagado vacía" }
        return when (engine) {
            PaidFallbackEngine.GROQ_PAID -> GroqClient.transcribe(
                context = context,
                apiKey = apiKey,
                file = file,
                mimeType = mimeType,
                model = groqModel,
                enforceFreeSizeLimit = false,
                languageCode = languageCode
            ).let { base ->
                val dur = base.durationSeconds ?: 0.0
                base.copy(
                    provider = SttProvider.PAID_FALLBACK,
                    providerLabel = "Groq Paid",
                    costUsd = dur / 3600.0 * 0.04
                )
            }

            PaidFallbackEngine.MICROSOFT_MAI -> microsoftTranscribe(
                apiKey = apiKey,
                endpoint = endpoint,
                file = file,
                mimeType = mimeType,
                languageCode = languageCode
            )
        }
    }

    private fun microsoftTranscribe(
        apiKey: String,
        endpoint: String,
        file: File,
        mimeType: String,
        languageCode: String?
    ): TranscriptionResult {
        val base = endpoint.trim().trimEnd('/')
        require(base.isNotBlank()) { "Endpoint Microsoft/MAI vacío" }

        // Fast Transcription API o endpoint OpenAI-compatible configurable.
        return if (base.contains("openai", ignoreCase = true) ||
            base.contains("/audio/transcriptions", ignoreCase = true)
        ) {
            openaiCompatible(apiKey, base, file, mimeType, languageCode)
        } else {
            azureFastTranscription(apiKey, base, file, mimeType, languageCode)
        }
    }

    private fun azureLocales(languageCode: String?): JSONArray {
        val forced = languageCode?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("multi", ignoreCase = true)
        }?.lowercase()
        return when (forced) {
            null -> JSONArray().put("es-MX").put("en-US")
            "es" -> JSONArray().put("es-MX").put("es-ES")
            "en" -> JSONArray().put("en-US").put("en-GB")
            "pt" -> JSONArray().put("pt-BR").put("pt-PT")
            "fr" -> JSONArray().put("fr-FR")
            "de" -> JSONArray().put("de-DE")
            "it" -> JSONArray().put("it-IT")
            "ja" -> JSONArray().put("ja-JP")
            "ko" -> JSONArray().put("ko-KR")
            "zh" -> JSONArray().put("zh-CN")
            else -> JSONArray().put(forced)
        }
    }

    private fun azureFastTranscription(
        apiKey: String,
        endpoint: String,
        file: File,
        mimeType: String,
        languageCode: String?
    ): TranscriptionResult {
        val url =
            "$endpoint/speechtotext/transcriptions:transcribe?api-version=2024-11-15"
        val definition = JSONObject()
            .put("locales", azureLocales(languageCode))
            .toString()
        val media = mimeType.toMediaTypeOrNull()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("definition", definition)
            .addFormDataPart("audio", file.name, file.asRequestBody(media))
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", apiKey)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SttProviderException.fromHttp(
                    SttProvider.PAID_FALLBACK,
                    response.code,
                    raw,
                    response.header("Retry-After")?.toLongOrNull()
                )
            }
            return parseAzure(raw, languageCode)
        }
    }

    private fun openaiCompatible(
        apiKey: String,
        endpoint: String,
        file: File,
        mimeType: String,
        languageCode: String?
    ): TranscriptionResult {
        val url = if (endpoint.endsWith("/audio/transcriptions")) {
            endpoint
        } else {
            "$endpoint/audio/transcriptions"
        }
        val media = mimeType.toMediaTypeOrNull()
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(media))
            .addFormDataPart("model", "mai-transcribe-2")
            .addFormDataPart("response_format", "verbose_json")
        val forced = languageCode?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("multi", ignoreCase = true)
        }
        if (forced != null) {
            bodyBuilder.addFormDataPart("language", forced.lowercase())
        }
        val body = bodyBuilder.build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("api-key", apiKey)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SttProviderException.fromHttp(
                    SttProvider.PAID_FALLBACK,
                    response.code,
                    raw,
                    response.header("Retry-After")?.toLongOrNull()
                )
            }
            val root = JSONObject(raw)
            val text = root.optString("text").orEmpty().trim()
            val duration = root.optDouble("duration", Double.NaN).takeIf { !it.isNaN() }
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            val detected = root.optString("language").takeIf { it.isNotBlank() }
                ?: forced
            return TranscriptionResult(
                transcript = text,
                durationSeconds = duration,
                detectedLanguage = detected,
                wordCount = words,
                provider = SttProvider.PAID_FALLBACK,
                providerLabel = "MAI-Transcribe-2",
                costUsd = (duration ?: 0.0) / 3600.0 * 0.10
            )
        }
    }

    private fun parseAzure(raw: String, languageCode: String?): TranscriptionResult {
        val root = JSONObject(raw)
        // Fast transcription suele devolver combinedPhrases / phrases / durationMilliseconds
        val combined = root.optJSONArray("combinedPhrases")
        val text = when {
            combined != null && combined.length() > 0 -> {
                buildString {
                    for (i in 0 until combined.length()) {
                        val t = combined.getJSONObject(i).optString("text").trim()
                        if (t.isNotEmpty()) {
                            if (isNotEmpty()) append(' ')
                            append(t)
                        }
                    }
                }
            }
            else -> root.optString("text").orEmpty().trim()
        }
        val durationMs = root.optLong("durationMilliseconds", -1L)
        val duration = if (durationMs > 0) durationMs / 1000.0 else null
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val forced = languageCode?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("multi", ignoreCase = true)
        }
        return TranscriptionResult(
            transcript = text,
            durationSeconds = duration,
            detectedLanguage = forced,
            wordCount = words,
            provider = SttProvider.PAID_FALLBACK,
            providerLabel = "Microsoft MAI",
            costUsd = (duration ?: 0.0) / 3600.0 * 0.10
        )
    }
}
