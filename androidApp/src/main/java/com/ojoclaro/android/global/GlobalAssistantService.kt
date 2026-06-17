package com.ojoclaro.android.global

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ojoclaro.android.BuildConfig
import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService
import com.ojoclaro.android.accessibility.WhatsAppAudioPlayResult
import com.ojoclaro.android.accessibility.WhatsAppDraftReadResult
import com.ojoclaro.android.accessibility.WhatsAppDraftSetResult
import com.ojoclaro.android.accessibility.WhatsAppSendTapResult
import com.ojoclaro.android.accessibility.WhatsAppVideoCallCheck
import com.ojoclaro.android.accessibility.WhatsAppVideoCallTapResult
import com.ojoclaro.android.accessibility.InstagramChatOpenResult
import com.ojoclaro.android.accessibility.InstagramDraftSetResult
import com.ojoclaro.android.accessibility.InstagramScreenCheck
import com.ojoclaro.android.accessibility.InstagramScreenState
import com.ojoclaro.android.agent.runtime.instagram.InstagramNameMatcher
import com.ojoclaro.android.accessibility.InstagramSendTapResult
import com.ojoclaro.android.accessibility.InstagramTapOutcome
import com.ojoclaro.android.accessibility.InstagramVideoCallCheck
import com.ojoclaro.android.accessibility.InstagramVideoCallTapResult
import com.ojoclaro.android.agent.messaging.MessagingReplyOutcome
import com.ojoclaro.android.agent.messaging.MessagingTaskIntent
import com.ojoclaro.android.agent.messaging.MessagingTaskRouter
import com.ojoclaro.android.agent.core.screen.NextStepQueryPhrases
import com.ojoclaro.android.agent.core.screen.ScreenActionSafetyNote
import com.ojoclaro.android.agent.core.screen.ScreenQueryPhrases
import com.ojoclaro.android.agent.mission.AgentBackendHealth
import com.ojoclaro.android.agent.mission.AgentLocalReadOutcome
import com.ojoclaro.android.agent.mission.AgentMissionBudgets
import com.ojoclaro.android.agent.mission.AgentMissionPhrases
import com.ojoclaro.android.agent.mission.AgentMissionStatus
import com.ojoclaro.android.agent.mission.AgentObservationBuilder
import com.ojoclaro.android.agent.mission.AgentPolicyGate
import com.ojoclaro.android.agent.mission.AgentSessionCoordinator
import com.ojoclaro.android.agent.mission.AgentToolRegistry
import com.ojoclaro.android.agent.mission.AndroidAgentToolExecutor
import com.ojoclaro.android.agent.mission.HttpAgentPlannerClient
import com.ojoclaro.android.agent.mission.AgentOutdoorFixSummary
import com.ojoclaro.android.llm.HttpUrlConnectionLlmAgentNetworkClient
import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.llm.ProxyHealthProbe
import com.ojoclaro.android.llm.ProxyHealthState
import com.ojoclaro.android.outdoor.AndroidOutdoorLocationEngine
import com.ojoclaro.android.outdoor.MobilityFallbackKind
import com.ojoclaro.android.outdoor.OutdoorDestinationNormalizer
import com.ojoclaro.android.outdoor.OutdoorDestinationReply
import com.ojoclaro.android.outdoor.OutdoorFixResult
import com.ojoclaro.android.outdoor.OutdoorForegroundService
import com.ojoclaro.android.outdoor.OutdoorLocationReader
import com.ojoclaro.android.outdoor.OutdoorMobilityFallbackHub
import com.ojoclaro.android.outdoor.OutdoorPhrases
import com.ojoclaro.android.outdoor.RideMonitorPhrases
import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.outdoor.BackendSceneDescriber
import com.ojoclaro.android.outdoor.OutdoorSceneCapturer
import com.ojoclaro.android.outdoor.OutdoorSceneOutcome
import com.ojoclaro.android.camera.CameraAssistController
import com.ojoclaro.android.camera.CameraAssistPhrases
import com.ojoclaro.android.camera.CameraAssistSession
import com.ojoclaro.android.presence.AssistantPresenceStateMapper
import com.ojoclaro.android.presence.AssistantVisualState
import com.ojoclaro.android.voice.EstelaColloquialNormalizer
import com.ojoclaro.android.agent.runtime.screen.ScreenNavigationCommand
import com.ojoclaro.android.agent.runtime.screen.ScreenNavigationCommandParser
import com.ojoclaro.android.agent.runtime.screen.ScreenScrollOutcome
import com.ojoclaro.android.agent.runtime.screen.ScreenUnderstandingResult
import com.ojoclaro.android.agent.runtime.screen.ScreenUnderstandingUseCase
import com.ojoclaro.android.agent.runtime.conversation.ConversationGate
import com.ojoclaro.android.agent.runtime.conversation.ConversationShortMemory
import com.ojoclaro.android.agent.runtime.conversation.EstelaCompanionPhrases
import com.ojoclaro.android.llm.EstelaConversationClient
import com.ojoclaro.android.speech.EstelaEarcons
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppChatListPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppChatListResponse
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppMessageReadPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppMessagesResponse
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppReadAloudPhrases
import com.ojoclaro.android.notifications.WhatsAppNotificationQueryPhrases
import com.ojoclaro.android.notifications.WhatsAppNotificationQueryResponder
import com.ojoclaro.android.notifications.WhatsAppNotificationStore
import com.ojoclaro.android.notifications.WhatsAppNotificationFilter
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVisibleChatsReader
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVisibleMessagesReader
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppSmartComposeParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVoiceSendPhrases
import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.EstelaTaskIntentRouter
import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCallPhrases
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.apps.AndroidInstalledAppResolver
import com.ojoclaro.android.agent.apps.AndroidSafeAppStarter
import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.agent.apps.SafeAppLaunchResult
import com.ojoclaro.android.agent.apps.SafeAppLauncher
import com.ojoclaro.android.agent.apps.toCommandResult
import com.ojoclaro.android.agent.intelligence.ActiveApp
import com.ojoclaro.android.agent.intelligence.ActiveAppResolver
import com.ojoclaro.android.agent.intelligence.ContactResolution
import com.ojoclaro.android.agent.intelligence.ContactResolver
import com.ojoclaro.android.agent.intelligence.RawScreen
import com.ojoclaro.android.agent.intelligence.ReasonerNode
import com.ojoclaro.android.agent.intelligence.ResolutionSource
import com.ojoclaro.android.agent.intelligence.ResolvedContact
import com.ojoclaro.android.agent.intelligence.ScreenIntelligenceAliases
import com.ojoclaro.android.agent.intelligence.ScreenIntelligenceNarrator
import com.ojoclaro.android.agent.intelligence.ScreenIntelligencePhrases
import com.ojoclaro.android.agent.intelligence.ScreenIntent
import com.ojoclaro.android.agent.intelligence.ScreenModel
import com.ojoclaro.android.agent.intelligence.ScreenReasoner
import com.ojoclaro.android.agent.intelligence.TargetApp
import com.ojoclaro.android.agent.intelligence.UberCopilotNarrator
import com.ojoclaro.android.agent.intelligence.UberCopilotPhrases
import com.ojoclaro.android.agent.intelligence.UberIntent
import com.ojoclaro.android.agent.intelligence.UberScreenModel
import com.ojoclaro.android.agent.intelligence.UberScreenReasoner
import com.ojoclaro.android.agent.runtime.screen.AndroidAccessibilityScreenContextProvider
import com.ojoclaro.android.agent.runtime.whatsapp.VisibleChatOpenResult
import com.ojoclaro.android.agent.runtime.whatsapp.VisibleScreenCommand
import com.ojoclaro.android.agent.runtime.whatsapp.VisibleScreenCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppControlPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppOrdinalChatOpenUseCase
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppOrdinalChatParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppOrdinalChatResponse
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppAnxietyPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppReplyConfirmationResolver
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppReplyPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppScreenDetector
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppStateNarrator
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppBlindRoute
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppBlindIntent
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppBlindRouteNarrator
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDestination
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCriticalGuard
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppLabelMatcher
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppMessageClarifierPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppMediaCallRefusalPhrases
import java.util.concurrent.atomic.AtomicInteger
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDestinationSource
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDestinationConfidence
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDestinationVerifier
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppRelationshipComposeParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppFeatureFlags
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppActionAudit
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppActionCatalog
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppActionType
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppActionGate
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppConversationContext
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDangerousCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDangerousIntent
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppForbiddenCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppForbiddenActionNarrator
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppRelationshipAlias
import com.ojoclaro.android.voice.VoiceFluencyPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCapabilityMatrix
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCapabilityNarrator
import com.ojoclaro.android.onboarding.AccessibilityOnboardingNarrator
import com.ojoclaro.android.onboarding.AccessibilityOnboardingPhrases
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
import com.ojoclaro.android.memory.RelationshipContactStore
import com.ojoclaro.android.model.AppState
import com.ojoclaro.android.phone.PhoneActionExecutor
import com.ojoclaro.android.speech.SpeechController
import com.ojoclaro.android.ui.home.TTS_TO_MIC_DELAY_MILLIS
import com.ojoclaro.android.voice.AndroidSpeechInputEngine
import com.ojoclaro.android.voice.SpeechListeningMode
import com.ojoclaro.android.voice.VoiceCommandController
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import com.ojoclaro.android.voice.VoiceListeningState
import com.ojoclaro.android.voice.VoicePhraseNormalizer
import com.ojoclaro.android.voice.WakeWordStripper
import com.ojoclaro.android.voice.VoiceRetryHandle
import com.ojoclaro.android.voice.VoiceRetryScheduler
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class GlobalAssistantService : Service() {

    // Blind Safety (Fix F): backstop de feedback. Si CUALQUIER coroutine del turno
    // de voz lanza una excepción no atrapada, el usuario no vidente JAMÁS queda
    // mudo: hablamos un fallback claro en vez de silencio.
    private val voiceTurnExceptionHandler = CoroutineExceptionHandler { _, t ->
        logBackground("voiceTurn uncaught=${t.javaClass.simpleName}")
        runCatching {
            speak("Tuve un problema y no pude completar eso. No toqué nada. Probá de nuevo.", force = true)
        }
    }
    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + voiceTurnExceptionHandler)
    private val contextState = ExternalConversationContext()
    private val intentParser = LocalIntentParser()
    private val screenContextProvider = AndroidAccessibilityScreenContextProvider()
    private val whatsAppScreenDetector = WhatsAppScreenDetector()
    private val visibleChatMatcher = WhatsAppVisibleChatMatcher()
    // Fase 2A.1: lectura de pantalla/chats/mensajes también desde el modo
    // continuación (Estela en background, WhatsApp adelante). Mismos use cases
    // que el HomeViewModel; verbal-only, nunca tocan botones ni envían.
    private val backgroundAccessibilityReady: () -> Boolean = { OjoClaroAccessibilityService.isConnected() }
    private val screenUnderstandingUseCase = ScreenUnderstandingUseCase(
        provider = screenContextProvider,
        isAccessibilityReady = backgroundAccessibilityReady
    )
    private val whatsAppVisibleChatsReader = WhatsAppVisibleChatsReader(
        provider = screenContextProvider,
        isAccessibilityReady = backgroundAccessibilityReady
    )
    private val whatsAppVisibleMessagesReader = WhatsAppVisibleMessagesReader(
        provider = screenContextProvider,
        isAccessibilityReady = backgroundAccessibilityReady
    )
    private val whatsAppOrdinalChatOpenUseCase = WhatsAppOrdinalChatOpenUseCase(
        provider = screenContextProvider,
        isAccessibilityReady = backgroundAccessibilityReady
    )

    // V1.10.3 — "ayudame con esta pantalla"/"qué hago ahora" en cualquier app:
    // advisor puro (jamás clickea) sobre el snapshot estructurado del momento.
    private val gasStructuredSnapshotBuilder by lazy {
        com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshotBuilder()
    }
    private val gasNextStepAdvisor by lazy {
        com.ojoclaro.android.agent.core.screen.NextStepAdvisor()
    }

    private lateinit var notifier: GlobalAssistantNotifier
    private lateinit var overlayController: GlobalAssistantOverlayController
    private lateinit var speechController: SpeechController
    private lateinit var voiceController: VoiceCommandController
    private lateinit var orchestrator: AssistantOrchestrator
    private lateinit var whatsAppIntentHelper: WhatsAppIntentHelper
    private lateinit var phoneActionExecutor: PhoneActionExecutor
    private lateinit var mapsActionExecutor: MapsActionExecutor

    private var accessibilityOverlayVoiceSingleShot: Boolean = false
    private var appOverlayEnabled: Boolean = true

    // Hardening Alexa-like: último estado de voz logueado (para VOICE_STATE_CHANGED
    // old=...->new=...). Solo diagnóstico, sin contenido del usuario.
    private var lastLoggedVoiceState: VoiceListeningState? = null

    // V1.2 — continuidad conversacional: cuántas re-escuchas automáticas
    // lleva el turno single-shot actual (solo cuando Estela preguntó algo).
    private var overlayFollowUpTurns: Int = 0

    // V1.2 — envío seguro: el borrador EXACTO que Estela leyó en voz alta y
    // espera "enviá" o "cancelar". Nunca se loguea su contenido.
    private var pendingWhatsAppSendDraft: String? = null

    /**
     * Full Control Hardening — flags de acciones PELIGROSAS de WhatsApp. TODAS
     * en false: por defecto Estela NUNCA toca enviar/llamar/videollamar/audio
     * real. Habilitar es un cambio deliberado (código + autorización), jamás por
     * voz. Vive sólo en runtime y vuelve a DISABLED en cada arranque.
     */
    private val whatsAppFlags: WhatsAppFeatureFlags = WhatsAppFeatureFlags.DISABLED

    /**
     * V1.8 — confirmación de contacto ANTES de preparar el mensaje.
     * El número completo vive solo acá en memoria: por voz se dicen los
     * últimos 4 dígitos salvo pedido explícito; jamás se loguea.
     */
    private data class PendingContactConfirmation(
        val recipientQuery: String,
        val displayName: String,
        val phoneE164: String,
        val messageDraft: String?,
        val options: List<com.ojoclaro.android.phone.ContactCandidate> = emptyList(),
        val awaitingMessage: Boolean = false,
        // "mandá un mensaje diciendo X" sin destinatario, o destinatario no
        // encontrado: el próximo texto es un nombre o un número dictado.
        val awaitingRecipient: Boolean = false
    )

    private var pendingContactConfirmation: PendingContactConfirmation? = null

    private val smartComposeResolver by lazy {
        com.ojoclaro.android.phone.MemoryContactResolver(LocalMemoryStore(this))
    }

    /**
     * Trusted Contacts por RELACIÓN ("mi novia" → contacto confiable). Almacén
     * LOCAL y privado (SharedPreferences del paquete). El número vive sólo acá
     * para construir el deep link; jamás se loguea completo ni sale del teléfono.
     */
    private val relationshipStore by lazy { RelationshipContactStore(this) }

    /**
     * V1.10.1 — "¿A dónde querés ir?" después de "dónde estoy": el próximo
     * texto puede ser un destino (ask=true) y, con candidato cargado, se
     * espera el sí/no antes de iniciar la orientación. Mueren con el turno.
     */
    private var pendingOutdoorDestinationAsk: Boolean = false
    private var pendingOutdoorDestinationCandidate: String? = null

    /**
     * V1.10.2 — oferta de ABRIR una app de movilidad (Uber/Cabify/Maps)
     * esperando sí/no. Abrir una app no pide, no confirma y no paga nada:
     * por eso un "sí" simple alcanza acá (el contrato de envío de WhatsApp
     * y el de viajes no se tocan: Estela jamás confirma un viaje).
     * El destino es texto hablado y nunca se loguea.
     */
    private data class PendingMobilityOpen(
        val appLabel: String,
        val packageName: String,
        val destination: String?,
        val isRideApp: Boolean
    )

    private var pendingMobilityOpen: PendingMobilityOpen? = null

    /**
     * Sprint WhatsApp: chat resuelto por ordinal ("abrí el primer chat"),
     * esperando confirmación para abrirlo. Abrir un chat es navegación
     * reversible (no envía nada), así que un "sí"/"abrilo" alcanza. Guarda solo
     * el displayName resuelto; muere con el turno y con todo stop.
     */
    private var pendingOrdinalChatOpen: String? = null

    /**
     * Trusted Contacts — vinculación de relación esperando confirmación. Cuando
     * la persona dice "este contacto es mi novia", Estela lee el número visible
     * del chat/perfil y CONFIRMA por los últimos 4 antes de guardar (así no se
     * guarda un número equivocado leído de un mensaje). El número completo vive
     * sólo acá en memoria; jamás se loguea entero. Muere con el turno y con stop.
     */
    private data class PendingRelationshipLink(
        val key: String,
        val label: String,
        val phoneE164: String
    ) {
        val phoneEnding: String get() = phoneE164.filter(Char::isDigit).takeLast(4)
    }

    private var pendingRelationshipLink: PendingRelationshipLink? = null

    /**
     * Sprint WhatsApp WA-5: respuesta en el chat abierto con DOBLE confirmación.
     * Guarda el texto ya escrito en el campo y en qué paso de confirmación va
     * (1 = "¿querés enviarlo?", 2 = "¿lo mando ahora?"). En DRY-RUN el paso 2
     * NUNCA envía. Muere con el turno y con todo stop.
     */
    private var pendingWhatsAppReply: PendingWhatsAppReply? = null

    private data class PendingWhatsAppReply(val text: String, val awaitingStep: Int)

    /**
     * V1.11 — botón de videollamada anunciado, esperando sí/no. Tocar el
     * botón inicia una llamada (reversible: se corta), por eso un "sí"
     * alcanza acá. El contrato de ENVÍO de mensajes no se toca: "sí" jamás
     * envía texto. Muere con el turno y con todo stop.
     */
    private var pendingVideoCallTap: Boolean = false

    /**
     * V1.12 — Instagram Direct. Mismo contrato dual que WhatsApp:
     *  - texto: SOLO confirmación fuerte ("mandalo"/"enviá"/"confirmo");
     *    "sí"/"dale" a secas se rechazan SIEMPRE;
     *  - videollamada: "sí"/"dale"/"tocá" confirman el toque (reversible).
     * Ambos pendientes vencen a los 3 minutos, mueren con el turno, con todo
     * stop y al cancelar. El contenido del borrador jamás se loguea.
     */
    private data class PendingInstagramSend(
        val contactLabel: String,
        val draft: String,
        val armedAtMillis: Long
    )

    private var pendingInstagramSend: PendingInstagramSend? = null
    private var pendingInstagramVideoCallArmedAt: Long? = null

    /**
     * V1.13 — Camera Assist. La cámara es un MODO (como el monitoreo de
     * viaje), no un pending: la maneja el controller con su propio
     * lifecycle, el FGS se promociona a microphone|camera mientras está
     * abierta, y se cierra con "cerrá la cámara", con todo stop y al morir
     * el servicio. Los frames viven solo en memoria; jamás se persisten.
     */
    private var cameraAssistRunning: Boolean = false
    private var cameraWatchJob: Job? = null
    private var cameraScanStartedAtMillis: Long = 0L

    private val cameraAssistControllerDelegate = lazy { CameraAssistController(this) }
    private val cameraAssistController by cameraAssistControllerDelegate

    private val cameraAssistSession by lazy {
        CameraAssistSession(
            nowMillis = { SystemClock.elapsedRealtime() },
            sensitivePredicate = WhatsAppVoiceSendPhrases::looksSensitive
        )
    }

    private val cameraSceneDescriber by lazy {
        BackendSceneDescriber(
            capturer = object : OutdoorSceneCapturer {
                override suspend fun captureSingleJpeg(timeoutMillis: Long): ByteArray? =
                    cameraAssistController.captureSceneJpeg(timeoutMillis)

                override fun hasCameraPermission(): Boolean =
                    cameraAssistController.hasCameraPermission()
            },
            config = agentLlmConfig,
            networkClient = HttpUrlConnectionLlmAgentNetworkClient(),
            encodeBase64 = { bytes ->
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            },
            log = { message -> logBackground("cameraScene $message") }
        )
    }

    /**
     * V1.14 — señales que alimentan la presencia animada del botón flotante.
     * El estado visual de reposo se deriva con AssistantPresenceStateMapper;
     * el WARNING es un destello transitorio que revierte al estado vigente.
     */
    private var presenceSignals = AssistantPresenceStateMapper.Signals()
    private var presenceWarningJob: Job? = null

    /** V1.11 — monitoreo honesto del viaje: solo lee la pantalla visible. */
    private var rideMonitorJob: Job? = null
    private var rideMonitorLastStatusIndex: Int = -1

    private val gasOutdoorRouteProvider by lazy {
        com.ojoclaro.android.outdoor.BackendOutdoorRouteProvider(
            config = agentLlmConfig,
            networkClient = HttpUrlConnectionLlmAgentNetworkClient()
        )
    }


    /** Estela hizo una pregunta y espera respuesta: confirmación o slot-fill. */
    private fun awaitingFollowUp(): Boolean {
        val snapshot = contextState.current
        return snapshot.active &&
            (
                snapshot.pendingConfirmation != null ||
                    snapshot.agentState in EXPECTING_STATES ||
                    pendingWhatsAppSendDraft != null ||
                    pendingWhatsAppReply != null ||
                    pendingOrdinalChatOpen != null ||
                    pendingContactConfirmation != null ||
                    pendingOutdoorDestinationAsk ||
                    pendingOutdoorDestinationCandidate != null ||
                    pendingMobilityOpen != null ||
                    pendingVideoCallTap ||
                    pendingInstagramSend != null ||
                    pendingInstagramVideoCallArmedAt != null
                )
    }

    // --- Outdoor Guidance v1 (Fase 3B) ---
    private val outdoorLocationEngine by lazy { AndroidOutdoorLocationEngine(this) }
    private val outdoorLocationReader by lazy { OutdoorLocationReader(outdoorLocationEngine) }

    // --- Estela Agent Core v1 (Fase 3A) ---
    private var agentMissionJob: Job? = null
    private var agentMissionReply: CompletableDeferred<String?>? = null
    private val agentLlmConfig: LlmAgentClientConfig by lazy { LlmAgentClientConfig.fromBuildConfig() }
    private val agentObservationBuilder: AgentObservationBuilder by lazy {
        AgentObservationBuilder(
            readPackageName = { OjoClaroAccessibilityService.readActivePackageName() },
            isAccessibilityConnected = { OjoClaroAccessibilityService.isConnected() }
        )
    }
    private val agentCoordinator: AgentSessionCoordinator by lazy {
        AgentSessionCoordinator(
            planner = HttpAgentPlannerClient(
                config = agentLlmConfig,
                networkClient = HttpUrlConnectionLlmAgentNetworkClient()
            ),
            registry = AgentToolRegistry(),
            policyGate = AgentPolicyGate(),
            executor = AndroidAgentToolExecutor(
                isAccessibilityConnected = { OjoClaroAccessibilityService.isConnected() },
                isOverlayAttached = { OjoClaroAccessibilityService.isOverlayAttached() },
                isMicrophoneGranted = { hasRecordAudioPermission() },
                checkBackendHealth = ::missionBackendHealth,
                currentPackage = { OjoClaroAccessibilityService.readActivePackageName() },
                openAppById = ::missionOpenAppById,
                openPackage = ::missionLaunchPackage,
                readScreenLocal = ::missionReadScreenLocal,
                speakText = { text -> speak(text, force = true) },
                performBack = { OjoClaroAccessibilityService.performGlobalBack() },
                performScroll = { forward ->
                    OjoClaroAccessibilityService.scrollVisibleContainer(forward).name
                },
                // Outdoor Guidance v1: las coordenadas exactas nunca entran al
                // resumen; solo buckets (privacidad por construcción).
                hasLocationPermission = { outdoorLocationEngine.hasPermission() },
                locationServicesEnabled = { outdoorLocationEngine.servicesEnabled() },
                readLocationSummary = {
                    when (val result = outdoorLocationReader.read()) {
                        is OutdoorFixResult.Valid -> AgentOutdoorFixSummary(
                            available = true,
                            accuracyBucket = result.fix.accuracyBucket,
                            ageBucket = result.fix.ageBucket,
                            provider = result.fix.provider
                        )
                        OutdoorFixResult.PermissionMissing ->
                            AgentOutdoorFixSummary(false, "unknown", "unknown", "none", "permission_missing")
                        OutdoorFixResult.ServicesDisabled ->
                            AgentOutdoorFixSummary(false, "unknown", "unknown", "none", "services_disabled")
                        OutdoorFixResult.Unavailable ->
                            AgentOutdoorFixSummary(false, "unknown", "unknown", "none", "unavailable")
                        is OutdoorFixResult.TooOld -> AgentOutdoorFixSummary(
                            false, result.fix.accuracyBucket, result.fix.ageBucket,
                            result.fix.provider, "too_old"
                        )
                        is OutdoorFixResult.TooInaccurate -> AgentOutdoorFixSummary(
                            false, result.fix.accuracyBucket, result.fix.ageBucket,
                            result.fix.provider, "too_inaccurate"
                        )
                    }
                },
                describeLocationAloud = {
                    val result = outdoorLocationReader.read()
                    speak(outdoorLocationReader.spokenLocationText(result), force = true)
                    true
                },
                startOutdoorGuidance = { destination ->
                    OutdoorForegroundService.startGuidance(this, destination)
                    // Poscondición observada: la guía realmente quedó activa.
                    var waited = 0L
                    while (waited < 15_000L && !OutdoorForegroundService.isGuidanceActive()) {
                        delay(500L)
                        waited += 500L
                    }
                    OutdoorForegroundService.isGuidanceActive()
                },
                outdoorGuidanceActive = { OutdoorForegroundService.isGuidanceActive() },
                speakRouteProgress = {
                    OutdoorForegroundService.repeatInstruction(this)
                    true
                },
                stopOutdoorGuidance = {
                    OutdoorForegroundService.stopGuidance(this)
                    var waited = 0L
                    while (waited < 5_000L && OutdoorForegroundService.isGuidanceActive()) {
                        delay(250L)
                        waited += 250L
                    }
                    !OutdoorForegroundService.isGuidanceActive()
                },
                describeSceneAloud = {
                    OutdoorForegroundService.describeAhead(this)
                    true
                }
            ),
            observe = { agentObservationBuilder.build() },
            speak = { text -> speak(text, force = true) },
            requestUserReply = ::awaitAgentMissionUserReply,
            log = ::logAgentCore,
            elapsedRealtime = { SystemClock.elapsedRealtime() }
        )
    }

    override fun onCreate() {
        super.onCreate()
        notifier = GlobalAssistantNotifier(this)
        whatsAppIntentHelper = WhatsAppIntentHelper(this)
        phoneActionExecutor = PhoneActionExecutor(this)
        mapsActionExecutor = MapsActionExecutor(this)
        orchestrator = AssistantOrchestrator(
            capabilityRegistry = CapabilityRegistry(this),
            memoryStore = LocalMemoryStore(this),
            locationProvider = LocationProvider(this),
            // V1.10.1 — sin esto, la política veía "unavailable" (default) y
            // "abrí whatsapp" en background terminaba en pregunta guiada sin
            // continuación, en vez de abrir la app (bug real del Moto).
            globalAssistantCapabilityProvider = {
                GlobalAssistantCapabilityGate(this).evaluate()
            }
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
                // Hardening Alexa-like: TTS arranca → STT se pausa SIEMPRE para
                // que el micrófono no capture la propia voz de Estela.
                logBackground("TTS_STARTED")
                logBackground("STT_PAUSED_FOR_TTS")
                serviceScope.launch { voiceController.pauseForSpeech() }
            },
            onSpeechFinished = {
                // V1.14 — terminó de hablar: la presencia vuelve al reposo
                // (CAMERA_ACTIVE si la cámara sigue abierta, si no IDLE).
                logBackground("TTS_COMPLETED")
                updatePresence { it.copy(speaking = false) }
                serviceScope.launch {
                    delay(TTS_TO_MIC_DELAY_MILLIS)
                    when {
                        // Durante una misión del Agent Core el ciclo de vida lo
                        // maneja el coordinator: ni completar turno ni re-escuchar.
                        agentCoordinator.isActive -> Unit
                        // V1.2 — continuidad: si Estela acaba de hacer una
                        // pregunta (confirmación pendiente o slot-fill), el
                        // turno single-shot NO se cierra: re-escucha la
                        // respuesta. Presupuesto acotado para no reabrir el
                        // micrófono infinitamente.
                        accessibilityOverlayVoiceSingleShot &&
                            awaitingFollowUp() &&
                            overlayFollowUpTurns < MAX_OVERLAY_FOLLOW_UP_TURNS -> {
                            overlayFollowUpTurns += 1
                            logOverlayVoice(
                                "followUpListen=true turn=$overlayFollowUpTurns " +
                                    "finalState=LISTENING"
                            )
                            resumeListeningIfActive()
                        }
                        accessibilityOverlayVoiceSingleShot ->
                            completeOverlayVoiceTurn("tts_completed")
                        else -> resumeListeningIfActive()
                    }
                }
            },
            onSpeechStopped = {
                logBackground("TTS_STOPPED")
                updatePresence { it.copy(speaking = false) }
                serviceScope.launch {
                    when {
                        agentCoordinator.isActive -> Unit
                        accessibilityOverlayVoiceSingleShot ->
                            completeOverlayVoiceTurn("tts_stopped")
                        else -> resumeListeningIfActive()
                    }
                }
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
            onStateChanged = ::onVoiceStateChanged,
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
            GlobalAssistantMode.ACTION_OVERLAY_VOICE_ENTRYPOINT -> startOverlayVoiceEntryPoint(intent)
            GlobalAssistantMode.ACTION_LISTEN -> listenNow()
            GlobalAssistantMode.ACTION_SILENCE -> silence()
            GlobalAssistantMode.ACTION_STOP -> stopMode()
            ACTION_AGENT_MISSION_CANCEL -> cancelAgentMission("user_button")
            ACTION_DEBUG_AGENT_MISSION -> {
                // QA físico (solo debug): inyecta un goal sin pasar por STT.
                if (BuildConfig.DEBUG) {
                    val goal = intent.getStringExtra(EXTRA_AGENT_GOAL).orEmpty()
                    startAgentMissionFromDebug(goal)
                }
            }
            ACTION_DEBUG_VOICE_TEXT -> {
                // QA físico (solo debug): simula texto reconocido por STT y lo
                // pasa por el routing REAL (outdoor fast path, misiones, etc.).
                if (BuildConfig.DEBUG) {
                    val text = intent.getStringExtra(EXTRA_AGENT_GOAL).orEmpty().take(500)
                    if (text.isNotBlank()) {
                        if (!contextState.current.active) {
                            accessibilityOverlayVoiceSingleShot = true
                            appOverlayEnabled = false
                            val snapshot = contextState.start(
                                externalApp = ExternalAppName.fromPackageName(
                                    OjoClaroAccessibilityService.readActivePackageName()
                                ),
                                reason = "debug_voice_text",
                                returnHint = "",
                                agentState = null
                            )
                            startForegroundSafely(snapshot)
                        }
                        logAgentCore("debugVoiceTextInjected=true len=${text.length}")
                        serviceScope.launch { handleRecognizedText(text) }
                    }
                }
            }
            else -> listenNow()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        agentMissionJob?.cancel()
        agentMissionJob = null
        missionActiveFlag = false
        cameraWatchJob?.cancel()
        cameraWatchJob = null
        cameraAssistRunning = false
        if (cameraAssistControllerDelegate.isInitialized()) {
            runCatching { cameraAssistController.release() }
        }
        voiceController.destroy()
        speechController.shutdown()
        overlayController.hide()
        notifier.cancel()
        super.onDestroy()
    }

    private fun startContinuation(intent: Intent) {
        accessibilityOverlayVoiceSingleShot = false
        appOverlayEnabled = true
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
        logBackground(
            "continuation start externalApp=${externalApp.name} expectWhatsApp=$expectWhatsAppAction " +
                "micPermission=${hasRecordAudioPermission()} accessibility=${backgroundAccessibilityReady()} " +
                "listenDelayMs=$delayMillis"
        )
        serviceScope.launch {
            delay(delayMillis.coerceAtLeast(0L))
            resumeListeningIfActive()
        }
    }

    private fun startOverlayVoiceEntryPoint(intent: Intent) {
        accessibilityOverlayVoiceSingleShot = true
        appOverlayEnabled = false
        overlayFollowUpTurns = 0
        val sourcePackage = intent.getStringExtra(GlobalAssistantMode.EXTRA_SOURCE_PACKAGE_NAME)
        val externalApp = ExternalAppName.fromPackageName(sourcePackage)
        val snapshot = contextState.start(
            externalApp = externalApp,
            reason = "accessibility_overlay_voice",
            returnHint = "",
            agentState = null
        )
        startForegroundSafely(snapshot)
        logOverlayVoice(
            "activityOpened=false listeningStartedRequested=true " +
                "targetPackage=${sanitizeTraceToken(sourcePackage ?: "-")} " +
                "externalApp=${externalApp.name} duplicateRequest=${voiceController.isListening}"
        )

        val delayMillis = intent.getLongExtra(
            GlobalAssistantMode.EXTRA_START_LISTENING_DELAY_MS,
            OVERLAY_VOICE_DEFAULT_START_DELAY_MS
        )
        serviceScope.launch {
            delay(delayMillis.coerceAtLeast(0L))
            listenNow()
        }
    }

    private fun startForegroundSafely(snapshot: ExternalConversationSnapshot) {
        val notification = notifier.build(snapshot)
        // V1.13 — con la cámara de Estela abierta el foreground declara
        // también el tipo camera (mismo patrón on-demand que Outdoor).
        val foregroundType = if (cameraAssistRunning) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    GlobalAssistantMode.NOTIFICATION_ID,
                    notification,
                    foregroundType
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
            logBackground("listen blocked: no mic permission")
            if (accessibilityOverlayVoiceSingleShot) {
                logOverlayVoice("activityOpened=false listeningStarted=false reason=mic_permission_missing")
            }
            speak(GlobalAssistantMode.BACKGROUND_MIC_FALLBACK, force = true)
            return
        }
        logBackground("listen start (foreground service mic)")
        if (accessibilityOverlayVoiceSingleShot) {
            logOverlayVoice("activityOpened=false listeningStarted=true finalState=LISTENING")
        }
        // V1.14 — Estela escuchando.
        updatePresence { it.copy(listening = true, processing = false, speaking = false) }
        voiceController.setExpectingResponse(true)
        voiceController.startListening()
    }

    private fun silence() {
        contextState.silence()
        speechController.stop()
        voiceController.pauseListening()
        // V1.14 — callar deja de hablar/escuchar; la cámara (si está) manda.
        updatePresence { it.copy(listening = false, processing = false, speaking = false) }
        // "Callar" durante una misión detiene el TTS pero NO cancela la misión
        // (eso es "Cancelar"). Tampoco completa el turno single-shot.
        if (agentCoordinator.isActive) return
        if (accessibilityOverlayVoiceSingleShot) {
            completeOverlayVoiceTurn("silenced")
        }
    }

    private fun stopMode() {
        logBackground("stop mode (foreground service ending)")
        stopRideMonitor(spoken = false)
        cancelAgentMission("stop_mode", speakConfirmation = false)
        // V1.14 — todo apagado: la presencia vuelve a IDLE (la cámara ya se
        // cierra en el stop de abajo, así que limpiamos todas las señales).
        presenceWarningJob?.cancel()
        presenceWarningJob = null
        updatePresence {
            AssistantPresenceStateMapper.Signals()
        }
        contextState.clear()
        speechController.stop()
        voiceController.stopListening()
        overlayController.hide()
        notifier.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        accessibilityOverlayVoiceSingleShot = false
        appOverlayEnabled = true
        stopSelf()
    }

    private fun resumeListeningIfActive() {
        val snapshot = contextState.current
        if (!snapshot.active) return
        if (!hasRecordAudioPermission()) {
            speak(GlobalAssistantMode.BACKGROUND_MIC_FALLBACK, force = true)
            return
        }
        // V1.14 — re-escucha tras hablar: volvemos a LISTENING.
        updatePresence { it.copy(listening = true, processing = false, speaking = false) }
        voiceController.setExpectingResponse(true)
        logBackground("STT_RESUMED_AFTER_TTS")
        voiceController.resumeAfterSpeech()
    }

    /**
     * Hardening Alexa-like: log de la máquina de estados conversacional. Sin
     * contenido del usuario, solo el nombre del estado (IDLE/LISTENING/
     * PROCESSING/SPEAKING/WAITING_RETRY/...). Permite verificar con evidencia
     * que el micrófono y la voz no se pisan.
     */
    private fun onVoiceStateChanged(state: VoiceListeningState) {
        val previous = lastLoggedVoiceState
        lastLoggedVoiceState = state
        logBackground("VOICE_STATE_CHANGED old=${previous?.name ?: "NONE"} new=${state.name}")
    }

    private fun resumeOrCompleteOverlayVoiceTurn() {
        if (accessibilityOverlayVoiceSingleShot) {
            completeOverlayVoiceTurn("silent_outcome")
        } else {
            resumeListeningIfActive()
        }
    }

    /**
     * Blind Safety (#14) — generación monotónica del compose diferido
     * (open→verify→draft). Sube al lanzar un compose y en cada cancelación/STOP/
     * cierre de turno; la corutina diferida compara su generación capturada antes
     * de escribir el borrador o armar el pending, así un cancel durante la ventana
     * async aborta la escritura y el re-armado (TOCTOU).
     */
    private val whatsAppComposeGeneration = AtomicInteger(0)

    /** #14 — invalida cualquier compose diferido en vuelo. Devuelve la nueva gen. */
    private fun invalidateInFlightWhatsAppCompose(): Int =
        whatsAppComposeGeneration.incrementAndGet()

    /**
     * Blind Safety (#11/#13) — si Estela dejó un borrador PROPIO escrito en el chat
     * (hay un pending de respuesta/envío), lo limpia ANTES de soltar el pending:
     * nunca se deja texto sin enviar y sin aviso. SEGURO: sólo borra lo que Estela
     * misma escribió (existía un pending); no envía, no toca chats de terceros.
     */
    private fun clearOwnWhatsAppDraftIfPending() {
        if (pendingWhatsAppReply != null || pendingWhatsAppSendDraft != null) {
            runCatching { OjoClaroAccessibilityService.setWhatsAppDraft("") }
            logBackground("WHATSAPP_OWN_DRAFT_CLEARED reason=teardown_or_cancel")
        }
    }

    private fun completeOverlayVoiceTurn(reason: String) {
        if (!accessibilityOverlayVoiceSingleShot) return
        // #11/#13/#14: al cerrar el turno, invalidá cualquier compose en vuelo y
        // limpiá el borrador propio ANTES de soltar los pendings (no dejar texto
        // tipeado sin aviso, ni que una corutina diferida lo re-escriba después).
        invalidateInFlightWhatsAppCompose()
        clearOwnWhatsAppDraftIfPending()
        val ttsCompleted = reason == "tts_completed"
        logOverlayVoice(
            "activityOpened=false ttsCompleted=$ttsCompleted " +
                "reason=$reason followUpTurns=$overlayFollowUpTurns finalState=IDLE"
        )
        overlayFollowUpTurns = 0
        // Un envío que quedó sin confirmar muere con el turno: jamás puede
        // sobrevivir a un cierre y ejecutarse después. (La oferta del hub de
        // movilidad SÍ sobrevive con vencimiento corto: solo abre apps.)
        pendingWhatsAppSendDraft = null
        pendingWhatsAppReply = null
        pendingContactConfirmation = null
        pendingMobilityOpen = null
        pendingOrdinalChatOpen = null
        pendingVideoCallTap = false
        pendingInstagramSend = null
        pendingInstagramVideoCallArmedAt = null
        stopCameraAssist(spoken = false)
        clearOutdoorDestinationAsk()
        accessibilityOverlayVoiceSingleShot = false
        appOverlayEnabled = true
        contextState.clear()
        voiceController.stopListening()
        overlayController.hide()
        notifier.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onVoiceFinalText(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        // No logueamos el texto: solo confirmamos que llegó audio reconocido.
        logBackground("final text received len=${cleanText.length} (mic alive in background)")
        if (accessibilityOverlayVoiceSingleShot) {
            logOverlayVoice(
                "activityOpened=false finalTextReceived=true localBeforeRemoteEvaluated=true " +
                    "duplicateRequest=false finalState=PROCESSING"
            )
        }
        serviceScope.launch { handleRecognizedText(cleanText) }
    }

    private fun onVoiceError(message: String) {
        val snapshot = contextState.current
        if (!snapshot.active) return
        logBackground("voice error active=true expecting=${snapshot.agentState in EXPECTING_STATES}")
        if (accessibilityOverlayVoiceSingleShot) {
            logOverlayVoice("activityOpened=false sttError=true finalState=SPEAKING")
            speak(GlobalAssistantMode.BACKGROUND_MIC_FALLBACK, force = true)
            return
        }
        if (snapshot.agentState in EXPECTING_STATES) {
            serviceScope.launch {
                delay(800L)
                resumeListeningIfActive()
            }
        } else {
            speak(GlobalAssistantMode.BACKGROUND_MIC_FALLBACK, force = true)
        }
    }

    private suspend fun handleRecognizedText(rawRecognizedText: String) {
        if (contextState.current.active.not()) return
        contextState.touch()
        // V1.14 — llegó texto reconocido: dejamos de escuchar y pasamos a
        // PENSAR mientras se rutea (local o backend). El SPEAKING llega solo
        // cuando speak() arranca; si la rama no habla, vuelve al reposo.
        updatePresence { it.copy(listening = false, processing = true) }

        // V1.4 — "Hola Estela, ..." / "Estela, ..." se limpia ANTES de todo
        // el routing: los comandos y la charla reciben la frase real.
        // V1.11 — después, la capa coloquial argentina canoniza frases reales
        // ("fijate qué dice" → "lee la pantalla", "garpar" → "pagar").
        val text = EstelaColloquialNormalizer.normalize(WakeWordStripper.strip(rawRecognizedText))

        // Hardening Alexa-like: auditoría de routing (sin contenido, solo
        // longitud) para diagnosticar a qué ruta cayó cada turno.
        logBackground("ROUTING_AUDIT len=${text.length} agentActive=${agentCoordinator.isActive}")
        logWhatsAppContextDiag()

        // Misión del Agent Core en curso: el texto reconocido es para la misión
        // (respuesta a ask_user o cancelación), nunca para el routing normal.
        if (agentCoordinator.isActive) {
            if (AgentMissionPhrases.isCancelCommand(text)) {
                cancelAgentMission("user_voice")
                return
            }
            val pendingReply = agentMissionReply
            if (pendingReply != null && !pendingReply.isCompleted) {
                pendingReply.complete(text)
            }
            return
        }

        // WA-5 micro-fix: una CANCELACIÓN sobre un envío PENDIENTE (WA-5 o V1.2)
        // tiene PRIORIDAD sobre el STOP global, para usar la ruta segura que
        // limpia el borrador propio y dice "Cancelado. No envié nada." (el STOP
        // global cancela pero silencia y no limpia el campo). Sin pending —o con
        // barge-in puro ("callate"/"silencio", que no son cancelación)— el STOP
        // global de abajo sigue intacto.
        // #14 (TOCTOU): cualquier cancelación/stop invalida un compose diferido en
        // vuelo (open→verify→draft) AUNQUE el pending todavía no se haya armado, así
        // la corutina diferida no escribe el borrador ni re-arma el pending después.
        if (WhatsAppReplyPhrases.isCancel(text) || WhatsAppVoiceSendPhrases.isCancelSend(text) ||
            isStopModeCommand(text) || VoiceCommandDispatcher.isStopCommand(text)
        ) {
            invalidateInFlightWhatsAppCompose()
        }
        if (pendingWhatsAppReply != null && WhatsAppReplyPhrases.isCancel(text)) {
            if (handlePendingWhatsAppReplyConfirmation(text)) return
        }
        if (pendingWhatsAppSendDraft != null && WhatsAppVoiceSendPhrases.isCancelSend(text)) {
            if (handlePendingWhatsAppSendReply(text)) return
        }

        when {
            isStopModeCommand(text) -> {
                // #11/#13: si Estela tenía un borrador propio escrito, limpialo
                // ANTES de soltar el pending (no dejar texto tipeado sin enviar).
                clearOwnWhatsAppDraftIfPending()
                pendingWhatsAppSendDraft = null
                pendingWhatsAppReply = null
                pendingContactConfirmation = null
                pendingMobilityOpen = null
                pendingOrdinalChatOpen = null
                pendingRelationshipLink = null
                pendingVideoCallTap = false
                pendingInstagramSend = null
                pendingInstagramVideoCallArmedAt = null
                stopCameraAssist(spoken = false)
                OutdoorMobilityFallbackHub.clear()
                clearOutdoorDestinationAsk()
                stopMode()
                return
            }
            VoiceCommandDispatcher.isStopCommand(text) -> {
                // #11/#13: limpiar el borrador propio antes de soltar el pending.
                clearOwnWhatsAppDraftIfPending()
                pendingWhatsAppSendDraft = null
                pendingWhatsAppReply = null
                pendingContactConfirmation = null
                pendingMobilityOpen = null
                pendingOrdinalChatOpen = null
                pendingRelationshipLink = null
                pendingVideoCallTap = false
                pendingInstagramSend = null
                pendingInstagramVideoCallArmedAt = null
                stopCameraAssist(spoken = false)
                OutdoorMobilityFallbackHub.clear()
                clearOutdoorDestinationAsk()
                silence()
                return
            }
        }

        // WhatsApp Fluency: "esperá" (te espero, sin tocar el pendiente) y
        // "te cortaste"/"no me escuchaste" (re-ofrecer / repetir lo último).
        // Read-only y disjuntos de sí/mandalo/cancelar; corren antes de los
        // pendientes para estar SIEMPRE disponibles sin robar una confirmación.
        if (handleVoiceFluencyCommand(text)) return

        // Accessibility Onboarding + capacidades: "activar Estela", "ya activé",
        // "estado", "qué puedo hacer ahora", "qué falta", "por qué no funciona".
        // Funciona SIN Accesibilidad vinculada (solo TTS + abrir Ajustes); guía,
        // verifica al volver y nunca deja al usuario perdido.
        if (handleAccessibilityOnboardingCommand(text)) return

        // WhatsApp Anxiety Hardening: orientación y seguridad SIEMPRE disponibles
        // ("dónde estoy", "qué pasó", "qué puedo hacer acá", "modo seguro").
        // Read-only: no limpia pendientes ni envía. Corre ANTES de los pendientes
        // para funcionar "desde cualquier estado"; sus matchers son disjuntos de
        // sí/mandalo/cancelar, así que jamás roba una confirmación en curso.
        if (handleWhatsAppAnxietyCommand(text)) return

        // WA-5 — respuesta con DOBLE confirmación: si hay un borrador esperando
        // confirmación (paso 1 o 2), esta respuesta es para eso y nada más.
        if (handlePendingWhatsAppReplyConfirmation(text)) return

        // V1.2 — envío seguro: si hay un borrador esperando "enviá"/"cancelar",
        // esta respuesta es para eso y para nada más.
        if (handlePendingWhatsAppSendReply(text)) return

        // Sprint WhatsApp: confirmación de "abrí el primer chat" (sí/abrilo o
        // cancelar). Abrir es reversible, así que un "sí" alcanza; jamás envía.
        if (handlePendingOrdinalChatOpenReply(text)) return

        // Trusted Contacts: confirmación de "este contacto es mi novia". Guardar
        // un vínculo local es reversible y no envía nada, así que un "sí" alcanza.
        // "no"/"cancelar" descarta sin guardar.
        if (handlePendingRelationshipLinkReply(text)) return

        // V1.8 — confirmación de contacto pendiente: la respuesta es para eso.
        if (handlePendingContactReply(text)) return

        // V1.10.1 — respuesta a "¿a dónde querés ir?" (destino + sí/no).
        if (handlePendingOutdoorDestinationReply(text)) return

        // V1.10.2 — respuesta a una oferta de movilidad (abrir Uber/Maps,
        // reintentar ruta, dirección tras geocode fallido). "sí" solo ABRE
        // apps o reintenta rutas: jamás confirma viajes, pagos ni envíos.
        if (handlePendingMobilityReply(text)) return

        // V1.11 — respuesta al "¿toco la videollamada?". "sí" toca el botón
        // de llamada (reversible); el contrato de envío de texto no cambia.
        if (handlePendingVideoCallReply(text)) return

        // V1.12 — respuestas a los pendientes de Instagram. El contrato dual
        // es el mismo del piloto: el texto exige confirmación FUERTE y la
        // videollamada acepta "sí" porque tocar el botón es reversible.
        if (handlePendingInstagramSendReply(text)) return
        if (handlePendingInstagramVideoCallReply(text)) return

        if (isNonConfirmingAffirmative(text) && contextState.current.pendingConfirmation != null) {
            speak("Decime confirmar o cancelar.", force = true)
            return
        }

        // Full Control Hardening: acciones PROHIBIDAS de WhatsApp (borrar/archivar/
        // bloquear/reportar/pagar/foto/archivo/sticker/reenviar/ubicación/link).
        // Corre ANTES de Instagram/tareas/cámara/compose/navegación y del LLM:
        // se rechazan localmente, sin tocar nada, y nunca caen al fallback.
        if (handleWhatsAppForbiddenActionCommand(text)) return

        // Trusted Contacts: vincular ("este contacto es mi novia") y olvidar
        // ("olvidá a mi novia") una relación a un contacto confiable LOCAL.
        // Corre ANTES de Instagram/tareas/cámara/blind/compose/LLM: es percepción
        // + guardado local, jamás envía/llama/paga. La vinculación lee el número
        // de la cabecera del chat y confirma por los últimos 4 antes de guardar.
        if (handleWhatsAppRelationshipCommand(text)) return

        // Trusted Contacts — COMPOSE por relación: "mandale a mi novia que estoy
        // llegando". Corre ANTES de blind-first/reply/smart-compose: resuelve la
        // relación, abre, VERIFICA el destino y recién escribe (doble confirmación).
        // Sin mensaje no reclama (lo toma el flujo de apertura). Nunca envía solo.
        if (handleWhatsAppRelationshipComposeCommand(text)) return

        // V1.12 — Instagram Direct: SOLO frases con marca explícita del canal
        // (el router devuelve null sin marca). Corre ANTES de las tareas
        // V1.11 para que la videollamada de Instagram no la secuestre el
        // flujo de WhatsApp; pagos/tarjetas no entran (el router los deja
        // pasar a la guía segura de abajo).
        if (handleInstagramTaskCommand(text)) return

        // Blind Safety (#4/#5): negativa ESPECÍFICA y LOCAL para videollamada / audio /
        // llamada de WhatsApp. CRÍTICO: corre ANTES del taskIntent viejo de videollamada
        // (handleTaskAssistCommand → handleVideoCallRequest, que buscaba el botón y armaba
        // un tap pendiente). Nunca toca UI, nunca arma tap, nunca LLM. Gateado por contexto
        // WhatsApp (la llamada de voz exige nombrar WhatsApp); Instagram ya corrió arriba.
        if (handleWhatsAppMediaCallRefusal(text)) return

        // V1.11 — tareas asistidas nuevas (videollamada, audio guiado,
        // monitoreo de viaje, guía de pagos). ANTES de outdoor a propósito:
        // "vinculá mercado pago a didi" contiene "didi" y el fast path de
        // transporte la secuestraría.
        if (handleTaskAssistCommand(text)) return

        // V1.13 — Camera Assist: cámara propia, OCR local y escena. ANTES de
        // outdoor a propósito: con la cámara de Estela abierta, "describime
        // qué estoy apuntando" debe usar ESTA cámara (el capturer de outdoor
        // la desbindaría). Sin marca de cámara el parser devuelve null y
        // outdoor/GPS siguen intactos.
        if (handleCameraAssistCommand(text)) return

        // WhatsApp Blind-First: NAVEGACIÓN por voz para no videntes. Corre ANTES
        // de abrir-genérico, ordinal, reply y compose (y del LLM): "abrí WhatsApp
        // con Juan" abre el chat de Juan en vez de WhatsApp a secas, y "respondé
        // el último" abre el chat del remitente en vez de tipear "el último".
        // Solo abre por deep link (sin texto) y verifica; jamás envía.
        if (handleWhatsAppBlindFirstCommand(text)) return

        // Full Control Hardening: contexto de WhatsApp (recuerdo/olvido) y
        // acciones HIGH-RISK por contacto (llamada de voz / audio). Ambas locales
        // y ANTES del LLM. Las peligrosas están BLOQUEADAS por feature flag.
        if (handleWhatsAppContextCommand(text)) return
        if (handleWhatsAppDangerousActionCommand(text)) return

        // Sprint WhatsApp-first: control de WhatsApp (abrir + diagnóstico) como
        // ruta local prioritaria, antes de Uber/Outdoor/agente/fallback.
        if (handleWhatsAppFirstControlCommand(text)) return

        // Sprint WhatsApp: abrir chat por ordinal ("abrí el primer chat"). Ruta
        // local antes de Uber/Outdoor/agente/fallback: nunca cae al LLM.
        if (handleWhatsAppOpenChatOrdinalCommand(text)) return

        // WA-5 read-path: consulta de NOTIFICACIONES de WhatsApp ("¿tengo
        // mensajes nuevos?", "¿quién me escribió?", "leeme las notificaciones de
        // WhatsApp"). Solo LEE el store en memoria del listener (WA-3): no abre
        // WhatsApp, no abre chats, no envía, no responde. Corre ANTES de los
        // lectores de pantalla VISIBLE para que estas frases lean el store y no
        // la UI; nunca cae al LLM/fallback.
        if (handleWhatsAppNotificationQueryCommand(text)) return

        // Mobility Copilot v1 (Uber) — ANTES de Outdoor a propósito: el fast-path
        // de Outdoor reclama CUALQUIER texto con "uber" para ofrecer ABRIR la app,
        // así que "qué dice Uber"/"qué tipo de Uber muestra"/"confirmo pedir Uber
        // ahora"/"cancelá Uber" se interceptan acá para LEER/guiar/frenar. NO toca
        // el botón final, NO pide viaje, NO paga. "abrí Uber"/"pedime un Uber" NO
        // se reclaman: siguen al flujo de apertura seguro de Outdoor.
        if (handleUberCopilotCommand(text)) return

        // Fase 3B: fast path local de Outdoor Guidance. Estas frases nunca
        // van a GPT; el OutdoorForegroundService habla con su propio TTS y
        // este turno single-shot se cierra sin duplicar voz.
        if (handleOutdoorCommand(text)) return

        // Blind Safety (#8): con WhatsApp al frente, una frase que parece CONTENIDO
        // de mensaje o continuación AMBIGUA ("estoy llegando", "decile que sí",
        // "mandale eso", "eso") NO viaja al LLM: se aclara local. Corre DESPUÉS de
        // forbidden/dangerous/trusted-contact (que ya atendieron las acciones
        // explícitas) y ANTES del compose/reply/LLM. El Q&A claro sigue normal.
        if (handleWhatsAppAmbiguousMessageClarifier(text)) return

        // V1.2 — envío seguro y audios de WhatsApp (local, nunca GPT).
        if (handleWhatsAppVoiceSendCommand(text)) return

        // WA-5 — responder en el chat abierto desde voz ("respondé X"): escribe
        // el borrador y pide DOBLE confirmación. Corre ANTES del smart compose
        // (que es compose-con-contacto "mandale a X que Y"). Dry-run: no envía.
        if (handleWhatsAppReplyCommand(text)) return

        // V1.8 — compose inteligente: resuelve contacto y lo confirma ANTES
        // de escribir o enviar nada.
        if (handleSmartCompose(text)) return

        handleVisibleChatPendingConfirmationIfNeeded(text)?.let { outcome ->
            applyOutcome(outcome)
            return
        }

        // V2.2 — inteligencia de pantalla (PERCEPCIÓN + NAVEGACIÓN). Corre
        // DESPUÉS de los pendientes (no los pisa) y ANTES del orquestador, para
        // que "qué personas aparecen"/"qué chat estoy viendo"/"a quién le mando"
        // y "abrí el chat de X" dejen de caer a no_local_match. No envía nada;
        // si no puede verificar, cae al flujo viejo o explica.
        if (handleScreenIntelligenceCommand(text)) return

        // Fase 3A: objetivo compuesto → Agent Core. Los comandos simples
        // ("leé la pantalla", "abrí whatsapp", "volver") NUNCA entran acá:
        // AgentMissionPhrases los excluye y siguen por sus rutas locales.
        if (contextState.current.pendingConfirmation == null &&
            AgentMissionPhrases.isMissionGoal(text)
        ) {
            startAgentMission(text)
            return
        }

        handleContextualWhatsApp(text)?.let { outcome ->
            applyOutcome(outcome)
            return
        }

        // V1.10.4b — "leé los chats"/"leé los mensajes" sin contexto
        // conversacional activo: la ruta RICA de WhatsApp también vale si
        // WhatsApp está adelante (QA real: caía al fallback). Los lectores
        // responden honesto si WhatsApp no está en pantalla.
        if (handleForegroundWhatsAppReadCommand(text)) return

        // V1.10.3 — lectura de pantalla en CUALQUIER app foreground (Maps,
        // DiDi, Ajustes...). Antes solo corría con WhatsApp delante y "leé la
        // pantalla" terminaba sin ruta local (falla real de QA 2026-06-12).
        if (handleGlobalScreenQuery(text)) return

        // Sprint WhatsApp: scroll local (bajá/subí/seguí leyendo/leé más
        // arriba/abajo). Mantiene el foco en la app actual (WhatsApp), usa
        // accesibilidad real y re-lee el nuevo contenido visible. Antes de
        // back/companion/charla: jamás cae al LLM.
        if (handleGlobalScrollCommand(text)) return

        // V1.12.1 — "volver"/"atrás" globales: BACK reversible en cualquier
        // app. Corre DESPUÉS de los flujos contextuales (que ya manejan su
        // propio volver) y de TODOS los pendientes críticos: con un envío o
        // una llamada esperando respuesta, la frase la consume el pending y
        // jamás llega acá.
        if (handleGlobalBackCommand(text)) return

        // Hardening Alexa-like: comandos conversacionales básicos (ayuda,
        // repetir, cancelar) SIEMPRE locales e instantáneos. Corren DESPUÉS de
        // todos los pendientes y flujos contextuales (que ya tuvieron
        // prioridad) y ANTES de compañía/charla libre/fallback: nunca caen al
        // agente ni al LLM ni dan "no entendí".
        if (handleBasicConversationCommand(text)) return

        // V1.3 — capa de compañía: frases exactas de charla/contención.
        // Corre DESPUÉS de todos los comandos locales (visión/GPS/rutas/
        // WhatsApp) y ANTES del fallback: nunca les roba una frase.
        EstelaCompanionPhrases.respond(text)?.let { warmReply ->
            logBackground("routing companion=true sttLength=${text.length}")
            // La compañía también deja hilo en la memoria corta: "¿qué
            // probamos?" después de "estoy nervioso" necesita este contexto.
            conversationMemory.recordUser(text)
            conversationMemory.recordAssistant(warmReply)
            speak(warmReply, force = true)
            return
        }

        // Blind Safety (Fix E): última línea ANTES de cualquier salida a LLM/
        // backend. Una frase WhatsApp-CRÍTICA no atendida por ningún handler local
        // NO debe viajar a /conversation ni al orchestrator.
        if (handleWhatsAppCriticalGuardBeforeLlm(text)) return

        // V1.7 — conversación libre LLM: SOLO frases sin ninguna marca de
        // acción real (ConversationGate sobre-bloquea a propósito). Los
        // comandos locales y pendings ya fueron evaluados arriba.
        if (ConversationGate.isConversational(text)) {
            handleFreeConversation(text)
            return
        }

        // Ninguna ruta local (outdoor/misión/WhatsApp) entendió la frase:
        // queda registrado sin contenido para diagnosticar "no entendí".
        logBackground("routing fallbackReason=no_local_match sttLength=${text.length}")
        val outcome = orchestrator.process(
            rawInput = text,
            pendingConfirmation = contextState.current.pendingConfirmation,
            appState = AppState.EXTERNAL_APP_HANDOFF
        )
        applyOutcome(outcome)
    }

    /**
     * Hardening Alexa-like: los tres comandos conversacionales que SIEMPRE se
     * resuelven local y al instante — AYUDA, REPETIR, CANCELAR — sin tocar
     * agente, LLM ni fallback. Matchers robustos a acentos/voseo. Se evalúa
     * después de TODOS los pendientes/flujos contextuales: con una confirmación
     * o envío en curso, esas capas ya consumieron el turno y esto ni corre.
     */
    private fun handleBasicConversationCommand(text: String): Boolean {
        when {
            VoiceCommandDispatcher.isHelpCommand(text) -> {
                logBackground("routing COMMAND_MATCHED basicCommand=HELP handler=local_help")
                speak(SHORT_HELP, force = true)
                return true
            }
            VoiceCommandDispatcher.isRepeatCommand(text) -> {
                val previous = lastSpokenResponse
                logBackground(
                    "routing COMMAND_MATCHED basicCommand=REPEAT handler=local_repeat " +
                        "available=${previous != null}"
                )
                speak(
                    if (previous != null) "$REPEAT_PREFIX$previous"
                    else "Todavía no dije nada. Decime qué necesitás.",
                    force = true
                )
                return true
            }
            VoiceCommandDispatcher.isBareCancelCommand(text) -> {
                logBackground("routing COMMAND_MATCHED basicCommand=CANCEL handler=local_cancel")
                // Descarta SOLO ofertas suaves (abrir Maps/Uber, destino
                // pendiente). Envíos/llamadas exigen confirmación fuerte y ya
                // pasaron por sus pendientes más arriba.
                pendingMobilityOpen = null
                OutdoorMobilityFallbackHub.clear()
                clearOutdoorDestinationAsk()
                speak("Listo, lo cancelo. Decime qué querés hacer.", force = true)
                return true
            }
        }
        return false
    }

    /**
     * Accessibility Onboarding + capacidades. Atiende SIN Accesibilidad
     * vinculada (solo TTS + abrir Ajustes). Reglas duras: Estela NO activa
     * Accesibilidad sola (Android exige confirmación de una persona); guía, abre
     * Ajustes, verifica al volver y explica qué falta. Logs sanitizados (flags).
     */
    private fun handleAccessibilityOnboardingCommand(text: String): Boolean {
        if (AccessibilityOnboardingPhrases.isActivateRequest(text)) {
            logBackground("ROUTING_AUDIT handler=accessibility_onboarding")
            logBackground("ACCESSIBILITY_ONBOARDING_GUIDANCE bound=${backgroundAccessibilityReady()}")
            speak(AccessibilityOnboardingNarrator.guidance(), force = true)
            val opened = openAccessibilitySettings()
            logBackground("ACCESSIBILITY_SETTINGS_OPENED success=$opened")
            if (!opened) speak(AccessibilityOnboardingNarrator.COULD_NOT_OPEN_SETTINGS, force = true)
            return true
        }
        if (AccessibilityOnboardingPhrases.isReturnConfirmation(text)) {
            val bound = backgroundAccessibilityReady()
            val listed = accessibilityServiceListed()
            logBackground("ROUTING_AUDIT handler=accessibility_onboarding")
            logBackground("ACCESSIBILITY_RETURN_CHECK bound=$bound listed=$listed")
            speak(AccessibilityOnboardingNarrator.returnCheck(serviceListed = listed, bound = bound), force = true)
            return true
        }
        val isStatus = AccessibilityOnboardingPhrases.isStatusRequest(text)
        val isCan = AccessibilityOnboardingPhrases.isWhatCanIDoNow(text)
        val isMissing = AccessibilityOnboardingPhrases.isWhatIsMissing(text)
        val isWhy = AccessibilityOnboardingPhrases.isWhyNotWorking(text)
        val isHelp = AccessibilityOnboardingPhrases.isWhatsAppHelp(text)
        if (!isStatus && !isCan && !isMissing && !isWhy && !isHelp) return false

        val inputs = buildWhatsAppCapabilityInputs()
        val verdict = WhatsAppCapabilityMatrix.evaluate(inputs)
        logBackground("ROUTING_AUDIT handler=accessibility_onboarding")
        logBackground(
            "WHATSAPP_CAPABILITY nextStep=${verdict.nextStep} can=${verdict.can.size} " +
                "bound=${inputs.accessibilityBound} notif=${inputs.notificationListener} " +
                "installed=${inputs.whatsappInstalled} inWa=${inputs.inWhatsApp} inChat=${inputs.inChat}"
        )
        val msg = when {
            isWhy -> WhatsAppCapabilityNarrator.whyNotWorking(inputs)
            isMissing -> WhatsAppCapabilityNarrator.whatIsMissing(verdict)
            else -> WhatsAppCapabilityNarrator.whatCanIDoNow(verdict)
        }
        speak(msg, force = true)
        return true
    }

    /** Señales reales (solo lecturas) para la matriz de capacidades de WhatsApp. */
    private fun buildWhatsAppCapabilityInputs(): WhatsAppCapabilityMatrix.Inputs {
        val accessibility = backgroundAccessibilityReady()
        val installed = isWhatsAppInstalled()
        val notif = hasNotificationListenerAccess()
        val activePkg = if (accessibility) OjoClaroAccessibilityService.readActivePackageName() else null
        val inWhatsApp = activePkg != null && WhatsAppScreenDetector.KNOWN_PACKAGES.any { it == activePkg }
        val inChat = if (accessibility && inWhatsApp) {
            runCatching { whatsAppScreenDetector.detect(screenContextProvider.current()).isInChat }
                .getOrDefault(false)
        } else {
            false
        }
        return WhatsAppCapabilityMatrix.Inputs(
            whatsappInstalled = installed,
            accessibilityBound = accessibility,
            notificationListener = notif,
            inWhatsApp = inWhatsApp,
            inChat = inChat,
            backendAvailable = false
        )
    }

    /** True si el servicio de Estela figura en enabled_accessibility_services (no implica bound). */
    private fun accessibilityServiceListed(): Boolean =
        runCatching {
            val value = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            value != null && value.contains(packageName) && value.contains("OjoClaroAccessibilityService")
        }.getOrDefault(false)

    /** Abre la pantalla de Accesibilidad para que una persona active Estela. */
    private fun openAccessibilitySettings(): Boolean =
        runCatching {
            startActivity(
                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)

    /**
     * WhatsApp Fluency — "esperá" (HOLD) y "te cortaste"/"no me escuchaste"
     * (REENGAGE). Read-only: NO cancela, NO confirma, NO envía; preserva
     * cualquier pendiente. Corre antes de los pendientes para estar SIEMPRE
     * disponible; sus matchers son disjuntos de las confirmaciones.
     */
    private fun handleVoiceFluencyCommand(text: String): Boolean {
        if (VoiceFluencyPhrases.isHold(text)) {
            val hasPending = pendingWhatsAppReply != null || pendingWhatsAppSendDraft != null
            logBackground("ROUTING_AUDIT handler=voice_fluency")
            logBackground("VOICE_HOLD_ACKNOWLEDGED hasPending=$hasPending")
            speak(
                if (hasPending) "Te espero. No envié nada. Cuando quieras, seguí."
                else "Te espero. Decime cuando quieras.",
                force = true
            )
            return true
        }
        if (VoiceFluencyPhrases.isReengage(text)) {
            val previous = lastSpokenResponse
            logBackground("ROUTING_AUDIT handler=voice_fluency")
            logBackground("VOICE_REENGAGE available=${previous != null}")
            // REPEAT_PREFIX evita pisar la memoria corta (lastSpokenResponse).
            speak(
                if (previous != null) "$REPEAT_PREFIX$previous"
                else "Acá estoy, te escucho. Decime qué necesitás.",
                force = true
            )
            return true
        }
        return false
    }

    /**
     * WhatsApp Anxiety Hardening — comandos de orientación y seguridad para una
     * persona no vidente ansiosa o desorientada. Read-only: NUNCA limpia un
     * pendiente, NUNCA envía, NUNCA toca acciones peligrosas. Solo informa,
     * tranquiliza y, opcionalmente, activa el modo seguro.
     *
     * Convivencia con otras rutas: la orientación GENÉRICA ("dónde estoy") solo
     * se reclama acá si WhatsApp es el contexto activo (pendiente o app delante);
     * si no, devuelve false para que la tomen Outdoor (GPS) o la lectura de
     * pantalla. Las frases que NOMBRAN WhatsApp y "qué pasó" se responden
     * siempre. Logs sanitizados: solo flags y conteos, nunca contenido.
     */
    private fun handleWhatsAppAnxietyCommand(text: String): Boolean {
        // Modo seguro / ansiedad: preferencia de proceso (no limpia pendientes).
        if (WhatsAppAnxietyPhrases.isSafeModeOn(text)) {
            whatsAppSafeMode = true
            logBackground("ROUTING_AUDIT handler=whatsapp_anxiety_hardening")
            logBackground("WHATSAPP_ANXIETY_MODE_ENABLED")
            logBackground("WHATSAPP_SAFE_MODE_ENABLED")
            val pending = pendingWhatsAppReply
            speak(
                WhatsAppStateNarrator.safeModeEnabled(
                    hasPending = pending != null || pendingWhatsAppSendDraft != null,
                    pendingStep = pending?.awaitingStep
                        ?: if (pendingWhatsAppSendDraft != null) {
                            WhatsAppReplyConfirmationResolver.STEP_SEND_DRAFT
                        } else {
                            0
                        }
                ),
                force = true
            )
            return true
        }
        if (WhatsAppAnxietyPhrases.isSafeModeOff(text)) {
            whatsAppSafeMode = false
            logBackground("ROUTING_AUDIT handler=whatsapp_anxiety_hardening")
            logBackground("WHATSAPP_SAFE_MODE_DISABLED")
            speak(WhatsAppStateNarrator.safeModeDisabled(), force = true)
            return true
        }

        val explicit = WhatsAppAnxietyPhrases.isWhatsAppStateQuery(text)
        val whatHappened = WhatsAppAnxietyPhrases.isWhatHappened(text)
        val genericWhere = WhatsAppAnxietyPhrases.isGenericWhereAmI(text)
        val contextualHelp = WhatsAppAnxietyPhrases.isContextualHelp(text)
        if (!explicit && !whatHappened && !genericWhere && !contextualHelp) return false

        val snapshot = buildAnxietySnapshot(namesWhatsApp = explicit)
        val whatsAppContext = snapshot.hasPending || snapshot.isOpen

        // La orientación/ayuda GENÉRICA solo es nuestra si WhatsApp es el contexto
        // activo; si no, devolvemos false para no robarle "dónde estoy" a Outdoor
        // (GPS) ni a la lectura de pantalla. "qué pasó" y lo que NOMBRA WhatsApp
        // se responden siempre.
        if (!explicit && !whatHappened && !whatsAppContext) {
            if (contextualHelp) {
                logBackground("ROUTING_AUDIT handler=whatsapp_anxiety_hardening")
                logBackground("WHATSAPP_HELP_CONTEXTUAL waContext=false")
                speak(SHORT_HELP, force = true)
                return true
            }
            return false
        }

        logBackground("ROUTING_AUDIT handler=whatsapp_anxiety_hardening")
        when {
            whatHappened -> {
                logBackground("WHATSAPP_STATE_QUERY_REQUESTED kind=what_happened")
                logWhatsAppStateResult(snapshot)
                logBackground("WHATSAPP_REPEAT_LAST_RESPONSE available=${lastSpokenResponse != null}")
                speak(WhatsAppStateNarrator.whatHappened(lastSpokenResponse, snapshot), force = true)
            }
            contextualHelp -> {
                logBackground("WHATSAPP_HELP_CONTEXTUAL waContext=true")
                speak(WhatsAppStateNarrator.contextualHelp(snapshot), force = true)
            }
            else -> {
                logBackground(
                    "WHATSAPP_STATE_QUERY_REQUESTED kind=${if (explicit) "explicit" else "where_am_i"}"
                )
                logWhatsAppStateResult(snapshot)
                if (snapshot.isUnknown && !snapshot.isOpen && !snapshot.hasPending) {
                    logBackground("WHATSAPP_RECOVERY_MESSAGE_SPOKEN reason=unknown_screen")
                }
                speak(WhatsAppStateNarrator.orientation(snapshot), force = true)
            }
        }
        return true
    }

    /** Snapshot content-free para narrar: detector de pantalla + pendientes + store. */
    private fun buildAnxietySnapshot(namesWhatsApp: Boolean): WhatsAppStateNarrator.Snapshot {
        val state = runCatching {
            whatsAppScreenDetector.detect(screenContextProvider.current())
        }.getOrNull()
        val pending = pendingWhatsAppReply
        val recent = runCatching { WhatsAppNotificationStore.size() }.getOrDefault(0)
        return WhatsAppStateNarrator.Snapshot(
            isOpen = state?.isOpen == true,
            isInChat = state?.isInChat == true,
            isUnknown = state?.isUnknown ?: true,
            hasPendingReply = pending != null,
            pendingStep = pending?.awaitingStep
                ?: if (pendingWhatsAppSendDraft != null) {
                    WhatsAppReplyConfirmationResolver.STEP_SEND_DRAFT
                } else {
                    0
                },
            hasPendingSendDraft = pendingWhatsAppSendDraft != null,
            recentNotificationCount = recent,
            safeMode = whatsAppSafeMode,
            namesWhatsApp = namesWhatsApp
        )
    }

    /** Log sanitizado del resultado de una consulta de estado: solo flags/conteos. */
    private fun logWhatsAppStateResult(s: WhatsAppStateNarrator.Snapshot) {
        logBackground(
            "WHATSAPP_STATE_QUERY_RESULT open=${s.isOpen} inChat=${s.isInChat} " +
                "unknown=${s.isUnknown} pendingReply=${s.hasPendingReply} pendingStep=${s.pendingStep} " +
                "pendingDraft=${s.hasPendingSendDraft} recentNotif=${s.recentNotificationCount} " +
                "safeMode=${s.safeMode}"
        )
    }

    /**
     * Sprint WhatsApp-first: control de WhatsApp como ruta local prioritaria.
     * Cubre ABRIR WhatsApp y el DIAGNÓSTICO. Nunca envía, nunca toca acciones
     * peligrosas. Logs WHATSAPP_* sanitizados (sin contenido de chats).
     */
    private fun handleWhatsAppFirstControlCommand(text: String): Boolean {
        if (WhatsAppControlPhrases.isDiagnosticCommand(text)) {
            runWhatsAppDiagnostic()
            return true
        }
        if (WhatsAppControlPhrases.isOpenWhatsAppCommand(text)) {
            logBackground("WHATSAPP_OPEN_REQUESTED")
            if (!isWhatsAppInstalled()) {
                logBackground("WHATSAPP_NOT_INSTALLED")
                speak("No encontré WhatsApp instalado en este teléfono.", force = true)
                return true
            }
            logBackground("WHATSAPP_PACKAGE_DETECTED")
            runCatching { whatsAppIntentHelper.openWhatsApp() }
            logBackground("WHATSAPP_OPENED")
            speak("Abrí WhatsApp.", force = true)
            return true
        }
        return false
    }

    /**
     * Diagnóstico hablado de WhatsApp: instalado, accesibilidad, acceso a
     * notificaciones, si está adelante y si puedo leer la pantalla. Honesto;
     * nunca lee contenido de chats. Solo flags en logs.
     */
    private fun runWhatsAppDiagnostic() {
        logBackground("WHATSAPP_DIAGNOSTIC_REQUESTED")
        val installed = isWhatsAppInstalled()
        if (!installed) {
            logBackground("WHATSAPP_DIAGNOSTIC installed=false")
            logBackground("WHATSAPP_NOT_INSTALLED")
            speak("No encontré WhatsApp instalado en este teléfono.", force = true)
            return
        }
        val accessibility = backgroundAccessibilityReady()
        val notifAccess = hasNotificationListenerAccess()
        val activePkg = OjoClaroAccessibilityService.readActivePackageName()
        val foreground = activePkg != null &&
            WhatsAppScreenDetector.KNOWN_PACKAGES.any { it == activePkg }
        val state = if (accessibility) {
            runCatching { whatsAppScreenDetector.detect(screenContextProvider.current()) }.getOrNull()
        } else {
            null
        }
        val canRead = accessibility && foreground && (state?.isOpen == true)
        logBackground(
            "WHATSAPP_DIAGNOSTIC installed=true accessibility=$accessibility " +
                "notifAccess=$notifAccess foreground=$foreground canRead=$canRead " +
                "confidence=${state?.confidence ?: "NA"}"
        )

        val parts = mutableListOf<String>()
        parts += "WhatsApp está instalado."
        parts += if (accessibility) "Accesibilidad activa." else
            "Necesito que actives Accesibilidad de Ojo Claro para leer WhatsApp."
        parts += if (notifAccess) "Acceso a notificaciones activo." else
            "Falta darme acceso a notificaciones para avisarte cuando lleguen mensajes."
        parts += when {
            !accessibility -> "Sin accesibilidad no puedo leer la pantalla."
            foreground && canRead -> "Puedo leer la pantalla de WhatsApp."
            foreground -> "WhatsApp está adelante, pero no veo tus chats; puede no tener la sesión iniciada."
            else -> "Abrí WhatsApp y volvé a pedirme el diagnóstico para revisar la lectura."
        }
        speak(parts.joinToString(" "), force = true)
    }

    private fun isWhatsAppInstalled(): Boolean =
        WhatsAppScreenDetector.KNOWN_PACKAGES.any { pkg ->
            runCatching {
                packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }

    private fun hasNotificationListenerAccess(): Boolean =
        runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        }.getOrDefault(false)

    /**
     * WA-5 read-path: responde "¿tengo mensajes nuevos de WhatsApp?", "¿quién me
     * escribió?", "leeme las notificaciones de WhatsApp", etc., LEYENDO el
     * [WhatsAppNotificationStore] que llena el listener WA-3.
     *
     * Alcance estricto (read-only): NO abre WhatsApp, NO abre chats, NO envía,
     * NO responde, NO toca botón enviar, NO llama, NO audios, NO archivos, NO
     * red, NO LLM. Solo lee el ring buffer en memoria y habla.
     *
     * Privacidad: el contenido (remitente/preview) puede ir al TTS porque el
     * usuario lo pidió, pero los logs son SIEMPRE redactados (logSummary: count
     * + package + group + longitudes + timestamp), nunca el texto del mensaje.
     */
    /**
     * Full Control Hardening — acciones PROHIBIDAS de WhatsApp (borrar/archivar/
     * silenciar/fijar/bloquear/reportar/pagar/sticker/archivo/foto/reenviar/
     * compartir ubicación/abrir link). Se rutean LOCALMENTE antes de cámara/
     * compose/navegación y del LLM, y se rechazan con una negativa segura.
     * NUNCA tocan UI, no escriben borrador, no abren backend/LLM. El gate del
     * catálogo confirma que son FORBIDDEN (ningún flag las habilita).
     */
    private fun handleWhatsAppForbiddenActionCommand(text: String): Boolean {
        val match = WhatsAppForbiddenCommandParser.parse(text) ?: return false
        // Reclamar en contexto WhatsApp (frase lo nombra o WhatsApp es la app/
        // contexto activo) O cuando la frase nombra un objeto de mensajería
        // EXPLÍCITO (chat/contacto/mensaje/grupo): una acción destructiva ya
        // detectada + "este chat"/"este contacto" es inequívocamente WhatsApp y NO
        // debe caer al LLM/backend (rule #7), aunque el harness/otra app esté al
        // frente. "este/esto" a secas no alcanza (podría ser archivo/pantalla).
        val objectAnchor = WhatsAppForbiddenCommandParser.mentionsExplicitMessagingObject(text)
        if (!match.namedWhatsApp && !isWhatsAppActiveContext() && !objectAnchor) return false
        val gate = WhatsAppActionCatalog.gate(
            match.action, whatsAppFlags, hasDestination = true, WhatsAppDestinationConfidence.HIGH
        )
        WhatsAppActionAudit.recordBlocked()
        logBackground(
            "ROUTING_AUDIT handler=whatsapp_forbidden_action action=${match.action.logMarker} " +
                "blocked=${gate is WhatsAppActionGate.Blocked} named=${match.namedWhatsApp} " +
                "objectAnchor=$objectAnchor " +
                WhatsAppActionAudit.redactedSummary()
        )
        speak(WhatsAppForbiddenActionNarrator.refusal(match.action), force = true)
        return true
    }

    /**
     * Blind Safety (#3) — DIAGNÓSTICO sanitizado del contexto WhatsApp, una vez por
     * comando. Sin contenido ni números: sólo categoría de app, edad del rastro y
     * qué fuente decidiría el contexto. Para depurar el ruteo en físico (Codex).
     */
    private fun logWhatsAppContextDiag() {
        val activeCat = runCatching { OjoClaroAccessibilityService.activeForegroundCategory() }
            .getOrDefault("error")
        val ageMs = runCatching { OjoClaroAccessibilityService.lastWhatsAppForegroundAgeMs() }
            .getOrDefault(-1L)
        val withinTtl = ageMs in 0 until WHATSAPP_CONTEXT_RECENCY_MS
        val ctxExternalWa = contextState.current.externalApp == ExternalAppName.WHATSAPP
        val pendingWa = pendingWhatsAppReply != null || pendingWhatsAppSendDraft != null
        val source = when {
            ctxExternalWa || activeCat == "whatsapp" -> "active_window"
            pendingWa -> "pending"
            withinTtl -> "last_whatsapp_tracker"
            else -> "none"
        }
        logBackground(
            "WHATSAPP_CONTEXT_DIAG activePkg=$activeCat lastWhatsAppAgeMs=$ageMs " +
                "lastWhatsAppSeen=${ageMs >= 0} withinTtl=$withinTtl contextSource=$source"
        )
    }

    /** True si WhatsApp es el contexto activo (app al frente, handoff o pending). */
    private fun isWhatsAppActiveContext(): Boolean {
        if (contextState.current.externalApp == ExternalAppName.WHATSAPP) return true
        if (pendingWhatsAppReply != null || pendingWhatsAppSendDraft != null) return true
        val pkg = runCatching { OjoClaroAccessibilityService.readActivePackageName() }
            .getOrNull()?.lowercase()
        if (pkg != null && WhatsAppScreenDetector.KNOWN_PACKAGES.any { it == pkg }) return true
        // Blind Safety (#3): WhatsApp puede ser el foreground REAL aunque un overlay/
        // actividad propia (o el harness debug) quede encima y el "active window" deje
        // de reportarlo. El servicio recuerda el último foreground de WhatsApp por
        // evento y lo olvida cuando OTRO app real toma el frente.
        return runCatching {
            OjoClaroAccessibilityService.wasWhatsAppForegroundWithin(WHATSAPP_CONTEXT_RECENCY_MS)
        }.getOrDefault(false)
    }

    /** ¿La frase nombra WhatsApp explícitamente? */
    private fun textNamesWhatsApp(text: String): Boolean {
        val f = text.lowercase().removeSpanishAccents()
        return f.contains("whatsapp") || f.contains("wasap") || f.contains("guasap") ||
            f.contains("wsp") || f.contains("wpp")
    }

    /**
     * Blind Safety (Fix E) — última línea ANTES de salir a LLM/backend. Si la
     * frase es WhatsApp-CRÍTICA (mutar/enviar/borrar/pagar/foto/llamar/audio) y el
     * contexto es WhatsApp (nombrada o app activa), NO la dejamos viajar a
     * /conversation ni al orchestrator: se rechaza local con TTS claro. Los verbos
     * de LECTURA no son críticos (siguen su ruta local). Corre DESPUÉS de todos los
     * handlers locales: lo que llega acá no lo atendió ninguno.
     */
    /**
     * Blind Safety (#4/#5) — negativa LOCAL y ESPECÍFICA para videollamada / audio /
     * llamada "peladas" (sin destinatario) que el forbidden/dangerous parser no cubre.
     * Sólo reclama si la frase nombra WhatsApp o WhatsApp es el contexto activo (para
     * la llamada de voz exige NOMBRAR WhatsApp, así no roba una llamada telefónica).
     * No toca UI, no llama, no graba, no LLM.
     */
    private fun handleWhatsAppMediaCallRefusal(text: String): Boolean {
        val kind = WhatsAppMediaCallRefusalPhrases.classify(text) ?: return false
        val named = textNamesWhatsApp(text)
        if (!named && !isWhatsAppActiveContext()) return false
        // La llamada de VOZ es ambigua con la telefónica: sólo la reclamamos si
        // la frase nombra WhatsApp explícitamente.
        if (kind == WhatsAppMediaCallRefusalPhrases.Kind.VOICE_CALL && !named) return false
        WhatsAppActionAudit.recordBlocked()
        logBackground(
            "ROUTING_AUDIT handler=whatsapp_media_call_refusal kind=$kind named=$named " +
                WhatsAppActionAudit.redactedSummary()
        )
        speak(WhatsAppMediaCallRefusalPhrases.refusal(kind), force = true)
        return true
    }

    /**
     * Blind Safety (#8) — clarifier LOCAL para contenido de mensaje ambiguo. Con
     * WhatsApp al frente, "estoy llegando" / "decile que sí" / "mandale eso" / "eso"
     * NO viajan al backend/LLM: se aclara local SIN escribir, SIN draft, SIN enviar,
     * SIN tocar UI. El Q&A general sigue su ruta normal (devuelve false). Las
     * acciones WhatsApp EXPLÍCITAS ya las atendieron forbidden/critical guard.
     */
    private fun handleWhatsAppAmbiguousMessageClarifier(text: String): Boolean {
        if (!isWhatsAppActiveContext()) return false
        // Acción explícita (borrar/foto/pagar/…): que siga su ruta segura, no acá.
        if (WhatsAppForbiddenCommandParser.parse(text) != null) return false
        // B: Q&A claro → dejar pasar al asistente normal.
        if (WhatsAppMessageClarifierPhrases.looksLikeQuestion(text)) return false
        // A: contenido de mensaje / continuación ambigua → aclarar local.
        if (!WhatsAppMessageClarifierPhrases.looksLikeAmbiguousMessageContent(text)) return false
        logBackground("ROUTING_AUDIT handler=whatsapp_msg_clarifier blocked_llm=true")
        speak(
            "¿Querés que use eso como mensaje de WhatsApp? Decime a quién y qué querés mandar.",
            force = true
        )
        return true
    }

    private fun handleWhatsAppCriticalGuardBeforeLlm(text: String): Boolean {
        if (!WhatsAppCriticalGuard.isCritical(text)) return false
        if (!textNamesWhatsApp(text) && !isWhatsAppActiveContext()) return false
        logBackground("ROUTING_AUDIT handler=whatsapp_critical_guard blocked_llm=true len=${text.length}")
        speak(
            "Eso es una acción de WhatsApp que no pude resolver de forma segura por voz. " +
                "No toqué nada. Abrí el chat y repetímelo, o decime el contacto.",
            force = true
        )
        return true
    }

    /**
     * Full Control Hardening — pedidos HIGH-RISK por contacto (llamada de voz /
     * audio) ruteados ANTES del LLM y BLOQUEADOS por feature flag. Resuelve el
     * destino para confirmar a quién, pero NO llama ni graba: los flags están en
     * false y no hay toque real cableado para estas acciones.
     */
    private fun handleWhatsAppDangerousActionCommand(text: String): Boolean {
        val intent = WhatsAppDangerousCommandParser.parse(text) ?: return false
        val action = when (intent) {
            is WhatsAppDangerousIntent.Call -> WhatsAppActionType.CALL
            is WhatsAppDangerousIntent.SendAudio -> WhatsAppActionType.SEND_AUDIO
        }
        val query = when (intent) {
            is WhatsAppDangerousIntent.Call -> intent.contactQuery
            is WhatsAppDangerousIntent.SendAudio -> intent.contactQuery
        }
        logBackground("ROUTING_AUDIT handler=whatsapp_dangerous_action action=${action.logMarker}")
        WhatsAppActionAudit.recordBlocked()
        if (WhatsAppVoiceSendPhrases.mentionsSensitiveKeyword(query)) {
            speak(WhatsAppBlindRouteNarrator.unsafeQuery(), force = true)
            return true
        }
        val resolved = smartComposeResolver.resolve(query)
        val destination = (resolved as? com.ojoclaro.android.phone.ContactResolutionResult.Resolved)?.let {
            WhatsAppDestination.of(
                source = WhatsAppDestinationSource.CONTACT,
                confidence = WhatsAppDestinationConfidence.HIGH,
                label = it.candidate.displayName,
                phoneE164 = it.candidate.phoneE164
            )
        }
        if (destination == null) {
            logBackground(
                "whatsappDangerous outcome=blocked_no_destination action=${action.logMarker} " +
                    WhatsAppActionAudit.redactedSummary()
            )
            speak(
                "No tengo a esa persona en tus contactos de confianza, así que no " +
                    "preparé nada. No llamé ni grabé nada.",
                force = true
            )
            return true
        }
        // Política central: con los flags en false esto SIEMPRE bloquea (no ejecuta).
        val gate = WhatsAppActionCatalog.gate(action, whatsAppFlags, hasDestination = true, destination.confidence)
        WhatsAppConversationContext.noteDestination(
            destination.redactedLabel, destination.phoneEnding, System.currentTimeMillis()
        )
        logBackground(
            "whatsappDangerous outcome=${if (gate is WhatsAppActionGate.Blocked) "blocked" else "not_enabled"} " +
                "action=${action.logMarker} ${WhatsAppActionAudit.redactedSummary()}"
        )
        speak(
            if (action == WhatsAppActionType.CALL) {
                "Las llamadas reales de WhatsApp están desactivadas en esta versión de prueba. No llamé."
            } else {
                "Mandar audios está desactivado en esta versión de prueba. No grabé ni mandé nada."
            },
            force = true
        )
        return true
    }

    /**
     * Full Control Hardening — recuerdo/olvido del contexto de WhatsApp (memoria
     * local REDACTADA: sin contenido crudo ni números completos).
     */
    private fun handleWhatsAppContextCommand(text: String): Boolean {
        val n = java.text.Normalizer
            .normalize(VoicePhraseNormalizer.normalizeForParser(text).lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val isForget = (n.contains("olvida") && (n.contains("contexto") || n.contains("whatsapp"))) ||
            (n.contains("limpia") && n.contains("memoria") && n.contains("whatsapp")) ||
            (n.contains("borra") && n.contains("contexto"))
        val isRecall = n in setOf(
            "de que estabamos hablando", "de que hablabamos", "que estabamos hablando",
            "que le iba a mandar", "que le iba a escribir", "a quien le estaba escribiendo",
            "a quien le iba a escribir", "que veniamos hablando"
        )
        if (isForget) {
            WhatsAppConversationContext.clear()
            logBackground("ROUTING_AUDIT handler=whatsapp_context_forget")
            speak("Listo, olvidé el contexto de WhatsApp.", force = true)
            return true
        }
        if (isRecall) {
            logBackground("ROUTING_AUDIT handler=whatsapp_context_recall")
            speak(WhatsAppConversationContext.spokenRecall(), force = true)
            return true
        }
        return false
    }

    /**
     * Trusted Contacts — vincular u olvidar una relación local ("este contacto
     * es mi novia" / "olvidá a mi novia"). La vinculación lee el número de la
     * CABECERA del chat/perfil abierto (identidad del contacto, jamás el cuerpo
     * del chat) y confirma por los últimos 4 antes de guardar; olvidar es
     * inmediato. Todo LOCAL: no envía, no llama, no paga, no toca el LLM, y el
     * número nunca se loguea entero.
     */
    private fun handleWhatsAppRelationshipCommand(text: String): Boolean {
        val forgetKey = WhatsAppRelationshipAlias.parseForgetRequest(text)
        if (forgetKey != null) {
            logBackground("ROUTING_AUDIT handler=whatsapp_relationship_forget key=$forgetKey")
            val had = relationshipStore.isLinked(forgetKey)
            relationshipStore.forget(forgetKey)
            if (pendingRelationshipLink?.key == forgetKey) pendingRelationshipLink = null
            speak(
                if (had) {
                    "Listo, olvidé a ${WhatsAppRelationshipAlias.spokenLabel(forgetKey)}."
                } else {
                    "No tenía guardado a ${WhatsAppRelationshipAlias.spokenLabel(forgetKey)}, " +
                        "así que no había nada que olvidar."
                },
                force = true
            )
            return true
        }

        val linkKey = WhatsAppRelationshipAlias.parseLinkRequest(text) ?: return false
        logBackground("ROUTING_AUDIT handler=whatsapp_relationship_link key=$linkKey")
        // El número se lee de la pantalla: hay que estar en el chat/perfil de WhatsApp.
        if (!isWhatsAppActiveContext()) {
            logBackground("whatsappRelationshipLink result=not_in_whatsapp key=$linkKey")
            speak(
                "Para guardar a ${WhatsAppRelationshipAlias.spokenLabel(linkKey)} necesito estar en su " +
                    "chat o en su perfil de WhatsApp, donde se vea el número. Abrí su chat y volvé a decírmelo.",
                force = true
            )
            return true
        }
        val phone = runCatching { OjoClaroAccessibilityService.readVisibleWhatsAppPhoneNumber() }.getOrNull()
        if (phone == null) {
            logBackground("whatsappRelationshipLink result=no_visible_number key=$linkKey")
            speak(
                "No pude leer un número en esta pantalla. Si la persona ya está agendada, WhatsApp muestra " +
                    "el nombre y no el número. Tocá el nombre de arriba para abrir su perfil y volvé a decírmelo.",
                force = true
            )
            return true
        }
        val label = WhatsAppRelationshipAlias.spokenLabel(linkKey)
        val pending = PendingRelationshipLink(linkKey, label, phone)
        pendingRelationshipLink = pending
        logBackground(
            "whatsappRelationshipLink result=awaiting_confirm key=$linkKey " +
                "phoneLen=${phone.filter(Char::isDigit).length}"
        )
        speak(
            "Encontré un número terminado en ${pending.phoneEnding}. ¿Lo guardo como $label? " +
                "Decí sí para guardar, o cancelá.",
            force = true
        )
        return true
    }

    /**
     * Confirmación de "este contacto es mi novia". Guardar un vínculo LOCAL es
     * reversible y no envía nada, así que un "sí" alcanza; "no"/"cancelar"
     * descarta sin guardar. Un nuevo pedido de relación reemplaza el pendiente.
     */
    private fun handlePendingRelationshipLinkReply(text: String): Boolean {
        val pending = pendingRelationshipLink ?: return false
        // Un nuevo "este contacto es mi X" reemplaza el pendiente: que re-resuelva.
        if (WhatsAppRelationshipAlias.parseLinkRequest(text) != null ||
            WhatsAppRelationshipAlias.parseForgetRequest(text) != null
        ) {
            pendingRelationshipLink = null
            return false
        }
        val normalized = VoicePhraseNormalizer.normalizeForParser(text).lowercase().trim()
        when {
            isOpenChatAffirmative(normalized) -> {
                pendingRelationshipLink = null
                val linked = relationshipStore.link(
                    pending.key,
                    label = pending.label,
                    phoneE164 = pending.phoneE164,
                    source = "voice_link"
                )
                if (linked == null) {
                    logBackground("whatsappRelationshipLink result=link_failed key=${pending.key}")
                    speak("No pude guardar el contacto, así que no guardé nada.", force = true)
                    return true
                }
                logBackground("whatsappRelationshipLink result=linked ${linked.redactedForLog()}")
                speak(
                    "Listo. Guardé a ${pending.label}, terminado en ${pending.phoneEnding}. " +
                        "La próxima decime: abrí WhatsApp con mi novia.",
                    force = true
                )
            }
            isOpenChatCancel(normalized) -> {
                pendingRelationshipLink = null
                logBackground("whatsappRelationshipLink result=cancelled key=${pending.key}")
                speak("Cancelado. No guardé nada.", force = true)
            }
            else -> {
                speak(
                    "Decí sí para guardar a ${pending.label} terminado en ${pending.phoneEnding}, o cancelá.",
                    force = true
                )
            }
        }
        return true
    }

    /** Mensaje seguro y consistente cuando la relación no está configurada. */
    private fun relationshipNotConfiguredMessage(key: String): String =
        "No tengo un contacto confiable configurado para " +
            "${WhatsAppRelationshipAlias.spokenLabel(key)}. Cuando estés en su chat o en su " +
            "perfil, decime: este contacto es mi novia. Y lo guardo para la próxima."

    /**
     * Trusted Contacts — COMPOSE por relación: "mandale/decile/escribile/
     * respondé a mi novia que ESTOY LLEGANDO". Resuelve la relación ANTES que el
     * smart compose genérico, abre el chat por deep link, VERIFICA FUERTE el
     * destino y RECIÉN si está confirmado prepara el borrador (que entra a la
     * doble confirmación; nunca envía por defecto). Si no se confirma el destino,
     * bloquea sin escribir/tocar/enviar/llamar ni usar el LLM.
     */
    private fun handleWhatsAppRelationshipComposeCommand(text: String): Boolean {
        val request = WhatsAppRelationshipComposeParser.parse(text) ?: return false
        logBackground("ROUTING_AUDIT handler=whatsapp_relationship_compose key=${request.key}")
        val message = request.message
        // Contenido sensible: bloquea ANTES de abrir/resolver/escribir nada.
        if (WhatsAppVoiceSendPhrases.looksSensitive(message)) {
            logBackground("whatsappRelationshipCompose blocked=sensitive_content msgLen=${message.length}")
            speak(
                "Ese mensaje parece contener datos sensibles, así que no lo preparo por voz. " +
                    "Escribilo a mano si estás seguro.",
                force = true
            )
            return true
        }
        if (!isWhatsAppInstalled()) {
            speak(WhatsAppBlindRouteNarrator.notInstalled(), force = true)
            return true
        }
        val rel = relationshipStore.resolve(request.key)
        if (rel == null) {
            logBackground("whatsappRelationshipCompose relationship=not_configured key=${request.key}")
            speak(relationshipNotConfiguredMessage(request.key), force = true)
            return true
        }
        val destination = WhatsAppDestination.of(
            source = WhatsAppDestinationSource.CONTACT,
            confidence = WhatsAppDestinationConfidence.HIGH,
            label = rel.label,
            phoneE164 = rel.phoneE164
        )
        logBackground(
            "whatsappRelationshipCompose relationship=resolved ${rel.redactedForLog()} " +
                "${destination.redactedForLog()} msgLen=${message.length}"
        )
        openVerifyThenDraft(rel.label, rel.phoneE164, destination, message)
        return true
    }

    /**
     * Abre por deep link → VERIFICA FUERTE el destino → RECIÉN si VERIFIED
     * escribe el borrador (que pasa a la doble confirmación). Si la verificación
     * no es VERIFIED: bloqueo COULD_NOT_CONFIRM_DESTINATION — no escribe, no
     * toca, no envía, no llama, no usa el LLM/backend.
     */
    private fun openVerifyThenDraft(
        displayName: String,
        phoneE164: String,
        destination: WhatsAppDestination,
        message: String
    ) {
        val opened = runCatching { whatsAppIntentHelper.openChat(displayName, phoneE164) }.getOrNull()
        if (opened !is CommandResult.Success) {
            logBackground("whatsappRelationshipCompose open=failed")
            speak(WhatsAppBlindRouteNarrator.couldNotOpen(), force = true)
            return
        }
        logBackground("whatsappRelationshipCompose open=launched")
        // #14: capturá la generación ANTES de la ventana async (e invalidá cualquier
        // compose previo). Si el usuario cancela/STOP durante el delay, la generación
        // cambia y abortamos sin escribir borrador ni armar pending.
        val composeGen = invalidateInFlightWhatsAppCompose()
        serviceScope.launch {
            try {
                // ~1,2 s: deja que el deep link traiga WhatsApp al frente.
                delay(1_200L)
                val verdict = verifyOpenedDestination(destination)
                logBackground("whatsappRelationshipCompose verify=${verdict.redactedForLog()}")
                if (!verdict.isVerified) {
                    logBackground(
                        "whatsappRelationshipCompose blocked=could_not_confirm_destination " +
                            "status=${verdict.status}"
                    )
                    speak(WhatsAppBlindRouteNarrator.couldNotConfirmDestination(), force = true)
                    return@launch
                }
                // #14: cancelado/STOP durante la ventana → no escribir ni armar nada.
                if (composeGen != whatsAppComposeGeneration.get()) {
                    logBackground("whatsappRelationshipCompose aborted=cancelled_during_open_window")
                    return@launch
                }
                // Destino VERIFIED: recién acá se prepara el borrador (nunca envía).
                draftWhatsAppMessageAndConfirm(message, destinationLabel = spokenLabelFor(displayName))
            } catch (t: Throwable) {
                // Blind Safety (Fix F): un throw post-apertura no deja mudo al usuario.
                logBackground("whatsappRelationshipCompose error=${t.javaClass.simpleName}")
                speak(WhatsAppBlindRouteNarrator.couldNotConfirmDestination(), force = true)
            }
        }
    }

    private fun handleWhatsAppNotificationQueryCommand(text: String): Boolean {
        if (!WhatsAppNotificationQueryPhrases.isNotificationQuery(text)) return false
        logBackground("ROUTING_AUDIT handler=whatsapp_notification_query")
        logBackground("WHATSAPP_NOTIFICATION_QUERY_REQUESTED")
        val notifications = WhatsAppNotificationStore.recent()
        logBackground("WHATSAPP_NOTIFICATION_STORE_READ count=${notifications.size}")
        val response = WhatsAppNotificationQueryResponder.respond(notifications)
        when (response.outcome) {
            WhatsAppNotificationQueryResponder.Outcome.EMPTY ->
                logBackground("WHATSAPP_NOTIFICATION_QUERY_EMPTY")
            WhatsAppNotificationQueryResponder.Outcome.CONTENT_HIDDEN ->
                logBackground("WHATSAPP_NOTIFICATION_QUERY_CONTENT_HIDDEN ${response.logSummary}")
            else ->
                logBackground("WHATSAPP_NOTIFICATION_QUERY_RESULT ${response.logSummary}")
        }
        speak(response.spokenText, force = true)
        return true
    }

    /**
     * WhatsApp Blind-First: rutas de NAVEGACIÓN por voz para que una persona no
     * vidente llegue al chat correcto SIN tocar la pantalla.
     *
     *  - "abrí WhatsApp con Juan" / "escribile a Juan" / "respondé a Juan":
     *    resuelve el contacto en memoria y abre su chat por deep link wa.me
     *    (sin texto). Antes, "abrí WhatsApp con Juan" abría WhatsApp a secas y
     *    dejaba a la persona en la lista (dependía de un toque manual).
     *  - "respondé el último WhatsApp" / "respondé a quien me escribió": abre el
     *    chat del remitente de la última notificación si es un contacto de
     *    confianza; si no, ofrece una alternativa segura. Antes, estas frases
     *    tipeaban "el último" como borrador en el chat abierto.
     *
     * Contrato: SOLO abre/verifica. Nunca prepara ni envía un mensaje, nunca toca
     * botones. Corre ANTES de abrir-genérico/ordinal/reply/compose y del LLM.
     */
    private fun handleWhatsAppBlindFirstCommand(text: String): Boolean {
        val intent = WhatsAppBlindRoute.parse(text) ?: return false
        // No pisar un envío/confirmación en curso (defensa: los pendientes ya
        // corrieron arriba; con uno activo dejamos que la ruta establecida gane).
        if (pendingContactConfirmation != null ||
            pendingWhatsAppSendDraft != null ||
            pendingWhatsAppReply != null
        ) {
            return false
        }
        return when (intent) {
            is WhatsAppBlindIntent.OpenContactChat ->
                handleBlindOpenContactChat(intent.contactQuery)
            WhatsAppBlindIntent.ReplyToLastNotification ->
                handleBlindReplyToLastNotification()
        }
    }

    /** Abrir el chat de un contacto nombrado por voz. Resuelve en memoria. */
    private fun handleBlindOpenContactChat(contactQuery: String): Boolean {
        logBackground("ROUTING_AUDIT handler=whatsapp_blind_open")
        if (WhatsAppVoiceSendPhrases.mentionsSensitiveKeyword(contactQuery)) {
            logBackground("whatsappBlindOpen blocked=sensitive_query")
            speak(WhatsAppBlindRouteNarrator.unsafeQuery(), force = true)
            return true
        }
        if (!isWhatsAppInstalled()) {
            logBackground("whatsappBlindOpen result=not_installed")
            speak(WhatsAppBlindRouteNarrator.notInstalled(), force = true)
            return true
        }
        // Trusted Contacts por RELACIÓN: "abrí WhatsApp con mi novia" no resuelve
        // por nombre (no es un contacto guardado), pero sí por relación si la
        // persona la vinculó antes ("este contacto es mi novia"). Sin vínculo no
        // pedimos abrir a mano (Estela es para personas no videntes): explicamos
        // cómo vincularla. Resuelto → mismo flujo seguro (deep link + verificar).
        val relKey = WhatsAppRelationshipAlias.canonicalKey(contactQuery)
        if (relKey != null) {
            val rel = relationshipStore.resolve(relKey)
            if (rel == null) {
                logBackground("whatsappBlindOpen relationship=not_configured key=$relKey")
                speak(relationshipNotConfiguredMessage(relKey), force = true)
                return true
            }
            val destination = WhatsAppDestination.of(
                source = WhatsAppDestinationSource.CONTACT,
                confidence = WhatsAppDestinationConfidence.HIGH,
                label = rel.label,
                phoneE164 = rel.phoneE164
            )
            logBackground(
                "whatsappBlindOpen relationship=resolved ${rel.redactedForLog()} " +
                    destination.redactedForLog()
            )
            openBlindContactChat(rel.label, rel.phoneE164, destination)
            return true
        }
        when (val resolved = smartComposeResolver.resolve(contactQuery)) {
            is com.ojoclaro.android.phone.ContactResolutionResult.Resolved -> {
                val candidate = resolved.candidate
                val destination = WhatsAppDestination.of(
                    source = WhatsAppDestinationSource.CONTACT,
                    confidence = WhatsAppDestinationConfidence.HIGH,
                    label = candidate.displayName,
                    phoneE164 = candidate.phoneE164
                )
                logBackground("whatsappBlindOpen resolved=single ${destination.redactedForLog()}")
                openBlindContactChat(candidate.displayName, candidate.phoneE164, destination)
            }
            is com.ojoclaro.android.phone.ContactResolutionResult.MultipleMatches -> {
                logBackground("whatsappBlindOpen resolved=multiple count=${resolved.candidates.size}")
                speak(
                    WhatsAppBlindRouteNarrator.multipleContacts(resolved.candidates.map { it.displayName }),
                    force = true
                )
            }
            else -> {
                logBackground("whatsappBlindOpen resolved=not_found")
                speak(WhatsAppBlindRouteNarrator.contactNotFound(contactQuery), force = true)
            }
        }
        return true
    }

    /** Responder la última notificación abriendo el chat del remitente (si es confiable). */
    private fun handleBlindReplyToLastNotification(): Boolean {
        logBackground("ROUTING_AUDIT handler=whatsapp_blind_reply_notification")
        val latest = runCatching {
            WhatsAppNotificationStore.recent().lastOrNull {
                WhatsAppNotificationFilter.isWhatsApp(it.packageName)
            }
        }.getOrNull()
        if (latest == null) {
            logBackground("whatsappBlindReply result=empty")
            speak(WhatsAppBlindRouteNarrator.noNotifications(), force = true)
            return true
        }
        logBackground("whatsappBlindReply latest=${latest.redactedForLog()}")
        val sender = latest.sender.trim()
        if (sender.isEmpty()) {
            // Android ocultó el remitente: no hay a quién abrir de forma segura.
            speak(WhatsAppBlindRouteNarrator.notificationHidden(), force = true)
            return true
        }
        if (!isWhatsAppInstalled()) {
            speak(WhatsAppBlindRouteNarrator.notInstalled(), force = true)
            return true
        }
        when (val resolved = smartComposeResolver.resolve(sender)) {
            is com.ojoclaro.android.phone.ContactResolutionResult.Resolved -> {
                val candidate = resolved.candidate
                val destination = WhatsAppDestination.of(
                    source = WhatsAppDestinationSource.NOTIFICATION,
                    confidence = WhatsAppDestinationConfidence.HIGH,
                    label = candidate.displayName,
                    phoneE164 = candidate.phoneE164
                )
                logBackground("whatsappBlindReply resolved=single ${destination.redactedForLog()}")
                openBlindContactChat(candidate.displayName, candidate.phoneE164, destination)
            }
            else -> {
                logBackground("whatsappBlindReply resolved=unresolved")
                speak(WhatsAppBlindRouteNarrator.notificationSenderUnresolved(sender), force = true)
            }
        }
        return true
    }

    /**
     * Abre el chat por deep link wa.me (SIN texto: no escribe ni envía) y, tras
     * una pequeña demora (el deep link tarda en traer WhatsApp al frente),
     * VERIFICA FUERTE el destino ([verifyOpenedDestination]). Solo lee; nunca
     * toca botones. Si no se confirma el destino, lo dice y no afirma nada falso.
     */
    private fun openBlindContactChat(
        displayName: String,
        phoneE164: String,
        destination: WhatsAppDestination
    ) {
        val opened = runCatching { whatsAppIntentHelper.openChat(displayName, phoneE164) }.getOrNull()
        if (opened !is CommandResult.Success) {
            logBackground("whatsappBlindOpen open=failed")
            speak(WhatsAppBlindRouteNarrator.couldNotOpen(), force = true)
            return
        }
        logBackground("whatsappBlindOpen open=launched")
        serviceScope.launch {
            // ~1,2 s: deja que el deep link traiga WhatsApp al frente.
            delay(1_200L)
            val verdict = verifyOpenedDestination(destination)
            logBackground("whatsappBlindOpen verify=${verdict.redactedForLog()}")
            if (verdict.isVerified) {
                speak(destination.spokenOpened(), force = true)
            } else {
                // Honesto: abrió pero no se pudo confirmar el destino. Sin escribir.
                speak(WhatsAppBlindRouteNarrator.couldNotConfirmDestination(), force = true)
            }
        }
    }

    /**
     * Verificación FUERTE de destino tras abrir por deep link: contrasta el
     * destino esperado (últimos 4) contra las señales del detector (inChat +
     * campo de texto) y el número visible en la cabecera. Devuelve un veredicto
     * explícito; el llamador bloquea/no-afirma si no es VERIFIED. Solo LEE.
     */
    private fun verifyOpenedDestination(
        destination: WhatsAppDestination
    ): WhatsAppDestinationVerifier.Result {
        val state = runCatching {
            whatsAppScreenDetector.detect(screenContextProvider.current())
        }.getOrNull()
        val visibleEnding = runCatching {
            OjoClaroAccessibilityService.readVisibleWhatsAppPhoneNumber()
        }.getOrNull()?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 }
        // Blind Safety: para contactos AGENDADOS la cabecera muestra un NOMBRE (no
        // número) → sin labelMatches el verificador daría UNVERIFIED_NO_SIGNALS y
        // bloquearía SIEMPRE el caso común. Comparamos el label esperado contra el
        // título visible para verificar también por nombre. El final (últimos 4)
        // sigue siendo la señal dominante cuando hay número visible.
        val visibleTitle = runCatching {
            OjoClaroAccessibilityService.readVisibleWhatsAppChatTitle()
        }.getOrNull()
        return WhatsAppDestinationVerifier.verify(
            expectedEnding = destination.phoneEnding,
            inChat = state?.isInChat == true,
            hasEntryField = state?.hasMessageField == true,
            timedOut = state == null,
            visibleEnding = visibleEnding,
            labelMatches = WhatsAppLabelMatcher.matches(destination.redactedLabel, visibleTitle)
        )
    }

    /**
     * Blind Safety — nombre para ANUNCIAR por voz. Si el "nombre" es en realidad
     * un número dictado (USER_DICTATED_NUMBER → displayName = E164), dice solo
     * "el contacto terminado en NNNN": NUNCA pronuncia el número completo.
     */
    private fun spokenLabelFor(label: String): String {
        val digits = label.filter(Char::isDigit)
        return if (digits.length >= 7) "el contacto terminado en ${digits.takeLast(4)}"
        else label.take(40)
    }

    /**
     * Sprint WhatsApp: "abrí el primer/segundo/tercer chat", "entrar al
     * primero", "abrí el de arriba", "abrí el chat número dos". Resuelve el
     * ordinal contra la lista VISIBLE real (snapshot de accesibilidad con
     * WhatsApp adelante) y pide confirmación antes de abrir. NUNCA abre a
     * ciegas, NUNCA envía. Logs WHATSAPP_OPEN_CHAT_ORDINAL_* sanitizados.
     */
    private fun handleWhatsAppOpenChatOrdinalCommand(text: String): Boolean {
        if (WhatsAppOrdinalChatParser.parse(text) == null) return false
        logBackground("ROUTING_AUDIT handler=whatsapp_open_chat_ordinal")
        logBackground("WHATSAPP_OPEN_CHAT_ORDINAL_REQUESTED")
        when (val result = whatsAppOrdinalChatOpenUseCase.handle(text)) {
            is WhatsAppOrdinalChatResponse.NotAnOrdinalCommand -> return false
            is WhatsAppOrdinalChatResponse.NeedsAccessibilityService -> {
                logBackground("WHATSAPP_OPEN_CHAT_FAILED reason=accessibility_off")
                speak(result.spokenText, force = true)
            }
            is WhatsAppOrdinalChatResponse.NotInWhatsApp -> {
                logBackground("WHATSAPP_OPEN_CHAT_FAILED reason=not_in_whatsapp")
                speak(result.spokenText, force = true)
            }
            is WhatsAppOrdinalChatResponse.AlreadyInChat -> {
                logBackground("WHATSAPP_OPEN_CHAT_FAILED reason=already_in_chat")
                speak(result.spokenText, force = true)
            }
            is WhatsAppOrdinalChatResponse.OutOfRange -> {
                logBackground("WHATSAPP_OPEN_CHAT_FAILED reason=out_of_range")
                speak(result.spokenText, force = true)
            }
            is WhatsAppOrdinalChatResponse.NeedsConfirmation -> {
                logBackground("WHATSAPP_VISIBLE_CHATS_READ")
                logBackground("WHATSAPP_CHAT_ORDINAL_RESOLVED index=${result.index}")
                pendingOrdinalChatOpen = result.displayName
                speak(result.spokenText, force = true)
            }
            is WhatsAppOrdinalChatResponse.Opened,
            is WhatsAppOrdinalChatResponse.CouldNotOpen -> Unit
        }
        return true
    }

    /**
     * Confirmación de la apertura por ordinal. "sí"/"abrilo" abre (reversible,
     * no envía); "no"/"cancelar" descarta. Un nuevo ordinal reemplaza el
     * pendiente. Verifica que de verdad se abrió un chat (no que seguimos en
     * la lista).
     */
    private suspend fun handlePendingOrdinalChatOpenReply(text: String): Boolean {
        val target = pendingOrdinalChatOpen ?: return false
        if (WhatsAppOrdinalChatParser.parse(text) != null) {
            // Un nuevo "abrí el N chat" reemplaza el pendiente: que re-resuelva.
            pendingOrdinalChatOpen = null
            return false
        }
        val normalized = VoicePhraseNormalizer.normalizeForParser(text).lowercase().trim()
        when {
            isOpenChatAffirmative(normalized) -> {
                logBackground("WHATSAPP_CHAT_SELECTED")
                when (val result = whatsAppOrdinalChatOpenUseCase.confirmOpen(target)) {
                    is WhatsAppOrdinalChatResponse.Opened -> {
                        delay(CHAT_OPEN_SETTLE_MILLIS)
                        val inChat = runCatching {
                            whatsAppScreenDetector.detect(screenContextProvider.current()).isInChat
                        }.getOrDefault(false)
                        logBackground("WHATSAPP_CHAT_OPENED verified=$inChat")
                        // Honesto: solo afirmamos que abrió si de verdad entramos
                        // al chat. En la versión actual de WhatsApp el tap de la
                        // fila no navega; en ese caso lo decimos sin mentir.
                        if (inChat) {
                            speak(result.spokenText, force = true)
                        } else {
                            speak(
                                "Encontré el chat, pero WhatsApp no me dejó abrirlo desde acá. " +
                                    "Tocalo vos en la pantalla y después pedime que lea los mensajes.",
                                force = true
                            )
                        }
                    }
                    is WhatsAppOrdinalChatResponse.NeedsAccessibilityService -> {
                        logBackground("WHATSAPP_OPEN_CHAT_FAILED reason=accessibility_off")
                        speak(result.spokenText, force = true)
                    }
                    is WhatsAppOrdinalChatResponse.NotInWhatsApp -> {
                        logBackground("WHATSAPP_OPEN_CHAT_FAILED reason=not_in_whatsapp")
                        speak(result.spokenText, force = true)
                    }
                    is WhatsAppOrdinalChatResponse.CouldNotOpen -> {
                        logBackground("WHATSAPP_OPEN_CHAT_FAILED reason=could_not_open")
                        speak(result.spokenText, force = true)
                    }
                    else -> {
                        logBackground("WHATSAPP_OPEN_CHAT_FAILED reason=unexpected")
                        speak("No pude abrir el chat. Probá: leeme los chats.", force = true)
                    }
                }
                pendingOrdinalChatOpen = null
            }
            isOpenChatCancel(normalized) -> {
                logBackground("WHATSAPP_OPEN_CHAT_FAILED reason=user_cancelled")
                pendingOrdinalChatOpen = null
                speak("Listo, no lo abro.", force = true)
            }
            else -> speak("Decí sí para abrir $target, o cancelá.", force = true)
        }
        return true
    }

    private fun isOpenChatAffirmative(normalized: String): Boolean =
        normalized in setOf(
            "si", "sí", "dale", "ok", "okey", "abrilo", "abrila", "abrir",
            "abrilo si", "si abrilo", "entra", "entrar", "abrilo dale", "abrime"
        )

    private fun isOpenChatCancel(normalized: String): Boolean =
        normalized in setOf(
            "no", "cancelar", "cancela", "no lo abras", "dejalo", "no abras",
            "no gracias", "mejor no", "basta", "para", "pará"
        )

    private suspend fun handleContextualWhatsApp(text: String): OrchestratorOutcome? {
        val snapshot = contextState.current
        if (snapshot.externalApp != ExternalAppName.WHATSAPP) return null

        handleWhatsAppReadFollowUp(text)?.let { return it }
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

    /**
     * Fase 2A.1 — lectura verbal de pantalla/chats/mensajes desde el modo
     * continuación. Estela está en background y WhatsApp adelante, así que el
     * snapshot de accesibilidad SÍ refleja WhatsApp. Verbal-only: nunca toca
     * botones, nunca envía, nunca inventa contenido.
     */
    private fun handleWhatsAppReadFollowUp(text: String): OrchestratorOutcome? {
        // 1) Lectura de pantalla general ("leé la pantalla", "qué puedo tocar").
        //    V1.10.4 — misma instrumentación y decoraciones que el fast path
        //    global: en WhatsApp estas frases también son lectura local
        //    (QA real: el resumen se hablaba pero sin log screenQuery=local
        //    ni nota de acciones sensibles). Los lectores RICOS de WhatsApp
        //    (mensajes/chats) siguen abajo para sus frases específicas.
        if (ScreenQueryPhrases.classify(text) != null) {
            return when (val result = screenUnderstandingUseCase.handle(text)) {
                ScreenUnderstandingResult.NotAScreenCommand -> null
                is ScreenUnderstandingResult.NeedsAccessibilityService -> {
                    logBackground("routing screenQuery=local needsAccessibility=true")
                    speakOnly(result.spokenText, AppState.SPEAKING)
                }
                is ScreenUnderstandingResult.Spoken -> {
                    logBackground(
                        "routing screenQuery=local mode=${result.mode} " +
                            "limited=${result.isLimited} safe=${result.isSafeToReadAloud}"
                    )
                    val limitedPrefix = if (result.isLimited && result.isSafeToReadAloud) {
                        "Esta app me está dando poca información accesible. " +
                            "Puedo decirte lo que encuentre, pero puede faltar contenido. "
                    } else {
                        ""
                    }
                    speakOnly(
                        limitedPrefix + ScreenActionSafetyNote.appendIfSensitive(result.spokenText),
                        AppState.SPEAKING
                    )
                }
            }
        }
        // 2) Mensajes del chat abierto ("leeme los mensajes", "último mensaje").
        if (WhatsAppMessageReadPhrases.classify(text) != null) {
            logBackground("routing whatsappRead=contextual kind=messages")
            return whatsAppMessagesOutcome(text)
        }
        // 3) Lista de chats visibles ("leeme los chats", "qué chats hay").
        if (WhatsAppChatListPhrases.isChatListCommand(text)) {
            logBackground("routing whatsappRead=contextual kind=chats")
            return whatsAppChatsOutcome(text)
        }
        // 4) Pedido genérico context-aware ("lee wp", "leeme WhatsApp").
        if (WhatsAppReadAloudPhrases.matches(text)) {
            if (!backgroundAccessibilityReady()) {
                return speakOnly(
                    WhatsAppVisibleMessagesReader.NEEDS_ACCESSIBILITY_TEXT,
                    AppState.SPEAKING
                )
            }
            val state = whatsAppScreenDetector.detect(screenContextProvider.current())
            return when {
                !state.isOpen -> speakOnly(
                    "No estoy viendo WhatsApp. Volvé a abrirlo si querés.",
                    AppState.SPEAKING
                )
                state.isInChat -> whatsAppMessagesOutcome("leeme los mensajes")
                else -> whatsAppChatsOutcome("leeme los chats")
            }
        }
        return null
    }

    private fun whatsAppMessagesOutcome(text: String): OrchestratorOutcome? =
        when (val result = whatsAppVisibleMessagesReader.handle(text)) {
            WhatsAppMessagesResponse.NotAMessageCommand -> null
            is WhatsAppMessagesResponse.NeedsAccessibilityService -> speakOnly(result.spokenText, AppState.SPEAKING)
            is WhatsAppMessagesResponse.NotInWhatsApp -> speakOnly(result.spokenText, AppState.SPEAKING)
            is WhatsAppMessagesResponse.NotInChat -> speakOnly(result.spokenText, AppState.SPEAKING)
            is WhatsAppMessagesResponse.StaleSnapshot -> speakOnly(result.spokenText, AppState.SPEAKING)
            is WhatsAppMessagesResponse.NoMessages -> speakOnly(result.spokenText, AppState.SPEAKING)
            is WhatsAppMessagesResponse.Read -> speakOnly(result.spokenText, AppState.SPEAKING)
        }

    private fun whatsAppChatsOutcome(text: String): OrchestratorOutcome? =
        when (val result = whatsAppVisibleChatsReader.handle(text)) {
            WhatsAppChatListResponse.NotAChatListCommand -> null
            is WhatsAppChatListResponse.NotInWhatsApp -> speakOnly(result.spokenText, AppState.SPEAKING)
            is WhatsAppChatListResponse.StateNotConfident -> speakOnly(result.spokenText, AppState.SPEAKING)
            is WhatsAppChatListResponse.InsideChat -> speakOnly(result.spokenText, AppState.SPEAKING)
            is WhatsAppChatListResponse.Listed -> speakOnly(result.spokenText, AppState.SPEAKING)
            is WhatsAppChatListResponse.NoChatsVisible -> speakOnly(result.spokenText, AppState.SPEAKING)
        }

    /**
     * V1.10.4b — comandos RICOS de lectura de WhatsApp ("leé los chats",
     * "leé los mensajes") cuando el contexto conversacional no está activo.
     * El flujo contextual sigue teniendo prioridad (corre antes); este hook
     * solo evita que las frases caigan al fallback. Verbal-only: los readers
     * jamás tocan botones ni envían, y responden honesto si WhatsApp no
     * está en pantalla.
     */
    private suspend fun handleForegroundWhatsAppReadCommand(text: String): Boolean {
        val isChatList = WhatsAppChatListPhrases.isChatListCommand(text)
        val isMessages = !isChatList && WhatsAppMessageReadPhrases.classify(text) != null
        if (!isChatList && !isMessages) return false
        // BUG 2: no fingir contexto WhatsApp. Si WhatsApp no es el contexto activo
        // (ni ventana ni tracker válido), NO leer un foreground falso (ej. Settings):
        // damos guía local segura. Sin lectura, sin UI, sin LLM/backend.
        if (!isWhatsAppActiveContext()) {
            logBackground("routing whatsappRead=blocked_no_context")
            speak(
                "No estoy en WhatsApp. Abrí WhatsApp, o decime: abrí WhatsApp, para leer los mensajes.",
                force = true
            )
            return true
        }
        logBackground("routing whatsappRead=foreground chatList=$isChatList")
        val outcome = if (isChatList) whatsAppChatsOutcome(text) else whatsAppMessagesOutcome(text)
        if (outcome == null) return false
        applyOutcome(outcome)
        return true
    }

    // --- V1.11: tareas asistidas (videollamada, audio, monitoreo, pagos) ---

    /**
     * Intenciones de tarea nuevas. Reglas duras:
     *  - pagos/tarjetas: SOLO guía hablada (jamás ingresar datos ni confirmar);
     *  - videollamada: detectar y preguntar; tocar SOLO tras confirmación;
     *  - audio: guía del gesto (grabar exige mantener apretado: gestos
     *    automatizados prohibidos por contrato);
     *  - monitoreo: solo LEE la pantalla del viaje; jamás pide ni confirma.
     */
    private suspend fun handleTaskAssistCommand(text: String): Boolean {
        PaymentGuidePhrases.classify(text)?.let { kind ->
            logBackground("taskIntent=payment kind=$kind")
            flashPresenceWarning()
            speak(PaymentGuidePhrases.spokenGuide(kind), force = true)
            return true
        }
        val videoRequest = WhatsAppCallPhrases.parseVideoCall(text)
        if (videoRequest != null) {
            logBackground("taskIntent=videoCall hasContact=${videoRequest.contactQuery != null}")
            conversationMemory.noteContext("pidió una videollamada")
            handleVideoCallRequest(videoRequest)
            return true
        }
        if (EstelaTaskIntentRouter.isAudioFlowActivation(text)) {
            speakAudioFlowGuide()
            return true
        }
        if (RideMonitorPhrases.isStopCommand(text)) {
            val wasActive = rideMonitorJob?.isActive == true
            logBackground("taskIntent=rideMonitor stop=true wasActive=$wasActive")
            if (wasActive) {
                stopRideMonitor(spoken = true)
            } else {
                speak("No estaba mirando ningún viaje.", force = true)
            }
            return true
        }
        if (RideMonitorPhrases.isStartCommand(text)) {
            logBackground("taskIntent=rideMonitor start=true")
            startRideMonitor()
            return true
        }
        return false
    }

    /** Pide o verifica el chat correcto; el TOQUE queda pendiente de sí/no. */
    private fun handleVideoCallRequest(request: WhatsAppCallPhrases.VideoCallRequest) {
        when (OjoClaroAccessibilityService.hasWhatsAppVideoCallButton()) {
            WhatsAppVideoCallCheck.Present -> {
                pendingVideoCallTap = true
                EstelaEarcons.pendingSensitive()
                speak(
                    "Veo el botón de videollamada en el chat abierto. " +
                        "¿Querés que lo toque? Decí: sí, o no.",
                    force = true
                )
            }
            WhatsAppVideoCallCheck.ServiceUnavailable -> speak(
                "Para eso necesito la accesibilidad de Estela activa en Ajustes.",
                force = true
            )
            WhatsAppVideoCallCheck.NotInWhatsApp,
            WhatsAppVideoCallCheck.NoButton -> {
                val contact = request.contactQuery
                if (contact == null) {
                    speak(
                        "Para la videollamada, abrí primero el chat de la " +
                            "persona, o decime: videollamada con, y el nombre.",
                        force = true
                    )
                    return
                }
                when (val resolved = smartComposeResolver.resolve(contact)) {
                    is com.ojoclaro.android.phone.ContactResolutionResult.Resolved -> {
                        val candidate = resolved.candidate
                        logBackground("taskIntent=videoCall openChat=true")
                        whatsAppIntentHelper.openChat(candidate.displayName, candidate.phoneE164)
                        speak(
                            "Abrí el chat de ${candidate.displayName}. Cuando " +
                                "esté en pantalla, decime: videollamada, y " +
                                "busco el botón para que lo confirmes.",
                            force = true
                        )
                    }
                    else -> speak(
                        "No encontré a $contact entre tus contactos guardados. " +
                            "Abrí su chat en WhatsApp y decime: videollamada.",
                        force = true
                    )
                }
            }
        }
    }

    /** @return true si el texto era la respuesta al "¿toco la videollamada?". */
    private fun handlePendingVideoCallReply(text: String): Boolean {
        if (!pendingVideoCallTap) return false
        if (OutdoorDestinationReply.isDecline(text)) {
            pendingVideoCallTap = false
            logBackground("videoCall outcome=declined")
            speak("Listo, no llamo.", force = true)
            return true
        }
        if (WhatsAppCallPhrases.isConfirmTap(text) || OutdoorDestinationReply.isYes(text)) {
            pendingVideoCallTap = false
            if (!whatsAppFlags.videoCallEnabled) {
                // Full Control Hardening: videollamada real DESACTIVADA por flag
                // (default). No se toca el botón aunque el usuario confirme.
                WhatsAppActionAudit.recordBlocked()
                logBackground("videoCall outcome=blocked_feature_disabled " + WhatsAppActionAudit.redactedSummary())
                speak(
                    "Las videollamadas reales están desactivadas en esta versión de prueba. No llamé.",
                    force = true
                )
                return true
            }
            val result = OjoClaroAccessibilityService.tapWhatsAppVideoCall()
            WhatsAppActionAudit.recordVideoCallTap()
            logBackground("videoCall outcome=${result.javaClass.simpleName} " + WhatsAppActionAudit.redactedSummary())
            when (result) {
                WhatsAppVideoCallTapResult.Tapped -> {
                    EstelaEarcons.confirm()
                    speak(
                        "Toqué videollamada. Para cortar, el botón rojo de " +
                            "colgar está abajo en el centro.",
                        force = true
                    )
                }
                WhatsAppVideoCallTapResult.NoButton -> speak(
                    "Ya no veo el botón de videollamada. Abrí el chat de la " +
                        "persona y pedímelo de nuevo.",
                    force = true
                )
                WhatsAppVideoCallTapResult.NotInWhatsApp -> speak(
                    "Ya no estoy viendo WhatsApp, así que no toqué nada.",
                    force = true
                )
                else -> speak(
                    "No pude tocar el botón. Probá de nuevo con el chat abierto.",
                    force = true
                )
            }
            return true
        }
        speak("¿Toco la videollamada? Decí: sí, o no.", force = true)
        return true
    }

    /**
     * Audio de WhatsApp: GUÍA del gesto real. Grabar exige mantener apretado
     * el micrófono (gesto continuo): la automatización de gestos está
     * prohibida por contrato, así que Estela acompaña y la persona graba.
     */
    private fun speakAudioFlowGuide() {
        val inWhatsApp = OjoClaroAccessibilityService.readActivePackageName()
            ?.lowercase()?.contains("whatsapp") == true
        logBackground("taskIntent=audioFlow inWhatsApp=$inWhatsApp")
        speak(
            if (inWhatsApp) {
                "Para mandar el audio: el botón del micrófono está abajo a la " +
                    "derecha, al lado del campo de escribir. Mantenelo " +
                    "apretado mientras hablás y soltalo para enviar. " +
                    "Yo no grabo ni envío audios por vos. Si preferís, " +
                    "decime el mensaje y lo mando como texto con tu confirmación."
            } else {
                "Abrí primero el chat de la persona en WhatsApp. El botón del " +
                    "micrófono está abajo a la derecha: mantenelo apretado " +
                    "mientras hablás y soltalo para enviar. " +
                    "Yo no grabo ni envío audios por vos."
            },
            force = true
        )
    }

    /**
     * Monitoreo HONESTO del viaje: cada tanto lee la pantalla SI la app del
     * viaje está adelante y avisa cambios de estado. No toca nada, no pide
     * nada. Presupuesto duro de tiempo; se corta con "dejá de mirar" o stop.
     */
    private fun startRideMonitor() {
        logBackground(
            "rideMonitor begin singleShot=$accessibilityOverlayVoiceSingleShot " +
                "alreadyActive=${rideMonitorJob?.isActive == true}"
        )
        if (rideMonitorJob?.isActive == true) {
            speak("Ya estoy mirando el viaje. Para frenar, decí: dejá de mirar.", force = true)
            return
        }
        if (accessibilityOverlayVoiceSingleShot) {
            // El turno single-shot muere al terminar de hablar: el monitoreo
            // necesita el modo asistente persistente (notificación activa).
            speak(
                "Para seguir el viaje necesito quedarme activa: abrí Estela " +
                    "en modo asistente y volvé a pedírmelo.",
                force = true
            )
            return
        }
        rideMonitorLastStatusIndex = -1
        speak(
            "Listo, sigo el viaje. Mientras la app esté en pantalla te aviso " +
                "los cambios: confirmado, llegando, o afuera. Para frenar, " +
                "decí: dejá de mirar.",
            force = true
        )
        rideMonitorJob = serviceScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - startedAt < RIDE_MONITOR_MAX_MILLIS) {
                delay(RIDE_MONITOR_INTERVAL_MILLIS)
                val pkg = OjoClaroAccessibilityService.readActivePackageName()
                    ?.lowercase().orEmpty()
                if (!isRideAppPackage(pkg)) continue
                val visibleText = runCatching { screenContextProvider.current()?.text }
                    .getOrNull()?.lowercase()?.removeSpanishAccents() ?: continue
                val statusIndex = RideMonitorPhrases.STATUS_KEYWORDS.indexOfLast { (keyword, _) ->
                    visibleText.contains(keyword)
                }
                if (statusIndex > rideMonitorLastStatusIndex) {
                    rideMonitorLastStatusIndex = statusIndex
                    logBackground("rideMonitor statusIdx=$statusIndex")
                    speak(RideMonitorPhrases.STATUS_KEYWORDS[statusIndex].second, force = true)
                }
            }
            logBackground("rideMonitor finished=time_budget")
        }
    }

    private fun stopRideMonitor(spoken: Boolean) {
        rideMonitorJob?.cancel()
        rideMonitorJob = null
        rideMonitorLastStatusIndex = -1
        if (spoken) speak("Listo, dejo de mirar el viaje.", force = true)
    }

    private fun isRideAppPackage(packageName: String): Boolean =
        packageName.contains("didi") || packageName.contains("ubercab") ||
            packageName.contains("cabify")

    // --- V1.12: Instagram Direct asistido (segundo canal de mensajería) ---

    /**
     * Despacho de tareas de Instagram. SOLO frases con marca explícita del
     * canal llegan acá (sin marca el router devuelve null y las rutas del
     * piloto siguen intactas). Detectar jamás toca; cada acción sensible
     * tiene su confirmación; pagos/tarjetas nunca entran por mensajería.
     */
    private suspend fun handleInstagramTaskCommand(text: String): Boolean {
        val request = MessagingTaskRouter.classify(text) ?: return false
        logBackground(
            "instagramTask intent=${request.intent} " +
                "hasContact=${request.contactQuery != null} " +
                "msgLen=${request.messageText?.length ?: 0}"
        )
        when (request.intent) {
            MessagingTaskIntent.OPEN_MESSAGING_APP ->
                handleInstagramOpenRequest(request.inboxRequested)
            MessagingTaskIntent.OPEN_CHAT -> {
                val contact = request.contactQuery ?: return false
                handleInstagramOpenChatRequest(contact)
            }
            MessagingTaskIntent.SEND_TEXT_PENDING_CONFIRMATION -> {
                val contact = request.contactQuery ?: return false
                val message = request.messageText ?: return false
                handleInstagramSendRequest(contact, message)
            }
            MessagingTaskIntent.START_VIDEO_CALL_PENDING_CONFIRMATION ->
                handleInstagramVideoCallRequest(request.contactQuery)
            MessagingTaskIntent.START_AUDIO_FLOW -> speakInstagramAudioGuide()
            else -> return false
        }
        return true
    }

    /** Apertura resistente (misma técnica que DiDi V1.10.2): launch intent
     *  del package manager lanzado desde el servicio de accesibilidad. */
    private fun launchInstagramApp(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(INSTAGRAM_PACKAGE_NAME)
        if (intent == null) {
            logBackground("instagramOpen launched=false reason=no_launch_intent")
            return false
        }
        val launched = OjoClaroAccessibilityService.launchIntentFromService(intent) ||
            runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }.isSuccess
        logBackground("instagramOpen launched=$launched")
        return launched
    }

    private suspend fun handleInstagramOpenRequest(inboxRequested: Boolean) {
        val state = OjoClaroAccessibilityService.instagramScreenCheck().state
        if (state == InstagramScreenState.NOT_IN_INSTAGRAM) {
            if (!launchInstagramApp()) {
                speak(
                    "No pude abrir Instagram. ¿Está instalado en este teléfono?",
                    force = true
                )
                return
            }
            if (!inboxRequested) {
                speak("Abriendo Instagram.", force = true)
                return
            }
            delay(INSTAGRAM_NAV_LAUNCH_DELAY_MILLIS)
        } else if (!inboxRequested) {
            speak("Instagram ya está abierto.", force = true)
            return
        }
        openInstagramInboxStep()
    }

    /** Entra al Direct desde donde esté Instagram; honesto si no llega. */
    private suspend fun openInstagramInboxStep() {
        when (OjoClaroAccessibilityService.instagramScreenCheck().state) {
            InstagramScreenState.INBOX -> {
                speak("Ya estás en los mensajes de Instagram.", force = true)
                return
            }
            InstagramScreenState.THREAD -> {
                speak("Ya estás en un chat de Instagram.", force = true)
                return
            }
            InstagramScreenState.NOT_IN_INSTAGRAM -> {
                speak("No estoy viendo Instagram, así que no toqué nada.", force = true)
                return
            }
            else -> Unit
        }
        when (OjoClaroAccessibilityService.openInstagramDirectTab()) {
            InstagramTapOutcome.Tapped -> {
                delay(INSTAGRAM_NAV_STEP_DELAY_MILLIS)
                val after = OjoClaroAccessibilityService.instagramScreenCheck().state
                logBackground("instagramNav step=inbox after=$after")
                if (after == InstagramScreenState.INBOX) {
                    speak("Listo, estás en los mensajes de Instagram.", force = true)
                } else {
                    speak(
                        "Toqué el botón de mensajes pero no pude confirmar la " +
                            "pantalla. Decí: leé la pantalla, para chequear.",
                        force = true
                    )
                }
            }
            InstagramTapOutcome.NotFound -> speak(
                "No encontré el botón de mensajes de Instagram en esta " +
                    "pantalla. Está en la barra de abajo, es el cuarto botón.",
                force = true
            )
            InstagramTapOutcome.NotInInstagram -> speak(
                "No estoy viendo Instagram, así que no toqué nada.",
                force = true
            )
            InstagramTapOutcome.ClickFailed -> speak(
                "No pude tocar el botón de mensajes. Probá de nuevo.",
                force = true
            )
            InstagramTapOutcome.ServiceUnavailable -> speak(
                "No puedo ver la pantalla todavía. Verificá que la " +
                    "accesibilidad de Estela esté activa.",
                force = true
            )
        }
    }

    /**
     * Lleva Instagram hasta el chat de [contactQuery]: lanza la app si hace
     * falta, entra al Direct y toca la fila visible que matchee. Devuelve la
     * etiqueta REAL del chat abierto (para anunciarla antes de confirmar
     * nada), o null después de hablar el error honesto.
     */
    private suspend fun navigateToInstagramThread(contactQuery: String): String? {
        var check = OjoClaroAccessibilityService.instagramScreenCheck()
        if (check.state == InstagramScreenState.NOT_IN_INSTAGRAM) {
            if (!launchInstagramApp()) {
                speak("No pude abrir Instagram.", force = true)
                return null
            }
            delay(INSTAGRAM_NAV_LAUNCH_DELAY_MILLIS)
            check = OjoClaroAccessibilityService.instagramScreenCheck()
        }
        if (check.state == InstagramScreenState.THREAD) {
            if (instagramThreadMatchesContact(check, contactQuery)) {
                return check.threadTitle ?: contactQuery
            }
            speak(
                "Estás en otro chat de Instagram. Decí: volver, y después " +
                    "pedímelo de nuevo.",
                force = true
            )
            return null
        }
        if (check.state == InstagramScreenState.FEED_OR_HOME ||
            check.state == InstagramScreenState.UNKNOWN
        ) {
            val tab = OjoClaroAccessibilityService.openInstagramDirectTab()
            logBackground("instagramNav step=tab outcome=${tab.javaClass.simpleName}")
            if (tab != InstagramTapOutcome.Tapped) {
                speak(
                    "No pude entrar a los mensajes de Instagram desde esta " +
                        "pantalla. Abrí los mensajes y pedímelo de nuevo.",
                    force = true
                )
                return null
            }
            delay(INSTAGRAM_NAV_STEP_DELAY_MILLIS)
        }
        return when (
            val open = OjoClaroAccessibilityService.openInstagramChatByVisibleName(contactQuery)
        ) {
            is InstagramChatOpenResult.Opened -> {
                delay(INSTAGRAM_NAV_STEP_DELAY_MILLIS)
                val after = OjoClaroAccessibilityService.instagramScreenCheck()
                logBackground("instagramNav step=chat after=${after.state}")
                if (after.state == InstagramScreenState.THREAD) {
                    after.threadTitle ?: open.matchedLabel
                } else {
                    speak(
                        "Toqué el chat pero no pude confirmar que se abrió. " +
                            "Probá de nuevo.",
                        force = true
                    )
                    null
                }
            }
            is InstagramChatOpenResult.NoMatch -> {
                speak(
                    "No veo un chat de ${open.query} en los mensajes de " +
                        "Instagram.",
                    force = true
                )
                null
            }
            InstagramChatOpenResult.NotInInbox -> {
                speak("No llegué a la lista de mensajes de Instagram.", force = true)
                null
            }
            InstagramChatOpenResult.NotInInstagram -> {
                speak("No estoy viendo Instagram, así que no toqué nada.", force = true)
                null
            }
            else -> {
                speak("No pude abrir ese chat de Instagram.", force = true)
                null
            }
        }
    }

    /**
     * V1.12.1 — el pedido puede venir por nombre ("Sofia") o por handle
     * dictado ("so roomero" por "so_roomero"): cuentan el título Y el
     * subtítulo del header. Misma regla de matching que el inbox.
     */
    private fun instagramThreadMatchesContact(
        check: InstagramScreenCheck,
        query: String
    ): Boolean =
        InstagramNameMatcher.matches(check.threadTitle, query) ||
            InstagramNameMatcher.matches(check.threadSubtitle, query)

    private suspend fun handleInstagramOpenChatRequest(contactQuery: String) {
        val label = navigateToInstagramThread(contactQuery) ?: return
        speak("Abrí el chat de $label en Instagram.", force = true)
    }

    /**
     * Prepara el envío por Instagram: chat correcto, borrador escrito y
     * verificado, y pregunta con el NOMBRE REAL del chat y el texto exacto.
     * Enviar exige la palabra fuerte; "sí" jamás alcanza.
     */
    private suspend fun handleInstagramSendRequest(contactQuery: String, messageText: String) {
        if (WhatsAppVoiceSendPhrases.looksSensitive(messageText)) {
            logBackground("instagramSend outcome=sensitive_blocked")
            flashPresenceWarning()
            speak(
                "Eso parece un dato sensible, como una clave o una tarjeta. " +
                    "Por seguridad no lo envío por voz: escribilo vos a mano.",
                force = true
            )
            return
        }
        val label = navigateToInstagramThread(contactQuery) ?: return
        when (OjoClaroAccessibilityService.setInstagramDraftText(messageText)) {
            InstagramDraftSetResult.SetOk -> {
                pendingInstagramSend = PendingInstagramSend(
                    contactLabel = label,
                    draft = messageText,
                    armedAtMillis = SystemClock.elapsedRealtime()
                )
                EstelaEarcons.pendingSensitive()
                logBackground("instagramSend outcome=draft_armed draftLen=${messageText.length}")
                speak(
                    "Voy a enviarle a $label por Instagram: '$messageText'. " +
                        "Para enviar decí: mandalo. Para cancelar decí: cancelar.",
                    force = true
                )
            }
            InstagramDraftSetResult.MismatchAfterSet -> {
                logBackground("instagramSend outcome=draft_mismatch_after_set")
                speak(
                    "El texto no quedó bien escrito en el campo, así que no " +
                        "preparo el envío. Probá de nuevo.",
                    force = true
                )
            }
            InstagramDraftSetResult.NoComposer, InstagramDraftSetResult.NotInThread -> speak(
                "No veo el campo de escribir de este chat, así que no " +
                    "preparé nada.",
                force = true
            )
            InstagramDraftSetResult.NotInInstagram -> speak(
                "No estoy viendo Instagram, así que no preparé nada.",
                force = true
            )
            else -> speak(
                "No pude escribir el borrador. Probá de nuevo con el chat " +
                    "abierto.",
                force = true
            )
        }
    }

    /** @return true si el texto era la respuesta al envío pendiente de Instagram. */
    private fun handlePendingInstagramSendReply(text: String): Boolean {
        val pending = pendingInstagramSend ?: return false
        if (messagingPendingExpired(pending.armedAtMillis)) {
            pendingInstagramSend = null
            OjoClaroAccessibilityService.clearInstagramDraftText()
            logBackground("instagramSend outcome=expired")
            speak(
                "Esa confirmación venció, así que no envié nada. Pedímelo " +
                    "de nuevo.",
                force = true
            )
            return true
        }
        when (MessagingTaskRouter.classifyTextSendReply(text)) {
            MessagingReplyOutcome.CANCEL -> {
                pendingInstagramSend = null
                OjoClaroAccessibilityService.clearInstagramDraftText()
                logBackground("instagramSend outcome=cancelled_by_user")
                speak("Listo, no envío nada.", force = true)
            }
            MessagingReplyOutcome.CONFIRM -> {
                pendingInstagramSend = null
                val result = OjoClaroAccessibilityService.tapInstagramSend(pending.draft)
                logBackground("instagramSend outcome=${result.javaClass.simpleName}")
                if (result == InstagramSendTapResult.Sent) {
                    EstelaEarcons.confirm()
                    conversationMemory.noteContext("envió un mensaje de Instagram confirmado")
                }
                val spoken = when (result) {
                    // Copy honesto: Estela vio que el toque funcionó, no la
                    // entrega (la política de copys prohíbe afirmar entregas).
                    InstagramSendTapResult.Sent -> "Listo, toqué enviar."
                    InstagramSendTapResult.FieldMismatch ->
                        "El texto del campo cambió, así que no envié nada. " +
                            "Revisalo y pedímelo de nuevo."
                    InstagramSendTapResult.NoSendButton ->
                        "No encontré el botón de enviar, así que no envié nada."
                    InstagramSendTapResult.NoComposer ->
                        "Ya no veo el campo de escribir, así que no envié nada."
                    InstagramSendTapResult.NotInInstagram ->
                        "No estoy viendo Instagram, así que no envié nada."
                    else ->
                        "No pude tocar enviar. El mensaje quedó escrito; " +
                            "podés enviarlo a mano."
                }
                speak(spoken, force = true)
            }
            MessagingReplyOutcome.WEAK_REJECTED -> {
                // "sí" / "dale" / "ok" a secas no alcanzan para enviar.
                logBackground("instagramSend outcome=weak_confirmation_rejected")
                speak(
                    "Para enviar, decí: mandalo. Para cancelar, decí: cancelar.",
                    force = true
                )
            }
            MessagingReplyOutcome.OTHER -> {
                logBackground("instagramSend outcome=reprompt")
                speak(
                    "Tenés un mensaje de Instagram listo. Decí: mandalo. " +
                        "O decí: cancelar.",
                    force = true
                )
            }
        }
        return true
    }

    /** Pide o verifica el chat correcto; el TOQUE queda pendiente de sí/no. */
    private suspend fun handleInstagramVideoCallRequest(contactQuery: String?) {
        conversationMemory.noteContext("pidió una videollamada de Instagram")
        val inThread = OjoClaroAccessibilityService.instagramScreenCheck().state ==
            InstagramScreenState.THREAD
        if (!inThread) {
            if (contactQuery == null) {
                speak(
                    "Abrí el chat de la persona en Instagram, o decime con " +
                        "quién querés la videollamada.",
                    force = true
                )
                return
            }
            navigateToInstagramThread(contactQuery) ?: return
        }
        when (OjoClaroAccessibilityService.hasInstagramVideoCallButton()) {
            InstagramVideoCallCheck.Present -> {
                pendingInstagramVideoCallArmedAt = SystemClock.elapsedRealtime()
                EstelaEarcons.pendingSensitive()
                logBackground("instagramVideoCall outcome=armed")
                speak(
                    "Veo el botón de videollamada en este chat de Instagram. " +
                        "¿Querés que lo toque? Decí: sí, o no.",
                    force = true
                )
            }
            InstagramVideoCallCheck.NoButton -> {
                logBackground("instagramVideoCall outcome=no_button")
                speak(
                    "En este chat no veo el botón de videollamada de Instagram.",
                    force = true
                )
            }
            InstagramVideoCallCheck.NotInThread -> speak(
                "Abrí el chat de la persona en Instagram y pedímelo de nuevo.",
                force = true
            )
            InstagramVideoCallCheck.NotInInstagram -> speak(
                "No estoy viendo Instagram, así que no toqué nada.",
                force = true
            )
            InstagramVideoCallCheck.ServiceUnavailable -> speak(
                "No puedo ver la pantalla todavía. Verificá que la " +
                    "accesibilidad de Estela esté activa.",
                force = true
            )
        }
    }

    /** @return true si el texto era la respuesta al "¿toco la videollamada?". */
    private fun handlePendingInstagramVideoCallReply(text: String): Boolean {
        val armedAt = pendingInstagramVideoCallArmedAt ?: return false
        if (messagingPendingExpired(armedAt)) {
            pendingInstagramVideoCallArmedAt = null
            logBackground("instagramVideoCall outcome=expired")
            speak("Esa pregunta venció, así que no llamo. Pedímelo de nuevo.", force = true)
            return true
        }
        when (MessagingTaskRouter.classifyVideoTapReply(text)) {
            MessagingReplyOutcome.CANCEL -> {
                pendingInstagramVideoCallArmedAt = null
                logBackground("instagramVideoCall outcome=declined")
                speak("Listo, no llamo.", force = true)
            }
            MessagingReplyOutcome.CONFIRM -> {
                pendingInstagramVideoCallArmedAt = null
                val result = OjoClaroAccessibilityService.tapInstagramVideoCall()
                logBackground("instagramVideoCall outcome=${result.javaClass.simpleName}")
                when (result) {
                    InstagramVideoCallTapResult.Tapped -> {
                        EstelaEarcons.confirm()
                        speak(
                            "Toqué videollamada en Instagram. Para cortar, " +
                                "tocá el botón rojo de colgar.",
                            force = true
                        )
                    }
                    InstagramVideoCallTapResult.NoButton -> speak(
                        "Ya no veo el botón de videollamada. Abrí el chat de " +
                            "la persona y pedímelo de nuevo.",
                        force = true
                    )
                    InstagramVideoCallTapResult.NotInThread,
                    InstagramVideoCallTapResult.NotInInstagram -> speak(
                        "Ya no estoy viendo ese chat de Instagram, así que no " +
                            "toqué nada.",
                        force = true
                    )
                    else -> speak(
                        "No pude tocar el botón. Probá de nuevo con el chat " +
                            "abierto.",
                        force = true
                    )
                }
            }
            else -> {
                logBackground("instagramVideoCall outcome=reprompt")
                speak("¿Toco la videollamada de Instagram? Decí: sí, o no.", force = true)
            }
        }
        return true
    }

    /**
     * Audio por Instagram: GUÍA del gesto real. Grabar exige mantener
     * presionado el micrófono (gesto continuo): la automatización de gestos
     * está prohibida por contrato. El botón solo se DETECTA para ubicarlo
     * mejor en la guía; no existe ninguna función que lo toque.
     */
    private fun speakInstagramAudioGuide() {
        val inThread = OjoClaroAccessibilityService.instagramScreenCheck().state ==
            InstagramScreenState.THREAD
        val audioButton = if (inThread) {
            OjoClaroAccessibilityService.findInstagramAudioButton()
        } else {
            null
        }
        logBackground(
            "instagramAudio guided=true inThread=$inThread " +
                "buttonPresent=${audioButton?.present == true}"
        )
        speak(
            if (inThread && audioButton?.present == true) {
                "Estoy en el chat. Te dejo ubicado el botón de audio: es el " +
                    "micrófono, abajo a la derecha del campo de escribir. " +
                    "Mantenelo apretado mientras hablás y soltalo para enviar. " +
                    "Grabá vos el mensaje: yo no grabo ni envío audios por vos."
            } else {
                "Abrí primero el chat de la persona en Instagram. El botón " +
                    "del micrófono está abajo a la derecha del campo de " +
                    "escribir: mantenelo apretado mientras hablás y soltalo " +
                    "para enviar. Yo no grabo ni envío audios por vos."
            },
            force = true
        )
    }

    private fun messagingPendingExpired(armedAtMillis: Long): Boolean =
        SystemClock.elapsedRealtime() - armedAtMillis > MESSAGING_PENDING_TTL_MILLIS

    /**
     * V1.12.1 — ruta local GLOBAL para "volver"/"atrás" (QA real: fuera de
     * los flujos contextuales caía al fallback). Reusa el parser de Fase 2A
     * (cero frases nuevas) y ejecuta SOLO el BACK global, que es reversible
     * y jamás envía, borra, llama ni comparte.
     */
    /**
     * Sprint WhatsApp: scroll seguro sobre el contenedor scrollable visible y
     * re-lectura del nuevo contenido. Mantiene el foco en la app actual
     * (WhatsApp), usa AccessibilityService real, jamás envía/borra/toca acciones
     * peligrosas. Logs WHATSAPP_SCROLL_*.
     */
    private suspend fun handleGlobalScrollCommand(text: String): Boolean {
        val forward = when (ScreenNavigationCommandParser.parse(text)) {
            ScreenNavigationCommand.ScrollDown -> true
            ScreenNavigationCommand.ScrollUp -> false
            else -> return false
        }
        val direction = if (forward) "down" else "up"
        logBackground("ROUTING_AUDIT handler=whatsapp_scroll")
        logBackground("WHATSAPP_SCROLL_REQUESTED")
        logBackground("WHATSAPP_SCROLL_DIRECTION direction=$direction")
        if (!backgroundAccessibilityReady()) {
            logBackground("WHATSAPP_SCROLL_FAILED reason=accessibility_off")
            speak(
                "Necesito que actives Accesibilidad de Ojo Claro para desplazar la pantalla.",
                force = true
            )
            return true
        }
        when (OjoClaroAccessibilityService.scrollVisibleContainer(forward)) {
            ScreenScrollOutcome.SCROLLED -> {
                logBackground("WHATSAPP_SCROLL_DONE outcome=SCROLLED direction=$direction")
                // Pequeña espera para que el snapshot post-scroll esté fresco
                // (el evento de scroll de accesibilidad llega asincrónico).
                delay(SCROLL_SETTLE_MILLIS)
                readVisibleContentAfterScroll(forward)
            }
            ScreenScrollOutcome.NO_TARGET -> {
                logBackground("WHATSAPP_SCROLL_FAILED reason=no_target direction=$direction")
                speak(
                    if (forward) {
                        "No tengo más para seguir leyendo ahora. Puedo leerte lo que está en pantalla."
                    } else {
                        "Ya estás arriba de todo."
                    },
                    force = true
                )
            }
            ScreenScrollOutcome.UNAVAILABLE -> {
                logBackground("WHATSAPP_SCROLL_FAILED reason=unavailable direction=$direction")
                logBackground("WHATSAPP_RECOVERY_MESSAGE_SPOKEN reason=scroll_unavailable")
                speak("No pude desplazar acá. Podés pedirme que lea lo visible.", force = true)
            }
        }
        return true
    }

    /**
     * Tras scrollear lee el NUEVO contenido visible. En un chat de WhatsApp lee
     * los mensajes ahora visibles; si no, da una confirmación corta. Verbal-only:
     * jamás toca botones ni envía.
     */
    private suspend fun readVisibleContentAfterScroll(forward: Boolean) {
        val state = runCatching {
            whatsAppScreenDetector.detect(screenContextProvider.current())
        }.getOrNull()
        if (state?.isInChat == true) {
            logBackground("WHATSAPP_MESSAGES_READ_AFTER_SCROLL")
            whatsAppMessagesOutcome("leeme los mensajes")?.let {
                applyOutcome(it)
                return
            }
        }
        speak(if (forward) "Bajé." else "Subí.", force = true)
    }

    private fun handleGlobalBackCommand(text: String): Boolean {
        if (ScreenNavigationCommandParser.parse(text) != ScreenNavigationCommand.Back) {
            return false
        }
        val performed = OjoClaroAccessibilityService.performGlobalBack()
        logBackground("navigationBack outcome=${if (performed) "performed" else "noop"}")
        speak(
            if (performed) {
                "Listo, volví atrás."
            } else {
                "No pude volver atrás. Verificá que la accesibilidad de " +
                    "Estela esté activa."
            },
            force = true
        )
        return true
    }

    // --- V1.13: Camera Assist (cámara propia, OCR local, escena bajo demanda) ---

    /**
     * Despacho de Camera Assist. Reglas duras:
     *  - detectar jamás captura: cada acción tiene su tono audible;
     *  - el modo cámara PERSISTENTE y el watch exigen modo asistente
     *    (notificación visible), igual que el monitoreo de viaje; los
     *    pedidos puntuales (leer un cartel, describir una vez) funcionan
     *    también en el turno single-shot, abriendo y cerrando la cámara;
     *  - los frames viven en memoria y se descartan; el texto leído jamás
     *    se loguea (solo longitudes).
     */
    private suspend fun handleCameraAssistCommand(text: String): Boolean {
        val command = CameraAssistPhrases.parse(text, cameraAssistRunning) ?: return false
        logBackground("cameraAssist intent=$command active=$cameraAssistRunning")
        when (command) {
            CameraAssistPhrases.Command.CAMERA_START -> startCameraAssistMode()
            CameraAssistPhrases.Command.CAMERA_STOP ->
                if (cameraAssistRunning) {
                    stopCameraAssist(spoken = true)
                } else {
                    speak("La cámara ya está cerrada.", force = true)
                }
            CameraAssistPhrases.Command.CAMERA_PAUSE -> pauseCameraAssist()
            CameraAssistPhrases.Command.TEXT_SCAN -> runCameraTextScan()
            CameraAssistPhrases.Command.WATCH_TEXT -> startCameraTextWatch()
            CameraAssistPhrases.Command.SCENE_DESCRIBE -> runCameraSceneDescribe()
        }
        return true
    }

    private suspend fun startCameraAssistMode() {
        if (cameraAssistRunning) {
            if (cameraAssistController.isPaused) {
                cameraAssistController.resumeAnalysis()
                speak("Reanudo la cámara.", force = true)
            } else {
                speak("La cámara ya está abierta.", force = true)
            }
            return
        }
        if (accessibilityOverlayVoiceSingleShot) {
            // Modo persistente solo con notificación visible (transparencia).
            speak(
                "Para dejar la cámara abierta necesito el modo asistente. " +
                    "Igual puedo leer algo puntual: decí, qué dice este cartel.",
                force = true
            )
            return
        }
        ensureCameraAssistStarted(
            spokenIntro = "Activo cámara. Apuntá al texto y decí: leé el texto."
        )
    }

    /** Abre la cámara si hace falta. Habla los errores honestos. */
    private suspend fun ensureCameraAssistStarted(spokenIntro: String?): Boolean {
        if (cameraAssistRunning) {
            if (cameraAssistController.isPaused) cameraAssistController.resumeAnalysis()
            return true
        }
        cameraAssistSession.onCameraStarting()
        cameraAssistController.onOcrText = { detected ->
            serviceScope.launch { onCameraOcrText(detected) }
        }
        return when (cameraAssistController.start()) {
            CameraAssistController.StartResult.STARTED,
            CameraAssistController.StartResult.ALREADY_RUNNING -> {
                cameraAssistRunning = true
                cameraAssistSession.onCameraReady()
                // Promociona el foreground a microphone|camera mientras dure.
                startForegroundSafely(contextState.current)
                // V1.14 — cámara encendida: presencia CAMERA_ACTIVE (salvo que
                // hable, que tiene prioridad por el spokenIntro de abajo).
                updatePresence { it.copy(cameraActive = true) }
                if (spokenIntro != null) speak(spokenIntro, force = true)
                true
            }
            CameraAssistController.StartResult.NO_PERMISSION -> {
                cameraAssistSession.onCameraPermissionMissing()
                logBackground("cameraAssist error=no_permission")
                flashPresenceWarning()
                speak(
                    "No tengo permiso de cámara. Tenés que activarlo para " +
                        "Estela: abrí la app de Estela y aceptá el permiso de cámara.",
                    force = true
                )
                false
            }
            CameraAssistController.StartResult.UNAVAILABLE -> {
                cameraAssistSession.onCameraUnavailable()
                logBackground("cameraAssist error=camera_unavailable")
                flashPresenceWarning()
                speak(
                    "La cámara no está disponible. Puede estar en uso por otra app.",
                    force = true
                )
                false
            }
        }
    }

    private fun stopCameraAssist(spoken: Boolean) {
        cameraWatchJob?.cancel()
        cameraWatchJob = null
        if (!cameraAssistControllerDelegate.isInitialized()) return
        val wasRunning = cameraAssistRunning
        cameraAssistRunning = false
        cameraAssistSession.onClosed()
        cameraAssistController.close()
        // V1.14 — cámara cerrada: la presencia deja CAMERA_ACTIVE y vuelve al
        // reposo (IDLE si no hay otra señal activa).
        updatePresence { it.copy(cameraActive = false) }
        if (wasRunning) {
            logBackground("cameraAssist closed=true")
            if (spoken) speak("Listo, cerré la cámara.", force = true)
        }
    }

    private fun pauseCameraAssist() {
        if (!cameraAssistRunning) {
            speak("La cámara no está abierta.", force = true)
            return
        }
        cameraWatchJob?.cancel()
        cameraWatchJob = null
        cameraAssistSession.stopWatch()
        cameraAssistController.pauseAnalysis()
        speak("Pauso la cámara. Decí: activá la cámara, para seguir.", force = true)
    }

    /** Texto del OCR local: la sesión decide si se habla; acá solo el copy.
     *  El contenido jamás se loguea: solo longitudes y conteos. */
    private fun onCameraOcrText(detected: String) {
        when (val decision = cameraAssistSession.onOcrText(detected)) {
            is CameraAssistSession.OcrDecision.Speak -> {
                val elapsed = if (decision.fromWatch) {
                    -1L
                } else {
                    SystemClock.elapsedRealtime() - cameraScanStartedAtMillis
                }
                logBackground(
                    "cameraTextScan textLen=${decision.text.length} " +
                        "blockCount=${decision.text.lines().size} " +
                        "elapsedMs=$elapsed watch=${decision.fromWatch} " +
                        "sensitive=${decision.sensitive} source=camera_frame"
                )
                speakCameraText(decision)
            }
            CameraAssistSession.OcrDecision.Ignore -> Unit
        }
    }

    private fun speakCameraText(decision: CameraAssistSession.OcrDecision.Speak) {
        if (decision.sensitive) {
            // Tarjetas/claves/códigos: ubicar sin repetir el contenido.
            flashPresenceWarning()
            speak(
                "Veo texto que parece información sensible, como una clave o " +
                    "una tarjeta. Puedo ayudarte a ubicarla, pero no la voy a " +
                    "leer completa en voz alta.",
                force = true
            )
            return
        }
        val text = decision.text
        if (text.length > MAX_SPOKEN_OCR_CHARS) {
            speak(
                "Es mucho texto. Te leo el comienzo: ${text.take(MAX_SPOKEN_OCR_CHARS)}",
                force = true
            )
        } else {
            speak("Leo: $text", force = true)
        }
    }

    private suspend fun runCameraTextScan() {
        val openedAdHoc = !cameraAssistRunning
        val intro = if (openedAdHoc) "Activo cámara. Apuntá al texto." else null
        if (!ensureCameraAssistStarted(spokenIntro = intro)) return
        cameraScanStartedAtMillis = SystemClock.elapsedRealtime()
        cameraAssistSession.beginTextScan()
        while (cameraAssistSession.state == CameraAssistSession.State.TEXT_SCAN_ONESHOT) {
            delay(CAMERA_SCAN_POLL_MILLIS)
            if (cameraAssistSession.checkDeadlines() ==
                CameraAssistSession.DeadlineEvent.ScanTimedOutWithoutText
            ) {
                logBackground("cameraTextScan textLen=0 result=no_text source=camera_frame")
                speak(
                    "No llego a leer texto claro. Acercá un poco la cámara o " +
                        "apuntá mejor.",
                    force = true
                )
            }
        }
        // En turno single-shot la cámara abierta solo para este pedido se
        // cierra al terminar: el modo continuo exige modo asistente.
        if (openedAdHoc && accessibilityOverlayVoiceSingleShot) {
            stopCameraAssist(spoken = false)
        }
    }

    private suspend fun startCameraTextWatch() {
        if (accessibilityOverlayVoiceSingleShot) {
            speak(
                "Para quedarme mirando necesito quedarme activa: abrí Estela " +
                    "en modo asistente y volvé a pedírmelo.",
                force = true
            )
            return
        }
        if (!ensureCameraAssistStarted(spokenIntro = null)) return
        cameraAssistSession.beginWatch()
        logBackground("cameraWatch started=true")
        speak("Estoy mirando. Te aviso si aparece texto.", force = true)
        cameraWatchJob?.cancel()
        cameraWatchJob = serviceScope.launch {
            while (cameraAssistSession.state == CameraAssistSession.State.WATCHING_TEXT) {
                delay(1_000L)
                if (cameraAssistSession.checkDeadlines() ==
                    CameraAssistSession.DeadlineEvent.WatchBudgetExhausted
                ) {
                    logBackground("cameraWatch ended=budget")
                    speak(
                        "Dejo de mirar. La cámara sigue abierta: decí, cerrá " +
                            "la cámara, cuando quieras.",
                        force = true
                    )
                }
            }
        }
    }

    private suspend fun runCameraSceneDescribe() {
        if (!cameraAssistRunning) {
            // Sin cámara propia abierta, la escena la describe el flujo de
            // visión de Outdoor (una foto, FGS camera propio, habla con su
            // propio TTS): cero duplicación de cámara.
            logBackground("cameraScene delegated=outdoor")
            runCatching {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, OutdoorForegroundService::class.java)
                        .setAction(OutdoorForegroundService.ACTION_DESCRIBE)
                )
            }
            return
        }
        cameraAssistSession.beginSceneDescribe()
        speak("Voy a mirar. Un momento.", force = true)
        val outcome = cameraSceneDescriber.describeAhead(explicitUserRequest = true)
        cameraAssistSession.endSceneDescribe()
        val spoken = when (outcome) {
            is OutdoorSceneOutcome.Described -> outcome.spokenText
            OutdoorSceneOutcome.Timeout ->
                "La cámara tardó demasiado. Probá de nuevo."
            OutdoorSceneOutcome.CaptureFailed ->
                "No pude capturar la imagen. Probá de nuevo."
            OutdoorSceneOutcome.CameraPermissionMissing ->
                "No tengo permiso de cámara. Tenés que activarlo para Estela."
            is OutdoorSceneOutcome.Error ->
                if (outcome.code == "vision_backend_not_configured") {
                    "Todavía no tengo descripción visual remota activada, " +
                        "pero puedo leer texto con la cámara."
                } else {
                    "No pude describir la escena ahora. Puedo leer texto con " +
                        "la cámara si querés."
                }
        }
        speak(spoken, force = true)
    }

    // --- V1.10.3: lectura de pantalla en CUALQUIER app (fast path local) ---

    /**
     * "Leé la pantalla" / "qué aparece" / "qué puedo tocar" / "ayudame con
     * esta pantalla" sobre la app que esté adelante (Maps, DiDi, Ajustes,
     * cualquiera con nodos accesibles).
     *
     * Reglas duras:
     *  - corre DESPUÉS de outdoor ("dónde estoy" sigue siendo GPS) y del
     *    flujo contextual de WhatsApp (sus lectores de chats/mensajes son
     *    más ricos), y ANTES de companion/LLM/fallback;
     *  - SOLO describe: jamás clickea, jamás envía, jamás toca botones;
     *  - el snapshot nunca va a un LLM ni se persiste (contrato del use case);
     *  - si el resumen menciona opciones sensibles (pedir/confirmar/pagar),
     *    se agrega en voz que Estela no las va a tocar.
     */
    private fun handleGlobalScreenQuery(text: String): Boolean {
        val screenMode = ScreenQueryPhrases.classify(text)
        val nextStepKind =
            if (screenMode == null) NextStepQueryPhrases.classify(text) else null
        if (screenMode == null && nextStepKind == null) return false

        logBackground(
            "SCREEN_READ_PIPELINE_START mode=${screenMode?.name ?: nextStepKind?.name} " +
                "accessibilityReady=${backgroundAccessibilityReady()}"
        )

        if (!backgroundAccessibilityReady()) {
            logBackground("routing screenQuery=local accessibilityReady=false")
            speak(
                "No pude leer esta pantalla todavía. Verificá que la " +
                    "accesibilidad de Estela esté activa en Ajustes.",
                force = true
            )
            return true
        }

        if (nextStepKind != null) {
            val structured = runCatching {
                gasStructuredSnapshotBuilder.build(screenContextProvider.current())
            }.getOrNull()
            val advice = gasNextStepAdvisor.advise(structured, nextStepKind)
            logBackground(
                "routing screenQuery=local nextStep=$nextStepKind " +
                    "advice=${advice.javaClass.simpleName}"
            )
            speak(ScreenActionSafetyNote.appendIfSensitive(advice.spokenText), force = true)
            return true
        }

        return when (val result = screenUnderstandingUseCase.handle(text)) {
            ScreenUnderstandingResult.NotAScreenCommand -> false
            is ScreenUnderstandingResult.NeedsAccessibilityService -> {
                logBackground("routing screenQuery=local needsAccessibility=true")
                speak(result.spokenText, force = true)
                true
            }
            is ScreenUnderstandingResult.Spoken -> {
                logBackground(
                    "routing screenQuery=local mode=${result.mode} " +
                        "limited=${result.isLimited} safe=${result.isSafeToReadAloud}"
                )
                // App que expone pocos nodos accesibles: aviso honesto antes
                // del contenido parcial.
                val limitedPrefix = if (result.isLimited && result.isSafeToReadAloud) {
                    "Esta app me está dando poca información accesible. " +
                        "Puedo decirte lo que encuentre, pero puede faltar contenido. "
                } else {
                    ""
                }
                speak(
                    limitedPrefix + ScreenActionSafetyNote.appendIfSensitive(result.spokenText),
                    force = true
                )
                true
            }
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

    // ---- V2.2: inteligencia de pantalla cableada al runtime ----

    /**
     * Construye un [ScreenModel] desde el snapshot de accesibilidad actual.
     * Adapter puro AccessibilityNodeSummary → ReasonerNode. Nunca toca la UI.
     */
    private fun currentScreenModel(): ScreenModel {
        val pkg = OjoClaroAccessibilityService.readActivePackageName()
        val nodes = OjoClaroAccessibilityService.readVisibleNodeSummaries().map { s ->
            ReasonerNode(
                text = s.text,
                contentDescription = s.contentDescription,
                hint = s.hint,
                className = s.className,
                isClickable = s.isClickable,
                isEditable = s.isEditable,
                isPassword = s.isPassword,
                isHeading = s.isHeading,
            )
        }
        // Fuente ESTABLE de app activa: readActivePackageName puede venir vacío/del
        // overlay tras un cambio de app. Sumamos los marcadores de Instagram
        // (mismo source que el router de tareas IG) y el handoff de WhatsApp.
        val igState = OjoClaroAccessibilityService.instagramScreenCheck().state
        val inInstagramByMarkers = igState == InstagramScreenState.THREAD ||
            igState == InstagramScreenState.INBOX ||
            igState == InstagramScreenState.FEED_OR_HOME
        val externalIsWhatsApp = contextState.current.externalApp == ExternalAppName.WHATSAPP
        val resolution = ActiveAppResolver.resolve(
            rawPackage = pkg,
            externalIsWhatsApp = externalIsWhatsApp,
            inInstagramByMarkers = inInstagramByMarkers,
            nodes = nodes,
            ownPackage = packageName,
        )
        logBackground(
            "screenIntel activeApp raw=${pkg ?: "null"} externalWA=$externalIsWhatsApp " +
                "igMarkers=$inInstagramByMarkers resolved=${resolution.app} source=${resolution.source}"
        )
        return ScreenReasoner.reason(RawScreen(packageName = pkg, nodes = nodes), resolution.app)
    }

    // ---- Mobility Copilot v1: Uber (Level 2, solo leer/guiar/frenar) ----

    /** Modelo de pantalla de Uber (app de pasajero). Adapter puro, no toca UI. */
    private fun currentUberScreenModel(): UberScreenModel {
        val pkg = OjoClaroAccessibilityService.readActivePackageName()
        val nodes = OjoClaroAccessibilityService.readVisibleNodeSummaries().map { s ->
            ReasonerNode(
                text = s.text,
                contentDescription = s.contentDescription,
                hint = s.hint,
                className = s.className,
                isClickable = s.isClickable,
                isEditable = s.isEditable,
                isPassword = s.isPassword,
                isHeading = s.isHeading,
            )
        }
        return UberScreenReasoner.reason(RawScreen(packageName = pkg, nodes = nodes))
    }

    /**
     * Copiloto de Uber: LEE la pantalla y guía; NUNCA toca el botón final, ni
     * pagos, ni llamadas, ni cancela un viaje en curso. "confirmo pedir Uber
     * ahora" en v1 NO ejecuta el pedido. Solo reclama frases de lectura/freno;
     * "abrí Uber"/"pedime un Uber" siguen al flujo de apertura seguro existente.
     */
    private fun handleUberCopilotCommand(text: String): Boolean {
        val intent = UberCopilotPhrases.parse(text)
        if (intent != null) {
            val model = currentUberScreenModel()
            // Log SIN datos sensibles: jamás origen/destino/precio (solo flags
            // booleanos de presencia, para verificar extracción en smoke).
            logBackground(
                "uberCopilot intent=${intent::class.simpleName} app=${model.activeApp} " +
                    "screen=${model.screenType} conf=${model.confidence} risky=${model.riskyControls.size} " +
                    "hasPrice=${model.priceText != null} hasType=${model.rideTypeText != null} " +
                    "hasPickup=${model.pickupText != null} hasDest=${model.destinationText != null}"
            )
            val spoken = when (intent) {
                UberIntent.DescribeUber -> UberCopilotNarrator.describe(model)
                UberIntent.WhatRide -> UberCopilotNarrator.whatRide(model)
                UberIntent.AskPrice -> UberCopilotNarrator.price(model)
                UberIntent.AskOrigin -> UberCopilotNarrator.origin(model)
                UberIntent.AskDestination -> UberCopilotNarrator.destination(model)
                UberIntent.AskRideType -> UberCopilotNarrator.rideType(model)
                UberIntent.ConfirmRequest -> {
                    // Freno duro v1: ni siquiera con la frase fuerte se toca el botón.
                    logBackground("uberCopilot confirmRequest=blocked_v1 tapped=false")
                    UberCopilotNarrator.confirmRequestBlocked()
                }
                UberIntent.CancelUber -> UberCopilotNarrator.cancel(model)
            }
            speak(spoken, force = true)
            return true
        }

        // FASE 4: en Uber, "qué personas aparecen"/"qué chat estoy viendo" no
        // aplican; respondemos honesto sin inventar chats. Solo si el dispositivo
        // está realmente en la app de pasajero (no la de conductor, no otra app).
        val pkg = OjoClaroAccessibilityService.readActivePackageName()?.trim()?.lowercase()
        if (pkg == UberScreenReasoner.UBER_RIDER_PACKAGE) {
            val screenQuery = ScreenIntelligencePhrases.parse(text)
            if (screenQuery is ScreenIntent.WhoIsVisible || screenQuery is ScreenIntent.WhichChat) {
                logBackground("uberCopilot redirect=perception_in_uber")
                speak(UberCopilotNarrator.notAChatApp(), force = true)
                return true
            }
        }
        return false
    }

    /**
     * Percepción + navegación V2.2. Devuelve true si consumió la frase.
     *
     * Percepción es de solo lectura (habla y termina). Navegación abre un chat
     * SIN escribir ni enviar; si no puede verificar, explica o cae al flujo
     * viejo. Nunca envía un mensaje real.
     */
    private fun handleScreenIntelligenceCommand(text: String): Boolean {
        val intent = ScreenIntelligencePhrases.parse(text) ?: return false
        return when (intent) {
            ScreenIntent.WhoIsVisible -> {
                val model = currentScreenModel()
                logBackground(
                    "screenIntel intent=who_is_visible app=${model.activeApp} " +
                        "contacts=${model.visibleContacts.size} conf=${model.confidence}"
                )
                speak(ScreenIntelligenceNarrator.whoIsVisible(model), force = true)
                true
            }
            ScreenIntent.WhichChat -> {
                val model = currentScreenModel()
                logBackground(
                    "screenIntel intent=which_chat app=${model.activeApp} " +
                        "type=${model.screenType} conf=${model.confidence}"
                )
                speak(ScreenIntelligenceNarrator.whichChat(model), force = true)
                true
            }
            ScreenIntent.WhoAmISending -> {
                val recipient = pendingInstagramSend?.contactLabel
                    ?: pendingContactConfirmation?.displayName?.takeIf { it.isNotBlank() }
                    ?: pendingWhatsAppSendDraft?.let { "el chat de WhatsApp que tenés abierto" }
                logBackground("screenIntel intent=who_am_i_sending hasRecipient=${recipient != null}")
                speak(ScreenIntelligenceNarrator.whoAmISending(recipient), force = true)
                true
            }
            is ScreenIntent.OpenChat -> handleScreenIntelligenceOpenChat(intent)
        }
    }

    /**
     * "abrí el chat de X". SOLO WhatsApp en este sprint. No duplica el flujo
     * existente (con confirmación) cuando el handoff ya marca WhatsApp: en ese
     * caso cae a handleVisibleScreenFollowUp. Actúa en el hueco: cuando el
     * handoff NO marca WhatsApp pero el dispositivo sí está en WhatsApp, o para
     * el fallback por número de un alias autorizado.
     */
    private fun handleScreenIntelligenceOpenChat(intent: ScreenIntent.OpenChat): Boolean {
        // 1. No pisar ningún flujo de envío/confirmación en curso.
        if (contextState.current.pendingConfirmation != null ||
            pendingContactConfirmation != null ||
            pendingInstagramSend != null ||
            pendingWhatsAppSendDraft != null
        ) {
            return false
        }

        // 2. Instagram: no lo cableamos acá (handleInstagramTaskCommand ya posee
        //    sus frases). Dejamos pasar al routing viejo.
        if (intent.app == TargetApp.INSTAGRAM) {
            logBackground("screenIntel openChat skip=instagram")
            return false
        }

        // 3. Si el handoff ya marca WhatsApp, NO duplicar: el flujo existente
        //    (handleVisibleScreenFollowUp, con confirmación) ya lo cubre.
        if (contextState.current.externalApp == ExternalAppName.WHATSAPP) {
            logBackground("screenIntel openChat defer=existing_whatsapp_path")
            return false
        }

        val model = currentScreenModel()
        val onWhatsApp = model.activeApp == ActiveApp.WHATSAPP ||
            model.activeApp == ActiveApp.WHATSAPP_BUSINESS

        // 4. Resolver. Fuera de WhatsApp no usamos candidatos visibles (serían de
        //    otra app): solo alias autorizados con número.
        val resolution = ContactResolver.resolve(
            query = intent.rawName,
            app = TargetApp.WHATSAPP,
            candidates = if (onWhatsApp) model.visibleContacts else emptyList(),
            aliases = ScreenIntelligenceAliases.WHATSAPP,
        )

        return when (resolution) {
            is ContactResolution.Resolved -> openResolvedWhatsAppChat(resolution.match, onWhatsApp)
            is ContactResolution.Ambiguous -> {
                val names = resolution.candidates
                    .map { it.primaryText.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(3)
                logBackground("screenIntel openChat=ambiguous count=${names.size}")
                speak(
                    "Veo varios que coinciden: ${names.joinToString(", ")}. " +
                        "¿Cuál abro? Decime el nombre completo.",
                    force = true
                )
                true
            }
            is ContactResolution.NotFound -> {
                logBackground("screenIntel openChat=not_found onWhatsApp=$onWhatsApp")
                if (!onWhatsApp) {
                    speak(
                        "Para abrir un chat por nombre necesito que WhatsApp esté " +
                            "en pantalla. Abrí WhatsApp y volvé a pedirme.",
                        force = true
                    )
                } else {
                    speak(
                        "No encontré a ${intent.rawName} en los chats visibles. " +
                            "Probá deslizando la lista, o decime el nombre completo.",
                        force = true
                    )
                }
                true
            }
            is ContactResolution.Unsafe -> {
                logBackground("screenIntel openChat=unsafe")
                speak(
                    "No puedo abrir eso de forma segura. Decime solo el nombre del contacto.",
                    force = true
                )
                true
            }
        }
    }

    /**
     * Abre el chat resuelto. Visible → reutiliza el matcher existente (tap).
     * PHONE_FALLBACK (alias autorizado no visible) → wa.me: abre el chat SIN
     * texto y SIN enviar. Nunca escribe ni manda nada.
     */
    private fun openResolvedWhatsAppChat(match: ResolvedContact, onWhatsApp: Boolean): Boolean {
        return when (match.source) {
            ResolutionSource.EXACT_VISIBLE, ResolutionSource.PARTIAL_VISIBLE -> {
                when (val result = OjoClaroAccessibilityService.openVisibleWhatsAppChatByName(match.name)) {
                    is VisibleChatOpenResult.Opened -> {
                        logBackground("screenIntel openChat=opened_visible src=${match.source}")
                        speak("Abrí el chat de ${result.displayName}. No envié ningún mensaje.", force = true)
                    }
                    is VisibleChatOpenResult.Unsafe -> {
                        logBackground("screenIntel openChat=visible_unsafe reason=${result.reason}")
                        speak(
                            "Veo a ${result.displayName ?: match.name}, pero no puedo abrirlo de " +
                                "forma segura. Tocá dos veces sobre ese chat.",
                            force = true
                        )
                    }
                    else -> {
                        logBackground("screenIntel openChat=visible_tap_failed")
                        speak(
                            "Vi a ${match.name} pero no pude abrir el chat. " +
                                "Tocá sobre ese chat para abrirlo.",
                            force = true
                        )
                    }
                }
                true
            }
            ResolutionSource.PHONE_FALLBACK -> {
                val phone = match.phoneFallback
                if (phone.isNullOrBlank()) {
                    speak(
                        "No tengo un número guardado para ${match.name}. " +
                            "Abrí WhatsApp y buscalo en la lista.",
                        force = true
                    )
                    return true
                }
                logBackground("screenIntel openChat=phone_fallback nameLen=${match.name.length}")
                // wa.me: abre el chat con el campo vacío. No escribe ni envía.
                val spoken = when (val r = whatsAppIntentHelper.openChat(match.name, phone)) {
                    is CommandResult.Success -> r.spokenText
                    is CommandResult.Failed -> r.spokenText
                    is CommandResult.NeedsConfirmation -> r.spokenText
                    is CommandResult.NotSupported -> r.spokenText
                }
                speak(spoken, force = true)
                true
            }
            ResolutionSource.ALIAS -> {
                // Alias sin número y sin candidato visible: no hay forma segura de abrir.
                logBackground("screenIntel openChat=alias_no_phone onWhatsApp=$onWhatsApp")
                speak(
                    "Conozco a ${match.name}, pero no lo veo en la pantalla. " +
                        "Abrí WhatsApp y mostrame la lista de chats.",
                    force = true
                )
                true
            }
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

    // --- Outdoor Guidance v1: fast path local (nunca GPT) ---

    /**
     * @return true si el texto fue un comando outdoor y quedó despachado.
     *
     * V1.2: "dónde estoy" SIEMPRE es ubicación GPS (pedido explícito del
     * piloto rider). La lectura de pantalla sigue disponible con
     * "leé la pantalla" / "qué estoy viendo".
     *
     * "repetí" a secas solo es outdoor con ruta activa: sin ruta conserva su
     * significado histórico (repetir el último resultado por la ruta normal).
     */
    private suspend fun handleOutdoorCommand(text: String): Boolean {
        val command = OutdoorPhrases.parse(text) ?: return false

        if (command is OutdoorPhrases.Command.RepeatInstruction &&
            !OutdoorForegroundService.isGuidanceActive() &&
            VoiceCommandDispatcher.isRepeatCommand(text)
        ) {
            // "repetí" sin ruta activa = repetir la última respuesta (lo maneja
            // handleBasicConversationCommand, robusto a acentos). "próximo paso"
            // y similares NO son isRepeatCommand: siguen a la guía outdoor.
            return false
        }

        when (command) {
            is OutdoorPhrases.Command.SafetyQuery -> {
                // Nunca autorizar movimiento: respuesta complementaria fija,
                // hablada en este mismo turno (el cierre llega por tts_completed).
                logBackground("outdoor fast path command=SafetyQuery")
                speak(
                    com.ojoclaro.android.outdoor.OutdoorSafetyPolicy.COMPLEMENTARY_GUIDANCE_TEXT,
                    force = true
                )
                return true
            }
            is OutdoorPhrases.Command.TransportQuery -> {
                // V1.10.2 — asistido: ofrecer ABRIR la app con confirmación.
                // Estela jamás pide, confirma ni paga un viaje; abrir la app
                // y dejar el destino sugerido es lo máximo que hace.
                logBackground("outdoor fast path command=TransportQuery app=${command.app}")
                handleTransportAssist(command.app)
                return true
            }
            is OutdoorPhrases.Command.OpenMaps -> {
                // V1.10.2 — Maps con confirmación: solo abrir con el destino
                // cargado, nunca iniciar navegación automática.
                logBackground(
                    "outdoor fast path command=OpenMaps hasDestination=${command.destination != null}"
                )
                offerOpenMaps(
                    command.destination
                        ?: pendingOutdoorDestinationCandidate
                        ?: OutdoorMobilityFallbackHub.peek()?.destination
                )
                return true
            }
            is OutdoorPhrases.Command.WhereAmI -> {
                logBackground(
                    "VOICE_WHERE_AM_I_MATCHED guidanceActive=" +
                        "${OutdoorForegroundService.isGuidanceActive()}"
                )
                conversationMemory.noteContext("consultó su ubicación")
                if (OutdoorForegroundService.isGuidanceActive()) {
                    logBackground("VOICE_WHERE_AM_I_PIPELINE_START mode=guidance")
                    OutdoorForegroundService.whereAmI(this)
                } else {
                    // V1.10.1 — sin ruta activa, "dónde estoy" es conversación:
                    // calle/zona + "¿a dónde querés ir?". El turno NO se cierra:
                    // queda esperando el destino.
                    logBackground("VOICE_WHERE_AM_I_PIPELINE_START mode=conversational")
                    handleWhereAmIConversational()
                    return true
                }
            }
            is OutdoorPhrases.Command.HowFar ->
                OutdoorForegroundService.howFar(this)
            is OutdoorPhrases.Command.RepeatInstruction ->
                OutdoorForegroundService.repeatInstruction(this)
            is OutdoorPhrases.Command.Recalculate -> {
                conversationMemory.noteContext("pidió recalcular la ruta")
                OutdoorForegroundService.recalculate(this)
            }
            is OutdoorPhrases.Command.CancelNavigation -> {
                conversationMemory.noteContext("canceló la ruta a pie")
                OutdoorForegroundService.stopGuidance(this)
            }
            is OutdoorPhrases.Command.DescribeAhead -> {
                logBackground("VOICE_DESCRIBE_ENV_MATCHED")
                logBackground("VOICE_DESCRIBE_ENV_PIPELINE_START")
                OutdoorForegroundService.describeAhead(this)
            }
            is OutdoorPhrases.Command.NavigateTo -> {
                conversationMemory.noteContext("inició una ruta a pie")
                OutdoorForegroundService.startGuidance(this, command.destination)
            }
        }
        logBackground("outdoor fast path command=${command.javaClass.simpleName}")
        if (accessibilityOverlayVoiceSingleShot) {
            completeOverlayVoiceTurn("outdoor_dispatch")
        }
        return true
    }

    // --- V1.10.1: "dónde estoy" conversacional + destino con confirmación ---

    /**
     * Lee ubicación con el lector propio, dice calle/zona y pregunta a dónde
     * ir. Corre en el turno de GAS (no en el OutdoorForegroundService) porque
     * acá vive el micrófono: la pregunta queda escuchando la respuesta.
     */
    private suspend fun handleWhereAmIConversational() {
        logBackground("whereAmI conversational=true")
        val result = outdoorLocationReader.read()
        val fix = (result as? OutdoorFixResult.Valid)?.fix
        if (fix == null) {
            // Error honesto del lector (sin permiso/sin señal/imprecisa):
            // siempre habla, nunca silencio, y no deja pending colgado.
            speak(outdoorLocationReader.spokenLocationText(result), force = true)
            return
        }
        val label = runCatching {
            gasOutdoorRouteProvider.reverseLabel(fix.latitude, fix.longitude)
        }.getOrNull()
        logBackground("whereAmI reverseLabelPresent=${label != null}")
        val base = if (!label.isNullOrBlank()) {
            val precision = fix.accuracyMeters?.toInt()
                ?.let { " La precisión es de unos $it metros." }
                .orEmpty()
            "Estás cerca de $label.$precision"
        } else {
            outdoorLocationReader.spokenLocationText(result) +
                " No pude obtener la calle ahora."
        }
        pendingOutdoorDestinationAsk = true
        speak("$base ¿A dónde querés ir? Decime el lugar, o decí: nada.", force = true)
    }

    /** @return true si el texto era la respuesta al "¿a dónde querés ir?". */
    private fun handlePendingOutdoorDestinationReply(text: String): Boolean {
        val candidate = pendingOutdoorDestinationCandidate
        if (!pendingOutdoorDestinationAsk && candidate == null) return false

        if (com.ojoclaro.android.outdoor.OutdoorDestinationReply.isDecline(text)) {
            clearOutdoorDestinationAsk()
            logBackground("destinationAsk outcome=declined")
            speak(
                "Listo. Si después querés una ruta, decí: llevame a, y el destino.",
                force = true
            )
            return true
        }

        if (candidate != null) {
            if (com.ojoclaro.android.outdoor.OutdoorDestinationReply.isYes(text)) {
                clearOutdoorDestinationAsk()
                conversationMemory.noteContext("inició una ruta a pie")
                logBackground("destinationAsk outcome=confirmed")
                OutdoorForegroundService.startGuidance(this, candidate)
                if (accessibilityOverlayVoiceSingleShot) {
                    completeOverlayVoiceTurn("outdoor_dispatch")
                }
                return true
            }
            val replacement =
                com.ojoclaro.android.outdoor.OutdoorDestinationReply.extractDestination(text)
            if (replacement != null) {
                pendingOutdoorDestinationCandidate = replacement
                logBackground("destinationAsk outcome=replaced len=${replacement.length}")
                speak("¿Querés que te oriente hacia $replacement? Decí: sí, o no.", force = true)
                return true
            }
            speak("¿Empiezo la ruta hacia $candidate? Decí: sí, o no.", force = true)
            return true
        }

        // "allá" / "cerca" / "ahí": lugar vago, pedir aclaración sin inventar.
        if (com.ojoclaro.android.outdoor.OutdoorDestinationReply.isVaguePlace(text)) {
            logBackground("destinationAsk outcome=vague_place")
            speak(
                "No me alcanza con eso. Decime el nombre del lugar, " +
                    "como: la plaza, o una dirección. O decí: nada.",
                force = true
            )
            return true
        }

        val destination =
            com.ojoclaro.android.outdoor.OutdoorDestinationReply.extractDestination(text)
        if (destination == null) {
            // No suena a lugar: soltar el turno para que la frase siga su
            // ruta normal ("leé la pantalla" sigue leyendo la pantalla).
            clearOutdoorDestinationAsk()
            logBackground("destinationAsk outcome=not_a_destination")
            return false
        }
        pendingOutdoorDestinationAsk = false
        pendingOutdoorDestinationCandidate = destination
        logBackground("destinationAsk outcome=candidate len=${destination.length}")
        speak("¿Querés que te oriente hacia $destination? Decí: sí, o no.", force = true)
        return true
    }

    private fun clearOutdoorDestinationAsk() {
        pendingOutdoorDestinationAsk = false
        pendingOutdoorDestinationCandidate = null
    }

    // --- V1.10.2: movilidad asistida (Uber/Cabify/Maps) sin acciones reales ---

    /**
     * "Pedime un Uber" ya no termina en "pedilo vos": ofrece abrir la app con
     * confirmación. Límites duros: Estela NO pide el viaje, NO lo confirma,
     * NO paga y NO toca tarjetas; solo abre la app y, si hay un destino
     * hablado, lo deja sugerido para que la persona lo revise.
     */
    private fun handleTransportAssist(app: OutdoorPhrases.TransportApp) {
        val resolver = AndroidInstalledAppResolver(this)
        val mapsInstalled =
            resolver.isPackageInstalled(AppCapabilityRegistry.GOOGLE_MAPS_PACKAGE)
        val rememberedDestination = pendingOutdoorDestinationCandidate
            ?: OutdoorMobilityFallbackHub.peek()?.destination

        if (app == OutdoorPhrases.TransportApp.TAXI_REMIS) {
            // Sin integración real de remís/taxi: jamás inventarla.
            val mapsNote =
                if (mapsInstalled) " También puedo abrir Google Maps: decí, abrí maps." else ""
            speak(
                "No puedo pedir un remís ni un taxi por mí misma. Si tenés un " +
                    "contacto de confianza guardado, puedo ayudarte a llamarlo: " +
                    "decí, llamá a, y el nombre.$mapsNote",
                force = true
            )
            return
        }

        val requestedPackage = transportPackage(app)
        val registry = AppCapabilityRegistry()
        val requested = requestedPackage?.let(registry::findByPackageName)
            ?.takeIf { resolver.isPackageInstalled(it.packageName) }
        val capability = requested ?: registry.firstInstalledRideApp(resolver)

        when {
            capability != null -> {
                pendingMobilityOpen = PendingMobilityOpen(
                    appLabel = capability.appName,
                    packageName = capability.packageName,
                    destination = rememberedDestination,
                    isRideApp = true
                )
                val prefix = if (capability.packageName != requestedPackage) {
                    "No encuentro esa app, pero tenés ${capability.appName}. "
                } else {
                    ""
                }
                speak(
                    prefix + "Puedo ayudarte a abrir ${capability.appName} y dejarte " +
                        "el destino preparado, pero no voy a pedir ni confirmar " +
                        "el viaje por vos, y no toco pagos. ¿Querés que abra " +
                        "${capability.appName}? Decí: sí, o no.",
                    force = true
                )
            }
            mapsInstalled -> {
                pendingMobilityOpen = PendingMobilityOpen(
                    appLabel = "Google Maps",
                    packageName = AppCapabilityRegistry.GOOGLE_MAPS_PACKAGE,
                    destination = rememberedDestination,
                    isRideApp = false
                )
                speak(
                    "No encuentro Uber ni otra app de viajes instalada. Puedo " +
                        "abrir Google Maps para que veas opciones, o podés pedir " +
                        "ayuda a alguien de confianza. ¿Querés que abra Maps? " +
                        "Decí: sí, o no.",
                    force = true
                )
            }
            else -> {
                speak(
                    "No encuentro Uber ni Google Maps instaladas. Para un viaje, " +
                        "pedí ayuda a alguien de confianza. Yo puedo orientarte " +
                        "a pie o decirte dónde estás.",
                    force = true
                )
            }
        }
    }

    private fun transportPackage(app: OutdoorPhrases.TransportApp): String? = when (app) {
        OutdoorPhrases.TransportApp.UBER -> AppCapabilityRegistry.UBER_PACKAGE
        OutdoorPhrases.TransportApp.CABIFY -> AppCapabilityRegistry.CABIFY_PACKAGE
        OutdoorPhrases.TransportApp.DIDI -> AppCapabilityRegistry.DIDI_PACKAGE
        OutdoorPhrases.TransportApp.TAXI_REMIS -> null
    }

    /** Pregunta de confirmación para abrir Google Maps (nunca abre sin sí). */
    private fun offerOpenMaps(destination: String?) {
        pendingMobilityOpen = PendingMobilityOpen(
            appLabel = "Google Maps",
            packageName = AppCapabilityRegistry.GOOGLE_MAPS_PACKAGE,
            destination = destination,
            isRideApp = false
        )
        val where = destination?.let { " con $it" }.orEmpty()
        speak(
            "Puedo abrir Google Maps$where. Solo lo abro: no toco nada más " +
                "ahí. ¿Querés que lo abra? Decí: sí, o no.",
            force = true
        )
    }

    /**
     * @return true si el texto era la respuesta a una oferta de movilidad.
     *
     * Reglas: "no" siempre cancela. "sí" SOLO abre una app o reintenta una
     * ruta (acciones sin envío y sin costo). Una frase que no es respuesta
     * explícita sigue su ruta normal: nada se secuestra.
     */
    private fun handlePendingMobilityReply(text: String): Boolean {
        val local = pendingMobilityOpen
        if (local != null) {
            if (OutdoorDestinationReply.isDecline(text)) {
                pendingMobilityOpen = null
                logBackground("mobilityOffer outcome=declined")
                speak("Listo, no abro nada.", force = true)
                return true
            }
            val parsed = OutdoorPhrases.parse(text)
            if (parsed is OutdoorPhrases.Command.TransportQuery) {
                // Repitió el pedido: misma app abre; otra app re-ofrece.
                pendingMobilityOpen = null
                if (local.isRideApp && transportPackage(parsed.app) == local.packageName) {
                    OutdoorMobilityFallbackHub.clear()
                    openRideAppAssisted(local)
                } else {
                    handleTransportAssist(parsed.app)
                }
                return true
            }
            if (parsed is OutdoorPhrases.Command.OpenMaps) {
                pendingMobilityOpen = null
                if (!local.isRideApp) {
                    OutdoorMobilityFallbackHub.clear()
                    openMapsAssisted(parsed.destination ?: local.destination)
                } else {
                    offerOpenMaps(parsed.destination ?: local.destination)
                }
                return true
            }
            if (OutdoorDestinationReply.isYes(text)) {
                pendingMobilityOpen = null
                OutdoorMobilityFallbackHub.clear()
                logBackground("mobilityOffer outcome=confirmed ride=${local.isRideApp}")
                if (local.isRideApp) openRideAppAssisted(local) else openMapsAssisted(local.destination)
                return true
            }
            speak("¿Abro ${local.appLabel}? Decí: sí, o no.", force = true)
            return true
        }
        return handleHubMobilityReply(text)
    }

    /**
     * Ofertas que dejó el coordinator de rutas (falla de servicio, GPS
     * impreciso, geocode sin resultado). Solo se consumen ante una respuesta
     * explícita; cualquier otra frase sigue su ruta normal y la oferta vence
     * sola a los 3 minutos.
     */
    private fun handleHubMobilityReply(text: String): Boolean {
        val offer = OutdoorMobilityFallbackHub.peek() ?: return false
        val isYes = OutdoorDestinationReply.isYes(text)
        val isNo = OutdoorDestinationReply.isDecline(text)

        when (offer.kind) {
            MobilityFallbackKind.ROUTE_RETRY_OR_MAPS,
            MobilityFallbackKind.ROUTE_TOO_FAR_MAPS -> {
                if (isNo) {
                    OutdoorMobilityFallbackHub.clear()
                    logBackground("mobilityFallback kind=${offer.kind} outcome=declined")
                    speak("Listo. Si querés, después volvemos a intentar.", force = true)
                    return true
                }
                if (isYes || isMapsAffirmative(text)) {
                    OutdoorMobilityFallbackHub.clear()
                    logBackground("mobilityFallback kind=${offer.kind} outcome=maps")
                    openMapsAssisted(offer.destination)
                    return true
                }
                if (offer.kind == MobilityFallbackKind.ROUTE_RETRY_OR_MAPS &&
                    isRetryPhrase(text)
                ) {
                    OutdoorMobilityFallbackHub.clear()
                    logBackground("mobilityFallback kind=${offer.kind} outcome=retry")
                    val destination = offer.destination
                    if (destination != null) {
                        conversationMemory.noteContext("reintentó una ruta a pie")
                        OutdoorForegroundService.startGuidance(this, destination)
                        if (accessibilityOverlayVoiceSingleShot) {
                            completeOverlayVoiceTurn("outdoor_dispatch")
                        }
                    } else {
                        speak("Decime el destino de nuevo: llevame a, y el lugar.", force = true)
                    }
                    return true
                }
                return false
            }
            MobilityFallbackKind.ROUTE_RETRY_IMPRECISE -> {
                if (isNo || isWaitPhrase(text)) {
                    OutdoorMobilityFallbackHub.clear()
                    logBackground("mobilityFallback kind=imprecise outcome=wait")
                    speak(
                        "Dale. Cuando estés en un lugar más abierto, pedime la ruta de nuevo.",
                        force = true
                    )
                    return true
                }
                if (isYes || isTryAnywayPhrase(text)) {
                    OutdoorMobilityFallbackHub.clear()
                    logBackground("mobilityFallback kind=imprecise outcome=try_anyway")
                    val destination = offer.destination
                    if (destination != null) {
                        OutdoorForegroundService.startGuidance(this, destination, allowImprecise = true)
                        if (accessibilityOverlayVoiceSingleShot) {
                            completeOverlayVoiceTurn("outdoor_dispatch")
                        }
                    } else {
                        speak("Decime el destino de nuevo: llevame a, y el lugar.", force = true)
                    }
                    return true
                }
                return false
            }
            MobilityFallbackKind.ADDRESS_RETRY -> {
                if (isNo) {
                    OutdoorMobilityFallbackHub.clear()
                    logBackground("mobilityFallback kind=address outcome=declined")
                    speak(
                        "Listo. Si después querés una ruta, decí: llevame a, y el destino.",
                        force = true
                    )
                    return true
                }
                val destination = OutdoorDestinationReply.extractDestination(text) ?: return false
                OutdoorMobilityFallbackHub.clear()
                logBackground("mobilityFallback kind=address outcome=candidate len=${destination.length}")
                pendingOutdoorDestinationCandidate = destination
                speak("¿Querés que te oriente hacia $destination? Decí: sí, o no.", force = true)
                return true
            }
        }
    }

    private fun foldMobilityReply(text: String): String =
        VoicePhraseNormalizer.normalizeForParser(text)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isMapsAffirmative(text: String): Boolean =
        OutdoorPhrases.parse(text) is OutdoorPhrases.Command.OpenMaps ||
            foldMobilityReply(text) in setOf(
                "maps", "si maps", "el maps", "google maps", "mejor maps", "el mapa"
            )

    private fun isRetryPhrase(text: String): Boolean =
        foldMobilityReply(text) in setOf(
            "proba de nuevo", "proba otra vez", "probar de nuevo",
            "intenta de nuevo", "intentalo de nuevo", "intenta otra vez",
            "reintenta", "reintentar", "reintentalo", "de nuevo", "otra vez"
        )

    private fun isTryAnywayPhrase(text: String): Boolean =
        foldMobilityReply(text) in setOf(
            "intenta igual", "intentalo igual", "proba igual", "igual",
            "dale igual", "si intenta igual", "si igual"
        )

    private fun isWaitPhrase(text: String): Boolean =
        foldMobilityReply(text) in setOf(
            "espero", "mejor espero", "esperar", "espera",
            "prefiero esperar", "mejor esperar", "espero mejor senal"
        )

    /**
     * Abre la app de viajes YA confirmada. Con destino hablado intenta el
     * deep link de Uber que SOLO precarga el destino (la persona revisa,
     * pide y confirma el viaje en la app); si no, apertura normal.
     * Jamás toca pedir/confirmar/pagar.
     */
    private fun openRideAppAssisted(pending: PendingMobilityOpen) {
        val resolver = AndroidInstalledAppResolver(this)
        if (!resolver.isPackageInstalled(pending.packageName)) {
            speak(
                "No encuentro ${pending.appLabel} instalada. Puedo abrir " +
                    "Google Maps si querés: decí, abrí maps.",
                force = true
            )
            return
        }
        var opened = false
        var prefillTried = false
        val query = pending.destination
            ?.let { OutdoorDestinationNormalizer.normalizeQuery(it) }
            ?.takeIf { it.isNotBlank() }
        if (pending.packageName == AppCapabilityRegistry.UBER_PACKAGE && query != null) {
            prefillTried = true
            val deepLink = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "uber://?action=setPickup&pickup=my_location" +
                        "&dropoff[formatted_address]=${Uri.encode(query)}"
                )
            ).setPackage(AppCapabilityRegistry.UBER_PACKAGE)
            opened = OjoClaroAccessibilityService.launchIntentFromService(Intent(deepLink)) ||
                runCatching {
                    deepLink.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (deepLink.resolveActivity(packageManager) != null) {
                        startActivity(deepLink)
                        true
                    } else {
                        false
                    }
                }.getOrDefault(false)
        }
        if (!opened) {
            // Camino resistente (lección V1.10.1 con WhatsApp): launcher
            // intent del sistema desde el contexto de accesibilidad.
            // Falla física real en Moto 2026-06-12: el splash de DiDi no
            // declara category.DEFAULT, así que resolveActivity (que usa
            // MATCH_DEFAULT_ONLY) da null y el lanzamiento clásico aborta.
            val launchIntent = runCatching {
                packageManager.getLaunchIntentForPackage(pending.packageName)
            }.getOrNull()
            if (launchIntent != null) {
                opened = OjoClaroAccessibilityService.launchIntentFromService(Intent(launchIntent)) ||
                    runCatching {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        true
                    }.getOrDefault(false)
            }
        }
        if (!opened) {
            val capability = AppCapabilityRegistry().findByPackageName(pending.packageName)
            opened = capability != null &&
                SafeAppLauncher(resolver, AndroidSafeAppStarter(this))
                    .launch(capability, userConfirmed = true) is SafeAppLaunchResult.Launched
        }
        logBackground(
            "mobilityOpen app=ride launched=$opened prefillTried=$prefillTried"
        )
        if (!opened) {
            speak(
                "No pude abrir ${pending.appLabel}. Probá abrirla a mano, o " +
                    "pedí ayuda a alguien de confianza.",
                force = true
            )
            return
        }
        val destinationNote = if (query != null) {
            "Te dejé el destino sugerido: revisalo vos antes de pedir. "
        } else {
            "Cargá vos el destino. "
        }
        speak(
            "Listo, abrí ${pending.appLabel}. $destinationNote" +
                "Yo no voy a pedir ni confirmar el viaje, ni tocar pagos. " +
                "Si necesitás saber qué aparece, decime: leé la pantalla.",
            force = true
        )
    }

    /**
     * Abre Google Maps en modo BÚSQUEDA con el destino cargado (geo:?q=),
     * nunca con el esquema de navegación que auto-inicia la guía. Sin Maps
     * instalada cae al navegador; sin nada, respuesta honesta.
     */
    private fun openMapsAssisted(destinationSpoken: String?) {
        val resolver = AndroidInstalledAppResolver(this)
        val query = destinationSpoken
            ?.let { OutdoorDestinationNormalizer.normalizeQuery(it) }
            ?.takeIf { it.isNotBlank() }
        val mapsInstalled =
            resolver.isPackageInstalled(AppCapabilityRegistry.GOOGLE_MAPS_PACKAGE)
        val intent = if (mapsInstalled) {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(if (query != null) "geo:0,0?q=${Uri.encode(query)}" else "geo:0,0")
            ).setPackage(AppCapabilityRegistry.GOOGLE_MAPS_PACKAGE)
        } else {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    if (query != null) {
                        "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}"
                    } else {
                        "https://maps.google.com"
                    }
                )
            )
        }
        val launched = OjoClaroAccessibilityService.launchIntentFromService(Intent(intent)) ||
            runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            }.getOrDefault(false)
        logBackground(
            "mobilityOpen app=maps installed=$mapsInstalled launched=$launched " +
                "hasDestination=${query != null}"
        )
        if (!launched) {
            speak(
                "No pude abrir un mapa en este teléfono. Pedí ayuda a alguien " +
                    "de confianza si lo necesitás ahora.",
                force = true
            )
            return
        }
        val whereText = destinationSpoken?.let { " con $it" }.orEmpty()
        val browserNote = if (!mapsInstalled) {
            " No encontré la app de Maps, así que lo abrí en el navegador."
        } else {
            ""
        }
        speak(
            "Listo, abrí Google Maps$whereText.$browserNote No voy a tocar " +
                "nada ahí. Si necesitás saber qué aparece, decime: leé la " +
                "pantalla. Recordá: puedo orientarte, pero no puedo garantizar " +
                "seguridad al cruzar; usá tu bastón o tu método habitual.",
            force = true
        )
    }

    // --- V1.2: envío seguro de WhatsApp + audios (nunca GPT) ---

    /**
     * @return true si el texto era la respuesta a un envío pendiente.
     *
     * Reglas: "no"/"cancelar" SIEMPRE cancelan; solo "enviá"/"mandalo"/
     * "confirmo" envían; "sí"/"dale" a secas NUNCA envían. El toque físico
     * de enviar re-verifica que el campo siga diciendo exactamente lo que
     * la persona escuchó.
     */
    /**
     * WA-5 — "respondé/contestale/decile/escribí/mandale [texto]" en el chat
     * ABIERTO. Escribe el borrador (ACTION_SET_TEXT) y entra al flujo de DOBLE
     * confirmación. Nunca envía acá. Si no hay chat abierto, lo dice. Logs
     * WHATSAPP_REPLY_* sanitizados (solo longitudes).
     */
    private fun handleWhatsAppReplyCommand(text: String): Boolean {
        val message = WhatsAppReplyPhrases.extractReply(text)
        if (message == null) {
            // Fluency: "respondé ehh" (solo muletillas) ES un intento de respuesta;
            // pedimos la frase en vez de ignorar el turno o preparar basura.
            if (WhatsAppReplyPhrases.isReplyAttempt(text)) {
                logBackground("ROUTING_AUDIT handler=whatsapp_reply_dry_run")
                logBackground("WHATSAPP_REPLY_INCOMPLETE reason=filler_or_empty")
                speak("¿Qué querés que responda? Todavía no preparé nada.", force = true)
                return true
            }
            return false
        }
        logBackground("ROUTING_AUDIT handler=whatsapp_reply_dry_run")
        logBackground("WHATSAPP_REPLY_REQUESTED dryRun=$whatsAppReplyDryRun")
        logBackground("WHATSAPP_REPLY_TEXT_EXTRACTED len=${message.length}")

        if (!backgroundAccessibilityReady()) {
            logBackground("WHATSAPP_REPLY_FAILED_NOT_IN_CHAT reason=accessibility_off")
            // Capability-aware: explica el límite y qué SÍ puede (notificaciones)
            // o cómo activar, sin dejar al usuario perdido.
            speak(
                WhatsAppCapabilityNarrator.accessibilityGatedRead(buildWhatsAppCapabilityInputs()),
                force = true
            )
            return true
        }
        val state = runCatching {
            whatsAppScreenDetector.detect(screenContextProvider.current())
        }.getOrNull()
        if (state?.isOpen != true || !state.isInChat) {
            logBackground("WHATSAPP_REPLY_FAILED_NOT_IN_CHAT inChat=${state?.isInChat == true}")
            speak("Primero necesito que abras un chat de WhatsApp.", force = true)
            return true
        }
        if (WhatsAppVoiceSendPhrases.looksSensitive(message)) {
            logBackground("WHATSAPP_REPLY blocked=sensitive_content len=${message.length}")
            speak(
                "Ese mensaje parece tener datos sensibles, así que no lo preparo. " +
                    "Escribilo a mano si estás seguro.",
                force = true
            )
            return true
        }
        // Blind Safety (Fix A/reply): el destino de un reply es el CHAT ABIERTO,
        // pero una persona no vidente no ve cuál es. Leemos la identidad de la
        // cabecera para ANUNCIARLA; si no podemos identificar el chat, NO escribimos
        // (preferimos bloquear antes que tipear en un chat dudoso).
        val openChatTitle = runCatching {
            OjoClaroAccessibilityService.readVisibleWhatsAppChatTitle()
        }.getOrNull()
        if (openChatTitle.isNullOrBlank()) {
            logBackground("WHATSAPP_REPLY blocked=could_not_confirm_destination")
            speak("No pude confirmar el chat correcto. No escribí nada.", force = true)
            return true
        }
        draftWhatsAppMessageAndConfirm(message, destinationLabel = describeOpenChatDestination(openChatTitle))
        return true
    }

    /**
     * Blind Safety — describe el destino del chat abierto SIN leer el número
     * completo: el nombre si es un contacto agendado, o "terminado en NNNN" si la
     * cabecera muestra un número.
     */
    private fun describeOpenChatDestination(title: String): String {
        val digits = title.filter(Char::isDigit)
        return if (digits.length >= 7) "el chat terminado en ${digits.takeLast(4)}"
        else "el chat de ${title.take(40)}"
    }

    /**
     * Escribe [message] como BORRADOR (ACTION_SET_TEXT) en el chat ABIERTO,
     * lo lee de vuelta y entra al flujo de DOBLE confirmación (WA-5). Asume que
     * ya se verificó que estamos en el chat correcto (el caller lo garantiza:
     * reply en chat abierto, o compose por relación con destino VERIFIED).
     * NUNCA envía: el envío real exige el call-site único bajo confirmación
     * fuerte y, hoy, el flag está apagado. Logs solo con longitudes.
     */
    private fun draftWhatsAppMessageAndConfirm(message: String, destinationLabel: String? = null) {
        val set = runCatching { OjoClaroAccessibilityService.setWhatsAppDraft(message) }
            .getOrElse {
                // Blind Safety (Fix F): un throw del set no deja mudo al usuario.
                logBackground("WHATSAPP_REPLY drafted=false reason=exception")
                speak("No pude escribir el mensaje en el chat. Probá de nuevo.", force = true)
                return
            }
        when (set) {
            WhatsAppDraftSetResult.SetOk -> {
                logBackground("WHATSAPP_REPLY_INPUT_FOUND")
                logBackground("WHATSAPP_REPLY_DRAFTED len=${message.length}")
                val readBack = when (
                    val r = runCatching { OjoClaroAccessibilityService.readWhatsAppDraft() }.getOrNull()
                ) {
                    is WhatsAppDraftReadResult.Draft -> r.text
                    else -> message
                }
                logBackground("WHATSAPP_REPLY_DRAFT_READBACK len=${readBack.length}")
                pendingWhatsAppReply = PendingWhatsAppReply(readBack, awaitingStep = 1)
                logBackground("WHATSAPP_SEND_CONFIRMATION_REQUIRED step=1")
                EstelaEarcons.pendingSensitive()
                speak(
                    "Preparé para ${destinationLabel ?: "el chat abierto"}: " +
                        "${readBack.take(220)}. ¿Querés enviarlo?",
                    force = true
                )
            }
            WhatsAppDraftSetResult.NotInWhatsApp, WhatsAppDraftSetResult.NoEntryField -> {
                logBackground("WHATSAPP_REPLY_FAILED_NOT_IN_CHAT reason=${set.javaClass.simpleName}")
                speak("Primero necesito que abras un chat de WhatsApp.", force = true)
            }
            WhatsAppDraftSetResult.SetFailed, WhatsAppDraftSetResult.MismatchAfterSet -> {
                logBackground("WHATSAPP_REPLY drafted=false reason=${set.javaClass.simpleName}")
                speak("No pude escribir el mensaje en el chat. Probá de nuevo.", force = true)
            }
            WhatsAppDraftSetResult.ServiceUnavailable -> {
                logBackground("WHATSAPP_REPLY_FAILED_NOT_IN_CHAT reason=service_unavailable")
                speak("Necesito el servicio de accesibilidad activo para esto.", force = true)
            }
        }
    }

    /**
     * WA-5 — confirmación de la respuesta. Paso 1: "¿querés enviarlo?" (un sí
     * avanza, no envía). Paso 2: "¿lo mando ahora?" (confirmación FUERTE). En
     * DRY-RUN nunca envía. Cancelar en cualquier paso borra el borrador propio
     * y no envía. "repetí" re-dice el prompt sin tocar WhatsApp.
     */
    private fun handlePendingWhatsAppReplyConfirmation(text: String): Boolean {
        val pending = pendingWhatsAppReply ?: return false

        when (
            WhatsAppReplyConfirmationResolver.resolve(
                awaitingStep = pending.awaitingStep,
                rawText = text,
                dryRun = whatsAppReplyDryRun
            )
        ) {
            WhatsAppReplyConfirmationResolver.Outcome.REPEAT_PROMPT -> {
                logBackground("WHATSAPP_REPEAT_LAST_RESPONSE source=pending_reply step=${pending.awaitingStep}")
                speak(replyConfirmationPrompt(pending.awaitingStep, pending.text), force = true)
            }
            WhatsAppReplyConfirmationResolver.Outcome.CANCELLED -> {
                pendingWhatsAppReply = null
                // Borra el borrador que Estela misma escribió (seguro: lo pusimos).
                runCatching { OjoClaroAccessibilityService.setWhatsAppDraft("") }
                logBackground("WHATSAPP_EMERGENCY_CANCEL source=pending_reply step=${pending.awaitingStep}")
                logBackground("WHATSAPP_SEND_CANCELLED")
                logBackground("WHATSAPP_NOT_SENT_REASSURANCE")
                speak(cancelledReassurance(), force = true)
            }
            WhatsAppReplyConfirmationResolver.Outcome.ADVANCED_TO_SEND -> {
                pendingWhatsAppReply = pending.copy(awaitingStep = 2)
                logBackground("WHATSAPP_SEND_CONFIRMATION_1_OK")
                logBackground("WHATSAPP_SEND_CONFIRMATION_2_REQUIRED step=2")
                speak(
                    "Confirmo envío a este chat. ¿Lo mando ahora? Decí: mandalo, o cancelar.",
                    force = true
                )
            }
            WhatsAppReplyConfirmationResolver.Outcome.REPROMPT_STEP_1 -> {
                logBackground("WHATSAPP_SEND_BLOCKED_NO_CONFIRMATION step=1")
                speak("Tengo el mensaje preparado, sin enviar. Decí: sí para seguir, o cancelar.", force = true)
            }
            WhatsAppReplyConfirmationResolver.Outcome.WEAK_CONFIRMATION_BLOCKED -> {
                // "sí"/"dale"/"ajá" a secas en el paso fuerte: jamás envía.
                logBackground("WHATSAPP_AMBIGUOUS_CONFIRMATION_BLOCKED step=2")
                logBackground("WHATSAPP_NOT_SENT_REASSURANCE")
                speak(
                    "Todavía no envié nada. Para enviar decí: mandalo. Para cancelar decí: cancelar.",
                    force = true
                )
            }
            WhatsAppReplyConfirmationResolver.Outcome.BLOCKED_DRY_RUN -> {
                pendingWhatsAppReply = null
                logBackground("WHATSAPP_SEND_CONFIRMATION_2_OK dryRun=true")
                logBackground("WHATSAPP_SEND_DRY_RUN_BLOCKED")
                speak("Modo prueba: no envié ningún mensaje.", force = true)
            }
            WhatsAppReplyConfirmationResolver.Outcome.SEND_REAL_NOT_ENABLED -> {
                // El envío real (tocar enviar) se habilita en un cambio futuro
                // DELIBERADO, reusando el ÚNICO call-site de envío
                // (handlePendingWhatsAppSendReply). Acá nunca se toca enviar:
                // así el contrato de "un solo tap" se mantiene.
                pendingWhatsAppReply = null
                logBackground("WHATSAPP_SEND_REAL_NOT_ENABLED")
                speak("El envío real todavía no está habilitado en este modo.", force = true)
            }
            WhatsAppReplyConfirmationResolver.Outcome.REPROMPT_STEP_2 -> {
                logBackground("WHATSAPP_SEND_BLOCKED_NO_CONFIRMATION step=2")
                speak("Para enviar de verdad decí: mandalo. Para cancelar decí: cancelar.", force = true)
            }
        }
        return true
    }

    /** Prompt de confirmación re-dicho por "repetí" (más corto en modo seguro). */
    private fun replyConfirmationPrompt(step: Int, draft: String): String =
        if (step <= 1) {
            if (whatsAppSafeMode) {
                "Tenés preparado: ${draft.take(160)}. ¿Querés enviarlo? Decí: sí, o cancelar."
            } else {
                "Preparé: ${draft.take(220)}. ¿Querés enviarlo?"
            }
        } else {
            if (whatsAppSafeMode) {
                "¿Lo mando ahora? Decí: mandalo, o cancelar."
            } else {
                "Confirmo envío a este chat. ¿Lo mando ahora?"
            }
        }

    /** Tranquilidad tras cancelar; en modo seguro re-dice dónde quedó la persona. */
    private fun cancelledReassurance(): String {
        val base = "Cancelado. No envié nada."
        if (!whatsAppSafeMode) return base
        // pendingWhatsAppReply ya es null acá: la orientación no menciona pending.
        return "$base ${WhatsAppStateNarrator.orientation(buildAnxietySnapshot(namesWhatsApp = false))}"
    }

    private fun handlePendingWhatsAppSendReply(text: String): Boolean {
        val draft = pendingWhatsAppSendDraft ?: return false
        when {
            WhatsAppVoiceSendPhrases.isCancelSend(text) -> {
                // #13: limpiar el borrador propio ANTES de soltar el pending, así no
                // queda texto tipeado sin enviar. Siempre con aviso (cancelledReassurance).
                clearOwnWhatsAppDraftIfPending()
                pendingWhatsAppSendDraft = null
                invalidateInFlightWhatsAppCompose()
                logBackground("whatsappSend outcome=cancelled_by_user draftCleared=true")
                // Blind Safety (Fix B): copy canónico unificado con WA-5.
                speak(cancelledReassurance(), force = true)
            }
            WhatsAppVoiceSendPhrases.isConfirmSend(text) -> {
                pendingWhatsAppSendDraft = null
                if (!whatsAppFlags.realSendEnabled) {
                    // Full Control Hardening: el envío real está DESACTIVADO por
                    // feature flag (default). Aunque el usuario confirme, jamás se
                    // toca enviar. Sólo se habilita con un cambio deliberado.
                    WhatsAppActionAudit.recordBlocked()
                    logBackground(
                        "whatsappSend outcome=blocked_feature_disabled " +
                            WhatsAppActionAudit.redactedSummary()
                    )
                    speak(
                        "El envío real está desactivado en esta versión de prueba. No envié nada.",
                        force = true
                    )
                    return true
                }
                val result = OjoClaroAccessibilityService.tapWhatsAppSend(draft)
                WhatsAppActionAudit.recordSendTap()
                logBackground(
                    "whatsappSend outcome=${result.javaClass.simpleName} " +
                        WhatsAppActionAudit.redactedSummary()
                )
                if (result == WhatsAppSendTapResult.Sent) {
                    EstelaEarcons.confirm()
                    conversationMemory.noteContext("envió un mensaje de WhatsApp confirmado")
                }
                val spoken = when (result) {
                    // Copy honesto: Estela vio que el toque funcionó, no la
                    // entrega (AiCopyPolicy prohíbe afirmar "mensaje enviado").
                    WhatsAppSendTapResult.Sent -> "Listo, toqué enviar."
                    WhatsAppSendTapResult.FieldMismatch ->
                        "El texto del campo cambió, así que no envié nada. " +
                            "Revisalo y pedímelo de nuevo."
                    WhatsAppSendTapResult.NotInWhatsApp ->
                        "No estoy viendo WhatsApp, así que no envié nada."
                    WhatsAppSendTapResult.NoSendButton ->
                        "No encontré el botón de enviar, así que no envié nada."
                    WhatsAppSendTapResult.ClickFailed,
                    WhatsAppSendTapResult.ServiceUnavailable ->
                        "No pude tocar enviar. El mensaje quedó escrito; " +
                            "podés enviarlo a mano."
                }
                speak(spoken, force = true)
            }
            VoicePhraseNormalizer.isNeverConfirm(text) -> {
                // "sí" / "dale" / "ok" a secas no alcanzan para enviar.
                logBackground("whatsappSend outcome=weak_confirmation_rejected")
                speak("Para enviar, decí: enviá. Para cancelar, decí: cancelar.", force = true)
            }
            else -> {
                logBackground("whatsappSend outcome=reprompt")
                speak("Tenés un mensaje listo. Decí: enviá. O decí: cancelar.", force = true)
            }
        }
        return true
    }

    /** @return true si el texto fue un comando de envío/audio y quedó atendido. */
    private fun handleWhatsAppVoiceSendCommand(text: String): Boolean {
        if (WhatsAppVoiceSendPhrases.isSendAudioRequest(text)) {
            // Grabar y soltar el micrófono de WhatsApp exigiría gestos
            // (dispatchGesture), prohibidos por contrato de seguridad.
            // V1.11 — fallback honesto MEJOR: guía del gesto real.
            logBackground("whatsappAudio sendRequested=true guided=true")
            speakAudioFlowGuide()
            return true
        }

        if (WhatsAppVoiceSendPhrases.isPlayAudioCommand(text)) {
            val result = OjoClaroAccessibilityService.playVisibleWhatsAppAudio()
            logBackground("whatsappAudio play=${result.javaClass.simpleName}")
            val spoken = when (result) {
                WhatsAppAudioPlayResult.Playing -> "Reproduciendo el audio."
                WhatsAppAudioPlayResult.NoAudioVisible ->
                    "No veo ningún audio en esta pantalla. Abrí el chat donde está el audio."
                WhatsAppAudioPlayResult.NotInWhatsApp ->
                    "Para escuchar un audio, primero abrí WhatsApp."
                WhatsAppAudioPlayResult.ClickFailed,
                WhatsAppAudioPlayResult.ServiceUnavailable ->
                    "No pude tocar el audio. Probá de nuevo."
            }
            speak(spoken, force = true)
            return true
        }

        if (!WhatsAppVoiceSendPhrases.isSendDraftCommand(text)) return false

        val draftResult = OjoClaroAccessibilityService.readWhatsAppDraft()
        logBackground("whatsappSend draftRead=${draftResult.javaClass.simpleName}")
        when (val draft = draftResult) {
            is WhatsAppDraftReadResult.Draft -> {
                if (WhatsAppVoiceSendPhrases.looksSensitive(draft.text)) {
                    logBackground(
                        "whatsappSend blocked=sensitive_content draftLen=${draft.text.length}"
                    )
                    speak(
                        "El mensaje parece contener datos sensibles, así que no lo " +
                            "envío por voz. Revisalo y envialo a mano si estás seguro.",
                        force = true
                    )
                } else {
                    pendingWhatsAppSendDraft = draft.text
                    logBackground("whatsappSend pending=true draftLen=${draft.text.length}")
                    // Tono sobrio: hay una acción sensible esperando.
                    EstelaEarcons.pendingSensitive()
                    speak(
                        "Voy a enviar este mensaje: ${draft.text.take(220)}. " +
                            "¿Confirmás? Decí: enviá. O decí: cancelar.",
                        force = true
                    )
                }
            }
            WhatsAppDraftReadResult.EmptyDraft ->
                speak("No hay ningún mensaje escrito en el campo de texto.", force = true)
            WhatsAppDraftReadResult.NotInWhatsApp ->
                speak("Para enviar un mensaje, primero abrí el chat en WhatsApp.", force = true)
            WhatsAppDraftReadResult.NoEntryField ->
                speak(
                    "No encontré el campo de texto del chat. Abrí una conversación primero.",
                    force = true
                )
            WhatsAppDraftReadResult.ServiceUnavailable ->
                speak("Necesito el servicio de accesibilidad activo para esto.", force = true)
        }
        return true
    }

    // --- V1.8: compose inteligente con confirmación de contacto ---

    /** @return true si el texto inicia un compose y quedó atendido. */
    private fun handleSmartCompose(text: String): Boolean {
        val request = WhatsAppSmartComposeParser.parseCompose(text) ?: return false

        // Secretos: se bloquean ANTES de resolver contacto o escribir nada.
        val draft = request.message
        if (draft != null && WhatsAppVoiceSendPhrases.looksSensitive(draft)) {
            logBackground("smartCompose blocked=sensitive_content msgLen=${draft.length}")
            speak(
                "Ese mensaje parece contener datos sensibles, así que no lo " +
                    "preparo por voz. Escribilo a mano si estás seguro.",
                force = true
            )
            return true
        }

        val recipientQuery = request.recipientQuery
        if (recipientQuery == null) {
            // "mandá un mensaje diciendo X": hay mensaje pero falta a quién.
            pendingContactConfirmation = PendingContactConfirmation(
                recipientQuery = "",
                displayName = "",
                phoneE164 = "",
                messageDraft = draft,
                awaitingRecipient = true
            )
            logBackground("smartCompose resolved=missing_recipient msgPresent=${draft != null}")
            speak("¿A quién se lo mando? Decime el nombre, o decí: cancelar.", force = true)
            return true
        }

        resolveAndConfirmContact(recipientQuery, draft)
        return true
    }

    /** Resuelve el destinatario y pide confirmar nombre + últimos 4 dígitos. */
    private fun resolveAndConfirmContact(recipientQuery: String, draft: String?) {
        // "mandale a marco mi clave 12345678" parsea como destinatario largo:
        // si nombra una credencial, se bloquea ANTES de resolver (el resolver
        // trataría los dígitos como número dictado) y sin repetirla en voz.
        if (WhatsAppVoiceSendPhrases.mentionsSensitiveKeyword(recipientQuery)) {
            pendingContactConfirmation = null
            logBackground("smartCompose blocked=sensitive_recipient")
            speak(
                "Eso parece contener datos sensibles, así que no lo uso " +
                    "como destinatario. Decime solo el nombre del contacto.",
                force = true
            )
            return
        }
        when (val resolved = smartComposeResolver.resolve(recipientQuery)) {
            is com.ojoclaro.android.phone.ContactResolutionResult.Resolved -> {
                val candidate = resolved.candidate
                pendingContactConfirmation = PendingContactConfirmation(
                    recipientQuery = recipientQuery,
                    displayName = candidate.displayName,
                    phoneE164 = candidate.phoneE164,
                    messageDraft = draft
                )
                EstelaEarcons.pendingSensitive()
                logBackground("smartCompose resolved=single msgPresent=${draft != null}")
                val isDictatedNumber = candidate.source ==
                    com.ojoclaro.android.phone.ContactSource.USER_DICTATED_NUMBER
                val spokenIntro = if (isDictatedNumber) {
                    "Voy a usar el número que me dictaste, terminado en "
                } else {
                    "Encontré a ${candidate.displayName}, número terminado en "
                }
                speak(
                    spokenIntro +
                        "${WhatsAppSmartComposeParser.phoneLast4(candidate.phoneE164)}. " +
                        "¿Es este contacto?",
                    force = true
                )
            }
            is com.ojoclaro.android.phone.ContactResolutionResult.MultipleMatches -> {
                val options = resolved.candidates.take(3)
                pendingContactConfirmation = PendingContactConfirmation(
                    recipientQuery = recipientQuery,
                    displayName = "",
                    phoneE164 = "",
                    messageDraft = draft,
                    options = options
                )
                logBackground("smartCompose resolved=multiple count=${options.size}")
                val spokenOptions = options.mapIndexed { index, option ->
                    "${index + 1}: ${option.displayName}, terminado en " +
                        WhatsAppSmartComposeParser.phoneLast4(option.phoneE164)
                }.joinToString(". ")
                speak(
                    "Encontré ${options.size} contactos. $spokenOptions. " +
                        "¿Cuál querés? Decí: el primero, o el segundo.",
                    force = true
                )
            }
            else -> {
                logBackground("smartCompose resolved=not_found")
                // Queda esperando: el próximo texto puede ser otro nombre o
                // un número dictado. "cancelar" siempre sale.
                pendingContactConfirmation = PendingContactConfirmation(
                    recipientQuery = recipientQuery,
                    displayName = "",
                    phoneE164 = "",
                    messageDraft = draft,
                    awaitingRecipient = true
                )
                speak(
                    "No encontré a ${recipientQuery.take(40)} en tus contactos " +
                        "de confianza. Decime el número, u otro nombre. " +
                        "O decí: cancelar.",
                    force = true
                )
            }
        }
    }

    /** @return true si el texto era la respuesta a la confirmación de contacto. */
    private fun handlePendingContactReply(text: String): Boolean {
        val pending = pendingContactConfirmation ?: return false

        // El usuario está dictando el DESTINATARIO que faltaba (nombre o número).
        if (pending.awaitingRecipient) {
            if (WhatsAppVoiceSendPhrases.isCancelSend(text)) {
                pendingContactConfirmation = null
                logBackground("smartCompose outcome=cancelled_awaiting_recipient")
                speak("Listo, cancelo.", force = true)
                return true
            }
            if (VoicePhraseNormalizer.isNeverConfirm(text)) {
                speak("Decime el nombre del contacto, o decí: cancelar.", force = true)
                return true
            }
            val recipient = VoicePhraseNormalizer.normalizeForContactExtraction(text)
                .replace(Regex("^a(?:l)?\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
                .take(60)
            if (recipient.isBlank()) {
                speak("¿A quién se lo mando? Decime el nombre, o decí: cancelar.", force = true)
                return true
            }
            pendingContactConfirmation = null
            resolveAndConfirmContact(recipient, pending.messageDraft)
            return true
        }

        // El usuario está dictando el MENSAJE que faltaba.
        if (pending.awaitingMessage) {
            if (WhatsAppVoiceSendPhrases.isCancelSend(text)) {
                pendingContactConfirmation = null
                speak("Listo, cancelo.", force = true)
                return true
            }
            val message = text.trim().take(300)
            if (WhatsAppVoiceSendPhrases.looksSensitive(message)) {
                pendingContactConfirmation = null
                logBackground("smartCompose blocked=sensitive_message")
                speak("Ese mensaje parece sensible. Mejor escribilo a mano.", force = true)
                return true
            }
            pendingContactConfirmation = null
            prepareDraftAndAskSend(pending.displayName, pending.phoneE164, message)
            return true
        }

        when {
            WhatsAppVoiceSendPhrases.isCancelSend(text) -> {
                pendingContactConfirmation = null
                logBackground("smartCompose outcome=cancelled")
                speak("Listo, cancelo. No preparo nada.", force = true)
            }
            WhatsAppSmartComposeParser.isReadFullNumber(text) -> {
                // Número completo SOLO a pedido explícito; jamás en logs.
                val digits = pending.phoneE164.filter(Char::isDigit)
                    .toCharArray().joinToString(" ")
                speak("El número completo es: $digits. ¿Es este contacto?", force = true)
            }
            pending.options.isNotEmpty() -> {
                val choice = WhatsAppSmartComposeParser.ordinalChoice(text)
                    ?: pending.options.indexOfFirst {
                        text.lowercase().contains(it.displayName.lowercase())
                    }.takeIf { it >= 0 }
                if (choice == null || choice !in pending.options.indices) {
                    speak("Decime: el primero, el segundo, o cancelar.", force = true)
                } else {
                    val chosen = pending.options[choice]
                    continueWithConfirmedContact(
                        pending.copy(
                            displayName = chosen.displayName,
                            phoneE164 = chosen.phoneE164,
                            options = emptyList()
                        )
                    )
                }
            }
            WhatsAppSmartComposeParser.isContactYes(text) -> {
                continueWithConfirmedContact(pending)
            }
            else -> {
                speak(
                    "¿Es ese el contacto? Decime sí, o cancelar.",
                    force = true
                )
            }
        }
        return true
    }

    /** Contacto confirmado: pedir mensaje si falta, o preparar el borrador. */
    private fun continueWithConfirmedContact(pending: PendingContactConfirmation) {
        val message = pending.messageDraft
        if (message == null) {
            pendingContactConfirmation = pending.copy(awaitingMessage = true)
            speak("¿Qué mensaje querés mandarle a ${spokenLabelFor(pending.displayName)}?", force = true)
            return
        }
        pendingContactConfirmation = null
        prepareDraftAndAskSend(pending.displayName, pending.phoneE164, message)
    }

    /**
     * Abre el chat del contacto y, SOLO si el destino se verifica, escribe el
     * borrador por accesibilidad y deja el envío esperando el "enviá" fuerte de
     * siempre. Nunca prefilla por deep link antes de verificar (Blind Safety #2).
     */
    private fun prepareDraftAndAskSend(displayName: String, phoneE164: String, message: String) {
        // Blind Safety (Fix #2): NO prellenar el borrador por deep link (eso lo
        // escribiría ANTES de saber dónde cayó el chat). Abrimos SIN texto,
        // VERIFICAMOS el destino y recién entonces escribimos por accesibilidad. Si
        // no se puede confirmar el chat, no se escribe nada ni se arma pending
        // (mismo patrón seguro que openVerifyThenDraft / relationship compose).
        val destination = WhatsAppDestination.of(
            source = WhatsAppDestinationSource.CONTACT,
            confidence = WhatsAppDestinationConfidence.HIGH,
            label = displayName,
            phoneE164 = phoneE164
        )
        val opened = runCatching { whatsAppIntentHelper.openChat(displayName, phoneE164) }.getOrNull()
        if (opened !is CommandResult.Success) {
            logBackground("smartCompose open=failed")
            speak(WhatsAppBlindRouteNarrator.couldNotOpen(), force = true)
            return
        }
        logBackground("smartCompose open=launched msgLen=${message.length}")
        // #14: generación capturada antes de la ventana async (invalida composes
        // previos). Un cancel/STOP durante el delay aborta la escritura y el armado.
        val composeGen = invalidateInFlightWhatsAppCompose()
        serviceScope.launch {
            try {
                delay(1_200L)
                val verdict = verifyOpenedDestination(destination)
                logBackground("smartCompose verify=${verdict.redactedForLog()}")
                if (!verdict.isVerified) {
                    logBackground("smartCompose blocked=could_not_confirm_destination status=${verdict.status}")
                    speak(WhatsAppBlindRouteNarrator.couldNotConfirmDestination(), force = true)
                    return@launch
                }
                // #14: cancelado/STOP durante la ventana → no escribir ni armar pending.
                if (composeGen != whatsAppComposeGeneration.get()) {
                    logBackground("smartCompose aborted=cancelled_during_open_window")
                    return@launch
                }
                val set = runCatching { OjoClaroAccessibilityService.setWhatsAppDraft(message) }.getOrNull()
                if (set == WhatsAppDraftSetResult.SetOk) {
                    pendingWhatsAppSendDraft = message
                    EstelaEarcons.pendingSensitive()
                    conversationMemory.noteContext("dejó un mensaje de WhatsApp esperando confirmación")
                    speak(
                        "Preparé el mensaje para ${spokenLabelFor(displayName)}: ${message.take(220)}. " +
                            "Para enviarlo decí: enviá. Para cancelar decí: cancelar.",
                        force = true
                    )
                } else {
                    logBackground("smartCompose draft=false reason=${set?.javaClass?.simpleName ?: "exception"}")
                    speak("No pude escribir el mensaje en el chat. No envié nada.", force = true)
                }
            } catch (t: Throwable) {
                logBackground("smartCompose error=${t.javaClass.simpleName}")
                speak(WhatsAppBlindRouteNarrator.couldNotConfirmDestination(), force = true)
            }
        }
    }

    // --- V1.7: conversación libre LLM (última capa antes del fallback) ---

    private val conversationMemory = ConversationShortMemory()
    private val conversationClient by lazy {
        EstelaConversationClient(
            config = agentLlmConfig,
            networkClient = HttpUrlConnectionLlmAgentNetworkClient()
        )
    }

    /**
     * Charla con el LLM con memoria corta de runtime. Garantías:
     *  - "Dame un momento." una sola vez si tarda más de 2,6 s (se cancela
     *    si la respuesta llega antes; la respuesta final lo reemplaza);
     *  - timeout/falla → fallback honesto hablado, nunca silencio;
     *  - el contenido de la charla no se loguea (solo longitudes).
     */
    private suspend fun handleFreeConversation(text: String) {
        conversationMemory.recordUser(text)
        logBackground("routing conversationLlm=true sttLength=${text.length}")
        val waitNotice = serviceScope.launch {
            delay(WAIT_NOTICE_DELAY_MILLIS)
            speak("Dame un momento.", force = true)
        }
        val reply = try {
            conversationClient.converse(
                userText = text,
                shortMemory = conversationMemory.snapshotWithContext(),
                activeApp = contextState.current.externalApp.name,
                routeActive = OutdoorForegroundService.isGuidanceActive(),
                whatsappPending = pendingWhatsAppSendDraft != null
            )
        } catch (t: Throwable) {
            // Blind Safety (Fix F): un throw de red/parse no deja mudo al usuario;
            // se trata como respuesta nula → fallback hablado de abajo.
            logBackground("conversation error=${t.javaClass.simpleName}")
            null
        } finally {
            waitNotice.cancel()
        }
        val spoken = reply
            ?: "Ahora no puedo consultar el asistente, pero puedo ayudarte con WhatsApp: " +
                "leer mensajes, abrir WhatsApp, repetir o cancelar."
        if (reply == null) {
            EstelaEarcons.error()
            conversationMemory.noteContext("falló la conexión de conversación")
        }
        conversationMemory.recordAssistant(spoken)
        logBackground("conversation replyPresent=${reply != null} replyLen=${spoken.length}")
        speak(spoken, force = true)
    }

    // --- Estela Agent Core v1: ciclo de vida de la misión ---

    private fun startAgentMission(goal: String) {
        if (agentCoordinator.isActive) {
            speak("Ya hay una misión en curso.", force = true)
            return
        }
        val cleanGoal = goal.trim()
        if (cleanGoal.isBlank()) return
        // El micrófono no queda escuchando durante la misión (single-shot).
        voiceController.pauseListening()
        logAgentCore("missionRequested=true goalLen=${cleanGoal.length}")
        missionActiveFlag = true
        agentMissionJob = serviceScope.launch {
            val outcome = try {
                agentCoordinator.runMission(cleanGoal)
            } catch (error: CancellationException) {
                logAgentCore("missionCancelledHard=true finalState=IDLE")
                throw error
            } finally {
                missionActiveFlag = false
                agentMissionReply?.let { if (!it.isCompleted) it.complete(null) }
                agentMissionReply = null
            }
            logAgentCore(
                "missionOutcome=${outcome.status} steps=${outcome.stepsExecuted} " +
                    "replans=${outcome.replans} errorCode=${outcome.errorCode ?: "-"} finalState=IDLE"
            )
            if (outcome.status == AgentMissionStatus.CANCELLED) {
                speak(AgentSessionCoordinator.CANCELLED_TEXT, force = true)
            }
            finishAgentMissionTurn()
        }
    }

    /** Entrada QA (solo debug): misión inyectada por broadcast sin pasar por STT. */
    private fun startAgentMissionFromDebug(goal: String) {
        if (!BuildConfig.DEBUG || goal.isBlank()) return
        accessibilityOverlayVoiceSingleShot = true
        appOverlayEnabled = false
        val sourcePackage = OjoClaroAccessibilityService.readActivePackageName()
        val snapshot = contextState.start(
            externalApp = ExternalAppName.fromPackageName(sourcePackage),
            reason = "debug_agent_mission",
            returnHint = "",
            agentState = null
        )
        startForegroundSafely(snapshot)
        logAgentCore("debugMissionInjected=true activityOpened=false")
        startAgentMission(goal)
    }

    private fun cancelAgentMission(reason: String, speakConfirmation: Boolean = true) {
        val wasActive = agentCoordinator.isActive || agentMissionJob?.isActive == true
        if (!wasActive) return
        agentCoordinator.cancel(reason)
        agentMissionReply?.let { if (!it.isCompleted) it.complete(null) }
        agentMissionReply = null
        agentMissionJob?.cancel()
        agentMissionJob = null
        missionActiveFlag = false
        logAgentCore("missionCancelled=true reason=${reason.take(32)} finalState=IDLE")
        if (speakConfirmation) {
            speak(AgentSessionCoordinator.CANCELLED_TEXT, force = true)
            serviceScope.launch { finishAgentMissionTurn() }
        }
    }

    /**
     * Cierre uniforme del turno tras una misión: espera a que el TTS final
     * termine y devuelve el servicio a IDLE (single-shot) o al loop normal.
     * completeOverlayVoiceTurn es idempotente, así que la carrera benigna con
     * onSpeechFinished no duplica nada.
     */
    private suspend fun finishAgentMissionTurn() {
        waitUntilTtsIdle(maxWaitMillis = 20_000L)
        if (accessibilityOverlayVoiceSingleShot) {
            completeOverlayVoiceTurn("agent_mission_end")
        } else {
            resumeListeningIfActive()
        }
    }

    private suspend fun waitUntilTtsIdle(maxWaitMillis: Long) {
        var waited = 0L
        while (speechController.isSpeaking && waited < maxWaitMillis) {
            delay(250L)
            waited += 250L
        }
        delay(TTS_TO_MIC_DELAY_MILLIS)
    }

    /**
     * ask_user: la pregunta ya fue hablada por el coordinator. Esperamos a que
     * el TTS termine (no capturar la propia voz) y abrimos UNA escucha
     * single-shot. El texto llega por handleRecognizedText → agentMissionReply.
     */
    private suspend fun awaitAgentMissionUserReply(question: String): String? {
        waitUntilTtsIdle(maxWaitMillis = 15_000L)
        if (!hasRecordAudioPermission()) {
            logAgentCore("askUserBlocked=mic_permission_missing")
            return null
        }
        val deferred = CompletableDeferred<String?>()
        agentMissionReply = deferred
        voiceController.setExpectingResponse(true)
        voiceController.startListening()
        val reply = withTimeoutOrNull(AgentMissionBudgets.ASK_USER_TIMEOUT_MILLIS) {
            deferred.await()
        }
        agentMissionReply = null
        voiceController.pauseListening()
        return reply
    }

    // --- Estela Agent Core v1: herramientas locales ---

    private suspend fun missionBackendHealth(timeoutMillis: Int): AgentBackendHealth =
        withContext(Dispatchers.IO) {
            val start = SystemClock.elapsedRealtime()
            val state = ProxyHealthProbe(
                baseUrl = agentLlmConfig.normalizedBaseUrl,
                timeoutMillis = timeoutMillis
            ).check()
            val latency = SystemClock.elapsedRealtime() - start
            when (state) {
                is ProxyHealthState.Available ->
                    AgentBackendHealth(reachable = true, status = "HEALTHY", latencyMillis = latency)
                else ->
                    AgentBackendHealth(reachable = false, status = "UNREACHABLE", latencyMillis = latency)
            }
        }

    private suspend fun missionOpenAppById(appId: String): Boolean = when (appId) {
        "WHATSAPP" -> missionLaunchPackage("com.whatsapp")
        "WHATSAPP_BUSINESS" -> missionLaunchPackage("com.whatsapp.w4b")
        "SETTINGS" -> withContext(Dispatchers.Main.immediate) {
            runCatching {
                startActivity(
                    Intent(android.provider.Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
        }
        "OJO_CLARO" -> missionLaunchPackage(packageName)
        else -> false
    }

    private suspend fun missionLaunchPackage(targetPackage: String): Boolean =
        withContext(Dispatchers.Main.immediate) {
            runCatching {
                val launch = packageManager.getLaunchIntentForPackage(targetPackage)
                    ?.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                    ?: return@withContext false
                startActivity(launch)
                true
            }.getOrDefault(false)
        }

    /**
     * Lectura LOCAL para la misión. Reusa los mismos use cases deterministas
     * del modo continuación; el TEXTO leído se habla acá y nunca viaja al
     * planner: el coordinator solo ve métricas (nodos/paquete/fuente).
     */
    private suspend fun missionReadScreenLocal(mode: String): AgentLocalReadOutcome {
        val spokenResult: Pair<Boolean, String?> = when (mode) {
            "VISIBLE_MESSAGES" ->
                when (val result = whatsAppVisibleMessagesReader.handle("leeme los mensajes")) {
                    is WhatsAppMessagesResponse.Read -> true to result.spokenText
                    WhatsAppMessagesResponse.NotAMessageCommand -> false to null
                    is WhatsAppMessagesResponse.NeedsAccessibilityService -> false to result.spokenText
                    is WhatsAppMessagesResponse.NotInWhatsApp -> false to result.spokenText
                    is WhatsAppMessagesResponse.NotInChat -> false to result.spokenText
                    is WhatsAppMessagesResponse.StaleSnapshot -> false to result.spokenText
                    is WhatsAppMessagesResponse.NoMessages -> false to result.spokenText
                }

            else -> {
                val phrase = if (mode == "VISIBLE_ACTIONS") "que puedo hacer aca" else "leer la pantalla"
                when (val result = screenUnderstandingUseCase.handle(phrase)) {
                    is ScreenUnderstandingResult.Spoken -> true to result.spokenText
                    is ScreenUnderstandingResult.NeedsAccessibilityService -> false to result.spokenText
                    ScreenUnderstandingResult.NotAScreenCommand -> false to null
                }
            }
        }
        val (success, spokenText) = spokenResult
        val ttsRequested = !spokenText.isNullOrBlank()
        if (ttsRequested) {
            speak(spokenText!!, force = true)
        }
        val diagnostics = OjoClaroAccessibilityService.lastTreeReadDiagnostics()
        return AgentLocalReadOutcome(
            success = success,
            acceptedNodes = diagnostics?.acceptedNodes ?: 0,
            targetPackage = diagnostics?.rootPackage,
            source = diagnostics?.source,
            ttsRequested = ttsRequested,
            failureReason = if (success) null else "local_read_degraded"
        )
    }

    private fun logAgentCore(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(AGENT_CORE_TAG, "route=$ROUTE_AGENT_CORE $message")
        }
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
                    resumeOrCompleteOverlayVoiceTurn()
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
        if (accessibilityOverlayVoiceSingleShot && outcome.spokenText.isBlank()) {
            completeOverlayVoiceTurn("action_completed")
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
            is ExternalActionEvent.OpenSafeApp -> {
                val registry = AppCapabilityRegistry()
                val capability = registry.findByPackageName(action.packageName)
                    ?: registry.findByAppName(action.appName)
                if (capability == null) {
                    CommandResult.Failed(
                        spokenText = "No reconoci esa app como segura para abrir.",
                        recoverable = true
                    )
                } else {
                    SafeAppLauncher(
                        resolver = AndroidInstalledAppResolver(this),
                        starter = AndroidSafeAppStarter(this)
                    ).launch(
                        capability = capability,
                        userConfirmed = action.userConfirmed
                    ).toCommandResult()
                }
            }
            ExternalActionEvent.ReadVisibleScreen ->
                CommandResult.Failed("Volve a Estela para leer pantalla.", recoverable = true)
            ExternalActionEvent.RequestLocationPermission ->
                CommandResult.Failed("Volve a Estela para activar ubicacion.", recoverable = true)
            is ExternalActionEvent.ExternalAppHandoff -> executeExternalAction(action.delegate)
        }

    private fun speak(text: String, force: Boolean = false) {
        // Memoria corta V1.3: "repetí" sin ruta activa repite esto.
        // El propio "Te repito..." no pisa la respuesta original.
        if (!text.startsWith(REPEAT_PREFIX)) {
            lastSpokenResponse = text
        }
        // V1.14 — al empezar a hablar la presencia pasa a SPEAKING; vuelve al
        // estado de reposo cuando el TTS termina (callbacks de SpeechController).
        updatePresence { it.copy(speaking = true, processing = false) }
        speechController.speak(text, force = force)
    }

    // --- V1.14: presencia animada (refuerzo visual; la voz es principal) ---

    /**
     * Muta las señales y empuja el estado de reposo derivado al botón
     * flotante. Si hay un destello de WARNING en curso, no lo pisa: el
     * revert del warning ya recalcula con las señales vigentes.
     */
    private fun updatePresence(
        mutate: (AssistantPresenceStateMapper.Signals) -> AssistantPresenceStateMapper.Signals
    ) {
        presenceSignals = mutate(presenceSignals)
        if (presenceWarningJob?.isActive == true) return
        OjoClaroAccessibilityService.setPresenceState(
            AssistantPresenceStateMapper.resolve(presenceSignals)
        )
    }

    /**
     * Destello breve de alerta (pago/clave/permiso/bloqueo): "me frené por
     * seguridad". No deja el estado colgado: vuelve SIEMPRE al estado de
     * reposo derivado de las señales vigentes.
     */
    private fun flashPresenceWarning() {
        presenceWarningJob?.cancel()
        OjoClaroAccessibilityService.setPresenceState(AssistantVisualState.WARNING)
        presenceWarningJob = serviceScope.launch {
            delay(PRESENCE_WARNING_MILLIS)
            OjoClaroAccessibilityService.setPresenceState(
                AssistantPresenceStateMapper.resolve(presenceSignals)
            )
        }
    }

    private fun refreshContinuationUi() {
        val snapshot = contextState.current
        if (!snapshot.active) return
        startForegroundSafely(snapshot)
        if (appOverlayEnabled) {
            overlayController.show(snapshot)
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Log de diagnóstico del modo continuación. Solo en debug. Nunca incluye
     * texto reconocido ni contenido de mensajes: solo booleanos y estados.
     */
    private fun logBackground(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(BACKGROUND_TAG, message)
        }
    }

    private fun logOverlayVoice(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(SCREEN_DIAGNOSTIC_TAG, "route=OVERLAY_VOICE_ENTRYPOINT $message")
        }
    }

    private fun sanitizeTraceToken(value: String): String =
        value
            .replace(Regex("[^A-Za-z0-9_.:-]"), "_")
            .take(MAX_TRACE_TOKEN_LENGTH)

    companion object {
        /**
         * Blind Safety (#3) — ventana de recencia para tratar a WhatsApp como contexto
         * activo tras verlo en foreground (aunque un overlay/actividad propia quede
         * encima). Acotada: si el usuario pasa a OTRO app real, el rastro se borra antes.
         */
        private const val WHATSAPP_CONTEXT_RECENCY_MS = 120_000L
        private const val BACKGROUND_TAG = "EstelaBackground"
        private const val SCREEN_DIAGNOSTIC_TAG = "EstelaScreenDiagnostic"
        private const val AGENT_CORE_TAG = "EstelaAgentCore"
        private const val ROUTE_AGENT_CORE = "ESTELA_AGENT_CORE_V1"
        private const val DEFAULT_START_LISTENING_DELAY_MS = 2_500L
        private const val OVERLAY_VOICE_DEFAULT_START_DELAY_MS = 250L
        private const val MAX_TRACE_TOKEN_LENGTH = 96

        /**
         * Máximo de re-escuchas automáticas por turno single-shot cuando hay
         * una pregunta pendiente. Evita el loop infinito de micrófono si la
         * persona no contesta.
         */
        private const val MAX_OVERLAY_FOLLOW_UP_TURNS = 3

        /**
         * V1.12 — Instagram Direct: vencimiento duro de los pendientes de
         * mensajería (texto y videollamada) y tiempos de navegación asistida.
         * El paquete es EXACTO por contrato de seguridad.
         */
        private const val MESSAGING_PENDING_TTL_MILLIS = 180_000L
        private const val INSTAGRAM_PACKAGE_NAME = "com.instagram.android"

        /** V1.13 — Camera Assist: corte de lectura hablada y polling. */
        private const val MAX_SPOKEN_OCR_CHARS = 240
        private const val CAMERA_SCAN_POLL_MILLIS = 400L

        /** V1.14 — duración del destello de alerta de la presencia. */
        private const val PRESENCE_WARNING_MILLIS = 1_600L
        private const val INSTAGRAM_NAV_LAUNCH_DELAY_MILLIS = 2_500L
        private const val INSTAGRAM_NAV_STEP_DELAY_MILLIS = 1_300L

        /** Prefijo de "repetí": evita que la repetición pise la memoria corta. */
        private const val REPEAT_PREFIX = "Te repito: "

        /**
         * Hardening Alexa-like: respuesta de AYUDA corta y predecible. Nombra
         * exactamente los comandos básicos garantizados (rutas locales), sin
         * prometer lo que depende de backend/permisos.
         */
        private const val SHORT_HELP =
            "Podés decir: describir entorno, leer pantalla, dónde estoy, repetir o cancelar."

        /** Espera para que el snapshot de accesibilidad refleje el post-scroll. */
        private const val SCROLL_SETTLE_MILLIS = 450L

        /** Espera para verificar que el chat realmente se abrió tras el click. */
        private const val CHAT_OPEN_SETTLE_MILLIS = 700L

        /**
         * Sprint WhatsApp WA-5: DRY-RUN del flujo de respuesta. Mientras sea
         * true, aunque el usuario confirme las DOS veces, NUNCA se toca enviar.
         * Default true por seguridad; el envío real solo se habilita
         * deliberadamente (más adelante, con un chat de prueba autorizado).
         */
        @Volatile
        @JvmStatic
        var whatsAppReplyDryRun: Boolean = true

        /**
         * Anxiety hardening — modo seguro/ansiedad de WhatsApp. Mientras esté
         * activo Estela habla más corto y reafirma "no toqué nada" al cancelar.
         * Vive en el companion (como [whatsAppReplyDryRun]) porque el servicio
         * single-shot muere entre turnos; solo en memoria del proceso, nunca
         * persistido.
         */
        @Volatile
        @JvmStatic
        var whatsAppSafeMode: Boolean = false

        /** "Dame un momento." si la conversación tarda más que esto. */
        private const val WAIT_NOTICE_DELAY_MILLIS = 2_600L

        /** V1.11 — monitoreo de viaje: lectura cada tanto, con tope duro. */
        private const val RIDE_MONITOR_INTERVAL_MILLIS = 20_000L
        private const val RIDE_MONITOR_MAX_MILLIS = 10L * 60_000L

        /**
         * V1.3 — memoria corta de runtime: la última respuesta hablada por
         * este servicio, para "repetí" sin ruta activa. Vive en el companion
         * porque el servicio single-shot muere entre turnos. Solo en memoria
         * del proceso, nunca persistida.
         */
        @Volatile
        private var lastSpokenResponse: String? = null

        const val ACTION_AGENT_MISSION_CANCEL =
            "com.ojoclaro.android.global.ACTION_AGENT_MISSION_CANCEL"
        const val ACTION_DEBUG_AGENT_MISSION =
            "com.ojoclaro.android.global.ACTION_DEBUG_AGENT_MISSION"
        const val ACTION_DEBUG_VOICE_TEXT =
            "com.ojoclaro.android.global.ACTION_DEBUG_VOICE_TEXT"
        const val EXTRA_AGENT_GOAL = "agent_goal"

        /** true mientras hay una misión del Agent Core en curso (para overlay). */
        @Volatile
        private var missionActiveFlag: Boolean = false

        fun isAgentMissionActive(): Boolean = missionActiveFlag

        /** Cancela la misión en curso (botón Cancelar del overlay). */
        fun requestAgentMissionCancel(context: Context) {
            runCatching {
                context.applicationContext.startService(
                    Intent(context.applicationContext, GlobalAssistantService::class.java).apply {
                        action = ACTION_AGENT_MISSION_CANCEL
                    }
                )
            }
        }

        /** QA físico (solo debug): inyecta texto "reconocido" al routing real. */
        fun debugInjectVoiceText(context: Context, text: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, GlobalAssistantService::class.java).apply {
                        action = ACTION_DEBUG_VOICE_TEXT
                        putExtra(EXTRA_AGENT_GOAL, text.take(500))
                    }
                )
            }
        }

        /** QA físico (solo debug): inyecta una misión sin pasar por STT. */
        fun debugStartAgentMission(context: Context, goal: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, GlobalAssistantService::class.java).apply {
                        action = ACTION_DEBUG_AGENT_MISSION
                        putExtra(EXTRA_AGENT_GOAL, goal.take(500))
                    }
                )
            }
        }

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

        fun startOverlayVoice(
            context: Context,
            sourcePackageName: String?,
            startListeningDelayMillis: Long
        ): Boolean =
            runCatching {
                val intent = Intent(context, GlobalAssistantService::class.java).apply {
                    action = GlobalAssistantMode.ACTION_OVERLAY_VOICE_ENTRYPOINT
                    putExtra(GlobalAssistantMode.EXTRA_SOURCE_PACKAGE_NAME, sourcePackageName)
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
