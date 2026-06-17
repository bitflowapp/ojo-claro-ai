package com.ojoclaro.android.agent.situation

import java.text.Normalizer

/**
 * Normaliza un comando para matching determinista: minúsculas, sin acentos,
 * espacios colapsados. Función pura, sin APIs de Android (Normalizer es JDK).
 *
 * `internal` para que [IntentClassifier] y [SituationBrain] compartan exactamente
 * la misma normalización y vocabulario, sin duplicar lógica.
 */
internal fun normalizeSituationCommand(raw: String): String {
    val lower = raw.trim().lowercase()
    val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return stripped.replace(Regex("\\s+"), " ").trim()
}

/**
 * Vocabulario de control compartido entre el clasificador y el cerebro.
 *
 * Todas las palabras ya están normalizadas (sin acentos): "cancelá" -> "cancela",
 * "pará" -> "para", "sí" -> "si". El matching de estas palabras es EXACTO (no
 * `contains`) para evitar falsos positivos como "para" dentro de "preparale".
 */
internal object SituationVocabulary {
    /** Corte duro inmediato. */
    val EMERGENCY_STOP: Set<String> = setOf("callate", "para", "silencio", "stop")

    /** Cancelación blanda / control de cancelación. */
    val SOFT_CANCEL: Set<String> = setOf("cancela", "no", "dejalo", "olvidalo")

    /** Confirmaciones cortas. */
    val CONFIRMATION: Set<String> = setOf("si", "dale", "ok", "confirmo", "hacelo", "acepto")
}

/**
 * Clasificador puro de comandos naturales a [SituationIntent].
 *
 * Fase 2: NO está cableado al flujo real. Es determinista, sin estado, sin APIs
 * de Android. No reemplaza a LocalIntentParser; es una capa de agrupación de
 * alto nivel pensada para el Situation Brain.
 */
class IntentClassifier {

    /**
     * Clasifica un comando. Si hay un [activeGoal] vivo, se usa como desempate:
     * una confirmación corta se interpreta como CONTROL, y cualquier texto que
     * no matchee nada fuerte se interpreta como continuación del objetivo.
     */
    fun classify(
        rawCommand: String,
        normalizedCommand: String = rawCommand.trim().lowercase(),
        activeGoal: ActiveGoal? = null
    ): SituationIntent {
        val n = normalizeSituationCommand(normalizedCommand.ifBlank { rawCommand })
        if (n.isBlank()) return SituationIntent.UNKNOWN

        // 1. Corte duro (match exacto).
        if (n in SituationVocabulary.EMERGENCY_STOP) return SituationIntent.EMERGENCY_STOP

        // 2. Cancelación blanda / control (match exacto).
        if (n in SituationVocabulary.SOFT_CANCEL) return SituationIntent.CONTROL

        // 3. Pedido inseguro (tiene prioridad sobre todo lo demás).
        if (UNSAFE_TOKENS.any { n.contains(it) }) return SituationIntent.UNSAFE_REQUEST

        // 4. Confirmación corta dentro de un objetivo activo -> CONTROL.
        if (activeGoal != null && n in SituationVocabulary.CONFIRMATION) {
            return SituationIntent.CONTROL
        }

        // 5. Modo compañero (frases fuertes).
        if (HELP_ME_WORK_STRONG.any { n.contains(it) }) return SituationIntent.HELP_ME_WORK

        // 6. Guiar al usuario (antes que el "ayudame" débil de modo compañero).
        if (GUIDE_USER_PHRASES.any { n.contains(it) }) return SituationIntent.GUIDE_USER

        // 7. Modo compañero (frase débil "ayudame").
        if (HELP_ME_WORK_WEAK.any { n.contains(it) }) return SituationIntent.HELP_ME_WORK

        // 8. Leer pantalla.
        if (READ_SCREEN_PHRASES.any { n.contains(it) }) return SituationIntent.READ_SCREEN

        // 9. Resumir pantalla.
        if (SUMMARIZE_PHRASES.any { n.contains(it) }) return SituationIntent.SUMMARIZE_SCREEN

        // 10. Explicar lo visible.
        if (EXPLAIN_PHRASES.any { n.contains(it) }) return SituationIntent.EXPLAIN_WHAT_I_SEE

        // 11. Escribir mensaje.
        if (WRITE_MESSAGE_PHRASES.any { n.contains(it) }) return SituationIntent.WRITE_MESSAGE

        // 12. Llamar.
        if (CALL_PHRASES.any { n.contains(it) }) return SituationIntent.CALL_CONTACT

        // 13. Memoria.
        if (MEMORY_PHRASES.any { n.contains(it) }) return SituationIntent.MANAGE_MEMORY

        // 14. Abrir app.
        if (OPEN_APP_PHRASES.any { n.contains(it) }) return SituationIntent.OPEN_APP

        // 15. Continuación de objetivo activo: si nada matchea pero hay un goal,
        //     se interpreta como continuación de ese objetivo.
        if (activeGoal != null) return activeGoal.intent

        return SituationIntent.UNKNOWN
    }

    private companion object {
        val UNSAFE_TOKENS: List<String> = listOf(
            "banco",
            "contrasena",
            "clave",
            "transferi",
            "pagar",
            "token",
            "tarjeta",
            "mercado pago",
            "home banking"
        )

        val HELP_ME_WORK_STRONG: List<String> = listOf(
            "estoy trabajando",
            "acompaname",
            "modo companero",
            "estoy repartiendo"
        )

        val HELP_ME_WORK_WEAK: List<String> = listOf(
            "ayudame"
        )

        val GUIDE_USER_PHRASES: List<String> = listOf(
            "guiame",
            "ayudame a usar",
            "como hago"
        )

        val READ_SCREEN_PHRASES: List<String> = listOf(
            "leeme la pantalla",
            "leer pantalla",
            "que dice la pantalla"
        )

        val SUMMARIZE_PHRASES: List<String> = listOf(
            "resumime la pantalla",
            "resumen de pantalla",
            "resumi esto"
        )

        val EXPLAIN_PHRASES: List<String> = listOf(
            "que estoy viendo",
            "donde estoy",
            "que ves"
        )

        val WRITE_MESSAGE_PHRASES: List<String> = listOf(
            "avisale a",
            "mandale mensaje a",
            "escribile a",
            "preparale un mensaje a",
            "decile a"
        )

        val CALL_PHRASES: List<String> = listOf(
            "llama a",
            "llamar a"
        )

        val MEMORY_PHRASES: List<String> = listOf(
            "recorda",
            "guardar contacto",
            "olvida",
            "que recordas"
        )

        val OPEN_APP_PHRASES: List<String> = listOf(
            "abri",
            "abre",
            "abrir",
            "anda a"
        )
    }
}
