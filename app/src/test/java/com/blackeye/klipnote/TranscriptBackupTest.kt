package com.blackeye.klipnote

import org.junit.Assert.assertEquals
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
}
