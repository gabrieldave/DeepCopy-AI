package com.blackeye.klipnote

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Respaldo cifrado de Mis llaves (solo API keys + campos de pago/Deepgram).
 * Nunca incluye cookies ni guiones. Las llaves no se loguean ni se muestran al importar.
 */
object ApiKeyBackup {

    const val FORMAT = "klipnote-keys-v1"
    const val FILE_NAME = "klipnote-keys.knbackup"
    const val KDF = "PBKDF2WithHmacSHA256"
    const val ITERATIONS = 200_000
    const val MIN_PASSWORD_LEN = 8
    /** Límite de lectura en importación (deja llaves actuales intactas si se supera). */
    const val MAX_FILE_BYTES = 512 * 1024

    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    sealed class Outcome {
        data class Ok(val bytes: ByteArray) : Outcome()
        data object WeakPassword : Outcome()
        data object PasswordMismatch : Outcome()
        data object BadPasswordOrCorrupt : Outcome()
        data object Corrupt : Outcome()
        data object Empty : Outcome()
        data object TooLarge : Outcome()
    }

    fun exportEncrypted(
        context: Context,
        password: String,
        passwordConfirm: String
    ): Outcome {
        if (password.length < MIN_PASSWORD_LEN) return Outcome.WeakPassword
        if (password != passwordConfirm) return Outcome.PasswordMismatch
        val plain = buildPlainPayload(context)
        return runCatching {
            Outcome.Ok(encrypt(plain, password))
        }.getOrElse { Outcome.Corrupt }
    }

    fun importEncrypted(context: Context, fileBytes: ByteArray, password: String): Outcome {
        if (password.length < MIN_PASSWORD_LEN) return Outcome.WeakPassword
        if (fileBytes.isEmpty()) return Outcome.Empty
        if (fileBytes.size > MAX_FILE_BYTES) return Outcome.TooLarge
        val json = runCatching { String(fileBytes, StandardCharsets.UTF_8) }.getOrNull()
            ?: return Outcome.Corrupt
        val envelope = parseEnvelope(json) ?: return Outcome.Corrupt
        val plain = try {
            decrypt(envelope, password)
        } catch (_: AEADBadTagException) {
            return Outcome.BadPasswordOrCorrupt
        } catch (_: Exception) {
            return Outcome.BadPasswordOrCorrupt
        }
        return if (applyPlainPayload(context, plain)) Outcome.Ok(ByteArray(0)) else Outcome.Corrupt
    }

