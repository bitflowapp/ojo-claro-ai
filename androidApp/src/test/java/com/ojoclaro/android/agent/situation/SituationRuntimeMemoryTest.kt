package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.AgentRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SituationRuntimeMemoryTest {

    private fun goal(createdAt: Long = 1_000L, ttlMillis: Long = 300_000L): ActiveGoal =
        ActiveGoal(
            description = "avisarle a Sofi que llego tarde",
            intent = SituationIntent.WRITE_MESSAGE,
            createdAt = createdAt,
            ttlMillis = ttlMillis
        )

    private fun pendingAction(): PendingAction = PendingAction(
        label = "preparar un mensaje",
        intentName = SituationIntent.WRITE_MESSAGE.name,
        riskLevel = AgentRiskLevel.MEDIUM,
        confirmationPrompt = "Para continuar, decí: confirmar.",
        expiresAt = 999_999L
    )

    private fun turn(index: Int): TurnSummary = TurnSummary(
        role = TurnRole.USER,
        shortText = "turno $index",
        intent = SituationIntent.UNKNOWN,
        timestamp = index.toLong()
    )

    private fun context(
        activeGoal: ActiveGoal? = null,
        pendingAction: PendingAction? = null,
        companionModeActive: Boolean = false,
        lastAssistantMessage: String = "",
        recentTurns: List<TurnSummary> = emptyList(),
        situationState: SituationState = SituationState.IDLE,
        mutedThroughRequestId: Long = 0L
    ): SituationContext = SituationContext(
        rawCommand = "x",
        normalizedCommand = "x",
        source = InputSource.VOICE,
        confidence = 0.9f,
        timestamp = 1_000L,
        situationIntent = SituationIntent.UNKNOWN,
        activeGoal = activeGoal,
        pendingAction = pendingAction,
        currentAppPackage = null,
        environmentHint = EnvironmentHint.UNKNOWN,
        screenContext = null,
        riskLevel = AgentRiskLevel.NONE,
        needsConfirmation = pendingAction != null,
        isPrivacyHotZone = false,
        lastAssistantMessage = lastAssistantMessage,
        recentTurns = recentTurns,
        situationState = situationState,
        activeRequestId = 10L,
        mutedThroughRequestId = mutedThroughRequestId,
        cancellationState = CancellationState.NONE,
        userMode = AgentExecutionMode.ACCESSIBILITY_VOICE,
        companionModeActive = companionModeActive
    )

    // 1 -----------------------------------------------------------------------
    @Test
    fun empty_no_tiene_active_goal_ni_pending_action() {
        val snapshot = SituationRuntimeSnapshot.empty()
        assertNull(snapshot.activeGoal)
        assertNull(snapshot.pendingAction)
        assertFalse(snapshot.companionModeActive)
        assertTrue(snapshot.isEmpty())
    }

    // 2 -----------------------------------------------------------------------
    @Test
    fun update_from_conserva_active_goal() {
        val memory = SituationRuntimeMemory()
        val g = goal()
        memory.updateFrom(context(activeGoal = g))
        assertEquals(g, memory.current().activeGoal)
    }

    // 3 -----------------------------------------------------------------------
    @Test
    fun update_from_conserva_pending_action() {
        val memory = SituationRuntimeMemory()
        val p = pendingAction()
        memory.updateFrom(context(pendingAction = p))
        assertEquals(p, memory.current().pendingAction)
    }

    // 4 -----------------------------------------------------------------------
    @Test
    fun update_from_conserva_companion_mode_active() {
        val memory = SituationRuntimeMemory()
        memory.updateFrom(context(companionModeActive = true))
        assertTrue(memory.current().companionModeActive)
    }

    // 5 -----------------------------------------------------------------------
    @Test
    fun update_from_conserva_last_assistant_message() {
        val memory = SituationRuntimeMemory()
        memory.updateFrom(context(lastAssistantMessage = "te acompaño"))
        assertEquals("te acompaño", memory.current().lastAssistantMessage)
    }

    // 6 -----------------------------------------------------------------------
    @Test
    fun update_from_conserva_recent_turns_maximo_cinco() {
        val memory = SituationRuntimeMemory()
        val turns = (1..5).map { turn(it) }
        memory.updateFrom(context(recentTurns = turns))
        assertEquals(5, memory.current().recentTurns.size)
        assertEquals(turns, memory.current().recentTurns)
    }

    // 7 + 8 + 9 --------------------------------------------------------------
    @Test
    fun clear_for_cancellation_limpia_goal_pending_y_companion() {
        val memory = SituationRuntimeMemory()
        memory.updateFrom(
            context(
                activeGoal = goal(),
                pendingAction = pendingAction(),
                companionModeActive = true,
                recentTurns = listOf(turn(1), turn(2))
            )
        )
        val cancelledContext = context(situationState = SituationState.CANCELLED)
        memory.clearForCancellation(cancelledContext)

        val snapshot = memory.current()
        assertNull(snapshot.activeGoal)
        assertNull(snapshot.pendingAction)
        assertFalse(snapshot.companionModeActive)
        assertTrue(snapshot.recentTurns.isEmpty())
        assertEquals(SituationState.CANCELLED, snapshot.situationState)
    }

    // 10 ---------------------------------------------------------------------
    @Test
    fun clear_expired_goals_borra_goal_vencido() {
        val memory = SituationRuntimeMemory()
        memory.updateFrom(context(activeGoal = goal(createdAt = 0L, ttlMillis = 1_000L)))
        memory.clearExpiredGoals(now = 1_000_000L)
        assertNull(memory.current().activeGoal)
    }

    // 11 ---------------------------------------------------------------------
    @Test
    fun clear_expired_goals_conserva_goal_vigente() {
        val memory = SituationRuntimeMemory()
        val vigente = goal(createdAt = 1_000L, ttlMillis = 300_000L)
        memory.updateFrom(context(activeGoal = vigente))
        memory.clearExpiredGoals(now = 1_500L)
        assertEquals(vigente, memory.current().activeGoal)
    }

    // 12 ---------------------------------------------------------------------
    @Test
    fun reset_vuelve_a_empty() {
        val memory = SituationRuntimeMemory()
        memory.updateFrom(context(activeGoal = goal(), companionModeActive = true))
        memory.reset()
        assertTrue(memory.current().isEmpty())
        assertEquals(SituationRuntimeSnapshot.empty(), memory.current())
    }

    // --- Fase 5: recentTurns alimentados por SituationBrain ------------------

    @Test
    fun update_from_conserva_recent_turns_alimentados_por_situation_brain() {
        val memory = SituationRuntimeMemory()
        val result = SituationBrain().process(
            context(situationState = SituationState.IDLE).copy(
                rawCommand = "leeme la pantalla",
                normalizedCommand = "leeme la pantalla"
            )
        )
        memory.updateFrom(result.updatedContext)
        val turns = memory.current().recentTurns
        assertEquals(1, turns.size)
        assertEquals(SituationIntent.READ_SCREEN, turns.last().intent)
    }

    @Test
    fun reset_limpia_recent_turns() {
        val memory = SituationRuntimeMemory()
        memory.updateFrom(context(recentTurns = listOf(turn(1), turn(2))))
        assertEquals(2, memory.current().recentTurns.size)
        memory.reset()
        assertTrue(memory.current().recentTurns.isEmpty())
    }

    // --- Fase 6: olvidar pendingAction --------------------------------------

    @Test
    fun forget_pending_action_limpia_solo_la_pending_action() {
        val memory = SituationRuntimeMemory()
        memory.updateFrom(
            context(
                pendingAction = pendingAction(),
                companionModeActive = true,
                lastAssistantMessage = "te acompaño"
            )
        )
        memory.forgetPendingAction()
        assertNull(memory.current().pendingAction)
        // No toca el resto de la memoria.
        assertTrue(memory.current().companionModeActive)
        assertEquals("te acompaño", memory.current().lastAssistantMessage)
    }

    @Test
    fun update_from_conserva_pending_action_con_payload() {
        val memory = SituationRuntimeMemory()
        val pending = PendingAction(
            label = "abrir una app",
            intentName = SituationIntent.OPEN_APP.name,
            riskLevel = AgentRiskLevel.LOW,
            confirmationPrompt = "¿Querés que abra WhatsApp?",
            expiresAt = 999_999L,
            originalCommand = "abrí WhatsApp",
            target = "WhatsApp",
            payload = mapOf("intent" to "OPEN_APP", "target" to "WhatsApp")
        )
        memory.updateFrom(context(pendingAction = pending))
        val stored = memory.current().pendingAction
        assertEquals(pending, stored)
        assertEquals("abrí WhatsApp", stored?.originalCommand)
        assertEquals("WhatsApp", stored?.target)
        assertEquals("WhatsApp", stored?.payload?.get("target"))
    }

    @Test
    fun update_from_conserva_pending_action_write_message_con_payload() {
        val memory = SituationRuntimeMemory()
        val pending = PendingAction(
            label = "preparar un mensaje",
            intentName = SituationIntent.WRITE_MESSAGE.name,
            riskLevel = AgentRiskLevel.MEDIUM,
            confirmationPrompt = "¿Querés que prepare el mensaje?",
            expiresAt = 999_999L,
            originalCommand = "avisale a Sofi que llego tarde",
            target = "Sofi",
            payload = mapOf("contact" to "Sofi", "message" to "llego tarde")
        )
        memory.updateFrom(context(pendingAction = pending))
        val stored = memory.current().pendingAction
        assertEquals(pending, stored)
        assertEquals(SituationIntent.WRITE_MESSAGE.name, stored?.intentName)
        assertEquals("Sofi", stored?.payload?.get("contact"))
        assertEquals("llego tarde", stored?.payload?.get("message"))
    }

    @Test
    fun forget_pending_action_limpia_pending_action_write_message_con_payload() {
        val memory = SituationRuntimeMemory()
        memory.updateFrom(
            context(
                pendingAction = PendingAction(
                    label = "preparar un mensaje",
                    intentName = SituationIntent.WRITE_MESSAGE.name,
                    riskLevel = AgentRiskLevel.MEDIUM,
                    confirmationPrompt = "¿Querés que prepare el mensaje?",
                    expiresAt = 999_999L,
                    target = "Sofi",
                    payload = mapOf("contact" to "Sofi", "message" to "llego tarde")
                )
            )
        )
        memory.forgetPendingAction()
        assertNull(memory.current().pendingAction)
    }

    @Test
    fun forget_pending_action_no_limpia_companion_mode_ni_recent_turns() {
        val memory = SituationRuntimeMemory()
        memory.updateFrom(
            context(
                pendingAction = pendingAction(),
                companionModeActive = true,
                recentTurns = listOf(turn(1), turn(2))
            )
        )
        memory.forgetPendingAction()
        assertNull(memory.current().pendingAction)
        assertTrue(memory.current().companionModeActive)
        assertEquals(2, memory.current().recentTurns.size)
    }

    @Test
    fun update_from_tras_confirmacion_del_brain_limpia_pending_action() {
        val memory = SituationRuntimeMemory()
        // Memoria con una acción pendiente OPEN_APP.
        memory.updateFrom(
            context(
                pendingAction = PendingAction(
                    label = "abrir una app",
                    intentName = SituationIntent.OPEN_APP.name,
                    riskLevel = AgentRiskLevel.LOW,
                    confirmationPrompt = "Para continuar, decí: confirmar.",
                    expiresAt = 999_999L
                ),
                situationState = SituationState.WAITING_CONFIRMATION
            )
        )
        // El usuario confirma: el Brain devuelve un contexto sin pendingAction.
        val result = SituationBrain().process(
            context(
                pendingAction = memory.current().pendingAction,
                situationState = SituationState.WAITING_CONFIRMATION
            ).copy(rawCommand = "sí", normalizedCommand = "sí")
        )
        memory.updateFrom(result.updatedContext)
        assertNull(memory.current().pendingAction)
    }
}
