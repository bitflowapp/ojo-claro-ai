package com.ojoclaro.android.outdoor

import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.10.1 — interpretación de la respuesta a "¿A dónde querés ir?".
 *
 * Después de "dónde estoy", Estela pregunta a dónde ir. La respuesta puede ser:
 *  - un destino ("a San Martín", "al hospital", "la plaza");
 *  - un comando completo ("llevame a la farmacia");
 *  - una negativa ("no", "nada", "después");
 *  - o cualquier otra cosa, que NO debe tratarse como destino (vuelve al
 *    routing normal: "leé la pantalla" sigue siendo leer la pantalla).
 *
 * Confirmar el inicio de una ruta NO es una acción sensible (no envía nada,
 * no gasta nada): un "sí" simple alcanza. El contrato de envío de WhatsApp
 * ("sí" jamás envía) no se toca.
 */
object OutdoorDestinationReply {

    private val DECLINE = setOf(
        "no", "no no", "nada", "ninguno", "ningun lado", "a ningun lado",
        "no por ahora", "ahora no", "despues", "mas tarde", "dejalo",
        "olvidalo", "cancelar", "cancela", "mejor no", "no gracias",
        "no quiero", "ya se", "ya se donde estoy"
    )

    private val YES = setOf(
        "si", "si si", "si dale", "dale", "si quiero", "quiero",
        "si por favor", "por favor", "obvio", "claro", "correcto",
        "exacto", "bueno", "ok", "okey", "si vamos", "vamos", "empeza",
        "si empeza", "arranca", "si arranca"
    )

    /** Prefijos hablados de destino ("a San Martín", "hasta la plaza"). */
    private val DESTINATION_PREFIXES = listOf(
        "quiero ir a ", "quiero ir al ", "llevame a ", "llevame al ",
        "hasta ", "para ", "al ", "a "
    )

    /**
     * Primeros tokens que delatan que la frase NO es un destino sino otro
     * comando o una pregunta. Solo aplica a respuestas SIN prefijo de
     * destino: "a la farmacia" nunca pasa por acá.
     */
    private val NOT_A_PLACE_FIRST_TOKENS = setOf(
        "lee", "leeme", "leer", "abri", "abrir", "mira", "mirar",
        "manda", "mandar", "mandale", "envia", "enviar", "escribi",
        "deci", "decile", "decime", "describi", "describir", "reproduci",
        "reproducir", "llama", "llamar", "llamale", "cancela", "cancelar",
        "callate", "callar", "para", "deten", "repeti", "repetir",
        "busca", "buscar", "guarda", "guardar", "pone", "poneme",
        "que", "cual", "como", "cuanto", "donde", "quien", "por",
        "hola", "gracias", "ayuda", "espera", "no", "recalcula"
    )

    private const val MAX_BARE_DESTINATION_TOKENS = 4

    /**
     * Lugares vagos que NO se pueden geocodificar ("allá", "cerca", "ahí"):
     * el caller debe pedir aclaración, jamás inventar un destino.
     */
    private val VAGUE_PLACES = setOf(
        "alla", "alli", "aca", "aqui", "ahi", "por alla", "por aca", "por ahi",
        "cerca", "lejos", "adelante", "atras", "derecho", "enfrente",
        "a la derecha", "a la izquierda", "alla lejos", "no se", "no se donde",
        "donde sea", "cualquier lado", "a cualquier lado"
    )

    fun isDecline(rawText: String): Boolean = fold(rawText) in DECLINE

    fun isYes(rawText: String): Boolean = fold(rawText) in YES

    fun isVaguePlace(rawText: String): Boolean = fold(rawText) in VAGUE_PLACES

    /**
     * Destino entendido, o null si la frase no parece un lugar. null le
     * devuelve la frase al routing normal: jamás secuestra otros comandos.
     */
    fun extractDestination(rawText: String): String? {
        val folded = fold(rawText)
        if (folded.isBlank() || isDecline(folded) || isYes(folded)) return null
        if (folded in VAGUE_PLACES) return null

        // "llevame a la farmacia" completo también es una respuesta válida.
        (OutdoorPhrases.parse(rawText) as? OutdoorPhrases.Command.NavigateTo)?.let {
            return it.destination.take(MAX_DESTINATION_CHARS)
        }
        // Cualquier OTRO comando outdoor (cancelar, describir...) no es destino.
        if (OutdoorPhrases.parse(rawText) != null) return null

        for (prefix in DESTINATION_PREFIXES) {
            if (folded.startsWith(prefix)) {
                return folded.removePrefix(prefix)
                    .removeSuffix(" por favor").trim()
                    .takeIf { it.length >= MIN_DESTINATION_CHARS }
                    ?.take(MAX_DESTINATION_CHARS)
            }
        }

        // Sin prefijo: solo frases cortas con pinta de lugar ("la plaza",
        // "san martin"), nunca comandos ni preguntas.
        val tokens = folded.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens.size > MAX_BARE_DESTINATION_TOKENS) return null
        if (tokens.first() in NOT_A_PLACE_FIRST_TOKENS) return null

        return folded.removeSuffix(" por favor").trim()
            .takeIf { it.length >= MIN_DESTINATION_CHARS }
            ?.take(MAX_DESTINATION_CHARS)
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private const val MIN_DESTINATION_CHARS = 3
    private const val MAX_DESTINATION_CHARS = 120
}
