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
                "Por seguridad no puedo hacer pagos ni transferencias por WhatsApp."

            WhatsAppActionType.SHARE_LOCATION ->
                "Por seguridad no puedo compartir ubicación por WhatsApp."

            WhatsAppActionType.SEND_PHOTO ->
                "Por seguridad no puedo mandar fotos por WhatsApp."

            WhatsAppActionType.SEND_FILE ->
                "Por seguridad no puedo mandar archivos por WhatsApp."

            WhatsAppActionType.SEND_STICKER ->
                "Por seguridad no puedo mandar stickers por WhatsApp."

            WhatsAppActionType.FORWARD_MESSAGE ->
                "Por seguridad no puedo reenviar mensajes por WhatsApp."

            WhatsAppActionType.OPEN_SUSPICIOUS_LINK ->
                "No voy a abrir ese enlace por seguridad."

            else ->
                "No puedo hacer esa acción en WhatsApp."
        }
        return "$core $SAFE_TAIL"
    }
}
