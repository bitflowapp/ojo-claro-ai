package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.core.AgentContext
import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.AgentRiskLevel
import com.ojoclaro.android.agent.core.AgentScreenContext
import com.ojoclaro.android.agent.core.AgentToolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AgentRuntimeBridgeTest {

    private val now = 1_700_000_000_000L

    private fun bridge(
        flags: AgentCoreFeatureFlags = AgentCoreFeatureFlags(typedConfirmationEnabled = true)
    ): AgentRuntimeBridge = AgentRuntimeBridge(
        flags = { flags },
        clock = { now }
    )

    private fun ctx(screen: AgentScreenContext? = null, command: String = "") =
        AgentContext.build(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            screen = screen,
            commandRawText = command
        )

    @Test
    fun `flag off returns Skipped without touching state`() {
        val bridge = bridge(flags = AgentCoreFeatureFlags.DISABLED)

        val outcome = bridge.submit("mandale a mama estoy llegando", ctx())

        assertTrue(outcome is BridgeOutcome.Skipped)
        assertEquals("typed_confirmation_disabled", (outcome as BridgeOutcome.Skipped).reason)
        assertNull(bridge.currentPending())
        assertEquals(BridgeUiState.Idle, bridge.uiState())
    }

    @Test
    fun `blank input returns Skipped blank_input`() {
        val outcome = bridge().submit("   ", ctx())

        val skipped = outcome as? BridgeOutcome.Skipped ?: fail("expected Skipped, got $outcome")
        assertEquals("blank_input", skipped.reason)
    }

    @Test
    fun `compose without contact returns NeedsSlot for contactName`() {
        val outcome = bridge().submit("mandar mensaje", ctx())

        val needsSlot = outcome as? BridgeOutcome.NeedsSlot
            ?: fail("expected NeedsSlot, got $outcome")
        assertEquals(AgentToolId.WHATSAPP, needsSlot.toolId)
        assertEquals("contactName", needsSlot.slot)
        assertTrue(needsSlot.spokenPrompt.isNotBlank())
    }

    @Test
    fun `compose with full slots returns Pending and registers it`() {
        val bridge = bridge()

        val outcome = bridge.submit("mandale a sofi que estoy llegando", ctx())

        val pending = outcome as? BridgeOutcome.Pending
            ?: fail("expected Pending, got $outcome")
        assertEquals(AgentToolId.WHATSAPP, pending.pending.action.toolId)
        assertNotNull(bridge.currentPending())
        val ui = bridge.uiState()
        assertTrue(ui is BridgeUiState.AwaitingConfirmation)
        assertEquals(AgentToolId.WHATSAPP, (ui as BridgeUiState.AwaitingConfirmation).toolId)
    }

    @Test
    fun `confirm after pending returns Confirmed and clears pending`() {
        val bridge = bridge()
        bridge.submit("mandale a sofi que estoy llegando", ctx())
        assertNotNull(bridge.currentPending())

        val outcome = bridge.submit("confirmar", ctx())

        val confirmed = outcome as? BridgeOutcome.Confirmed
            ?: fail("expected Confirmed, got $outcome")
        assertEquals(AgentToolId.WHATSAPP, confirmed.action.toolId)
        assertNull(bridge.currentPending())
        assertTrue(confirmed.spokenText.contains("Confirmado"))
        // NUNCA debe afirmar que el envío ya ocurrió.
        assertTrue(confirmed.spokenText.contains("No envío automáticamente"))
    }

    @Test
    fun `cancel after pending returns Cancelled and clears pending`() {
        val bridge = bridge()
        bridge.submit("mandale a sofi que estoy llegando", ctx())
        assertNotNull(bridge.currentPending())

        val outcome = bridge.submit("cancelar", ctx())

        assertTrue(outcome is BridgeOutcome.Cancelled)
        assertNull(bridge.currentPending())
    }

    @Test
    fun `confirm without pending returns NoPending`() {
        val outcome = bridge().submit("confirmar", ctx())

        val no = outcome as? BridgeOutcome.NoPending ?: fail("expected NoPending, got $outcome")
        assertTrue(no.spokenText.isNotBlank())
    }

    @Test
    fun `cancel without pending returns NoPending`() {
        val outcome = bridge().submit("cancelar", ctx())

        assertTrue(outcome is BridgeOutcome.NoPending)
    }

    @Test
    fun `dangerous verb in compose elevates risk to HIGH and stays Pending`() {
        val bridge = bridge()

        val outcome = bridge.submit("mandale a juan que transferi 5000 pesos", ctx())

        val pending = outcome as? BridgeOutcome.Pending
            ?: fail("expected Pending, got $outcome")
        assertEquals(AgentRiskLevel.HIGH, pending.pending.action.risk)
        // El prompt debe mencionar el verbo peligroso para que el usuario lo
        // oiga antes de confirmar.
        val prompt = pending.pending.action.confirmationPrompt.orEmpty()
        assertTrue(prompt.contains("transferi", ignoreCase = true), "prompt was: $prompt")
    }

    @Test
    fun `enviá ya without prior context is not auto-executed and falls through as unknown`() {
        val outcome = bridge().submit("envia ya este mensaje", ctx())

        // Sin un compose previo este fragmento no es un intent reconocible.
        // El sistema NO debe inferir un envío. Aceptamos cualquier outcome que
        // NO sea Ready/Confirmed.
        val notExecutable = outcome !is BridgeOutcome.Ready &&
            outcome !is BridgeOutcome.Confirmed
        assertTrue(notExecutable, "must not auto-execute, got $outcome")
    }

    @Test
    fun `screen hot zone rejects read visible screen`() {
        val screen = AgentScreenContext(
            packageName = "ar.com.galicia",
            isBankingScreen = true,
            isSensitive = true
        )

        val outcome = bridge().submit("leeme la pantalla", ctx(screen = screen))

        val rejected = outcome as? BridgeOutcome.Rejected
            ?: fail("expected Rejected, got $outcome")
        assertEquals("screen_hot_zone", rejected.reason)
    }

    @Test
    fun `unknown intent returns Rejected unknown_intent`() {
        val outcome = bridge().submit("xyz inintelegible foo bar", ctx())

        val rejected = outcome as? BridgeOutcome.Rejected
            ?: fail("expected Rejected, got $outcome")
        assertEquals("unknown_intent", rejected.reason)
    }

    @Test
    fun `new command after pending abandons previous and processes new one`() {
        val bridge = bridge()
        bridge.submit("mandale a sofi que estoy llegando", ctx())
        val firstPending = bridge.currentPending()
        assertNotNull(firstPending)

        // Llega otro compose con destinatario distinto: el bridge debe descartar
        // el primer pending y registrar el nuevo.
        val outcome = bridge.submit("mandale a maria que ya salgo", ctx())

        val abandoned = outcome as? BridgeOutcome.PreviousAbandoned
            ?: fail("expected PreviousAbandoned, got $outcome")
        val newPending = abandoned.replacement as? BridgeOutcome.Pending
            ?: fail("expected replacement Pending, got ${abandoned.replacement}")
        assertEquals(AgentToolId.WHATSAPP, newPending.pending.action.toolId)
        assertEquals("maria", newPending.pending.action.slots["contactName"])
        // El pending vivo es el nuevo, el viejo se fue.
        val current = bridge.currentPending()
        assertNotNull(current)
        assertEquals(newPending.pending.id, current.id)
    }

    @Test
    fun `reset clears pending without speaking or executing`() {
        val bridge = bridge()
        bridge.submit("mandale a sofi que estoy llegando", ctx())
        assertNotNull(bridge.currentPending())

        bridge.reset()

        assertNull(bridge.currentPending())
        assertEquals(BridgeUiState.Idle, bridge.uiState())
    }

    @Test
    fun `pending expired clears safely when consumed`() {
        // TTL del ConsentManager es 2 minutos. Adelantamos el reloj.
        val later = now + 3 * 60 * 1000L
        val movingClock = object {
            var current = now
        }
        val bridge = AgentRuntimeBridge(
            flags = { AgentCoreFeatureFlags(typedConfirmationEnabled = true) },
            clock = { movingClock.current }
        )

        bridge.submit("mandale a sofi que estoy llegando", ctx())
        assertNotNull(bridge.currentPending())

        movingClock.current = later
        val outcome = bridge.submit("confirmar", ctx())

        assertTrue(outcome is BridgeOutcome.Expired, "got $outcome")
        assertNull(bridge.currentPending())
    }

    @Test
    fun `feature flag off does not register any pending`() {
        val bridge = bridge(flags = AgentCoreFeatureFlags.DISABLED)

        bridge.submit("mandale a sofi que estoy llegando", ctx())

        assertNull(bridge.currentPending())
        assertFalse(bridge.uiState() is BridgeUiState.AwaitingConfirmation)
    }

    @Test
    fun `compose with credit card content is rejected before pending`() {
        val outcome = bridge().submit(
            "mandale a juan que mi tarjeta es 4111 1111 1111 1111",
            ctx()
        )

        val rejected = outcome as? BridgeOutcome.Rejected
            ?: fail("expected Rejected, got $outcome")
        // SafetyPolicy o el evaluator lo bloquean con variante message_payload_unsafe.
        assertTrue(
            rejected.reason.contains("message_payload_unsafe", ignoreCase = true),
            "reason was: ${rejected.reason}"
        )
        // Y nunca se registra como pending.
        assertNull(bridge().currentPending())
    }
}
