package com.blackeye.klipnote

import android.content.Context
import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Historial de guiones (JSON en filesDir). Sin KSP / Room.
 * Máx. 100 entradas, más recientes primero.
 *
 * El ID debe coincidir con [BillingManager.HistoryEntry.id] cuando el trabajo
 * guarda desde el servicio, para que la UI pueda hacer upsert idempotente.
 */
object TranscriptHistoryStore {

    private const val FILE_NAME = "transcript_history.json"
    private const val MAX_ENTRIES = 100
    private const val PREFS = "klipnote_transcript_prefs"
    private const val KEY_MIGRATED_BILLING = "migrated_billing_to_guiones_v163"

    private val lock = Any()

    @Keep
    data class Entry(
        val id: String,
        val timestampMs: Long,
        val sourceTitleOrUrl: String,
        val providerUsed: String,
        val durationSec: Double,
        val text: String
    )

    fun load(context: Context): List<Entry> = synchronized(lock) {
        loadUnlocked(context)
    }

    /**
     * Inserta o ignora si [id] ya existe. Idempotente: nunca duplica por ID.
     * Tampoco inserta si ya hay el mismo texto (respaldo UI / migraciones viejas).
     */
    fun upsertById(
        context: Context,
        id: String,
        sourceTitleOrUrl: String,
        providerUsed: String,
        durationSec: Double,
        text: String,
        timestampMs: Long = System.currentTimeMillis()
    ): List<Entry> = synchronized(lock) {
        if (text.isBlank() || id.isBlank()) return loadUnlocked(context)
        val current = loadUnlocked(context)
        if (current.any { it.id == id }) return current
        if (current.any { it.text == text }) return current
        val entry = Entry(
            id = id,
            timestampMs = timestampMs,
            sourceTitleOrUrl = sourceTitleOrUrl.trim().ifBlank {
                context.getString(R.string.untitled_source)
            },
            providerUsed = providerUsed.ifBlank {
                context.getString(R.string.history_provider_unknown)
            },
            durationSec = durationSec,
            text = text
        )
        val next = (listOf(entry) + current).take(MAX_ENTRIES)
        saveUnlocked(context, next)
        return next
    }

    /** Respaldo UI: sin ID de trabajo, evita duplicar por texto idéntico. */
    fun append(
        context: Context,
        sourceTitleOrUrl: String,
        providerUsed: String,
        durationSec: Double,
        text: String
    ): List<Entry> {
        if (text.isBlank()) return load(context)
        return upsertById(
            context = context,
            id = UUID.randomUUID().toString(),
            sourceTitleOrUrl = sourceTitleOrUrl,
            providerUsed = providerUsed,
            durationSec = durationSec,
            text = text
        )
    }

    fun delete(context: Context, id: String): List<Entry> = synchronized(lock) {
        val next = loadUnlocked(context).filterNot { it.id == id }
        saveUnlocked(context, next)
        next
    }

    fun clear(context: Context): List<Entry> = synchronized(lock) {
        saveUnlocked(context, emptyList())
        emptyList()
    }

    /**
     * Una sola vez: pasa a Guiones las entradas de [BillingManager] con texto
     * que aún no estén (por ID o por texto idéntico).
     * @return cuántas entradas nuevas se añadieron
     */
    fun migrateFromBillingIfNeeded(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED_BILLING, false)) return 0
        val added = synchronized(lock) {
            val billing = BillingManager.loadHistory(context)
            var count = 0
            var current = loadUnlocked(context)
            for (b in billing) {
                if (b.transcript.isBlank()) continue
                if (current.any { it.id == b.id }) continue
                if (current.any { it.text == b.transcript }) continue
                val entry = Entry(
                    id = b.id,
                    timestampMs = b.timestampMs,
                    sourceTitleOrUrl = b.fileName.ifBlank {
                        context.getString(R.string.untitled_source)
                    },
                    providerUsed = b.provider?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.history_provider_unknown),
                    durationSec = b.durationSeconds,
                    text = b.transcript
                )
                current = (listOf(entry) + current).take(MAX_ENTRIES)
                count++
            }
            if (count > 0) saveUnlocked(context, current)
            count
        }
        prefs.edit().putBoolean(KEY_MIGRATED_BILLING, true).apply()
        return added
    }

    private fun loadUnlocked(context: Context): List<Entry> {
        val file = storeFile(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Entry(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            timestampMs = o.optLong("timestampMs"),
                            sourceTitleOrUrl = o.optString("sourceTitleOrUrl"),
                            providerUsed = o.optString("providerUsed"),
                            durationSec = o.optDouble("durationSec"),
                            text = o.optString("text")
                        )
                    )
                }
            }.sortedByDescending { it.timestampMs }
        }.getOrElse { emptyList() }
    }

    private fun saveUnlocked(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("timestampMs", e.timestampMs)
                    put("sourceTitleOrUrl", e.sourceTitleOrUrl)
                    put("providerUsed", e.providerUsed)
                    put("durationSec", e.durationSec)
                    put("text", e.text)
                }
            )
        }
        val target = storeFile(context)
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun storeFile(context: Context): File =
        File(context.filesDir, FILE_NAME)
}
