package com.ojoclaro.android.ui.home

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
            // Paquete 4C: si el runtime graph está instalado (vía MainActivity),
            // inyectamos su dispatchController. Si no, queda null y el VM
            // sigue el flujo legacy. El controller mismo respeta los flags.
            agentBridgeDispatch = selectAgentBridgeDispatchControllerForHome(),
            // Paquete 5C: mismo origen, expone el coordinador semántico de voz
            // process-scope. Sobrevive recomposiciones por venir del graph.
            agentBridgeVoiceCoordinator = selectAgentBridgeVoiceCoordinatorForHome()
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
                // El callback del TTS llega en su propio thread. SpeechRecognizer.cancel()
                // tiene que ejecutarse desde el main thread; si no, queda un W Binder y el
                // mic puede quedar abierto mientras hablamos (TTS se escucha a sí mismo).
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

    // Saludo inicial: se ejecuta concurrentemente al collector; yield() le da tiempo
    // al collector para suscribirse antes de que greetIfFirstTime() emita.
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

    // Trigger de "modo escucha" desde Quick Settings tile, botón flotante de
    // Accesibilidad o deep link. Saludo se dice una sola vez por proceso (lo
    // garantiza el ViewModel). Acá solo arrancamos el voice loop o pedimos mic.
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = 24.dp,
                    top = 24.dp,
                    end = 24.dp,
                    // Sin overlay fijo: los botones criticos viven al final del scroll.
                    // Padding chico para Samsung; navigationBarsPadding lo agrega aparte.
                    bottom = 32.dp
                )
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ojo Claro AI",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { heading() }
            )

            // Paquete 4C: banner accesible que aparece solo cuando el bridge
            // tiene una acción pendiente de confirmación. Si no hay pending,
            // el composable se replega y no ocupa espacio.
            PendingConfirmationBanner(
                state = PendingConfirmationViewState.from(state),
                onConfirm = { viewModel.submitVoiceText(CONFIRM_BUTTON_VOICE_PHRASE) },
                onCancel = { viewModel.submitVoiceText(CANCEL_BUTTON_VOICE_PHRASE) }
            )

            Text(
                text = "Encende el robot para que escuche mientras esta pantalla esta abierta. Pausalo cuando quieras.",
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription =
                            "Encende el robot para que escuche mientras esta pantalla esta abierta. Pausalo cuando quieras."
                    }
            )

            Text(
                text = FIRST_USE_GUIDE_TEXT,
                color = Color(0xFFE6F4EA),
                fontSize = 17.sp,
                lineHeight = 23.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE6F4EA), RoundedCornerShape(8.dp))
                    .padding(14.dp)
                    .semantics {
                        contentDescription = "Primer uso. $FIRST_USE_GUIDE_TEXT"
                    }
            )

            val statusLabel = statusText(appState, state.agentState)
            Text(
                text = robotStatusBlockText(
                    robotSessionState = state.robotSessionState,
                    appState = appState,
                    agentState = state.agentState,
                    pendingSummary = state.pendingDebug,
                    loading = state.loading,
                    micListening = state.micListening,
                    ttsSpeaking = state.ttsSpeaking
                ),
                color = Color(0xFFFFF176),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFFF176), RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .semantics {
                        contentDescription = "Estado: $statusLabel"
                    }
            )

            Text(
                text = recognizedSpeechBlockText(state.lastRecognizedSpeechText),
                color = Color(0xFFE6F4EA),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE6F4EA), RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .semantics {
                        contentDescription = "Ultima frase reconocida."
                    }
            )

            Text(
                text = state.spokenText,
                color = Color.White,
                fontSize = 21.sp,
                lineHeight = 28.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    .padding(14.dp)
                    .semantics {
                        contentDescription = "Respuesta: ${state.spokenText}"
                    }
            )

            if (state.loading || appState == AppState.PROCESSING) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.semantics {
                        contentDescription = "Procesando, esperá un momento."
                    }
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!microphoneGranted) {
                SecondaryActionButton(
                    text = "Activar voz",
                    contentDescription = "Activar control por voz con el micrófono.",
                    onClick = {
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }

            SecondaryActionButton(
                text = if (state.robotEnabled) "Pausar robot" else "Encender robot",
                contentDescription = if (state.robotEnabled) {
                    "Pausar el robot y cerrar el microfono."
                } else {
                    "Encender el robot mientras Ojo Claro esta visible."
                },
                onClick = {
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
            )

            if (microphoneGranted && state.robotEnabled && !state.listening && appState == AppState.IDLE) {
                SecondaryActionButton(
                    text = "Escuchar",
                    contentDescription = "Escuchar un comando de voz.",
                    onClick = {
                        voiceController.startListening()
                    }
                )
            }

            // DESCRIBIR oculto a propósito hasta que exista descripción visual real con IA avanzada.
            // El comando por voz "describir que tengo enfrente" sigue funcionando y devuelve la
            // respuesta honesta de LocalRuleBasedAiProvider.describeSceneFallback().
            // Ver docs/DESCRIBIR_BUTTON_DEMO_FIX.md.

            SecondaryActionButton(
                text = "Leer texto",
                contentDescription = "Leer texto con la cámara.",
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        viewModel.startScanning()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            )

            SecondaryActionButton(
                text = "¿Qué puedo decir?",
                contentDescription = "Escuchar ejemplos de comandos disponibles.",
                onClick = { viewModel.requestHelp() }
            )

            if (BuildConfig.DEBUG) {
                SecondaryActionButton(
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

            val cameraGranted = hasPermission(context, Manifest.permission.CAMERA)
            val whatsappStatus = if (isPackageInstalled(context.packageManager, WhatsAppIntentHelper.WHATSAPP_PACKAGE) ||
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
            Text(
                text = diagnosticText,
                color = Color(0xFFE6F4EA),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE6F4EA), RoundedCornerShape(8.dp))
                    .padding(14.dp)
                    .semantics {
                        contentDescription = "Diagnóstico de demo. $diagnosticText"
                    }
            )

            // Panel de debug visible para QA física. Solo en builds debug. No es
            // promesa comercial: permite ver qué se reconoció, qué estado y qué intent.
            if (BuildConfig.DEBUG && productDisplayMode == ProductDisplayMode.QA) {
                val debugStateLabel = state.agentState?.name ?: appState.name
                val debugDecision = state.lastDecision.ifBlank { "none" }
                val debugPending = state.pendingDebug.ifBlank { "none" }
                val debugIntentLabel = state.lastAgentIntent?.name ?: "-"
                val debugSpeechError = state.lastSpeechError.ifBlank { "-" }
                val debugTimestamp = if (state.lastCommandTimestampMillis == 0L) {
                    "-"
                } else {
                    state.lastCommandTimestampMillis.toString()
                }
                Text(
                    text = "Debug seguro QA\n" +
                        "Confidence: ${"%.2f".format(state.lastConfidence)}\n" +
                        "Source: ${state.decisionSource.ifBlank { "local" }}\n" +
                        "Estado: $debugStateLabel\n" +
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
                        "Timestamp: $debugTimestamp",
                    color = Color(0xFFB0B0B0),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Debug seguro: $debugStateLabel, $debugIntentLabel, $debugSpeechError, " +
                                    "listening ${state.micListening}, speaking ${state.ttsSpeaking}"
                        }
                )
            }

            // Zona criticos al final del scroll: separador visible + botones grandes.
            // Antes vivian en un overlay fijo BottomCenter que se superponia con el
            // bloque de respuesta en pantallas chicas (Samsung QA). Al estar dentro
            // del scroll no hay forma de que tapen contenido y se siguen alcanzando
            // facil rolando hasta abajo.
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Acciones rapidas",
                color = Color(0xFFB0B0B0),
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Acciones rapidas." }
            )
            SecondaryActionButton(
                text = "Resetear flujo",
                contentDescription = "Resetear el flujo actual sin borrar preferencias.",
                onClick = {
                    voiceController.stopListening()
                    speechController.stop()
                    viewModel.resetFlow()
                },
                compact = true
            )
            SecondaryActionButton(
                text = "Callar",
                contentDescription = "Callar la voz.",
                onClick = {
                    voiceController.stopForCommandAndResume()
                    speechController.stop()
                    viewModel.onStopSpeechRequested()
                },
                compact = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SecondaryActionButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White
        ),
        border = BorderStroke(2.dp, Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 54.dp else 64.dp)
            .semantics {
                this.contentDescription = contentDescription
            }
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (compact) 20.sp else 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
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
        appState == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> "Esperando WhatsApp"
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
        appState == AppState.WAITING_CONFIRMATION || raw.contains("CONFIRM") -> "confirmacion"
        raw.contains("WHATSAPP") -> "WhatsApp"
        raw.isNotBlank() && raw != "NINGUNA" && raw != "NONE" -> raw.take(48)
        else -> ""
    }
}

