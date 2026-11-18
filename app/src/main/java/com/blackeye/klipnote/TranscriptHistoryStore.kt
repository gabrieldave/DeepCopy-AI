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
 */
object TranscriptHistoryStore {

    private const val FILE_NAME = "transcript_history.json"
    private const val MAX_ENTRIES = 100

    @Keep
    data class Entry(
        val id: String,
        val timestampMs: Long,
        val sourceTitleOrUrl: String,
        val providerUsed: String,
        val durationSec: Double,
        val text: String
    )

    fun load(context: Context): List<Entry> {
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

    fun append(
        context: Context,
        sourceTitleOrUrl: String,
        providerUsed: String,
        durationSec: Double,
        text: String
    ): List<Entry> {
        if (text.isBlank()) return load(context)
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            timestampMs = System.currentTimeMillis(),
            sourceTitleOrUrl = sourceTitleOrUrl.trim().ifBlank { "Sin título" },
            providerUsed = providerUsed,
            durationSec = durationSec,
            text = text
        )
        val next = (listOf(entry) + load(context)).take(MAX_ENTRIES)
        save(context, next)
        return next
    }

    fun delete(context: Context, id: String): List<Entry> {
        val next = load(context).filterNot { it.id == id }
        save(context, next)
        return next
    }

    fun clear(context: Context): List<Entry> {
        save(context, emptyList())
        return emptyList()
    }

    private fun save(context: Context, entries: List<Entry>) {
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
        storeFile(context).writeText(arr.toString())
    }

    private fun storeFile(context: Context): File =
        File(context.filesDir, FILE_NAME)
}
