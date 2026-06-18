package com.ojoclaro.android.voice

import java.text.Normalizer
import java.util.Locale

/**
 * WhatsApp Fluency — clasificador PURO de "muletillas / frase incompleta" para
 * una persona que habla con ansiedad, pausas o titubeos.
 *
 * Dos usos:
 *  - [stripFillers]: limpia muletillas de un MENSAJE dictado preservando el
 *    contenido real ("respondé ehh estoy llegando" → "estoy llegando").
 *  - [isFillerOnly] / [looksIncomplete]: detectan que lo dictado fue solo ruido
 *    o quedó colgado (termina en conector), para que el caller pida la frase
 *    en vez de preparar un borrador basura.
 *
 * Conservador a propósito: solo quita interjecciones inequívocas (eh/mmm/...) y
 * "o sea". NUNCA toca palabras que podrían ser contenido real ("este sábado",
 * "a ver mañana"), para no mutilar el mensaje del usuario.
 */
object IncompleteUtteranceClassifier {

    /** Interjecciones que jamás son contenido: se quitan en cualquier posición. */
    private val INTERJECTION_FILLERS = setOf(
        "eh", "ehh", "ehhh", "ehhhh", "ehm", "ehmm", "em", "emm", "emmm",
        "mmm", "mmmm", "mm", "ah", "ahh", "ahhh", "uh", "uhh", "ejem"
    )

    /** Muletillas multi-palabra inequívocas (secuencia exacta). */
    private val MULTIWORD_FILLERS = listOf(listOf("o", "sea"))

    /** Conectores que, al final de la frase, sugieren que quedó colgada. */
    private val DANGLING_CONNECTORS = setOf(
        "y", "o", "u", "pero", "que", "porque", "con", "de", "para", "a", "en",
        "el", "la", "los", "las", "un", "una", "mi", "tu", "su", "le", "les",
        "me", "se", "como", "cuando", "aunque", "entonces", "asi"
    )

    /** Quita muletillas preservando el contenido real (case/acentos del resto). */
    fun stripFillers(rawText: String): String {
        val tokens = rawText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        val folded = tokens.map { fold(it) }
        val drop = BooleanArray(tokens.size)

        // 1) Muletillas multi-palabra (secuencia foldeada exacta).
        for (phrase in MULTIWORD_FILLERS) {
            var i = 0
            while (i + phrase.size <= tokens.size) {
                val matches = phrase.indices.all { j -> folded[i + j] == phrase[j] && !drop[i + j] }
                if (matches) {
                    for (j in phrase.indices) drop[i + j] = true
                    i += phrase.size
                } else {
                    i += 1
                }
            }
        }
        // 2) Interjecciones en cualquier posición.
        for (i in tokens.indices) {
            if (folded[i] in INTERJECTION_FILLERS) drop[i] = true
        }

        return tokens.filterIndexed { idx, _ -> !drop[idx] }.joinToString(" ").trim()
    }

    /** True si lo dictado fue SOLO muletillas (no quedó contenido). */
    fun isFillerOnly(rawText: String): Boolean =
        rawText.isNotBlank() && stripFillers(rawText).isBlank()

    /** True si la frase quedó colgada: solo muletillas o termina en conector. */
    fun looksIncomplete(rawText: String): Boolean {
        val stripped = stripFillers(rawText)
        if (stripped.isBlank()) return rawText.isNotBlank()
        val lastToken = fold(stripped.split(Regex("\\s+")).last())
        return lastToken in DANGLING_CONNECTORS
    }

    private fun fold(token: String): String =
        Normalizer.normalize(token.lowercase(Locale("es", "AR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[¿?¡!.,;:]"), "")
            .trim()
}
