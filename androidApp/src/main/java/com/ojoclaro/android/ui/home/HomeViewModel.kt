package com.ojoclaro.android.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ojoclaro.android.BuildConfig
import com.ojoclaro.android.debugSubmitTextDecision
import com.ojoclaro.android.accessibility.AccessibilityScreenReader
import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService
import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentOutcome
import com.ojoclaro.android.agent.AgentSessionMemory
import com.ojoclaro.android.agent.AgentSessionSnapshot
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.agent.toAppState
import com.ojoclaro.android.agent.toAgentState
import com.ojoclaro.android.agent.core.emergency.EmergencyPolicy
import com.ojoclaro.android.agent.runtime.conversation.ConversationalRepair
import com.ojoclaro.android.agent.runtime.conversation.ConversationalRepairRequest
import com.ojoclaro.android.agent.runtime.conversation.RepairSuggestedIntent
import com.ojoclaro.android.agent.runtime.conversation.RobotActiveHandler
import com.ojoclaro.android.agent.runtime.conversation.RobotExternalApp
import com.ojoclaro.android.agent.runtime.conversation.RobotFailureReason
import com.ojoclaro.android.agent.runtime.conversation.RobotPendingState
import com.ojoclaro.android.agent.runtime.conversation.RobotRecognizedKind
import com.ojoclaro.android.agent.runtime.conversation.RobotShortTermContext
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenQueryPhrases
import com.ojoclaro.android.agent.runtime.screen.AndroidAccessibilityScreenContextProvider
import com.ojoclaro.android.agent.runtime.screen.RobotStatusDiagnosticResult
import com.ojoclaro.android.agent.runtime.screen.RobotStatusDiagnosticUseCase
import com.ojoclaro.android.agent.runtime.screen.RobotStatusDiagnosticPhrases
import com.ojoclaro.android.agent.runtime.screen.ScreenUnderstandingResult
import com.ojoclaro.android.agent.runtime.screen.ScreenUnderstandingUseCase
import com.ojoclaro.android.agent.runtime.routine.HumanResponseStyleProvider
import com.ojoclaro.android.agent.runtime.routine.HumanRoutineMemoryResult
import com.ojoclaro.android.agent.runtime.routine.HumanRoutineUseCase
import com.ojoclaro.android.agent.runtime.routine.RoutinePreferenceApplier
import com.ojoclaro.android.agent.runtime.routine.RoutineResponseKind
import com.ojoclaro.android.agent.runtime.routine.StoreBackedHumanResponseStyleProvider
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppChatListResponse
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppChatListPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppGuidedResponse
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppGuidedPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppGuidedWorkflowUseCase
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVisibleChatsReader
import com.ojoclaro.android.agent.situation.SituationBrain
import com.ojoclaro.android.agent.situation.SituationBrainFeatureFlag
import com.ojoclaro.android.agent.situation.PendingAction
import com.ojoclaro.android.agent.situation.SituationConfirmedAction
import com.ojoclaro.android.agent.situation.SituationConfirmedActionAdapter
import com.ojoclaro.android.agent.situation.SituationMessageSafety
import com.ojoclaro.android.agent.situation.commandForExecution
import com.ojoclaro.android.agent.situation.SituationContextFactory
import com.ojoclaro.android.agent.situation.SituationDecisionApplier
import com.ojoclaro.android.agent.situation.SituationIntent
import com.ojoclaro.android.agent.situation.SituationRuntimeMemory
import com.ojoclaro.android.agent.situation.SituationUiEffect
import com.ojoclaro.android.agent.situation.situationIntentFromPendingAction
import com.ojoclaro.android.capabilities.Capability
import com.ojoclaro.android.capabilities.CapabilityRegistry
import com.ojoclaro.android.consent.PendingSensitiveAction
import com.ojoclaro.android.domain.AssistantOrchestrator
import com.ojoclaro.android.domain.PersonalAgentDecision
import com.ojoclaro.android.domain.PersonalAgentDecisionEngine
import com.ojoclaro.android.domain.PersonalAgentDecisionInput
import com.ojoclaro.android.domain.OrchestratorOutcome
import com.ojoclaro.android.external.CommandConfidence
import com.ojoclaro.android.external.CommandResult
import com.ojoclaro.android.external.CommandRouter
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.external.ExternalCommand
import com.ojoclaro.android.external.ExternalCommandType
import com.ojoclaro.android.external.PendingConfirmation
import com.ojoclaro.android.global.GlobalAssistantCapability
import com.ojoclaro.android.global.GlobalAssistantCapabilityGate
import com.ojoclaro.android.handoff.ExternalHandoffCallbackTracker
import com.ojoclaro.android.handoff.ExternalHandoffCallbacks
import com.ojoclaro.android.help.VoiceHelpCenter
import com.ojoclaro.android.help.VoiceHelpContext
import com.ojoclaro.android.maps.LocationCommandPhrases
import com.ojoclaro.android.maps.LocationProvider
import com.ojoclaro.android.memory.LocalMemoryStore
import com.ojoclaro.android.memory.MemoryPolicy
import com.ojoclaro.android.memory.PersonalMemorySnapshot
import com.ojoclaro.android.memory.SharedPreferencesPersonalMemoryStore
import com.ojoclaro.android.model.AppState
import com.ojoclaro.android.model.RobotSessionState
import com.ojoclaro.android.patterns.FrequentPatternTracker
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopLogResult
import com.ojoclaro.android.performance.RobotLoopLogStage
import com.ojoclaro.android.performance.RobotLoopMetric
import com.ojoclaro.android.performance.RobotLoopSafeLogEvent
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.risk.RiskDetector
import com.ojoclaro.android.risk.RiskWarning
import com.ojoclaro.android.llm.DisabledLlmAgentInterpreter
import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.llm.LlmAgentInterpreter
import com.ojoclaro.android.llm.OpenAiProxyAgentInterpreter
import com.ojoclaro.android.llm.SafeAiFallbackCopy
import com.ojoclaro.android.voice.PendingVoiceCommandCorrection
import com.ojoclaro.android.voice.VoiceCommandConfirmationResponse
import com.ojoclaro.android.voice.VoiceCommandController
import com.ojoclaro.android.voice.VoiceCommandCorrection
import com.ojoclaro.android.voice.VoiceCommandCorrectionResult
import com.ojoclaro.android.voice.VoiceCommandCorrectionType
import com.ojoclaro.android.voice.VoiceCommandTargetIntent
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import com.ojoclaro.android.voice.VoiceListeningDiagnostic
import com.ojoclaro.android.voice.VoiceListeningState
import com.ojoclaro.android.voice.VoicePhraseNormalizer
import com.ojoclaro.shared.commands.CommandParser
import com.ojoclaro.shared.model.AccessibilityMode
import com.ojoclaro.shared.model.AppCommand
import com.ojoclaro.shared.model.AppCommandType
import com.ojoclaro.shared.model.AssistRequest
import com.ojoclaro.shared.model.AssistResponse
import com.ojoclaro.shared.model.ConfidenceLevel
import com.ojoclaro.shared.model.ResponseCategory
import com.ojoclaro.shared.network.AssistantApi
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val SHORT_READY_TEXT: String =
    "Puedo leer pantalla, abrir WhatsApp, guiarte y repetir. Decime que necesitas."

internal const val RESET_FLOW_TEXT: String =
    "Flujo reseteado. Te escucho."

internal const val VOICE_CORRECTION_FALLBACK_TEXT: String =
    ConversationalRepair.NOISE

internal const val SENSITIVE_RECOGNIZED_TEXT: String =
    "Escuche una frase sensible. No la muestro."

data class HomeUiState(
    val robotEnabled: Boolean = false,
    val robotSessionState: RobotSessionState = RobotSessionState.OFF,
    val listening: Boolean = false,
    val loading: Boolean = false,
    val lastCommand: String = "",
    val lastNormalizedCommand: String = "",
    val lastRecognizedSpeechText: String = "-",
    val lastCommandTimestampMillis: Long = 0L,
    val lastSpeechError: String = "",
    val lastDecision: String = "",
    val decisionSource: String = "",
    val lastConfidence: Float = 0f,
    val pendingDebug: String = "",
    val contactDebug: String = "",
    val messageDebug: String = "",
    val memoryUsedDebug: String = "",
    val suggestionDebug: String = "",
    val globalModeOn: Boolean = false,
    val micContinuationReady: Boolean = false,
    val overlayReady: Boolean = false,
    val notificationReady: Boolean = false,
    val fallbackReturnReady: Boolean = false,
    val externalAppName: String = "None",
    val ttlRemainingMillis: Long = 0L,
    val llmFallback: String = "",
    val llmEnabled: Boolean = false,
    val llmReason: String = "disabled",
    val ttsSpeaking: Boolean = false,
    val micListening: Boolean = false,
    val voiceHearingStatus: String = "sin resultado",
    val voiceErrorCategory: String = "ninguno",
    val voiceSpeechEngine: String = "sistema",
    val voiceListenRequestId: Long = 0L,
    val spokenText: String = SHORT_READY_TEXT,
    val voicePartialText: String = "",
    val microphonePermissionGranted: Boolean = false,
    val externalAppHandoff: ExternalActionEvent.ExternalAppHandoff? = null,
    /**
     * Sub-estado conversacional activo cuando hay pending del agente. La UI lo
     * usa para distinguir, por ejemplo, "Esperando acción de WhatsApp" vs el
     * genérico "Esperando confirmación". Null cuando no hay flujo guiado vivo.
     */
    val agentState: AgentState? = null,
    /**
     * Último intent parseado del agente. Solo se expone para el panel de debug
     * visible en builds debug — no es parte del contrato semántico del estado.
     */
    val lastAgentIntent: AgentIntent? = null,
    val error: String? = null,
    /**
     * Resultado del último probe a `/health` del proxy local. Se usa SOLO en
     * el bloque de diagnóstico para mostrar "GPT mini: disponible / sin conexión".
     * Nunca se expone la URL ni la API key.
     */
    val proxyHealth: com.ojoclaro.android.llm.ProxyHealthState =
        com.ojoclaro.android.llm.ProxyHealthState.Unknown,
    /**
     * Paquete 4B — campos mínimos para el bridge tipado.
     *
     * Solo se popúlan cuando el flag `typedConfirmationEnabled` está ON y el
     * [com.ojoclaro.android.agent.core.runtime.AgentBridgeDispatchController]
     * devuelve un outcome `Handled`. En modo legacy se mantienen en sus
     * defaults (null / false / null), así que la UI legacy no cambia.
     */
    val pendingConfirmationText: String? = null,
    val hasPendingConfirmation: Boolean = false,
    val lastAgentBridgeMessage: String? = null
)

data class SpeechEvent(
    val text: String,
    val force: Boolean = false
)

/**
 * ViewModel del Home. Su trabajo es exponer estado y delegar:
 *  - Comandos externos van al AssistantOrchestrator.
 *  - DESCRIBIR / EMERGENCIA / texto libre conocido por el shared CommandParser
 *    siguen al backend con fallback local.
 *  - El saludo inicial y el centro de ayuda se resuelven en local.
 */
