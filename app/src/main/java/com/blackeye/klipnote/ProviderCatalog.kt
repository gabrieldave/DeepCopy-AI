package com.blackeye.klipnote

import android.content.Context
import androidx.annotation.Keep

/**
 * De dónde sale el número que muestra cada barra de consumo.
 * - REAL: lo informa el proveedor (cabeceras o endpoint de saldo).
 * - ESTIMATED: lo calcula la app sumando lo que transcribe; el usuario puede corregirlo.
 * - NONE: no hay medidor (respaldo pagado).
 */
@Keep
enum class MeterKind { REAL, ESTIMATED, NONE }

@Keep
data class ProviderInfo(
    val provider: SttProvider,
    val name: String,
    /** Texto corto de la oferta. Puede sobrescribirse desde config/stt-router.json ("offers"). */
    val offer: String,
    /** Página donde el usuario crea su cuenta / copia su llave. */
    val keyUrl: String,
    /** Pasos cortos para conseguir la llave (para gente que no programa). */
    val steps: List<String>,
    val meterKind: MeterKind,
    /** Explica de dónde sale el número de la barra. */
    val meterNote: String,
    val isFree: Boolean
)

/**
 * Catálogo único de proveedores. Textos vía recursos (EN default / values-es / …).
 */
object ProviderCatalog {

    fun all(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            provider = SttProvider.GROQ_FREE,
            name = context.getString(R.string.provider_groq_free_name),
            offer = context.getString(R.string.provider_groq_free_offer),
            keyUrl = "https://console.groq.com/keys",
            steps = listOf(
                context.getString(R.string.provider_groq_free_step_1),
                context.getString(R.string.provider_groq_free_step_2),
                context.getString(R.string.provider_groq_free_step_3)
            ),
            meterKind = MeterKind.REAL,
            meterNote = context.getString(R.string.provider_groq_free_meter),
            isFree = true
        ),
        ProviderInfo(
            provider = SttProvider.INWORLD,
            name = context.getString(R.string.provider_inworld_name),
            offer = context.getString(R.string.provider_inworld_offer),
            keyUrl = "https://platform.inworld.ai/",
            steps = listOf(
                context.getString(R.string.provider_inworld_step_1),
                context.getString(R.string.provider_inworld_step_2),
                context.getString(R.string.provider_inworld_step_3)
            ),
            meterKind = MeterKind.ESTIMATED,
            meterNote = context.getString(R.string.provider_inworld_meter),
            isFree = true
        ),
        ProviderInfo(
            provider = SttProvider.ASSEMBLY_AI,
            name = context.getString(R.string.provider_assembly_name),
            offer = context.getString(R.string.provider_assembly_offer),
            keyUrl = "https://www.assemblyai.com/app",
            steps = listOf(
                context.getString(R.string.provider_assembly_step_1),
                context.getString(R.string.provider_assembly_step_2),
                context.getString(R.string.provider_assembly_step_3)
            ),
            meterKind = MeterKind.ESTIMATED,
            meterNote = context.getString(R.string.provider_assembly_meter),
            isFree = true
        ),
        ProviderInfo(
            provider = SttProvider.DEEPGRAM,
            name = context.getString(R.string.provider_deepgram_name),
            offer = context.getString(R.string.provider_deepgram_offer),
            keyUrl = "https://console.deepgram.com/",
            steps = listOf(
                context.getString(R.string.provider_deepgram_step_1),
                context.getString(R.string.provider_deepgram_step_2),
                context.getString(R.string.provider_deepgram_step_3),
                context.getString(R.string.provider_deepgram_step_4)
            ),
            meterKind = MeterKind.REAL,
            meterNote = context.getString(R.string.provider_deepgram_meter),
            isFree = true
        ),
        ProviderInfo(
            provider = SttProvider.PAID_FALLBACK,
            name = context.getString(R.string.provider_paid_name),
            offer = context.getString(R.string.provider_paid_offer),
            keyUrl = "https://console.groq.com/settings/billing",
            steps = listOf(
                context.getString(R.string.provider_paid_step_1),
                context.getString(R.string.provider_paid_step_2),
                context.getString(R.string.provider_paid_step_3)
            ),
            meterKind = MeterKind.NONE,
            meterNote = context.getString(R.string.provider_paid_meter),
            isFree = false
        )
    )

    fun info(context: Context, provider: SttProvider): ProviderInfo =
        all(context).first { it.provider == provider }

    /** Oferta efectiva: la de config remota (si existe) o la del catálogo. */
    fun offerText(context: Context, provider: SttProvider): String =
        RemoteRouterConfig.currentOrDefault().offers[provider]?.takeIf { it.isNotBlank() }
            ?: info(context, provider).offer

    fun zeroCostWarning(context: Context): String =
        context.getString(R.string.zero_cost_warning_long)
}

fun MeterKind.badge(context: Context): String = when (this) {
    MeterKind.REAL -> context.getString(R.string.meter_badge_real)
    MeterKind.ESTIMATED -> context.getString(R.string.meter_badge_estimated)
    MeterKind.NONE -> context.getString(R.string.meter_badge_none)
}
