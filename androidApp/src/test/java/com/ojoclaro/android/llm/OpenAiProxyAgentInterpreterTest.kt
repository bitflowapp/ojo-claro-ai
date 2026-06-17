package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class OpenAiProxyAgentInterpreterTest {

    @Test
    fun parsesProxyJsonAndForcesSensitiveActionsOff() = runTest {
        val interpreter = OpenAiProxyAgentInterpreter(
            config = LlmAgentClientConfig(
                baseUrl = "http://127.0.0.1:8787",
                enabled = true
            ),
            networkClient = FakeNetworkClient(
                LlmHttpResponse(
                    statusCode = 200,
                    body = LlmAgentJsonContract.responseToJson(
                        LlmAgentResponse(
                            intent = AgentIntent.OPEN_WHATSAPP_CHAT,
                            confidence = 0.93f,
                            contactName = "ContactoDemo",
                            messageText = null,
                            proposedMessage = null,
                            destination = null,
                            locationAlias = null,
                            routineName = null,
                            pendingTask = null,
                            missingSlots = emptyList(),
                            userFacingQuestion = "¿Abrir chat con ContactoDemo o mandarle un mensaje?",
                            suggestionText = null,
                            requiresConfirmation = true,
                            shouldExecuteImmediately = true,
                            safetyNotes = "ok"
                        )
                    )
                )
            )
        )

        val response = interpreter.interpret(
            LlmAgentRequest(
                originalText = "decile a ContactoDemo que llego tarde",
                normalizedText = "decir a ContactoDemo que llego tarde",
                locale = "es-AR",
                agentState = AgentState.WAITING_MESSAGE,
                externalApp = "WhatsApp",
                memorySummary = "ContactoDemo ContactoDemo.",
                knownSafeContacts = listOf("ContactoDemo"),
                knownPlaces = listOf("casa", "laburo"),
                activePendingTasks = emptyList(),
                allowedIntents = listOf(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, AgentIntent.OPEN_WHATSAPP_CHAT),
                forbiddenActions = listOf("read_contacts", "call_phone")
            )
        )

        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, response.intent)
        assertEquals("ContactoDemo", response.contactName)
        assertFalse(response.shouldExecuteImmediately)
        assertTrue(response.confidence > 0.8f)
    }

    @Test
    fun invalidJsonFallsBackSafely() = runTest {
        val interpreter = OpenAiProxyAgentInterpreter(
            config = LlmAgentClientConfig(
                baseUrl = "http://127.0.0.1:8787",
                enabled = true
            ),
            networkClient = FakeNetworkClient(
                LlmHttpResponse(
                    statusCode = 200,
                    body = "not json"
                )
            )
        )

        val response = interpreter.interpret(
            LlmAgentRequest(
                originalText = "algo raro",
                normalizedText = "algo raro",
                locale = "es-AR",
                agentState = AgentState.IDLE,
                externalApp = null,
                memorySummary = "",
                knownSafeContacts = emptyList(),
                knownPlaces = emptyList(),
                activePendingTasks = emptyList(),
                allowedIntents = emptyList(),
                forbiddenActions = emptyList()
            )
        )

        assertEquals(0f, response.confidence)
        assertNull(response.intent)
        assertTrue(response.safetyNotes.orEmpty().contains("JSON", ignoreCase = true))
    }

    private class FakeNetworkClient(
        private val response: LlmHttpResponse
    ) : LlmAgentNetworkClient {
        override suspend fun postJson(
            url: String,
            jsonBody: String,
            timeoutMillis: Long,
            headers: Map<String, String>
        ): LlmHttpResponse = response
    }
}

