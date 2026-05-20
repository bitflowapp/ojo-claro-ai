package com.ojoclaro.android.ui.home

import com.ojoclaro.android.DEBUG_SUBMIT_TEXT_MAX_CHARS
import com.ojoclaro.android.DebugSubmitTextRejectReason
import com.ojoclaro.android.external.CommandRouter
import com.ojoclaro.android.external.ExternalCommandType
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.AgentSessionSnapshot
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.task.AgentTaskOrchestrator
import com.ojoclaro.android.agent.runtime.screen.RobotStatusDiagnosticPhrases
import com.ojoclaro.android.debugSubmitTextDecision
import com.ojoclaro.android.sanitizeDebugSubmitText
import com.ojoclaro.android.domain.PersonalAgentDecision
import com.ojoclaro.android.message.MessageCompositionResult
import com.ojoclaro.android.message.MessageStyle
import com.ojoclaro.android.model.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HomeViewModelExternalRoutingTest {

    private val router = CommandRouter()

    @Test
    fun unsupportedCommandWithoutPendingConsentDoesNotEnterExternalOrchestrator() {
        val shouldHandle = shouldHandleExternalCommand(
            text = "contame algo raro del clima",
            hasPendingConsent = false,
            router = router
        )

        assertFalse(shouldHandle)
    }

    @Test
    fun externalCommandEntersExternalOrchestrator() {
        val shouldHandle = shouldHandleExternalCommand(
            text = "mandale un mensaje a ContactoDemo que estoy llegando",
            hasPendingConsent = false,
            router = router
        )

        assertTrue(shouldHandle)
    }

    @Test
    fun pendingConsentAlwaysEntersExternalOrchestratorForCleanup() {
        val shouldHandle = shouldHandleExternalCommand(
            text = "necesito ayuda",
            hasPendingConsent = true,
            router = router
        )

        assertTrue(shouldHandle)
    }

    @Test
    fun externalAppHandoffPausesVoiceLoop() {
        val event = ExternalActionEvent.ExternalAppHandoff(
            externalAppName = "WhatsApp",
            reason = "Abrí WhatsApp.",
            returnHint = "Para seguir, volvé con el botón Estela.",
            spokenText = "Abrí WhatsApp.",
            delegate = ExternalActionEvent.OpenWhatsApp
        )

        assertTrue(shouldPauseVoiceLoopForExternalEvent(event))
        assertFalse(shouldPauseVoiceLoopForExternalEvent(ExternalActionEvent.ReadVisibleScreen))
    }

    @Test
    fun externalHandoffDoesNotRestartVoiceLoopAfterSpeechOrResume() {
        assertFalse(canStartListeningAfterSpeech(AppState.EXTERNAL_APP_HANDOFF))
        assertFalse(canStartListeningAfterSpeech(AppState.GLOBAL_ASSISTANT_ACTIVE))
        assertFalse(shouldAutoStartListeningOnResume(AppState.EXTERNAL_APP_HANDOFF))
        assertFalse(shouldAutoStartListeningOnResume(AppState.GLOBAL_ASSISTANT_ACTIVE))
        assertTrue(canStartListeningAfterSpeech(AppState.IDLE))
        assertTrue(canStartListeningAfterSpeech(AppState.WAITING_WHATSAPP_ACTION))
        assertTrue(shouldAutoStartListeningOnResume(AppState.IDLE))
    }

    @Test
    fun callateDuringDecidePersonalAgentDropsLateResponse() {
        assertTrue(
            shouldDropAsyncResult(
                requestId = 4L,
                activeRequestId = 4L,
                mutedThroughRequestId = 4L,
                robotEnabled = true
            )
        )
        assertEquals(
            AsyncResultDropReason.MUTED_OR_STALE,
            asyncResultDropReason(
                requestId = 4L,
                activeRequestId = 4L,
                mutedThroughRequestId = 4L,
                robotEnabled = true
            )
        )
    }

    @Test
    fun resetDuringDecidePersonalAgentDropsLateResponse() {
        assertTrue(isRequestMutedOrStale(requestId = 4L, activeRequestId = 5L, mutedThroughRequestId = 5L))
        assertTrue(
            shouldDropAsyncResult(
                requestId = 4L,
                activeRequestId = 5L,
                mutedThroughRequestId = 5L,
                robotEnabled = true
            )
        )
    }

    @Test
    fun callateDuringAssistantApiDropsLateResponse() {
        assertEquals(
            "muted_or_stale",
            asyncResultDropReason(
                requestId = 8L,
                activeRequestId = 8L,
                mutedThroughRequestId = 8L,
                robotEnabled = true
            ).logCode
        )
    }

    @Test
    fun resetDuringExternalCommandDropsLateExternalAction() {
        assertTrue(
            shouldDropAsyncResult(
                requestId = 9L,
                activeRequestId = 10L,
                mutedThroughRequestId = 10L,
                robotEnabled = true
            )
        )
    }

    @Test
    fun applyOutcomeIfExternalMustDropWhenRequestIsMuted() {
        assertTrue(
            isRequestMutedOrStale(
                requestId = 12L,
                activeRequestId = 12L,
                mutedThroughRequestId = 12L
            )
        )
    }

    @Test
    fun onExternalCommandResultDropsWhenRobotPaused() {
        assertEquals(
            AsyncResultDropReason.ROBOT_PAUSED,
            asyncResultDropReason(
                requestId = 14L,
                activeRequestId = 14L,
                mutedThroughRequestId = 13L,
                robotEnabled = false
            )
        )
    }

    @Test
    fun currentAsyncResultStillApplies() {
        assertFalse(
            shouldDropAsyncResult(
                requestId = 21L,
                activeRequestId = 21L,
                mutedThroughRequestId = 20L,
                robotEnabled = true
            )
        )
    }

    @Test
    fun waitingWhatsAppActionUsesExtendedListening() {
        assertTrue(shouldUseExtendedListeningForAgentState(AgentState.WAITING_WHATSAPP_ACTION))
        assertTrue(shouldUseExtendedListeningForAgentState(AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE))
        assertTrue(shouldUseExtendedListeningForAgentState(AgentState.WAITING_CONTACT))
        assertTrue(shouldUseExtendedListeningForAgentState(AgentState.WAITING_MESSAGE))
        assertTrue(shouldUseExtendedListening(AppState.WAITING_CONFIRMATION, null))
        assertTrue(shouldUseExtendedListening(AppState.WAITING_WHATSAPP_ACTION, null))
        assertFalse(shouldUseExtendedListening(AppState.IDLE, null))
    }

    @Test
    fun ttsToMicDelayIsSmallButNonZero() {
        assertTrue(TTS_TO_MIC_DELAY_MILLIS in 150L..500L)
    }

    @Test
    fun humanMessageDraftRequestUsesPersonalAgentBeforePlainExternalRouting() {
        val parsed = LocalIntentParser().parse("decile a ContactoDemo que llego tarde pero decilo bien")

        assertTrue(
            shouldUsePersonalAgentForHumanMessageDraft(
                text = "decile a ContactoDemo que llego tarde pero decilo bien",
                parsedIntent = parsed
            )
        )
    }

    @Test
    fun personalHumanMessageCreatesRealWhatsAppPending() {
        val decision = PersonalAgentDecision.ComposeHumanMessage(
            contactName = "ContactoDemo",
            originalMessageText = "llego tarde",
            composition = MessageCompositionResult(
                proposedMessage = "ContactoDemo, estoy llegando un poco tarde, pero ya estoy en camino. Te aviso apenas esté cerca.",
                spokenProposal = "Puedo preparar este mensaje para ContactoDemo: 'ContactoDemo, estoy llegando un poco tarde, pero ya estoy en camino. Te aviso apenas esté cerca.'. Para prepararlo en WhatsApp, decí: confirmar.",
                styleUsed = MessageStyle.WARM,
                requiresConfirmation = true,
                shouldSendAutomatically = false
            ),
            debugLabel = "LLM_COMPOSE"
        )

        val pending = buildWhatsAppComposePendingFromPersonalDecision(decision, nowMillis = 1_000L)

        assertNotNull(pending)
        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, pending.command.type)
        assertEquals("ContactoDemo", pending.command.contactName)
        assertEquals("ContactoDemo, estoy llegando un poco tarde, pero ya estoy en camino. Te aviso apenas esté cerca.", pending.command.messageText)
    }

    @Test
    fun personalHumanMessageDoesNotCreatePendingIfModelTriesAutoSend() {
        val decision = PersonalAgentDecision.ComposeHumanMessage(
            contactName = "ContactoDemo",
            originalMessageText = "llego tarde",
            composition = MessageCompositionResult(
                proposedMessage = "Llego en unos minutos.",
                spokenProposal = "Puedo preparar este mensaje para ContactoDemo: Llego en unos minutos. ¿Querés confirmarlo?",
                styleUsed = MessageStyle.WARM,
                requiresConfirmation = true,
                shouldSendAutomatically = true
            ),
            debugLabel = "LLM_COMPOSE"
        )

        val pending = buildWhatsAppComposePendingFromPersonalDecision(decision, nowMillis = 1_000L)

        assertTrue(pending == null)
    }

    @Test
    fun strictReminderForSiYDaleKeepsConfirmationSafe() {
        assertEquals(
            "Para evitar errores, necesito que digas exactamente: confirmar.",
            strictConfirmationReminderText()
        )
    }

    @Test
    fun voiceControlCommandsAreRecognizedForSessionMemory() {
        assertTrue(isRepeatLastResponseCommand("repetí"))
        assertTrue(isRepeatLastResponseCommand("qué dijiste"))
        assertTrue(isSlowVoiceCommand("más lento"))
        assertTrue(isGoHomeCommand("volver al inicio"))
        assertTrue(isResetFlowCommand("resetear"))
        assertTrue(isResetFlowCommand("volver al inicio"))
        assertTrue(isResetFlowCommand("limpiar estado"))
        assertTrue(slowVoiceUnavailableText().contains("frases cortas", ignoreCase = true))
        assertEquals("Todavía no dije nada para repetir.", repeatedResponseText(""))
        assertEquals("Te escucho.", repeatedResponseText("  Te escucho.  "))
        assertTrue(isContextualMessageRetryCommand("mandáselo a ContactoDemo mejor"))
    }

    @Test
    fun robotSessionCommandsAreExplicitAndDoNotCaptureRepeatLast() {
        assertEquals(RobotSessionCommand.ENABLE, robotSessionCommand("ojo claro"))
        assertEquals(RobotSessionCommand.ENABLE, robotSessionCommand("activá robot"))
        assertEquals(RobotSessionCommand.ENABLE, robotSessionCommand("segui escuchando"))
        assertEquals(RobotSessionCommand.DISABLE, robotSessionCommand("desactivá robot"))
        assertEquals(RobotSessionCommand.DISABLE, robotSessionCommand("pausar robot"))
        assertEquals(RobotSessionCommand.NONE, robotSessionCommand("repetí"))
        assertTrue(isRepeatLastResponseCommand("repetí"))
    }

    @Test
    fun repeatLastResponseKeepsExactPreviousSpokenText() {
        val previous = "Volviste a Estela. WhatsApp quedo abierto, pero yo no envie nada automaticamente."

        assertTrue(isRepeatLastResponseCommand("repetir"))
        assertFalse(isResetFlowCommand("repetir"))
        assertEquals(previous, repeatedResponseText(previous))
    }

    @Test
    fun recognizedSpeechDisplayHidesSensitiveText() {
        assertEquals(
            SENSITIVE_RECOGNIZED_TEXT,
            safeRecognizedSpeechDisplayText("mi clave es 1234")
        )
        assertEquals(
            SENSITIVE_RECOGNIZED_TEXT,
            safeRecognizedSpeechDisplayText("mi banco muestra saldo")
        )
        assertEquals(
            SENSITIVE_RECOGNIZED_TEXT,
            safeRecognizedSpeechDisplayText("otp 123456")
        )
        assertEquals(
            "abrir WhatsApp principal",
            safeRecognizedSpeechDisplayText("abrir WhatsApp principal")
        )
    }

    @Test
    fun debugSubmitTextIsBoundedAndStillUsesSafeDisplayRedaction() {
        val longText = "  mi clave es 1234  " + "x".repeat(DEBUG_SUBMIT_TEXT_MAX_CHARS * 3)
        val decision = debugSubmitTextDecision(longText)

        assertFalse(decision.accepted)
        assertEquals(DebugSubmitTextRejectReason.TOO_LONG, decision.rejectReason)
        assertEquals("", sanitizeDebugSubmitText(longText))
    }

    @Test
    fun debugSubmitTextRejectsSensitiveInput() {
        val decision = debugSubmitTextDecision("mi clave es 1234")

        assertFalse(decision.accepted)
        assertEquals(DebugSubmitTextRejectReason.SENSITIVE, decision.rejectReason)
        assertTrue(decision.commandRedacted)
    }

    @Test
    fun debugSubmitTextAcceptsNormalShortInput() {
        val decision = debugSubmitTextDecision("  abrir   WhatsApp  ")

        assertTrue(decision.accepted)
        assertEquals("abrir WhatsApp", decision.text)
        assertEquals(null, decision.rejectReason)
    }

    @Test
    fun resetTextDoesNotClaimMemoryDeletion() {
        assertTrue(RESET_FLOW_TEXT.contains("reseteado", ignoreCase = true))
        assertFalse(RESET_FLOW_TEXT.contains("memoria", ignoreCase = true))
        assertFalse(RESET_FLOW_TEXT.contains("preferencia", ignoreCase = true))
    }

    @Test
    fun voiceCorrectionFallbackIsLocalAndActionable() {
        assertTrue(VOICE_CORRECTION_FALLBACK_TEXT.contains("No entend", ignoreCase = true))
        assertTrue(VOICE_CORRECTION_FALLBACK_TEXT.contains("pantalla", ignoreCase = true))
        assertTrue(VOICE_CORRECTION_FALLBACK_TEXT.contains("WhatsApp", ignoreCase = true))
        assertTrue(VOICE_CORRECTION_FALLBACK_TEXT.contains("resetear", ignoreCase = true))
    }

    @Test
    fun whatsappWaitingFallbackIsExplicitAndDoesNotInventAction() {
        val fallback = whatsAppWaitingFallbackText()

        assertTrue(fallback.contains("WhatsApp principal", ignoreCase = true))
        assertTrue(fallback.contains("chat de Marco", ignoreCase = true))
        assertTrue(fallback.contains("mensaje para Marco", ignoreCase = true))
        assertTrue(fallback.contains("cancelar", ignoreCase = true))
        assertFalse(fallback.contains("confirmado", ignoreCase = true))
        assertFalse(fallback.contains("enviado", ignoreCase = true))
    }

    @Test
    fun diagnosticCommandDoesNotCaptureRepeatLast() {
        assertTrue(isRepeatLastResponseCommand("repetir"))
        assertFalse(RobotStatusDiagnosticPhrases.isDiagnosticCommand("repetir"))
        assertFalse(RobotStatusDiagnosticPhrases.isDiagnosticCommand("que dijiste"))
    }

    @Test
    fun diagnosticCommandDoesNotCaptureWhatsAppLegacy() {
        val legacy = "mandale un mensaje a ContactoDemo que estoy llegando"

        assertTrue(shouldHandleExternalCommand(legacy, hasPendingConsent = false, router = router))
        assertFalse(RobotStatusDiagnosticPhrases.isDiagnosticCommand(legacy))
    }

    @Test
    fun taskReviewCommandsAreRecognizedBeforeLegacyFallback() {
        assertTrue(AgentTaskOrchestrator.isTaskScreenReviewCommand("revisa la tarea"))
        assertTrue(AgentTaskOrchestrator.isTaskScreenReviewCommand("segui con la tarea"))
        assertTrue(AgentTaskOrchestrator.isStatusQuery("en que paso estamos"))
    }

    @Test
    fun homeDiagnosticTextIsUsefulAndDoesNotExposeSecrets() {
        val diagnostic = buildHomeDiagnosticText(
            versionName = "0.1.1-alpha",
            isDebug = true,
            assistantBaseUrlConfigured = false,
            microphoneGranted = true,
            cameraGranted = false,
            ttsAvailable = true,
            whatsappStatus = "no detectado",
            pendingSummary = "COMPOSE_WHATSAPP_MESSAGE",
            lastError = "NO_SPEECH",
            voiceHearingStatus = "usando parcial",
            voiceErrorCategory = "SPEECH_TIMEOUT",
            voiceSpeechEngine = "on-device",
            displayMode = ProductDisplayMode.QA
        )

        assertTrue(diagnostic.contains("0.1.1-alpha"))
        assertTrue(diagnostic.contains("debug", ignoreCase = true))
        assertTrue(diagnostic.contains("Asistente: modo seguro", ignoreCase = true))
        assertTrue(diagnostic.contains("GPT mini:", ignoreCase = true))
        assertFalse(diagnostic.contains("IA flexible", ignoreCase = true))
        assertFalse(diagnostic.contains("proxy", ignoreCase = true))
        assertTrue(diagnostic.contains("Micrófono: permiso OK", ignoreCase = true))
        assertTrue(diagnostic.contains("usando parcial", ignoreCase = true))
        assertTrue(diagnostic.contains("SPEECH_TIMEOUT", ignoreCase = true))
        assertTrue(diagnostic.contains("Motor de voz: on-device", ignoreCase = true))
        assertTrue(diagnostic.contains("Cámara: falta permiso", ignoreCase = true))
        assertTrue(diagnostic.contains("TTS: disponible", ignoreCase = true))
        assertTrue(diagnostic.contains("WhatsApp: no detectado", ignoreCase = true))
        assertTrue(diagnostic.contains("Última acción pendiente: COMPOSE_WHATSAPP_MESSAGE"))
        assertTrue(diagnostic.contains("Último error seguro: NO_SPEECH"))
        assertTrue(diagnostic.contains("Resumen seguro QA"))
        assertFalse(diagnostic.contains("OPENAI" + "_API" + "_KEY", ignoreCase = true))
        assertFalse(diagnostic.contains("sk" + "-", ignoreCase = true))
    }

    @Test
    fun defaultProductDisplayModeStartsInDemo() {
        assertEquals(ProductDisplayMode.DEMO, defaultProductDisplayMode())
    }

    @Test
    fun demoDiagnosticHidesTechnicalCopy() {
        val diagnostic = buildHomeDiagnosticText(
            versionName = "0.1.1-alpha",
            isDebug = true,
            assistantBaseUrlConfigured = true,
            microphoneGranted = true,
            cameraGranted = true,
            ttsAvailable = true,
            whatsappStatus = "disponible",
            pendingSummary = "COMPOSE_WHATSAPP_MESSAGE",
            lastError = "fallback handler intent slot proxy",
            voiceHearingStatus = "escuchando",
            voiceErrorCategory = "SPEECH_TIMEOUT",
            voiceSpeechEngine = "on-device",
            displayMode = ProductDisplayMode.DEMO
        )

        assertTrue(diagnostic.contains("Modo Demo"))
        assertTrue(diagnostic.contains("Sugerencia"))
        listOf("handler", "intent", "slot", "proxy", "fallback").forEach { forbidden ->
            assertFalse(diagnostic.contains(forbidden, ignoreCase = true), forbidden)
        }
    }

    @Test
    fun qaDiagnosticKeepsDebugInformation() {
        val diagnostic = buildHomeDiagnosticText(
            versionName = "0.1.1-alpha",
            isDebug = true,
            assistantBaseUrlConfigured = true,
            microphoneGranted = true,
            cameraGranted = true,
            ttsAvailable = true,
            whatsappStatus = "disponible",
            pendingSummary = "COMPOSE_WHATSAPP_MESSAGE",
            lastError = "SPEECH_TIMEOUT",
            voiceHearingStatus = "usando parcial",
            voiceErrorCategory = "SPEECH_TIMEOUT",
            voiceSpeechEngine = "on-device",
            displayMode = ProductDisplayMode.QA
        )

        assertTrue(diagnostic.contains("Resumen seguro QA"))
        assertTrue(diagnostic.contains("COMPOSE_WHATSAPP_MESSAGE"))
        assertTrue(diagnostic.contains("SPEECH_TIMEOUT"))
    }

    @Test
    fun productSuggestionsChangeByContext() {
        assertTrue(
            productUtilitySuggestionText(
                robotEnabled = true,
                accessibilityReady = true,
                waitingConfirmation = false,
                whatsappActive = false,
                voiceErrorCategory = "ninguno"
            ).contains("pantalla", ignoreCase = true)
        )
        assertTrue(
            productUtilitySuggestionText(
                robotEnabled = true,
                accessibilityReady = true,
                waitingConfirmation = false,
                whatsappActive = true,
                voiceErrorCategory = "ninguno"
            ).contains("chats", ignoreCase = true)
        )
        assertTrue(
            productUtilitySuggestionText(
                robotEnabled = true,
                accessibilityReady = true,
                waitingConfirmation = true,
                whatsappActive = false,
                voiceErrorCategory = "ninguno"
            ).contains("cancelar", ignoreCase = true)
        )
        assertTrue(
            productUtilitySuggestionText(
                robotEnabled = false,
                accessibilityReady = true,
                waitingConfirmation = false,
                whatsappActive = false,
                voiceErrorCategory = "ninguno"
            ).contains("encender robot", ignoreCase = true)
        )
        assertTrue(
            productUtilitySuggestionText(
                robotEnabled = true,
                accessibilityReady = false,
                waitingConfirmation = false,
                whatsappActive = false,
                voiceErrorCategory = "ninguno"
            ).contains("Accesibilidad", ignoreCase = true)
        )
        assertTrue(
            productUtilitySuggestionText(
                robotEnabled = true,
                accessibilityReady = true,
                waitingConfirmation = false,
                whatsappActive = false,
                voiceErrorCategory = "SPEECH_TIMEOUT"
            ).contains("repetir", ignoreCase = true)
        )
    }

    @Test
    fun speechFinishedResumeRequiresRobotVisibleAndSafeAppState() {
        assertTrue(
            shouldResumeListeningAfterSpeech(
                robotEnabled = true,
                appVisible = true,
                appState = AppState.IDLE
            )
        )
        assertFalse(
            shouldResumeListeningAfterSpeech(
                robotEnabled = false,
                appVisible = true,
                appState = AppState.IDLE
            )
        )
        assertFalse(
            shouldResumeListeningAfterSpeech(
                robotEnabled = true,
                appVisible = false,
                appState = AppState.IDLE
            )
        )
        assertFalse(
            shouldResumeListeningAfterSpeech(
                robotEnabled = true,
                appVisible = true,
                appState = AppState.EXTERNAL_APP_HANDOFF
            )
        )
    }

    @Test
    fun diagnosticSanitizesSecretLikeValues() {
        val unsafe = "api_key=abc123 token " + "sk" + "-secret"
        val safe = sanitizeDiagnosticValue(unsafe)

        assertFalse(safe.contains("abc123"))
        assertFalse(safe.contains("sk" + "-"))
        assertTrue(safe.contains("oculta", ignoreCase = true))
    }

    @Test
    fun firstUseGuideIsShortAndActionable() {
        assertTrue(FIRST_USE_GUIDE_TEXT.contains("Estela", ignoreCase = true))
        assertTrue(FIRST_USE_GUIDE_TEXT.contains("lea la pantalla", ignoreCase = true))
        assertTrue(FIRST_USE_GUIDE_TEXT.contains("describa el entorno", ignoreCase = true))
        assertTrue(FIRST_USE_GUIDE_TEXT.contains("guíe paso a paso", ignoreCase = true))
        assertTrue(FIRST_USE_GUIDE_TEXT.length < 120)
    }

    @Test
    fun robotStatusBlockUsesHumanSessionLabels() {
        val text = robotStatusBlockText(
            appState = AppState.IDLE,
            agentState = null,
            pendingSummary = "",
            loading = false,
            micListening = false,
            ttsSpeaking = false,
            robotSessionState = com.ojoclaro.android.model.RobotSessionState.SPEAKING
        )

        assertEquals("Estado: Estoy respondiendo", text)
    }

    @Test
    fun contextualMandaseloUsesLastSessionMessageSafely() {
        val pending = buildContextualWhatsAppPendingFromSession(
            text = "mandáselo a ContactoDemo mejor",
            snapshot = AgentSessionSnapshot(
                lastContactName = "Marco",
                lastProposedMessage = "Estoy llegando un poco tarde."
            ),
            nowMillis = 1_000L
        )

        assertNotNull(pending)
        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, pending.command.type)
        assertEquals("ContactoDemo", pending.command.contactName)
        assertEquals("Estoy llegando un poco tarde.", pending.command.messageText)
        assertTrue(pending.spokenText.contains("decí: confirmar"))
    }

    @Test
    fun contextualMandaseloWithoutContextAsksForClarification() {
        val pending = buildContextualWhatsAppPendingFromSession(
            text = "mandáselo a ContactoDemo mejor",
            snapshot = AgentSessionSnapshot(),
            nowMillis = 1_000L
        )

        assertTrue(pending == null)
    }

    @Test
    fun handoffSpeechDelayIsBounded() {
        assertTrue(handoffSpeechDelayMillis("corto") >= 1_200L)
        assertTrue(handoffSpeechDelayMillis("x".repeat(500)) <= 4_500L)
    }
}
