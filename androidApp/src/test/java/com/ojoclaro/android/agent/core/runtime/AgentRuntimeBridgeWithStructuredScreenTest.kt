package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.core.AgentContext
import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.screen.ScreenSignals
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class AgentRuntimeBridgeWithStructuredScreenTest {

    private val now = 1_700_000_000_000L

    private fun bridge() = AgentRuntimeBridge(
        flags = { AgentCoreFeatureFlags(typedConfirmationEnabled = true) },
        clock = { now }
    )

    private fun ctxFromStructured(structured: StructuredScreenSnapshot?, command: String = "") =
        AgentContext.buildFromStructured(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            structured = structured,
            commandRawText = command
        )

    @Test
    fun `bridge rejects read screen when structured snapshot is banking`() {
        val structured = StructuredScreenSnapshot(
            packageName = "ar.com.galicia",
            appLabel = "Banco Galicia",
            capturedAtMillis = now,
            redactedTextLines = listOf("Saldo disponible"),
            buttons = listOf("Transferir"),
            editableFields = emptyList(),
            focusedLabel = "Saldo disponible",
            totalNodes = 5,
            signals = ScreenSignals(isBankingApp = true, hasPaymentOrTransferSignals = true),
            warnings = emptyList(),
            isLimited = false
        )
        val context = ctxFromStructured(structured)

        val outcome = bridge().submit("leeme la pantalla", context)

        val rejected = outcome as? BridgeOutcome.Rejected
            ?: fail("expected Rejected, got $outcome")
        assertEquals("screen_hot_zone", rejected.reason)
    }

    @Test
    fun `bridge processes compose normally on safe messaging screen`() {
        val structured = StructuredScreenSnapshot(
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            capturedAtMillis = now,
            redactedTextLines = listOf("Sofi"),
            buttons = emptyList(),
            editableFields = listOf("Escribe un mensaje"),
            focusedLabel = "Sofi",
            totalNodes = 4,
            signals = ScreenSignals(isMessagingApp = true),
            warnings = emptyList(),
            isLimited = false
        )
        val context = ctxFromStructured(structured)

        val outcome = bridge().submit("mandale a sofi que estoy llegando", context)

        assertTrue(outcome is BridgeOutcome.Pending, "got $outcome")
    }

    @Test
    fun `bridge with null structured does not crash`() {
        val context = ctxFromStructured(null)
        val outcome = bridge().submit("repeti", context)
        // REPEAT_LAST → Ready (NO sensible, NO requires confirmation)
        assertTrue(outcome is BridgeOutcome.Ready, "got $outcome")
    }
}
