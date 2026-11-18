package com.blackeye.klipnote

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
 * Catálogo único de proveedores. Si una oferta cambia, se edita aquí
 * o (sin publicar APK) en config/stt-router.json → "offers".
 */
object ProviderCatalog {

    val all: List<ProviderInfo> = listOf(
        ProviderInfo(
            provider = SttProvider.GROQ_FREE,
            name = "Groq (gratis)",
            offer = "Gratis siempre, con límite: ~2 h de audio por hora y ~8 h por día. Archivos de hasta 25 MB.",
            keyUrl = "https://console.groq.com/keys",
            steps = listOf(
                "Entra a console.groq.com y crea tu cuenta (correo o Google).",
                "Ve a «API Keys» y pulsa «Create API Key».",
                "Copia la llave (empieza con gsk_) y pégala aquí."
            ),
            meterKind = MeterKind.REAL,
            meterNote = "Dato real: Groq lo informa en cada transcripción. Se renueva solo cada hora/día.",
            isFree = true
        ),
        ProviderInfo(
            provider = SttProvider.INWORLD,
            name = "Inworld (gratis)",
            offer = "Hasta 400 min de STT gratis una vez (plan On-Demand, $0). Archivos de hasta ~16 MB.",
            keyUrl = "https://platform.inworld.ai/",
            steps = listOf(
                "Entra a platform.inworld.ai y crea tu cuenta.",
                "Ve a Manage → API Keys → Create Inworld API key (tipo Standard).",
                "Copia la llave con «Copy key» (ya viene en Basic/Base64) y pégala aquí."
            ),
            meterKind = MeterKind.ESTIMATED,
            meterNote = "Estimado: bolsa única de ~400 min; la app suma lo que transcribes. Corrígelo si tu panel de Inworld dice otra cosa.",
            isFree = true
        ),
        ProviderInfo(
            provider = SttProvider.ASSEMBLY_AI,
            name = "AssemblyAI (crédito de regalo)",
            offer = "Crédito de regalo una sola vez ($50 USD al momento de publicar), sin tarjeta y sin vencimiento.",
            keyUrl = "https://www.assemblyai.com/app",
            steps = listOf(
                "Entra a assemblyai.com y crea tu cuenta (no pongas tarjeta).",
                "En el panel principal verás tu API Key.",
                "Cópiala y pégala aquí."
            ),
            meterKind = MeterKind.ESTIMATED,
            meterNote = "Estimado: AssemblyAI no informa el saldo a la app. Escribe abajo el saldo que ves en tu panel para corregirlo.",
            isFree = true
        ),
        ProviderInfo(
            provider = SttProvider.DEEPGRAM,
            name = "Deepgram (crédito de regalo)",
            offer = "Crédito de regalo una sola vez ($200 USD al momento de publicar), sin tarjeta.",
            keyUrl = "https://console.deepgram.com/",
            steps = listOf(
                "Entra a console.deepgram.com y crea tu cuenta (no pongas tarjeta).",
                "Ve a «API Keys» → «Create a New API Key».",
                "Para ver el saldo real, dale rol «Admin» o «Owner» a la llave.",
                "Cópiala y pégala aquí. El Project ID se vincula solo."
            ),
            meterKind = MeterKind.REAL,
            meterNote = "Dato real si la llave tiene permiso de facturación; si no, la app estima con su propio contador.",
            isFree = true
        ),
        ProviderInfo(
            provider = SttProvider.PAID_FALLBACK,
            name = "Respaldo de pago (opcional)",
            offer = "Aquí SÍ pagas. Solo se usa si apagas «Modo \$0» y todos los gratuitos fallaron.",
            keyUrl = "https://console.groq.com/settings/billing",
            steps = listOf(
                "Opción A · Groq de pago: activa facturación en console.groq.com y crea una llave.",
                "Opción B · Microsoft (Azure): crea un recurso de voz en Azure y copia su llave y endpoint.",
                "Pega la llave aquí y elige el motor."
            ),
            meterKind = MeterKind.NONE,
            meterNote = "Sin medidor: revisa tu gasto en el panel del proveedor.",
            isFree = false
        )
    )

    fun info(provider: SttProvider): ProviderInfo = all.first { it.provider == provider }

    /** Oferta efectiva: la de config remota (si existe) o la del catálogo. */
    fun offerText(provider: SttProvider): String =
        RemoteRouterConfig.currentOrDefault().offers[provider]?.takeIf { it.isNotBlank() }
            ?: info(provider).offer

    const val ZERO_COST_WARNING =
        "Para gastar \$0: NO registres tarjeta en Groq, Inworld, AssemblyAI ni Deepgram. " +
            "Si pones tarjeta, cuando se acabe el regalo el proveedor te cobrará y la app no puede impedirlo."
}

fun MeterKind.badge(): String = when (this) {
    MeterKind.REAL -> "Real"
    MeterKind.ESTIMATED -> "Estimado"
    MeterKind.NONE -> "Sin medidor"
}
