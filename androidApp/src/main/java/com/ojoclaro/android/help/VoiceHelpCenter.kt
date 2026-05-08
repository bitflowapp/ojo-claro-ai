package com.ojoclaro.android.help

/**
 * Centro de ayuda de voz.
 *
 * Regla de producto:
 * La ayuda debe ser corta, clara y usable sin mirar la pantalla.
 * No debe sonar como manual técnico ni leer una lista infinita.
 */
object VoiceHelpCenter {

    val CORE_EXAMPLES: List<String> = listOf(
        "preparar mensajes de WhatsApp con confirmación",
        "abrir Teléfono para llamar con marcador seguro",
        "leer texto con cámara",
        "qué dice la pantalla",
        "repetir la última respuesta",
        "cancelar una acción pendiente",
        "explicar si la IA flexible está apagada"
    )

    val MEMORY_EXAMPLES: List<String> = listOf(
        "recordá que prefiero respuestas cortas",
        "qué recordás de mí",
        "borrá tu memoria"
    )

    val SAFETY_EXAMPLES: List<String> = listOf(
        "advertime si aparece una transferencia",
        "no leas contraseñas",
        "cancelar"
    )

    val SPOKEN_HELP: String =
        "Puedo ayudarte con funciones reales de esta versión: ${CORE_EXAMPLES.joinToString(separator = "; ")}. " +
            "Para WhatsApp preparo mensajes, no los envío solo. Si la IA flexible está apagada, sigo con funciones locales."

    val MEMORY_HELP: String =
        "Para memoria, podés decir: ${MEMORY_EXAMPLES.joinToString(separator = "; ")}. " +
            "Siempre te voy a pedir confirmación antes de guardar o borrar."

    val SAFETY_HELP: String =
        "Para seguridad, puedo avisarte sobre transferencias, códigos, contraseñas o datos sensibles. " +
            "No guardo chats ni pantallas completas."

    fun spokenHelp(includeMemory: Boolean = true): String {
        return if (includeMemory) {
            SPOKEN_HELP
        } else {
            "Podés decir: ${CORE_EXAMPLES.joinToString(separator = "; ")}."
        }
    }
}
