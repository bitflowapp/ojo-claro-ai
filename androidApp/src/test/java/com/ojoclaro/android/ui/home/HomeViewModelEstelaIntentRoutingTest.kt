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
    fun exactSamsungHelpAndGreetingPhrasesReturnUsefulDeterministicResponse() {
        listOf(
            "Hola Estela" to ESTELA_GREETING_TEXT,
            "Hola, qu\u00E9 pod\u00E9s hacer" to ESTELA_CAPABILITIES_TEXT,
            "Qu\u00E9 pod\u00E9s hacer" to ESTELA_CAPABILITIES_TEXT,
            "Qu\u00E9 puedes hacer" to ESTELA_CAPABILITIES_TEXT,
            "Ayuda" to ESTELA_CAPABILITIES_TEXT
        ).forEach { (phrase, expected) ->
            val response = deterministicAssistantResponseFor(phrase)

            assertEquals(expected, response, "phrase=$phrase")
            assertFalse(response.orEmpty().contains("No entend", ignoreCase = true), "phrase=$phrase")
            assertFalse(response.orEmpty().contains("contacto", ignoreCase = true), "phrase=$phrase")
            assertFalse(response.orEmpty().contains("enviado", ignoreCase = true), "phrase=$phrase")
        }
    }

    @Test
    fun estelaTraceSpeechLabelDoesNotExposeComposePayload() {
        val label = estelaTraceSpeechLabel("Mandale a Sofi que ya llegu\u00E9")

        assertTrue(label.contains("compose_whatsapp_message"))
        assertTrue(label.contains("[redacted-message]"))
        assertFalse(label.contains("Sofi", ignoreCase = true))
        assertFalse(label.contains("llegu", ignoreCase = true))
    }

    @Test
    fun intentRuntimeSkipReasonExplainsWaitingConfirmationGuard() {
        val reason = estelaIntentRuntimeSkipReason(
            assistantBaseUrlConfigured = true,
            hasPendingExternalConfirmation = false,
            hasPendingConsentAction = false,
            hasPendingVoiceCorrection = false,
            uiHasPendingConfirmation = false,
            runtimeHasPendingConfirmation = false,
            managerInWaitingConfirmation = true
        )

        assertEquals("manager_waiting_confirmation", reason)
    }

    @Test
    fun deterministicCapabilityQuestionReturnsUsefulHelp() {
        val response = deterministicAssistantResponseFor("qué podés hacer")

        assertEquals(ESTELA_CAPABILITIES_TEXT, response)
        assertTrue(response!!.contains("leer la pantalla", ignoreCase = true))
        assertTrue(response.contains("WhatsApp", ignoreCase = true))
    }

    @Test
    fun deterministicGreetingReturnsAssistantPrompt() {
        val response = deterministicAssistantResponseFor("hola Estela")

        assertEquals(ESTELA_GREETING_TEXT, response)
        assertTrue(response!!.contains("Estela", ignoreCase = true))
    }

    @Test
    fun deterministicHelpWithGreetingAndPunctuationWorks() {
        assertEquals(ESTELA_CAPABILITIES_TEXT, deterministicAssistantResponseFor("hola, qué podés hacer"))
    }

    @Test
    fun deterministicHelpDoesNotHijackWhatsAppCompose() {
        assertEquals(null, deterministicAssistantResponseFor("mandale un mensaje a Sofi diciendo que ya llegue"))
    }

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
    fun recoverableIntentRuntimeFailureFallsBackToLegacyWithoutApplying() {
        var legacyCalls = 0
        var appliedCalls = 0
        val result = EstelaIntentEngineResult(
            request = EstelaIntentRequest(
                userText = "abrir whatsapp",
                conversationState = "idle"
            ),
            rawJson = null,
            adapterResult = null,
            spokenText = "No pude conectarme para interpretar eso.",
            fallbackReason = "intent_proxy_http_404",
            shouldFallbackToLocal = true
        )

        val completion = completeEstelaIntentRuntimeOrFallback(
            result = result,
            shouldDrop = false,
            applyResult = {
                appliedCalls += 1
                canApplyEstelaIntentRuntimeResult(it)
            },
            submitLegacy = { legacyCalls += 1 }
        )

        assertEquals(EstelaIntentRuntimeCompletion.LEGACY_FALLBACK, completion)
        assertEquals(1, legacyCalls)
        assertEquals(1, appliedCalls)
    }

    @Test
    fun adapterResultNullIsNotAppliedAsFinalIntentResponse() {
        val result = EstelaIntentEngineResult(
            request = EstelaIntentRequest(
                userText = "abrir whatsapp",
                conversationState = "idle"
            ),
            rawJson = null,
            adapterResult = null,
            spokenText = "No pude conectarme para interpretar eso.",
            fallbackReason = "intent_proxy_http_500"
        )

        assertFalse(canApplyEstelaIntentRuntimeResult(result))
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

    // ── M1 guard regression: WAITING_CONFIRMATION must keep "confirmar" on the
    // legacy path so AgentConversationManager.handle(CONFIRM) actually executes
    // the pending action instead of being consumed as TTS-only by /intent.

    @Test
    fun managerInWaitingConfirmationDisablesIntentRuntime() {
        assertFalse(
            shouldUseEstelaIntentRuntime(
                assistantBaseUrlConfigured = true,
                hasPendingExternalConfirmation = false,
                hasPendingConsentAction = false,
                hasPendingVoiceCorrection = false,
                uiHasPendingConfirmation = false,
                runtimeHasPendingConfirmation = false,
                managerInWaitingConfirmation = true
            )
        )
    }

    @Test
    fun managerNotInWaitingConfirmationDoesNotDisableIntentRuntime() {
        // Defensive: the new guard is scoped to WAITING_CONFIRMATION only and
        // does not regress the happy path.
        assertTrue(
            shouldUseEstelaIntentRuntime(
                assistantBaseUrlConfigured = true,
                hasPendingExternalConfirmation = false,
                hasPendingConsentAction = false,
                hasPendingVoiceCorrection = false,
                uiHasPendingConfirmation = false,
                runtimeHasPendingConfirmation = false,
                managerInWaitingConfirmation = false
            )
        )
    }

    @Test
    fun activityLocalReadAndNavigationRoutesWinBeforeIntentRuntime() {
        val expectedRoutes = mapOf(
            "lee wp" to ActivityLocalRouteBeforeIntent.WHATSAPP_READ_ALOUD,
            "leeme los chat que aparecen en pantalla" to ActivityLocalRouteBeforeIntent.WHATSAPP_VISIBLE_CHATS,
            "que chats hay" to ActivityLocalRouteBeforeIntent.WHATSAPP_VISIBLE_CHATS,
            "leeme esta conversacion" to ActivityLocalRouteBeforeIntent.WHATSAPP_MESSAGES,
            "\u00faltimo mensaje de WhatsApp" to ActivityLocalRouteBeforeIntent.WHATSAPP_MESSAGES,
            "leeme lo que aparece en pantalla" to ActivityLocalRouteBeforeIntent.SCREEN_UNDERSTANDING,
            "baja" to ActivityLocalRouteBeforeIntent.SCREEN_NAVIGATION,
            "subi" to ActivityLocalRouteBeforeIntent.SCREEN_NAVIGATION,
            "volver" to ActivityLocalRouteBeforeIntent.SCREEN_NAVIGATION,
            "abri el primer chat" to ActivityLocalRouteBeforeIntent.WHATSAPP_ORDINAL_CHAT_OPEN
        )

        expectedRoutes.forEach { (phrase, expectedRoute) ->
            assertEquals(expectedRoute, activityLocalRouteBeforeEstelaIntentRuntime(phrase), "phrase=$phrase")
        }
    }

    @Test
    fun activityLocalReadPhrasesDoNotStartIntentRuntime() {
        listOf(
            "lee wp",
            "leeme los chat que aparecen en pantalla",
            "que chats hay",
            "leeme esta conversacion",
            "\u00faltimo mensaje de WhatsApp",
            "leeme lo que aparece en pantalla"
        ).forEach { phrase ->
            var intentRuntimeCalls = 0
            val legacyInputs = mutableListOf<String>()

            val path = submitVoiceTextViaIntentOrLegacy(
                text = phrase,
                // Espeja la defensa real de handleEstelaIntentRuntimeIfNeeded: el
                // MISMO gate puro que usa producción, no un check inventado.
                startIntentRuntime = { input ->
                    if (localActivityRouteBlocksEstelaIntentRuntime(input)) {
                        false
                    } else {
                        intentRuntimeCalls += 1
                        shouldUseEstelaIntentRuntime(
                            assistantBaseUrlConfigured = true,
                            hasPendingExternalConfirmation = false,
                            hasPendingConsentAction = false,
                            hasPendingVoiceCorrection = false,
                            uiHasPendingConfirmation = false,
                            runtimeHasPendingConfirmation = false
                        )
                    }
                },
                submitLegacy = { input -> legacyInputs += input }
            )

            assertEquals(EstelaIntentSubmitPath.LEGACY_FALLBACK, path, "phrase=$phrase")
            assertEquals(0, intentRuntimeCalls, "phrase=$phrase")
            assertEquals(listOf(phrase), legacyInputs, "phrase=$phrase")
        }
    }

    @Test
    fun localActivityRouteGateBlocksReadAndNavBeforeIntentRuntime() {
        // Defensa en profundidad: estas frases NUNCA pueden disparar /intent,
        // entren por donde entren. Se prueba el MISMO gate puro que aplica
        // handleEstelaIntentRuntimeIfNeeded al inicio (no un lambda inventado).
        listOf(
            "lee wp",
            "leeme los chat que aparecen en pantalla",
            "qué chats hay",
            "leeme esta conversación",
            "último mensaje de WhatsApp",
            "leeme lo que aparece en pantalla",
            "bajá",
            "subí",
            "volver",
            "abrí el primer chat"
        ).forEach { phrase ->
            assertTrue(
                localActivityRouteBlocksEstelaIntentRuntime(phrase),
                "phrase=$phrase debe bloquear /intent (ruta local de lectura/navegacion)"
            )
        }
    }

    @Test
    fun localActivityRouteGateDoesNotBlockGenuineIntentCommands() {
        // Comandos que NO son lectura/navegacion local: el gate no los bloquea,
        // /intent sigue disponible. (WHATSAPP_GUIDED queda fuera del gate a proposito.)
        listOf(
            "abrí WhatsApp",
            "mandale a Marco que ya llegué",
            "qué hora es",
            "contame un chiste",
            "cómo mando una foto"
        ).forEach { phrase ->
            assertFalse(
                localActivityRouteBlocksEstelaIntentRuntime(phrase),
                "phrase=$phrase no debe bloquear /intent"
            )
        }
    }

    @Test
    fun screenReadDiagnosticPhraseRoutesLocallyAndNeverHitsIntent() {
        // El botón de prueba sin voz inyecta esta frase. Debe clasificar como
        // SCREEN_UNDERSTANDING (ruta local) y bloquear /intent, para que el test
        // aísle router/AccessibilityService/TTS sin depender de la red.
        assertEquals(
            ActivityLocalRouteBeforeIntent.SCREEN_UNDERSTANDING,
            activityLocalRouteBeforeEstelaIntentRuntime(SCREEN_READ_DIAGNOSTIC_PHRASE),
            "la frase del botón de prueba debe enrutar a SCREEN_UNDERSTANDING"
        )
        assertTrue(
            localActivityRouteBlocksEstelaIntentRuntime(SCREEN_READ_DIAGNOSTIC_PHRASE),
            "la frase del botón de prueba nunca debe disparar /intent"
        )
    }

    @Test
    fun waitingConfirmationConfirmarFallsBackToLegacyWithoutDroppingInput() {
        val legacyInputs = mutableListOf<String>()

        val path = submitVoiceTextViaIntentOrLegacy(
            text = "confirmar",
            startIntentRuntime = {
                shouldUseEstelaIntentRuntime(
                    assistantBaseUrlConfigured = true,
                    hasPendingExternalConfirmation = false,
                    hasPendingConsentAction = false,
                    hasPendingVoiceCorrection = false,
                    uiHasPendingConfirmation = false,
                    runtimeHasPendingConfirmation = false,
                    managerInWaitingConfirmation = true
                )
            },
            submitLegacy = { input -> legacyInputs += input }
        )

        assertEquals(EstelaIntentSubmitPath.LEGACY_FALLBACK, path)
        assertEquals(listOf("confirmar"), legacyInputs)
    }

    @Test
    fun waitingConfirmationFillerStaysInLegacyAndDoesNotDropInput() {
        // dale/sí/si/ok/bueno/ajá/claro must stay on the legacy path so the
        // AgentConversationManager invalidConfirmationReprompt branch fires
        // and preserves the pending action.
        listOf("dale", "sí", "si", "ok", "bueno", "ajá", "claro").forEach { phrase ->
            val legacyInputs = mutableListOf<String>()

            val path = submitVoiceTextViaIntentOrLegacy(
                text = phrase,
                startIntentRuntime = {
                    shouldUseEstelaIntentRuntime(
                        assistantBaseUrlConfigured = true,
                        hasPendingExternalConfirmation = false,
                        hasPendingConsentAction = false,
                        hasPendingVoiceCorrection = false,
                        uiHasPendingConfirmation = false,
                        runtimeHasPendingConfirmation = false,
                        managerInWaitingConfirmation = true
                    )
                },
                submitLegacy = { input -> legacyInputs += input }
            )

            assertEquals(EstelaIntentSubmitPath.LEGACY_FALLBACK, path, "phrase=$phrase")
            assertEquals(listOf(phrase), legacyInputs, "phrase=$phrase")
        }
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
