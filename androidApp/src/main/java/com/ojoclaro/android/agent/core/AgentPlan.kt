package com.ojoclaro.android.agent.core

/**
 * Plan multi-paso. Inmutable: cualquier avance crea un AgentPlan nuevo.
 *
 * Reglas de seguridad:
 *  - Un plan nunca tiene pasos BLOCKED — esos son rechazos del planner.
 *  - Un plan con cualquier paso sensible debe tener requiresStepByStepConfirmation = true.
 *  - Un plan NO ejecuta automáticamente: la UI/ChainedActionSession decide cuándo
 *    avanzar al siguiente paso, siempre con confirmación.
 */
data class AgentPlan(
    val id: String,
    val goal: AgentGoal,
    val steps: List<AgentPlanStep>,
    val status: AgentPlanStatus,
    val currentStepIndex: Int = 0,
    val requiresStepByStepConfirmation: Boolean = true,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    init {
        require(id.isNotBlank()) { "plan id must not be blank" }
        require(steps.isNotEmpty()) { "plan must have at least one step" }
        require(currentStepIndex in steps.indices) { "currentStepIndex out of range" }
        require(updatedAtMillis >= createdAtMillis) { "updatedAt must be >= createdAt" }
        val sensitiveSteps = steps.count { it.risk >= AgentRiskLevel.MEDIUM }
        if (sensitiveSteps > 0) {
            require(requiresStepByStepConfirmation) {
                "plan with sensitive steps must require step-by-step confirmation"
            }
        }
    }

    val currentStep: AgentPlanStep
        get() = steps[currentStepIndex]

    val isMultiStep: Boolean
        get() = steps.size > 1

    val isFinalStep: Boolean
        get() = currentStepIndex == steps.lastIndex

    fun advance(nowMillis: Long): AgentPlan {
        check(!isFinalStep) { "cannot advance past final step" }
        return copy(
            currentStepIndex = currentStepIndex + 1,
            status = if (currentStepIndex + 1 == steps.lastIndex) {
                AgentPlanStatus.AWAITING_NEXT_STEP_CONFIRMATION
            } else {
                AgentPlanStatus.AWAITING_NEXT_STEP_CONFIRMATION
            },
            updatedAtMillis = nowMillis
        )
    }

    fun withStatus(newStatus: AgentPlanStatus, nowMillis: Long): AgentPlan =
        copy(status = newStatus, updatedAtMillis = nowMillis)
}
