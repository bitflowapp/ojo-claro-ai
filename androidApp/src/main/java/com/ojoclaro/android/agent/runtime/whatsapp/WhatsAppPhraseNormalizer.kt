package com.ojoclaro.android.agent.runtime.whatsapp

import java.text.Normalizer

/**
 * Normalización compartida para los reconocedores deterministas de frases de
 * WhatsApp (lista de chats, lectura de mensajes, lectura en voz alta).
 *
 * Hace lo mínimo y predecible:
 *  - lowercase + strip de acentos,
 *  - quita signos de puntuación,
 *  - colapsa espacios,
 *  - canoniza los alias hablados de WhatsApp (wp, wsp, guasap, ...) a "whatsapp".
 *
 * NO aplica voseo ni reescrituras pesadas: eso vive en
 * [com.ojoclaro.android.voice.VoicePhraseNormalizer]. Acá queremos un resultado
 * estable para comparar contra sets fijos sin sorpresas.
 */
internal object WhatsAppPhraseNormalizer {

    private val aliasRegex = Regex(
        "\\b(?:whats\\s*app|whatsapp|wp|wsp|wpp|wasap|wasup|guasap|guasapp|guasab|watsap|whasap|guasa)\\b",
        RegexOption.IGNORE_CASE
    )

    fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val cleaned = stripped
            .replace(Regex("[¿?¡!.,;:]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return cleaned
        return aliasRegex.replace(cleaned, "whatsapp")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
