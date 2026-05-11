package com.ojoclaro.android.agent.core

/**
 * Modo activo del usuario actual. El planner consulta este modo para decidir
 * qué tools están disponibles, qué tan verboso responder y qué umbral aplicar
 * a las confirmaciones.
 */
enum class AgentExecutionMode {
    /** Usuario no vidente o con baja visión. Default seguro. */
    ACCESSIBILITY_VOICE,

    /** Usuario vidente — habilita modos visuales/cortos. */
    SIGHTED,

    /** Modo emergencia activo — confirmaciones más cortas, prioridad en contacto/llamada. */
    EMERGENCY
}
