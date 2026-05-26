package com.ojoclaro.android.agent.task.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentControlledActionMemoryTest {

    private var now = 7_000L
    private val memory = AgentControlledActionMemory(clock = { now })

    @Test
    fun startsWithoutProposal() {
        assertNull(memory.currentActionProposal())
        assertEquals("No hay una propuesta de accion activa.", memory.proposalSummaryForSpeech())
    }

    @Test
    fun saveProposalStoresIt() {
        val proposal = proposal(AgentControlledActionType.PREPARE_MESSAGE_TEXT)

        memory.saveProposal(proposal)

        assertEquals(proposal.id, memory.currentActionProposal()?.id)
        assertEquals(proposal.spokenText, memory.proposalSummaryForSpeech())
    }

    @Test
    fun clearProposalRemovesIt() {
        memory.saveProposal(proposal(AgentControlledActionType.PREPARE_MESSAGE_TEXT))

        memory.clearProposal()

        assertNull(memory.currentActionProposal())
    }

    @Test
    fun cancelProposalMarksCancelledAndClears() {
        memory.saveProposal(proposal(AgentControlledActionType.PREPARE_AUDIO_SCRIPT))

        val cancelled = memory.cancelProposal("user_cancelled")

        assertTrue(cancelled != null)
        assertEquals(AgentControlledActionStatus.CANCELLED, cancelled.status)
        assertNull(memory.currentActionProposal())
    }

    @Test
    fun cancelProposalWithoutActiveProposalReturnsNull() {
        assertNull(memory.cancelProposal("user_cancelled"))
    }

    private fun proposal(type: AgentControlledActionType): AgentControlledActionProposal {
        val evaluation = AgentControlledActionPolicy.evaluate(type)
        return AgentControlledActionProposal(
            id = "action-$now",
            taskId = "plan-1",
            ticketId = null,
            type = type,
            title = "Propuesta de prueba",
            safeDescription = "Descripcion segura de prueba.",
            riskLevel = evaluation.riskLevel,
            status = evaluation.status,
            requiresConfirmation = evaluation.requiresConfirmation,
            allowedToExecuteNow = evaluation.allowedToExecuteNow,
            blockedReason = evaluation.blockedReason,
            forbiddenReason = evaluation.forbiddenReason,
            spokenText = "Texto hablado de prueba.",
            createdAt = now,
            updatedAt = now
        )
    }
}
