package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Negativas SEGURAS para acciones prohibidas de WhatsApp: cortas, tranquilas,
 * sin pánico. Siempre dicen que NO se tocó nada y ofrecen una alternativa segura.
 * No exponen PII.
 */
object WhatsAppForbiddenActionNarrator {

    private const val SAFE_TAIL =
        "No toqué nada. Puedo leerte los chats, prepararte un borrador o cancelar."

    fun refusal(action: WhatsAppActionType): String {
        val core = when (action) {
            WhatsAppActionType.DELETE_CHAT,
            WhatsAppActionType.ARCHIVE_CHAT,
            WhatsAppActionType.MUTE_CHAT,
            WhatsAppActionType.PIN_CHAT ->
                "No puedo borrar, archivar, silenciar ni fijar chats de WhatsApp."

            WhatsAppActionType.BLOCK_CONTACT,
            WhatsAppActionType.REPORT_CONTACT ->
                "No puedo bloquear ni reportar contactos en WhatsApp."

            WhatsAppActionType.PAYMENT ->
                "No puedo hacer pagos ni transferencias por WhatsApp."

            WhatsAppActionType.SEND_STICKER,
            WhatsAppActionType.SEND_FILE,
            WhatsAppActionType.SEND_PHOTO,
            WhatsAppActionType.FORWARD_MESSAGE,
            WhatsAppActionType.SHARE_LOCATION ->
                "No puedo mandar fotos, archivos ni stickers, reenviar mensajes ni compartir ubicación por WhatsApp."

            WhatsAppActionType.OPEN_SUSPICIOUS_LINK ->
                "No voy a abrir ese enlace por seguridad."

            else ->
                "No puedo hacer esa acción en WhatsApp."
        }
        return "$core $SAFE_TAIL"
    }
}
