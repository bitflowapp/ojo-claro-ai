package com.ojoclaro.android.agent.runtime.routine

import java.text.Normalizer

/**
 * Reconocimiento determinista de comandos explícitos de Routine Learning.
 *
 * Reglas estrictas:
 *  - Solo reconoce frases muy específicas. Sin LLM. Sin inferencia.
 *  - Devuelve [HumanRoutineMemoryCommand.NotARoutineCommand] si no matchea.
 *    El caller debe seguir su flujo normal (REPEAT_LAST, ScreenUnderstanding,
 *    WhatsApp Guided/Visible Chats, orchestrator legacy).
 *  - NUNCA matchea: "repetí", "qué hay en pantalla", "qué chats ves",
 *    "abrí WhatsApp", "mandale a Marco", "callate", "confirmar", "cancelar",
 *    "ayuda".
 */
object HumanRoutineCommandParser {

    fun parse(rawText: String): HumanRoutineMemoryCommand {
        val key = normalize(rawText)
        if (key.isBlank()) return HumanRoutineMemoryCommand.NotARoutineCommand

        // === Disable / forget all preferences ===
        if (key in DISABLE_LEARNING_PHRASES) return HumanRoutineMemoryCommand.DisableLearning
        if (key in FORGET_ALL_PREFERENCES_PHRASES) return HumanRoutineMemoryCommand.ForgetAllPreferences
        if (key in ENABLE_LEARNING_PHRASES) return HumanRoutineMemoryCommand.EnableLearning

        // === Preferences (response length / speed / clarity) ===
        if (key in SHORT_RESPONSE_PHRASES) {
            return HumanRoutineMemoryCommand.SetPreference(
                key = RoutinePreferenceKeys.RESPONSE_LENGTH,
                value = RoutinePreferenceValues.LENGTH_SHORT
            )
        }
        if (key in NORMAL_RESPONSE_PHRASES) {
            return HumanRoutineMemoryCommand.SetPreference(
                key = RoutinePreferenceKeys.RESPONSE_LENGTH,
                value = RoutinePreferenceValues.LENGTH_NORMAL
            )
        }
        if (key in SLOW_SPEECH_PHRASES) {
            return HumanRoutineMemoryCommand.SetPreference(
                key = RoutinePreferenceKeys.RESPONSE_SPEED,
                value = RoutinePreferenceValues.SPEED_SLOW
            )
        }
        if (key in CLEAR_SPEECH_PHRASES) {
            return HumanRoutineMemoryCommand.SetPreference(
                key = RoutinePreferenceKeys.RESPONSE_CLARITY,
                value = RoutinePreferenceValues.CLARITY_CLEAR
            )
        }

        // === Forget last quick message ===
        if (key in FORGET_QUICK_MESSAGE_PHRASES) {
            return HumanRoutineMemoryCommand.ForgetLastQuickMessage
        }

        // === Save quick message: "recordá este mensaje rápido: <texto>" ===
        SAVE_QUICK_MESSAGE_REGEX.matchEntire(key)?.let { match ->
            val text = match.groupValues[1].trim()
            if (text.isNotBlank()) {
                return HumanRoutineMemoryCommand.SaveQuickMessage(text = text)
            }
        }
        // === Save quick message: "guardá '<texto>' como mensaje rápido" ===
        SAVE_QUICK_MESSAGE_QUOTED_REGEX.matchEntire(key)?.let { match ->
            val text = match.groupValues[1].trim()
            if (text.isNotBlank()) {
                return HumanRoutineMemoryCommand.SaveQuickMessage(text = text)
            }
        }

        // === Forget contact: "olvidá a X como contacto frecuente" ===
        FORGET_CONTACT_REGEX.matchEntire(key)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotBlank()) {
                return HumanRoutineMemoryCommand.ForgetFrequentContact(name = name)
            }
        }

        // === Save primary contact: "recordá que X es mi contacto principal" ===
        PRIMARY_CONTACT_REGEX.matchEntire(key)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotBlank()) {
                return HumanRoutineMemoryCommand.SetFrequentContact(name = name, isPrimary = true)
            }
        }
        // === Save frequent contact: "recordá que X es contacto frecuente" ===
        FREQUENT_CONTACT_REGEX.matchEntire(key)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotBlank()) {
                return HumanRoutineMemoryCommand.SetFrequentContact(name = name, isPrimary = false)
            }
        }

        return HumanRoutineMemoryCommand.NotARoutineCommand
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace(Regex("[¿?¡!.,;]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // ===== Frase fija sets =====

    private val SHORT_RESPONSE_PHRASES: Set<String> = setOf(
        "hablame mas corto",
        "hablame corto",
        "respuestas cortas",
        "usa respuestas cortas",
        "respuestas mas cortas",
        "no me leas todo",
        "no me leas todo resumime",
        "resumime todo",
        "respondeme corto"
    )

    private val NORMAL_RESPONSE_PHRASES: Set<String> = setOf(
        "hablame normal",
        "respuestas normales",
        "respuestas mas largas"
    )

    private val SLOW_SPEECH_PHRASES: Set<String> = setOf(
        "hablame mas lento",
        "habla mas lento",
        "hablar mas lento",
        "voz mas lenta"
    )

    private val CLEAR_SPEECH_PHRASES: Set<String> = setOf(
        "repeti mas claro",
        "hablame mas claro",
        "habla mas claro",
        "voz mas clara",
        "mas claro"
    )

    private val DISABLE_LEARNING_PHRASES: Set<String> = setOf(
        "no guardes preferencias",
        "no guardes mis preferencias",
        "no aprendas de mi",
        "no aprendas",
        "no quiero que aprendas"
    )

    private val ENABLE_LEARNING_PHRASES: Set<String> = setOf(
        "podes aprender de mi",
        "aprende de mi",
        "si quiero que aprendas"
    )

    private val FORGET_ALL_PREFERENCES_PHRASES: Set<String> = setOf(
        "olvida mis preferencias",
        "olvida todas mis preferencias",
        "borra mis preferencias",
        "borra todas mis preferencias"
    )

    private val FORGET_QUICK_MESSAGE_PHRASES: Set<String> = setOf(
        "olvida ese mensaje rapido",
        "olvida el mensaje rapido",
        "borra el mensaje rapido"
    )

    /** "recordá este mensaje rápido: ..." */
    private val SAVE_QUICK_MESSAGE_REGEX: Regex = Regex(
        "^(?:recorda|guarda) (?:este )?mensaje rapido[: ]+(.+)$"
    )

    /** "guardá '...' como mensaje rápido" — comilla simple/doble/curva soportada. */
    private val SAVE_QUICK_MESSAGE_QUOTED_REGEX: Regex = Regex(
        "^(?:recorda|guarda) ['\"`](.+?)['\"`] como mensaje rapido$"
    )

    /** "olvidá a X como contacto frecuente" */
    private val FORGET_CONTACT_REGEX: Regex = Regex(
        "^olvida (?:a )?(.+?) como contacto (?:frecuente|principal)$"
    )

    /** "recordá que X es mi contacto principal" */
    private val PRIMARY_CONTACT_REGEX: Regex = Regex(
        "^recorda que (.+?) es (?:mi )?contacto principal$"
    )

    /** "recordá que X es (mi )?contacto frecuente" */
    private val FREQUENT_CONTACT_REGEX: Regex = Regex(
        "^recorda que (.+?) es (?:mi )?contacto frecuente$"
    )
}
