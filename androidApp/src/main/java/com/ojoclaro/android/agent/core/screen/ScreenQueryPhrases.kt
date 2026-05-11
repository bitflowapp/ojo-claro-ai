package com.ojoclaro.android.agent.core.screen

import java.text.Normalizer

/**
 * Reconocimiento determinista de comandos relacionados con la pantalla.
 *
 * No tocamos LocalIntentParser porque ese ya hace su trabajo. Esta clase es
 * complementaria: el HomeViewModel/orchestrator puede preguntar "¿esto es un
 * pedido de resumen de pantalla?" antes de seguir.
 *
 * Devuelve un ScreenSummaryMode si reconoce, o null si no aplica.
 */
object ScreenQueryPhrases {

    private val whereAmI = setOf(
        "donde estoy",
        "donde estoy ahora",
        "que app es esta",
        "en que pantalla estoy"
    )

    private val whatCanIDo = setOf(
        "que puedo hacer aca",
        "que puedo hacer ahora",
        "que opciones tengo",
        "que botones hay",
        "que hay para tocar"
    )

    private val summarize = setOf(
        "resumi la pantalla",
        "resumime esta pantalla",
        "resumi lo que ves",
        "que hay en pantalla",
        "que dice la pantalla"
    )

    private val important = setOf(
        "leeme lo importante",
        "que es lo importante",
        "lo urgente",
        "lo importante de la pantalla"
    )

    fun classify(rawText: String): ScreenSummaryMode? {
        val key = normalize(rawText)
        if (key.isBlank()) return null
        return when {
            key in whereAmI -> ScreenSummaryMode.WHERE_AM_I
            key in whatCanIDo -> ScreenSummaryMode.WHAT_CAN_I_DO
            key in summarize -> ScreenSummaryMode.SHORT
            key in important -> ScreenSummaryMode.IMPORTANT
            key.contains("detall") && key.contains("pantalla") -> ScreenSummaryMode.DETAILED
            else -> null
        }
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace(Regex("[¿?¡!.,;:]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
