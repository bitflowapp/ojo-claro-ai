package com.ojoclaro.android.agent.core.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlot
import com.ojoclaro.android.agent.ParsedAgentIntent

/**
 * Resultado de pedir un fallback al LLM.
 *
 * Sealed para forzar manejo explícito. NUNCA contiene ejecución — solo señala
 * qué intent local equivalente proponemos (o ninguno).
 */
sealed class LlmFallbackResult {

    /** El LLM propuso un intent dentro del whitelist y se materializa como ParsedAgentIntent. */
    data class Accepted(
        val parsed: ParsedAgentIntent,
        val sourceCandidate: LlmIntentCandidate
    ) : LlmFallbackResult()

    /** El LLM no devolvió nada útil o estaba apagado. */
    data class NotAvailable(val reason: String) : LlmFallbackResult()

    /**
     * El LLM devolvió algo, pero estaba fuera del whitelist o era peligroso.
     * No se ejecuta nada — el agente solo pide aclaración.
     */
    data class RejectedBySafety(
        val candidate: LlmIntentCandidate,
        val reason: String,
        val spokenAck: String
    ) : LlmFallbackResult()

    companion object {
        fun acceptedFrom(
            candidate: LlmIntentCandidate,
            rawText: String,
            slots: List<AgentSlot> = emptyList(),
            missingSlots: List<String> = emptyList(),
            requiresConfirmation: Boolean = true
        ): Accepted {
            val parsed = ParsedAgentIntent(
                intent = candidate.intent,
                slots = slots,
                rawText = rawText,
                confidence = candidate.confidence,
                missingSlots = missingSlots,
                requiresConfirmation = requiresConfirmation
            )
            return Accepted(parsed = parsed, sourceCandidate = candidate)
        }

        fun rejected(candidate: LlmIntentCandidate, reason: String): RejectedBySafety =
            RejectedBySafety(
                candidate = candidate,
                reason = reason,
                spokenAck = "No estoy segura de lo que me pediste. ¿Lo podés decir más corto?"
            )

        fun notAvailable(reason: String): NotAvailable = NotAvailable(reason = reason)

        fun unknownIntent(): NotAvailable = NotAvailable("llm_returned_unknown")
    }
}

/**
 * Lista cerrada de intents que un LLM puede proponer.
 *
 * Si el modelo propone otra cosa (p.ej. PAY_BILL, SEND_NUDE, OPEN_VAULT), la
 * policy lo rechaza. Ojo Claro nunca ejecuta intents inventados.
 */
object LlmIntentWhitelist {
    val ALLOWED: Set<AgentIntent> = setOf(
        AgentIntent.HELP,
        AgentIntent.STOP_SPEAKING,
        AgentIntent.CANCEL,
        AgentIntent.CONFIRM,
        AgentIntent.OPEN_WHATSAPP,
        AgentIntent.OPEN_WHATSAPP_CHAT,
        AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
        AgentIntent.READ_VISIBLE_SCREEN,
        AgentIntent.READ_OCR_TEXT,
        AgentIntent.CALL_CONTACT,
        AgentIntent.OPEN_PHONE,
        AgentIntent.OPEN_MAPS,
        AgentIntent.GET_CURRENT_LOCATION,
        AgentIntent.NAVIGATE_TO_DESTINATION,
        AgentIntent.REPEAT_LAST,
        AgentIntent.REMEMBER_MEMORY,
        AgentIntent.LIST_MEMORY,
        AgentIntent.CLEAR_MEMORY,
        AgentIntent.SAVE_CONTACT,
        AgentIntent.SAVE_CONTACT_PHONE,
        AgentIntent.LIST_CONTACTS,
        AgentIntent.DELETE_CONTACT,
        AgentIntent.SAVE_LOCATION_ALIAS,
        AgentIntent.LIST_LOCATION_ALIASES,
        AgentIntent.DELETE_LOCATION_ALIAS,
        AgentIntent.UNKNOWN
    )
}
