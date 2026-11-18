package com.blackeye.klipnote

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

object DeepgramClient {

    private const val BASE = "https://api.deepgram.com/v1/listen"
    private const val CHUNK = 8 * 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    fun transcribe(
        apiKey: String,
        file: File,
        mimeType: String,
        mode: LanguageMode,
        diarize: Boolean,
        model: String = "nova-3"
    ): TranscriptionResult {
        require(apiKey.isNotBlank()) { "API Key vacía" }

        val urlBuilder = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("model", model)
            .addQueryParameter("smart_format", "true")

        when (mode) {
            LanguageMode.AUTO -> urlBuilder.addQueryParameter("detect_language", "true")
            LanguageMode.MULTI -> urlBuilder.addQueryParameter("language", "multi")
        }

        if (diarize) {
            urlBuilder.addQueryParameter("diarize", "true")
            urlBuilder.addQueryParameter("utterances", "true")
        }

        val contentType = resolveContentType(file, mimeType)
        val body = streamingFileBody(file, contentType)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("Authorization", "Token $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SttProviderException.fromHttp(
                    provider = SttProvider.DEEPGRAM,
                    code = response.code,
                    body = runCatching {
                        JSONObject(raw).optString("err_msg")
                            .ifBlank { JSONObject(raw).optString("message") }
                            .ifBlank { raw }
                    }.getOrDefault(raw),
                    retryAfter = response.header("Retry-After")?.toLongOrNull()
                )
            }
            return parseResponse(raw, diarize)
        }
    }

    private fun resolveContentType(file: File, mimeType: String): String {
        val lower = mimeType.lowercase()
        val name = file.name.lowercase()
        return when {
            name.endsWith(".m4a") || lower.contains("mp4") || lower.contains("m4a") ||
                lower.contains("aac") -> "audio/mp4"
            lower.startsWith("audio/") || lower.startsWith("video/") -> lower
            else -> "application/octet-stream"
        }
    }

    private fun streamingFileBody(file: File, contentType: String): RequestBody {
        val mediaType = contentType.toMediaTypeOrNull()
        return object : RequestBody() {
            override fun contentType() = mediaType
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
    }

    private fun parseResponse(raw: String, diarize: Boolean): TranscriptionResult {
        val root = JSONObject(raw)
        val results = root.optJSONObject("results")
            ?: return TranscriptionResult("", null, null, 0)

        val metadata = root.optJSONObject("metadata")
        val duration = metadata?.optDouble("duration", Double.NaN)
            ?.takeIf { !it.isNaN() }

        var language: String? = null
        val channels = results.optJSONArray("channels")
        if (channels != null && channels.length() > 0) {
            val alt = channels.getJSONObject(0)
                .optJSONArray("alternatives")
                ?.optJSONObject(0)
            language = alt?.optString("detected_language")
                ?.takeIf { it.isNotBlank() }
                ?: channels.getJSONObject(0).optString("detected_language")
                    .takeIf { it.isNotBlank() }
        }

        val transcript = if (diarize) {
            formatUtterances(results)
        } else {
            extractPlainTranscript(results)
        }

        val words = transcript.split(Regex("\\s+")).count { it.isNotBlank() }
        val cost = BillingManager.actualCost(
            durationSeconds = duration ?: 0.0,
            mode = LanguageMode.AUTO,
            diarizeUsed = diarize
        )
        return TranscriptionResult(
            transcript = transcript.trim(),
            durationSeconds = duration,
            detectedLanguage = language,
            wordCount = words,
            provider = SttProvider.DEEPGRAM,
            providerLabel = "Deepgram Nova-3",
            costUsd = cost
        )
    }

    private fun formatUtterances(results: JSONObject): String {
        val utterances = results.optJSONArray("utterances")
        if (utterances == null || utterances.length() == 0) {
            return extractPlainTranscript(results)
        }
        val sb = StringBuilder()
        for (i in 0 until utterances.length()) {
            val u = utterances.getJSONObject(i)
            val speaker = u.optInt("speaker", 0) + 1
            val text = u.optString("transcript").trim()
            if (text.isEmpty()) continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("[Voz ").append(speaker).append("]: ").append(text)
        }
        return sb.toString().ifBlank { extractPlainTranscript(results) }
    }

    private fun extractPlainTranscript(results: JSONObject): String {
        val channels = results.optJSONArray("channels") ?: return ""
        if (channels.length() == 0) return ""
        val alt = channels.getJSONObject(0)
            .optJSONArray("alternatives")
            ?.optJSONObject(0)
            ?: return ""

        val paragraphs = alt.optJSONObject("paragraphs")
        val fromParagraphs = paragraphs?.optString("transcript")?.takeIf { it.isNotBlank() }
        if (fromParagraphs != null) return fromParagraphs

        return alt.optString("transcript").orEmpty()
    }
}
