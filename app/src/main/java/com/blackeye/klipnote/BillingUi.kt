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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    val groqRemainingSeconds: Double = BillingManager.GROQ_DAILY_SECONDS,
    val inworldRemainingSeconds: Double = BillingManager.INWORLD_MONTHLY_SECONDS,
    val inworldUsedMinutesEditable: String = "0",
    val assemblyRemainingSeconds: Double = BillingManager.ASSEMBLY_POOL_SECONDS,
    val assemblyUsedHoursEditable: String = "0"
)

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
    onAssemblyUsedHoursSave: (Double) -> Unit,
    onOpenKeys: () -> Unit
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
                    "Router gratis · Escudo Anti-Gladia",
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
                    detail = "${String.format("%.1f", state.groqRemainingSeconds / 3600.0)}h / 8h hoy",
                    progress = (state.groqRemainingSeconds / BillingManager.GROQ_DAILY_SECONDS)
                        .toFloat()
                        .coerceIn(0f, 1f),
                    provider = SttProvider.GROQ_FREE,
                    status = state.providerStatuses.firstOrNull { it.provider == SttProvider.GROQ_FREE },
                    keyMask = state.keyMasks[SttProvider.GROQ_FREE],
                    onOpenKeys = onOpenKeys
                )
                MotorQuotaCard(
                    level = "2",
                    title = "Inworld Free",
                    detail = "${String.format("%.0f", state.inworldRemainingSeconds / 60.0)}m / 400m mes",
                    progress = (state.inworldRemainingSeconds / BillingManager.INWORLD_MONTHLY_SECONDS)
                        .toFloat()
                        .coerceIn(0f, 1f),
                    provider = SttProvider.INWORLD,
                    status = state.providerStatuses.firstOrNull { it.provider == SttProvider.INWORLD },
                    keyMask = state.keyMasks[SttProvider.INWORLD],
                    onOpenKeys = onOpenKeys
                )
                MotorQuotaCard(
                    level = "3",
                    title = "AssemblyAI",
                    detail = "Bolsa Gratis \$50 USD (~185h) · " +
                        "${String.format("%.1f", state.assemblyRemainingSeconds / 3600.0)}h rest.",
                    progress = (state.assemblyRemainingSeconds / BillingManager.ASSEMBLY_POOL_SECONDS)
                        .toFloat()
                        .coerceIn(0f, 1f),
                    provider = SttProvider.ASSEMBLY_AI,
                    status = state.providerStatuses.firstOrNull { it.provider == SttProvider.ASSEMBLY_AI },
                    keyMask = state.keyMasks[SttProvider.ASSEMBLY_AI],
                    onOpenKeys = onOpenKeys
                )
                MotorQuotaCard(
                    level = "4",
                    title = "Deepgram \$200",
                    detail = if (official != null) {
                        BillingManager.formatUsd(official, 2)
                    } else {
                        "Uso local · ${BillingManager.formatUsd(state.appSpent, 4)} gastado app"
                    },
                    progress = progress,
                    provider = SttProvider.DEEPGRAM,
                    status = state.providerStatuses.firstOrNull { it.provider == SttProvider.DEEPGRAM },
                    keyMask = state.keyMasks[SttProvider.DEEPGRAM],
                    onOpenKeys = onOpenKeys,
                    barColor = barColor
                )
                MotorQuotaCard(
                    level = "5",
                    title = "Respaldo Pagado",
                    detail = if (state.allowPaidFallback) "Habilitado" else "Modo \$0 Estricto",
                    progress = if (state.allowPaidFallback) 1f else 0f,
                    provider = SttProvider.PAID_FALLBACK,
                    status = state.providerStatuses.firstOrNull { it.provider == SttProvider.PAID_FALLBACK },
                    keyMask = state.keyMasks[SttProvider.PAID_FALLBACK],
                    onOpenKeys = onOpenKeys
                )

                QuotaAdjustEditor(
                    label = "Minutos Inworld ya usados (mes)",
                    initialText = state.inworldUsedMinutesEditable,
                    onSave = onInworldUsedMinutesSave
                )
                QuotaAdjustEditor(
                    label = "Horas AssemblyAI ya usadas",
                    initialText = state.assemblyUsedHoursEditable,
                    onSave = onAssemblyUsedHoursSave
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Modo \$0 Estricto (Bloquear cobros si se acaban las gratis)",
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
                        if (state.verifyingOffers) "Verificando ofertas…" else "Verificar Ofertas Activas",
                        fontWeight = FontWeight.SemiBold
                    )
                }

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
    onOpenKeys: () -> Unit,
    barColor: Color = Accent
) {
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
            if (isInworld) "ACTIVO 400m/mes" else "ACTIVO (Gratis)",
            Accent
        )
        ProviderHealth.KEY_READY -> Triple("🟢", "KEY LISTA", Accent)
        ProviderHealth.PAUSED_LIMIT -> Triple(
            "🟡",
            if (isInworld) "LÍMITE MENSUAL" else "EN PAUSA / LÍMITE DIARIO",
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
            .clickable(onClick = onOpenKeys)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Nivel $level · $title", color = OnBg, fontWeight = FontWeight.Medium, fontSize = 13.sp)
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
        Text(detail, color = Muted, fontSize = 12.sp)
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
    }
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
fun MultiProviderKeysDialog(
    groqMask: String?,
    inworldMask: String?,
    assemblyMask: String?,
    deepgramMask: String?,
    deepgramProjectId: String,
    paidMask: String?,
    paidEndpoint: String,
    paidEngine: PaidFallbackEngine,
    onDismiss: () -> Unit,
    onSaveGroq: (String) -> Unit,
    onClearGroq: () -> Unit,
    onSaveInworld: (String) -> Unit,
    onClearInworld: () -> Unit,
    onSaveAssembly: (String) -> Unit,
    onClearAssembly: () -> Unit,
    onSaveDeepgram: (String) -> Unit,
    onClearDeepgram: () -> Unit,
    onSaveDeepgramProjectId: (String) -> Unit,
    onSavePaid: (String, String, PaidFallbackEngine) -> Unit,
    onClearPaid: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        titleContentColor = OnBg,
        textContentColor = OnBg,
        title = { Text("API Keys (BYOK cifrado)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                KeySlotRow(
                    title = "1 · Groq Free",
                    mask = groqMask,
                    onSave = onSaveGroq,
                    onClear = onClearGroq
                )
                KeySlotRow(
                    title = "2 · Inworld Free (Basic Base64)",
                    mask = inworldMask,
                    onSave = onSaveInworld,
                    onClear = onClearInworld
                )
                KeySlotRow(
                    title = "3 · AssemblyAI",
                    mask = assemblyMask,
                    onSave = onSaveAssembly,
                    onClear = onClearAssembly
                )
                DeepgramKeySlot(
                    mask = deepgramMask,
                    projectId = deepgramProjectId,
                    onSaveKey = onSaveDeepgram,
                    onClearKey = onClearDeepgram,
                    onSaveProjectId = onSaveDeepgramProjectId
                )
                PaidKeySlot(
                    mask = paidMask,
                    endpoint = paidEndpoint,
                    engine = paidEngine,
                    onSave = onSavePaid,
                    onClear = onClearPaid
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Accent) }
        },
        dismissButton = {}
    )
}

