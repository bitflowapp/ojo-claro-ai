package com.ojoclaro.android.ui.home

import com.ojoclaro.android.external.CommandRouter
import com.ojoclaro.android.external.ExternalCommandType
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.AgentSessionSnapshot
import com.ojoclaro.android.agent.LocalIntentParser
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
            text = "mandale un mensaje a Sofi que estoy llegando",
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
            returnHint = "Para seguir, volvé con el botón Ojo Claro.",
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
        val parsed = LocalIntentParser().parse("decile a Sofi que llego tarde pero decilo bien")

        assertTrue(
            shouldUsePersonalAgentForHumanMessageDraft(
                text = "decile a Sofi que llego tarde pero decilo bien",
                parsedIntent = parsed
            )
        )
    }

    @Test
    fun personalHumanMessageCreatesRealWhatsAppPending() {
        val decision = PersonalAgentDecision.ComposeHumanMessage(
            contactName = "Sofi",
            originalMessageText = "llego tarde",
            composition = MessageCompositionResult(
                proposedMessage = "Sofi, estoy llegando un poco tarde, pero ya estoy en camino. Te aviso apenas esté cerca.",
                spokenProposal = "Puedo preparar este mensaje para Sofi: 'Sofi, estoy llegando un poco tarde, pero ya estoy en camino. Te aviso apenas esté cerca.'. Para prepararlo en WhatsApp, decí: confirmar.",
                styleUsed = MessageStyle.WARM,
                requiresConfirmation = true,
                shouldSendAutomatically = false
            ),
            debugLabel = "LLM_COMPOSE"
        )

        val pending = buildWhatsAppComposePendingFromPersonalDecision(decision, nowMillis = 1_000L)

        assertNotNull(pending)
        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, pending.command.type)
        assertEquals("Sofi", pending.command.contactName)
        assertEquals("Sofi, estoy llegando un poco tarde, pero ya estoy en camino. Te aviso apenas esté cerca.", pending.command.messageText)
    }

    @Test
    fun personalHumanMessageDoesNotCreatePendingIfModelTriesAutoSend() {
        val decision = PersonalAgentDecision.ComposeHumanMessage(
            contactName = "Sofi",
            originalMessageText = "llego tarde",
            composition = MessageCompositionResult(
                proposedMessage = "Llego en unos minutos.",
                spokenProposal = "Puedo preparar este mensaje para Sofi: Llego en unos minutos. ¿Querés confirmarlo?",
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
        assertTrue(slowVoiceUnavailableText().contains("frases cortas", ignoreCase = true))
        assertEquals("Todavía no dije nada para repetir.", repeatedResponseText(""))
        assertEquals("Te escucho.", repeatedResponseText("  Te escucho.  "))
        assertTrue(isContextualMessageRetryCommand("mandáselo a Sofi mejor"))
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
            lastError = "NO_SPEECH"
        )

        assertTrue(diagnostic.contains("0.1.1-alpha"))
        assertTrue(diagnostic.contains("debug", ignoreCase = true))
        assertTrue(diagnostic.contains("IA flexible/proxy: no configurado", ignoreCase = true))
        assertTrue(diagnostic.contains("Micrófono: permiso OK", ignoreCase = true))
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
    fun diagnosticSanitizesSecretLikeValues() {
        val unsafe = "api_key=abc123 token " + "sk" + "-secret"
        val safe = sanitizeDiagnosticValue(unsafe)

        assertFalse(safe.contains("abc123"))
        assertFalse(safe.contains("sk" + "-"))
        assertTrue(safe.contains("oculta", ignoreCase = true))
    }

    @Test
    fun firstUseGuideIsShortAndActionable() {
        assertTrue(FIRST_USE_GUIDE_TEXT.contains("preparar mensajes", ignoreCase = true))
        assertTrue(FIRST_USE_GUIDE_TEXT.contains("confirmación", ignoreCase = true))
        assertTrue(FIRST_USE_GUIDE_TEXT.contains("alpha experimental", ignoreCase = true))
        assertTrue(FIRST_USE_GUIDE_TEXT.contains("qué podés hacer", ignoreCase = true))
        assertTrue(FIRST_USE_GUIDE_TEXT.length < 260)
    }

    @Test
    fun contextualMandaseloUsesLastSessionMessageSafely() {
        val pending = buildContextualWhatsAppPendingFromSession(
            text = "mandáselo a Sofi mejor",
            snapshot = AgentSessionSnapshot(
                lastContactName = "Marco",
                lastProposedMessage = "Estoy llegando un poco tarde."
            ),
            nowMillis = 1_000L
        )

        assertNotNull(pending)
        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, pending.command.type)
        assertEquals("Sofi", pending.command.contactName)
        assertEquals("Estoy llegando un poco tarde.", pending.command.messageText)
        assertTrue(pending.spokenText.contains("decí: confirmar"))
    }

    @Test
    fun contextualMandaseloWithoutContextAsksForClarification() {
        val pending = buildContextualWhatsAppPendingFromSession(
            text = "mandáselo a Sofi mejor",
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
