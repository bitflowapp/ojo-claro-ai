package com.ojoclaro.android.agent.core.emergency

/**
 * Estado activo del modo emergencia.
 *
 * INACTIVE -> CONFIRMING (cuenta atrás cancelable) -> ACTING (handoff disparado) -> ENDED.
 * También puede ir directamente a CANCELED desde CONFIRMING.
 */
enum class EmergencyModeState {
    INACTIVE,
    CONFIRMING,
    ACTING,
    CANCELED,
    ENDED
}
