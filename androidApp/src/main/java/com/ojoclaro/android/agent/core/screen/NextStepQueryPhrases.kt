package com.ojoclaro.android.agent.core.screen

import java.text.Normalizer

/**
 * Clasificador determinista de consultas tipo "qué hago ahora".
 *
 * Reconoce frases que el usuario ciego usa cuando NO sabe qué tocar ni qué
 * pantalla tiene delante. Se mantiene separado de [ScreenQueryPhrases] para
 * no chocar con sus sets ("qué puedo hacer acá" → resumen WHAT_CAN_I_DO,
 * "qué hago ahora" → advisor accionable).
 *
 * Devuelve un [NextStepQueryKind] si reconoce, o null si no aplica. La capa
 * de wiring decide qué hacer con cada kind.
 */
object NextStepQueryPhrases {

    private val whatNow = setOf(
        "que hago ahora",
        "que hago aca",
        "que toco",
        "que tengo que tocar",
        "que tengo que apretar",
        "que aprieto",
        "que tengo que hacer",
        "como sigo",
        "como continuo"
    )

    private val whereButton = setOf(
        "donde esta el boton",
        "donde queda el boton",
        "donde toco",
        "donde aprieto",
        "donde toco para continuar",
        "donde toco para seguir"
    )

    private val helpScreen = setOf(
        "ayudame con esta pantalla",
        "ayudame con la pantalla",
        "guiame con esta pantalla",
        "explicame esta pantalla",
        "ayuda con esta pantalla"
    )

    fun classify(rawText: String): NextStepQueryKind? {
        val key = normalize(rawText)
        if (key.isBlank()) return null
        return when {
            key in whatNow -> NextStepQueryKind.WHAT_NOW
            key in whereButton -> NextStepQueryKind.WHERE_IS_BUTTON
            key in helpScreen -> NextStepQueryKind.HELP_WITH_SCREEN
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

enum class NextStepQueryKind {
    WHAT_NOW,
    WHERE_IS_BUTTON,
    HELP_WITH_SCREEN
}
