package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlot
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.ParsedAgentIntent

/**
 * Una acción evaluada y lista para ser ofrecida al usuario.
 *
 * No es un plan multi-paso (eso es [AgentPlan]) y no es un parseo crudo
 * (eso es [ParsedAgentIntent]). Es la capa intermedia: el agent core ya
 * pasó la intención del usuario por SafetyPolicy / RiskDetector y produjo
 * un objeto cerrado que describe:
 *  - qué tool va a usar,
 *  - qué slots ya tiene resueltos,
 *  - qué riesgo asignado,
 *  - si necesita confirmación explícita antes de ejecutarse,
 *  - cómo se anuncia por voz.
 *
 * El consumidor (UI/ViewModel/executor) no decide el riesgo: lo respeta.
 *
 * Diseñada como tipo "valor": pura, inmutable, sin Android, sin coroutines.
 */
data class AgentAction(
    val id: String,
    val toolId: AgentToolId,
    val intent: AgentIntent,
    val slots: Map<String, String>,
    val risk: AgentRiskLevel,
    val requiresConfirmation: Boolean,
    val spokenPreview: String,
    val confirmationPrompt: String? = null,
    val missingSlots: Set<String> = emptySet()
) {
    init {
        require(id.isNotBlank()) { "AgentAction.id must not be blank" }
        require(spokenPreview.isNotBlank()) { "AgentAction.spokenPreview must not be blank" }
        require(risk != AgentRiskLevel.BLOCKED) {
            "AgentAction must not be BLOCKED — gate it at the evaluator and return a Reject instead."
        }
        if (requiresConfirmation) {
            require(!confirmationPrompt.isNullOrBlank()) {
                "AgentAction with requiresConfirmation=true must include a confirmationPrompt."
            }
        }
    }

    val isReady: Boolean
        get() = missingSlots.isEmpty()

    fun slotValue(name: String): String? = slots[name]

    /**
     * Convierte la action en un [AgentPlanStep] simple, para reusar el motor
     * de planning existente cuando se quiera disparar la acción dentro de un
     * [AgentPlan].
     */
    fun toPlanStep(): AgentPlanStep = AgentPlanStep(
        id = id,
        toolId = toolId,
        description = spokenPreview,
        slotValues = slots,
        missingSlots = missingSlots,
        risk = risk,
        requiresConfirmation = requiresConfirmation,
        spokenPrompt = spokenPreview,
        confirmationPrompt = confirmationPrompt
    )

    companion object {
        /**
         * Adapter para construir una [AgentAction] desde una [ParsedAgentIntent]
         * + el [AgentTool] elegido por el registry. NO hace evaluaciones de
         * seguridad — eso es responsabilidad del evaluador.
         */
        fun fromParsedIntent(
            parsed: ParsedAgentIntent,
            tool: AgentTool,
            actionId: String,
            spokenPreview: String,
            confirmationPrompt: String?,
            risk: AgentRiskLevel = tool.risk,
            requiresConfirmation: Boolean = tool.requiresConfirmation
        ): AgentAction {
            val resolvedSlots: Map<String, String> = parsed.slots
                .filter { it.name != AgentSlotName.RAW_COMMAND }
                .associate { slot: AgentSlot -> slot.name to slot.value }
                .filterValues { it.isNotBlank() }
            return AgentAction(
                id = actionId,
                toolId = tool.id,
                intent = parsed.intent,
                slots = resolvedSlots,
                risk = risk,
                requiresConfirmation = requiresConfirmation,
                spokenPreview = spokenPreview,
                confirmationPrompt = confirmationPrompt,
                missingSlots = parsed.missingSlots.toSet()
            )
        }
    }
}
