package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Blind Safety — guardia LOCAL de última línea antes de cualquier salida a
 * LLM/backend (handleFreeConversation -> /conversation, y el fallback del
 * orchestrator). Detecta frases WhatsApp-CRÍTICAS (mutar/enviar/borrar/pagar/
 * foto/llamar/audio) para que NUNCA viajen fuera del dispositivo.
 *
 * Reglas:
 *  - Solo verbos MUTANTES o de ACCIÓN. Los verbos de LECTURA ("leé", "leeme",
 *    "mostrame", "qué dice", "qué chats hay") NO son críticos: deben seguir su
 *    ruta local de lectura. Por eso este set NO incluye leer/mostrar/qué.
 *  - El caller decide el alcance (solo bloquea si la frase nombra WhatsApp o el
 *    contexto activo ES WhatsApp); este objeto solo clasifica el verbo.
 *
 * PURO: sin Android, sin estado, sin IO. Mismo fold() que los parsers hermanos.
 */
object WhatsAppCriticalGuard {

    private val CRITICAL_MARKERS = listOf(
        // responder / contestar
        "respond", "contestale", "contesta",
        // mandar / enviar / escribir a alguien
        "mandar", "manda", "mandale", "envia", "enviar", "enviale",
        "escribile", "escribir a", "escribile a",
        // borrar / eliminar
        "borrar", "borra", "elimina", "eliminar",
        // bloquear
        "bloquear", "bloquea", "bloque",
        // reenviar
        "reenvi", "reenviar", "reenvia",
        // reportar / denunciar
        "reporta", "reportar", "denunci",
        // archivar
        "archiva", "archivar",
        // silenciar / mutear
        "silenci", "mutea", "mutear",
        // fijar
        "fijar", "pinea",
        // multimedia / adjuntos / ubicación
        "foto", "imagen", "sticker", "figurita", "adjunta", "adjuntar", "ubicacion",
        // pagos
        "pagar", "pagale", "plata", "transfer",
        // llamadas / audio
        "llamar", "llamale", "videollamada", "nota de voz", "mandale un audio",
        "mandar un audio", "enviar un audio"
    )

    fun isCritical(rawText: String): Boolean {
        val folded = fold(rawText)
        if (folded.isBlank()) return false
        return CRITICAL_MARKERS.any { folded.contains(it) }
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
