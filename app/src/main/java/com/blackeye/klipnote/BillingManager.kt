package com.blackeye.klipnote

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Auditor de gasto multi-proveedor + cuotas gratis (Groq diario, AssemblyAI bolsa, Deepgram USD).
 */
object BillingManager {

    private const val PREFS = "klipnote_billing_prefs"
    private const val LEGACY_PREFS = "deepcopy_billing_prefs"
    private const val KEY_INITIAL_CREDIT = "initial_credit_usd"
    private const val KEY_APP_CAP = "app_cap_usd"
    private const val KEY_MIN_RESERVE = "min_reserve_usd"
    private const val KEY_INCLUDE_DIARIZE = "include_diarize_cost"
    private const val KEY_AUTO_TRANSCRIBE = "auto_transcribe_on_share"
    private const val KEY_LANGUAGE_MODE = "language_mode"
    private const val KEY_FORCED_LANGUAGE = "forced_language_code"
    private const val KEY_DIARIZE_ENABLED = "diarize_enabled"
    private const val KEY_PROJECT_ID = "deepgram_project_id"
    private const val KEY_APP_SPENT = "app_spent_usd"
    private const val KEY_MONTH_KEY = "usage_month_key"
    private const val KEY_MONTH_TRANSCRIPTIONS = "usage_month_transcriptions"
    private const val KEY_MONTH_SECONDS = "usage_month_seconds"
    private const val KEY_SESSION_TRANSCRIPTIONS = "usage_session_transcriptions"
    private const val KEY_SESSION_SECONDS = "usage_session_seconds"
    private const val KEY_GROQ_DAY = "groq_day_key"
    private const val KEY_GROQ_REMAINING = "groq_remaining_seconds"
    private const val KEY_GROQ_LIMIT = "groq_limit_seconds"
    private const val KEY_GROQ_USED_TODAY = "groq_used_seconds_today"
    private const val KEY_GROQ_LIVE = "groq_has_live_headers"
    private const val KEY_ASSEMBLY_USED = "assembly_used_seconds"
    private const val KEY_INWORLD_USED = "inworld_used_seconds"
    private const val KEY_ALLOW_PAID = "allow_paid_fallback"
    private const val KEY_LAST_OFFER_CHECK = "last_offer_check_ms"
    private const val KEY_REMOTE_CONFIG_URL = "remote_router_config_url"
    private const val HISTORY_FILE = "billing_history.json"

    const val RATE_AUTO_PER_MIN = 0.0043
    const val RATE_MULTI_PER_MIN = 0.0052
    const val RATE_DIARIZE_PER_MIN = 0.0020
    const val DEFAULT_INITIAL_CREDIT = 200.0
    const val DEFAULT_APP_CAP = 195.0
    const val DEFAULT_MIN_RESERVE = 5.0
    const val GIANT_FILE_THRESHOLD_USD = 0.50
    const val DEEPGRAM_SAFE_FLOOR_USD = 1.0
    /** Fallback histórico; la UI usa el límite real de cabeceras (~7200s/ventana). */
    const val GROQ_DAILY_SECONDS = 28_800.0
    /** Tope provisional hasta la primera respuesta con headers de Groq (ventana ~1h). */
    const val GROQ_DEFAULT_LIMIT_SECONDS = 7_200.0
    /** Bolsa única On-Demand (~400 min); no se renueva sola cada mes. */
    const val INWORLD_POOL_SECONDS = 24_000.0
    const val ASSEMBLY_POOL_SECONDS = 666_000.0 // 185h (estimación del regalo de $50)
    /** Regalo de AssemblyAI en USD y tarifa estimada usada para convertir saldo ↔ horas. */
    const val ASSEMBLY_POOL_USD = 50.0
    const val ASSEMBLY_EST_USD_PER_HOUR = ASSEMBLY_POOL_USD / (ASSEMBLY_POOL_SECONDS / 3600.0)

    private val cooldownUntilMs = ConcurrentHashMap<SttProvider, Long>()
    private val promoEnded = ConcurrentHashMap.newKeySet<SttProvider>()

