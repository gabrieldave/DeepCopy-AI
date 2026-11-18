package com.blackeye.klipnote

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.collectAsState
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
import java.io.File

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
    data class Downloading(val label: String) : UiPhase()
    data object Extracting : UiPhase()
    data class Transcribing(val motorHint: String = "") : UiPhase()
    data class Done(val meta: TranscriptionResult) : UiPhase()
    data class Error(
        val message: String,
        val isManualTest: Boolean = false
    ) : UiPhase()
}

data class PreFlightState(
    val uri: Uri,
    val fileName: String,
    val durationSeconds: Double,
    val estimate: BillingManager.CostEstimate,
    val hardStopMessage: String?,
    val needsGiantConfirm: Boolean,
    val estimatedUsdDisplay: Double,
    val routeHint: String
)

class MainActivity : ComponentActivity() {

    private var pendingShareUri by mutableStateOf<Uri?>(null)
    private var pendingShareText by mutableStateOf<String?>(null)
    private var shareNonce by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ingestMigrationKey(intent)
        handleShareIntent(intent)
        setContent {
            KlipNoteTheme {
                KlipNoteApp(
                    shareUri = pendingShareUri,
                    shareText = pendingShareText,
                    shareNonce = shareNonce,
                    onShareConsumed = {
                        pendingShareUri = null
                        pendingShareText = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ingestMigrationKey(intent)
        handleShareIntent(intent)
    }

    private fun ingestMigrationKey(intent: Intent?) {
        ApiKeyStore.ingestMigrationFile(this)
        if (intent == null) return
        val raw = intent.getStringExtra(ApiKeyStore.MIGRATE_INTENT_EXTRA)
        if (ApiKeyStore.ingestMigrationExtra(this, raw)) {
            intent.removeExtra(ApiKeyStore.MIGRATE_INTENT_EXTRA)
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND) return
        val uri = intent.getParcelableExtraCompat(Intent.EXTRA_STREAM)
        if (uri != null) {
            pendingShareUri = uri
            shareNonce += 1
            return
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            pendingShareText = text
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
private fun KlipNoteTheme(content: @Composable () -> Unit) {
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
private fun KlipNoteApp(
    shareUri: Uri?,
    shareText: String?,
    shareNonce: Int,
    onShareConsumed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val sealReady by SealDownloader.ready.collectAsState()

    var showKeyDialog by remember { mutableStateOf(false) }
    var languageMode by remember { mutableStateOf(LanguageMode.AUTO) }
    var diarize by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf<UiPhase>(UiPhase.Idle) }
    var linkInput by remember { mutableStateOf("") }
    var linkMode by remember { mutableStateOf(SealDownloader.LinkMode.TEXT_ONLY) }
    var downloadKind by remember { mutableStateOf(SealDownloader.DownloadKind.AUDIO_M4A) }
    var updatingYtDlp by remember { mutableStateOf(false) }
    var transcriptText by remember { mutableStateOf("") }
    var lastResultMeta by remember { mutableStateOf<TranscriptionResult?>(null) }
    var promptMenu by remember { mutableStateOf(false) }
    var copyFeedback by remember { mutableStateOf(false) }

    var creditExpanded by remember { mutableStateOf(true) }
    var creditRefreshing by remember { mutableStateOf(false) }
    var verifyingOffers by remember { mutableStateOf(false) }
    var balanceSnapshot by remember {
        mutableStateOf<DeepgramBillingClient.BalanceSnapshot?>(null)
    }
    var providerStatuses by remember {
        mutableStateOf(OfferHealthMonitor.seedFromStore(context))
    }
    var keyMasks by remember {
        mutableStateOf(readKeyMasks(context))
    }
    var routingSelection by remember { mutableStateOf<RoutingSelection>(RoutingSelection.Auto) }
    var routeChain by remember {
        mutableStateOf(SttRouter.summarizeActiveRoute(context, RoutingSelection.Auto).chainLabel)
    }
    var appSpent by remember { mutableStateOf(BillingManager.getAppSpent(context)) }
    var localUsage by remember { mutableStateOf(BillingManager.getLocalUsage(context)) }
    var includeDiarizeCost by remember {
        mutableStateOf(BillingManager.getIncludeDiarizeCost(context))
    }
    var autoTranscribeOnShare by remember {
        mutableStateOf(BillingManager.getAutoTranscribeOnShare(context))
    }
    var allowPaidFallback by remember {
        mutableStateOf(BillingManager.getAllowPaidFallback(context))
    }
    var initialCredit by remember { mutableStateOf(BillingManager.getInitialCredit(context)) }
    var appCap by remember { mutableStateOf(BillingManager.getAppCap(context)) }
    var minReserve by remember { mutableStateOf(BillingManager.getMinReserve(context)) }
    var groqRemaining by remember { mutableStateOf(BillingManager.getGroqRemainingSeconds(context)) }
    var inworldRemaining by remember {
        mutableStateOf(BillingManager.getInworldRemainingSeconds(context))
    }
    var assemblyRemaining by remember {
        mutableStateOf(BillingManager.getAssemblyRemainingSeconds(context))
    }

    var preFlight by remember { mutableStateOf<PreFlightState?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showCostSettings by remember { mutableStateOf(false) }
    var giantConfirm by remember { mutableStateOf<PreFlightState?>(null) }
    var historyEntries by remember { mutableStateOf(BillingManager.loadHistory(context)) }

    fun syncKeysUi() {
        keyMasks = readKeyMasks(context)
        // Semilla inmediata: con llave → KEY LISTA, nunca SIN KEY vacío.
        providerStatuses = OfferHealthMonitor.reconcileWithStore(
            context,
            providerStatuses
        )
        routeChain = SttRouter.summarizeActiveRoute(context, routingSelection).chainLabel
    }

    fun setRouting(selection: RoutingSelection) {
        routingSelection = selection
        routeChain = SttRouter.summarizeActiveRoute(context, selection).chainLabel
        val err = phase
        if (err is UiPhase.Error && err.isManualTest && selection is RoutingSelection.Auto) {
            phase = UiPhase.Idle
        }
    }

    fun refreshDashboard() {
        scope.launch {
            creditRefreshing = true
            syncKeysUi()
            withContext(Dispatchers.IO) {
                RemoteRouterConfig.refreshQuietly(context)
            }
            val key = ApiKeyStore.getDeepgram(context)
            val snap = withContext(Dispatchers.IO) {
                DeepgramBillingClient.fetchOfficialBalance(context, key)
            }
            balanceSnapshot = snap
            appSpent = BillingManager.getAppSpent(context)
            localUsage = BillingManager.getLocalUsage(context)
            groqRemaining = BillingManager.getGroqRemainingSeconds(context)
            inworldRemaining = BillingManager.getInworldRemainingSeconds(context)
            assemblyRemaining = BillingManager.getAssemblyRemainingSeconds(context)
            routeChain = SttRouter.summarizeActiveRoute(context, routingSelection).chainLabel
            historyEntries = BillingManager.loadHistory(context)
            allowPaidFallback = BillingManager.getAllowPaidFallback(context)
            syncKeysUi()
            creditRefreshing = false
        }
    }

    fun verifyOffers(showSnackbar: Boolean = true) {
        scope.launch {
            verifyingOffers = true
            syncKeysUi()
            val report = withContext(Dispatchers.IO) {
                OfferHealthMonitor.verifyAll(context)
            }
            providerStatuses = report.statuses
            keyMasks = readKeyMasks(context)
            groqRemaining = BillingManager.getGroqRemainingSeconds(context)
            inworldRemaining = BillingManager.getInworldRemainingSeconds(context)
            assemblyRemaining = BillingManager.getAssemblyRemainingSeconds(context)
            routeChain = SttRouter.summarizeActiveRoute(context, routingSelection).chainLabel
            verifyingOffers = false
            if (showSnackbar) {
                snackbar.showSnackbar("Ofertas verificadas")
            }
        }
    }

    fun onKeysChanged() {
        syncKeysUi()
        verifyOffers(showSnackbar = false)
    }

    fun notifyCopied() {
        copyFeedback = true
        scope.launch {
            snackbar.showSnackbar(context.getString(R.string.copied))
            copyFeedback = false
        }
    }

    fun buildPreFlight(
        uri: Uri,
        mode: LanguageMode = languageMode,
        diarizeOn: Boolean = diarize,
        includeDiarize: Boolean = includeDiarizeCost,
        officialBalance: Double? = balanceSnapshot?.balanceUsd
    ): PreFlightState {
        val probe = BillingManager.probeMedia(context, uri)
        val route = SttRouter.summarizeActiveRoute(context, routingSelection)
        val estimate = BillingManager.estimate(
            durationSeconds = probe.durationSeconds,
            mode = mode,
            diarizeEnabled = diarizeOn,
            includeDiarizeInEstimate = includeDiarize
        )
        val displayUsd = if (route.hasFreePath) 0.0 else estimate.estimatedUsd
        val stop = BillingManager.checkHardStop(
            context = context,
            estimatedUsd = displayUsd,
            officialBalance = officialBalance,
            hasFreePath = route.hasFreePath
        )
        val hardMsg = when (stop) {
            is BillingManager.HardStop.ReserveBreach ->
                "Bloqueado: saldo oficial tras el gasto " +
                    "(${BillingManager.formatUsd(stop.remainingAfter, 2)}) " +
                    "quedaría bajo la reserva de ${BillingManager.formatUsd(stop.reserve, 2)}."
            is BillingManager.HardStop.AppCapBreach ->
                "Bloqueado: gasto app proyectado " +
                    "(${BillingManager.formatUsd(stop.projectedSpent, 4)}) " +
                    "supera el tope de ${BillingManager.formatUsd(stop.cap, 2)}."
            is BillingManager.HardStop.StrictZeroMode -> stop.message
            BillingManager.HardStop.Ok -> null
        }
        return PreFlightState(
            uri = uri,
            fileName = probe.displayName,
            durationSeconds = probe.durationSeconds,
            estimate = estimate,
            hardStopMessage = hardMsg,
            needsGiantConfirm = !route.hasFreePath && BillingManager.isGiantFile(displayUsd),
            estimatedUsdDisplay = displayUsd,
            routeHint = route.chainLabel
        )
    }

    fun startTranscription(uri: Uri, fileName: String) {
        val selectionSnap = routingSelection
        scope.launch {
            runTranscription(
                context = context,
                uri = uri,
                fileName = fileName,
                mode = languageMode,
                diarize = diarize,
                routingSelection = selectionSnap,
                onPhase = { phase = it },
                onText = { transcriptText = it },
                onMeta = { lastResultMeta = it },
                onBalance = { balanceSnapshot = it },
                onAppSpent = {
                    appSpent = it
                    localUsage = BillingManager.getLocalUsage(context)
                    groqRemaining = BillingManager.getGroqRemainingSeconds(context)
                    inworldRemaining = BillingManager.getInworldRemainingSeconds(context)
                    assemblyRemaining = BillingManager.getAssemblyRemainingSeconds(context)
                    routeChain = SttRouter.summarizeActiveRoute(context, selectionSnap).chainLabel
                },
                onHistoryChanged = { historyEntries = it },
                snackbar = snackbar
            )
        }
    }

    fun startFromLink(raw: String, auto: Boolean = false) {
        val url = SealDownloader.extractFirstUrl(raw)
        if (url.isNullOrBlank()) {
            if (!auto) {
                Toast.makeText(context, "No hay URL válida en el texto", Toast.LENGTH_SHORT).show()
            }
            return
        }
        linkInput = url
        val needsStt = linkMode != SealDownloader.LinkMode.DOWNLOAD_ONLY
        if (needsStt && !ApiKeyStore.hasAnyKey(context)) {
            showKeyDialog = true
            Toast.makeText(context, context.getString(R.string.error_no_key), Toast.LENGTH_LONG).show()
            return
        }
        if (sealReady != SealDownloader.ReadyState.READY) {
            Toast.makeText(
                context,
                if (sealReady == SealDownloader.ReadyState.ERROR) {
                    "yt-dlp no disponible: ${SealDownloader.readyError.value ?: "error"}"
                } else {
                    "Inicializando yt-dlp / FFmpeg…"
                },
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val modeSnap = linkMode
        val kindSnap = downloadKind
        val selectionSnap = routingSelection
        val langSnap = languageMode
        val diarizeSnap = diarize
        scope.launch {
            var artifact: SealDownloader.DownloadArtifact? = null
            try {
                phase = UiPhase.Downloading("Descargando: 0%…")
                artifact = SealDownloader.download(
                    context = context,
                    rawInput = url,
                    mode = modeSnap,
                    downloadKind = kindSnap,
                    onProgress = { label -> phase = UiPhase.Downloading(label) }
                )
                if (modeSnap == SealDownloader.LinkMode.DOWNLOAD_ONLY) {
                    phase = UiPhase.Idle
                    snackbar.showSnackbar("Guardado en Downloads/KlipNote · ${artifact.title}")
                    return@launch
                }
                val uri = artifact.uri ?: Uri.fromFile(artifact.file)
                runTranscription(
                    context = context,
                    uri = uri,
                    fileName = artifact.title,
                    mode = langSnap,
                    diarize = diarizeSnap,
                    routingSelection = selectionSnap,
                    onPhase = { phase = it },
                    onText = { transcriptText = it },
                    onMeta = { lastResultMeta = it },
                    onBalance = { balanceSnapshot = it },
                    onAppSpent = {
                        appSpent = it
                        localUsage = BillingManager.getLocalUsage(context)
                        groqRemaining = BillingManager.getGroqRemainingSeconds(context)
                        inworldRemaining = BillingManager.getInworldRemainingSeconds(context)
                        assemblyRemaining = BillingManager.getAssemblyRemainingSeconds(context)
                        routeChain =
                            SttRouter.summarizeActiveRoute(context, selectionSnap).chainLabel
                    },
                    onHistoryChanged = { historyEntries = it },
                    snackbar = snackbar
                )
            } catch (e: com.yausername.youtubedl_android.YoutubeDL.CanceledException) {
                phase = UiPhase.Idle
                snackbar.showSnackbar("Descarga cancelada")
            } catch (t: Throwable) {
                phase = UiPhase.Error(t.message ?: "Error de descarga")
            } finally {
                artifact?.let { SealDownloader.cleanupArtifact(it) }
            }
        }
    }

    fun cancelWork() {
        SealDownloader.cancelActive()
        if (phase is UiPhase.Downloading) {
            phase = UiPhase.Idle
        }
    }

    fun confirmPreFlight(pf: PreFlightState) {
        if (pf.needsGiantConfirm) {
            giantConfirm = pf
            return
        }
        preFlight = null
        startTranscription(pf.uri, pf.fileName)
    }

    val keysRevision by ApiKeyStore.revision.collectAsState()
    LaunchedEffect(keysRevision) {
        // Sync ligero de máscaras/estados locales. verifyAll solo vía onKeysChanged/verifyOffers.
        syncKeysUi()
        if (keysRevision == 0L) return@LaunchedEffect
        providerStatuses = OfferHealthMonitor.reconcileWithStore(context, providerStatuses)
    }

    LaunchedEffect(Unit) {
        syncKeysUi()
        refreshDashboard()
        if (ApiKeyStore.hasAnyKey(context)) {
            // Ping inmediato al abrir si hay llaves (o si pasaron >7 días).
            if (OfferHealthMonitor.shouldAutoCheck(context)) {
                verifyOffers(showSnackbar = false)
            }
        }
    }

    LaunchedEffect(shareNonce, shareUri, shareText) {
        if (shareText != null) {
            val text = shareText
            onShareConsumed()
            val url = SealDownloader.extractFirstUrl(text)
            if (url != null) {
                linkInput = url
                // Espera breve a init nativo si hace falta.
                var waits = 0
                while (
                    SealDownloader.ready.value == SealDownloader.ReadyState.IDLE && waits < 25
                ) {
                    kotlinx.coroutines.delay(200)
                    waits++
                }
                startFromLink(url, auto = true)
            } else {
                linkInput = text
            }
            return@LaunchedEffect
        }
        val uri = shareUri ?: return@LaunchedEffect
        onShareConsumed()
        if (!ApiKeyStore.hasAnyKey(context)) {
            showKeyDialog = true
            Toast.makeText(
                context,
                context.getString(R.string.error_no_key),
                Toast.LENGTH_LONG
            ).show()
            return@LaunchedEffect
        }
        val modeSnap = languageMode
        val diarizeSnap = diarize
        val includeSnap = includeDiarizeCost
        val balSnap = balanceSnapshot?.balanceUsd
        val autoSnap = autoTranscribeOnShare
        val pf = withContext(Dispatchers.IO) {
            buildPreFlight(uri, modeSnap, diarizeSnap, includeSnap, balSnap)
        }
        if (autoSnap && pf.hardStopMessage == null && !pf.needsGiantConfirm) {
            startTranscription(pf.uri, pf.fileName)
        } else {
            preFlight = pf
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val modeSnap = languageMode
            val diarizeSnap = diarize
            val includeSnap = includeDiarizeCost
            val balSnap = balanceSnapshot?.balanceUsd
            scope.launch {
                val pf = withContext(Dispatchers.IO) {
                    buildPreFlight(uri, modeSnap, diarizeSnap, includeSnap, balSnap)
                }
                preFlight = pf
            }
        }
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
                    TextButton(
                        onClick = {
                            if (updatingYtDlp) return@TextButton
                            updatingYtDlp = true
                            scope.launch {
                                val msg = runCatching {
                                    SealDownloader.updateYtDlpNightly(context)
                                }.getOrElse { e ->
                                    e.message ?: "Error al actualizar"
                                }
                                updatingYtDlp = false
                                snackbar.showSnackbar("yt-dlp: $msg")
                            }
                        },
                        enabled = !updatingYtDlp && sealReady == SealDownloader.ReadyState.READY
                    ) {
                        Text(
                            if (updatingYtDlp) "…" else "Actualizar yt-dlp",
                            color = Accent,
                            fontSize = 12.sp
                        )
                    }
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
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LinkActionCard(
                linkInput = linkInput,
                onLinkChange = { linkInput = it },
                linkMode = linkMode,
                onModeChange = { linkMode = it },
                downloadKind = downloadKind,
                onDownloadKindChange = { downloadKind = it },
                sealReady = sealReady,
                busy = phase is UiPhase.Downloading ||
                    phase is UiPhase.Extracting ||
                    phase is UiPhase.Transcribing,
                onPasteLink = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    val pasted = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)
                        ?.toString()?.trim().orEmpty()
                    if (pasted.isNotBlank()) {
                        linkInput = SealDownloader.extractFirstUrl(pasted) ?: pasted
                    }
                },
                onRun = { startFromLink(linkInput) },
                onCancel = { cancelWork() }
            )

            RoutingModeChips(
                selection = routingSelection,
                keyMasks = keyMasks,
                onSelectAuto = { setRouting(RoutingSelection.Auto) },
                onSelectManual = { setRouting(RoutingSelection.Manual(it)) },
                onOpenKeys = { showKeyDialog = true }
            )

            CreditControlPanel(
                state = CreditUiState(
                    expanded = creditExpanded,
                    refreshing = creditRefreshing,
                    snapshot = balanceSnapshot,
                    appSpent = appSpent,
                    initialCredit = initialCredit,
                    includeDiarizeCost = includeDiarizeCost,
                    languageMode = languageMode,
                    localUsage = localUsage,
                    routeChain = routeChain,
                    providerStatuses = providerStatuses,
                    keyMasks = keyMasks,
                    allowPaidFallback = allowPaidFallback,
                    verifyingOffers = verifyingOffers,
                    groqRemainingSeconds = groqRemaining,
                    inworldRemainingSeconds = inworldRemaining,
                    inworldUsedMinutesEditable = String.format(
                        "%.1f",
                        BillingManager.getInworldUsedSeconds(context) / 60.0
                    ),
                    assemblyRemainingSeconds = assemblyRemaining,
                    assemblyUsedHoursEditable = String.format(
                        "%.2f",
                        BillingManager.getAssemblyUsedSeconds(context) / 3600.0
                    ),
                    routingSelection = routingSelection
                ),
                onToggleExpand = { creditExpanded = !creditExpanded },
                onRefresh = { refreshDashboard() },
                onOpenHistory = {
                    historyEntries = BillingManager.loadHistory(context)
                    showHistory = true
                },
                onOpenCostSettings = { showCostSettings = true },
                onVerifyOffers = { verifyOffers(showSnackbar = true) },
                onAllowPaidChange = { enabled ->
                    allowPaidFallback = enabled
                    BillingManager.setAllowPaidFallback(context, enabled)
                    routeChain = SttRouter.summarizeActiveRoute(context, routingSelection).chainLabel
                },
                onInworldUsedMinutesSave = { minutes ->
                    BillingManager.setInworldUsedSeconds(context, minutes * 60.0)
                    inworldRemaining = BillingManager.getInworldRemainingSeconds(context)
                },
                onAssemblyUsedHoursSave = { hours ->
                    BillingManager.setAssemblyUsedSeconds(context, hours * 3600.0)
                    assemblyRemaining = BillingManager.getAssemblyRemainingSeconds(context)
                },
                onOpenKeys = { showKeyDialog = true },
                onProbeProvider = { setRouting(RoutingSelection.Manual(it)) }
            )

            StatusCard(
                phase = phase,
                lastMeta = lastResultMeta,
                onBackToAuto = { setRouting(RoutingSelection.Auto) }
            )

            preFlight?.let { pf ->
                PreFlightCard(
                    fileName = pf.fileName,
                    durationLabel = BillingManager.formatDuration(pf.durationSeconds),
                    modeLabel = when (pf.estimate.mode) {
                        LanguageMode.AUTO -> "Auto"
                        LanguageMode.MULTI -> "Spanglish"
                    } + if (pf.estimate.includeDiarize) " + Diarize" else "",
                    estimatedUsd = pf.estimatedUsdDisplay,
                    hardStopMessage = pf.hardStopMessage,
                    autoTranscribe = autoTranscribeOnShare,
                    autoTranscribeEnabled = pf.hardStopMessage == null,
                    routeHint = pf.routeHint,
                    onAutoTranscribeChange = { enabled ->
                        if (enabled && pf.hardStopMessage != null) return@PreFlightCard
                        autoTranscribeOnShare = enabled
                        BillingManager.setAutoTranscribeOnShare(context, enabled)
                    },
                    onConfirm = { confirmPreFlight(pf) },
                    onDismiss = { preFlight = null },
                    busy = phase is UiPhase.Downloading ||
                        phase is UiPhase.Extracting ||
                        phase is UiPhase.Transcribing
                )
            }

            ControlsCard(
                languageMode = languageMode,
                onModeChange = { languageMode = it },
                diarize = diarize,
                onDiarizeChange = { diarize = it },
                busy = phase is UiPhase.Downloading ||
                    phase is UiPhase.Extracting ||
                    phase is UiPhase.Transcribing,
                onPick = {
                    if (!ApiKeyStore.hasAnyKey(context)) {
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
                    metaLabel = lastResultMeta?.let { meta ->
                        buildString {
                            append(meta.resultBadge())
                            if (copyFeedback) append(" · Copiado")
                        }
                    },
                    onTextChange = { transcriptText = it },
                    promptMenuExpanded = promptMenu,
                    onPromptMenuChange = { promptMenu = it },
                    copyFeedback = copyFeedback,
                    onCopyPlain = {
                        copyToClipboard(context, transcriptText)
                        notifyCopied()
                    },
                    onCopyWithPrompt = { prefix ->
                        copyToClipboard(context, prefix + transcriptText)
                        promptMenu = false
                        notifyCopied()
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
        MultiProviderKeysDialog(
            onDismiss = { showKeyDialog = false },
            onChanged = {
                if (ApiKeyStore.getDeepgram(context).isBlank()) {
                    balanceSnapshot = null
                }
                onKeysChanged()
            }
        )
    }

    if (showCostSettings) {
        CostSettingsDialog(
            initialCredit = initialCredit,
            appCap = appCap,
            minReserve = minReserve,
            includeDiarize = includeDiarizeCost,
            onDismiss = { showCostSettings = false },
            onSave = { credit, cap, reserve, include ->
                BillingManager.setInitialCredit(context, credit)
                BillingManager.setAppCap(context, cap)
                BillingManager.setMinReserve(context, reserve)
                BillingManager.setIncludeDiarizeCost(context, include)
                initialCredit = credit
                appCap = cap
                minReserve = reserve
                includeDiarizeCost = include
                showCostSettings = false
            }
        )
    }

    if (showHistory) {
        CostHistorySheet(
            entries = historyEntries,
            onDismiss = { showHistory = false },
            onOpen = { entry ->
                transcriptText = entry.transcript
                promptMenu = false
                phase = UiPhase.Idle
                showHistory = false
            },
            onDelete = { entry ->
                BillingManager.deleteHistory(context, entry.id)
                historyEntries = BillingManager.loadHistory(context)
            },
            onCopyTranscript = { text ->
                copyToClipboard(context, text)
                notifyCopied()
            }
        )
    }

    giantConfirm?.let { pf ->
        AlertDialog(
            onDismissRequest = { giantConfirm = null },
            containerColor = Card,
            titleContentColor = OnBg,
            textContentColor = OnBg,
            title = { Text("Archivo de alto costo") },
            text = {
                Text(
                    "Este archivo cuesta ~${BillingManager.formatUsd(pf.estimatedUsdDisplay, 4)} " +
                        "(umbral ${BillingManager.formatUsd(BillingManager.GIANT_FILE_THRESHOLD_USD, 2)}). " +
                        "¿Confirmas la transcripción?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        giantConfirm = null
                        preFlight = null
                        startTranscription(pf.uri, pf.fileName)
                    }
                ) {
                    Text("Sí, transcribir", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { giantConfirm = null }) {
                    Text("Cancelar", color = Muted)
                }
            }
        )
    }
}

@Composable
private fun StatusCard(
    phase: UiPhase,
    lastMeta: TranscriptionResult?,
    onBackToAuto: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp)
    ) {
        when (phase) {
            UiPhase.Idle -> {
                Text(
                    "Pega un enlace (YT/TikTok/IG/X) o elige un archivo local.",
                    color = Muted
                )
            }
            is UiPhase.Downloading -> BusyRow(phase.label)
            UiPhase.Extracting -> BusyRow(stringResource(R.string.extracting))
            is UiPhase.Transcribing -> BusyRow(
                phase.motorHint.ifBlank { stringResource(R.string.transcribing) }
            )
            is UiPhase.Done -> {
                val m = phase.meta
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.ready), color = Accent, fontWeight = FontWeight.SemiBold)
                    Text(m.resultBadge(), color = Accent, fontSize = 13.sp)
                    Text(
                        buildString {
                            m.durationSeconds?.let {
                                append(String.format("%.1fs audio", it))
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(phase.message, color = Color(0xFFF87171))
                    if (phase.isManualTest) {
                        TextButton(onClick = onBackToAuto) {
                            Text("Volver a Auto (Cascada)", color = Accent)
                        }
                    }
                }
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

        OutlinedButton(
            onClick = onPick,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Accent)
            Spacer(Modifier.size(8.dp))
            Text(
                "Elegir archivo local (Video / Audio)",
                color = Accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LinkActionCard(
    linkInput: String,
    onLinkChange: (String) -> Unit,
    linkMode: SealDownloader.LinkMode,
    onModeChange: (SealDownloader.LinkMode) -> Unit,
    downloadKind: SealDownloader.DownloadKind,
    onDownloadKindChange: (SealDownloader.DownloadKind) -> Unit,
    sealReady: SealDownloader.ReadyState,
    busy: Boolean,
    onPasteLink: () -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit
) {
    val primaryLabel = when (linkMode) {
        SealDownloader.LinkMode.TEXT_ONLY -> "⚡ Solo Texto"
        SealDownloader.LinkMode.AUDIO_AND_TEXT -> "🎵 Audio + Texto"
        SealDownloader.LinkMode.VIDEO_AND_TEXT -> "🎬 Video + Texto"
        SealDownloader.LinkMode.DOWNLOAD_ONLY -> "📥 Solo Bajar"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Enlace", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = linkInput,
            onValueChange = onLinkChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("Enlace de YouTube, TikTok, IG, X...", color = Muted)
            },
            trailingIcon = {
                TextButton(onClick = onPasteLink, enabled = !busy) {
                    Text("Pegar", color = Accent, fontSize = 12.sp)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color(0xFF243044),
                focusedTextColor = OnBg,
                unfocusedTextColor = OnBg,
                cursorColor = Accent
            )
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ModeChip(
                    selected = linkMode == SealDownloader.LinkMode.TEXT_ONLY,
                    label = "⚡ Solo Texto",
                    enabled = !busy,
                    onClick = { onModeChange(SealDownloader.LinkMode.TEXT_ONLY) }
                )
            }
            item {
                ModeChip(
                    selected = linkMode == SealDownloader.LinkMode.AUDIO_AND_TEXT,
                    label = "🎵 Audio + Texto",
                    enabled = !busy,
                    onClick = { onModeChange(SealDownloader.LinkMode.AUDIO_AND_TEXT) }
                )
            }
            item {
                ModeChip(
                    selected = linkMode == SealDownloader.LinkMode.VIDEO_AND_TEXT,
                    label = "🎬 Video + Texto",
                    enabled = !busy,
                    onClick = { onModeChange(SealDownloader.LinkMode.VIDEO_AND_TEXT) }
                )
            }
            item {
                ModeChip(
                    selected = linkMode == SealDownloader.LinkMode.DOWNLOAD_ONLY,
                    label = "📥 Solo Bajar",
                    enabled = !busy,
                    onClick = { onModeChange(SealDownloader.LinkMode.DOWNLOAD_ONLY) }
                )
            }
        }
        if (linkMode == SealDownloader.LinkMode.DOWNLOAD_ONLY) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(
                    selected = downloadKind == SealDownloader.DownloadKind.AUDIO_M4A,
                    label = "Audio M4A",
                    enabled = !busy,
                    onClick = { onDownloadKindChange(SealDownloader.DownloadKind.AUDIO_M4A) }
                )
                ModeChip(
                    selected = downloadKind == SealDownloader.DownloadKind.VIDEO_MP4,
                    label = "Video MP4",
                    enabled = !busy,
                    onClick = { onDownloadKindChange(SealDownloader.DownloadKind.VIDEO_MP4) }
                )
            }
        }
        val readyHint = when (sealReady) {
            SealDownloader.ReadyState.READY -> null
            SealDownloader.ReadyState.IDLE -> "Inicializando yt-dlp / FFmpeg…"
            SealDownloader.ReadyState.ERROR -> "yt-dlp no listo"
        }
        if (readyHint != null) {
            Text(readyHint, color = Muted, fontSize = 12.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRun,
                enabled = !busy && linkInput.isNotBlank() &&
                    sealReady == SealDownloader.ReadyState.READY,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.Black,
                    disabledContainerColor = Accent.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(primaryLabel, fontWeight = FontWeight.SemiBold)
            }
            if (busy) {
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(12.dp)) {
                    Text("Cancelar", color = Color(0xFFF87171))
                }
            }
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
    metaLabel: String?,
    onTextChange: (String) -> Unit,
    promptMenuExpanded: Boolean,
    onPromptMenuChange: (Boolean) -> Unit,
    copyFeedback: Boolean,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transcripción", color = Muted, fontSize = 12.sp)
            if (copyFeedback) {
                Text("Copiado", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        metaLabel?.let {
            Text(it, color = Accent, fontSize = 12.sp)
        }
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

private fun readKeyMasks(context: android.content.Context): Map<SttProvider, String?> = mapOf(
    SttProvider.GROQ_FREE to ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.GROQ),
    SttProvider.INWORLD to ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.INWORLD),
    SttProvider.ASSEMBLY_AI to ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.ASSEMBLY_AI),
    SttProvider.DEEPGRAM to ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.DEEPGRAM),
    SttProvider.PAID_FALLBACK to ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.PAID_FALLBACK)
)

private fun copyToClipboard(context: android.content.Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("KlipNote", text))
}

private suspend fun runTranscription(
    context: android.content.Context,
    uri: Uri,
    fileName: String,
    mode: LanguageMode,
    diarize: Boolean,
    routingSelection: RoutingSelection,
    onPhase: (UiPhase) -> Unit,
    onText: (String) -> Unit,
    onMeta: (TranscriptionResult) -> Unit,
    onBalance: (DeepgramBillingClient.BalanceSnapshot) -> Unit,
    onAppSpent: (Double) -> Unit,
    onHistoryChanged: (List<BillingManager.HistoryEntry>) -> Unit,
    snackbar: SnackbarHostState
) {
    if (!ApiKeyStore.hasAnyKey(context)) {
        onPhase(UiPhase.Error(context.getString(R.string.error_no_key)))
        return
    }
    if (routingSelection is RoutingSelection.Manual &&
        !SttRouter.isConfigured(context, routingSelection.provider)
    ) {
        onPhase(
            UiPhase.Error(
                message = "🧪 Prueba Manual · ${routingSelection.provider.displayName()}: sin key",
                isManualTest = true
            )
        )
        return
    }

    var extracted: AudioExtractor.ExtractedAudio? = null
    var balanceBefore: Double? = null
    try {
        val dgKey = ApiKeyStore.getDeepgram(context)
        if (dgKey.isNotBlank()) {
            val beforeSnap = withContext(Dispatchers.IO) {
                DeepgramBillingClient.fetchOfficialBalance(context, dgKey)
            }
            onBalance(beforeSnap)
            balanceBefore = beforeSnap.balanceUsd
        }

        onPhase(UiPhase.Extracting)
        extracted = withContext(Dispatchers.IO) {
            AudioExtractor.prepareAudio(context, uri)
        }

        val motorHint = when (routingSelection) {
            is RoutingSelection.Auto -> "Transcribiendo con cascada Auto…"
            is RoutingSelection.Manual ->
                "Transcribiendo con ${routingSelection.provider.displayName()}…"
        }
        onPhase(UiPhase.Transcribing(motorHint))
        val audio = extracted
        val result = withContext(Dispatchers.IO) {
            SttRouter.transcribe(
                context = context,
                audioFile = audio.file,
                mimeType = audio.mimeType,
                mode = mode,
                diarize = diarize,
                deepgramBalanceUsd = balanceBefore,
                selection = routingSelection
            )
        }

        val duration = result.durationSeconds ?: 0.0
        val cost = result.costUsd
        if (cost > 0.0) {
            BillingManager.addAppSpent(context, cost)
        }
        BillingManager.recordLocalUsage(context, duration)
        onAppSpent(BillingManager.getAppSpent(context))

        var afterBalance: Double? = null
        if (result.provider == SttProvider.DEEPGRAM && dgKey.isNotBlank()) {
            val afterSnap = withContext(Dispatchers.IO) {
                DeepgramBillingClient.fetchOfficialBalance(context, dgKey)
            }
            onBalance(afterSnap)
            afterBalance = afterSnap.balanceUsd
        }

        val persistedAudio = extracted?.let { audioFile ->
            runCatching {
                val dest = File(
                    context.filesDir,
                    "recordings/rec_${System.currentTimeMillis()}_${audioFile.file.name}"
                )
                dest.parentFile?.mkdirs()
                audioFile.file.copyTo(dest, overwrite = true)
                dest.absolutePath
            }.getOrNull()
        }

        val entry = BillingManager.newHistoryEntry(
            fileName = fileName,
            durationSeconds = duration,
            mode = mode,
            diarize = diarize,
            appCostUsd = cost,
            balanceBefore = balanceBefore,
            balanceAfter = afterBalance,
            transcript = result.transcript,
            audioPath = persistedAudio,
            provider = result.providerLabel
        )
        BillingManager.appendHistory(context, entry)
        onHistoryChanged(BillingManager.loadHistory(context))

        onText(result.transcript)
        onMeta(result)
        onPhase(UiPhase.Done(result))
        if (result.transcript.isNotBlank()) {
            copyToClipboard(context, result.transcript)
            snackbar.showSnackbar(context.getString(R.string.copied))
        }
    } catch (e: ManualRouteException) {
        onPhase(
            UiPhase.Error(
                message = e.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.error_generic),
                isManualTest = true
            )
        )
    } catch (e: Exception) {
        onPhase(
            UiPhase.Error(
                message = e.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.error_generic),
                isManualTest = routingSelection is RoutingSelection.Manual
            )
        )
    } finally {
        AudioExtractor.cleanup(extracted)
    }
}
