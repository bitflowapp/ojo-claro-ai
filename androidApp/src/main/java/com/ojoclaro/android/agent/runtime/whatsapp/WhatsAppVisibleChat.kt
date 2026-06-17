package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Un chat visible en la lista de chats de WhatsApp.
 *
 * Solo contiene el nombre/título tal cual aparece en pantalla. NUNCA contiene
 * preview de mensajes, hora del último mensaje, contador de no leídos, ni
 * cualquier otro contenido privado.
 *
 * No se persiste. No se serializa.
 */
data class WhatsAppVisibleChat(
    val displayName: String
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(displayName.length <= MAX_NAME_LENGTH) {
            "displayName too long; should be filtered before construction"
        }
    }

    companion object {
        const val MAX_NAME_LENGTH: Int = 40
    }
}
