package com.ojoclaro.android.voice

import java.text.Normalizer
import java.util.Locale

/**
 * WhatsApp Fluency — frases de "dame tiempo / volvé a escucharme" para una
 * persona ansiosa o que cree que Estela la cortó.
 *
 * Clasificador PURO, robusto a tildes/voseo. No toca nada y no expone contenido.
 * Ambas intenciones son READ-ONLY: NO cancelan, NO confirman, NO envían —
 * preservan cualquier pendiente en curso (lo decide GlobalAssistantService).
 *
 *  - [isHold]: "esperá", "dame un segundo" → "te espero", sin tocar el pendiente.
 *  - [isReengage]: "te cortaste", "no me escuchaste" → re-ofrecer / repetir lo
 *    último sin tocar WhatsApp.
 *
 * Disjunto de sí/dale/mandalo/enviá/cancelar/no por diseño: corre antes de los
 * pendientes y jamás debe consumir una confirmación de envío.
 */
object VoiceFluencyPhrases {

    private val HOLD = setOf(
        "espera", "espera espera", "esperate", "esperame", "esperenme",
        "espera un momento", "espera un segundo", "espera un toque", "espera un cachito",
        "esperame un momento", "esperame un segundo", "esperate un momento", "esperate un toque",
        "dame un momento", "dame un segundo", "dame un minuto", "dame un toque", "dame un cachito",
        "pera", "pera pera", "perame", "aguanta", "aguantame", "aguanta un momento",
        "un momento", "un momentito", "un segundo", "un segundito", "momentito", "pera un toque"
    )

    private val REENGAGE = setOf(
        "te cortaste", "te corto", "se corto", "se corto todo", "se corto la voz",
        "no me escuchaste", "no escuchaste", "no me escuchas", "no me escuchaste bien",
        "no me oiste", "no me oyes", "no me has escuchado", "me escuchas", "me escuchaste",
        "estas ahi", "segui ahi", "seguis ahi", "hola estas ahi", "no te escuche",
        "no se si me escuchaste", "te quedaste"
    )

    fun isHold(rawText: String): Boolean = norm(rawText) in HOLD

    fun isReengage(rawText: String): Boolean = norm(rawText) in REENGAGE

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
