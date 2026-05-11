package com.ojoclaro.android.agent.core.planner

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.agent.SafetyDecision
import com.ojoclaro.android.agent.SafetyPolicy
import com.ojoclaro.android.agent.core.AgentContextSnapshot
import com.ojoclaro.android.agent.core.AgentDecision
import com.ojoclaro.android.agent.core.AgentGoal
import com.ojoclaro.android.agent.core.AgentPlan
import com.ojoclaro.android.agent.core.AgentPlanStatus
import com.ojoclaro.android.agent.core.AgentPlanStep
import com.ojoclaro.android.agent.core.AgentRiskLevel
import com.ojoclaro.android.agent.core.AgentTool
import com.ojoclaro.android.agent.core.AgentToolId
import com.ojoclaro.android.agent.core.tool.AgentToolRegistry
import com.ojoclaro.android.privacy.PrivacyGuard

/**
 * Planner supervisado de Agent Core v1.
 *
 * Reglas no negociables:
 *  - Si SafetyPolicy rechaza el intent local, el plan se rechaza.
 *  - Si la pantalla actual está marcada como bancaria o con campo de
 *    contraseña, se rechaza cualquier acción excepto repetir/ayuda/emergencia.
 *  - Si el mensaje contiene credenciales/tarjetas/códigos, se rechaza.
 *  - Si falta un slot esencial, se devuelve AskForSlot.
 *  - Si el plan tiene cualquier paso sensible, se exige confirmación paso a paso.
 *  - Para multi-step, se construye una cadena máxima de 2 pasos con
 *    confirmación entre ellos. Más que eso lo rechaza (Rejected).
 *  - El planner NUNCA inventa contenido — sólo encadena lo que el goal ya pidió.
 */
