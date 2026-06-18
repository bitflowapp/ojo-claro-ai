package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Reconoce pedidos HIGH-RISK por contacto que hoy podrían caer al LLM:
 *  - llamada de voz de WhatsApp ("llamá a Juan por WhatsApp"),
 *  - envío de audio por contacto ("mandale un audio a Juan").
 *
 * La videollamada NO se reconoce acá: tiene su propia ruta (WhatsAppCallPhrases /
 * handleVideoCallRequest), cuyo toque ya está gateado por feature flag.
 *
 * SÓLO clasifica intención. No resuelve contacto, no abre, no llama, no graba.
 * El gate por flag + confirmación lo hace el servicio vía [WhatsAppActionCatalog].
 */
sealed class WhatsAppDangerousIntent {
    /** Llamada de VOZ de WhatsApp a [contactQuery]. */
    data class Call(val contactQuery: String) : WhatsAppDangerousIntent()

    /** Enviar un AUDIO de WhatsApp a [contactQuery]. */
    data class SendAudio(val contactQuery: String) : WhatsAppDangerousIntent()
}

object WhatsAppDangerousCommandParser {

    private val WA = Regex("\\b(?:whatsapp|wasap|wsp|wpp)\\b")

    // "llamá a juan por whatsapp" / "hacele una llamada de whatsapp a juan"
    private val CALL_TO = Regex(
        "^(?:llamar|llama|llamalo|llamala|hacele una llamada|hace una llamada|" +
            "quiero llamar|llamada)\\b.*?\\b(?:a|al) (.+)$"
    )

    // "mandale un audio a juan" / "enviale una nota de voz a juan"
    private val AUDIO_TO = Regex(
        "^(?:mandar|mandale|manda|enviar|enviale|envia|grabar|grabale|grabarle) " +
            "(?:un |una |el |la )?(?:audio|nota de voz|mensaje de voz) " +
            "(?:a|al|para|con) (.+)$"
    )

    fun parse(rawText: String): WhatsAppDangerousIntent? {
        val f = fold(rawText)
        if (f.isBlank()) return null
        if (f.contains("video")) return null // videollamada: ruta aparte

        AUDIO_TO.find(f)?.let { m ->
            cleanName(m.groupValues[1])?.let { return WhatsAppDangerousIntent.SendAudio(it) }
        }
        // Llamada de voz: SÓLO si nombra WhatsApp explícitamente (no robar la
        // llamada telefónica común).
        if (WA.containsMatchIn(f)) {
            CALL_TO.find(f)?.let { m ->
                cleanName(m.groupValues[1])?.let { return WhatsAppDangerousIntent.Call(it) }
            }
        }
        return null
    }

    private fun cleanName(tail: String): String? {
        val t = tail
            .replace(WA, " ")
            .replace(Regex("\\bpor\\b|\\bde\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (t.isBlank()) return null
        val tokens = t.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens.size > 4) return null
        if (t in setOf("el", "la", "un", "una", "mi", "alguien")) return null
        return t.take(60)
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
