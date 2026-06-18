package com.ojoclaro.android.agent.runtime.instagram

import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCallPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVoiceSendPhrases
import com.ojoclaro.android.outdoor.OutdoorDestinationReply
import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.12 — frases de Instagram Direct.
 *
 * Reglas duras (mismas que WhatsApp, mismo lexicon de confirmación):
 *  - detectar un pedido JAMÁS toca nada;
 *  - texto: confirmación FUERTE únicamente (reusa el set de
 *    [WhatsAppVoiceSendPhrases]); "sí"/"dale" a secas se rechazan;
 *  - videollamada: "sí"/"dale"/"tocá" confirman el TOQUE ya anunciado
 *    (reversible); "no" siempre cancela;
 *  - una frase SIN marca de canal de Instagram nunca se clasifica acá:
 *    las rutas de WhatsApp del piloto quedan intactas.
 *
 * "insta"/"ig" se canonizan a "instagram" en dos capas (normalizador
 * coloquial del GAS y [foldChannel] local) para que el parser funcione
 * igual en unit tests y en runtime.
 */
object InstagramDirectPhrases {

    data class SendTextRequest(val contactQuery: String, val messageText: String)

    data class VideoCallRequest(val contactQuery: String?)

    /** Tokens que nombran el canal sin ambigüedad. "directo" suelto NO está:
     *  es adjetivo común ("llevame directo a casa" es de Outdoor). */
    private val CHANNEL_TOKENS = setOf("instagram", "insta", "ig", "dm", "md", "direct")

    private val CHANNEL_PHRASES = listOf("mensaje directo", "mensajes directos")

    /** Menciones de canal que se PELAN antes de extraer contacto/mensaje.
     *  Más largas primero para que el resto no deje residuos. */
    private val CHANNEL_MENTION_PHRASES = listOf(
        "por mensaje directo de instagram", "por mensajes directos de instagram",
        "por mensaje directo", "por mensajes directos",
        "en el direct de instagram", "el direct de instagram",
        "por el instagram", "en el instagram", "del instagram", "al instagram",
        "por instagram", "en instagram", "de instagram",
        "por el direct", "en el direct", "al direct", "por direct",
        "por el dm", "en el dm", "al dm", "por dm", "los dm", "el dm",
        "por md", "los md",
        "instagram", "direct", "dm", "md"
    )

    // El voseo reescribe "abrí"→"abrir" ANTES de este parser: cubrimos ambas
    // formas en TODAS las entradas (convención del repo).
    private val OPEN_APP_COMMANDS = setOf(
        "abri instagram", "abrir instagram",
        "abri el instagram", "abrir el instagram",
        "abrime instagram", "abrime el instagram",
        "entra a instagram", "entrar a instagram",
        "entra en instagram", "entrar en instagram",
        "anda a instagram", "pone instagram", "poner instagram"
    )

    private val OPEN_INBOX_COMMANDS = setOf(
        "abri los mensajes de instagram", "abrir los mensajes de instagram",
        "abri mensajes de instagram", "abrir mensajes de instagram",
        "abrime los mensajes de instagram",
        "abri los mensajes en instagram", "abrir los mensajes en instagram",
        "mensajes de instagram",
        "entra a los mensajes de instagram", "entrar a los mensajes de instagram",
        "abri los mensajes directos", "abrir los mensajes directos",
        "abri el direct", "abrir el direct", "abri direct", "abrir direct",
        "abri el direct de instagram", "abrir el direct de instagram",
        "entra al direct", "entrar al direct",
        "abri los dm", "abrir los dm", "abri el dm", "abrir el dm",
        "abri dm", "abrir dm", "abrime los dm",
        "abri los md", "abrir los md",
        "abri la bandeja de instagram", "abrir la bandeja de instagram"
    )

    private val OPEN_CHAT_REGEX = Regex(
        "^(?:abri|abrir|abrime|entra a|entrar a|anda a|pasame a|pasame al)\\s+" +
            "(?:el\\s+)?chat\\s+(?:de\\s+|con\\s+)?(.+)$"
    )

    private val SEND_TEXT_REGEX = Regex(
        "^(?:mandale|mandar|manda|mandame|decile|decir|deci|escribile|escribir|" +
            "escribi|avisale|avisar)\\s+a\\s+(.+?)\\s+que\\s+(.+)$"
    )

    /** Confirmaciones extra del TOQUE de videollamada pedidas por la misión
     *  ("tocá", "confirmo") además del set compartido de WhatsApp. */
    private val EXTRA_VIDEO_CONFIRMS = setOf("toca", "tocala", "confirmo", "si confirmo")

    private val AUDIO_ACTIVATION_TOKENS = listOf("activa", "activame", "prepara", "preparame")

    // --- Canal ---

