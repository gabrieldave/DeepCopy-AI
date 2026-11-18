package com.blackeye.klipnote

import android.content.Context
import androidx.annotation.Keep
import java.io.File

/**
 * Router STT: cascada automática (5 niveles) o prueba manual exclusiva.
 * 1 Groq Free → 2 Inworld Free → 3 AssemblyAI → 4 Deepgram → 5 Pagado
 */
object SttRouter {

    @Keep
    data class ActiveRouteSummary(
        val primaryLabel: String,
        val chainLabel: String,
        val hasFreePath: Boolean,
        val primaryProvider: SttProvider? = null
    )

    fun summarizeActiveRoute(
        context: Context,
        selection: RoutingSelection = RoutingSelection.Auto
    ): ActiveRouteSummary {
        when (selection) {
            is RoutingSelection.Manual -> {
                val p = selection.provider
                val label = "📌 Modo Prueba: ${p.displayName()}"
                return ActiveRouteSummary(
                    primaryLabel = label,
                    chainLabel = label,
                    hasFreePath = p != SttProvider.PAID_FALLBACK,
                    primaryProvider = p
                )
            }
            RoutingSelection.Auto -> Unit
        }
        val cfg = RemoteRouterConfig.currentOrDefault()
        val available = cfg.priority.filter { isConfigured(context, it) && !cfg.disabled.contains(it) }
        val primary = available.firstOrNull()
        val primaryLabel = when (primary) {
            SttProvider.GROQ_FREE -> {
                val h = BillingManager.getGroqRemainingSeconds(context) / 3600.0
                "Groq Free (${String.format("%.1fh", h)} hoy)"
            }
            SttProvider.INWORLD -> {
                val m = BillingManager.getInworldRemainingSeconds(context) / 60.0
                "Inworld Free (${String.format("%.0fm", m)}/mes)"
            }
            SttProvider.ASSEMBLY_AI -> {
                val h = BillingManager.getAssemblyRemainingSeconds(context) / 3600.0
                "AssemblyAI (${String.format("%.1fh", h)} bolsa)"
            }
            SttProvider.DEEPGRAM -> "Deepgram"
            SttProvider.PAID_FALLBACK -> "Respaldo Pagado"
            null -> "Sin motores"
        }
        val backups = available.drop(1).take(2).joinToString(" → ") { it.displayName() }
        val chain = buildString {
            append("⚡ Motor activo: ")
            append(primaryLabel)
            if (backups.isNotBlank()) {
                append(" → Respaldo: ")
                append(backups)
            }
        }
        val hasFree = available.any {
            it == SttProvider.GROQ_FREE ||
                it == SttProvider.INWORLD ||
                it == SttProvider.ASSEMBLY_AI ||
                it == SttProvider.DEEPGRAM
        }
        return ActiveRouteSummary(
            primaryLabel = primaryLabel,
            chainLabel = chain,
            hasFreePath = hasFree,
            primaryProvider = primary
        )
    }

    fun transcribe(
        context: Context,
        audioFile: File,
        mimeType: String,
        mode: LanguageMode,
        diarize: Boolean,
        deepgramBalanceUsd: Double? = null,
        selection: RoutingSelection = RoutingSelection.Auto
    ): TranscriptionResult {
        return when (selection) {
            is RoutingSelection.Manual -> transcribeManual(
                context = context,
                provider = selection.provider,
                audioFile = audioFile,
                mimeType = mimeType,
                mode = mode,
                diarize = diarize,
                deepgramBalanceUsd = deepgramBalanceUsd
            )
            RoutingSelection.Auto -> transcribeCascade(
                context = context,
                audioFile = audioFile,
                mimeType = mimeType,
                mode = mode,
                diarize = diarize,
                deepgramBalanceUsd = deepgramBalanceUsd
            )
        }
    }

