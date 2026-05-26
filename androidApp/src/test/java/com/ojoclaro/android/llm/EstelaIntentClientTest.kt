package com.ojoclaro.android.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EstelaIntentClientTest {

    @Test
    fun postsIntentRequestToConfiguredEndpoint() = runTest {
        val network = RecordingNetworkClient(
            response = LlmHttpResponse(
                statusCode = 200,
                body = """{"intent":"help","confidence":0.9,"params":{},"safety_level":"allow_safe","voice_response":"Decime.","voice_response_template":null,"raw_text":"ayuda"}"""
            )
        )
        val client = HttpEstelaIntentClient(
            config = LlmAgentClientConfig(baseUrl = "http://10.0.2.2:8787"),
            networkClient = network
        )

        val result = client.classifyIntent(
            EstelaIntentRequest(
                userText = "ayuda",
                conversationState = "idle"
            )
        )

        assertTrue(result.isSuccess)
        assertEquals("http://10.0.2.2:8787/intent", network.lastUrl)
        val payload = Json.parseToJsonElement(network.lastBody).jsonObject
        assertEquals("gpt-5.4-mini", payload["model"]?.jsonPrimitive?.content)
        assertEquals("ayuda", payload["input"]?.jsonObject?.get("user_text")?.jsonPrimitive?.content)
    }

    @Test
    fun httpFailureReturnsFailureResult() = runTest {
        val client = HttpEstelaIntentClient(
            config = LlmAgentClientConfig(baseUrl = "http://10.0.2.2:8787"),
            networkClient = RecordingNetworkClient(
                response = LlmHttpResponse(statusCode = 503, body = "unavailable")
            )
        )

        val result = client.classifyIntent(
            EstelaIntentRequest(userText = "abrime WhatsApp", conversationState = "idle")
        )

        assertTrue(result.isFailure)
    }

    private class RecordingNetworkClient(
        private val response: LlmHttpResponse
    ) : LlmAgentNetworkClient {
        var lastUrl: String = ""
            private set
        var lastBody: String = ""
            private set

        override suspend fun postJson(
            url: String,
            jsonBody: String,
            timeoutMillis: Long,
            headers: Map<String, String>
        ): LlmHttpResponse {
            lastUrl = url
            lastBody = jsonBody
            return response
        }
    }
}
