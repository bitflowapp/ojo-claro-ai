package com.ojoclaro.android.agent.runtime.whatsapp

import java.text.Normalizer

/**
 * Parser puro para "abrí el primer/segundo/... chat" (apertura por posición).
 *
 * Devuelve el índice 0-based del chat pedido, o null si no es un comando de
 * apertura por ordinal. No resuelve el nombre: eso lo hace el use case con el
 * snapshot real (la lista visible de chats).
 *
 * Reglas:
 *  - Requiere un verbo de apertura explícito (abrí, entrá, tocá, abrime...).
 *  - Acepta ordinales en palabra (primer..quinto) o número (1..20).
 *  - No matchea nombres ni acciones sensibles.
 */
object WhatsAppOrdinalChatParser {

    fun parse(rawText: String): Int? {
        val key = normalize(rawText)
        if (key.isBlank()) return null

        // "abrí el de arriba" / "abrí el chat de arriba" => primero (índice 0).
        if (TOP_REGEX.matchEntire(key) != null) return 0

        for (regex in ORDINAL_WORD_REGEXES) {
            val match = regex.matchEntire(key) ?: continue
            val word = match.groupValues[1]
            val index = ORDINAL_WORDS[word] ?: continue
            return index
        }

        for (regex in ORDINAL_NUMBER_REGEXES) {
            val match = regex.matchEntire(key) ?: continue
            val number = match.groupValues[1].toIntOrNull() ?: continue
            if (number < 1 || number > MAX_SUPPORTED_NUMBER) return null
            return number - 1
        }

        // "abrí el chat número dos" (número en palabra).
        for (regex in ORDINAL_NUMBER_WORD_REGEXES) {
            val match = regex.matchEntire(key) ?: continue
            val number = NUMBER_WORDS[match.groupValues[1]] ?: continue
            return number - 1
        }

        return null
    }

    private const val MAX_SUPPORTED_NUMBER = 20

    private const val VERB =
        "(?:abri|abrime|abrir|abre|entra|entrar|anda|andate|toca|tocar|selecciona|seleccionar)"

    private val ORDINAL_WORDS: Map<String, Int> = mapOf(
        "primer" to 0,
        "primero" to 0,
        "primera" to 0,
        "segundo" to 1,
        "segunda" to 1,
        "tercer" to 2,
        "tercero" to 2,
        "tercera" to 2,
        "cuarto" to 3,
        "cuarta" to 3,
        "quinto" to 4,
        "quinta" to 4
    )

    private const val ORDINAL_WORD_GROUP =
        "(primer|primero|primera|segundo|segunda|tercer|tercero|tercera|cuarto|cuarta|quinto|quinta)"

    private val ORDINAL_WORD_REGEXES: List<Regex> = listOf(
        Regex(
            "^(?:estela\\s*,?\\s*)?$VERB\\s+(?:a\\s+)?(?:el|la|al)\\s+$ORDINAL_WORD_GROUP" +
                "\\s+(?:chat|conversacion|contacto|mensaje)(?:\\s+visible)?\\s*$"
        ),
        Regex(
            "^(?:estela\\s*,?\\s*)?$VERB\\s+(?:a\\s+)?(?:el|la|al)\\s+$ORDINAL_WORD_GROUP\\s*$"
        )
    )

    private val ORDINAL_NUMBER_REGEXES: List<Regex> = listOf(
        Regex(
            "^(?:estela\\s*,?\\s*)?$VERB\\s+(?:a\\s+)?(?:el|la|al)?\\s*" +
                "(?:chat|conversacion|contacto)\\s+(?:numero\\s+)?(\\d{1,2})\\s*$"
        )
    )

    // "abrí el de arriba" / "entrá al de arriba" / "abrí el chat de arriba".
    private val TOP_REGEX: Regex = Regex(
        "^(?:estela\\s*,?\\s*)?$VERB\\s+(?:a\\s+)?(?:el|la|al)\\s+" +
            "(?:chat\\s+|conversacion\\s+|contacto\\s+)?de\\s+arriba\\s*$"
    )

    // Números en palabra: "abrí el chat número dos".
    private val NUMBER_WORDS: Map<String, Int> = mapOf(
        "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10
    )

    private const val NUMBER_WORD_GROUP =
        "(uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)"

    private val ORDINAL_NUMBER_WORD_REGEXES: List<Regex> = listOf(
        Regex(
            "^(?:estela\\s*,?\\s*)?$VERB\\s+(?:a\\s+)?(?:el|la|al)?\\s*" +
                "(?:chat|conversacion|contacto)\\s+numero\\s+$NUMBER_WORD_GROUP\\s*$"
        )
    )

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
