package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent

class DisabledLlmAgentInterpreter : LlmAgentInterpreter {
    override suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse {
        val allowed = request.allowedIntents
        val notes = "LLM deshabilitado. Usa reglas locales."
        return LlmAgentResponse(
            intent = if (AgentIntent.UNKNOWN in allowed) AgentIntent.UNKNOWN else null,
            confidence = 0f,
            contactName = null,
            messageText = null,
            proposedMessage = null,
            destination = null,
            locationAlias = null,
            routineName = null,
            pendingTask = null,
            missingSlots = emptyList(),
            userFacingQuestion = "No pude interpretar eso con el modo flexible apagado.",
            suggestionText = null,
            requiresConfirmation = false,
            shouldExecuteImmediately = false,
            safetyNotes = notes
        )
    }
}

