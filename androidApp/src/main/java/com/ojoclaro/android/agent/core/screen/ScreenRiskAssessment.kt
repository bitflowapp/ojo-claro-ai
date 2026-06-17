package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.risk.RiskWarning

/**
 * Análisis de riesgo de una pantalla concreta.
 *
 * Lo arma ScreenContextProvider a partir de RiskDetector. allowedToReadAloud
 * controla si Ojo Claro puede leer el texto. Si false, solo se dice la
 * advertencia genérica.
 */
data class ScreenRiskAssessment(
    val warnings: List<RiskWarning>,
    val isBanking: Boolean,
    val containsPasswordField: Boolean,
    val containsVerificationCode: Boolean,
    val allowedToReadAloud: Boolean
) {
    val isHotZone: Boolean
        get() = isBanking || containsPasswordField

    companion object {
        val SAFE: ScreenRiskAssessment = ScreenRiskAssessment(
            warnings = emptyList(),
            isBanking = false,
            containsPasswordField = false,
            containsVerificationCode = false,
            allowedToReadAloud = true
        )
    }
}
