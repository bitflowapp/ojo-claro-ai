package com.ojoclaro.android.ui.home

import com.ojoclaro.android.external.CommandRouter
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.model.AppState
import kotlin.test.Test
import kotlin.test.assertFalse
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
    fun handoffSpeechDelayIsBounded() {
        assertTrue(handoffSpeechDelayMillis("corto") >= 1_200L)
        assertTrue(handoffSpeechDelayMillis("x".repeat(500)) <= 4_500L)
    }
}
