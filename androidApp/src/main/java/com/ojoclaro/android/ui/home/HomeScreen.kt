package com.ojoclaro.android.ui.home

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojoclaro.android.BuildConfig
import com.ojoclaro.android.accessibility.AccessibilityScreenReader
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.external.CommandResult
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.external.WhatsAppIntentHelper
import com.ojoclaro.android.global.GlobalAssistantService
import com.ojoclaro.android.handoff.ExternalAppHandoffNotifier
import com.ojoclaro.android.maps.LocationProvider
import com.ojoclaro.android.maps.MapsActionExecutor
import com.ojoclaro.android.model.AppState
import com.ojoclaro.android.model.RobotSessionState
import com.ojoclaro.android.model.humanLabel
import com.ojoclaro.android.phone.PhoneActionExecutor
import com.ojoclaro.android.speech.SpeechController
import com.ojoclaro.android.ui.camera.TextScanScreen
import com.ojoclaro.android.ui.components.ActivityEntry
import com.ojoclaro.android.ui.components.AssistantResponseCard
import com.ojoclaro.android.ui.components.AssistantStateCard
import com.ojoclaro.android.ui.components.AssistantStatusKind
import com.ojoclaro.android.ui.components.AssistantStatusPill
import com.ojoclaro.android.ui.components.AssistantStatusViewModel
import com.ojoclaro.android.ui.components.GuidedActionCard
import com.ojoclaro.android.ui.components.HistoryCard
import com.ojoclaro.android.ui.components.LastActionCard
import com.ojoclaro.android.ui.components.ListenIndicator
import com.ojoclaro.android.ui.components.OjoClaroConfirmRow
import com.ojoclaro.android.ui.components.OjoClaroDangerButton
import com.ojoclaro.android.ui.components.OjoClaroPrimaryButton
import com.ojoclaro.android.ui.components.OjoClaroSecondaryButton
import com.ojoclaro.android.ui.components.ScreenReadingCard
import com.ojoclaro.android.ui.components.SuggestedActionCard
import com.ojoclaro.android.ui.theme.OjoClaroPalette
import com.ojoclaro.android.voice.AndroidSpeechInputEngine
import com.ojoclaro.android.voice.VoiceCommandController
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import com.ojoclaro.android.voice.VoiceRetryHandle
import com.ojoclaro.android.voice.VoiceRetryScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    listeningTriggers: StateFlow<Long> = MutableStateFlow(0L),
    stopSpeechTriggers: StateFlow<Long> = MutableStateFlow(0L),
    debugTextSubmissions: Flow<String> = emptyFlow()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            application = context.applicationContext as Application,
            agentBridgeDispatch = selectAgentBridgeDispatchControllerForHome(),
            agentBridgeVoiceCoordinator = selectAgentBridgeVoiceCoordinatorForHome(),
            screenChangeAnnouncements = selectScreenChangeAnnouncementsForHome()
        )
    )

    val state by viewModel.state.collectAsState()
    val appState by viewModel.appState.collectAsState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var microphoneGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO))
    }
    var productDisplayMode by remember {
        mutableStateOf(defaultProductDisplayMode())
    }
    var requestedMicrophoneOnLaunch by remember { mutableStateOf(false) }
    val currentAppState = rememberUpdatedState(appState)
    val currentMicrophoneGranted = rememberUpdatedState(microphoneGranted)
    val currentUiState = rememberUpdatedState(state)
    val voiceControllerHolder = remember { arrayOfNulls<VoiceCommandController>(1) }
    val speechController = remember(context, viewModel) {
        SpeechController(
            context = context,
            onSpeechStarted = {
                viewModel.onTtsSpeakingChanged(true)
                scope.launch {
                    voiceControllerHolder[0]?.pauseForSpeech()
                }
            },
            onSpeechFinished = {
                scope.launch {
                    delay(TTS_TO_MIC_DELAY_MILLIS)
                    viewModel.onTtsSpeakingChanged(false)
                    viewModel.onSpeechDispatched()
                    if (
                        currentMicrophoneGranted.value &&
                        currentUiState.value.robotEnabled &&
                        canStartListeningAfterSpeech(currentAppState.value)
                    ) {
                        voiceControllerHolder[0]?.resumeAfterSpeech()
                    }
                }
            },
            onSpeechStopped = {
                scope.launch {
                    viewModel.onTtsSpeakingChanged(false)
                    viewModel.onSpeechDispatched()
                    if (
                        currentMicrophoneGranted.value &&
                        currentUiState.value.robotEnabled &&
                        canStartListeningAfterSpeech(currentAppState.value)
                    ) {
                        voiceControllerHolder[0]?.resumeAfterSpeech()
                    }
                }
            }
        )
    }
    val voiceDispatcher = remember(viewModel, speechController) {
        VoiceCommandDispatcher(
            executeCommand = { recognizedText ->
                if (isResetFlowCommand(recognizedText)) {
                    speechController.stop()
                }
                viewModel.onVoiceFinalText(recognizedText)
            },
            stopSpeechNow = {
                voiceControllerHolder[0]?.stopForCommandAndResume()
                speechController.stop()
                viewModel.onStopSpeechRequested()
            },
            updatePartialText = viewModel::onVoicePartialText
        )
    }
    val voiceController = remember(context, voiceDispatcher, viewModel) {
        VoiceCommandController(
            engine = AndroidSpeechInputEngine(context),
            hasRecordAudioPermission = {
                hasPermission(context, Manifest.permission.RECORD_AUDIO)
            },
            onPartialTextCallback = voiceDispatcher::onPartialText,
            onFinalTextCallback = voiceDispatcher::onFinalText,
            onErrorCallback = viewModel::onVoiceError,
            onReadyCallback = viewModel::onVoiceReady,
            onStateChanged = viewModel::onVoiceListeningStateChanged,
            onErrorCodeCallback = viewModel::onSpeechRecognizerError,
            onDiagnosticCallback = viewModel::onVoiceDiagnosticChanged,
            onStatusMessageCallback = viewModel::onVoiceStatusMessage,
            retryScheduler = VoiceRetryScheduler { delayMillis, action ->
                val job = scope.launch {
                    delay(delayMillis)
                    action()
                }
                VoiceRetryHandle { job.cancel() }
            }
        )
    }
    voiceControllerHolder[0] = voiceController

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startScanning()
        } else {
            viewModel.onCameraPermissionDenied()
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
        if (granted) {
            viewModel.onMicrophonePermissionGranted()
            if (currentUiState.value.robotEnabled) {
                voiceController.startListening()
            }
        } else {
            viewModel.onMicrophonePermissionDenied()
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        viewModel.onLocationPermissionResult(grants.values.any { it })
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceController.destroy()
            speechController.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, voiceController, microphoneGranted) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    voiceController.stopListening()
                    viewModel.pauseRobotSession()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onForegroundReturned()
                    if (
                        microphoneGranted &&
                        currentUiState.value.robotEnabled &&
                        shouldAutoStartListeningOnResume(currentAppState.value)
                    ) {
                        voiceController.startListening()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.speechEvents.collect { event ->
            voiceController.pauseForSpeech()
            speechController.speak(event.text, force = event.force)
        }
    }

    LaunchedEffect(viewModel) {
        kotlinx.coroutines.yield()
        val hasMicrophone = hasPermission(context, Manifest.permission.RECORD_AUDIO)
        microphoneGranted = hasMicrophone
        viewModel.greetIfFirstTime(hasMicrophone)
        if (!hasMicrophone && !requestedMicrophoneOnLaunch) {
            requestedMicrophoneOnLaunch = true
            delay(900)
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val listeningTrigger by listeningTriggers.collectAsState()
    LaunchedEffect(listeningTrigger) {
        if (listeningTrigger == 0L) return@LaunchedEffect
        ExternalAppHandoffNotifier.cancel(context)
        GlobalAssistantService.requestStop(context)
        val hasMicrophone = hasPermission(context, Manifest.permission.RECORD_AUDIO)
        microphoneGranted = hasMicrophone
        viewModel.onListeningIntentReceived(hasMicrophone)
        if (hasMicrophone) {
            voiceController.startListening()
        } else if (!requestedMicrophoneOnLaunch) {
            requestedMicrophoneOnLaunch = true
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val stopSpeechTrigger by stopSpeechTriggers.collectAsState()
    LaunchedEffect(stopSpeechTrigger) {
        if (stopSpeechTrigger == 0L) return@LaunchedEffect
        ExternalAppHandoffNotifier.cancel(context)
        GlobalAssistantService.requestSilence(context)
        voiceController.stopListening()
        speechController.stop()
        viewModel.onStopSpeechRequested()
    }

    LaunchedEffect(appState, microphoneGranted, state.robotEnabled, state.ttsSpeaking) {
        if (!microphoneGranted) return@LaunchedEffect
        voiceController.setExpectingResponse(
            shouldUseExtendedListening(appState = appState, agentState = state.agentState)
        )
        if (!state.robotEnabled) {
            voiceController.pauseListening()
            return@LaunchedEffect
        }
        if (state.ttsSpeaking || state.robotSessionState == RobotSessionState.SPEAKING) {
            voiceController.pauseListening()
            return@LaunchedEffect
        }
        when (appState) {
            AppState.IDLE,
            AppState.WAITING_WHATSAPP_ACTION,
            AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE,
            AppState.WAITING_CONTACT,
            AppState.WAITING_MESSAGE,
            AppState.WAITING_CONFIRMATION -> voiceController.startListening()
            AppState.SCANNING,
            AppState.PROCESSING,
            AppState.EXTERNAL_APP_HANDOFF,
            AppState.GLOBAL_ASSISTANT_ACTIVE,
            AppState.GLOBAL_ASSISTANT_UNAVAILABLE -> voiceController.pauseListening()
            AppState.PERMISSION_REQUIRED -> voiceController.stopListening()
            AppState.LISTENING,
            AppState.SPEAKING,
            AppState.ERROR -> Unit
        }
    }

    LaunchedEffect(appState, state.agentState) {
        voiceController.setExpectingResponse(
            shouldUseExtendedListening(appState = appState, agentState = state.agentState)
        )
    }

    LaunchedEffect(state.voiceListenRequestId) {
        if (state.voiceListenRequestId == 0L || !microphoneGranted || !state.robotEnabled) return@LaunchedEffect
        if (!state.ttsSpeaking && canStartListeningAfterSpeech(appState)) {
            voiceController.startListening()
        }
    }

    LaunchedEffect(debugTextSubmissions) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        debugTextSubmissions.collect { text ->
            viewModel.submitDebugInjectedText(text)
        }
    }

    LaunchedEffect(viewModel, context) {
        val whatsAppIntentHelper = WhatsAppIntentHelper(context)
        val phoneActionExecutor = PhoneActionExecutor(context)
        val mapsActionExecutor = MapsActionExecutor(context)
        viewModel.externalActionEvents.collect { event ->
            suspend fun executeExternalAction(action: ExternalActionEvent): CommandResult = when (action) {
                ExternalActionEvent.OpenWhatsApp ->
                    whatsAppIntentHelper.openWhatsApp()

                ExternalActionEvent.OpenPhone ->
                    phoneActionExecutor.openDialer()

                ExternalActionEvent.ReadVisibleScreen ->
                    AccessibilityScreenReader.readVisibleScreen(context)

                is ExternalActionEvent.ComposeWhatsAppMessage ->
                    whatsAppIntentHelper.composeMessage(
                        contactName = action.contactName,
                        messageText = action.messageText
                    )

                is ExternalActionEvent.OpenWhatsAppChat ->
                    whatsAppIntentHelper.openChat(
                        contactName = action.contactName,
                        phoneE164 = action.phoneE164
                    )

                is ExternalActionEvent.DialPhoneNumber ->
                    phoneActionExecutor.prepareCall(
                        contactName = action.contactName,
                        phoneNumber = action.phoneNumber
                    )

                ExternalActionEvent.OpenMaps ->
                    mapsActionExecutor.openMaps()

                is ExternalActionEvent.OpenCurrentLocation ->
                    mapsActionExecutor.openCurrentLocation(
                        latitude = action.latitude,
                        longitude = action.longitude
                    )

                is ExternalActionEvent.NavigateToDestination ->
                    mapsActionExecutor.openNavigationTo(action.destination)

                is ExternalActionEvent.NavigateToCoordinates ->
                    mapsActionExecutor.openNavigationToCoordinates(
                        latitude = action.latitude,
                        longitude = action.longitude,
                        label = action.label
                    )

                ExternalActionEvent.RequestLocationPermission -> {
                    locationPermissionLauncher.launch(LocationProvider.REQUIRED_PERMISSIONS.toTypedArray())
                    CommandResult.Success("Pedido permiso de ubicación.")
                }

                is ExternalActionEvent.ExternalAppHandoff ->
                    CommandResult.NotSupported("No pude abrir esa app externa.")
            }

            val result = if (event is ExternalActionEvent.ExternalAppHandoff) {
                voiceController.stopListening()
                val speechDelay = handoffSpeechDelayMillis(event.spokenText)
                val globalStarted = GlobalAssistantService.startContinuation(
                    context = context,
                    handoff = event,
                    startListeningDelayMillis = speechDelay + TTS_TO_MIC_DELAY_MILLIS + 500L
                )
                if (!globalStarted) {
                    ExternalAppHandoffNotifier.show(context, event)
                }
                speechController.speak(event.spokenText, force = true)
                delay(speechDelay)
                executeExternalAction(event.delegate).also { launchResult ->
                    viewModel.onExternalHandoffLaunchResult(event, launchResult)
                }
            } else {
                executeExternalAction(event).also(viewModel::onExternalCommandResult)
            }

            if (event is ExternalActionEvent.ExternalAppHandoff && result !is CommandResult.Success) {
                ExternalAppHandoffNotifier.cancel(context)
            }
        }
    }

    if (appState == AppState.SCANNING) {
        TextScanScreen(
            viewModel = viewModel,
            speechController = speechController,
            onClose = viewModel::stopScanning
        )
        return
    }

    val statusKind = resolveAssistantStatusKind(
        appState = appState,
        agentState = state.agentState,
        loading = state.loading,
        micListening = state.micListening,
        ttsSpeaking = state.ttsSpeaking,
        robotEnabled = state.robotEnabled
    )
    val statusTitleText = statusText(appState, state.agentState)
    val statusSupportingText = when {
        state.activeTaskSummary.isNotBlank() -> state.activeTaskSummary
        else -> pendingActionLabel(
            appState = appState,
            agentState = state.agentState,
            pendingSummary = state.pendingDebug
        ).let { pending ->
            if (pending.isNotBlank()) "Pendiente: $pending" else FIRST_USE_GUIDE_TEXT
        }
    }
    val assistantStatus = AssistantStatusViewModel(
        kind = statusKind,
        title = statusTitleText,
        supporting = statusSupportingText
    )

    val isListeningVisible = (state.micListening || appState == AppState.LISTENING) && state.robotEnabled
    val pendingViewState = PendingConfirmationViewState.from(state)
    val cameraGranted = hasPermission(context, Manifest.permission.CAMERA)
    val whatsappStatus = if (
        isPackageInstalled(context.packageManager, WhatsAppIntentHelper.WHATSAPP_PACKAGE) ||
        isPackageInstalled(context.packageManager, WhatsAppIntentHelper.WHATSAPP_BUSINESS_PACKAGE)
    ) {
        "disponible"
    } else {
        "no detectado"
    }
    val diagnosticText = buildHomeDiagnosticText(
        versionName = BuildConfig.VERSION_NAME,
        isDebug = BuildConfig.DEBUG,
        assistantBaseUrlConfigured = BuildConfig.ASSISTANT_BASE_URL.isNotBlank(),
        microphoneGranted = microphoneGranted,
        cameraGranted = cameraGranted,
        ttsAvailable = true,
        whatsappStatus = whatsappStatus,
        robotEnabled = state.robotEnabled,
        accessibilityReady = AccessibilityScreenReader.isServiceEnabled(context),
        waitingConfirmation = state.agentState == AgentState.WAITING_CONFIRMATION ||
            appState == AppState.WAITING_CONFIRMATION,
        whatsappActive = state.externalAppName.equals("WhatsApp", ignoreCase = true) ||
            isWhatsAppWaitingState(state.agentState) ||
            appState == AppState.WAITING_WHATSAPP_ACTION ||
            appState == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE,
        pendingSummary = state.pendingDebug.ifBlank { "ninguna" },
        lastError = state.lastSpeechError.ifBlank { state.error.orEmpty() },
        voiceHearingStatus = state.voiceHearingStatus,
        voiceErrorCategory = state.voiceErrorCategory,
        voiceSpeechEngine = state.voiceSpeechEngine,
        proxyHealth = state.proxyHealth,
        displayMode = productDisplayMode
    )
    val suggestionLine = productUtilitySuggestionText(
        robotEnabled = state.robotEnabled,
        accessibilityReady = AccessibilityScreenReader.isServiceEnabled(context),
        waitingConfirmation = state.agentState == AgentState.WAITING_CONFIRMATION ||
            appState == AppState.WAITING_CONFIRMATION,
        whatsappActive = state.externalAppName.equals("WhatsApp", ignoreCase = true) ||
            isWhatsAppWaitingState(state.agentState) ||
            appState == AppState.WAITING_WHATSAPP_ACTION ||
            appState == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE,
        voiceErrorCategory = state.voiceErrorCategory
    )
    val historyEntries = buildHistoryEntries(state, appState)

    val readingSummary = when {
        appState == AppState.WAITING_WHATSAPP_ACTION ||
            appState == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE ->
            "Estoy mirando WhatsApp. Decime qué chat o qué mensaje querés que abra."
        state.spokenText.isNotBlank() &&
            (state.lastRecognizedSpeechText.contains("pantalla", ignoreCase = true) ||
                state.lastRecognizedSpeechText.contains("lee", ignoreCase = true)) ->
            state.spokenText
        else -> ""
    }
    val readingWarnings = mutableListOf<String>().apply {
        if (state.voiceErrorCategory.isNotBlank() &&
            !state.voiceErrorCategory.equals("ninguno", ignoreCase = true) &&
            !state.voiceErrorCategory.equals("none", ignoreCase = true)
        ) {
            add("Aviso de voz: ${sanitizeDiagnosticValue(state.voiceErrorCategory)}.")
        }
        if (!AccessibilityScreenReader.isServiceEnabled(context)) {
            add("Activá Estela en Accesibilidad para leer la pantalla actual.")
        }
    }

    val handleConfirm: () -> Unit = {
        viewModel.submitVoiceText(CONFIRM_BUTTON_VOICE_PHRASE)
    }
    val handleCancel: () -> Unit = {
        viewModel.submitVoiceText(CANCEL_BUTTON_VOICE_PHRASE)
    }
    val handleRepeat: () -> Unit = {
        viewModel.submitVoiceText("repetir")
    }
    val handleExplain: () -> Unit = {
        viewModel.requestHelp()
    }
    val handleListen: () -> Unit = {
        if (!microphoneGranted) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            if (!state.robotEnabled) {
                viewModel.enableRobotSession(hasMicrophonePermission = true)
            }
            voiceController.startListening()
        }
    }
    val handleReadScreen: () -> Unit = {
        viewModel.submitVoiceText("leer la pantalla")
    }
    val handleDescribeEnvironment: () -> Unit = {
        viewModel.submitVoiceText("describir que tengo enfrente")
    }
    val handleOpenWhatsApp: () -> Unit = {
        viewModel.submitVoiceText("abrir whatsapp")
    }
    val handleLastResult: () -> Unit = {
        viewModel.submitVoiceText("repetir")
    }
    val handleHelp: () -> Unit = {
        viewModel.requestHelp()
    }
    val handleScanText: () -> Unit = {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            viewModel.startScanning()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val handleToggleRobot: () -> Unit = {
        if (state.robotEnabled) {
            voiceController.stopListening()
            speechController.stop()
            viewModel.pauseRobotSession()
        } else {
            val hasMicrophone = hasPermission(context, Manifest.permission.RECORD_AUDIO)
            microphoneGranted = hasMicrophone
            viewModel.enableRobotSession(hasMicrophonePermission = hasMicrophone)
            if (hasMicrophone) {
                voiceController.startListening()
            } else {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    val handleSilence: () -> Unit = {
        voiceController.stopForCommandAndResume()
        speechController.stop()
        viewModel.onStopSpeechRequested()
    }
    val handleReset: () -> Unit = {
        voiceController.stopListening()
        speechController.stop()
        viewModel.resetFlow()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HomeHeader(statusKind = statusKind)

            AssistantStateCard(state = assistantStatus)

            if (pendingViewState.visible) {
                GuidedActionCard(
                    contextTitle = "Confirmación pendiente",
                    proposedAction = pendingViewState.message
                )
                OjoClaroConfirmRow(
                    confirmText = "Sí, continuar",
                    cancelText = "No, cancelar",
                    confirmContentDescription = "Confirmar la acción propuesta por Estela.",
                    cancelContentDescription = "Cancelar la acción propuesta por Estela.",
                    onConfirm = handleConfirm,
                    onCancel = handleCancel
                )
                OjoClaroSecondaryButton(
                    text = "Explicar mejor",
                    contentDescription = "Pedir al asistente que explique con otras palabras.",
                    onClick = handleExplain
                )
            }

            if (isListeningVisible) {
                ListenIndicator(
                    isListening = true,
                    partialText = state.voicePartialText,
                    recognizedText = state.lastRecognizedSpeechText
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OjoClaroPrimaryButton(
                        text = "Confirmar",
                        contentDescription = "Confirmar lo que dije al asistente.",
                        onClick = handleConfirm,
                        modifier = Modifier.weight(1f),
                        large = false
                    )
                    OjoClaroSecondaryButton(
                        text = "Reintentar",
                        contentDescription = "Volver a escuchar mi voz desde cero.",
                        onClick = handleListen,
                        modifier = Modifier.weight(1f),
                        compact = true
                    )
                }
                OjoClaroDangerButton(
                    text = "Cancelar escucha",
                    contentDescription = "Cancelar la escucha actual.",
                    onClick = handleSilence,
                    compact = true
                )
            }

            AssistantResponseCard(text = state.spokenText)

            if (state.loading || appState == AppState.PROCESSING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Procesando tu pedido, esperá un momento." },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = OjoClaroPalette.Orange)
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }

            LastActionCard(
                recognizedSpeech = state.lastRecognizedSpeechText,
                intent = state.lastAgentIntent?.name,
                pendingLabel = pendingActionLabel(
                    appState = appState,
                    agentState = state.agentState,
                    pendingSummary = state.pendingDebug
                ).ifBlank { state.pendingDebug.ifBlank { "—" } }
            )

            SuggestedActionCard(
                contextTitle = "Lo que podés pedir ahora",
                suggestion = suggestionLine
            )

            ScreenReadingCard(
                summary = readingSummary,
                detectedText = state.lastRecognizedSpeechText.takeIf {
                    it.contains("pantalla", ignoreCase = true) ||
                        it.contains("lee", ignoreCase = true)
                } ?: "",
                warnings = readingWarnings
            )

            // Acciones rápidas — el corazón de la demo.
            SectionTitle(text = "Acciones rápidas")

            OjoClaroPrimaryButton(
                text = "Escuchar",
                contentDescription = "Escuchar un comando de voz.",
                onClick = handleListen
            )

            QuickActionGrid(
                actions = listOf(
                    QuickAction(
                        label = "Leer pantalla",
                        description = "Pedirle a Estela que lea lo que hay en la pantalla.",
                        onClick = handleReadScreen
                    ),
                    QuickAction(
                        label = "Describir entorno",
                        description = "Pedirle a Estela que describa el entorno con la cámara.",
                        onClick = handleDescribeEnvironment
                    ),
                    QuickAction(
                        label = "Abrir WhatsApp",
                        description = "Abrir WhatsApp con asistencia guiada.",
                        onClick = handleOpenWhatsApp
                    ),
                    QuickAction(
                        label = "Último resultado",
                        description = "Repetir la última respuesta de Estela.",
                        onClick = handleLastResult
                    ),
                    QuickAction(
                        label = "Leer texto con cámara",
                        description = "Apuntar la cámara para leer texto físico en voz alta.",
                        onClick = handleScanText
                    ),
                    QuickAction(
                        label = "Ayuda",
                        description = "Escuchar ejemplos de comandos disponibles para Estela.",
                        onClick = handleHelp
                    )
                )
            )

            HistoryCard(entries = historyEntries)

            // Controles del robot — encender / pausar / callar / reset.
            SectionTitle(text = "Controles del asistente")

            if (!microphoneGranted) {
                OjoClaroSecondaryButton(
                    text = "Activar voz",
                    contentDescription = "Activar el control por voz con el micrófono.",
                    onClick = {
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }

            OjoClaroSecondaryButton(
                text = if (state.robotEnabled) "Pausar asistente" else "Encender asistente",
                contentDescription = if (state.robotEnabled) {
                    "Pausar el asistente y cerrar el micrófono."
                } else {
                    "Encender el asistente mientras Estela está visible."
                },
                onClick = handleToggleRobot
            )

            OjoClaroDangerButton(
                text = "Callar",
                contentDescription = "Detener la voz del asistente.",
                onClick = handleSilence,
                compact = true
            )

            OjoClaroSecondaryButton(
                text = "Resetear flujo",
                contentDescription = "Resetear el flujo actual sin borrar preferencias.",
                onClick = handleReset,
                compact = true
            )

            if (BuildConfig.DEBUG) {
                OjoClaroSecondaryButton(
                    text = if (productDisplayMode == ProductDisplayMode.QA) "Modo QA" else "Modo Demo",
                    contentDescription = "Cambiar entre modo Demo y modo QA.",
                    onClick = {
                        productDisplayMode = if (productDisplayMode == ProductDisplayMode.QA) {
                            ProductDisplayMode.DEMO
                        } else {
                            ProductDisplayMode.QA
                        }
                    },
                    compact = true
                )
            }

            DiagnosticBlock(text = diagnosticText)

            if (BuildConfig.DEBUG && productDisplayMode == ProductDisplayMode.QA) {
                DebugQaBlock(state = state, appState = appState)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeHeader(statusKind: AssistantStatusKind) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Estela",
            color = OjoClaroPalette.Orange,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Asistente accesible con voz, visión e IA.",
            color = OjoClaroPalette.TextSecondary,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            modifier = Modifier.semantics {
                contentDescription =
                    "Estela. Asistente accesible con voz, visión e IA."
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
        AssistantStatusPill(kind = statusKind)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = text.uppercase(),
        color = OjoClaroPalette.OrangeSoft,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = text }
    )
}

internal data class QuickAction(
    val label: String,
    val description: String,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionGrid(actions: List<QuickAction>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { action ->
                    OjoClaroSecondaryButton(
                        text = action.label,
                        contentDescription = action.description,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                        compact = false
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DiagnosticBlock(text: String) {
    com.ojoclaro.android.ui.components.OjoClaroCard(
        accent = OjoClaroPalette.Outline,
        accentWidth = 1.dp,
        background = OjoClaroPalette.Surface
    ) {
        com.ojoclaro.android.ui.components.OjoClaroCardHeader(
            label = "Diagnóstico",
            title = "Estado técnico"
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            color = OjoClaroPalette.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Diagnóstico. $text" }
        )
    }
}

@Composable
private fun DebugQaBlock(state: HomeUiState, appState: AppState) {
    val debugStateLabel = state.agentState?.name ?: appState.name
    val debugDecision = state.lastDecision.ifBlank { "none" }
    val debugPending = state.pendingDebug.ifBlank { "none" }
    val debugIntentLabel = state.lastAgentIntent?.name ?: "-"
    val debugSpeechError = state.lastSpeechError.ifBlank { "-" }
    val debugTimestamp = if (state.lastCommandTimestampMillis == 0L) "-" else state.lastCommandTimestampMillis.toString()
    val estelaTrace = state.estelaTraceSummary.ifBlank { "-" }
    val body = "Debug seguro QA\n" +
        "Confidence: ${"%.2f".format(state.lastConfidence)}\n" +
        "Source: ${state.decisionSource.ifBlank { "local" }}\n" +
        "Estado: $debugStateLabel\n" +
        "Estela live: ${state.estelaLiveState}\n" +
        "Estela trace:\n$estelaTrace\n" +
        "Task title: ${state.activeTaskTitle.ifBlank { "-" }}\n" +
        "Task step: ${state.activeTaskStep.ifBlank { "-" }}\n" +
        "Intent: $debugIntentLabel\n" +
        "Decision: $debugDecision\n" +
        "Pending: $debugPending\n" +
        "Robot session: ${state.robotSessionState.name}\n" +
        "Robot enabled: ${if (state.robotEnabled) "YES" else "NO"}\n" +
        "Global mode: ${if (state.globalModeOn) "ON" else "OFF"}\n" +
        "Can continue outside: ${if (state.globalModeOn && state.micContinuationReady) "YES" else "NO"}\n" +
        "Mic continuation: ${if (state.micContinuationReady) "YES" else "NO"}\n" +
        "Overlay: ${if (state.overlayReady) "YES" else "NO"}\n" +
        "Notification: ${if (state.notificationReady) "YES" else "NO"}\n" +
        "Fallback: ${if (state.fallbackReturnReady) "YES" else "NO"}\n" +
        "Speech error: $debugSpeechError\n" +
        "Oido: ${state.voiceHearingStatus}\n" +
        "Voice category: ${state.voiceErrorCategory}\n" +
        "Voice engine: ${state.voiceSpeechEngine}\n" +
        "LLM fallback: ${state.llmFallback.ifBlank { "-" }}\n" +
        "LLM enabled: ${if (state.llmEnabled) "YES" else "NO"}\n" +
        "LLM reason: ${state.llmReason.ifBlank { "-" }}\n" +
        "Listening: ${state.micListening}\n" +
        "Speaking: ${state.ttsSpeaking}\n" +
        "External app: ${state.externalAppName}\n" +
        "TTL remaining: ${state.ttlRemainingMillis}\n" +
        "Timestamp: $debugTimestamp"
    com.ojoclaro.android.ui.components.OjoClaroCard(
        accent = OjoClaroPalette.Outline,
        accentWidth = 1.dp,
        background = OjoClaroPalette.SurfaceVariant
    ) {
        Text(
            text = body,
            color = OjoClaroPalette.TextMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Debug seguro: $debugStateLabel, $debugIntentLabel, $debugSpeechError, " +
                        "listening ${state.micListening}, speaking ${state.ttsSpeaking}"
                }
        )
    }
}

internal fun resolveAssistantStatusKind(
    appState: AppState,
    agentState: AgentState?,
    loading: Boolean,
    micListening: Boolean,
    ttsSpeaking: Boolean,
    robotEnabled: Boolean
): AssistantStatusKind {
    if (!robotEnabled && !ttsSpeaking && !micListening && !loading) {
        return AssistantStatusKind.Inactive
    }
    if (ttsSpeaking) return AssistantStatusKind.Speaking
    if (loading || appState == AppState.PROCESSING) return AssistantStatusKind.Processing
    if (micListening || appState == AppState.LISTENING) return AssistantStatusKind.Listening
    if (appState == AppState.SCANNING) return AssistantStatusKind.ReadingScreen
    if (appState == AppState.WAITING_CONFIRMATION ||
        appState == AppState.WAITING_WHATSAPP_ACTION ||
        appState == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE ||
        appState == AppState.WAITING_CONTACT ||
        appState == AppState.WAITING_MESSAGE ||
        agentState == AgentState.WAITING_CONFIRMATION ||
        agentState == AgentState.WAITING_WHATSAPP_ACTION ||
        agentState == AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE ||
        agentState == AgentState.WAITING_CONTACT ||
        agentState == AgentState.WAITING_MESSAGE ||
        agentState == AgentState.WAITING_PHONE_NUMBER ||
        agentState == AgentState.WAITING_DESTINATION
    ) {
        return AssistantStatusKind.AwaitingConfirmation
    }
    if (appState == AppState.ERROR || agentState == AgentState.ERROR_RECOVERABLE) {
        return AssistantStatusKind.Error
    }
    if (agentState == AgentState.STOPPED_BY_USER) return AssistantStatusKind.ActionCancelled
    return AssistantStatusKind.Inactive
}

internal fun buildHistoryEntries(state: HomeUiState, appState: AppState): List<ActivityEntry> {
    val entries = mutableListOf<ActivityEntry>()
    if (state.spokenText.isNotBlank()) {
        entries += ActivityEntry(
            title = "Respuesta del asistente",
            status = "Completada",
            timestamp = if (state.lastCommandTimestampMillis == 0L) "ahora" else "reciente",
            result = state.spokenText.take(140),
            kind = AssistantStatusKind.ActionCompleted
        )
    }
    if (state.lastRecognizedSpeechText.isNotBlank() && state.lastRecognizedSpeechText != "-") {
        entries += ActivityEntry(
            title = "Frase reconocida",
            status = "Procesada",
            timestamp = "reciente",
            result = "“${state.lastRecognizedSpeechText.take(140)}”",
            kind = AssistantStatusKind.Listening
        )
    }
    if (state.activeTaskTitle.isNotBlank()) {
        entries += ActivityEntry(
            title = state.activeTaskTitle,
            status = state.activeTaskStep.ifBlank { "En curso" },
            timestamp = "ahora",
            result = state.activeTaskSummary.take(140),
            kind = AssistantStatusKind.AwaitingConfirmation
        )
    }
    val pendingLabel = pendingActionLabel(
        appState = appState,
        agentState = state.agentState,
        pendingSummary = state.pendingDebug
    )
    if (pendingLabel.isNotBlank()) {
        entries += ActivityEntry(
            title = "Acción en curso",
            status = "Esperando confirmación",
            timestamp = "ahora",
            result = "Pendiente: $pendingLabel",
            kind = AssistantStatusKind.AwaitingConfirmation
        )
    }
    if (state.lastSpeechError.isNotBlank()) {
        entries += ActivityEntry(
            title = "Aviso de voz",
            status = "Necesita atención",
            timestamp = "reciente",
            result = sanitizeDiagnosticValue(state.lastSpeechError),
            kind = AssistantStatusKind.Error
        )
    }
    return entries.take(5)
}

internal fun recognizedSpeechBlockText(lastRecognizedSpeechText: String): String =
    "Última frase: ${lastRecognizedSpeechText.ifBlank { "-" }}"

internal fun robotStatusBlockText(
    appState: AppState,
    agentState: AgentState?,
    pendingSummary: String,
    loading: Boolean,
    micListening: Boolean,
    ttsSpeaking: Boolean,
    robotSessionState: RobotSessionState? = null
): String {
    val status = robotSessionState?.humanLabel() ?: compactRobotStatus(
        appState = appState,
        agentState = agentState,
        loading = loading,
        micListening = micListening,
        ttsSpeaking = ttsSpeaking
    )
    val pending = pendingActionLabel(appState, agentState, pendingSummary)
    return if (pending.isBlank()) {
        "Estado: $status"
    } else {
        "Estado: $status\nPendiente: $pending"
    }
}

internal fun compactRobotStatus(
    appState: AppState,
    agentState: AgentState?,
    loading: Boolean,
    micListening: Boolean,
    ttsSpeaking: Boolean
): String = when {
    ttsSpeaking -> "Estoy respondiendo"
    loading || appState == AppState.PROCESSING -> "Procesando"
    micListening || appState == AppState.LISTENING -> "Te estoy escuchando"
    agentState == AgentState.WAITING_WHATSAPP_ACTION ||
        agentState == AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE ||
        appState == AppState.WAITING_WHATSAPP_ACTION ||
        appState == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> "Leyendo WhatsApp"
    agentState == AgentState.WAITING_CONFIRMATION ||
        appState == AppState.WAITING_CONFIRMATION -> "Esperando confirmación"
    else -> statusText(appState, agentState)
}

internal fun pendingActionLabel(
    appState: AppState,
    agentState: AgentState?,
    pendingSummary: String
): String {
    val raw = pendingSummary.trim().uppercase()
    return when {
        agentState == AgentState.WAITING_WHATSAPP_ACTION ||
            appState == AppState.WAITING_WHATSAPP_ACTION -> "accion de WhatsApp"
        agentState == AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE ||
            appState == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> "chat o mensaje de WhatsApp"
        agentState == AgentState.WAITING_CONTACT ||
            appState == AppState.WAITING_CONTACT -> "contacto"
        agentState == AgentState.WAITING_MESSAGE ||
            appState == AppState.WAITING_MESSAGE -> "mensaje"
        agentState == AgentState.WAITING_DESTINATION -> "destino"
        appState == AppState.WAITING_CONFIRMATION || raw.contains("CONFIRM") -> "confirmacion"
        raw.contains("WHATSAPP") -> "WhatsApp"
        raw.isNotBlank() && raw != "NINGUNA" && raw != "NONE" -> raw.take(48)
        else -> ""
    }
}

internal fun statusText(appState: AppState, agentState: AgentState? = null): String {
    when (agentState) {
        AgentState.WAITING_WHATSAPP_ACTION,
        AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> return "Leyendo WhatsApp"
        AgentState.WAITING_CONTACT -> return "Esperando contacto"
        AgentState.WAITING_MESSAGE -> return "Esperando mensaje"
        AgentState.WAITING_PHONE_NUMBER -> return "Esperando número"
        AgentState.WAITING_DESTINATION -> return "Esperando destino"
        AgentState.WAITING_LOCATION_ALIAS -> return "Esperando nombre de lugar"
        AgentState.WAITING_TIME -> return "Esperando hora"
        AgentState.WAITING_FREQUENCY -> return "Esperando frecuencia"
        else -> Unit
    }
    return when (appState) {
        AppState.IDLE -> "Estela está lista para ayudarte."
        AppState.LISTENING -> "Estela está escuchando."
        AppState.SCANNING -> "Estela está leyendo la pantalla."
        AppState.PROCESSING -> "Estela está pensando."
        AppState.SPEAKING -> "Estela está respondiendo."
        AppState.WAITING_WHATSAPP_ACTION,
        AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> "Leyendo WhatsApp"
        AppState.WAITING_CONTACT -> "Esperando contacto"
        AppState.WAITING_MESSAGE -> "Esperando mensaje"
        AppState.WAITING_CONFIRMATION -> "Estela está esperando tu confirmación."
        AppState.EXTERNAL_APP_HANDOFF -> "App externa"
        AppState.GLOBAL_ASSISTANT_ACTIVE -> "Estela activa"
        AppState.GLOBAL_ASSISTANT_UNAVAILABLE -> "Modo global no disponible"
        AppState.PERMISSION_REQUIRED -> "Activá un permiso"
        AppState.ERROR -> "Aviso"
    }
}

private fun hasPermission(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun isPackageInstalled(packageManager: PackageManager, packageName: String): Boolean =
    try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

internal const val FIRST_USE_GUIDE_TEXT: String =
    "Podés pedirle a Estela que lea la pantalla, describa el entorno o te guíe paso a paso."

internal enum class ProductDisplayMode {
    DEMO,
    QA
}

internal fun defaultProductDisplayMode(): ProductDisplayMode = ProductDisplayMode.DEMO

internal fun buildHomeDiagnosticText(
    versionName: String,
    isDebug: Boolean,
    assistantBaseUrlConfigured: Boolean,
    microphoneGranted: Boolean,
    cameraGranted: Boolean,
    ttsAvailable: Boolean,
    whatsappStatus: String,
    robotEnabled: Boolean = true,
    accessibilityReady: Boolean = true,
    waitingConfirmation: Boolean = false,
    whatsappActive: Boolean = false,
    pendingSummary: String = "ninguna",
    lastError: String = "",
    voiceHearingStatus: String = "sin resultado",
    voiceErrorCategory: String = "ninguno",
    voiceSpeechEngine: String = "sistema",
    proxyHealth: com.ojoclaro.android.llm.ProxyHealthState = com.ojoclaro.android.llm.ProxyHealthState.Unknown,
    displayMode: ProductDisplayMode = ProductDisplayMode.DEMO
): String {
    val safePending = sanitizeDiagnosticValue(pendingSummary.ifBlank { "ninguna" })
    val safeError = sanitizeDiagnosticValue(lastError.ifBlank { "ninguno" })
    val assistantStatus = "modo seguro"
    val gptMiniLine = com.ojoclaro.android.llm.describeProxyHealth(
        state = proxyHealth,
        assistantBaseUrlConfigured = assistantBaseUrlConfigured
    )
    val micStatus = if (microphoneGranted) "permiso OK" else "falta permiso"
    val safeVoiceHearing = sanitizeDiagnosticValue(voiceHearingStatus.ifBlank { "sin resultado" })
    val safeVoiceErrorCategory = sanitizeDiagnosticValue(voiceErrorCategory.ifBlank { "ninguno" })
    val safeVoiceSpeechEngine = sanitizeDiagnosticValue(voiceSpeechEngine.ifBlank { "sistema" })
    val cameraStatus = if (cameraGranted) "permiso OK" else "falta permiso"
    val ttsStatus = if (ttsAvailable) "disponible" else "no disponible"

    if (displayMode == ProductDisplayMode.DEMO) {
        val suggestion = productUtilitySuggestionText(
            robotEnabled = robotEnabled,
            accessibilityReady = accessibilityReady,
            waitingConfirmation = waitingConfirmation,
            whatsappActive = whatsappActive,
            voiceErrorCategory = voiceErrorCategory
        )
        return "Modo Demo\n" +
            "Versión: $versionName\n" +
            "Micrófono: $micStatus\n" +
            "Oído: $safeVoiceHearing\n" +
            "Cámara: $cameraStatus\n" +
            "Voz: $ttsStatus\n" +
            "WhatsApp: $whatsappStatus\n" +
            "Sugerencia: $suggestion"
    }

    return "Diagnóstico de demo\n" +
        "Versión: $versionName\n" +
        "Modo: ${if (isDebug) "debug" else "release"}\n" +
        "Asistente: $assistantStatus\n" +
        "$gptMiniLine\n" +
        "Micrófono: $micStatus\n" +
        "Oído: $safeVoiceHearing\n" +
        "Último error de voz: $safeVoiceErrorCategory\n" +
        "Motor de voz: $safeVoiceSpeechEngine\n" +
        "Cámara: $cameraStatus\n" +
        "TTS: $ttsStatus\n" +
        "WhatsApp: $whatsappStatus\n" +
        "Última acción pendiente: $safePending\n" +
        "Último error seguro: $safeError\n" +
        "Resumen seguro QA: versión $versionName; modo ${if (isDebug) "debug" else "release"}; " +
        "asistente $assistantStatus; ${gptMiniLine.lowercase()}; " +
        "micrófono $micStatus; oído $safeVoiceHearing; motor $safeVoiceSpeechEngine; " +
        "cámara $cameraStatus; TTS $ttsStatus; " +
        "WhatsApp $whatsappStatus; pendiente $safePending; error $safeError."
}

internal fun productUtilitySuggestionText(
    robotEnabled: Boolean,
    accessibilityReady: Boolean,
    waitingConfirmation: Boolean,
    whatsappActive: Boolean,
    voiceErrorCategory: String
): String {
    val hasVoiceError = voiceErrorCategory.isNotBlank() &&
        !voiceErrorCategory.equals("ninguno", ignoreCase = true) &&
        !voiceErrorCategory.equals("none", ignoreCase = true)
    return when {
        !robotEnabled -> "Podés decir: encender robot, ayuda o resetear."
        !accessibilityReady -> "Activá Estela en Accesibilidad para leer la pantalla."
        waitingConfirmation -> "Podés decir: sí, cancelar, repetir o resetear."
        hasVoiceError -> "Podés decir: repetir, ayuda o resetear."
        whatsappActive -> "Podés decir: qué chats ves, cómo mando una foto o cancelar."
        else -> "Podés decir: qué hay en pantalla, abrir WhatsApp, repetir o resetear."
    }
}

internal fun sanitizeDiagnosticValue(value: String): String {
    val keyPrefix = "sk" + "-"
    return value
        .replace(Regex("$keyPrefix\\S+", RegexOption.IGNORE_CASE), "[secreto]")
        .replace(Regex("(?i)api[_-]?key\\s*[:=]\\s*\\S+"), "api_key=[oculta]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
        .ifBlank { "ninguno" }
}

internal fun canStartListeningAfterSpeech(appState: AppState): Boolean =
    appState != AppState.SCANNING &&
        appState != AppState.PROCESSING &&
        appState != AppState.EXTERNAL_APP_HANDOFF &&
        appState != AppState.GLOBAL_ASSISTANT_ACTIVE

internal fun shouldResumeListeningAfterSpeech(
    robotEnabled: Boolean,
    appVisible: Boolean,
    appState: AppState
): Boolean =
    robotEnabled && appVisible && canStartListeningAfterSpeech(appState)

internal fun shouldAutoStartListeningOnResume(appState: AppState): Boolean =
    appState != AppState.EXTERNAL_APP_HANDOFF &&
        appState != AppState.GLOBAL_ASSISTANT_ACTIVE

internal fun shouldUseExtendedListening(appState: AppState, agentState: AgentState?): Boolean =
    appState == AppState.WAITING_CONFIRMATION ||
        appState == AppState.WAITING_WHATSAPP_ACTION ||
        appState == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE ||
        appState == AppState.WAITING_CONTACT ||
        appState == AppState.WAITING_MESSAGE ||
        shouldUseExtendedListeningForAgentState(agentState)

internal fun shouldUseExtendedListeningForAgentState(agentState: AgentState?): Boolean =
    agentState in setOf(
        AgentState.WAITING_WHATSAPP_ACTION,
        AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE,
        AgentState.WAITING_CONTACT,
        AgentState.WAITING_MESSAGE,
        AgentState.WAITING_CONFIRMATION
    )

internal fun shouldPauseVoiceLoopForExternalEvent(event: ExternalActionEvent): Boolean =
    event is ExternalActionEvent.ExternalAppHandoff

internal fun handoffSpeechDelayMillis(text: String): Long =
    (900L + text.length * 45L).coerceIn(1_200L, 4_500L)

internal const val TTS_TO_MIC_DELAY_MILLIS = 250L
