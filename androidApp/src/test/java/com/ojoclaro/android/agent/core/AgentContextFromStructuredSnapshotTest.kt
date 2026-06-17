package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.core.screen.ScreenSignals
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.risk.RiskType
import com.ojoclaro.android.risk.RiskWarning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentContextFromStructuredSnapshotTest {

    private val now = 1_700_000_000_000L

    private fun bankingStructured() = StructuredScreenSnapshot(
        packageName = "ar.com.galicia",
        appLabel = "Banco Galicia",
        capturedAtMillis = now,
        redactedTextLines = listOf("Bienvenido a Galicia"),
        buttons = listOf("Transferir", "Saldo"),
        editableFields = emptyList(),
        focusedLabel = "Bienvenido a Galicia",
        totalNodes = 8,
        signals = ScreenSignals(
            isBankingApp = true,
            hasPaymentOrTransferSignals = true,
            hasScrollableContent = false
        ),
        warnings = listOf(
            RiskWarning(
                type = RiskType.BANKING_SCREEN,
                spokenText = "banco",
                severity = 3,
                requiresConfirmation = true
            )
        ),
        isLimited = false
    )

    @Test
    fun `null structured snapshot returns context without screen`() {
        val snap = AgentContext.buildFromStructured(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            structured = null
        )

        assertNull(snap.screen)
        assertTrue(snap.screenRiskWarnings.isEmpty())
    }

    @Test
    fun `structured snapshot maps to AgentScreenContext with derived flags`() {
        val snap = AgentContext.buildFromStructured(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            structured = bankingStructured()
        )

        val screen = snap.screen
        assertTrue(screen != null)
        assertEquals("ar.com.galicia", screen.packageName)
        assertTrue(screen.isBankingScreen)
        assertTrue(screen.isSensitive)
        assertTrue(screen.shouldBlockGeneralActions)
        assertFalse(screen.containsPasswordField)
        assertEquals("Bienvenido a Galicia", screen.shortSummary)
    }

    @Test
    fun `warnings from structured are forwarded to snapshot`() {
        val snap = AgentContext.buildFromStructured(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            structured = bankingStructured()
        )

        assertTrue(snap.screenRiskWarnings.any { it.type == RiskType.BANKING_SCREEN })
    }

    @Test
    fun `command raw text still produces command warnings independently`() {
        val snap = AgentContext.buildFromStructured(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            structured = null,
            commandRawText = "transferi mil pesos"
        )

        assertTrue(snap.commandRiskWarnings.any { it.type == RiskType.MONEY_REQUEST })
    }

    @Test
    fun `toAgentScreenContext handles missing focused label by using first redacted line`() {
        val structured = StructuredScreenSnapshot(
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            capturedAtMillis = now,
            redactedTextLines = listOf("Sofi", "Hola"),
            buttons = emptyList(),
            editableFields = emptyList(),
            focusedLabel = null,
            totalNodes = 2,
            signals = ScreenSignals(isMessagingApp = true),
            warnings = emptyList(),
            isLimited = false
        )

        val agentScreen = AgentContext.toAgentScreenContext(structured)
        assertEquals("Sofi", agentScreen.shortSummary)
        assertFalse(agentScreen.isBankingScreen)
        assertFalse(agentScreen.containsPasswordField)
        assertFalse(agentScreen.isSensitive)
    }
}
