package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentIntent

/**
 * El "qué quiere el usuario" en alto nivel.
 *
 * Se construye desde texto + intent local detectado + (opcional) la salida del
 * LLM fallback. El planner trabaja sobre el goal, no sobre el texto crudo, para
 * que decisiones de seguridad sean explícitas (no se parsea de nuevo dentro del
 * planner).
 */
data class AgentGoal(
    val rawText: String,
    val normalizedText: String,
    val primaryIntent: AgentIntent,
    val secondaryIntents: List<AgentIntent> = emptyList(),
    val confidence: Float = 0f,
    val source: AgentGoalSource = AgentGoalSource.LOCAL_PARSER
) {
    init {
        require(rawText.isNotBlank()) { "rawText must not be blank" }
        require(confidence in 0f..1f) { "confidence must be in [0,1]" }
    }

    /** True si el texto parece pedir una cadena de pasos (WhatsApp y después Maps, etc.). */
    val isMultiStep: Boolean
        get() = secondaryIntents.isNotEmpty()
}

enum class AgentGoalSource {
    LOCAL_PARSER,
    LLM_FALLBACK,
    USER_DIRECT
}
