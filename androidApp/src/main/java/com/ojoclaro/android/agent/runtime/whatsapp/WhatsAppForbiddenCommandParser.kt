package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Mapea frases de acciones PROHIBIDAS de WhatsApp a su [WhatsAppActionType]
 * FORBIDDEN del catálogo, para que el servicio las rutee LOCALMENTE (antes del
 * LLM/cámara/compose) y responda con una negativa segura.
 *
 * Acciones cubiertas: borrar/eliminar chat o mensaje, archivar, silenciar/mutear,
 * fijar/pinear, bloquear, reportar/denunciar, pagar/transferir, sticker,
 * archivo/adjunto, foto/imagen, reenviar, compartir ubicación, abrir enlace.
 *
 * SÓLO clasifica. No toca UI, no escribe borradores, no abre nada. [namedWhatsApp]
 * dice si la frase nombró WhatsApp explícitamente; el caller decide reclamarla
 * sólo si WhatsApp es el contexto (para no robar cámara/pagos/contactos ajenos).
 *
 * Robusto a muletillas (las quita [VoicePhraseNormalizer]) y excluye casos
 * legítimos: "borrá el borrador"/"limpiá la memoria"/"olvidá el contexto" NO son
 * prohibidos (son del flujo WA-5 / contexto).
 */
data class WhatsAppForbiddenMatch(
    val action: WhatsAppActionType,
    val namedWhatsApp: Boolean
)

object WhatsAppForbiddenCommandParser {

    private val WA = Regex("\\b(?:whatsapp|wasap|wsp|wpp)\\b")

    // Verbo de "mandar/sacar/adjuntar/compartir" para foto/archivo/ubicación.
    private val SEND_VERB = Regex(
        "\\b(?:manda\\w*|mandale|envia\\w*|enviale|saca\\w*|sacale|adjunt\\w*|" +
            "comparti\\w*|compartir|pasa\\w*|sube|subir)\\b"
    )
    private val CHAT_NOUN = Regex("\\b(?:chat|conversacion|charla|mensaje|grupo|este|esta|esto)\\b")
    private val PAYMENT = Regex("\\b(?:pagar|pagale|paga|pago|plata|transferir|transferi|transferencia|dinero)\\b")

    fun parse(rawText: String): WhatsAppForbiddenMatch? {
        val f = fold(rawText)
        if (f.isBlank()) return null
        val action = detect(f) ?: return null
        return WhatsAppForbiddenMatch(action, WA.containsMatchIn(f))
    }

    private fun detect(f: String): WhatsAppActionType? {
        val send = SEND_VERB.containsMatchIn(f)
        val chatNoun = CHAT_NOUN.containsMatchIn(f)
        return when {
            (f.contains("link") || f.contains("enlace")) &&
                (f.contains("abri") || f.contains("toca") || f.contains("entra") || f.contains("abre")) ->
                WhatsAppActionType.OPEN_SUSPICIOUS_LINK

            PAYMENT.containsMatchIn(f) || f.contains("mercado pago") ->
                WhatsAppActionType.PAYMENT

            f.contains("sticker") || f.contains("figurita") ->
                WhatsAppActionType.SEND_STICKER

            (f.contains("foto") || f.contains("imagen")) && send ->
                WhatsAppActionType.SEND_PHOTO

            (f.contains("archivo") || f.contains("adjunt") || f.contains("documento") || f.contains("pdf")) &&
                (send || f.contains("adjunt")) ->
                WhatsAppActionType.SEND_FILE

            f.contains("ubicacion") && (send || f.contains("en vivo")) ->
                WhatsAppActionType.SHARE_LOCATION

            f.contains("reenvi") ->
                WhatsAppActionType.FORWARD_MESSAGE

            f.contains("bloque") ->
                WhatsAppActionType.BLOCK_CONTACT

            f.contains("reporta") || f.contains("denunci") ->
                WhatsAppActionType.REPORT_CONTACT

            f.contains("archiva") ->
                WhatsAppActionType.ARCHIVE_CHAT

            (f.contains("silenci") || f.contains("mutea") || f.contains("mute")) && chatNoun ->
                WhatsAppActionType.MUTE_CHAT

            (f.contains("fija") || f.contains("pinea") || f.contains("ancla")) && chatNoun ->
                WhatsAppActionType.PIN_CHAT

            (f.contains("borr") || f.contains("elimin")) &&
                !f.contains("borrador") && !f.contains("contexto") && !f.contains("memoria") &&
                chatNoun ->
                WhatsAppActionType.DELETE_CHAT

            else -> null
        }
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
