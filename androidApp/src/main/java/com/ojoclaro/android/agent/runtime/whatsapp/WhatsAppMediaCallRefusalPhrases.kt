package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Clasifica pedidos de VIDEOLLAMADA / AUDIO / LLAMADA de WhatsApp que el forbidden
 * parser y el dangerous parser (que exige destinatario) NO cubren en su forma
 * "pelada" ("hacé videollamada", "mandale un audio", "llamá por WhatsApp"). El
 * caller los rechaza LOCALMENTE con una negativa ESPECÍFICA, antes del clarifier
 * y del LLM. SÓLO clasifica. PURO: sin Android, sin estado, sin IO.
 */
object WhatsAppMediaCallRefusalPhrases {

    enum class Kind { VIDEO_CALL, AUDIO, VOICE_CALL }

    // Cubre TODAS las variantes de videollamada (alineado con WhatsAppCallPhrases):
    // videollamada/videollamar/video llamada/llamada de|con video, + "video"+verbo.
    private val VIDEO_CALL_WORD = Regex(
        "\\bvideollam\\w*\\b|\\bvideo llamada\\b|\\bllamada (?:de|con) video\\b"
    )
    private val AUDIO_NOUN = Regex("\\b(?:audio|nota de voz|mensaje de voz)\\b")
    // Prefijos tolerantes al voseo que aplica el normalizer ("grabá"→"graba",
    // "mandá"→"manda", etc.): manda*/envia*/grab*/tira*/pasa*.
    private val AUDIO_VERB = Regex(
        "\\b(?:manda\\w*|enviar|envia\\w*|enviale|grab\\w*|tira\\w*|pasa\\w*)\\b"
    )
    private val CALL = Regex("\\b(?:llamar|llama|llamada|llamale|llamalo|llamala|llamame)\\b")

    fun classify(rawText: String): Kind? {
        val f = fold(rawText)
        if (f.isBlank()) return null
        // Videollamada primero (cubre videollamar/videollamada/llamada de video…).
        if (VIDEO_CALL_WORD.containsMatchIn(f)) return Kind.VIDEO_CALL
        if (f.contains("video") && CALL.containsMatchIn(f)) return Kind.VIDEO_CALL
        if (AUDIO_NOUN.containsMatchIn(f) && AUDIO_VERB.containsMatchIn(f)) return Kind.AUDIO
        if (f.contains("grabar un audio") || f.contains("grabame un audio")) return Kind.AUDIO
        if (CALL.containsMatchIn(f)) return Kind.VOICE_CALL
        return null
    }

    /** Negativa específica por tipo. */
    fun refusal(kind: Kind): String = when (kind) {
        Kind.VIDEO_CALL -> "Por seguridad no puedo iniciar videollamadas por WhatsApp."
        Kind.AUDIO -> "Por seguridad no puedo mandar audios por WhatsApp."
        Kind.VOICE_CALL -> "Por seguridad no puedo iniciar llamadas por WhatsApp."
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