    fun mentionsInstagram(rawText: String): Boolean {
        val text = foldChannel(rawText)
        if (text.isBlank()) return false
        if (text.split(' ').any { it in CHANNEL_TOKENS }) return true
        return CHANNEL_PHRASES.any { text.contains(it) }
    }

    /** Quita la mención de canal para que los extractores de contacto y
     *  mensaje no arrastren "por instagram" dentro del slot. */
    fun stripChannelMention(rawText: String): String {
        var text = " ${foldChannel(rawText)} "
        for (phrase in CHANNEL_MENTION_PHRASES) {
            text = text.replace(" $phrase ", " ")
        }
        return text.replace(Regex("\\s+"), " ").trim()
    }

    // --- Comandos ---

    fun isOpenAppCommand(rawText: String): Boolean =
        foldChannel(rawText).removeSuffix(" por favor").trim() in OPEN_APP_COMMANDS

    fun isOpenInboxCommand(rawText: String): Boolean =
        foldChannel(rawText).removeSuffix(" por favor").trim() in OPEN_INBOX_COMMANDS

    fun parseOpenChat(rawText: String): String? {
        if (!mentionsInstagram(rawText)) return null
        val stripped = stripChannelMention(rawText).removeSuffix(" por favor").trim()
        val match = OPEN_CHAT_REGEX.find(stripped) ?: return null
        return cleanContactSlot(match.groupValues[1])
    }

    fun parseSendText(rawText: String): SendTextRequest? {
        if (!mentionsInstagram(rawText)) return null
        val stripped = stripChannelMention(rawText).trim()
        val match = SEND_TEXT_REGEX.find(stripped) ?: return null
        val contact = cleanContactSlot(match.groupValues[1]) ?: return null
        val message = match.groupValues[2].trim().removeSuffix(" por favor").trim()
        if (message.isBlank()) return null
        return SendTextRequest(contactQuery = contact, messageText = message)
    }

    fun parseVideoCall(rawText: String): VideoCallRequest? {
        if (!mentionsInstagram(rawText)) return null
        val stripped = stripChannelMention(rawText)
        val request = WhatsAppCallPhrases.parseVideoCall(stripped) ?: return null
        return VideoCallRequest(contactQuery = request.contactQuery)
    }

    fun isAudioRequest(rawText: String): Boolean {
        if (!mentionsInstagram(rawText)) return false
        val stripped = stripChannelMention(rawText)
        if (WhatsAppVoiceSendPhrases.isSendAudioRequest(stripped)) return true
        val folded = foldChannel(stripped)
        return folded.contains("audio") &&
            AUDIO_ACTIVATION_TOKENS.any { folded.contains(it) }
    }

    // --- Respuestas a pendientes (mismo lexicon que WhatsApp) ---

    /** Texto: SOLO confirmación fuerte. "sí"/"dale"/"ok" → WEAK_REJECTED. */
    fun classifyTextSendReply(rawText: String): ReplyKind {
        if (WhatsAppVoiceSendPhrases.isCancelSend(rawText)) return ReplyKind.CANCEL
        if (WhatsAppVoiceSendPhrases.isConfirmSend(rawText)) return ReplyKind.CONFIRM
        if (VoicePhraseNormalizer.isNeverConfirm(rawText)) return ReplyKind.WEAK_REJECTED
        return ReplyKind.OTHER
    }

    /** Videollamada: el toque es reversible, "sí"/"dale"/"tocá" confirman. */
    fun classifyVideoTapReply(rawText: String): ReplyKind {
        if (OutdoorDestinationReply.isDecline(rawText) ||
            WhatsAppVoiceSendPhrases.isCancelSend(rawText)
        ) {
            return ReplyKind.CANCEL
        }
        if (WhatsAppCallPhrases.isConfirmTap(rawText) ||
            OutdoorDestinationReply.isYes(rawText) ||
            fold(rawText) in EXTRA_VIDEO_CONFIRMS
        ) {
            return ReplyKind.CONFIRM
        }
        return ReplyKind.OTHER
    }

    enum class ReplyKind { CONFIRM, CANCEL, WEAK_REJECTED, OTHER }

    // --- Internos ---

    private fun cleanContactSlot(rawSlot: String): String? {
        val candidate = rawSlot
            .removeSuffix(" por favor").trim()
            .removePrefix("el ").removePrefix("la ").trim()
            .replace(Regex("\\s+"), " ")
        if (candidate.length !in 2..40) return null
        if (candidate.any { it.isDigit() }) return null
        if (candidate.split(' ').any { it in CHANNEL_TOKENS }) return null
        return candidate
    }

    /** fold del repo + canonización local insta/ig→instagram. */
    private fun foldChannel(rawText: String): String =
        fold(rawText)
            .split(' ')
            .joinToString(" ") { token ->
                if (token == "insta" || token == "ig") "instagram" else token
            }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
