package com.blackeye.klipnote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardBg = Color(0xFF151C28)
private val Accent = Color(0xFF10B981)
private val OnBg = Color(0xFFE8EEF7)
private val Muted = Color(0xFF8B9BB4)
private val Amber = Color(0xFFFBBF24)
private val Red = Color(0xFFF87171)

data class CreditUiState(
    val expanded: Boolean = true,
    val refreshing: Boolean = false,
    val snapshot: DeepgramBillingClient.BalanceSnapshot? = null,
    val appSpent: Double = 0.0,
    val initialCredit: Double = BillingManager.DEFAULT_INITIAL_CREDIT,
    val includeDiarizeCost: Boolean = true,
    val languageMode: LanguageMode = LanguageMode.AUTO,
    val localUsage: BillingManager.LocalUsage = BillingManager.LocalUsage(0, 0.0, 0, 0.0),
    val routeChain: String = "",
    val providerStatuses: List<ProviderStatus> = emptyList(),
    val keyMasks: Map<SttProvider, String?> = emptyMap(),
    val allowPaidFallback: Boolean = false,
    val verifyingOffers: Boolean = false,
    val groqRemainingSeconds: Double = BillingManager.GROQ_DEFAULT_LIMIT_SECONDS,
    val groqLimitSeconds: Double = BillingManager.GROQ_DEFAULT_LIMIT_SECONDS,
    val groqUsedTodaySeconds: Double = 0.0,
    val groqHasLiveQuota: Boolean = false,
    val inworldRemainingSeconds: Double = BillingManager.INWORLD_POOL_SECONDS,
    val inworldUsedMinutesEditable: String = "0",
    val assemblyRemainingSeconds: Double = BillingManager.ASSEMBLY_POOL_SECONDS,
    val assemblyRemainingUsdEditable: String = "50.00",
    val engineEnabled: Map<SttProvider, Boolean> = emptyMap(),
    val routingSelection: RoutingSelection = RoutingSelection.Auto
)

@Composable
fun RoutingModeChips(
    selection: RoutingSelection,
    keyMasks: Map<SttProvider, String?>,
    onSelectAuto: () -> Unit,
    onSelectManual: (SttProvider) -> Unit,
    onOpenKeys: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selection is RoutingSelection.Auto,
                    onClick = onSelectAuto,
                    label = { Text("⚡ Auto (Cascada)") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF0F1623),
                        labelColor = Muted,
                        selectedContainerColor = Accent.copy(alpha = 0.22f),
                        selectedLabelColor = Accent
                    )
                )
            }
            items(SttProvider.entries.toList(), key = { it.name }) { provider ->
                val selected = selection is RoutingSelection.Manual &&
                    selection.provider == provider
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (keyMasks[provider].isNullOrBlank()) {
                            onOpenKeys()
                        } else {
                            onSelectManual(provider)
                        }
                    },
                    label = { Text(provider.chipLabel()) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF0F1623),
                        labelColor = Muted,
                        selectedContainerColor = Accent.copy(alpha = 0.22f),
                        selectedLabelColor = Accent
                    )
                )
            }
        }
        if (selection is RoutingSelection.Manual) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📌 Modo Prueba: ${selection.provider.displayName()}",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSelectAuto) {
                    Text("Volver a Auto", color = Accent)
                }
            }
        }
    }
}

