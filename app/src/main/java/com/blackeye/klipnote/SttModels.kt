package com.blackeye.klipnote

import androidx.annotation.Keep

enum class LanguageMode {
    /** Detecta el idioma del audio (APIs: detect_language / omit language). */
    AUTO,
    /** Code-switch / Spanglish (Deepgram language=multi). */
    MULTI,
    /** Fuerza un idioma de reconocimiento concreto. */
    FIXED
}

data class SttLanguageOption(
    val code: String,
    val label: String
)

/** Idiomas comunes para fijar reconocimiento / guion. */
val COMMON_STT_LANGUAGES: List<SttLanguageOption> = listOf(
    SttLanguageOption("es", "ES"),
    SttLanguageOption("en", "EN"),
    SttLanguageOption("pt", "PT"),
    SttLanguageOption("fr", "FR"),
    SttLanguageOption("de", "DE"),
    SttLanguageOption("it", "IT"),
    SttLanguageOption("ja", "JA"),
    SttLanguageOption("ko", "KO"),
    SttLanguageOption("zh", "ZH"),
    SttLanguageOption("multi", "Multi")
)

/** Código para APIs: null = auto-detect; "multi" = code-switch; ISO = forzado. */
fun LanguageMode.toApiLanguageCode(forcedCode: String?): String? = when (this) {
    LanguageMode.AUTO -> null
    LanguageMode.MULTI -> "multi"
    LanguageMode.FIXED -> forcedCode?.trim()?.takeIf { it.isNotBlank() } ?: "es"
}

/** MULTI usa tarifa multi; AUTO y FIXED usan tarifa auto. */
fun LanguageMode.billingBaseMode(): LanguageMode = when (this) {
    LanguageMode.MULTI -> LanguageMode.MULTI
    LanguageMode.AUTO, LanguageMode.FIXED -> LanguageMode.AUTO
}

fun LanguageMode.shortUiLabel(forcedCode: String? = null): String = when (this) {
    LanguageMode.AUTO -> "Detectar"
    LanguageMode.MULTI -> "ES+EN"
    LanguageMode.FIXED -> forcedCode?.takeIf { it.isNotBlank() }?.uppercase() ?: "Fijo"
}

@Keep
sealed class RoutingSelection {
    @Keep
    data object Auto : RoutingSelection()

    @Keep
    data class Manual(val provider: SttProvider) : RoutingSelection()
}

@Keep
data class TranscriptionResult(
    val transcript: String,
    val durationSeconds: Double?,
    val detectedLanguage: String?,
    val wordCount: Int,
    val provider: SttProvider = SttProvider.DEEPGRAM,
    val providerLabel: String = "Deepgram",
    val costUsd: Double = 0.0,
    val elapsedMs: Long = 0L,
    val hopLog: List<String> = emptyList(),
    val routingSelection: RoutingSelection = RoutingSelection.Auto,
    val levelIndex: Int = 0
) {
    fun resultBadge(copiedHint: Boolean = false): String {
        val secs = elapsedMs / 1000.0
        val prefix = when (routingSelection) {
            is RoutingSelection.Auto -> "⚡"
            is RoutingSelection.Manual -> "🧪"
        }
        val lang = detectedLanguage?.trim()?.takeIf { it.isNotBlank() }
        return buildString {
            append("$prefix $providerLabel")
            if (lang != null) append(" • $lang")
            append(" • ${String.format("%.1fs", secs)}")
            if (copiedHint) append(" • Copiado")
            else if (costUsd > 0.0) append(" • ${BillingManager.formatUsd(costUsd, 2)}")
        }
    }
}

@Keep
enum class SttProvider {
    GROQ_FREE,
    INWORLD,
    ASSEMBLY_AI,
    DEEPGRAM,
    PAID_FALLBACK
}

@Keep
enum class PaidFallbackEngine {
    MICROSOFT_MAI,
    GROQ_PAID
}

@Keep
enum class ProviderHealth {
    ACTIVE_FREE,
    /** Llave guardada en ApiKeyStore; ping de red aún no corrido o pendiente. */
    KEY_READY,
    PAUSED_LIMIT,
    OFFER_ENDED,
    NO_KEY,
    COOLDOWN,
    UNKNOWN
}

