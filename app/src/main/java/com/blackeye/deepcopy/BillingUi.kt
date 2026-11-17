package com.blackeye.deepcopy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
    val languageMode: LanguageMode = LanguageMode.AUTO
)

@Composable
fun CreditControlPanel(
    state: CreditUiState,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCostSettings: () -> Unit
) {
    val official = state.snapshot?.balanceUsd
    val remainingForBar = official ?: (state.initialCredit - state.appSpent)
    val progress = (remainingForBar / state.initialCredit).toFloat().coerceIn(0f, 1f)
    val barColor = when {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Control de Crédito \$200 USD",
                    color = OnBg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    "Auditor Nova-3 · reserva y tope activos",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refrescar saldo",
                    tint = Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onOpenCostSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes de costos", tint = Muted)
            }
            Icon(
                if (state.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Muted
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = Color(0xFF0F1623)
        )

        AnimatedVisibility(visible = state.expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state.snapshot?.status) {
                    DeepgramBillingClient.BillingScopeStatus.MEMBER_KEY_NO_BILLING_SCOPE -> {
                        Text(
                            state.snapshot.message
                                ?: "API Key sin permiso billing:read (rol Member).",
                            color = Amber,
                            fontSize = 12.sp
                        )
                    }
                    DeepgramBillingClient.BillingScopeStatus.UNAUTHORIZED -> {
                        Text(
                            state.snapshot.message ?: "API Key no autorizada (401)",
                            color = Red,
                            fontSize = 12.sp
                        )
                    }
                    DeepgramBillingClient.BillingScopeStatus.ERROR -> {
                        Text(
                            state.snapshot.message ?: "Error al consultar saldo",
                            color = Red,
                            fontSize = 12.sp
                        )
                    }
                    else -> Unit
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricColumn(
                        modifier = Modifier.weight(1f),
                        title = "Saldo Oficial",
                        value = when {
                            official != null -> BillingManager.formatUsd(official, 2)
                            state.snapshot?.status ==
                                DeepgramBillingClient.BillingScopeStatus.MEMBER_KEY_NO_BILLING_SCOPE ->
                                "N/D Key Member"
                            else -> "—"
                        }
                    )
                    MetricColumn(
                        modifier = Modifier.weight(1f),
                        title = "Gastado",
                        value = buildString {
                            if (officialSpent != null) {
                                append("Oficial ")
                                append(BillingManager.formatUsd(officialSpent, 2))
                                append("\n")
                            }
                            append("App ")
                            append(BillingManager.formatUsd(state.appSpent, 4))
                        }
                    )
                    MetricColumn(
                        modifier = Modifier.weight(1f),
                        title = "Δ Oficial−App",
                        value = delta?.let { BillingManager.formatUsd(it, 4) } ?: "—"
                    )
                }

                Text(
                    "Te alcanza para ~${String.format("%.1f", hoursLeft)} horas de video al ritmo actual",
                    color = Muted,
                    fontSize = 12.sp
                )

                TextButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Accent)
                    Spacer(Modifier.size(6.dp))
                    Text("Ver Historial de Costos", color = Accent)
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    modifier: Modifier,
    title: String,
    value: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F1623))
            .padding(10.dp)
    ) {
        Text(title, color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = OnBg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Historial de Costos",
                color = OnBg,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Text("Sin transcripciones registradas aún.", color = Muted)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        HistoryRow(entry = entry, onCopy = { onCopyTranscript(entry.transcript) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: BillingManager.HistoryEntry,
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
            "Duración ${BillingManager.formatDuration(entry.durationSeconds)} · " +
                "App ${BillingManager.formatUsd(entry.appCostUsd, 4)}" +
                (entry.officialDeltaUsd?.let {
                    " · Δ real ${BillingManager.formatUsd(it, 4)}"
                } ?: ""),
            color = Muted,
            fontSize = 12.sp
        )
        TextButton(onClick = onCopy, enabled = entry.transcript.isNotBlank()) {
            Text("Recuperar / copiar texto", color = Accent)
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Color(0xFF243044),
            focusedTextColor = OnBg,
            unfocusedTextColor = OnBg,
            cursorColor = Accent,
            focusedLabelColor = Muted,
            unfocusedLabelColor = Muted
        )
    )
}