@Composable
fun CreditControlPanel(
    state: CreditUiState,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCostSettings: () -> Unit,
    onVerifyOffers: () -> Unit,
    onAllowPaidChange: (Boolean) -> Unit,
    onInworldUsedMinutesSave: (Double) -> Unit,
    onAssemblyRemainingUsdSave: (Double) -> Unit,
    onOpenKeys: () -> Unit,
    onProbeProvider: (SttProvider) -> Unit = {},
    onEngineEnabledChange: (SttProvider, Boolean) -> Unit = { _, _ -> }
) {
    val official = state.snapshot?.balanceUsd
    val localOnly = official == null
    val remainingForBar = official ?: (state.initialCredit - state.appSpent)
    val progress = (remainingForBar / state.initialCredit).toFloat().coerceIn(0f, 1f)
    val barColor = when {
        localOnly -> Accent
        remainingForBar > 50.0 -> Accent
        remainingForBar >= 15.0 -> Amber
        else -> Red
    }
    val officialSpent = official?.let { state.initialCredit - it }
    val delta = officialSpent?.let { it - state.appSpent }
    val hoursLeft = BillingManager.hoursRemainingAtRate(
        remainingUsd = remainingForBar,
        mode = state.languageMode,
        includeDiarize = state.includeDiarizeCost
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.routeChain.isNotBlank()) {
            Text(state.routeChain, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        ZeroCostWarning()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Motores y Cuotas",
                    color = OnBg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    "Se usan en orden: si uno se acaba, salta solo al siguiente",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refrescar",
                    tint = Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onOpenCostSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Muted)
            }
            Icon(
                if (state.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Muted
            )
        }

        AnimatedVisibility(visible = state.expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MotorQuotaCard(
                    level = "1",
                    title = "Groq Free",
                    detail = BillingManager.formatGroqQuotaDetail(
                        remainingSeconds = state.groqRemainingSeconds,
                        limitSeconds = state.groqLimitSeconds,
                        usedTodaySeconds = state.groqUsedTodaySeconds,
                        hasLiveQuota = state.groqHasLiveQuota
                    ),
                    progress = BillingManager.groqQuotaProgress(
                        remainingSeconds = state.groqRemainingSeconds,
                        limitSeconds = state.groqLimitSeconds,
                        hasLiveQuota = state.groqHasLiveQuota
                    ),
                    provider = SttProvider.GROQ_FREE,
                    status = state.providerStatuses.firstOrNull { it.provider == SttProvider.GROQ_FREE },
                    keyMask = state.keyMasks[SttProvider.GROQ_FREE],
                    pinned = state.routingSelection is RoutingSelection.Manual &&
                        state.routingSelection.provider == SttProvider.GROQ_FREE,
                    onOpenKeys = onOpenKeys,
                    onProbe = { onProbeProvider(SttProvider.GROQ_FREE) },
                    enabled = state.engineEnabled[SttProvider.GROQ_FREE] ?: true,
                    onEnabledChange = { onEngineEnabledChange(SttProvider.GROQ_FREE, it) }
                )
                MotorQuotaCard(
                    level = "2",
                    title = "Inworld Free",
                    detail = "Quedan ~${String.format("%.0f", state.inworldRemainingSeconds / 60.0)} min de 400 este mes",
                    progress = (state.inworldRemainingSeconds / BillingManager.INWORLD_POOL_SECONDS)
                        .toFloat()
                        .coerceIn(0f, 1f),
                    provider = SttProvider.INWORLD,
                    status = state.providerStatuses.firstOrNull { it.provider == SttProvider.INWORLD },
                    keyMask = state.keyMasks[SttProvider.INWORLD],
                    pinned = state.routingSelection is RoutingSelection.Manual &&
                        state.routingSelection.provider == SttProvider.INWORLD,
                    onOpenKeys = onOpenKeys,
                    onProbe = { onProbeProvider(SttProvider.INWORLD) },
                    enabled = state.engineEnabled[SttProvider.INWORLD] ?: true,
                    onEnabledChange = { onEngineEnabledChange(SttProvider.INWORLD, it) }
                )
                MotorQuotaCard(
                    level = "3",
                    title = "AssemblyAI",
                    detail = "Saldo ~${BillingManager.formatUsd(
                        state.assemblyRemainingSeconds / 3600.0 * BillingManager.ASSEMBLY_EST_USD_PER_HOUR, 2
                    )} de \$50 · ~${String.format("%.1f", state.assemblyRemainingSeconds / 3600.0)} h",
                    progress = (state.assemblyRemainingSeconds / BillingManager.ASSEMBLY_POOL_SECONDS)
                        .toFloat()
                        .coerceIn(0f, 1f),
                    provider = SttProvider.ASSEMBLY_AI,
                    status = state.providerStatuses.firstOrNull { it.provider == SttProvider.ASSEMBLY_AI },
                    keyMask = state.keyMasks[SttProvider.ASSEMBLY_AI],
                    pinned = state.routingSelection is RoutingSelection.Manual &&
                        state.routingSelection.provider == SttProvider.ASSEMBLY_AI,
                    onOpenKeys = onOpenKeys,
                    onProbe = { onProbeProvider(SttProvider.ASSEMBLY_AI) },
                    enabled = state.engineEnabled[SttProvider.ASSEMBLY_AI] ?: true,
                    onEnabledChange = { onEngineEnabledChange(SttProvider.ASSEMBLY_AI, it) }
                )
                MotorQuotaCard(
                    level = "4",
                    title = "Deepgram \$200",
                    detail = if (official != null) {
                        BillingManager.formatUsd(official, 2)
                    } else {
                        "Saldo no disponible (la llave no tiene permiso de facturación) · " +
                            "gastado según la app: ${BillingManager.formatUsd(state.appSpent, 2)}"
                    },
                    progress = progress,
                    provider = SttProvider.DEEPGRAM,
                    status = state.providerStatuses.firstOrNull { it.provider == SttProvider.DEEPGRAM },
                    keyMask = state.keyMasks[SttProvider.DEEPGRAM],
                    pinned = state.routingSelection is RoutingSelection.Manual &&
                        state.routingSelection.provider == SttProvider.DEEPGRAM,
                    onOpenKeys = onOpenKeys,
                    onProbe = { onProbeProvider(SttProvider.DEEPGRAM) },
                    enabled = state.engineEnabled[SttProvider.DEEPGRAM] ?: true,
                    onEnabledChange = { onEngineEnabledChange(SttProvider.DEEPGRAM, it) },
                    barColor = barColor
                )
                MotorQuotaCard(
                    level = "5",
                    title = "Respaldo Pagado",
                    detail = if (state.allowPaidFallback) {
                        "Permitido: se usa solo si fallan todos los gratis"
                    } else {
                        "Bloqueado por Modo \$0"
                    },
                    progress = if (state.allowPaidFallback) 1f else 0f,
                    provider = SttProvider.PAID_FALLBACK,
                    status = state.providerStatuses.firstOrNull { it.provider == SttProvider.PAID_FALLBACK },
                    keyMask = state.keyMasks[SttProvider.PAID_FALLBACK],
                    pinned = state.routingSelection is RoutingSelection.Manual &&
                        state.routingSelection.provider == SttProvider.PAID_FALLBACK,
                    onOpenKeys = onOpenKeys,
                    onProbe = { onProbeProvider(SttProvider.PAID_FALLBACK) },
                    enabled = state.engineEnabled[SttProvider.PAID_FALLBACK] ?: true,
                    onEnabledChange = { onEngineEnabledChange(SttProvider.PAID_FALLBACK, it) }
                )

                Text(
                    "Corregir los medidores «Estimado» con lo que ves en el panel de cada proveedor:",
                    color = Muted,
                    fontSize = 11.sp
                )
                QuotaAdjustEditor(
                    label = "Inworld: minutos usados este mes",
                    initialText = state.inworldUsedMinutesEditable,
                    onSave = onInworldUsedMinutesSave
                )
                QuotaAdjustEditor(
                    label = "AssemblyAI: saldo que ves (USD)",
                    initialText = state.assemblyRemainingUsdEditable,
                    onSave = onAssemblyRemainingUsdSave
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Modo \$0 (nunca usar el motor de pago)",
                        color = OnBg,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = !state.allowPaidFallback,
                        onCheckedChange = { strict -> onAllowPaidChange(!strict) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent
                        )
                    )
                }

                Button(
                    onClick = onVerifyOffers,
                    enabled = !state.verifyingOffers,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A2436),
                        contentColor = OnBg
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Accent)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (state.verifyingOffers) "Verificando…" else "Verificar mis llaves y ofertas",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "Prueba cada llave con un audio de silencio de 0.3 s y te dice si sigue gratis, " +
                        "en pausa o sin saldo. También se hace sola una vez por semana.",
                    color = Muted,
                    fontSize = 11.sp
                )

                TextButton(onClick = onOpenKeys) {
                    Text("Configurar API keys (BYOK)", color = Accent)
                }

                if (state.snapshot?.status == DeepgramBillingClient.BillingScopeStatus.UNAUTHORIZED) {
                    Text(
                        "API Key Deepgram rechazada. Usa Reemplazar en llaves.",
                        color = Amber,
                        fontSize = 12.sp
                    )
                }

                if (!localOnly && official != null) {
                    Text(
                        "Te alcanza ~${String.format("%.1f", hoursLeft)}h Deepgram · Δ ${
                            delta?.let { BillingManager.formatUsd(it, 4) } ?: "—"
                        }",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }

                TextButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Accent)
                    Spacer(Modifier.size(6.dp))
                    Text("Historial de grabaciones", color = Accent)
                }
            }
        }
    }
}

