package com.blackeye.deepcopy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B0F17)
private val Card = Color(0xFF151C28)
private val Accent = Color(0xFF10B981)
private val OnBg = Color(0xFFE8EEF7)
private val Muted = Color(0xFF8B9BB4)

private val AiPrompts = listOf(
    "Resume este contenido de forma clara y accionable:\n\n",
    "Extrae las ideas clave, decisiones y pendientes:\n\n",
    "Reescribe esto como un brief listo para pegar en otra IA:\n\n"
)

sealed class UiPhase {
    data object Idle : UiPhase()
    data object Extracting : UiPhase()
    data object Transcribing : UiPhase()
    data class Done(val meta: TranscriptionResult) : UiPhase()
    data class Error(val message: String) : UiPhase()
}

class MainActivity : ComponentActivity() {

    private var pendingShareUri by mutableStateOf<Uri?>(null)
    private var shareNonce by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            DeepCopyTheme {
                DeepCopyApp(
                    shareUri = pendingShareUri,
                    shareNonce = shareNonce,
                    onShareConsumed = { pendingShareUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND) return
        val uri = intent.getParcelableExtraCompat(Intent.EXTRA_STREAM)
        if (uri != null) {
            pendingShareUri = uri
            shareNonce += 1
        }
    }
}

@Suppress("DEPRECATION")
private fun Intent.getParcelableExtraCompat(key: String): Uri? {
    return if (android.os.Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, Uri::class.java)
    } else {
        getParcelableExtra(key) as? Uri
    }
}

@Composable
private fun DeepCopyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Bg,
            surface = Card,
            onBackground = OnBg,
            onSurface = OnBg,
            secondary = Accent
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeepCopyApp(
    shareUri: Uri?,
    shareNonce: Int,
    onShareConsumed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var apiKey by remember { mutableStateOf(ApiKeyStore.get(context)) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var languageMode by remember { mutableStateOf(LanguageMode.AUTO) }
    var diarize by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf<UiPhase>(UiPhase.Idle) }
    var transcriptText by remember { mutableStateOf("") }
    var promptMenu by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runTranscription(
                    context = context,
                    uri = uri,
                    apiKey = apiKey,
                    mode = languageMode,
                    diarize = diarize,
                    onPhase = { phase = it },
                    onText = { transcriptText = it },
                    snackbar = snackbar
                )
            }
        }
    }

    LaunchedEffect(shareNonce, shareUri) {
        val uri = shareUri ?: return@LaunchedEffect
        onShareConsumed()
        runTranscription(
            context = context,
            uri = uri,
            apiKey = apiKey,
            mode = languageMode,
            diarize = diarize,
            onPhase = { phase = it },
            onText = { transcriptText = it },
            snackbar = snackbar
        )
    }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            color = OnBg
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showKeyDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = Accent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bg,
                    titleContentColor = OnBg
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusCard(phase = phase)

            ControlsCard(
                languageMode = languageMode,
                onModeChange = { languageMode = it },
                diarize = diarize,
                onDiarizeChange = { diarize = it },
                busy = phase is UiPhase.Extracting || phase is UiPhase.Transcribing,
                onPick = {
                    if (apiKey.isBlank()) {
                        showKeyDialog = true
                        Toast.makeText(
                            context,
                            context.getString(R.string.error_no_key),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        picker.launch(arrayOf("audio/*", "video/*"))
                    }
                }
            )

            if (transcriptText.isNotBlank()) {
                ResultCard(
                    text = transcriptText,
                    onTextChange = { transcriptText = it },
                    promptMenuExpanded = promptMenu,
                    onPromptMenuChange = { promptMenu = it },
                    onCopyPlain = {
                        copyToClipboard(context, transcriptText)
                        scope.launch {
                            snackbar.showSnackbar(context.getString(R.string.copied))
                        }
                    },
                    onCopyWithPrompt = { prefix ->
                        copyToClipboard(context, prefix + transcriptText)
                        promptMenu = false
                        scope.launch {
                            snackbar.showSnackbar(context.getString(R.string.copied))
                        }
                    },
                    onSendToAi = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, transcriptText)
                        }
                        context.startActivity(
                            Intent.createChooser(send, context.getString(R.string.send_to_ai))
                        )
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showKeyDialog) {
        ApiKeyDialog(
            initial = apiKey,
            onDismiss = { showKeyDialog = false },
            onSave = { value ->
                ApiKeyStore.set(context, value)
                apiKey = ApiKeyStore.get(context)
                showKeyDialog = false
            }
        )
    }
}

@Composable
private fun StatusCard(phase: UiPhase) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp)
    ) {
        when (phase) {
            UiPhase.Idle -> {
                Text("Comparte un audio/video o selecciónalo aquí.", color = Muted)
            }
            UiPhase.Extracting -> BusyRow(stringResource(R.string.extracting))
            UiPhase.Transcribing -> BusyRow(stringResource(R.string.transcribing))
            is UiPhase.Done -> {
                val m = phase.meta
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.ready), color = Accent, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            m.durationSeconds?.let {
                                append(String.format("%.1fs", it))
                                append(" · ")
                            }
                            append(m.detectedLanguage?.uppercase() ?: "—")
                            append(" · ")
                            append("${m.wordCount} palabras")
                        },
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
            }
            is UiPhase.Error -> {
                Text(phase.message, color = Color(0xFFF87171))
            }
        }
    }
}