    /** Visible for tests: encrypt UTF-8 plaintext with password. */
    internal fun encrypt(plainUtf8: String, password: String): ByteArray {
        require(password.length >= MIN_PASSWORD_LEN)
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plainUtf8.toByteArray(StandardCharsets.UTF_8))
        val out = buildString {
            append('{')
            append("\"format\":\"").append(FORMAT).append("\",")
            append("\"kdf\":\"").append(KDF).append("\",")
            append("\"iterations\":").append(ITERATIONS).append(',')
            append("\"salt\":\"").append(b64(salt)).append("\",")
            append("\"iv\":\"").append(b64(iv)).append("\",")
            append("\"ciphertext\":\"").append(b64(ciphertext)).append('"')
            append('}')
        }
        return out.toByteArray(StandardCharsets.UTF_8)
    }

    /** Visible for tests: decrypt envelope JSON. */
    internal fun decryptToPlain(fileUtf8: String, password: String): String {
        val envelope = parseEnvelope(fileUtf8) ?: error("corrupt")
        return decrypt(envelope, password)
    }

    private data class Envelope(
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray
    )

    private fun parseEnvelope(json: String): Envelope? {
        if (!json.contains("\"format\":\"$FORMAT\"") && !json.contains("\"format\": \"$FORMAT\"")) {
            // allow either spacing
            val fmt = jsonStringField(json, "format") ?: return null
            if (fmt != FORMAT) return null
        }
        val format = jsonStringField(json, "format") ?: return null
        if (format != FORMAT) return null
        val kdf = jsonStringField(json, "kdf") ?: return null
        if (kdf != KDF) return null
        val iterations = jsonIntField(json, "iterations") ?: return null
        if (iterations !in 10_000..2_000_000) return null
        val salt = b64d(jsonStringField(json, "salt") ?: return null) ?: return null
        val iv = b64d(jsonStringField(json, "iv") ?: return null) ?: return null
        val ciphertext = b64d(jsonStringField(json, "ciphertext") ?: return null) ?: return null
        if (salt.size != SALT_LEN || iv.size != IV_LEN || ciphertext.isEmpty()) return null
        return Envelope(iterations, salt, iv, ciphertext)
    }

    private fun decrypt(envelope: Envelope, password: String): String {
        val key = deriveKey(password, envelope.salt, envelope.iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, envelope.iv))
        val plain = cipher.doFinal(envelope.ciphertext)
        return String(plain, StandardCharsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KDF)
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    internal fun buildPlainPayload(context: Context): String {
        val groq = ApiKeyStore.getGroq(context)
        val inworld = ApiKeyStore.getInworld(context)
        val assembly = ApiKeyStore.getAssemblyAi(context)
        val deepgram = ApiKeyStore.getDeepgram(context)
        val paid = ApiKeyStore.getPaidFallback(context)
        val project = ApiKeyStore.getDeepgramProjectId(context).orEmpty()
        val endpoint = ApiKeyStore.getPaidFallbackEndpoint(context)
        val engine = ApiKeyStore.getPaidFallbackEngine(context).name

        return buildString {
            append('{')
            append("\"groq\":\"").append(esc(groq)).append("\",")
            append("\"inworld\":\"").append(esc(inworld)).append("\",")
            append("\"assemblyai\":\"").append(esc(assembly)).append("\",")
            append("\"deepgram\":\"").append(esc(deepgram)).append("\",")
            append("\"paid_fallback\":\"").append(esc(paid)).append("\",")
            append("\"deepgram_project_id\":\"").append(esc(project)).append("\",")
            append("\"paid_fallback_endpoint\":\"").append(esc(endpoint)).append("\",")
            append("\"paid_fallback_engine\":\"").append(esc(engine)).append('"')
            append('}')
        }
    }

    /** Visible for tests without Android Context. */
    internal fun applyPlainPayloadJson(json: String): Map<String, String>? {
        val keys = listOf(
            "groq", "inworld", "assemblyai", "deepgram", "paid_fallback",
            "deepgram_project_id", "paid_fallback_endpoint", "paid_fallback_engine"
        )
        val out = linkedMapOf<String, String>()
        for (k in keys) {
            out[k] = jsonStringField(json, k) ?: ""
        }
        // Must look like our payload (at least one known key present as field).
        if (!keys.any { json.contains("\"$it\"") }) return null
        return out
    }

    private fun applyPlainPayload(context: Context, plain: String): Boolean {
        val map = applyPlainPayloadJson(plain) ?: return false
        // Apply without logging values.
        map["groq"]?.let { if (it.isNotBlank()) ApiKeyStore.setGroq(context, it) }
        map["inworld"]?.let { if (it.isNotBlank()) ApiKeyStore.setInworld(context, it) }
        map["assemblyai"]?.let { if (it.isNotBlank()) ApiKeyStore.setAssemblyAi(context, it) }
        map["deepgram"]?.let { if (it.isNotBlank()) ApiKeyStore.setDeepgram(context, it) }
        map["paid_fallback"]?.let { if (it.isNotBlank()) ApiKeyStore.setPaidFallback(context, it) }
        map["deepgram_project_id"]?.let { id ->
            if (id.isNotBlank()) ApiKeyStore.setDeepgramProjectId(context, id)
        }
        map["paid_fallback_endpoint"]?.let { ep ->
            ApiKeyStore.setPaidFallbackEndpoint(context, ep)
        }
        map["paid_fallback_engine"]?.let { name ->
            val engine = runCatching { PaidFallbackEngine.valueOf(name) }.getOrNull()
            if (engine != null) ApiKeyStore.setPaidFallbackEngine(context, engine)
        }
        return true
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun b64d(s: String): ByteArray? = runCatching {
        Base64.getDecoder().decode(s)
    }.getOrNull()

    private fun esc(s: String): String {
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

    private fun unesc(s: String): String {
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

    private fun jsonIntField(json: String, key: String): Int? {
        val re = Regex(""""$key"\s*:\s*(-?\d+)""")
        return re.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
