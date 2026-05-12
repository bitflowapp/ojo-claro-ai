package com.ojoclaro.android.domain

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.llm.LlmAgentInterpreter
import com.ojoclaro.android.llm.LlmAgentRequest
import com.ojoclaro.android.llm.LlmAgentResponse
import com.ojoclaro.android.llm.SafeAiFallbackCopy
import com.ojoclaro.android.llm.SafeAiFallbackGuard
import com.ojoclaro.android.memory.PersonalMemorySnapshot
import com.ojoclaro.android.model.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests del Safe AI Fallback v1 vivo dentro de [PersonalAgentDecisionEngine].
 *
 * Cubre:
 *  - Sin proxy / sin LLM -> nunca exponer "no estoy usando la IA".
 *  - Pantalla sensible -> bloquea sin pasar por LLM.
 *  - Pending confirmation -> bloquea sin pasar por LLM.
 *  - Bancos / contraseñas en el texto -> bloquea.
 *  - LLM propone intent fuera de whitelist v1 -> se descarta.
 *  - LLM jamas auto-envia ni auto-clickea.
 */
class PersonalAgentDecisionEngineSafeAiFallbackTest {

    @Test
    fun unknownIntentWithoutProxyDoesNotLeakAiDebugCopy() = runTest {
        val engine = PersonalAgentDecisionEngine()  // default uses DisabledLlmAgentInterpreter
        val decision = engine.decide(
            inputUnknown(originalText = "haceme algo raro")
        )
        assertTrue(decision is PersonalAgentDecision.UseLlmFallback)
        val fallback = decision as PersonalAgentDecision.UseLlmFallback
        val reason = fallback.reason
        val userFacing = fallback.response?.userFacingQuestion.orEmpty()
        assertFalse(reason.contains("No estoy usando la IA", ignoreCase = true), "reason: $reason")
        assertFalse(reason.contains("No uso la IA", ignoreCase = true), "reason: $reason")
        assertFalse(reason.contains("proxy", ignoreCase = true), "reason: $reason")
        assertFalse(userFacing.contains("No estoy usando la IA", ignoreCase = true))
        assertFalse(userFacing.contains("No uso la IA", ignoreCase = true))
        assertFalse(userFacing.contains("proxy", ignoreCase = true))
        assertFalse(userFacing.contains("IA flexible", ignoreCase = true))
    }

    @Test
    fun sensitiveScreenBlocksLlmCallAndUsesSensitiveCopy() = runTest {
        var interpreterWasCalled = false
        val interpreter = object : LlmAgentInterpreter {
            override suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse {
                interpreterWasCalled = true
                return stubResponse(AgentIntent.READ_VISIBLE_SCREEN, 0.95f)
            }
        }
        val engine = PersonalAgentDecisionEngine(
            llmAgentInterpreter = interpreter,
            safeAiFallbackGuard = SafeAiFallbackGuard(isProxyConfigured = { true })
        )
        val decision = engine.decide(
            inputUnknown(originalText = "leeme la pantalla", screenIsSensitive = true)
        )
        assertFalse(interpreterWasCalled, "LLM should not be called on sensitive screen")
        assertTrue(decision is PersonalAgentDecision.UseLlmFallback)
        val reason = (decision as PersonalAgentDecision.UseLlmFallback).reason
        assertEquals(SafeAiFallbackCopy.SENSITIVE_SCREEN, reason)
    }

    @Test
    fun pendingConfirmationBlocksLlmCall() = runTest {
        var interpreterWasCalled = false
        val interpreter = object : LlmAgentInterpreter {
            override suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse {
                interpreterWasCalled = true
                return stubResponse(AgentIntent.HELP, 0.95f)
            }
        }
        val engine = PersonalAgentDecisionEngine(
            llmAgentInterpreter = interpreter,
            safeAiFallbackGuard = SafeAiFallbackGuard(isProxyConfigured = { true })
        )
        val decision = engine.decide(
            inputUnknown(originalText = "ayuda", hasPendingConfirmation = true)
        )
        assertFalse(interpreterWasCalled, "LLM should not be called while pending confirmation")
        assertTrue(decision is PersonalAgentDecision.UseLlmFallback)
    }

    @Test
    fun bankWordsInUserTextBlockLlmCall() = runTest {
        var interpreterWasCalled = false
        val interpreter = object : LlmAgentInterpreter {
            override suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse {
                interpreterWasCalled = true
                return stubResponse(AgentIntent.HELP, 0.95f)
            }
        }
        val engine = PersonalAgentDecisionEngine(
            llmAgentInterpreter = interpreter,
            safeAiFallbackGuard = SafeAiFallbackGuard(isProxyConfigured = { true })
        )
        val decision = engine.decide(
            inputUnknown(originalText = "transferí mil pesos al banco")
        )
        // The engine also has an early-RejectUnsafe for sensitive financial data.
        // Either rejection path is acceptable: the test only proves the LLM is not called.
        assertFalse(interpreterWasCalled, "LLM must not be called for bank/password content")
    }

