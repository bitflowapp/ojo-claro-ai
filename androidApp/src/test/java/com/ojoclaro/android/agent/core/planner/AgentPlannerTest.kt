package com.ojoclaro.android.agent.core.planner

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlot
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.agent.core.AgentContextSnapshot
import com.ojoclaro.android.agent.core.AgentDecision
import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.AgentGoal
import com.ojoclaro.android.agent.core.AgentRiskLevel
import com.ojoclaro.android.agent.core.AgentScreenContext
import com.ojoclaro.android.agent.core.AgentToolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentPlannerTest {

    private val planner = AgentPlanner()
    private val now = 100L

    private fun ctx(
        mode: AgentExecutionMode = AgentExecutionMode.ACCESSIBILITY_VOICE,
        screen: AgentScreenContext? = null,
        isInEmergency: Boolean = false
    ) = AgentContextSnapshot(
        mode = mode,
        agentState = AgentState.IDLE,
        screen = screen,
        nowMillis = now,
        isInEmergency = isInEmergency
    )

    private fun goal(intent: AgentIntent, text: String = "x"): AgentGoal = AgentGoal(
        rawText = text,
        normalizedText = text,
        primaryIntent = intent
    )

    @Test
    fun unknownIntentReturnsUnknownDecision() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.UNKNOWN,
            slots = emptyList(),
            rawText = "blah",
            confidence = 0.1f
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.UNKNOWN, "blah"),
            parsedIntent = parsed,
            context = ctx(),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.Unknown)
    }

    @Test
    fun bankingScreenBlocksGeneralActions() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.OPEN_MAPS,
            slots = emptyList(),
            rawText = "abrí maps",
            confidence = 0.95f
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.OPEN_MAPS, "abrí maps"),
            parsedIntent = parsed,
            context = ctx(screen = AgentScreenContext(isBankingScreen = true)),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.Rejected)
        decision as AgentDecision.Rejected
        assertEquals("screen_hot_zone", decision.reason)
    }

    @Test
    fun passwordFieldScreenBlocksGeneralActions() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "Sofi", 0.95f),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "estoy llegando", 0.9f)
            ),
            rawText = "mandale a Sofi",
            confidence = 0.95f,
            requiresConfirmation = true
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.COMPOSE_WHATSAPP_MESSAGE),
            parsedIntent = parsed,
            context = ctx(screen = AgentScreenContext(containsPasswordField = true)),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.Rejected)
    }

    @Test
    fun safeIntentsAllowedEvenOnHotZone() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.REPEAT_LAST,
            slots = emptyList(),
            rawText = "repetí",
            confidence = 0.95f
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.REPEAT_LAST),
            parsedIntent = parsed,
            context = ctx(screen = AgentScreenContext(isBankingScreen = true)),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.ExecutePlan)
    }

    @Test
    fun unsafeMessagePayloadIsRejected() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "Sofi", 0.95f),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "mi clave es 1234abcd", 0.9f)
            ),
            rawText = "mandale a sofi mi clave es 1234abcd",
            confidence = 0.9f,
            requiresConfirmation = true
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.COMPOSE_WHATSAPP_MESSAGE),
            parsedIntent = parsed,
            context = ctx(),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.Rejected)
        decision as AgentDecision.Rejected
        assertTrue(
            decision.reason.endsWith("message_payload_unsafe"),
            "reason should mention message_payload_unsafe, got: ${decision.reason}"
        )
    }

    @Test
    fun missingContactAsksForSlot() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = emptyList(),
            rawText = "mandale un mensaje",
            confidence = 0.8f,
            missingSlots = listOf(AgentSlotName.CONTACT_NAME)
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.COMPOSE_WHATSAPP_MESSAGE),
            parsedIntent = parsed,
            context = ctx(),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.AskForSlot)
        decision as AgentDecision.AskForSlot
        assertEquals(AgentSlotName.CONTACT_NAME, decision.slot)
        assertEquals(AgentToolId.WHATSAPP, decision.toolId)
    }

    @Test
    fun composeMessageAsksForConfirmationAndDoesNotAutoSend() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "Sofi", 0.95f),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "estoy llegando", 0.9f)
            ),
            rawText = "mandale a Sofi que estoy llegando",
            confidence = 0.95f,
            requiresConfirmation = true
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.COMPOSE_WHATSAPP_MESSAGE),
            parsedIntent = parsed,
            context = ctx(),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.AskForConfirmation)
        decision as AgentDecision.AskForConfirmation
        val step = decision.plan.currentStep
        assertEquals(AgentToolId.WHATSAPP, step.toolId)
        assertTrue(step.spokenPrompt.contains("No lo envío automáticamente"))
        assertTrue(decision.plan.requiresStepByStepConfirmation)
    }

    @Test
    fun multiStepWhatsAppThenMapsIsAllowedAndSupervised() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "Sofi", 0.95f),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "estoy saliendo", 0.9f)
            ),
            rawText = "Avisale a Sofi que estoy saliendo y después abrí Maps para volver a casa",
            confidence = 0.92f,
            requiresConfirmation = true
        )
        val twoStepGoal = AgentGoal(
            rawText = parsed.rawText,
            normalizedText = parsed.rawText,
            primaryIntent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            secondaryIntents = listOf(AgentIntent.OPEN_MAPS)
        )
        val decision = planner.plan(
            goal = twoStepGoal,
            parsedIntent = parsed,
            context = ctx(),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.AskForConfirmation)
        decision as AgentDecision.AskForConfirmation
        assertEquals(2, decision.plan.steps.size)
        assertEquals(AgentToolId.WHATSAPP, decision.plan.steps[0].toolId)
        assertEquals(AgentToolId.MAPS, decision.plan.steps[1].toolId)
        assertTrue(decision.plan.requiresStepByStepConfirmation)
        assertTrue(decision.plan.steps[1].requiresConfirmation)
    }

    @Test
    fun moreThanTwoStepsIsRejected() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.OPEN_WHATSAPP,
            slots = emptyList(),
            rawText = "x",
            confidence = 0.8f
        )
        val tooMany = AgentGoal(
            rawText = "x",
            normalizedText = "x",
            primaryIntent = AgentIntent.OPEN_WHATSAPP,
            secondaryIntents = listOf(
                AgentIntent.OPEN_MAPS,
                AgentIntent.CALL_CONTACT
            )
        )
        val decision = planner.plan(
            goal = tooMany,
            parsedIntent = parsed,
            context = ctx(),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.Rejected)
        decision as AgentDecision.Rejected
        assertEquals("chain_too_long", decision.reason)
    }

    @Test
    fun openMapsLowRiskRequiresConfirmation() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.OPEN_MAPS,
            slots = emptyList(),
            rawText = "abrí mapas",
            confidence = 0.95f
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.OPEN_MAPS),
            parsedIntent = parsed,
            context = ctx(),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.AskForConfirmation)
        val step = (decision as AgentDecision.AskForConfirmation).plan.currentStep
        assertEquals(AgentRiskLevel.LOW, step.risk)
    }

    @Test
    fun repeatLastNoConfirmationDirectExecute() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.REPEAT_LAST,
            slots = emptyList(),
            rawText = "repetí",
            confidence = 0.95f
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.REPEAT_LAST),
            parsedIntent = parsed,
            context = ctx(),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.ExecutePlan)
        val plan = (decision as AgentDecision.ExecutePlan).plan
        assertEquals(AgentToolId.REPEAT_LAST, plan.currentStep.toolId)
        assertEquals(false, plan.currentStep.requiresConfirmation)
    }

    @Test
    fun emergencyEnabledToolsExist() {
        // Asegurar que el planner puede recomendar tools de emergencia en modo
        // EMERGENCY (Phone, WhatsApp y Maps están habilitados).
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.OPEN_PHONE,
            slots = emptyList(),
            rawText = "abrí el teléfono",
            confidence = 0.95f
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.OPEN_PHONE),
            parsedIntent = parsed,
            context = ctx(mode = AgentExecutionMode.EMERGENCY),
            nowMillis = now
        )
        assertNotNull(decision)
        assertTrue(decision is AgentDecision.AskForConfirmation || decision is AgentDecision.ExecutePlan)
    }

    @Test
    fun safetyPolicyRejectionPropagates() {
        // Un intent con confidence inválida sale por SafetyPolicy.
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.OPEN_PHONE,
            slots = emptyList(),
            rawText = "abrí el teléfono",
            confidence = 2.0f
        )
        val decision = planner.plan(
            goal = goal(AgentIntent.OPEN_PHONE),
            parsedIntent = parsed,
            context = ctx(),
            nowMillis = now
        )
        assertTrue(decision is AgentDecision.Rejected)
        decision as AgentDecision.Rejected
        assertTrue(decision.reason.startsWith("safety_policy_"))
    }
}
