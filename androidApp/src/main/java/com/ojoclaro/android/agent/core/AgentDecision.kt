package com.ojoclaro.android.agent.core

/**
 * Resultado de hacer planning sobre un AgentGoal.
 *
 * Sealed para forzar que el caller (HomeViewModel/UI capa) maneje cada caso
 * de forma explícita. No hay default silencioso.
 */
sealed class AgentDecision {

    /** El planner construyó un plan ejecutable. UI debe presentar y confirmar paso por paso. */
    data class ExecutePlan(val plan: AgentPlan) : AgentDecision()

    /** Falta un slot esencial. UI debe preguntar. (slot = nombre string de AgentSlotName) */
    data class AskForSlot(
        val toolId: AgentToolId,
        val slot: String,
        val spokenPrompt: String
    ) : AgentDecision()

    /** Plan listo pero requiere confirmación inicial. UI debe pedir "confirmar". */
    data class AskForConfirmation(
        val plan: AgentPlan,
        val spokenPrompt: String
    ) : AgentDecision()

    /**
     * Rechazo seguro: contenido sensible, banca, contraseña, fuera de whitelist.
     * No es lo mismo que UNKNOWN — el planner sabe que NO debe ejecutar esto.
     */
    data class Rejected(
        val spokenText: String,
        val reason: String
    ) : AgentDecision()

    /** No se entendió ni con local ni con LLM fallback. Pide otra vez. */
    data class Unknown(val spokenText: String) : AgentDecision()
}
