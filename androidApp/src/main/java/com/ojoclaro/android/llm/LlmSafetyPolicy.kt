package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent

object LlmSafetyPolicy {
    private val forcedFalseIntents = setOf(
        AgentIntent.OPEN_WHATSAPP,
        AgentIntent.OPEN_WHATSAPP_CHAT,
        AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
        AgentIntent.CALL_CONTACT,
        AgentIntent.OPEN_PHONE,
        AgentIntent.OPEN_MAPS,
        AgentIntent.NAVIGATE_TO_DESTINATION
    )

    private val forbiddenActionTokens = listOf(
        "call_phone",
        "action_call",
        "read_contacts",
        "background_location",
        "banco",
        "tarjeta",
        "codigo",
        "clave",
        "password",
        "token",
        "api key",
        "documento"
    )

    fun coerce(response: LlmAgentResponse): LlmAgentResponse {
        val safeIntent = response.intent
        val forceNoImmediate = safeIntent != null && safeIntent in forcedFalseIntents
        return response.copy(
            confidence = response.confidence.coerceIn(0f, 1f),
            shouldExecuteImmediately = if (forceNoImmediate) false else response.shouldExecuteImmediately,
            safetyNotes = response.safetyNotes?.trim()
        )
    }

    fun requiresManualReview(response: LlmAgentResponse): Boolean =
        response.confidence < 0.75f || response.shouldExecuteImmediately

    fun isForbiddenAction(action: String): Boolean {
        val normalized = action.lowercase()
        return forbiddenActionTokens.any { normalized.contains(it) }
    }
}

