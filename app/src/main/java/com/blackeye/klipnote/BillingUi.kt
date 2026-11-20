package com.blackeye.klipnote

import com.blackeye.klipnote.ui.KlipNeutrals
import com.blackeye.klipnote.ui.KlipPalette

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardBg get() = KlipNeutrals.Card
private val Accent get() = KlipPalette.accent
private val OnAccent get() = KlipPalette.onAccent
private val OnBg get() = KlipNeutrals.OnBg
private val Muted get() = KlipNeutrals.Muted
private val Amber get() = KlipNeutrals.Amber
private val Red get() = KlipNeutrals.Red

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
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selection is RoutingSelection.Auto,
                    onClick = onSelectAuto,
                    label = { Text(stringResource(R.string.automatic)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = KlipNeutrals.ChipBg,
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
                    label = { Text(provider.chipLabel(context)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = KlipNeutrals.ChipBg,
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
                    "📌 Probando: ${selection.provider.displayName(context)}",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSelectAuto) {
                    Text(stringResource(R.string.back_to_auto), color = Accent)
                }
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
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
    FreeServicesPanel(
        state = state,
        onRefresh = onRefresh,
        onVerifyOffers = onVerifyOffers,
        onInworldUsedMinutesSave = onInworldUsedMinutesSave,
        onAssemblyRemainingUsdSave = onAssemblyRemainingUsdSave,
        onOpenKeys = onOpenKeys,
        onProbeProvider = onProbeProvider,
        onEngineEnabledChange = onEngineEnabledChange
    )
}

@Composable
fun FreeServicesPanel(
    state: CreditUiState,
    onRefresh: () -> Unit,
    onVerifyOffers: () -> Unit,
    onInworldUsedMinutesSave: (Double) -> Unit,
    onAssemblyRemainingUsdSave: (Double) -> Unit,
    onOpenKeys: () -> Unit,
    onConnectProvider: (SttProvider) -> Unit = { onOpenKeys() },
    onProbeProvider: (SttProvider) -> Unit = {},
    onEngineEnabledChange: (SttProvider, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val official = state.snapshot?.balanceUsd
    val remainingForBar = official ?: (state.initialCredit - state.appSpent)
    val progressDg = (remainingForBar / state.initialCredit).toFloat().coerceIn(0f, 1f)
    val barColorDg = when {
        official == null -> Accent
        remainingForBar > 50.0 -> Accent
        remainingForBar >= 15.0 -> Amber
        else -> Red
    }
    val freeProviders = listOf(
        SttProvider.GROQ_FREE,
        SttProvider.INWORLD,
        SttProvider.ASSEMBLY_AI,
        SttProvider.DEEPGRAM
    )
    val connected = freeProviders.count { !state.keyMasks[it].isNullOrBlank() }
    val hasAnyKey = connected > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionHeader(stringResource(R.string.free_services_title), helpRes = HelpCopy.SERVICES)
                Text(
                    stringResource(R.string.connected_count, connected),
                    color = Muted,
                    fontSize = 13.sp
                )
            }
            if (hasAnyKey) {
                TextButton(onClick = onOpenKeys) {
                    Text(stringResource(R.string.my_keys), color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (!hasAnyKey) {
            Button(
                onClick = onOpenKeys,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.connect_my_keys), fontWeight = FontWeight.SemiBold)
            }
        }

        ClearServiceRow(
            name = SttProvider.GROQ_FREE.displayName(context),
            primary = BillingManager.formatGroqQuotaDetail(
                context = context,
                remainingSeconds = state.groqRemainingSeconds,
                limitSeconds = state.groqLimitSeconds,
                usedTodaySeconds = state.groqUsedTodaySeconds,
                hasLiveQuota = state.groqHasLiveQuota
            ).let { raw ->
                val mins = (state.groqRemainingSeconds / 60.0).coerceAtLeast(0.0)
                if (state.groqHasLiveQuota || state.groqRemainingSeconds > 0) {
                    context.getString(R.string.billing_groq_min_this_hour, mins)
                } else raw
            },
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
            enabled = state.engineEnabled[SttProvider.GROQ_FREE] ?: true,
            onEnabledChange = { onEngineEnabledChange(SttProvider.GROQ_FREE, it) },
            onProbe = { onProbeProvider(SttProvider.GROQ_FREE) },
            onConnect = { onConnectProvider(SttProvider.GROQ_FREE) },
            showMeterAdjust = false,
            meterAdjustLabel = "",
            meterAdjustInitial = "",
            onMeterAdjustSave = {}
        )
        ClearServiceRow(
            name = SttProvider.INWORLD.displayName(context),
            primary = context.getString(
                R.string.billing_inworld_meter_primary,
                state.inworldRemainingSeconds / 60.0
            ),
            progress = (state.inworldRemainingSeconds / BillingManager.INWORLD_POOL_SECONDS)
                .toFloat().coerceIn(0f, 1f),
            provider = SttProvider.INWORLD,
            status = state.providerStatuses.firstOrNull { it.provider == SttProvider.INWORLD },
            keyMask = state.keyMasks[SttProvider.INWORLD],
            pinned = state.routingSelection is RoutingSelection.Manual &&
                state.routingSelection.provider == SttProvider.INWORLD,
            enabled = state.engineEnabled[SttProvider.INWORLD] ?: true,
            onEnabledChange = { onEngineEnabledChange(SttProvider.INWORLD, it) },
            onProbe = { onProbeProvider(SttProvider.INWORLD) },
            onConnect = { onConnectProvider(SttProvider.INWORLD) },
            showMeterAdjust = true,
            meterAdjustLabel = stringResource(R.string.billing_inworld_used_hint),
            meterAdjustInitial = state.inworldUsedMinutesEditable,
            onMeterAdjustSave = onInworldUsedMinutesSave
        )
        ClearServiceRow(
            name = SttProvider.ASSEMBLY_AI.displayName(context),
            primary = run {
                val usd = state.assemblyRemainingSeconds / 3600.0 * BillingManager.ASSEMBLY_EST_USD_PER_HOUR
                context.getString(
                    R.string.billing_assembly_meter_primary,
                    BillingManager.formatUsd(usd, 2),
                    BillingManager.formatUsd(50.0, 0)
                )
            },
            progress = (state.assemblyRemainingSeconds / BillingManager.ASSEMBLY_POOL_SECONDS)
                .toFloat().coerceIn(0f, 1f),
            provider = SttProvider.ASSEMBLY_AI,
            status = state.providerStatuses.firstOrNull { it.provider == SttProvider.ASSEMBLY_AI },
            keyMask = state.keyMasks[SttProvider.ASSEMBLY_AI],
            pinned = state.routingSelection is RoutingSelection.Manual &&
                state.routingSelection.provider == SttProvider.ASSEMBLY_AI,
            enabled = state.engineEnabled[SttProvider.ASSEMBLY_AI] ?: true,
            onEnabledChange = { onEngineEnabledChange(SttProvider.ASSEMBLY_AI, it) },
            onProbe = { onProbeProvider(SttProvider.ASSEMBLY_AI) },
            onConnect = { onConnectProvider(SttProvider.ASSEMBLY_AI) },
            showMeterAdjust = true,
            meterAdjustLabel = stringResource(R.string.billing_assembly_balance_hint),
            meterAdjustInitial = state.assemblyRemainingUsdEditable,
            onMeterAdjustSave = onAssemblyRemainingUsdSave
        )
        ClearServiceRow(
            name = SttProvider.DEEPGRAM.displayName(context),
            primary = if (official != null) {
                context.getString(
                    R.string.billing_assembly_meter_primary,
                    BillingManager.formatUsd(official, 2),
                    BillingManager.formatUsd(state.initialCredit, 0)
                )
            } else {
                context.getString(
                    R.string.billing_deepgram_spent_app,
                    BillingManager.formatUsd(state.appSpent, 2)
                )
            },
            progress = progressDg,
            provider = SttProvider.DEEPGRAM,
            status = state.providerStatuses.firstOrNull { it.provider == SttProvider.DEEPGRAM },
            keyMask = state.keyMasks[SttProvider.DEEPGRAM],
            pinned = state.routingSelection is RoutingSelection.Manual &&
                state.routingSelection.provider == SttProvider.DEEPGRAM,
            enabled = state.engineEnabled[SttProvider.DEEPGRAM] ?: true,
            onEnabledChange = { onEngineEnabledChange(SttProvider.DEEPGRAM, it) },
            onProbe = { onProbeProvider(SttProvider.DEEPGRAM) },
            onConnect = { onConnectProvider(SttProvider.DEEPGRAM) },
            barColor = barColorDg,
            showMeterAdjust = false,
            meterAdjustLabel = "",
            meterAdjustInitial = "",
            onMeterAdjustSave = {}
        )

        ZeroCostHelpRow()

        Button(
            onClick = onVerifyOffers,
            enabled = hasAnyKey && !state.verifyingOffers,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = KlipNeutrals.Panel,
                contentColor = OnBg,
                disabledContainerColor = KlipNeutrals.PanelDisabled,
                disabledContentColor = Muted
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = if (hasAnyKey) Accent else Muted
            )
            Spacer(Modifier.size(8.dp))
            Text(
                when {
                    !hasAnyKey -> stringResource(R.string.billing_connect_key_first)
                    state.verifyingOffers -> stringResource(R.string.billing_verifying)
                    else -> stringResource(R.string.billing_verify_keys_offers)
                },
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            if (hasAnyKey) {
                stringResource(R.string.billing_verify_hint)
            } else {
                stringResource(R.string.billing_verify_need_key)
            },
            color = Muted,
            fontSize = 12.sp
        )
        if (hasAnyKey) {
            TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.refresh_meters), color = Accent)
            }
        }
    }
}

