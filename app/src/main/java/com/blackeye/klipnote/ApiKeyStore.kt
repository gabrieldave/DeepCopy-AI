package com.blackeye.klipnote

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.annotation.Keep
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bóveda BYOK multi-proveedor (AndroidKeyStore + AES/GCM).
 * Nunca expone la clave completa en UI; migra plaintext legado y Deepgram one-shot.
 */
object ApiKeyStore {
    private const val LEGACY_PREFS = "deepcopy_secure_prefs"
    private const val ENCRYPTED_PREFS = "klipnote_encrypted_prefs"
    private const val LEGACY_ENCRYPTED_PREFS = "deepcopy_encrypted_prefs"

    private const val KEY_DEEPGRAM = "deepgram_api_key"
    private const val KEY_DEEPGRAM_LAST4 = "deepgram_api_key_last4"
    private const val KEY_DEEPGRAM_PROJECT = "deepgram_project_id"
    private const val KEY_GROQ = "groq_api_key"
    private const val KEY_GROQ_LAST4 = "groq_api_key_last4"
    private const val KEY_ASSEMBLY = "assemblyai_api_key"
    private const val KEY_ASSEMBLY_LAST4 = "assemblyai_api_key_last4"
    private const val KEY_PAID = "paid_fallback_api_key"
    private const val KEY_PAID_LAST4 = "paid_fallback_api_key_last4"
    private const val KEY_PAID_ENDPOINT = "paid_fallback_endpoint"
    private const val KEY_PAID_ENGINE = "paid_fallback_engine"

    const val MIGRATE_INTENT_EXTRA = "klipnote_migrate_key"
    const val MIGRATE_FILE_NAME = ".klipnote_migrate_once"
    private const val LEGACY_MIGRATE_FILE = ".deepcopy_migrate_once"

    private val migrated = AtomicBoolean(false)

    @Keep
    enum class ProviderSlot {
        GROQ, ASSEMBLY_AI, DEEPGRAM, PAID_FALLBACK
    }

    fun getDeepgram(context: Context): String = getSlot(context, ProviderSlot.DEEPGRAM)
    fun setDeepgram(context: Context, value: String) = setSlot(context, ProviderSlot.DEEPGRAM, value)
    fun clearDeepgram(context: Context) = clearSlot(context, ProviderSlot.DEEPGRAM)

    fun getGroq(context: Context): String = getSlot(context, ProviderSlot.GROQ)
    fun setGroq(context: Context, value: String) = setSlot(context, ProviderSlot.GROQ, value)
    fun clearGroq(context: Context) = clearSlot(context, ProviderSlot.GROQ)

    fun getAssemblyAi(context: Context): String = getSlot(context, ProviderSlot.ASSEMBLY_AI)
    fun setAssemblyAi(context: Context, value: String) = setSlot(context, ProviderSlot.ASSEMBLY_AI, value)
    fun clearAssemblyAi(context: Context) = clearSlot(context, ProviderSlot.ASSEMBLY_AI)

    fun getPaidFallback(context: Context): String = getSlot(context, ProviderSlot.PAID_FALLBACK)
    fun setPaidFallback(context: Context, value: String) = setSlot(context, ProviderSlot.PAID_FALLBACK, value)
    fun clearPaidFallback(context: Context) = clearSlot(context, ProviderSlot.PAID_FALLBACK)

    fun getPaidFallbackEndpoint(context: Context): String {
        migrateIfNeeded(context)
        return encryptedPrefs(context).getString(KEY_PAID_ENDPOINT, null)?.trim().orEmpty()
    }

    fun setPaidFallbackEndpoint(context: Context, value: String) {
        migrateIfNeeded(context)
        encryptedPrefs(context).edit()
            .putString(KEY_PAID_ENDPOINT, value.trim())
            .apply()
    }

    fun getPaidFallbackEngine(context: Context): PaidFallbackEngine {
        migrateIfNeeded(context)
        val raw = encryptedPrefs(context).getString(KEY_PAID_ENGINE, null)
        return runCatching { PaidFallbackEngine.valueOf(raw ?: "") }
            .getOrDefault(PaidFallbackEngine.GROQ_PAID)
    }

