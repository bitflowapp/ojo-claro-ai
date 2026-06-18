package com.ojoclaro.android.ui.home

import ai.ojoclaro.adapter.LlmIntentAdapter
import ai.ojoclaro.adapter.LlmIntentAdapterResult
import ai.ojoclaro.router.AgentSafeExecutionDelegate
import ai.ojoclaro.router.EmptyIntentRouteHandler
import ai.ojoclaro.router.IntentRouteRequest
import ai.ojoclaro.router.IntentRouteResult
import ai.ojoclaro.router.IntentRouter
import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentOutcome
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.agent.apps.FakeInstalledAppResolver
import com.ojoclaro.android.agent.task.AgentTaskMemory
import com.ojoclaro.android.agent.task.AgentTaskOrchestrator
import com.ojoclaro.android.agent.task.AgentTaskOrchestratorResult
import com.ojoclaro.android.agent.task.AgentTaskPlanner
import com.ojoclaro.android.agent.task.AgentTaskType
import com.ojoclaro.android.llm.EstelaIntentClient
import com.ojoclaro.android.llm.EstelaIntentEngine
import com.ojoclaro.android.llm.EstelaIntentRequest
import com.ojoclaro.android.llm.EstelaIntentRequestBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeViewModelLlmFirstRoutingTest {

    @Test
    fun whatsappComposeIsDeflectedToIntentRuntimeBeforeLocalPlanner() = runTest {
        val phrase = "Mandale un mensaje a Sofi diciendo que ya llegu\u00E9"
        val memory = AgentTaskMemory(clock = { 1_000L })
        val orchestrator = orchestrator(memory)
        val runtimeAllowed = shouldUseEstelaIntentRuntime(
            assistantBaseUrlConfigured = true,
            hasPendingExternalConfirmation = false,
            hasPendingConsentAction = false,
            hasPendingVoiceCorrection = false,
            uiHasPendingConfirmation = false,
            runtimeHasPendingConfirmation = false,
            managerInWaitingConfirmation = false
        )

        val localFirstResult = orchestrator.handle(
            rawUserCommand = phrase,
            deflectWhatsAppMessagingToLlm = runtimeAllowed
        )

        assertTrue(localFirstResult is AgentTaskOrchestratorResult.NotHandled)
        assertNull(memory.currentPlan(), "Local planner must not swallow a brand-new WhatsApp compose request")

        val gate = RecordingIntentGate()
        val client = FakeIntentClient(Result.success(composeWhatsappJson(phrase)))
        val runtimeResult = engine(client = client, gate = gate).classifyAndRoute(phrase)
        val routed = runtimeResult.adapterResult as? LlmIntentAdapterResult.Routed

        assertTrue(routed != null)
        assertEquals(listOf(phrase), client.requests.map { it.userText })
        assertEquals("compose_whatsapp_message", routed.request.intent)
        assertEquals("prepare_only", routed.request.safetyLevel)
        assertTrue(routed.routeResult is IntentRouteResult.Delegated)
        assertEquals(1, gate.requests.size)
        assertEquals("compose_whatsapp_message", gate.requests.single().intent)
    }

    @Test
    fun nullIntentRuntimeResultFallsBackToLocalPlannerWithoutDroppingInput() {
        val phrase = "Mandale un mensaje a Sofi diciendo que ya llegu\u00E9"
        val memory = AgentTaskMemory(clock = { 1_000L })
        val orchestrator = orchestrator(memory)
        var legacyResult: AgentTaskOrchestratorResult? = null

        val completion = completeEstelaIntentRuntimeOrFallback(
            result = null,
            shouldDrop = false,
            applyResult = {
                error("A null /intent result must not call applyResult")
            },
            submitLegacy = {
                legacyResult = orchestrator.handle(
                    rawUserCommand = phrase,
                    deflectWhatsAppMessagingToLlm = false
                )
            }
        )

        assertEquals(EstelaIntentRuntimeCompletion.LEGACY_FALLBACK, completion)
        val handled = legacyResult as? AgentTaskOrchestratorResult.Handled
        assertTrue(handled != null)
        assertEquals(AgentTaskType.SEND_WHATSAPP_MESSAGE, memory.currentPlan()?.type)
        assertTrue(handled.spokenText.isNotBlank())
    }

    @Test
    fun waitingConfirmationKeepsConfirmarOnLegacyPathAndDoesNotCallIntentRuntime() {
        val manager = AgentConversationManager()
        val parser = LocalIntentParser()
        val pending = manager.handle(parser.parse("Mandale a Sofi que ya llegu\u00E9"))
        var intentRuntimeCalls = 0
        var legacyInput: String? = null
        var legacyOutcome: AgentOutcome? = null

        assertEquals(AgentState.WAITING_CONFIRMATION, pending.targetState)
        assertEquals(AgentState.WAITING_CONFIRMATION, manager.currentState)

        val path = submitVoiceTextViaIntentOrLegacy(
            text = "confirmar",
            startIntentRuntime = {
                val allowed = shouldUseEstelaIntentRuntime(
                    assistantBaseUrlConfigured = true,
                    hasPendingExternalConfirmation = false,
                    hasPendingConsentAction = false,
                    hasPendingVoiceCorrection = false,
                    uiHasPendingConfirmation = false,
                    runtimeHasPendingConfirmation = false,
                    managerInWaitingConfirmation = manager.currentState == AgentState.WAITING_CONFIRMATION
                )
                if (allowed) intentRuntimeCalls += 1
                allowed
            },
            submitLegacy = { input ->
                legacyInput = input
                legacyOutcome = manager.handle(parser.parse(input))
            }
        )

        assertEquals(EstelaIntentSubmitPath.LEGACY_FALLBACK, path)
        assertEquals(0, intentRuntimeCalls)
        assertEquals("confirmar", legacyInput)
        assertEquals(AgentState.PROCESSING, legacyOutcome?.targetState)
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, legacyOutcome?.suggestedIntent?.intent)
    }

    private fun orchestrator(memory: AgentTaskMemory): AgentTaskOrchestrator =
        AgentTaskOrchestrator(
            planner = AgentTaskPlanner(clock = { 1_000L }),
            memory = memory,
            installedAppResolver = FakeInstalledAppResolver(setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE)),
            clock = { 1_000L }
        )

    private fun engine(
        client: EstelaIntentClient,
        gate: RecordingIntentGate,
        manager: AgentConversationManager = AgentConversationManager()
    ): EstelaIntentEngine =
        EstelaIntentEngine(
            client = client,
            requestBuilder = EstelaIntentRequestBuilder(),
            conversationManager = manager,
            adapter = LlmIntentAdapter(
                intentRouter = IntentRouter(EmptyIntentRouteHandler(), gate),
                conversationManager = manager
            )
        )

    private fun composeWhatsappJson(rawText: String): String =
        """
        {
          "intent": "compose_whatsapp_message",
          "confidence": 0.95,
          "params": {
            "contact_query": "Sofi",
            "message_text": "ya llegu\u00E9"
          },
          "safety_level": "prepare_only",
          "voice_response": null,
          "voice_response_template": "CONFIRM_REPROMPT",
          "raw_text": "${rawText.jsonEscaped()}"
        }
        """.trimIndent()

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

private class FakeIntentClient(
    private val response: Result<String>
) : EstelaIntentClient {
    val requests = mutableListOf<EstelaIntentRequest>()

    override suspend fun classifyIntent(request: EstelaIntentRequest): Result<String> {
        requests += request
        return response
    }
}

private class RecordingIntentGate : AgentSafeExecutionDelegate {
    val requests = mutableListOf<IntentRouteRequest>()

    override fun submit(request: IntentRouteRequest) {
        requests += request
    }
}
