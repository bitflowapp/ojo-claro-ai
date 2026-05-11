package com.ojoclaro.android.agent.core.chain

/**
 * Estado de confirmación de un paso individual dentro de una ChainedActionSession.
 */
enum class PlanStepConfirmationState {
    /** Recién creado, no se le presentó al usuario aún. */
    NOT_YET_PRESENTED,

    /** El asistente le presentó el paso al usuario y está esperando "confirmar". */
    WAITING_FOR_USER_CONFIRMATION,

    /** El usuario confirmó. Ya se ejecutó (handoff externo). */
    CONFIRMED,

    /** El usuario canceló este paso. */
    CANCELED,

    /** Expiró sin confirmación. */
    EXPIRED
}