    fun setPaidFallbackEngine(context: Context, engine: PaidFallbackEngine) {
        migrateIfNeeded(context)
        encryptedPrefs(context).edit()
            .putString(KEY_PAID_ENGINE, engine.name)
            .apply()
    }

    fun getDeepgramProjectId(context: Context): String? {
        migrateIfNeeded(context)
        return encryptedPrefs(context).getString(KEY_DEEPGRAM_PROJECT, null)?.takeIf { it.isNotBlank() }
            ?: BillingManager.getProjectId(context)
    }

    fun setDeepgramProjectId(context: Context, projectId: String) {
        migrateIfNeeded(context)
        val trimmed = projectId.trim()
        encryptedPrefs(context).edit().putString(KEY_DEEPGRAM_PROJECT, trimmed).apply()
        BillingManager.setProjectId(context, trimmed)
    }

    /** Compat: Deepgram como get/set/clear/hasStored legacy. */
    fun get(context: Context): String = getDeepgram(context)

    fun set(context: Context, value: String) = setDeepgram(context, value)

    fun clear(context: Context) = clearDeepgram(context)

    fun hasStored(context: Context): Boolean = hasSlot(context, ProviderSlot.DEEPGRAM)

    fun hasAnyKey(context: Context): Boolean =
        ProviderSlot.entries.any { hasSlot(context, it) }

    fun hasSlot(context: Context, slot: ProviderSlot): Boolean {
        migrateIfNeeded(context)
        return encryptedPrefs(context).getString(keyFor(slot), null)?.isNotBlank() == true
    }

    fun maskedSuffix(context: Context, slot: ProviderSlot = ProviderSlot.DEEPGRAM): String? {
        migrateIfNeeded(context)
        val prefs = encryptedPrefs(context)
        val last4Key = last4KeyFor(slot)
        val storedLast4 = prefs.getString(last4Key, null)?.takeIf { it.isNotBlank() }
        if (storedLast4 != null) return storedLast4
        val key = prefs.getString(keyFor(slot), null)?.trim().orEmpty()
        if (key.isEmpty()) return null
        val suffix = last4(key)
        prefs.edit().putString(last4Key, suffix).apply()
        return suffix
    }

    fun statusLabel(context: Context, slot: ProviderSlot = ProviderSlot.DEEPGRAM): String? {
        val suffix = maskedSuffix(context, slot) ?: return null
        return "Conectada (••••$suffix)"
    }

    fun maskDisplay(context: Context, slot: ProviderSlot): String? {
        val suffix = maskedSuffix(context, slot) ?: return null
        return "••••$suffix"
    }

