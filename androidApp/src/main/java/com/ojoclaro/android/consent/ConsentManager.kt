package com.ojoclaro.android.consent

import java.util.UUID

/**
 * Capa central de consentimiento para acciones sensibles.
 *
 * Diseño:
 *  - Stateless por diseño: la UI/ViewModel guarda el `currentPending`. ConsentManager solo
 *    clasifica acciones, crea pendings y verifica confirmaciones. Esto lo mantiene puro y testeable.
 *  - No habla, no toca Intents, no persiste nada.
 *  - Las confirmaciones fuertes (LONG_PRESS, BIOMETRIC) están preparadas pero rechazan en el MVP
 *    para no agregar BiometricPrompt antes de tiempo. Si el día de mañana se implementan,
 *    confirmStrong() devolverá Confirmed cuando reciba la prueba de la UI.
 *
 * Un usuario ciego espera:
 *  - Acciones cotidianas no piden confirmación extra (hablar, leer texto, abrir cámara).
 *  - Acciones privadas piden una sola confirmación corta.
 *  - Acciones muy sensibles (banco, contraseñas) no se ejecutan sin seguridad real.
 *  - "Confirmar" sin pendiente nunca queda en silencio.
 *  - "Cancelar" siempre limpia.
 */
class ConsentManager(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {

    /**
     * Decide qué nivel de confirmación necesita una acción.
     *
     * Reglas base — pueden ajustarse con feedback de usuarios:
     *  - READ_PASSWORD_FIELD nunca se confirma. Se rechaza directamente.
     *  - READ_BANKING_SCREEN requiere biometría (no implementado todavía → rechazado).
     *  - READ_VISIBLE_MESSAGE pide confirmación simple (puede contener mensajes privados).
     *  - COMPOSE_MESSAGE pide confirmación simple. Nunca se envía solo.
     *  - OPEN_EXTERNAL_APP normalmente NONE; el sistema operativo ya muestra la app receptora.
     *  - UNKNOWN_SENSITIVE: por defecto, exigir confirmación simple.
     */
    fun classify(type: SensitiveActionType): ConsentLevel = when (type) {
        SensitiveActionType.READ_PASSWORD_FIELD -> ConsentLevel.NONE
        SensitiveActionType.READ_BANKING_SCREEN -> ConsentLevel.BIOMETRIC_CONFIRMATION
        SensitiveActionType.READ_VISIBLE_MESSAGE -> ConsentLevel.SIMPLE_CONFIRMATION
        SensitiveActionType.COMPOSE_MESSAGE -> ConsentLevel.SIMPLE_CONFIRMATION
        SensitiveActionType.OPEN_EXTERNAL_APP -> ConsentLevel.NONE
        SensitiveActionType.SAVE_MEMORY -> ConsentLevel.SIMPLE_CONFIRMATION
        SensitiveActionType.DELETE_MEMORY -> ConsentLevel.SIMPLE_CONFIRMATION
        SensitiveActionType.CLEAR_MEMORY -> ConsentLevel.SIMPLE_CONFIRMATION
        SensitiveActionType.UNKNOWN_SENSITIVE -> ConsentLevel.SIMPLE_CONFIRMATION
    }

    /**
     * Crea una acción pendiente o devuelve un Decision de rechazo si no se puede ejecutar.
     */
    fun requestAction(
        type: SensitiveActionType,
        spokenExplanation: String,
        payload: Map<String, String> = emptyMap(),
        nowMillis: Long
    ): ConsentDecision {
        if (type == SensitiveActionType.READ_PASSWORD_FIELD) {
            return ConsentDecision.Rejected(
                spokenText = ConsentPhrases.READ_PASSWORD_FIELD_REJECTED
            )
        }

        val level = classify(type)

        if (level == ConsentLevel.NONE) {
            return ConsentDecision.AllowedImmediately(spokenText = spokenExplanation)
        }

        if (level == ConsentLevel.LONG_PRESS_CONFIRMATION ||
            level == ConsentLevel.BIOMETRIC_CONFIRMATION
        ) {
            // Strong consent no implementado todavía. Rechazamos con mensaje claro
            // para no fingir seguridad que no tenemos.
            return ConsentDecision.Rejected(
                spokenText = if (type == SensitiveActionType.READ_BANKING_SCREEN) {
                    ConsentPhrases.READ_BANKING_SCREEN
                } else {
                    ConsentPhrases.STRONG_CONFIRMATION_NOT_AVAILABLE
                }
            )
        }

        val pending = PendingSensitiveAction(
            id = idFactory(),
            type = type,
            spokenExplanation = spokenExplanation,
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + ttlMillis,
            requiresConsentLevel = level,
            payload = payload
        )

        return ConsentDecision.NeedsConfirmation(
            spokenText = spokenExplanation,
            pending = pending
        )
    }

    /**
     * Confirma una acción pendiente. Devuelve la acción si es confirmable, o un mensaje claro
     * si no hay pendiente, está vencida, o requiere confirmación fuerte.
     */
    fun confirmSimple(
        pending: PendingSensitiveAction?,
        nowMillis: Long
    ): ConsentDecision {
        if (pending == null) {
            return ConsentDecision.NoPending(
                spokenText = ConsentPhrases.NO_PENDING_CONFIRMATION
            )
        }
        if (pending.isExpired(nowMillis)) {
            return ConsentDecision.Expired(
                spokenText = ConsentPhrases.EXPIRED_ACTION
            )
        }
        if (pending.requiresConsentLevel != ConsentLevel.SIMPLE_CONFIRMATION) {
            // Si el día de mañana llega un long-press o biometric desde la UI,
            // hay que llamar al método correspondiente. Acá rechazamos para
            // no degradar seguridad por error.
            return ConsentDecision.Rejected(
                spokenText = ConsentPhrases.STRONG_CONFIRMATION_NOT_AVAILABLE
            )
        }
        return ConsentDecision.Confirmed(pending = pending)
    }

    fun cancel(pending: PendingSensitiveAction?): ConsentDecision {
        if (pending == null) {
            return ConsentDecision.NoPending(
                spokenText = ConsentPhrases.NO_PENDING_CANCELLATION
            )
        }
        return ConsentDecision.Cancelled(
            spokenText = ConsentPhrases.ACTION_CANCELLED
        )
    }

    fun expireIfNeeded(
        pending: PendingSensitiveAction?,
        nowMillis: Long
    ): PendingSensitiveAction? {
        if (pending == null) return null
        return if (pending.isExpired(nowMillis)) null else pending
    }

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 2 * 60 * 1000L
    }
}

sealed class ConsentDecision {
    /** Acción aprobada sin confirmación porque no es sensible o el contexto la cubre. */
    data class AllowedImmediately(val spokenText: String) : ConsentDecision()

    /** Acción guardada como pendiente; UI debe guardar el pending y hablar el explanation. */
    data class NeedsConfirmation(
        val spokenText: String,
        val pending: PendingSensitiveAction
    ) : ConsentDecision()

    /** Confirmación simple aceptada. La UI puede ejecutar la acción. */
    data class Confirmed(val pending: PendingSensitiveAction) : ConsentDecision()

    /** El usuario canceló o no había nada pendiente. */
    data class Cancelled(val spokenText: String) : ConsentDecision()

    /** No había acción pendiente al confirmar/cancelar. */
    data class NoPending(val spokenText: String) : ConsentDecision()

    /** La acción pendiente venció. */
    data class Expired(val spokenText: String) : ConsentDecision()

    /** Acción rechazada por seguridad. NO se va a ejecutar. */
    data class Rejected(val spokenText: String) : ConsentDecision()
}
