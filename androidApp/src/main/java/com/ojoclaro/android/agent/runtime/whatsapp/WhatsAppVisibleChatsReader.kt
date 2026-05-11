package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider

/**
 * Reader del WhatsApp Visible Chats v1.
 *
 * Reglas hard:
 *  - Verbal-only. No abre chats, no toca botones, no escribe.
 *  - Lee solo NOMBRES visibles. Nunca previews, nunca contenido del chat.
 *  - Si está dentro de un chat, devuelve un mensaje fijo aclarando que NO lee
 *    mensajes completos. NO enumera el contenido.
 *  - Plantillas FIJAS de respuesta. La única parte dinámica es la lista
 *    [WhatsAppVisibleChat.displayName] (ya filtrada) — y eso no es contenido
 *    privado: es el nombre del contacto/grupo que el dueño del teléfono guardó.
 *  - No persiste. No envía a red. No usa LLM.
 *  - Cap en 5 chats por respuesta.
 */
class WhatsAppVisibleChatsReader(
    private val provider: ScreenContextProvider,
    private val screenDetector: WhatsAppScreenDetector = WhatsAppScreenDetector(),
    private val chatListDetector: WhatsAppChatListDetector = WhatsAppChatListDetector(),
    private val isAccessibilityReady: () -> Boolean = { true }
) {

    fun handle(rawText: String): WhatsAppChatListResponse {
        if (!WhatsAppChatListPhrases.isChatListCommand(rawText)) {
            return WhatsAppChatListResponse.NotAChatListCommand
        }

        if (!isAccessibilityReady()) {
            return WhatsAppChatListResponse.NotInWhatsApp(
                spokenText = NEEDS_ACCESSIBILITY_TEXT
            )
        }

        val snapshot = runCatching { provider.current() }.getOrNull()
        val state = screenDetector.detect(snapshot)

        if (state.isUnknown) {
            return WhatsAppChatListResponse.StateNotConfident(
                spokenText = "No puedo confirmar la lista de chats. " +
                    "Abrí WhatsApp en la pantalla principal de chats y decime otra vez."
            )
        }

        if (!state.isOpen) {
            return WhatsAppChatListResponse.NotInWhatsApp(
                spokenText = "No estás en WhatsApp. Abrílo primero y volvé a pedirme."
            )
        }

        if (state.isInChat) {
            return WhatsAppChatListResponse.InsideChat(
                spokenText = "Estás dentro de un chat. No leo mensajes completos sin que me lo pidas."
            )
        }

        // Estamos en WhatsApp y NO en un chat → probablemente la lista principal.
        val chats = chatListDetector.extractChats(snapshot)
        if (chats.isEmpty()) {
            return WhatsAppChatListResponse.NoChatsVisible(
                spokenText = "No puedo confirmar la lista de chats. " +
                    "Abrí WhatsApp en la pantalla principal de chats y decime otra vez."
            )
        }

        return WhatsAppChatListResponse.Listed(
            chats = chats,
            spokenText = buildListedText(chats)
        )
    }

    private fun buildListedText(chats: List<WhatsAppVisibleChat>): String {
        val names = chats.map { it.displayName }
        val joined = when (names.size) {
            1 -> names.first()
            else -> {
                val head = names.dropLast(1).joinToString(", ")
                val tail = names.last()
                "$head y $tail"
            }
        }
        return "Veo estos chats visibles: $joined. No leo mensajes completos."
    }

    companion object {
        const val NEEDS_ACCESSIBILITY_TEXT: String =
            "Para que pueda leer la lista de chats necesito el servicio de Accesibilidad activo. " +
                "Activá Ojo Claro en Ajustes de Accesibilidad."
    }
}
