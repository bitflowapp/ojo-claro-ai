package com.ojoclaro.android.agent.core.llm

import com.ojoclaro.android.agent.AgentIntent

/**
 * Candidato a intent propuesto por un LLM fallback.
 *
 * Es CANDIDATO, no decisión: pasa por LlmFallbackPolicy antes de mirarse como
 * algo ejecutable. Si el LLM devolvió herramientas/intents fuera del whitelist,
 * la policy lo descarta.
 */
data class LlmIntentCandidate(
    val intent: AgentIntent,
    val toolNameSuggested: String? = null,
    val confidence: Float,
    val slotsProposed: Map<String, String> = emptyMap(),
    val rawModelText: String = ""
) {
    init {
        require(confidence in 0f..1f) { "confidence must be in [0,1]" }
    }
}
