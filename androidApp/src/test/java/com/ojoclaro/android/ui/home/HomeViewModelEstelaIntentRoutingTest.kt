package com.ojoclaro.android.ui.home

import ai.ojoclaro.adapter.LlmIntentAdapterResult
import ai.ojoclaro.router.IntentRouteRequest
import ai.ojoclaro.router.IntentRouteResult
import android.content.Intent
import com.ojoclaro.android.llm.EstelaIntentEngineResult
import com.ojoclaro.android.llm.EstelaIntentRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Direct HomeViewModel construction pulls in AndroidViewModel, BuildConfig-backed
 * clients, and app singletons. These tests stay on the internal entrypoint seam
 * used by submitVoiceTextViaIntent(), so the reviewed fallback/recursion branch
 * is covered without broadening the production constructor.
 */
class HomeViewModelEstelaIntentRoutingTest {

    @Test
    fun configuredAssistantBaseUrlUsesIntentRuntimeBeforeLegacy() {
        assertTrue(
            shouldUseEstelaIntentRuntime(
                assistantBaseUrlConfigured = true,
                hasPendingExternalConfirmation = false,
                hasPendingConsentAction = false,
                hasPendingVoiceCorrection = false,
                uiHasPendingConfirmation = false,
                runtimeHasPendingConfirmation = false
            )
        )
    }

    @Test
    fun blankAssistantBaseUrlFallsBackToLegacyWithoutDroppingInput() {
        val legacyInputs = mutableListOf<String>()
        val startedInputs = mutableListOf<String>()

        val path = submitVoiceTextViaIntentOrLegacy(
            text = "  abrir whatsapp  ",
            startIntentRuntime = { input ->
                startedInputs += input
                shouldUseEstelaIntentRuntime(
                    assistantBaseUrlConfigured = false,
                    hasPendingExternalConfirmation = false,
                    hasPendingConsentAction = false,
                    hasPendingVoiceCorrection = false,
                    uiHasPendingConfirmation = false,
                    runtimeHasPendingConfirmation = false
                )
            },
            submitLegacy = { input -> legacyInputs += input }
        )

        assertEquals(EstelaIntentSubmitPath.LEGACY_FALLBACK, path)
        assertEquals(listOf("abrir whatsapp"), startedInputs)
        assertEquals(listOf("abrir whatsapp"), legacyInputs)
    }

    @Test
    fun runtimeNullFallsBackToLegacyWithoutDroppingInput() {
        var legacyCalls = 0
        var appliedCalls = 0

        val completion = completeEstelaIntentRuntimeOrFallback(
            result = null,
            shouldDrop = false,
            applyResult = {
                appliedCalls += 1
                true
            },
            submitLegacy = { legacyCalls += 1 }
        )

        assertEquals(EstelaIntentRuntimeCompletion.LEGACY_FALLBACK, completion)
        assertEquals(1, legacyCalls)
        assertEquals(0, appliedCalls)
    }

    @Test
    fun handledRuntimeResultDoesNotCallLegacy() {
        var legacyCalls = 0
        var appliedCalls = 0

        val completion = completeEstelaIntentRuntimeOrFallback(
            result = handledResult(),
            shouldDrop = false,
            applyResult = {
                appliedCalls += 1
                true
            },
            submitLegacy = { legacyCalls += 1 }
        )

        assertEquals(EstelaIntentRuntimeCompletion.APPLIED_RESULT, completion)
        assertEquals(0, legacyCalls)
        assertEquals(1, appliedCalls)
    }

    @Test
    fun staleRuntimeResultDoesNotApplyOrFallback() {
        var legacyCalls = 0
        var appliedCalls = 0

        val completion = completeEstelaIntentRuntimeOrFallback(
            result = null,
            shouldDrop = true,
            applyResult = {
                appliedCalls += 1
                true
            },
            submitLegacy = { legacyCalls += 1 }
        )

        assertEquals(EstelaIntentRuntimeCompletion.DROPPED_STALE, completion)
        assertEquals(0, legacyCalls)
        assertEquals(0, appliedCalls)
    }

    @Test
    fun pendingHomeViewModelGuardFallsBackToLegacyWithoutDroppingInput() {
        val legacyInputs = mutableListOf<String>()

        val path = submitVoiceTextViaIntentOrLegacy(
            text = "abrir telefono",
            startIntentRuntime = {
                shouldUseEstelaIntentRuntime(
                    assistantBaseUrlConfigured = true,
                    hasPendingExternalConfirmation = true,
                    hasPendingConsentAction = false,
                    hasPendingVoiceCorrection = false,
                    uiHasPendingConfirmation = false,
                    runtimeHasPendingConfirmation = false
                )
            },
            submitLegacy = { input -> legacyInputs += input }
        )

        assertEquals(EstelaIntentSubmitPath.LEGACY_FALLBACK, path)
        assertEquals(listOf("abrir telefono"), legacyInputs)
    }

    @Test
    fun pendingHomeViewModelGuardRejectsIntentRuntime() {
        assertFalse(
            shouldUseEstelaIntentRuntime(
                assistantBaseUrlConfigured = false,
                hasPendingExternalConfirmation = false,
                hasPendingConsentAction = false,
                hasPendingVoiceCorrection = false,
                uiHasPendingConfirmation = false,
                runtimeHasPendingConfirmation = false
            )
        )
    }

