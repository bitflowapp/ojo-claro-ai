package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.11 — pedidos de VIDEOLLAMADA de WhatsApp.
 *
 * Contrato duro: detectar el pedido JAMÁS toca nada. El flujo es:
 * abrir/verificar chat → ubicar botón → preguntar → tocar SOLO tras
 * confirmación explícita del usuario ("sí"/"dale" alcanzan: iniciar una
 * llamada es reversible —se corta—, a diferencia de enviar un mensaje).
 */
object WhatsAppCallPhrases {

    data class VideoCallRequest(val contactQuery: String?)

    private val VIDEO_MARKERS = listOf(
        "videollamada", "video llamada", "videollamar", "llamada de video",
        "llamalo por video", "llamala por video", "llamar por video",
        "llamada con video"
    )

    /** Confirmaciones para TOCAR el botón ya anunciado (reversible). */
    private val CONFIRM_TAP = setOf(
        "si", "si dale", "dale", "si tocalo", "tocalo", "si toca",
        "tocala", "si quiero", "hacelo", "si hacelo", "llamalo", "llama"
    )

    private val CONTACT_CONNECTORS = listOf(" con ", " a ")

    fun parseVideoCall(rawText: String): VideoCallRequest? {
        val text = fold(rawText)
        if (text.isBlank()) return null
        if (VIDEO_MARKERS.none { text.contains(it) }) return null

        // "videollamada con marco" / "hace una videollamada a marco"
        for (connector in CONTACT_CONNECTORS) {
            val idx = text.lastIndexOf(connector)
            if (idx > 0) {
                val candidate = text.substring(idx + connector.length)
                    .removeSuffix(" por favor").trim()
                    .removePrefix("el ").removePrefix("la ").trim()
                if (candidate.length in 2..40 && candidate.none { it.isDigit() } &&
                    "video" !in candidate
                ) {
                    return VideoCallRequest(contactQuery = candidate)
                }
            }
        }
        return VideoCallRequest(contactQuery = null)
    }

    fun isConfirmTap(rawText: String): Boolean = fold(rawText) in CONFIRM_TAP

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
