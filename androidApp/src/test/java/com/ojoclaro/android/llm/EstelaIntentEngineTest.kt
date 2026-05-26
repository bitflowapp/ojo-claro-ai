package com.ojoclaro.android.llm

import ai.ojoclaro.adapter.LlmIntentAdapter
import ai.ojoclaro.adapter.LlmIntentAdapterResult
import ai.ojoclaro.consent.ConsentPhraseResolver
import ai.ojoclaro.router.AgentSafeExecutionDelegate
import ai.ojoclaro.router.ConversationAction
import ai.ojoclaro.router.ConversationActionType
import ai.ojoclaro.router.IntentRouteHandler
import ai.ojoclaro.router.IntentRouteRequest
import ai.ojoclaro.router.IntentRouter
import android.content.Intent
import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.LocalIntentParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EstelaIntentEngineTest {

    @Test
    fun validLlmJsonIsPassedToLlmIntentAdapter() = runTest {
        val handler = RecordingHandler()
        val engine = engine(
            client = StaticIntentClient(
                llmJson(
                    intent = "open_app",
                    params = """{"app_name":"spotify"}""",
                    voiceResponse = "Abro Spotify."
                )
            ),
            handler = handler
        )

        val result = engine.classifyAndRoute("abrime Spotify")

        assertTrue(result.adapterResult is LlmIntentAdapterResult.Routed)
        assertEquals(listOf("open_app"), handler.calls)
        assertEquals("Abro Spotify.", result.spokenText)
    }

    @Test
    fun malformedLlmJsonReturnsSafeFallbackAndDoesNotExecuteAction() = runTest {
        val handler = RecordingHandler()
        val engine = engine(
            client = StaticIntentClient("{not json"),
            handler = handler
        )

        val result = engine.classifyAndRoute("abrime Spotify")

        assertTrue(result.adapterResult is LlmIntentAdapterResult.SafeFallback)
        assertEquals("malformed_json", result.fallbackReason)
        assertEquals(emptyList(), handler.calls)
        assertEquals(EstelaIntentFallbackPhrases.SAFE_FALLBACK, result.spokenText)
    }

    @Test
    fun networkFailureReturnsSafeFallbackAndDoesNotExecuteAction() = runTest {
        val handler = RecordingHandler()
        val engine = engine(
            client = FailingIntentClient(EstelaIntentNetworkException("offline")),
            handler = handler
        )

        val result = engine.classifyAndRoute("abrime Spotify")

        assertEquals(null, result.adapterResult)
        assertEquals(emptyList(), handler.calls)
        assertEquals(EstelaIntentFallbackPhrases.NETWORK_ERROR, result.spokenText)
    }

    @Test
    fun slotFillResponseReachesConversationManagerThroughAdapter() = runTest {
        val manager = AgentConversationManager()
        manager.handle(LocalIntentParser().parse("mandale un mensaje"))
        val handler = RecordingHandler()
        val engine = engine(
            client = StaticIntentClient(
                llmJson(
                    intent = "slot_fill",
                    safetyLevel = "prepare_only",
                    params = """{"slot":"contact_query","value":"mama"}""",
                    voiceResponse = null
                )
            ),
            handler = handler,
            conversationManager = manager
        )

        val result = engine.classifyAndRoute("a mamá")

        assertTrue(result.adapterResult is LlmIntentAdapterResult.SlotFilled)
        assertEquals(emptyList(), handler.calls)
        assertEquals(AgentState.WAITING_MESSAGE, manager.currentState)
    }

    @Test
    fun blockedSensitiveResponseDoesNotExecuteAllowSafePath() = runTest {
        val handler = RecordingHandler()
        val engine = engine(
            client = StaticIntentClient(
                llmJson(
                    intent = "open_app",
                    safetyLevel = "blocked_sensitive",
                    voiceResponseTemplate = "PROTECTED_APP_REJECTED"
                )
            ),
            handler = handler
        )

        val result = engine.classifyAndRoute("abrime una app sensible")

        assertTrue(result.adapterResult is LlmIntentAdapterResult.Routed)
        assertEquals(listOf("blocked"), handler.calls)
        assertTrue("open_app" !in handler.calls)
        assertEquals(ConsentPhraseResolver.resolve("PROTECTED_APP_REJECTED"), result.spokenText)
    }

    @Test
    fun voiceResponseTemplateIsResolvedBeforeSpeaking() = runTest {
        val handler = RecordingHandler()
        val engine = engine(
            client = StaticIntentClient(
                llmJson(
                    intent = "open_app",
                    params = """{"preferred_app":"uber"}""",
                    voiceResponseTemplate = "RIDE_APP_OPEN_DISCLAIMER"
                )
            ),
            handler = handler
        )

        val result = engine.classifyAndRoute("abrime Uber")

        assertEquals(
            ConsentPhraseResolver.resolve("RIDE_APP_OPEN_DISCLAIMER", mapOf("preferred_app" to "uber")),
            result.spokenText
        )
        assertEquals(result.spokenText, handler.lastRequest?.voiceResponse)
    }

    private fun engine(
        client: EstelaIntentClient,
        handler: RecordingHandler = RecordingHandler(),
        conversationManager: AgentConversationManager = AgentConversationManager()
    ): EstelaIntentEngine =
        EstelaIntentEngine(
            client = client,
            requestBuilder = EstelaIntentRequestBuilder(),
            conversationManager = conversationManager,
            adapter = LlmIntentAdapter(
                intentRouter = IntentRouter(handler, RecordingSafeExecutionDelegate()),
                conversationManager = conversationManager
            )
        )

    private fun llmJson(
        intent: String,
        confidence: Double = 0.9,
        params: String = "{}",
        safetyLevel: String = "allow_safe",
        voiceResponse: String? = null,
        voiceResponseTemplate: String? = null,
        rawText: String = "raw"
    ): String {
        val fields = mutableListOf(
            "\"intent\":\"${intent.jsonEscaped()}\"",
            "\"confidence\":$confidence",
            "\"params\":$params",
            "\"safety_level\":\"${safetyLevel.jsonEscaped()}\"",
            "\"raw_text\":\"${rawText.jsonEscaped()}\""
        )
        fields += if (voiceResponse == null) {
            "\"voice_response\":null"
        } else {
            "\"voice_response\":\"${voiceResponse.jsonEscaped()}\""
        }
        fields += if (voiceResponseTemplate == null) {
            "\"voice_response_template\":null"
        } else {
            "\"voice_response_template\":\"${voiceResponseTemplate.jsonEscaped()}\""
        }
        return "{${fields.joinToString(",")}}"
    }

    private fun String.jsonEscaped(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}

private class StaticIntentClient(
    private val rawJson: String
) : EstelaIntentClient {
    override suspend fun classifyIntent(request: EstelaIntentRequest): Result<String> =
        Result.success(rawJson)
}

private class FailingIntentClient(
    private val error: Throwable
) : EstelaIntentClient {
    override suspend fun classifyIntent(request: EstelaIntentRequest): Result<String> =
        Result.failure(error)
}

private class RecordingHandler : IntentRouteHandler {
    val calls = mutableListOf<String>()
    var lastRequest: IntentRouteRequest? = null
        private set

    override fun handleOpenApp(request: IntentRouteRequest): Intent? {
        calls += "open_app"
        lastRequest = request
        return null
    }

    override fun handleBlocked(request: IntentRouteRequest): ConversationAction? {
        calls += "blocked"
        lastRequest = request
        return ConversationAction(type = ConversationActionType.BLOCKED)
    }

    override fun handleUnknown(request: IntentRouteRequest): ConversationAction? {
        calls += "unknown"
        lastRequest = request
        return ConversationAction(type = ConversationActionType.UNKNOWN, rawText = request.rawText)
    }
}

private class RecordingSafeExecutionDelegate : AgentSafeExecutionDelegate {
    override fun submit(request: IntentRouteRequest) = Unit
}
