package com.ojoclaro.android.agent.estela

import ai.ojoclaro.adapter.LlmIntentAdapter
import ai.ojoclaro.router.EmptyIntentRouteHandler
import ai.ojoclaro.router.IntentRouter
import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.llm.EstelaIntentClient
import com.ojoclaro.android.llm.EstelaIntentEngine
import com.ojoclaro.android.llm.EstelaIntentRequest
import com.ojoclaro.android.llm.EstelaIntentRequestBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the new `/intent` entry point on [EstelaAgentRuntime].
 *
 * The legacy [EstelaAgentRuntime.handle] path is covered by
 * [EstelaAgentRuntimeTest]; we deliberately leave that file alone. Here we
 * just verify that handleViaIntent delegates to [EstelaIntentEngine] (which
 * already owns the full LLM→Adapter→Router chain and has its own tests in
 * EstelaIntentEngineTest).
 */
class EstelaAgentRuntimeIntentPathTest {

    @Test
    fun handleViaIntentReturnsNullWhenEngineNotWired() = runTest {
        val runtime = EstelaAgentRuntime(intentEngine = null)

        val result = runtime.handleViaIntent("abrime WhatsApp")

        assertNull(result, "without engine the runtime should fall back to legacy")
    }

    @Test
    fun handleViaIntentDelegatesToEngineAndReturnsSpokenText() = runTest {
        val rawJson = """
            {
              "intent": "open_phone",
              "confidence": 0.95,
              "params": {},
              "safety_level": "allow_safe",
              "voice_response": "Abro el marcador.",
              "voice_response_template": null,
              "raw_text": "abrí el teléfono"
            }
        """.trimIndent()
        val runtime = EstelaAgentRuntime(
            intentEngine = engineWithFakeClient(rawJson)
        )

        val result = runtime.handleViaIntent("abrí el teléfono")

        assertTrue(result != null, "expected the runtime to delegate to the engine")
        assertEquals("Abro el marcador.", result.spokenText)
    }

    @Test
    fun legacyHandleStillWorksWhenIntentPathIsAlsoWired() {
        // Regression: the legacy local planner must keep working even when the
        // /intent engine is present. Both entry points coexist.
        val runtime = EstelaAgentRuntime(
            intentEngine = engineWithFakeClient(rawJson = "{not json}"),
            clockMillis = { 1234L }
        )

        val result = runtime.handle("abrí WhatsApp")

        assertTrue(result.handled, "legacy local path must still handle 'abrí WhatsApp'")
    }

    private fun engineWithFakeClient(rawJson: String): EstelaIntentEngine {
        val manager = AgentConversationManager()
        val router = IntentRouter(handler = EmptyIntentRouteHandler())
        val adapter = LlmIntentAdapter(
            intentRouter = router,
            conversationManager = manager
        )
        return EstelaIntentEngine(
            client = StaticEstelaIntentClient(rawJson),
            requestBuilder = EstelaIntentRequestBuilder(),
            conversationManager = manager,
            adapter = adapter
        )
    }
}

/** Returns the same canned JSON for every request — no network. */
private class StaticEstelaIntentClient(private val body: String) : EstelaIntentClient {
    override suspend fun classifyIntent(request: EstelaIntentRequest): Result<String> =
        Result.success(body)
}