class HomeViewModel(
    application: Application,
    capabilityRegistry: CapabilityRegistry = CapabilityRegistry(application),
    private val globalAssistantCapabilityProvider: () -> GlobalAssistantCapability = {
        GlobalAssistantCapabilityGate(application).evaluate()
    },
    private val orchestrator: AssistantOrchestrator = AssistantOrchestrator(
        capabilityRegistry = capabilityRegistry,
        memoryStore = LocalMemoryStore(application),
        locationProvider = LocationProvider(application),
        patternTracker = FrequentPatternTracker(
            application.getSharedPreferences(FrequentPatternTracker.PREFS_NAME, Context.MODE_PRIVATE)
        ),
        globalAssistantCapabilityProvider = globalAssistantCapabilityProvider
    ),
    private val personalMemoryStore: SharedPreferencesPersonalMemoryStore =
        SharedPreferencesPersonalMemoryStore(application),
    private val personalAgentDecisionEngine: PersonalAgentDecisionEngine =
        PersonalAgentDecisionEngine(
            llmAgentInterpreter = createPersonalAgentInterpreter()
        ),
    private val parser: CommandParser = CommandParser(),
    private val riskDetector: RiskDetector = RiskDetector(),
    private val api: AssistantApi = AssistantApi(BuildConfig.ASSISTANT_BASE_URL),
    /**
     * Paquete 4B — controlador opcional del Agent Runtime Bridge.
     *
     * Si es null (default de producción hoy), `submitVoiceText` sigue su
     * flujo legacy intacto. Si se inyecta (vía VMFactory o
     * OjoClaroRuntimeGraph), el VM intercepta cada comando antes del
     * pipeline legacy:
     *  - si el controller devuelve FallbackToLegacy → el legacy continúa,
     *  - si devuelve Handled → el VM aplica el outcome y NO ejecuta legacy.
     *
     * El controller mismo respeta `typedConfirmationEnabled` internamente:
     * con el flag off, siempre devuelve FallbackToLegacy.
     */
    private val agentBridgeDispatch: com.ojoclaro.android.agent.core.runtime.AgentBridgeDispatchController? = null
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _speechEvents = MutableSharedFlow<SpeechEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val speechEvents: SharedFlow<SpeechEvent> = _speechEvents.asSharedFlow()

    private val _externalActionEvents = MutableSharedFlow<ExternalActionEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val externalActionEvents: SharedFlow<ExternalActionEvent> = _externalActionEvents.asSharedFlow()

    private var activeRequestId = 0L
    private var mutedThroughRequestId = 0L
    private var activeVoiceCommandStartNanos = 0L
    private var pendingVoiceCorrection: PendingVoiceCommandCorrection? = null
    private var shortTermContext: RobotShortTermContext = RobotShortTermContext()
    private var pendingExternalConfirmation: PendingConfirmation? = null
    private var pendingConsentAction: PendingSensitiveAction? = null
    private var activeExternalActionRequestId: Long? = null
    private var consecutiveWhatsAppWaitingErrors = 0
    private var greeted = false
    private val agentIntentParser = LocalIntentParser()
    private val agentConversationManager = AgentConversationManager()
    private val emergencyPolicy = EmergencyPolicy()
    private val sessionMemory = AgentSessionMemory()
    // Memoria runtime del Situation Brain (Fase 4). Efímera, en RAM. Solo se usa
    // dentro de tryHandleWithSituationBrain, que a su vez solo corre con el flag
    // SituationBrainFeatureFlag.ENABLED encendido.
    private val situationRuntimeMemory = SituationRuntimeMemory()
    private val handoffCallbackTracker = ExternalHandoffCallbackTracker()
    // Provider y ready-lambda compartidos entre Screen Understanding y WhatsApp
    // Guided Workflow. Ambos consumen el mismo snapshot de Accessibility.
    private val screenContextProvider: ScreenContextProvider =
        AndroidAccessibilityScreenContextProvider()
    private val isAccessibilityServiceReady: () -> Boolean = {
        AccessibilityScreenReader.isServiceEnabled(application) &&
            OjoClaroAccessibilityService.isConnected()
    }
    private val screenUnderstandingUseCase: ScreenUnderstandingUseCase = ScreenUnderstandingUseCase(
        provider = screenContextProvider,
        isAccessibilityReady = isAccessibilityServiceReady
    )
    private val robotStatusDiagnosticUseCase: RobotStatusDiagnosticUseCase = RobotStatusDiagnosticUseCase(
        provider = screenContextProvider,
        isAccessibilityReady = isAccessibilityServiceReady
    )
    private val whatsAppGuidedWorkflowUseCase: WhatsAppGuidedWorkflowUseCase = WhatsAppGuidedWorkflowUseCase(
        provider = screenContextProvider,
        isAccessibilityReady = isAccessibilityServiceReady
    )
    private val whatsAppVisibleChatsReader: WhatsAppVisibleChatsReader = WhatsAppVisibleChatsReader(
        provider = screenContextProvider,
        isAccessibilityReady = isAccessibilityServiceReady
    )
    private val humanRoutineUseCase: HumanRoutineUseCase = HumanRoutineUseCase()
    private val humanResponseStyleProvider: HumanResponseStyleProvider =
        StoreBackedHumanResponseStyleProvider(humanRoutineUseCase.memoryStore)

    fun greetIfFirstTime(hasMicrophonePermission: Boolean) {
        if (greeted) return
        greeted = true
        val message = if (hasMicrophonePermission) {
            SHORT_READY_TEXT
        } else {
            VoiceCommandController.MICROPHONE_PERMISSION_MESSAGE
        }
        _state.update {
            it.copy(
                spokenText = message,
                microphonePermissionGranted = hasMicrophonePermission
            )
        }
        sessionMemory.rememberSpokenResponse(message)
        emitSpeechEvent(message, force = true)
        probeProxyHealthOnce()
    }

    /**
     * Lanza UN solo healthcheck a `/health` del proxy local. Solo afecta el
     * panel de diagnóstico ("GPT mini: disponible / sin conexión"). Si la URL
     * no esta configurada, marca Disconnected y termina sin tocar la red.
     */
    private fun probeProxyHealthOnce() {
        val baseUrl = BuildConfig.ASSISTANT_BASE_URL
        if (baseUrl.isBlank()) {
            _state.update { it.copy(proxyHealth = com.ojoclaro.android.llm.ProxyHealthState.Disconnected) }
            return
        }
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                com.ojoclaro.android.llm.ProxyHealthProbe(baseUrl = baseUrl).check()
            }
            _state.update { it.copy(proxyHealth = state) }
        }
    }

    /**
     * Lo invoca el HomeScreen cuando llega un intent de "modo escucha"
     * (Quick Settings tile, botón flotante de Accesibilidad, deep link).
     *
     * Reglas:
     *  - El saludo "Ojo Claro listo. Decime qué necesitás." se dice UNA vez por proceso.
     *    Si la app ya saludó, no lo repetimos en loop.
     *  - Si falta micrófono, lo decimos siempre con un mensaje humano y dejamos
     *    el estado en PERMISSION_REQUIRED para que la UI pida el permiso.
     */
    fun onListeningIntentReceived(hasMicrophonePermission: Boolean) {
        clearExternalHandoff()
        enableRobotSession(hasMicrophonePermission)
        if (!greeted) {
            greetIfFirstTime(hasMicrophonePermission)
            return
        }
        if (!hasMicrophonePermission) {
            publishLocalMessage(
                text = VoiceCommandController.MICROPHONE_PERMISSION_MESSAGE,
                force = true,
                appState = AppState.PERMISSION_REQUIRED
            )
        }
        // Si ya saludó y hay mic, el HomeScreen llama a voiceController.startListening()
        // por su lado. No repetimos saludo ni hablamos encima.
    }

    fun enableRobotSession(hasMicrophonePermission: Boolean) {
        _state.update {
            it.copy(
                robotEnabled = true,
                robotSessionState = if (hasMicrophonePermission) {
                    RobotSessionState.READY
                } else {
                    RobotSessionState.ERROR_RECOVERABLE
                },
                microphonePermissionGranted = hasMicrophonePermission
            )
        }
        if (!hasMicrophonePermission) {
            _appState.value = AppState.PERMISSION_REQUIRED
        } else if (_appState.value == AppState.IDLE || _appState.value == AppState.ERROR) {
            _appState.value = AppState.IDLE
        }
        logVoiceLoopState(
            action = "enable_robot",
            state = if (hasMicrophonePermission) RobotSessionState.READY else RobotSessionState.ERROR_RECOVERABLE,
            micActive = false
        )
    }

    fun pauseRobotSession() {
        _state.update {
            it.copy(
                robotEnabled = false,
                robotSessionState = RobotSessionState.OFF,
                listening = false,
                micListening = false
            )
        }
        logVoiceLoopState(action = "pause_robot", state = RobotSessionState.OFF, micActive = false)
    }

    fun submitDebugInjectedText(text: String) {
        val decision = debugSubmitTextDecision(text)
        logVoiceCommandEvent(
            handler = "debug_submit_text",
            result = if (decision.accepted) RobotLoopLogResult.OK else RobotLoopLogResult.DROPPED,
            understood = null,
            consumed = true,
            reasonCode = decision.rejectReason?.logCode ?: "received"
        )
        if (!decision.accepted) return
        submitVoiceText(decision.text)
    }

    fun requestHelp() {
        val message = VoiceHelpCenter.contextualSpokenHelp(currentVoiceHelpContext())
        recordVoiceCommandToSpokenTextIfNeeded()
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                spokenText = message,
                error = null
            )
        }
        sessionMemory.rememberSpokenResponse(message)
        _appState.value = AppState.SPEAKING
        emitSpeechEvent(message, force = true)
    }

    private fun currentVoiceHelpContext(): VoiceHelpContext {
        val state = _state.value
        val appState = _appState.value
        return when {
            !state.robotEnabled -> VoiceHelpContext.ROBOT_OFF
            !isAccessibilityServiceReady() -> VoiceHelpContext.ACCESSIBILITY_OFF
            appState == AppState.WAITING_CONFIRMATION ||
                state.agentState == AgentState.WAITING_CONFIRMATION ||
                pendingVoiceCorrection != null -> VoiceHelpContext.WAITING_CONFIRMATION
            state.voiceErrorCategory.isNotBlank() &&
                !state.voiceErrorCategory.equals("ninguno", ignoreCase = true) -> VoiceHelpContext.VOICE_ERROR
            state.externalAppName.equals("WhatsApp", ignoreCase = true) ||
                isWhatsAppWaitingState(state.agentState) ||
                appState == AppState.WAITING_WHATSAPP_ACTION ||
                appState == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> VoiceHelpContext.WHATSAPP
            else -> VoiceHelpContext.DEFAULT
        }
    }

    /**
     * Paquete 4B — aplica el resultado del [agentBridgeDispatch] al estado UI
     * y emite el speech event. NO continúa el pipeline legacy.
     *
     * Reglas:
     *  - Actualiza siempre `lastAgentBridgeMessage` para el panel de debug.
     *  - Si hay pending, set `hasPendingConfirmation = true` y guarda el
     *    prompt en `pendingConfirmationText`.
     *  - Si no hay pending (Confirmed/Cancelled/Rejected/Ready/etc.), limpia.
     *  - El speech se emite con `force = true` solo en outcomes que el
     *    usuario debe oír sí o sí (rechazos, no pending). Para pending y
     *    confirm/cancel, el dedup natural de SpeechController alcanza.
     */
    private fun applyAgentBridgeOutcome(
        outcome: com.ojoclaro.android.agent.core.runtime.BridgeDispatchOutcome.Handled
    ) {
        val forceSpeak = when (outcome.kind) {
            com.ojoclaro.android.agent.core.runtime.BridgeDispatchKind.REJECTED,
            com.ojoclaro.android.agent.core.runtime.BridgeDispatchKind.NO_PENDING,
            com.ojoclaro.android.agent.core.runtime.BridgeDispatchKind.EXPIRED -> true
            else -> false
        }
        _state.update {
            it.copy(
                spokenText = outcome.speakText,
                pendingConfirmationText = outcome.pendingPrompt,
                hasPendingConfirmation = outcome.hasPending,
                lastAgentBridgeMessage = outcome.speakText,
                loading = false,
                listening = false,
                error = null
            )
        }
        sessionMemory.rememberSpokenResponse(outcome.speakText)
        _appState.value = if (outcome.hasPending) {
            AppState.WAITING_CONFIRMATION
        } else {
            AppState.SPEAKING
        }
        emitSpeechEvent(outcome.speakText, force = forceSpeak)
    }

    private fun handleEmergencyModeIfNeeded(text: String): Boolean {
        if (!isEmergencyModeCommand(text)) return false
        agentConversationManager.clear()
        pendingVoiceCorrection = null
        pendingExternalConfirmation = null
        pendingConsentAction = null
        shortTermContext = shortTermContext.recordSuccess(
            activeHandler = RobotActiveHandler.HELP,
            suggestedIntent = RepairSuggestedIntent.HELP,
            externalApp = RobotExternalApp.NONE
        )
        _state.update { it.copy(pendingDebug = pendingDebugLabel()) }
        publishLocalMessage(
            text = emergencyPolicy.safeOfferText(),
            force = true,
            appState = AppState.SPEAKING
        )
        return true
    }

    fun submitVoiceText(text: String, imageBase64: String? = null) {
        var cleanText = text.trim()
        if (cleanText.isBlank()) {
            publishLocalMessage(
                text = repairFailureText(
                    reason = RobotFailureReason.EMPTY_INPUT,
                    kind = RobotRecognizedKind.EMPTY_INPUT
                ),
                force = true,
                appState = AppState.ERROR
            )
            return
        }
        // Paquete 4B: si hay un AgentBridgeDispatchController inyectado y el
        // flag typedConfirmationEnabled está ON, interceptamos antes del legacy.
        // Si el controller devuelve FallbackToLegacy (flag off, blank, Skipped),
        // el flujo legacy continúa intacto.
        if (imageBase64 == null && agentBridgeDispatch != null) {
            val dispatchOutcome = agentBridgeDispatch.dispatch(cleanText)
            if (dispatchOutcome is com.ojoclaro.android.agent.core.runtime.BridgeDispatchOutcome.Handled) {
                applyAgentBridgeOutcome(dispatchOutcome)
                return
            }
            // FallbackToLegacy → caemos al pipeline normal.
        }
        // Ruta experimental del Situation Brain (Fase 3). Apagada por defecto:
        // con SituationBrainFeatureFlag.ENABLED == false esto NO se ejecuta y el
        // comportamiento de producción es idéntico al de antes de esta fase.
        if (SituationBrainFeatureFlag.ENABLED && imageBase64 == null) {
            if (tryHandleWithSituationBrain(cleanText)) return
        }
        markVoiceCommandStarted()
        activeRequestId += 1L
        var normalizedText = VoicePhraseNormalizer.normalizeForParser(cleanText)
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                lastCommand = cleanText,
                lastNormalizedCommand = normalizedText,
                lastRecognizedSpeechText = safeRecognizedSpeechDisplayText(cleanText),
                lastCommandTimestampMillis = now
            )
        }
        logVoiceCommandEvent(
            handler = "received",
            result = RobotLoopLogResult.OK,
            understood = null
        )
        consecutiveWhatsAppWaitingErrors = 0

        val personalSnapshot = personalMemoryStore.snapshot()
        _state.update {
            it.copy(
                decisionSource = "local",
                lastConfidence = 0f,
                memoryUsedDebug = personalSnapshot.summary(),
                contactDebug = personalSnapshot.contacts.firstOrNull()?.label.orEmpty(),
                messageDebug = personalSnapshot.pendingTasks.firstOrNull()?.value.orEmpty(),
                llmEnabled = false,
                llmReason = "disabled",
                llmFallback = "",
                suggestionDebug = ""
            )
        }

        fun applyVoiceCorrectionForRouting(correction: VoiceCommandCorrectionResult) {
            cleanText = correction.correctedText
            normalizedText = VoicePhraseNormalizer.normalizeForParser(cleanText)
            _state.update {
                it.copy(
                    lastNormalizedCommand = normalizedText,
                    lastDecision = "VOICE_CORRECTION_${correction.targetIntent.name}",
                    pendingDebug = pendingDebugLabel()
                )
            }
        }

        if (imageBase64 == null) {
            val correction = VoiceCommandCorrection.correct(cleanText)
            pendingVoiceCorrection = pendingVoiceCorrection
                ?.takeUnless { it.isExpired(now) }

            val pendingCorrection = pendingVoiceCorrection
            if (pendingCorrection != null) {
                if (handleGlobalCommandDuringVoiceCorrection(cleanText, imageBase64)) {
                    return
                }
                val canInterruptPendingCorrection =
                    correction.shouldAutoExecute &&
                        correction.targetIntent in VOICE_CORRECTION_INTERRUPT_TARGETS
                if (canInterruptPendingCorrection) {
                    logVoiceCorrection(correction, RobotLoopLogResult.CORRECTED, consumed = false)
                    pendingVoiceCorrection = null
                    applyVoiceCorrectionForRouting(correction)
                } else {
                    when (VoiceCommandCorrection.confirmationResponse(cleanText)) {
                        VoiceCommandConfirmationResponse.CONFIRM -> {
                            if (!pendingCorrection.correction.canBeConfirmedSafely) {
                                logVoiceCorrection(
                                    pendingCorrection.correction,
                                    RobotLoopLogResult.REJECTED_SENSITIVE,
                                    consumed = true,
                                    reasonCode = "confirmation_not_safe"
                                )
                                pendingVoiceCorrection = null
                                shortTermContext = shortTermContext.reset()
                                publishLocalMessage(
                                    text = ConversationalRepair.SAFE_AI_UNAVAILABLE,
                                    force = true,
                                    appState = AppState.ERROR
                                )
                                return
                            }
                            logVoiceCorrection(
                                pendingCorrection.correction,
                                RobotLoopLogResult.CORRECTED,
                                consumed = true,
                                reasonCode = "confirmed"
                            )
                            pendingVoiceCorrection = null
                            recordShortTermSuccess(RobotActiveHandler.VOICE_CORRECTION)
                            applyVoiceCorrectionForRouting(pendingCorrection.correction)
                        }
                        VoiceCommandConfirmationResponse.CANCEL -> {
                            logVoiceCorrection(
                                pendingCorrection.correction,
                                RobotLoopLogResult.NO_CORRECTION,
                                consumed = true,
                                reasonCode = "user_cancelled"
                            )
                            pendingVoiceCorrection = null
                            shortTermContext = shortTermContext.reset()
                            _state.update { it.copy(pendingDebug = pendingDebugLabel()) }
                            publishLocalMessage(
                                ConversationalRepair.CONFIRMATION_CANCELLED,
                                force = true,
                                appState = AppState.IDLE
                            )
                            return
                        }
                        VoiceCommandConfirmationResponse.NONE -> {
                            logVoiceCorrection(
                                pendingCorrection.correction,
                                RobotLoopLogResult.NEEDS_CONFIRMATION,
                                consumed = true,
                                reasonCode = "waiting_confirmation"
                            )
                            publishLocalMessage(
                                text = repairFailureText(
                                    reason = RobotFailureReason.CONFIRMATION_UNCLEAR,
                                    kind = RobotRecognizedKind.NOISE,
                                    activeHandler = RobotActiveHandler.VOICE_CORRECTION,
                                    forcedPendingState = RobotPendingState.CONFIRMATION
                                ),
                                force = false,
                                appState = AppState.WAITING_CONFIRMATION
                            )
                            return
                        }
                    }
                }
            } else {
                when (correction.correctionType) {
                    VoiceCommandCorrectionType.NO_CORRECTION -> {
                        logVoiceCorrection(correction, RobotLoopLogResult.NO_CORRECTION, consumed = false)
                        if (VoiceCommandCorrection.isKnownRecognizerNoise(cleanText)) {
                            publishLocalMessage(
                                repairFailureText(
                                    reason = RobotFailureReason.RECOGNIZER_NOISE,
                                    kind = RobotRecognizedKind.NOISE,
                                    activeHandler = RobotActiveHandler.VOICE_CORRECTION
                                ),
                                force = false,
                                appState = AppState.ERROR
                            )
                            _state.update { it.copy(voiceListenRequestId = it.voiceListenRequestId + 1L) }
                            return
                        }
                    }
                    VoiceCommandCorrectionType.REJECTED_SENSITIVE -> {
                        logVoiceCorrection(correction, RobotLoopLogResult.REJECTED_SENSITIVE, consumed = false)
                    }
                    VoiceCommandCorrectionType.AUTO_CORRECTION -> {
                        logVoiceCorrection(correction, RobotLoopLogResult.CORRECTED, consumed = false)
                        recordShortTermSuccess(
                            activeHandler = RobotActiveHandler.VOICE_CORRECTION,
                            suggestedIntent = correction.targetIntent.toRepairSuggestedIntent()
                        )
                        applyVoiceCorrectionForRouting(correction)
                    }
                    VoiceCommandCorrectionType.CONFIRMATION_REQUIRED -> {
                        pendingVoiceCorrection = PendingVoiceCommandCorrection(
                            correction = correction,
                            createdAtMillis = now,
                            expiresAtMillis = now + VoiceCommandCorrection.CONFIRMATION_TTL_MILLIS
                        )
                        shortTermContext = shortTermContext.recordFailure(
                            reason = RobotFailureReason.LOW_CONFIDENCE,
                            kind = RobotRecognizedKind.POSSIBLE_COMMAND,
                            suggestedIntent = correction.targetIntent.toRepairSuggestedIntent(),
                            activeHandler = RobotActiveHandler.VOICE_CORRECTION,
                            externalApp = currentRepairExternalApp(),
                            pendingState = RobotPendingState.CONFIRMATION,
                            askedConfirmation = true
                        )
                        _state.update {
                            it.copy(
                                pendingDebug = pendingDebugLabel(),
                                lastDecision = "VOICE_CORRECTION_CONFIRM_${correction.targetIntent.name}"
                            )
                        }
                        logVoiceCorrection(correction, RobotLoopLogResult.NEEDS_CONFIRMATION, consumed = true)
                        publishLocalMessage(
                            text = ConversationalRepair.possibleCommand(correction.targetIntent.toRepairSuggestedIntent()),
                            force = true,
                            appState = AppState.WAITING_CONFIRMATION
                        )
                        return
                    }
                }
            }
        }

        when (robotSessionCommand(cleanText)) {
            RobotSessionCommand.ENABLE -> {
                logVoiceCommandEvent(
                    handler = "robot_session",
                    result = RobotLoopLogResult.UNDERSTOOD,
                    understood = true,
                    consumed = true,
                    reasonCode = "enable"
                )
                enableRobotSession(hasMicrophonePermission = _state.value.microphonePermissionGranted)
                recordShortTermSuccess(RobotActiveHandler.ROBOT_SESSION)
                publishLocalMessage("Robot encendido. Te escucho.", force = true, appState = AppState.SPEAKING)
                return
            }
            RobotSessionCommand.DISABLE -> {
                logVoiceCommandEvent(
                    handler = "robot_session",
                    result = RobotLoopLogResult.UNDERSTOOD,
                    understood = true,
                    consumed = true,
                    reasonCode = "disable"
                )
                pauseRobotSession()
                recordShortTermSuccess(RobotActiveHandler.ROBOT_SESSION)
                publishLocalMessage("Robot pausado.", force = true, appState = AppState.IDLE)
                return
            }
            RobotSessionCommand.NONE -> Unit
        }

        if (VoiceCommandDispatcher.isStopCommand(cleanText)) {
            logVoiceCommandEvent(
                handler = "stop_speech",
                result = RobotLoopLogResult.UNDERSTOOD,
                understood = true,
                consumed = true,
                reasonCode = "stop"
            )
            agentConversationManager.clear()
            clearVoiceCommandStarted()
            recordShortTermSuccess(RobotActiveHandler.STOP_SPEAKING)
            onStopSpeechRequested()
            return
        }

        if (imageBase64 == null && routeCommand("reset_flow") { isResetFlowCommand(cleanText) }) {
            resetFlow()
            return
        }

        if (imageBase64 == null && routeCommand("repeat_last") { isRepeatLastResponseCommand(cleanText) }) {
            logVoiceCommandEvent(
                handler = "repeat_last",
                result = RobotLoopLogResult.UNDERSTOOD,
                understood = true
            )
            recordShortTermSuccess(RobotActiveHandler.REPEAT_LAST)
            repeatLastResponse()
            return
        }

        if (imageBase64 == null && routeCommand("slow_voice") { isSlowVoiceCommand(cleanText) }) {
            logVoiceCommandEvent(
                handler = "slow_voice",
                result = RobotLoopLogResult.UNDERSTOOD,
                understood = true
            )
            publishLocalMessage(slowVoiceUnavailableText(), force = true, appState = _appState.value)
            return
        }

        if (imageBase64 == null && routeCommand("return_home") { isGoHomeCommand(cleanText) }) {
            logVoiceCommandEvent(
                handler = "return_home",
                result = RobotLoopLogResult.UNDERSTOOD,
                understood = true
            )
            returnToHome()
            return
        }

        if (imageBase64 == null && routeCommand("emergency_mode") { handleEmergencyModeIfNeeded(cleanText) }) {
            return
        }

        if (imageBase64 == null && routeCommand("help") { VoiceCommandDispatcher.isHelpCommand(cleanText) }) {
            logVoiceCommandEvent(
                handler = "help",
                result = RobotLoopLogResult.UNDERSTOOD,
                understood = true
            )
            agentConversationManager.clear()
            recordShortTermSuccess(RobotActiveHandler.HELP)
            requestHelp()
            return
        }

        if (imageBase64 == null && routeCommand("robot_status_diagnostic") { handleRobotStatusDiagnosticIfNeeded(cleanText) }) {
            return
        }

        if (imageBase64 == null && routeCommand("screen_understanding") { handleScreenUnderstandingIfNeeded(cleanText) }) {
            return
        }

        if (imageBase64 == null && routeCommand("whatsapp_guided") { handleWhatsAppGuidedWorkflowIfNeeded(cleanText) }) {
            return
        }

        if (imageBase64 == null && routeCommand("visible_chats") { handleWhatsAppVisibleChatsIfNeeded(cleanText) }) {
            return
        }

        if (imageBase64 == null && routeCommand("routine_learning") { handleHumanRoutineLearningIfNeeded(cleanText) }) {
            return
        }

        if (imageBase64 == null && isContextualMessageRetryCommand(cleanText)) {
            val pending = buildContextualWhatsAppPendingFromSession(
                text = cleanText,
                snapshot = sessionMemory.snapshot(),
                nowMillis = now
            )
            if (pending == null) {
                logVoiceCommandEvent(
                    handler = "contextual_retry",
                    result = RobotLoopLogResult.NOT_UNDERSTOOD,
                    understood = false
                )
                publishLocalMessage(
                    text = "Necesito un poco más de contexto. Decime: mandale a un contacto que estoy llegando.",
                    force = true,
                    appState = AppState.SPEAKING
                )
            } else {
                logVoiceCommandEvent(
                    handler = "contextual_retry",
                    result = RobotLoopLogResult.UNDERSTOOD,
                    understood = true
                )
                pendingExternalConfirmation = pending
                sessionMemory.rememberPendingAction(pending)
                sessionMemory.rememberContactAndMessage(
                    pending.command.contactName.orEmpty(),
                    pending.command.messageText.orEmpty()
                )
                publishLocalMessage(
                    text = pending.spokenText,
                    force = true,
                    appState = AppState.WAITING_CONFIRMATION
                )
            }
            return
        }

        if (
            imageBase64 == null &&
            (pendingExternalConfirmation != null || pendingConsentAction != null) &&
            isAffirmativeNoise(cleanText)
        ) {
            logRoutingDecision("noise_filter", consumed = true, reasonCode = "confirmation_noise")
            logVoiceCommandEvent(
                handler = "confirmation_noise",
                result = RobotLoopLogResult.NOT_UNDERSTOOD,
                understood = false
            )
            publishLocalMessage(strictConfirmationReminderText(), force = true, appState = AppState.WAITING_CONFIRMATION)
            return
        }

        val parsedIntentForPersonal = if (imageBase64 == null) {
            agentIntentParser.parse(cleanText)
        } else {
            null
        }
        if (
            imageBase64 == null &&
            parsedIntentForPersonal != null &&
            shouldUsePersonalAgentForHumanMessageDraft(cleanText, parsedIntentForPersonal)
        ) {
            logVoiceCommandEvent(
                handler = "personal_agent",
                result = RobotLoopLogResult.UNDERSTOOD,
                understood = true
            )
            handlePersonalAgentRequest(
                cleanText = cleanText,
                normalizedText = normalizedText,
                personalSnapshot = personalSnapshot,
                parsedIntent = parsedIntentForPersonal,
                now = now
            )
            return
        }

        if (imageBase64 == null && routeCommand("agent_conversation") { handleAgentConversationIfNeeded(cleanText) }) {
            return
        }

        if (imageBase64 == null && routeCommand("external_command") { handleExternalCommandIfNeeded(cleanText) }) {
            return
        }

        if (imageBase64 == null && routeCommand("read_text") { VoiceCommandDispatcher.isReadTextCommand(cleanText) }) {
            logVoiceCommandEvent(
                handler = "read_text",
                result = RobotLoopLogResult.UNDERSTOOD,
                understood = true
            )
            agentConversationManager.clear()
            startScanning()
            return
        }

        // Cortocircuito: textos como "sí", "si", "dale", "ok", "uh", "eh" sin pending real
        // no deben llegar al backend. Antes producían "No pude conectar. Entendí: sí sí".
        // Si llegan acá es porque los filtros previos ya descartaron la opción de
        // confirmar/cancelar/comando externo/agente conversacional.
        if (imageBase64 == null && isAffirmativeNoise(cleanText)) {
            logRoutingDecision("noise_filter", consumed = true, reasonCode = "affirmative_noise")
            logVoiceCommandEvent(
                handler = "affirmative_noise",
                result = RobotLoopLogResult.NOT_UNDERSTOOD,
                understood = false
            )
            publishLocalMessage("No hay ninguna acción pendiente.", force = false)
            return
        }

        val requestId = activeRequestId
        val command = parser.parse(cleanText)
        logRoutingDecision("assistant_api", consumed = true, reasonCode = "fallback")

        _state.update {
            it.copy(
                loading = true,
                listening = false,
                lastCommand = cleanText,
                lastNormalizedCommand = normalizedText,
                lastRecognizedSpeechText = safeRecognizedSpeechDisplayText(cleanText),
                lastCommandTimestampMillis = now,
                error = null
            )
        }
        logVoiceCommandEvent(
            handler = "assistant_api",
            result = RobotLoopLogResult.UNDERSTOOD,
            understood = true
        )
        _appState.value = AppState.PROCESSING

        viewModelScope.launch {
            val handledByPersonalAgent = if (imageBase64 == null && command.type == AppCommandType.UNKNOWN) {
                val decision = decidePersonalAgent(
                    cleanText = cleanText,
                    normalizedText = normalizedText,
                    personalSnapshot = personalSnapshot,
                    parsedIntent = parsedIntentForPersonal ?: agentIntentParser.parse(cleanText),
                    now = now
                )
                if (shouldDropAsyncResult(requestId, handler = "personal_agent")) return@launch
                handlePersonalAgentDecision(decision, requestId)
            } else {
                false
            }

            if (handledByPersonalAgent) return@launch

            runCatching {
                api.assist(
                    AssistRequest(
                        command = command,
                        userMessage = cleanText,
                        imageBase64 = imageBase64,
                        accessibilityMode = AccessibilityMode.VOICE_FIRST
                    )
                )
            }.onSuccess { response ->
                if (shouldDropAsyncResult(requestId, handler = "assistant_api")) return@onSuccess
                publishAssistantResponse(requestId, response)
            }.onFailure { error ->
                if (shouldDropAsyncResult(requestId, handler = "assistant_api")) return@onFailure
                val fallback = localFallback(command, cleanText)
                recordVoiceCommandToSpokenTextIfNeeded()
                _state.update {
                    it.copy(
                        loading = false,
                        spokenText = fallback.spokenText,
                        error = error.message ?: "No se pudo conectar con el backend."
                    )
                }

                if (requestId > mutedThroughRequestId) {
                    _appState.value = AppState.ERROR
                    emitSpeechEvent(fallback.spokenText)
                } else {
                    _appState.value = AppState.IDLE
                }
            }
        }
    }

    /**
     * Ruta experimental del Situation Brain (Fase 3). Solo se invoca cuando
     * [SituationBrainFeatureFlag.ENABLED] es true. Con el flag apagado este
     * método nunca corre y el comportamiento de producción no cambia.
     *
     * Devuelve true si el Situation Brain manejó el comando por completo;
     * false para que el flujo viejo lo procese (fallback seguro).
     *
     * Fase 9: mantiene Speak/Cancel/Reject y suma confirmación segura para
     * OPEN_APP, CALL_CONTACT y WRITE_MESSAGE. ChangeState/Unsupported siguen
     * cayendo al flujo viejo.
     */
    private fun tryHandleWithSituationBrain(rawCommand: String): Boolean {
        return try {
            // Fase 4: la memoria runtime hace que el Brain no sea amnésico entre
            // comandos. Primero se descartan objetivos vencidos.
            situationRuntimeMemory.clearExpiredGoals(System.currentTimeMillis())
            val snapshot = situationRuntimeMemory.current()
            val context = SituationContextFactory().fromVoiceCommand(
                rawCommand = rawCommand,
                currentStateName = _appState.value.name,
                currentAppPackage = null,
                activeRequestId = activeRequestId,
                mutedThroughRequestId = mutedThroughRequestId,
                lastAssistantMessage = _state.value.spokenText,
                runtimeSnapshot = snapshot
            )
            val result = SituationBrain().process(context)
            // Se persiste en RAM el contexto actualizado del Brain.
            situationRuntimeMemory.updateFrom(result.updatedContext)
            when (val effect = SituationDecisionApplier().toUiEffect(result.decision)) {
                is SituationUiEffect.Speak -> {
                    situationRuntimeMemory.rememberAssistantMessage(effect.message)
                    publishLocalMessage(effect.message, force = true, appState = AppState.SPEAKING)
                    true
                }
                is SituationUiEffect.Cancel -> {
                    situationRuntimeMemory.clearForCancellation(result.updatedContext)
                    // Reusa la cancelación existente: no se duplica lógica.
                    agentConversationManager.clear()
                    onStopSpeechRequested()
                    true
                }
                is SituationUiEffect.Reject -> {
                    if (effect.reason.isNotBlank()) {
                        situationRuntimeMemory.rememberAssistantMessage(effect.reason)
                    }
                    publishLocalMessage(effect.reason, force = true, appState = AppState.ERROR)
                    true
                }
                is SituationUiEffect.Execute -> handleSituationExecuteIntent(effect, rawCommand)
                is SituationUiEffect.AskConfirmation -> {
                    // La pendingAction ya quedó en memoria por updateFrom. Solo
                    // se habla el prompt si la acción está permitida en esta
                    // fase; si no, se olvida la pendingAction (para no trabar la
                    // conversación) y se cae al flujo viejo.
                    if (isSituationPendingActionAllowed(effect.pendingAction)) {
                        situationRuntimeMemory.rememberAssistantMessage(effect.prompt)
                        publishLocalMessage(
                            effect.prompt,
                            force = true,
                            appState = AppState.WAITING_CONFIRMATION
                        )
                        true
                    } else {
                        situationRuntimeMemory.forgetPendingAction()
                        handleRejectedSituationPendingAction(effect.pendingAction)
                    }
                }
                // Efectos todavía no cableados: la memoria ya quedó actualizada,
                // pero la acción real la maneja el flujo viejo.
                is SituationUiEffect.NoOp,
                is SituationUiEffect.ChangeState,
                is SituationUiEffect.Unsupported -> false
            }
        } catch (e: Exception) {
            // Cualquier falla del cerebro experimental NO debe romper el flujo
            // real ni corromper la memoria: se cae al flujo viejo.
            false
        }
    }

    /**
     * Cablea ExecuteIntent del Situation Brain SOLO para intenciones seguras y
     * ya implementadas. Delega a handlers existentes sin duplicar lógica.
     *
     * Cualquier otra intención (GUIDE_USER, MANAGE_MEMORY) devuelve false y cae
     * al flujo viejo. Si la delegación falla, también se cae al flujo viejo.
     */
    private fun handleSituationExecuteIntent(
        effect: SituationUiEffect.Execute,
        rawCommand: String
    ): Boolean {
        return try {
            when (effect.intent) {
                // Lectura / entendimiento de pantalla (Fase 5).
                SituationIntent.READ_SCREEN,
                SituationIntent.SUMMARIZE_SCREEN,
                SituationIntent.EXPLAIN_WHAT_I_SEE ->
                    handleScreenUnderstandingIfNeeded(rawCommand)
                // Abrir app / llamar (Fase 6-7): se delega al router de comandos
                // externos existente, que ya tiene sus filtros de seguridad y
                // usa ACTION_DIAL para llamar (nunca CALL_PHONE).
                //
                // Fase 7: se usa el comando ORIGINAL conservado en la
                // pendingAction confirmada (ej. "abrí WhatsApp", "llamá a Sofi"),
                // no el "sí" del turno de confirmación. Si el handler viejo no
                // reconoce el texto, devuelve false y cae al flujo viejo.
                SituationIntent.OPEN_APP,
                SituationIntent.CALL_CONTACT -> {
                    val commandForHandler = effect.pendingAction?.commandForExecution()
                        ?.takeIf { it.isNotBlank() }
                        ?: rawCommand
                    val handled = handleExternalCommandIfNeeded(commandForHandler)
                    if (handled) {
                        situationRuntimeMemory.forgetPendingAction()
                    }
                    handled
                }
                SituationIntent.WRITE_MESSAGE -> {
                    val action = SituationConfirmedActionAdapter.fromExecuteEffect(effect)
                        ?: return false
                    handleConfirmedSituationWriteMessage(action)
                }
                // GUIDE_USER, MANAGE_MEMORY: sin handler genérico seguro en esta
                // fase -> fallback al flujo viejo.
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun handleConfirmedSituationWriteMessage(
        action: SituationConfirmedAction
    ): Boolean {
        if (action.intent != SituationIntent.WRITE_MESSAGE) return false
        val pending = action.pendingAction
        if (!SituationMessageSafety.isSafeWriteMessagePendingAction(pending)) return false
        if (pendingExternalConfirmation != null || pendingConsentAction != null) return false

        val contact = SituationMessageSafety.contactFrom(pending)
        val message = SituationMessageSafety.messageFrom(pending)
        if (contact.isBlank() || message.isBlank()) return false

        val now = System.currentTimeMillis()
        val requestId = ++activeRequestId
        val currentAppState = _appState.value
        val legacyPending = PendingConfirmation(
            id = "situation-confirmed-$now",
            command = ExternalCommand(
                type = ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE,
                rawText = action.commandForExecution,
                normalizedText = action.commandForExecution,
                contactName = contact,
                messageText = message,
                confidence = CommandConfidence.HIGH
            ),
            spokenText = pending.confirmationPrompt,
            createdAtMillis = now
        )

        _state.update {
            it.copy(
                loading = true,
                listening = false,
                micListening = false,
                error = null
            )
        }
        _appState.value = AppState.PROCESSING

        viewModelScope.launch {
            val outcome = orchestrator.process(
                rawInput = "confirmar",
                pendingConfirmation = legacyPending,
                pendingConsent = null,
                appState = currentAppState,
                nowMillis = now
            )
            if (shouldDropAsyncResult(requestId, handler = "situation_confirmed_write_message")) return@launch
            applyOutcomeIfExternal(requestId, outcome)
            if (!outcome.isError) {
                situationRuntimeMemory.forgetPendingAction()
            }
        }
        return true
    }

    /**
     * Acciones pendientes que el Situation Brain puede confirmar.
     * Abrir app y llamar conservan el comportamiento previo. WRITE_MESSAGE se
     * permite solo si trae contacto/mensaje y pasa la guardia segura local.
     */
    private fun isSituationPendingActionAllowed(action: PendingAction): Boolean {
        val intent = situationIntentFromPendingAction(action) ?: return false
        return when (intent) {
            SituationIntent.OPEN_APP,
            SituationIntent.CALL_CONTACT -> true
            SituationIntent.WRITE_MESSAGE -> SituationMessageSafety.isSafeWriteMessagePendingAction(action)
            else -> false
        }
    }

    private fun handleRejectedSituationPendingAction(action: PendingAction): Boolean {
        if (situationIntentFromPendingAction(action) != SituationIntent.WRITE_MESSAGE) return false
        val contact = SituationMessageSafety.contactFrom(action)
        val message = SituationMessageSafety.messageFrom(action)
        if (contact.isBlank() || message.isBlank()) return false
        val text = "No puedo preparar mensajes con datos sensibles."
        situationRuntimeMemory.rememberAssistantMessage(text)
        publishLocalMessage(text, force = true, appState = AppState.ERROR)
        return true
    }

    private fun handlePersonalAgentRequest(
        cleanText: String,
        normalizedText: String,
        personalSnapshot: PersonalMemorySnapshot,
        parsedIntent: ParsedAgentIntent,
        now: Long
    ) {
        val requestId = ++activeRequestId
        _state.update {
            it.copy(
                loading = true,
                listening = false,
                lastCommand = cleanText,
                lastNormalizedCommand = normalizedText,
                lastCommandTimestampMillis = now,
                error = null
            )
        }
        _appState.value = AppState.PROCESSING
        viewModelScope.launch {
            val decision = decidePersonalAgent(
                cleanText = cleanText,
                normalizedText = normalizedText,
                personalSnapshot = personalSnapshot,
                parsedIntent = parsedIntent,
                now = now
            )
            if (shouldDropAsyncResult(requestId, handler = "personal_agent")) return@launch
            handlePersonalAgentDecision(decision, requestId)
        }
    }

    private suspend fun decidePersonalAgent(
        cleanText: String,
        normalizedText: String,
        personalSnapshot: PersonalMemorySnapshot,
        parsedIntent: ParsedAgentIntent,
        now: Long
    ): PersonalAgentDecision =
        personalAgentDecisionEngine.decide(
            PersonalAgentDecisionInput(
                originalText = cleanText,
                normalizedText = normalizedText,
                agentState = _appState.value.toAgentState(),
                appState = _appState.value,
                memorySnapshot = personalSnapshot,
                externalApp = _state.value.externalAppName.takeIf { it != "None" },
                hasPendingConfirmation = pendingExternalConfirmation != null,
                currentTimeMillis = now,
                parsedIntent = parsedIntent,
                globalCapability = globalAssistantCapabilityProvider(),
                suggestionsEnabled = true
            )
        )

    fun startScanning() {
        recordVoiceCommandToSpokenTextIfNeeded()
        mutedThroughRequestId = activeRequestId
        val message = "Buscando texto con la cámara. Apuntá al texto."
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                error = null,
                spokenText = message
            )
        }
        sessionMemory.rememberSpokenResponse(message)
        _appState.value = AppState.SCANNING
        emitSpeechEvent(message, force = true)
    }

    fun stopScanning() {
        _appState.value = AppState.IDLE
    }

    fun onTextScanResult(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        val warnings = riskDetector.detectFromOcrText(cleanText)
        val spoken = riskAwareOcrText(cleanText, warnings)
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                spokenText = spoken,
                error = null
            )
        }
        emitSpeechEvent(spoken)
    }

    fun onTextScanNoTextFound() {
        val message = "No encontré texto claro."
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                spokenText = message,
                error = null
            )
        }
        emitSpeechEvent(message)
    }

    fun onCameraPermissionDenied() {
        publishLocalMessage(
            text = Capability.MSG_CAMERA_MISSING,
            force = true,
            appState = AppState.PERMISSION_REQUIRED
        )
    }

    fun onCameraError() {
        publishLocalMessage(
            text = "La cámara está ocupada o no pudo abrir. Probá de nuevo en un momento.",
            force = true,
            appState = AppState.ERROR
        )
    }

    fun onMicrophonePermissionGranted() {
        publishLocalMessage(
            text = "Microfono activado. $SHORT_READY_TEXT",
            force = true,
            appState = AppState.SPEAKING
        )
        _state.update { it.copy(microphonePermissionGranted = true) }
    }

    fun onMicrophonePermissionDenied() {
        publishLocalMessage(
            text = VoiceCommandController.MICROPHONE_PERMISSION_MESSAGE,
            force = true,
            appState = AppState.PERMISSION_REQUIRED
        )
        _state.update { it.copy(microphonePermissionGranted = false) }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val message = if (granted) {
            "Permiso de ubicación activado. Volvé a pedirme la ubicación cuando quieras."
        } else {
            LocationCommandPhrases.LOCATION_PERMISSION_DENIED
        }
        publishLocalMessage(
            text = message,
            force = true,
            appState = if (granted) AppState.SPEAKING else AppState.PERMISSION_REQUIRED
        )
    }

    fun onVoiceReady() {
        _state.update {
            it.copy(
                loading = false,
                listening = true,
                micListening = true,
                robotSessionState = RobotSessionState.LISTENING,
                error = null
            )
        }
        _appState.value = AppState.LISTENING
        logVoiceLoopState(action = "listening", state = RobotSessionState.LISTENING, micActive = true)
    }

    fun onVoicePartialText(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        _state.update {
            it.copy(
                listening = true,
                micListening = true,
                robotSessionState = RobotSessionState.LISTENING,
                voicePartialText = cleanText,
                lastCommand = cleanText,
                lastNormalizedCommand = VoicePhraseNormalizer.normalizeForParser(cleanText),
                lastRecognizedSpeechText = safeRecognizedSpeechDisplayText(cleanText),
                lastCommandTimestampMillis = System.currentTimeMillis()
            )
        }
    }

    fun onVoiceFinalText(text: String) {
        val cleanText = text.trim()
        _state.update {
            it.copy(
                listening = false,
                micListening = false,
                robotSessionState = RobotSessionState.PROCESSING,
                voicePartialText = "",
                lastCommand = cleanText,
                lastNormalizedCommand = VoicePhraseNormalizer.normalizeForParser(cleanText),
                lastRecognizedSpeechText = safeRecognizedSpeechDisplayText(cleanText),
                lastCommandTimestampMillis = System.currentTimeMillis()
            )
        }
        submitVoiceText(cleanText)
    }

    fun onVoiceError(message: String) {
        logVoiceCommandEvent(
            handler = "speech_recognizer",
            result = RobotLoopLogResult.NOT_UNDERSTOOD,
            understood = false,
            consumed = false,
            reasonCode = "recognizer_error"
        )
        if (agentConversationManager.hasPendingSlotRequest) {
            val currentAgentState = agentConversationManager.currentState
            val shouldExplainWhatsApp = isWhatsAppWaitingState(currentAgentState)
            if (shouldExplainWhatsApp) {
                consecutiveWhatsAppWaitingErrors += 1
            }
            _state.update {
                it.copy(
                    loading = false,
                    listening = false,
                    micListening = false,
                    robotSessionState = RobotSessionState.ERROR_RECOVERABLE,
                    voiceListenRequestId = it.voiceListenRequestId + 1L
                )
            }
            _appState.value = agentConversationManager.currentState.toAppState()
            if (shouldExplainWhatsApp && consecutiveWhatsAppWaitingErrors >= WHATSAPP_WAITING_FALLBACK_ERROR_COUNT) {
                consecutiveWhatsAppWaitingErrors = 0
                publishLocalMessage(
                    text = whatsAppWaitingFallbackText(),
                    force = true,
                    appState = currentAgentState.toAppState()
                )
            }
            return
        }
        consecutiveWhatsAppWaitingErrors = 0
        publishLocalMessage(
            text = message,
            force = false,
            appState = AppState.ERROR
        )
    }

    fun onSpeechRecognizerError(errorCode: Int?) {
        _state.update {
            it.copy(
                lastSpeechError = VoiceCommandController.errorName(errorCode),
                micListening = false
            )
        }
    }

    fun onVoiceDiagnosticChanged(diagnostic: VoiceListeningDiagnostic) {
        _state.update {
            it.copy(
                voiceHearingStatus = diagnostic.hearingStatus.publicLabel,
                voiceErrorCategory = diagnostic.errorCategory?.name ?: "ninguno",
                voiceSpeechEngine = when (diagnostic.speechEngine) {
                    com.ojoclaro.android.voice.VoiceSpeechEngine.ON_DEVICE -> "on-device"
                    com.ojoclaro.android.voice.VoiceSpeechEngine.PLATFORM_DEFAULT -> "sistema"
                    com.ojoclaro.android.voice.VoiceSpeechEngine.UNAVAILABLE -> "no disponible"
                },
                micListening = diagnostic.hearingStatus == com.ojoclaro.android.voice.VoiceHearingStatus.LISTENING
            )
        }
    }

    fun onVoiceListeningStateChanged(state: VoiceListeningState) {
        val nextRobotState = when (state) {
            VoiceListeningState.LISTENING -> RobotSessionState.LISTENING
            VoiceListeningState.SPEAKING -> RobotSessionState.SPEAKING
            VoiceListeningState.PROCESSING -> RobotSessionState.PROCESSING
            VoiceListeningState.ERROR -> RobotSessionState.ERROR_RECOVERABLE
            VoiceListeningState.STOPPED_BY_USER -> RobotSessionState.OFF
            VoiceListeningState.WAITING_RETRY,
            VoiceListeningState.IDLE -> if (_state.value.robotEnabled) RobotSessionState.READY else RobotSessionState.OFF
        }
        _state.update {
            it.copy(
                micListening = state == VoiceListeningState.LISTENING,
                listening = state == VoiceListeningState.LISTENING,
                robotSessionState = nextRobotState
            )
        }
        logVoiceLoopState(
            action = state.name.lowercase(),
            state = nextRobotState,
            micActive = state == VoiceListeningState.LISTENING
        )
    }

    fun onTtsSpeakingChanged(isSpeaking: Boolean) {
        _state.update {
            it.copy(
                ttsSpeaking = isSpeaking,
                robotSessionState = if (isSpeaking) {
                    RobotSessionState.SPEAKING
                } else if (it.robotEnabled) {
                    RobotSessionState.READY
                } else {
                    RobotSessionState.OFF
                }
            )
        }
        logVoiceLoopState(
            action = if (isSpeaking) "tts_started" else "resume_after_tts",
            state = if (isSpeaking) RobotSessionState.SPEAKING else if (_state.value.robotEnabled) RobotSessionState.READY else RobotSessionState.OFF,
            micActive = false
        )
    }

    fun onStopSpeechRequested() {
        mutedThroughRequestId = activeRequestId
        // "Callar" / "pará" solo corta la voz. Para cancelar acciones pendientes
        // el usuario debe decir "cancelar", así evitamos perder contexto por error.
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                micListening = false,
                robotSessionState = if (it.robotEnabled) RobotSessionState.READY else RobotSessionState.OFF,
                voicePartialText = "",
                externalAppHandoff = null,
                globalModeOn = false,
                externalAppName = "None",
                ttlRemainingMillis = 0L,
                error = null
            )
        }
        _appState.value = AppState.IDLE
    }

    fun resetFlow() {
        logVoiceCommandEvent(
            handler = "reset_flow",
            result = RobotLoopLogResult.RESET,
            understood = true
        )
        activeRequestId += 1L
        mutedThroughRequestId = activeRequestId
        clearVoiceCommandStarted()
        pendingVoiceCorrection = null
        pendingExternalConfirmation = null
        pendingConsentAction = null
        shortTermContext = shortTermContext.reset()
        consecutiveWhatsAppWaitingErrors = 0
        agentConversationManager.clear()
        sessionMemory.clearConversationContext()
        handoffCallbackTracker.clear()
        val message = RESET_FLOW_TEXT
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                micListening = false,
                robotSessionState = if (it.robotEnabled) RobotSessionState.READY else RobotSessionState.OFF,
                voicePartialText = "",
                spokenText = message,
                pendingDebug = "",
                agentState = null,
                lastDecision = "RESET_FLOW",
                externalAppHandoff = null,
                globalModeOn = false,
                externalAppName = "None",
                ttlRemainingMillis = 0L,
                error = null
            )
        }
        sessionMemory.rememberSpokenResponse(message)
        _appState.value = AppState.IDLE
        emitSpeechEvent(message, force = true)
    }

    fun onExternalCommandResult(result: CommandResult) {
        val requestId = activeExternalActionRequestId
        if (requestId != null && shouldDropAsyncResult(requestId, handler = "external_command_result")) {
            activeExternalActionRequestId = null
            return
        }
        activeExternalActionRequestId = null
        val appState = when (result) {
            is CommandResult.Failed -> AppState.ERROR
            is CommandResult.NotSupported -> AppState.ERROR
            is CommandResult.NeedsConfirmation -> AppState.WAITING_CONFIRMATION
            is CommandResult.Success -> AppState.SPEAKING
        }
        publishLocalMessage(
            text = result.spokenText(),
            force = true,
            appState = appState
        )
    }

    fun onExternalHandoffLaunchResult(
        handoff: ExternalActionEvent.ExternalAppHandoff,
        result: CommandResult
    ) {
        val requestId = activeExternalActionRequestId
        if (requestId != null && shouldDropAsyncResult(requestId, handler = "external_handoff_result")) {
            activeExternalActionRequestId = null
            return
        }
        activeExternalActionRequestId = null
        if (result is CommandResult.Success) {
            handoffCallbackTracker.markStarted(ExternalHandoffCallbacks.classify(handoff))
            _state.update {
                it.copy(
                    loading = false,
                    listening = false,
                    externalAppHandoff = handoff,
                    globalModeOn = true,
                    externalAppName = handoff.externalAppName,
                    ttlRemainingMillis = 60_000L,
                    error = null
                )
            }
            _appState.value = AppState.EXTERNAL_APP_HANDOFF
        } else {
            handoffCallbackTracker.clear()
            clearExternalHandoff()
            onExternalCommandResult(result)
        }
    }

    /**
     * Lifecycle hook que el HomeScreen llama en ON_RESUME. Si Ojo Claro lanzó un
     * handoff externo y el usuario está volviendo, decimos UNA frase corta que
     * resume qué pasó. Si no hay nada pendiente o el usuario ya está hablando
     * con el asistente, no decimos nada para no pisar al usuario.
     */
    fun onForegroundReturned() {
        if (!handoffCallbackTracker.hasPending) return
        if (!canSpeakHandoffCallback()) {
            // El usuario ya está dando un comando o hay una conversación viva.
            // No descartamos la pendiente: se intentará en el próximo retorno
            // limpio. La regla "no repetir" sigue valiendo: una sola vez consumido,
            // no vuelve a hablar.
            return
        }
        val kind = handoffCallbackTracker.consumeIfPending() ?: return
        val callbackText = ExternalHandoffCallbacks.textFor(kind)
        clearExternalHandoff()
        publishLocalMessage(
            text = callbackText,
            force = true,
            appState = AppState.SPEAKING
        )
    }

    private fun canSpeakHandoffCallback(): Boolean {
        val snapshot = _state.value
        if (agentConversationManager.hasPendingSlotRequest) return false
        if (pendingVoiceCorrection != null) return false
        if (pendingExternalConfirmation != null) return false
        if (pendingConsentAction != null) return false
        if (snapshot.micListening) return false
        if (snapshot.loading) return false
        return when (_appState.value) {
            AppState.LISTENING,
            AppState.PROCESSING,
            AppState.SCANNING,
            AppState.WAITING_CONTACT,
            AppState.WAITING_MESSAGE,
            AppState.WAITING_CONFIRMATION,
            AppState.WAITING_WHATSAPP_ACTION,
            AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> false
            else -> true
        }
    }

    fun onSpeechDispatched() {
        if (_appState.value == AppState.SPEAKING || _appState.value == AppState.ERROR) {
            _appState.value = AppState.IDLE
        }
    }

    fun setIdle() {
        _appState.value = AppState.IDLE
    }

    fun setSpeaking() {
        _appState.value = AppState.SPEAKING
    }

    private fun publishAssistantResponse(requestId: Long, response: AssistResponse) {
        if (shouldDropAsyncResult(requestId, handler = "assistant_api")) return
        val spokenText = response.spokenText.trim().ifBlank {
            "Recibí una respuesta vacía del asistente."
        }

        recordVoiceCommandToSpokenTextIfNeeded()
        _state.update {
            it.copy(
                loading = false,
                spokenText = spokenText,
                error = null
            )
        }
        sessionMemory.rememberSpokenResponse(spokenText)

        if (requestId > mutedThroughRequestId) {
            _appState.value = AppState.SPEAKING
            emitSpeechEvent(spokenText)
        } else {
            _appState.value = AppState.IDLE
        }
    }

    private fun handleExternalCommandIfNeeded(text: String): Boolean {
        if (!shouldHandleExternalCommand(
                text = text,
                hasPendingConsent = pendingConsentAction != null,
                router = orchestratorRouter
            )
        ) {
            return false
        }
        logVoiceCommandEvent(
            handler = "external_command",
            result = RobotLoopLogResult.UNDERSTOOD,
            understood = true
        )
        val requestId = ++activeRequestId
        val currentAppState = _appState.value
        _state.update {
            it.copy(
                loading = true,
                listening = false,
                micListening = false,
                error = null
            )
        }
        _appState.value = AppState.PROCESSING
        viewModelScope.launch {
            val outcome = orchestrator.process(
                rawInput = text,
                pendingConfirmation = pendingExternalConfirmation,
                pendingConsent = pendingConsentAction,
                appState = currentAppState
            )
            if (shouldDropAsyncResult(requestId, handler = "external_command")) return@launch
            applyOutcomeIfExternal(requestId, outcome)
        }
        // Si hay un consent pending vivo, "confirmar"/"cancelar" deben pasar por el
        // orchestrator (no caer al backend). En cualquier otro caso, basta con detectar
        // el comando externo de forma sincrónica como antes.
        return true
    }

    /**
     * Agent Runtime v1: ruta vertical mínima de Screen Understanding.
     *
     * Si el texto del usuario es una consulta sobre la pantalla actual
     * ("qué hay en pantalla", "resumí la pantalla", "dónde estoy", "qué puedo
     * hacer acá", "leeme lo importante"), tomamos un snapshot vía Accessibility
     * Service, lo pasamos al DeterministicScreenSummarizer y hablamos la
     * respuesta resultante.
     *
     * Reglas:
     *  - Si hay pending de conversación (slot fill, confirmación externa,
     *    consent), NO consumimos el input. El usuario puede estar mid-flow.
     *  - El servicio de Accesibilidad puede no estar activo. En ese caso el
     *    use case devuelve NeedsAccessibilityService con mensaje claro.
     *  - Pantalla bancaria / con campo password: el summarizer bloquea la
     *    lectura y devuelve solo advertencia, sin exponer contenido.
     *  - Nunca se envía el snapshot al backend ni a LLM. Nunca se persiste.
     */
    private fun handleRobotStatusDiagnosticIfNeeded(text: String): Boolean {
        if (agentConversationManager.hasPendingSlotRequest) return false
        if (pendingExternalConfirmation != null) return false
        if (pendingConsentAction != null) return false
        if (!RobotStatusDiagnosticPhrases.isDiagnosticCommand(text)) return false

        logVoiceCommandEvent(
            handler = "robot_status_diagnostic",
            result = RobotLoopLogResult.UNDERSTOOD,
            understood = true
        )
        val requestId = ++activeRequestId
        _state.update {
            it.copy(
                loading = true,
                listening = false,
                micListening = false,
                error = null
            )
        }
        _appState.value = AppState.PROCESSING

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                robotStatusDiagnosticUseCase.handle(text)
            }
            if (requestId != activeRequestId) return@launch
            if (shouldIgnoreMutedResponse(requestId)) return@launch
            when (result) {
                RobotStatusDiagnosticResult.NotADiagnosticCommand -> {
                    _state.update { it.copy(loading = false) }
                    _appState.value = AppState.IDLE
                }
                is RobotStatusDiagnosticResult.Spoken -> {
                    agentConversationManager.clear()
                    publishLocalMessage(
                        text = result.spokenText,
                        force = true,
                        appState = if (result.spokenText == RobotStatusDiagnosticUseCase.ACCESSIBILITY_OFF_TEXT) {
                            AppState.PERMISSION_REQUIRED
                        } else {
                            AppState.SPEAKING
                        }
                    )
                }
            }
        }
        return true
    }

    private fun handleScreenUnderstandingIfNeeded(text: String): Boolean {
        if (agentConversationManager.hasPendingSlotRequest) return false
        if (pendingExternalConfirmation != null) return false
        if (pendingConsentAction != null) return false
        if (ScreenQueryPhrases.classify(text) == null) return false

        logVoiceCommandEvent(
            handler = "screen_understanding",
            result = RobotLoopLogResult.UNDERSTOOD,
            understood = true
        )
        val requestId = ++activeRequestId
        _state.update {
            it.copy(
                loading = true,
                listening = false,
                micListening = false,
                error = null
            )
        }
        _appState.value = AppState.PROCESSING

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                screenUnderstandingUseCase.handle(text)
            }
            if (requestId != activeRequestId) return@launch
            if (shouldIgnoreMutedResponse(requestId)) return@launch
            when (result) {
                ScreenUnderstandingResult.NotAScreenCommand -> {
                    _state.update { it.copy(loading = false) }
                    _appState.value = AppState.IDLE
                }
                is ScreenUnderstandingResult.NeedsAccessibilityService -> {
                    agentConversationManager.clear()
                    publishStyledLocalMessage(
                        text = result.spokenText,
                        kind = RoutineResponseKind.SCREEN_SUMMARY,
                        force = true,
                        appState = AppState.PERMISSION_REQUIRED
                    )
                }
                is ScreenUnderstandingResult.Spoken -> {
                    agentConversationManager.clear()
                    publishStyledLocalMessage(
                        text = result.spokenText,
                        kind = RoutineResponseKind.SCREEN_SUMMARY,
                        force = true,
                        appState = AppState.SPEAKING
                    )
                }
            }
        }
        return true
    }

    /**
     * Agent Runtime: WhatsApp Guided Workflow v1.
     *
     * Si el texto es una consulta guiada de WhatsApp ("¿estoy en WhatsApp?",
     * "¿cómo mando una foto?", etc.), respondemos con una guía verbal usando
     * el snapshot estructurado actual. Nunca tocamos botones ni enviamos nada.
     *
     * Reglas (idénticas a Screen Understanding):
     *  - Si hay pending de conversación / confirmación / consent, NO consumimos.
     *    El usuario puede estar mid-flow del orquestador legacy.
     *  - Si el classifier devuelve NotAWhatsAppCommand, retornamos false y el
     *    flujo continúa hacia el orquestador legacy (WhatsApp seguro existente).
     *  - Si la pantalla parece WhatsApp pero no hay confianza, respondemos
     *    "no puedo confirmar..." sin afirmar nada.
     *  - Las plantillas de respuesta son fijas: nunca incluyen contenido del
     *    chat (mensajes privados). Verificado en WhatsAppGuidedWorkflowUseCaseTest.
     */
    private fun handleWhatsAppGuidedWorkflowIfNeeded(text: String): Boolean {
        if (agentConversationManager.hasPendingSlotRequest) return false
        if (pendingExternalConfirmation != null) return false
        if (pendingConsentAction != null) return false
        if (WhatsAppGuidedPhrases.classify(text) == null) return false

        logVoiceCommandEvent(
            handler = "whatsapp_guided",
            result = RobotLoopLogResult.UNDERSTOOD,
            understood = true
        )
        val requestId = ++activeRequestId
        _state.update {
            it.copy(
                loading = true,
                listening = false,
                micListening = false,
                error = null
            )
        }
        _appState.value = AppState.PROCESSING

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                whatsAppGuidedWorkflowUseCase.handle(text)
            }
            if (requestId != activeRequestId) return@launch
            if (shouldIgnoreMutedResponse(requestId)) return@launch
            when (result) {
                WhatsAppGuidedResponse.NotAWhatsAppCommand -> {
                    _state.update { it.copy(loading = false) }
                    _appState.value = AppState.IDLE
                }
                is WhatsAppGuidedResponse.NotInWhatsApp -> {
                    agentConversationManager.clear()
                    publishStyledLocalMessage(
                        text = result.spokenText,
                        kind = RoutineResponseKind.WHATSAPP_GUIDED,
                        force = true,
                        appState = AppState.SPEAKING
                    )
                }
                is WhatsAppGuidedResponse.StateNotConfident -> {
                    agentConversationManager.clear()
                    publishStyledLocalMessage(
                        text = result.spokenText,
                        kind = RoutineResponseKind.WHATSAPP_GUIDED,
                        force = true,
                        appState = AppState.SPEAKING
                    )
                }
                is WhatsAppGuidedResponse.Guidance -> {
                    agentConversationManager.clear()
                    publishStyledLocalMessage(
                        text = result.spokenText,
                        kind = RoutineResponseKind.WHATSAPP_GUIDED,
                        force = true,
                        appState = AppState.SPEAKING
                    )
                }
            }
        }
        return true
    }

    /**
     * Agent Runtime: WhatsApp Visible Chats Reader v1.
     *
     * Si el texto es "qué chats ves" / "leeme los chats" / etc., respondemos
     * con los nombres visibles de chats de WhatsApp (max 5). Nunca leemos
     * mensajes completos ni previews.
     *
     * Reglas (idénticas a Screen Understanding y WhatsApp Guided):
     *  - Si hay pending de conversación / confirmación / consent, NO consumimos.
     *  - Si el classifier devuelve NotAChatListCommand, retornamos false y el
     *    flujo continúa hacia el orquestador legacy.
     *  - Si el usuario está dentro de un chat, respondemos honestamente
     *    "Estás dentro de un chat. No leo mensajes completos sin que me lo pidas."
     *    sin enumerar ningún contenido.
     *  - Si no encontramos chats visibles con confianza, pedimos abrir la
     *    pantalla principal y reintentar.
     */
    private fun handleWhatsAppVisibleChatsIfNeeded(text: String): Boolean {
        if (agentConversationManager.hasPendingSlotRequest) return false
        if (pendingExternalConfirmation != null) return false
        if (pendingConsentAction != null) return false
        if (!WhatsAppChatListPhrases.isChatListCommand(text)) return false

        logVoiceCommandEvent(
            handler = "visible_chats",
            result = RobotLoopLogResult.UNDERSTOOD,
            understood = true
        )
        val requestId = ++activeRequestId
        _state.update {
            it.copy(
                loading = true,
                listening = false,
                micListening = false,
                error = null
            )
        }
        _appState.value = AppState.PROCESSING

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                whatsAppVisibleChatsReader.handle(text)
            }
            if (requestId != activeRequestId) return@launch
            if (shouldIgnoreMutedResponse(requestId)) return@launch
            when (result) {
                WhatsAppChatListResponse.NotAChatListCommand -> {
                    _state.update { it.copy(loading = false) }
                    _appState.value = AppState.IDLE
                }
                is WhatsAppChatListResponse.NotInWhatsApp -> {
                    agentConversationManager.clear()
                    publishStyledLocalMessage(
                        text = result.spokenText,
                        kind = RoutineResponseKind.GENERIC,
                        force = true,
                        appState = AppState.SPEAKING
                    )
                }
                is WhatsAppChatListResponse.StateNotConfident -> {
                    agentConversationManager.clear()
                    publishStyledLocalMessage(
                        text = result.spokenText,
                        kind = RoutineResponseKind.GENERIC,
                        force = true,
                        appState = AppState.SPEAKING
                    )
                }
                is WhatsAppChatListResponse.InsideChat -> {
                    agentConversationManager.clear()
                    publishStyledLocalMessage(
                        text = result.spokenText,
                        kind = RoutineResponseKind.VISIBLE_CHATS_INSIDE,
                        force = true,
                        appState = AppState.SPEAKING
                    )
                }
                is WhatsAppChatListResponse.Listed -> {
                    agentConversationManager.clear()
                    publishStyledLocalMessage(
                        text = result.spokenText,
                        kind = RoutineResponseKind.VISIBLE_CHATS_LIST,
                        force = true,
                        appState = AppState.SPEAKING
                    )
                }
                is WhatsAppChatListResponse.NoChatsVisible -> {
                    agentConversationManager.clear()
                    publishStyledLocalMessage(
                        text = result.spokenText,
                        kind = RoutineResponseKind.GENERIC,
                        force = true,
                        appState = AppState.SPEAKING
                    )
                }
            }
        }
        return true
    }

    /**
     * Agent Runtime: Human Routine Learning v1.
     *
     * Si el texto es un comando explícito de memoria/preferencia ("hablame
     * más corto", "recordá que Sofi es contacto frecuente", "olvidá mis
     * preferencias", etc.), lo ejecutamos contra HumanRoutineUseCase.
     *
     * Reglas:
     *  - Si hay pending de conversación / confirmación / consent, NO consumimos.
     *  - Si el classifier devuelve NotARoutineCommand, retornamos false y el
     *    flujo continúa hacia el orquestador legacy.
     *  - Si el policy bloquea por seguridad ("no puedo guardar eso porque...")
     *    hablamos el motivo seguro y consumimos el input — el usuario sabe
     *    que se intentó pero no se guardó.
     *  - El use case mantiene store in-memory; nada se persiste en disco en v1.
     */
    private fun handleHumanRoutineLearningIfNeeded(text: String): Boolean {
        if (agentConversationManager.hasPendingSlotRequest) return false
        if (pendingExternalConfirmation != null) return false
        if (pendingConsentAction != null) return false

        return when (val result = humanRoutineUseCase.handleVoice(text)) {
            HumanRoutineMemoryResult.NotARoutineCommand -> false
            is HumanRoutineMemoryResult.Saved -> {
                logVoiceCommandEvent(
                    handler = "human_routine",
                    result = RobotLoopLogResult.SAVED,
                    understood = true
                )
                agentConversationManager.clear()
                publishLocalMessage(
                    text = result.spokenAck,
                    force = true,
                    appState = AppState.SPEAKING
                )
                true
            }
            is HumanRoutineMemoryResult.Forgotten -> {
                logVoiceCommandEvent(
                    handler = "human_routine",
                    result = RobotLoopLogResult.FORGOTTEN,
                    understood = true
                )
                agentConversationManager.clear()
                publishLocalMessage(
                    text = result.spokenAck,
                    force = true,
                    appState = AppState.SPEAKING
                )
                true
            }
            is HumanRoutineMemoryResult.BlockedBySafety -> {
                logVoiceCommandEvent(
                    handler = "human_routine",
                    result = RobotLoopLogResult.BLOCKED_BY_SAFETY,
                    understood = true
                )
                agentConversationManager.clear()
                publishLocalMessage(
                    text = result.spokenText,
                    force = true,
                    appState = AppState.SPEAKING
                )
                true
            }
            is HumanRoutineMemoryResult.LearningDisabled -> {
                logVoiceCommandEvent(
                    handler = "human_routine",
                    result = RobotLoopLogResult.LEARNING_DISABLED,
                    understood = true
                )
                agentConversationManager.clear()
                publishLocalMessage(
                    text = result.spokenAck,
                    force = true,
                    appState = AppState.SPEAKING
                )
                true
            }
            is HumanRoutineMemoryResult.LearningEnabled -> {
                logVoiceCommandEvent(
                    handler = "human_routine",
                    result = RobotLoopLogResult.LEARNING_ENABLED,
                    understood = true
                )
                agentConversationManager.clear()
                publishLocalMessage(
                    text = result.spokenAck,
                    force = true,
                    appState = AppState.SPEAKING
                )
                true
            }
        }
    }

    private fun handleAgentConversationIfNeeded(text: String): Boolean {
        if (pendingConsentAction != null) return false

        val parsedIntent = agentIntentParser.parse(text)
        _state.update { it.copy(lastAgentIntent = parsedIntent.intent) }
        if (
            agentConversationManager.hasPendingSlotRequest &&
            parsedIntent.intent !in setOf(AgentIntent.UNKNOWN, AgentIntent.CANCEL, AgentIntent.CONFIRM)
        ) {
            agentConversationManager.clear()
            return false
        }

        val shouldStartWhatsAppGuidedInApp =
            parsedIntent.intent == AgentIntent.OPEN_WHATSAPP &&
                parsedIntent.missingSlots.contains(AgentSlotName.WHATSAPP_ACTION) &&
                !globalAssistantCapabilityProvider().canSafelyContinueOutsideApp

        val shouldUseAgentConversation =
            agentConversationManager.hasPendingSlotRequest ||
                shouldStartWhatsAppGuidedInApp ||
                (parsedIntent.intent == AgentIntent.COMPOSE_WHATSAPP_MESSAGE &&
                    parsedIntent.missingSlots.isNotEmpty()) ||
                (parsedIntent.intent == AgentIntent.CALL_CONTACT &&
                    parsedIntent.missingSlots.isNotEmpty()) ||
                (parsedIntent.intent == AgentIntent.OPEN_WHATSAPP_CHAT &&
                    parsedIntent.missingSlots.isNotEmpty()) ||
                (parsedIntent.intent in CONTACT_MEMORY_INTENTS &&
                    parsedIntent.missingSlots.isNotEmpty()) ||
                (parsedIntent.intent in MAPS_SLOT_INTENTS &&
                    parsedIntent.missingSlots.isNotEmpty())

        if (!shouldUseAgentConversation) return false

        logVoiceCommandEvent(
            handler = "agent_conversation",
            result = RobotLoopLogResult.UNDERSTOOD,
            understood = true
        )
        val outcome = agentConversationManager.handle(parsedIntent)
        val suggestedIntent = outcome.suggestedIntent
        if (!outcome.isError && suggestedIntent != null && shouldRouteSuggestedIntent(suggestedIntent.intent)) {
            val commandText = when (suggestedIntent.intent) {
                AgentIntent.COMPOSE_WHATSAPP_MESSAGE -> suggestedIntent.toLegacyComposeCommand()
                AgentIntent.CALL_CONTACT -> suggestedIntent.toLegacyCallCommand()
                AgentIntent.OPEN_WHATSAPP_CHAT -> suggestedIntent.toLegacyOpenWhatsAppChatCommand()
                AgentIntent.SAVE_CONTACT -> suggestedIntent.toLegacySaveContactCommand()
                AgentIntent.SAVE_CONTACT_PHONE -> suggestedIntent.toLegacySaveContactPhoneCommand()
                AgentIntent.DELETE_CONTACT -> suggestedIntent.toLegacyDeleteContactCommand()
                AgentIntent.NAVIGATE_TO_DESTINATION -> suggestedIntent.toLegacyNavigationCommand()
                AgentIntent.SAVE_LOCATION_ALIAS -> suggestedIntent.toLegacySaveLocationAliasCommand()
                AgentIntent.DELETE_LOCATION_ALIAS -> suggestedIntent.toLegacyDeleteLocationAliasCommand()
                else -> text
            }
            agentConversationManager.clear()
            val requestId = activeRequestId
            viewModelScope.launch {
                val orchestratorOutcome = orchestrator.process(
                    rawInput = commandText,
                    pendingConfirmation = pendingExternalConfirmation,
                    pendingConsent = pendingConsentAction,
                    appState = _appState.value
                )
                if (shouldDropAsyncResult(requestId, handler = "external_command")) return@launch
                applyOutcomeIfExternal(requestId, orchestratorOutcome)
            }
            return true
        }

        applyAgentOutcome(outcome)
        return true
    }

    private fun shouldRouteSuggestedIntent(intent: AgentIntent): Boolean = intent in setOf(
        AgentIntent.OPEN_WHATSAPP,
        AgentIntent.OPEN_WHATSAPP_CHAT,
        AgentIntent.OPEN_PHONE,
        AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
        AgentIntent.CALL_CONTACT,
        AgentIntent.READ_VISIBLE_SCREEN,
        AgentIntent.REMEMBER_MEMORY,
        AgentIntent.LIST_MEMORY,
        AgentIntent.CLEAR_MEMORY,
        AgentIntent.SAVE_CONTACT,
        AgentIntent.SAVE_CONTACT_PHONE,
        AgentIntent.LIST_CONTACTS,
        AgentIntent.DELETE_CONTACT,
        AgentIntent.OPEN_MAPS,
        AgentIntent.GET_CURRENT_LOCATION,
        AgentIntent.NAVIGATE_TO_DESTINATION,
        AgentIntent.SAVE_LOCATION_ALIAS,
        AgentIntent.LIST_LOCATION_ALIASES,
        AgentIntent.DELETE_LOCATION_ALIAS,
        AgentIntent.CONFIRM,
        AgentIntent.CANCEL
    )

    private fun ParsedAgentIntent.toLegacyComposeCommand(): String {
        val contactName = slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
        val messageText = slotValue(AgentSlotName.MESSAGE_TEXT).orEmpty()
        return "mandale a $contactName: $messageText"
    }

    private fun ParsedAgentIntent.toLegacyCallCommand(): String {
        val contactName = slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
        return "llama a $contactName"
    }

    private fun ParsedAgentIntent.toLegacyOpenWhatsAppChatCommand(): String {
        val contactName = slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
        return "abrí el chat de $contactName"
    }

    private fun ParsedAgentIntent.toLegacySaveContactCommand(): String {
        val contactName = slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
        val contactType = if (slotValue(AgentSlotName.CONTACT_TYPE) == LocalIntentParser.CONTACT_TYPE_EMERGENCY) {
            "contacto de emergencia"
        } else {
            "contacto de confianza"
        }
        return "recordá que $contactName es $contactType"
    }

    private fun ParsedAgentIntent.toLegacySaveContactPhoneCommand(): String {
        val contactName = slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
        val phoneNumber = slotValue(AgentSlotName.PHONE_NUMBER).orEmpty()
        return "el número de $contactName es $phoneNumber"
    }

    private fun ParsedAgentIntent.toLegacyDeleteContactCommand(): String {
        val contactName = slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
        return "olvidá el contacto $contactName"
    }

    private fun ParsedAgentIntent.toLegacyNavigationCommand(): String {
        val destination = slotValue(AgentSlotName.DESTINATION).orEmpty()
        return "navegar a $destination"
    }

    private fun ParsedAgentIntent.toLegacySaveLocationAliasCommand(): String {
        val alias = slotValue(AgentSlotName.LOCATION_ALIAS).orEmpty()
        return "guardá esta ubicación como $alias"
    }

    private fun ParsedAgentIntent.toLegacyDeleteLocationAliasCommand(): String {
        val alias = slotValue(AgentSlotName.LOCATION_ALIAS).orEmpty()
        return "olvidá la ubicación $alias"
    }

    private fun applyAgentOutcome(outcome: AgentOutcome) {
        val appState = outcome.targetState.toAppState()
        val agentSubState = outcome.targetState.takeIf { isLiveAgentState(it) }
        val pendingState = if (isWhatsAppWaitingState(outcome.targetState)) {
            RobotPendingState.WHATSAPP
        } else if (appState == AppState.WAITING_CONFIRMATION) {
            RobotPendingState.CONFIRMATION
        } else {
            RobotPendingState.NONE
        }
        if (outcome.isError) {
            shortTermContext = shortTermContext.recordFailure(
                reason = if (pendingState == RobotPendingState.WHATSAPP) {
                    RobotFailureReason.WAITING_WHATSAPP
                } else {
                    RobotFailureReason.LOW_CONFIDENCE
                },
                kind = RobotRecognizedKind.FAILURE,
                activeHandler = RobotActiveHandler.AGENT_CONVERSATION,
                externalApp = if (pendingState == RobotPendingState.WHATSAPP) {
                    RobotExternalApp.WHATSAPP
                } else {
                    currentRepairExternalApp()
                },
                pendingState = pendingState
            )
        } else {
            recordShortTermSuccess(
                activeHandler = RobotActiveHandler.AGENT_CONVERSATION,
                externalApp = if (pendingState == RobotPendingState.WHATSAPP) {
                    RobotExternalApp.WHATSAPP
                } else {
                    currentRepairExternalApp()
                },
                pendingState = pendingState
            )
        }
        recordVoiceCommandToSpokenTextIfNeeded()
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                spokenText = outcome.spokenText,
                agentState = agentSubState,
                decisionSource = "agent",
                lastDecision = "AGENT_${outcome.targetState.name}",
                pendingDebug = pendingDebugLabel(),
                error = if (outcome.isError) outcome.spokenText else null
            )
        }
        sessionMemory.rememberSpokenResponse(outcome.spokenText)
        _appState.value = appState
        if (outcome.spokenText.isNotBlank()) {
            emitSpeechEvent(outcome.spokenText, force = outcome.isError)
        } else if (outcome.shouldListenAgain && agentSubState != null) {
            _state.update { it.copy(voiceListenRequestId = it.voiceListenRequestId + 1L) }
        }
    }

    private fun isAffirmativeNoise(text: String): Boolean {
        return VoicePhraseNormalizer.isAffirmativeNoise(text)
    }

    private fun pendingDebugLabel(): String {
        pendingVoiceCorrection?.let { pending ->
            return "VOICE_CORRECTION_${pending.correction.targetIntent.name}"
        }
        pendingExternalConfirmation?.let { pending ->
            return pending.command.type.name
        }
        pendingConsentAction?.let { pending ->
            return pending.type.name
        }
        return if (agentConversationManager.hasPendingSlotRequest) {
            agentConversationManager.currentState.name
        } else {
            ""
        }
    }

    private fun repairFailureText(
        reason: RobotFailureReason,
        kind: RobotRecognizedKind = RobotRecognizedKind.FAILURE,
        suggestedIntent: RepairSuggestedIntent = RepairSuggestedIntent.NONE,
        activeHandler: RobotActiveHandler = RobotActiveHandler.NONE,
        forcedPendingState: RobotPendingState? = null,
        askedConfirmation: Boolean = false
    ): String {
        val externalApp = currentRepairExternalApp()
        val pendingState = forcedPendingState ?: currentRepairPendingState()
        shortTermContext = shortTermContext.recordFailure(
            reason = reason,
            kind = kind,
            suggestedIntent = suggestedIntent,
            activeHandler = activeHandler,
            externalApp = externalApp,
            pendingState = pendingState,
            askedConfirmation = askedConfirmation
        )
        return ConversationalRepair.response(
            ConversationalRepairRequest(
                reason = reason,
                context = shortTermContext,
                robotEnabled = _state.value.robotEnabled,
                externalApp = externalApp,
                pendingState = pendingState,
                suggestedIntent = suggestedIntent
            )
        ).spokenText
    }

    private fun handleGlobalCommandDuringVoiceCorrection(
        text: String,
        imageBase64: String?
    ): Boolean {
        if (imageBase64 != null) return false
        return when (pendingVoiceCorrectionGlobalAction(text)) {
            PendingVoiceCorrectionGlobalAction.NONE -> false
            PendingVoiceCorrectionGlobalAction.RESET_FLOW -> {
                pendingVoiceCorrection = null
                resetFlow()
                true
            }
            PendingVoiceCorrectionGlobalAction.CANCEL -> {
                logVoiceCommandEvent(
                    handler = "voice_correction_cancel",
                    result = RobotLoopLogResult.NO_CORRECTION,
                    understood = true,
                    consumed = true,
                    reasonCode = "user_cancelled"
                )
                pendingVoiceCorrection = null
                shortTermContext = shortTermContext.reset()
                _state.update { it.copy(pendingDebug = pendingDebugLabel()) }
                publishLocalMessage(
                    text = ConversationalRepair.CONFIRMATION_CANCELLED,
                    force = true,
                    appState = AppState.IDLE
                )
                true
            }
            PendingVoiceCorrectionGlobalAction.STOP_SPEAKING -> {
                logVoiceCommandEvent(
                    handler = "stop_speech",
                    result = RobotLoopLogResult.UNDERSTOOD,
                    understood = true,
                    consumed = true,
                    reasonCode = "stop"
                )
                clearVoiceCommandStarted()
                recordShortTermSuccess(RobotActiveHandler.STOP_SPEAKING)
                onStopSpeechRequested()
                true
            }
            PendingVoiceCorrectionGlobalAction.REPEAT_LAST -> {
                logVoiceCommandEvent(
                    handler = "repeat_last",
                    result = RobotLoopLogResult.UNDERSTOOD,
                    understood = true,
                    consumed = true
                )
                recordShortTermSuccess(RobotActiveHandler.REPEAT_LAST)
                repeatLastResponse()
                true
            }
            PendingVoiceCorrectionGlobalAction.EMERGENCY -> {
                pendingVoiceCorrection = null
                handleEmergencyModeIfNeeded(text)
            }
            PendingVoiceCorrectionGlobalAction.HELP -> {
                logVoiceCommandEvent(
                    handler = "help",
                    result = RobotLoopLogResult.UNDERSTOOD,
                    understood = true,
                    consumed = true
                )
                recordShortTermSuccess(RobotActiveHandler.HELP)
                requestHelp()
                true
            }
            PendingVoiceCorrectionGlobalAction.PAUSE_ROBOT -> {
                logVoiceCommandEvent(
                    handler = "robot_session",
                    result = RobotLoopLogResult.UNDERSTOOD,
                    understood = true,
                    consumed = true,
                    reasonCode = "disable"
                )
                pauseRobotSession()
                recordShortTermSuccess(RobotActiveHandler.ROBOT_SESSION)
                publishLocalMessage("Robot pausado.", force = true, appState = AppState.IDLE)
                true
            }
            PendingVoiceCorrectionGlobalAction.ENABLE_ROBOT -> {
                logVoiceCommandEvent(
                    handler = "robot_session",
                    result = RobotLoopLogResult.UNDERSTOOD,
                    understood = true,
                    consumed = true,
                    reasonCode = "enable"
                )
                enableRobotSession(hasMicrophonePermission = _state.value.microphonePermissionGranted)
                recordShortTermSuccess(RobotActiveHandler.ROBOT_SESSION)
                publishLocalMessage("Robot encendido. Te escucho.", force = true, appState = AppState.SPEAKING)
                true
            }
        }
    }

    private fun recordShortTermSuccess(
        activeHandler: RobotActiveHandler,
        suggestedIntent: RepairSuggestedIntent = RepairSuggestedIntent.NONE,
        externalApp: RobotExternalApp = currentRepairExternalApp(),
        pendingState: RobotPendingState = RobotPendingState.NONE
    ) {
        shortTermContext = shortTermContext.recordSuccess(
            activeHandler = activeHandler,
            suggestedIntent = suggestedIntent,
            externalApp = externalApp,
            pendingState = pendingState
        )
    }

    private fun currentRepairExternalApp(): RobotExternalApp =
        when {
            _state.value.externalAppName.equals("WhatsApp", ignoreCase = true) -> RobotExternalApp.WHATSAPP
            isWhatsAppWaitingState(_state.value.agentState) -> RobotExternalApp.WHATSAPP
            _appState.value == AppState.WAITING_WHATSAPP_ACTION ||
                _appState.value == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> RobotExternalApp.WHATSAPP
            _state.value.externalAppName != "None" -> RobotExternalApp.OTHER
            else -> RobotExternalApp.NONE
        }

    private fun currentRepairPendingState(): RobotPendingState =
        when {
            pendingVoiceCorrection != null -> RobotPendingState.CONFIRMATION
            pendingExternalConfirmation != null || pendingConsentAction != null -> RobotPendingState.CONFIRMATION
            _appState.value == AppState.WAITING_CONFIRMATION -> RobotPendingState.CONFIRMATION
            isWhatsAppWaitingState(_state.value.agentState) ||
                _appState.value == AppState.WAITING_WHATSAPP_ACTION ||
                _appState.value == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> RobotPendingState.WHATSAPP
            else -> RobotPendingState.NONE
        }

    private fun isLiveAgentState(state: AgentState): Boolean = state in setOf(
        AgentState.WAITING_CONFIRMATION,
        AgentState.WAITING_CONTACT,
        AgentState.WAITING_MESSAGE,
        AgentState.WAITING_PHONE_NUMBER,
        AgentState.WAITING_DESTINATION,
        AgentState.WAITING_LOCATION_ALIAS,
        AgentState.WAITING_WHATSAPP_ACTION,
        AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE,
        AgentState.WAITING_TIME,
        AgentState.WAITING_FREQUENCY
    )

    private fun applyOutcomeIfExternal(requestId: Long, outcome: OrchestratorOutcome): Boolean {
        if (shouldDropAsyncResult(requestId, handler = "external_command")) return true
        if (outcome.newPending != null) {
            pendingExternalConfirmation = outcome.newPending
            sessionMemory.rememberPendingAction(outcome.newPending)
        }
        if (outcome.clearsPending) {
            pendingExternalConfirmation = null
            sessionMemory.clearPendingAction()
        }
        if (outcome.newPendingConsent != null) {
            pendingConsentAction = outcome.newPendingConsent
        }
        if (outcome.clearsPendingConsent) {
            pendingConsentAction = null
            sessionMemory.clearPendingAction()
        }

        val handoff = outcome.externalEvent as? ExternalActionEvent.ExternalAppHandoff
        val capability = globalAssistantCapabilityProvider()
        if (outcome.isError) {
            shortTermContext = shortTermContext.recordFailure(
                reason = RobotFailureReason.SAFE_AI_UNAVAILABLE,
                kind = RobotRecognizedKind.FAILURE,
                activeHandler = RobotActiveHandler.EXTERNAL_COMMAND,
                externalApp = currentRepairExternalApp(),
                pendingState = currentRepairPendingState()
            )
        } else {
            recordShortTermSuccess(
                activeHandler = RobotActiveHandler.EXTERNAL_COMMAND,
                externalApp = if (handoff?.externalAppName.equals("WhatsApp", ignoreCase = true)) {
                    RobotExternalApp.WHATSAPP
                } else {
                    currentRepairExternalApp()
                },
                pendingState = when {
                    outcome.newPending != null || outcome.newPendingConsent != null -> RobotPendingState.CONFIRMATION
                    isWhatsAppWaitingState(outcome.agentState) -> RobotPendingState.WHATSAPP
                    else -> RobotPendingState.NONE
                }
            )
        }
        recordVoiceCommandToSpokenTextIfNeeded()
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                spokenText = outcome.spokenText,
                externalAppHandoff = handoff,
                // El orquestador ya no es flujo conversacional del agente — limpiamos el
                // sub-estado para que la UI no muestre "Esperando confirmación" colgado
                // cuando el outcome real es PROCESSING/SPEAKING.
                agentState = outcome.agentState,
                decisionSource = "external",
                lastDecision = outcome.decisionDebugLabel,
                pendingDebug = pendingDebugLabel(),
                globalModeOn = handoff != null,
                micContinuationReady = capability.microphoneContinuationReady,
                overlayReady = capability.overlayReady,
                notificationReady = capability.notificationReady,
                fallbackReturnReady = capability.fallbackReturnReady,
                externalAppName = handoff?.externalAppName ?: "None",
                ttlRemainingMillis = if (handoff != null) 60_000L else 0L,
                error = if (outcome.isError) outcome.spokenText else null
            )
        }
        sessionMemory.rememberSpokenResponse(outcome.spokenText)
        _appState.value = outcome.targetState
        if (handoff == null) {
            emitSpeechEvent(outcome.spokenText, force = outcome.forceSpeak)
        }
        outcome.externalEvent?.let {
            activeExternalActionRequestId = requestId
            _externalActionEvents.tryEmit(it)
        }
        return true
    }

    private fun clearExternalHandoff() {
        if (_state.value.externalAppHandoff == null && _appState.value != AppState.EXTERNAL_APP_HANDOFF) return
        _state.update {
            it.copy(
                externalAppHandoff = null,
                globalModeOn = false,
                externalAppName = "None",
                ttlRemainingMillis = 0L
            )
        }
        if (_appState.value == AppState.EXTERNAL_APP_HANDOFF) {
            _appState.value = AppState.IDLE
        }
    }

    /**
     * Reconocimiento sincrónico para decidir si el orquestador consumirá el input
     * antes de fallback al backend. Mantiene el comportamiento original del MVP:
     * solo comandos externos seguros se desvían; el resto sigue al backend.
     */
    private val orchestratorRouter = CommandRouter()

    private fun markVoiceCommandStarted() {
        activeVoiceCommandStartNanos = System.nanoTime()
    }

    private fun clearVoiceCommandStarted() {
        activeVoiceCommandStartNanos = 0L
    }

    private fun recordVoiceCommandToSpokenTextIfNeeded() {
        val start = activeVoiceCommandStartNanos
        if (start <= 0L) return
        activeVoiceCommandStartNanos = 0L
        RobotLoopInstrumentation.recordElapsedNanos(
            metric = RobotLoopMetric.VOICE_COMMAND_TO_SPOKEN_TEXT,
            elapsedNanos = System.nanoTime() - start
        )
    }

    private fun shouldIgnoreMutedResponse(requestId: Long): Boolean =
        requestId <= mutedThroughRequestId

    private fun isRequestMutedOrStale(requestId: Long): Boolean =
        isRequestMutedOrStale(
            requestId = requestId,
            activeRequestId = activeRequestId,
            mutedThroughRequestId = mutedThroughRequestId
        )

    private fun shouldDropAsyncResult(requestId: Long, handler: String): Boolean {
        val reason = asyncResultDropReason(
            requestId = requestId,
            activeRequestId = activeRequestId,
            mutedThroughRequestId = mutedThroughRequestId,
            robotEnabled = _state.value.robotEnabled
        )
        if (reason == AsyncResultDropReason.NONE) return false
        logDroppedAsyncResult(handler = handler, requestId = requestId, reason = reason)
        return true
    }

    private fun logDroppedAsyncResult(
        handler: String,
        requestId: Long,
        reason: AsyncResultDropReason
    ) {
        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.VOICE_COMMAND,
                result = RobotLoopLogResult.DROPPED,
                requestId = requestId,
                commandRedacted = true,
                handler = "stale_async",
                consumed = true,
                reasonCode = reason.logCode,
                targetIntent = handler
            )
        )
    }

    private fun logVoiceCommandEvent(
        handler: String,
        result: RobotLoopLogResult,
        understood: Boolean?,
        consumed: Boolean? = null,
        reasonCode: String? = null,
        stage: RobotLoopLogStage = RobotLoopLogStage.VOICE_COMMAND
    ) {
        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = stage,
                result = result,
                requestId = activeRequestId,
                durationMillis = currentVoiceCommandDurationMillis(),
                robotState = safeRobotStateLabel(_appState.value, _state.value.agentState),
                commandRedacted = true,
                handler = handler,
                understood = understood,
                consumed = consumed,
                reasonCode = reasonCode,
                appState = _appState.value.name
            )
        )
    }

    private fun logVoiceCorrection(
        correction: VoiceCommandCorrectionResult,
        result: RobotLoopLogResult,
        consumed: Boolean,
        reasonCode: String? = null
    ) {
        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.ROUTING_AUDIT,
                result = result,
                requestId = activeRequestId,
                durationMillis = currentVoiceCommandDurationMillis(),
                robotState = safeRobotStateLabel(_appState.value, _state.value.agentState),
                commandRedacted = true,
                handler = "voice_correction",
                understood = result == RobotLoopLogResult.CORRECTED ||
                    result == RobotLoopLogResult.NEEDS_CONFIRMATION,
                consumed = consumed,
                reasonCode = reasonCode ?: correction.correctionType.name.lowercase(Locale.US),
                appState = _appState.value.name,
                targetIntent = correction.targetIntent.name,
                confidence = correction.confidence.name
            )
        )
    }

    private fun logRoutingDecision(
        handler: String,
        consumed: Boolean,
        reasonCode: String = if (consumed) "matched" else "not_matched"
    ) {
        logVoiceCommandEvent(
            handler = handler,
            result = if (consumed) RobotLoopLogResult.UNDERSTOOD else RobotLoopLogResult.NOT_A_COMMAND,
            understood = consumed,
            consumed = consumed,
            reasonCode = reasonCode,
            stage = RobotLoopLogStage.ROUTING_AUDIT
        )
    }

    private inline fun routeCommand(
        handler: String,
        reasonWhenNotConsumed: String = "not_matched",
        block: () -> Boolean
    ): Boolean {
        val consumed = block()
        logRoutingDecision(
            handler = handler,
            consumed = consumed,
            reasonCode = if (consumed) "matched" else reasonWhenNotConsumed
        )
        return consumed
    }

    private fun logVoiceLoopState(
        action: String,
        state: RobotSessionState,
        micActive: Boolean
    ) {
        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.VOICE_LOOP,
                result = RobotLoopLogResult.OK,
                requestId = activeRequestId,
                robotState = state.name,
                handler = "VoiceLoop",
                reasonCode = action,
                appState = _appState.value.name,
                micActive = micActive
            )
        )
    }

    private fun currentVoiceCommandDurationMillis(): Long? {
        val start = activeVoiceCommandStartNanos
        if (start <= 0L) return null
        return ((System.nanoTime() - start).coerceAtLeast(0L) / 1_000_000L)
    }

    private fun emitSpeechEvent(text: String, force: Boolean = false) {
        _state.update { it.copy(robotSessionState = RobotSessionState.SPEAKING) }
        logVoiceLoopState(action = "speaking", state = RobotSessionState.SPEAKING, micActive = false)
        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.TTS_SPOKEN_EVENT,
                result = RobotLoopLogResult.SPOKEN,
                forceSpeak = force
            )
        )
        _speechEvents.tryEmit(SpeechEvent(text, force = force))
    }

    private fun publishLocalMessage(
        text: String,
        force: Boolean = false,
        appState: AppState = AppState.SPEAKING
    ) {
        recordVoiceCommandToSpokenTextIfNeeded()
        sessionMemory.rememberSpokenResponse(text)
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                micListening = false,
                robotSessionState = robotSessionStateForAppState(appState, it.robotEnabled),
                spokenText = text,
                error = if (appState == AppState.ERROR || appState == AppState.PERMISSION_REQUIRED) text else null
            )
        }
        _appState.value = appState
        emitSpeechEvent(text, force = force)
    }

    private fun robotSessionStateForAppState(appState: AppState, robotEnabled: Boolean): RobotSessionState =
        when (appState) {
            AppState.SPEAKING -> RobotSessionState.SPEAKING
            AppState.PROCESSING -> RobotSessionState.PROCESSING
            AppState.LISTENING -> RobotSessionState.LISTENING
            AppState.WAITING_WHATSAPP_ACTION,
            AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> RobotSessionState.WAITING_WHATSAPP
            AppState.WAITING_CONFIRMATION,
            AppState.WAITING_CONTACT,
            AppState.WAITING_MESSAGE -> RobotSessionState.WAITING_CONFIRMATION
            AppState.ERROR,
            AppState.PERMISSION_REQUIRED -> RobotSessionState.ERROR_RECOVERABLE
            else -> if (robotEnabled) RobotSessionState.READY else RobotSessionState.OFF
        }

    /**
     * Publica un mensaje pasando primero por [RoutinePreferenceApplier], que
     * acorta o aclara la respuesta según las preferencias humanas activas.
     * Las frases de seguridad críticas se preservan por contrato del adapter.
     *
     * Solo se aplica a las rutas nuevas (Screen Understanding, WhatsApp Guided,
     * WhatsApp Visible Chats). El flujo legacy (orchestrator, REPEAT_LAST,
     * callbacks, etc.) sigue usando [publishLocalMessage] sin transformación
     * para no alterar comportamiento estable.
     */
    private fun publishStyledLocalMessage(
        text: String,
        kind: RoutineResponseKind,
        force: Boolean = false,
        appState: AppState = AppState.SPEAKING
    ) {
        val styled = RoutinePreferenceApplier.apply(
            text = text,
            kind = kind,
            style = humanResponseStyleProvider.current()
        )
        publishLocalMessage(text = styled, force = force, appState = appState)
    }

    private fun handlePersonalAgentDecision(
        decision: PersonalAgentDecision,
        requestId: Long
    ): Boolean {
        if (shouldDropAsyncResult(requestId, handler = "personal_agent")) return true
        when (decision) {
            is PersonalAgentDecision.DoNothing -> return false

            is PersonalAgentDecision.UseLlmFallback -> {
                _state.update {
                    it.copy(
                        decisionSource = "llm",
                        llmEnabled = true,
                        llmReason = decision.reason,
                        llmFallback = decision.response?.userFacingQuestion.orEmpty(),
                        lastConfidence = decision.response?.confidence ?: 0f,
                        lastDecision = decision.debugLabel
                    )
                }
                // Safe AI Fallback v1: nunca exponer detalles tecnicos ("LLM disabled",
                // "low confidence", "proxy", "no estoy usando la IA"). Si el interpreter
                // devolvio una pregunta humana segura, usarla; si no, degradar a copy
                // contextual humano.
                val safeQuestion = decision.response?.userFacingQuestion
                    ?.takeIf { it.isNotBlank() && !SafeAiFallbackCopy.looksLikeAiDebugCopy(it) }
                val externalAppLabel = _state.value.externalAppName.takeIf { it != "None" }
                val spoken = safeQuestion ?: SafeAiFallbackCopy.contextual(
                    appState = _appState.value,
                    agentState = _state.value.agentState,
                    externalApp = externalAppLabel
                )
                if (spoken.isBlank()) return false
                publishLocalMessage(spoken, force = false, appState = AppState.SPEAKING)
                return true
            }

            is PersonalAgentDecision.AskQuestion -> {
                _state.update {
                    it.copy(
                        decisionSource = "llm",
                        llmEnabled = true,
                        llmReason = decision.debugLabel,
                        lastDecision = decision.debugLabel
                    )
                }
                publishLocalMessage(decision.spokenText, force = true, appState = decision.targetState.toAppState())
                return true
            }

            is PersonalAgentDecision.SuggestAction -> {
                _state.update {
                    it.copy(
                        decisionSource = "llm",
                        llmEnabled = true,
                        llmReason = decision.debugLabel,
                        suggestionDebug = decision.suggestion.text,
                        lastDecision = decision.debugLabel
                    )
                }
                publishLocalMessage(decision.suggestion.text, force = true, appState = AppState.SPEAKING)
                return true
            }

            is PersonalAgentDecision.ComposeHumanMessage -> {
                val pending = buildWhatsAppComposePendingFromPersonalDecision(
                    decision = decision,
                    nowMillis = System.currentTimeMillis()
                )
                if (pending != null) {
                    pendingExternalConfirmation = pending
                    sessionMemory.rememberPendingAction(pending)
                    sessionMemory.rememberContactAndMessage(
                        decision.contactName,
                        decision.composition.proposedMessage
                    )
                }
                val appState = when {
                    pending != null -> AppState.WAITING_CONFIRMATION
                    decision.composition.blockedReason != null -> AppState.ERROR
                    decision.composition.requiresConfirmation -> AppState.ERROR
                    else -> AppState.SPEAKING
                }
                val spokenText = when {
                    pending != null -> decision.composition.spokenProposal
                    decision.composition.blockedReason != null -> decision.composition.spokenProposal
                    decision.composition.requiresConfirmation -> "No pude preparar una confirmación segura. Probá de nuevo."
                    else -> decision.composition.spokenProposal
                }
                _state.update {
                    it.copy(
                        decisionSource = if (decision.debugLabel.startsWith("LLM")) "llm" else "local",
                        llmEnabled = true,
                        llmReason = decision.debugLabel,
                        messageDebug = decision.composition.proposedMessage,
                        contactDebug = decision.contactName,
                        lastDecision = decision.debugLabel,
                        pendingDebug = pendingDebugLabel()
                    )
                }
                publishLocalMessage(
                    text = spokenText,
                    force = true,
                    appState = appState
                )
                return true
            }

            is PersonalAgentDecision.RequestConfirmation -> {
                _state.update {
                    it.copy(
                        decisionSource = "llm",
                        llmEnabled = true,
                        llmReason = decision.debugLabel,
                        lastDecision = decision.debugLabel
                    )
                }
                publishLocalMessage(decision.spokenText, force = true, appState = AppState.WAITING_CONFIRMATION)
                return true
            }

            is PersonalAgentDecision.ExecuteSafeAction -> {
                _state.update {
                    it.copy(
                        decisionSource = "llm",
                        llmEnabled = true,
                        llmReason = decision.debugLabel,
                        lastDecision = decision.debugLabel
                    )
                }
                decision.externalEvent?.let {
                    activeExternalActionRequestId = requestId
                    _externalActionEvents.tryEmit(it)
                }
                publishLocalMessage(decision.spokenText, force = true, appState = AppState.EXTERNAL_APP_HANDOFF)
                return true
            }

            is PersonalAgentDecision.RejectUnsafe -> {
                _state.update {
                    it.copy(
                        decisionSource = "llm",
                        llmEnabled = true,
                        llmReason = decision.debugLabel,
                        lastDecision = decision.debugLabel
                    )
                }
                publishLocalMessage(decision.spokenText, force = true, appState = AppState.ERROR)
                return true
            }

            is PersonalAgentDecision.RetryListening -> {
                _state.update {
                    it.copy(
                        decisionSource = "llm",
                        llmEnabled = true,
                        llmReason = decision.debugLabel,
                        lastDecision = decision.debugLabel
                    )
                }
                publishLocalMessage(decision.spokenText, force = false, appState = AppState.ERROR)
                _state.update { it.copy(voiceListenRequestId = it.voiceListenRequestId + 1L) }
                return true
            }

            is PersonalAgentDecision.Cancel -> {
                _state.update {
                    it.copy(
                        decisionSource = "llm",
                        llmEnabled = true,
                        llmReason = decision.debugLabel,
                        lastDecision = decision.debugLabel
                    )
                }
                pendingVoiceCorrection = null
                pendingExternalConfirmation = null
                pendingConsentAction = null
                sessionMemory.clearConversationContext()
                publishLocalMessage(decision.spokenText, force = true, appState = AppState.IDLE)
                return true
            }
        }
    }

    private fun repeatLastResponse() {
        val message = repeatedResponseText(sessionMemory.snapshot().lastSpokenResponse)
        publishLocalMessage(message, force = true, appState = _appState.value)
    }

    private fun returnToHome() {
        pendingVoiceCorrection = null
        pendingExternalConfirmation = null
        pendingConsentAction = null
        shortTermContext = shortTermContext.reset()
        agentConversationManager.clear()
        sessionMemory.clearConversationContext()
        publishLocalMessage("Volví al inicio. Te escucho.", force = true, appState = AppState.IDLE)
    }

    private fun localFallback(command: AppCommand, text: String): AssistResponse {
        val spokenText = when (command.type) {
            AppCommandType.EMERGENCY_HELP ->
                emergencyPolicy.safeOfferText()
            AppCommandType.READ_TEXT ->
                "Para leer texto, usá el botón Leer texto. Funciona con OCR local y no guarda imágenes."
            else ->
                // Mensaje corto y honesto. Antes decía "No pude conectar. Entendí: $text..."
                // y dejaba pegado el "Entendí: sí sí" cuando el usuario decía "sí" sin
                // pending. Ahora no exponemos el detalle interno del backend.
                "No entendí. Probá de nuevo."
        }

        return AssistResponse(
            spokenText = spokenText,
            shortText = "Respuesta local.",
            confidence = ConfidenceLevel.LOW,
            category = if (command.type == AppCommandType.EMERGENCY_HELP) {
                ResponseCategory.EMERGENCY
            } else {
                ResponseCategory.SYSTEM
            }
        )
    }

    private fun riskAwareOcrText(text: String, warnings: List<RiskWarning>): String {
        if (warnings.isEmpty()) {
            return "El texto dice: ${PrivacyGuard.sanitizeForSpeech(redactForSpeech(text))}"
        }

        val warningText = "Antes de responder, te aviso: este texto puede ser sensible. " +
            warnings.joinToString(" ") { it.spokenText }
        if (!PrivacyGuard.canReadAloud(text, warnings)) {
            return warningText.trim()
        }

        return "$warningText El texto dice: ${PrivacyGuard.sanitizeForSpeech(redactForSpeech(text))}".trim()
    }

    private fun redactForSpeech(text: String): String =
        PrivacyGuard.redactCardLikeNumbers(
            PrivacyGuard.redactVerificationCodes(
                PrivacyGuard.redactPasswords(text)
            )
        )

    private fun CommandResult.spokenText(): String {
        return when (this) {
            is CommandResult.Success -> spokenText
            is CommandResult.NeedsConfirmation -> spokenText
            is CommandResult.Failed -> spokenText
            is CommandResult.NotSupported -> spokenText
        }
    }

    companion object {
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(application) as T
        }
    }
}

