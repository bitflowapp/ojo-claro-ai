package com.ojoclaro.android.agent.task.action

import com.ojoclaro.android.agent.core.screen.ScreenSignals
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.AgentTaskPlan
import com.ojoclaro.android.agent.task.AgentTaskPlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentControlledActionPlannerTest {

    private var now = 5_000L
    private val planner = AgentControlledActionPlanner(
        clock = { now },
        idFactory = { fixedNow, taskId -> "action-$fixedNow-$taskId" }
    )
    private val taskPlanner = AgentTaskPlanner(
        clock = { now },
        idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
    )

    @Test
    fun rideWithDestinationPendingWaitsForUser() {
        val proposal = planner.proposeNextAction(ridePlan("pedime un taxi"))

        assertEquals(AgentControlledActionType.WAIT_FOR_USER_INPUT, proposal.type)
        assertEquals(AgentControlledActionStatus.WAITING_FOR_USER, proposal.status)
        assertTrue(proposal.spokenText.contains("destino", ignoreCase = true))
        assertFalse(proposal.allowedToExecuteNow)
    }

    @Test
    fun rideWithVisiblePriceProposesReviewRidePrice() {
        val proposal = planner.proposeNextAction(
            plan = ridePlan("pedime un taxi para ir a casa"),
            snapshot = snapshot(
                packageName = "com.ubercab",
                textLines = listOf("Precio estimado", "UberX 8 min")
            )
        )

        assertEquals(AgentControlledActionType.REVIEW_RIDE_PRICE, proposal.type)
        assertEquals(AgentControlledActionRisk.HIGH, proposal.riskLevel)
        assertFalse(proposal.allowedToExecuteNow)
    }

    @Test
    fun rideWithVisibleFinalConfirmationIsCriticalAndBlocked() {
        val proposal = planner.proposeNextAction(
            plan = ridePlan("pedime un taxi para ir a casa"),
            snapshot = snapshot(
                packageName = "com.ubercab",
                buttons = listOf("Solicitar viaje")
            )
        )

        assertEquals(AgentControlledActionType.FINAL_CONFIRM_RIDE, proposal.type)
        assertEquals(AgentControlledActionRisk.CRITICAL, proposal.riskLevel)
        assertEquals(AgentControlledActionStatus.BLOCKED, proposal.status)
        assertFalse(proposal.allowedToExecuteNow)
    }

    @Test
    fun rideProposalsNeverClaimRideWasRequested() {
        val proposals = listOf(
            planner.proposeNextAction(ridePlan("pedime un taxi")),
            planner.proposeNextAction(
                plan = ridePlan("pedime un taxi para ir a casa"),
                snapshot = snapshot(packageName = "com.ubercab", buttons = listOf("Solicitar viaje"))
            ),
            planner.proposeNextAction(
                plan = ridePlan("pedime un taxi para ir a casa"),
                request = AgentControlledActionRequest.REVIEW_PRICE
            )
        )
        proposals.forEach { proposal ->
            val text = proposal.spokenText.lowercase()
            assertFalse(text.contains("viaje " + "solicitado"), "spoken=${proposal.spokenText}")
            assertFalse(text.contains("taxi " + "pedido"), "spoken=${proposal.spokenText}")
        }
    }

    @Test
    fun ridePaymentVisibleProposesReviewPaymentMethodHigh() {
        val proposal = planner.proposeNextAction(
            plan = ridePlan("pedime un taxi para ir a casa"),
            snapshot = snapshot(
                packageName = "com.ubercab",
                textLines = listOf("Metodo de pago", "Tarjeta de credito")
            )
        )

        assertEquals(AgentControlledActionType.REVIEW_PAYMENT_METHOD, proposal.type)
        assertEquals(AgentControlledActionRisk.HIGH, proposal.riskLevel)
        assertFalse(proposal.allowedToExecuteNow)
    }

    @Test
    fun whatsAppMessagePrepareCreatesPrepareMessageText() {
        val proposal = planner.proposeNextAction(
            plan = whatsAppPlan("mandale un mensaje a Sofi diciendo llego en 10"),
            request = AgentControlledActionRequest.PREPARE_MESSAGE
        )

        assertEquals(AgentControlledActionType.PREPARE_MESSAGE_TEXT, proposal.type)
        assertTrue(proposal.spokenText.contains("Sofi"))
        assertTrue(proposal.spokenText.contains("llego en 10"))
        assertTrue(proposal.spokenText.contains("confirmacion final", ignoreCase = true))
        assertFalse(proposal.allowedToExecuteNow)
    }

    @Test
    fun whatsAppAudioPrepareCreatesPrepareAudioScript() {
        val proposal = planner.proposeNextAction(
            plan = whatsAppPlan("mandale un audio a Sofi diciendo llego en 10"),
            request = AgentControlledActionRequest.PREPARE_AUDIO
        )

        assertEquals(AgentControlledActionType.PREPARE_AUDIO_SCRIPT, proposal.type)
        assertEquals(AgentControlledActionRisk.HIGH, proposal.riskLevel)
    }

    @Test
    fun whatsAppAudioProposalNeverRecordsOrSends() {
        val proposal = planner.proposeNextAction(
            plan = whatsAppPlan("mandale un audio a Sofi diciendo llego en 10"),
            request = AgentControlledActionRequest.PREPARE_AUDIO
        )

        assertFalse(proposal.allowedToExecuteNow)
        val text = proposal.spokenText.lowercase()
        assertTrue(text.contains("no voy a grabarlo") || text.contains("todavia no voy a grabarlo"))
        assertFalse(text.contains("audio " + "enviado"))
        assertEquals(AgentControlledActionPolicy.REASON_NO_AUDIO_RECORD, proposal.forbiddenReason)
    }

    @Test
    fun whatsAppSearchChatPreparesQueryWithoutTyping() {
        val proposal = planner.proposeNextAction(
            plan = whatsAppPlan("busca el chat de Sofi"),
            request = AgentControlledActionRequest.SEARCH_CHAT
        )

        assertTrue(
            proposal.type == AgentControlledActionType.PREPARE_SEARCH_QUERY ||
                proposal.type == AgentControlledActionType.FOCUS_SEARCH_FIELD,
            "Unexpected type ${proposal.type}"
        )
        assertFalse(proposal.allowedToExecuteNow)
        assertTrue(proposal.spokenText.contains("no voy a escribir", ignoreCase = true))
    }

    @Test
    fun whatsAppSendButtonVisibleIsCriticalAndBlocked() {
        val message = planner.proposeNextAction(
            plan = whatsAppPlan("mandale un mensaje a Sofi diciendo hola"),
            snapshot = snapshot(packageName = "com.whatsapp", buttons = listOf("Enviar"))
        )
        val audio = planner.proposeNextAction(
            plan = whatsAppPlan("mandale un audio a Sofi diciendo hola"),
            snapshot = snapshot(packageName = "com.whatsapp", buttons = listOf("Enviar"))
        )

        assertEquals(AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE, message.type)
        assertEquals(AgentControlledActionStatus.BLOCKED, message.status)
        assertEquals(AgentControlledActionRisk.CRITICAL, message.riskLevel)
        assertEquals(AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO, audio.type)
        assertEquals(AgentControlledActionStatus.BLOCKED, audio.status)
        assertFalse(message.allowedToExecuteNow)
        assertFalse(audio.allowedToExecuteNow)
    }

    @Test
    fun sensitiveScreenBlocksAnyProposal() {
        val proposal = planner.proposeNextAction(
            plan = ridePlan("pedime un taxi para ir a casa"),
            snapshot = snapshot(
                packageName = "com.bank",
                signals = ScreenSignals(isBankingApp = true, hasPasswordField = true)
            )
        )

        assertEquals(AgentControlledActionType.BLOCKED_SENSITIVE_ACTION, proposal.type)
        assertFalse(proposal.allowedToExecuteNow)
    }

    @Test
    fun proposalNeverContainsExecutionClaims() {
        val proposals = listOf(
            planner.proposeNextAction(whatsAppPlan("mandale un mensaje a Sofi diciendo hola")),
            planner.proposeNextAction(whatsAppPlan("mandale un audio a Sofi diciendo hola")),
            planner.proposeNextAction(ridePlan("pedime un taxi para ir a casa"))
        )
        proposals.forEach { proposal ->
            val haystack = (proposal.spokenText + " " + proposal.safeDescription).lowercase()
            assertFalse(haystack.contains("mensaje " + "enviado"))
            assertFalse(haystack.contains("audio " + "enviado"))
            assertFalse(haystack.contains("taxi " + "pedido"))
            assertFalse(haystack.contains("viaje " + "solicitado"))
        }
    }

    private fun ridePlan(command: String): AgentTaskPlan = taskPlanner.plan(command)

    private fun whatsAppPlan(command: String): AgentTaskPlan = taskPlanner.plan(command)

    private fun snapshot(
        packageName: String?,
        textLines: List<String> = emptyList(),
        buttons: List<String> = emptyList(),
        editableFields: List<String> = emptyList(),
        signals: ScreenSignals = ScreenSignals()
    ): StructuredScreenSnapshot = StructuredScreenSnapshot(
        packageName = packageName,
        appLabel = null,
        capturedAtMillis = now,
        redactedTextLines = textLines,
        buttons = buttons,
        editableFields = editableFields,
        focusedLabel = null,
        totalNodes = textLines.size + buttons.size + editableFields.size,
        signals = signals,
        warnings = emptyList(),
        isLimited = false
    )
}