    @Keep
    data class MediaProbe(
        val displayName: String,
        val durationSeconds: Double
    )

    @Keep
    data class CostEstimate(
        val durationSeconds: Double,
        val mode: LanguageMode,
        val includeDiarize: Boolean,
        val ratePerMinute: Double,
        val estimatedUsd: Double
    )

    sealed class HardStop {
        data object Ok : HardStop()
        data class ReserveBreach(val remainingAfter: Double, val reserve: Double) : HardStop()
        data class AppCapBreach(val projectedSpent: Double, val cap: Double) : HardStop()
        data class StrictZeroMode(val message: String) : HardStop()
    }

    @Keep
    data class HistoryEntry(
        val id: String,
        val timestampMs: Long,
        val fileName: String,
        val durationSeconds: Double,
        val mode: String,
        val diarize: Boolean,
        val appCostUsd: Double,
        val balanceBefore: Double?,
        val balanceAfter: Double?,
        val officialDeltaUsd: Double?,
        val transcript: String,
        val audioPath: String? = null,
        val provider: String? = null
    ) {
        val formattedWhen: String
            get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(timestampMs))
    }

    @Keep
    data class LocalUsage(
        val monthTranscriptions: Int,
        val monthMinutes: Double,
        val sessionTranscriptions: Int,
        val sessionMinutes: Double
    )

    fun probeMedia(context: Context, uri: Uri): MediaProbe {
        val name = resolveDisplayName(context, uri)
        val durationMs = MediaMetadataRetriever().use { retriever ->
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                } ?: retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
        return MediaProbe(
            displayName = name,
            durationSeconds = max(0.0, durationMs / 1000.0)
        )
    }

    fun estimate(
        durationSeconds: Double,
        mode: LanguageMode,
        diarizeEnabled: Boolean,
        includeDiarizeInEstimate: Boolean
    ): CostEstimate {
        val billingMode = mode.billingBaseMode()
        val base = when (billingMode) {
            LanguageMode.MULTI -> RATE_MULTI_PER_MIN
            LanguageMode.AUTO, LanguageMode.FIXED -> RATE_AUTO_PER_MIN
        }
        val withDiarize = includeDiarizeInEstimate && diarizeEnabled
        val rate = base + if (withDiarize) RATE_DIARIZE_PER_MIN else 0.0
        val cost = (durationSeconds / 60.0) * rate
        return CostEstimate(
            durationSeconds = durationSeconds,
            mode = mode,
            includeDiarize = withDiarize,
            ratePerMinute = rate,
            estimatedUsd = cost
        )
    }

    fun actualCost(
        durationSeconds: Double,
        mode: LanguageMode,
        diarizeUsed: Boolean
    ): Double {
        val billingMode = mode.billingBaseMode()
        val base = when (billingMode) {
            LanguageMode.MULTI -> RATE_MULTI_PER_MIN
            LanguageMode.AUTO, LanguageMode.FIXED -> RATE_AUTO_PER_MIN
        }
        val rate = base + if (diarizeUsed) RATE_DIARIZE_PER_MIN else 0.0
        return (durationSeconds / 60.0) * rate
    }

    fun checkHardStop(
        context: Context,
        estimatedUsd: Double,
        officialBalance: Double?,
        hasFreePath: Boolean
    ): HardStop {
        if (hasFreePath) return HardStop.Ok

        if (!getAllowPaidFallback(context) && estimatedUsd > 0.0 && officialBalance == null) {
            // Solo Deepgram estimado sin free path — dejar que el router decida;
            // hard-stop clásico si hay balance oficial bajo.
        }

        val reserve = getMinReserve(context)
        val cap = getAppCap(context)
        val appSpent = getAppSpent(context)

        if (officialBalance != null) {
            val remainingAfter = officialBalance - estimatedUsd
            if (remainingAfter < reserve) {
                return HardStop.ReserveBreach(remainingAfter, reserve)
            }
        }

        val projected = appSpent + estimatedUsd
        if (projected > cap) {
            return HardStop.AppCapBreach(projected, cap)
        }
        return HardStop.Ok
    }

    fun isGiantFile(estimatedUsd: Double): Boolean =
        estimatedUsd > GIANT_FILE_THRESHOLD_USD

    fun hoursRemainingAtRate(
        remainingUsd: Double,
        mode: LanguageMode,
        includeDiarize: Boolean
    ): Double {
        val rate = estimate(60.0, mode, includeDiarize, includeDiarize).ratePerMinute
        if (rate <= 0.0 || remainingUsd <= 0.0) return 0.0
        val minutes = remainingUsd / rate
        return minutes / 60.0
    }

    fun formatUsd(value: Double, decimals: Int = 4): String =
        String.format(Locale.US, "$%,.${decimals}f USD", value)

    fun formatDuration(seconds: Double): String {
        val total = seconds.toInt().coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return String.format(Locale.US, "%d:%02d", m, s)
    }

    // --- Settings ---

    fun getInitialCredit(context: Context): Double =
        prefs(context).getFloat(KEY_INITIAL_CREDIT, DEFAULT_INITIAL_CREDIT.toFloat()).toDouble()

    fun setInitialCredit(context: Context, value: Double) {
        prefs(context).edit().putFloat(KEY_INITIAL_CREDIT, value.toFloat()).apply()
    }

    fun getAppCap(context: Context): Double =
        prefs(context).getFloat(KEY_APP_CAP, DEFAULT_APP_CAP.toFloat()).toDouble()

    fun setAppCap(context: Context, value: Double) {
        prefs(context).edit().putFloat(KEY_APP_CAP, value.toFloat()).apply()
    }

    fun getMinReserve(context: Context): Double =
        prefs(context).getFloat(KEY_MIN_RESERVE, DEFAULT_MIN_RESERVE.toFloat()).toDouble()

    fun setMinReserve(context: Context, value: Double) {
        prefs(context).edit().putFloat(KEY_MIN_RESERVE, value.toFloat()).apply()
    }

    fun getIncludeDiarizeCost(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCLUDE_DIARIZE, true)

    fun setIncludeDiarizeCost(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_INCLUDE_DIARIZE, value).apply()
    }

    fun getAutoTranscribeOnShare(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_TRANSCRIBE, false)

    fun setAutoTranscribeOnShare(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_TRANSCRIBE, value).apply()
    }

    fun getLanguageMode(context: Context): LanguageMode {
        val raw = prefs(context).getString(KEY_LANGUAGE_MODE, LanguageMode.AUTO.name)
        return runCatching { LanguageMode.valueOf(raw ?: LanguageMode.AUTO.name) }
            .getOrDefault(LanguageMode.AUTO)
    }

    fun setLanguageMode(context: Context, mode: LanguageMode) {
        prefs(context).edit().putString(KEY_LANGUAGE_MODE, mode.name).apply()
    }

    fun getForcedLanguage(context: Context): String =
        prefs(context).getString(KEY_FORCED_LANGUAGE, "es")?.trim()?.takeIf { it.isNotBlank() } ?: "es"

    fun setForcedLanguage(context: Context, code: String) {
        val normalized = code.trim().lowercase(Locale.US).ifBlank { "es" }
        prefs(context).edit().putString(KEY_FORCED_LANGUAGE, normalized).apply()
    }

    fun getDiarizeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DIARIZE_ENABLED, false)

    fun setDiarizeEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DIARIZE_ENABLED, value).apply()
    }

    fun getProjectId(context: Context): String? =
        prefs(context).getString(KEY_PROJECT_ID, null)?.takeIf { it.isNotBlank() }

    fun setProjectId(context: Context, projectId: String) {
        prefs(context).edit().putString(KEY_PROJECT_ID, projectId).apply()
    }

    fun getAppSpent(context: Context): Double =
        prefs(context).getFloat(KEY_APP_SPENT, 0f).toDouble()

    fun addAppSpent(context: Context, delta: Double) {
        val next = getAppSpent(context) + delta
        prefs(context).edit().putFloat(KEY_APP_SPENT, next.toFloat()).apply()
    }

    fun getAllowPaidFallback(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALLOW_PAID, false)

    fun setAllowPaidFallback(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_PAID, value).apply()
    }

    /**
     * URL de config remota. Por defecto: config/stt-router.json del repo de GitHub
     * con el que se compiló el APK (BuildConfig). Vacía = desactivada (la app usa sus valores).
     */
    fun getRemoteConfigUrl(context: Context): String =
        prefs(context).getString(KEY_REMOTE_CONFIG_URL, "")?.trim().orEmpty()
            .ifBlank { BuildConfig.REMOTE_CONFIG_URL.trim() }

    // --- Interruptor por motor (el usuario elige cuáles usar) ---

    private fun engineKey(provider: SttProvider) = "engine_enabled_${provider.name}"

    /** El respaldo pagado se controla con Modo $0 (getAllowPaidFallback), no con este interruptor. */
    fun isEngineEnabled(context: Context, provider: SttProvider): Boolean {
        if (provider == SttProvider.PAID_FALLBACK) return getAllowPaidFallback(context)
        return prefs(context).getBoolean(engineKey(provider), true)
    }

    fun setEngineEnabled(context: Context, provider: SttProvider, enabled: Boolean) {
        if (provider == SttProvider.PAID_FALLBACK) {
            setAllowPaidFallback(context, enabled)
            return
        }
        prefs(context).edit().putBoolean(engineKey(provider), enabled).apply()
    }

    fun setRemoteConfigUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_REMOTE_CONFIG_URL, url.trim()).apply()
    }

    fun getLastOfferCheckMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_OFFER_CHECK, 0L)

    fun setLastOfferCheckMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_LAST_OFFER_CHECK, ms).apply()
    }

    // --- Groq quota (ventana horaria vía headers; uso acumulado UTC day) ---

    fun getGroqRemainingSeconds(context: Context): Double {
        ensureGroqDay(context)
        return prefs(context)
            .getFloat(KEY_GROQ_REMAINING, GROQ_DEFAULT_LIMIT_SECONDS.toFloat())
            .toDouble()
    }

    fun getGroqLimitSeconds(context: Context): Double {
        ensureGroqDay(context)
        return prefs(context)
            .getFloat(KEY_GROQ_LIMIT, GROQ_DEFAULT_LIMIT_SECONDS.toFloat())
            .toDouble()
            .coerceAtLeast(1.0)
    }

    fun getGroqUsedTodaySeconds(context: Context): Double {
        ensureGroqDay(context)
        return prefs(context).getFloat(KEY_GROQ_USED_TODAY, 0f).toDouble().coerceAtLeast(0.0)
    }

    fun hasGroqLiveQuota(context: Context): Boolean {
        ensureGroqDay(context)
        return prefs(context).getBoolean(KEY_GROQ_LIVE, false)
    }

    fun setGroqRemainingFromHeaders(
        context: Context,
        remaining: Double,
        limit: Double?,
        resetSeconds: Double?
    ) {
        ensureGroqDay(context)
        val ed = prefs(context).edit()
            .putFloat(KEY_GROQ_REMAINING, remaining.toFloat().coerceAtLeast(0f))
            .putBoolean(KEY_GROQ_LIVE, true)
        if (limit != null && limit > 0) {
            ed.putFloat(KEY_GROQ_LIMIT, limit.toFloat())
        }
        ed.apply()
        // resetSeconds informativo (ventana ~1h); el rollover UTC day reinicia «Usado hoy».
        resetSeconds
    }

    fun addGroqUsedSeconds(context: Context, seconds: Double) {
        if (seconds <= 0.0) return
        ensureGroqDay(context)
        val next = getGroqUsedTodaySeconds(context) + seconds
        prefs(context).edit().putFloat(KEY_GROQ_USED_TODAY, next.toFloat()).apply()
    }

    fun consumeGroqSeconds(context: Context, seconds: Double) {
        ensureGroqDay(context)
        val next = (getGroqRemainingSeconds(context) - seconds).coerceAtLeast(0.0)
        prefs(context).edit()
            .putFloat(KEY_GROQ_REMAINING, next.toFloat())
            .apply()
        addGroqUsedSeconds(context, seconds)
    }

    fun formatGroqUsedToday(seconds: Double): String {
        val total = seconds.toInt().coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return if (s == 0) "${m}m" else "${m}m ${s}s"
    }

    fun formatGroqQuotaDetail(
        remainingSeconds: Double,
        limitSeconds: Double,
        usedTodaySeconds: Double,
        hasLiveQuota: Boolean
    ): String {
        val usedLabel = formatGroqUsedToday(usedTodaySeconds)
        if (!hasLiveQuota && usedTodaySeconds <= 0.0) {
            return "Listo · Usado hoy: $usedLabel"
        }
        val remMin = (remainingSeconds / 60.0).toInt().coerceAtLeast(0)
        val limMin = (limitSeconds / 60.0).toInt().coerceAtLeast(1)
        val window = when {
            limitSeconds <= 8_000.0 -> "ventana 1h"
            limitSeconds <= 30_000.0 -> "ventana día"
            else -> "ventana"
        }
        return "${remMin}m / ${limMin}m disp. ($window) · Usado hoy: $usedLabel"
    }

    fun groqQuotaProgress(remainingSeconds: Double, limitSeconds: Double, hasLiveQuota: Boolean): Float {
        if (!hasLiveQuota) return 1f
        val lim = limitSeconds.coerceAtLeast(1.0)
        return (remainingSeconds / lim).toFloat().coerceIn(0f, 1f)
    }

    private fun ensureGroqDay(context: Context) {
        val day = utcDayKey()
        val p = prefs(context)
        if (p.getString(KEY_GROQ_DAY, null) == day) return
        p.edit()
            .putString(KEY_GROQ_DAY, day)
            .putFloat(KEY_GROQ_REMAINING, GROQ_DEFAULT_LIMIT_SECONDS.toFloat())
            .putFloat(KEY_GROQ_LIMIT, GROQ_DEFAULT_LIMIT_SECONDS.toFloat())
            .putFloat(KEY_GROQ_USED_TODAY, 0f)
            .putBoolean(KEY_GROQ_LIVE, false)
            .apply()
    }

    private fun utcDayKey(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // --- Inworld pool (400 min On-Demand, bolsa única; no reset mensual) ---

    fun getInworldUsedSeconds(context: Context): Double =
        prefs(context).getFloat(KEY_INWORLD_USED, 0f).toDouble()

    fun setInworldUsedSeconds(context: Context, seconds: Double) {
        prefs(context).edit()
            .putFloat(KEY_INWORLD_USED, seconds.toFloat().coerceAtLeast(0f))
            .apply()
    }

    fun getInworldRemainingSeconds(context: Context): Double =
        (INWORLD_POOL_SECONDS - getInworldUsedSeconds(context)).coerceAtLeast(0.0)

    fun consumeInworldSeconds(context: Context, seconds: Double) {
        setInworldUsedSeconds(context, getInworldUsedSeconds(context) + seconds)
    }

    // --- AssemblyAI pool ---

    fun getAssemblyUsedSeconds(context: Context): Double =
        prefs(context).getFloat(KEY_ASSEMBLY_USED, 0f).toDouble()

    fun setAssemblyUsedSeconds(context: Context, seconds: Double) {
        prefs(context).edit()
            .putFloat(KEY_ASSEMBLY_USED, seconds.toFloat().coerceAtLeast(0f))
            .apply()
    }

    fun getAssemblyRemainingSeconds(context: Context): Double =
        (ASSEMBLY_POOL_SECONDS - getAssemblyUsedSeconds(context)).coerceAtLeast(0.0)

    /** Saldo estimado en USD (lo que el usuario ve en su panel de AssemblyAI). */
    fun getAssemblyRemainingUsdEstimate(context: Context): Double =
        (getAssemblyRemainingSeconds(context) / 3600.0) * ASSEMBLY_EST_USD_PER_HOUR

    /** Corrige el contador con el saldo real que el usuario ve en su panel. */
    fun setAssemblyRemainingUsd(context: Context, remainingUsd: Double) {
        val usd = remainingUsd.coerceIn(0.0, ASSEMBLY_POOL_USD)
        val remainingSeconds = (usd / ASSEMBLY_EST_USD_PER_HOUR) * 3600.0
        setAssemblyUsedSeconds(context, ASSEMBLY_POOL_SECONDS - remainingSeconds)
    }

    fun consumeAssemblySeconds(context: Context, seconds: Double) {
        setAssemblyUsedSeconds(context, getAssemblyUsedSeconds(context) + seconds)
    }

    // --- Circuit breaker state ---

    fun markPromoEnded(provider: SttProvider) {
        promoEnded.add(provider)
    }

    fun isPromoEnded(provider: SttProvider): Boolean = promoEnded.contains(provider)

    fun clearPromoEnded(provider: SttProvider) {
        promoEnded.remove(provider)
    }

    fun markCooldown(provider: SttProvider, retryAfterSeconds: Long?) {
        val wait = (retryAfterSeconds ?: 60L).coerceAtLeast(5L)
        cooldownUntilMs[provider] = System.currentTimeMillis() + wait * 1000L
    }

    fun isInCooldown(provider: SttProvider): Boolean {
        val until = cooldownUntilMs[provider] ?: return false
        if (System.currentTimeMillis() >= until) {
            cooldownUntilMs.remove(provider)
            return false
        }
        return true
    }

    fun cooldownRemainingSeconds(provider: SttProvider): Long {
        val until = cooldownUntilMs[provider] ?: return 0L
        return ((until - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
    }

    fun recordLocalUsage(context: Context, durationSeconds: Double) {
        ensureCurrentMonth(context)
        val p = prefs(context)
        p.edit()
            .putInt(KEY_MONTH_TRANSCRIPTIONS, p.getInt(KEY_MONTH_TRANSCRIPTIONS, 0) + 1)
            .putFloat(
                KEY_MONTH_SECONDS,
                p.getFloat(KEY_MONTH_SECONDS, 0f) + durationSeconds.toFloat()
            )
            .putInt(KEY_SESSION_TRANSCRIPTIONS, p.getInt(KEY_SESSION_TRANSCRIPTIONS, 0) + 1)
            .putFloat(
                KEY_SESSION_SECONDS,
                p.getFloat(KEY_SESSION_SECONDS, 0f) + durationSeconds.toFloat()
            )
            .apply()
    }

    fun getLocalUsage(context: Context): LocalUsage {
        ensureCurrentMonth(context)
        val p = prefs(context)
        return LocalUsage(
            monthTranscriptions = p.getInt(KEY_MONTH_TRANSCRIPTIONS, 0),
            monthMinutes = p.getFloat(KEY_MONTH_SECONDS, 0f).toDouble() / 60.0,
            sessionTranscriptions = p.getInt(KEY_SESSION_TRANSCRIPTIONS, 0),
            sessionMinutes = p.getFloat(KEY_SESSION_SECONDS, 0f).toDouble() / 60.0
        )
    }

    private fun ensureCurrentMonth(context: Context) {
        val current = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val p = prefs(context)
        if (p.getString(KEY_MONTH_KEY, null) == current) return
        p.edit()
            .putString(KEY_MONTH_KEY, current)
            .putInt(KEY_MONTH_TRANSCRIPTIONS, 0)
            .putFloat(KEY_MONTH_SECONDS, 0f)
            .apply()
    }

    fun officialSpent(context: Context, officialBalance: Double?): Double? {
        if (officialBalance == null) return null
        return getInitialCredit(context) - officialBalance
    }

    fun deltaOfficialVsApp(context: Context, officialBalance: Double?): Double? {
        val off = officialSpent(context, officialBalance) ?: return null
        return off - getAppSpent(context)
    }

    // --- History ---

    fun appendHistory(context: Context, entry: HistoryEntry) {
        val list = loadHistory(context).toMutableList()
        list.add(0, entry)
        saveHistory(context, list.sortedByDescending { it.timestampMs })
    }

    fun deleteHistory(context: Context, id: String) {
        val list = loadHistory(context).filterNot { it.id == id }
        val removed = loadHistory(context).firstOrNull { it.id == id }
        removed?.audioPath?.let { path ->
            runCatching { File(path).delete() }
        }
        saveHistory(context, list)
    }

    fun loadHistory(context: Context): List<HistoryEntry> {
        val file = historyFile(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        HistoryEntry(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            timestampMs = o.optLong("timestampMs"),
                            fileName = o.optString("fileName"),
                            durationSeconds = o.optDouble("durationSeconds"),
                            mode = o.optString("mode"),
                            diarize = o.optBoolean("diarize"),
                            appCostUsd = o.optDouble("appCostUsd"),
                            balanceBefore = o.optDoubleOrNull("balanceBefore"),
                            balanceAfter = o.optDoubleOrNull("balanceAfter"),
                            officialDeltaUsd = o.optDoubleOrNull("officialDeltaUsd"),
                            transcript = o.optString("transcript"),
                            audioPath = o.optString("audioPath").takeIf { it.isNotBlank() },
                            provider = o.optString("provider").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }.sortedByDescending { it.timestampMs }
        }.getOrElse { emptyList() }
    }

    fun newHistoryEntry(
        fileName: String,
        durationSeconds: Double,
        mode: LanguageMode,
        diarize: Boolean,
        appCostUsd: Double,
        balanceBefore: Double?,
        balanceAfter: Double?,
        transcript: String,
        audioPath: String? = null,
        provider: String? = null
    ): HistoryEntry {
        val delta = if (balanceBefore != null && balanceAfter != null) {
            balanceBefore - balanceAfter
        } else {
            null
        }
        return HistoryEntry(
            id = UUID.randomUUID().toString(),
            timestampMs = System.currentTimeMillis(),
            fileName = fileName,
            durationSeconds = durationSeconds,
            mode = mode.name,
            diarize = diarize,
            appCostUsd = appCostUsd,
            balanceBefore = balanceBefore,
            balanceAfter = balanceAfter,
            officialDeltaUsd = delta,
            transcript = transcript,
            audioPath = audioPath,
            provider = provider
        )
    }

    private fun saveHistory(context: Context, entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("timestampMs", e.timestampMs)
                    put("fileName", e.fileName)
                    put("durationSeconds", e.durationSeconds)
                    put("mode", e.mode)
                    put("diarize", e.diarize)
                    put("appCostUsd", e.appCostUsd)
                    if (e.balanceBefore != null) put("balanceBefore", e.balanceBefore)
                    else put("balanceBefore", JSONObject.NULL)
                    if (e.balanceAfter != null) put("balanceAfter", e.balanceAfter)
                    else put("balanceAfter", JSONObject.NULL)
                    if (e.officialDeltaUsd != null) put("officialDeltaUsd", e.officialDeltaUsd)
                    else put("officialDeltaUsd", JSONObject.NULL)
                    put("transcript", e.transcript)
                    if (e.audioPath != null) put("audioPath", e.audioPath)
                    else put("audioPath", JSONObject.NULL)
                    if (e.provider != null) put("provider", e.provider)
                    else put("provider", JSONObject.NULL)
                }
            )
        }
        val target = historyFile(context)
        val tmp = File(context.filesDir, "$HISTORY_FILE.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun historyFile(context: Context): File =
        File(context.filesDir, HISTORY_FILE)

    private fun prefs(context: Context): android.content.SharedPreferences {
        val app = context.applicationContext
        val modern = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (modern.all.isNotEmpty()) return modern
        // Migración one-shot desde prefs legacy (nombre de archivo en disco; no renombrar).
        val legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return modern
        modern.edit().apply {
            legacy.all.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Float -> putFloat(k, v)
                    is Boolean -> putBoolean(k, v)
                }
            }
            apply()
        }
        return modern
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                if (!name.isNullOrBlank()) return name
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "archivo"
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }

    private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T {
        try {
            return block(this)
        } finally {
            runCatching { release() }
        }
    }
}
