package com.blackeye.klipnote

import android.content.Context
import androidx.annotation.Keep
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Router STT: cascada automática (5 niveles) o prueba manual exclusiva.
 * 1 Groq Free → 2 Inworld Free → 3 AssemblyAI → 4 Deepgram → 5 Pagado
 */
object SttRouter {

    private const val NETWORK_RETRY_DELAY_MS = 2_500L

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
                val label = context.getString(R.string.route_test_mode_pin, p.displayName(context))
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
        val available = cfg.priority.filter {
            isConfigured(context, it) &&
                !cfg.disabled.contains(it) &&
                BillingManager.isEngineEnabled(context, it)
        }
        val primary = available.firstOrNull()
        val primaryLabel = when (primary) {
            SttProvider.GROQ_FREE -> {
                val h = BillingManager.getGroqRemainingSeconds(context) / 3600.0
                context.getString(
                    R.string.route_primary_groq,
                    primary.displayName(context),
                    h
                )
            }
            SttProvider.INWORLD -> {
                val m = BillingManager.getInworldRemainingSeconds(context) / 60.0
                context.getString(
                    R.string.route_primary_inworld,
                    primary.displayName(context),
                    m
                )
            }
            SttProvider.ASSEMBLY_AI -> {
                val h = BillingManager.getAssemblyRemainingSeconds(context) / 3600.0
                context.getString(
                    R.string.route_primary_assembly,
                    primary.displayName(context),
                    h
                )
            }
            SttProvider.DEEPGRAM -> primary.displayName(context)
            SttProvider.PAID_FALLBACK -> primary.displayName(context)
            null -> context.getString(R.string.route_no_engines)
        }
        val backups = available.drop(1).take(2).joinToString(" → ") { it.displayName(context) }
        val chain = buildString {
            append(context.getString(R.string.route_active_engine, primaryLabel))
            if (backups.isNotBlank()) {
                append(context.getString(R.string.route_backup_chain, backups))
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
        selection: RoutingSelection = RoutingSelection.Auto,
        forcedLanguageCode: String? = null,
        savedInDownloads: Boolean = false,
        onStatus: ((String) -> Unit)? = null
    ): TranscriptionResult {
        val languageCode = mode.toApiLanguageCode(forcedLanguageCode)
        return when (selection) {
            is RoutingSelection.Manual -> transcribeManual(
                context = context,
                provider = selection.provider,
                audioFile = audioFile,
                mimeType = mimeType,
                mode = mode,
                languageCode = languageCode,
                diarize = diarize,
                deepgramBalanceUsd = deepgramBalanceUsd,
                savedInDownloads = savedInDownloads,
                onStatus = onStatus
            )
            RoutingSelection.Auto -> transcribeCascade(
                context = context,
                audioFile = audioFile,
                mimeType = mimeType,
                mode = mode,
                languageCode = languageCode,
                diarize = diarize,
                deepgramBalanceUsd = deepgramBalanceUsd,
                savedInDownloads = savedInDownloads,
                onStatus = onStatus
            )
        }
    }

    private fun transcribeManual(
        context: Context,
        provider: SttProvider,
        audioFile: File,
        mimeType: String,
        mode: LanguageMode,
        languageCode: String?,
        diarize: Boolean,
        deepgramBalanceUsd: Double?,
        savedInDownloads: Boolean,
        onStatus: ((String) -> Unit)?
    ): TranscriptionResult {
        val started = System.currentTimeMillis()
        val block = preflightBlockReason(
            context, provider, deepgramBalanceUsd, respectUserToggle = false
        )
        if (block != null) {
            throw ManualRouteException(
                provider,
                context.getString(
                    R.string.manual_test_error,
                    provider.displayName(context),
                    block
                )
            )
        }
        onStatus?.invoke(
            context.getString(R.string.status_transcribing_with, provider.displayName(context))
        )
        return try {
            runProviderWithNetworkRetry(
                context = context,
                provider = provider,
                audioFile = audioFile,
                mimeType = mimeType,
                mode = mode,
                languageCode = languageCode,
                diarize = diarize
            ).copy(
                hopLog = listOf(
                    context.getString(R.string.hop_ok, provider.displayName(context))
                ),
                elapsedMs = System.currentTimeMillis() - started,
                routingSelection = RoutingSelection.Manual(provider),
                levelIndex = provider.levelIndex()
            )
        } catch (e: SttProviderException) {
            if (isNetworkFailure(e)) {
                throw ManualRouteException(
                    provider = provider,
                    message = networkUserMessage(context, savedInDownloads),
                    cause = e
                )
            }
            throw ManualRouteException(
                provider = provider,
                message = context.getString(
                    R.string.manual_test_error,
                    provider.displayName(context),
                    e.message.orEmpty()
                ),
                cause = e
            )
        } catch (e: ManualRouteException) {
            throw e
        } catch (e: Exception) {
            if (isNetworkFailure(e)) {
                throw ManualRouteException(
                    provider = provider,
                    message = networkUserMessage(context, savedInDownloads),
                    cause = e
                )
            }
            throw ManualRouteException(
                provider = provider,
                message = context.getString(
                    R.string.manual_test_error,
                    provider.displayName(context),
                    e.message ?: context.getString(R.string.health_error)
                ),
                cause = e
            )
        }
    }

    private fun transcribeCascade(
        context: Context,
        audioFile: File,
        mimeType: String,
        mode: LanguageMode,
        languageCode: String?,
        diarize: Boolean,
        deepgramBalanceUsd: Double?,
        savedInDownloads: Boolean,
        onStatus: ((String) -> Unit)?
    ): TranscriptionResult {
        val cfg = RemoteRouterConfig.currentOrDefault()
        val hopLog = mutableListOf<String>()
        val started = System.currentTimeMillis()
        var lastError: Exception? = null
        var sizeSkipCount = 0
        var networkFailCount = 0
        var otherAttemptFailures = 0

        for (provider in cfg.priority) {
            val block = preflightBlockReason(context, provider, deepgramBalanceUsd, cfg)
            if (block != null) {
                hopLog += context.getString(
                    R.string.hop_fail_skip,
                    provider.displayName(context),
                    block
                )
                continue
            }

            onStatus?.invoke(
                context.getString(R.string.status_transcribing_with, provider.displayName(context))
            )
            try {
                val result = runProviderWithNetworkRetry(
                    context = context,
                    provider = provider,
                    audioFile = audioFile,
                    mimeType = mimeType,
                    mode = mode,
                    languageCode = languageCode,
                    diarize = diarize
                )
                hopLog += context.getString(R.string.hop_ok, provider.displayName(context))
                return result.copy(
                    hopLog = hopLog.toList(),
                    elapsedMs = System.currentTimeMillis() - started,
                    routingSelection = RoutingSelection.Auto,
                    levelIndex = provider.levelIndex()
                )
            } catch (e: SttProviderException) {
                lastError = e
                when {
                    e.kind == SttFailureKind.SIZE_SKIP -> {
                        sizeSkipCount++
                        hopLog += context.getString(
                            R.string.hop_fail_skip,
                            provider.displayName(context),
                            e.message.orEmpty()
                        )
                    }
                    e.kind == SttFailureKind.NETWORK || isNetworkFailure(e) -> {
                        networkFailCount++
                        hopLog += context.getString(
                            R.string.hop_network_skip,
                            provider.displayName(context)
                        )
                    }
                    e.kind == SttFailureKind.PROMO_ENDED -> {
                        otherAttemptFailures++
                        BillingManager.markPromoEnded(provider)
                        hopLog += context.getString(
                            R.string.hop_fail_skip,
                            provider.displayName(context),
                            e.message.orEmpty()
                        )
                    }
                    e.kind == SttFailureKind.COOLDOWN -> {
                        otherAttemptFailures++
                        BillingManager.markCooldown(provider, e.retryAfterSeconds)
                        hopLog += context.getString(
                            R.string.hop_fail_skip,
                            provider.displayName(context),
                            context.getString(
                                R.string.block_cooldown_seconds,
                                BillingManager.cooldownRemainingSeconds(provider)
                            )
                        )
                    }
                    e.kind == SttFailureKind.UNAUTHORIZED || e.kind == SttFailureKind.MODEL_GONE -> {
                        otherAttemptFailures++
                        hopLog += context.getString(
                            R.string.hop_fail_skip,
                            provider.displayName(context),
                            e.message.orEmpty()
                        )
                    }
                    else -> {
                        otherAttemptFailures++
                        hopLog += context.getString(
                            R.string.hop_fail_skip,
                            provider.displayName(context),
                            e.message.orEmpty()
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e
                if (isNetworkFailure(e)) {
                    networkFailCount++
                    hopLog += context.getString(
                        R.string.hop_network_skip,
                        provider.displayName(context)
                    )
                } else {
                    otherAttemptFailures++
                    hopLog += context.getString(
                        R.string.hop_fail_skip,
                        provider.displayName(context),
                        e.message?.take(120).orEmpty()
                    )
                }
            }
        }

        val strict = !BillingManager.getAllowPaidFallback(context)
        val detail = hopLog.joinToString(" | ")
        val networkWasTheBlocker =
            networkFailCount > 0 && sizeSkipCount == 0 && otherAttemptFailures == 0
        val sizeWasTheBlocker = sizeSkipCount > 0 && networkFailCount == 0 && otherAttemptFailures == 0
        val msg = when {
            networkWasTheBlocker || (networkFailCount > 0 && otherAttemptFailures == 0 && sizeSkipCount == 0) ->
                if (savedInDownloads) {
                    context.getString(R.string.error_network_saved_in_downloads, detail)
                } else {
                    context.getString(R.string.error_network_lost) + " " +
                        context.getString(R.string.technical_detail, detail)
                }
            sizeWasTheBlocker -> {
                val mb = String.format("%.1f", audioFile.length() / (1024.0 * 1024.0))
                context.getString(R.string.error_audio_too_large_free, mb, detail)
            }
            strict -> context.getString(R.string.error_no_free_engine_zero_mode, detail)
            else -> context.getString(R.string.error_no_engine, detail)
        }
        // Siempre se muestra el recorrido completo; el último error solo va como causa.
        throw IllegalStateException(msg, lastError)
    }

    private fun runProviderWithNetworkRetry(
        context: Context,
        provider: SttProvider,
        audioFile: File,
        mimeType: String,
        mode: LanguageMode,
        languageCode: String?,
        diarize: Boolean
    ): TranscriptionResult {
        return try {
            runProvider(
                context = context,
                provider = provider,
                audioFile = audioFile,
                mimeType = mimeType,
                mode = mode,
                languageCode = languageCode,
                diarize = diarize
            )
        } catch (e: Exception) {
            if (!isNetworkFailure(e)) throw e
            try {
                Thread.sleep(NETWORK_RETRY_DELAY_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            }
            try {
                runProvider(
                    context = context,
                    provider = provider,
                    audioFile = audioFile,
                    mimeType = mimeType,
                    mode = mode,
                    languageCode = languageCode,
                    diarize = diarize
                )
            } catch (retry: Exception) {
                if (isNetworkFailure(retry) && retry !is SttProviderException) {
                    throw SttProviderException(
                        provider = provider,
                        httpCode = null,
                        kind = SttFailureKind.NETWORK,
                        message = retry.message ?: context.getString(R.string.error_network_generic)
                    )
                }
                throw retry
            }
        }
    }

    fun isNetworkFailure(error: Throwable?): Boolean {
        var cur: Throwable? = error
        var depth = 0
        while (cur != null && depth < 8) {
            when (cur) {
                is UnknownHostException,
                is SocketTimeoutException,
                is ConnectException -> return true
            }
            val msg = cur.message.orEmpty().lowercase()
            if (msg.contains("unable to resolve host") ||
                msg.contains("no address associated") ||
                msg.contains("failed to connect") ||
                msg.contains("network is unreachable") ||
                msg.contains("software caused connection abort") ||
                msg.contains("connection reset") ||
                msg.contains("timeout") ||
                msg.contains("broken pipe") ||
                (cur is IOException && (
                    msg.contains("host") ||
                        msg.contains("connect") ||
                        msg.contains("network")
                    ))
            ) {
                return true
            }
            if (cur is SttProviderException && cur.kind == SttFailureKind.NETWORK) return true
            cur = cur.cause
            depth++
        }
        return false
    }

    private fun networkUserMessage(context: Context, savedInDownloads: Boolean): String {
        val base = context.getString(R.string.error_network_lost)
        return if (savedInDownloads) {
            "$base ${context.getString(R.string.hint_find_in_downloads)}"
        } else {
            base
        }
    }

    private fun preflightBlockReason(
        context: Context,
        provider: SttProvider,
        deepgramBalanceUsd: Double?,
        cfg: RemoteRouterConfig.Snapshot = RemoteRouterConfig.currentOrDefault(),
        respectUserToggle: Boolean = true
    ): String? {
        if (!isConfigured(context, provider)) {
            return context.getString(R.string.block_no_key)
        }
        if (respectUserToggle && provider != SttProvider.PAID_FALLBACK &&
            !BillingManager.isEngineEnabled(context, provider)
        ) {
            return context.getString(R.string.block_disabled_by_you)
        }
        if (respectUserToggle && cfg.disabled.contains(provider)) {
            return context.getString(R.string.block_community_paused)
        }
        if (BillingManager.isPromoEnded(provider)) {
            return context.getString(R.string.block_promo_ended)
        }
        if (BillingManager.isInCooldown(provider)) {
            return context.getString(
                R.string.block_cooldown_seconds,
                BillingManager.cooldownRemainingSeconds(provider)
            )
        }
        if (provider == SttProvider.PAID_FALLBACK && !BillingManager.getAllowPaidFallback(context)) {
            return context.getString(R.string.block_strict_zero_mode)
        }
        if (provider == SttProvider.GROQ_FREE &&
            BillingManager.getGroqRemainingSeconds(context) <= 0.0
        ) {
            return context.getString(R.string.block_daily_quota)
        }
        if (provider == SttProvider.INWORLD &&
            BillingManager.getInworldRemainingSeconds(context) <= 0.0
        ) {
            return context.getString(R.string.block_inworld_bag)
        }
        if (provider == SttProvider.ASSEMBLY_AI &&
            BillingManager.getAssemblyRemainingSeconds(context) <= 0.0
        ) {
            return context.getString(R.string.block_assembly_bag)
        }
        if (provider == SttProvider.DEEPGRAM &&
            deepgramBalanceUsd != null &&
            deepgramBalanceUsd < BillingManager.DEEPGRAM_SAFE_FLOOR_USD
        ) {
            return context.getString(R.string.block_balance_under_one)
        }
        return null
    }

    private fun runProvider(
        context: Context,
        provider: SttProvider,
        audioFile: File,
        mimeType: String,
        mode: LanguageMode,
        languageCode: String?,
        diarize: Boolean
    ): TranscriptionResult {
        val cfg = RemoteRouterConfig.currentOrDefault()
        val inworldHint = languageCode?.takeIf {
            it.isNotBlank() && !it.equals("multi", ignoreCase = true)
        }
        return when (provider) {
            SttProvider.GROQ_FREE -> GroqClient.transcribe(
                context = context,
                apiKey = ApiKeyStore.getGroq(context),
                file = audioFile,
                mimeType = mimeType,
                model = cfg.groqModel,
                enforceFreeSizeLimit = true,
                languageCode = languageCode
            )
            SttProvider.INWORLD -> InworldClient.transcribe(
                context = context,
                apiKey = ApiKeyStore.getInworld(context),
                file = audioFile,
                mimeType = mimeType,
                languageHint = inworldHint
            )
            SttProvider.ASSEMBLY_AI -> AssemblyAiClient.transcribe(
                context = context,
                apiKey = ApiKeyStore.getAssemblyAi(context),
                file = audioFile,
                languageCode = languageCode
            )
            SttProvider.DEEPGRAM -> DeepgramClient.transcribe(
                context = context,
                apiKey = ApiKeyStore.getDeepgram(context),
                file = audioFile,
                mimeType = mimeType,
                languageCode = languageCode,
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
                groqModel = cfg.groqModel,
                languageCode = languageCode
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
