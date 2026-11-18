package com.blackeye.klipnote

import androidx.annotation.Keep

enum class LanguageMode {
    AUTO,
    MULTI
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
    val hopLog: List<String> = emptyList()
)

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