class AgentPlanner(
    private val registry: AgentToolRegistry = AgentToolRegistry()
) {

    fun plan(
        goal: AgentGoal,
        parsedIntent: ParsedAgentIntent,
        context: AgentContextSnapshot,
        nowMillis: Long,
        idFactory: (suffix: String) -> String = { "plan-$nowMillis-$it" }
    ): AgentDecision {
        // 1. Gate por SafetyPolicy (whitelist + sanity de slots).
        val safetyDecision = SafetyPolicy.gate(parsedIntent)
        if (safetyDecision is SafetyDecision.Reject) {
            return AgentDecision.Rejected(
                spokenText = safetyDecision.spokenExplanation,
                reason = "safety_policy_${safetyDecision.reason}"
            )
        }

        // 2. Bloqueo total sobre pantallas críticas.
        if (context.screen?.shouldBlockGeneralActions == true &&
            parsedIntent.intent !in SAFE_INTENTS_DURING_HOT_ZONE
        ) {
            return AgentDecision.Rejected(
                spokenText = "Esta pantalla parece bancaria o de contraseña. " +
                    "No puedo hacer nada acá. Salí de la pantalla primero.",
                reason = "screen_hot_zone"
            )
        }

        // 3. Mensajes con contenido sensible.
        val messageSlot = parsedIntent.slotValue(AgentSlotName.MESSAGE_TEXT)
        if (!messageSlot.isNullOrBlank() && !PrivacyGuard.isSafeMessagePayload(messageSlot)) {
            return AgentDecision.Rejected(
                spokenText = "No puedo preparar ese mensaje porque parece tener datos sensibles.",
                reason = "message_payload_unsafe"
            )
        }

        if (parsedIntent.intent == AgentIntent.UNKNOWN) {
            return AgentDecision.Unknown(
                spokenText = "No entendí. ¿Podés repetirlo más corto?"
            )
        }

        // 4. Tool selection. Si no hay tool habilitado en este modo, rechazamos.
        val primaryTool = registry.firstFor(parsedIntent.intent, context.mode)
            ?: return AgentDecision.Rejected(
                spokenText = "Esa acción no está disponible ahora.",
                reason = "no_tool_for_intent"
            )

        // 5. Si faltan slots críticos, preguntamos.
        val missingSet: Set<String> = parsedIntent.missingSlots.toSet()
        val missing: Collection<String> = missingSet.intersect(primaryTool.requiredSlots)
            .ifEmpty { missingSet }
        if (missing.isNotEmpty()) {
            val firstMissing = missing.first()
            return AgentDecision.AskForSlot(
                toolId = primaryTool.id,
                slot = firstMissing,
                spokenPrompt = promptForSlot(firstMissing)
            )
        }

        // 6. Construcción del plan. Multi-step solo en patrones reconocidos.
        val steps = buildSteps(
            parsedIntent = parsedIntent,
            primaryTool = primaryTool,
            goal = goal,
            context = context,
            idFactory = idFactory
        ) ?: return AgentDecision.Rejected(
            spokenText = "Esa cadena de acciones es demasiado larga. Hacelas una por una.",
            reason = "chain_too_long"
        )

        val plan = AgentPlan(
            id = idFactory("root"),
            goal = goal,
            steps = steps,
            status = AgentPlanStatus.PENDING,
            requiresStepByStepConfirmation = true,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )

        // 7. Si el plan necesita confirmación, devolvemos AskForConfirmation.
        return if (steps.any { it.requiresConfirmation }) {
            AgentDecision.AskForConfirmation(
                plan = plan,
                spokenPrompt = plan.currentStep.confirmationPrompt
                    ?: plan.currentStep.spokenPrompt
            )
        } else {
            AgentDecision.ExecutePlan(plan)
        }
    }

    private fun buildSteps(
        parsedIntent: ParsedAgentIntent,
        primaryTool: AgentTool,
        goal: AgentGoal,
        context: AgentContextSnapshot,
        idFactory: (String) -> String
    ): List<AgentPlanStep>? {
        val primaryStep = primaryStepFor(parsedIntent, primaryTool, idFactory)

        // Secundarios solo si el goal explícitamente los listó (provenientes del
        // parser o de un fallback que el caller validó), y siempre máximo 1.
        val secondary = goal.secondaryIntents
            .firstOrNull { it != parsedIntent.intent && it != AgentIntent.UNKNOWN }
        if (secondary != null && goal.secondaryIntents.size > 1) {
            return null
        }
        val secondaryTool = secondary?.let { registry.firstFor(it, context.mode) }

        return if (secondaryTool != null) {
            val secondaryStep = secondaryStepFor(secondary, secondaryTool, idFactory)
            listOf(primaryStep, secondaryStep)
        } else {
            listOf(primaryStep)
        }
    }

    private fun primaryStepFor(
        parsedIntent: ParsedAgentIntent,
        tool: AgentTool,
        idFactory: (String) -> String
    ): AgentPlanStep {
        val slotValues: Map<String, String> = parsedIntent.slots
            .associate { it.name to it.value }
            .filterValues { it.isNotBlank() }
        val description = describe(tool, parsedIntent)
        val spokenPrompt = describe(tool, parsedIntent)
        val confirmationPrompt = if (tool.requiresConfirmation) {
            confirmationPromptFor(tool)
        } else {
            null
        }
        val risk = when {
            parsedIntent.intent == AgentIntent.COMPOSE_WHATSAPP_MESSAGE -> AgentRiskLevel.MEDIUM
            else -> tool.risk
        }
        return AgentPlanStep(
            id = idFactory("primary"),
            toolId = tool.id,
            description = description,
            slotValues = slotValues,
            missingSlots = parsedIntent.missingSlots.toSet(),
            risk = risk,
            requiresConfirmation = tool.requiresConfirmation,
            spokenPrompt = spokenPrompt,
            confirmationPrompt = confirmationPrompt
        )
    }

    private fun secondaryStepFor(
        intent: AgentIntent,
        tool: AgentTool,
        idFactory: (String) -> String
    ): AgentPlanStep {
        val description = "Después: ${tool.displayName}"
        return AgentPlanStep(
            id = idFactory("secondary-${intent.name.lowercase()}"),
            toolId = tool.id,
            description = description,
            slotValues = emptyMap(),
            missingSlots = emptySet(),
            risk = tool.risk,
            requiresConfirmation = true,
            spokenPrompt = "Después puedo " + describeShort(tool),
            confirmationPrompt = "Confirmá para " + describeShort(tool)
        )
    }

    private fun describe(tool: AgentTool, parsedIntent: ParsedAgentIntent): String =
        when (tool.id) {
            AgentToolId.WHATSAPP -> when (parsedIntent.intent) {
                AgentIntent.COMPOSE_WHATSAPP_MESSAGE -> {
                    val contact = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
                    val message = parsedIntent.slotValue(AgentSlotName.MESSAGE_TEXT).orEmpty()
                    "Voy a preparar un WhatsApp para $contact con el mensaje: '$message'. No lo envío automáticamente."
                }
                AgentIntent.OPEN_WHATSAPP_CHAT -> {
                    val contact = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
                    "Voy a abrir el chat de $contact en WhatsApp. No envío nada."
                }
                else -> "Voy a abrir WhatsApp. No envío nada."
            }
            AgentToolId.MAPS -> {
                val destination = parsedIntent.slotValue(AgentSlotName.DESTINATION)
                if (!destination.isNullOrBlank()) {
                    "Voy a abrir Maps con dirección a $destination."
                } else {
                    "Voy a abrir Maps."
                }
            }
            AgentToolId.PHONE -> {
                val contact = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME)
                if (!contact.isNullOrBlank()) {
                    "Voy a abrir el marcador con $contact. La llamada la disparás vos."
                } else {
                    "Voy a abrir el marcador. La llamada la disparás vos."
                }
            }
            AgentToolId.SCREEN_READER -> "Voy a leer el texto visible de esta pantalla."
            AgentToolId.OCR -> "Voy a leer texto con la cámara."
            AgentToolId.REPEAT_LAST -> "Repito lo último que dije."
            AgentToolId.EMERGENCY -> "Activo modo emergencia."
            AgentToolId.MEMORY -> "Voy a actualizar tu memoria personal."
            AgentToolId.PREFERENCE -> "Voy a ajustar tu preferencia."
            AgentToolId.GENERIC_APP -> "No habilitado." // jamás llega acá
        }

    private fun describeShort(tool: AgentTool): String = when (tool.id) {
        AgentToolId.MAPS -> "abrir Maps."
        AgentToolId.PHONE -> "abrir el marcador."
        AgentToolId.WHATSAPP -> "abrir WhatsApp."
        AgentToolId.SCREEN_READER -> "leer la pantalla."
        AgentToolId.OCR -> "leer texto con la cámara."
        AgentToolId.MEMORY -> "actualizar la memoria."
        AgentToolId.PREFERENCE -> "guardar preferencia."
        AgentToolId.REPEAT_LAST -> "repetir."
        AgentToolId.EMERGENCY -> "modo emergencia."
        AgentToolId.GENERIC_APP -> "operación reservada."
    }

    private fun confirmationPromptFor(tool: AgentTool): String =
        when (tool.id) {
            AgentToolId.WHATSAPP ->
                "Para preparar el WhatsApp, decí: confirmar."
            AgentToolId.MAPS ->
                "Para abrir Maps, decí: confirmar."
            AgentToolId.PHONE ->
                "Para abrir el marcador, decí: confirmar."
            AgentToolId.SCREEN_READER ->
                "Para leer esta pantalla, decí: confirmar."
            AgentToolId.MEMORY ->
                "Para actualizar la memoria, decí: confirmar."
            AgentToolId.EMERGENCY ->
                "Para activar modo emergencia, decí: confirmar."
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
        private val SAFE_INTENTS_DURING_HOT_ZONE: Set<AgentIntent> = setOf(
            AgentIntent.HELP,
            AgentIntent.REPEAT_LAST,
            AgentIntent.STOP_SPEAKING,
            AgentIntent.CANCEL,
            AgentIntent.CONFIRM
        )
    }
}
