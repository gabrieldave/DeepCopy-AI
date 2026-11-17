package com.blackeye.deepcopy

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Almacén BYOK cifrado (AndroidKeyStore + AES/GCM vía EncryptedSharedPreferences).
 * Nunca expone la clave completa en UI; migra plaintext legado y BuildConfig one-shot.
 */
object ApiKeyStore {
    private const val LEGACY_PREFS = "deepcopy_secure_prefs"
    private const val ENCRYPTED_PREFS = "deepcopy_encrypted_prefs"
    private const val KEY = "deepgram_api_key"
    private const val KEY_LAST4 = "deepgram_api_key_last4"

    const val MIGRATE_INTENT_EXTRA = "deepcopy_migrate_key"
    const val MIGRATE_FILE_NAME = ".deepcopy_migrate_once"

    private val migrated = AtomicBoolean(false)

    fun get(context: Context): String {
        migrateIfNeeded(context)
        ingestMigrationFile(context)
        return encryptedPrefs(context).getString(KEY, null)?.trim().orEmpty()
    }

    fun set(context: Context, value: String) {
        migrateIfNeeded(context)
        val trimmed = value.trim()
        encryptedPrefs(context).edit()
            .putString(KEY, trimmed)
            .putString(KEY_LAST4, last4(trimmed))
            .apply()
        // Limpia cualquier rastro plaintext legado.
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }

    fun clear(context: Context) {
        migrateIfNeeded(context)
        encryptedPrefs(context).edit()
            .remove(KEY)
            .remove(KEY_LAST4)
            .apply()
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }

    fun hasStored(context: Context): Boolean {
        migrateIfNeeded(context)
        return encryptedPrefs(context).getString(KEY, null)?.isNotBlank() == true
    }

    /** Últimos 4 caracteres para UI "Conectada (••••XXXX)". Nunca la clave completa. */
    fun maskedSuffix(context: Context): String? {
        migrateIfNeeded(context)
        val prefs = encryptedPrefs(context)
        val storedLast4 = prefs.getString(KEY_LAST4, null)?.takeIf { it.isNotBlank() }
        if (storedLast4 != null) return storedLast4
        val key = prefs.getString(KEY, null)?.trim().orEmpty()
        if (key.isEmpty()) return null
        val suffix = last4(key)
        prefs.edit().putString(KEY_LAST4, suffix).apply()
        return suffix
    }

    fun statusLabel(context: Context): String? {
        val suffix = maskedSuffix(context) ?: return null
        return "Conectada (••••$suffix)"
    }

    /**
     * Ingesta one-shot desde Intent (adb/migración local). No loguea el valor.
     * @return true si se guardó una clave nueva.
     */
    fun ingestMigrationExtra(context: Context, raw: String?): Boolean {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty()) return false
        migrateIfNeeded(context)
        if (encryptedPrefs(context).getString(KEY, null)?.isNotBlank() == true) return false
        set(context, candidate)
        return true
    }

    /**
     * Ingesta one-shot desde archivo en Downloads o files externos.
     * El archivo se borra tras cifrar. Nunca se loguea el contenido.
     */
    fun ingestMigrationFile(context: Context): Boolean {
        migrateIfNeeded(context)
        if (encryptedPrefs(context).getString(KEY, null)?.isNotBlank() == true) {
            deleteMigrateFiles(context)
            return false
        }
        val candidates = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, MIGRATE_FILE_NAME) },
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                MIGRATE_FILE_NAME
            )
        )
        for (file in candidates) {
            val raw = runCatching {
                if (file.exists()) file.readText().trim() else ""
            }.getOrDefault("")
            if (raw.isNotBlank()) {
                set(context, raw)
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

    private fun deleteMigrateFiles(context: Context) {
        listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, MIGRATE_FILE_NAME) },
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                MIGRATE_FILE_NAME
            )
        ).forEach { runCatching { it.delete() } }
    }

    private fun migrateIfNeeded(context: Context) {
        if (migrated.get()) return
        synchronized(this) {
            if (migrated.get()) return
            val enc = encryptedPrefs(context)
            if (enc.getString(KEY, null).isNullOrBlank()) {
                val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, null)
                    ?.trim()
                    .orEmpty()
                when {
                    legacy.isNotEmpty() -> {
                        enc.edit()
                            .putString(KEY, legacy)
                            .putString(KEY_LAST4, last4(legacy))
                            .apply()
                        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .remove(KEY)
                            .apply()
                    }
                    BuildConfig.DEFAULT_DEEPGRAM_KEY.isNotBlank() -> {
                        // Solo aplica a builds de estudio con llave inyectada; release comercial va vacío.
                        val baked = BuildConfig.DEFAULT_DEEPGRAM_KEY.trim()
                        enc.edit()
                            .putString(KEY, baked)
                            .putString(KEY_LAST4, last4(baked))
                            .apply()
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
}
