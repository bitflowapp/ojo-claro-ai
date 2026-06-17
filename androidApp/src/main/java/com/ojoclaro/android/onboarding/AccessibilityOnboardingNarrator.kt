package com.ojoclaro.android.onboarding

/**
 * Accessibility Onboarding — copy CALMO y guiado para una persona no vidente.
 * PURO (sin Android). No promete lo que Android no permite: Estela NO puede
 * activar Accesibilidad sola; guía, abre Ajustes y verifica al volver.
 */
object AccessibilityOnboardingNarrator {

    /** Guía hablada de activación (TalkBack-aware). Se dice al abrir Ajustes. */
    fun guidance(): String =
        "Te voy a guiar para activar Estela. Android exige que una persona confirme este permiso, " +
            "así que no puedo activarlo solo. Voy a abrir Ajustes. Buscá Ojo Claro o Estela, " +
            "activá el interruptor y aceptá el aviso. Si usás TalkBack, deslizá hasta escuchar Estela " +
            "y tocá dos veces. Cuando vuelvas, decí: ya activé."

    /**
     * Verificación al volver. [serviceListed] = el servicio figura en
     * enabled_accessibility_services; [bound] = está realmente vinculado
     * (isConnected). Distingue el caso "switch on pero Android no vinculó".
     */
    fun returnCheck(serviceListed: Boolean, bound: Boolean): String = when {
        bound -> "Listo, Estela ya puede leer la pantalla."
        serviceListed ->
            "El interruptor figura activado, pero Android todavía no vinculó el servicio. " +
                "Probá apagar y prender Estela en Accesibilidad, o reiniciar el teléfono."
        else -> "Todavía falta activar Estela en Accesibilidad. Decí: activar Estela, y te guío."
    }

    /** Copy de recuperación, corto y tranquilo. */
    const val CANT_READ = "Todavía no puedo leer la pantalla."
    const val TAKING_TO_SETTINGS = "Te llevo a Ajustes."
    const val WONT_TOUCH_ANYTHING = "No voy a tocar nada peligroso."
    const val SAY_DONE_WHEN_BACK = "Cuando vuelvas, decí: ya activé."
    const val ASK_TRUSTED_HELPER =
        "Si no ves la pantalla, pedile a alguien de confianza que active Estela una sola vez."

    /** Mensaje cuando no se pudo abrir Ajustes (fallback honesto). */
    const val COULD_NOT_OPEN_SETTINGS =
        "No pude abrir Ajustes. Entrá a Ajustes, Accesibilidad, y activá Ojo Claro o Estela."
}
