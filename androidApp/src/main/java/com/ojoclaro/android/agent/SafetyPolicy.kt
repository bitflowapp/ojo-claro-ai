package com.ojoclaro.android.agent

import com.ojoclaro.android.memory.SafeContactMemory
import com.ojoclaro.android.privacy.PrivacyGuard

/**
 * Capa determinista que valida cualquier [ParsedAgentIntent] antes de
 * dejar que el agente la ejecute, hable de ella o la guarde.
 *
 * Importancia:
 *  - Hoy el único productor de intenciones es [LocalIntentParser]. Las
 *    reglas alcanzan para nuestra app local.
 *  - Mañana, si se agrega un `AiIntentInterpreter` (o cualquier modelo
 *    cloud), TODA intención que él proponga debe pasar por esta política
 *    antes de tocar el orquestador o un Executor.
 *  - Esta capa NUNCA habla con la IA; es local, pura y sin dependencias
 *    de Android. Es trivial de testear.
 *
 * Razón de seguridad:
 *  Si alguna vez un modelo devuelve una intención inventada (`PAY_BILL`,
 *  `OPEN_VAULT`, `SEND_NUDE`, etc.) o slots tóxicos (mensaje con
 *  contraseña, número con caracteres raros), aquí lo bloqueamos. La app
 *  no se rompe, simplemente se reduce a [SafetyDecision.Reject] con un
 *  motivo legible.
 */
sealed class SafetyDecision {
    data class Accept(val intent: ParsedAgentIntent) : SafetyDecision()

    data class Reject(
        val intent: ParsedAgentIntent,
        val reason: String,
        val spokenExplanation: String
    ) : SafetyDecision()
}

object SafetyPolicy {

    /**
     * Whitelist explícita. Cualquier intent fuera de este conjunto se
     * rechaza, incluso si el modelo (presente o futuro) lo propone.
     */
    private val ALLOWED_INTENTS: Set<AgentIntent> = setOf(
        AgentIntent.HELP,
        AgentIntent.STOP_SPEAKING,
        AgentIntent.CANCEL,
        AgentIntent.CONFIRM,
        AgentIntent.OPEN_APP,
        AgentIntent.OPEN_WHATSAPP,
        AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
        AgentIntent.READ_VISIBLE_SCREEN,
        AgentIntent.READ_OCR_TEXT,
        AgentIntent.CALL_CONTACT,
        AgentIntent.OPEN_PHONE,
        AgentIntent.OPEN_MAPS,
        AgentIntent.GET_CURRENT_LOCATION,
        AgentIntent.NAVIGATE_TO_DESTINATION,
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

    /**
     * Intents que se ejecutan sin pedir confirmación por sí solos.
     * Si una capa propone alguno con [ParsedAgentIntent.requiresConfirmation]
     * = false, está bien. Cualquier otro intent debe entrar al manager
     * conversacional para que pida confirmación.
     */
    private val INTENTS_WITHOUT_CONFIRMATION: Set<AgentIntent> = setOf(
        AgentIntent.HELP,
        AgentIntent.STOP_SPEAKING,
        AgentIntent.CANCEL,
        AgentIntent.CONFIRM,
        AgentIntent.OPEN_PHONE,
        AgentIntent.OPEN_MAPS,
        AgentIntent.GET_CURRENT_LOCATION,
        AgentIntent.OPEN_WHATSAPP,
        AgentIntent.READ_OCR_TEXT,
        AgentIntent.LIST_MEMORY,
        AgentIntent.LIST_CONTACTS,
        AgentIntent.LIST_LOCATION_ALIASES,
        AgentIntent.UNKNOWN
    )

    fun gate(intent: ParsedAgentIntent): SafetyDecision {
        if (intent.intent !in ALLOWED_INTENTS) {
            return reject(intent, "intent_not_allowed",
                "No reconozco esa acción.")
        }

        if (intent.confidence < 0f || intent.confidence > 1f) {
            return reject(intent, "invalid_confidence",
                "No pude entender bien lo que dijiste.")
        }

        // Slots con contenido sensible que no fueron marcados como tales.
        // Esto cubre el caso "el modelo propuso un mensaje con contraseña
        // y olvidó marcar isSensitive": lo detectamos igual.
        for (slot in intent.slots) {
            if (slot.name == AgentSlotName.MESSAGE_TEXT &&
                slot.value.isNotBlank() &&
                !PrivacyGuard.isSafeMessagePayload(slot.value)
            ) {
                return reject(
                    intent = intent,
                    reason = "message_payload_unsafe",
                    spokenExplanation = "No puedo preparar ese mensaje porque parece contener datos sensibles."
                )
            }
        }

        // COMPOSE sin slots: solo se acepta si declara missingSlots y
        // explícitamente NO requiere confirmación todavía. Eso evita que
        // una capa futura proponga "manda WhatsApp" y se ejecute sin
        // contacto ni mensaje.
        if (intent.intent == AgentIntent.COMPOSE_WHATSAPP_MESSAGE &&
            intent.missingSlots.isEmpty() &&
            intent.slotValue(AgentSlotName.CONTACT_NAME).isNullOrBlank()
        ) {
            return reject(intent, "compose_missing_contact",
                "Necesito un contacto para preparar el mensaje.")
        }

        // CALL sin contacto y sin missingSlots declarado: rechazo.
        if (intent.intent == AgentIntent.CALL_CONTACT &&
            intent.missingSlots.isEmpty() &&
            intent.slotValue(AgentSlotName.CONTACT_NAME).isNullOrBlank()
        ) {
            return reject(intent, "call_missing_contact",
                "Necesito un contacto o número para preparar la llamada.")
        }

        if (intent.intent in CONTACT_MEMORY_INTENTS &&
            intent.missingSlots.isEmpty() &&
            intent.slotValue(AgentSlotName.CONTACT_NAME).isNullOrBlank()
        ) {
            return reject(intent, "contact_memory_missing_contact",
                "Necesito el nombre del contacto.")
        }

        if (intent.intent == AgentIntent.SAVE_CONTACT_PHONE &&
            intent.missingSlots.isEmpty()
        ) {
            val phoneNumber = intent.slotValue(AgentSlotName.PHONE_NUMBER).orEmpty()
            if (SafeContactMemory.normalizePhoneNumber(phoneNumber) == null) {
                return reject(intent, "contact_phone_invalid",
                    "Ese nÃºmero no parece vÃ¡lido.")
            }
        }

        // Coherencia: intents sensibles que no aparecen en la whitelist de
        // "no requieren confirmación" DEBEN tener confirmación pedida o
        // missingSlots. Si llegan con requiresConfirmation=false y sin
        // slots faltantes, es porque alguien quiere ejecutarlos solos —
        // bloqueamos.
        if (intent.intent !in INTENTS_WITHOUT_CONFIRMATION &&
            !intent.requiresConfirmation &&
            intent.missingSlots.isEmpty()
        ) {
            return reject(intent, "missing_required_confirmation",
                "Para hacer eso, necesito tu confirmación explícita.")
        }

        return SafetyDecision.Accept(intent)
    }

    private fun reject(
        intent: ParsedAgentIntent,
        reason: String,
        spokenExplanation: String
    ): SafetyDecision = SafetyDecision.Reject(
        intent = intent,
        reason = reason,
        spokenExplanation = spokenExplanation
    )

    private val CONTACT_MEMORY_INTENTS: Set<AgentIntent> = setOf(
        AgentIntent.SAVE_CONTACT,
        AgentIntent.SAVE_CONTACT_PHONE,
        AgentIntent.DELETE_CONTACT
    )
}
