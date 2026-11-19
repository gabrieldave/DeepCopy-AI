package com.blackeye.klipnote

import com.blackeye.klipnote.ui.KlipNeutrals
import com.blackeye.klipnote.ui.KlipPalette

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardBg get() = KlipNeutrals.Card
private val Accent get() = KlipPalette.accent
private val OnAccent get() = KlipPalette.onAccent
private val OnBg get() = KlipNeutrals.OnBg
private val Muted get() = KlipNeutrals.Muted
private val Red get() = KlipNeutrals.Red
private val ChipGreen = Color(0xFF0D3B2E)
private val BorderSubtle = Color(0xFF2A3548)

private val LevelBgs = listOf(
    Color(0xFF171F2C),
    Color(0xFF161D2A),
    Color(0xFF151C28),
    Color(0xFF141B27),
    Color(0xFF131A26)
)

@Composable
fun MultiProviderKeysDialog(
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {},
    focusProvider: SttProvider? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by ApiKeyStore.revision.collectAsState()
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    val flushers = remember { mutableStateListOf<() -> Unit>() }

    val groqMask = remember(revision) {
        ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.GROQ)
    }
    val inworldMask = remember(revision) {
        ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.INWORLD)
    }
    val assemblyMask = remember(revision) {
        ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.ASSEMBLY_AI)
    }
    val deepgramMask = remember(revision) {
        ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.DEEPGRAM)
    }
    val projectId = remember(revision) {
        ApiKeyStore.getDeepgramProjectId(context).orEmpty()
    }
    val paidMask = remember(revision) {
        ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.PAID_FALLBACK)
    }
    val paidEndpoint = remember(revision) {
        ApiKeyStore.getPaidFallbackEndpoint(context)
    }
    val paidEngine = remember(revision) {
        ApiKeyStore.getPaidFallbackEngine(context)
    }

    var projectAutoLinked by remember { mutableStateOf(false) }
    var projectLinking by remember { mutableStateOf(false) }

    fun notifyChanged() = onChanged()

    fun linkDeepgramProject(force: Boolean = false) {
        val key = ApiKeyStore.getDeepgram(context)
        if (key.isBlank()) return
        if (!force && !ApiKeyStore.getDeepgramProjectId(context).isNullOrBlank()) return
        projectLinking = true
        scope.launch {
            val linked = withContext(Dispatchers.IO) {
                DeepgramBillingClient.ensureProjectIdLinked(context, key, forceNetwork = force)
            }
            projectLinking = false
            if (linked != null) {
                projectAutoLinked = true
                notifyChanged()
            }
        }
    }

    LaunchedEffect(deepgramMask, projectId) {
        if (deepgramMask != null && projectId.isBlank()) {
            linkDeepgramProject(force = false)
        }
    }

    fun dismissFlushing() {
        flushers.toList().forEach { runCatching { it.invoke() } }
        onDismiss()
    }

    Dialog(
        onDismissRequest = { dismissFlushing() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = maxHeight),
            shape = RoundedCornerShape(20.dp),
            color = CardBg,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        stringResource(R.string.keys_dialog_title),
                        color = OnBg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                    Text(
                        stringResource(R.string.keys_dialog_intro),
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    ZeroCostWarning(modifier = Modifier.padding(top = 8.dp))
                }
                HorizontalDivider(color = BorderSubtle)
                val scroll = rememberScrollState()
                val cardOffsets = remember { mutableMapOf<SttProvider, Int>() }
                LaunchedEffect(focusProvider) {
                    val target = focusProvider ?: return@LaunchedEffect
                    repeat(12) {
                        val y = cardOffsets[target]
                        if (y != null) {
                            scroll.animateScrollTo(y.coerceAtLeast(0))
                            return@LaunchedEffect
                        }
                        delay(40)
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight - 148.dp)
                        .clipToBounds()
                        .verticalScroll(scroll)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AutosaveKeyCard(
                        title = "1 · Groq Free",
                        provider = SttProvider.GROQ_FREE,
                        mask = groqMask,
                        cardBg = LevelBgs[0],
                        highlighted = focusProvider == SttProvider.GROQ_FREE,
                        onPositioned = { cardOffsets[SttProvider.GROQ_FREE] = it },
                        flushers = flushers,
                        onSave = {
                            ApiKeyStore.setGroq(context, it)
                            notifyChanged()
                        },
                        onClear = {
                            ApiKeyStore.clearGroq(context)
                            notifyChanged()
                        }
                    )
                    AutosaveKeyCard(
                        title = "2 · Inworld Free (Basic Base64)",
                        provider = SttProvider.INWORLD,
                        mask = inworldMask,
                        cardBg = LevelBgs[1],
                        highlighted = focusProvider == SttProvider.INWORLD,
                        onPositioned = { cardOffsets[SttProvider.INWORLD] = it },
                        flushers = flushers,
                        onSave = {
                            ApiKeyStore.setInworld(context, it)
                            notifyChanged()
                        },
                        onClear = {
                            ApiKeyStore.clearInworld(context)
                            notifyChanged()
                        }
                    )
                    AutosaveKeyCard(
                        title = "3 · AssemblyAI",
                        provider = SttProvider.ASSEMBLY_AI,
                        mask = assemblyMask,
                        cardBg = LevelBgs[2],
                        highlighted = focusProvider == SttProvider.ASSEMBLY_AI,
                        onPositioned = { cardOffsets[SttProvider.ASSEMBLY_AI] = it },
                        flushers = flushers,
                        onSave = {
                            ApiKeyStore.setAssemblyAi(context, it)
                            notifyChanged()
                        },
                        onClear = {
                            ApiKeyStore.clearAssemblyAi(context)
                            notifyChanged()
                        }
                    )
                    DeepgramAutosaveCard(
                        mask = deepgramMask,
                        projectId = projectId,
                        projectAutoLinked = projectAutoLinked,
                        projectLinking = projectLinking,
                        cardBg = LevelBgs[3],
                        highlighted = focusProvider == SttProvider.DEEPGRAM,
                        onPositioned = { cardOffsets[SttProvider.DEEPGRAM] = it },
                        flushers = flushers,
                        onSaveKey = {
                            ApiKeyStore.setDeepgram(context, it)
                            notifyChanged()
                            projectAutoLinked = false
                            linkDeepgramProject(force = true)
                        },
                        onClearKey = {
                            ApiKeyStore.clearDeepgram(context)
                            notifyChanged()
                        },
                        onSaveProject = {
                            ApiKeyStore.setDeepgramProjectId(context, it)
                            projectAutoLinked = false
                            notifyChanged()
                        }
                    )
                    PaidAutosaveCard(
                        mask = paidMask,
                        endpoint = paidEndpoint,
                        engine = paidEngine,
                        cardBg = LevelBgs[4],
                        highlighted = focusProvider == SttProvider.PAID_FALLBACK,
                        onPositioned = { cardOffsets[SttProvider.PAID_FALLBACK] = it },
                        flushers = flushers,
                        onSave = { key, endpoint, engine ->
                            ApiKeyStore.setPaidFallback(context, key)
                            ApiKeyStore.setPaidFallbackEndpoint(context, endpoint)
                            ApiKeyStore.setPaidFallbackEngine(context, engine)
                            notifyChanged()
                        },
                        onClear = {
                            ApiKeyStore.clearPaidFallback(context)
                            notifyChanged()
                        }
                    )
                }
                HorizontalDivider(color = BorderSubtle)
                Button(
                    onClick = { dismissFlushing() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.ready), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AutosaveKeyCard(
    title: String,
    provider: SttProvider,
    mask: String?,
    cardBg: Color,
    flushers: MutableList<() -> Unit>,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    highlighted: Boolean = false,
    onPositioned: (Int) -> Unit = {}
) {
    var replacing by remember(mask) { mutableStateOf(mask == null) }
    var value by remember { mutableStateOf("") }
    var lastLen by remember { mutableIntStateOf(0) }

    fun commit(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        onSave(trimmed)
        value = ""
        lastLen = 0
        replacing = false
    }

    DisposableEffect(replacing, value) {
        val action: () -> Unit = {
            if (replacing && value.isNotBlank()) commit(value)
        }
        flushers.add(action)
        onDispose { flushers.remove(action) }
    }

    LaunchedEffect(value, replacing) {
        if (!replacing || value.isBlank()) return@LaunchedEffect
        delay(600)
        if (value.isNotBlank()) commit(value)
    }

    LevelCard(
        title = title,
        cardBg = cardBg,
        provider = provider,
        highlighted = highlighted,
        onPositioned = onPositioned
    ) {
        if (mask != null && !replacing) {
            SavedChip(mask)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { replacing = true; value = ""; lastLen = 0 }) {
                    Text(stringResource(R.string.change), color = Accent)
                }
                FilledTonalButton(
                    onClick = onClear,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Red.copy(alpha = 0.18f),
                        contentColor = Red
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        } else {
            KeyInputRow(
                value = value,
                onValueChange = { new ->
                    val jump = new.length - lastLen >= 12
                    lastLen = new.length
                    value = new
                    if (jump && new.trim().isNotBlank()) commit(new)
                },
                onPaste = { pasted -> commit(pasted) },
                placeholder = stringResource(R.string.keys_hint_generic)
            )
            if (mask != null) {
                TextButton(onClick = { replacing = false; value = ""; lastLen = 0 }) {
                    Text(stringResource(R.string.cancel), color = Muted)
                }
            }
        }
    }
}

@Composable
private fun DeepgramAutosaveCard(
    mask: String?,
    projectId: String,
    projectAutoLinked: Boolean,
    projectLinking: Boolean,
    cardBg: Color,
    flushers: MutableList<() -> Unit>,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onSaveProject: (String) -> Unit,
    highlighted: Boolean = false,
    onPositioned: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var replacing by remember(mask) { mutableStateOf(mask == null) }
    var value by remember { mutableStateOf("") }
    var lastLen by remember { mutableIntStateOf(0) }
    var editingProject by remember(projectId) { mutableStateOf(false) }
    var projectText by remember(projectId) { mutableStateOf(projectId) }
    var projectLastLen by remember { mutableIntStateOf(projectId.length) }

    fun commitKey(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        onSaveKey(trimmed)
        value = ""
        lastLen = 0
        replacing = false
    }

    fun commitProject(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        onSaveProject(trimmed)
        projectText = trimmed
        editingProject = false
    }

    DisposableEffect(replacing, value, editingProject, projectText) {
        val action: () -> Unit = {
            if (replacing && value.isNotBlank()) commitKey(value)
            if (editingProject && projectText.isNotBlank()) commitProject(projectText)
        }
        flushers.add(action)
        onDispose { flushers.remove(action) }
    }

    LaunchedEffect(value, replacing) {
        if (!replacing || value.isBlank()) return@LaunchedEffect
        delay(600)
        if (value.isNotBlank()) commitKey(value)
    }

    LaunchedEffect(projectText, editingProject) {
        if (!editingProject || projectText.isBlank()) return@LaunchedEffect
        delay(600)
        if (projectText.isNotBlank()) commitProject(projectText)
    }

    LevelCard(
        title = "4 · Deepgram",
        cardBg = cardBg,
        provider = SttProvider.DEEPGRAM,
        highlighted = highlighted,
        onPositioned = onPositioned
    ) {
        if (mask != null && !replacing) {
            SavedChip(mask)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { replacing = true; value = ""; lastLen = 0 }) {
                    Text(stringResource(R.string.change), color = Accent)
                }
                FilledTonalButton(
                    onClick = onClearKey,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Red.copy(alpha = 0.18f),
                        contentColor = Red
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        } else {
            KeyInputRow(
                value = value,
                onValueChange = { new ->
                    val jump = new.length - lastLen >= 12
                    lastLen = new.length
                    value = new
                    if (jump && new.trim().isNotBlank()) commitKey(new)
                },
                onPaste = { pasted -> commitKey(pasted) },
                placeholder = stringResource(R.string.keys_hint_generic)
            )
            if (mask != null) {
                TextButton(onClick = { replacing = false; value = ""; lastLen = 0 }) {
                    Text(stringResource(R.string.cancel), color = Muted)
                }
            }
        }

        when {
            projectLinking -> {
                Text(stringResource(R.string.linking_project_id), color = Muted, fontSize = 12.sp)
            }
            projectId.isNotBlank() && !editingProject -> {
                val suffix = if (projectId.length <= 4) projectId else projectId.takeLast(4)
                val label = if (projectAutoLinked) {
                    "✓ Project ID auto-vinculado (••••$suffix)"
                } else {
                    "✓ Project ID ••••$suffix"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(label, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    TextButton(
                        onClick = {
                            editingProject = true
                            projectText = projectId
                            projectLastLen = projectId.length
                        }
                    ) {
                        Text(stringResource(R.string.edit), color = Muted, fontSize = 12.sp)
                    }
                }
            }
            else -> {
                if (mask != null || editingProject) {
                    OutlinedTextField(
                        value = projectText,
                        onValueChange = { new ->
                            val jump = new.length - projectLastLen >= 12
                            projectLastLen = new.length
                            projectText = new
                            if (jump && new.trim().isNotBlank()) commitProject(new)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.project_id)) },
                        placeholder = { Text(stringResource(R.string.project_id), color = Muted) },
                        colors = keysFieldColors(),
                        trailingIcon = {
                            OutlinedButton(
                                onClick = {
                                    readClipboard(context)?.let { commitProject(it) }
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(stringResource(R.string.paste), color = Accent, fontSize = 12.sp)
                            }
                        }
                    )
                    if (editingProject && projectId.isNotBlank()) {
                        TextButton(onClick = {
                            editingProject = false
                            projectText = projectId
                        }) {
                            Text(stringResource(R.string.cancel), color = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaidAutosaveCard(
    mask: String?,
    endpoint: String,
    engine: PaidFallbackEngine,
    cardBg: Color,
    flushers: MutableList<() -> Unit>,
    onSave: (String, String, PaidFallbackEngine) -> Unit,
    onClear: () -> Unit,
    highlighted: Boolean = false,
    onPositioned: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var replacing by remember(mask) { mutableStateOf(mask == null) }
    var value by remember { mutableStateOf("") }
    var lastLen by remember { mutableIntStateOf(0) }
    // Estado local puro: NO re-sincronizar desde store en cada revision/engine write.
    var endpointText by remember { mutableStateOf(endpoint) }
    var engineSel by remember { mutableStateOf(engine) }

    fun commit(raw: String = value) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        onSave(trimmed, endpointText.trim(), engineSel)
        value = ""
        lastLen = 0
        replacing = false
    }

    fun persistPrefsQuiet() {
        val appCtx = context.applicationContext
        val eng = engineSel
        val ep = endpointText.trim()
        scope.launch(Dispatchers.IO) {
            ApiKeyStore.setPaidFallbackEngine(appCtx, eng, notify = false)
            if (eng == PaidFallbackEngine.MICROSOFT_MAI && ep.isNotBlank()) {
                ApiKeyStore.setPaidFallbackEndpoint(appCtx, ep, notify = false)
            }
        }
    }

    DisposableEffect(replacing, value, endpointText, engineSel) {
        val action: () -> Unit = {
            if (replacing && value.isNotBlank()) {
                commit(value)
            } else {
                // Listo / dismiss: persistir motor+endpoint sin verifyAll.
                persistPrefsQuiet()
            }
        }
        flushers.add(action)
        onDispose { flushers.remove(action) }
    }

    LaunchedEffect(value, replacing) {
        if (!replacing || value.isBlank()) return@LaunchedEffect
        delay(600)
        if (value.isNotBlank()) commit(value)
    }

    // Si ya hay key pagada, persistir preferencia de motor en IO con debounce — sin onChanged.
    LaunchedEffect(engineSel) {
        if (mask == null) return@LaunchedEffect
        if (engineSel == engine) return@LaunchedEffect
        delay(300)
        withContext(Dispatchers.IO) {
            ApiKeyStore.setPaidFallbackEngine(context.applicationContext, engineSel, notify = false)
        }
    }

    LaunchedEffect(endpointText) {
        if (mask == null) return@LaunchedEffect
        if (engineSel != PaidFallbackEngine.MICROSOFT_MAI) return@LaunchedEffect
        if (endpointText.isBlank() || endpointText.trim() == endpoint.trim()) return@LaunchedEffect
        delay(300)
        withContext(Dispatchers.IO) {
            ApiKeyStore.setPaidFallbackEndpoint(
                context.applicationContext,
                endpointText.trim(),
                notify = false
            )
        }
    }

    val focusManager = LocalFocusManager.current
    LevelCard(
        title = "5 · Respaldo Pagado",
        cardBg = cardBg,
        provider = SttProvider.PAID_FALLBACK,
        highlighted = highlighted,
        onPositioned = onPositioned
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EngineChip(
                label = stringResource(R.string.paid_engine_groq),
                selected = engineSel == PaidFallbackEngine.GROQ_PAID,
                onClick = {
                    focusManager.clearFocus(force = true)
                    engineSel = PaidFallbackEngine.GROQ_PAID
                }
            )
            EngineChip(
                label = stringResource(R.string.paid_engine_mai),
                selected = engineSel == PaidFallbackEngine.MICROSOFT_MAI,
                onClick = {
                    focusManager.clearFocus(force = true)
                    engineSel = PaidFallbackEngine.MICROSOFT_MAI
                }
            )
        }
        if (mask != null && !replacing) {
            SavedChip(mask)
            AnimatedVisibility(visible = engineSel == PaidFallbackEngine.MICROSOFT_MAI) {
                Text(
                    endpointText.ifBlank { endpoint.ifBlank { "(sin endpoint)" } },
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { replacing = true; value = ""; lastLen = 0 }) {
                    Text(stringResource(R.string.change), color = Accent)
                }
                FilledTonalButton(
                    onClick = onClear,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Red.copy(alpha = 0.18f),
                        contentColor = Red
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        } else {
            KeyInputRow(
                value = value,
                onValueChange = { new ->
                    val jump = new.length - lastLen >= 12
                    lastLen = new.length
                    value = new
                    if (jump && new.trim().isNotBlank()) commit(new)
                },
                onPaste = { pasted -> commit(pasted) },
                placeholder = stringResource(R.string.keys_hint_generic)
            )
            AnimatedVisibility(visible = engineSel == PaidFallbackEngine.MICROSOFT_MAI) {
                OutlinedTextField(
                    value = endpointText,
                    onValueChange = { endpointText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(stringResource(R.string.keys_paid_endpoint_placeholder), color = Muted)
                    },
                    colors = keysFieldColors()
                )
            }
            if (mask != null) {
                TextButton(onClick = { replacing = false; value = ""; lastLen = 0 }) {
                    Text(stringResource(R.string.cancel), color = Muted)
                }
            }
        }
    }
}

@Composable
private fun EngineChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Accent
                )
            }
        } else null,
        border = BorderStroke(
            1.dp,
            if (selected) Accent.copy(alpha = 0.7f) else BorderSubtle
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0xFF0F1623),
            labelColor = Muted,
            selectedContainerColor = Accent.copy(alpha = 0.22f),
            selectedLabelColor = Accent
        )
    )
}

@Composable
private fun LevelCard(
    title: String,
    cardBg: Color,
    provider: SttProvider? = null,
    highlighted: Boolean = false,
    onPositioned: (Int) -> Unit = {},
    content: @Composable () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                onPositioned(coords.positionInParent().y.toInt())
            },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            if (highlighted) 2.dp else 1.dp,
            if (highlighted) Accent else BorderSubtle
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = cardBg
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (provider != null) ProviderGuide(provider)
            content()
        }
    }
}

/** Offer text + “How do I get my key?” toggle that opens the provider page. */
@Composable
private fun ProviderGuide(provider: SttProvider) {
    val context = LocalContext.current
    val info = ProviderCatalog.info(context, provider)
    val uriHandler = LocalUriHandler.current
    var showSteps by remember { mutableStateOf(false) }
    Text(
        ProviderCatalog.offerText(context, provider),
        color = if (info.isFree) OnBg else Color(0xFFFBBF24),
        fontSize = 12.sp
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { runCatching { uriHandler.openUri(info.keyUrl) } },
            border = BorderStroke(1.dp, Accent.copy(alpha = 0.55f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(stringResource(R.string.get_my_key), color = Accent, fontSize = 12.sp)
        }
        TextButton(onClick = { showSteps = !showSteps }) {
            Text(
                if (showSteps) stringResource(R.string.keys_hide_steps) else stringResource(R.string.keys_how),
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
    AnimatedVisibility(visible = showSteps) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            info.steps.forEachIndexed { i, step ->
                Text("${i + 1}. $step", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SavedChip(mask: String) {
    Surface(
        color = ChipGreen,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.45f))
    ) {
        Text(
            "✓ Guardada $mask",
            color = Accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun KeyInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: (String) -> Unit,
    placeholder: String
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        placeholder = { Text(placeholder, color = Muted) },
        colors = keysFieldColors(),
        trailingIcon = {
            OutlinedButton(
                onClick = {
                    val pasted = readClipboard(context)
                    if (!pasted.isNullOrBlank()) onPaste(pasted)
                },
                modifier = Modifier.padding(end = 4.dp),
                border = BorderStroke(1.dp, Accent.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(
                    horizontal = 10.dp,
                    vertical = 4.dp
                )
            ) {
                Text(stringResource(R.string.paste), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun keysFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color(0xFF243044),
    focusedTextColor = OnBg,
    unfocusedTextColor = OnBg,
    cursorColor = Accent,
    focusedLabelColor = Muted,
    unfocusedLabelColor = Muted
)

private fun readClipboard(context: Context): String? {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
