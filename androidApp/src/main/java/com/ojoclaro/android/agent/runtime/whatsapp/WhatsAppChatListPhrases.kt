package com.ojoclaro.android.agent.runtime.whatsapp

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
 *
 * La normalización canoniza los alias hablados ("wp", "guasap", ...) a
 * "whatsapp" vía [WhatsAppPhraseNormalizer], así "qué contactos hay en wp"
 * matchea igual que "qué contactos hay en whatsapp".
 */
object WhatsAppChatListPhrases {

    private val PHRASES: Set<String> = setOf(
        "que chats ves",
        "que chats hay",
        "que chats tengo",
        "que chats aparecen",
        "que chats aparecen en pantalla",
        "que chat ves",
        // V1.10.4b — formas naturales que la QA real usó y faltaban.
        "lee los chats",
        "lee los chats de whatsapp",
        "lee los chat",
        "leer los chats",
        "lee mis chats",
        "lee mis chats de whatsapp",
        "chats visibles",
        "los chats visibles",
        "decime los chats visibles",
        "decime que chats hay",
        "leeme los chats",
        "leeme los chat",
        "leeme mis chats",
        "leeme la lista de chats",
        "leeme los chats visibles",
        "leeme los chat que aparecen",
        "leeme los chats que aparecen",
        "leeme los chat que aparecen en pantalla",
        "leeme los chats que aparecen en pantalla",
        "que conversaciones aparecen",
        "que conversaciones aparecen en pantalla",
        "que conversaciones hay",
        "leeme las conversaciones",
        "que chats ves en whatsapp",
        "que contactos aparecen",
        "que contactos hay",
        "que contactos aparecen en whatsapp",
        "que contactos hay en whatsapp"
    )

    // Sufijo de app al final ("de/del/en/por whatsapp") NO debe romper el match:
    // "leé los chats del guasap" → "lee los chats del whatsapp" → "lee los chats".
    // Mismo patrón que WhatsAppMessageReadPhrases (la lista de chats no lo tenía).
    private val TRAILING_APP = Regex("\\s+(?:de|del|en|por)\\s+whatsapp$")

    fun isChatListCommand(rawText: String): Boolean {
        val key = WhatsAppPhraseNormalizer.normalize(rawText)
        if (key.isBlank()) return false
        if (key in PHRASES) return true
        return TRAILING_APP.replace(key, "").trim() in PHRASES
    }
}
