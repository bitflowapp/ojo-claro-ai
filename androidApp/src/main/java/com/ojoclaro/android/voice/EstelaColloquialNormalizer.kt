package com.ojoclaro.android.voice

/**
 * V1.11 — capa de lenguaje argentino real ANTES del routing.
 *
 * Convierte frases coloquiales a las formas canónicas que los parsers
 * deterministas ya entienden. Corre UNA sola vez en la entrada del
 * GlobalAssistantService (después de WakeWordStripper); los parsers no
 * cambian y el path foreground queda intacto.
 *
 * Reglas:
 *  - reescrituras por FRASE EXACTA primero (más seguras), tokens después;
 *  - jamás reescribe dentro de un nombre propio probable: las frases token
 *    solo aplican a palabras sueltas;
 *  - si nada matchea, el texto pasa intacto.
 */
object EstelaColloquialNormalizer {

    /** Frases completas → comando canónico. Matching exacto tras plegar. */
    private val PHRASE_REWRITES: Map<String, String> = mapOf(
        // Pantalla
        "fijate que dice" to "lee la pantalla",
        "fijate que dice ahi" to "lee la pantalla",
        "fijate que hay" to "lee la pantalla",
        "que onda esto" to "explicame esta pantalla",
        "que onda esta pantalla" to "explicame esta pantalla",
        "que es esto" to "explicame esta pantalla",
        "contame que ves" to "lee la pantalla",
        // Lectura contextual ("¿qué dice ahí?"): lectura LOCAL de pantalla, jamás LLM.
        "que dice" to "lee la pantalla",
        "que dice ahi" to "lee la pantalla",
        "que dice aca" to "lee la pantalla",
        "que dice eso" to "lee la pantalla",
        "que dice la pantalla" to "lee la pantalla",
        "que pone ahi" to "lee la pantalla",
        "que pone aca" to "lee la pantalla",
        "lee eso" to "lee la pantalla",
        "leeme eso" to "lee la pantalla",
        // Tocar sin referente claro: primero hay que saber QUÉ hay.
        "toca ahi" to "que puedo tocar",
        "tocale ahi" to "que puedo tocar",
        "apreta ahi" to "que puedo tocar",
        // Chats
        "pasame al primero" to "abri el primer chat",
        "entra al primero" to "abri el primer chat",
        "abrime al primero" to "abri el primer chat",
        // Monitoreo de viaje
        "avisame cuando llegue" to "avisame cuando llegue el viaje",
        "avisame cuando este afuera" to "avisame cuando el viaje este afuera",
        "avisame cuando este llegando" to "avisame cuando el viaje este llegando",
        "avisame cuando este confirmado" to "avisame cuando el viaje este confirmado"
    )

    /** Tokens sueltos → forma canónica (solo palabra exacta). */
    private val TOKEN_REWRITES: Map<String, String> = mapOf(
        "garpar" to "pagar",
        "garpa" to "pagar",
        "garpo" to "pagar",
        "guita" to "plata",
        "mango" to "peso",
        "mangos" to "pesos",
        "bondi" to "colectivo",
        "tel" to "telefono",
        "finde" to "fin de semana",
        // V1.12 — canal Instagram: "insta"/"ig" → forma canónica que los
        // parsers de mensajería entienden. "dm"/"md" NO se reescriben acá:
        // los maneja InstagramDirectPhrases como marca de canal.
        "insta" to "instagram",
        "ig" to "instagram"
    )

    /**
     * Prefijos coloquiales que se PELAN (el resto sigue su ruta normal):
     * "che estela fijate que dice" → "fijate que dice".
     */
    private val LEADING_FILLERS = listOf(
        // Las más largas primero: "che estela" antes que "che".
        "che estela ", "hola estela ", "estela ", "che ",
        "bueno ", "dale ", "a ver ", "porfa ", "porfi "
    )

    /**
     * Verbo de apertura de chat: "buscá/buscar/encontrá/encontrar (el) chat/
     * conversación de X" → "abri ..." para tomar EXACTAMENTE el mismo flujo local
     * seguro que "abrí el chat de X" (mismo matcher anti-avatar). Requiere el
     * sustantivo de chat: "buscá la farmacia" (búsqueda de lugar) NO se reescribe.
     */
    private val openChatVerbRegex = Regex(
        "^(?:busca|buscar|buscame|encontra|encontrar|encontrame)( (?:el|la|un|una|mi))? " +
            "(chat|conversacion|charla|conversa)\\b"
    )

    fun normalize(rawText: String): String {
        val folded = fold(rawText)
        if (folded.isBlank()) return rawText

        var text = folded
        var stripped = true
        while (stripped) {
            stripped = false
            for (filler in LEADING_FILLERS) {
                if (text.startsWith(filler) && text.length > filler.length + 2) {
                    text = text.removePrefix(filler)
                    stripped = true
                    break
                }
            }
        }

        // "buscá/encontrá (el) chat de X" → "abri ..." (mismo flujo seguro que abrí).
        val openCanon = openChatVerbRegex.replace(text) { m ->
            "abri" + m.groupValues[1] + " " + m.groupValues[2]
        }
        if (openCanon != text) return openCanon

        PHRASE_REWRITES[text.removeSuffix(" por favor").trim()]?.let { return it }

        val tokens = text.split(' ')
        if (tokens.none { it in TOKEN_REWRITES }) return rawText.trim()
        return tokens.joinToString(" ") { token -> TOKEN_REWRITES[token] ?: token }
    }

    private fun fold(rawText: String): String =
        rawText
            .lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
