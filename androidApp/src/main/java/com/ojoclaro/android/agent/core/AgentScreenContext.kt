package com.ojoclaro.android.agent.core

/**
 * Contexto reducido de pantalla que el planner puede usar SIN guardar el texto.
 *
 * No contiene el dump completo de la pantalla. Solo flags y un resumen corto.
 * Los flags son consumidos por la policy para bloquear o limitar acciones.
 */
data class AgentScreenContext(
    val packageName: String? = null,
    val shortSummary: String = "",
    val isSensitive: Boolean = false,
    val isBankingScreen: Boolean = false,
    val containsPasswordField: Boolean = false,
    val containsVerificationCode: Boolean = false
) {
    val shouldBlockGeneralActions: Boolean
        get() = isBankingScreen || containsPasswordField
}
