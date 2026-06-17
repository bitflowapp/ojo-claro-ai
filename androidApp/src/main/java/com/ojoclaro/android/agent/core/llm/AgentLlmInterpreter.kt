package com.ojoclaro.android.agent.core.llm

/**
 * Interfaz para fallback de LLM dedicado al planner del Agent Core.
 *
 * Está separado del LlmAgentInterpreter existente en com.ojoclaro.android.llm
 * porque ese interpreta composición de mensajes humanos, no intents. Este
 * devuelve un LlmIntentCandidate que después pasa por LlmFallbackPolicy.
 *
 * Reglas:
 *  - La implementación NO ejecuta acciones, NO habla, NO persiste.
 *  - Si no hay backend configurado, debe devolver null.
 *  - El test suite usa FakeAgentLlmInterpreter en lugar de tocar internet.
 */
fun interface AgentLlmInterpreter {
    suspend fun proposeCandidate(rawText: String, context: AgentLlmContext): LlmIntentCandidate?
}

/**
 * Contexto mínimo y sin PII que el LLM puede usar para mejorar su propuesta.
 */
data class AgentLlmContext(
    val normalizedText: String,
    val hasPendingExternalAction: Boolean = false,
    val hasActiveChainedPlan: Boolean = false,
    val screenIsSensitive: Boolean = false,
    val knownContactCount: Int = 0,
    val knownPlaceAliasCount: Int = 0
)

/**
 * Implementación que nunca contacta al LLM. Es el default cuando no hay
 * configuración o cuando feature flag está apagado.
 */
object DisabledAgentLlmInterpreter : AgentLlmInterpreter {
    override suspend fun proposeCandidate(rawText: String, context: AgentLlmContext): LlmIntentCandidate? = null
}