    @Test
    fun llmProposingOutsideWhitelistIsDiscardedOnUnknownPath() = runTest {
        val interpreter = object : LlmAgentInterpreter {
            override suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse {
                // LLM tries to propose a risky/non-whitelisted intent.
                return stubResponse(AgentIntent.SAVE_CONTACT_PHONE, 0.95f).copy(
                    contactName = "alguien",
                    proposedMessage = null
                )
            }
        }
        val engine = PersonalAgentDecisionEngine(
            llmAgentInterpreter = interpreter,
            safeAiFallbackGuard = SafeAiFallbackGuard(isProxyConfigured = { true })
        )
        val decision = engine.decide(
            inputUnknown(originalText = "guardame el numero secreto de marco")
        )
        assertTrue(decision is PersonalAgentDecision.UseLlmFallback)
        val label = (decision as PersonalAgentDecision.UseLlmFallback).debugLabel
        assertTrue(
            label.startsWith("LLM_OUTSIDE_WHITELIST") || label.startsWith("LLM_RISKY") ||
                label.startsWith("LLM_FALLBACK"),
            "unexpected label: $label"
        )
        // No ComposeHumanMessage, no ExecuteSafeAction: LLM nunca auto-envia ni
        // auto-clickea desde el fallback UNKNOWN.
        assertFalse(decision is PersonalAgentDecision.ComposeHumanMessage)
        assertFalse(decision is PersonalAgentDecision.ExecuteSafeAction)
    }

    @Test
    fun llmCannotAutoExecuteFromUnknownFallback() = runTest {
        val interpreter = object : LlmAgentInterpreter {
            override suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse {
                return stubResponse(AgentIntent.CALL_CONTACT, 0.99f)
                    .copy(contactName = "alguien", shouldExecuteImmediately = true)
            }
        }
        val engine = PersonalAgentDecisionEngine(
            llmAgentInterpreter = interpreter,
            safeAiFallbackGuard = SafeAiFallbackGuard(isProxyConfigured = { true })
        )
        val decision = engine.decide(
            inputUnknown(originalText = "haceme una llamada")
        )
        assertFalse(decision is PersonalAgentDecision.ExecuteSafeAction, "LLM cannot self-execute calls")
    }

    @Test
    fun repeatLastIntentNotRoutedThroughLlmFallback() = runTest {
        val engine = PersonalAgentDecisionEngine()
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.REPEAT_LAST,
            slots = emptyList(),
            rawText = "repetí",
            confidence = 0.9f
        )
        val decision = engine.decide(
            inputCustom(originalText = "repetí", parsedIntent = parsed)
        )
        // REPEAT_LAST is not handled by the engine's `when`, falls through to DoNothing.
        // HomeViewModel handles it directly. The point is: it does NOT hit the LLM path.
        assertTrue(decision is PersonalAgentDecision.DoNothing)
    }

    private fun stubResponse(intent: AgentIntent, confidence: Float): LlmAgentResponse =
        LlmAgentResponse(
            intent = intent,
            confidence = confidence,
            contactName = null,
            messageText = null,
            proposedMessage = null,
            destination = null,
            locationAlias = null,
            routineName = null,
            pendingTask = null,
            missingSlots = emptyList(),
            userFacingQuestion = "ok",
            suggestionText = null,
            requiresConfirmation = false,
            shouldExecuteImmediately = false,
            safetyNotes = null
        )

    private fun inputUnknown(
        originalText: String,
        screenIsSensitive: Boolean = false,
        hasPendingConfirmation: Boolean = false
    ): PersonalAgentDecisionInput =
        PersonalAgentDecisionInput(
            originalText = originalText,
            normalizedText = originalText.lowercase(),
            agentState = AgentState.IDLE,
            appState = AppState.IDLE,
            memorySnapshot = PersonalMemorySnapshot(),
            externalApp = null,
            hasPendingConfirmation = hasPendingConfirmation,
            currentTimeMillis = 1_000L,
            parsedIntent = ParsedAgentIntent(
                intent = AgentIntent.UNKNOWN,
                slots = emptyList(),
                rawText = originalText,
                confidence = 0f
            ),
            screenIsSensitive = screenIsSensitive
        )

    private fun inputCustom(
        originalText: String,
        parsedIntent: ParsedAgentIntent
    ): PersonalAgentDecisionInput =
        PersonalAgentDecisionInput(
            originalText = originalText,
            normalizedText = originalText.lowercase(),
            agentState = AgentState.IDLE,
            appState = AppState.IDLE,
            memorySnapshot = PersonalMemorySnapshot(),
            externalApp = null,
            hasPendingConfirmation = false,
            currentTimeMillis = 1_000L,
            parsedIntent = parsedIntent
        )
}
