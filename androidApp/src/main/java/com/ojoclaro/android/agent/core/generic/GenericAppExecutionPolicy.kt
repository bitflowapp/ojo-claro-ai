package com.ojoclaro.android.agent.core.generic

/**
 * Policy de alto nivel para ejecutar (o más bien NO ejecutar) acciones
 * genéricas sobre apps de terceros.
 *
 * En v1:
 *  - executionEnabled está siempre en false.
 *  - Solo se acepta el path "ALLOWED_WITH_CONFIRMATION" del SafetyGate, y
 *    aun así el caller debe traducirlo a un handoff externo conocido (open app,
 *    leer pantalla con servicio de accesibilidad ya autorizado, etc.).
 *  - GENERIC_AUTOMATION está prohibido — esta policy nunca puede ejecutar taps.
 */
class GenericAppExecutionPolicy(
    private val safetyGate: GenericAppSafetyGate = GenericAppSafetyGate(),
    val executionEnabled: Boolean = false
) {

    fun decide(request: GenericAppActionRequest): GenericAppExecutionDecision {
        if (request.capability in DISALLOWED_IN_V1) {
            return GenericAppExecutionDecision.Refused(
                spokenText = "Esa acción todavía no está permitida.",
                reason = "capability_in_v1_blocklist"
            )
        }
        if (!executionEnabled) {
            return GenericAppExecutionDecision.GuidanceOnly(
                spokenText = "No puedo operar la app por vos. Te puedo guiar con voz."
            )
        }
        return when (val gate = safetyGate.evaluate(request)) {
            is GenericAppGateDecision.Blocked -> GenericAppExecutionDecision.Refused(
                spokenText = gate.spokenText,
                reason = gate.reason
            )
            is GenericAppGateDecision.AllowedWithConfirmation ->
                GenericAppExecutionDecision.AwaitingConfirmation(
                    spokenText = gate.spokenConfirmationPrompt
                )
        }
    }

    companion object {
        val DISALLOWED_IN_V1: Set<GenericAppCapability> = setOf(
            GenericAppCapability.TAP_BUTTON,
            GenericAppCapability.TYPE_INTO_FIELD,
            GenericAppCapability.SUBMIT_FORM,
            GenericAppCapability.NAVIGATE_WITHIN_APP,
            GenericAppCapability.PAY,
            GenericAppCapability.GENERIC_AUTOMATION
        )
    }
}

sealed class GenericAppExecutionDecision {
    data class AwaitingConfirmation(val spokenText: String) : GenericAppExecutionDecision()
    data class GuidanceOnly(val spokenText: String) : GenericAppExecutionDecision()
    data class Refused(val spokenText: String, val reason: String) : GenericAppExecutionDecision()
}
