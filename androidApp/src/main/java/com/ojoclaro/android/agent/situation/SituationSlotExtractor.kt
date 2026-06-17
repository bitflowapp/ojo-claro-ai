package com.ojoclaro.android.agent.situation

/**
 * Extractor de slots simple y determinista para el Situation Brain.
 *
 * Puro: sin APIs de Android, sin NLP, solo matching de prefijos palabra por
 * palabra. Preserva el casing original del target ("WhatsApp", no "whatsapp").
 *
 * Fase 7: solo OPEN_APP y CALL_CONTACT. Cualquier otra intención devuelve "".
 */
object SituationSlotExtractor {

    /**
     * Prefijos de verbo (ya normalizados: minúsculas, sin acentos) por intención.
     * El target es lo que queda del comando después del prefijo.
     */
    private val PREFIXES_BY_INTENT: Map<SituationIntent, List<List<String>>> = mapOf(
        SituationIntent.OPEN_APP to listOf(
            listOf("abri"),
            listOf("abre"),
            listOf("abrir"),
            listOf("anda", "a")
        ),
        SituationIntent.CALL_CONTACT to listOf(
            listOf("llama", "a"),
            listOf("llamar", "a")
        )
    )

    /**
     * Prefijos para extraer contacto en comandos de mensaje o llamada.
     */
    private val CONTACT_PREFIXES: List<List<String>> = listOf(
        listOf("avisale", "a"),
        listOf("decile", "a"),
        listOf("mandale", "mensaje", "a"),
        listOf("escribile", "a"),
        listOf("preparale", "un", "mensaje", "a"),
        listOf("llama", "a"),
        listOf("llamar", "a")
    )

    /**
     * Primeras palabras (normalizadas) que indican un comando de acción nuevo,
     * NO una continuación de mensaje.
     */
    private val NON_MESSAGE_ACTION_PREFIXES: Set<String> = setOf(
        "abri", "abre", "abrir", "anda",
        "llama", "llamar",
        "leeme", "leer", "resumime", "resumi", "resumen",
        "avisale", "decile", "mandale", "escribile", "preparale"
    )

    /**
     * Extrae el target de un comando natural. Devuelve "" si no reconoce ningún
     * prefijo o si no queda nada después del prefijo.
     */
    fun extractTarget(command: String, intent: SituationIntent): String {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return ""
        val prefixes = PREFIXES_BY_INTENT[intent] ?: return ""

        val originalWords = trimmed.split(Regex("\\s+"))
        val normalizedWords = originalWords.map { normalizeSituationCommand(it) }

        for (prefix in prefixes) {
            if (normalizedWords.size > prefix.size &&
                normalizedWords.take(prefix.size) == prefix
            ) {
                return originalWords.drop(prefix.size)
                    .joinToString(" ")
                    .take(PendingAction.MAX_TARGET_CHARS)
            }
        }
        return ""
    }

    /**
     * Extrae el contacto / destinatario de un comando de mensaje o llamada.
     * Reconoce prefijos comunes ("avisale a", "decile a", "llamá a", etc.) y
     * devuelve la próxima palabra (o dos) preservando casing. Si encuentra
     * "que" se corta antes (el texto siguiente es el mensaje, no el contacto).
     */
    fun extractContact(command: String): String {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return ""
        val originalWords = trimmed.split(Regex("\\s+"))
        val normalizedWords = originalWords.map { normalizeSituationCommand(it) }

        for (prefix in CONTACT_PREFIXES) {
            if (normalizedWords.size > prefix.size &&
                normalizedWords.take(prefix.size) == prefix
            ) {
                val rest = originalWords.drop(prefix.size)
                val contactWords = rest.takeWhile {
                    normalizeSituationCommand(it) != "que"
                }
                return contactWords
                    .take(2)
                    .joinToString(" ")
                    .take(PendingAction.MAX_TARGET_CHARS)
            }
        }
        return ""
    }

    /**
     * Extrae el cuerpo del mensaje a partir de un comando que tiene la forma
     * "... $contact que <mensaje>". Si no encuentra la marca " que ",
     * devuelve "".
     */
    fun extractMessageForContact(command: String, contact: String): String {
        if (contact.isBlank() || command.isBlank()) return ""
        val pattern = Regex("\\sque\\s", RegexOption.IGNORE_CASE)
        val match = pattern.find(command.lowercase()) ?: return ""
        val splitAt = match.range.first
        val before = command.substring(0, splitAt)
        val beforeNormalized = normalizeSituationCommand(before)
        val normalizedContact = normalizeSituationCommand(contact)
        if (!beforeNormalized.contains(normalizedContact)) return ""
        val after = command.substring(splitAt)
        return after
            .replaceFirst(Regex("^\\s*que\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .take(PendingAction.MAX_PAYLOAD_VALUE_CHARS)
    }

    /**
     * True si el texto parece "contenido" de un mensaje (continuación de un
     * WRITE_MESSAGE), no un comando nuevo ni una confirmación/cancelación.
     */
    fun looksLikeMessageContinuation(command: String): Boolean {
        val n = normalizeSituationCommand(command)
        if (n.isBlank() || n.length < 3) return false
        if (n in SituationVocabulary.CONFIRMATION) return false
        if (n in SituationVocabulary.SOFT_CANCEL) return false
        if (n in SituationVocabulary.EMERGENCY_STOP) return false
        val firstWord = n.split(" ").firstOrNull() ?: return false
        if (firstWord in NON_MESSAGE_ACTION_PREFIXES) return false
        return true
    }

    /**
     * Arma el payload seguro de una PendingAction. Máximo 3 entradas, sin texto
     * de pantalla ni OCR. Todas las claves no van en blanco y los valores van
     * acotados en longitud.
     */
    fun buildPendingPayload(command: String, intent: SituationIntent): Map<String, String> {
        val payload = mutableMapOf<String, String>()
        payload["intent"] = intent.name

        val originalCommand = command.trim().take(PendingAction.MAX_ORIGINAL_COMMAND_CHARS)
        if (originalCommand.isNotBlank()) {
            payload["originalCommand"] = originalCommand
        }

        val target = extractTarget(command, intent)
        if (target.isNotBlank()) {
            payload["target"] = target
        }

        return payload.toMap()
    }
}
