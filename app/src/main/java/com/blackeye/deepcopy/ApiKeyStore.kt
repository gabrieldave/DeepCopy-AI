package com.blackeye.deepcopy

import android.content.Context

object ApiKeyStore {
    private const val PREFS = "deepcopy_secure_prefs"
    private const val KEY = "deepgram_api_key"

    fun get(context: Context): String {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.trim()
            .orEmpty()
        if (stored.isNotEmpty()) return stored
        return BuildConfig.DEFAULT_DEEPGRAM_KEY.trim()
    }

    fun set(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, value.trim())
            .apply()
    }

    fun hasStored(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.isNotBlank() == true
    }
}