internal fun statusText(appState: AppState, agentState: AgentState? = null): String {
    // Sub-estados conversacionales primero: si el agente está esperando algo
    // específico, lo decimos por nombre. WAITING_CONFIRMATION genérico solo
    // aparece cuando hay una confirmación real pendiente.
    when (agentState) {
        AgentState.WAITING_WHATSAPP_ACTION,
        AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> return "Esperando acción de WhatsApp"
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
        AppState.IDLE -> "Listo"
        AppState.LISTENING -> "Escuchando"
        AppState.SCANNING -> "Leyendo texto"
        AppState.PROCESSING -> "Procesando"
        AppState.SPEAKING -> "Hablando"
        AppState.WAITING_WHATSAPP_ACTION,
        AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> "Esperando acción de WhatsApp"
        AppState.WAITING_CONTACT -> "Esperando contacto"
        AppState.WAITING_MESSAGE -> "Esperando mensaje"
        AppState.WAITING_CONFIRMATION -> "Esperando confirmación"
        AppState.EXTERNAL_APP_HANDOFF -> "App externa"
        AppState.GLOBAL_ASSISTANT_ACTIVE -> "Ojo Claro activo"
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
    "Puedo leer pantalla, abrir WhatsApp, guiarte y repetir. Decime que necesitas."

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
        !accessibilityReady -> "Activá Ojo Claro en Accesibilidad para leer la pantalla."
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
