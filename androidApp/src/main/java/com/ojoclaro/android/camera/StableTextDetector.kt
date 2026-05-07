package com.ojoclaro.android.camera

import java.util.Locale

sealed class StableTextResult {
    data class TextReady(val text: String) : StableTextResult()
    data object NoTextFound : StableTextResult()
}

/**
 * Detecta texto estable antes de emitirlo por voz.
 *
 * Reglas:
 * - El mismo texto debe mantenerse estable durante stableWindowMillis.
 * - No emite mientras TTS está hablando.
 * - No repite el mismo texto durante el mismo escaneo.
 * - Si no hay texto durante noTextTimeoutMillis, avisa una sola vez.
 * - No guarda imágenes ni OCR histórico.
 */
class StableTextDetector(
    private val stableWindowMillis: Long = 1_000L,
    private val noTextTimeoutMillis: Long = 6_000L
) {
    private var scanStartedAtMillis: Long = 0L
    private var lastDetectionAtMillis: Long = 0L

    private var candidateKey: String = ""
    private var candidateText: String = ""
    private var candidateSinceMillis: Long = 0L

    private var noTextAnnounced = false
    private val emittedKeys = mutableSetOf<String>()

    @Synchronized
    fun reset(nowMillis: Long) {
        scanStartedAtMillis = nowMillis
        lastDetectionAtMillis = 0L
        clearCandidate()
        noTextAnnounced = false
        emittedKeys.clear()
    }

    @Synchronized
    fun onTextDetected(
        text: String,
        nowMillis: Long,
        isSpeechBusy: Boolean
    ): StableTextResult? {
        if (isSpeechBusy) return null

        val normalized = normalizeForSpeech(text)

        if (normalized.isBlank()) {
            clearCandidate()
            return onNoText(nowMillis, isSpeechBusy = false)
        }

        lastDetectionAtMillis = nowMillis

        val key = normalizeKey(normalized)

        if (key.isBlank()) {
            clearCandidate()
            return onNoText(nowMillis, isSpeechBusy = false)
        }

        if (key != candidateKey) {
            candidateKey = key
            candidateText = normalized
            candidateSinceMillis = nowMillis
            return null
        }

        val stableForMillis = nowMillis - candidateSinceMillis
        if (stableForMillis < stableWindowMillis) return null

        if (!emittedKeys.add(key)) return null

        return StableTextResult.TextReady(candidateText)
    }

    @Synchronized
    fun onNoText(
        nowMillis: Long,
        isSpeechBusy: Boolean
    ): StableTextResult? {
        if (isSpeechBusy || noTextAnnounced) return null

        val referenceMillis = maxOf(scanStartedAtMillis, lastDetectionAtMillis)
        if (referenceMillis <= 0L) return null

        if (nowMillis - referenceMillis < noTextTimeoutMillis) return null

        noTextAnnounced = true
        clearCandidate()

        return StableTextResult.NoTextFound
    }

    private fun clearCandidate() {
        candidateKey = ""
        candidateText = ""
        candidateSinceMillis = 0L
    }

    private fun normalizeForSpeech(text: String): String {
        val normalized = text
            .lineSequence()
            .map { line ->
                line
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .trim()

        if (normalized.length <= MAX_TEXT_CHARS_TO_SPEAK) {
            return normalized
        }

        return normalized
            .take(MAX_TEXT_CHARS_TO_SPEAK)
            .trimEnd()
            .trimEnd('.', ',', ';', ':')
            .plus("…")
    }

    private fun normalizeKey(text: String): String {
        return text
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private const val MAX_TEXT_CHARS_TO_SPEAK = 900
    }
}
