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
 * Config remota OPCIONAL del router (orden, motores apagados, modelos y textos de ofertas).
 *
 * - Por defecto se lee de config/stt-router.json del propio repo de GitHub
 *   (BuildConfig.REMOTE_CONFIG_URL, que se arma solo en CI con GITHUB_REPOSITORY).
 * - Si no hay URL, no hay internet o el JSON es inválido, la app usa sus valores por defecto.
 * - Nunca puede habilitar el respaldo pagado: eso solo lo decide el usuario (Modo $0).
 * - La última config buena se guarda para usarla sin conexión.
 */
object RemoteRouterConfig {

    private const val PREFS = "klipnote_remote_config"
    private const val KEY_RAW = "last_good_raw"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val cached = AtomicReference<Snapshot?>(null)

    @Keep
    data class Snapshot(
        val priority: List<SttProvider>,
        val disabled: Set<SttProvider>,
        val groqModel: String,
        val deepgramModel: String,
        val offers: Map<SttProvider, String>,
        val fetchedAtMs: Long
    )

    fun currentOrDefault(): Snapshot =
        cached.get() ?: defaultSnapshot()

    fun defaultSnapshot(): Snapshot = Snapshot(
        priority = DEFAULT_PRIORITY,
        disabled = emptySet(),
        groqModel = "whisper-large-v3-turbo",
        deepgramModel = "nova-3",
        offers = emptyMap(),
        fetchedAtMs = 0L
    )

    private val DEFAULT_PRIORITY = listOf(
        SttProvider.GROQ_FREE,
        SttProvider.INWORLD,
        SttProvider.ASSEMBLY_AI,
        SttProvider.DEEPGRAM,
        SttProvider.PAID_FALLBACK
    )

    /** Carga la última config buena guardada (sin red). Llamar al iniciar la app. */
    fun loadCached(context: Context) {
        if (cached.get() != null) return
        runCatching {
            val raw = prefs(context).getString(KEY_RAW, null) ?: return
            parse(raw)?.let { cached.compareAndSet(null, it) }
        }
    }

    /** Refresco en segundo plano; nunca lanza. */
    fun refreshQuietly(context: Context) {
        runCatching {
            val url = BillingManager.getRemoteConfigUrl(context)
            if (url.isBlank() || !url.startsWith("https://")) return
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) return
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank() || raw.length > 64_000) return
                val snap = parse(raw) ?: return
                cached.set(snap)
                prefs(context).edit().putString(KEY_RAW, raw).apply()
            }
        }
    }

    private fun parse(raw: String): Snapshot? {
        return runCatching {
            val root = JSONObject(raw)
            val priority = mutableListOf<SttProvider>()
            val arr = root.optJSONArray("priority") ?: JSONArray()
            for (i in 0 until arr.length()) {
                parseProvider(arr.optString(i))?.let { if (it !in priority) priority.add(it) }
            }
            // Ningún motor puede quedar fuera por error de config: se agregan los faltantes al final
            // y el respaldo pagado siempre va último.
            DEFAULT_PRIORITY.forEach { if (it !in priority) priority.add(it) }
            priority.remove(SttProvider.PAID_FALLBACK)
            priority.add(SttProvider.PAID_FALLBACK)

            val disabled = mutableSetOf<SttProvider>()
            val off = root.optJSONArray("disabled") ?: JSONArray()
            for (i in 0 until off.length()) {
                parseProvider(off.optString(i))?.let { disabled.add(it) }
            }

            val offers = mutableMapOf<SttProvider, String>()
            root.optJSONObject("offers")?.let { obj ->
                obj.keys().forEach { k ->
                    val p = parseProvider(k) ?: return@forEach
                    val text = obj.optString(k).trim()
                    if (text.isNotEmpty()) offers[p] = text.take(240)
                }
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
                offers = offers,
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

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