@Keep
enum class SttFailureKind {
    PROMO_ENDED,
    COOLDOWN,
    UNAUTHORIZED,
    MODEL_GONE,
    SIZE_SKIP,
    NO_BALANCE,
    NETWORK,
    OTHER
}

class SttProviderException(
    val provider: SttProvider,
    val httpCode: Int?,
    val kind: SttFailureKind,
    message: String,
    val retryAfterSeconds: Long? = null
) : Exception(message) {
    companion object {
        fun fromHttp(
            provider: SttProvider,
            code: Int,
            body: String,
            retryAfter: Long? = null
        ): SttProviderException {
            val kind = when (code) {
                402, 403 -> SttFailureKind.PROMO_ENDED
                429 -> SttFailureKind.COOLDOWN
                401 -> SttFailureKind.UNAUTHORIZED
                404, 410 -> SttFailureKind.MODEL_GONE
                else -> SttFailureKind.OTHER
            }
            val label = when (kind) {
                SttFailureKind.PROMO_ENDED -> "PROMO_TERMINADA (HTTP $code)"
                SttFailureKind.COOLDOWN -> "COOLDOWN TEMPORAL (HTTP 429)"
                SttFailureKind.UNAUTHORIZED -> "KEY INVÁLIDA (HTTP 401)"
                SttFailureKind.MODEL_GONE -> "MODELO RETIRADO (HTTP $code)"
                else -> "HTTP $code"
            }
            val detail = body.take(200).ifBlank { label }
            return SttProviderException(
                provider = provider,
                httpCode = code,
                kind = kind,
                message = "$label · $detail",
                retryAfterSeconds = retryAfter
            )
        }
    }
}

@Keep
data class ProviderStatus(
    val provider: SttProvider,
    val health: ProviderHealth,
    val label: String,
    val detail: String,
    val remainingSeconds: Double? = null,
    val remainingUsd: Double? = null
)

fun SttProvider.displayName(): String = when (this) {
    SttProvider.GROQ_FREE -> "Groq Free"
    SttProvider.INWORLD -> "Inworld Free"
    SttProvider.ASSEMBLY_AI -> "AssemblyAI"
    SttProvider.DEEPGRAM -> "Deepgram"
    SttProvider.PAID_FALLBACK -> "Respaldo Pagado"
}

fun SttProvider.levelIndex(): Int = when (this) {
    SttProvider.GROQ_FREE -> 1
    SttProvider.INWORLD -> 2
    SttProvider.ASSEMBLY_AI -> 3
    SttProvider.DEEPGRAM -> 4
    SttProvider.PAID_FALLBACK -> 5
}

fun SttProvider.chipLabel(): String = when (this) {
    SttProvider.GROQ_FREE -> "1 · Groq"
    SttProvider.INWORLD -> "2 · Inworld"
    SttProvider.ASSEMBLY_AI -> "3 · AssemblyAI"
    SttProvider.DEEPGRAM -> "4 · Deepgram"
    SttProvider.PAID_FALLBACK -> "5 · Pagado"
}

fun SttProvider.shortName(): String = when (this) {
    SttProvider.GROQ_FREE -> "Groq"
    SttProvider.INWORLD -> "Inworld"
    SttProvider.ASSEMBLY_AI -> "AssemblyAI"
    SttProvider.DEEPGRAM -> "Deepgram"
    SttProvider.PAID_FALLBACK -> "Pagado"
}

/** Pill compacta del TopBar Capturar: Auto verde / Prueba ámbar. */
fun activeMotorPillText(
    selection: RoutingSelection,
    autoPrimary: SttProvider?,
    hasKeys: Boolean = true
): String = when {
    !hasKeys -> "Sin llaves"
    selection is RoutingSelection.Auto ->
        "Usando: ${autoPrimary?.shortName() ?: "Groq"}"
    selection is RoutingSelection.Manual ->
        "Probando: ${selection.provider.shortName()}"
    else -> "Sin llaves"
}

/** Falla exclusiva de Modo Prueba: no hay failover silencioso. */
class ManualRouteException(
    val provider: SttProvider,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
