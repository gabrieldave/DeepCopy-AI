package com.blackeye.klipnote

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

object AssemblyAiClient {

    private const val UPLOAD_URL = "https://api.assemblyai.com/v2/upload"
    private const val TRANSCRIPT_URL = "https://api.assemblyai.com/v2/transcript"
    private const val CHUNK = 8 * 1024
    private const val POLL_MS = 1500L

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
        file: File
    ): TranscriptionResult {
        require(apiKey.isNotBlank()) { "API Key AssemblyAI vacía" }

        val uploadUrl = upload(apiKey, file)
        val id = createTranscript(apiKey, uploadUrl)
        val result = pollUntilDone(apiKey, id)

        val duration = result.durationSeconds ?: 0.0
        BillingManager.consumeAssemblySeconds(context, duration)

        return result.copy(
            provider = SttProvider.ASSEMBLY_AI,
            providerLabel = "AssemblyAI",
            costUsd = 0.0
        )
    }

    fun healthCheck(context: Context, apiKey: String): ProviderStatus {
        if (apiKey.isBlank()) {
            return ProviderStatus(
                provider = SttProvider.ASSEMBLY_AI,
                health = ProviderHealth.NO_KEY,
                label = "AssemblyAI",
                detail = "Sin key"
            )
        }
        val remaining = BillingManager.getAssemblyRemainingSeconds(context)
        val request = Request.Builder()
            .url(TRANSCRIPT_URL)
            .addHeader("Authorization", apiKey)
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 404, 405 -> ProviderStatus(
                        provider = SttProvider.ASSEMBLY_AI,
                        health = if (remaining > 0) ProviderHealth.ACTIVE_FREE else ProviderHealth.PAUSED_LIMIT,
                        label = "AssemblyAI",
                        detail = if (remaining > 0) {
                            "ACTIVO (Gratis) · ${String.format("%.1fh", remaining / 3600.0)} bolsa"
                        } else {
                            "EN PAUSA / LÍMITE"
                        },
                        remainingSeconds = remaining
                    )
                    401 -> ProviderStatus(
                        provider = SttProvider.ASSEMBLY_AI,
                        health = ProviderHealth.OFFER_ENDED,
                        label = "AssemblyAI",
                        detail = "KEY INVÁLIDA (HTTP 401)"
                    )
                    402, 403 -> ProviderStatus(
                        provider = SttProvider.ASSEMBLY_AI,
                        health = ProviderHealth.OFFER_ENDED,
                        label = "AssemblyAI",
                        detail = "OFERTA TERMINADA (HTTP ${response.code})"
                    )
                    429 -> ProviderStatus(
                        provider = SttProvider.ASSEMBLY_AI,
                        health = ProviderHealth.PAUSED_LIMIT,
                        label = "AssemblyAI",
                        detail = "EN PAUSA / LÍMITE DIARIO"
                    )
                    else -> ProviderStatus(
                        provider = SttProvider.ASSEMBLY_AI,
                        health = ProviderHealth.UNKNOWN,
                        label = "AssemblyAI",
                        detail = "HTTP ${response.code}",
                        remainingSeconds = remaining
                    )
                }
            }
        } catch (e: Exception) {
            ProviderStatus(
                provider = SttProvider.ASSEMBLY_AI,
                health = ProviderHealth.UNKNOWN,
                label = "AssemblyAI",
                detail = e.message?.take(120) ?: "Error",
                remainingSeconds = remaining
            )
        }
    }

    private fun upload(apiKey: String, file: File): String {
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength(): Long = file.length()
            override fun writeTo(sink: BufferedSink) {
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(CHUNK)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }
        val request = Request.Builder()
            .url(UPLOAD_URL)
            .addHeader("Authorization", apiKey)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SttProviderException.fromHttp(
                    SttProvider.ASSEMBLY_AI,
                    response.code,
                    raw,
                    response.header("Retry-After")?.toLongOrNull()
                )
            }
            val url = JSONObject(raw).optString("upload_url")
            if (url.isBlank()) error("AssemblyAI upload sin upload_url")
            return url
        }
    }

    private fun createTranscript(apiKey: String, uploadUrl: String): String {
        val payload = JSONObject()
            .put("audio_url", uploadUrl)
            .put(
                "speech_models",
                JSONArray().put("universal-3-pro").put("universal-2")
            )
            .put("language_detection", true)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(TRANSCRIPT_URL)
            .addHeader("Authorization", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SttProviderException.fromHttp(
                    SttProvider.ASSEMBLY_AI,
                    response.code,
                    raw,
                    response.header("Retry-After")?.toLongOrNull()
                )
            }
            val id = JSONObject(raw).optString("id")
            if (id.isBlank()) error("AssemblyAI transcript sin id")
            return id
        }
    }

    private fun pollUntilDone(apiKey: String, id: String): TranscriptionResult {
        while (true) {
            val request = Request.Builder()
                .url("$TRANSCRIPT_URL/$id")
                .addHeader("Authorization", apiKey)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SttProviderException.fromHttp(
                        SttProvider.ASSEMBLY_AI,
                        response.code,
                        raw,
                        response.header("Retry-After")?.toLongOrNull()
                    )
                }
                val root = JSONObject(raw)
                when (root.optString("status")) {
                    "completed" -> {
                        val text = root.optString("text").orEmpty().trim()
                        val duration = when {
                            root.has("audio_duration") && !root.isNull("audio_duration") ->
                                root.optDouble("audio_duration")
                            else -> null
                        }
                        val language = root.optString("language_code").takeIf { it.isNotBlank() }
                        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
                        return TranscriptionResult(
                            transcript = text,
                            durationSeconds = duration,
                            detectedLanguage = language,
                            wordCount = words,
                            provider = SttProvider.ASSEMBLY_AI,
                            providerLabel = "AssemblyAI"
                        )
                    }
                    "error" -> {
                        throw SttProviderException(
                            provider = SttProvider.ASSEMBLY_AI,
                            httpCode = null,
                            kind = SttFailureKind.OTHER,
                            message = root.optString("error").ifBlank { "AssemblyAI error" }
                        )
                    }
                    else -> Thread.sleep(POLL_MS)
                }
            }
        }
    }
}
