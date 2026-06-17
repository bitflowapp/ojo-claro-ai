package com.ojoclaro.android.agent.core.chain

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.core.AgentGoal
import com.ojoclaro.android.agent.core.AgentPlan
import com.ojoclaro.android.agent.core.AgentPlanStatus
import com.ojoclaro.android.agent.core.AgentPlanStep
import com.ojoclaro.android.agent.core.AgentRiskLevel
import com.ojoclaro.android.agent.core.AgentToolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChainedActionSessionTest {

    private fun twoStepPlan(now: Long = 100L): AgentPlan = AgentPlan(
        id = "plan-1",
        goal = AgentGoal(
            rawText = "Avisale a Sofi y abrí Maps",
            normalizedText = "avisale a sofi y abrí maps",
            primaryIntent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            secondaryIntents = listOf(AgentIntent.OPEN_MAPS)
        ),
        steps = listOf(
            AgentPlanStep(
                id = "s1",
                toolId = AgentToolId.WHATSAPP,
                description = "Preparar WhatsApp",
                risk = AgentRiskLevel.MEDIUM,
                requiresConfirmation = true,
                spokenPrompt = "Voy a preparar WhatsApp. No envío automáticamente.",
                confirmationPrompt = "Confirmá para abrir WhatsApp."
            ),
            AgentPlanStep(
                id = "s2",
                toolId = AgentToolId.MAPS,
                description = "Abrir Maps a casa",
                risk = AgentRiskLevel.LOW,
                requiresConfirmation = true,
                spokenPrompt = "Después abro Maps a casa.",
                confirmationPrompt = "Confirmá para abrir Maps a casa."
            )
        ),
        status = AgentPlanStatus.PENDING,
        createdAtMillis = now,
        updatedAtMillis = now
    )

    @Test
    fun startMovesToAwaitingFirstConfirmation() {
        val session = ChainedActionSession()
        val event = session.start(twoStepPlan(), nowMillis = 100L)
        assertTrue(event is ChainedActionEvent.PresentingStep)
        val presenting = event as ChainedActionEvent.PresentingStep
        assertEquals(AgentPlanStatus.AWAITING_FIRST_CONFIRMATION, presenting.plan.status)
        assertTrue(presenting.spokenText.contains("WhatsApp"))
    }

    @Test
    fun confirmTransitionsToExecutingStep() {
        val session = ChainedActionSession()
        session.start(twoStepPlan(), 100L)
        val event = session.confirmCurrentStep(200L)
        assertTrue(event is ChainedActionEvent.ExecutingStep)
        assertEquals(AgentPlanStatus.EXECUTING_STEP, (event as ChainedActionEvent.ExecutingStep).plan.status)
    }

    @Test
    fun externalStepExecutionMovesToAwaitingReturn() {
        val session = ChainedActionSession()
        session.start(twoStepPlan(), 100L)
        session.confirmCurrentStep(200L)
        val event = session.onStepExecutedExternally(300L)
        assertTrue(event is ChainedActionEvent.AwaitingReturn)
        val await = event as ChainedActionEvent.AwaitingReturn
        assertEquals(1, await.plan.currentStepIndex)
        assertEquals(AgentPlanStatus.AWAITING_NEXT_STEP_CONFIRMATION, await.plan.status)
        assertTrue(await.spokenText.contains("Maps"))
    }

    @Test
    fun completingFinalStepProducesPlanCompleted() {
        val session = ChainedActionSession()
        session.start(twoStepPlan(), 100L)
        session.confirmCurrentStep(200L)
        session.onStepExecutedExternally(300L)
        session.confirmCurrentStep(400L)
        val event = session.onStepExecutedExternally(500L)
        assertTrue(event is ChainedActionEvent.PlanCompleted)
        assertEquals(AgentPlanStatus.COMPLETED, (event as ChainedActionEvent.PlanCompleted).plan.status)
    }

    @Test
    fun cancelClearsActivePlan() {
        val session = ChainedActionSession()
        session.start(twoStepPlan(), 100L)
        val event = session.cancel(200L)
        assertEquals(ChainedActionEvent.Cancelled, event)
        assertNull(session.activePlan)
    }

    @Test
    fun expireMarksExpiredAndClears() {
        val session = ChainedActionSession()
        session.start(twoStepPlan(), 100L)
        val event = session.expireIfNeeded(nowMillis = 100L + 10 * 60 * 1000L)
        assertEquals(ChainedActionEvent.Expired, event)
        assertNull(session.activePlan)
    }

    @Test
    fun expireDoesNothingWhenFresh() {
        val session = ChainedActionSession()
        session.start(twoStepPlan(), 100L)
        val event = session.expireIfNeeded(nowMillis = 100L + 1000L)
        assertEquals(ChainedActionEvent.StillFresh, event)
        assertNotNull(session.activePlan)
    }

    @Test
    fun confirmWithoutActivePlanIsNoActivePlan() {
        val session = ChainedActionSession()
        val event = session.confirmCurrentStep(100L)
        assertEquals(ChainedActionEvent.NoActivePlan, event)
    }

    @Test
    fun continuationPolicyOnAwaitingNextReturnsWait() {
        val session = ChainedActionSession()
        session.start(twoStepPlan(), 100L)
        session.confirmCurrentStep(200L)
        session.onStepExecutedExternally(300L)
        val decision = PlanContinuationPolicy.decide(
            session.activePlan!!,
            nowMillis = 400L
        )
        assertTrue(decision is PlanContinuationDecision.Wait)
    }

    @Test
    fun continuationPolicyExpiresStalePlans() {
        val session = ChainedActionSession()
        session.start(twoStepPlan(), 100L)
        val decision = PlanContinuationPolicy.decide(
            session.activePlan!!,
            nowMillis = 100L + 10 * 60 * 1000L
        )
        assertTrue(decision is PlanContinuationDecision.Expire)
    }
}
