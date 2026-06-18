package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Compose por RELACIÓN: "mandale a mi novia que estoy llegando" → (pareja,
 * "estoy llegando"). A diferencia del compose genérico, el destinatario es una
 * RELACIÓN conocida (no un contacto guardado por nombre), así que el split
 * reconoce la relación con [WhatsAppRelationshipAlias] al inicio del resto y
 * toma lo que sigue como mensaje.
 *
 * Solo reclama cuando hay relación Y mensaje. Sin mensaje (p. ej. "abrí
 * WhatsApp con mi novia") devuelve null y lo toma el flujo de apertura, que no
 * escribe nada.
 *
 * Es PURO: clasifica texto, no resuelve números, no toca Android.
 */
object WhatsAppRelationshipComposeParser {

    data class Request(val key: String, val message: String)

    // Mismos verbos que el compose genérico + responder/contestar (para
    // "respondé a mi novia que ..."). "mandame" queda afuera (es navegación).
    private const val VERBS =
        "(?:manda(?:r|le)?|envia(?:r|le)?|escribi(?:r|le)?|deci(?:r|le)?|" +
            "avisa(?:r|le)?|responde(?:r|le)?|contesta(?:r|le)?)"
    private const val NOUN = "(?:(?:un|el|este) )?(?:whatsapp|mensaje)"
    private const val TO = "(?:a|al|para)"

    private val LEAD = Regex("^$VERBS(?: $NOUN)? $TO ")
    private val SEP = Regex(
        "^(?:diciendo que|diciendo|que dice|que diga|con el texto|con mensaje|mensaje|que) "
    )

    // "mi contacto de prueba" / "mi contacto de testeo" = 4 palabras (la más larga).
    private const val MAX_REL_WORDS = 5
    private const val MAX_MESSAGE = 300

    fun parse(rawText: String): Request? {
        val folded = fold(rawText)
        val lead = LEAD.find(folded) ?: return null
        val remainder = folded.substring(lead.value.length).trim()
        if (remainder.isBlank()) return null

        val words = remainder.split(" ").filter { it.isNotBlank() }
        val upTo = minOf(words.size, MAX_REL_WORDS)
        // Más largo primero: "mi contacto de prueba" antes que "mi contacto".
        for (n in upTo downTo 1) {
            val candidate = words.take(n).joinToString(" ")
            val key = WhatsAppRelationshipAlias.canonicalKey(candidate) ?: continue
            val rest = words.drop(n).joinToString(" ").trim()
            val message = SEP.replaceFirst(rest, "").trim().take(MAX_MESSAGE)
            if (message.isBlank()) return null   // relación sin mensaje: no es compose
            return Request(key, message)
        }
        return null
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
