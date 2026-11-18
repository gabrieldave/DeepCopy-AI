package com.blackeye.klipnote

import android.content.Context
import androidx.annotation.Keep
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Escudo Anti-Gladia: verifica ofertas vivas con micro-WAV silencioso (~0.3s).
 */
object OfferHealthMonitor {

    private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L

    @Keep
    data class Report(
        val statuses: List<ProviderStatus>,
        val checkedAtMs: Long
    )

    fun shouldAutoCheck(context: Context): Boolean {
        val last = BillingManager.getLastOfferCheckMs(context)
        return last == 0L || System.currentTimeMillis() - last > SEVEN_DAYS_MS
    }

    /**
     * Estado UI inmediato desde ApiKeyStore (sin red).
     * Nunca marca SIN KEY si hay llave cifrada; usa KEY_READY hasta el ping.
     */
    fun seedFromStore(context: Context): List<ProviderStatus> = listOf(
        seedSlot(
            provider = SttProvider.GROQ_FREE,
            label = "Groq Free",
            hasKey = ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.GROQ),
            mask = ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.GROQ),
            remainingSeconds = BillingManager.getGroqRemainingSeconds(context)
        ),
        seedSlot(
            provider = SttProvider.INWORLD,
            label = "Inworld Free",
            hasKey = ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.INWORLD),
            mask = ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.INWORLD),
            remainingSeconds = BillingManager.getInworldRemainingSeconds(context)
        ),
        seedSlot(
            provider = SttProvider.ASSEMBLY_AI,
            label = "AssemblyAI",
            hasKey = ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.ASSEMBLY_AI),
            mask = ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.ASSEMBLY_AI),
            remainingSeconds = BillingManager.getAssemblyRemainingSeconds(context)
        ),
        seedSlot(
            provider = SttProvider.DEEPGRAM,
            label = "Deepgram",
            hasKey = ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.DEEPGRAM),
            mask = ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.DEEPGRAM),
            remainingSeconds = null
        ),
        seedSlot(
            provider = SttProvider.PAID_FALLBACK,
            label = "Respaldo Pagado",
            hasKey = ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.PAID_FALLBACK),
            mask = ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.PAID_FALLBACK),
            remainingSeconds = null
        )
    )

    /**
     * Combina seed local con ping de red: si el ping dice NO_KEY pero hay llave, fuerza KEY_READY.
     */
    fun reconcileWithStore(context: Context, pinged: List<ProviderStatus>): List<ProviderStatus> {
        val seed = seedFromStore(context).associateBy { it.provider }
        return SttProvider.entries.map { provider ->
            val local = seed[provider]
            val remote = pinged.firstOrNull { it.provider == provider }
            when {
                local == null -> remote ?: ProviderStatus(
                    provider = provider,
                    health = ProviderHealth.NO_KEY,
                    label = provider.displayName(),
                    detail = "SIN KEY"
                )
                local.health == ProviderHealth.NO_KEY -> local
                remote == null || remote.health == ProviderHealth.NO_KEY -> local
                else -> remote.copy(
                    detail = remote.detail.ifBlank { local.detail }
                )
            }
        }
    }

    private fun seedSlot(
        provider: SttProvider,
        label: String,
        hasKey: Boolean,
        mask: String?,
        remainingSeconds: Double?
    ): ProviderStatus {
        if (!hasKey) {
            return ProviderStatus(
                provider = provider,
                health = ProviderHealth.NO_KEY,
                label = label,
                detail = "SIN KEY",
                remainingSeconds = remainingSeconds
            )
        }
        return ProviderStatus(
            provider = provider,
            health = ProviderHealth.KEY_READY,
            label = label,
            detail = "KEY LISTA · Key ${mask ?: "••••"}",
            remainingSeconds = remainingSeconds
        )
    }

    fun verifyAll(context: Context): Report {
        val probe = writeSilentWav(context)
        return try {
            val statuses = listOf(
                checkGroq(context, probe),
                checkInworld(context, probe),
                checkAssembly(context),
                checkDeepgram(context),
                checkPaid(context)
            )
            BillingManager.setLastOfferCheckMs(context, System.currentTimeMillis())
            // Si un motor responde OK, limpia marca de promo terminada.
            val reconciled = reconcileWithStore(context, statuses)
            reconciled.forEach { st ->
                if (st.health == ProviderHealth.ACTIVE_FREE ||
                    st.health == ProviderHealth.PAUSED_LIMIT ||
                    st.health == ProviderHealth.KEY_READY
                ) {
                    BillingManager.clearPromoEnded(st.provider)
                }
            }
            Report(statuses = reconciled, checkedAtMs = System.currentTimeMillis())
        } finally {
            runCatching { probe.delete() }
        }
    }

    private fun checkGroq(context: Context, probe: File): ProviderStatus {
        val key = ApiKeyStore.getGroq(context)
        if (key.isBlank()) {
            return ProviderStatus(
                provider = SttProvider.GROQ_FREE,
                health = ProviderHealth.NO_KEY,
                label = "Groq Free",
                detail = "SIN KEY"
            )
        }
        if (BillingManager.isPromoEnded(SttProvider.GROQ_FREE)) {
            // Igual re-probar: la oferta pudo volver.
        }
        return GroqClient.healthCheck(context, key, probe)
    }

    private fun checkInworld(context: Context, probe: File): ProviderStatus {
        val key = ApiKeyStore.getInworld(context)
        if (key.isBlank()) {
            return ProviderStatus(
                provider = SttProvider.INWORLD,
                health = ProviderHealth.NO_KEY,
                label = "Inworld Free",
                detail = "SIN KEY"
            )
        }
        val remaining = BillingManager.getInworldRemainingSeconds(context)
        if (remaining <= 0.0) {
            return ProviderStatus(
                provider = SttProvider.INWORLD,
                health = ProviderHealth.PAUSED_LIMIT,
                label = "Inworld Free",
                detail = "LÍMITE MENSUAL",
                remainingSeconds = 0.0
            )
        }
        return InworldClient.healthCheck(context, key, probe)
    }

    private fun checkAssembly(context: Context): ProviderStatus =
        AssemblyAiClient.healthCheck(context, ApiKeyStore.getAssemblyAi(context))

    private fun checkDeepgram(context: Context): ProviderStatus {
        val key = ApiKeyStore.getDeepgram(context)
        if (key.isBlank()) {
            return ProviderStatus(
                provider = SttProvider.DEEPGRAM,
                health = ProviderHealth.NO_KEY,
                label = "Deepgram",
                detail = "SIN KEY"
            )
        }
        val snap = DeepgramBillingClient.fetchOfficialBalance(context, key)
        return when {
            snap.status == DeepgramBillingClient.BillingScopeStatus.UNAUTHORIZED ->
                ProviderStatus(
                    provider = SttProvider.DEEPGRAM,
                    health = ProviderHealth.OFFER_ENDED,
                    label = "Deepgram",
                    detail = "KEY INVÁLIDA"
                )
            snap.balanceUsd != null && snap.balanceUsd < BillingManager.DEEPGRAM_SAFE_FLOOR_USD ->
                ProviderStatus(
                    provider = SttProvider.DEEPGRAM,
                    health = ProviderHealth.OFFER_ENDED,
                    label = "Deepgram",
                    detail = "SIN SALDO (<\$1)",
                    remainingUsd = snap.balanceUsd
                )
            snap.balanceUsd != null ->
                ProviderStatus(
                    provider = SttProvider.DEEPGRAM,
                    health = ProviderHealth.ACTIVE_FREE,
                    label = "Deepgram",
                    detail = "ACTIVO · ${BillingManager.formatUsd(snap.balanceUsd, 2)}",
                    remainingUsd = snap.balanceUsd
                )
            else ->
                ProviderStatus(
                    provider = SttProvider.DEEPGRAM,
                    health = ProviderHealth.ACTIVE_FREE,
                    label = "Deepgram",
                    detail = "ACTIVO (uso local)"
                )
        }
    }

    private fun checkPaid(context: Context): ProviderStatus {
        if (!ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.PAID_FALLBACK)) {
            return ProviderStatus(
                provider = SttProvider.PAID_FALLBACK,
                health = ProviderHealth.NO_KEY,
                label = "Respaldo Pagado",
                detail = "SIN KEY"
            )
        }
        val allowed = BillingManager.getAllowPaidFallback(context)
        return ProviderStatus(
            provider = SttProvider.PAID_FALLBACK,
            health = if (allowed) ProviderHealth.ACTIVE_FREE else ProviderHealth.PAUSED_LIMIT,
            label = "Respaldo Pagado",
            detail = if (allowed) {
                "LISTO · ${ApiKeyStore.getPaidFallbackEngine(context).name}"
            } else {
                "Modo \$0 Estricto (bloqueado)"
            }
        )
    }

    /** WAV PCM 16-bit mono 16kHz, 0.3s de silencio (~10KB). */
    fun writeSilentWav(context: Context): File {
        val sampleRate = 16_000
        val durationSec = 0.3
        val numSamples = (sampleRate * durationSec).toInt()
        val dataSize = numSamples * 2
        val out = ByteArrayOutputStream()
        fun writeInt(v: Int) {
            out.write(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
            )
        }
        fun writeShort(v: Short) {
            out.write(
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array()
            )
        }
        out.write("RIFF".toByteArray())
        writeInt(36 + dataSize)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        writeInt(16)
        writeShort(1) // PCM
        writeShort(1) // mono
        writeInt(sampleRate)
        writeInt(sampleRate * 2)
        writeShort(2)
        writeShort(16)
        out.write("data".toByteArray())
        writeInt(dataSize)
        out.write(ByteArray(dataSize))
        val file = File(context.cacheDir, "klipnote_probe_${System.currentTimeMillis()}.wav")
        file.writeBytes(out.toByteArray())
        return file
    }
}
