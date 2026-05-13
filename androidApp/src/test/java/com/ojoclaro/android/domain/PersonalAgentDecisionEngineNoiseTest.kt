package com.ojoclaro.android.domain

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.memory.PersonalMemorySnapshot
import com.ojoclaro.android.model.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * QA Samsung reportó que SpeechRecognizer devuelve frases basura ("sí Aurelio",
 * "Android", "eh", "uh", etc.) y que esas frases no deben disparar acciones
 * peligrosas. Ahora caen al filtro de ruido y reciben un mensaje humano.
 */
class PersonalAgentDecisionEngineNoiseTest {

    private val engine = PersonalAgentDecisionEngine()

    @Test
    fun shortFillerPhrasesTriggerRetryListening() = runTest {
        val junk = listOf("eh", "uh", "mmm", "ah", "hm", "este", "ehmm")
        for (phrase in junk) {
            val decision = engine.decide(input(phrase))
            assertTrue(
                decision is PersonalAgentDecision.RetryListening,
                "Expected RetryListening for '$phrase' but got $decision"
            )
        }
    }

    @Test
    fun otherAssistantWakeWordsTriggerRetryListening() = runTest {
        val junk = listOf("android", "ok google", "hey google", "alexa", "siri", "aurelio")
        for (phrase in junk) {
            val decision = engine.decide(input(phrase))
            assertTrue(
                decision is PersonalAgentDecision.RetryListening,
                "Expected RetryListening for '$phrase' but got $decision"
            )
        }
    }

    @Test
    fun shortPhrasesMixingWakewordAndFillerAreNoise() = runTest {
        val junk = listOf("si aurelio", "ok android", "eh android", "ah aurelio")
        for (phrase in junk) {
            val decision = engine.decide(input(phrase))
            assertTrue(
                decision is PersonalAgentDecision.RetryListening,
                "Expected RetryListening for '$phrase' but got $decision"
            )
        }
    }

    @Test
    fun retryListeningMessageIsHumanAndConcrete() = runTest {
        val decision = engine.decide(input("eh"))
        assertTrue(decision is PersonalAgentDecision.RetryListening)
        val spoken = (decision as PersonalAgentDecision.RetryListening).spokenText
        assertTrue(spoken.contains("No entendí", ignoreCase = true))
        assertTrue(spoken.contains("qué hay en pantalla", ignoreCase = true))
        assertTrue(spoken.contains("WhatsApp", ignoreCase = true))
        assertTrue(spoken.contains("repetí", ignoreCase = true))
        // Contraqualidad: no decimos "no estoy usando la IA".
        assertFalse(spoken.contains("no estoy usando la IA", ignoreCase = true))
        assertFalse(spoken.contains("proxy", ignoreCase = true))
    }

    @Test
    fun realCommandStillExecutes() = runTest {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.OPEN_WHATSAPP,
            slots = emptyList(),
            rawText = "abrir whatsapp",
            confidence = 0.9f
        )
        val decision = engine.decide(input("abrir whatsapp", parsedIntent = parsed))
        // Debe entrar al flujo de WhatsApp (AskQuestion porque no hay continuation real).
        assertEquals(false, decision is PersonalAgentDecision.RetryListening)
    }

    @Test
    fun noiseDoesNotTriggerWhatsAppOrMaps() = runTest {
        val junk = listOf("android", "eh", "si aurelio", "alexa")
        for (phrase in junk) {
            val decision = engine.decide(input(phrase))
            assertFalse(
                decision is PersonalAgentDecision.ExecuteSafeAction,
                "Junk '$phrase' should never trigger ExecuteSafeAction"
            )
            assertFalse(
                decision is PersonalAgentDecision.ComposeHumanMessage,
                "Junk '$phrase' should never compose a WhatsApp message"
            )
        }
    }

    private fun input(
        text: String,
        parsedIntent: ParsedAgentIntent = ParsedAgentIntent(
            intent = AgentIntent.UNKNOWN,
            slots = emptyList(),
            rawText = text,
            confidence = 0f
        )
    ): PersonalAgentDecisionInput =
        PersonalAgentDecisionInput(
            originalText = text,
            normalizedText = text.lowercase(),
            agentState = AgentState.IDLE,
            appState = AppState.IDLE,
            memorySnapshot = PersonalMemorySnapshot(),
            externalApp = null,
            hasPendingConfirmation = false,
            currentTimeMillis = 1_000L,
            parsedIntent = parsedIntent
        )
}
