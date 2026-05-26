package ai.ojoclaro.adapter

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmIntentAdapterTest {

    @Test
    fun validSlotFillJsonCallsAgentConversationManager() {
        val manager = AgentConversationManager()
        manager.handle(LocalIntentParser().parse("mandale un mensaje"))
        val adapter = adapter(conversationManager = manager)

        val result = adapter.adapt(
            llmJson(
                intent = "slot_fill",
                params = """{"slot":"${AgentConversationManager.SLOT_FILL_CONTACT_QUERY}","value":"mama"}"""
            )
        )

        assertTrue(result is LlmIntentAdapterResult.SlotFilled)
        assertEquals(AgentConversationManager.SLOT_FILL_CONTACT_QUERY, result.slot)
        assertEquals("mama", result.value)
        assertEquals(AgentState.WAITING_MESSAGE, result.outcome.targetState)
        assertEquals(AgentState.WAITING_MESSAGE, manager.currentState)
    }

    @Test
    fun prepareOnlySlotFillCallsAgentConversationManager() {
        val handler = RecordingHandler()
        val manager = pendingContactManager()
        val result = adapter(handler = handler, conversationManager = manager).adapt(
            llmJson(
                intent = "slot_fill",
                params = slotFillParams(),
                safetyLevel = "prepare_only"
            )
        )

        assertTrue(result is LlmIntentAdapterResult.SlotFilled)
        assertEquals(emptyList(), handler.calls)
        assertEquals(AgentState.WAITING_MESSAGE, manager.currentState)
    }

    @Test
    fun slotFillWithMissingSlotReturnsSafeFallback() {
        val result = adapter().adapt(
            llmJson(
                intent = "slot_fill",
                params = """{"value":"mama"}"""
            )
        )

        assertTrue(result is LlmIntentAdapterResult.SafeFallback)
        assertEquals("missing_slot", result.reason)
    }

    @Test
    fun slotFillWithMissingValueReturnsSafeFallback() {
        val result = adapter().adapt(
            llmJson(
                intent = "slot_fill",
                params = """{"slot":"${AgentConversationManager.SLOT_FILL_CONTACT_QUERY}"}"""
            )
        )

        assertTrue(result is LlmIntentAdapterResult.SafeFallback)
        assertEquals("missing_value", result.reason)
    }

    @Test
    fun blockedSensitiveSlotFillDoesNotCallSlotFillOrRoute() {
        val handler = RecordingHandler()
        val manager = pendingContactManager()
        val result = adapter(handler = handler, conversationManager = manager).adapt(
            llmJson(
                intent = "slot_fill",
                params = slotFillParams(),
                safetyLevel = "blocked_sensitive"
            )
        )

        assertTrue(result is LlmIntentAdapterResult.SafeFallback)
        assertEquals("unsafe_slot_fill", result.reason)
        assertEquals(emptyList(), handler.calls)
        assertEquals(AgentState.WAITING_CONTACT, manager.currentState)
    }

    @Test
    fun missingSafetyLevelSlotFillDoesNotCallSlotFillOrRoute() {
        val handler = RecordingHandler()
        val manager = pendingContactManager()
        val result = adapter(handler = handler, conversationManager = manager).adapt(
            llmJson(
                intent = "slot_fill",
                params = slotFillParams(),
                safetyLevel = null
            )
        )

        assertTrue(result is LlmIntentAdapterResult.SafeFallback)
        assertEquals("unsafe_slot_fill", result.reason)
        assertEquals(emptyList(), handler.calls)
        assertEquals(AgentState.WAITING_CONTACT, manager.currentState)
    }

    @Test
    fun requiresConfirmSlotFillDoesNotCallSlotFillOrRoute() {
        val handler = RecordingHandler()
        val manager = pendingContactManager()
        val result = adapter(handler = handler, conversationManager = manager).adapt(
            llmJson(
                intent = "slot_fill",
                params = slotFillParams(),
                safetyLevel = "requires_confirm"
            )
        )

        assertTrue(result is LlmIntentAdapterResult.SafeFallback)
        assertEquals("unsafe_slot_fill", result.reason)
        assertEquals(emptyList(), handler.calls)
        assertEquals(AgentState.WAITING_CONTACT, manager.currentState)
    }

    @Test
    fun lowConfidenceSlotFillRoutesUnknownWithoutCallingSlotFill() {
        val handler = RecordingHandler()
        val manager = pendingContactManager()
        val result = adapter(handler = handler, conversationManager = manager).adapt(
            llmJson(
                intent = "slot_fill",
                confidence = 0.59,
                params = slotFillParams()
            )
        )

        assertTrue(result is LlmIntentAdapterResult.Routed)
        assertEquals(listOf("unknown"), handler.calls)
        assertEquals("unknown", handler.lastRequest?.intent)
        assertEquals("unknown", result.request.intent)
        assertEquals(AgentState.WAITING_CONTACT, manager.currentState)
    }

    @Test
    fun validOpenAppJsonRoutesThroughIntentRouter() {
        val handler = RecordingHandler()
        val result = adapter(handler = handler).adapt(
            llmJson(intent = "open_app", params = """{"app_name":"spotify"}""")
        )

        assertTrue(result is LlmIntentAdapterResult.Routed)
        assertEquals(listOf("open_app"), handler.calls)
        assertEquals("open_app", result.request.intent)
    }

    @Test
    fun voiceResponseTemplateResolvesThroughConsentPhraseResolver() {
        val handler = RecordingHandler()
        val result = adapter(handler = handler).adapt(
            llmJson(
                intent = "open_app",
                params = """{"app_name":"spotify"}""",
                voiceResponseTemplate = "SAVE_MEMORY_GENERIC"
            )
        )

        assertTrue(result is LlmIntentAdapterResult.Routed)
        assertEquals(
            ConsentPhraseResolver.resolve("SAVE_MEMORY_GENERIC"),
            handler.lastRequest?.voiceResponse
        )
    }

    @Test
    fun unknownVoiceResponseTemplateDoesNotCrash() {
        val handler = RecordingHandler()
        val result = adapter(handler = handler).adapt(
            llmJson(
                intent = "open_app",
                params = """{"app_name":"spotify"}""",
                voiceResponse = "fallback response",
                voiceResponseTemplate = "UNKNOWN_TEMPLATE"
            )
        )

        assertTrue(result is LlmIntentAdapterResult.Routed)
        assertEquals(listOf("open_app"), handler.calls)
        assertEquals("fallback response", handler.lastRequest?.voiceResponse)
    }

    @Test
    fun malformedJsonReturnsSafeFallback() {
        val result = adapter().adapt("{not json")

        assertTrue(result is LlmIntentAdapterResult.SafeFallback)
        assertEquals("malformed_json", result.reason)
    }

    @Test
    fun missingIntentRoutesAsUnknown() {
        val handler = RecordingHandler()
        val result = adapter(handler = handler).adapt(llmJson(intent = null))

        assertTrue(result is LlmIntentAdapterResult.Routed)
        assertEquals(listOf("unknown"), handler.calls)
        assertEquals("unknown", result.request.intent)
    }

    @Test
    fun missingSafetyLevelFailsSafeAsBlockedSensitive() {
        val handler = RecordingHandler()
        val result = adapter(handler = handler).adapt(
            llmJson(intent = "open_app", safetyLevel = null)
        )

        assertTrue(result is LlmIntentAdapterResult.Routed)
        assertEquals(listOf("blocked"), handler.calls)
        assertEquals("blocked_sensitive", result.request.safetyLevel)
    }

    @Test
    fun confidenceBelowMinimumRoutesAsUnknown() {
        val handler = RecordingHandler()
        val result = adapter(handler = handler).adapt(
            llmJson(intent = "open_app", confidence = 0.59)
        )

        assertTrue(result is LlmIntentAdapterResult.Routed)
        assertEquals(listOf("unknown"), handler.calls)
        assertEquals("unknown", result.request.intent)
    }

    @Test
    fun blockedSensitiveIntentDoesNotExecuteAllowSafePath() {
        val handler = RecordingHandler()
        val result = adapter(handler = handler).adapt(
            llmJson(intent = "open_app", safetyLevel = "blocked_sensitive")
        )

        assertTrue(result is LlmIntentAdapterResult.Routed)
        assertEquals(listOf("blocked"), handler.calls)
        assertTrue("open_app" !in handler.calls)
    }

    @Test
    fun rawTextIsPreservedWhenPresent() {
        val handler = RecordingHandler()
        val result = adapter(handler = handler).adapt(
            llmJson(intent = "open_app", rawText = "open spotify please")
        )

        assertTrue(result is LlmIntentAdapterResult.Routed)
        assertEquals("open spotify please", result.request.rawText)
        assertEquals("open spotify please", handler.lastRequest?.rawText)
    }

    private fun adapter(
        handler: RecordingHandler = RecordingHandler(),
        conversationManager: AgentConversationManager = AgentConversationManager()
    ): LlmIntentAdapter =
        LlmIntentAdapter(
            intentRouter = IntentRouter(handler, RecordingSafeExecutionDelegate()),
            conversationManager = conversationManager
        )

    private fun llmJson(
        intent: String? = "open_app",
        confidence: Double = 0.9,
        params: String = "{}",
        safetyLevel: String? = "allow_safe",
        voiceResponse: String? = null,
        voiceResponseTemplate: String? = null,
        rawText: String? = "open app"
    ): String {
        val fields = mutableListOf<String>()
        intent?.let { fields += "\"intent\":\"${it.jsonEscaped()}\"" }
        fields += "\"confidence\":$confidence"
        fields += "\"params\":$params"
        safetyLevel?.let { fields += "\"safety_level\":\"${it.jsonEscaped()}\"" }
        voiceResponse?.let { fields += "\"voice_response\":\"${it.jsonEscaped()}\"" }
        voiceResponseTemplate?.let { fields += "\"voice_response_template\":\"${it.jsonEscaped()}\"" }
        rawText?.let { fields += "\"raw_text\":\"${it.jsonEscaped()}\"" }
        return "{${fields.joinToString(",")}}"
    }

    private fun pendingContactManager(): AgentConversationManager =
        AgentConversationManager().apply {
            handle(LocalIntentParser().parse("mandale un mensaje"))
            assertEquals(AgentState.WAITING_CONTACT, currentState)
        }

    private fun slotFillParams(): String =
        """{"slot":"${AgentConversationManager.SLOT_FILL_CONTACT_QUERY}","value":"mama"}"""

    private fun String.jsonEscaped(): String =
        buildString {
            for (char in this@jsonEscaped) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
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

    override fun handleUnknown(request: IntentRouteRequest): ConversationAction? {
        calls += "unknown"
        lastRequest = request
        return ConversationAction(type = ConversationActionType.UNKNOWN, rawText = request.rawText)
    }

    override fun handleBlocked(request: IntentRouteRequest): ConversationAction? {
        calls += "blocked"
        lastRequest = request
        return ConversationAction(type = ConversationActionType.BLOCKED, blockedReason = request.safetyLevel)
    }
}

private class RecordingSafeExecutionDelegate : AgentSafeExecutionDelegate {
    override fun submit(request: IntentRouteRequest) = Unit
}
