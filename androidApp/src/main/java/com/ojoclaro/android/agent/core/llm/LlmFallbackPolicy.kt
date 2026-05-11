package com.ojoclaro.android.agent.core.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlot
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.SafetyDecision
import com.ojoclaro.android.agent.SafetyPolicy
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.agent.core.tool.AgentToolRegistry
import com.ojoclaro.android.privacy.PrivacyGuard

/**
 * Política que decide si una propuesta de LLM se puede materializar como un
 * ParsedAgentIntent ejecutable.
 *
 * Reglas:
 *  - LLM debe estar habilitado y configurado.
 *  - El intent propuesto debe estar en LlmIntentWhitelist.
 *  - Si el LLM sugirió un toolName, debe coincidir con un tool del registry.
 *  - Confianza mínima.
 *  - El mensaje propuesto (si hay slot MESSAGE_TEXT) debe pasar PrivacyGuard.
 *  - El resultado pasa por SafetyPolicy.gate antes de aceptarse.
 *  - No se acepta nada del LLM mientras estemos en una pantalla "hot zone"
 *    (banca/contraseña) — el caller debería ni siquiera invocar al LLM en ese
 *    contexto, pero la policy también lo bloquea por defensa en profundidad.
 */
class LlmFallbackPolicy(
    private val registry: AgentToolRegistry = AgentToolRegistry(),
    private val minimumConfidence: Float = DEFAULT_MIN_CONFIDENCE
) {

    fun evaluate(
        candidate: LlmIntentCandidate,
        rawText: String,
        screenIsHotZone: Boolean,
        llmEnabled: Boolean
    ): LlmFallbackResult {
        if (!llmEnabled) {
            return LlmFallbackResult.notAvailable("llm_disabled")
        }
        if (screenIsHotZone) {
            return LlmFallbackResult.rejected(candidate, reason = "screen_hot_zone")
        }
        if (candidate.intent !in LlmIntentWhitelist.ALLOWED) {
            return LlmFallbackResult.rejected(candidate, reason = "intent_not_whitelisted")
        }
        if (candidate.confidence < minimumConfidence) {
            return LlmFallbackResult.notAvailable("llm_low_confidence")
        }
        val toolName = candidate.toolNameSuggested
        if (!toolName.isNullOrBlank() && !registry.isWhitelistedToolName(toolName)) {
            return LlmFallbackResult.rejected(candidate, reason = "tool_not_whitelisted")
        }

        val proposedMessage = candidate.slotsProposed[AgentSlotName.MESSAGE_TEXT]
        if (!proposedMessage.isNullOrBlank() && !PrivacyGuard.isSafeMessagePayload(proposedMessage)) {
            return LlmFallbackResult.rejected(candidate, reason = "message_payload_unsafe")
        }

        // Construir slots a partir de las propuestas (filtrando blanks).
        val slots: List<AgentSlot> = candidate.slotsProposed
            .filter { it.value.isNotBlank() }
            .map { (name, value) ->
                AgentSlot(name = name, value = value, confidence = candidate.confidence)
            }
        val requiresConfirmation = candidate.intent != AgentIntent.HELP &&
            candidate.intent != AgentIntent.REPEAT_LAST &&
            candidate.intent != AgentIntent.STOP_SPEAKING &&
            candidate.intent != AgentIntent.CANCEL

        val parsed = ParsedAgentIntent(
            intent = candidate.intent,
            slots = slots,
            rawText = rawText,
            confidence = candidate.confidence,
            missingSlots = emptyList(),
            requiresConfirmation = requiresConfirmation
        )

        val gated = SafetyPolicy.gate(parsed)
        return when (gated) {
            is SafetyDecision.Accept -> LlmFallbackResult.Accepted(
                parsed = gated.intent,
                sourceCandidate = candidate
            )
            is SafetyDecision.Reject -> LlmFallbackResult.rejected(
                candidate = candidate,
                reason = "safety_policy_${gated.reason}"
            )
        }
    }

    companion object {
        const val DEFAULT_MIN_CONFIDENCE = 0.55f
    }
}
