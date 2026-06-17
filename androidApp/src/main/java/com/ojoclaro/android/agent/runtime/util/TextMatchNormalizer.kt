package com.ojoclaro.android.agent.runtime.util

import java.text.Normalizer

/**
 * Shared normalization for deterministic UI matching.
 *
 * This is intentionally small and content-agnostic: callers receive a normalized
 * string for local comparisons only, never for persistence or logging.
 */
object TextMatchNormalizer {

    private val combiningMarksRegex = Regex("\\p{Mn}+")
    private val punctuationRegex = Regex("[\\u00BF?\\u00A1!.,;:]")
    private val whitespaceRegex = Regex("\\s+")

    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(combiningMarksRegex, "")
        return stripped
            .replace(punctuationRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }
}
