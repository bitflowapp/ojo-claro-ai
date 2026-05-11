package com.ojoclaro.android.agent.core

/**
 * Estado de un AgentPlan. Las transiciones permitidas las controla
 * ChainedActionSession.
 */
enum class AgentPlanStatus {
    /** Plan recién creado, primer paso aún no presentado al usuario. */
    PENDING,

    /** El primer paso fue presentado, esperando que el usuario confirme. */
    AWAITING_FIRST_CONFIRMATION,

    /**
     * Un paso ya fue ejecutado (típicamente con handoff externo), esperando que
     * el usuario vuelva al app y confirme el siguiente paso.
     */
    AWAITING_NEXT_STEP_CONFIRMATION,

    /** Ejecutando un paso (transitorio). */
    EXECUTING_STEP,

    /** Todos los pasos completados. */
    COMPLETED,

    /** Usuario canceló. */
    CANCELED,

    /** SafetyPolicy/Planner bloqueó el plan a mitad de camino. */
    BLOCKED,

    /** Plan venció por inactividad. */
    EXPIRED
}
