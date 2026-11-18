package com.blackeye.klipnote

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Build

private val Bg = Color(0xFF0B0F17)
private val Card = Color(0xFF151C28)
private val Accent = Color(0xFF10B981)
private val OnBg = Color(0xFFE8EEF7)
private val Muted = Color(0xFF8B9BB4)
private val Amber = Color(0xFFFBBF24)

private enum class MainTab { Capturar, Guiones, Motores }

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
        KlipNoteNotify.ensureChannels(this)
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
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(KlipNoteNotify.EXTRA_OPEN_RESULT, false)) {
            // Abrir en Capturar; el JobHub ya habrá rellenado el resultado si aplica.
            return
        }
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

    var selectedTab by remember { mutableStateOf(MainTab.Capturar) }
    var showMotorSheet by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var languageMode by remember { mutableStateOf(BillingManager.getLanguageMode(context)) }
    var forcedLanguage by remember { mutableStateOf(BillingManager.getForcedLanguage(context)) }
    var diarize by remember { mutableStateOf(BillingManager.getDiarizeEnabled(context)) }
    var phase by remember { mutableStateOf<UiPhase>(UiPhase.Idle) }
    var linkInput by remember { mutableStateOf("") }
    var linkMode by remember { mutableStateOf(SealDownloader.LinkMode.TEXT_ONLY) }
    var downloadKind by remember { mutableStateOf(SealDownloader.DownloadKind.AUDIO_M4A) }
    var updatingYtDlp by remember { mutableStateOf(false) }
    /** Sitio cuya hoja de login está abierta (null = cerrada). */
    var cookieSheetSite by remember { mutableStateOf<CookieSite?>(null) }
    /** Sitio del último enlace que falló por login (para el botón del error en Capturar). */
    var cookieSiteHint by remember { mutableStateOf(CookieSite.INSTAGRAM) }
    var cookiesRevision by remember { mutableStateOf(0) }
    var transcriptText by remember { mutableStateOf("") }
    var lastResultMeta by remember { mutableStateOf<TranscriptionResult?>(null) }
    var copyFeedback by remember { mutableStateOf(false) }
    var lastSourceLabel by remember { mutableStateOf("") }

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
    var autoPrimary by remember {
        mutableStateOf(SttRouter.summarizeActiveRoute(context, RoutingSelection.Auto).primaryProvider)
    }
    var appSpent by remember { mutableStateOf(BillingManager.getAppSpent(context)) }
    var localUsage by remember { mutableStateOf(BillingManager.getLocalUsage(context)) }
    var includeDiarizeCost by remember {
        mutableStateOf(BillingManager.getIncludeDiarizeCost(context))
    }
    var autoTranscribeOnShare by remember {
        mutableStateOf(BillingManager.getAutoTranscribeOnShare(context))
    }
    var engineEnabled by remember {
        mutableStateOf(SttProvider.entries.associateWith { BillingManager.isEngineEnabled(context, it) })
    }
    var allowPaidFallback by remember {
        mutableStateOf(BillingManager.getAllowPaidFallback(context))
    }
    var initialCredit by remember { mutableStateOf(BillingManager.getInitialCredit(context)) }
    var appCap by remember { mutableStateOf(BillingManager.getAppCap(context)) }
    var minReserve by remember { mutableStateOf(BillingManager.getMinReserve(context)) }
    var groqRemaining by remember { mutableStateOf(BillingManager.getGroqRemainingSeconds(context)) }
    var groqLimit by remember { mutableStateOf(BillingManager.getGroqLimitSeconds(context)) }
    var groqUsedToday by remember { mutableStateOf(BillingManager.getGroqUsedTodaySeconds(context)) }
    var groqHasLive by remember { mutableStateOf(BillingManager.hasGroqLiveQuota(context)) }
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
    var transcriptEntries by remember { mutableStateOf(TranscriptHistoryStore.load(context)) }
    var guionSearch by remember { mutableStateOf("") }
    var expandedGuionId by remember { mutableStateOf<String?>(null) }
    var lastSavedMedia by remember { mutableStateOf<SavedMediaInfo?>(null) }
    var notifyWhenDone by remember { mutableStateOf(KlipNoteNotify.isNotifyEnabled(context)) }
    var showNotifPermissionRationale by remember { mutableStateOf(false) }
    var pendingLinkAfterNotif by remember { mutableStateOf<String?>(null) }

    fun launchLinkJob(url: String) {
        phase = UiPhase.Downloading("Descargando: 0%…")
        lastSavedMedia = null
        KlipNoteWorkService.startLinkJob(
            context = context,
            url = url,
            mode = linkMode,
            kind = downloadKind,
            languageMode = languageMode,
            forcedLanguage = forcedLanguage,
            diarize = diarize,
            routingSelection = routingSelection
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        KlipNoteNotify.markAskedNotificationPermission(context)
        if (!granted) {
            Toast.makeText(
                context,
                "Sin notificaciones: la app sigue funcionando igual",
                Toast.LENGTH_SHORT
            ).show()
        }
        val pending = pendingLinkAfterNotif
        pendingLinkAfterNotif = null
        if (pending != null) launchLinkJob(pending)
    }

    fun syncKeysUi() {
        keyMasks = readKeyMasks(context)
        engineEnabled = SttProvider.entries.associateWith { BillingManager.isEngineEnabled(context, it) }
        providerStatuses = OfferHealthMonitor.reconcileWithStore(context, providerStatuses)
        val summary = SttRouter.summarizeActiveRoute(context, routingSelection)
        routeChain = summary.chainLabel
        autoPrimary = when (routingSelection) {
            is RoutingSelection.Auto -> summary.primaryProvider
            is RoutingSelection.Manual -> summary.primaryProvider
        }
    }

    fun setRouting(selection: RoutingSelection) {
        routingSelection = selection
        val summary = SttRouter.summarizeActiveRoute(context, selection)
        routeChain = summary.chainLabel
        autoPrimary = summary.primaryProvider
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
            groqLimit = BillingManager.getGroqLimitSeconds(context)
            groqUsedToday = BillingManager.getGroqUsedTodaySeconds(context)
            groqHasLive = BillingManager.hasGroqLiveQuota(context)
            inworldRemaining = BillingManager.getInworldRemainingSeconds(context)
            assemblyRemaining = BillingManager.getAssemblyRemainingSeconds(context)
            syncKeysUi()
            historyEntries = BillingManager.loadHistory(context)
            allowPaidFallback = BillingManager.getAllowPaidFallback(context)
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
            groqLimit = BillingManager.getGroqLimitSeconds(context)
            groqUsedToday = BillingManager.getGroqUsedTodaySeconds(context)
            groqHasLive = BillingManager.hasGroqLiveQuota(context)
            inworldRemaining = BillingManager.getInworldRemainingSeconds(context)
            assemblyRemaining = BillingManager.getAssemblyRemainingSeconds(context)
            syncKeysUi()
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

    fun persistGuion(source: String, result: TranscriptionResult) {
        transcriptEntries = TranscriptHistoryStore.append(
            context = context,
            sourceTitleOrUrl = source,
            providerUsed = result.providerLabel,
            durationSec = result.durationSeconds ?: 0.0,
            text = result.transcript
        )
    }

    fun startTranscription(uri: Uri, fileName: String) {
        val selectionSnap = routingSelection
        lastSourceLabel = fileName
        scope.launch {
            runTranscription(
                context = context,
                uri = uri,
                fileName = fileName,
                mode = languageMode,
                forcedLanguageCode = forcedLanguage,
                diarize = diarize,
                routingSelection = selectionSnap,
                // Galería / compartir: comprimir copia STT para videos largos con solo Groq.
                compressForStt = true,
                savedInDownloads = false,
                onPhase = { phase = it },
                onText = { transcriptText = it },
                onMeta = {
                    lastResultMeta = it
                    persistGuion(fileName, it)
                },
                onBalance = { balanceSnapshot = it },
                onAppSpent = {
                    appSpent = it
                    localUsage = BillingManager.getLocalUsage(context)
                    groqRemaining = BillingManager.getGroqRemainingSeconds(context)
                    groqLimit = BillingManager.getGroqLimitSeconds(context)
                    groqUsedToday = BillingManager.getGroqUsedTodaySeconds(context)
                    groqHasLive = BillingManager.hasGroqLiveQuota(context)
                    inworldRemaining = BillingManager.getInworldRemainingSeconds(context)
                    assemblyRemaining = BillingManager.getAssemblyRemainingSeconds(context)
                    syncKeysUi()
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
        if (KlipNoteJobHub.isBusy()) {
            Toast.makeText(context, "Ya hay una descarga/transcripción en curso", Toast.LENGTH_SHORT).show()
            return
        }
        linkInput = url
        lastSourceLabel = url
        val needsStt = linkMode != SealDownloader.LinkMode.DOWNLOAD_ONLY
        if (needsStt && !ApiKeyStore.hasAnyKey(context)) {
            showKeyDialog = true
            selectedTab = MainTab.Motores
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
        // Android 13+: pedir notificaciones la 1ª vez (si el usuario quiere avisos).
        if (Build.VERSION.SDK_INT >= 33 &&
            KlipNoteNotify.isNotifyEnabled(context) &&
            !KlipNoteNotify.canPostNotifications(context) &&
            !KlipNoteNotify.hasAskedNotificationPermission(context)
        ) {
            pendingLinkAfterNotif = url
            showNotifPermissionRationale = true
            return
        }
        launchLinkJob(url)
    }

    fun cancelWork() {
        KlipNoteWorkService.cancel(context)
        SealDownloader.cancelActive()
        if (phase is UiPhase.Downloading ||
            phase is UiPhase.Extracting ||
            phase is UiPhase.Transcribing
        ) {
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

    val jobState by KlipNoteJobHub.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME ->
                    KlipNoteJobHub.appInForeground = true
                Lifecycle.Event.ON_STOP ->
                    KlipNoteJobHub.appInForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        KlipNoteJobHub.appInForeground =
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(jobState) {
        when (val s = jobState) {
            is KlipNoteJobHub.JobState.Running -> {
                val label = s.label
                phase = when {
                    label.contains("Descarg", ignoreCase = true) -> UiPhase.Downloading(label)
                    label.contains("Extra", ignoreCase = true) ||
                        label.contains("Preparando audio", ignoreCase = true) -> UiPhase.Extracting
                    else -> UiPhase.Transcribing(label)
                }
            }
            is KlipNoteJobHub.JobState.Success -> {
                transcriptText = s.transcript
                lastResultMeta = s.meta
                lastSavedMedia = s.savedMedia
                lastSourceLabel = s.sourceLabel.ifBlank { lastSourceLabel }
                s.meta?.let { persistGuion(lastSourceLabel, it) }
                historyEntries = BillingManager.loadHistory(context)
                appSpent = BillingManager.getAppSpent(context)
                localUsage = BillingManager.getLocalUsage(context)
                syncKeysUi()
                phase = if (s.meta != null) {
                    UiPhase.Done(s.meta)
                } else {
                    UiPhase.Idle
                }
                selectedTab = MainTab.Capturar
                if (KlipNoteJobHub.appInForeground) {
                    vibrateShort(context)
                    snackbar.showSnackbar(s.toastMessage)
                }
                KlipNoteJobHub.consumeTerminal()
            }
            is KlipNoteJobHub.JobState.Failed -> {
                CookieStore.siteForUrl(linkInput)?.let { cookieSiteHint = it }
                phase = UiPhase.Error(s.message)
                historyEntries = BillingManager.loadHistory(context)
                if (KlipNoteJobHub.appInForeground) vibrateShort(context)
                KlipNoteJobHub.consumeTerminal()
            }
            KlipNoteJobHub.JobState.Cancelled -> {
                phase = UiPhase.Idle
                snackbar.showSnackbar("Cancelado")
                KlipNoteJobHub.consumeTerminal()
            }
            KlipNoteJobHub.JobState.Idle -> Unit
        }
    }

    val keysRevision by ApiKeyStore.revision.collectAsState()
    LaunchedEffect(keysRevision) {
        syncKeysUi()
        if (keysRevision == 0L) return@LaunchedEffect
        providerStatuses = OfferHealthMonitor.reconcileWithStore(context, providerStatuses)
    }

    LaunchedEffect(Unit) {
        syncKeysUi()
        refreshDashboard()
        if (ApiKeyStore.hasAnyKey(context)) {
            if (OfferHealthMonitor.shouldAutoCheck(context)) {
                verifyOffers(showSnackbar = false)
            }
        }
    }

    LaunchedEffect(shareNonce, shareUri, shareText) {
        if (shareText != null) {
            val text = shareText
            onShareConsumed()
            selectedTab = MainTab.Capturar
            val url = SealDownloader.extractFirstUrl(text)
            if (url != null) {
                linkInput = url
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
        selectedTab = MainTab.Capturar
        if (!ApiKeyStore.hasAnyKey(context)) {
            showKeyDialog = true
            selectedTab = MainTab.Motores
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

    val busy = phase is UiPhase.Downloading ||
        phase is UiPhase.Extracting ||
        phase is UiPhase.Transcribing ||
        KlipNoteJobHub.isBusy()

    val pillManual = routingSelection is RoutingSelection.Manual
    val pillText = activeMotorPillText(routingSelection, autoPrimary)

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            when (selectedTab) {
                MainTab.Capturar -> {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${stringResource(R.string.app_name)} · ${BuildConfig.VERSION_NAME}",
                                fontWeight = FontWeight.Bold,
                                color = OnBg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        actions = {
                            ActiveMotorPill(
                                label = pillText,
                                isManual = pillManual,
                                onClick = { showMotorSheet = true }
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Bg,
                            titleContentColor = OnBg
                        )
                    )
                }
                MainTab.Guiones -> {
                    TopAppBar(
                        title = {
                            Text("Guiones", fontWeight = FontWeight.Bold, color = OnBg)
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Bg,
                            titleContentColor = OnBg
                        )
                    )
                }
                MainTab.Motores -> {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Motores", fontWeight = FontWeight.Bold, color = OnBg)
                                Text(
                                    "Admin · Escudo Anti-Gladia",
                                    fontSize = 12.sp,
                                    color = Muted
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Bg,
                            titleContentColor = OnBg
                        )
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Card, contentColor = OnBg) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.Capturar,
                    onClick = { selectedTab = MainTab.Capturar },
                    icon = { Icon(Icons.Default.Link, contentDescription = null) },
                    label = { Text("Capturar") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Guiones,
                    onClick = {
                        transcriptEntries = TranscriptHistoryStore.load(context)
                        selectedTab = MainTab.Guiones
                    },
                    icon = { Icon(Icons.Default.Description, contentDescription = null) },
                    label = { Text("Guiones") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Motores,
                    onClick = { selectedTab = MainTab.Motores },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Motores") },
                    colors = navColors()
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            when (selectedTab) {
                MainTab.Capturar -> {
                    CapturarTab(
                        linkInput = linkInput,
                        onLinkChange = { raw ->
                            linkInput = SealDownloader.extractFirstUrl(raw)
                                ?: SealDownloader.cleanMediaUrl(raw)
                        },
                        linkMode = linkMode,
                        onModeChange = { linkMode = it },
                        downloadKind = downloadKind,
                        onDownloadKindChange = { downloadKind = it },
                        sealReady = sealReady,
                        busy = busy,
                        phase = phase,
                        transcriptText = transcriptText,
                        sourceTitleOrUrl = lastSourceLabel.ifBlank { linkInput },
                        lastResultMeta = lastResultMeta,
                        copyFeedback = copyFeedback,
                        preFlight = preFlight,
                        forcedLanguage = forcedLanguage,
                        autoTranscribeOnShare = autoTranscribeOnShare,
                        onPasteLink = {
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            val pasted = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)
                                ?.toString()?.trim().orEmpty()
                            if (pasted.isNotBlank()) {
                                linkInput = SealDownloader.extractFirstUrl(pasted)
                                    ?: SealDownloader.cleanMediaUrl(pasted)
                            }
                        },
                        onClearLink = { linkInput = "" },
                        onRun = { startFromLink(linkInput) },
                        onCancel = { cancelWork() },
                        onPickFile = {
                            if (!ApiKeyStore.hasAnyKey(context) &&
                                linkMode != SealDownloader.LinkMode.DOWNLOAD_ONLY
                            ) {
                                showKeyDialog = true
                                selectedTab = MainTab.Motores
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error_no_key),
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                picker.launch(arrayOf("audio/*", "video/*"))
                            }
                        },
                        onConnectCookies = { cookieSheetSite = cookieSiteHint },
                        onConfirmPreFlight = { confirmPreFlight(it) },
                        onDismissPreFlight = { preFlight = null },
                        onAutoTranscribeChange = { enabled ->
                            preFlight?.let { pf ->
                                if (enabled && pf.hardStopMessage != null) return@CapturarTab
                            }
                            autoTranscribeOnShare = enabled
                            BillingManager.setAutoTranscribeOnShare(context, enabled)
                        },
                        onCopy = {
                            copyToClipboard(context, transcriptText)
                            notifyCopied()
                        },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, transcriptText)
                            }
                            context.startActivity(
                                Intent.createChooser(send, context.getString(R.string.send_to_ai))
                            )
                        },
                        onClearResult = {
                            transcriptText = ""
                            lastResultMeta = null
                            lastSavedMedia = null
                            phase = UiPhase.Idle
                            copyFeedback = false
                        },
                        onBackToAuto = { setRouting(RoutingSelection.Auto) },
                        onTextChange = { transcriptText = it },
                        savedMedia = lastSavedMedia,
                        onOpenSavedMedia = { SavedMediaActions.openWithPlayer(context, it) },
                        onShareSavedMedia = { SavedMediaActions.share(context, it) },
                        onOpenDownloads = { SavedMediaActions.openDownloadsFolder(context) }
                    )
                }
                MainTab.Guiones -> {
                    GuionesTab(
                        entries = transcriptEntries,
                        search = guionSearch,
                        onSearchChange = { guionSearch = it },
                        expandedId = expandedGuionId,
                        onExpandToggle = { id ->
                            expandedGuionId = if (expandedGuionId == id) null else id
                        },
                        onCopy = { text ->
                            copyToClipboard(context, text)
                            notifyCopied()
                        },
                        onOpenLink = { raw ->
                            val url = SealDownloader.extractFirstUrl(raw)
                            if (url != null) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Sin enlace abríble",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onDelete = { id ->
                            transcriptEntries = TranscriptHistoryStore.delete(context, id)
                            if (expandedGuionId == id) expandedGuionId = null
                        },
                        onClearAll = {
                            transcriptEntries = TranscriptHistoryStore.clear(context)
                            expandedGuionId = null
                        }
                    )
                }
                MainTab.Motores -> {
                    MotoresTab(
                        routingSelection = routingSelection,
                        keyMasks = keyMasks,
                        onSelectAuto = { setRouting(RoutingSelection.Auto) },
                        onSelectManual = { setRouting(RoutingSelection.Manual(it)) },
                        onOpenKeys = { showKeyDialog = true },
                        creditState = CreditUiState(
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
                            groqLimitSeconds = groqLimit,
                            groqUsedTodaySeconds = groqUsedToday,
                            groqHasLiveQuota = groqHasLive,
                            inworldRemainingSeconds = inworldRemaining,
                            inworldUsedMinutesEditable = String.format(
                                "%.1f",
                                BillingManager.getInworldUsedSeconds(context) / 60.0
                            ),
                            assemblyRemainingSeconds = assemblyRemaining,
                            assemblyRemainingUsdEditable = String.format(
                                java.util.Locale.US,
                                "%.2f",
                                BillingManager.getAssemblyRemainingUsdEstimate(context)
                            ),
                            engineEnabled = engineEnabled,
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
                            syncKeysUi()
                        },
                        onInworldUsedMinutesSave = { minutes ->
                            BillingManager.setInworldUsedSeconds(context, minutes * 60.0)
                            inworldRemaining = BillingManager.getInworldRemainingSeconds(context)
                        },
                        onAssemblyRemainingUsdSave = { usd ->
                            BillingManager.setAssemblyRemainingUsd(context, usd)
                            assemblyRemaining = BillingManager.getAssemblyRemainingSeconds(context)
                        },
                        onEngineEnabledChange = { provider, enabled ->
                            BillingManager.setEngineEnabled(context, provider, enabled)
                            if (provider == SttProvider.PAID_FALLBACK) {
                                allowPaidFallback = enabled
                            }
                            syncKeysUi()
                        },
                        onProbeProvider = { setRouting(RoutingSelection.Manual(it)) },
                        languageMode = languageMode,
                        onLanguageModeChange = {
                            languageMode = it
                            BillingManager.setLanguageMode(context, it)
                        },
                        forcedLanguage = forcedLanguage,
                        onForcedLanguageChange = {
                            forcedLanguage = it
                            BillingManager.setForcedLanguage(context, it)
                            if (languageMode != LanguageMode.FIXED) {
                                languageMode = LanguageMode.FIXED
                                BillingManager.setLanguageMode(context, LanguageMode.FIXED)
                            }
                        },
                        diarize = diarize,
                        onDiarizeChange = {
                            diarize = it
                            BillingManager.setDiarizeEnabled(context, it)
                        },
                        sealReady = sealReady,
                        updatingYtDlp = updatingYtDlp,
                        onUpdateYtDlp = {
                            if (updatingYtDlp) return@MotoresTab
                            updatingYtDlp = true
                            scope.launch {
                                val msg = runCatching {
                                    SealDownloader.updateYtDlpNightly(context)
                                }.getOrElse { e -> e.message ?: "Error al actualizar" }
                                updatingYtDlp = false
                                snackbar.showSnackbar("yt-dlp: $msg")
                            }
                        },
                        cookiesEnabled = CookieStore.isEnabled(context),
                        cookiesRevision = cookiesRevision,
                        onConnectCookies = { site -> cookieSheetSite = site },
                        onToggleCookies = { enabled ->
                            CookieStore.setEnabled(context, enabled)
                            cookiesRevision += 1
                        },
                        onClearCookies = { site ->
                            CookieStore.clear(context, site)
                            cookiesRevision += 1
                            scope.launch {
                                snackbar.showSnackbar("Cuenta de ${site.label} desconectada")
                            }
                        },
                        notifyWhenDone = notifyWhenDone,
                        onNotifyWhenDoneChange = { enabled ->
                            notifyWhenDone = enabled
                            KlipNoteNotify.setNotifyEnabled(context, enabled)
                        }
                    )
                }
            }
        }
    }

    cookieSheetSite?.let { sheetSite ->
        SiteCookiesSheet(
            site = sheetSite,
            onDismiss = { cookieSheetSite = null },
            onSaved = { count ->
                cookiesRevision += 1
                cookieSheetSite = null
                scope.launch {
                    snackbar.showSnackbar("Cookies guardadas ($count) — solo en este dispositivo")
                }
            }
        )
    }

    if (showMotorSheet) {
        MotorPickerSheet(
            selection = routingSelection,
            keyMasks = keyMasks,
            onDismiss = { showMotorSheet = false },
            onSelectAuto = {
                setRouting(RoutingSelection.Auto)
                showMotorSheet = false
            },
            onSelectManual = { provider ->
                if (keyMasks[provider].isNullOrBlank()) {
                    showMotorSheet = false
                    showKeyDialog = true
                    selectedTab = MainTab.Motores
                } else {
                    setRouting(RoutingSelection.Manual(provider))
                    showMotorSheet = false
                }
            }
        )
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
                lastSavedMedia = SavedMediaActions.fromHistory(
                    entry.downloadUri,
                    entry.downloadDisplayName,
                    entry.downloadIsVideo
                )
                phase = UiPhase.Idle
                selectedTab = MainTab.Capturar
                showHistory = false
            },
            onDelete = { entry ->
                BillingManager.deleteHistory(context, entry.id)
                historyEntries = BillingManager.loadHistory(context)
            },
            onCopyTranscript = { text ->
                copyToClipboard(context, text)
                notifyCopied()
            },
            onOpenSavedMedia = { SavedMediaActions.openWithPlayer(context, it) },
            onShareSavedMedia = { SavedMediaActions.share(context, it) },
            onOpenDownloads = { SavedMediaActions.openDownloadsFolder(context) }
        )
    }

    if (showNotifPermissionRationale) {
        AlertDialog(
            onDismissRequest = {
                showNotifPermissionRationale = false
                KlipNoteNotify.markAskedNotificationPermission(context)
                val pending = pendingLinkAfterNotif
                pendingLinkAfterNotif = null
                if (pending != null) launchLinkJob(pending)
            },
            containerColor = Card,
            titleContentColor = OnBg,
            textContentColor = OnBg,
            title = { Text("Avisos al terminar") },
            text = {
                Text(
                    "KlipNote puede avisarte con una notificación cuando termine la descarga " +
                        "o el guion (útil si sales de la app). Puedes denegarlo y seguir usando " +
                        "la app igual, solo sin avisos."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotifPermissionRationale = false
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPermissionLauncher.launch(
                                android.Manifest.permission.POST_NOTIFICATIONS
                            )
                        } else {
                            val pending = pendingLinkAfterNotif
                            pendingLinkAfterNotif = null
                            if (pending != null) launchLinkJob(pending)
                        }
                    }
                ) { Text("Permitir", color = Accent) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotifPermissionRationale = false
                        KlipNoteNotify.markAskedNotificationPermission(context)
                        val pending = pendingLinkAfterNotif
                        pendingLinkAfterNotif = null
                        if (pending != null) launchLinkJob(pending)
                    }
                ) { Text("Ahora no", color = Muted) }
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
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Accent,
    selectedTextColor = Accent,
    indicatorColor = Accent.copy(alpha = 0.18f),
    unselectedIconColor = Muted,
    unselectedTextColor = Muted
)

@Composable
private fun ActiveMotorPill(
    label: String,
    isManual: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isManual) Amber else Accent
    Text(
        text = label,
        color = tint,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MotorPickerSheet(
    selection: RoutingSelection,
    keyMasks: Map<SttProvider, String?>,
    onDismiss: () -> Unit,
    onSelectAuto: () -> Unit,
    onSelectManual: (SttProvider) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Motor activo",
                color = OnBg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Text(
                "Cambia Auto o fija un motor sin salir de Capturar.",
                color = Muted,
                fontSize = 13.sp
            )
            FilterChip(
                selected = selection is RoutingSelection.Auto,
                onClick = onSelectAuto,
                label = { Text("⚡ Auto (Cascada)") },
                modifier = Modifier.fillMaxWidth(),
                colors = chipColors()
            )
            SttProvider.entries.forEach { provider ->
                val selected = selection is RoutingSelection.Manual &&
                    selection.provider == provider
                FilterChip(
                    selected = selected,
                    onClick = { onSelectManual(provider) },
                    label = {
                        Text(
                            buildString {
                                append(provider.chipLabel())
                                if (keyMasks[provider].isNullOrBlank()) append(" · sin key")
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = chipColors()
                )
            }
        }
    }
}

@Composable
private fun CapturarTab(
    linkInput: String,
    onLinkChange: (String) -> Unit,
    linkMode: SealDownloader.LinkMode,
    onModeChange: (SealDownloader.LinkMode) -> Unit,
    downloadKind: SealDownloader.DownloadKind,
    onDownloadKindChange: (SealDownloader.DownloadKind) -> Unit,
    sealReady: SealDownloader.ReadyState,
    busy: Boolean,
    phase: UiPhase,
    transcriptText: String,
    sourceTitleOrUrl: String,
    lastResultMeta: TranscriptionResult?,
    copyFeedback: Boolean,
    preFlight: PreFlightState?,
    forcedLanguage: String,
    autoTranscribeOnShare: Boolean,
    onPasteLink: () -> Unit,
    onClearLink: () -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onPickFile: () -> Unit,
    onConnectCookies: () -> Unit,
    onConfirmPreFlight: (PreFlightState) -> Unit,
    onDismissPreFlight: () -> Unit,
    onAutoTranscribeChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClearResult: () -> Unit,
    onBackToAuto: () -> Unit,
    onTextChange: (String) -> Unit,
    savedMedia: SavedMediaInfo?,
    onOpenSavedMedia: (SavedMediaInfo) -> Unit,
    onShareSavedMedia: (SavedMediaInfo) -> Unit,
    onOpenDownloads: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LinkHeroCard(
            linkInput = linkInput,
            onLinkChange = onLinkChange,
            linkMode = linkMode,
            onModeChange = onModeChange,
            downloadKind = downloadKind,
            onDownloadKindChange = onDownloadKindChange,
            sealReady = sealReady,
            busy = busy,
            onPasteLink = onPasteLink,
            onClearLink = onClearLink,
            onRun = onRun,
            onPickFile = onPickFile
        )

        when (phase) {
            is UiPhase.Downloading,
            UiPhase.Extracting,
            is UiPhase.Transcribing -> {
                ProgressCard(phase = phase, onCancel = onCancel)
            }
            is UiPhase.Error -> {
                ErrorCard(
                    message = phase.message,
                    isManual = phase.isManualTest,
                    showConnectCookies = SealDownloader.looksLikeLoginRequired(phase.message),
                    onConnectCookies = onConnectCookies,
                    onBackToAuto = onBackToAuto
                )
            }
            else -> Unit
        }

        preFlight?.let { pf ->
            PreFlightCard(
                fileName = pf.fileName,
                durationLabel = BillingManager.formatDuration(pf.durationSeconds),
                modeLabel = pf.estimate.mode.shortUiLabel(forcedLanguage) +
                    if (pf.estimate.includeDiarize) " + Diarize" else "",
                estimatedUsd = pf.estimatedUsdDisplay,
                hardStopMessage = pf.hardStopMessage,
                autoTranscribe = autoTranscribeOnShare,
                autoTranscribeEnabled = pf.hardStopMessage == null,
                routeHint = pf.routeHint,
                onAutoTranscribeChange = onAutoTranscribeChange,
                onConfirm = { onConfirmPreFlight(pf) },
                onDismiss = onDismissPreFlight,
                busy = busy
            )
        }

        if (savedMedia != null) {
            SavedMediaBanner(
                media = savedMedia,
                onOpen = { onOpenSavedMedia(savedMedia) },
                onOpenDownloads = onOpenDownloads,
                onShareFile = { onShareSavedMedia(savedMedia) }
            )
        }

        if (transcriptText.isNotBlank() || phase is UiPhase.Done) {
            ResultWorkspaceCard(
                text = transcriptText,
                sourceTitleOrUrl = sourceTitleOrUrl,
                metaLabel = lastResultMeta?.resultBadge(copiedHint = copyFeedback),
                onTextChange = onTextChange,
                onCopy = onCopy,
                onShare = onShare,
                onClear = onClearResult
            )
        }
    }
}

@Composable
private fun SavedMediaBanner(
    media: SavedMediaInfo,
    onOpen: () -> Unit,
    onOpenDownloads: () -> Unit,
    onShareFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "✅ Guardado en Descargas/KlipNote: ${media.displayName}",
            color = Accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(media.openLabel, color = Accent, fontSize = 12.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenDownloads,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Ver en Descargas", color = Accent, fontSize = 12.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onShareFile,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Compartir", color = Accent, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LinkHeroCard(
    linkInput: String,
    onLinkChange: (String) -> Unit,
    linkMode: SealDownloader.LinkMode,
    onModeChange: (SealDownloader.LinkMode) -> Unit,
    downloadKind: SealDownloader.DownloadKind,
    onDownloadKindChange: (SealDownloader.DownloadKind) -> Unit,
    sealReady: SealDownloader.ReadyState,
    busy: Boolean,
    onPasteLink: () -> Unit,
    onClearLink: () -> Unit,
    onRun: () -> Unit,
    onPickFile: () -> Unit
) {
    val primaryLabel = when (linkMode) {
        SealDownloader.LinkMode.TEXT_ONLY -> "Extraer Guion"
        SealDownloader.LinkMode.AUDIO_AND_TEXT -> "Descargar Audio y Guion"
        SealDownloader.LinkMode.VIDEO_AND_TEXT -> "Descargar Video y Guion"
        SealDownloader.LinkMode.DOWNLOAD_ONLY -> "Descargar Archivo"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = linkInput,
            onValueChange = onLinkChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("Pega enlace de YouTube, TikTok, IG, X...", color = Muted)
            },
            trailingIcon = {
                if (linkInput.isNotBlank()) {
                    IconButton(onClick = onClearLink, enabled = !busy) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Muted)
                    }
                } else {
                    IconButton(onClick = onPasteLink, enabled = !busy) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Pegar", tint = Accent)
                    }
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

        // 2x2 — todos los modos visibles (sin LazyRow que recorta Solo Bajar)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeChip(
                    selected = linkMode == SealDownloader.LinkMode.TEXT_ONLY,
                    label = "Solo Texto",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(SealDownloader.LinkMode.TEXT_ONLY) }
                )
                ModeChip(
                    selected = linkMode == SealDownloader.LinkMode.AUDIO_AND_TEXT,
                    label = "Audio+Texto",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(SealDownloader.LinkMode.AUDIO_AND_TEXT) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeChip(
                    selected = linkMode == SealDownloader.LinkMode.VIDEO_AND_TEXT,
                    label = "Video+Texto",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(SealDownloader.LinkMode.VIDEO_AND_TEXT) }
                )
                ModeChip(
                    selected = linkMode == SealDownloader.LinkMode.DOWNLOAD_ONLY,
                    label = "Solo Bajar",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
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

        Button(
            onClick = onRun,
            enabled = !busy && linkInput.isNotBlank() &&
                sealReady == SealDownloader.ReadyState.READY,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color.Black,
                disabledContainerColor = Accent.copy(alpha = 0.35f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(primaryLabel, fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = onPickFile,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Accent)
            Spacer(Modifier.size(8.dp))
            Text(
                "📂 Elegir video o audio del teléfono",
                color = Accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ProgressCard(phase: UiPhase, onCancel: () -> Unit) {
    val (label, progress) = when (phase) {
        is UiPhase.Downloading -> {
            val pct = Regex("""(\d+)%""").find(phase.label)?.groupValues?.getOrNull(1)
                ?.toFloatOrNull()?.div(100f)
            phase.label to pct
        }
        UiPhase.Extracting -> "Extrayendo audio…" to null
        is UiPhase.Transcribing ->
            (phase.motorHint.ifBlank { "Transcribiendo…" }) to null
        else -> return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Accent,
                strokeWidth = 2.5.dp
            )
            Text(label, color = OnBg, modifier = Modifier.weight(1f))
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Accent,
                trackColor = Color(0xFF0F1623)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Accent,
                trackColor = Color(0xFF0F1623)
            )
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Cancelar", color = Color(0xFFF87171))
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    isManual: Boolean,
    showConnectCookies: Boolean = false,
    onConnectCookies: () -> Unit = {},
    onBackToAuto: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(message, color = Color(0xFFF87171))
        if (showConnectCookies) {
            Text(
                "Conecta tu propia cuenta de ese sitio. Las cookies se guardan solo en este teléfono.",
                color = Muted,
                fontSize = 12.sp
            )
            Button(
                onClick = onConnectCookies,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Conectar mi cuenta", fontWeight = FontWeight.SemiBold)
            }
        }
        if (isManual) {
            TextButton(onClick = onBackToAuto) {
                Text("Volver a Auto (Cascada)", color = Accent)
            }
        }
    }
}

@Composable
private fun ResultWorkspaceCard(
    text: String,
    sourceTitleOrUrl: String,
    metaLabel: String?,
    onTextChange: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        metaLabel?.let {
            Text(
                it,
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Copiar", color = Accent)
            }
            TextButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Compartir", color = Accent)
            }
            TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Clear, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Limpiar", color = Muted)
            }
        }
        if (text.isNotBlank()) {
            AiStudyActionsSection(
                transcript = text,
                sourceTitleOrUrl = sourceTitleOrUrl
            )
        }
        SelectionContainer {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
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
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AiStudyActionsSection(
    transcript: String,
    sourceTitleOrUrl: String,
    modifier: Modifier = Modifier
) {
    if (transcript.isBlank()) return

    val context = LocalContext.current
    var replyInSpanish by remember {
        mutableStateOf(AiStudyPrompts.isReplyInSpanish(context))
    }
    /** null = nada; MASTER_ALL_8 o AiStudyMode.name */
    var lastCopiedId by remember { mutableStateOf<String?>(null) }
    var lastCopiedLabel by remember { mutableStateOf<String?>(null) }
    var lastCopiedPrompt by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lastCopiedId) {
        val id = lastCopiedId ?: return@LaunchedEffect
        delay(2_500)
        if (lastCopiedId == id) lastCopiedId = null
    }

    fun markCopied(id: String, label: String, prompt: String) {
        copyToClipboard(context, prompt)
        lastCopiedPrompt = prompt
        lastCopiedLabel = label
        lastCopiedId = id
    }

    fun copyMode(mode: AiStudyMode) {
        val prompt = AiStudyPrompts.build(
            mode = mode,
            transcript = transcript,
            sourceTitleOrUrl = sourceTitleOrUrl,
            replyInSpanish = replyInSpanish
        )
        markCopied(mode.name, mode.modeName, prompt)
    }

    fun copyMasterAll8() {
        val prompt = AiStudyPrompts.buildMasterAll8(
            transcript = transcript,
            sourceTitleOrUrl = sourceTitleOrUrl,
            replyInSpanish = replyInSpanish
        )
        markCopied(
            AiStudyPrompts.MASTER_ALL_8,
            AiStudyPrompts.MASTER_ALL_8_MODE_NAME,
            prompt
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1623))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "✨ Estudiar con IA",
                color = OnBg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            FilterChip(
                selected = replyInSpanish,
                onClick = {
                    replyInSpanish = !replyInSpanish
                    AiStudyPrompts.setReplyInSpanish(context, replyInSpanish)
                },
                label = {
                    Text(
                        if (replyInSpanish) {
                            "🌐 Salida: Español"
                        } else {
                            "🎞️ Idioma del video"
                        },
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent.copy(alpha = 0.22f),
                    selectedLabelColor = Accent,
                    containerColor = Color(0xFF1A2333),
                    labelColor = Muted
                )
            )
        }

        val masterJustCopied = lastCopiedId == AiStudyPrompts.MASTER_ALL_8
        Button(
            onClick = { copyMasterAll8() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (masterJustCopied) {
                    Accent.copy(alpha = 0.85f)
                } else {
                    Accent
                },
                contentColor = Color(0xFF04110A)
            )
        ) {
            Text(
                if (masterJustCopied) {
                    "✓ Copiado al portapapeles"
                } else {
                    AiStudyPrompts.MASTER_ALL_8_LABEL
                },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AiStudyMode.entries.forEach { mode ->
                val justCopied = lastCopiedId == mode.name
                AssistChip(
                    onClick = { copyMode(mode) },
                    label = {
                        Text(
                            if (justCopied) "✓ Copiado al portapapeles" else mode.chipLabel,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (justCopied) {
                            Accent.copy(alpha = 0.28f)
                        } else {
                            Color(0xFF1A2333)
                        },
                        labelColor = if (justCopied) Accent else OnBg
                    )
                )
            }
        }

        lastCopiedId?.let {
            val prompt = lastCopiedPrompt
            val label = lastCopiedLabel ?: "Prompt"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Accent.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "✓ Copiado al portapapeles · $label",
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        val sendText = prompt ?: return@TextButton
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, sendText)
                        }
                        context.startActivity(
                            Intent.createChooser(send, "Enviar a IA")
                        )
                    }
                ) {
                    Text("🚀 Enviar a IA", color = Accent, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun GuionesTab(
    entries: List<TranscriptHistoryStore.Entry>,
    search: String,
    onSearchChange: (String) -> Unit,
    expandedId: String?,
    onExpandToggle: (String) -> Unit,
    onCopy: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val filtered = remember(entries, search) {
        val q = search.trim().lowercase()
        if (q.isBlank()) entries
        else entries.filter {
            it.text.lowercase().contains(q) ||
                it.sourceTitleOrUrl.lowercase().contains(q) ||
                it.providerUsed.lowercase().contains(q)
        }
    }
    val fmt = remember {
        SimpleDateFormat("dd MMM · HH:mm", Locale("es", "MX"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Buscar en mis guiones...", color = Muted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color(0xFF243044),
                focusedTextColor = OnBg,
                unfocusedTextColor = OnBg,
                cursorColor = Accent
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${filtered.size} guion${if (filtered.size == 1) "" else "es"}",
                color = Muted,
                fontSize = 12.sp
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Borrar historial", color = Color(0xFFF87171))
                }
            }
        }
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Aún no hay guiones guardados.", color = Muted)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { entry ->
                    val expanded = expandedId == entry.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Card)
                            .clickable { onExpandToggle(entry.id) }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            entry.sourceTitleOrUrl,
                            color = OnBg,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${fmt.format(Date(entry.timestampMs))} · ${entry.providerUsed}",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Text(
                            entry.text,
                            color = OnBg.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (expanded) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(onClick = { onCopy(entry.text) }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = Accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text("Copiar texto", color = Accent)
                                }
                                TextButton(onClick = { onOpenLink(entry.sourceTitleOrUrl) }) {
                                    Icon(
                                        Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = Accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text("Abrir link", color = Accent)
                                }
                                TextButton(onClick = { onDelete(entry.id) }) {
                                    Text("Borrar", color = Color(0xFFF87171))
                                }
                            }
                            if (entry.text.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                AiStudyActionsSection(
                                    transcript = entry.text,
                                    sourceTitleOrUrl = entry.sourceTitleOrUrl
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotoresTab(
    routingSelection: RoutingSelection,
    keyMasks: Map<SttProvider, String?>,
    onSelectAuto: () -> Unit,
    onSelectManual: (SttProvider) -> Unit,
    onOpenKeys: () -> Unit,
    creditState: CreditUiState,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCostSettings: () -> Unit,
    onVerifyOffers: () -> Unit,
    onAllowPaidChange: (Boolean) -> Unit,
    onInworldUsedMinutesSave: (Double) -> Unit,
    onAssemblyRemainingUsdSave: (Double) -> Unit,
    onEngineEnabledChange: (SttProvider, Boolean) -> Unit,
    onProbeProvider: (SttProvider) -> Unit,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    forcedLanguage: String,
    onForcedLanguageChange: (String) -> Unit,
    diarize: Boolean,
    onDiarizeChange: (Boolean) -> Unit,
    sealReady: SealDownloader.ReadyState,
    updatingYtDlp: Boolean,
    onUpdateYtDlp: () -> Unit,
    cookiesEnabled: Boolean,
    cookiesRevision: Int,
    onConnectCookies: (CookieSite) -> Unit,
    onToggleCookies: (Boolean) -> Unit,
    onClearCookies: (CookieSite) -> Unit,
    notifyWhenDone: Boolean,
    onNotifyWhenDoneChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var advancedOpen by remember { mutableStateOf(diarize) }
    val connected = remember(cookiesRevision) { CookieStore.connectedSites(context).toSet() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionLabel("1 · Modo de enrutado")
        RoutingModeChips(
            selection = routingSelection,
            keyMasks = keyMasks,
            onSelectAuto = onSelectAuto,
            onSelectManual = onSelectManual,
            onOpenKeys = onOpenKeys
        )

        SectionLabel("2 · Motores, ofertas y consumo")
        CreditControlPanel(
            state = creditState.copy(expanded = true),
            onToggleExpand = onToggleExpand,
            onRefresh = onRefresh,
            onOpenHistory = onOpenHistory,
            onOpenCostSettings = onOpenCostSettings,
            onVerifyOffers = onVerifyOffers,
            onAllowPaidChange = onAllowPaidChange,
            onInworldUsedMinutesSave = onInworldUsedMinutesSave,
            onAssemblyRemainingUsdSave = onAssemblyRemainingUsdSave,
            onOpenKeys = onOpenKeys,
            onProbeProvider = onProbeProvider,
            onEngineEnabledChange = onEngineEnabledChange
        )

        SectionLabel("3 · Llaves y mantenimiento")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onOpenKeys,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A2436),
                    contentColor = OnBg
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("API Keys (BYOK)", fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Avisarme al terminar", color = OnBg, fontSize = 14.sp)
                    Text(
                        "Notificación cuando termine la descarga o el guion",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = notifyWhenDone,
                    onCheckedChange = onNotifyWhenDoneChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Modo \$0 (nunca usar el motor de pago)",
                    color = OnBg,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = !creditState.allowPaidFallback,
                    onCheckedChange = { strict -> onAllowPaidChange(!strict) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent
                    )
                )
            }

            Text(stringResource(R.string.lang_detection_label), color = Muted, fontSize = 12.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeChip(
                    selected = languageMode == LanguageMode.AUTO,
                    label = stringResource(R.string.mode_auto),
                    enabled = true,
                    onClick = { onLanguageModeChange(LanguageMode.AUTO) }
                )
                ModeChip(
                    selected = languageMode == LanguageMode.MULTI,
                    label = stringResource(R.string.mode_multi),
                    enabled = true,
                    onClick = { onLanguageModeChange(LanguageMode.MULTI) }
                )
                ModeChip(
                    selected = languageMode == LanguageMode.FIXED,
                    label = stringResource(R.string.mode_fixed),
                    enabled = true,
                    onClick = { onLanguageModeChange(LanguageMode.FIXED) }
                )
            }

            Text(stringResource(R.string.lang_target_label), color = Muted, fontSize = 12.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                COMMON_STT_LANGUAGES.forEach { opt ->
                    ModeChip(
                        selected = languageMode == LanguageMode.FIXED &&
                            forcedLanguage.equals(opt.code, ignoreCase = true),
                        label = opt.label,
                        enabled = true,
                        onClick = { onForcedLanguageChange(opt.code) }
                    )
                }
            }
            if (languageMode == LanguageMode.AUTO) {
                Text(
                    "Auto detecta el idioma hablado. Elige un código solo si quieres forzar reconocimiento.",
                    color = Muted,
                    fontSize = 11.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { advancedOpen = !advancedOpen }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.advanced_section),
                    color = Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (advancedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (advancedOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.diarize), color = OnBg, fontSize = 14.sp)
                        Text(
                            stringResource(R.string.diarize_hint),
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = diarize,
                        onCheckedChange = onDiarizeChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F1623))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Motor de descargas (yt-dlp)", color = OnBg, fontWeight = FontWeight.Medium)
                Text(
                    "Se actualiza solo cada 3 días y en cuanto una descarga falla porque un sitio cambió. " +
                        "No necesitas tocar nada; este botón es solo para forzarlo.",
                    color = Muted,
                    fontSize = 11.sp
                )
                Text(
                    when (sealReady) {
                        SealDownloader.ReadyState.READY -> "Listo"
                        SealDownloader.ReadyState.IDLE -> "Inicializando…"
                        SealDownloader.ReadyState.ERROR -> "Error — reintenta actualizar"
                    },
                    color = Muted,
                    fontSize = 12.sp
                )
                OutlinedButton(
                    onClick = onUpdateYtDlp,
                    enabled = !updatingYtDlp && sealReady == SealDownloader.ReadyState.READY,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (updatingYtDlp) "Actualizando…" else "Actualizar motor yt-dlp",
                        color = Accent
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F1623))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Cuentas para descargar (cookies)", color = OnBg, fontWeight = FontWeight.Medium)
                Text(
                    "Solo hace falta si un sitio te pide iniciar sesión. Cada quien conecta SU cuenta; " +
                        "las cookies se quedan en este teléfono, no se suben ni entran en backup.",
                    color = Muted,
                    fontSize = 11.sp
                )
                CookieSite.entries.forEach { site ->
                    val isConnected = site in connected
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(site.label, color = OnBg, fontSize = 14.sp)
                            Text(
                                if (isConnected) "✓ Conectado" else "Sin conectar",
                                color = if (isConnected) Accent else Muted,
                                fontSize = 11.sp
                            )
                        }
                        if (isConnected) {
                            TextButton(onClick = { onClearCookies(site) }) {
                                Text("Desconectar", color = Color(0xFFF87171), fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onConnectCookies(site) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Conectar", color = Accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Usar cookies en descargas", color = OnBg, fontSize = 14.sp)
                    Switch(
                        checked = cookiesEnabled,
                        onCheckedChange = onToggleCookies,
                        enabled = connected.isNotEmpty(),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ModeChip(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = chipColors()
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color(0xFF0F1623),
    labelColor = Muted,
    selectedContainerColor = Accent.copy(alpha = 0.2f),
    selectedLabelColor = Accent
)

private fun readKeyMasks(context: android.content.Context): Map<SttProvider, String?> = mapOf(
    SttProvider.GROQ_FREE to ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.GROQ),
    SttProvider.INWORLD to ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.INWORLD),
    SttProvider.ASSEMBLY_AI to ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.ASSEMBLY_AI),
    SttProvider.DEEPGRAM to ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.DEEPGRAM),
    SttProvider.PAID_FALLBACK to ApiKeyStore.maskDisplay(context, ApiKeyStore.ProviderSlot.PAID_FALLBACK)
)

private suspend fun runTranscription(
    context: android.content.Context,
    uri: Uri,
    fileName: String,
    mode: LanguageMode,
    forcedLanguageCode: String? = null,
    diarize: Boolean,
    routingSelection: RoutingSelection,
    compressForStt: Boolean = false,
    savedInDownloads: Boolean = false,
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
    var sttCompact: File? = null
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

        val audioForStt = if (compressForStt) {
            onPhase(UiPhase.Transcribing("Preparando audio liviano para transcribir…"))
            withContext(Dispatchers.IO) {
                SealDownloader.compressAudioForStt(context, extracted!!.file).also { sttCompact = it }
            }
        } else {
            extracted.file
        }
        val mimeForStt = if (sttCompact != null) "audio/mp4" else extracted.mimeType

        val motorHint = when (routingSelection) {
            is RoutingSelection.Auto -> "Transcribiendo con cascada Auto…"
            is RoutingSelection.Manual ->
                "Transcribiendo con ${routingSelection.provider.displayName()}…"
        }
        onPhase(UiPhase.Transcribing(motorHint))
        val result = withContext(Dispatchers.IO) {
            SttRouter.transcribe(
                context = context,
                audioFile = audioForStt,
                mimeType = mimeForStt,
                mode = mode,
                diarize = diarize,
                deepgramBalanceUsd = balanceBefore,
                selection = routingSelection,
                forcedLanguageCode = forcedLanguageCode,
                savedInDownloads = savedInDownloads
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
        sttCompact?.let { runCatching { it.delete() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SiteCookiesSheet(
    site: CookieSite,
    onDismiss: () -> Unit,
    onSaved: (Int) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf("webview") } // webview | paste
    var pasteText by remember { mutableStateOf("") }
    var status by remember(site) { mutableStateOf("Inicia sesión en ${site.label} y pulsa Guardar") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var busy by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Card,
        contentColor = OnBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Conectar mi cuenta de ${site.label}",
                fontWeight = FontWeight.Bold,
                color = OnBg,
                fontSize = 18.sp
            )
            Text(
                "Se guardan en privado en este teléfono. No se suben a ningún lado.",
                color = Muted,
                fontSize = 12.sp
            )
            site.note?.let { Text(it, color = Color(0xFFFBBF24), fontSize = 11.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(
                    selected = mode == "webview",
                    label = "Login WebView",
                    enabled = !busy,
                    onClick = { mode = "webview" }
                )
                ModeChip(
                    selected = mode == "paste",
                    label = "Pegar cookies.txt",
                    enabled = !busy,
                    onClick = { mode = "paste" }
                )
            }

            if (mode == "webview") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F1623))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = WebViewClient()
                                loadUrl(site.loginUrl)
                                webViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webViewRef = it }
                    )
                }
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        try {
                            CookieManager.getInstance().flush()
                            val ua = webViewRef?.settings?.userAgentString
                            val count = CookieStore.saveFromCookieManager(context, site, ua)
                            status = "Guardadas $count cookies"
                            onSaved(count)
                        } catch (t: Throwable) {
                            status = t.message ?: "No se pudieron guardar"
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Guardar cookies del WebView", fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    placeholder = {
                        Text("Pega un cookies.txt (formato Netscape) o name=value; …", color = Muted)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Muted.copy(alpha = 0.4f),
                        focusedTextColor = OnBg,
                        unfocusedTextColor = OnBg,
                        cursorColor = Accent
                    )
                )
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        try {
                            val count = CookieStore.saveFromPaste(context, site, pasteText)
                            status = "Guardadas $count cookies"
                            onSaved(count)
                        } catch (t: Throwable) {
                            status = t.message ?: "Pegado inválido"
                            busy = false
                        }
                    },
                    enabled = !busy && pasteText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Guardar cookies pegadas", fontWeight = FontWeight.SemiBold)
                }
            }

            Text(status, color = Muted, fontSize = 12.sp)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Cerrar", color = Muted)
            }
        }
    }
}