    private fun transcribeManual(
        context: Context,
        provider: SttProvider,
        audioFile: File,
        mimeType: String,
        mode: LanguageMode,
        diarize: Boolean,
        deepgramBalanceUsd: Double?
    ): TranscriptionResult {
        val started = System.currentTimeMillis()
        val block = preflightBlockReason(context, provider, deepgramBalanceUsd)
        if (block != null) {
            throw ManualRouteException(provider, "🧪 Prueba Manual · ${provider.displayName()}: $block")
        }
        return try {
            runProvider(
                context = context,
                provider = provider,
                audioFile = audioFile,
                mimeType = mimeType,
                mode = mode,
                diarize = diarize
            ).copy(
                hopLog = listOf("MANUAL ${provider.displayName()}"),
                elapsedMs = System.currentTimeMillis() - started,
                routingSelection = RoutingSelection.Manual(provider),
                levelIndex = provider.levelIndex()
            )
        } catch (e: SttProviderException) {
            throw ManualRouteException(
                provider = provider,
                message = "🧪 Prueba Manual · ${provider.displayName()}: ${e.message}",
                cause = e
            )
        } catch (e: ManualRouteException) {
            throw e
        } catch (e: Exception) {
            throw ManualRouteException(
                provider = provider,
                message = "🧪 Prueba Manual · ${provider.displayName()}: ${e.message ?: "Error"}",
                cause = e
            )
        }
    }

    private fun transcribeCascade(
        context: Context,
        audioFile: File,
        mimeType: String,
        mode: LanguageMode,
        diarize: Boolean,
        deepgramBalanceUsd: Double?
    ): TranscriptionResult {
        val cfg = RemoteRouterConfig.currentOrDefault()
        val hopLog = mutableListOf<String>()
        val started = System.currentTimeMillis()
        var lastError: Exception? = null

        for (provider in cfg.priority) {
            val block = preflightBlockReason(context, provider, deepgramBalanceUsd, cfg)
            if (block != null) {
                hopLog += "${provider.displayName()}: $block → salto"
                continue
            }

            try {
                val result = runProvider(
                    context = context,
                    provider = provider,
                    audioFile = audioFile,
                    mimeType = mimeType,
                    mode = mode,
                    diarize = diarize
                )
                hopLog += "OK ${provider.displayName()}"
                return result.copy(
                    hopLog = hopLog.toList(),
                    elapsedMs = System.currentTimeMillis() - started,
                    routingSelection = RoutingSelection.Auto,
                    levelIndex = provider.levelIndex()
                )
            } catch (e: SttProviderException) {
                lastError = e
                when (e.kind) {
                    SttFailureKind.SIZE_SKIP -> {
                        hopLog += e.message ?: "Tamaño excedido → salto"
                    }
                    SttFailureKind.PROMO_ENDED -> {
                        BillingManager.markPromoEnded(provider)
                        hopLog += "${provider.displayName()}: ${e.message} → salto"
                    }
                    SttFailureKind.COOLDOWN -> {
                        BillingManager.markCooldown(provider, e.retryAfterSeconds)
                        hopLog += "${provider.displayName()}: COOLDOWN → salto"
                    }
                    SttFailureKind.UNAUTHORIZED, SttFailureKind.MODEL_GONE -> {
                        hopLog += "${provider.displayName()}: ${e.message} → salto"
                    }
                    else -> {
                        hopLog += "${provider.displayName()}: ${e.message} → salto"
                    }
                }
            } catch (e: Exception) {
                lastError = e
                hopLog += "${provider.displayName()}: ${e.message?.take(120)} → salto"
            }
        }

        val strict = !BillingManager.getAllowPaidFallback(context)
        val msg = if (strict) {
            "Se agotaron los motores gratis y Modo \$0 Estricto está activo. " +
                "Activa el respaldo pagado o recarga cuotas. Hops: ${hopLog.joinToString(" | ")}"
        } else {
            "Ningún motor STT disponible. Hops: ${hopLog.joinToString(" | ")}"
        }
        throw lastError ?: IllegalStateException(msg)
    }

