package com.blackeye.klipnote.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/** Neutros fijos (fondo oscuro). Ámbar/rojo no cambian con el tema. */
object KlipNeutrals {
    val Bg = Color(0xFF0B0F17)
    val Card = Color(0xFF151C28)
    val OnBg = Color(0xFFE8EEF7)
    val Muted = Color(0xFFB0BCCE)
    val Amber = Color(0xFFFBBF24)
    val Red = Color(0xFFF87171)
    val FieldBorder = Color(0xFF243044)
    val ChipBg = Color(0xFF0F1623)
}

enum class KlipThemeId(val prefKey: String, val label: String) {
    GREEN("green", "Verde KlipNote"),
    BLUE("blue", "Azul"),
    PURPLE("purple", "Morado"),
    ORANGE("orange", "Naranja"),
    PINK("pink", "Rosa"),
    DYNAMIC("dynamic", "Dinámico");

    companion object {
        fun fromPref(raw: String?): KlipThemeId {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.prefKey == key } ?: GREEN
        }

        fun selectable(context: Context): List<KlipThemeId> =
            if (Build.VERSION.SDK_INT >= 31) entries.toList()
            else entries.filter { it != DYNAMIC }
    }
}

@Immutable
data class KlipColors(
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val bg: Color = KlipNeutrals.Bg,
    val card: Color = KlipNeutrals.Card,
    val onBg: Color = KlipNeutrals.OnBg,
    val muted: Color = KlipNeutrals.Muted,
    val amber: Color = KlipNeutrals.Amber,
    val red: Color = KlipNeutrals.Red
)

object ThemePrefs {
    private const val PREFS = "klipnote_appearance"
    private const val KEY_THEME = "theme_id"

    fun getThemeId(context: Context): KlipThemeId {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, KlipThemeId.GREEN.prefKey)
        val id = KlipThemeId.fromPref(raw)
        return if (id == KlipThemeId.DYNAMIC && Build.VERSION.SDK_INT < 31) {
            KlipThemeId.GREEN
        } else id
    }

    fun setThemeId(context: Context, id: KlipThemeId) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, id.prefKey)
            .apply()
    }
}

/**
 * Paleta mutable legible desde getters no-@Composable.
 * Se actualiza al cambiar tema; Compose recompone al leer el State.
 */
@Stable
object KlipPalette {
    var accent by mutableStateOf(Color(0xFF10B981))
        private set
    var onAccent by mutableStateOf(Color.Black)
        private set
    var accentSoft by mutableStateOf(Color(0xFF10B981).copy(alpha = 0.22f))
        private set

    fun apply(colors: KlipColors) {
        accent = colors.accent
        onAccent = colors.onAccent
        accentSoft = colors.accentSoft
    }
}

val LocalKlipColors = staticCompositionLocalOf {
    resolveStaticColors(KlipThemeId.GREEN)
}

fun resolveStaticColors(id: KlipThemeId): KlipColors {
    val (accent, onAccent) = when (id) {
        KlipThemeId.GREEN -> Color(0xFF10B981) to Color.Black
        KlipThemeId.BLUE -> Color(0xFF3B82F6) to Color.White
        KlipThemeId.PURPLE -> Color(0xFF8B5CF6) to Color.White
        KlipThemeId.ORANGE -> Color(0xFFF59E0B) to Color.Black
        KlipThemeId.PINK -> Color(0xFFEC4899) to Color.White
        KlipThemeId.DYNAMIC -> Color(0xFF10B981) to Color.Black // fallback sin contexto
    }
    return KlipColors(
        accent = accent,
        onAccent = contrastOnAccent(accent, onAccent),
        accentSoft = accent.copy(alpha = 0.22f)
    )
}

@Composable
fun resolveKlipColors(id: KlipThemeId): KlipColors {
    val context = LocalContext.current
    if (id == KlipThemeId.DYNAMIC && Build.VERSION.SDK_INT >= 31) {
        val dyn = dynamicDarkColorScheme(context)
        val accent = dyn.primary
        val onAccent = contrastOnAccent(accent, dyn.onPrimary)
        return KlipColors(
            accent = accent,
            onAccent = onAccent,
            accentSoft = accent.copy(alpha = 0.22f)
        )
    }
    return resolveStaticColors(if (id == KlipThemeId.DYNAMIC) KlipThemeId.GREEN else id)
}

/** Garantiza contraste AA aproximado del texto sobre el acento. */
private fun contrastOnAccent(accent: Color, preferred: Color): Color {
    val lum = accent.luminance()
    return when {
        preferred == Color.Black && lum < 0.35f -> Color.White
        preferred == Color.White && lum > 0.65f -> Color.Black
        else -> preferred
    }
}

@Composable
fun KlipTheme(
    themeId: KlipThemeId,
    content: @Composable () -> Unit
) {
    val colors = resolveKlipColors(themeId)
    // Mantener KlipPalette al día para getters legacy (Accent get() = …).
    KlipPalette.apply(colors)
    CompositionLocalProvider(LocalKlipColors provides colors) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                secondary = colors.accent,
                onSecondary = colors.onAccent,
                background = colors.bg,
                surface = colors.card,
                onBackground = colors.onBg,
                onSurface = colors.onBg,
                error = colors.red
            ),
            content = content
        )
    }
}

/** Evita warning de isSystemInDarkTheme sin usarlo: siempre oscuro. */
@Composable
fun rememberIsDarkSurface(): Boolean = isSystemInDarkTheme() || true
