package com.ojoclaro.android.agent.task.execution

import com.ojoclaro.android.agent.task.action.AgentControlledActionPolicy
import com.ojoclaro.android.agent.task.action.AgentControlledActionProposal
import com.ojoclaro.android.agent.task.action.AgentControlledActionType

/**
 * Paquete 6F -- Safe Execution Gate.
 *
 * Capa PURA: decide si una propuesta de accion puede ejecutarse de forma
 * segura, si solo puede prepararse, si esta bloqueada o si requiere
 * confirmacion. No tiene efectos: no abre apps, no escribe, no envia, no
 * llama a performClick / dispatchGesture / performGlobalAction.
 *
 * La ejecucion real (abrir app vvia SafeAppLauncher, preparar contenido en
 * memoria) la realiza el orquestador SOLO cuando esta puerta devuelve
 * [AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION].
 */
class AgentSafeExecutionGate {

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

        return when (proposal.type) {
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
                    "version.",
                reason = "ride_request_blocked"
            )
            AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE,
            AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO -> AgentSafeExecutionDecision(
                status = AgentSafeExecutionStatus.BLOCK_SENSITIVE_ACTION,
                spokenText = "No puedo enviar mensajes o audios automaticamente en " +
                    "esta version.",
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
}
