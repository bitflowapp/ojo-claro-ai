package com.ojoclaro.android.consent

/**
 * Cuán fuerte tiene que ser la confirmación del usuario para una acción sensible.
 *
 * NONE: sin confirmación extra — la acción ya es segura por defecto.
 * SIMPLE_CONFIRMATION: el usuario dice "confirmar" o toca un botón accesible.
 * LONG_PRESS_CONFIRMATION: requiere mantener presionado un botón (preparado, no implementado todavía).
 * BIOMETRIC_CONFIRMATION: requiere biometría del sistema (preparado, no implementado todavía).
 *
 * Las dos confirmaciones fuertes están descritas en la arquitectura pero no se ejecutan en el MVP
 * para evitar agregar BiometricPrompt antes de tiempo. Si una acción las requiere y todavía no
 * están implementadas, el ConsentManager debe rechazar la acción con un mensaje claro.
 */
enum class ConsentLevel {
    NONE,
    SIMPLE_CONFIRMATION,
    LONG_PRESS_CONFIRMATION,
    BIOMETRIC_CONFIRMATION
}
