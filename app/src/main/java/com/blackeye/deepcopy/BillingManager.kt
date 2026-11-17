package com.blackeye.deepcopy

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max

/**
 * Auditor de gasto Nova-3 Pre-Recorded.
 * Tarifas por minuto; cobro exacto por segundo: (durationSeconds / 60) * ratePerMinute.
 */
object BillingManager {

    private const val PREFS = "deepcopy_billing_prefs"
    private const val KEY_INITIAL_CREDIT = "initial_credit_usd"
    private const val KEY_APP_CAP = "app_cap_usd"
    private const val KEY_MIN_RESERVE = "min_reserve_usd"
    private const val KEY_INCLUDE_DIARIZE = "include_diarize_cost"
    private const val KEY_AUTO_TRANSCRIBE = "auto_transcribe_on_share"
    private const val KEY_PROJECT_ID = "deepgram_project_id"
    private const val KEY_APP_SPENT = "app_spent_usd"
    private const val HISTORY_FILE = "billing_history.json"

    const val RATE_AUTO_PER_MIN = 0.0043
    const val RATE_MULTI_PER_MIN = 0.0052
    const val RATE_DIARIZE_PER_MIN = 0.0020
    const val DEFAULT_INITIAL_CREDIT = 200.0
    const val DEFAULT_APP_CAP = 195.0
    const val DEFAULT_MIN_RESERVE = 5.0
    const val GIANT_FILE_THRESHOLD_USD = 0.50

    data class MediaProbe(
        val displayName: String,
        val durationSeconds: Double
    )

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
    }

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
        val transcript: String
    ) {
        val formattedWhen: String
            get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(timestampMs))
    }

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
        val base = when (mode) {
            LanguageMode.AUTO -> RATE_AUTO_PER_MIN
            LanguageMode.MULTI -> RATE_MULTI_PER_MIN
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

    /** Costo exacto post-transcripción (usa duración real de metadata.duration). */
    fun actualCost(
        durationSeconds: Double,
        mode: LanguageMode,
        diarizeUsed: Boolean
    ): Double {
        val base = when (mode) {
            LanguageMode.AUTO -> RATE_AUTO_PER_MIN
            LanguageMode.MULTI -> RATE_MULTI_PER_MIN
        }
        val rate = base + if (diarizeUsed) RATE_DIARIZE_PER_MIN else 0.0
        return (durationSeconds / 60.0) * rate
    }

    fun checkHardStop(
        context: Context,
        estimatedUsd: Double,
        officialBalance: Double?
    ): HardStop {
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
                            transcript = o.optString("transcript")
                        )
                    )
                }
            }
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
        transcript: String
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
            transcript = transcript
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
                }
            )
        }
        historyFile(context).writeText(arr.toString())
    }

    private fun historyFile(context: Context): File =
        File(context.filesDir, HISTORY_FILE)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