@Composable
private fun MotorQuotaCard(
    level: String,
    title: String,
    detail: String,
    progress: Float,
    provider: SttProvider,
    status: ProviderStatus?,
    keyMask: String?,
    pinned: Boolean,
    onOpenKeys: () -> Unit,
    onProbe: () -> Unit,
    barColor: Color = Accent,
    enabled: Boolean = true,
    onEnabledChange: (Boolean) -> Unit = {}
) {
    val info = ProviderCatalog.info(provider)
    val hasKey = !keyMask.isNullOrBlank()
    // Regla estricta: con llave en ApiKeyStore NUNCA mostrar SIN KEY.
    val health = when {
        !hasKey -> ProviderHealth.NO_KEY
        status == null || status.health == ProviderHealth.NO_KEY -> ProviderHealth.KEY_READY
        else -> status.health
    }
    val isInworld = provider == SttProvider.INWORLD
    val (emoji, healthLabel, tint) = when (health) {
        ProviderHealth.ACTIVE_FREE -> Triple(
            "🟢",
            if (isInworld) "ACTIVO 400m bolsa" else "ACTIVO (Gratis)",
            Accent
        )
        ProviderHealth.KEY_READY -> Triple("🟢", "KEY LISTA", Accent)
        ProviderHealth.PAUSED_LIMIT -> Triple(
            "🟡",
            if (isInworld) "BOLSA AGOTADA" else "EN PAUSA / LÍMITE DIARIO",
            Amber
        )
        ProviderHealth.OFFER_ENDED -> Triple("🔴", "OFERTA TERMINADA / SIN SALDO", Red)
        ProviderHealth.COOLDOWN -> Triple("🟡", "COOLDOWN", Amber)
        ProviderHealth.NO_KEY -> Triple("⚪", "SIN KEY", Muted)
        ProviderHealth.UNKNOWN -> Triple("⚪", status?.detail ?: "—", Muted)
    }
    val maskLine = if (hasKey) "Key $keyMask" else "SIN KEY"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1623))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Nivel $level · $title",
                color = OnBg,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenKeys)
            )
            Text(
                "$emoji $healthLabel",
                color = tint,
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onOpenKeys)
            )
        }
        Text(
            maskLine,
            color = if (hasKey) Accent else Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onOpenKeys)
        )
        Text(ProviderCatalog.offerText(provider), color = Muted, fontSize = 11.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MeterBadge(info.meterKind)
            Text(detail, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        Text(info.meterNote, color = Muted.copy(alpha = 0.8f), fontSize = 10.sp)
        status?.detail
            ?.takeIf { it.isNotBlank() && it != healthLabel && !it.startsWith("SIN KEY") }
            ?.let {
                Text(it, color = Muted, fontSize = 11.sp)
            }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = Color(0xFF151C28)
        )
        if (hasKey) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (provider == SttProvider.PAID_FALLBACK) {
                        "Permitir gastar dinero"
                    } else {
                        "Usar este motor"
                    },
                    color = OnBg,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = if (provider == SttProvider.PAID_FALLBACK) Amber else Accent
                    )
                )
            }
            TextButton(
                onClick = onProbe,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (pinned) "📌 FIJADO" else "Probar",
                    color = Accent,
                    fontWeight = if (pinned) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun MeterBadge(kind: MeterKind) {
    val color = when (kind) {
        MeterKind.REAL -> Accent
        MeterKind.ESTIMATED -> Amber
        MeterKind.NONE -> Muted
    }
    Text(
        kind.badge(),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Aviso fijo: Modo $0 solo bloquea el motor de pago; las tarjetas en los proveedores sí cobran. */
@Composable
fun ZeroCostWarning(modifier: Modifier = Modifier) {
    Text(
        "⚠️ ${ProviderCatalog.ZERO_COST_WARNING}",
        color = Amber,
        fontSize = 11.sp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Amber.copy(alpha = 0.10f))
            .padding(10.dp)
    )
}

@Composable
private fun QuotaAdjustEditor(
    label: String,
    initialText: String,
    onSave: (Double) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = fieldColors()
        )
        TextButton(
            onClick = {
                val value = text.toDoubleOrNull() ?: return@TextButton
                onSave(value)
            }
        ) {
            Text("Ajustar", color = Accent)
        }
    }
}

