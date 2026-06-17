package com.ojoclaro.android.agent.command

import java.text.Normalizer
import java.util.Locale

object CommandNormalizer {
    private val combiningMarksRegex = Regex("\\p{Mn}+")
    private val punctuationRegex = Regex("[\\u00BF\\u00A1?!.,;:()\\[\\]{}\"']")
    private val whitespaceRegex = Regex("\\s+")
    private val punctuationChars = charArrayOf(
        '.', ',', ';', ':', '?', '!', '"', '\'', '(', ')', '[', ']', '{', '}',
        '\u00BF', '\u00A1'
    )

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val lower = input.lowercase(Locale("es", "AR"))
        val withoutAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(combiningMarksRegex, "")

        return withoutAccents
            .replace(punctuationRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun tokenize(input: String): List<CommandToken> =
        input.trim()
            .split(whitespaceRegex)
            .mapNotNull { rawPart ->
                val rawToken = rawPart.trim(*punctuationChars)
                val normalizedToken = normalize(rawToken)
                if (rawToken.isBlank() || normalizedToken.isBlank()) {
                    null
                } else {
                    CommandToken(raw = rawToken, normalized = normalizedToken)
                }
            }

    fun cleanSlotValue(tokens: List<CommandToken>): String =
        tokens.joinToString(separator = " ") { it.raw }
            .trim()
            .trim(*punctuationChars)
            .replace(whitespaceRegex, " ")
}

data class CommandToken(
    val raw: String,
    val normalized: String
)
