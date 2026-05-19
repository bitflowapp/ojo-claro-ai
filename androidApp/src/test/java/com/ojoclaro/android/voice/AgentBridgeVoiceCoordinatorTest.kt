package com.ojoclaro.android.voice

import com.ojoclaro.android.agent.core.runtime.BridgeDispatchKind
import com.ojoclaro.android.agent.core.runtime.BridgeDispatchOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentBridgeVoiceCoordinatorTest {

    private var now: Long = 1_000L
    private val controller = VoiceFeedbackController(clock = { now })
    private val coordinator = AgentBridgeVoiceCoordinator(controller = controller)

    @Test
    fun pendingIsSpokenOnFirstEmit() {
        val route = coordinator.route(pendingOutcome())

        val speak = assertSpeak(route)
        assertEquals(
            "Esta acción requiere confirmación. Decime confirmar o cancelar.",
            speak.text
        )
    }

    @Test
    fun twoConsecutivePendingsAreDeduplicated() {
        coordinator.route(pendingOutcome())
        now += 250L

        val second = coordinator.route(pendingOutcome())

        val suppress = assertSuppress(second)
        assertTrue(
            suppress.reason.contains("duplicate"),
            "Expected duplicate reason, got '${suppress.reason}'"
        )
    }

    @Test
    fun noPendingDoesNotRepeatLikeParrotWithinDedupWindow() {
        val first = coordinator.route(noPendingOutcome())
        now += 100L
        val second = coordinator.route(noPendingOutcome())

        assertSpeak(first)
        assertSuppress(second)
    }

    @Test
    fun rejectedIsForcedAndSpeaksEvenAfterRecentSameKey() {
        coordinator.route(rejectedOutcome("No puedo hacer eso por seguridad."))
        now += 100L

        val route = coordinator.route(rejectedOutcome("No puedo hacer eso por seguridad."))

        val speak = assertSpeak(route)
        assertTrue(speak.force, "Rejected should be force-spoken")
    }

    @Test
    fun expiredIsForceSpoken() {
        val route = coordinator.route(
            BridgeDispatchOutcome.Handled(
                speakText = "La confirmación venció. Volvé a pedir la acción.",
                pendingPrompt = null,
                hasPending = false,
                kind = BridgeDispatchKind.EXPIRED
            )
        )

        val speak = assertSpeak(route)
        assertTrue(speak.force)
    }

    @Test
    fun fallbackToLegacyReturnsPassThroughAndDoesNotConsultController() {
        val previousLastSpoken = mutableListOf<String>()
        val recordingController = VoiceFeedbackController(
            speakNow = { previousLastSpoken += it },
            clock = { now }
        )
        val isolatedCoordinator = AgentBridgeVoiceCoordinator(controller = recordingController)

        val route = isolatedCoordinator.route(
            BridgeDispatchOutcome.FallbackToLegacy(reason = "typed_confirmation_disabled")
        )

        assertEquals(BridgeVoiceRoute.PassThrough, route)
        assertEquals(emptyList(), previousLastSpoken)
    }

    @Test
    fun resetMemoryClearsDedupState() {
        coordinator.route(pendingOutcome())
        coordinator.resetMemory()

        // After reset, the previous PENDING should not block a fresh PENDING in
        // the same dedup window.
        val route = coordinator.route(pendingOutcome())

        assertSpeak(route)
    }

    @Test
    fun confirmedFollowedByRepeatedConfirmedIsDeduplicated() {
        val first = coordinator.route(confirmedOutcome())
        now += 200L
        val second = coordinator.route(confirmedOutcome())

        assertSpeak(first)
        assertSuppress(second)
    }

    private fun pendingOutcome(): BridgeDispatchOutcome.Handled = BridgeDispatchOutcome.Handled(
        speakText = "Esta acción requiere confirmación. Decime confirmar o cancelar.",
        pendingPrompt = "Vas a abrir WhatsApp. ¿Confirmás?",
        hasPending = true,
        kind = BridgeDispatchKind.PENDING
    )

    private fun noPendingOutcome(): BridgeDispatchOutcome.Handled = BridgeDispatchOutcome.Handled(
        speakText = "No hay ninguna acción pendiente para confirmar.",
        pendingPrompt = null,
        hasPending = false,
        kind = BridgeDispatchKind.NO_PENDING
    )

    private fun rejectedOutcome(text: String): BridgeDispatchOutcome.Handled =
        BridgeDispatchOutcome.Handled(
            speakText = text,
            pendingPrompt = null,
            hasPending = false,
            kind = BridgeDispatchKind.REJECTED
        )

    private fun confirmedOutcome(): BridgeDispatchOutcome.Handled =
        BridgeDispatchOutcome.Handled(
            speakText = "Confirmado. La acción quedó autorizada.",
            pendingPrompt = null,
            hasPending = false,
            kind = BridgeDispatchKind.CONFIRMED
        )

    private fun assertSpeak(route: BridgeVoiceRoute): BridgeVoiceRoute.Speak {
        val speak = route as? BridgeVoiceRoute.Speak
        assertTrue(speak != null, "Expected Speak, got $route")
        return speak
    }

    private fun assertSuppress(route: BridgeVoiceRoute): BridgeVoiceRoute.Suppress {
        val suppress = route as? BridgeVoiceRoute.Suppress
        assertTrue(suppress != null, "Expected Suppress, got $route")
        return suppress
    }
}
