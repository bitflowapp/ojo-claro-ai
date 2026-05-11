package com.ojoclaro.android.agent.runtime.whatsapp

import java.text.Normalizer

/**
 * Reconocimiento determinista del comando "leeme los chats visibles".
 *
 * Reglas estrictas:
 *  - Set fijo de frases. Set-membership exacta (después de normalizar).
 *  - NUNCA matchea REPEAT_LAST ("repetí", "que dijiste", etc.).
 *  - NUNCA matchea Screen Understanding general ("qué hay en pantalla",
 *    "dónde estoy", "leeme lo importante", etc.).
 *  - NUNCA matchea acciones legacy de WhatsApp ("abrí WhatsApp", "mandale a Marco", etc.).
 *  - NUNCA matchea stop/cancel/confirm/help.
 */
object WhatsAppChatListPhrases {

    private val PHRASES: Set<String> = setOf(
        "que chats ves",
        "que chats hay",
        "que chats aparecen",
        "que chat ves",
        "leeme los chats",
        "leeme mis chats",
        "leeme los chats visibles",
        "que conversaciones aparecen",
        "que conversaciones hay",
        "que contactos aparecen en whatsapp",
        "que contactos hay en whatsapp"
    )

    fun isChatListCommand(rawText: String): Boolean {
        val key = normalize(rawText)
        if (key.isBlank()) return false
        return key in PHRASES
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace(Regex("[¿?¡!.,;:]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
