package com.ojoclaro.android.agent.task.action

/**
 * Paquete 6E -- Controlled Action Proposal v1.
 *
 * Esta capa es PURA: no toca Android, no ejecuta nada, no llama a
 * performClick / dispatchGesture / performGlobalAction. Solo describe cual
 * seria la proxima accion segura de una tarea activa, clasifica su riesgo y
 * dice si Estela puede prepararla, si requiere confirmacion o si esta
 * bloqueada.
 *
 * Diferencia clave (6E):
 *  - PROPONER  -> describir que se podria hacer.
 *  - PREPARAR  -> dejar contenido listo en memoria (texto, guion, busqueda).
 *  - EJECUTAR  -> NO ocurre en 6E para ninguna accion sensible.
 */
enum class AgentControlledActionType {
    /** Abrir una app ya soportada por SafeAppLauncher. No toca nada adentro. */
    OPEN_APP,
    /** Dejar marcado el campo de busqueda. No escribe texto automaticamente. */
    FOCUS_SEARCH_FIELD,
    /** Preparar el texto de busqueda en memoria. No lo escribe en la app. */
    PREPARE_SEARCH_QUERY,
    /** Preparar el contenido de un mensaje en memoria. No lo envia. */
    PREPARE_MESSAGE_TEXT,
    /** Preparar el guion de un audio en memoria. No lo graba ni lo envia. */
    PREPARE_AUDIO_SCRIPT,
    /** Orientar sobre el metodo de pago. No lo toca ni lo cambia. */
    REVIEW_PAYMENT_METHOD,
    /** Orientar sobre el precio del viaje. No lo acepta ni pide el viaje. */
    REVIEW_RIDE_PRICE,
    /** Confirmacion final para solicitar viaje. Critica y bloqueada en 6E. */
    FINAL_CONFIRM_RIDE,
    /** Confirmacion final para enviar mensaje. Critica y bloqueada en 6E. */
    FINAL_CONFIRM_SEND_MESSAGE,
    /** Confirmacion final para enviar audio. Critica y bloqueada en 6E. */
    FINAL_CONFIRM_SEND_AUDIO,
    /** Pantalla sensible: no se lee ni se propone ninguna accion. */
    BLOCKED_SENSITIVE_ACTION,
    /** Falta un dato del usuario para poder seguir. */
    WAIT_FOR_USER_INPUT,
    /** No se reconoce una proxima accion segura. */
    UNKNOWN
}

/** Nivel de riesgo de la accion propuesta. */
enum class AgentControlledActionRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/** Estado de la propuesta de accion. */
enum class AgentControlledActionStatus {
    /** Propuesta recien creada. */
    PROPOSED,
    /** Falta una respuesta del usuario antes de poder avanzar. */
    WAITING_FOR_USER,
    /** Lista pero necesita confirmacion explicita antes de cualquier avance. */
    REQUIRES_CONFIRMATION,
    /** Bloqueada: no se puede ejecutar en esta version. */
    BLOCKED,
    /** Preparada en memoria, pero NO ejecutada (no se envia / no se toca). */
    READY_BUT_NOT_EXECUTED,
    /** Cancelada por el usuario o por seguridad. */
    CANCELLED
}

/**
 * Propuesta de accion controlada. Tipo valor inmutable, seguro para cruzar
 * capas (memoria de tarea, UI, voz) sin riesgo de ejecutar nada.
 *
 * Regla central: [allowedToExecuteNow] es false para CUALQUIER accion
 * sensible. En 6E solo OPEN_APP puede marcarse como ejecutable, porque abrir
 * una app ya esta soportado de forma segura por SafeAppLauncher.
 */
data class AgentControlledActionProposal(
    val id: String,
    val taskId: String,
    val ticketId: String? = null,
    val type: AgentControlledActionType,
    val title: String,
    val safeDescription: String,
    val riskLevel: AgentControlledActionRisk,
    val status: AgentControlledActionStatus,
    val requiresConfirmation: Boolean,
    val allowedToExecuteNow: Boolean,
    val blockedReason: String? = null,
    val forbiddenReason: String? = null,
    val spokenText: String,
    /**
     * Paquete 6F -- contenido preparado en memoria asociado a la propuesta:
     * texto del mensaje, guion del audio o query de busqueda. Null para tipos
     * que no preparan contenido (OPEN_APP, REVIEW_*, FINAL_CONFIRM_*, etc.).
     * Nunca contiene datos sensibles: se sanea antes de guardarse.
     */
    val preparedText: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank()) { "proposal id must not be blank" }
        require(taskId.isNotBlank()) { "proposal taskId must not be blank" }
        require(title.isNotBlank()) { "proposal title must not be blank" }
        require(safeDescription.isNotBlank()) { "proposal safeDescription must not be blank" }
        require(spokenText.isNotBlank()) { "proposal spokenText must not be blank" }
        require(createdAt >= 0L) { "createdAt must be non-negative" }
        require(updatedAt >= createdAt) { "updatedAt must be >= createdAt" }
        if (type in AgentControlledActionPolicy.SENSITIVE_TYPES) {
            require(!allowedToExecuteNow) {
                "sensitive controlled actions must never be allowed to execute now"
            }
        }
    }

    val isSensitive: Boolean
        get() = type in AgentControlledActionPolicy.SENSITIVE_TYPES

    /** Texto corto para UI: riesgo en castellano. */
    val riskLabelForUi: String
        get() = when (riskLevel) {
            AgentControlledActionRisk.LOW -> "bajo"
            AgentControlledActionRisk.MEDIUM -> "medio"
            AgentControlledActionRisk.HIGH -> "alto"
            AgentControlledActionRisk.CRITICAL -> "critico"
        }
}

/**
 * Intencion del comando que pide una propuesta de accion. Se deriva del
 * comando de voz ya normalizado.
 */
enum class AgentControlledActionRequest {
    /** "cual es el proximo paso", "que vas a hacer ahora", "segui", ... */
    NEXT_STEP,
    /** "busca el chat" */
    SEARCH_CHAT,
    /** "prepara el mensaje" */
    PREPARE_MESSAGE,
    /** "prepara el audio" */
    PREPARE_AUDIO,
    /** "prepara el taxi" */
    PREPARE_RIDE,
    /** "revisa el precio" */
    REVIEW_PRICE
}

/** Resultado de pedir una propuesta de accion controlada. */
data class AgentControlledActionResult(
    val kind: AgentControlledActionResultKind,
    val spokenText: String,
    val proposal: AgentControlledActionProposal?
)

enum class AgentControlledActionResultKind {
    PROPOSAL_CREATED,
    PROPOSAL_CANCELLED,
    NO_ACTIVE_TASK,
    NO_PROPOSAL_TO_CANCEL,
    BLOCKED_BY_PENDING_CONFIRMATION
}
