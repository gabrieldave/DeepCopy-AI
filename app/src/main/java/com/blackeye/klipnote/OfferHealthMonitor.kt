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
            statuses.forEach { st ->
                if (st.health == ProviderHealth.ACTIVE_FREE || st.health == ProviderHealth.PAUSED_LIMIT) {
                    BillingManager.clearPromoEnded(st.provider)
                }
            }
            Report(statuses = statuses, checkedAtMs = System.currentTimeMillis())
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
