package com.ojoclaro.android.agent.runtime.whatsapp

/** Nivel de riesgo de una acción de WhatsApp. */
enum class WhatsAppRiskLevel { SAFE, MEDIUM, HIGH, FORBIDDEN }

/** Confirmación que exige una acción antes de ejecutarse. */
enum class WhatsAppConfirmationLevel {
    /** Lectura/navegación: no requiere confirmación. */
    NONE,
    /** Reversible (abrir/borrador): basta un sí. */
    SINGLE,
    /** Peligrosa: doble confirmación con frase FUERTE exacta. */
    STRONG_DOUBLE
}

/**
 * Catálogo declarativo de TODO lo que Estela podría hacer en WhatsApp, con su
 * riesgo, confianza de destino requerida, confirmación, si necesita destino y
 * un marcador de log sin PII.
 *
 * Cada acción peligrosa (HIGH) pasa por [WhatsAppActionCatalog.gate] antes de
 * cualquier toque. Las FORBIDDEN se bloquean siempre, por diseño.
 */
enum class WhatsAppActionType(
    val riskLevel: WhatsAppRiskLevel,
    val requiredConfirmation: WhatsAppConfirmationLevel,
    val destinationRequired: Boolean,
    val requiredConfidence: WhatsAppDestinationConfidence,
    val logMarker: String
) {
    // --- SAFE / read-only ---
    READ_CHAT_LIST(WhatsAppRiskLevel.SAFE, WhatsAppConfirmationLevel.NONE, false, WhatsAppDestinationConfidence.LOW, "WA_ACT_READ_CHATS"),
    READ_MESSAGES(WhatsAppRiskLevel.SAFE, WhatsAppConfirmationLevel.NONE, false, WhatsAppDestinationConfidence.LOW, "WA_ACT_READ_MESSAGES"),
    DESCRIBE_STATE(WhatsAppRiskLevel.SAFE, WhatsAppConfirmationLevel.NONE, false, WhatsAppDestinationConfidence.LOW, "WA_ACT_DESCRIBE_STATE"),
    REPEAT(WhatsAppRiskLevel.SAFE, WhatsAppConfirmationLevel.NONE, false, WhatsAppDestinationConfidence.LOW, "WA_ACT_REPEAT"),
    HELP(WhatsAppRiskLevel.SAFE, WhatsAppConfirmationLevel.NONE, false, WhatsAppDestinationConfidence.LOW, "WA_ACT_HELP"),
    LIST_VISIBLE_OPTIONS(WhatsAppRiskLevel.SAFE, WhatsAppConfirmationLevel.NONE, false, WhatsAppDestinationConfidence.LOW, "WA_ACT_LIST_OPTIONS"),

    // --- MEDIUM ---
    OPEN_CHAT(WhatsAppRiskLevel.MEDIUM, WhatsAppConfirmationLevel.NONE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_OPEN_CHAT"),
    SCROLL(WhatsAppRiskLevel.MEDIUM, WhatsAppConfirmationLevel.NONE, false, WhatsAppDestinationConfidence.LOW, "WA_ACT_SCROLL"),
    PREPARE_DRAFT(WhatsAppRiskLevel.MEDIUM, WhatsAppConfirmationLevel.SINGLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_PREPARE_DRAFT"),
    CLEAR_DRAFT(WhatsAppRiskLevel.MEDIUM, WhatsAppConfirmationLevel.NONE, false, WhatsAppDestinationConfidence.LOW, "WA_ACT_CLEAR_DRAFT"),

    // --- HIGH (cada una gateada por su feature flag) ---
    SEND_MESSAGE(WhatsAppRiskLevel.HIGH, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_SEND_MESSAGE"),
    RECORD_AUDIO(WhatsAppRiskLevel.HIGH, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_RECORD_AUDIO"),
    SEND_AUDIO(WhatsAppRiskLevel.HIGH, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_SEND_AUDIO"),
    CALL(WhatsAppRiskLevel.HIGH, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_CALL"),
    VIDEO_CALL(WhatsAppRiskLevel.HIGH, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_VIDEO_CALL"),

    // --- FORBIDDEN (siempre bloqueadas, sin flag que las habilite) ---
    DELETE_CHAT(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_DELETE_CHAT"),
    ARCHIVE_CHAT(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_ARCHIVE_CHAT"),
    BLOCK_CONTACT(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_BLOCK_CONTACT"),
    REPORT_CONTACT(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_REPORT_CONTACT"),
    MUTE_CHAT(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.SINGLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_MUTE_CHAT"),
    PIN_CHAT(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.SINGLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_PIN_CHAT"),
    PAYMENT(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_PAYMENT"),
    SEND_STICKER(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_SEND_STICKER"),
    SEND_FILE(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_SEND_FILE"),
    SEND_PHOTO(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_SEND_PHOTO"),
    FORWARD_MESSAGE(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_FORWARD"),
    SHARE_LOCATION(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, true, WhatsAppDestinationConfidence.HIGH, "WA_ACT_SHARE_LOCATION"),
    OPEN_SUSPICIOUS_LINK(WhatsAppRiskLevel.FORBIDDEN, WhatsAppConfirmationLevel.STRONG_DOUBLE, false, WhatsAppDestinationConfidence.LOW, "WA_ACT_OPEN_LINK");

    val isDangerous: Boolean get() = riskLevel == WhatsAppRiskLevel.HIGH
    val isForbidden: Boolean get() = riskLevel == WhatsAppRiskLevel.FORBIDDEN
}

/** Por qué una acción quedó bloqueada en la compuerta (sin PII). */
enum class WhatsAppBlockedReason {
    FORBIDDEN_ACTION,
    FEATURE_DISABLED,
    NO_DESTINATION,
    LOW_CONFIDENCE
}

/** Resultado de la compuerta: ¿puede arrancar la acción y qué confirmación pide? */
sealed class WhatsAppActionGate {
    abstract val action: WhatsAppActionType

    data class Blocked(
        override val action: WhatsAppActionType,
        val reason: WhatsAppBlockedReason
    ) : WhatsAppActionGate()

    /** Puede arrancar pero exige confirmación (SINGLE o STRONG_DOUBLE). */
    data class NeedsConfirmation(
        override val action: WhatsAppActionType,
        val level: WhatsAppConfirmationLevel
    ) : WhatsAppActionGate()

    /** Acción segura: puede ejecutarse sin confirmación. */
    data class AllowedReadOnly(override val action: WhatsAppActionType) : WhatsAppActionGate()
}

object WhatsAppActionCatalog {

    /**
     * Compuerta única para empezar cualquier acción. NO ejecuta nada; sólo
     * decide si la acción puede arrancar y qué confirmación necesita.
     *
     * Orden de bloqueo (de más fuerte a más débil):
     *  1. FORBIDDEN → siempre bloqueada (no hay flag que la habilite).
     *  2. HIGH con su feature flag en false → bloqueada.
     *  3. Necesita destino y no hay → bloqueada.
     *  4. Necesita confianza ALTA de destino y no la hay → bloqueada.
     *  5. Si no requiere confirmación → permitida (read-only).
     *  6. Si no → pide la confirmación que corresponde.
     */
    fun gate(
        action: WhatsAppActionType,
        flags: WhatsAppFeatureFlags,
        hasDestination: Boolean,
        destinationConfidence: WhatsAppDestinationConfidence?
    ): WhatsAppActionGate {
        if (action.isForbidden) {
            return WhatsAppActionGate.Blocked(action, WhatsAppBlockedReason.FORBIDDEN_ACTION)
        }
        if (action.isDangerous && !flags.isEnabled(action)) {
            return WhatsAppActionGate.Blocked(action, WhatsAppBlockedReason.FEATURE_DISABLED)
        }
        if (action.destinationRequired && !hasDestination) {
            return WhatsAppActionGate.Blocked(action, WhatsAppBlockedReason.NO_DESTINATION)
        }
        if (action.destinationRequired &&
            action.requiredConfidence == WhatsAppDestinationConfidence.HIGH &&
            destinationConfidence != WhatsAppDestinationConfidence.HIGH
        ) {
            return WhatsAppActionGate.Blocked(action, WhatsAppBlockedReason.LOW_CONFIDENCE)
        }
        return if (action.requiredConfirmation == WhatsAppConfirmationLevel.NONE) {
            WhatsAppActionGate.AllowedReadOnly(action)
        } else {
            WhatsAppActionGate.NeedsConfirmation(action, action.requiredConfirmation)
        }
    }

    fun actionsOf(level: WhatsAppRiskLevel): List<WhatsAppActionType> =
        WhatsAppActionType.values().filter { it.riskLevel == level }
}