    private fun preflightBlockReason(
        context: Context,
        provider: SttProvider,
        deepgramBalanceUsd: Double?,
        cfg: RemoteRouterConfig.Snapshot = RemoteRouterConfig.currentOrDefault()
    ): String? {
        if (cfg.disabled.contains(provider)) return "deshabilitado por config remota"
        if (!isConfigured(context, provider)) return "sin key"
        if (BillingManager.isPromoEnded(provider)) return "PROMO_TERMINADA"
        if (BillingManager.isInCooldown(provider)) {
            return "COOLDOWN ${BillingManager.cooldownRemainingSeconds(provider)}s"
        }
        if (provider == SttProvider.PAID_FALLBACK && !BillingManager.getAllowPaidFallback(context)) {
            return "Modo \$0 Estricto activo → bloqueado"
        }
        if (provider == SttProvider.GROQ_FREE &&
            BillingManager.getGroqRemainingSeconds(context) <= 0.0
        ) {
            return "cuota diaria agotada"
        }
        if (provider == SttProvider.INWORLD &&
            BillingManager.getInworldRemainingSeconds(context) <= 0.0
        ) {
            return "400m/mes agotados"
        }
        if (provider == SttProvider.ASSEMBLY_AI &&
            BillingManager.getAssemblyRemainingSeconds(context) <= 0.0
        ) {
            return "bolsa 185h agotada"
        }
        if (provider == SttProvider.DEEPGRAM &&
            deepgramBalanceUsd != null &&
            deepgramBalanceUsd < BillingManager.DEEPGRAM_SAFE_FLOOR_USD
        ) {
            return "saldo < \$1.00"
        }
        return null
    }

    private fun runProvider(
        context: Context,
        provider: SttProvider,
        audioFile: File,
        mimeType: String,
        mode: LanguageMode,
        diarize: Boolean
    ): TranscriptionResult {
        val cfg = RemoteRouterConfig.currentOrDefault()
        return when (provider) {
            SttProvider.GROQ_FREE -> GroqClient.transcribe(
                context = context,
                apiKey = ApiKeyStore.getGroq(context),
                file = audioFile,
                mimeType = mimeType,
                model = cfg.groqModel,
                enforceFreeSizeLimit = true
            )
            SttProvider.INWORLD -> InworldClient.transcribe(
                context = context,
                apiKey = ApiKeyStore.getInworld(context),
                file = audioFile,
                mimeType = mimeType,
                languageHint = null
            )
            SttProvider.ASSEMBLY_AI -> AssemblyAiClient.transcribe(
                context = context,
                apiKey = ApiKeyStore.getAssemblyAi(context),
                file = audioFile
            )
            SttProvider.DEEPGRAM -> DeepgramClient.transcribe(
                apiKey = ApiKeyStore.getDeepgram(context),
                file = audioFile,
                mimeType = mimeType,
                mode = mode,
                diarize = diarize,
                model = cfg.deepgramModel
            ).let { dg ->
                val cost = BillingManager.actualCost(
                    durationSeconds = dg.durationSeconds ?: 0.0,
                    mode = mode,
                    diarizeUsed = diarize
                )
                dg.copy(costUsd = cost, providerLabel = "Deepgram Nova-3")
            }
            SttProvider.PAID_FALLBACK -> PaidFallbackClient.transcribe(
                context = context,
                apiKey = ApiKeyStore.getPaidFallback(context),
                endpoint = ApiKeyStore.getPaidFallbackEndpoint(context),
                engine = ApiKeyStore.getPaidFallbackEngine(context),
                file = audioFile,
                mimeType = mimeType,
                groqModel = cfg.groqModel
            )
        }
    }

    fun isConfigured(context: Context, provider: SttProvider): Boolean = when (provider) {
        SttProvider.GROQ_FREE -> ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.GROQ)
        SttProvider.INWORLD -> ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.INWORLD)
        SttProvider.ASSEMBLY_AI -> ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.ASSEMBLY_AI)
        SttProvider.DEEPGRAM -> ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.DEEPGRAM)
        SttProvider.PAID_FALLBACK -> ApiKeyStore.hasSlot(context, ApiKeyStore.ProviderSlot.PAID_FALLBACK)
    }
}
