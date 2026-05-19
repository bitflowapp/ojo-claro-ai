package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.risk.RiskType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentContextTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `empty inputs produce empty warning lists`() {
        val snapshot = AgentContext.build(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now
        )

        assertTrue(snapshot.screenRiskWarnings.isEmpty())
        assertTrue(snapshot.commandRiskWarnings.isEmpty())
        assertFalse(snapshot.hasAnyRiskWarning)
    }

    @Test
    fun `banking package name derives banking screen flag`() {
        val screen = AgentScreenContext(
            packageName = "ar.com.galicia",
            shortSummary = "Pantalla principal de la app"
        )

        val snapshot = AgentContext.build(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            screen = screen
        )

        assertTrue(snapshot.hasAnyRiskWarning)
        val effectiveScreen = snapshot.screen
        assertTrue(effectiveScreen != null && effectiveScreen.isBankingScreen)
        assertTrue(effectiveScreen.isSensitive)
        assertTrue(snapshot.screenRiskWarnings.any { it.type == RiskType.BANKING_SCREEN })
    }

    @Test
    fun `screen summary with verification code derives flag and warning`() {
        val screen = AgentScreenContext(
            packageName = "com.whatsapp",
            shortSummary = "Tu codigo de verificacion es 123456"
        )

        val snapshot = AgentContext.build(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            screen = screen
        )

        assertTrue(snapshot.screen?.containsVerificationCode == true)
        assertTrue(snapshot.screenRiskWarnings.any { it.type == RiskType.VERIFICATION_CODE })
    }

    @Test
    fun `command with money word produces command warnings`() {
        val snapshot = AgentContext.build(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            commandRawText = "transferi 5000 pesos a juan"
        )

        assertTrue(snapshot.commandRiskWarnings.any { it.type == RiskType.MONEY_REQUEST })
    }

    @Test
    fun `warnings are sorted by severity descending`() {
        val screen = AgentScreenContext(
            packageName = "com.whatsapp",
            // Combina verification code (severity 3) y money request (severity 2)
            shortSummary = "Tu codigo de verificacion es 123456 y transferi plata"
        )

        val snapshot = AgentContext.build(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            screen = screen
        )

        val warnings = snapshot.screenRiskWarnings
        assertTrue(warnings.isNotEmpty())
        // El primero debe tener severity >= el resto.
        val first = warnings.first()
        assertTrue(warnings.all { it.severity <= first.severity })
        assertEquals(3, first.severity)
    }

    @Test
    fun `caller flags are preserved and never downgraded`() {
        val screen = AgentScreenContext(
            packageName = "com.whatsapp",
            isSensitive = true,
            containsPasswordField = true
        )

        val snapshot = AgentContext.build(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            screen = screen
        )

        assertTrue(snapshot.screen?.isSensitive == true)
        assertTrue(snapshot.screen?.containsPasswordField == true)
    }
}
