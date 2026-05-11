package com.ojoclaro.android.agent.core

/**
 * De dónde vino una preferencia.
 *
 * Reglas:
 *  - USER_EXPLICIT: el usuario lo dijo literalmente ("recordá que ...").
 *  - USER_CONFIRMED: el sistema sugirió, el usuario aceptó.
 *  - INFERRED_PENDING_CONFIRMATION: el sistema infirió un patrón pero todavía
 *    NO debe usarlo. El planner debe ignorar preferencias en este estado.
 *  - DEFAULT: valor por defecto sin input del usuario.
 */
enum class AgentPreferenceSource {
    USER_EXPLICIT,
    USER_CONFIRMED,
    INFERRED_PENDING_CONFIRMATION,
    DEFAULT
}
