package ai.ojoclaro.integration

import ai.ojoclaro.adapter.LlmIntentAdapter
import ai.ojoclaro.adapter.LlmIntentAdapterResult
import ai.ojoclaro.consent.ConsentPhraseResolver
import ai.ojoclaro.router.AgentSafeExecutionDelegate
import ai.ojoclaro.router.EmptyIntentRouteHandler
import ai.ojoclaro.router.IntentRouteRequest
import ai.ojoclaro.router.IntentRouteResult
import ai.ojoclaro.router.IntentRouter
import ai.ojoclaro.router.IntentSpec
import android.content.Intent
import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentOutcome
import com.ojoclaro.android.agent.AgentSlot
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.llm.EstelaIntentClient
import com.ojoclaro.android.llm.EstelaIntentEngine
import com.ojoclaro.android.llm.EstelaIntentRequest
import com.ojoclaro.android.llm.EstelaIntentRequestBuilder
import java.net.URLEncoder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsappIntentEndToEndTest {

    @Test
    fun whatsappPrepareFlowUsesConfirmRepromptBecauseWhatsappPrepareTemplateIsNotRegistered() = runTest {
        val harness = WhatsappFlowHarness()

        val prepared = harness.prepareFromLlm(USER_TEXT)

        assertEquals("compose_whatsapp_message", prepared.request.intent)
        assertEquals("prepare_only", prepared.request.safetyLevel)
        assertEquals(1, harness.prepareGate.submitted.size)
        assertIs<IntentRouteResult.Delegated>(prepared.adapterResult.routeResult)
        assertNull(harness.prepareHandler.lastIntent)

        val resolvedPhrase = ConsentPhraseResolver.resolve(PREPARE_CONFIRM_TEMPLATE)
        assertNotNull(resolvedPhrase)
        assertEquals(resolvedPhrase, prepared.spokenText)

        val conversationOutcome = harness.stageConversationConfirmation(prepared.request)
        assertEquals(AgentState.WAITING_CONFIRMATION, conversationOutcome.targetState)
        assertEquals(AgentState.WAITING_CONFIRMATION, harness.manager.currentState)
        assertTrue(conversationOutcome.needsConfirmation)

        val confirmed = harness.confirm("confirmar")
        val finalIntent = assertPreparedWhatsappIntent(confirmed.routeResult)

        assertEquals(Intent.ACTION_VIEW, finalIntent.action)
        assertTrue(finalIntent.dataString.orEmpty().contains("wa.me"))
        assertTrue(finalIntent.dataString.orEmpty().contains(encodedMessageText()))
        assertFalse(finalIntent.action == Intent.ACTION_SEND)
        assertEquals(0, harness.finalHandler.accessibilityClicks)
        assertEquals(0, harness.finalHandler.directMessageSends)
    }

    @Test
    fun daleWhileWaitingConfirmDoesNotPrepareWhatsappIntent() = runTest {
        val harness = WhatsappFlowHarness()
        harness.prepareWaitingConfirmation()

        val confirmed = harness.confirm("dale")

        assertTrue(confirmed.outcome.isError)
        assertNull(confirmed.routeResult)
        assertNull(harness.finalHandler.lastIntent)
        assertEquals(0, harness.finalHandler.composeCalls)
    }

    @Test
    fun siWhileWaitingConfirmDoesNotPrepareWhatsappIntent() = runTest {
        val harness = WhatsappFlowHarness()
        harness.prepareWaitingConfirmation()

        val confirmed = harness.confirm("s\u00ED")

        assertTrue(confirmed.outcome.isError)
        assertNull(confirmed.routeResult)
        assertNull(harness.finalHandler.lastIntent)
        assertEquals(0, harness.finalHandler.composeCalls)
    }

    @Test
    fun confirmarWhileWaitingConfirmPreparesWhatsappActionViewIntent() = runTest {
        val harness = WhatsappFlowHarness()
        harness.prepareWaitingConfirmation()

        val confirmed = harness.confirm("confirmar")
        val finalIntent = assertPreparedWhatsappIntent(confirmed.routeResult)

        assertEquals(Intent.ACTION_VIEW, finalIntent.action)
        assertTrue(finalIntent.dataString.orEmpty().contains("wa.me"))
        assertTrue(finalIntent.dataString.orEmpty().contains(encodedMessageText()))
        assertFalse(finalIntent.action == Intent.ACTION_SEND)
    }

    private fun assertPreparedWhatsappIntent(routeResult: IntentRouteResult?): Intent {
        val launch = assertIs<IntentRouteResult.LaunchIntent>(routeResult)
        val intent = launch.intent

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.dataString.orEmpty().contains("wa.me"))
        assertFalse(intent.action == Intent.ACTION_SEND)
        return intent
    }

    companion object {
        const val USER_TEXT = "Mandale a Sofi que ya lleg\u00E9"
        const val CONTACT_QUERY = "Sofi"
        const val MESSAGE_TEXT = "ya lleg\u00E9"

        // WHATSAPP_MESSAGE_PREPARE_CONFIRM is not registered in ConsentPhraseResolver yet.
        // Keep the demo on the nearest existing spoken confirmation template without
        // touching ConsentPhrases.kt.
        const val PREPARE_CONFIRM_TEMPLATE = "CONFIRM_REPROMPT"

        fun mockedLlmJson(): String =
            """
            {
              "intent": "compose_whatsapp_message",
              "confidence": 0.95,
              "params": {
                "contact_query": "${CONTACT_QUERY.jsonEscaped()}",
                "message_text": "${MESSAGE_TEXT.jsonEscaped()}"
              },
              "safety_level": "prepare_only",
              "voice_response": null,
              "voice_response_template": "$PREPARE_CONFIRM_TEMPLATE",
              "raw_text": "${USER_TEXT.jsonEscaped()}"
            }
            """.trimIndent()

        fun encodedMessageText(): String =
            URLEncoder.encode(MESSAGE_TEXT, Charsets.UTF_8.name())

        fun String.jsonEscaped(): String =
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
}

