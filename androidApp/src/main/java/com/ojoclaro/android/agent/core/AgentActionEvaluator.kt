package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.agent.SafetyDecision
import com.ojoclaro.android.agent.SafetyPolicy
import com.ojoclaro.android.agent.core.tool.AgentToolRegistry
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.risk.RiskDetector

/**
 * Evaluador determinista del Agent Core.
 *
 * Recibe una [ParsedAgentIntent] (lo que entendió el parser) más el contexto
 * actual (modo, pantalla, memoria) y devuelve una [AgentActionDecision]
 * cerrada: Allowed, NeedsConfirmation, NeedsSlot o Rejected.
 *
 * Reglas no negociables:
 *  1. Si SafetyPolicy rechaza, se rechaza.
 *  2. Si la pantalla actual está marcada como hot-zone (banca o campo
 *     contraseña), solo se permiten intents de [SAFE_INTENTS_DURING_HOT_ZONE].
 *  3. Si el slot MESSAGE_TEXT tiene contenido sensible (tarjeta, contraseña,
 *     código verificación) → rechazo, no se intenta "limpiarlo".
 *  4. Si el texto del comando dispara alertas de riesgo (dinero/banco) y el
 *     intent es OPEN_APP, OPEN_WHATSAPP, COMPOSE_WHATSAPP_MESSAGE o CALL_CONTACT,
 *     se eleva a confirmación obligatoria aunque el tool no la pida.
 *  5. Si falta un slot crítico, se devuelve NeedsSlot.
 *  6. Si todo está OK, se devuelve Allowed o NeedsConfirmation según el tool.
 *
 * Se diseña como add-only: no toca `AssistantOrchestrator`, no rompe lo
 * existente. La UI puede consumirlo cuando el feature flag esté on.
 */
