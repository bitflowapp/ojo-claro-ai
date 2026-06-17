package com.ojoclaro.android.outdoor

import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.11 — monitoreo HONESTO del estado de un viaje (DiDi/Uber/Cabify).
 *
 * Estela solo puede ver lo que la pantalla muestra: el monitoreo lee el
 * snapshot de accesibilidad mientras la app del viaje esté adelante y avisa
 * cambios (confirmado / llegando / afuera). Jamás pide ni confirma viajes.
 */
object RideMonitorPhrases {

    private val START_MARKERS = listOf(
        // El lexicon reescribe "avisame"→"avisar" ANTES de este parser:
        // cubrimos ambas formas (convención del repo).
        "avisame cuando llegue el viaje", "avisar cuando llegue el viaje",
        "avisame cuando el viaje", "avisar cuando el viaje",
        "avisame cuando llegue el conductor", "avisar cuando llegue el conductor",
        "avisame cuando este el auto", "avisar cuando este el auto",
        "monitorea el viaje", "monitoreo del viaje", "segui mirando el viaje",
        "segui el viaje", "fijate como va el viaje", "como va el viaje",
        "mira el viaje", "controla el viaje"
    )

    private val STOP_PHRASES = setOf(
        "deja de mirar el viaje", "deja de mirar", "corta el monitoreo",
        "cancela el monitoreo", "cancelar monitoreo", "termina el monitoreo",
        "deja el viaje", "no mires mas"
    )

    /** Estados visibles que vale la pena anunciar, en orden de progreso. */
    val STATUS_KEYWORDS: List<Pair<String, String>> = listOf(
        "confirmado" to "Tu viaje parece confirmado.",
        "conductor asignado" to "Ya tenés conductor asignado.",
        "en camino" to "El conductor está en camino.",
        "llegando" to "El conductor está llegando.",
        "esta cerca" to "El conductor está cerca.",
        "ha llegado" to "El conductor parece estar afuera.",
        "llego" to "El conductor parece estar afuera.",
        "te esta esperando" to "El conductor te está esperando afuera.",
        "esperando" to "El conductor parece estar esperándote.",
        "en viaje" to "El viaje está en curso.",
        "finalizado" to "El viaje aparece como finalizado."
    )

    fun isStartCommand(rawText: String): Boolean {
        val text = fold(rawText)
        if (text.isBlank()) return false
        return START_MARKERS.any { text.contains(it) }
    }

    fun isStopCommand(rawText: String): Boolean = fold(rawText) in STOP_PHRASES

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
