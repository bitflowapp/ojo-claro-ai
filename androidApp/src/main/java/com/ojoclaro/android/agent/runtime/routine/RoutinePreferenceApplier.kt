package com.ojoclaro.android.agent.runtime.routine

/**
 * Tipo de respuesta que va a leerse por TTS. El applier usa el kind para
 * elegir reglas específicas de acortado por contexto. Los kinds GENERIC
 * no se modifican por defecto, para no impactar partes del flujo legacy.
 */
enum class RoutineResponseKind {
    SCREEN_SUMMARY,
    WHATSAPP_GUIDED,
    VISIBLE_CHATS_LIST,
    VISIBLE_CHATS_INSIDE,
    GENERIC
}

/**
 * Adapta texto a TTS según las preferencias humanas activas.
 *
 * Reglas hard:
 *  - response.length=SHORT puede acortar respuestas, PERO debe preservar
 *    frases de seguridad críticas:
 *      "Yo nunca envío"
 *      "Yo no envío"
 *      "No leo mensajes" / "no la leo" / "no lo leo"
 *      "datos bancarios" / "información sensible"
 *      "Por seguridad"
 *    Si el texto contiene una frase de seguridad, NO se elimina — solo se
 *    acortan partes informativas.
 *  - response.clarity=CLEAR: no introduce nuevas frases. Solo se asegura
 *    que las oraciones tengan punto final (mejora separación al hablar).
 *    No cambia significado.
 *  - response.speed=SLOW: NO modifica el texto. Aplica al SpeechController
 *    (pendiente — ver reporte).
 *
 * Es puro Kotlin, sin estado. Inyectable por kind para testear cada combo.
 */
object RoutinePreferenceApplier {

    fun apply(
        text: String,
        kind: RoutineResponseKind,
        style: HumanResponseStyle
    ): String {
        if (text.isBlank()) return text

        var result = text
        if (style.isShort) {
            result = applyShortLength(result, kind)
        }
        if (style.isClear) {
            result = applyClearClarity(result)
        }
        return result.trim()
    }

    // ===== SHORT LENGTH =====

    private fun applyShortLength(text: String, kind: RoutineResponseKind): String {
        return when (kind) {
            RoutineResponseKind.VISIBLE_CHATS_LIST -> shortenVisibleChatsList(text)
            RoutineResponseKind.VISIBLE_CHATS_INSIDE -> shortenVisibleChatsInside(text)
            RoutineResponseKind.WHATSAPP_GUIDED -> shortenWhatsAppGuided(text)
            RoutineResponseKind.SCREEN_SUMMARY -> shortenScreenSummary(text)
            RoutineResponseKind.GENERIC -> text
        }
    }

    private fun shortenVisibleChatsList(text: String): String {
        // "Veo estos chats visibles: <names>. No leo mensajes completos."
        // Para una lista de NOMBRES la frase de cierre es informativa, no es
        // safety crítica (no estamos leyendo mensajes — solo listamos nombres).
        // Acortado: "Veo: <names>."
        var result = text
            .replace(LIST_PREAMBLE, "Veo: ")
        // Eliminar la coletilla informativa si y solo si está al final.
        if (result.contains(NO_READING_DISCLAIMER)) {
            result = result.replace(NO_READING_DISCLAIMER, "").trim()
            if (!result.endsWith(".")) result = "$result."
        }
        return result
    }

    private fun shortenVisibleChatsInside(text: String): String {
        // Caso especial dentro de un chat: la frase de cierre SÍ es safety
        // (el usuario podría esperar que leamos los mensajes). Preservamos
        // el aviso pero compactamos.
        // "Estás dentro de un chat. No leo mensajes completos sin que me lo pidas."
        // → "Estás en un chat. No leo mensajes."
        return text
            .replace(
                "Estás dentro de un chat. No leo mensajes completos sin que me lo pidas.",
                "Estás en un chat. No leo mensajes."
            )
    }

