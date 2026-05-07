package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent

class DisabledLlmAgentInterpreter : LlmAgentInterpreter {
    override suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse {
        val allowed = request.allowedIntents
        val notes = "LLM deshabilitado. Falta configurar el proxy local."
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
            userFacingQuestion = "La IA flexible está apagada en esta versión. Puedo ayudarte con comandos básicos del teléfono, lectura y funciones locales.",
            suggestionText = null,
            requiresConfirmation = false,
            shouldExecuteImmediately = false,
            safetyNotes = notes
        )
    }
}