private class WhatsappFlowHarness {
    val manager = AgentConversationManager()
    val prepareGate = RecordingSafeExecutionDelegate()
    val prepareHandler = RecordingWhatsappHandler()
    val finalHandler = RecordingWhatsappHandler()

    private val parser = LocalIntentParser()
    private val prepareEngine = EstelaIntentEngine(
        client = StaticEstelaIntentClient(WhatsappIntentEndToEndTest.mockedLlmJson()),
        requestBuilder = EstelaIntentRequestBuilder(),
        conversationManager = manager,
        adapter = LlmIntentAdapter(
            intentRouter = IntentRouter(
                handler = prepareHandler,
                safeExecutionDelegate = prepareGate
            ),
            conversationManager = manager
        )
    )
    private val finalRouter = IntentRouter(
        handler = finalHandler,
        safeExecutionDelegate = RecordingSafeExecutionDelegate()
    )

    suspend fun prepareFromLlm(userText: String): PreparedWhatsappRequest {
        val result = prepareEngine.classifyAndRoute(userText)
        val adapterResult = assertIs<LlmIntentAdapterResult.Routed>(result.adapterResult)
        return PreparedWhatsappRequest(
            adapterResult = adapterResult,
            request = adapterResult.request,
            spokenText = result.spokenText
        )
    }

    suspend fun prepareWaitingConfirmation(): PreparedWhatsappRequest {
        val prepared = prepareFromLlm(WhatsappIntentEndToEndTest.USER_TEXT)
        stageConversationConfirmation(prepared.request)
        assertEquals(AgentState.WAITING_CONFIRMATION, manager.currentState)
        return prepared
    }

    fun stageConversationConfirmation(request: IntentRouteRequest): AgentOutcome =
        manager.handle(request.toComposeWhatsappParsedIntent())

    fun confirm(phrase: String): ConfirmedWhatsappRequest {
        val outcome = manager.handle(parser.parse(phrase))
        val suggested = outcome.suggestedIntent
        val routeResult = if (!outcome.isError &&
            outcome.targetState == AgentState.PROCESSING &&
            suggested?.intent == AgentIntent.COMPOSE_WHATSAPP_MESSAGE
        ) {
            finalRouter.routeAndCollect(suggested.toAllowedRouterJson())
        } else {
            null
        }

        return ConfirmedWhatsappRequest(outcome = outcome, routeResult = routeResult)
    }

    private fun IntentRouteRequest.toComposeWhatsappParsedIntent(): ParsedAgentIntent {
        assertEquals("compose_whatsapp_message", intent)
        val contact = params["contact_query"] as? String
        val message = params["message_text"] as? String

        return ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOfNotNull(
                contact?.let {
                    AgentSlot(
                        name = AgentSlotName.CONTACT_NAME,
                        value = it,
                        confidence = 0.95f
                    )
                },
                message?.let {
                    AgentSlot(
                        name = AgentSlotName.MESSAGE_TEXT,
                        value = it,
                        confidence = 0.95f
                    )
                }
            ),
            rawText = rawText,
            confidence = confidence.toFloat(),
            missingSlots = emptyList(),
            requiresConfirmation = true
        )
    }

    private fun ParsedAgentIntent.toAllowedRouterJson(): Map<String, Any?> =
        mapOf(
            "intent" to "compose_whatsapp_message",
            "confidence" to confidence.toDouble(),
            "params" to mapOf(
                "contact_query" to slotValue(AgentSlotName.CONTACT_NAME),
                "message_text" to slotValue(AgentSlotName.MESSAGE_TEXT)
            ),
            "safety_level" to "allow_safe",
            "voice_response" to null,
            "voice_response_template" to null,
            "raw_text" to rawText
        )
}

private data class PreparedWhatsappRequest(
    val adapterResult: LlmIntentAdapterResult.Routed,
    val request: IntentRouteRequest,
    val spokenText: String
)

private data class ConfirmedWhatsappRequest(
    val outcome: AgentOutcome,
    val routeResult: IntentRouteResult?
)

private class StaticEstelaIntentClient(
    private val rawJson: String
) : EstelaIntentClient {
    override suspend fun classifyIntent(request: EstelaIntentRequest): Result<String> =
        Result.success(rawJson)
}

private class RecordingSafeExecutionDelegate : AgentSafeExecutionDelegate {
    val submitted = mutableListOf<IntentRouteRequest>()

    override fun submit(request: IntentRouteRequest) {
        submitted += request
    }
}

private class RecordingWhatsappHandler : ai.ojoclaro.router.IntentRouteHandler {
    private val delegate = EmptyIntentRouteHandler(
        intentFactory = { spec -> TestIntent(spec) }
    )

    var composeCalls = 0
        private set
    var accessibilityClicks = 0
        private set
    var directMessageSends = 0
        private set
    var lastIntent: Intent? = null
        private set

    override fun handleComposeWhatsappMessage(request: IntentRouteRequest): Intent? {
        composeCalls += 1
        return delegate.handleComposeWhatsappMessage(request)?.also { intent ->
            lastIntent = intent
        }
    }
}

private class TestIntent(
    private val spec: IntentSpec
) : Intent() {
    private var recordedFlags = 0

    override fun getAction(): String = spec.action

    override fun getDataString(): String? = spec.uri

    override fun addFlags(flags: Int): Intent {
        recordedFlags = recordedFlags or flags
        return this
    }

    override fun getFlags(): Int = recordedFlags
}
