package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Respuesta del WhatsAppVisibleChatsReader.
 *
 * Sealed para forzar manejo explícito.
 *
 *  - [NotAChatListCommand]: el texto no era un pedido de lectura de chats.
 *    El caller debe IGNORAR este resultado y seguir el flujo normal.
 *  - [NotInWhatsApp]: el comando se entendió, pero el snapshot indica que el
 *    usuario no está en WhatsApp. Pedimos abrir la app.
 *  - [StateNotConfident]: no hay snapshot confiable. Pedimos abrir la lista
 *    principal de chats y reintentar.
 *  - [InsideChat]: el usuario está dentro de un chat. Devolvemos un mensaje
 *    honesto explicando que no leemos mensajes completos.
 *  - [Listed]: hay chats visibles. Devolvemos sus nombres (sin contenido).
 */
sealed class WhatsAppChatListResponse {

    object NotAChatListCommand : WhatsAppChatListResponse()

    data class NotInWhatsApp(
        val spokenText: String
    ) : WhatsAppChatListResponse()

    data class StateNotConfident(
        val spokenText: String
    ) : WhatsAppChatListResponse()

    data class InsideChat(
        val spokenText: String
    ) : WhatsAppChatListResponse()

    data class Listed(
        val chats: List<WhatsAppVisibleChat>,
        val spokenText: String
    ) : WhatsAppChatListResponse() {
        init {
            require(chats.isNotEmpty()) { "Listed must have at least one chat" }
            require(chats.size <= WhatsAppChatListDetector.MAX_VISIBLE_CHATS) {
                "Listed exceeds MAX_VISIBLE_CHATS"
            }
        }
    }

    /** Caso especial: comando se entendió pero NO encontramos chats visibles. */
    data class NoChatsVisible(
        val spokenText: String
    ) : WhatsAppChatListResponse()
}
