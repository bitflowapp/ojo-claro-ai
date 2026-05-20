package com.ojoclaro.android.agent.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentTaskOrchestratorTest {

    private var now = 1_000L
    private val memory = AgentTaskMemory(clock = { now })
    private val orchestrator = AgentTaskOrchestrator(
        planner = AgentTaskPlanner(
            clock = { now },
            idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
        ),
        memory = memory
    )

    @Test
    fun rideCommandStartsPlanAndAsksForDestinationWhenMissing() {
        val result = orchestrator.handle("pedime un taxi")

        val handled = result as? AgentTaskOrchestratorResult.Handled
        assertTrue(handled != null, "Expected handled result, got $result")
        assertEquals(AgentTaskOrchestratorResultKind.PLAN_STARTED, handled.kind)
        assertTrue(handled.spokenText.contains("A donde queres ir?"))
        assertEquals(AgentTaskType.REQUEST_RIDE, memory.currentPlan()?.type)
    }

    @Test
    fun whatAreYouDoingReturnsSafeTaskSummary() {
        orchestrator.handle("pedime un taxi")

        val result = orchestrator.handle("que estas haciendo")

        val handled = result as? AgentTaskOrchestratorResult.Handled
        assertTrue(handled != null)
        assertEquals(AgentTaskOrchestratorResultKind.STATUS, handled.kind)
        assertEquals(
            "Estoy preparando la tarea Pedir viaje. Falta confirmar destino.",
            handled.spokenText
        )
    }

    @Test
    fun cancelTaskClearsActivePlan() {
        orchestrator.handle("pedime un taxi")

        val result = orchestrator.handle("cancelar tarea")

        val handled = result as? AgentTaskOrchestratorResult.Handled
        assertTrue(handled != null)
        assertEquals(AgentTaskOrchestratorResultKind.CANCELLED, handled.kind)
        assertNull(memory.currentPlan())
    }

    @Test
    fun pendingBridgeConfirmationBlocksNewTask() {
        val result = orchestrator.handle(
            rawUserCommand = "pedime un taxi",
            hasPendingBridgeConfirmation = true
        )

        val handled = result as? AgentTaskOrchestratorResult.Handled
        assertTrue(handled != null)
        assertEquals(AgentTaskOrchestratorResultKind.BLOCKED_BY_PENDING_CONFIRMATION, handled.kind)
        assertTrue(handled.spokenText.contains("confirmar o cancelar", ignoreCase = true))
        assertNull(memory.currentPlan())
    }

    @Test
    fun unsupportedCommandFallsBackToLegacy() {
        val result = orchestrator.handle("contame un chiste")

        assertTrue(result is AgentTaskOrchestratorResult.NotHandled)
    }

    @Test
    fun statusTextDoesNotClaimRideWasRequested() {
        orchestrator.handle("pedime un taxi")

        val status = orchestrator.handle("que falta") as AgentTaskOrchestratorResult.Handled

        assertFalse(status.spokenText.contains("taxi " + "pedido", ignoreCase = true))
        assertFalse(status.spokenText.contains("viaje " + "solicitado", ignoreCase = true))
    }
}
