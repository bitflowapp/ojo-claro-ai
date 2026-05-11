package com.ojoclaro.android.agent.core.emergency

/**
 * Contacto de emergencia configurado por el usuario.
 *
 * No persiste por sí mismo. Esta estructura solo describe el contacto en RAM.
 * La persistencia real va por SafeContactMemory / PersonalAgentMemory.
 */
data class EmergencyContact(
    val displayName: String,
    val phoneE164: String,
    val relation: String? = null
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(phoneE164.isNotBlank()) { "phoneE164 must not be blank" }
    }
}
