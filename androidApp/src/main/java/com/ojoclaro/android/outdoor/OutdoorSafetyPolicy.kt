package com.ojoclaro.android.outdoor

import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Política de seguridad del Outdoor Guidance.
 *
 * Estela es ayuda COMPLEMENTARIA: nunca afirma que un cruce, camino o
 * movimiento sea seguro, venga de donde venga la frase (GPT, backend de
 * visión o código local). Esta política se aplica a TODO texto que se vaya
 * a hablar en contexto exterior, como última línea de defensa.
 */
object OutdoorSafetyPolicy {

    enum class MessageClass { INFORMATIONAL, CAUTION, BLOCKED_SAFETY_CLAIM }

    const val SAFE_FALLBACK_TEXT: String =
        "No puedo confirmar que el paso esté libre. " +
            "Verificá con tu bastón o tu método habitual de movilidad."

    const val SCENE_DISCLAIMER: String =
        "No puedo confirmar que el camino esté libre."

    /**
     * Respuesta fija ante preguntas tipo "¿es seguro cruzar?". Redactada para
     * clasificar CAUTION (nunca contiene una afirmación bloqueable).
     */
    const val COMPLEMENTARY_GUIDANCE_TEXT: String =
        "No puedo confirmar eso: no veo el tránsito en tiempo real. " +
            "Soy una ayuda complementaria. Verificá con tu bastón, tu perro guía " +
            "o tu método habitual de movilidad, o pedí ayuda a una persona."

    /**
     * Afirmaciones de seguridad prohibidas, con variaciones lingüísticas.
     * Se evalúan sobre texto normalizado (sin tildes, lowercase).
     */
    private val BLOCKED_PATTERNS: List<Regex> = listOf(
        Regex("\\bes seguro\\b"),
        Regex("\\bsegur[oa] (de )?cruzar\\b"),
        Regex("\\bpod[ei]s (cruzar|avanzar|pasar|seguir sin)\\b"),
        Regex("\\bpuede[sn]? (cruzar|avanzar|pasar)\\b"),
        Regex("\\bcruz[aá] (ahora|ya|tranquil[oa])\\b"),
        Regex("\\bcruza ahora\\b"),
        Regex("\\bavanz[aá]\\b"),
        Regex("\\bel camino (esta |est[aá] )?(libre|despejado)\\b"),
        Regex("\\bcamino libre\\b"),
        Regex("\\bpaso (esta |est[aá] )?libre\\b"),
        Regex("\\bno hay (peligro|peligros|obstaculos|autos|nada que temer)\\b"),
        Regex("\\bno viene (ningun |ninguna )?(auto|coche|vehiculo|moto|colectivo)\\b"),
        Regex("\\bel auto se detuvo\\b"),
        Regex("\\bsin (el |tu )?baston\\b"),
        Regex("\\btodo (esta |est[aá] )?despejado\\b"),
        Regex("\\bvia libre\\b")
    )

    private val CAUTION_MARKERS: List<String> = listOf(
        "posible obstaculo",
        "posible desnivel",
        "podria obstaculizar",
        "no puedo confirmar",
        "detecte un posible",
        "parece que",
        "segun la ubicacion disponible",
        "verifica con tu baston"
    )

    fun classify(rawText: String): MessageClass {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return MessageClass.INFORMATIONAL
        if (BLOCKED_PATTERNS.any { it.containsMatchIn(normalized) }) {
            return MessageClass.BLOCKED_SAFETY_CLAIM
        }
        if (CAUTION_MARKERS.any { normalized.contains(it) }) {
            return MessageClass.CAUTION
        }
        return MessageClass.INFORMATIONAL
    }

    /**
     * Texto final a hablar. Si la frase contiene una afirmación de seguridad
     * prohibida, se reemplaza COMPLETA por el fallback prudente (no se intenta
     * "arreglar" la frase: una afirmación peligrosa invalida todo el mensaje).
     */
    fun sanitizeForSpeech(rawText: String): String {
        val clean = rawText.trim()
        if (clean.isBlank()) return SAFE_FALLBACK_TEXT
        return when (classify(clean)) {
            MessageClass.BLOCKED_SAFETY_CLAIM -> SAFE_FALLBACK_TEXT
            else -> clean
        }
    }

    /**
     * Para descripciones de escena: sanitiza y garantiza que el cierre
     * prudente esté presente.
     */
    fun sanitizeSceneDescription(rawText: String): String {
        val sanitized = sanitizeForSpeech(rawText)
        val normalized = normalize(sanitized)
        return if (normalized.contains("no puedo confirmar")) {
            sanitized
        } else {
            "$sanitized $SCENE_DISCLAIMER".trim()
        }
    }

    private fun normalize(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}

/**
 * Des-acentúa para matching robusto de STT ("podés"→"podes"). El
 * VoicePhraseNormalizer compartido conserva tildes a propósito (otros
 * consumidores las necesitan), así que esta capa es local del módulo.
 */
internal fun String.removeSpanishAccents(): String =
    this
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
        .replace('ñ', 'n')
