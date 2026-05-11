package com.ojoclaro.android.agent.core.screen

/**
 * Snapshot inmutable de pantalla, sin PII innecesaria.
 *
 * - text: texto visible bruto (ya viene del AccessibilityService o del OCR).
 * - elements: lista opcional de elementos detectados.
 * - packageName: app que generó la pantalla (puede ser null).
 *
 * No persistir. No mandar al cloud sin pasar por PrivacyGuard.canSendToCloud.
 */
data class ScreenSnapshot(
    val packageName: String? = null,
    val text: String = "",
    val elements: List<ScreenElement> = emptyList(),
    val capturedAtMillis: Long
) {
    val hasText: Boolean
        get() = text.isNotBlank()

    val hasElements: Boolean
        get() = elements.isNotEmpty()
}

/**
 * Elemento detectado dentro de la pantalla. Lo importante para el agent-core es
 * saber si es un botón/acción, su label y si es interactivo. El planner NO
 * accede a botones — solo los lista en el resumen.
 */
data class ScreenElement(
    val label: String,
    val role: ScreenElementRole,
    val isInteractive: Boolean,
    val isPassword: Boolean = false
)

enum class ScreenElementRole {
    HEADING,
    BUTTON,
    TEXT,
    EDIT_FIELD,
    LINK,
    LIST_ITEM,
    UNKNOWN
}