@Composable
private fun ZeroCostHelpRow() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { open = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(18.dp)
        )
        Text(stringResource(R.string.how_spend_zero), color = Accent, fontSize = 13.sp)
    }
    if (open) {
        HelpDialog(
            title = stringResource(R.string.how_spend_zero),
            body = stringResource(HelpCopy.ZERO_COST),
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun ClearServiceRow(
    name: String,
    primary: String,
    progress: Float,
    provider: SttProvider,
    status: ProviderStatus?,
    keyMask: String?,
    pinned: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onProbe: () -> Unit,
    onConnect: () -> Unit,
    barColor: Color = Accent,
    showMeterAdjust: Boolean,
    meterAdjustLabel: String,
    meterAdjustInitial: String,
    onMeterAdjustSave: (Double) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val info = ProviderCatalog.info(context, provider)
    val hasKey = !keyMask.isNullOrBlank()
    val health = when {
        !hasKey -> ProviderHealth.NO_KEY
        status == null || status.health == ProviderHealth.NO_KEY -> ProviderHealth.KEY_READY
        else -> status.health
    }
    val (dot, _) = when (health) {
        ProviderHealth.ACTIVE_FREE, ProviderHealth.KEY_READY -> "🟢" to Accent
        ProviderHealth.PAUSED_LIMIT, ProviderHealth.COOLDOWN -> "🟡" to Amber
        ProviderHealth.OFFER_ENDED -> "🔴" to Red
        ProviderHealth.NO_KEY, ProviderHealth.UNKNOWN -> "⚪" to Muted
    }
    val headline = if (hasKey) {
        "$name $dot · $primary"
    } else {
        context.getString(R.string.billing_no_key_row, name)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KlipNeutrals.ChipBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                headline,
                color = OnBg,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.action_close else R.string.cd_open
                ),
                tint = Muted,
                modifier = Modifier.size(20.dp)
            )
        }
        if (hasKey) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = barColor,
                trackColor = KlipNeutrals.Card
            )
        } else {
            OutlinedButton(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Accent.copy(alpha = 0.55f))
            ) {
                Text(stringResource(R.string.connect), color = Accent, fontWeight = FontWeight.SemiBold)
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ProviderCatalog.offerText(context, provider), color = Muted, fontSize = 12.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MeterBadge(info.meterKind)
                    Text(info.meterNote, color = Muted, fontSize = 12.sp)
                }
                if (hasKey) {
                    status?.detail
                        ?.takeIf {
                            it.isNotBlank() &&
                                !it.equals(context.getString(R.string.health_no_key), ignoreCase = true) &&
                                !it.contains(context.getString(R.string.health_key_ready), ignoreCase = true)
                        }
                        ?.let {
                            Text(it, color = Muted, fontSize = 12.sp)
                        }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.use_this_service), color = OnBg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = enabled,
                            onCheckedChange = onEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Accent
                            )
                        )
                    }
                    TextButton(onClick = onProbe, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (pinned) {
                                stringResource(R.string.pinned_testing_now)
                            } else {
                                stringResource(R.string.test_service_manual)
                            },
                            color = Accent,
                            fontWeight = if (pinned) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    if (showMeterAdjust) {
                        Text(stringResource(R.string.adjust_counter), color = OnBg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(HelpCopy.METER_ADJUST), color = Muted, fontSize = 12.sp)
                        QuotaAdjustEditor(
                            label = meterAdjustLabel,
                            initialText = meterAdjustInitial,
                            onSave = onMeterAdjustSave
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeterBadge(kind: MeterKind) {
    val context = LocalContext.current
    val color = when (kind) {
        MeterKind.REAL -> Accent
        MeterKind.ESTIMATED -> Amber
        MeterKind.NONE -> Muted
    }
    Text(
        kind.badge(context),
        color = color,
        fontSize = 12.sp,
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
    val context = LocalContext.current
    Text(
        "⚠️ ${context.getString(R.string.zero_cost_warning)}",
        color = Amber,
        fontSize = 12.sp,
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
            Text(stringResource(R.string.adjust), color = Accent)
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
        Text(stringResource(R.string.preflight_title), color = OnBg, fontWeight = FontWeight.SemiBold)
        Text(fileName, color = OnBg, fontSize = 14.sp)
        Text(
            stringResource(R.string.billing_duration_mode, durationLabel, modeLabel),
            color = Muted,
            fontSize = 13.sp
        )
        if (routeHint.isNotBlank()) {
            Text(routeHint, color = Accent, fontSize = 12.sp)
        }
        Text(
            stringResource(
                R.string.billing_estimated_cost,
                BillingManager.formatUsd(estimatedUsd, 4)
            ),
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
                stringResource(R.string.billing_auto_transcribe_share),
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
                    uncheckedTrackColor = KlipNeutrals.SwitchTrack
                )
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel), color = Muted)
            }
            Button(
                onClick = onConfirm,
                enabled = !busy && hardStopMessage == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent,
                    disabledContainerColor = Accent.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.transcribe_now), fontWeight = FontWeight.SemiBold)
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
    onCopyTranscript: (String) -> Unit,
    onOpenSavedMedia: ((SavedMediaInfo) -> Unit)? = null,
    onShareSavedMedia: ((SavedMediaInfo) -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null
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
                stringResource(R.string.billing_recording_history),
                color = OnBg,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Text(stringResource(R.string.billing_no_recordings), color = Muted)
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
                            onCopy = { onCopyTranscript(entry.transcript) },
                            onOpenSavedMedia = onOpenSavedMedia,
                            onShareSavedMedia = onShareSavedMedia,
                            onOpenDownloads = onOpenDownloads
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
    onCopy: () -> Unit,
    onOpenSavedMedia: ((SavedMediaInfo) -> Unit)? = null,
    onShareSavedMedia: ((SavedMediaInfo) -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val saved = SavedMediaActions.fromHistory(
        entry.downloadUri,
        entry.downloadDisplayName,
        entry.downloadIsVideo
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KlipNeutrals.ChipBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(entry.formattedWhen, color = Muted, fontSize = 12.sp)
        Text(entry.fileName, color = OnBg, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(
            buildString {
                entry.provider?.let { append(it); append(" · ") }
                append(
                    stringResource(
                        R.string.billing_duration_mode,
                        BillingManager.formatDuration(entry.durationSeconds),
                        "—"
                    )
                )
                append(" · App ${BillingManager.formatUsd(entry.appCostUsd, 4)}")
                entry.officialDeltaUsd?.let {
                    append(stringResource(R.string.history_row_delta_real, BillingManager.formatUsd(it, 4)))
                }
            },
            color = Muted,
            fontSize = 12.sp
        )
        if (saved != null) {
            Text(
                stringResource(R.string.history_saved_in_downloads, saved.displayName),
                color = Accent,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = { onOpenSavedMedia?.invoke(saved) }) {
                    Text(saved.openLabel(context), color = Accent, fontSize = 12.sp)
                }
                TextButton(onClick = { onOpenDownloads?.invoke() }) {
                    Text(stringResource(R.string.view_in_downloads), color = Accent, fontSize = 12.sp)
                }
                TextButton(onClick = { onShareSavedMedia?.invoke(saved) }) {
                    Text(
                        stringResource(R.string.action_share),
                        color = Accent,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpen, enabled = entry.transcript.isNotBlank()) {
                Text(stringResource(R.string.open), color = Accent)
            }
            TextButton(onClick = onCopy, enabled = entry.transcript.isNotBlank()) {
                Text(
                    stringResource(R.string.action_copy),
                    color = Accent,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = Red) }
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
        title = { Text(stringResource(R.string.cost_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MoneyField(stringResource(R.string.money_field_initial_credit), creditText) { creditText = it }
                MoneyField(stringResource(R.string.money_field_app_cap), capText) { capText = it }
                MoneyField(stringResource(R.string.money_field_min_reserve), reserveText) { reserveText = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.billing_include_diarize_cost),
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
                Text(stringResource(R.string.save), color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Muted)
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
    unfocusedBorderColor = KlipNeutrals.FieldBorder,
    focusedTextColor = OnBg,
    unfocusedTextColor = OnBg,
    cursorColor = Accent,
    focusedLabelColor = Muted,
    unfocusedLabelColor = Muted
)
