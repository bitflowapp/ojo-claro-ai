package com.ojoclaro.android.agent.task.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentControlledActionPolicyTest {

    @Test
    fun openAppIsLowRiskAndTheOnlyExecutableType() {
        val evaluation = AgentControlledActionPolicy.evaluate(AgentControlledActionType.OPEN_APP)

        assertEquals(AgentControlledActionRisk.LOW, evaluation.riskLevel)
        assertTrue(evaluation.allowedToExecuteNow)
        assertFalse(evaluation.requiresConfirmation)
    }

    @Test
    fun noSensitiveActionIsEverExecutable() {
        AgentControlledActionPolicy.SENSITIVE_TYPES.forEach { type ->
            val evaluation = AgentControlledActionPolicy.evaluate(type)
            assertFalse(
                evaluation.allowedToExecuteNow,
                "Sensitive type $type must never be executable in 6E"
            )
        }
    }

    @Test
    fun prepareMessageAndAudioAreHighAndNotExecutable() {
        listOf(
            AgentControlledActionType.PREPARE_MESSAGE_TEXT,
            AgentControlledActionType.PREPARE_AUDIO_SCRIPT
        ).forEach { type ->
            val evaluation = AgentControlledActionPolicy.evaluate(type)
            assertEquals(AgentControlledActionRisk.HIGH, evaluation.riskLevel, "type=$type")
            assertFalse(evaluation.allowedToExecuteNow, "type=$type")
            assertEquals(
                AgentControlledActionStatus.READY_BUT_NOT_EXECUTED,
                evaluation.status,
                "type=$type"
            )
        }
    }

    @Test
    fun reviewPaymentAndPriceAreHighAndRequireConfirmation() {
        listOf(
            AgentControlledActionType.REVIEW_PAYMENT_METHOD,
            AgentControlledActionType.REVIEW_RIDE_PRICE
        ).forEach { type ->
            val evaluation = AgentControlledActionPolicy.evaluate(type)
            assertEquals(AgentControlledActionRisk.HIGH, evaluation.riskLevel, "type=$type")
            assertTrue(evaluation.requiresConfirmation, "type=$type")
            assertFalse(evaluation.allowedToExecuteNow, "type=$type")
        }
    }

    @Test
    fun finalConfirmActionsAreCriticalAndBlocked() {
        listOf(
            AgentControlledActionType.FINAL_CONFIRM_RIDE,
            AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE,
            AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO
        ).forEach { type ->
            val evaluation = AgentControlledActionPolicy.evaluate(type)
            assertEquals(AgentControlledActionRisk.CRITICAL, evaluation.riskLevel, "type=$type")
            assertEquals(AgentControlledActionStatus.BLOCKED, evaluation.status, "type=$type")
            assertFalse(evaluation.allowedToExecuteNow, "type=$type")
            assertTrue(evaluation.blockedReason != null, "type=$type")
        }
    }

    @Test
    fun waitForUserInputIsLowRiskAndWaiting() {
        val evaluation = AgentControlledActionPolicy.evaluate(
            AgentControlledActionType.WAIT_FOR_USER_INPUT
        )
        assertEquals(AgentControlledActionRisk.LOW, evaluation.riskLevel)
        assertEquals(AgentControlledActionStatus.WAITING_FOR_USER, evaluation.status)
        assertFalse(evaluation.allowedToExecuteNow)
    }

    @Test
    fun blockedSensitiveActionIsCriticalBlocked() {
        val evaluation = AgentControlledActionPolicy.evaluate(
            AgentControlledActionType.BLOCKED_SENSITIVE_ACTION
        )
        assertEquals(AgentControlledActionRisk.CRITICAL, evaluation.riskLevel)
        assertEquals(AgentControlledActionStatus.BLOCKED, evaluation.status)
        assertFalse(evaluation.allowedToExecuteNow)
    }
}
