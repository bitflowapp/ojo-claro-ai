package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SituationDecisionTest {

    @Test
    fun ask_confirmation_tiene_next_state_waiting_confirmation_por_defecto() {
        val decision = SituationDecision.AskConfirmation(
            prompt = "Para continuar, decí: confirmar.",
            pendingAction = PendingAction(
                label = "preparar un mensaje",
                intentName = SituationIntent.WRITE_MESSAGE.name,
                riskLevel = AgentRiskLevel.MEDIUM,
                confirmationPrompt = "Para continuar, decí: confirmar.",
                expiresAt = 1L
            )
        )
        assertEquals(SituationState.WAITING_CONFIRMATION, decision.nextState)
    }

    @Test
    fun speak_tiene_next_state_speaking_por_defecto() {
        val decision = SituationDecision.Speak(message = "Hola")
        assertEquals(SituationState.SPEAKING, decision.nextState)
    }

    @Test
    fun reject_tiene_next_state_error_recovery_por_defecto() {
        val decision = SituationDecision.Reject(
            reason = "no seguro",
            riskLevel = AgentRiskLevel.BLOCKED
        )
        assertEquals(SituationState.ERROR_RECOVERY, decision.nextState)
    }

    @Test
    fun cancel_es_hard_y_va_a_cancelled_por_defecto() {
        val decision = SituationDecision.Cancel()
        assertTrue(decision.hard)
        assertEquals(SituationState.CANCELLED, decision.nextState)
        assertEquals("Cancelado", decision.reason)
    }

    @Test
    fun ignore_es_singleton() {
        assertEquals(SituationDecision.Ignore, SituationDecision.Ignore)
    }
}