@Composable
fun PreFlightCard(
    fileName: String,
    durationLabel: String,
    modeLabel: String,
    estimatedUsd: Double,
    hardStopMessage: String?,
    autoTranscribe: Boolean,
    autoTranscribeEnabled: Boolean,
    routeHint: String,
    onAutoTranscribeChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    busy: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Cotizador Pre-Vuelo", color = OnBg, fontWeight = FontWeight.SemiBold)
        Text(fileName, color = OnBg, fontSize = 14.sp)
        Text(
            "Duración $durationLabel · Modo $modeLabel",
            color = Muted,
            fontSize = 13.sp
        )
        if (routeHint.isNotBlank()) {
            Text(routeHint, color = Accent, fontSize = 12.sp)
        }
        Text(
            "Costo Estimado ${BillingManager.formatUsd(estimatedUsd, 4)}",
            color = Accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )

        if (hardStopMessage != null) {
            Text(hardStopMessage, color = Red, fontSize = 13.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Auto-transcribir al compartir",
                color = if (autoTranscribeEnabled) OnBg else Muted,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp
            )
            Switch(
                checked = autoTranscribe,
                onCheckedChange = onAutoTranscribeChange,
                enabled = autoTranscribeEnabled && !busy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = Color(0xFF1F2937)
                )
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancelar", color = Muted)
            }
            Button(
                onClick = onConfirm,
                enabled = !busy && hardStopMessage == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.Black,
                    disabledContainerColor = Accent.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Transcribir Ahora", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostHistorySheet(
    entries: List<BillingManager.HistoryEntry>,
    onDismiss: () -> Unit,
    onOpen: (BillingManager.HistoryEntry) -> Unit,
    onDelete: (BillingManager.HistoryEntry) -> Unit,
    onCopyTranscript: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .imePadding()
        ) {
            Text(
                "Historial de grabaciones",
                color = OnBg,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Text("Sin grabaciones aún. Las transcripciones se guardan solas.", color = Muted)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onOpen = { onOpen(entry) },
                            onDelete = { onDelete(entry) },
                            onCopy = { onCopyTranscript(entry.transcript) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: BillingManager.HistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1623))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(entry.formattedWhen, color = Muted, fontSize = 11.sp)
        Text(entry.fileName, color = OnBg, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(
            buildString {
                entry.provider?.let { append(it); append(" · ") }
                append("Duración ${BillingManager.formatDuration(entry.durationSeconds)} · ")
                append("App ${BillingManager.formatUsd(entry.appCostUsd, 4)}")
                entry.officialDeltaUsd?.let {
                    append(" · Δ real ${BillingManager.formatUsd(it, 4)}")
                }
            },
            color = Muted,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpen) { Text("Abrir", color = Accent) }
            TextButton(onClick = onCopy, enabled = entry.transcript.isNotBlank()) {
                Text("Copiar", color = Accent)
            }
            TextButton(onClick = onDelete) { Text("Borrar", color = Red) }
        }
    }
}

