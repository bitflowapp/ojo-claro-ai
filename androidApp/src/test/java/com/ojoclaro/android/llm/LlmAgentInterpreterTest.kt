package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LlmAgentInterpreterTest {

    @Test
    fun disabledInterpreterReturnsUnavailableResponse() = runTest {
        val response = DisabledLlmAgentInterpreter().interpret(
            LlmAgentRequest(
                originalText = "decile a Sofi que llego tarde",
                normalizedText = "decir a sofi que llego tarde",
                locale = "es-AR",
                agentState = AgentState.WAITING_MESSAGE,
                externalApp = "WhatsApp",
                memorySummary = "Contacto Sofi.",
                knownSafeContacts = listOf("Sofi"),
                knownPlaces = listOf("laburo"),
                activePendingTasks = listOf("Responderle a Marco"),
                allowedIntents = listOf(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, AgentIntent.OPEN_MAPS),
                forbiddenActions = listOf("read_contacts", "call_phone")
            )
        )

        assertEquals(null, response.intent)
        assertEquals(0f, response.confidence)
        assertFalse(response.shouldExecuteImmediately)
        assertNotNull(response.userFacingQuestion)
        assertTrue(response.safetyNotes.orEmpty().contains("deshabilitado", ignoreCase = true))
    }

    @Test
    fun contractModelKeepsFieldsAndSafetyPolicy() {
        val request = LlmAgentRequest(
            originalText = "abrir whatsapp principal",
            normalizedText = "abrir whatsapp principal",
            locale = "es-AR",
            agentState = AgentState.IDLE,
            externalApp = null,
            memorySummary = "Preferencia: respuestas cortas.",
            knownSafeContacts = listOf("Sofi", "Marco"),
            knownPlaces = listOf("laburo", "casa"),
            activePendingTasks = listOf("Responderle a Marco"),
            allowedIntents = listOf(AgentIntent.OPEN_WHATSAPP, AgentIntent.COMPOSE_WHATSAPP_MESSAGE),
            forbiddenActions = listOf("read_contacts", "action_call")
        )
        val response = LlmAgentResponse(
            intent = AgentIntent.OPEN_WHATSAPP_CHAT,
            responseType = "propose_open_app",
            confidence = 0.91f,
            contactName = "Marco",
            messageText = null,
            proposedMessage = null,
            destination = null,
            locationAlias = null,
            routineName = null,
            pendingTask = null,
            missingSlots = listOf("contactName"),
            userFacingQuestion = "Abrir chat con Marco o mandarle un mensaje?",
            suggestionText = null,
            requiresConfirmation = true,
            shouldExecuteImmediately = false,
            safetyNotes = "Local only."
        )
        val coerced = LlmSafetyPolicy.coerce(response)

        assertEquals("abrir whatsapp principal", request.originalText)
        assertEquals(listOf(AgentIntent.OPEN_WHATSAPP, AgentIntent.COMPOSE_WHATSAPP_MESSAGE), request.allowedIntents)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, coerced.intent)
        assertEquals("propose_open_app", coerced.responseType)
        assertEquals("Marco", coerced.contactName)
        assertEquals(listOf("contactName"), coerced.missingSlots)
        assertFalse(coerced.shouldExecuteImmediately)
    }

    @Test
    fun safetyPolicyForcesWhatsAppAndMapsToManualExecution() {
        val coerced = LlmSafetyPolicy.coerce(
            LlmAgentResponse(
                intent = AgentIntent.OPEN_WHATSAPP_CHAT,
                confidence = 0.94f,
                contactName = "Marco",
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
                safetyNotes = "Debe revisar el usuario."
            )
        )

        assertFalse(coerced.shouldExecuteImmediately)
        assertEquals(0.94f, coerced.confidence)
        assertTrue(LlmSafetyPolicy.requiresManualReview(coerced).not())
    }

    @Test
    fun lowConfidenceResponseNeedsManualReview() {
        val response = LlmAgentResponse(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            confidence = 0.5f,
            contactName = "Sofi",
            messageText = "llego tarde",
            proposedMessage = "Voy un poco demorado.",
            destination = null,
            locationAlias = null,
            routineName = null,
            pendingTask = null,
            missingSlots = emptyList(),
            userFacingQuestion = null,
            suggestionText = null,
            requiresConfirmation = true,
            shouldExecuteImmediately = false,
            safetyNotes = "Baja confianza."
        )

        assertTrue(LlmSafetyPolicy.requiresManualReview(response))
        assertTrue(LlmSafetyPolicy.isForbiddenAction("action_call"))
        assertTrue(LlmSafetyPolicy.isForbiddenAction("read_contacts"))
        assertFalse(LlmSafetyPolicy.isForbiddenAction("calendar"))
    }
}
