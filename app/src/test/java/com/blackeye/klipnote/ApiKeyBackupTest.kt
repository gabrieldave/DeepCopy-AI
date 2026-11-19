package com.blackeye.klipnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

class ApiKeyBackupTest {

    @Test
    fun encryptDecrypt_roundTrip() {
        val plain = """{"groq":"gsk_test","inworld":"","assemblyai":"","deepgram":"dg","paid_fallback":"","deepgram_project_id":"proj","paid_fallback_endpoint":"","paid_fallback_engine":"GROQ_PAID"}"""
        val password = "secret-pass"
        val file = ApiKeyBackup.encrypt(plain, password)
        val back = ApiKeyBackup.decryptToPlain(String(file, Charsets.UTF_8), password)
        assertEquals(plain, back)
    }

    @Test
    fun decrypt_failsWithWrongPassword() {
        val plain = """{"groq":"abc","inworld":"","assemblyai":"","deepgram":"","paid_fallback":"","deepgram_project_id":"","paid_fallback_endpoint":"","paid_fallback_engine":"GROQ_PAID"}"""
        val file = String(ApiKeyBackup.encrypt(plain, "correct!!"), Charsets.UTF_8)
        try {
            ApiKeyBackup.decryptToPlain(file, "wrong!!!!")
            fail("expected AEADBadTagException")
        } catch (_: AEADBadTagException) {
            // ok
        } catch (t: Throwable) {
            // Some JREs wrap the tag failure
            assertTrue(t.message?.contains("tag", ignoreCase = true) == true || t.cause is AEADBadTagException)
        }
    }

    @Test
    fun decrypt_failsWhenCiphertextTampered() {
        val plain = """{"groq":"abc","inworld":"","assemblyai":"","deepgram":"","paid_fallback":"","deepgram_project_id":"","paid_fallback_endpoint":"","paid_fallback_engine":"GROQ_PAID"}"""
        val file = String(ApiKeyBackup.encrypt(plain, "correct!!"), Charsets.UTF_8)
        val marker = "\"ciphertext\":\""
        val start = file.indexOf(marker)
        assertTrue(start >= 0)
        val valueStart = start + marker.length
        val valueEnd = file.indexOf('"', valueStart)
        assertTrue(valueEnd > valueStart + 4)
        val flipAt = valueStart + 4
        val flipped = if (file[flipAt] == 'A') 'B' else 'A'
        val tampered = file.replaceRange(flipAt, flipAt + 1, flipped.toString())
        assertNotEquals(file, tampered)
        try {
            ApiKeyBackup.decryptToPlain(tampered, "correct!!")
            fail("expected failure on tampered ciphertext")
        } catch (_: Exception) {
            // ok
        }
    }

    @Test
    fun encrypt_rejectsShortPassword() {
        try {
            ApiKeyBackup.encrypt("{}", "short")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // ok
        }
    }
}
