package com.ojoclaro.android.agent.task.execution

import com.ojoclaro.android.agent.task.action.AgentControlledActionPolicy
import com.ojoclaro.android.agent.task.action.AgentControlledActionProposal
import com.ojoclaro.android.agent.task.action.AgentControlledActionType
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityDecision
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityRegistry
import com.ojoclaro.android.agent.task.capability.AgentActionCapabilityType

/**
 * Paquete 6F -- Safe Execution Gate. Paquete 6G -- consulta de capacidades.
 *
 * Capa PURA: decide si una propuesta de accion puede ejecutarse de forma
 * segura, si solo puede prepararse, si esta bloqueada o si requiere
 * confirmacion. No tiene efectos: no abre apps, no escribe, no envia, no
 * llama a performClick / dispatchGesture / performGlobalAction.
 *
 * Desde 6G, antes de permitir cualquier ejecucion, la puerta consulta el
 * [AgentActionCapabilityRegistry]: si la capacidad de Android asociada no es
 * SUPPORTED_SAFE, la ejecucion se baja a preparar, confirmar o bloquear,
 * incluso si la propuesta decia que era ejecutable.
 */
class AgentSafeExecutionGate(
    private val capabilityRegistry: AgentActionCapabilityRegistry = AgentActionCapabilityRegistry()
) {

    fun decide(request: AgentSafeExecutionRequest): AgentSafeExecutionDecision {
        val proposal = request.proposal
            ?: return AgentSafeExecutionDecision(
                status = AgentSafeExecutionStatus.NO_ACTIVE_PROPOSAL,
                spokenText = "No hay una accion preparada.",
                reason = "no_active_proposal"
            )

        // Una confirmacion pendiente (bridge o legacy) frena cualquier
        // ejecucion, sin importar el tipo de accion. Primero resolver eso.
        if (request.hasPendingExternalConfirmation) {
            return AgentSafeExecutionDecision(
                status = AgentSafeExecutionStatus.REQUIRE_CONFIRMATION,
                spokenText = "Hay una confirmacion pendiente. Deci confirmar o " +
                    "cancelar antes de ejecutar la accion.",
                reason = "pending_external_confirmation"
            )
        }

        val base = baseDecision(proposal)
        // Paquete 6G: la capacidad de Android manda. Si no es SUPPORTED_SAFE,
        // bajamos la decision aunque la propuesta dijera que era ejecutable.
        return guardWithCapability(proposal, base)
    }

    private fun baseDecision(
        proposal: AgentControlledActionProposal
    ): AgentSafeExecutionDecision = when (proposal.type) {
        AgentControlledActionType.OPEN_APP -> decideOpenApp(proposal)
        AgentControlledActionType.PREPARE_MESSAGE_TEXT,
        AgentControlledActionType.PREPARE_AUDIO_SCRIPT,
        AgentControlledActionType.PREPARE_SEARCH_QUERY -> allowPrepare(proposal)
        AgentControlledActionType.FOCUS_SEARCH_FIELD -> AgentSafeExecutionDecision(
            status = AgentSafeExecutionStatus.PREPARE_ONLY,
            spokenText = "Puedo dejar lista la busqueda, pero todavia no voy a " +
                "tocar nada dentro de la app.",
            reason = "focus_needs_safe_api"
        )
        AgentControlledActionType.REVIEW_PAYMENT_METHOD,
        AgentControlledActionType.REVIEW_RIDE_PRICE -> AgentSafeExecutionDecision(
            status = AgentSafeExecutionStatus.PREPARE_ONLY,
            spokenText = "Puedo ayudarte a revisar, pero no voy a tocar ni " +
                "confirmar nada.",
            reason = "review_only"
        )
        AgentControlledActionType.FINAL_CONFIRM_RIDE -> AgentSafeExecutionDecision(
            status = AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION,
            spokenText = "No puedo solicitar el viaje automaticamente en esta " +
                "version. Puedo guiarte hasta la pantalla de confirmacion y " +
                "ayudarte a revisar precio, destino y forma de pago.",
            reason = "ride_request_blocked"
        )
        AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE -> AgentSafeExecutionDecision(
            status = AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION,
            spokenText = "No puedo enviar mensajes automaticamente en esta " +
                "version. Puedo dejar el mensaje preparado y pedirte " +
                "confirmacion antes de cualquier envio futuro.",
            reason = "send_blocked"
        )
        AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO -> AgentSafeExecutionDecision(
            status = AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION,
            spokenText = "No puedo enviar audios automaticamente en esta " +
                "version. Puedo dejar el guion preparado y pedirte " +
                "confirmacion antes de cualquier envio futuro.",
            reason = "send_blocked"
        )
        AgentControlledActionType.BLOCKED_SENSITIVE_ACTION -> AgentSafeExecutionDecision(
            status = AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION,
            spokenText = "Esta accion es sensible. No la voy a ejecutar.",
            reason = "sensitive_action_blocked"
        )
        AgentControlledActionType.WAIT_FOR_USER_INPUT -> AgentSafeExecutionDecision(
            status = AgentSafeExecutionStatus.WAITING_FOR_USER,
            spokenText = proposal.spokenText,
            reason = "waiting_for_user_input"
        )
        AgentControlledActionType.UNKNOWN -> AgentSafeExecutionDecision(
            status = AgentSafeExecutionStatus.FAILED_SAFE,
            spokenText = "No tengo una accion segura para ejecutar ahora.",
            reason = "unknown_action"
        )
    }

    private fun decideOpenApp(
        proposal: AgentControlledActionProposal
    ): AgentSafeExecutionDecision =
        if (proposal.allowedToExecuteNow) {
            AgentSafeExecutionDecision(
                status = AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION,
                spokenText = proposal.spokenText,
                reason = "open_app_allowed",
                executableType = AgentControlledActionType.OPEN_APP
            )
        } else {
            AgentSafeExecutionDecision(
                status = AgentSafeExecutionStatus.PREPARE_ONLY,
                spokenText = "Por ahora solo puedo dejar la apertura propuesta, no " +
                    "ejecutarla.",
                reason = "open_app_not_executable"
            )
        }

    /**
     * Preparar contenido (texto, guion de audio, query) ES una ejecucion
     * segura: solo escribe en memoria propia, nunca en una app externa y
     * nunca envia. Defensa extra: el tipo no puede ser ejecutable-en-app.
     */
    private fun allowPrepare(
        proposal: AgentControlledActionProposal
    ): AgentSafeExecutionDecision {
        check(!proposal.allowedToExecuteNow) {
            "prepare actions must never be marked executable in an external app"
        }
        check(proposal.type in AgentControlledActionPolicy.SENSITIVE_TYPES ||
            proposal.type == AgentControlledActionType.PREPARE_SEARCH_QUERY) {
            "unexpected prepare type ${proposal.type}"
        }
        return AgentSafeExecutionDecision(
            status = AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION,
            spokenText = proposal.spokenText,
            reason = "prepare_in_memory",
            executableType = proposal.type
        )
    }

    /**
     * Paquete 6G -- guarda de capacidad. Solo se aplica cuando la decision
     * base permite ejecutar: si la capacidad de Android asociada no es
     * SUPPORTED_SAFE, la decision se baja a confirmar, preparar o bloquear.
     */
    private fun guardWithCapability(
        proposal: AgentControlledActionProposal,
        base: AgentSafeExecutionDecision
    ): AgentSafeExecutionDecision {
        if (base.status != AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION) return base
        val capabilityType = capabilityTypeFor(proposal.type) ?: return base
        val capability = capabilityRegistry.capability(capabilityType)
        if (capability.decision == AgentActionCapabilityDecision.SUPPORTED_SAFE) return base

        return when (capability.decision) {
            AgentActionCapabilityDecision.SUPPORTED_REQUIRES_CONFIRMATION ->
                AgentSafeExecutionDecision(
                    status = AgentSafeExecutionStatus.REQUIRE_CONFIRMATION,
                    spokenText = "Esa accion necesita tu confirmacion explicita " +
                        "antes de ejecutarla.",
                    reason = "capability_requires_confirmation:${capabilityType.name}"
                )
            AgentActionCapabilityDecision.UNSUPPORTED_NEEDS_RESEARCH,
            AgentActionCapabilityDecision.INSTRUMENTED_TEST_REQUIRED ->
                AgentSafeExecutionDecision(
                    status = AgentSafeExecutionStatus.PREPARE_ONLY,
                    spokenText = "Todavia no puedo ejecutar esa accion de forma " +
                        "segura. Puedo dejarla preparada hasta habilitarla con " +
                        "mas pruebas.",
                    reason = "capability_not_ready:${capabilityType.name}"
                )
            AgentActionCapabilityDecision.BLOCKED_SENSITIVE,
            AgentActionCapabilityDecision.BLOCKED_DANGEROUS ->
                AgentSafeExecutionDecision(
                    status = AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION,
                    spokenText = "Esa accion esta bloqueada por seguridad. No la " +
                        "voy a ejecutar.",
                    reason = "capability_blocked:${capabilityType.name}"
                )
            AgentActionCapabilityDecision.SUPPORTED_SAFE -> base
        }
    }

    /**
     * Mapea el tipo de accion propuesta (6E) a la capacidad de Android (6G).
     * Devuelve null cuando no hay una capacidad que auditar para ese tipo.
     */
    private fun capabilityTypeFor(
        type: AgentControlledActionType
    ): AgentActionCapabilityType? = when (type) {
        AgentControlledActionType.OPEN_APP ->
            AgentActionCapabilityType.OPEN_APP
        AgentControlledActionType.FOCUS_SEARCH_FIELD ->
            AgentActionCapabilityType.FOCUS_FIELD
        AgentControlledActionType.PREPARE_SEARCH_QUERY ->
            AgentActionCapabilityType.PREPARE_SEARCH_QUERY_IN_MEMORY
        AgentControlledActionType.PREPARE_MESSAGE_TEXT ->
            AgentActionCapabilityType.PREPARE_TEXT_IN_MEMORY
        AgentControlledActionType.PREPARE_AUDIO_SCRIPT ->
            AgentActionCapabilityType.PREPARE_AUDIO_SCRIPT_IN_MEMORY
        AgentControlledActionType.REVIEW_PAYMENT_METHOD,
        AgentControlledActionType.REVIEW_RIDE_PRICE ->
            AgentActionCapabilityType.READ_SAFE_SCREEN_SUMMARY
        AgentControlledActionType.FINAL_CONFIRM_RIDE ->
            AgentActionCapabilityType.REQUEST_RIDE
        AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE ->
            AgentActionCapabilityType.SEND_MESSAGE
        AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO ->
            AgentActionCapabilityType.SEND_AUDIO
        AgentControlledActionType.WAIT_FOR_USER_INPUT ->
            AgentActionCapabilityType.READ_TASK_STATE
        AgentControlledActionType.BLOCKED_SENSITIVE_ACTION,
        AgentControlledActionType.UNKNOWN -> null
    }
}