@Composable
private fun KeySlotRow(
    title: String,
    mask: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var replacing by remember(mask) { mutableStateOf(mask == null) }
    var value by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (mask != null && !replacing) {
            Text(mask, color = Accent, fontWeight = FontWeight.SemiBold)
            Row {
                TextButton(onClick = { replacing = true; value = "" }) {
                    Text("Reemplazar", color = Accent)
                }
                TextButton(onClick = onClear) {
                    Text("Borrar", color = Red)
                }
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("Pega API key…", color = Muted) },
                colors = fieldColors()
            )
            TextButton(
                onClick = { onSave(value); replacing = false; value = "" },
                enabled = value.isNotBlank()
            ) {
                Text("Guardar", color = Accent)
            }
        }
    }
}

@Composable
private fun DeepgramKeySlot(
    mask: String?,
    projectId: String,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onSaveProjectId: (String) -> Unit
) {
    var replacing by remember(mask) { mutableStateOf(mask == null) }
    var value by remember { mutableStateOf("") }
    var projectText by remember(projectId) { mutableStateOf(projectId) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("4 · Deepgram", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (mask != null && !replacing) {
            Text(mask, color = Accent, fontWeight = FontWeight.SemiBold)
            Row {
                TextButton(onClick = { replacing = true; value = "" }) {
                    Text("Reemplazar", color = Accent)
                }
                TextButton(onClick = onClearKey) {
                    Text("Borrar", color = Red)
                }
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("Pega API key Deepgram…", color = Muted) },
                colors = fieldColors()
            )
            TextButton(
                onClick = { onSaveKey(value); replacing = false; value = "" },
                enabled = value.isNotBlank()
            ) {
                Text("Guardar key", color = Accent)
            }
        }
        OutlinedTextField(
            value = projectText,
            onValueChange = { projectText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Project ID (opcional)") },
            placeholder = { Text("Deepgram project_id", color = Muted) },
            colors = fieldColors()
        )
        TextButton(
            onClick = { onSaveProjectId(projectText) },
            enabled = projectText.isNotBlank()
        ) {
            Text("Guardar Project ID", color = Accent)
        }
    }
}

@Composable
private fun PaidKeySlot(
    mask: String?,
    endpoint: String,
    engine: PaidFallbackEngine,
    onSave: (String, String, PaidFallbackEngine) -> Unit,
    onClear: () -> Unit
) {
    var replacing by remember(mask) { mutableStateOf(mask == null) }
    var value by remember { mutableStateOf("") }
    var endpointText by remember { mutableStateOf(endpoint) }
    var engineSel by remember { mutableStateOf(engine) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("5 · Respaldo Pagado", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { engineSel = PaidFallbackEngine.GROQ_PAID }) {
                Text(
                    "Groq Paid",
                    color = if (engineSel == PaidFallbackEngine.GROQ_PAID) Accent else Muted
                )
            }
            TextButton(onClick = { engineSel = PaidFallbackEngine.MICROSOFT_MAI }) {
                Text(
                    "Microsoft MAI",
                    color = if (engineSel == PaidFallbackEngine.MICROSOFT_MAI) Accent else Muted
                )
            }
        }
        if (mask != null && !replacing) {
            Text(mask, color = Accent, fontWeight = FontWeight.SemiBold)
            Text(endpoint.ifBlank { "(sin endpoint)" }, color = Muted, fontSize = 11.sp)
            Row {
                TextButton(onClick = { replacing = true; value = "" }) {
                    Text("Reemplazar", color = Accent)
                }
                TextButton(onClick = onClear) {
                    Text("Borrar", color = Red)
                }
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("API key pagada…", color = Muted) },
                colors = fieldColors()
            )
            if (engineSel == PaidFallbackEngine.MICROSOFT_MAI) {
                OutlinedTextField(
                    value = endpointText,
                    onValueChange = { endpointText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Endpoint Azure / OpenAI-compat", color = Muted) },
                    colors = fieldColors()
                )
            }
            TextButton(
                onClick = {
                    onSave(value, endpointText, engineSel)
                    replacing = false
                    value = ""
                },
                enabled = value.isNotBlank()
            ) {
                Text("Guardar", color = Accent)
            }
        }
    }
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
