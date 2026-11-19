package com.blackeye.klipnote

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Persisted UI language (independent from STT language mode). */
object AppLocales {

    private const val PREFS = "klipnote_app_locale"
    private const val KEY_TAG = "locale_tag"

    /** `""` = follow system; `en`; `es`. */
    fun getSavedTag(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, "").orEmpty()

    fun setSavedTag(context: Context, tag: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, tag)
            .apply()
    }

    fun applyFromPrefs(context: Context) {
        val tag = getSavedTag(context)
        val locales = when (tag) {
            "en" -> LocaleListCompat.forLanguageTags("en")
            "es" -> LocaleListCompat.forLanguageTags("es")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun setAndApply(context: Context, tag: String) {
        setSavedTag(context, tag)
        applyFromPrefs(context)
    }

    fun isAppSpanish(context: Context): Boolean {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            return appLocales[0]?.language?.startsWith("es", ignoreCase = true) == true
        }
        val cfg = context.resources.configuration
        val locale = if (android.os.Build.VERSION.SDK_INT >= 24) {
            cfg.locales[0]
        } else {
            @Suppress("DEPRECATION")
            cfg.locale
        }
        return locale.language.startsWith("es", ignoreCase = true)
    }

    fun currentDisplayName(context: Context): String = when (getSavedTag(context)) {
        "en" -> context.getString(R.string.app_language_en)
        "es" -> context.getString(R.string.app_language_es)
        else -> context.getString(R.string.app_language_system)
    }
}