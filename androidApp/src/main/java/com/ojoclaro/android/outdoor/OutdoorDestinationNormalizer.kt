package com.ojoclaro.android.outdoor

import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.10.2 — limpieza determinística del destino hablado ANTES de geocodificar.
 *
 * El geocoder (Pelias/ORS) entiende "san martin 500" pero NO "la calle san
 * martín al 500" (falla física real en Moto: terminaba en ruta no calculada).
 * Reglas chicas y testeables; si ninguna aplica, el texto pasa intacto.
 * Nunca inventa: no agrega ciudad ni país acá (eso lo hace el backend con la
 * localidad REAL del reverse geocode del origen).
 */
object OutdoorDestinationNormalizer {

    /** "llevame a casa" sin memoria de casa: pedir la dirección, jamás geocodificar "casa". */
    private val HOME_REFERENCES = setOf(
        "mi casa", "casa", "a casa", "a mi casa", "mi domicilio", "domicilio", "mi hogar"
    )

    /** Prefijos de relleno que degradan el geocoding ("la calle mendoza"). */
    private val LEADING_FILLERS = listOf(
        "a la calle ", "la calle ", "calle ",
        "a la ", "a el ", "la ", "el ", "los ", "las "
    )

    /** "san martin al 500" / "san martin altura 500" → "san martin 500". */
    private val HEIGHT_CONNECTOR = Regex("\\b(?:al|a la altura(?: de)?|altura) (\\d{1,5})\\b")

    /** "al 500 de san martin" → "san martin 500". */
    private val HEIGHT_FIRST = Regex("^(?:al |el )?(\\d{1,5}) de (.+)$")

    private val UNITS = mapOf(
        "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3,
        "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7,
        "ocho" to 8, "nueve" to 9
    )
    private val TENS_AND_TEENS = mapOf(
        "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13,
        "catorce" to 14, "quince" to 15, "dieciseis" to 16,
        "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veinte" to 20, "veintiuno" to 21, "veintidos" to 22,
        "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25,
        "veintiseis" to 26, "veintisiete" to 27, "veintiocho" to 28,
        "veintinueve" to 29, "treinta" to 30, "cuarenta" to 40,
        "cincuenta" to 50, "sesenta" to 60, "setenta" to 70,
        "ochenta" to 80, "noventa" to 90
    )
    private val HUNDREDS = mapOf(
        "cien" to 100, "ciento" to 100, "doscientos" to 200, "doscientas" to 200,
        "trescientos" to 300, "trescientas" to 300, "cuatrocientos" to 400,
        "cuatrocientas" to 400, "quinientos" to 500, "quinientas" to 500,
        "seiscientos" to 600, "seiscientas" to 600, "setecientos" to 700,
        "setecientas" to 700, "ochocientos" to 800, "ochocientas" to 800,
        "novecientos" to 900, "novecientas" to 900
    )

    fun isHomeReference(rawDestination: String): Boolean =
        fold(rawDestination).removeSuffix(" por favor").trim() in HOME_REFERENCES

    /**
     * Query de geocoding limpia. Idempotente; entrada y salida son texto
     * hablado (sin coordenadas, jamás se loguea el contenido).
     */
    fun normalizeQuery(rawDestination: String): String {
        var text = fold(rawDestination).removeSuffix(" por favor").trim()
        if (text.isBlank()) return text

        // "al 500 de san martin" → "san martin 500" (antes de pisar el "al").
        HEIGHT_FIRST.matchEntire(text)?.let { match ->
            text = "${match.groupValues[2]} ${match.groupValues[1]}"
        }

        // "san martin al quinientos" → "san martin al 500" (palabras → dígitos).
        text = replaceTrailingNumberWords(text)

        // "san martin al 500" → "san martin 500".
        text = HEIGHT_CONNECTOR.replace(text) { match -> match.groupValues[1] }

        // "la calle mendoza" → "mendoza"; "el centro" → "centro".
        var stripped = true
        while (stripped) {
            stripped = false
            for (filler in LEADING_FILLERS) {
                if (text.startsWith(filler) && text.length > filler.length + 2) {
                    // No comerse "al 500 ..." ni números que son la altura.
                    val rest = text.removePrefix(filler)
                    if (rest.firstOrNull()?.isDigit() != true) {
                        text = rest
                        stripped = true
                        break
                    }
                }
            }
        }

        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Convierte UNA corrida final de números en palabras a dígitos:
     * "san martin quinientos cincuenta" → "san martin 550". También la corrida
     * que sigue a "al"/"altura". Si no hay corrida válida, no toca nada.
     */
    internal fun replaceTrailingNumberWords(text: String): String {
        val tokens = text.split(' ').filter { it.isNotBlank() }
        if (tokens.size < 2) return text

        // Corrida más larga de palabras-número al FINAL de la frase, con "y"
        // interno permitido ("quinientos cincuenta y cinco").
        var start = tokens.size
        while (start > 0) {
            val token = tokens[start - 1]
            if (isNumberWord(token)) {
                start--
                continue
            }
            if (token == "y" && start < tokens.size && start - 2 >= 0 &&
                isNumberWord(tokens[start - 2])
            ) {
                start--
                continue
            }
            break
        }
        if (start >= tokens.size) return text
        // Una palabra suelta tipo "una"/"dos" al final suele ser parte del
        // nombre ("plaza las dos"); solo convertimos si la corrida arranca
        // tras "al"/"altura" o si vale 100 o más (altura real).
        val run = tokens.subList(start, tokens.size).filter { it != "y" }
        val value = numberFromWords(run) ?: return text
        val afterHeightMarker = start > 0 && tokens[start - 1] in setOf("al", "altura")
        if (!afterHeightMarker && value < 100) return text

        val head = tokens.subList(0, start).joinToString(" ")
        return "$head $value".trim()
    }

    private fun isNumberWord(token: String): Boolean =
        token in UNITS || token in TENS_AND_TEENS || token in HUNDREDS || token == "mil"

    /** Valor de una corrida de palabras-número (1..99999), o null si no forma número. */
    internal fun numberFromWords(words: List<String>): Int? {
        if (words.isEmpty()) return null
        var total = 0
        var current = 0
        for (word in words) {
            when {
                word == "mil" -> {
                    total += (if (current == 0) 1 else current) * 1000
                    current = 0
                }
                word in HUNDREDS -> current += HUNDREDS.getValue(word)
                word in TENS_AND_TEENS -> current += TENS_AND_TEENS.getValue(word)
                word in UNITS -> current += UNITS.getValue(word)
                else -> return null
            }
        }
        val value = total + current
        return value.takeIf { it in 1..99_999 }
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
