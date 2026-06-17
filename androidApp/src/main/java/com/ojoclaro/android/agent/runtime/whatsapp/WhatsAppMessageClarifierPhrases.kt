package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Blind Safety (#8) — clasifica una frase, CON WhatsApp al frente, para decidir
 * si es:
 *  - PREGUNTA / Q&A clara → puede ir al asistente normal ([looksLikeQuestion]).
 *  - CONTENIDO de mensaje / continuación AMBIGUA (sin destinatario ni mensaje
 *    claro: "estoy llegando", "decile que sí", "mandale eso", "eso") → NO debe
 *    viajar al LLM/backend; el caller pide una aclaración LOCAL
 *    ([looksLikeAmbiguousMessageContent]).
 *
 * Por qué: parado en WhatsApp, dictar algo que parece para mandar no puede
 * egresar como charla al backend (podría ser contenido privado), pero el Q&A
 * general (qué significa, cómo se calcula, dame ideas) debe seguir funcionando.
 *
 * NO clasifica acciones WhatsApp EXPLÍCITAS (borrar/foto/pagar/llamar/…): de esas
 * se ocupan el forbidden parser y el critical guard ANTES. Acá sólo se mira la
 * AMBIGÜEDAD de "contenido de mensaje". PURO: sin Android, sin estado, sin IO.
 */
object WhatsAppMessageClarifierPhrases {

    private val QUESTION_OPENER = Regex(
        "^(que|cual|como|cuando|cuanto|cuanta|quien|donde|por que|para que)\\b"
    )
    private val QA_MARKERS = listOf(
        "que significa", "que es ", "que seria", "que quiere decir", "como se",
        "como funciona", "se calcula", "explicame", "explica", "explicar",
        "ayudame a entender", "ayuda a entender", "ayudame a", "me ayudas",
        "dame ideas", "ideas para", "que puedo responder", "que le puedo decir",
        "que le digo", "que le contesto", "que le respondo", "que le pongo",
        "que hora"
    )

    /** Frase entera = continuación vaga (pronombre/referencia, sin verbo). */
    private val WHOLE_VAGUE = setOf(
        "eso", "esto", "eso si", "si eso", "lo anterior", "lo de antes",
        "lo de recien", "lo mismo", "lo ultimo"
    )

    /**
     * Verbo de decir/mandar/avisar/contestar/poner/pasar a alguien. Incluye las
     * formas INFINITIVAS porque VoicePhraseNormalizer reescribe el voseo a
     * infinitivo ("mandale"→"mandar", "decile"→"decir") ANTES de llegar acá.
     */
    private val TELL_VERB = Regex(
        "^(mandar|mandale|manda|mandalo|enviar|enviale|decir|decile|dile|decirle|" +
            "avisar|avisale|avisa|contestar|contestale|contesta|responder|responde|" +
            "respondele|poner|ponele|pone|pasar|pasale|pasa)\\b"
    )

    /** Estado/movimiento dictado: parece el cuerpo de un mensaje. */
    private val STATUS_DECLARATIVE = Regex(
        "^(ya |estoy |estamos |ahi |aca )?(llegando|llego|llegue|llegamos|yendo|" +
            "voy|vamos|saliendo|salgo|sali|salimos|en camino|por llegar|por salir)\\b"
    )

    /**
     * Palabras de acción PELIGROSA/multimedia: si aparecen, NO es un clarifier de
     * "contenido de mensaje" — las atienden forbidden/critical/dangerous antes. Evita
     * que "mandá una foto"/"mandale un audio"/"compartí ubicación" caigan al clarifier.
     */
    private val DANGEROUS_OBJECT = Regex(
        "\\b(?:foto|fotos|imagen|imagenes|sticker|stickers|figurita|gif|archivo|" +
            "documento|pdf|audio|nota de voz|mensaje de voz|grabar|ubicacion|" +
            "llamar|llamada|llamale|videollamada|video|pagar|pagale|plata|transfer\\w*|" +
            "borra\\w*|elimina\\w*|bloque\\w*|reenvi\\w*|reporta\\w*|denunci\\w*|archiva\\w*)\\b"
    )

    /** Contenido vago tras el verbo (no un mensaje real). */
    private val VAGUE_CONTENT = setOf(
        "", "eso", "esto", "si", "sii", "no", "ok", "oka", "okey", "okay",
        "dale", "bueno", "listo", "manana", "hoy", "ahora", "luego", "ahi", "aca",
        "lo anterior", "lo de antes", "lo mismo"
    )

    fun looksLikeQuestion(rawText: String): Boolean {
        val f = fold(rawText)
        if (f.isBlank()) return false
        if (QUESTION_OPENER.containsMatchIn(f)) return true
        return QA_MARKERS.any { f.contains(it) }
    }

    fun looksLikeAmbiguousMessageContent(rawText: String): Boolean {
        val f = fold(rawText)
        if (f.isBlank()) return false
        if (looksLikeQuestion(f)) return false
        // Acción peligrosa/multimedia → NO es clarifier (la atiende forbidden/dangerous).
        if (DANGEROUS_OBJECT.containsMatchIn(f)) return false
        if (f in WHOLE_VAGUE) return true
        if (STATUS_DECLARATIVE.containsMatchIn(f)) return true
        val verb = TELL_VERB.find(f) ?: return false
        var rest = f.removePrefix(verb.value).trim()
        rest = rest.removePrefix("que ").removePrefix("le ").removePrefix("se ").trim()
        if (rest in VAGUE_CONTENT) return true
        // un solo token corto tras el verbo también es ambiguo ("decile dale").
        val toks = rest.split(' ').filter { it.isNotBlank() }
        return toks.size <= 1 && (toks.firstOrNull()?.length ?: 0) <= 6
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
