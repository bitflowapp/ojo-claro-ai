package com.ojoclaro.android.llm

/**
 * Sanitiza el texto del usuario ANTES de enviarlo al LLM/backend. Nunca debe
 * salir del teléfono un número de teléfono, email o token. No toca lo que se le
 * HABLA al usuario; solo lo que viaja afuera. Sobre-redactar es seguro: preferir
 * perder un número en una pregunta antes que filtrar PII.
 *
 * PURO: sin Android, sin estado, sin IO.
 */
object LlmInputSanitizer {

    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.-]+")

    // Cadena larga alfanumérica MIXTA (letras + dígitos, 24+) = posible token/clave
    // dictada. Sin prefijos de proveedor: no escribimos material parecido a una key
    // en el código fuente. No leemos .env real.
    private val TOKEN = Regex(
        "\\b(?=[A-Za-z0-9_-]*[A-Za-z])(?=[A-Za-z0-9_-]*\\d)[A-Za-z0-9_-]{24,}\\b"
    )

    // Corrida de dígitos (con +, espacios, guiones, paréntesis): si tiene 7+
    // dígitos reales = teléfono/identificador → redactar. Menos = se deja (ej.
    // "calle 1234", "a las 10:30").
    private val DIGIT_RUN = Regex("\\+?\\d[\\d()\\s.\\-]{5,}\\d")

    // Secreto DICTADO por palabra clave + valor: "mi pin es 1234", "la clave es
    // gato7", "el código es 4821", "cvv: 123". El DIGIT_RUN no atrapa un PIN corto
    // (4 dígitos < 7) y ConversationGate no filtra "pin/código/cvv", así que un
    // valor corto podía viajar a /conversation. Exige conector (es/son/:/=) para
    // NO romper texto normal ("la clave musical", "qué es un pin"): sin valor
    // tras el conector no hay match. Solo redacta el VALOR; conserva la palabra.
    private val SECRET = Regex(
        "\\b(?:contrase(?:n|ñ)a|contrasenia|clave|pin|c[oó]digo|cvv|password|otp)\\b" +
            "\\s*(?:es|son|sera|seria|:|=)\\s+([\\p{L}\\p{N}._-]{2,})",
        RegexOption.IGNORE_CASE
    )

    fun sanitize(text: String): String {
        if (text.isBlank()) return text
        var out = EMAIL.replace(text, "[email]")
        out = SECRET.replace(out) { m -> m.value.removeSuffix(m.groupValues[1]) + "[dato]" }
        out = TOKEN.replace(out, "[clave]")
        out = DIGIT_RUN.replace(out) { match ->
            if (match.value.count { it.isDigit() } >= 7) "[número]" else match.value
        }
        return out
    }
}
