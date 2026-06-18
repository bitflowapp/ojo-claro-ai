package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Resuelve frases de RELACIÓN ("mi novia", "mi pareja", "mi contacto de prueba")
 * a una clave canónica estable, para que un contacto confiable se guarde y se
 * resuelva por relación, sin hardcodear números.
 *
 * Distintas palabras de la MISMA relación caen a la MISMA clave: "mi novia" y
 * "mi pareja" → "pareja". Así, vincular una vez alcanza para todos los alias.
 *
 * SÓLO clasifica texto. No guarda nada, no expone PII.
 */
object WhatsAppRelationshipAlias {

    /** clave canónica -> frases que la nombran (normalizadas, sin acento). */
    private val GROUPS: Map<String, Set<String>> = mapOf(
        "pareja" to setOf(
            "mi novia", "novia", "mi novio", "novio", "mi pareja", "pareja",
            "mi mujer", "mujer", "mi esposa", "esposa", "mi esposo", "esposo",
            "mi marido", "marido", "mi señora", "mi senora", "mi compañera",
            "mi companera", "mi compañero", "mi companero"
        ),
        "qa" to setOf(
            "mi contacto de prueba", "contacto de prueba", "mi contacto qa",
            "contacto qa", "mi qa", "contacto de testeo", "mi contacto de testeo",
            "contacto de test"
        ),
        "mama" to setOf("mi mama", "mama", "mi vieja", "mi madre", "madre"),
        "papa" to setOf("mi papa", "papa", "mi viejo", "mi padre", "padre")
    )

    /** Clave canónica de una frase de relación, o null si no es una. */
    fun canonicalKey(phrase: String): String? {
        val f = fold(phrase)
        GROUPS.forEach { (key, phrases) -> if (f in phrases) return key }
        return null
    }

    fun isRelationshipPhrase(phrase: String): Boolean = canonicalKey(phrase) != null

    /** Etiqueta hablable/redactada para una clave ("pareja" → "tu pareja"). */
    fun spokenLabel(key: String): String = when (key) {
        "pareja" -> "tu pareja"
        "qa" -> "tu contacto de prueba"
        "mama" -> "tu mamá"
        "papa" -> "tu papá"
        else -> "ese contacto"
    }

    // Marca de VINCULACIÓN explícita seguida de la relación.
    // Ej: "este contacto es mi novia", "guardá a mi novia",
    //     "marcá a este contacto como mi pareja", "ella es mi novia".
    private val LINK_PREFIX = Regex(
        "^(?:este contacto es|esta persona es|este es|esta es|ella es|el es|" +
            "guarda(?:la|lo)? a|agrega(?:la|lo)? a|registra(?:la|lo)? a) (.+)$"
    )
    private val LINK_AS = Regex("\\b(?:es mi|como mi|como|marca(?:la|lo)? como) (.+)$")

    /**
     * Si la frase pide VINCULAR el contacto visible a una relación, devuelve la
     * clave canónica a guardar; si no, null. Sólo devuelve algo cuando lo
     * capturado es una relación conocida (no roba "abrí el chat de mi novia",
     * que no tiene marca de vinculación).
     */
    fun parseLinkRequest(rawText: String): String? {
        val f = fold(rawText)
        if (f.isBlank()) return null
        val rel = LINK_PREFIX.find(f)?.groupValues?.get(1)
            ?: LINK_AS.find(f)?.groupValues?.get(1)
            ?: return null
        return resolveCapturedRelation(rel)
    }

    // Marca de DESVINCULACIÓN seguida de la relación.
    // Ej: "olvidá a mi novia", "olvidate de mi novia", "desvinculá a mi novia",
    //     "ya no es mi novia", "borrá el contacto de mi novia".
    private val FORGET_PREFIX = Regex(
        "^(?:olvida(?:te)?(?: de)?|desvincula|desvincular|ya no es|" +
            "borra(?: el contacto de| a)?|elimina(?: el contacto de| a)?|" +
            "quita(?: a)?|saca(?: a)?) (.+)$"
    )

    /**
     * Si la frase pide DESVINCULAR/olvidar una relación, devuelve su clave
     * canónica; si no, null. Sólo si lo capturado es una relación conocida (no
     * roba "olvidá lo que dije").
     */
    fun parseForgetRequest(rawText: String): String? {
        val f = fold(rawText)
        if (f.isBlank()) return null
        val rel = FORGET_PREFIX.find(f)?.groupValues?.get(1) ?: return null
        return resolveCapturedRelation(rel)
    }

    /**
     * Resuelve la relación capturada a su clave canónica, tolerando una
     * preposición/artículo inicial ("a mi novia", "de mi pareja") y la elisión
     * del posesivo ("novia" → "mi novia").
     */
    private fun resolveCapturedRelation(captured: String): String? {
        val r = captured.trim()
            .replace(Regex("^(?:a|al|a la|de|del|el|la|los|las)\\s+"), "")
            .trim()
        return canonicalKey(r) ?: canonicalKey("mi $r") ?: canonicalKey(captured.trim())
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
