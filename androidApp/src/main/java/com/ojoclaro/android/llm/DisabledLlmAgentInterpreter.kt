package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent

/**
 * Interpreter "vacio" para cuando no hay proxy de IA configurado.
 *
 * Devuelve siempre UNKNOWN con un mensaje humano y util, NUNCA expone al usuario
 * detalles internos sobre la IA, el proxy o por que no se uso un modelo remoto.
 */
class DisabledLlmAgentInterpreter : LlmAgentInterpreter {
    override suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse {
        val allowed = request.allowedIntents
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
            userFacingQuestion = SafeAiFallbackCopy.GENERAL,
            suggestionText = null,
            requiresConfirmation = false,
            shouldExecuteImmediately = false,
            safetyNotes = "llm_disabled"
        )
    }
}