private fun createPersonalAgentInterpreter(): LlmAgentInterpreter {
    val config = LlmAgentClientConfig.fromBuildConfig(BuildConfig.ASSISTANT_BASE_URL)
    return if (config.isConfigured()) {
        OpenAiProxyAgentInterpreter(config)
    } else {
        DisabledLlmAgentInterpreter()
    }
}

internal fun shouldUsePersonalAgentForHumanMessageDraft(
    text: String,
    parsedIntent: ParsedAgentIntent
): Boolean {
    if (parsedIntent.intent != AgentIntent.COMPOSE_WHATSAPP_MESSAGE) return false
    val normalized = VoicePhraseNormalizer.normalizeForParser(text).lowercase()
    return listOf(
        "decilo bien",
        "decirlo bien",
        "decilo mejor",
        "mas amable",
        "más amable",
        "mas calido",
        "más cálido",
        "calido",
        "cálido",
        "cariñoso",
        "formal",
        "profesional",
        "con tono",
        "redact",
        "propon"
    ).any { normalized.contains(it) }
}

internal fun buildWhatsAppComposePendingFromPersonalDecision(
    decision: PersonalAgentDecision.ComposeHumanMessage,
    nowMillis: Long
): PendingConfirmation? {
    val contactName = decision.contactName.trim()
    val messageText = decision.composition.proposedMessage.trim()
    if (contactName.isBlank()) return null
    if (messageText.isBlank()) return null
    if (!decision.composition.requiresConfirmation) return null
    if (decision.composition.shouldSendAutomatically) return null
    if (decision.composition.blockedReason != null) return null
    if (!PrivacyGuard.isSafeMessagePayload(messageText)) return null

    return PendingConfirmation(
        id = "personal-compose-confirmation-$nowMillis",
        command = ExternalCommand(
            type = ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE,
            rawText = "personal_agent_compose_message",
            normalizedText = "personal_agent_compose_message",
            contactName = contactName,
            messageText = messageText,
            confidence = CommandConfidence.HIGH
        ),
        spokenText = decision.composition.spokenProposal,
        createdAtMillis = nowMillis,
        expiresAtMillis = nowMillis + PERSONAL_AGENT_PENDING_TTL_MILLIS
    )
}

