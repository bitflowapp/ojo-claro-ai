package com.ojoclaro.android.agent.core.screen

/**
 * Resultado del [NextStepAdvisor].
 *
 * Diseñado para que el caller (HomeViewModel) sepa exactamente qué hablar y
 * cómo presentar la respuesta en UI. NUNCA implica ejecución — el advisor
 * sólo orienta, jamás dispara performClick / dispatchGesture / Intent.
 */
sealed class NextStepAdvice {

    /** Texto seguro listo para TTS. Nunca afirma haber ejecutado nada. */
    abstract val spokenText: String

    /**
     * No hay un snapshot estructurado disponible (flags off, AccessibilityService
     * desactivado, o el repository nunca recibió un publish). El caller debe
     * ofrecer al usuario activar Accesibilidad o esperar.
     */
    data class NoSnapshot(
        override val spokenText: String = DEFAULT_NO_SNAPSHOT_TEXT
    ) : NextStepAdvice()

    /**
     * La pantalla es hot zone (banca, password, OTP, transferencia). El
     * advisor no enumera elementos; sólo advierte y sugiere salir.
     */
    data class SafetyBlocked(
        val reasonKey: String,
        override val spokenText: String
    ) : NextStepAdvice()

    /**
     * Snapshot válido pero no hay botones / campos accionables visibles. El
     * usuario puede leer texto o pedir un resumen distinto.
     */
    data class NoActionsDetected(
        override val spokenText: String = DEFAULT_NO_ACTIONS_TEXT
    ) : NextStepAdvice()

    /**
     * Hay una sola acción clara (ej. único botón principal). El advisor sugiere
     * tocarla, pero el toque queda en manos del usuario.
     */
    data class SingleAction(
        val buttonLabel: String,
        val appLabel: String?,
        override val spokenText: String
    ) : NextStepAdvice()

    /**
     * Hay varias acciones. El advisor lista las top N y aclara que el usuario
     * elige. No ranquea por preferencia personal (no hay aún); solo orden de
     * aparición en el snapshot.
     */
    data class MultipleActions(
        val buttonLabels: List<String>,
        val appLabel: String?,
        override val spokenText: String
    ) : NextStepAdvice()

    /**
     * Hay campos editables que faltan completar antes de poder avanzar. El
     * advisor sugiere completar el primero (ya enfocado si aplica).
     */
    data class FormFillNeeded(
        val fieldLabel: String,
        val appLabel: String?,
        override val spokenText: String
    ) : NextStepAdvice()

    companion object {
        const val DEFAULT_NO_SNAPSHOT_TEXT: String =
            "Todavía no tengo lectura de esta pantalla. " +
                "Activá el modo asistido o tocá en cualquier parte para que pueda mirar."

        const val DEFAULT_NO_ACTIONS_TEXT: String =
            "Veo la pantalla pero no detecté botones claros. " +
                "Pedime resumir la pantalla o decime qué querés hacer."
    }
}
