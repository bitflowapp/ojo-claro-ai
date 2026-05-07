package com.ojoclaro.android.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ojoclaro.android.BuildConfig
import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentOutcome
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.agent.toAppState
import com.ojoclaro.android.agent.toAgentState
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
import com.ojoclaro.android.help.VoiceHelpCenter
import com.ojoclaro.android.maps.LocationCommandPhrases
import com.ojoclaro.android.maps.LocationProvider
import com.ojoclaro.android.memory.LocalMemoryStore
import com.ojoclaro.android.memory.PersonalMemorySnapshot
import com.ojoclaro.android.memory.SharedPreferencesPersonalMemoryStore
import com.ojoclaro.android.model.AppState
import com.ojoclaro.android.patterns.FrequentPatternTracker
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.risk.RiskDetector
import com.ojoclaro.android.risk.RiskWarning
import com.ojoclaro.android.llm.DisabledLlmAgentInterpreter
import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.llm.LlmAgentInterpreter
import com.ojoclaro.android.llm.OpenAiProxyAgentInterpreter
import com.ojoclaro.android.voice.VoiceCommandController
import com.ojoclaro.android.voice.VoiceCommandDispatcher
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val listening: Boolean = false,
    val loading: Boolean = false,
    val lastCommand: String = "",
    val lastNormalizedCommand: String = "",
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
    val voiceListenRequestId: Long = 0L,
    val spokenText: String = "Ojo Claro listo. Decime qué necesitás.",
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
    val error: String? = null
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
    private val api: AssistantApi = AssistantApi(BuildConfig.ASSISTANT_BASE_URL)
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
    private var pendingExternalConfirmation: PendingConfirmation? = null
    private var pendingConsentAction: PendingSensitiveAction? = null
    private var greeted = false
    private val agentIntentParser = LocalIntentParser()
    private val agentConversationManager = AgentConversationManager()

    fun greetIfFirstTime(hasMicrophonePermission: Boolean) {
        if (greeted) return
        greeted = true
        val message = if (hasMicrophonePermission) {
            "Ojo Claro listo. Decime qué necesitás."
        } else {
            VoiceCommandController.MICROPHONE_PERMISSION_MESSAGE
        }
        _state.update {
            it.copy(
                spokenText = message,
                microphonePermissionGranted = hasMicrophonePermission
            )
        }
        _speechEvents.tryEmit(SpeechEvent(message, force = true))
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

    fun requestHelp() {
        val message = VoiceHelpCenter.SPOKEN_HELP
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                spokenText = message,
                error = null
            )
        }
        _appState.value = AppState.SPEAKING
        _speechEvents.tryEmit(SpeechEvent(message, force = true))
    }

    fun submitVoiceText(text: String, imageBase64: String? = null) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            publishLocalMessage("No escuché un comando claro.", force = true)
            return
        }
        val normalizedText = VoicePhraseNormalizer.normalizeForParser(cleanText)
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                lastCommand = cleanText,
                lastNormalizedCommand = normalizedText,
                lastCommandTimestampMillis = now
            )
        }

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

        if (VoiceCommandDispatcher.isStopCommand(cleanText)) {
            agentConversationManager.clear()
            onStopSpeechRequested()
            return
        }

        if (imageBase64 == null && VoiceCommandDispatcher.isHelpCommand(cleanText)) {
            agentConversationManager.clear()
            requestHelp()
            return
        }

        if (
            imageBase64 == null &&
            (pendingExternalConfirmation != null || pendingConsentAction != null) &&
            isAffirmativeNoise(cleanText)
        ) {
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
            handlePersonalAgentRequest(
                cleanText = cleanText,
                normalizedText = normalizedText,
                personalSnapshot = personalSnapshot,
                parsedIntent = parsedIntentForPersonal,
                now = now
            )
            return
        }

        if (imageBase64 == null && handleAgentConversationIfNeeded(cleanText)) {
            return
        }

        if (imageBase64 == null && handleExternalCommandIfNeeded(cleanText)) {
            return
        }

        if (imageBase64 == null && VoiceCommandDispatcher.isReadTextCommand(cleanText)) {
            agentConversationManager.clear()
            startScanning()
            return
        }

        // Cortocircuito: textos como "sí", "si", "dale", "ok", "uh", "eh" sin pending real
        // no deben llegar al backend. Antes producían "No pude conectar. Entendí: sí sí".
        // Si llegan acá es porque los filtros previos ya descartaron la opción de
        // confirmar/cancelar/comando externo/agente conversacional.
        if (imageBase64 == null && isAffirmativeNoise(cleanText)) {
            publishLocalMessage("No hay ninguna acción pendiente.", force = false)
            return
        }

        val requestId = ++activeRequestId
        val command = parser.parse(cleanText)

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
            val handledByPersonalAgent = if (imageBase64 == null && command.type == AppCommandType.UNKNOWN) {
                val decision = decidePersonalAgent(
                    cleanText = cleanText,
                    normalizedText = normalizedText,
                    personalSnapshot = personalSnapshot,
                    parsedIntent = parsedIntentForPersonal ?: agentIntentParser.parse(cleanText),
                    now = now
                )
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
                if (requestId != activeRequestId) return@onSuccess
                publishAssistantResponse(requestId, response)
            }.onFailure { error ->
                if (requestId != activeRequestId) return@onFailure
                val fallback = localFallback(command, cleanText)
                _state.update {
                    it.copy(
                        loading = false,
                        spokenText = fallback.spokenText,
                        error = error.message ?: "No se pudo conectar con el backend."
                    )
                }

                if (requestId > mutedThroughRequestId) {
                    _appState.value = AppState.ERROR
                    _speechEvents.tryEmit(SpeechEvent(fallback.spokenText))
                } else {
                    _appState.value = AppState.IDLE
                }
            }
        }
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
        _appState.value = AppState.SCANNING
        _speechEvents.tryEmit(SpeechEvent(message, force = true))
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
        _speechEvents.tryEmit(SpeechEvent(spoken))
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
        _speechEvents.tryEmit(SpeechEvent(message))
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
            text = "Micrófono activado. Ojo Claro listo. Decime qué necesitás.",
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
                error = null
            )
        }
        _appState.value = AppState.LISTENING
    }

    fun onVoicePartialText(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        _state.update {
            it.copy(
                listening = true,
                micListening = true,
                voicePartialText = cleanText,
                lastCommand = cleanText,
                lastNormalizedCommand = VoicePhraseNormalizer.normalizeForParser(cleanText),
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
                voicePartialText = "",
                lastCommand = cleanText,
                lastNormalizedCommand = VoicePhraseNormalizer.normalizeForParser(cleanText),
                lastCommandTimestampMillis = System.currentTimeMillis()
            )
        }
        submitVoiceText(cleanText)
    }

    fun onVoiceError(message: String) {
        if (agentConversationManager.hasPendingSlotRequest) {
            _state.update {
                it.copy(
                    loading = false,
                    listening = false,
                    micListening = false,
                    voiceListenRequestId = it.voiceListenRequestId + 1L
                )
            }
            _appState.value = agentConversationManager.currentState.toAppState()
            return
        }
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

    fun onVoiceListeningStateChanged(state: VoiceListeningState) {
        _state.update {
            it.copy(
                micListening = state == VoiceListeningState.LISTENING,
                listening = state == VoiceListeningState.LISTENING
            )
        }
    }

    fun onTtsSpeakingChanged(isSpeaking: Boolean) {
        _state.update { it.copy(ttsSpeaking = isSpeaking) }
    }

    fun onStopSpeechRequested() {
        mutedThroughRequestId = activeRequestId
        pendingExternalConfirmation = null
        // Callar también cancela cualquier acción sensible pendiente: el usuario
        // claramente no quiere que la app siga adelante con esa lectura/operación.
        pendingConsentAction = null
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                micListening = false,
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

    fun onExternalCommandResult(result: CommandResult) {
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
        if (result is CommandResult.Success) {
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
            clearExternalHandoff()
            onExternalCommandResult(result)
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
        val spokenText = response.spokenText.trim().ifBlank {
            "Recibí una respuesta vacía del asistente."
        }

        _state.update {
            it.copy(
                loading = false,
                spokenText = spokenText,
                error = null
            )
        }

        if (requestId > mutedThroughRequestId) {
            _appState.value = AppState.SPEAKING
            _speechEvents.tryEmit(SpeechEvent(spokenText))
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
        viewModelScope.launch {
            val outcome = orchestrator.process(
                rawInput = text,
                pendingConfirmation = pendingExternalConfirmation,
                pendingConsent = pendingConsentAction,
                appState = _appState.value
            )
            applyOutcomeIfExternal(outcome)
        }
        // Si hay un consent pending vivo, "confirmar"/"cancelar" deben pasar por el
        // orchestrator (no caer al backend). En cualquier otro caso, basta con detectar
        // el comando externo de forma sincrónica como antes.
        return true
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
            viewModelScope.launch {
                val orchestratorOutcome = orchestrator.process(
                    rawInput = commandText,
                    pendingConfirmation = pendingExternalConfirmation,
                    pendingConsent = pendingConsentAction,
                    appState = _appState.value
                )
                applyOutcomeIfExternal(orchestratorOutcome)
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
        _appState.value = appState
        if (outcome.spokenText.isNotBlank()) {
            _speechEvents.tryEmit(SpeechEvent(outcome.spokenText, force = outcome.isError))
        } else if (outcome.shouldListenAgain && agentSubState != null) {
            _state.update { it.copy(voiceListenRequestId = it.voiceListenRequestId + 1L) }
        }
    }

    private fun isAffirmativeNoise(text: String): Boolean {
        return VoicePhraseNormalizer.isAffirmativeNoise(text)
    }

    private fun pendingDebugLabel(): String {
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

    private fun applyOutcomeIfExternal(outcome: OrchestratorOutcome): Boolean {
        if (outcome.newPending != null) {
            pendingExternalConfirmation = outcome.newPending
        }
        if (outcome.clearsPending) {
            pendingExternalConfirmation = null
        }
        if (outcome.newPendingConsent != null) {
            pendingConsentAction = outcome.newPendingConsent
        }
        if (outcome.clearsPendingConsent) {
            pendingConsentAction = null
        }

        val handoff = outcome.externalEvent as? ExternalActionEvent.ExternalAppHandoff
        val capability = globalAssistantCapabilityProvider()
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
        _appState.value = outcome.targetState
        if (handoff == null) {
            _speechEvents.tryEmit(SpeechEvent(outcome.spokenText, force = outcome.forceSpeak))
        }
        outcome.externalEvent?.let { _externalActionEvents.tryEmit(it) }
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

    private fun publishLocalMessage(
        text: String,
        force: Boolean = false,
        appState: AppState = AppState.SPEAKING
    ) {
        _state.update {
            it.copy(
                loading = false,
                listening = false,
                micListening = false,
                spokenText = text,
                error = if (appState == AppState.ERROR || appState == AppState.PERMISSION_REQUIRED) text else null
            )
        }
        _appState.value = appState
        _speechEvents.tryEmit(SpeechEvent(text, force = force))
    }

    private fun handlePersonalAgentDecision(
        decision: PersonalAgentDecision,
        requestId: Long
    ): Boolean {
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
                val spoken = decision.response?.userFacingQuestion ?: decision.reason
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
                decision.externalEvent?.let { _externalActionEvents.tryEmit(it) }
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
                publishLocalMessage(decision.spokenText, force = true, appState = AppState.IDLE)
                return true
            }
        }
    }

    private fun localFallback(command: AppCommand, text: String): AssistResponse {
        val spokenText = when (command.type) {
            AppCommandType.EMERGENCY_HELP ->
                "Si estás en peligro, llamá a tu contacto de emergencia o pedí ayuda a una persona cercana."
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

private const val PERSONAL_AGENT_PENDING_TTL_MILLIS = 2 * 60 * 1000L

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