@Composable
fun CostSettingsDialog(
    initialCredit: Double,
    appCap: Double,
    minReserve: Double,
    includeDiarize: Boolean,
    onDismiss: () -> Unit,
    onSave: (credit: Double, cap: Double, reserve: Double, includeDiarize: Boolean) -> Unit
) {
    var creditText by remember { mutableStateOf(initialCredit.toString()) }
    var capText by remember { mutableStateOf(appCap.toString()) }
    var reserveText by remember { mutableStateOf(minReserve.toString()) }
    var diarize by remember { mutableStateOf(includeDiarize) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        titleContentColor = OnBg,
        textContentColor = OnBg,
        title = { Text("Ajustes de costos") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MoneyField("Crédito inicial USD", creditText) { creditText = it }
                MoneyField("Tope app USD", capText) { capText = it }
                MoneyField("Reserva mínima USD", reserveText) { reserveText = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Incluir \$0.0020/min por Diarize en estimado",
                        color = OnBg,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = diarize,
                        onCheckedChange = { diarize = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        creditText.toDoubleOrNull() ?: initialCredit,
                        capText.toDoubleOrNull() ?: appCap,
                        reserveText.toDoubleOrNull() ?: minReserve,
                        diarize
                    )
                }
            ) {
                Text("Guardar", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Muted)
            }
        }
    )
}

@Composable
private fun MoneyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color(0xFF243044),
    focusedTextColor = OnBg,
    unfocusedTextColor = OnBg,
    cursorColor = Accent,
    focusedLabelColor = Muted,
    unfocusedLabelColor = Muted
)
