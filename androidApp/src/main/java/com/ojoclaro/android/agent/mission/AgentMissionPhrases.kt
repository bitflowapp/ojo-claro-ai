package com.ojoclaro.android.agent.mission

import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Detección de activación del modo agente (Fase 3A).
 *
 * Regla de oro: las rutas locales simples SIEMPRE ganan. Este detector solo
 * dispara con objetivos compuestos o frases de activación explícitas; nunca
 * con comandos de un paso ("leé la pantalla", "abrí whatsapp", "volver",
 * "callar"), que ya tienen ruta local determinista.
 */
object AgentMissionPhrases {

    private val EXPLICIT_TRIGGERS = listOf(
        "hace una mision",
        "hace la mision",
        "modo agente",
        "necesito que hagas varias cosas",
        "comproba si estas lista",
        "comproba que estes lista",
        "fijate si estas lista",
        "revisa todo y despues",
        "revisa todo y volve"
    )

    /** Verbos de chequeo + sustantivos del dominio "preparación". */
    private val CHECK_VERBS = listOf("comproba", "revisa", "verifica", "chequea", "controla", "fijate")
    private val READINESS_NOUNS = listOf(
        "accesibilidad",
        "microfono",
        "conexion",
        "backend",
        "permiso",
        "permisos",
        "servicio"
    )

    /** Comandos simples que NUNCA deben convertirse en misión. */
    private val SIMPLE_LOCAL_COMMANDS = listOf(
        "lee la pantalla",
        "leeme la pantalla",
        "lee pantalla",
        "que aparece",
        "que hay en pantalla",
        "abri whatsapp",
        "abre whatsapp",
        "volver",
        "volve",
        "atras",
        "callar",
        "callate",
        "silencio",
        "lee los chats",
        "leeme los chats",
        "leeme los mensajes",
        "lee los mensajes"
    )

    fun isSimpleLocalCommand(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return SIMPLE_LOCAL_COMMANDS.any { normalized == it || normalized == "$it por favor" }
    }

    /**
     * ¿El texto expresa un objetivo compuesto para el Agent Core?
     *
     * true solo si:
     *  - NO es un comando simple local; y
     *  - contiene un trigger explícito, o
     *  - combina un verbo de chequeo + un sustantivo de preparación, o
     *  - encadena chequeo + continuación ("... y despues ..." / "... y volve ...").
     */
    fun isMissionGoal(rawText: String): Boolean {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return false
        if (isSimpleLocalCommand(normalized)) return false

        if (EXPLICIT_TRIGGERS.any { normalized.contains(it) }) return true

        val hasCheckVerb = CHECK_VERBS.any { normalized.contains(it) }
        val hasReadinessNoun = READINESS_NOUNS.any { normalized.contains(it) }
        if (hasCheckVerb && hasReadinessNoun) return true

        val chained = normalized.contains(" y despues ") || normalized.contains(" despues ")
        if (hasCheckVerb && chained) return true

        return false
    }

    /** Frases de cancelación de misión (totales, no solo callar TTS). */
    fun isCancelCommand(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return normalized in setOf(
            "cancela",
            "cancelar",
            "cancela la mision",
            "cancelar mision",
            "para",
            "para la mision",
            "detener mision",
            "detene la mision"
        )
    }

    private fun normalize(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            // STT real llega con tildes ("comprobá", "estás"); los triggers
            // están des-acentuados. VoicePhraseNormalizer las conserva a
            // propósito para otros consumidores, así que des-acentuamos acá.
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace('ñ', 'n')
            .replace(Regex("[¿?¡!.,;]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
