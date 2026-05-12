package com.ojoclaro.android.domain

import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlot
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.agent.SuggestionType
import com.ojoclaro.android.global.GlobalAssistantCapabilityGate
import com.ojoclaro.android.llm.LlmAgentInterpreter
import com.ojoclaro.android.llm.LlmAgentRequest
import com.ojoclaro.android.llm.LlmAgentResponse
import com.ojoclaro.android.memory.PersonalAgentMemory
import com.ojoclaro.android.memory.PersonalMemorySnapshot
import com.ojoclaro.android.memory.PersonalMemoryType
import com.ojoclaro.android.model.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PersonalAgentDecisionEngineTest {

    private val engine = PersonalAgentDecisionEngine()

    @Test
    fun abrirWpSinContinuationRealSeQuedaEnOjoClaro() = runTest {
        val parsed = LocalIntentParser().parse("abrir wp")
        val decision = engine.decide(
            input(
                originalText = "abrir wp",
                normalizedText = "abrir whatsapp",
                parsedIntent = parsed,
                globalCapability = GlobalAssistantCapabilityGate.unavailable("mic_unavailable")
            )
        )

        assertTrue(decision is PersonalAgentDecision.AskQuestion)
        val ask = decision as PersonalAgentDecision.AskQuestion
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, ask.targetState)
        assertTrue(ask.spokenText.contains("WhatsApp principal", ignoreCase = true))
        assertTrue(ask.spokenText.contains("chat", ignoreCase = true))
    }

    @Test
    fun abrirWpConContinuationRealPuedeAbrirWhatsApp() = runTest {
        val parsed = LocalIntentParser().parse("abrir wp")
        val decision = engine.decide(
            input(
                originalText = "abrir wp",
                normalizedText = "abrir whatsapp",
                parsedIntent = parsed,
                globalCapability = globalCapabilityReady()
            )
        )

        assertTrue(decision is PersonalAgentDecision.ExecuteSafeAction)
        val action = decision as PersonalAgentDecision.ExecuteSafeAction
        assertNotNull(action.externalEvent)
        assertTrue(action.spokenText.contains("Puedo seguir", ignoreCase = true))
    }

    @Test
    fun meVoyAlLaburoSugiereRutaSiHayMemoria() = runTest {
        val decision = engine.decide(
            input(
                originalText = "me voy al laburo",
                normalizedText = "me voy al laburo",
                parsedIntent = ParsedAgentIntent(
                    intent = AgentIntent.UNKNOWN,
                    slots = emptyList(),
                    rawText = "me voy al laburo",
                    confidence = 0f
                ),
                memorySnapshot = snapshot(
                    memory(
                        id = "place-1",
                        type = PersonalMemoryType.PLACE,
                        label = "laburo",
                        value = "laburo"
                    )
                ),
                currentTimeMillis = 8 * HOUR_MILLIS
            )
        )

        assertTrue(decision is PersonalAgentDecision.SuggestAction)
        val suggestion = (decision as PersonalAgentDecision.SuggestAction).suggestion
        assertEquals(SuggestionType.ROUTINE, suggestion.type)
        assertEquals(AgentIntent.NAVIGATE_TO_DESTINATION, suggestion.proposedIntent)
        assertTrue(suggestion.text.contains("ruta", ignoreCase = true))
    }

    @Test
    fun queTengoPendienteListaPendingTasks() = runTest {
        val decision = engine.decide(
            input(
                originalText = "que tengo pendiente",
                normalizedText = "que tengo pendiente",
                parsedIntent = ParsedAgentIntent(
                    intent = AgentIntent.UNKNOWN,
                    slots = emptyList(),
                    rawText = "que tengo pendiente",
                    confidence = 0f
                ),
                memorySnapshot = snapshot(
                    memory(
                        id = "pending-1",
                        type = PersonalMemoryType.PENDING_TASK,
                        label = "Marco",
                        value = "Responderle a Marco"
                    )
                )
            )
        )

        assertTrue(decision is PersonalAgentDecision.SuggestAction)
        val suggestion = (decision as PersonalAgentDecision.SuggestAction).suggestion
        assertEquals(SuggestionType.PENDING_TASK, suggestion.type)
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, suggestion.proposedIntent)
        assertTrue(suggestion.text.contains("pendiente", ignoreCase = true))
    }

    @Test
    fun decileQueLlegoEn10UsaContactoContextual() = runTest {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "llego en 10", confidence = 0.9f)
            ),
            rawText = "decile que llego en 10",
            confidence = 0.9f,
            missingSlots = listOf(AgentSlotName.CONTACT_NAME),
            requiresConfirmation = false
        )

        val decision = engine.decide(
            input(
                originalText = "decile que llego en 10",
                normalizedText = "decir que llego en 10",
                parsedIntent = parsed,
                memorySnapshot = snapshot(
                    memory(
                        id = "contact-1",
                        type = PersonalMemoryType.CONTACT,
                        label = "Marco",
                        value = "Marco es contacto de confianza"
                    )
                )
            )
        )

        assertTrue(decision is PersonalAgentDecision.ComposeHumanMessage)
        val composition = (decision as PersonalAgentDecision.ComposeHumanMessage).composition
        assertTrue(composition.proposedMessage.contains("Llego en 10", ignoreCase = true))
        assertTrue(composition.requiresConfirmation)
        assertFalse(composition.shouldSendAutomatically)
    }

    @Test
    fun contactoFavoritoDemoNormalizaAliasSinAgenda() = runTest {
        val parsed = LocalIntentParser().parse("decile a contacto demo que estoy llegando")

        val decision = engine.decide(
            input(
                originalText = "decile a contacto demo que estoy llegando",
                normalizedText = "decir a contacto demo que estoy llegando",
                parsedIntent = parsed
            )
        )

        assertTrue(decision is PersonalAgentDecision.ComposeHumanMessage)
        val compose = decision as PersonalAgentDecision.ComposeHumanMessage
        assertEquals("Contacto demo", compose.contactName)
        assertTrue(compose.composition.requiresConfirmation)
        assertFalse(compose.composition.shouldSendAutomatically)
    }

    @Test
    fun fraseHumanaDeMensajeUsaLlmSiEstaDisponible() = runTest {
        val llmEngine = PersonalAgentDecisionEngine(
            llmAgentInterpreter = object : LlmAgentInterpreter {
                override suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse =
                    LlmAgentResponse(
                        intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
                        responseType = "propose_whatsapp_message",
                        confidence = 0.91f,
                        contactName = "Contacto demo",
                        messageText = "llego tarde",
                        proposedMessage = "Estoy llegando un poco tarde, pero ya estoy en camino.",
                        destination = null,
                        locationAlias = null,
                        routineName = null,
                        pendingTask = null,
                        missingSlots = emptyList(),
                        userFacingQuestion = "Puedo preparar este mensaje para tu contacto: Estoy llegando un poco tarde, pero ya estoy en camino. Decime confirmar para prepararlo.",
                        suggestionText = null,
                        requiresConfirmation = true,
                        shouldExecuteImmediately = false,
                        safetyNotes = null
                    )
            }
        )
        val parsed = LocalIntentParser().parse("decile a contacto demo que llego tarde pero decilo bien")

        val decision = llmEngine.decide(
            input(
                originalText = "decile a contacto demo que llego tarde pero decilo bien",
                normalizedText = "decir a contacto demo que llego tarde pero decirlo bien",
                parsedIntent = parsed
            )
        )

        assertTrue(decision is PersonalAgentDecision.ComposeHumanMessage)
        val compose = decision as PersonalAgentDecision.ComposeHumanMessage
        assertEquals("LLM_COMPOSE", compose.debugLabel)
        assertEquals("Contacto demo", compose.contactName)
        assertTrue(compose.composition.proposedMessage.contains("camino", ignoreCase = true))
        assertTrue(compose.composition.requiresConfirmation)
        assertFalse(compose.composition.shouldSendAutomatically)
    }

    @Test
    fun siYDaleNoConfirman() = runTest {
        val siDecision = engine.decide(
            input(
                originalText = "si",
                normalizedText = "si",
                parsedIntent = ParsedAgentIntent(
                    intent = AgentIntent.UNKNOWN,
                    slots = emptyList(),
                    rawText = "si",
                    confidence = 0f
                )
            )
        )
        val daleDecision = engine.decide(
            input(
                originalText = "dale",
                normalizedText = "dale",
                parsedIntent = ParsedAgentIntent(
                    intent = AgentIntent.UNKNOWN,
                    slots = emptyList(),
                    rawText = "dale",
                    confidence = 0f
                )
            )
        )

        assertTrue(siDecision is PersonalAgentDecision.RetryListening)
        assertTrue(daleDecision is PersonalAgentDecision.RetryListening)
    }

    @Test
    fun unknownConTextoUtilPideFallbackLlm() = runTest {
        val decision = engine.decide(
            input(
                originalText = "algo raro que no entiendo",
                normalizedText = "algo raro que no entiendo",
                parsedIntent = ParsedAgentIntent(
                    intent = AgentIntent.UNKNOWN,
                    slots = emptyList(),
                    rawText = "algo raro que no entiendo",
                    confidence = 0f
                )
            )
        )

        assertTrue(decision is PersonalAgentDecision.UseLlmFallback)
        val fallback = decision as PersonalAgentDecision.UseLlmFallback
        // Safe AI Fallback v1: sin proxy real, la guarda degrada silenciosamente.
        // Antes esperabamos `response != null` con el DisabledLlmAgentInterpreter;
        // ahora el guard corta antes de llamar al interpreter y devuelve una
        // respuesta humana sin exponer detalles tecnicos.
        assertTrue(fallback.debugLabel.startsWith("SAFE_FALLBACK_") || fallback.debugLabel == "LLM_FALLBACK")
        assertFalse(fallback.reason.contains("No uso la IA", ignoreCase = true))
        assertFalse(fallback.reason.contains("proxy", ignoreCase = true))
    }

    private fun input(
        originalText: String,
        normalizedText: String,
        parsedIntent: ParsedAgentIntent,
        memorySnapshot: PersonalMemorySnapshot = PersonalMemorySnapshot(),
        globalCapability: com.ojoclaro.android.global.GlobalAssistantCapability =
            GlobalAssistantCapabilityGate.unavailable("test"),
        currentTimeMillis: Long = 1_000L
    ): PersonalAgentDecisionInput =
        PersonalAgentDecisionInput(
            originalText = originalText,
            normalizedText = normalizedText,
            agentState = AgentState.IDLE,
            appState = AppState.IDLE,
            memorySnapshot = memorySnapshot,
            externalApp = null,
            hasPendingConfirmation = false,
            currentTimeMillis = currentTimeMillis,
            parsedIntent = parsedIntent,
            globalCapability = globalCapability
        )

    private fun snapshot(vararg memories: PersonalAgentMemory): PersonalMemorySnapshot =
        PersonalMemorySnapshot(memories.toList())

    private fun memory(
        id: String,
        type: PersonalMemoryType,
        label: String,
        value: String
    ): PersonalAgentMemory =
        PersonalAgentMemory(
            id = id,
            type = type,
            label = label,
            value = value,
            createdAtMillis = 1_000L,
            updatedAtMillis = 1_000L,
            userApproved = true
        )

    private fun globalCapabilityReady(): com.ojoclaro.android.global.GlobalAssistantCapability =
        GlobalAssistantCapabilityGate.fromFlags(
            foregroundServiceReady = true,
            notificationReady = true,
            overlayReady = true,
            microphoneContinuationReady = true,
            fallbackReturnReady = true
        )

    private companion object {
        private const val HOUR_MILLIS = 60 * 60 * 1000L
    }
}
