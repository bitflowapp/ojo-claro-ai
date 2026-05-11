package com.ojoclaro.android.agent.core.generic

/**
 * Capacidades atómicas que una "acción genérica sobre app de terceros" podría
 * pedir. La policy decide cuáles están permitidas.
 *
 * Reglas:
 *  - Permitidas por default: OPEN_APP, READ_SCREEN (con consentimiento ya
 *    confirmado), SUMMARIZE_SCREEN, GUIDE_USER.
 *  - Bloqueadas por default: TAP_BUTTON, TYPE_INTO_FIELD, SUBMIT_FORM,
 *    NAVIGATE_WITHIN_APP, PAY, GENERIC_AUTOMATION.
 */
enum class GenericAppCapability {
    OPEN_APP,
    READ_SCREEN,
    SUMMARIZE_SCREEN,
    GUIDE_USER,

    TAP_BUTTON,
    TYPE_INTO_FIELD,
    SUBMIT_FORM,
    NAVIGATE_WITHIN_APP,
    PAY,
    GENERIC_AUTOMATION
}
