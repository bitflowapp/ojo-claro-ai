package com.ojoclaro.android.agent.runtime.whatsapp

import java.text.Normalizer
import java.util.Locale

/**
 * WhatsApp Anxiety Hardening — clasificador PURO de los comandos de "usabilidad
 * bajo ansiedad": modo seguro, estado de WhatsApp, "qué pasó", orientación
 * genérica ("dónde estoy"/"qué estoy viendo"/"estoy perdido") y ayuda
 * contextual.
 *
 * No toca nada y no expone contenido. Robusto a tildes/mayúsculas/voseo.
 *
 * IMPORTANTE — convivencia con otras rutas:
 *  - La orientación GENÉRICA ("dónde estoy") la comparten Outdoor (GPS) y la
 *    lectura de pantalla. Por eso [isGenericWhereAmI] está pensado para rutearse
 *    SOLO cuando WhatsApp es el contexto activo (lo decide
 *    GlobalAssistantService); si no, debe caer a Outdoor como siempre.
 *  - Las frases que NOMBRAN WhatsApp ([isWhatsAppStateQuery]) sí son
 *    inequívocas y valen aunque WhatsApp esté cerrado.
 *  - La ayuda genérica ("ayuda"/"qué puedo hacer") la sigue resolviendo
 *    VoiceCommandDispatcher; acá solo las variantes con marca de lugar
 *    ("qué puedo hacer ACÁ").
 */
object WhatsAppAnxietyPhrases {

    private val SAFE_MODE_ON = setOf(
        "modo seguro", "modo ansiedad", "modo calma", "modo tranquilo", "modo facil",
        "hablame simple", "habla simple", "hablame mas simple", "hablame facil",
        "mas corto", "se mas corto", "decime corto", "decime simple",
        "explicame simple", "mas tranquilo", "modo lento"
    )

    private val SAFE_MODE_OFF = setOf(
        "modo normal", "sali del modo seguro", "salir del modo seguro",
        "desactiva el modo seguro", "desactivar modo seguro", "quita el modo seguro",
        "podes hablar normal", "habla normal", "hablame normal", "modo largo"
    )

    private val WA_ALIASES = setOf(
        "whatsapp", "whats app", "wasap", "guasap", "watsap", "whasap", "wsp", "wpp"
    )

    // Marcas de "¿cómo está / dónde estoy en?" que, junto a WhatsApp, son
    // una consulta de estado inequívoca.
    private val WA_STATE_MARKERS = listOf(
        "que pasa en", "que pasa con", "que hay en", "que onda", "como va",
        "como vamos", "en que parte de", "donde estoy en", "que estoy viendo en",
        "estoy en", "que pantalla de", "en que estoy en"
    )

    private val WHAT_HAPPENED = setOf(
        "que paso", "que paso recien", "que paso ahora", "que acaba de pasar",
        "que esta pasando", "que hiciste", "que hiciste recien",
        "en que quedamos", "que estabamos haciendo", "que fue lo que paso"
    )

    private val GENERIC_WHERE_AM_I = setOf(
        "donde estoy", "donde estoy ahora", "donde estoy parado", "donde estoy parada",
        "que estoy viendo", "que estoy mirando", "en que pantalla estoy",
        "que pantalla es esta", "que pantalla es", "en que estoy",
        "estoy perdido", "estoy perdida", "me perdi", "no se donde estoy",
        "no entiendo donde estoy", "estoy confundido", "estoy confundida",
        "no entiendo nada", "ayuda estoy perdido", "ayuda estoy perdida"
    )

    private val CONTEXTUAL_HELP = setOf(
        "que puedo hacer aca", "que puedo hacer aqui", "que puedo hacer ahora",
        "que opciones tengo", "que hago aca", "que hago ahora", "que hago aqui",
        "ayudame con esto", "que mas puedo hacer", "que mas puedo decir aca"
    )

    fun isSafeModeOn(text: String): Boolean = norm(text) in SAFE_MODE_ON

    fun isSafeModeOff(text: String): Boolean = norm(text) in SAFE_MODE_OFF

    /** Consulta de estado que NOMBRA WhatsApp: inequívoca, vale con la app cerrada. */
    fun isWhatsAppStateQuery(text: String): Boolean {
        val n = norm(text)
        if (WA_ALIASES.none { n.contains(it) }) return false
        return WA_STATE_MARKERS.any { n.contains(it) }
    }

    fun isWhatHappened(text: String): Boolean = norm(text) in WHAT_HAPPENED

    /** Orientación genérica: rutear a WhatsApp SOLO si WhatsApp es el contexto. */
    fun isGenericWhereAmI(text: String): Boolean = norm(text) in GENERIC_WHERE_AM_I

    fun isContextualHelp(text: String): Boolean = norm(text) in CONTEXTUAL_HELP

    private fun norm(text: String): String {
        val lower = text.lowercase(Locale("es", "AR"))
        val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
