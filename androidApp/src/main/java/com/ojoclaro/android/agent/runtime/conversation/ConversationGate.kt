package com.ojoclaro.android.agent.runtime.conversation

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.7 — decide si una frase puede ir a conversación libre con el LLM.
 *
 * Regla de oro: ante la duda, NO es conversación (la frase sigue su ruta
 * histórica por el orchestrator). Cualquier marca de acción real —abrir,
 * mandar, leer, llevar, describir, pantalla, whatsapp, audio, ubicación,
 * cancelar, confirmar…— excluye la frase de la charla. Sobre-bloquear es
 * seguro; sub-bloquear podría robarle frases a un comando.
 */
object ConversationGate {

    private val ACTION_MARKERS = listOf(
        "abri", "abrir", "manda", "mandar", "envia", "enviar", "escribi",
        "lee", "leer", "leeme", "llama", "llamar", "llev", "ruta", "ir a",
        "describ", "pantalla", "mensaje", "chat", "whatsapp", "audio",
        "camara", "ubicacion", "donde", "cancel", "confirm", "repeti",
        "cuanto falta", "buscar", "navega", "guia", "volver", "cerrar",
        "callate", "callar", "para", "deten", "cruzar", "avanzar",
        "clave", "token", "contrase", "tarjeta", "cbu",
        // Memoria de contactos: "el número de X es...", "guardá el número",
        // "agendá a X" son comandos del orchestrator, nunca charla.
        "numero", "guarda", "agend", "contacto",
        // V1.9 — recalcular es acción de navegación, jamás charla.
        "recalcul",
        // V1.10.3 — consultas de pantalla en apps externas ("qué aparece",
        // "qué puedo tocar", "qué opciones tengo"): acción local, jamás LLM.
        "aparece", "tocar", "opciones",
        // Blind Safety — verbos MUTANTES de WhatsApp: jamás charla, jamás backend.
        // Defensa en profundidad: aunque una frase crítica esquive los handlers
        // locales por contexto poco claro, NUNCA debe viajar a /conversation.
        "respond", "borr", "elimin", "bloque", "reenvi", "reporta", "denunci",
        "archiv", "silenci", "mute", "foto", "imagen", "sticker", "figurita",
        "paga", "plata", "transfer", "decile", "decirle",
        // Blind Safety v2: audios/adjuntos coloquiales (ej. "tirale una nota de
        // voz") que esquivaban el guard bajo contexto ilegible.
        "nota de voz", "adjunt"
    )

    fun isConversational(rawText: String): Boolean {
        val folded = fold(rawText)
        if (folded.isBlank() || folded.length < 3) return false
        return ACTION_MARKERS.none { folded.contains(it) }
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