    fun ingestMigrationExtra(context: Context, raw: String?): Boolean {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty()) return false
        migrateIfNeeded(context)
        if (hasSlot(context, ProviderSlot.DEEPGRAM)) return false
        setDeepgram(context, candidate)
        return true
    }

    fun ingestMigrationFile(context: Context): Boolean {
        migrateIfNeeded(context)
        if (hasSlot(context, ProviderSlot.DEEPGRAM)) {
            deleteMigrateFiles(context)
            return false
        }
        val candidates = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, MIGRATE_FILE_NAME) },
            context.getExternalFilesDir(null)?.let { File(it, LEGACY_MIGRATE_FILE) },
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                MIGRATE_FILE_NAME
            ),
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                LEGACY_MIGRATE_FILE
            )
        )
        for (file in candidates) {
            val raw = runCatching {
                if (file.exists()) file.readText().trim() else ""
            }.getOrDefault("")
            if (raw.isNotBlank()) {
                setDeepgram(context, raw)
                runCatching {
                    file.writeBytes(ByteArray(file.length().toInt().coerceAtLeast(1)))
                    file.delete()
                }
                deleteMigrateFiles(context)
                return true
            }
        }
        return false
    }

    private fun getSlot(context: Context, slot: ProviderSlot): String {
        migrateIfNeeded(context)
        return encryptedPrefs(context).getString(keyFor(slot), null)?.trim().orEmpty()
    }

    private fun setSlot(context: Context, slot: ProviderSlot, value: String) {
        migrateIfNeeded(context)
        val trimmed = value.trim()
        encryptedPrefs(context).edit()
            .putString(keyFor(slot), trimmed)
            .putString(last4KeyFor(slot), last4(trimmed))
            .apply()
        if (slot == ProviderSlot.DEEPGRAM) {
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_DEEPGRAM)
                .apply()
        }
    }

    private fun clearSlot(context: Context, slot: ProviderSlot) {
        migrateIfNeeded(context)
        encryptedPrefs(context).edit()
            .remove(keyFor(slot))
            .remove(last4KeyFor(slot))
            .apply()
        if (slot == ProviderSlot.DEEPGRAM) {
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_DEEPGRAM)
                .apply()
        }
    }

    private fun keyFor(slot: ProviderSlot): String = when (slot) {
        ProviderSlot.GROQ -> KEY_GROQ
        ProviderSlot.ASSEMBLY_AI -> KEY_ASSEMBLY
        ProviderSlot.DEEPGRAM -> KEY_DEEPGRAM
        ProviderSlot.PAID_FALLBACK -> KEY_PAID
    }

    private fun last4KeyFor(slot: ProviderSlot): String = when (slot) {
        ProviderSlot.GROQ -> KEY_GROQ_LAST4
        ProviderSlot.ASSEMBLY_AI -> KEY_ASSEMBLY_LAST4
        ProviderSlot.DEEPGRAM -> KEY_DEEPGRAM_LAST4
        ProviderSlot.PAID_FALLBACK -> KEY_PAID_LAST4
    }

    private fun deleteMigrateFiles(context: Context) {
        listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, MIGRATE_FILE_NAME) },
            context.getExternalFilesDir(null)?.let { File(it, LEGACY_MIGRATE_FILE) },
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                MIGRATE_FILE_NAME
            ),
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                LEGACY_MIGRATE_FILE
            )
        ).forEach { runCatching { it.delete() } }
    }

    private fun migrateIfNeeded(context: Context) {
        if (migrated.get()) return
        synchronized(this) {
            if (migrated.get()) return
            val enc = encryptedPrefs(context)
            if (enc.getString(KEY_DEEPGRAM, null).isNullOrBlank()) {
                val fromLegacyEnc = runCatching {
                    legacyEncryptedPrefs(context).getString(KEY_DEEPGRAM, null)?.trim().orEmpty()
                }.getOrDefault("")
                val fromPlain = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_DEEPGRAM, null)
                    ?.trim()
                    .orEmpty()
                val candidate = when {
                    fromLegacyEnc.isNotEmpty() -> fromLegacyEnc
                    fromPlain.isNotEmpty() -> fromPlain
                    BuildConfig.DEFAULT_DEEPGRAM_KEY.isNotBlank() ->
                        BuildConfig.DEFAULT_DEEPGRAM_KEY.trim()
                    else -> ""
                }
                if (candidate.isNotEmpty()) {
                    enc.edit()
                        .putString(KEY_DEEPGRAM, candidate)
                        .putString(KEY_DEEPGRAM_LAST4, last4(candidate))
                        .apply()
                    context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .remove(KEY_DEEPGRAM)
                        .apply()
                }
                // Copia last4 / project_id si existían en prefs cifradas legacy.
                runCatching {
                    val legacy = legacyEncryptedPrefs(context)
                    val last4 = legacy.getString(KEY_DEEPGRAM_LAST4, null)
                    if (!last4.isNullOrBlank() && enc.getString(KEY_DEEPGRAM_LAST4, null).isNullOrBlank()) {
                        enc.edit().putString(KEY_DEEPGRAM_LAST4, last4).apply()
                    }
                }
            }
            migrated.set(true)
        }
    }

    private fun last4(value: String): String =
        if (value.length <= 4) value else value.takeLast(4)

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            ENCRYPTED_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun legacyEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            LEGACY_ENCRYPTED_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
