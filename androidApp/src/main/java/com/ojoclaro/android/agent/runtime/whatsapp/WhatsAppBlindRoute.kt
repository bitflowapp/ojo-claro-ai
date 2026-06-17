package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * WhatsApp Blind-First Automation — clasifica SOLO las intenciones de
 * NAVEGACIÓN por voz que hoy no tienen una ruta confiable sin tocar la pantalla.
 *
 * Motivación de producto: una persona no vidente no puede depender de "tocá el
 * chat correcto". Estela tiene que llegar al chat por voz/contacto/notificación.
 *
 * Este objeto cubre exactamente DOS huecos del routing actual:
 *
 *  1. [WhatsAppBlindIntent.OpenContactChat] — abrir el chat de un contacto
 *     nombrado por voz:
 *       - "abrí WhatsApp con Juan" / "abrime WhatsApp para Juan"
 *       - "escribile a Juan" / "mandale un WhatsApp a Juan" / "respondé a Juan"
 *     SIN mensaje. Si la frase nombra el sustantivo "chat"/"conversación" la
 *     deja pasar (ya la cubre ScreenIntelligence: "abrí el chat de X"). Si la
 *     frase trae mensaje ("... que ..."), la deja pasar (la cubre el compose:
 *     [WhatsAppSmartComposeParser]).
 *
 *  2. [WhatsAppBlindIntent.ReplyToLastNotification] — responder la ÚLTIMA
 *     notificación registrada (WA-3):
 *       - "respondé el último WhatsApp" / "contestá el último mensaje"
 *       - "respondé a quien me escribió"
 *
 * Contrato de seguridad:
 *  - SOLO decide intención. No abre nada, no resuelve contactos, no expone PII.
 *  - Las intenciones son de NAVEGACIÓN: jamás preparan ni envían un mensaje.
 *  - Si no puede extraer un nombre limpio y corto, devuelve null para que el
 *    caller deje pasar la frase a las rutas existentes en vez de adivinar.
 */
sealed class WhatsAppBlindIntent {

    /**
     * Abrir el chat de [contactQuery] por voz. [contactQuery] viene normalizado
     * (minúsculas, sin acentos): es una clave de búsqueda para el resolver, no
     * un texto para mostrar. El nombre hablado sale del candidato resuelto.
     */
    data class OpenContactChat(val contactQuery: String) : WhatsAppBlindIntent()

    /** Responder la última notificación de WhatsApp registrada por el listener. */
    object ReplyToLastNotification : WhatsAppBlindIntent()
}

object WhatsAppBlindRoute {

    fun parse(rawText: String): WhatsAppBlindIntent? {
        // ':' es un separador de mensaje dictado ("escribile a Juan: estoy
        // llegando"): eso es compose-con-texto y lo resuelve el smart compose.
        // Bailamos antes de que fold() borre el ':' y confundamos el mensaje
        // con un nombre de contacto.
        if (rawText.contains(':')) return null

        val folded = fold(rawText)
        if (folded.isBlank()) return null

        // Las frases con sustantivo "chat"/"conversación" son de ScreenIntelligence
        // ("abrí el chat de X"): no las robamos.
        if (CHAT_NOUN.containsMatchIn(folded)) return null

        // 1) Responder la última notificación (se evalúa primero: "respondé a
        //    quien me escribió" no debe confundirse con abrir el chat de alguien).
        if (isReplyToLastNotification(folded)) return WhatsAppBlindIntent.ReplyToLastNotification

        // 2) Abrir el chat de un contacto por voz.
        parseOpenContactChat(folded)?.let { return it }

        return null
    }

    // --- Reply to last notification --------------------------------------------

    private val REPLY_VERB = "(?:respond\\w*|contest\\w*)"
    private val NOTIF_NOUN = "(?:whatsapp|wasap|wsp|wpp|mensaje|notificacion|chat|audio)"

    // "respondé el último whatsapp", "contestá el último mensaje", "respondé el último".
    private val LAST_NOTIF = Regex(
        "^$REPLY_VERB(?:\\s+(?:el|al|la))?\\s+ultim[oa](?:\\s+$NOTIF_NOUN)?$"
    )

    // "respondé el mensaje nuevo", "contestá el whatsapp reciente".
    private val NEW_NOTIF = Regex(
        "^$REPLY_VERB\\s+(?:el|al|la)?\\s*$NOTIF_NOUN\\s+(?:nuev[oa]|reciente)$"
    )

