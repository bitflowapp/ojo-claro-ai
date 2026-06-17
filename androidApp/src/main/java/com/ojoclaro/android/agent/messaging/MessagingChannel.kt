package com.ojoclaro.android.agent.messaging

/**
 * V1.12 — capa común de canales de mensajería.
 *
 * Diseño deliberadamente MÍNIMO: WhatsApp ya tiene flujos probados en piloto
 * (V1.2→V1.11) y NO se migran acá; el canal WHATSAPP existe para tipado,
 * logs y migración futura. El router clasifica SOLO frases con marca
 * explícita de Instagram: sin marca de canal devuelve null y todas las rutas
 * legacy de WhatsApp siguen intactas (contrato "no robar rutas").
 */
enum class MessagingChannel { WHATSAPP, INSTAGRAM }

/**
 * Intenciones de mensajería multi-canal. Mismas clases de seguridad que la
 * matriz V1.11:
 *  - OPEN_*: SAFE (abrir apps/pantallas no envía ni confirma nada);
 *  - SEND_TEXT_PENDING_CONFIRMATION: CONFIRM_REQUIRED con confirmación
 *    FUERTE ("mandalo"/"enviá"/"confirmo"); "sí"/"dale" JAMÁS envían texto;
 *  - START_VIDEO_CALL_PENDING_CONFIRMATION: CONFIRM_REQUIRED; "sí"/"dale"
 *    alcanzan porque iniciar una llamada es reversible (se corta);
 *  - START_AUDIO_FLOW: SENSITIVE_GUIDED (guía del gesto; grabar exige
 *    mantener apretado y los gestos automatizados están prohibidos).
 */
enum class MessagingTaskIntent {
    OPEN_MESSAGING_APP,
    OPEN_CHAT,
    SEND_TEXT_PENDING_CONFIRMATION,
    CONFIRM_SEND_TEXT,
    START_VIDEO_CALL_PENDING_CONFIRMATION,
    CONFIRM_VIDEO_CALL_TAP,
    START_AUDIO_FLOW,
    CANCEL_PENDING_ACTION
}

/**
 * Pedido clasificado. [contactQuery] y [messageText] vienen del texto
 * hablado: el contenido del mensaje JAMÁS se loguea (solo longitudes).
 * [inboxRequested] distingue "abrí instagram" de "abrí los mensajes".
 */
data class MessagingTaskRequest(
    val channel: MessagingChannel,
    val intent: MessagingTaskIntent,
    val contactQuery: String? = null,
    val messageText: String? = null,
    val inboxRequested: Boolean = false
)

/** Respuesta a una pregunta pendiente de mensajería (texto o videollamada). */
enum class MessagingReplyOutcome { CONFIRM, CANCEL, WEAK_REJECTED, OTHER }