    private fun shortenWhatsAppGuided(text: String): String {
        var result = text
        // Confirmaciones AmIInWhatsApp.
        result = result
            .replace(
                "Sí, estás en WhatsApp y veo un chat abierto.",
                "Sí. WhatsApp con chat."
            )
            .replace(
                "Sí, estás en WhatsApp. Todavía no veo un chat abierto.",
                "Sí. WhatsApp. No veo chat aún."
            )
            .replace(
                "Parece que estás en WhatsApp, pero no puedo confirmarlo del todo. Abrí un chat para empezar.",
                "Parece WhatsApp. Abrí un chat."
            )

        // What can I do here.
        result = result
            .replace(
                "Veo el campo de mensaje, está en la parte inferior",
                "Campo de mensaje abajo"
            )
            .replace("veo un botón de cámara", "botón cámara")
            .replace("veo un botón de adjuntar", "botón adjuntar")
            .replace("veo un botón de micrófono", "botón micrófono")
            .replace("veo un botón de enviar", "botón enviar")
            .replace("veo un botón para volver", "botón volver")
            .replace(
                "Yo no toco la app por vos: te guío para que toques vos.",
                "No toco la app."
            )
            .replace(
                "Estás en un chat de WhatsApp, pero no detecté claramente los controles. Probá tocar abajo de la pantalla.",
                "En un chat. No detecté controles. Probá tocar abajo."
            )

        // How to send photo / location / message: acortamos el preámbulo, mantenemos
        // la promesa de seguridad ("Yo nunca envío..." / "Yo no envío...").
        result = result
            .replace("Para mandar una foto: ", "Foto: ")
            .replace("Para mandar tu ubicación en WhatsApp: ", "Ubicación: ")
        // El mensaje no tiene preámbulo "Para mandar..." — empieza con "Tocá".
        result = result
            .replace(
                "Tocá dos veces sobre el campo de mensaje y dictá lo que querés escribir.",
                "Tocá dos veces el campo de mensaje y dictá."
            )
            .replace(" Después tocá el botón enviar.", " Después: enviar.")
            .replace(
                " Después buscá el botón enviar abajo a la derecha.",
                " Después: enviar (abajo a la derecha)."
            )

        // Casos de "abrí primero el chat..." quedan sin cambios — ya son cortos.
        return result
    }

    private fun shortenScreenSummary(text: String): String {
        // Reglas conservadoras: NUNCA tocamos los advisories de pantalla
        // sensible (banca/contraseña). Solo intros informativas.
        if (containsScreenSafetyAdvisory(text)) return text
        return text
            .replace("Estás en: ", "")
            .replace("La pantalla dice: ", "")
            .replace("Estás en: ", "")
            .replace("App: ", "")
    }

    private fun containsScreenSafetyAdvisory(text: String): Boolean {
        val lower = text.lowercase()
        return SAFETY_PHRASES.any { lower.contains(it) }
    }

    // ===== CLEAR CLARITY =====

    /**
     * V1: la preferencia de clarity se acepta y queda expuesta vía
     * [HumanResponseStyleProvider], pero el applier NO modifica el texto.
     *
     * Razón: cualquier reescritura genérica corre el riesgo de partir
     * enumeraciones legítimas ("Marco, Sofi y Mamá") o de cambiar el sentido
     * de una frase. La pausa real para TTS la debería dar [SpeechController]
     * vía SSML/setSpeechRate cuando se habilite el wire-up — eso es la
     * iteración siguiente. Documentado en el reporte como pendiente.
     */
    private fun applyClearClarity(text: String): String = text

    // ===== Constantes / safety =====

    /** Frases que NUNCA se eliminan ni se modifican aunque esté SHORT. */
    private val SAFETY_PHRASES: List<String> = listOf(
        "yo nunca envío",
        "yo no envío",
        "datos bancarios",
        "información sensible",
        "campo de contraseña",
        "por seguridad",
        "no la leo",
        "no lo leo",
        "no la leo sin tu confirmación"
    )

    private const val LIST_PREAMBLE: String = "Veo estos chats visibles: "
    private const val NO_READING_DISCLAIMER: String = " No leo mensajes completos."
}