internal fun strictConfirmationReminderText(): String =
    "Para evitar errores, necesito que digas exactamente: confirmar."

internal fun isRepeatLastResponseCommand(text: String): Boolean =
    controlCommandKey(text) in setOf(
        "repeti",
        "repetir",
        "repetilo",
        "que dijiste",
        "que me dijiste",
        "que acabas de decir"
    )

internal fun isSlowVoiceCommand(text: String): Boolean =
    controlCommandKey(text) in setOf(
        "mas lento",
        "habla mas lento",
        "hablar mas lento",
        "voz mas lenta"
    )

internal fun isGoHomeCommand(text: String): Boolean =
    controlCommandKey(text) in setOf(
        "volver al inicio",
        "volve al inicio",
        "inicio",
        "ir al inicio"
    )

internal fun isResetFlowCommand(text: String): Boolean =
    controlCommandKey(text) in setOf(
        "resetear",
        "resetear flujo",
        "volver al inicio",
        "volve al inicio",
        "limpiar estado"
    )

internal enum class RobotSessionCommand {
    ENABLE,
    DISABLE,
    NONE
}

internal enum class PendingVoiceCorrectionGlobalAction {
    NONE,
    RESET_FLOW,
    STOP_SPEAKING,
    REPEAT_LAST,
    EMERGENCY,
    HELP,
    PAUSE_ROBOT,
    ENABLE_ROBOT,
    CANCEL
}

