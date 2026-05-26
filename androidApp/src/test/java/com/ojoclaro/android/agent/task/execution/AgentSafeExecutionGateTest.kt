package com.ojoclaro.android.agent.task.execution

import com.ojoclaro.android.agent.task.action.AgentControlledActionPolicy
import com.ojoclaro.android.agent.task.action.AgentControlledActionProposal
import com.ojoclaro.android.agent.task.action.AgentControlledActionType
import com.ojoclaro.android.agent.task.capability.AgentActionCapability
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityDecision
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityRegistry
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityRequirement
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityRisk
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentSafeExecutionGateTest {

    private val gate = AgentSafeExecutionGate()

    @Test
    fun noProposalReturnsNoActiveProposal() {
        val decision = gate.decide(request(proposal = null))

        assertEquals(AgentSafeExecutionStatus.NO_ACTIVE_PROPOSAL, decision.status)
        assertFalse(decision.isExecutable)
        assertNull(decision.executableType)
    }

    @Test
    fun openAppProposalIsAllowedForSafeExecution() {
        val decision = gate.decide(
            request(proposal = proposal(AgentControlledActionType.OPEN_APP))
        )

        assertEquals(AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION, decision.status)
        assertEquals(AgentControlledActionType.OPEN_APP, decision.executableType)
    }

    @Test
    fun prepareActionsAreAllowedAsSafeExecution() {
        listOf(
            AgentControlledActionType.PREPARE_MESSAGE_TEXT,
            AgentControlledActionType.PREPARE_AUDIO_SCRIPT,
            AgentControlledActionType.PREPARE_SEARCH_QUERY
        ).forEach { type ->
            val decision = gate.decide(request(proposal = proposal(type)))
            assertEquals(
                AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION,
                decision.status,
                "type=$type"
            )
            assertEquals(type, decision.executableType, "type=$type")
        }
    }

    @Test
    fun reviewActionsArePrepareOnly() {
        listOf(
            AgentControlledActionType.REVIEW_PAYMENT_METHOD,
            AgentControlledActionType.REVIEW_RIDE_PRICE
        ).forEach { type ->
            val decision = gate.decide(request(proposal = proposal(type)))
            assertEquals(AgentSafeExecutionStatus.PREPARE_ONLY, decision.status, "type=$type")
            assertFalse(decision.isExecutable, "type=$type")
        }
    }

    @Test
    fun finalConfirmRideIsBlocked() {
        val decision = gate.decide(
            request(proposal = proposal(AgentControlledActionType.FINAL_CONFIRM_RIDE))
        )

        assertEquals(AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION, decision.status)
        assertFalse(decision.isExecutable)
    }

    @Test
    fun finalConfirmSendMessageAndAudioAreBlocked() {
        listOf(
            AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE,
            AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO
        ).forEach { type ->
            val decision = gate.decide(request(proposal = proposal(type)))
            assertEquals(
                AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION,
                decision.status,
                "type=$type"
            )
            assertFalse(decision.isExecutable, "type=$type")
        }
    }

    @Test
    fun criticalActionStaysBlockedEvenIfUserConfirmedStrongly() {
        val decision = gate.decide(
            request(
                proposal = proposal(AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE),
                userConfirmedStrongly = true
            )
        )

        assertEquals(AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION, decision.status)
        assertFalse(decision.isExecutable)
    }

    @Test
    fun pendingExternalConfirmationBlocksExecution() {
        val decision = gate.decide(
            request(
                proposal = proposal(AgentControlledActionType.PREPARE_MESSAGE_TEXT),
                hasPendingExternalConfirmation = true
            )
        )

        assertEquals(AgentSafeExecutionStatus.REQUIRE_CONFIRMATION, decision.status)
        assertFalse(decision.isExecutable)
    }

    @Test
    fun decisionTextNeverClaimsSensitiveActionsCompleted() {
        AgentControlledActionType.values().forEach { type ->
            val decision = gate.decide(request(proposal = proposal(type)))
            val text = decision.spokenText.lowercase()
            assertFalse(text.contains("mensaje " + "enviado"), "type=$type")
            assertFalse(text.contains("audio " + "enviado"), "type=$type")
            assertFalse(text.contains("taxi " + "pedido"), "type=$type")
            assertFalse(text.contains("viaje " + "solicitado"), "type=$type")
        }
    }

    @Test
    fun gateConsultsCapabilityRegistryAndDowngradesUnsafeCapability() {
        // Capability registry que marca preparar texto como no-listo todavia.
        val registry = AgentActionCapabilityRegistry(
            overrides = mapOf(
                AgentActionCapabilityType.PREPARE_TEXT_IN_MEMORY to capability(
                    type = AgentActionCapabilityType.PREPARE_TEXT_IN_MEMORY,
                    decision = AgentActionCapabilityDecision.INSTRUMENTED_TEST_REQUIRED
                )
            )
        )
        val guardedGate = AgentSafeExecutionGate(registry)

        val decision = guardedGate.decide(
            request(proposal = proposal(AgentControlledActionType.PREPARE_MESSAGE_TEXT))
        )

        // Sin la consulta, PREPARE_MESSAGE_TEXT seria ALLOW_SAFE_EXECUTION.
        assertEquals(AgentSafeExecutionStatus.PREPARE_ONLY, decision.status)
    }

    @Test
    fun blockedCapabilityBlocksExecutionEvenIfProposalAllowsIt() {
        val registry = AgentActionCapabilityRegistry(
            overrides = mapOf(
                AgentActionCapabilityType.OPEN_APP to capability(
                    type = AgentActionCapabilityType.OPEN_APP,
                    decision = AgentActionCapabilityDecision.BLOCKED_DANGEROUS
                )
            )
        )
        val guardedGate = AgentSafeExecutionGate(registry)
        val openAppProposal = proposal(AgentControlledActionType.OPEN_APP)
        assertTrue(openAppProposal.allowedToExecuteNow, "OPEN_APP proposal should be executable")

        val decision = guardedGate.decide(request(proposal = openAppProposal))

        assertEquals(AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION, decision.status)
        assertFalse(decision.isExecutable)
    }

    private fun capability(
        type: AgentActionCapabilityType,
        decision: AgentActionCapabilityDecision
    ): AgentActionCapability = AgentActionCapability(
        type = type,
        decision = decision,
        risk = AgentActionCapabilityRisk.HIGH,
        requirement = AgentActionCapabilityRequirement.INSTRUMENTED_TEST,
        safeDescription = "Capacidad de prueba para $type."
    )

    private fun request(
        proposal: AgentControlledActionProposal?,
        hasPendingExternalConfirmation: Boolean = false,
        userConfirmedStrongly: Boolean = false
    ): AgentSafeExecutionRequest = AgentSafeExecutionRequest(
        plan = null,
        proposal = proposal,
        userCommand = "hacelo",
        hasPendingExternalConfirmation = hasPendingExternalConfirmation,
        userConfirmedStrongly = userConfirmedStrongly
    )

    private fun proposal(type: AgentControlledActionType): AgentControlledActionProposal {
        val evaluation = AgentControlledActionPolicy.evaluate(type)
        return AgentControlledActionProposal(
            id = "action-1",
            taskId = "plan-1",
            ticketId = null,
            type = type,
            title = "Propuesta $type",
            safeDescription = "Descripcion segura de prueba.",
            riskLevel = evaluation.riskLevel,
            status = evaluation.status,
            requiresConfirmation = evaluation.requiresConfirmation,
            allowedToExecuteNow = evaluation.allowedToExecuteNow,
            blockedReason = evaluation.blockedReason,
            forbiddenReason = evaluation.forbiddenReason,
            spokenText = "Texto hablado de prueba para $type.",
            preparedText = null,
            createdAt = 1_000L,
            updatedAt = 1_000L
        )
    }
}
