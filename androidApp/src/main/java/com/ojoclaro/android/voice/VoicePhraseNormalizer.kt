package com.ojoclaro.android.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Limpieza de español rioplatense aplicada al texto del SpeechRecognizer
 * ANTES de pasarlo al parser. Funciones puras, sin estado, idempotentes.
 *
 * Reglas de seguridad:
 *  - Si stripear muletillas dejara la frase vacía, devuelve la frase original.
 *    Esto es importante para que "dale" alone NO se convierta en "" (que el
 *    parser ignoraría) sino en "dale" (que el caller filtra como noise).
 *  - "dale", "sí", "ok", "ajá" alone nunca confirman acciones sensibles.
 *    `isStrictConfirm` es la única puerta válida para confirmar.
 *  - No reemplaza nombres propios. La capitalización se preserva para que
 *    los regex que extraen contactos no pierdan información.
 */
object VoicePhraseNormalizer {

    /**
     * Devuelve el texto listo para que el parser lo entienda. El orden es:
     *  1) Reescribir voseo (abrime -> abrir, llamame a -> llamar a, etc.)
     *  2) Stripear muletillas multi-palabra (a ver, o sea, por favor)
     *  3) Stripear muletillas de un token (che, eh, bueno, dale, ...)
     *  4) Colapsar repeticiones (sí sí → sí, este este → este)
     *  5) Canonizar aliases y comandos frecuentes (wp -> whatsapp).
     *  6) Limpieza de espacios.
     *
     * Si el resultado quedara vacío (ej: input == "dale"), devuelve el
     * texto original para que la capa superior pueda decidir.
     */
    fun normalizeForParser(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return ""

        var result = trimmed

        // 1) Voseo / reflexivos.
        for ((regex, replacement) in ArgentineSpanishLexicon.VOSEO_REWRITES) {
            result = regex.replace(result, replacement)
        }

        // 2) Muletillas multi-palabra.
        for (phrase in ArgentineSpanishLexicon.MULETILLA_PHRASES) {
            result = stripPhraseTokensInPlace(result, phrase)
        }

        // 3) Muletillas single-token.
        result = stripSingleTokenMuletillas(result)

        // 4) Colapsar repeticiones (excepto si el token completo es la utterance).
        result = collapseAdjacentRepeats(result)

        // 5) Aliases + forma canonica de comandos, sin bajar nombres propios.
        result = normalizeWhatsAppAliases(result)
        result = canonicalizeKnownTokens(result)
        result = postProcessCanonicalPhrases(result)

        // 6) Espacios.
        result = result.replace(Regex("\\s+"), " ").trim()

        return result.ifBlank { trimmed }
    }

    /**
     * Igual que normalizeForParser pero también remueve fillers de inicio
     * tipo "este", "esto", "ese", "eso" que en contextos de WhatsApp Guided
     * se cuelan delante del contacto: "este Marco Antonio" → "Marco Antonio".
     *
     * Pensado para el camino WAITING_WHATSAPP_ACTION → extracción de contacto.
     * No usar globalmente porque "este" puede ser una palabra real en otros
     * contextos ("leeme este mensaje").
     */
    fun normalizeForContactExtraction(rawText: String): String {
        val base = normalizeForParser(rawText)
        if (base.isBlank()) return base

        var tokens = base.split(Regex("\\s+")).toMutableList()
        // Strip leading filler tokens repetidamente.
        while (tokens.isNotEmpty() &&
            tokenLookupKey(tokens.first()) in ArgentineSpanishLexicon.LEADING_CONTEXT_FILLERS
        ) {
            tokens.removeAt(0)
        }
        if (tokens.isEmpty()) return base

        // Quitar fillers intercalados (raro pero posible: "Marco este Antonio").
        tokens = tokens.filter { tokenLookupKey(it) !in ArgentineSpanishLexicon.LEADING_CONTEXT_FILLERS }
            .toMutableList()
        if (tokens.isEmpty()) return base

        return tokens.joinToString(" ")
    }

    /** Reescribe voseo a forma canónica. Pública para tests. */
    fun normalizeVoseo(text: String): String {
        var result = text
        for ((regex, replacement) in ArgentineSpanishLexicon.VOSEO_REWRITES) {
            result = regex.replace(result, replacement)
        }
        return result
    }

    /** Quita muletillas (single y multi-palabra). Pública para tests. */
    fun stripMuletillas(text: String): String {
        var result = text
        for (phrase in ArgentineSpanishLexicon.MULETILLA_PHRASES) {
            result = stripPhraseTokensInPlace(result, phrase)
        }
        result = stripSingleTokenMuletillas(result)
        return result.replace(Regex("\\s+"), " ").trim()
    }

    /** Colapsa repeticiones adyacentes ("sí sí" → "sí"). Pública para tests. */
    fun collapseRepeats(text: String): String = collapseAdjacentRepeats(text)

    /**
     * True si el texto es ruido típico del SR (repeticiones, asentimientos
     * sin valor, muletillas usadas como utterance entera). El caller debe
     * usar este check para evitar mandar la frase al backend.
     */
    fun isAffirmativeNoise(rawText: String): Boolean {
        val foldKey = foldForLookup(rawText)
        if (foldKey.isBlank()) return false
        if (foldKey in ArgentineSpanishLexicon.AFFIRMATIVE_NOISE_PHRASES) return true
        if (ArgentineSpanishLexicon.NOISE_REPEAT_PATTERNS.any { it.matches(foldKey) }) return true
        return false
    }

    /** True si el texto es exactamente una de las frases de confirmación válidas. */
    fun isStrictConfirm(rawText: String): Boolean =
        foldForLookup(rawText) in ArgentineSpanishLexicon.STRICT_CONFIRM_PHRASES