internal fun pendingVoiceCorrectionGlobalAction(text: String): PendingVoiceCorrectionGlobalAction {
    val sessionCommand = robotSessionCommand(text)
    return when {
        isResetFlowCommand(text) -> PendingVoiceCorrectionGlobalAction.RESET_FLOW
        VoiceCommandDispatcher.isStopCommand(text) -> PendingVoiceCorrectionGlobalAction.STOP_SPEAKING
        isRepeatLastResponseCommand(text) -> PendingVoiceCorrectionGlobalAction.REPEAT_LAST
        isEmergencyModeCommand(text) -> PendingVoiceCorrectionGlobalAction.EMERGENCY
        VoiceCommandDispatcher.isHelpCommand(text) -> PendingVoiceCorrectionGlobalAction.HELP
        sessionCommand == RobotSessionCommand.DISABLE -> PendingVoiceCorrectionGlobalAction.PAUSE_ROBOT
        sessionCommand == RobotSessionCommand.ENABLE -> PendingVoiceCorrectionGlobalAction.ENABLE_ROBOT
        controlCommandKey(text) in setOf("cancelar", "cancela") -> PendingVoiceCorrectionGlobalAction.CANCEL
        else -> PendingVoiceCorrectionGlobalAction.NONE
    }
}

internal fun isEmergencyModeCommand(text: String): Boolean =
    controlCommandKey(text) in setOf(
        "emergencia",
        "necesito ayuda",
        "ayuda urgente",
        "auxilio",
        "estoy en peligro",
        "estoy en problemas"
    )

