package com.blackeye.klipnote

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Respaldo separado de Guiones: JSON UTF-8 sin contraseña.
 * Importa con [TranscriptHistoryStore.upsertById] (no duplica por ID).
 */
object TranscriptBackup {

    const val FORMAT = "klipnote-transcripts-v1"
    const val FILE_NAME = "klipnote-transcripts.json"
    /** Límite de lectura en importación (deja datos actuales intactos si se supera). */
    const val MAX_FILE_BYTES = 10 * 1024 * 1024

    sealed class ParseResult {
        data class Ok(val entries: List<TranscriptHistoryStore.Entry>) : ParseResult()
        data object Corrupt : ParseResult()
    }

    fun exportJson(
        entries: List<TranscriptHistoryStore.Entry>,
        exportedAtMs: Long = System.currentTimeMillis()
    ): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"format\":\"").append(FORMAT).append("\",")
        sb.append("\"exportedAtMs\":").append(exportedAtMs).append(',')
        sb.append("\"entries\":[")
        entries.forEachIndexed { index, e ->
            if (index > 0) sb.append(',')
            sb.append('{')
            sb.append("\"id\":\"").append(esc(e.id)).append("\",")
            sb.append("\"timestampMs\":").append(e.timestampMs).append(',')
            sb.append("\"sourceTitleOrUrl\":\"").append(esc(e.sourceTitleOrUrl)).append("\",")
            sb.append("\"providerUsed\":\"").append(esc(e.providerUsed)).append("\",")
            sb.append("\"durationSec\":").append(e.durationSec).append(',')
            sb.append("\"text\":\"").append(esc(e.text)).append('"')
            val url = e.sourceUrl
            if (!url.isNullOrBlank()) {
                sb.append(",\"sourceUrl\":\"").append(esc(url)).append('"')
            }
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    fun parse(json: String): ParseResult {
        val format = jsonStringField(json, "format") ?: return ParseResult.Corrupt
        if (format != FORMAT) return ParseResult.Corrupt
        val entriesBlock = extractArray(json, "entries") ?: return ParseResult.Corrupt
        if (entriesBlock.isBlank() || entriesBlock == "[]") {
            return ParseResult.Ok(emptyList())
        }
        val objects = splitJsonObjects(entriesBlock)
        val entries = ArrayList<TranscriptHistoryStore.Entry>(objects.size)
        for (obj in objects) {
            val id = jsonStringField(obj, "id") ?: continue
            if (id.isBlank()) continue
            val text = jsonStringField(obj, "text") ?: ""
            if (text.isBlank()) continue
            val timestampMs = jsonLongField(obj, "timestampMs") ?: 0L
            val source = jsonStringField(obj, "sourceTitleOrUrl").orEmpty()
            val provider = jsonStringField(obj, "providerUsed").orEmpty()
            val duration = jsonDoubleField(obj, "durationSec") ?: 0.0
            val sourceUrl = jsonStringField(obj, "sourceUrl")?.takeIf { it.isNotBlank() }
            entries.add(
                TranscriptHistoryStore.Entry(
                    id = id,
                    timestampMs = timestampMs,
                    sourceTitleOrUrl = source,
                    providerUsed = provider,
                    durationSec = duration,
                    text = text,
                    sourceUrl = sourceUrl
                )
            )
        }
        return ParseResult.Ok(entries)
    }

    /**
     * @return number of newly inserted guiones (IDs that were not already present),
     * or -1 if the file is corrupt / too large (current data left untouched).
     */
    fun importInto(context: Context, jsonUtf8: ByteArray): Int {
        if (jsonUtf8.size > MAX_FILE_BYTES) return -1
        val json = runCatching { String(jsonUtf8, StandardCharsets.UTF_8) }.getOrNull()
            ?: return -1
        val parsed = parse(json)
        if (parsed !is ParseResult.Ok) return -1
        var added = 0
        val beforeIds = TranscriptHistoryStore.load(context).map { it.id }.toHashSet()
        for (e in parsed.entries) {
            TranscriptHistoryStore.upsertById(
                context = context,
                id = e.id,
                sourceTitleOrUrl = e.sourceTitleOrUrl,
                providerUsed = e.providerUsed,
                durationSec = e.durationSec,
                text = e.text,
                timestampMs = e.timestampMs,
                sourceUrl = e.sourceUrl
            )
            if (e.id !in beforeIds) {
                val now = TranscriptHistoryStore.load(context)
                if (now.any { it.id == e.id } && e.id !in beforeIds) {
                    added++
                    beforeIds.add(e.id)
                }
            }
        }
        return added
    }

    /** Lee como máximo [MAX_FILE_BYTES]; null si el archivo es demasiado grande. */
    fun readLimited(input: InputStream): ByteArray? =
        readBytesLimited(input, MAX_FILE_BYTES)

    internal fun esc(s: String): String {
        val out = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /** Unescape JSON string contents left-to-right (preserves paths like C:\\notas\\tarea). */
    internal fun unesc(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> out.append('\\')
                    '"' -> out.append('"')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    '/' -> out.append('/')
                    else -> out.append(s[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun jsonStringField(json: String, key: String): String? {
        val re = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val raw = re.find(json)?.groupValues?.getOrNull(1) ?: return null
        return unesc(raw)
    }

    private fun jsonLongField(json: String, key: String): Long? {
        val re = Regex(""""$key"\s*:\s*(-?\d+)""")
        return re.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun jsonDoubleField(json: String, key: String): Double? {
        val re = Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""")
        return re.find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun extractArray(json: String, key: String): String? {
        val marker = Regex(""""$key"\s*:\s*\[""")
        val m = marker.find(json) ?: return null
        val start = m.range.last // position of '['
        var depth = 0
        var i = start
        while (i < json.length) {
            when (json[i]) {
                '"' -> {
                    i++
                    while (i < json.length) {
                        when (json[i]) {
                            '\\' -> i += 2
                            '"' -> break
                            else -> i++
                        }
                    }
                }
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return json.substring(start + 1, i)
                }
            }
            i++
        }
        return null
    }

    private fun splitJsonObjects(arrayBody: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = -1
        var i = 0
        while (i < arrayBody.length) {
            val c = arrayBody[i]
            when (c) {
                '"' -> {
                    i++
                    while (i < arrayBody.length) {
                        when (arrayBody[i]) {
                            '\\' -> i += 2
                            '"' -> break
                            else -> i++
                        }
                    }
                }
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(arrayBody.substring(start, i + 1))
                        start = -1
                    }
                }
            }
            i++
        }
        return out
    }

    internal fun readBytesLimited(input: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buf = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
