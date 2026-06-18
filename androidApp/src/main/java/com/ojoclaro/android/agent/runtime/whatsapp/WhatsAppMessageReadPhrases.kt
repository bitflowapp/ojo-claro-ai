package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Modo de lectura de mensajes pedido por el usuario.
 *
 *  - ALL: leer los últimos mensajes visibles (resumen corto).
 *  - LAST: leer solo el último mensaje visible.
 */
enum class WhatsAppMessageReadMode {
    ALL,
    LAST
}

/**
 * Reconocimiento determinista del pedido EXPLÍCITO de leer mensajes de un chat.
 *
 * Importante: solo matchea cuando el usuario pide leer mensajes a propósito.
 * Esto respeta la regla "no leer datos sensibles si el usuario no lo pidió
 * explícitamente": acá el pedido ES explícito.
 *
 * Reglas:
 *  - Set fijo, membership exacta tras normalizar.
 *  - NO matchea la lista de chats ([WhatsAppChatListPhrases]) ni screen
 *    understanding general ([ScreenQueryPhrases]).
 *  - NO matchea acciones (enviar, responder, borrar): solo lectura.
 */
object WhatsAppMessageReadPhrases {

    private val LAST: Set<String> = setOf(
        "ultimo mensaje",
        "el ultimo mensaje",
        "lee el ultimo mensaje",
        "leeme el ultimo mensaje",
        "que dice el ultimo mensaje",
        "cual es el ultimo mensaje",
        "leeme el ultimo",
        "ultimo whatsapp",
        "el ultimo whatsapp",
        "ultimo mensaje de whatsapp",
        "el ultimo mensaje de whatsapp",
        "leeme el ultimo mensaje de whatsapp"
    )

    private val ALL: Set<String> = setOf(
        "que mensajes hay",
        "que mensaje hay",
        "que mensajes tengo",
        "que mensajes aparecen",
        "que mensajes se ven",
        "que mensajes me llegaron",
        "lee los mensajes",
        "leeme los mensajes",
        "lee este chat",
        "leeme este chat",
        "lee el chat",
        "leeme el chat",
        "leeme esta conversacion",
        "lee esta conversacion",
        "leeme este whatsapp",
        "lee este whatsapp",
        "que dice el chat",
        "que dice este chat",
        "que dice esta conversacion",
        "leeme los ultimos mensajes",
        "lee los ultimos mensajes"
    )

    // BUG 1 — sufijo de app ("de wp"/"de whatsapp"/"en whatsapp") al final NO debe
    // romper el match ("leeme los mensajes de wp" → "leeme los mensajes"). Local a la
    // lectura de mensajes (no toca la lectura en voz alta, que usa el sufijo).
    private val TRAILING_APP = Regex("\\s+(?:de|del|en|por)\\s+whatsapp$")

    fun classify(rawText: String): WhatsAppMessageReadMode? {
        val key = WhatsAppPhraseNormalizer.normalize(rawText)
        if (key.isBlank()) return null
        val keyNoApp = TRAILING_APP.replace(key, "").trim()
        return when {
            key in LAST || keyNoApp in LAST -> WhatsAppMessageReadMode.LAST
            key in ALL || keyNoApp in ALL -> WhatsAppMessageReadMode.ALL
            else -> null
        }
    }
}
