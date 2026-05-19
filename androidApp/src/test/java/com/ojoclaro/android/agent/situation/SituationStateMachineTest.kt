package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.AgentRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SituationStateMachineTest {

    private fun contextInState(state: SituationState): SituationContext = SituationContext(
        rawCommand = "x",
        normalizedCommand = "x",
        source = InputSource.VOICE,
        confidence = 0.5f,
        timestamp = 0L,
        situationIntent = SituationIntent.UNKNOWN,
        activeGoal = null,
        pendingAction = null,
        currentAppPackage = null,
        environmentHint = EnvironmentHint.UNKNOWN,
        screenContext = null,
        riskLevel = AgentRiskLevel.NONE,
        needsConfirmation = false,
        isPrivacyHotZone = false,
        lastAssistantMessage = "",
        recentTurns = emptyList(),
        situationState = state,
        activeRequestId = 0L,
        mutedThroughRequestId = 0L,
        cancellationState = CancellationState.NONE,
        userMode = AgentExecutionMode.ACCESSIBILITY_VOICE,
        companionModeActive = false
    )

    private val normalTransitions: List<Pair<SituationState, SituationState>> = listOf(
        SituationState.IDLE to SituationState.LISTENING,
        SituationState.LISTENING to SituationState.UNDERSTANDING,
        SituationState.UNDERSTANDING to SituationState.READING_SCREEN,
        SituationState.UNDERSTANDING to SituationState.PLANNING,
        SituationState.UNDERSTANDING to SituationState.WAITING_CONFIRMATION,
        SituationState.UNDERSTANDING to SituationState.SPEAKING,
        SituationState.READING_SCREEN to SituationState.PLANNING,
        SituationState.READING_SCREEN to SituationState.SPEAKING,
        SituationState.PLANNING to SituationState.WAITING_CONFIRMATION,
        SituationState.PLANNING to SituationState.EXECUTING_GUIDED_ACTION,
        SituationState.PLANNING to SituationState.SPEAKING,
        SituationState.EXECUTING_GUIDED_ACTION to SituationState.SPEAKING,
        SituationState.EXECUTING_GUIDED_ACTION to SituationState.IDLE,
        SituationState.SPEAKING to SituationState.IDLE,
        SituationState.SPEAKING to SituationState.LISTENING,
        SituationState.ERROR_RECOVERY to SituationState.LISTENING,
        SituationState.ERROR_RECOVERY to SituationState.IDLE,
        SituationState.CANCELLED to SituationState.IDLE
    )

    @Test
    fun todas_las_transiciones_normales_permitidas_devuelven_true() {
        normalTransitions.forEach { (from, to) ->
            assertTrue(
                SituationStateMachine.canTransition(from, to),
                "se esperaba que $from -> $to fuera legal"
            )
        }
    }

    @Test
    fun transiciones_ilegales_devuelven_false() {
        // IDLE solo va a LISTENING (o a los escapes).
        assertFalse(SituationStateMachine.canTransition(SituationState.IDLE, SituationState.SPEAKING))
        assertFalse(SituationStateMachine.canTransition(SituationState.IDLE, SituationState.PLANNING))
        // LISTENING solo va a UNDERSTANDING.
        assertFalse(SituationStateMachine.canTransition(SituationState.LISTENING, SituationState.IDLE))
        // SPEAKING no vuelve a PLANNING.
        assertFalse(SituationStateMachine.canTransition(SituationState.SPEAKING, SituationState.PLANNING))
        // EXECUTING_GUIDED_ACTION no vuelve a UNDERSTANDING.
        assertFalse(
            SituationStateMachine.canTransition(
                SituationState.EXECUTING_GUIDED_ACTION,
                SituationState.UNDERSTANDING
            )
        )
        // CANCELLED no va a LISTENING.
        assertFalse(SituationStateMachine.canTransition(SituationState.CANCELLED, SituationState.LISTENING))
    }

    @Test
    fun transition_legal_devuelve_contexto_con_nuevo_estado() {
        val ctx = contextInState(SituationState.IDLE)
        val next = SituationStateMachine.transition(ctx, SituationState.LISTENING)
        assertEquals(SituationState.LISTENING, next.situationState)
    }

    @Test
    fun transition_ilegal_lanza_illegal_state_exception() {
        val ctx = contextInState(SituationState.IDLE)
        assertFailsWith<IllegalStateException> {
            SituationStateMachine.transition(ctx, SituationState.SPEAKING)
        }
    }

    @Test
    fun cancelled_es_alcanzable_desde_cualquier_estado() {
        // Desde CANCELLED no aplica (regla "solo va a IDLE"): se excluye a sí mismo.
        SituationState.entries
            .filter { it != SituationState.CANCELLED }
            .forEach { from ->
                assertTrue(
                    SituationStateMachine.canTransition(from, SituationState.CANCELLED),
                    "se esperaba poder cancelar desde $from"
                )
            }
    }

    @Test
    fun error_recovery_es_alcanzable_desde_cualquier_estado() {
        // Desde CANCELLED no aplica (regla "solo va a IDLE"): se excluye.
        SituationState.entries
            .filter { it != SituationState.CANCELLED }
            .forEach { from ->
                assertTrue(
                    SituationStateMachine.canTransition(from, SituationState.ERROR_RECOVERY),
                    "se esperaba poder ir a ERROR_RECOVERY desde $from"
                )
            }
    }

    @Test
    fun cancelled_solo_puede_ir_a_idle() {
        SituationState.entries.forEach { to ->
            val expected = to == SituationState.IDLE
            assertEquals(
                expected,
                SituationStateMachine.canTransition(SituationState.CANCELLED, to),
                "CANCELLED -> $to debería ser ${if (expected) "legal" else "ilegal"}"
            )
        }
    }

    @Test
    fun waiting_confirmation_solo_va_a_executing_cancelled_o_error_recovery() {
        val allowed = setOf(
            SituationState.EXECUTING_GUIDED_ACTION,
            SituationState.CANCELLED,
            SituationState.ERROR_RECOVERY
        )
        SituationState.entries.forEach { to ->
            val expected = to in allowed
            assertEquals(
                expected,
                SituationStateMachine.canTransition(SituationState.WAITING_CONFIRMATION, to),
                "WAITING_CONFIRMATION -> $to debería ser ${if (expected) "legal" else "ilegal"}"
            )
        }
    }
}
