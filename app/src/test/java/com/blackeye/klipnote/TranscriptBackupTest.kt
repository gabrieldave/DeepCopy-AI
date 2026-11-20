package com.blackeye.klipnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptBackupTest {

    @Test
    fun exportImport_roundTrip() {
        val entries = listOf(
            TranscriptHistoryStore.Entry(
                id = "id-1",
                timestampMs = 1_700_000_000_000L,
                sourceTitleOrUrl = "https://youtu.be/abc",
                providerUsed = "Groq",
                durationSec = 12.5,
                text = "Hola mundo"
            ),
            TranscriptHistoryStore.Entry(
                id = "id-2",
                timestampMs = 1_700_000_100_000L,
                sourceTitleOrUrl = "Nota",
                providerUsed = "Deepgram",
                durationSec = 3.0,
                text = "Linea\ncon salto"
            )
        )
        val json = TranscriptBackup.exportJson(entries, exportedAtMs = 99L)
        assertTrue(json.contains("\"format\":\"${TranscriptBackup.FORMAT}\""))
        assertTrue(json.contains("\"exportedAtMs\":99"))
        val parsed = TranscriptBackup.parse(json)
        assertTrue(parsed is TranscriptBackup.ParseResult.Ok)
        val back = (parsed as TranscriptBackup.ParseResult.Ok).entries
        assertEquals(2, back.size)
        assertEquals("id-1", back[0].id)
        assertEquals("Hola mundo", back[0].text)
        assertEquals("Linea\ncon salto", back[1].text)
        assertEquals(12.5, back[0].durationSec, 0.001)
    }

    @Test
    fun exportImport_emptyList() {
        val json = TranscriptBackup.exportJson(emptyList(), exportedAtMs = 1L)
        val parsed = TranscriptBackup.parse(json)
        assertTrue(parsed is TranscriptBackup.ParseResult.Ok)
        assertTrue((parsed as TranscriptBackup.ParseResult.Ok).entries.isEmpty())
    }

    @Test
    fun parse_rejectsCorruptJson() {
        assertTrue(TranscriptBackup.parse("{not json") is TranscriptBackup.ParseResult.Corrupt)
        assertTrue(TranscriptBackup.parse("{\"format\":\"other\",\"entries\":[]}") is TranscriptBackup.ParseResult.Corrupt)
        assertTrue(TranscriptBackup.parse("{\"format\":\"klipnote-transcripts-v1\"}") is TranscriptBackup.ParseResult.Corrupt)
    }

    @Test
    fun exportImport_preservesSourceUrl_andLegacyWithoutSourceUrlLoadsAsNull() {
        val withUrl = TranscriptHistoryStore.Entry(
            id = "url-1",
            timestampMs = 10L,
            sourceTitleOrUrl = "Video title",
            providerUsed = "Groq",
            durationSec = 1.0,
            text = "hola",
            sourceUrl = "https://youtu.be/abc123"
        )
        val json = TranscriptBackup.exportJson(listOf(withUrl), exportedAtMs = 2L)
        assertTrue(json.contains("\"sourceUrl\":\"https://youtu.be/abc123\""))
        val parsed = TranscriptBackup.parse(json)
        assertTrue(parsed is TranscriptBackup.ParseResult.Ok)
        val back = (parsed as TranscriptBackup.ParseResult.Ok).entries.single()
        assertEquals("https://youtu.be/abc123", back.sourceUrl)

        val legacy = """
            {"format":"klipnote-transcripts-v1","exportedAtMs":1,"entries":[{
              "id":"legacy-1",
              "timestampMs":5,
              "sourceTitleOrUrl":"Viejo",
              "providerUsed":"Groq",
              "durationSec":1.0,
              "text":"sin url"
            }]}
        """.trimIndent()
        val legacyParsed = TranscriptBackup.parse(legacy)
        assertTrue(legacyParsed is TranscriptBackup.ParseResult.Ok)
        val legacyEntry = (legacyParsed as TranscriptBackup.ParseResult.Ok).entries.single()
        assertEquals("legacy-1", legacyEntry.id)
        assertNull(legacyEntry.sourceUrl)
    }

    @Test
    fun exportImport_preservesWindowsPathQuotesAndNewlines() {
        val path = """C:\notas\tarea"""
        val quoted = """Dijo: "hola" y 'adiós'"""
        val multiline = "linea1\nlinea2\r\nlinea3"
        val withBackslashes = """a\\b\\c"""
        val entries = listOf(
            TranscriptHistoryStore.Entry(
                id = "win-1",
                timestampMs = 42L,
                sourceTitleOrUrl = path,
                providerUsed = "Groq",
                durationSec = 1.0,
                text = "$quoted\n$path\n$multiline\n$withBackslashes"
            )
        )
        val json = TranscriptBackup.exportJson(entries, exportedAtMs = 7L)
        val parsed = TranscriptBackup.parse(json)
        assertTrue(parsed is TranscriptBackup.ParseResult.Ok)
        val back = (parsed as TranscriptBackup.ParseResult.Ok).entries.single()
        assertEquals(path, back.sourceTitleOrUrl)
        assertEquals("$quoted\n$path\n$multiline\n$withBackslashes", back.text)
        assertEquals("""C:\notas\tarea""", back.sourceTitleOrUrl)
    }
}
