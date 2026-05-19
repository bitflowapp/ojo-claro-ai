package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.screen.ScreenContextRepository
import com.ojoclaro.android.agent.core.screen.ScreenSignals
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AgentBridgeDispatchControllerTest {

    private val now = 1_700_000_000_000L

    private fun makeController(
        typedConfirmationOn: Boolean = true,
        screenRuntimeOn: Boolean = true,
        initialSnapshot: StructuredScreenSnapshot? = null
    ): Pair<AgentBridgeDispatchController, ScreenContextRepository> {
        val flags = AgentCoreFeatureFlags(
            typedConfirmationEnabled = typedConfirmationOn,
            accessibilityRuntimeContextEnabled = screenRuntimeOn
        )
        val repo = ScreenContextRepository(flags = { flags })
        // Force initial snapshot publication if requested. We bypass the flag
        // gating by publishing first and relying on the test to verify behavior.
        if (initialSnapshot != null) {
            repo.publish(initialSnapshot)
        }
        val bridge = AgentRuntimeBridge(
            flags = { flags },
            clock = { now }
        )
        val controller = AgentBridgeDispatchController(
            bridge = bridge,
            screenRepository = repo,
            flags = { flags },
            clock = { now }
        )
        return controller to repo
    }

    private fun safeSnapshot() = StructuredScreenSnapshot(
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        capturedAtMillis = now,
        redactedTextLines = listOf("Sofi"),
        buttons = emptyList(),
        editableFields = emptyList(),
        focusedLabel = "Sofi",
        totalNodes = 1,
        signals = ScreenSignals(isMessagingApp = true),
        warnings = emptyList(),
        isLimited = false
    )

    private fun bankingSnapshot() = StructuredScreenSnapshot(
        packageName = "ar.com.galicia",
        appLabel = "Banco Galicia",
        capturedAtMillis = now,
        redactedTextLines = listOf("Saldo"),
        buttons = emptyList(),
        editableFields = emptyList(),
        focusedLabel = "Saldo",
        totalNodes = 1,
        signals = ScreenSignals(isBankingApp = true, hasPaymentOrTransferSignals = true),
        warnings = emptyList(),
        isLimited = false
    )

    @Test
    fun `flag off returns FallbackToLegacy with reason`() {
        val (controller, _) = makeController(typedConfirmationOn = false)

        val outcome = controller.dispatch("mandale a sofi que estoy llegando")

        val fallback = outcome as? BridgeDispatchOutcome.FallbackToLegacy
            ?: fail("expected FallbackToLegacy, got $outcome")
        assertEquals("typed_confirmation_disabled", fallback.reason)
    }

    @Test
    fun `blank input ultimately falls back to legacy`() {
        val (controller, _) = makeController()

        val outcome = controller.dispatch("   ")

        // Bridge devuelve Skipped(blank_input) → controller traduce a FallbackToLegacy.
        val fallback = outcome as? BridgeDispatchOutcome.FallbackToLegacy
            ?: fail("expected FallbackToLegacy, got $outcome")
        assertTrue(fallback.reason.contains("blank"))
    }

    @Test
    fun `compose with full slots produces Handled with PENDING and pendingPrompt`() {
        val (controller, _) = makeController()

        val outcome = controller.dispatch("mandale a sofi que estoy llegando")

        val handled = outcome as? BridgeDispatchOutcome.Handled
            ?: fail("expected Handled, got $outcome")
        assertEquals(BridgeDispatchKind.PENDING, handled.kind)
        assertTrue(handled.hasPending)
        assertTrue(!handled.pendingPrompt.isNullOrBlank())
        assertTrue(handled.speakText.isNotBlank())
    }

    @Test
    fun `confirm after pending returns Handled CONFIRMED with no further pending`() {
        val (controller, _) = makeController()
        controller.dispatch("mandale a sofi que estoy llegando")
        assertTrue(controller.currentUiState() is BridgeUiState.AwaitingConfirmation)

        val outcome = controller.dispatch("confirmar")

        val handled = outcome as? BridgeDispatchOutcome.Handled
            ?: fail("expected Handled, got $outcome")
        assertEquals(BridgeDispatchKind.CONFIRMED, handled.kind)
        assertTrue(!handled.hasPending)
        assertNull(handled.pendingPrompt)
        // No debe afirmar que el envío YA ocurrió.
        assertTrue(
            !handled.speakText.contains("enviado", ignoreCase = true),
            "speakText must not claim send happened, got: ${handled.speakText}"
        )
    }

    @Test
    fun `cancel after pending clears pending and returns CANCELLED`() {
        val (controller, _) = makeController()
        controller.dispatch("mandale a sofi que estoy llegando")
        assertTrue(controller.currentUiState() is BridgeUiState.AwaitingConfirmation)

        val outcome = controller.dispatch("cancelar")

        val handled = outcome as? BridgeDispatchOutcome.Handled
            ?: fail("expected Handled, got $outcome")
        assertEquals(BridgeDispatchKind.CANCELLED, handled.kind)
        assertEquals(BridgeUiState.Idle, controller.currentUiState())
    }

    @Test
    fun `rejected banking screen returns Handled REJECTED with reason in spoken text`() {
        val (controller, _) = makeController(initialSnapshot = bankingSnapshot())

        val outcome = controller.dispatch("leeme la pantalla")

        val handled = outcome as? BridgeDispatchOutcome.Handled
            ?: fail("expected Handled, got $outcome")
        assertEquals(BridgeDispatchKind.REJECTED, handled.kind)
        assertTrue(!handled.hasPending)
        assertTrue(handled.speakText.isNotBlank())
    }

    @Test
    fun `compose missing message returns NEEDS_SLOT and asks for it`() {
        val (controller, _) = makeController()

        val outcome = controller.dispatch("mandale a sofi")

        val handled = outcome as? BridgeDispatchOutcome.Handled
            ?: fail("expected Handled, got $outcome")
        assertEquals(BridgeDispatchKind.NEEDS_SLOT, handled.kind)
        assertTrue(!handled.pendingPrompt.isNullOrBlank())
        assertTrue(
            handled.speakText.contains("mensaje", ignoreCase = true) ||
                handled.speakText.contains("qué", ignoreCase = true),
            "speakText should prompt for the missing slot, got: ${handled.speakText}"
        )
    }

    @Test
    fun `structured snapshot from repository is forwarded to context`() {
        // Si el snapshot indica banca y el comando es "leeme la pantalla",
        // el bridge debe rechazar por hot zone. Eso es nuestra señal de que
        // el snapshot llegó al context.
        val (controller, repo) = makeController()
        repo.publish(bankingSnapshot())

        val outcome = controller.dispatch("leeme la pantalla")

        val handled = outcome as? BridgeDispatchOutcome.Handled
            ?: fail("expected Handled with rejection, got $outcome")
        assertEquals(BridgeDispatchKind.REJECTED, handled.kind)
    }

    @Test
    fun `null snapshot still allows bridge to run with empty context`() {
        val (controller, _) = makeController() // sin snapshot inicial

        // Repeat last: no requiere confirmación, no requiere snapshot.
        val outcome = controller.dispatch("repeti")

        val handled = outcome as? BridgeDispatchOutcome.Handled
            ?: fail("expected Handled (READY), got $outcome")
        assertEquals(BridgeDispatchKind.READY, handled.kind)
    }

    @Test
    fun `confirm without prior pending returns NO_PENDING`() {
        val (controller, _) = makeController()

        val outcome = controller.dispatch("confirmar")

        val handled = outcome as? BridgeDispatchOutcome.Handled
            ?: fail("expected Handled, got $outcome")
        assertEquals(BridgeDispatchKind.NO_PENDING, handled.kind)
        assertTrue(handled.speakText.isNotBlank())
    }

    @Test
    fun `reset clears pending without dispatching anything`() {
        val (controller, _) = makeController()
        controller.dispatch("mandale a sofi que estoy llegando")
        assertTrue(controller.currentUiState() is BridgeUiState.AwaitingConfirmation)

        controller.reset()

        assertEquals(BridgeUiState.Idle, controller.currentUiState())
    }

    @Test
    fun `dispatch survives without any snapshot in repository`() {
        // Caso explícito: repository limpio, flag de screen runtime OFF.
        // El controller debe seguir funcionando — la pantalla es opcional.
        val flags = AgentCoreFeatureFlags(
            typedConfirmationEnabled = true,
            accessibilityRuntimeContextEnabled = false
        )
        val repo = ScreenContextRepository(flags = { flags })
        val bridge = AgentRuntimeBridge(flags = { flags }, clock = { now })
        val controller = AgentBridgeDispatchController(
            bridge = bridge,
            screenRepository = repo,
            flags = { flags },
            clock = { now }
        )

        val outcome = controller.dispatch("repeti")

        val handled = outcome as? BridgeDispatchOutcome.Handled
            ?: fail("expected Handled, got $outcome")
        assertEquals(BridgeDispatchKind.READY, handled.kind)
    }
}
