package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.core.runtime.BridgeDispatchKind
import com.ojoclaro.android.agent.core.runtime.BridgeDispatchOutcome
import com.ojoclaro.android.voice.AgentBridgeVoiceCoordinator
import com.ojoclaro.android.voice.VoiceFeedbackController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeViewModelBridgeVoiceRoutingTest {

    private var now: Long = 1_000L
    private val controller = VoiceFeedbackController(clock = { now })
    private val coordinator = AgentBridgeVoiceCoordinator(controller = controller)

    @Test
    fun withoutCoordinatorEmitsLegacySpeakText() {
        val outcome = handled(
            kind = BridgeDispatchKind.CONFIRMED,
            speakText = "Confirmado. La acción quedó autorizada."
        )

        val decision = resolveAgentBridgeSpeech(
            outcome = outcome,
            coordinator = null,
            legacyForceSpeak = false
        )

        val emit = decision as? AgentBridgeSpeechDecision.Emit
        assertTrue(emit != null, "Expected Emit, got $decision")
        assertEquals("Confirmado. La acción quedó autorizada.", emit.text)
        assertEquals(false, emit.force)
    }

    @Test
    fun coordinatorSuppressesDuplicateWhileUiPatchStillCarriesText() {
        val outcome = handled(
            kind = BridgeDispatchKind.PENDING,
            speakText = "Esta acción requiere confirmación. Decime confirmar o cancelar.",
            pendingPrompt = "Vas a abrir WhatsApp. ¿Confirmás?",
            hasPending = true
        )

        val first = resolveAgentBridgeSpeech(outcome, coordinator, legacyForceSpeak = false)
        now += 200L
        val second = resolveAgentBridgeSpeech(outcome, coordinator, legacyForceSpeak = false)

        assertTrue(first is AgentBridgeSpeechDecision.Emit)
        assertTrue(
            second is AgentBridgeSpeechDecision.Skip,
            "Expected Skip on duplicate PENDING, got $second"
        )

        // UI must still reflect the latest outcome even when speech is skipped.
        // The HomeViewModel applies _state.update {} BEFORE consulting this
        // helper, so we just assert the outcome data is intact for the UI.
        assertEquals(
            "Esta acción requiere confirmación. Decime confirmar o cancelar.",
            outcome.speakText
        )
        assertEquals("Vas a abrir WhatsApp. ¿Confirmás?", outcome.pendingPrompt)
        assertEquals(true, outcome.hasPending)
    }

    @Test
    fun rejectedKeepsForceTrueEvenIfLegacyForceFalse() {
        val outcome = handled(
            kind = BridgeDispatchKind.REJECTED,
            speakText = "No puedo hacer eso por seguridad."
        )

        val decision = resolveAgentBridgeSpeech(
            outcome = outcome,
            coordinator = coordinator,
            legacyForceSpeak = false
        )

        val emit = decision as? AgentBridgeSpeechDecision.Emit
        assertTrue(emit != null, "Expected Emit, got $decision")
        assertTrue(emit.force, "Rejected must be force-spoken")
    }

    @Test
    fun legacyForceTrueIsHonoredEvenIfRouteWouldNotForce() {
        val outcome = handled(
            kind = BridgeDispatchKind.CONFIRMED,
            speakText = "Confirmado. La acción quedó autorizada."
        )

        val decision = resolveAgentBridgeSpeech(
            outcome = outcome,
            coordinator = coordinator,
            legacyForceSpeak = true
        )

        val emit = decision as? AgentBridgeSpeechDecision.Emit
        assertTrue(emit != null)
        assertTrue(emit.force)
    }

    @Test
    fun coordinatorApprovedSpeakBypassesSpeechControllerDedup() {
        // Paquete 5C: cuando el coordinator semántico aprueba un Speak, el
        // SpeechEvent debe salir con force=true para que el dedup literal del
        // SpeechController (ventana 5s) no silencie un mensaje cuya capa
        // semántica ya confirmó como fresh.
        val outcome = handled(
            kind = BridgeDispatchKind.CONFIRMED,
            speakText = "Confirmado. La acción quedó autorizada."
        )

        val decision = resolveAgentBridgeSpeech(
            outcome = outcome,
            coordinator = coordinator,
            legacyForceSpeak = false
        )

        val emit = decision as? AgentBridgeSpeechDecision.Emit
        assertTrue(emit != null)
        assertTrue(emit.force, "Coordinator-approved Speak must force=true to bypass SpeechController dedup")
    }

    @Test
    fun coordinatorSuppressIsSkipRegardlessOfLegacyForce() {
        // Suppress wins even si legacyForceSpeak=true: la UI ya quedó
        // actualizada antes (en applyAgentBridgeOutcome) y el dedup semántico
        // mandó no hablar. Forzar acá rompería la decisión semántica.
        val outcome = handled(
            kind = BridgeDispatchKind.PENDING,
            speakText = "Esta acción requiere confirmación. Decime confirmar o cancelar.",
            pendingPrompt = "Vas a abrir WhatsApp. ¿Confirmás?",
            hasPending = true
        )
        resolveAgentBridgeSpeech(outcome, coordinator, legacyForceSpeak = false)
        now += 200L

        val second = resolveAgentBridgeSpeech(outcome, coordinator, legacyForceSpeak = true)

        assertTrue(
            second is AgentBridgeSpeechDecision.Skip,
            "Even legacyForceSpeak should not override semantic Suppress, got $second"
        )
    }

    @Test
    fun fallbackToLegacyOutcomeNeverReachesThisHelper() {
        // Defensive contract: resolveAgentBridgeSpeech is only called with
        // Handled outcomes (HomeViewModel checks `is Handled` first). We still
        // assert that a coordinator returning PassThrough degrades safely if
        // wired against an alternative mapper.
        val outcome = handled(
            kind = BridgeDispatchKind.NO_PENDING,
            speakText = "No hay ninguna acción pendiente para confirmar."
        )
        val passThroughCoordinator = AgentBridgeVoiceCoordinator(
            controller = controller,
            mapper = { null }
        )

        val decision = resolveAgentBridgeSpeech(
            outcome = outcome,
            coordinator = passThroughCoordinator,
            legacyForceSpeak = true
        )

        val emit = decision as? AgentBridgeSpeechDecision.Emit
        assertTrue(emit != null, "PassThrough should emit legacy speakText")
        assertEquals("No hay ninguna acción pendiente para confirmar.", emit.text)
        assertTrue(emit.force)
    }

    private fun handled(
        kind: BridgeDispatchKind,
        speakText: String,
        pendingPrompt: String? = null,
        hasPending: Boolean = false
    ): BridgeDispatchOutcome.Handled = BridgeDispatchOutcome.Handled(
        speakText = speakText,
        pendingPrompt = pendingPrompt,
        hasPending = hasPending,
        kind = kind
    )
}