class AgentActionEvaluator(
    private val registry: AgentToolRegistry = AgentToolRegistry(),
    private val riskDetector: RiskDetector = RiskDetector(),
    private val safetyPolicy: (ParsedAgentIntent) -> SafetyDecision = SafetyPolicy::gate
) {

    fun evaluate(
        parsed: ParsedAgentIntent,
        context: AgentContextSnapshot,
        nowMillis: Long
    ): AgentActionDecision {
        // 1. Gate por SafetyPolicy.
        val safety = safetyPolicy(parsed)
        if (safety is SafetyDecision.Reject) {
            return AgentActionDecision.Rejected(
                spokenText = safety.spokenExplanation,
                reason = "safety_${safety.reason}"
            )
        }

        // 2. UNKNOWN: el evaluador no inventa nada. La capa de conversación
        //    se encarga de decir "no entendí".
        if (parsed.intent == AgentIntent.UNKNOWN) {
            return AgentActionDecision.Rejected(
                spokenText = "No entendí. ¿Podés repetirlo más corto?",
                reason = "unknown_intent"
            )
        }

        // 3. Hot-zone: pantalla bancaria o con password.
        val screen = context.screen
        if (screen?.shouldBlockGeneralActions == true &&
            parsed.intent !in SAFE_INTENTS_DURING_HOT_ZONE
        ) {
            return AgentActionDecision.Rejected(
                spokenText = "Esta pantalla parece sensible. Salí de ahí antes de pedirme otra acción.",
                reason = "screen_hot_zone"
            )
        }

        // 4. Mensaje con contenido sensible.
        val messageText = parsed.slotValue(AgentSlotName.MESSAGE_TEXT)
        if (!messageText.isNullOrBlank() && !PrivacyGuard.isSafeMessagePayload(messageText)) {
            return AgentActionDecision.Rejected(
                spokenText = "No puedo preparar ese mensaje porque parece tener datos sensibles.",
                reason = "message_payload_unsafe"
            )
        }

        // 5. Tool selection en función del modo.
        val tool = registry.firstFor(parsed.intent, context.mode)
            ?: return AgentActionDecision.Rejected(
                spokenText = "Esa acción no está disponible en este modo.",
                reason = "no_tool_for_intent_${parsed.intent.name.lowercase()}"
            )

        // 6. Slots faltantes.
        val missing = computeMissingSlots(parsed, tool)
        if (missing.isNotEmpty()) {
            val firstMissing = missing.first()
            return AgentActionDecision.NeedsSlot(
                toolId = tool.id,
                slot = firstMissing,
                spokenPrompt = promptForSlot(firstMissing)
            )
        }

        // 7. Riesgo escalado por contenido del comando.
        val escalated = shouldEscalateForRisk(parsed, context)
        val baseRequiresConfirmation = tool.requiresConfirmation ||
            escalated ||
            parsed.requiresConfirmation

        val preview = buildSpokenPreview(tool, parsed)
        val initialConfirmationPrompt = if (baseRequiresConfirmation) {
            buildConfirmationPrompt(tool, parsed)
        } else {
            null
        }

        // El slot RAW_COMMAND viaja en la AgentAction. Lo necesita
        // DangerousActionGuard para revisar el comando original. La UI ya
        // ignora RAW_COMMAND a la hora de mostrar slots al usuario.
        val rawCommandValue = parsed.slotValue(AgentSlotName.RAW_COMMAND)?.takeIf { it.isNotBlank() }
        val baseSlots: Map<String, String> = parsed.slots
            .filter { it.name != AgentSlotName.RAW_COMMAND }
            .associate { it.name to it.value }
            .filterValues { it.isNotBlank() }
        val slotsForGuard: Map<String, String> = if (rawCommandValue != null) {
            baseSlots + (AgentSlotName.RAW_COMMAND to rawCommandValue)
        } else {
            baseSlots
        }

        val initialAction = AgentAction(
            id = "action-$nowMillis-${parsed.intent.name.lowercase()}",
            toolId = tool.id,
            intent = parsed.intent,
            slots = slotsForGuard,
            risk = effectiveRisk(tool, parsed, escalated),
            requiresConfirmation = baseRequiresConfirmation,
            spokenPreview = preview,
            confirmationPrompt = initialConfirmationPrompt
                ?: if (baseRequiresConfirmation) buildConfirmationPrompt(tool, parsed) else null,
            missingSlots = emptySet()
        )

        val guarded = when (val verdict = DangerousActionGuard.review(initialAction)) {
            DangerousActionGuard.Verdict.Safe -> initialAction
            is DangerousActionGuard.Verdict.Elevated -> DangerousActionGuard.apply(initialAction, verdict)
        }

        // Drop RAW_COMMAND from the exposed slots so el resto del sistema sigue
        // viendo solo slots semánticos. El raw command ya fue inspeccionado.
        val publishedAction = guarded.copy(slots = guarded.slots - AgentSlotName.RAW_COMMAND)

        return if (publishedAction.requiresConfirmation) {
            AgentActionDecision.NeedsConfirmation(
                action = publishedAction,
                spokenConfirmationPrompt = publishedAction.confirmationPrompt
                    ?: buildConfirmationPrompt(tool, parsed)
            )
        } else {
            AgentActionDecision.Allowed(publishedAction)
        }
    }

    private fun computeMissingSlots(parsed: ParsedAgentIntent, tool: AgentTool): List<String> {
        val declared = parsed.missingSlots.toList()
        if (declared.isNotEmpty()) {
            return declared.filter { it in tool.requiredSlots || it in tool.optionalSlots || declared.size == 1 }
                .ifEmpty { declared }
        }
        val present = parsed.slots.map { it.name }.toSet()
        return tool.requiredSlots.filter { it !in present }
    }

    private fun shouldEscalateForRisk(
        parsed: ParsedAgentIntent,
        context: AgentContextSnapshot
    ): Boolean {
        if (parsed.intent !in RISK_ESCALATED_INTENTS) return false

        // Preferimos warnings precomputados (vía AgentContext.build). Si no
        // vinieron, caemos al fallback regex sobre el comando crudo — mismo
        // comportamiento que antes.
        if (context.commandRiskWarnings.isNotEmpty()) return true
        if (context.screenRiskWarnings.isNotEmpty()) return true

        val raw = parsed.rawText
        if (raw.isBlank()) return false
        return riskDetector.detectFromCommand(raw).isNotEmpty()
    }

    private fun effectiveRisk(
        tool: AgentTool,
        parsed: ParsedAgentIntent,
        escalated: Boolean
    ): AgentRiskLevel {
        val base = if (parsed.intent == AgentIntent.COMPOSE_WHATSAPP_MESSAGE) {
            AgentRiskLevel.MEDIUM
        } else {
            tool.risk
        }
        return if (escalated && base.ordinal < AgentRiskLevel.HIGH.ordinal) {
            AgentRiskLevel.HIGH
        } else {
            base
        }
    }

    private fun buildSpokenPreview(tool: AgentTool, parsed: ParsedAgentIntent): String {
        val contact = parsed.slotValue(AgentSlotName.CONTACT_NAME)
        val destination = parsed.slotValue(AgentSlotName.DESTINATION)
            ?: parsed.slotValue(AgentSlotName.LOCATION_ALIAS)
        val message = parsed.slotValue(AgentSlotName.MESSAGE_TEXT)
        return when (tool.id) {
            AgentToolId.WHATSAPP -> when {
                !contact.isNullOrBlank() && !message.isNullOrBlank() ->
                    "Voy a preparar un WhatsApp para $contact con el mensaje: '$message'. No lo envío automáticamente."
                !contact.isNullOrBlank() ->
                    "Voy a abrir el chat de $contact en WhatsApp."
                else -> "Voy a abrir WhatsApp."
            }
            AgentToolId.MAPS -> if (!destination.isNullOrBlank()) {
                "Voy a abrir Maps con dirección a $destination."
            } else {
                "Voy a abrir Maps."
            }
            AgentToolId.PHONE -> if (!contact.isNullOrBlank()) {
                "Voy a abrir el marcador para $contact. La llamada la disparás vos."
            } else {
                "Voy a abrir el marcador. La llamada la disparás vos."
            }
            AgentToolId.SCREEN_READER -> "Voy a leer el texto visible de esta pantalla."
            AgentToolId.OCR -> "Voy a leer texto con la cámara."
            AgentToolId.REPEAT_LAST -> "Repito lo último que dije."
            AgentToolId.EMERGENCY -> "Voy a preparar el modo emergencia. No llamo solo."
            AgentToolId.MEMORY -> "Voy a actualizar tu memoria personal."
            AgentToolId.PREFERENCE -> "Voy a ajustar tu preferencia."
            AgentToolId.GENERIC_APP -> "Esa operación no está habilitada."
        }
    }

    private fun buildConfirmationPrompt(tool: AgentTool, parsed: ParsedAgentIntent): String =
        when (tool.id) {
            AgentToolId.WHATSAPP -> "Para preparar el WhatsApp, decí: confirmar."
            AgentToolId.MAPS -> "Para abrir Maps, decí: confirmar."
            AgentToolId.PHONE -> {
                val contact = parsed.slotValue(AgentSlotName.CONTACT_NAME)
                if (!contact.isNullOrBlank()) {
                    "Para abrir el marcador con $contact, decí: confirmar."
                } else {
                    "Para abrir el marcador, decí: confirmar."
                }
            }
            AgentToolId.SCREEN_READER -> "Para leer esta pantalla, decí: confirmar."
            AgentToolId.EMERGENCY -> "Para activar modo emergencia, decí: confirmar."
            AgentToolId.MEMORY -> "Para actualizar la memoria, decí: confirmar."
            else -> "Para continuar, decí: confirmar."
        }

    private fun promptForSlot(slot: String): String = when (slot) {
        AgentSlotName.CONTACT_NAME -> "¿A qué contacto?"
        AgentSlotName.MESSAGE_TEXT -> "¿Qué mensaje querés mandar?"
        AgentSlotName.PHONE_NUMBER -> "¿Cuál es el número?"
        AgentSlotName.DESTINATION -> "¿A qué destino?"
        AgentSlotName.LOCATION_ALIAS -> "¿Con qué nombre lo guardo?"
        AgentSlotName.WHATSAPP_ACTION -> "¿Qué querés hacer en WhatsApp?"
        AgentSlotName.CONTACT_TYPE -> "¿Es contacto de confianza o de emergencia?"
        AgentSlotName.TIME -> "¿A qué hora?"
        AgentSlotName.RECURRENCE -> "¿Con qué frecuencia?"
        else -> "¿Podés darme un poco más de detalle?"
    }

    companion object {
        /**
         * Intents que se permiten incluso cuando la pantalla activa es hot-zone.
         * El usuario debe poder pedir ayuda, repetir o cancelar siempre.
         */
        private val SAFE_INTENTS_DURING_HOT_ZONE: Set<AgentIntent> = setOf(
            AgentIntent.HELP,
            AgentIntent.REPEAT_LAST,
            AgentIntent.STOP_SPEAKING,
            AgentIntent.CANCEL,
            AgentIntent.CONFIRM
        )

        /**
         * Intents donde si el TEXTO del comando trae palabras de riesgo
         * (dinero, banco, contraseña), se eleva a confirmación obligatoria.
         * Ej: "abrime mi banco" → exige confirmación.
         */
        private val RISK_ESCALATED_INTENTS: Set<AgentIntent> = setOf(
            AgentIntent.OPEN_APP,
            AgentIntent.OPEN_WHATSAPP,
            AgentIntent.OPEN_WHATSAPP_CHAT,
            AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            AgentIntent.CALL_CONTACT,
            AgentIntent.NAVIGATE_TO_DESTINATION,
            AgentIntent.OPEN_PHONE
        )
    }
}
