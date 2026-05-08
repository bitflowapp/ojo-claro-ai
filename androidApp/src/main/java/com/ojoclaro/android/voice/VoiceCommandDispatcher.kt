package com.ojoclaro.android.voice

import java.text.Normalizer
import java.util.Locale

class VoiceCommandDispatcher(
    private val executeCommand: (String) -> Unit,
    private val stopSpeechNow: () -> Unit,
    private val updatePartialText: (String) -> Unit = {}
) {

    fun onPartialText(text: String) {
        if (isStopCommand(text)) {
            stopSpeechNow()
        } else {
            updatePartialText(text)
        }
    }

    fun onFinalText(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        if (isStopCommand(cleanText)) {
            stopSpeechNow()
        } else {
            executeCommand(cleanText)
        }
    }

    companion object {
        fun isStopCommand(text: String): Boolean {
            val normalized = normalize(text)
            return normalized in setOf("callar", "callate", "silencio", "para", "parar") ||
                normalized.contains(" callar ") ||
                normalized.startsWith("callar ") ||
                normalized.endsWith(" callar") ||
                normalized.startsWith("para ") ||
                normalized.startsWith("parar ")
        }

        fun isHelpCommand(text: String): Boolean =
            normalize(text) in setOf(
                "que puedo decir",
                "que puedo hacer",
                "que podes hacer",
                "ayuda",
                "explicame como usar esto",
                "explicame como usar la app"
            )

        fun isReadTextCommand(text: String): Boolean {
            val normalized = normalize(text)
            return normalized in setOf("leer texto", "lee texto", "leeme texto", "leer un texto") ||
                normalized.startsWith("leer texto ") ||
                normalized.startsWith("leeme texto ")
        }

        private fun normalize(text: String): String {
            val parserReadyText = VoicePhraseNormalizer.normalizeForParser(text)
            val withoutAccents = Normalizer.normalize(
                parserReadyText.lowercase(Locale("es", "AR")),
                Normalizer.Form.NFD
            ).replace(Regex("\\p{Mn}+"), "")

            return withoutAccents
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('.', '!', '?', '¿', '¡')
        }
    }
}
