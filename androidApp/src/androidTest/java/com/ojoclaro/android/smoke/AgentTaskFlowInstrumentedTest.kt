package com.ojoclaro.android.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojoclaro.android.agent.core.screen.ScreenSignals
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.AgentTaskOrchestrator
import com.ojoclaro.android.agent.task.AgentTaskOrchestratorResult
import com.ojoclaro.android.agent.task.AgentTaskPlanner
import com.ojoclaro.android.agent.task.AgentTaskType
import com.ojoclaro.android.agent.task.action.AgentControlledActionPlanner
import com.ojoclaro.android.agent.task.action.AgentControlledActionType
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityDecision
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityRegistry
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityType
import com.ojoclaro.android.agent.task.execution.AgentSafeExecutionGate
import com.ojoclaro.android.agent.task.execution.AgentSafeExecutionRequest
import com.ojoclaro.android.agent.task.execution.AgentSafeExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Paquete 6H -- Smoke harness instrumentado.
 *
 * Ejercita la capa de tareas del agente bajo el runtime real de Android
 * (ART), sin tocar apps de terceros: planifica una tarea de taxi y verifica
 * que la puerta de ejecucion segura bloquea las acciones sensibles.
 */
@RunWith(AndroidJUnit4::class)
class AgentTaskFlowInstrumentedTest {

    @Test
    fun orchestratorPlansRideTaskWithoutRequestingRide() {
        val orchestrator = AgentTaskOrchestrator()

        val result = orchestrator.handle("pedime un taxi")
            as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskType.REQUEST_RIDE, orchestrator.currentPlan()?.type)
        val spoken = result.spokenText.lowercase()
        assertFalse(spoken.contains("viaje " + "solicitado"))
        assertFalse(spoken.contains("taxi " + "pedido"))
    }

    @Test
    fun capabilityRegistryBlocksSensitiveActionsAndAllowsOpenApp() {
        val registry = AgentActionCapabilityRegistry()

        assertEquals(
            AgentActionCapabilityDecision.SUPPORTED_SAFE,
            registry.decision(AgentActionCapabilityType.OPEN_APP)
        )
        listOf(
            AgentActionCapabilityType.SEND_MESSAGE,
            AgentActionCapabilityType.SEND_AUDIO,
            AgentActionCapabilityType.REQUEST_RIDE
        ).forEach { type ->
            assertFalse(
                "Sensitive capability $type must not be safe",
                registry.isSafeToExecuteNow(type)
            )
        }
    }

    @Test
    fun safeExecutionGateBlocksFinalRideConfirmation() {
        val ridePlan = AgentTaskPlanner().plan("pedime un taxi para ir a casa")
        val proposal = AgentControlledActionPlanner().proposeNextAction(
            plan = ridePlan,
            snapshot = snapshotWithButton("Solicitar viaje")
        )
        assertEquals(AgentControlledActionType.FINAL_CONFIRM_RIDE, proposal.type)

        val decision = AgentSafeExecutionGate().decide(
            AgentSafeExecutionRequest(
                plan = ridePlan,
                proposal = proposal,
                userCommand = "hacelo"
            )
        )

        assertEquals(AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION, decision.status)
        assertFalse(decision.isExecutable)
        assertTrue(decision.spokenText.isNotBlank())
    }

    private fun snapshotWithButton(button: String): StructuredScreenSnapshot =
        StructuredScreenSnapshot(
            packageName = "com.ubercab",
            appLabel = "Uber",
            capturedAtMillis = 1_000L,
            redactedTextLines = emptyList(),
            buttons = listOf(button),
            editableFields = emptyList(),
            focusedLabel = null,
            totalNodes = 1,
            signals = ScreenSignals(),
            warnings = emptyList(),
            isLimited = false
        )
}
