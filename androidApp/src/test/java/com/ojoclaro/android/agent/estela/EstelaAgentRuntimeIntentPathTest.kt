package com.ojoclaro.android.agent.estela

import ai.ojoclaro.adapter.LlmIntentAdapter
import ai.ojoclaro.adapter.LlmIntentAdapterResult
import ai.ojoclaro.router.EmptyIntentRouteHandler
import ai.ojoclaro.router.IntentRouter
import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.llm.EstelaIntentClient
import com.ojoclaro.android.llm.EstelaIntentEngine
import com.ojoclaro.android.llm.EstelaIntentFallbackPhrases
import com.ojoclaro.android.llm.EstelaIntentNetworkException
import com.ojoclaro.android.llm.EstelaIntentRequest
import com.ojoclaro.android.llm.EstelaIntentRequestBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun handleViaIntentSendsUserTextAndContextToIntentClient() = runTest {
        val manager = AgentConversationManager()
        manager.handle(LocalIntentParser().parse("mandale un mensaje"))
        val client = RecordingEstelaIntentClient(rawJson = helpJson())
        val runtime = EstelaAgentRuntime(
            intentEngine = engine(
                client = client,
                manager = manager,
                requestBuilder = EstelaIntentRequestBuilder(
                    installedAppsProvider = { listOf("whatsapp", "uber") },
                    memoryContactsProvider = { listOf("mama", "Marco") },
                    activeAppProvider = { "com.whatsapp" },
                    permissionsProvider = {
                        mapOf(
                            "fine_location" to true,
                            "camera" to false
                        )
                    }
                )
            )
        )

        runtime.handleViaIntent("a mama")

        val request = assertNotNull(client.lastRequest)
        assertEquals("a mama", request.userText)
        assertEquals("waiting_contact", request.conversationState)
        assertEquals("compose_whatsapp_message", request.pendingAction?.intent)
        assertEquals(listOf("whatsapp", "uber"), request.installedApps)
        assertEquals(listOf("mama", "Marco"), request.memoryContacts)
        assertEquals("com.whatsapp", request.activeApp)
        assertEquals(true, request.permissionsGranted["fine_location"])
        assertEquals(false, request.permissionsGranted["camera"])
    }

    @Test
    fun handleViaIntentSlotFillReachesConversationManagerThroughAdapter() = runTest {
        val manager = AgentConversationManager()
        manager.handle(LocalIntentParser().parse("mandale un mensaje"))
        val runtime = EstelaAgentRuntime(
            intentEngine = engine(
                client = StaticEstelaIntentClient(
                    """
                    {
                      "intent": "slot_fill",
                      "confidence": 0.95,
                      "params": {"slot":"contact_query","value":"mama"},
                      "safety_level": "prepare_only",
                      "voice_response": null,
                      "voice_response_template": null,
                      "raw_text": "a mama"
                    }
                    """.trimIndent()
                ),
                manager = manager
            )
        )

        val result = assertNotNull(runtime.handleViaIntent("a mama"))

        assertTrue(result.adapterResult is LlmIntentAdapterResult.SlotFilled)
        assertEquals(AgentState.WAITING_MESSAGE, manager.currentState)
    }

    @Test
    fun handleViaIntentNetworkFailureReturnsSafeFallback() = runTest {
        val runtime = EstelaAgentRuntime(
            intentEngine = engine(
                client = FailingEstelaIntentClient(EstelaIntentNetworkException("offline"))
            )
        )

        val result = assertNotNull(runtime.handleViaIntent("abrime WhatsApp"))

        assertNull(result.adapterResult)
        assertEquals(EstelaIntentFallbackPhrases.NETWORK_ERROR, result.spokenText)
    }

    @Test
    fun handleViaIntentMalformedProxyResponseReturnsAdapterSafeFallback() = runTest {
        val runtime = EstelaAgentRuntime(
            intentEngine = engineWithFakeClient(rawJson = "{not json}")
        )

        val result = assertNotNull(runtime.handleViaIntent("abrime WhatsApp"))

        assertTrue(result.adapterResult is LlmIntentAdapterResult.SafeFallback)
        assertEquals("malformed_json", result.fallbackReason)
        assertEquals(EstelaIntentFallbackPhrases.SAFE_FALLBACK, result.spokenText)
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

    private fun engineWithFakeClient(rawJson: String): EstelaIntentEngine =
        engine(client = StaticEstelaIntentClient(rawJson))

    private fun engine(
        client: EstelaIntentClient,
        manager: AgentConversationManager = AgentConversationManager(),
        requestBuilder: EstelaIntentRequestBuilder = EstelaIntentRequestBuilder()
    ): EstelaIntentEngine {
        val router = IntentRouter(handler = EmptyIntentRouteHandler())
        val adapter = LlmIntentAdapter(
            intentRouter = router,
            conversationManager = manager
        )
        return EstelaIntentEngine(
            client = client,
            requestBuilder = requestBuilder,
            conversationManager = manager,
            adapter = adapter
        )
    }

    private fun helpJson(): String =
        """
        {
          "intent": "help",
          "confidence": 0.95,
          "params": {},
          "safety_level": "allow_safe",
          "voice_response": "Decime que necesitas.",
          "voice_response_template": null,
          "raw_text": "ayuda"
        }
        """.trimIndent()
}

/** Returns the same canned JSON for every request — no network. */
private class StaticEstelaIntentClient(private val body: String) : EstelaIntentClient {
    override suspend fun classifyIntent(request: EstelaIntentRequest): Result<String> =
        Result.success(body)
}

private class RecordingEstelaIntentClient(private val rawJson: String) : EstelaIntentClient {
    var lastRequest: EstelaIntentRequest? = null
        private set

    override suspend fun classifyIntent(request: EstelaIntentRequest): Result<String> {
        lastRequest = request
        return Result.success(rawJson)
    }
}

private class FailingEstelaIntentClient(private val error: Throwable) : EstelaIntentClient {
    override suspend fun classifyIntent(request: EstelaIntentRequest): Result<String> =
        Result.failure(error)
}
