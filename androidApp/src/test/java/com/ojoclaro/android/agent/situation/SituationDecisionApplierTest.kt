package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SituationDecisionApplierTest {

    private val applier = SituationDecisionApplier()

    private fun pendingAction(): PendingAction = PendingAction(
        label = "preparar un mensaje",
        intentName = SituationIntent.WRITE_MESSAGE.name,
        riskLevel = AgentRiskLevel.MEDIUM,
        confirmationPrompt = "Para continuar, decí: confirmar.",
        expiresAt = 1L
    )

    private fun goal(): ActiveGoal = ActiveGoal(
        description = "avisarle a Sofi",
        intent = SituationIntent.WRITE_MESSAGE,
        createdAt = 0L
    )

    @Test
    fun ignore_se_mapea_a_noop() {
        assertEquals(SituationUiEffect.NoOp, applier.toUiEffect(SituationDecision.Ignore))
    }

    @Test
    fun update_context_se_mapea_a_noop() {
        val ctx = SituationContextFactory().fromVoiceCommand(
            rawCommand = "x",
            currentStateName = "IDLE",
            currentAppPackage = null,
            activeRequestId = 0L,
            mutedThroughRequestId = 0L,
            lastAssistantMessage = ""
        )
        assertEquals(
            SituationUiEffect.NoOp,
            applier.toUiEffect(SituationDecision.UpdateContext(ctx))
        )
    }

    @Test
    fun speak_se_mapea_a_speak() {
        val effect = applier.toUiEffect(SituationDecision.Speak("hola"))
        assertTrue(effect is SituationUiEffect.Speak)
        assertEquals("hola", effect.message)
    }

    @Test
    fun cancel_se_mapea_a_cancel() {
        val effect = applier.toUiEffect(SituationDecision.Cancel(reason = "Cancelado", hard = true))
        assertTrue(effect is SituationUiEffect.Cancel)
        assertTrue(effect.hard)
        assertEquals("Cancelado", effect.reason)
    }

    @Test
    fun reject_se_mapea_a_reject() {
        val effect = applier.toUiEffect(
            SituationDecision.Reject(reason = "no seguro", riskLevel = AgentRiskLevel.BLOCKED)
        )
        assertTrue(effect is SituationUiEffect.Reject)
        assertEquals("no seguro", effect.reason)
    }

    @Test
    fun change_state_se_mapea_a_change_state() {
        val effect = applier.toUiEffect(SituationDecision.ChangeState(SituationState.LISTENING))
        assertTrue(effect is SituationUiEffect.ChangeState)
        assertEquals(SituationState.LISTENING, effect.state)
    }

    @Test
    fun ask_confirmation_se_mapea_a_ask_confirmation() {
        val pending = pendingAction()
        val effect = applier.toUiEffect(
            SituationDecision.AskConfirmation(prompt = "confirmá", pendingAction = pending)
        )
        assertTrue(effect is SituationUiEffect.AskConfirmation)
        assertEquals("confirmá", effect.prompt)
        assertEquals(pending, effect.pendingAction)
    }

    @Test
    fun execute_intent_read_screen_se_mapea_a_execute() {
        val effect = applier.toUiEffect(
            SituationDecision.ExecuteIntent(
                intent = SituationIntent.READ_SCREEN,
                reason = "lectura solicitada",
                nextState = SituationState.READING_SCREEN
            )
        )
        assertTrue(effect is SituationUiEffect.Execute)
        assertEquals(SituationIntent.READ_SCREEN, effect.intent)
        assertEquals("lectura solicitada", effect.reason)
        assertEquals(SituationState.READING_SCREEN, effect.nextState)
    }

    @Test
    fun execute_intent_summarize_screen_se_mapea_a_execute() {
        val effect = applier.toUiEffect(
            SituationDecision.ExecuteIntent(
                intent = SituationIntent.SUMMARIZE_SCREEN,
                reason = "x",
                nextState = SituationState.READING_SCREEN
            )
        )
        assertTrue(effect is SituationUiEffect.Execute)
        assertEquals(SituationIntent.SUMMARIZE_SCREEN, effect.intent)
    }

    @Test
    fun execute_intent_explain_what_i_see_se_mapea_a_execute() {
        val effect = applier.toUiEffect(
            SituationDecision.ExecuteIntent(
                intent = SituationIntent.EXPLAIN_WHAT_I_SEE,
                reason = "x",
                nextState = SituationState.READING_SCREEN
            )
        )
        assertTrue(effect is SituationUiEffect.Execute)
        assertEquals(SituationIntent.EXPLAIN_WHAT_I_SEE, effect.intent)
    }

    @Test
    fun execute_intent_open_app_se_mapea_a_execute() {
        val effect = applier.toUiEffect(
            SituationDecision.ExecuteIntent(
                intent = SituationIntent.OPEN_APP,
                reason = "x",
                nextState = SituationState.EXECUTING_GUIDED_ACTION
            )
        )
        assertTrue(effect is SituationUiEffect.Execute)
        assertEquals(SituationIntent.OPEN_APP, effect.intent)
    }

    @Test
    fun execute_intent_call_contact_se_mapea_a_execute() {
        val effect = applier.toUiEffect(
            SituationDecision.ExecuteIntent(
                intent = SituationIntent.CALL_CONTACT,
                reason = "x",
                nextState = SituationState.EXECUTING_GUIDED_ACTION
            )
        )
        assertTrue(effect is SituationUiEffect.Execute)
        assertEquals(SituationIntent.CALL_CONTACT, effect.intent)
    }

    @Test
    fun execute_intent_con_pending_action_se_mapea_a_execute_con_pending_action() {
        val pending = pendingAction()
        val effect = applier.toUiEffect(
            SituationDecision.ExecuteIntent(
                intent = SituationIntent.OPEN_APP,
                reason = "confirmado",
                nextState = SituationState.EXECUTING_GUIDED_ACTION,
                pendingAction = pending
            )
        )
        assertTrue(effect is SituationUiEffect.Execute)
        assertEquals(pending, effect.pendingAction)
    }

    @Test
    fun execute_intent_write_message_con_pending_action_se_mapea_a_execute_con_pending_action() {
        val pending = pendingAction().copy(
            target = "Sofi",
            payload = mapOf("contact" to "Sofi", "message" to "llego tarde")
        )
        val effect = applier.toUiEffect(
            SituationDecision.ExecuteIntent(
                intent = SituationIntent.WRITE_MESSAGE,
                reason = "confirmado",
                nextState = SituationState.EXECUTING_GUIDED_ACTION,
                pendingAction = pending
            )
        )
        assertTrue(effect is SituationUiEffect.Execute)
        assertEquals(SituationIntent.WRITE_MESSAGE, effect.intent)
        assertEquals(pending, effect.pendingAction)
    }

    @Test
    fun execute_intent_sin_pending_action_sigue_funcionando() {
        val effect = applier.toUiEffect(
            SituationDecision.ExecuteIntent(
                intent = SituationIntent.READ_SCREEN,
                reason = "x",
                nextState = SituationState.READING_SCREEN
            )
        )
        assertTrue(effect is SituationUiEffect.Execute)
        assertNull(effect.pendingAction)
    }

    @Test
    fun continue_goal_se_mapea_a_unsupported() {
        val effect = applier.toUiEffect(
            SituationDecision.ContinueGoal(goal = goal(), nextState = SituationState.PLANNING)
        )
        assertTrue(effect is SituationUiEffect.Unsupported)
    }

    @Test
    fun feature_flag_esta_apagado_por_defecto() {
        assertEquals(false, SituationBrainFeatureFlag.ENABLED)
    }
}
