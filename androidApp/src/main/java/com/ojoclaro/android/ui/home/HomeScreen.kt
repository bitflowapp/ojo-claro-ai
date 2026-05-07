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
import com.ojoclaro.android.phone.PhoneActionExecutor
import com.ojoclaro.android.speech.SpeechController
import com.ojoclaro.android.ui.camera.TextScanScreen
import com.ojoclaro.android.voice.AndroidSpeechInputEngine
import com.ojoclaro.android.voice.VoiceCommandController
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import com.ojoclaro.android.voice.VoiceRetryHandle
import com.ojoclaro.android.voice.VoiceRetryScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    listeningTriggers: StateFlow<Long> = MutableStateFlow(0L),
    stopSpeechTriggers: StateFlow<Long> = MutableStateFlow(0L)
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: HomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(context.applicationContext as Application) as T
        }
    )

    val state by viewModel.state.collectAsState()
    val appState by viewModel.appState.collectAsState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var microphoneGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO))
    }
    var requestedMicrophoneOnLaunch by remember { mutableStateOf(false) }
    val currentAppState = rememberUpdatedState(appState)
    val currentMicrophoneGranted = rememberUpdatedState(microphoneGranted)
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
            voiceController.startListening()
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
                Lifecycle.Event.ON_PAUSE -> voiceController.stopListening()
                Lifecycle.Event.ON_RESUME -> {
                    if (microphoneGranted && shouldAutoStartListeningOnResume(currentAppState.value)) {
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
        } else if (hasMicrophone) {
            voiceController.startListening()
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

    LaunchedEffect(appState, microphoneGranted) {
        if (!microphoneGranted) return@LaunchedEffect
        voiceController.setExpectingResponse(
            shouldUseExtendedListening(appState = appState, agentState = state.agentState)
        )
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
        if (state.voiceListenRequestId == 0L || !microphoneGranted) return@LaunchedEffect
        if (!state.ttsSpeaking && canStartListeningAfterSpeech(appState)) {
            voiceController.startListening()
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
                    // Reserva espacio para que el botón fijo "Callar" nunca tape contenido.
                    bottom = 136.dp
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ojo Claro AI",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { heading() }
            )

            Text(
                text = "Ojo Claro escucha automáticamente mientras esta pantalla está abierta. El botón Escuchar es solo respaldo.",
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription =
                            "Ojo Claro escucha automáticamente mientras esta pantalla está abierta. El botón Escuchar es solo respaldo."
                    }
            )

            val statusLabel = statusText(appState, state.agentState)
            Text(
                text = statusLabel,
                color = Color(0xFFFFF176),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = "Estado: $statusLabel"
                }
            )

            Text(
                text = state.spokenText,
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    .padding(18.dp)
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

            if (microphoneGranted && !state.listening && appState == AppState.IDLE) {
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

            // Panel de debug visible para QA física. Solo en builds debug. No es
            // promesa comercial: permite ver qué se reconoció, qué estado y qué intent.
            if (BuildConfig.DEBUG) {
                val debugLast = state.lastCommand.ifBlank { "—" }
                val debugNormalized = state.lastNormalizedCommand.ifBlank { "—" }
                val debugStateLabel = state.agentState?.name ?: appState.name
                val debugDecision = state.lastDecision.ifBlank { "none" }
                val debugPending = state.pendingDebug.ifBlank { "none" }
                val debugIntentLabel = state.lastAgentIntent?.name ?: "—"
                val debugSpeechError = state.lastSpeechError.ifBlank { "—" }
                val debugTimestamp = if (state.lastCommandTimestampMillis == 0L) {
                    "—"
                } else {
                    state.lastCommandTimestampMillis.toString()
                }
                Text(
                    text = "Original: $debugLast\n" +
                        "Normalizado: $debugNormalized\n" +
                        "Confidence: ${"%.2f".format(state.lastConfidence)}\n" +
                        "Source: ${state.decisionSource.ifBlank { "local" }}\n" +
                        "Estado: $debugStateLabel\n" +
                        "Intent: $debugIntentLabel\n" +
                        "Decision: $debugDecision\n" +
                        "Pending: $debugPending\n" +
                        "Contact: ${state.contactDebug.ifBlank { "—" }}\n" +
                        "Message: ${state.messageDebug.ifBlank { "—" }}\n" +
                        "Memory used: ${state.memoryUsedDebug.ifBlank { "—" }}\n" +
                        "Suggestion: ${state.suggestionDebug.ifBlank { "—" }}\n" +
                        "Global mode: ${if (state.globalModeOn) "ON" else "OFF"}\n" +
                        "Can continue outside: ${if (state.globalModeOn && state.micContinuationReady) "YES" else "NO"}\n" +
                        "Mic continuation: ${if (state.micContinuationReady) "YES" else "NO"}\n" +
                        "Overlay: ${if (state.overlayReady) "YES" else "NO"}\n" +
                        "Notification: ${if (state.notificationReady) "YES" else "NO"}\n" +
                        "Fallback: ${if (state.fallbackReturnReady) "YES" else "NO"}\n" +
                        "Speech error: $debugSpeechError\n" +
                        "LLM fallback: ${state.llmFallback.ifBlank { "—" }}\n" +
                        "LLM enabled: ${if (state.llmEnabled) "YES" else "NO"}\n" +
                        "LLM reason: ${state.llmReason.ifBlank { "—" }}\n" +
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
                                "Debug: $debugLast, $debugNormalized, $debugStateLabel, " +
                                    "$debugIntentLabel, $debugSpeechError, " +
                                    "listening ${state.micListening}, speaking ${state.ttsSpeaking}"
                        }
                )
            }
        }

        // Botón crítico fijo. No debe depender del largo de la respuesta.
        SecondaryActionButton(
            text = "Callar",
            contentDescription = "Callar la voz.",
            onClick = {
                voiceController.stopForCommandAndResume()
                speechController.stop()
                viewModel.onStopSpeechRequested()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 16.dp
                )
        )
    }
}

@Composable
private fun SecondaryActionButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            .heightIn(min = 72.dp)
            .semantics {
                this.contentDescription = contentDescription
            }
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
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

internal fun canStartListeningAfterSpeech(appState: AppState): Boolean =
    appState != AppState.SCANNING &&
        appState != AppState.PROCESSING &&
        appState != AppState.EXTERNAL_APP_HANDOFF &&
        appState != AppState.GLOBAL_ASSISTANT_ACTIVE

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
