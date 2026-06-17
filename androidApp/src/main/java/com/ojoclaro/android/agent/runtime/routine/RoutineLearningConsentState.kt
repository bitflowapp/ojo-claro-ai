package com.ojoclaro.android.agent.runtime.routine

/**
 * Estado de consentimiento para el aprendizaje pasivo de rutinas.
 *
 *  - UNSET: por default. Inferencias NO se sugieren. Comandos explícitos sí
 *    funcionan ("recordá que ...") porque ya son consentimiento del usuario.
 *  - OPTED_IN: el usuario aceptó recibir sugerencias de patrones repetidos.
 *  - OPTED_OUT: el usuario explícitamente desactivó el aprendizaje. Mismo
 *    comportamiento que UNSET para inferencias; los comandos explícitos
 *    funcionan, pero al pedir guardar nuevas preferencias se le aclara.
 */
enum class RoutineLearningConsentState {
    UNSET,
    OPTED_IN,
    OPTED_OUT;

    val allowsInference: Boolean
        get() = this == OPTED_IN
}
