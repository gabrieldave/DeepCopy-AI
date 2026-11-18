package com.blackeye.klipnote

import android.content.Context
import androidx.annotation.Keep
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Override remoto opcional del orden/modelos STT.
 * Fail-safe silencioso: timeout 3s; si falla, usa config local.
 */
object RemoteRouterConfig {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private val cached = AtomicReference<Snapshot?>(null)

    @Keep
    data class Snapshot(
        val priority: List<SttProvider>,
        val disabled: Set<SttProvider>,
        val groqModel: String,
        val deepgramModel: String,
        val fetchedAtMs: Long
    )

    fun currentOrDefault(): Snapshot =
        cached.get() ?: defaultSnapshot()

    fun defaultSnapshot(): Snapshot = Snapshot(
        priority = listOf(
            SttProvider.GROQ_FREE,
            SttProvider.INWORLD,
            SttProvider.ASSEMBLY_AI,
            SttProvider.DEEPGRAM,
            SttProvider.PAID_FALLBACK
        ),
        disabled = emptySet(),
        groqModel = "whisper-large-v3-turbo",
        deepgramModel = "nova-3",
        fetchedAtMs = 0L
    )

    /** Background refresh; nunca lanza. */
    fun refreshQuietly(context: Context) {
        runCatching {
            val url = BillingManager.getRemoteConfigUrl(context)
            if (url.isBlank()) return
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) return
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return
                cached.set(parse(raw) ?: return)
            }
        }
    }

    private fun parse(raw: String): Snapshot? {
        return runCatching {
            val root = JSONObject(raw)
            val priority = mutableListOf<SttProvider>()
            val arr = root.optJSONArray("priority") ?: JSONArray()
            for (i in 0 until arr.length()) {
                parseProvider(arr.optString(i))?.let { priority.add(it) }
            }
            if (priority.isEmpty()) {
                priority.addAll(defaultSnapshot().priority)
            }
            val disabled = mutableSetOf<SttProvider>()
            val off = root.optJSONArray("disabled") ?: JSONArray()
            for (i in 0 until off.length()) {
                parseProvider(off.optString(i))?.let { disabled.add(it) }
            }
            val models = root.optJSONObject("models")
            Snapshot(
                priority = priority,
                disabled = disabled,
                groqModel = models?.optString("groq")
                    ?.takeIf { it.isNotBlank() }
                    ?: "whisper-large-v3-turbo",
                deepgramModel = models?.optString("deepgram")
                    ?.takeIf { it.isNotBlank() }
                    ?: "nova-3",
                fetchedAtMs = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    private fun parseProvider(raw: String): SttProvider? = when (raw.trim().lowercase()) {
        "groq", "groq_free", "groq-free" -> SttProvider.GROQ_FREE
        "inworld", "inworld_free", "inworld-free" -> SttProvider.INWORLD
        "assembly", "assemblyai", "assembly_ai" -> SttProvider.ASSEMBLY_AI
        "deepgram" -> SttProvider.DEEPGRAM
        "paid", "paid_fallback", "fallback" -> SttProvider.PAID_FALLBACK
        else -> runCatching { SttProvider.valueOf(raw.trim().uppercase()) }.getOrNull()
    }
}
