package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * WhatsApp Fluency (sprint #6) — DISEÑO de guardrails de llamadas/videollamadas.
 *
 * IMPORTANTE — SOLO POLÍTICA PURA:
 *  - NO ejecuta ni toca botones de llamada.
 *  - NO está cableada al runtime; el flujo de videollamada V1.11 existente queda
 *    INTACTO. Esto es exclusivamente el conjunto de reglas que un futuro cableado
 *    DELIBERADO debería respetar (cuando el usuario lo autorice).
 *
 * Reglas:
 *  - Aceptar / rechazar una llamada ENTRANTE: requiere UNA confirmación explícita
 *    (nunca se atiende/rechaza solo porque sí).
 *  - Iniciar llamada o videollamada: requiere contacto CLARO + DOBLE confirmación
 *    FUERTE. Nunca por "sí"/"dale" a secas. Se BLOQUEA si el contacto es ambiguo
 *    o no se encontró.
 *  - Aun cuando todas las guardas pasen ([Decision.WOULD_ALLOW]), hoy
 *    [isExecutionEnabled] es false: la llamada real NO se ejecuta por contrato.
 *  - Decisiones sanitizadas: enums, sin teléfono ni contenido.
 */
object WhatsAppCallGuardrailPolicy {

    enum class CallIntent {
        INITIATE_CALL,
        INITIATE_VIDEO_CALL,
        ACCEPT_INCOMING,
        REJECT_INCOMING
    }

    enum class ContactResolution {
        RESOLVED,
        AMBIGUOUS,
        NOT_FOUND,
        /** Para entrantes: no hace falta resolver contacto. */
        NOT_NEEDED
    }

    enum class ConfirmationStrength { NONE, WEAK, STRONG }

    data class CallRequest(
        val intent: CallIntent,
        val contact: ContactResolution,
        val confirmation: ConfirmationStrength,
        /** Confirmaciones FUERTES ya acumuladas en turnos anteriores. */
        val priorStrongConfirmations: Int = 0
    )

    enum class Decision {
        /** Contrato del sprint: las llamadas reales no se ejecutan todavía. */
        BLOCKED_NOT_IMPLEMENTED,
        BLOCKED_AMBIGUOUS_CONTACT,
        BLOCKED_NO_CONTACT,
        /** "sí"/"dale" a secas nunca inicia una llamada. */
        BLOCKED_WEAK_CONFIRMATION,
        /** Entrante: pedir una confirmación explícita (atender/rechazar). */
        REQUIRES_CONFIRMATION,
        REQUIRES_FIRST_STRONG_CONFIRMATION,
        REQUIRES_SECOND_STRONG_CONFIRMATION,
        /** Todas las guardas pasaron. NO implica ejecutar: ver [isExecutionEnabled]. */
        WOULD_ALLOW
    }

    /**
     * Decisión de las GUARDAS (no ejecuta nada). El cableado real debe, además,
     * chequear [isExecutionEnabled] antes de cualquier acción.
     */
    fun decide(request: CallRequest): Decision = when (request.intent) {
        CallIntent.ACCEPT_INCOMING, CallIntent.REJECT_INCOMING ->
            if (request.confirmation == ConfirmationStrength.NONE) {
                Decision.REQUIRES_CONFIRMATION
            } else {
                Decision.WOULD_ALLOW
            }

        CallIntent.INITIATE_CALL, CallIntent.INITIATE_VIDEO_CALL -> when {
            request.contact == ContactResolution.AMBIGUOUS -> Decision.BLOCKED_AMBIGUOUS_CONTACT
            request.contact == ContactResolution.NOT_FOUND -> Decision.BLOCKED_NO_CONTACT
            request.confirmation == ConfirmationStrength.WEAK -> Decision.BLOCKED_WEAK_CONFIRMATION
            request.confirmation == ConfirmationStrength.NONE -> Decision.REQUIRES_FIRST_STRONG_CONFIRMATION
            request.priorStrongConfirmations < 1 -> Decision.REQUIRES_SECOND_STRONG_CONFIRMATION
            else -> Decision.WOULD_ALLOW
        }
    }

    /**
     * Gate de ejecución del sprint: SIEMPRE false. Aunque [decide] devuelva
     * [Decision.WOULD_ALLOW], la llamada real no se realiza hasta que se habilite
     * deliberadamente (cambio de código + autorización del usuario).
     */
    fun isExecutionEnabled(): Boolean = false

    /** True si la decisión jamás debe terminar en una acción de llamada hoy. */
    fun blocksExecution(decision: Decision): Boolean =
        decision != Decision.WOULD_ALLOW || !isExecutionEnabled()
}