internal fun robotSessionCommand(text: String): RobotSessionCommand =
    when (controlCommandKey(text)) {
        "ojo claro",
        "activa robot",
        "activar robot",
        "encender robot",
        "prender robot",
        "segui escuchando",
        "seguir escuchando" -> RobotSessionCommand.ENABLE
        "desactiva robot",
        "desactivar robot",
        "pausa robot",
        "pausar robot",
        "apagar robot" -> RobotSessionCommand.DISABLE
        else -> RobotSessionCommand.NONE
    }

internal fun VoiceCommandTargetIntent.toRepairSuggestedIntent(): RepairSuggestedIntent =
    when (this) {
        VoiceCommandTargetIntent.OPEN_WHATSAPP -> RepairSuggestedIntent.OPEN_WHATSAPP
        VoiceCommandTargetIntent.READ_VISIBLE_SCREEN -> RepairSuggestedIntent.READ_VISIBLE_SCREEN
        VoiceCommandTargetIntent.WHAT_CAN_I_DO -> RepairSuggestedIntent.WHAT_CAN_I_DO
        VoiceCommandTargetIntent.READ_VISIBLE_CHATS -> RepairSuggestedIntent.READ_VISIBLE_CHATS
        VoiceCommandTargetIntent.REPEAT_LAST -> RepairSuggestedIntent.REPEAT_LAST
        VoiceCommandTargetIntent.RESET_FLOW -> RepairSuggestedIntent.RESET_FLOW
        VoiceCommandTargetIntent.STOP_SPEAKING -> RepairSuggestedIntent.STOP_SPEAKING
        VoiceCommandTargetIntent.PAUSE_ROBOT -> RepairSuggestedIntent.PAUSE_ROBOT
        VoiceCommandTargetIntent.ENABLE_ROBOT -> RepairSuggestedIntent.ENABLE_ROBOT
        VoiceCommandTargetIntent.NONE -> RepairSuggestedIntent.NONE
    }

