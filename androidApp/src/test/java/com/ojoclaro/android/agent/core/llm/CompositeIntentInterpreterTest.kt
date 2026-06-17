package com.ojoclaro.android.agent.core.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.IntentInterpreter
import com.ojoclaro.android.agent.ParsedAgentIntent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompositeIntentInterpreterTest {

    private fun localUnknown(): IntentInterpreter = IntentInterpreter { text ->
        ParsedAgentIntent(
            intent = AgentIntent.UNKNOWN,
            slots = emptyList(),
            rawText = text,
            confidence = 0.1f
        )
    }

    private fun localStrong(intent: AgentIntent): IntentInterpreter = IntentInterpreter { text ->
        ParsedAgentIntent(
            intent = intent,
            slots = emptyList(),
            rawText = text,
            confidence = 0.95f
        )
    }

    private fun fakeLlm(candidate: LlmIntentCandidate?): AgentLlmInterpreter =
        AgentLlmInterpreter { _, _ -> candidate }

    @Test
    fun usesLocalWhenConfidenceIsHigh() = runTest {
        val interpreter = CompositeIntentInterpreter(
            localParser = localStrong(AgentIntent.OPEN_MAPS),
            llmInterpreter = fakeLlm(null),
            llmEnabledProvider = { true }
        )
        val result = interpreter.interpret(
            rawText = "abrí mapas",
            context = AgentLlmContext(normalizedText = "abrí mapas")
        )
        assertEquals(CompositeIntentSource.LOCAL, result.source)
        assertEquals(AgentIntent.OPEN_MAPS, result.parsed.intent)
        assertNull(result.llmCandidate)
    }

    @Test
    fun doesNotCallLlmWhenDisabled() = runTest {
        val interpreter = CompositeIntentInterpreter(
            localParser = localUnknown(),
            llmInterpreter = fakeLlm(
                LlmIntentCandidate(
                    intent = AgentIntent.OPEN_MAPS,
                    confidence = 0.9f
                )
            ),
            llmEnabledProvider = { false }
        )
        val result = interpreter.interpret(
            rawText = "x",
            context = AgentLlmContext(normalizedText = "x")
        )
        assertEquals(CompositeIntentSource.LOCAL_UNKNOWN_NO_LLM, result.source)
        assertEquals(AgentIntent.UNKNOWN, result.parsed.intent)
    }

    @Test
    fun usesLlmWhenLocalIsUnknownAndPolicyAccepts() = runTest {
        val interpreter = CompositeIntentInterpreter(
            localParser = localUnknown(),
            llmInterpreter = fakeLlm(
                LlmIntentCandidate(
                    intent = AgentIntent.OPEN_MAPS,
                    confidence = 0.9f,
                    toolNameSuggested = "MAPS"
                )
            ),
            llmEnabledProvider = { true }
        )
        val result = interpreter.interpret(
            rawText = "necesito llegar al trabajo",
            context = AgentLlmContext(normalizedText = "necesito llegar al trabajo")
        )
        assertEquals(CompositeIntentSource.LLM_FALLBACK, result.source)
        assertEquals(AgentIntent.OPEN_MAPS, result.parsed.intent)
        assertNotNull(result.llmCandidate)
        assertTrue(result.isLlmAccepted)
    }

    @Test
    fun llmRejectedFallbackKeepsLocalUnknown() = runTest {
        val interpreter = CompositeIntentInterpreter(
            localParser = localUnknown(),
            llmInterpreter = fakeLlm(
                LlmIntentCandidate(
                    intent = AgentIntent.CREATE_REMINDER, // fuera del whitelist
                    confidence = 0.9f
                )
            ),
            llmEnabledProvider = { true }
        )
        val result = interpreter.interpret(
            rawText = "recordame algo",
            context = AgentLlmContext(normalizedText = "recordame algo")
        )
        assertEquals(CompositeIntentSource.LLM_REJECTED, result.source)
        assertEquals(AgentIntent.UNKNOWN, result.parsed.intent)
    }

    @Test
    fun hotZoneScreenSkipsLlmEntirely() = runTest {
        var llmCalled = false
        val interpreter = CompositeIntentInterpreter(
            localParser = localUnknown(),
            llmInterpreter = AgentLlmInterpreter { _, _ ->
                llmCalled = true
                null
            },
            llmEnabledProvider = { true }
        )
        val result = interpreter.interpret(
            rawText = "x",
            context = AgentLlmContext(normalizedText = "x", screenIsSensitive = true)
        )
        assertEquals(CompositeIntentSource.LOCAL_UNKNOWN_HOT_ZONE, result.source)
        assertEquals(false, llmCalled)
    }

    @Test
    fun llmReturningNullKeepsUnknown() = runTest {
        val interpreter = CompositeIntentInterpreter(
            localParser = localUnknown(),
            llmInterpreter = fakeLlm(null),
            llmEnabledProvider = { true }
        )
        val result = interpreter.interpret(
            rawText = "x",
            context = AgentLlmContext(normalizedText = "x")
        )
        assertEquals(CompositeIntentSource.LOCAL_UNKNOWN_LLM_QUIET, result.source)
    }

    @Test
    fun llmThrowingFallsBackGracefully() = runTest {
        val interpreter = CompositeIntentInterpreter(
            localParser = localUnknown(),
            llmInterpreter = AgentLlmInterpreter { _, _ -> error("boom") },
            llmEnabledProvider = { true }
        )
        val result = interpreter.interpret(
            rawText = "x",
            context = AgentLlmContext(normalizedText = "x")
        )
        assertEquals(CompositeIntentSource.LOCAL_UNKNOWN_LLM_QUIET, result.source)
    }

    @Test
    fun llmCannotInventToolEvenIfIntentValid() = runTest {
        val interpreter = CompositeIntentInterpreter(
            localParser = localUnknown(),
            llmInterpreter = fakeLlm(
                LlmIntentCandidate(
                    intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
                    confidence = 0.9f,
                    toolNameSuggested = "telegram_send",
                    slotsProposed = mapOf(
                        AgentSlotName.CONTACT_NAME to "Sofi",
                        AgentSlotName.MESSAGE_TEXT to "estoy llegando"
                    )
                )
            ),
            llmEnabledProvider = { true }
        )
        val result = interpreter.interpret(
            rawText = "mandale a Sofi",
            context = AgentLlmContext(normalizedText = "mandale a Sofi")
        )
        assertEquals(CompositeIntentSource.LLM_REJECTED, result.source)
    }
}
