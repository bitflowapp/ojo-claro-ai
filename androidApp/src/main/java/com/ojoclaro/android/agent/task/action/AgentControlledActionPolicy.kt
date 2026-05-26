package com.ojoclaro.android.agent.task.action

/**
 * Paquete 6E -- Politica de seguridad de acciones controladas.
 *
 * Fuente unica de verdad para: riesgo, estado, si requiere confirmacion y si
 * la accion puede ejecutarse ahora. La politica es PURA y determinista.
 *
 * Reglas duras (no se pueden romper en 6E):
 *  - Ninguna accion sensible es ejecutable ([allowedToExecuteNow] = false).
 *  - Pedir viaje, enviar mensaje, enviar audio: BLOQUEADAS aunque el usuario
 *    confirme. La confirmacion fuerte se puede pedir, pero no se ejecuta.
 *  - Pago y precio de viaje: solo orientar, nunca tocar.
 *  - Solo OPEN_APP puede marcarse ejecutable, porque abrir apps ya esta
 *    soportado de forma segura por SafeAppLauncher.
 */
object AgentControlledActionPolicy {

    /** Tipos considerados sensibles: nunca ejecutables en 6E. */
    val SENSITIVE_TYPES: Set<AgentControlledActionType> = setOf(
        AgentControlledActionType.PREPARE_MESSAGE_TEXT,
        AgentControlledActionType.PREPARE_AUDIO_SCRIPT,
        AgentControlledActionType.REVIEW_PAYMENT_METHOD,
        AgentControlledActionType.REVIEW_RIDE_PRICE,
        AgentControlledActionType.FINAL_CONFIRM_RIDE,
        AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE,
        AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO,
        AgentControlledActionType.BLOCKED_SENSITIVE_ACTION
    )

    fun isSensitive(type: AgentControlledActionType): Boolean = type in SENSITIVE_TYPES

    data class Evaluation(
        val riskLevel: AgentControlledActionRisk,
        val status: AgentControlledActionStatus,
        val requiresConfirmation: Boolean,
        val allowedToExecuteNow: Boolean,
        val blockedReason: String?,
        val forbiddenReason: String?
    )

    /**
     * Evalua un tipo de accion y devuelve su clasificacion de seguridad.
     * [allowedToExecuteNow] se fuerza a false para cualquier tipo sensible,
     * sin importar lo que diga la tabla (defensa en profundidad).
     */
    fun evaluate(type: AgentControlledActionType): Evaluation {
        val base = baseEvaluation(type)
        return if (isSensitive(type) && base.allowedToExecuteNow) {
            base.copy(allowedToExecuteNow = false)
        } else {
            base
        }
    }

