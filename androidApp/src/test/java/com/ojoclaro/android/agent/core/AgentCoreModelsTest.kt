package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AgentCoreModelsTest {

    private fun whatsAppTool() = AgentTool(
        id = AgentToolId.WHATSAPP,
        displayName = "WhatsApp",
        description = "Prepara mensaje para WhatsApp; nunca envía solo.",
        capabilities = setOf(
            AgentToolCapability.OPENS_EXTERNAL_APP,
            AgentToolCapability.PREPARES_MESSAGE_WITHOUT_SENDING,
            AgentToolCapability.REQUIRES_CONFIRMATION,
            AgentToolCapability.NEVER_AUTO_COMPLETES
        ),
        requiredSlots = setOf(AgentSlotName.CONTACT_NAME, AgentSlotName.MESSAGE_TEXT),
        risk = AgentRiskLevel.MEDIUM,
        requiresConfirmation = true,
        supportedModes = setOf(
            AgentExecutionMode.ACCESSIBILITY_VOICE,
            AgentExecutionMode.SIGHTED
        ),
        emergencyCapable = false,
        coversIntents = setOf(
            AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            AgentIntent.OPEN_WHATSAPP
        )
    )

    @Test
    fun toolForbidsBlockedWithConfirmation() {
        val ex = runCatching {
            AgentTool(
                id = AgentToolId.GENERIC_APP,
                displayName = "X",
                description = "Y",
                capabilities = emptySet(),
                requiredSlots = emptySet(),
                risk = AgentRiskLevel.BLOCKED,
                requiresConfirmation = true,
                supportedModes = setOf(AgentExecutionMode.ACCESSIBILITY_VOICE),
                emergencyCapable = false,
                coversIntents = emptySet()
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun toolMustSupportAtLeastOneMode() {
        val ex = runCatching {
            AgentTool(
                id = AgentToolId.WHATSAPP,
                displayName = "X",
                description = "Y",
                capabilities = emptySet(),
                requiredSlots = emptySet(),
                risk = AgentRiskLevel.LOW,
                requiresConfirmation = false,
                supportedModes = emptySet(),
                emergencyCapable = false,
                coversIntents = emptySet()
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun toolIsAvailableInSupportedModeOnly() {
        val tool = whatsAppTool()
        assertTrue(tool.isAvailableIn(AgentExecutionMode.ACCESSIBILITY_VOICE))
        assertTrue(tool.isAvailableIn(AgentExecutionMode.SIGHTED))
        assertFalse(tool.isAvailableIn(AgentExecutionMode.EMERGENCY))
    }

    @Test
    fun blockedToolIsNeverAvailable() {
        val tool = AgentTool(
            id = AgentToolId.GENERIC_APP,
            displayName = "Generic",
            description = "Reservado",
            capabilities = setOf(AgentToolCapability.FORBIDDEN_ON_BANKING),
            requiredSlots = emptySet(),
            risk = AgentRiskLevel.BLOCKED,
            requiresConfirmation = false,
            supportedModes = setOf(
                AgentExecutionMode.ACCESSIBILITY_VOICE,
                AgentExecutionMode.SIGHTED,
                AgentExecutionMode.EMERGENCY
            ),
            emergencyCapable = false,
            coversIntents = emptySet()
        )
        assertFalse(tool.isAvailableIn(AgentExecutionMode.ACCESSIBILITY_VOICE))
        assertFalse(tool.isAvailableIn(AgentExecutionMode.SIGHTED))
        assertFalse(tool.isAvailableIn(AgentExecutionMode.EMERGENCY))
    }

    @Test
    fun planStepBlockedIsForbidden() {
        val ex = runCatching {
            AgentPlanStep(
                id = "s1",
                toolId = AgentToolId.WHATSAPP,
                description = "x",
                risk = AgentRiskLevel.BLOCKED,
                requiresConfirmation = false,
                spokenPrompt = "x"
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun planStepConfirmationPromptRequiredIfFlagged() {
        val ex = runCatching {
            AgentPlanStep(
                id = "s1",
                toolId = AgentToolId.WHATSAPP,
                description = "x",
                risk = AgentRiskLevel.MEDIUM,
                requiresConfirmation = true,
                spokenPrompt = "x",
                confirmationPrompt = null
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun planStepWithoutMissingSlotsIsReady() {
        val step = AgentPlanStep(
            id = "s1",
            toolId = AgentToolId.MAPS,
            description = "Navegar a casa",
            slotValues = mapOf(AgentSlotName.DESTINATION to "casa"),
            risk = AgentRiskLevel.LOW,
            requiresConfirmation = true,
            spokenPrompt = "Voy a abrir Maps a casa.",
            confirmationPrompt = "Confirmá para abrir Maps a casa."
        )
        assertTrue(step.isReady)
        assertEquals("casa", step.slotValue(AgentSlotName.DESTINATION))
    }

    @Test
    fun planMustHaveAtLeastOneStep() {
        val ex = runCatching {
            AgentPlan(
                id = "p1",
                goal = goal(),
                steps = emptyList(),
                status = AgentPlanStatus.PENDING,
                createdAtMillis = 0,
                updatedAtMillis = 0
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun planWithSensitiveStepsForcesStepByStepConfirmation() {
        val ex = runCatching {
            AgentPlan(
                id = "p1",
                goal = goal(),
                steps = listOf(
                    AgentPlanStep(
                        id = "s1",
                        toolId = AgentToolId.WHATSAPP,
                        description = "x",
                        risk = AgentRiskLevel.MEDIUM,
                        requiresConfirmation = true,
                        spokenPrompt = "x",
                        confirmationPrompt = "y"
                    )
                ),
                status = AgentPlanStatus.PENDING,
                requiresStepByStepConfirmation = false,
                createdAtMillis = 0,
                updatedAtMillis = 0
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun planAdvanceMovesToNextStepAndUpdatesTimestamp() {
        val plan = twoStepPlan(now = 100L)
        val advanced = plan.advance(nowMillis = 200L)

        assertEquals(1, advanced.currentStepIndex)
        assertEquals(AgentPlanStatus.AWAITING_NEXT_STEP_CONFIRMATION, advanced.status)
        assertTrue(advanced.isFinalStep)
        assertEquals(200L, advanced.updatedAtMillis)
    }

    @Test
    fun planAdvancePastFinalStepIsForbidden() {
        val plan = twoStepPlan(now = 100L).advance(nowMillis = 200L)
        try {
            plan.advance(nowMillis = 300L)
            fail("expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // ok
        }
    }

    @Test
    fun goalConfidenceMustBeNormalized() {
        val ex = runCatching {
            AgentGoal(
                rawText = "x",
                normalizedText = "x",
                primaryIntent = AgentIntent.HELP,
                confidence = 1.5f
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun preferenceWithInferredPendingIsNotApplicable() {
        val pref = AgentUserPreference(
            key = AgentPreferenceKeys.HOME_DESTINATION,
            value = "casa",
            source = AgentPreferenceSource.INFERRED_PENDING_CONFIRMATION,
            updatedAtMillis = 0L
        )
        assertFalse(pref.isApplicable)
    }

    @Test
    fun preferenceKeyLookupReturnsNullWhenMissing() {
        val memoryContext = AgentMemoryContext(
            activePreferences = listOf(
                AgentUserPreference(
                    key = AgentPreferenceKeys.RESPONSE_LENGTH,
                    value = "short",
                    source = AgentPreferenceSource.USER_EXPLICIT,
                    updatedAtMillis = 0L
                )
            )
        )
        assertEquals("short", memoryContext.preference(AgentPreferenceKeys.RESPONSE_LENGTH)?.value)
        assertNull(memoryContext.preference("nonexistent"))
    }

    @Test
    fun screenContextSensitiveBlocksGeneralActions() {
        val safeScreen = AgentScreenContext(packageName = "com.example", shortSummary = "x")
        val bankScreen = AgentScreenContext(packageName = "com.bank", isBankingScreen = true)
        val pwScreen = AgentScreenContext(packageName = "com.app", containsPasswordField = true)

        assertFalse(safeScreen.shouldBlockGeneralActions)
        assertTrue(bankScreen.shouldBlockGeneralActions)
        assertTrue(pwScreen.shouldBlockGeneralActions)
    }

    private fun goal() = AgentGoal(
        rawText = "test",
        normalizedText = "test",
        primaryIntent = AgentIntent.HELP
    )

    private fun twoStepPlan(now: Long): AgentPlan = AgentPlan(
        id = "p1",
        goal = goal(),
        steps = listOf(
            AgentPlanStep(
                id = "s1",
                toolId = AgentToolId.WHATSAPP,
                description = "Preparar WhatsApp",
                risk = AgentRiskLevel.MEDIUM,
                requiresConfirmation = true,
                spokenPrompt = "Preparo WhatsApp.",
                confirmationPrompt = "Confirmá para abrir WhatsApp."
            ),
            AgentPlanStep(
                id = "s2",
                toolId = AgentToolId.MAPS,
                description = "Abrir Maps a casa",
                risk = AgentRiskLevel.LOW,
                requiresConfirmation = true,
                spokenPrompt = "Abro Maps a casa.",
                confirmationPrompt = "Confirmá para abrir Maps a casa."
            )
        ),
        status = AgentPlanStatus.PENDING,
        createdAtMillis = now,
        updatedAtMillis = now
    )
}
