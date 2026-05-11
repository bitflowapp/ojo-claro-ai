package com.ojoclaro.android.agent.core.generic

import com.ojoclaro.android.risk.RiskDetector

/**
 * Gate de seguridad para acciones genéricas sobre apps de terceros.
 *
 * Reglas no negociables:
 *  - Apps bancarias / de pago: TODO bloqueado, incluso lectura.
 *  - Capacidades de "tap"/"type"/"submit"/"navigate"/"pay" están bloqueadas en
 *    v1 sin excepción.
 *  - OPEN_APP, READ_SCREEN, SUMMARIZE_SCREEN y GUIDE_USER están permitidas, pero
 *    todas requieren confirmación.
 *  - GENERIC_AUTOMATION queda bloqueada — no se puede ejecutar nada arbitrario.
 */
class GenericAppSafetyGate(
    private val riskDetector: RiskDetector = RiskDetector()
) {

    fun evaluate(request: GenericAppActionRequest): GenericAppGateDecision {
        if (isBankingOrPaymentPackage(request.packageName)) {
            return GenericAppGateDecision.Blocked(
                reason = "package_is_banking_or_payment",
                spokenText = "No opero apps de bancos ni de pagos. Hacelo vos."
            )
        }

        if (request.capability in DANGEROUS_CAPABILITIES) {
            return GenericAppGateDecision.Blocked(
                reason = "capability_not_allowed_in_v1",
                spokenText = "Esa acción no está habilitada por ahora. Te puedo guiar con voz, pero no toco la app."
            )
        }

        return when (request.capability) {
            GenericAppCapability.OPEN_APP,
            GenericAppCapability.READ_SCREEN,
            GenericAppCapability.SUMMARIZE_SCREEN,
            GenericAppCapability.GUIDE_USER -> GenericAppGateDecision.AllowedWithConfirmation(
                spokenConfirmationPrompt = confirmationFor(request.capability, request.packageName)
            )
            else -> GenericAppGateDecision.Blocked(
                reason = "capability_unknown",
                spokenText = "No reconozco esa acción."
            )
        }
    }

    private fun isBankingOrPaymentPackage(packageName: String): Boolean =
        riskDetector.detectFromPackageName(packageName).isNotEmpty() ||
            PAYMENT_PACKAGE_TOKENS.any { packageName.lowercase().contains(it) }

    private fun confirmationFor(capability: GenericAppCapability, packageName: String): String =
        when (capability) {
            GenericAppCapability.OPEN_APP ->
                "Voy a abrir $packageName. Para abrirla, decí: confirmar."
            GenericAppCapability.READ_SCREEN ->
                "Voy a leer la pantalla visible. Eso requiere tu confirmación: decí confirmar."
            GenericAppCapability.SUMMARIZE_SCREEN ->
                "Voy a resumir lo que veo en la pantalla. Confirmá."
            GenericAppCapability.GUIDE_USER ->
                "Te voy a guiar con voz, sin tocar la app. ¿Seguimos? Decí: confirmar."
            else -> "Para continuar, decí: confirmar."
        }

    companion object {
        val DANGEROUS_CAPABILITIES: Set<GenericAppCapability> = setOf(
            GenericAppCapability.TAP_BUTTON,
            GenericAppCapability.TYPE_INTO_FIELD,
            GenericAppCapability.SUBMIT_FORM,
            GenericAppCapability.NAVIGATE_WITHIN_APP,
            GenericAppCapability.PAY,
            GenericAppCapability.GENERIC_AUTOMATION
        )

        private val PAYMENT_PACKAGE_TOKENS = listOf(
            "mercadopago",
            "modo",
            "uala",
            "lemon",
            "naranjax",
            "pago",
            "wallet"
        )
    }
}

sealed class GenericAppGateDecision {
    data class AllowedWithConfirmation(val spokenConfirmationPrompt: String) : GenericAppGateDecision()
    data class Blocked(val reason: String, val spokenText: String) : GenericAppGateDecision()
}
