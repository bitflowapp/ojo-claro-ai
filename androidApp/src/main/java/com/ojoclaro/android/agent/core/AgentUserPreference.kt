package com.ojoclaro.android.agent.core

/**
 * Preferencia del usuario, declarada explícitamente o aceptada tras pregunta.
 *
 * El source es importante: una preferencia INFERRED nunca debería usarse sin
 * haber sido confirmada (source pasa a USER_CONFIRMED). Si en algún momento se
 * usa una INFERRED, eso es bug.
 */
data class AgentUserPreference(
    val key: String,
    val value: String,
    val source: AgentPreferenceSource,
    val updatedAtMillis: Long
) {
    init {
        require(key.isNotBlank()) { "preference key must not be blank" }
        require(value.length <= 240) { "preference value too long" }
    }

    val isApplicable: Boolean
        get() = source != AgentPreferenceSource.INFERRED_PENDING_CONFIRMATION
}

object AgentPreferenceKeys {
    const val RESPONSE_LENGTH = "response.length"
    const val RESPONSE_STYLE = "response.style"
    const val PRIMARY_CONTACT = "contact.primary"
    const val HOME_DESTINATION = "place.home"
    const val ACCESSIBILITY_MODE = "mode.accessibility"
    const val CONFIRMATION_STRICTNESS = "confirmation.strictness"
    const val LEARNING_OPT_IN = "learning.optIn"
}
