package com.ojoclaro.android.voice

import java.text.Normalizer
import java.util.Locale

/**
 * V1.4 — quita wake words y saludos iniciales ANTES de todo el routing:
 * "Hola Estela, ¿cómo estás?" → "¿cómo estás?";
 * "Estela, dónde estoy" → "dónde estoy";
 * "che estela quiero ir a la plaza" → "quiero ir a la plaza".
 *
 * Reglas:
 *  - Solo remueve tokens INICIALES; nunca toca el medio de la frase.
 *  - Si al limpiar no queda nada ("hola", "estela"), devuelve el texto
 *    original: esas frases tienen respuesta propia en la capa de compañía.
 *  - "hola" solo se remueve si va acompañado (p. ej. "hola estela cómo
 *    estás"); preserva el saludo puro.
 */
object WakeWordStripper {

    private val WAKE_TOKENS = setOf("estela", "asistente", "oye", "ey", "che", "eh")
    private val GREETING_TOKENS = setOf("hola", "bueno", "buenas")
    private val WAKE_PHRASES = listOf(listOf("ojo", "claro"))

    fun strip(rawText: String): String {
        val tokens = rawText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 2) return rawText

        var index = 0
        while (index < tokens.size) {
            val key = fold(tokens[index])
            val phraseLength = WAKE_PHRASES.firstOrNull { phrase ->
                index + phrase.size <= tokens.size &&
                    phrase.indices.all { fold(tokens[index + it]) == phrase[it] }
            }?.size
            when {
                phraseLength != null -> index += phraseLength
                key in WAKE_TOKENS -> index += 1
                key in GREETING_TOKENS -> index += 1
                else -> break
            }
        }

        if (index == 0) return rawText
        val rest = tokens.drop(index)
        // Nada después del saludo/wake word: la frase original ES el mensaje.
        if (rest.isEmpty()) return rawText
        return rest.joinToString(" ")
    }

    private fun fold(token: String): String {
        val lower = token.lowercase(Locale("es", "AR"))
            .trim(',', '.', ';', ':', '!', '?', '¿', '¡')
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
    }
}