internal fun safeRecognizedSpeechDisplayText(text: String): String {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.isBlank()) return "-"
    if (isSensitiveRecognizedSpeech(clean)) return SENSITIVE_RECOGNIZED_TEXT
    return clean.take(MAX_RECOGNIZED_SPEECH_DISPLAY_CHARS)
}

internal fun isSensitiveRecognizedSpeech(text: String): Boolean {
    val normalized = MemoryPolicy.normalize(text)
    if (normalized.isBlank()) return false
    if (MemoryPolicy.containsProhibitedContent(text)) return true
    if (PrivacyGuard.containsSensitiveFinancialData(text)) return true
    if (sensitiveRecognizedTokens.any { token -> normalized.contains(token) }) return true
    return false
}

internal fun isWhatsAppWaitingState(agentState: AgentState?): Boolean =
    agentState == AgentState.WAITING_WHATSAPP_ACTION ||
        agentState == AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE

internal enum class AsyncResultDropReason(val logCode: String) {
    NONE("none"),
    MUTED_OR_STALE("muted_or_stale"),
    ROBOT_PAUSED("robot_paused")
}

internal fun isRequestMutedOrStale(
    requestId: Long,
    activeRequestId: Long,
    mutedThroughRequestId: Long
): Boolean =
    requestId != activeRequestId || requestId <= mutedThroughRequestId

