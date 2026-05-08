package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmSafetyPolicyTest {

    @Test
    fun whatsappMapsAndPhoneAreForcedToManualExecution() {
        val response = LlmAgentResponse(
            intent = AgentIntent.OPEN_WHATSAPP_CHAT,
            confidence = 0.97f,
            contactName = "ContactoDemo",
            messageText = null,
            proposedMessage = null,
            destination = null,
            locationAlias = null,
            routineName = null,
            pendingTask = null,
            missingSlots = emptyList(),
            userFacingQuestion = null,
            suggestionText = null,
            requiresConfirmation = true,
            shouldExecuteImmediately = true,
            safetyNotes = "Test"
        )

        val coerced = LlmSafetyPolicy.coerce(response)
        assertFalse(coerced.shouldExecuteImmediately)
        assertTrue(LlmSafetyPolicy.requiresManualReview(coerced).not())
    }

    @Test
    fun forbiddenTokensAreDetected() {
        assertTrue(LlmSafetyPolicy.isForbiddenAction("read_contacts"))
        assertTrue(LlmSafetyPolicy.isForbiddenAction("action_call"))
        assertTrue(LlmSafetyPolicy.isForbiddenAction("banco"))
        assertFalse(LlmSafetyPolicy.isForbiddenAction("calendar"))
    }
}

