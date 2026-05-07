package com.ojoclaro.android.domain

import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.ContextualSuggestionEngine
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.SuggestionContext
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.global.GlobalAssistantCapability
import com.ojoclaro.android.llm.DisabledLlmAgentInterpreter
import com.ojoclaro.android.llm.LlmAgentInterpreter
import com.ojoclaro.android.llm.LlmAgentRequest
import com.ojoclaro.android.llm.LlmAgentResponse
import com.ojoclaro.android.llm.LlmSafetyPolicy
import com.ojoclaro.android.llm.LlmUsageDecision
import com.ojoclaro.android.llm.LlmUsageGuard
import com.ojoclaro.android.message.HumanMessageComposer
import com.ojoclaro.android.message.LocalMessageTemplateComposer
import com.ojoclaro.android.message.MessageCompositionRequest
import com.ojoclaro.android.message.MessageCompositionResult
import com.ojoclaro.android.message.MessageStyle
import com.ojoclaro.android.memory.PersonalMemorySnapshot
import com.ojoclaro.android.memory.PersonalMemoryType
import com.ojoclaro.android.model.AppState
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.risk.RiskDetector

data class PersonalAgentDecisionInput(
    val originalText: String,
    val normalizedText: String,
    val agentState: AgentState,
    val appState: AppState,
    val memorySnapshot: PersonalMemorySnapshot,
    val externalApp: String?,
    val hasPendingConfirmation: Boolean,
    val currentTimeMillis: Long,
    val parsedIntent: com.ojoclaro.android.agent.ParsedAgentIntent? = null,
    val globalCapability: GlobalAssistantCapability = GlobalAssistantCapabilityGateSnapshot.unavailable(),
    val suggestionsEnabled: Boolean = true
)

sealed class PersonalAgentDecision(open val debugLabel: String) {
    data class AskQuestion(
        val spokenText: String,
        val targetState: AgentState,
        override val debugLabel: String
    ) : PersonalAgentDecision(debugLabel)

    data class SuggestAction(
        val suggestion: com.ojoclaro.android.agent.AgentSuggestion,
        override val debugLabel: String
    ) : PersonalAgentDecision(debugLabel)

    data class ComposeHumanMessage(
        val composition: MessageCompositionResult,
        val contactName: String,
        val originalMessageText: String,
        override val debugLabel: String
    ) : PersonalAgentDecision(debugLabel)

    data class RequestConfirmation(
        val spokenText: String,
        val proposedIntent: AgentIntent? = null,
        override val debugLabel: String
    ) : PersonalAgentDecision(debugLabel)

    data class ExecuteSafeAction(
        val spokenText: String,
        val externalEvent: ExternalActionEvent? = null,
        override val debugLabel: String
    ) : PersonalAgentDecision(debugLabel)

    data class UseLlmFallback(
        val request: LlmAgentRequest,
        val response: LlmAgentResponse?,
        val reason: String,
        override val debugLabel: String
    ) : PersonalAgentDecision(debugLabel)

    data class RejectUnsafe(
        val spokenText: String,
        override val debugLabel: String
    ) : PersonalAgentDecision(debugLabel)

    data class RetryListening(
        val spokenText: String,
        override val debugLabel: String
    ) : PersonalAgentDecision(debugLabel)

    data class Cancel(
        val spokenText: String = "Cancelado.",
        override val debugLabel: String = "CANCEL"
    ) : PersonalAgentDecision(debugLabel)

    data class DoNothing(
        override val debugLabel: String = "NONE"
    ) : PersonalAgentDecision(debugLabel)
}