@Composable
private fun BusyRow(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Accent,
            strokeWidth = 2.5.dp
        )
        Text(label, color = OnBg)
    }
}

@Composable
private fun ControlsCard(
    languageMode: LanguageMode,
    onModeChange: (LanguageMode) -> Unit,
    diarize: Boolean,
    onDiarizeChange: (Boolean) -> Unit,
    busy: Boolean,
    onPick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Idioma", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(
                selected = languageMode == LanguageMode.AUTO,
                label = stringResource(R.string.mode_auto),
                enabled = !busy,
                onClick = { onModeChange(LanguageMode.AUTO) }
            )
            ModeChip(
                selected = languageMode == LanguageMode.MULTI,
                label = stringResource(R.string.mode_multi),
                enabled = !busy,
                onClick = { onModeChange(LanguageMode.MULTI) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.diarize), color = OnBg)
            Switch(
                checked = diarize,
                onCheckedChange = onDiarizeChange,
                enabled = !busy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = Color(0xFF1F2937)
                )
            )
        }

        Button(
            onClick = onPick,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color.Black,
                disabledContainerColor = Accent.copy(alpha = 0.35f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.select_media),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ModeChip(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0xFF0F1623),
            labelColor = Muted,
            selectedContainerColor = Accent.copy(alpha = 0.2f),
            selectedLabelColor = Accent
        )
    )
}

@Composable
private fun ResultCard(
    text: String,
    onTextChange: (String) -> Unit,
    promptMenuExpanded: Boolean,
    onPromptMenuChange: (Boolean) -> Unit,
    onCopyPlain: () -> Unit,
    onCopyWithPrompt: (String) -> Unit,
    onSendToAi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Transcripción", color = Muted, fontSize = 12.sp)
        SelectionContainer {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = OnBg,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 22.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color(0xFF243044),
                    focusedContainerColor = Color(0xFF0F1623),
                    unfocusedContainerColor = Color(0xFF0F1623),
                    cursorColor = Accent
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                icon = Icons.Default.ContentCopy,
                label = stringResource(R.string.copy_plain),
                onClick = onCopyPlain
            )

            Box {
                ActionButton(
                    icon = Icons.Default.SmartToy,
                    label = stringResource(R.string.copy_with_prompt),
                    onClick = { onPromptMenuChange(true) }
                )
                DropdownMenu(
                    expanded = promptMenuExpanded,
                    onDismissRequest = { onPromptMenuChange(false) }
                ) {
                    AiPrompts.forEachIndexed { index, prefix ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (index) {
                                        0 -> "Resumen accionable"
                                        1 -> "Ideas / decisiones"
                                        else -> "Brief para otra IA"
                                    }
                                )
                            },
                            onClick = { onCopyWithPrompt(prefix) }
                        )
                    }
                }
            }

            ActionButton(
                icon = Icons.Default.Send,
                label = stringResource(R.string.send_to_ai),
                onClick = onSendToAi
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1A2436),
            contentColor = OnBg
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Accent)
        Spacer(Modifier.size(8.dp))
        Text(label)
    }
}

@Composable
private fun ApiKeyDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        titleContentColor = OnBg,
        textContentColor = OnBg,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.api_key_hint), color = Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color(0xFF243044),
                    focusedTextColor = OnBg,
                    unfocusedTextColor = OnBg,
                    cursorColor = Accent
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) {
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

private fun copyToClipboard(context: android.content.Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("DeepCopy AI", text))
}

private suspend fun runTranscription(
    context: android.content.Context,
    uri: Uri,
    apiKey: String,
    mode: LanguageMode,
    diarize: Boolean,
    onPhase: (UiPhase) -> Unit,
    onText: (String) -> Unit,
    snackbar: SnackbarHostState
) {
    if (apiKey.isBlank()) {
        onPhase(UiPhase.Error(context.getString(R.string.error_no_key)))
        return
    }

    var extracted: AudioExtractor.ExtractedAudio? = null
    try {
        onPhase(UiPhase.Extracting)
        extracted = withContext(Dispatchers.IO) {
            AudioExtractor.prepareAudio(context, uri)
        }

        onPhase(UiPhase.Transcribing)
        val result = withContext(Dispatchers.IO) {
            DeepgramClient.transcribe(
                apiKey = apiKey,
                file = extracted.file,
                mimeType = extracted.mimeType,
                mode = mode,
                diarize = diarize
            )
        }

        onText(result.transcript)
        onPhase(UiPhase.Done(result))
        if (result.transcript.isNotBlank()) {
            copyToClipboard(context, result.transcript)
            snackbar.showSnackbar(context.getString(R.string.copied))
        }
    } catch (e: Exception) {
        onPhase(
            UiPhase.Error(
                e.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.error_generic)
            )
        )
    } finally {
        AudioExtractor.cleanup(extracted)
    }
}
