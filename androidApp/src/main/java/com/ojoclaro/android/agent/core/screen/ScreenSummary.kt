package com.ojoclaro.android.agent.core.screen

/**
 * Resumen estructurado de pantalla.
 *
 * spokenText es lo que se le dice al usuario. La policy de seguridad ya pasó:
 * si la pantalla era hot zone, este resumen NO contiene el texto sino una
 * advertencia.
 */
data class ScreenSummary(
    val mode: ScreenSummaryMode,
    val spokenText: String,
    val risk: ScreenRiskAssessment,
    val isLimited: Boolean
) {
    val isSafeToRead: Boolean
        get() = risk.allowedToReadAloud
}
