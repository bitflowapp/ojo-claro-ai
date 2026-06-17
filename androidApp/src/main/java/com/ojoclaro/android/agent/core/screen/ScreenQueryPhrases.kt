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
        "que hay para tocar",
        // V1.10.3 — la forma más natural en el Moto real.
        "que puedo tocar",
        "que se puede tocar"
    )

    private val summarize = setOf(
        "resumi la pantalla",
        "resumime esta pantalla",
        "resumi lo que ves",
        "que hay en pantalla",
        "que hay en la pantalla",
        "que dice la pantalla"
    )

    // Frases explícitas de "leeme la pantalla" / "qué estoy viendo". Se mapean a
    // un resumen corto (SHORT): la respuesta tiene que ser útil y breve para una
    // persona no vidente.
    private val readScreen = setOf(
        "lee la pantalla",
        "leer la pantalla",
        "lee pantalla",
        "leer pantalla",
        "leeme la pantalla",
        "leeme la pantalla actual",
        "leeme lo que hay en pantalla",
        "leeme lo que aparece",
        "leeme lo que aparece en pantalla",
        "leeme lo de la pantalla",
        "que estoy viendo",
        "que veo",
        "que aparece en pantalla",
        // V1.10.3 — "qué aparece" a secas (uso real tras abrir Maps/DiDi).
        "que aparece",
        "que aparece aca",
        "decime que aparece",
        "deci que aparece",
        "decime que hay en pantalla",
        "que dice aca",
        "describi la pantalla",
        "describime la pantalla",
        // Hardening Alexa-like: "leeme esto" / "leeme lo que dice" mirando la
        // pantalla. (La lectura de texto por cámara usa "leer texto", aparte.)
        "leeme esto",
        "leme esto",
        "lee esto",
        "leeme lo que dice",
        "leeme lo que dice aca",
        "que dice esto"
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
            key in readScreen -> ScreenSummaryMode.SHORT
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