    @Test
    fun pendingHomeViewModelStatesRejectIntentRuntime() {
        assertFalse(
            shouldUseEstelaIntentRuntime(
                assistantBaseUrlConfigured = true,
                hasPendingExternalConfirmation = true,
                hasPendingConsentAction = false,
                hasPendingVoiceCorrection = false,
                uiHasPendingConfirmation = false,
                runtimeHasPendingConfirmation = false
            )
        )
        assertFalse(
            shouldUseEstelaIntentRuntime(
                assistantBaseUrlConfigured = true,
                hasPendingExternalConfirmation = false,
                hasPendingConsentAction = true,
                hasPendingVoiceCorrection = false,
                uiHasPendingConfirmation = false,
                runtimeHasPendingConfirmation = false
            )
        )
        assertFalse(
            shouldUseEstelaIntentRuntime(
                assistantBaseUrlConfigured = true,
                hasPendingExternalConfirmation = false,
                hasPendingConsentAction = false,
                hasPendingVoiceCorrection = true,
                uiHasPendingConfirmation = false,
                runtimeHasPendingConfirmation = false
            )
        )
    }

    @Test
    fun unknownRuntimeConfirmationFallsBackToLegacyWithoutDroppingInput() {
        val legacyInputs = mutableListOf<String>()

        val path = submitVoiceTextViaIntentOrLegacy(
            text = "confirmar",
            startIntentRuntime = {
                shouldUseEstelaIntentRuntime(
                    assistantBaseUrlConfigured = true,
                    hasPendingExternalConfirmation = false,
                    hasPendingConsentAction = false,
                    hasPendingVoiceCorrection = false,
                    uiHasPendingConfirmation = true,
                    runtimeHasPendingConfirmation = false
                )
            },
            submitLegacy = { input -> legacyInputs += input }
        )

        assertEquals(EstelaIntentSubmitPath.LEGACY_FALLBACK, path)
        assertEquals(listOf("confirmar"), legacyInputs)
    }

    @Test
    fun unknownRuntimeConfirmationRejectsIntentRuntime() {
        assertFalse(
            shouldUseEstelaIntentRuntime(
                assistantBaseUrlConfigured = true,
                hasPendingExternalConfirmation = false,
                hasPendingConsentAction = false,
                hasPendingVoiceCorrection = false,
                uiHasPendingConfirmation = true,
                runtimeHasPendingConfirmation = false
            )
        )
    }

    @Test
    fun runtimeOwnedConfirmationCanUseIntentRuntimeWithoutLegacyFallback() {
        val legacyInputs = mutableListOf<String>()
        val startedInputs = mutableListOf<String>()

        val path = submitVoiceTextViaIntentOrLegacy(
            text = "confirmar",
            startIntentRuntime = { input ->
                startedInputs += input
                shouldUseEstelaIntentRuntime(
                    assistantBaseUrlConfigured = true,
                    hasPendingExternalConfirmation = false,
                    hasPendingConsentAction = false,
                    hasPendingVoiceCorrection = false,
                    uiHasPendingConfirmation = true,
                    runtimeHasPendingConfirmation = true
                )
            },
            submitLegacy = { input -> legacyInputs += input }
        )

        assertEquals(EstelaIntentSubmitPath.INTENT_RUNTIME_STARTED, path)
        assertEquals(listOf("confirmar"), startedInputs)
        assertEquals(emptyList(), legacyInputs)
    }

    @Test
    fun routedActionResultFallsBackToLegacyWhenHomeCannotApplySideEffect() {
        var legacyCalls = 0
        var appliedCalls = 0
        val result = routedResult(IntentRouteResult.LaunchIntent(Intent(Intent.ACTION_DIAL)))

        val completion = completeEstelaIntentRuntimeOrFallback(
            result = result,
            shouldDrop = false,
            applyResult = { runtimeResult ->
                val canApply = canApplyEstelaIntentRuntimeResult(runtimeResult)
                if (canApply) appliedCalls += 1
                canApply
            },
            submitLegacy = { legacyCalls += 1 }
        )

        assertEquals(EstelaIntentRuntimeCompletion.LEGACY_FALLBACK, completion)
        assertEquals(1, legacyCalls)
        assertEquals(0, appliedCalls)
    }

    @Test
    fun routedNonActionResultIsHandledWithoutLegacyFallback() {
        var legacyCalls = 0
        var appliedCalls = 0
        val result = routedResult(IntentRouteResult.NoOp)

        val completion = completeEstelaIntentRuntimeOrFallback(
            result = result,
            shouldDrop = false,
            applyResult = { runtimeResult ->
                val canApply = canApplyEstelaIntentRuntimeResult(runtimeResult)
                if (canApply) appliedCalls += 1
                canApply
            },
            submitLegacy = { legacyCalls += 1 }
        )

        assertEquals(EstelaIntentRuntimeCompletion.APPLIED_RESULT, completion)
        assertEquals(0, legacyCalls)
        assertEquals(1, appliedCalls)
    }

    private fun handledResult(): EstelaIntentEngineResult =
        EstelaIntentEngineResult(
            request = EstelaIntentRequest(
                userText = "abrir telefono",
                conversationState = "idle"
            ),
            rawJson = "{}",
            adapterResult = null,
            spokenText = "Abro el marcador."
        )

    private fun routedResult(routeResult: IntentRouteResult): EstelaIntentEngineResult =
        EstelaIntentEngineResult(
            request = EstelaIntentRequest(
                userText = "abrir telefono",
                conversationState = "idle"
            ),
            rawJson = "{}",
            adapterResult = LlmIntentAdapterResult.Routed(
                request = IntentRouteRequest(
                    intent = "open_phone",
                    confidence = 0.95,
                    params = emptyMap(),
                    safetyLevel = "allow_safe",
                    voiceResponse = "Abro el marcador.",
                    voiceResponseTemplate = null,
                    rawText = "abrir telefono"
                ),
                routeResult = routeResult
            ),
            spokenText = "Abro el marcador."
        )
}