    /** True si el texto es exactamente una frase que jamás puede confirmar. */
    fun isNeverConfirm(rawText: String): Boolean =
        foldForLookup(rawText) in ArgentineSpanishLexicon.NEVER_CONFIRM_PHRASES

    private fun stripSingleTokenMuletillas(text: String): String {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val filtered = tokens.filter { token ->
            tokenLookupKey(token) !in ArgentineSpanishLexicon.MULETILLA_TOKENS
        }
        return filtered.joinToString(" ")
    }

    private fun stripPhraseTokensInPlace(text: String, phrase: String): String {
        val phraseTokens = phrase.split(" ").map(::tokenLookupKey)
        if (phraseTokens.isEmpty()) return text

        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val matches = i + phraseTokens.size <= tokens.size &&
                phraseTokens.indices.all { j -> tokenLookupKey(tokens[i + j]) == phraseTokens[j] }
            if (matches) {
                i += phraseTokens.size
            } else {
                result.add(tokens[i])
                i++
            }
        }
        return result.joinToString(" ")
    }

    private fun collapseAdjacentRepeats(text: String): String {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        for (token in tokens) {
            val key = tokenLookupKey(token)
            if (result.isNotEmpty() &&
                tokenLookupKey(result.last()) == key &&
                key in repeatNoiseTokens
            ) {
                continue
            }
            result.add(token)
        }
        return result.joinToString(" ")
    }

    private fun normalizeWhatsAppAliases(text: String): String =
        whatsappAliasRegex.replace(text, "whatsapp")

    private fun canonicalizeKnownTokens(text: String): String =
        text.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { token ->
                val preservedToken = token.trimTokenPunctuation()
                val key = tokenLookupKey(token)
                when {
                    preservedToken == ":" -> ":"
                    key.isBlank() -> ""
                    key == "whatsapp" -> "whatsapp"
                    key in canonicalLowercaseTokens -> key
                    else -> preservedToken
                }
            }
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private fun postProcessCanonicalPhrases(text: String): String {
        var result = text.replace(Regex("\\s+"), " ").trim()
        val folded = foldForLookup(result)

        if (folded == "donde ando" || folded == "ubicame") return "donde estoy"
        if (folded == "abrir el whatsapp") return "abrir whatsapp"

        result = buscarChatCanonicalRegex.replace(result) { match ->
            "buscar chat ${match.groupValues[1].cleanCanonicalSlot()}"
        }
        result = encontrarChatCanonicalRegex.replace(result) { match ->
            "buscar chat ${match.groupValues[1].cleanCanonicalSlot()}"
        }
        result = abrirChatCanonicalRegex.replace(result) { match ->
            "abrir chat ${match.groupValues[1].cleanCanonicalSlot()}"
        }

        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun String.cleanCanonicalSlot(): String =
        trim()
            .replace(Regex("^(?:de|con)\\s+", RegexOption.IGNORE_CASE), "")
            .trimSlotPunctuation()
            .replace(Regex("\\s+"), " ")

    private fun String.trimTokenPunctuation(): String =
        trim('.', ',', ';', '!', '?', '¿', '¡')

    private fun String.trimSlotPunctuation(): String =
        trim('.', ',', ';', ':', '!', '?', '¿', '¡')

    private fun tokenLookupKey(token: String): String =
        foldForLookup(token.trimSlotPunctuation())

    /**
     * Lower + accent strip + trim de puntuación. Misma normalización que usa
     * el parser interno. Pública sólo a través de los helpers booleanos.
     */
    private fun foldForLookup(text: String): String {
        val withoutAccents = Normalizer.normalize(
            text.lowercase(Locale("es", "AR")),
            Normalizer.Form.NFD
        ).replace(Regex("\\p{Mn}+"), "")

        return withoutAccents
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', '!', '?', '¿', '¡')
    }

    private val whatsappAliasRegex = Regex(
        "\\b(?:whats\\s*app|whatsapp|wp|wsp|wpp|wasap|guasap|watsap|whasap)\\b",
        RegexOption.IGNORE_CASE
    )

    private val buscarChatCanonicalRegex = Regex(
        "^buscar\\s+(?:el\\s+)?chat\\s+(?:de\\s+|con\\s+)?(.+?)$",
        RegexOption.IGNORE_CASE
    )

    private val encontrarChatCanonicalRegex = Regex(
        "^encontrar\\s+(?:el\\s+)?chat\\s+(?:de\\s+|con\\s+)?(.+?)$",
        RegexOption.IGNORE_CASE
    )

    private val abrirChatCanonicalRegex = Regex(
        "^abrir\\s+(?:el\\s+)?chat\\s+(?:de\\s+|con\\s+)?(.+?)$",
        RegexOption.IGNORE_CASE
    )

    private val canonicalLowercaseTokens = setOf(
        "abrir",
        "buscar",
        "encontrar",
        "mandar",
        "decir",
        "escribir",
        "llamar",
        "llevar",
        "poner",
        "avisar",
        "recordar",
        "revisar",
        "anda",
        "donde",
        "estoy",
        "chat",
        "whatsapp",
        "mensaje",
        "mapas",
        "telefono",
        "ubicacion",
        "actual",
        "numero",
        "contacto",
        "emergencia",
        "confianza",
        "principal",
        "solamente",
        "solo",
        "a",
        "al",
        "el",
        "la",
        "los",
        "las",
        "de",
        "del",
        "con",
        "que",
        "por",
        "en",
        "para",
        "un",
        "una",
        "mi",
        "mis",
        "lo",
        "y"
    )

    private val repeatNoiseTokens = setOf(
        "si",
        "eh",
        "dale",
        "bueno",
        "este",
        "no",
        "uh",
        "mm",
        "mmm",
        "aja"
    )
}
