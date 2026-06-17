package com.ojoclaro.android.agent.messaging

import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.instagram.InstagramDirectPhrases

/**
 * V1.12 — router de tareas de mensajería multi-canal.
 *
 * Contrato de convivencia con el piloto:
 *  - SOLO clasifica frases con marca explícita de Instagram ("por
 *    instagram", "en insta", "por dm"...). Sin marca → null, y la frase
 *    sigue su ruta legacy (WhatsApp V1.2→V1.11 queda intacto: ese flujo es
 *    el "adapter" WHATSAPP existente y probado en piloto).
 *  - Pagos/tarjetas JAMÁS se mezclan con mensajería: si la frase es de
 *    pagos, este router devuelve null y la guía segura de V1.11 responde
 *    (incluida la negativa "ni con confirmación" para datos sensibles).
 *  - Clasificar JAMÁS ejecuta nada: devuelve un pedido tipado y el caller
 *    aplica la política de confirmación correspondiente.
 */
object MessagingTaskRouter {

    fun classify(rawText: String): MessagingTaskRequest? {
        if (rawText.isBlank()) return null
        if (!InstagramDirectPhrases.mentionsInstagram(rawText)) return null
        if (PaymentGuidePhrases.classify(rawText) != null) return null

        InstagramDirectPhrases.parseVideoCall(rawText)?.let { request ->
            return MessagingTaskRequest(
                channel = MessagingChannel.INSTAGRAM,
                intent = MessagingTaskIntent.START_VIDEO_CALL_PENDING_CONFIRMATION,
                contactQuery = request.contactQuery
            )
        }

        if (InstagramDirectPhrases.isAudioRequest(rawText)) {
            return MessagingTaskRequest(
                channel = MessagingChannel.INSTAGRAM,
                intent = MessagingTaskIntent.START_AUDIO_FLOW
            )
        }

        InstagramDirectPhrases.parseSendText(rawText)?.let { request ->
            return MessagingTaskRequest(
                channel = MessagingChannel.INSTAGRAM,
                intent = MessagingTaskIntent.SEND_TEXT_PENDING_CONFIRMATION,
                contactQuery = request.contactQuery,
                messageText = request.messageText
            )
        }

        InstagramDirectPhrases.parseOpenChat(rawText)?.let { contact ->
            return MessagingTaskRequest(
                channel = MessagingChannel.INSTAGRAM,
                intent = MessagingTaskIntent.OPEN_CHAT,
                contactQuery = contact
            )
        }

        if (InstagramDirectPhrases.isOpenInboxCommand(rawText)) {
            return MessagingTaskRequest(
                channel = MessagingChannel.INSTAGRAM,
                intent = MessagingTaskIntent.OPEN_MESSAGING_APP,
                inboxRequested = true
            )
        }

        if (InstagramDirectPhrases.isOpenAppCommand(rawText)) {
            return MessagingTaskRequest(
                channel = MessagingChannel.INSTAGRAM,
                intent = MessagingTaskIntent.OPEN_MESSAGING_APP
            )
        }

        return null
    }

    /**
     * Clasifica la RESPUESTA a un envío de texto pendiente. El contrato del
     * piloto no cambia entre canales: "sí"/"dale" → WEAK_REJECTED, jamás
     * envían; solo "mandalo"/"enviá"/"confirmo" (y variantes "sí, mandalo")
     * confirman; "no"/"cancelar" siempre cancelan.
     */
    fun classifyTextSendReply(rawText: String): MessagingReplyOutcome =
        when (InstagramDirectPhrases.classifyTextSendReply(rawText)) {
            InstagramDirectPhrases.ReplyKind.CONFIRM -> MessagingReplyOutcome.CONFIRM
            InstagramDirectPhrases.ReplyKind.CANCEL -> MessagingReplyOutcome.CANCEL
            InstagramDirectPhrases.ReplyKind.WEAK_REJECTED -> MessagingReplyOutcome.WEAK_REJECTED
            InstagramDirectPhrases.ReplyKind.OTHER -> MessagingReplyOutcome.OTHER
        }

    /** Respuesta al "¿toco la videollamada?": el toque es reversible, por eso
     *  "sí"/"dale"/"tocá"/"confirmo" confirman y "no" cancela. */
    fun classifyVideoTapReply(rawText: String): MessagingReplyOutcome =
        when (InstagramDirectPhrases.classifyVideoTapReply(rawText)) {
            InstagramDirectPhrases.ReplyKind.CONFIRM -> MessagingReplyOutcome.CONFIRM
            InstagramDirectPhrases.ReplyKind.CANCEL -> MessagingReplyOutcome.CANCEL
            InstagramDirectPhrases.ReplyKind.WEAK_REJECTED -> MessagingReplyOutcome.WEAK_REJECTED
            InstagramDirectPhrases.ReplyKind.OTHER -> MessagingReplyOutcome.OTHER
        }
}
