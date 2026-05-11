package com.ojoclaro.android.agent.core

/**
 * Un paso de un AgentPlan.
 *
 * slotValues / missingSlots usan los nombres String de AgentSlotName (que es
 * un object con constantes, no un enum).
 *
 * El planner NO crea pasos con risk = BLOCKED. Si detecta blocked, devuelve una
 * AgentDecision.Rejected en lugar de un plan.
 */
data class AgentPlanStep(
    val id: String,
    val toolId: AgentToolId,
    val description: String,
    val slotValues: Map<String, String> = emptyMap(),
    val missingSlots: Set<String> = emptySet(),
    val risk: AgentRiskLevel,
    val requiresConfirmation: Boolean,
    val spokenPrompt: String,
    val confirmationPrompt: String? = null
) {
    init {
        require(id.isNotBlank()) { "step id must not be blank" }
        require(description.isNotBlank()) { "step description must not be blank" }
        require(spokenPrompt.isNotBlank()) { "step spokenPrompt must not be blank" }
        require(risk != AgentRiskLevel.BLOCKED) {
            "blocked steps must not be materialized into a plan — return Rejected instead"
        }
        if (requiresConfirmation) {
            require(!confirmationPrompt.isNullOrBlank()) {
                "step requiresConfirmation but confirmationPrompt is blank"
            }
        }
    }

    val isReady: Boolean
        get() = missingSlots.isEmpty()

    fun slotValue(name: String): String? = slotValues[name]
}