    private fun baseEvaluation(type: AgentControlledActionType): Evaluation = when (type) {
        AgentControlledActionType.OPEN_APP -> Evaluation(
            riskLevel = AgentControlledActionRisk.LOW,
            status = AgentControlledActionStatus.READY_BUT_NOT_EXECUTED,
            requiresConfirmation = false,
            // Unico tipo ejecutable: abrir app ya es seguro vvia SafeAppLauncher.
            allowedToExecuteNow = true,
            blockedReason = null,
            forbiddenReason = null
        )
        AgentControlledActionType.FOCUS_SEARCH_FIELD -> Evaluation(
            riskLevel = AgentControlledActionRisk.MEDIUM,
            status = AgentControlledActionStatus.READY_BUT_NOT_EXECUTED,
            requiresConfirmation = false,
            allowedToExecuteNow = false,
            blockedReason = null,
            forbiddenReason = REASON_NO_AUTO_TYPING
        )
        AgentControlledActionType.PREPARE_SEARCH_QUERY -> Evaluation(
            riskLevel = AgentControlledActionRisk.MEDIUM,
            status = AgentControlledActionStatus.READY_BUT_NOT_EXECUTED,
            requiresConfirmation = false,
            allowedToExecuteNow = false,
            blockedReason = null,
            forbiddenReason = REASON_NO_AUTO_TYPING
        )
        AgentControlledActionType.PREPARE_MESSAGE_TEXT -> Evaluation(
            riskLevel = AgentControlledActionRisk.HIGH,
            status = AgentControlledActionStatus.READY_BUT_NOT_EXECUTED,
            requiresConfirmation = false,
            allowedToExecuteNow = false,
            blockedReason = null,
            forbiddenReason = REASON_NO_AUTO_SEND
        )
        AgentControlledActionType.PREPARE_AUDIO_SCRIPT -> Evaluation(
            riskLevel = AgentControlledActionRisk.HIGH,
            status = AgentControlledActionStatus.READY_BUT_NOT_EXECUTED,
            requiresConfirmation = false,
            allowedToExecuteNow = false,
            blockedReason = null,
            forbiddenReason = REASON_NO_AUDIO_RECORD
        )
        AgentControlledActionType.REVIEW_PAYMENT_METHOD -> Evaluation(
            riskLevel = AgentControlledActionRisk.HIGH,
            status = AgentControlledActionStatus.REQUIRES_CONFIRMATION,
            requiresConfirmation = true,
            allowedToExecuteNow = false,
            blockedReason = null,
            forbiddenReason = REASON_NO_PAYMENT_TOUCH
        )
        AgentControlledActionType.REVIEW_RIDE_PRICE -> Evaluation(
            riskLevel = AgentControlledActionRisk.HIGH,
            status = AgentControlledActionStatus.REQUIRES_CONFIRMATION,
            requiresConfirmation = true,
            allowedToExecuteNow = false,
            blockedReason = null,
            forbiddenReason = REASON_NO_RIDE_ACCEPT
        )
        AgentControlledActionType.FINAL_CONFIRM_RIDE -> Evaluation(
            riskLevel = AgentControlledActionRisk.CRITICAL,
            status = AgentControlledActionStatus.BLOCKED,
            requiresConfirmation = true,
            allowedToExecuteNow = false,
            blockedReason = REASON_RIDE_BLOCKED,
            forbiddenReason = REASON_NO_RIDE_REQUEST
        )
        AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE -> Evaluation(
            riskLevel = AgentControlledActionRisk.CRITICAL,
            status = AgentControlledActionStatus.BLOCKED,
            requiresConfirmation = true,
            allowedToExecuteNow = false,
            blockedReason = REASON_SEND_BLOCKED,
            forbiddenReason = REASON_NO_MESSAGE_SEND
        )
        AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO -> Evaluation(
            riskLevel = AgentControlledActionRisk.CRITICAL,
            status = AgentControlledActionStatus.BLOCKED,
            requiresConfirmation = true,
            allowedToExecuteNow = false,
            blockedReason = REASON_SEND_BLOCKED,
            forbiddenReason = REASON_NO_AUDIO_SEND
        )
        AgentControlledActionType.BLOCKED_SENSITIVE_ACTION -> Evaluation(
            riskLevel = AgentControlledActionRisk.CRITICAL,
            status = AgentControlledActionStatus.BLOCKED,
            requiresConfirmation = false,
            allowedToExecuteNow = false,
            blockedReason = REASON_SENSITIVE_SCREEN,
            forbiddenReason = REASON_NO_SENSITIVE_READ
        )
        AgentControlledActionType.WAIT_FOR_USER_INPUT -> Evaluation(
            riskLevel = AgentControlledActionRisk.LOW,
            status = AgentControlledActionStatus.WAITING_FOR_USER,
            requiresConfirmation = false,
            allowedToExecuteNow = false,
            blockedReason = null,
            forbiddenReason = null
        )
        AgentControlledActionType.UNKNOWN -> Evaluation(
            riskLevel = AgentControlledActionRisk.LOW,
            status = AgentControlledActionStatus.BLOCKED,
            requiresConfirmation = false,
            allowedToExecuteNow = false,
            blockedReason = REASON_UNKNOWN_ACTION,
            forbiddenReason = null
        )
    }

    const val REASON_NO_AUTO_TYPING: String =
        "no se escribe texto automaticamente en apps externas"
    const val REASON_NO_AUTO_SEND: String =
        "el mensaje no se envia sin confirmacion final"
    const val REASON_NO_AUDIO_RECORD: String =
        "el audio no se graba ni se envia automaticamente"
    const val REASON_NO_PAYMENT_TOUCH: String =
        "no se toca ni se cambia el metodo de pago"
    const val REASON_NO_RIDE_ACCEPT: String =
        "no se acepta el precio ni se pide el viaje"
    const val REASON_NO_RIDE_REQUEST: String =
        "no se pide el viaje en esta version"
    const val REASON_NO_MESSAGE_SEND: String =
        "no se envia el mensaje en esta version"
    const val REASON_NO_AUDIO_SEND: String =
        "no se graba ni se envia el audio en esta version"
    const val REASON_NO_SENSITIVE_READ: String =
        "no se leen datos sensibles de la pantalla"
    const val REASON_RIDE_BLOCKED: String =
        "el pedido de viaje sigue bloqueado hasta un paquete futuro"
    const val REASON_SEND_BLOCKED: String =
        "el envio sigue bloqueado hasta un paquete futuro"
    const val REASON_SENSITIVE_SCREEN: String =
        "la pantalla actual puede tener datos sensibles"
    const val REASON_UNKNOWN_ACTION: String =
        "no hay una proxima accion segura reconocida"
}
