package com.ojoclaro.android.global

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService
import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.runtime.screen.AndroidAccessibilityScreenContextProvider
import com.ojoclaro.android.agent.runtime.whatsapp.VisibleChatOpenResult
import com.ojoclaro.android.agent.runtime.whatsapp.VisibleScreenCommand
import com.ojoclaro.android.agent.runtime.whatsapp.VisibleScreenCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppScreenDetector
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVisibleChatMatch
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVisibleChatMatcher
import com.ojoclaro.android.capabilities.CapabilityRegistry
import com.ojoclaro.android.domain.AssistantOrchestrator
import com.ojoclaro.android.domain.OrchestratorOutcome
import com.ojoclaro.android.external.CommandConfidence
import com.ojoclaro.android.external.CommandResult
import com.ojoclaro.android.external.ExternalCommand
import com.ojoclaro.android.external.ExternalCommandType
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.external.PendingConfirmation
import com.ojoclaro.android.external.WhatsAppIntentHelper
import com.ojoclaro.android.maps.LocationProvider
import com.ojoclaro.android.maps.MapsActionExecutor
import com.ojoclaro.android.memory.LocalMemoryStore
import com.ojoclaro.android.model.AppState
import com.ojoclaro.android.phone.PhoneActionExecutor
import com.ojoclaro.android.speech.SpeechController
import com.ojoclaro.android.ui.home.TTS_TO_MIC_DELAY_MILLIS
import com.ojoclaro.android.voice.AndroidSpeechInputEngine
import com.ojoclaro.android.voice.SpeechListeningMode
import com.ojoclaro.android.voice.VoiceCommandController
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import com.ojoclaro.android.voice.VoicePhraseNormalizer
import com.ojoclaro.android.voice.VoiceRetryHandle
import com.ojoclaro.android.voice.VoiceRetryScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GlobalAssistantService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val contextState = ExternalConversationContext()
    private val intentParser = LocalIntentParser()
    private val screenContextProvider = AndroidAccessibilityScreenContextProvider()
    private val whatsAppScreenDetector = WhatsAppScreenDetector()
    private val visibleChatMatcher = WhatsAppVisibleChatMatcher()

    private lateinit var notifier: GlobalAssistantNotifier
    private lateinit var overlayController: GlobalAssistantOverlayController
    private lateinit var speechController: SpeechController
    private lateinit var voiceController: VoiceCommandController
    private lateinit var orchestrator: AssistantOrchestrator
    private lateinit var whatsAppIntentHelper: WhatsAppIntentHelper
    private lateinit var phoneActionExecutor: PhoneActionExecutor
    private lateinit var mapsActionExecutor: MapsActionExecutor

    override fun onCreate() {
        super.onCreate()
        notifier = GlobalAssistantNotifier(this)
        whatsAppIntentHelper = WhatsAppIntentHelper(this)
        phoneActionExecutor = PhoneActionExecutor(this)
        mapsActionExecutor = MapsActionExecutor(this)
        orchestrator = AssistantOrchestrator(
            capabilityRegistry = CapabilityRegistry(this),
            memoryStore = LocalMemoryStore(this),
            locationProvider = LocationProvider(this)
        )

        overlayController = GlobalAssistantOverlayController(
            context = this,
            onListen = ::listenNow,
            onSilence = ::silence,
            onStop = ::stopMode
        )

        speechController = SpeechController(
            context = this,
            onSpeechStarted = {
                serviceScope.launch { voiceController.pauseForSpeech() }
            },
            onSpeechFinished = {
                serviceScope.launch {
                    delay(TTS_TO_MIC_DELAY_MILLIS)
                    resumeListeningIfActive()
                }
            },
            onSpeechStopped = {
                serviceScope.launch { resumeListeningIfActive() }
            }
        )

        val engine = AndroidSpeechInputEngine(this)
        engine.setListeningMode(SpeechListeningMode.EXPECTING_RESPONSE)
        voiceController = VoiceCommandController(
            engine = engine,
            hasRecordAudioPermission = { hasRecordAudioPermission() },
            onPartialTextCallback = {},
            onFinalTextCallback = ::onVoiceFinalText,
            onErrorCallback = ::onVoiceError,
            onReadyCallback = {},
            onStateChanged = {},
            retryScheduler = VoiceRetryScheduler { delayMillis, action ->
                val job = serviceScope.launch {
                    delay(delayMillis)
                    action()
                }
                VoiceRetryHandle { job.cancel() }
            }
        ).apply {
            setExpectingResponse(true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            GlobalAssistantMode.ACTION_START -> startContinuation(intent)
            GlobalAssistantMode.ACTION_LISTEN -> listenNow()
            GlobalAssistantMode.ACTION_SILENCE -> silence()
            GlobalAssistantMode.ACTION_STOP -> stopMode()
            else -> listenNow()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        voiceController.destroy()
        speechController.shutdown()
        overlayController.hide()
        notifier.cancel()
        super.onDestroy()
    }

    private fun startContinuation(intent: Intent) {
        val externalApp = ExternalAppName.fromHandoffName(
            intent.getStringExtra(GlobalAssistantMode.EXTRA_EXTERNAL_APP_NAME).orEmpty()
        )
        val expectWhatsAppAction = intent.getBooleanExtra(
            GlobalAssistantMode.EXTRA_EXPECT_WHATSAPP_ACTION,
            false
        )
        val snapshot = contextState.start(
            externalApp = externalApp,
            reason = intent.getStringExtra(GlobalAssistantMode.EXTRA_REASON).orEmpty(),
            returnHint = intent.getStringExtra(GlobalAssistantMode.EXTRA_RETURN_HINT).orEmpty(),
            agentState = if (expectWhatsAppAction) AgentState.WAITING_WHATSAPP_ACTION else null
        )
        startForegroundSafely(snapshot)
        overlayController.show(snapshot)

        val delayMillis = intent.getLongExtra(
            GlobalAssistantMode.EXTRA_START_LISTENING_DELAY_MS,
            DEFAULT_START_LISTENING_DELAY_MS
        )
        serviceScope.launch {
            delay(delayMillis.coerceAtLeast(0L))
            resumeListeningIfActive()
        }
    }

    private fun startForegroundSafely(snapshot: ExternalConversationSnapshot) {
        val notification = notifier.build(snapshot)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    GlobalAssistantMode.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(GlobalAssistantMode.NOTIFICATION_ID, notification)
            }
        }.onFailure {
            runCatching { startForeground(GlobalAssistantMode.NOTIFICATION_ID, notification) }
        }
    }

    private fun listenNow() {
        contextState.touch()
        if (!hasRecordAudioPermission()) {
            speak(GlobalAssistantMode.BACKGROUND_MIC_FALLBACK, force = true)
            return
        }
        voiceController.setExpectingResponse(true)
        voiceController.startListening()
    }

    private fun silence() {
        contextState.silence()
        speechController.stop()
        voiceController.pauseListening()
    }

    private fun stopMode() {
        contextState.clear()
        speechController.stop()
        voiceController.stopListening()
        overlayController.hide()
        notifier.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun resumeListeningIfActive() {
        val snapshot = contextState.current
        if (!snapshot.active) return
        if (!hasRecordAudioPermission()) {
            speak(GlobalAssistantMode.BACKGROUND_MIC_FALLBACK, force = true)
            return
        }
        voiceController.setExpectingResponse(true)
        voiceController.resumeAfterSpeech()
    }

    private fun onVoiceFinalText(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        serviceScope.launch { handleRecognizedText(cleanText) }
    }

    private fun onVoiceError(message: String) {
        val snapshot = contextState.current
        if (!snapshot.active) return
        if (snapshot.agentState in EXPECTING_STATES) {
            serviceScope.launch {
                delay(800L)
                resumeListeningIfActive()
            }
        } else {
            speak(GlobalAssistantMode.BACKGROUND_MIC_FALLBACK, force = true)
        }
    }

    private suspend fun handleRecognizedText(text: String) {
        if (contextState.current.active.not()) return
        contextState.touch()

        when {
            isStopModeCommand(text) -> {
                stopMode()
                return
            }
            VoiceCommandDispatcher.isStopCommand(text) -> {
                silence()
                return
            }
            isNonConfirmingAffirmative(text) && contextState.current.pendingConfirmation != null -> {
                speak("Decime confirmar o cancelar.", force = true)
                return
            }
        }

        handleVisibleChatPendingConfirmationIfNeeded(text)?.let { outcome ->
            applyOutcome(outcome)
            return
        }

        handleContextualWhatsApp(text)?.let { outcome ->
            applyOutcome(outcome)
            return
        }

        val outcome = orchestrator.process(
            rawInput = text,
            pendingConfirmation = contextState.current.pendingConfirmation,
            appState = AppState.EXTERNAL_APP_HANDOFF
        )
        applyOutcome(outcome)
    }

    private suspend fun handleContextualWhatsApp(text: String): OrchestratorOutcome? {
        val snapshot = contextState.current
        if (snapshot.externalApp != ExternalAppName.WHATSAPP) return null

        handleVisibleScreenFollowUp(text)?.let { return it }

        val normalized = VoicePhraseNormalizer.normalizeForParser(text)
        val parsed = intentParser.parse(normalized)
        if (parsed.intent in setOf(AgentIntent.OPEN_WHATSAPP_CHAT, AgentIntent.COMPOSE_WHATSAPP_MESSAGE)) {
            return orchestrator.process(
                rawInput = normalized,
                pendingConfirmation = snapshot.pendingConfirmation,
                appState = AppState.EXTERNAL_APP_HANDOFF
            )
        }

        return when (snapshot.agentState) {
            AgentState.WAITING_WHATSAPP_ACTION -> handleWaitingWhatsAppAction(normalized)
            AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> handleWaitingChatOrMessage(normalized)
            AgentState.WAITING_MESSAGE -> handleWaitingMessage(normalized)
            else -> null
        }
    }

    private fun handleVisibleScreenFollowUp(text: String): OrchestratorOutcome? {
        val command = VisibleScreenCommandParser.parse(text) as? VisibleScreenCommand.OpenVisibleChat
            ?: return null

        contextState.updateAgentState(AgentState.PROCESSING)
        refreshContinuationUi()

        val snapshot = screenContextProvider.current()
        val screenState = whatsAppScreenDetector.detect(snapshot)
        if (!screenState.isOpen) {
            contextState.updateAgentState(AgentState.WAITING_WHATSAPP_ACTION)
            return speakOnly(
                "No estás en WhatsApp. Abrí WhatsApp y volvé a pedirme.",
                AppState.WAITING_WHATSAPP_ACTION
            ).copy(agentState = AgentState.WAITING_WHATSAPP_ACTION)
        }
        if (screenState.isInChat) {
            contextState.updateAgentState(AgentState.WAITING_WHATSAPP_ACTION)
            return speakOnly(
                "Estás dentro de un chat. Volvé a la lista de chats y decime el nombre que querés abrir.",
                AppState.WAITING_WHATSAPP_ACTION
            ).copy(agentState = AgentState.WAITING_WHATSAPP_ACTION)
        }

        val match = visibleChatMatcher.findBest(
            targetName = command.targetName,
            elements = snapshot?.elements.orEmpty()
        ) ?: run {
            contextState.updateAgentState(AgentState.WAITING_WHATSAPP_ACTION)
            return speakOnly(
                "No encontré ${command.targetName} en la pantalla. " +
                    "Puedo volver a leer los chats visibles o podés desplazarte.",
                AppState.WAITING_WHATSAPP_ACTION
            ).copy(agentState = AgentState.WAITING_WHATSAPP_ACTION, decisionDebugLabel = "VISIBLE_CHAT_NO_MATCH")
        }

        val pending = buildVisibleChatPendingConfirmation(
            rawText = text,
            match = match,
            nowMillis = System.currentTimeMillis()
        )
        contextState.updateAgentState(AgentState.WAITING_CONFIRMATION)
        return OrchestratorOutcome(
            spokenText = pending.spokenText,
            targetState = AppState.WAITING_CONFIRMATION,
            newPending = pending,
            forceSpeak = true,
            agentState = AgentState.WAITING_CONFIRMATION,
            decisionDebugLabel = "VISIBLE_CHAT_PENDING_CONFIRMATION"
        )
    }

    private fun handleVisibleChatPendingConfirmationIfNeeded(text: String): OrchestratorOutcome? {
        val pending = contextState.current.pendingConfirmation ?: return null
        if (!isVisibleChatPendingConfirmation(pending)) return null

        val parsedIntent = intentParser.parse(text).intent
        if (parsedIntent == AgentIntent.CANCEL) {
            return OrchestratorOutcome(
                spokenText = "Acción cancelada. No abrí ningún chat.",
                targetState = AppState.SPEAKING,
                clearsPending = true,
                forceSpeak = true,
                decisionDebugLabel = "VISIBLE_CHAT_CANCELLED"
            )
        }

        if (!shouldExecuteVisibleChatClick(text, pending)) {
            return OrchestratorOutcome(
                spokenText = "Estela necesita tu confirmación antes de continuar. Decí confirmar o cancelar.",
                targetState = AppState.WAITING_CONFIRMATION,
                forceSpeak = true,
                agentState = AgentState.WAITING_CONFIRMATION,
                decisionDebugLabel = "VISIBLE_CHAT_CONFIRMATION_REQUIRED"
            )
        }

        contextState.updateAgentState(AgentState.PROCESSING)
        refreshContinuationUi()

        val targetName = pending.command.targetName.orEmpty()
        return when (val result = OjoClaroAccessibilityService.openVisibleWhatsAppChatByName(targetName)) {
            is VisibleChatOpenResult.Opened -> OrchestratorOutcome(
                spokenText = "Abrí el chat de ${result.displayName}. No envié ningún mensaje.",
                targetState = AppState.SPEAKING,
                clearsPending = true,
                forceSpeak = true,
                decisionDebugLabel = "VISIBLE_CHAT_OPENED"
            )
            is VisibleChatOpenResult.NotInWhatsApp -> OrchestratorOutcome(
                spokenText = "No puedo abrirlo porque la pantalla actual no parece ser WhatsApp.",
                targetState = AppState.ERROR,
                clearsPending = true,
                isError = true,
                forceSpeak = true,
                decisionDebugLabel = "VISIBLE_CHAT_NOT_IN_WHATSAPP"
            )
            is VisibleChatOpenResult.NoMatch -> OrchestratorOutcome(
                spokenText = "No encontré ${result.targetName} en la pantalla. " +
                    "Puedo volver a leer los chats visibles o podés desplazarte.",
                targetState = AppState.ERROR,
                clearsPending = true,
                isError = true,
                forceSpeak = true,
                decisionDebugLabel = "VISIBLE_CHAT_NO_MATCH_ON_CONFIRM"
            )
            is VisibleChatOpenResult.Unsafe -> OrchestratorOutcome(
                spokenText = "Veo ${result.displayName ?: targetName}, pero no puedo abrirlo de forma segura. " +
                    "Tocá dos veces sobre ese chat para abrirlo.",
                targetState = AppState.ERROR,
                clearsPending = true,
                isError = true,
                forceSpeak = true,
                decisionDebugLabel = "VISIBLE_CHAT_UNSAFE_${result.reason}"
            )
            is VisibleChatOpenResult.Failed -> OrchestratorOutcome(
                spokenText = "No pude abrir el chat de ${result.displayName ?: targetName}. " +
                    "Puedo guiarte para tocarlo manualmente.",
                targetState = AppState.ERROR,
                clearsPending = true,
                isError = true,
                forceSpeak = true,
                decisionDebugLabel = "VISIBLE_CHAT_CLICK_FAILED_${result.reason}"
            )
        }
    }

    private suspend fun handleWaitingWhatsAppAction(text: String): OrchestratorOutcome? {
        val contact = extractBareContact(text) ?: return null
        contextState.updateContact(contact)
        contextState.updateAgentState(AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE)
        return speakOnly(
            "Abrir chat con $contact o mandarle un mensaje?",
            AppState.WAITING_CONFIRMATION
        )
    }

    private suspend fun handleWaitingChatOrMessage(text: String): OrchestratorOutcome? {
        val contact = contextState.current.lastContactName.orEmpty()
        if (contact.isBlank()) return null
        val normalized = VoicePhraseNormalizer.normalizeForParser(text).lowercase()
        return when {
            normalized in CHAT_CHOICES ->
                orchestrator.process(
                    rawInput = "abri el chat de $contact",
                    pendingConfirmation = contextState.current.pendingConfirmation,
                    appState = AppState.EXTERNAL_APP_HANDOFF
                )
            normalized in MESSAGE_CHOICES -> {
                contextState.updateAgentState(AgentState.WAITING_MESSAGE)
                speakOnly("Que mensaje queres mandarle a $contact?", AppState.WAITING_CONFIRMATION)
            }
            extractMessageWithoutContact(text) != null ->
                orchestrator.process(
                    rawInput = "mandale a $contact que ${extractMessageWithoutContact(text)}",
                    pendingConfirmation = contextState.current.pendingConfirmation,
                    appState = AppState.EXTERNAL_APP_HANDOFF
                )
            else -> null
        }
    }

    private suspend fun handleWaitingMessage(text: String): OrchestratorOutcome? {
        val contact = contextState.current.lastContactName.orEmpty()
        if (contact.isBlank()) return null
        val message = extractMessageWithoutContact(text) ?: text.trim()
        return orchestrator.process(
            rawInput = "mandale a $contact que $message",
            pendingConfirmation = contextState.current.pendingConfirmation,
            appState = AppState.EXTERNAL_APP_HANDOFF
        )
    }

    private fun speakOnly(text: String, appState: AppState): OrchestratorOutcome =
        OrchestratorOutcome(
            spokenText = text,
            targetState = appState,
            forceSpeak = true
        )

    private suspend fun applyOutcome(outcome: OrchestratorOutcome) {
        if (outcome.newPending != null) {
            contextState.updatePendingConfirmation(outcome.newPending)
        }
        if (outcome.clearsPending) {
            contextState.updatePendingConfirmation(null)
            if (outcome.agentState == null) {
                contextState.updateAgentState(null)
            }
        }
        if (outcome.agentState != null) {
            contextState.updateAgentState(outcome.agentState)
        }
        refreshContinuationUi()

        when (val event = outcome.externalEvent) {
            null -> {
                if (outcome.spokenText.isNotBlank()) {
                    speak(outcome.spokenText, force = outcome.forceSpeak)
                } else {
                    resumeListeningIfActive()
                }
            }
            is ExternalActionEvent.ExternalAppHandoff -> {
                if (outcome.spokenText.isNotBlank()) {
                    speak(outcome.spokenText, force = true)
                    delay(handoffSpeechDelayMillis(outcome.spokenText))
                }
                executeExternalAction(event.delegate)
            }
            else -> {
                if (outcome.spokenText.isNotBlank()) {
                    speak(outcome.spokenText, force = outcome.forceSpeak)
                    delay(handoffSpeechDelayMillis(outcome.spokenText))
                }
                executeExternalAction(event)
            }
        }
    }

    private fun executeExternalAction(action: ExternalActionEvent): CommandResult =
        when (action) {
            ExternalActionEvent.OpenWhatsApp -> whatsAppIntentHelper.openWhatsApp()
            is ExternalActionEvent.ComposeWhatsAppMessage ->
                whatsAppIntentHelper.composeMessage(action.contactName, action.messageText)
            is ExternalActionEvent.OpenWhatsAppChat ->
                whatsAppIntentHelper.openChat(action.contactName, action.phoneE164)
            ExternalActionEvent.OpenMaps -> mapsActionExecutor.openMaps()
            is ExternalActionEvent.NavigateToDestination ->
                mapsActionExecutor.openNavigationTo(action.destination)
            is ExternalActionEvent.NavigateToCoordinates ->
                mapsActionExecutor.openNavigationToCoordinates(
                    latitude = action.latitude,
                    longitude = action.longitude,
                    label = action.label
                )
            is ExternalActionEvent.OpenCurrentLocation ->
                mapsActionExecutor.openCurrentLocation(action.latitude, action.longitude)
            ExternalActionEvent.OpenPhone -> phoneActionExecutor.openDialer()
            is ExternalActionEvent.DialPhoneNumber ->
                phoneActionExecutor.prepareCall(action.contactName, action.phoneNumber)
            ExternalActionEvent.ReadVisibleScreen ->
                CommandResult.Failed("Volve a Estela para leer pantalla.", recoverable = true)
            ExternalActionEvent.RequestLocationPermission ->
                CommandResult.Failed("Volve a Estela para activar ubicacion.", recoverable = true)
            is ExternalActionEvent.ExternalAppHandoff -> executeExternalAction(action.delegate)
        }

    private fun speak(text: String, force: Boolean = false) {
        speechController.speak(text, force = force)
    }

    private fun refreshContinuationUi() {
        val snapshot = contextState.current
        if (!snapshot.active) return
        startForegroundSafely(snapshot)
        overlayController.show(snapshot)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    companion object {
        private const val DEFAULT_START_LISTENING_DELAY_MS = 2_500L

        fun startContinuation(
            context: Context,
            handoff: ExternalActionEvent.ExternalAppHandoff,
            startListeningDelayMillis: Long
        ): Boolean =
            runCatching {
                val intent = Intent(context, GlobalAssistantService::class.java).apply {
                    action = GlobalAssistantMode.ACTION_START
                    putExtra(GlobalAssistantMode.EXTRA_EXTERNAL_APP_NAME, handoff.externalAppName)
                    putExtra(GlobalAssistantMode.EXTRA_REASON, handoff.reason)
                    putExtra(GlobalAssistantMode.EXTRA_RETURN_HINT, handoff.returnHint)
                    putExtra(
                        GlobalAssistantMode.EXTRA_EXPECT_WHATSAPP_ACTION,
                        GlobalAssistantMode.shouldExpectWhatsAppAction(handoff)
                    )
                    putExtra(
                        GlobalAssistantMode.EXTRA_START_LISTENING_DELAY_MS,
                        startListeningDelayMillis
                    )
                }
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }.isSuccess

        fun requestStop(context: Context) {
            runCatching {
                context.applicationContext.startService(
                    GlobalAssistantNotifier.serviceIntent(
                        context.applicationContext,
                        GlobalAssistantMode.ACTION_STOP
                    )
                )
            }
        }

        fun requestSilence(context: Context) {
            runCatching {
                context.applicationContext.startService(
                    GlobalAssistantNotifier.serviceIntent(
                        context.applicationContext,
                        GlobalAssistantMode.ACTION_SILENCE
                    )
                )
            }
        }

        fun extractMessageWithoutContact(text: String): String? {
            val clean = VoicePhraseNormalizer.normalizeForParser(text).trim()
            val match = MESSAGE_WITHOUT_CONTACT_REGEX.matchEntire(clean) ?: return null
            return match.groupValues[1].trim().takeIf { it.isNotBlank() }
        }

        fun extractBareContact(text: String): String? {
            val clean = VoicePhraseNormalizer.normalizeForContactExtraction(text)
                .trim()
                .trim('.', ',', ';', ':')
                .replace(Regex("\\s+"), " ")
                .replace(Regex("^(?:con|el de|la de)\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
            if (clean.isBlank()) return null
            val normalized = VoicePhraseNormalizer.normalizeForParser(clean).lowercase()
            if (normalized.split(" ").size > 4) return null
            if (normalized in NON_CONTACT_PHRASES) return null
            if (normalized.split(" ").any { it in NON_CONTACT_TOKENS }) return null
            return clean.takeIf { normalized.any { char -> char.isLetter() } }
        }

        fun isStopModeCommand(text: String): Boolean {
            val normalized = VoicePhraseNormalizer.normalizeForParser(text).lowercase().trim()
            return normalized in setOf("detener", "terminar", "apagar ojo claro", "apagar estela")
        }

        fun isNonConfirmingAffirmative(text: String): Boolean =
            VoicePhraseNormalizer.isAffirmativeNoise(text)

        fun buildVisibleChatPendingConfirmation(
            rawText: String,
            match: WhatsAppVisibleChatMatch,
            nowMillis: Long
        ): PendingConfirmation {
            val spokenText = "Veo ${match.displayName} en pantalla. ¿Querés que lo abra?"
            return PendingConfirmation(
                id = "visible-chat-confirmation-$nowMillis",
                command = ExternalCommand(
                    type = ExternalCommandType.OPEN_WHATSAPP_CHAT,
                    rawText = rawText,
                    normalizedText = VISIBLE_CHAT_CONFIRMATION_MARKER,
                    targetName = match.requestedName,
                    payloadText = match.displayName,
                    confidence = CommandConfidence.HIGH
                ),
                spokenText = spokenText,
                createdAtMillis = nowMillis,
                expiresAtMillis = nowMillis + VISIBLE_CHAT_CONFIRMATION_TTL_MILLIS
            )
        }

        fun isVisibleChatPendingConfirmation(pending: PendingConfirmation): Boolean =
            pending.command.type == ExternalCommandType.OPEN_WHATSAPP_CHAT &&
                pending.command.normalizedText == VISIBLE_CHAT_CONFIRMATION_MARKER

        fun shouldExecuteVisibleChatClick(text: String, pending: PendingConfirmation): Boolean =
            isVisibleChatPendingConfirmation(pending) &&
                GlobalAssistantMode.isStrictConfirmation(text)

        private fun handoffSpeechDelayMillis(text: String): Long =
            (900L + text.length * 45L).coerceIn(1_200L, 4_500L)

        internal const val VISIBLE_CHAT_CONFIRMATION_MARKER = "visible_screen_open_chat"
        private const val VISIBLE_CHAT_CONFIRMATION_TTL_MILLIS = 45_000L

        private val EXPECTING_STATES = setOf(
            AgentState.WAITING_WHATSAPP_ACTION,
            AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE,
            AgentState.WAITING_CONTACT,
            AgentState.WAITING_MESSAGE,
            AgentState.WAITING_CONFIRMATION
        )

        private val CHAT_CHOICES = setOf(
            "chat",
            "el chat",
            "abrir chat",
            "abrir el chat",
            "abri chat",
            "abri el chat",
            "abrilo"
        )

        private val MESSAGE_CHOICES = setOf(
            "mensaje",
            "un mensaje",
            "el mensaje",
            "mandarle mensaje",
            "mandarle un mensaje",
            "mandar mensaje",
            "decirle",
            "decile",
            "escribirle",
            "escribile"
        )

        private val NON_CONTACT_PHRASES = setOf(
            "si",
            "dale",
            "ok",
            "bueno",
            "confirmar",
            "cancelar",
            "callar",
            "detener"
        )

        private val NON_CONTACT_TOKENS = setOf(
            "chat",
            "mensaje",
            "si",
            "dale",
            "ok",
            "confirmar",
            "cancelar",
            "callar",
            "detener"
        )

        private val MESSAGE_WITHOUT_CONTACT_REGEX = Regex(
            "^\\s*(?:(?:decile|decirle|decir|mandale|mandarle|mandar|escribile|escribirle|escribir)\\s+(?:que\\s+)?|que\\s+)(.+?)\\s*$",
            RegexOption.IGNORE_CASE
        )
    }
}