class PersonalAgentDecisionEngine(
    private val localIntentParser: LocalIntentParser = LocalIntentParser(),
    private val humanMessageComposer: HumanMessageComposer = LocalMessageTemplateComposer(),
    private val suggestionEngine: ContextualSuggestionEngine = ContextualSuggestionEngine(),
    private val llmAgentInterpreter: LlmAgentInterpreter = DisabledLlmAgentInterpreter(),
    private val llmUsageGuard: LlmUsageGuard = LlmUsageGuard(),
    private val agentExecutionPolicy: AgentExecutionPolicy = AgentExecutionPolicy(),
    private val riskDetector: RiskDetector = RiskDetector()
) {
    suspend fun decide(input: PersonalAgentDecisionInput): PersonalAgentDecision {
        val cleanOriginal = input.originalText.trim()
        val normalized = input.normalizedText.trim()
        if (cleanOriginal.isBlank() && normalized.isBlank()) {
            return PersonalAgentDecision.DoNothing()
        }

        if (isCancelCommand(normalized)) {
            return PersonalAgentDecision.Cancel()
        }

        if (isStrictNoise(normalized) && !input.hasPendingConfirmation) {
            return PersonalAgentDecision.RetryListening("No escuché bien. Probá de nuevo.", "NOISE_RETRY")
        }

        if (PrivacyGuard.containsSensitiveFinancialData(cleanOriginal) ||
            riskDetector.detectFromCommand(cleanOriginal).any { it.requiresConfirmation }
        ) {
            return PersonalAgentDecision.RejectUnsafe(
                spokenText = "No puedo ayudar con ese dato porque parece sensible.",
                debugLabel = "RISK_BLOCK"
            )
        }

        val parsed = input.parsedIntent ?: localIntentParser.parse(cleanOriginal)
        val suggestion = if (parsed.intent == AgentIntent.UNKNOWN) {
            suggestionEngine.evaluate(
                SuggestionContext(
                    originalText = cleanOriginal,
                    normalizedText = normalized,
                    currentTimeMillis = input.currentTimeMillis,
                    agentState = input.agentState,
                    appState = input.appState,
                    memorySnapshot = input.memorySnapshot,
                    externalApp = input.externalApp,
                    suggestionsEnabled = input.suggestionsEnabled
                )
            ).firstOrNull()
        } else {
            null
        }
        if (suggestion != null) {
            return PersonalAgentDecision.SuggestAction(suggestion, "SUGGEST_${suggestion.type.name}")
        }

        return when (parsed.intent) {
            AgentIntent.OPEN_WHATSAPP -> decideWhatsApp(parsed, input)
            AgentIntent.OPEN_WHATSAPP_CHAT -> decideOpenWhatsAppChat(parsed, input)
            AgentIntent.COMPOSE_WHATSAPP_MESSAGE -> decideComposeMessage(parsed, input)
            AgentIntent.OPEN_MAPS,
            AgentIntent.OPEN_PHONE,
            AgentIntent.CALL_CONTACT -> decideExternalButSafe(parsed, input)
            AgentIntent.UNKNOWN -> decideLlmFallback(parsed, input)
            else -> PersonalAgentDecision.DoNothing("LOCAL_NOOP")
        }
    }

    private fun decideWhatsApp(
        parsed: com.ojoclaro.android.agent.ParsedAgentIntent,
        input: PersonalAgentDecisionInput
    ): PersonalAgentDecision {
        return if (parsed.missingSlots.contains(AgentSlotName.WHATSAPP_ACTION)) {
            if (input.globalCapability.canSafelyContinueOutsideApp) {
                PersonalAgentDecision.ExecuteSafeAction(
                    spokenText = "Abro WhatsApp. Puedo seguir por unos segundos. Decime el chat o el mensaje.",
                    externalEvent = ExternalActionEvent.ExternalAppHandoff(
                        externalAppName = "WhatsApp",
                        reason = "Abro WhatsApp.",
                        returnHint = "Para seguir, toca Escuchar o volve a Ojo Claro.",
                        spokenText = "Abro WhatsApp. Puedo seguir por unos segundos. Decime el chat o el mensaje.",
                        delegate = ExternalActionEvent.OpenWhatsApp
                    ),
                    debugLabel = "WHATSAPP_GLOBAL_CONTINUATION"
                )
            } else {
                PersonalAgentDecision.AskQuestion(
                    spokenText = AgentConversationManager.WHATSAPP_GUIDED_QUESTION,
                    targetState = AgentState.WAITING_WHATSAPP_ACTION,
                    debugLabel = "WHATSAPP_GUIDED_IN_APP"
                )
            }
        } else {
            PersonalAgentDecision.ExecuteSafeAction(
                spokenText = "Abro WhatsApp principal. Para seguir, toca Escuchar o volve a Ojo Claro.",
                externalEvent = ExternalActionEvent.ExternalAppHandoff(
                    externalAppName = "WhatsApp",
                    reason = "Abro WhatsApp principal.",
                    returnHint = "Para seguir, toca Escuchar o volve a Ojo Claro.",
                    spokenText = "Abro WhatsApp principal. Para seguir, toca Escuchar o volve a Ojo Claro.",
                    delegate = ExternalActionEvent.OpenWhatsApp
                ),
                debugLabel = "WHATSAPP_PRINCIPAL"
            )
        }
    }

    private fun decideOpenWhatsAppChat(
        parsed: com.ojoclaro.android.agent.ParsedAgentIntent,
        input: PersonalAgentDecisionInput
    ): PersonalAgentDecision {
        val contact = parsed.slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
        if (contact.isBlank()) {
            return PersonalAgentDecision.AskQuestion(
                spokenText = "¿Qué chat querés abrir?",
                targetState = AgentState.WAITING_CONTACT,
                debugLabel = "WHATSAPP_CHAT_NEEDS_CONTACT"
            )
        }
        return PersonalAgentDecision.RequestConfirmation(
            spokenText = "Voy a abrir el chat de $contact. No envío nada. Confirmá para continuar.",
            proposedIntent = AgentIntent.OPEN_WHATSAPP_CHAT,
            debugLabel = if (input.globalCapability.canSafelyContinueOutsideApp) {
                "WHATSAPP_CHAT_SAFE_CONFIRM"
            } else {
                "WHATSAPP_CHAT_IN_APP_CONFIRM"
            }
        )
    }

    private suspend fun decideComposeMessage(
        parsed: com.ojoclaro.android.agent.ParsedAgentIntent,
        input: PersonalAgentDecisionInput
    ): PersonalAgentDecision {
        val contact = parsed.slotValue(AgentSlotName.CONTACT_NAME).orEmpty().ifBlank {
            input.memorySnapshot.contacts.firstOrNull()?.label.orEmpty()
        }
        val message = parsed.slotValue(AgentSlotName.MESSAGE_TEXT).orEmpty()
        if (contact.isBlank()) {
            return PersonalAgentDecision.AskQuestion(
                spokenText = "¿A quién querés mandarle el mensaje?",
                targetState = AgentState.WAITING_CONTACT,
                debugLabel = "COMPOSE_NEEDS_CONTACT"
            )
        }
        if (message.isBlank()) {
            return PersonalAgentDecision.AskQuestion(
                spokenText = "¿Qué mensaje querés mandarle a $contact?",
                targetState = AgentState.WAITING_MESSAGE,
                debugLabel = "COMPOSE_NEEDS_MESSAGE"
            )
        }

        if (shouldUseFlexibleMessageDraft(input.originalText)) {
            val llmDecision = decideLlmFallback(parsed, input)
            if (llmDecision is PersonalAgentDecision.ComposeHumanMessage) {
                return llmDecision
            }
        }

        val style = inferStyle(input.memorySnapshot, contact)
        val composition = humanMessageComposer.compose(
            MessageCompositionRequest(
                originalText = input.originalText,
                contactName = contact,
                messageHint = message,
                style = style,
                memorySnapshot = input.memorySnapshot
            )
        )
        return PersonalAgentDecision.ComposeHumanMessage(
            composition = composition,
            contactName = contact,
            originalMessageText = message,
            debugLabel = if (composition.blockedReason != null) "COMPOSE_BLOCKED" else "COMPOSE_LOCAL"
        )
    }

    private fun shouldUseFlexibleMessageDraft(text: String): Boolean {
        val normalized = text.lowercase()
        return listOf(
            "decilo bien",
            "decirlo bien",
            "decilo mejor",
            "mas amable",
            "más amable",
            "mas calido",
            "más cálido",
            "calido",
            "cálido",
            "cariñoso",
            "formal",
            "profesional",
            "con tono",
            "redact",
            "propon"
        ).any { normalized.contains(it) }
    }

    private fun decideExternalButSafe(
        parsed: com.ojoclaro.android.agent.ParsedAgentIntent,
        input: PersonalAgentDecisionInput
    ): PersonalAgentDecision {
        return when (parsed.intent) {
            AgentIntent.OPEN_MAPS ->
                PersonalAgentDecision.RequestConfirmation(
                    spokenText = "¿Querés que abra mapas?",
                    proposedIntent = AgentIntent.OPEN_MAPS,
                    debugLabel = if (input.globalCapability.canSafelyContinueOutsideApp) {
                        "MAPS_CONTINUATION"
                    } else {
                        "MAPS_CONFIRM"
                    }
                )

            AgentIntent.OPEN_PHONE ->
                PersonalAgentDecision.RequestConfirmation(
                    spokenText = "¿Querés que abra el marcador?",
                    proposedIntent = AgentIntent.OPEN_PHONE,
                    debugLabel = "PHONE_CONFIRM"
                )

            AgentIntent.CALL_CONTACT ->
                PersonalAgentDecision.RequestConfirmation(
                    spokenText = "¿Confirmás que querés llamar?",
                    proposedIntent = AgentIntent.CALL_CONTACT,
                    debugLabel = "CALL_CONFIRM"
                )

            else -> PersonalAgentDecision.DoNothing("SAFE_EXTERNAL_NOOP")
        }
    }

    private suspend fun decideLlmFallback(
        parsed: com.ojoclaro.android.agent.ParsedAgentIntent,
        input: PersonalAgentDecisionInput
    ): PersonalAgentDecision {
        val request = LlmAgentRequest(
            originalText = input.originalText,
            normalizedText = input.normalizedText,
            locale = "es-AR",
            agentState = input.agentState,
            externalApp = input.externalApp,
            memorySummary = input.memorySnapshot.summary(),
            knownSafeContacts = input.memorySnapshot.contacts.map { it.label },
            knownPlaces = input.memorySnapshot.places.map { it.label },
            activePendingTasks = input.memorySnapshot.pendingTasks.map { it.value },
            allowedIntents = AgentIntent.entries.filter { it != AgentIntent.UNKNOWN },
            forbiddenActions = listOf(
                "read_contacts",
                "call_phone",
                "action_call",
                "background_location",
                "bancos",
                "tarjetas"
            )
        )
        val usageDecision = llmUsageGuard.canUse("LLM fallback for ${parsed.intent.name}")
        if (usageDecision is LlmUsageDecision.Blocked) {
            return PersonalAgentDecision.UseLlmFallback(
                request = request,
                response = null,
                reason = usageDecision.humanMessage,
                debugLabel = "LLM_BLOCKED_${usageDecision.code.uppercase()}"
            )
        }
        val response = runCatching { llmAgentInterpreter.interpret(request) }.getOrNull()
        val coerced = response?.let(LlmSafetyPolicy::coerce)
        if (coerced == null) {
            llmUsageGuard.recordFailure("no_response")
        } else {
            llmUsageGuard.recordSuccess("response_received")
        }
        if (coerced == null || coerced.confidence < 0.75f || coerced.intent == null) {
            return PersonalAgentDecision.UseLlmFallback(
                request = request,
                response = coerced,
                reason = coerced?.safetyNotes ?: "LLM disabled or low confidence",
                debugLabel = "LLM_FALLBACK"
            )
        }
        return when (coerced.intent) {
            AgentIntent.COMPOSE_WHATSAPP_MESSAGE -> {
                val contact = coerced.contactName ?: input.memorySnapshot.contacts.firstOrNull()?.label.orEmpty()
                val proposal = coerced.proposedMessage ?: coerced.messageText.orEmpty()
                val composition = MessageCompositionResult(
                    proposedMessage = proposal,
                    spokenProposal = coerced.userFacingQuestion ?: "Te propongo: $proposal. ¿Lo preparo?",
                    styleUsed = MessageStyle.NEUTRAL,
                    requiresConfirmation = true,
                    shouldSendAutomatically = false,
                    safetyNotes = coerced.safetyNotes
                )
                PersonalAgentDecision.ComposeHumanMessage(
                    composition = composition,
                    contactName = contact,
                    originalMessageText = coerced.messageText.orEmpty(),
                    debugLabel = "LLM_COMPOSE"
                )
            }
            AgentIntent.OPEN_MAPS,
            AgentIntent.OPEN_PHONE,
            AgentIntent.OPEN_WHATSAPP,
            AgentIntent.CALL_CONTACT -> PersonalAgentDecision.UseLlmFallback(
                request = request,
                response = coerced,
                reason = "LLM suggested risky action; keep local control.",
                debugLabel = "LLM_RISKY"
            )
            else -> PersonalAgentDecision.UseLlmFallback(
                request = request,
                response = coerced,
                reason = coerced.safetyNotes ?: "LLM response requires local review.",
                debugLabel = "LLM_FALLBACK"
            )
        }
    }

    private fun inferStyle(
        snapshot: PersonalMemorySnapshot,
        contact: String
    ): MessageStyle {
        val styleMemory = snapshot.messageStyles.firstOrNull { memory ->
            memory.label.contains(contact, ignoreCase = true) ||
                memory.value.contains(contact, ignoreCase = true) ||
                memory.value.contains("cari", ignoreCase = true) ||
                memory.value.contains("formal", ignoreCase = true) ||
                memory.value.contains("tranquil", ignoreCase = true)
        } ?: return MessageStyle.NEUTRAL
        val value = styleMemory.value.lowercase()
        return when {
            value.contains("cari") -> MessageStyle.WARM
            value.contains("formal") -> MessageStyle.FORMAL
            value.contains("tranquil") -> MessageStyle.CALM
            value.contains("profes") -> MessageStyle.PROFESSIONAL
            value.contains("breve") -> MessageStyle.BRIEF
            else -> MessageStyle.NEUTRAL
        }
    }

    private fun isCancelCommand(normalizedText: String): Boolean {
        val normalized = normalizedText.lowercase()
        return normalized in setOf("cancelar", "cancela", "anular", "stop")
    }

    private fun isStrictNoise(normalizedText: String): Boolean {
        val normalized = normalizedText.lowercase()
        return normalized in setOf("si", "si si", "dale", "ok", "bueno", "aja", "ajá")
    }
}

private object GlobalAssistantCapabilityGateSnapshot {
    fun unavailable(): GlobalAssistantCapability =
        com.ojoclaro.android.global.GlobalAssistantCapabilityGate.unavailable()
}