internal fun asyncResultDropReason(
    requestId: Long,
    activeRequestId: Long,
    mutedThroughRequestId: Long,
    robotEnabled: Boolean
): AsyncResultDropReason =
    when {
        isRequestMutedOrStale(
            requestId = requestId,
            activeRequestId = activeRequestId,
            mutedThroughRequestId = mutedThroughRequestId
        ) -> AsyncResultDropReason.MUTED_OR_STALE
        !robotEnabled -> AsyncResultDropReason.ROBOT_PAUSED
        else -> AsyncResultDropReason.NONE
    }

internal fun shouldDropAsyncResult(
    requestId: Long,
    activeRequestId: Long,
    mutedThroughRequestId: Long,
    robotEnabled: Boolean
): Boolean =
    asyncResultDropReason(
        requestId = requestId,
        activeRequestId = activeRequestId,
        mutedThroughRequestId = mutedThroughRequestId,
        robotEnabled = robotEnabled
    ) != AsyncResultDropReason.NONE

internal fun whatsAppWaitingFallbackText(): String =
    "No entendi. Estas en un flujo de WhatsApp. Podes decir: WhatsApp principal, chat de Marco, mensaje para Marco, o cancelar."

internal fun safeRobotStateLabel(appState: AppState, agentState: AgentState?): String =
    (agentState?.name ?: appState.name).lowercase(Locale.US)

internal fun slowVoiceUnavailableText(): String =
    "Todavía no puedo cambiar la velocidad de voz desde acá. Te voy a responder con frases cortas."

internal fun repeatedResponseText(lastResponse: String): String =
    lastResponse.trim().ifBlank { "Todavía no dije nada para repetir." }

internal fun isContextualMessageRetryCommand(text: String): Boolean {
    val key = controlCommandKey(text)
    return key.contains("mandaselo") ||
        key.contains("mandalo") ||
        key.contains("preparalo") ||
        key.contains("ese mensaje") ||
        key.contains("ese whatsapp")
}

internal fun buildContextualWhatsAppPendingFromSession(
    text: String,
    snapshot: AgentSessionSnapshot,
    nowMillis: Long
): PendingConfirmation? {
    val messageText = snapshot.lastProposedMessage.trim()
    if (messageText.isBlank()) return null
    if (!PrivacyGuard.isSafeMessagePayload(messageText)) return null

    val contactName = extractContextualContactName(text)
        .ifBlank { snapshot.lastContactName }
        .trim()
    if (contactName.isBlank()) return null

    val spokenText = "Puedo preparar este mensaje para $contactName: '$messageText'. " +
        "Para prepararlo en WhatsApp, decí: confirmar."

    return PendingConfirmation(
        id = "session-compose-confirmation-$nowMillis",
        command = ExternalCommand(
            type = ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE,
            rawText = "session_context_compose_message",
            normalizedText = "session_context_compose_message",
            contactName = contactName,
            messageText = messageText,
            confidence = CommandConfidence.MEDIUM
        ),
        spokenText = spokenText,
        createdAtMillis = nowMillis,
        expiresAtMillis = nowMillis + PERSONAL_AGENT_PENDING_TTL_MILLIS
    )
}

private fun extractContextualContactName(text: String): String {
    val normalized = VoicePhraseNormalizer.normalizeForParser(text)
        .replace(Regex("\\s+"), " ")
        .trim()
    val match = Regex(
        pattern = "(?:\\ba\\b|\\bpara\\b|\\bcon\\b)\\s+(.+)$",
        option = RegexOption.IGNORE_CASE
    ).find(normalized) ?: return ""
    return match.groupValues[1]
        .replace(Regex("\\bmejor\\b", RegexOption.IGNORE_CASE), "")
        .trim('.', ',', ';', ':', '!', '?', '¿', '¡')
        .trim()
}

private fun controlCommandKey(text: String): String {
    val normalized = VoicePhraseNormalizer.normalizeForParser(text)
    val withoutAccents = Normalizer.normalize(
        normalized.lowercase(Locale("es", "AR")),
        Normalizer.Form.NFD
    ).replace(Regex("\\p{Mn}+"), "")

    return withoutAccents
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('.', '!', '?', '¿', '¡')
}

private const val PERSONAL_AGENT_PENDING_TTL_MILLIS = 2 * 60 * 1000L
private const val MAX_RECOGNIZED_SPEECH_DISPLAY_CHARS = 140
private const val WHATSAPP_WAITING_FALLBACK_ERROR_COUNT = 2

private val sensitiveRecognizedTokens: Set<String> = setOf(
    "banco",
    "bancaria",
    "bancario",
    "tarjeta",
    "cbu",
    "cvu",
    "otp",
    "codigo",
    "verificacion",
    "clave",
    "contrasena",
    "password",
    "pin"
)

private val CONTACT_MEMORY_INTENTS: Set<AgentIntent> = setOf(
    AgentIntent.SAVE_CONTACT,
    AgentIntent.SAVE_CONTACT_PHONE,
    AgentIntent.LIST_CONTACTS,
    AgentIntent.DELETE_CONTACT
)

// Texto reconocido que NO es un comando: ruidos, asentimientos y monosílabos
// que NO deben confirmar acciones ni llegar al backend.
private val AFFIRMATIVE_NOISE_PHRASES: Set<String> = setOf(
    "si",
    "sí",
    "si si",
    "sí sí",
    "si si si",
    "sí sí sí",
    "dale",
    "dale dale",
    "ok",
    "okey",
    "okay",
    "uh",
    "eh",
    "mm",
    "mhm",
    "ajá",
    "aja"
)

private val MAPS_SLOT_INTENTS: Set<AgentIntent> = setOf(
    AgentIntent.NAVIGATE_TO_DESTINATION,
    AgentIntent.SAVE_LOCATION_ALIAS,
    AgentIntent.DELETE_LOCATION_ALIAS
)

private val VOICE_CORRECTION_INTERRUPT_TARGETS: Set<VoiceCommandTargetIntent> = setOf(
    VoiceCommandTargetIntent.RESET_FLOW,
    VoiceCommandTargetIntent.STOP_SPEAKING,
    VoiceCommandTargetIntent.REPEAT_LAST,
    VoiceCommandTargetIntent.PAUSE_ROBOT,
    VoiceCommandTargetIntent.ENABLE_ROBOT
)

internal fun shouldHandleExternalCommand(
    text: String,
    hasPendingConsent: Boolean,
    router: CommandRouter = CommandRouter()
): Boolean {
    if (hasPendingConsent) return true
    val parsedIntent = LocalIntentParser(router).parse(text)
    val knownCurrentFlow = parsedIntent.intent in setOf(
        AgentIntent.OPEN_WHATSAPP,
        AgentIntent.OPEN_WHATSAPP_CHAT,
        AgentIntent.OPEN_PHONE,
        AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
        AgentIntent.CALL_CONTACT,
        AgentIntent.READ_VISIBLE_SCREEN,
        AgentIntent.REMEMBER_MEMORY,
        AgentIntent.LIST_MEMORY,
        AgentIntent.CLEAR_MEMORY,
        AgentIntent.SAVE_CONTACT,
        AgentIntent.SAVE_CONTACT_PHONE,
        AgentIntent.LIST_CONTACTS,
        AgentIntent.DELETE_CONTACT,
        AgentIntent.OPEN_MAPS,
        AgentIntent.GET_CURRENT_LOCATION,
        AgentIntent.NAVIGATE_TO_DESTINATION,
        AgentIntent.SAVE_LOCATION_ALIAS,
        AgentIntent.LIST_LOCATION_ALIASES,
        AgentIntent.DELETE_LOCATION_ALIAS,
        AgentIntent.CONFIRM,
        AgentIntent.CANCEL
    )
    return knownCurrentFlow || router.parse(text).type != ExternalCommandType.UNSUPPORTED
}