    // "respondé a quien me escribió", "contestá al que me escribió/habló/mandó".
    // "al que" = "a"+"el que" contraído: lo aceptamos junto a "a quien"/"a la que".
    private val WHO_WROTE = Regex(
        "^$REPLY_VERB\\s+a(?:l\\s+que|\\s+quien|\\s+la\\s+que)\\s+me\\s+" +
            "(?:escribio|escribia|hablo|mando|mensajeo)$"
    )

    private fun isReplyToLastNotification(folded: String): Boolean =
        LAST_NOTIF.matches(folded) ||
            NEW_NOTIF.matches(folded) ||
            WHO_WROTE.matches(folded)

    // --- Open contact chat ------------------------------------------------------

    // "abrí WhatsApp con Juan" / "abrime WhatsApp para Juan" / "andá a WhatsApp a Juan".
    private val OPEN_APP_WITH = Regex(
        "^(?:abrir|abrime|abri|abrila|abrilo|abre|anda a|andate a|entra a|entrar a|" +
            "entrar en|ir a|quiero abrir|quiero entrar a|llevame a) " +
            "(?:el |la )?(?:whatsapp|wasap|wsp|wpp)(?: business)? " +
            "(?:con|para|a|al|de|del) (.+)$"
    )

    // "escribile a Juan" / "mandale un WhatsApp a Juan" / "respondé a Juan".
    // Los verbos de mensaje SIN texto = abrir el chat (no hay nada que escribir).
    // Lista EXPLÍCITA de formas: las reflexivas "-me" ("mandame a casa",
    // "escribime") quedan afuera a propósito (son navegación / dictado, no abrir).
    private val OPEN_CONTACT_VERB = Regex(
        "^(?:escribir|escribile|escribi|escribe|mandar|mandale|manda|enviar|enviale|" +
            "envia|decir|decile|deci|responder|responde|respondele|contestar|contesta|" +
            "contestale|avisar|avisale|avisa|hablar|hablale) " +
            "(?:un |el |una |la |mi )?(?:whatsapp |wasap |wsp |wpp |mensaje )?" +
            "(?:a|al|con|para) (.+)$"
    )

    private fun parseOpenContactChat(folded: String): WhatsAppBlindIntent.OpenContactChat? {
        val tail = OPEN_APP_WITH.find(folded)?.groupValues?.get(1)
            ?: OPEN_CONTACT_VERB.find(folded)?.groupValues?.get(1)
            ?: return null
        val name = cleanContactName(tail) ?: return null
        return WhatsAppBlindIntent.OpenContactChat(name)
    }

    /**
     * Extrae un nombre de contacto limpio del resto de la frase, o null si lo
     * que queda parece un MENSAJE (no un nombre) o una frase de notificación.
     * Devolver null hace que el caller no adivine y deje pasar la frase a las
     * rutas existentes (compose / reply / lectura).
     */
    private fun cleanContactName(tail: String): String? {
        val t = tail.trim()
        if (t.isBlank()) return null
        val tokens = t.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens.size > MAX_NAME_TOKENS) return null
        // Un separador de mensaje ("que", "diciendo"...) significa compose, no abrir.
        if (tokens.any { it in MESSAGE_MARKERS }) return null
        // Frases de notificación ("el último", "quien me escribió") no son nombres.
        if (tokens.any { it in NOTIF_WORDS }) return null
        if ("quien me" in t) return null
        if (t in STOPWORD_NAMES) return null
        return t.take(MAX_NAME_CHARS)
    }

    private const val MAX_NAME_TOKENS = 4
    private const val MAX_NAME_CHARS = 60

    private val CHAT_NOUN = Regex("\\b(?:chat|conversacion|charla|conversa)\\b")

    private val MESSAGE_MARKERS = setOf(
        "que", "diciendo", "decirle", "avisarle", "texto"
    )

    private val NOTIF_WORDS = setOf(
        "ultimo", "ultima", "ultimos", "ultimas"
    )

    private val STOPWORD_NAMES = setOf(
        "el", "la", "los", "las", "un", "una", "mi", "mis",
        "alguien", "ese", "esa", "esto", "eso", "alguno", "alguna"
    )

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
