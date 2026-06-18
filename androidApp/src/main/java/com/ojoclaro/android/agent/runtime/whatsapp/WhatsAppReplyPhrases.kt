package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.voice.IncompleteUtteranceClassifier
import java.text.Normalizer
import java.util.Locale

/**
 * "Responder en el chat ABIERTO" desde voz: respondé/contestale/decile/
 * escribí/mandale [texto]. Extrae el texto del mensaje preservando su
 * contenido (no se baja a minúsculas ni se quitan tildes del mensaje).
 *
 * NO es compose-con-contacto: "mandale a Marco que hola" lo maneja el
 * smart compose (corre aparte). Acá se rechaza cualquier patrón "a <nombre>
 * que ..." para no robarlo.
 *
 * Escribir el borrador NUNCA envía: el envío exige doble confirmación.
 */
object WhatsAppReplyPhrases {

    private val REPLY_REGEX = Regex(
        "^\\s*(?:hola\\s+)?(?:estela[,\\s]+)?" +
            "(respond[eé]|respond[eé]le|respondele|resp[oó]ndele|contest[aá]|contest[aá]le|" +
            "contestale|cont[eé]stale|" +
            "decile|escribile|escrib[ií]|escribe|mandale)" +
            "[:,]?\\s+(.+)$",
        RegexOption.IGNORE_CASE
    )

    // "a <nombre> ... que ..." = compose con contacto → NO es respuesta al chat.
    private val CONTACT_COMPOSE_REGEX = Regex(
        "^a\\s+\\S+.*\\bque\\b",
        RegexOption.IGNORE_CASE
    )

    private val LEADING_QUE_REGEX = Regex("^que\\s+", RegexOption.IGNORE_CASE)

    fun extractReply(rawText: String): String? {
        val match = REPLY_REGEX.find(rawText.trim()) ?: return null
        val rest = match.groupValues[2].trim()
        if (CONTACT_COMPOSE_REGEX.containsMatchIn(rest)) return null
        // "contestale QUE ya salí" → "ya salí".
        val message = LEADING_QUE_REGEX.replaceFirst(rest, "").trim()
        // Fluency: limpia muletillas dictadas ("respondé ehh estoy llegando"
        // → "estoy llegando"). Si solo había muletillas, queda en blanco y el
        // caller pide la frase (isReplyAttempt lo distingue de "no es respuesta").
        val cleaned = IncompleteUtteranceClassifier.stripFillers(message)
        return cleaned.takeIf { it.isNotBlank() }
    }

    /**
     * True si el texto TIENE forma de respuesta al chat (verbo de respuesta +
     * algo), aunque ese "algo" sea solo muletillas. Permite que GAS pida la
     * frase ("¿qué querés que responda?") en vez de ignorar el turno.
     */
    fun isReplyAttempt(rawText: String): Boolean {
        val match = REPLY_REGEX.find(rawText.trim()) ?: return false
        val rest = match.groupValues[2].trim()
        return rest.isNotBlank() && !CONTACT_COMPOSE_REGEX.containsMatchIn(rest)
    }

    // Sets en forma SIN acentos (norm() quita tildes antes de comparar).

    /** Paso 1 ("¿Querés enviarlo?"): basta un sí para AVANZAR (no envía). */
    fun isConfirmStep1(rawText: String): Boolean =
        norm(rawText) in setOf(
            "si", "dale", "ok", "okey", "esta bien", "quiero", "si quiero",
            "si dale", "claro", "obvio", "envialo", "envialo si", "mandalo",
            "si mandalo", "si envia", "si envialo", "confirmo", "lo quiero enviar",
            // Anxiety hardening: sinónimos calmos que también AVANZAN (no envían).
            "correcto", "segui", "segui dale", "preparar", "prepara", "prepararlo",
            "lo preparo", "perfecto", "esta perfecto", "de una"
        )

    /**
     * Paso 2 ("¿Lo mando ahora?"): confirmación FUERTE para enviar de verdad.
     * Un "sí"/"dale" a secas NO alcanza (contrato: bare-yes nunca envía).
     */
    fun isStrongSendConfirm(rawText: String): Boolean =
        norm(rawText) in setOf(
            "mandalo", "si mandalo", "dale mandalo", "mandalo ahora", "mandalo ya",
            "envialo", "si envialo", "envialo ahora", "envia", "envialo dale",
            "confirmo", "confirmo envio", "lo mando", "lo mando ahora", "si lo mando",
            // Anxiety hardening: confirmaciones fuertes explícitas del contrato.
            "enviar ahora", "confirmo enviar", "mandala", "manda eso"
        )

    fun isCancel(rawText: String): Boolean =
        norm(rawText) in setOf(
            "no", "cancelar", "cancela", "cancelalo", "para", "pare", "basta",
            "no mandes", "no lo mandes", "no envies", "me equivoque",
            "dejalo", "olvidalo", "mejor no", "borra el borrador", "borrar borrador",
            // Anxiety hardening: más formas de frenar sin castigar.
            "para no mandes", "no mandes nada", "deja", "deja eso", "ya no",
            "no quiero enviarlo", "no quiero mandarlo", "mejor cancela", "me arrepenti",
            // Fluency: "no, me equivoqué" entero.
            "no me equivoque", "no me equivoco"
        )

    private fun norm(text: String): String {
        val lower = text.lowercase(Locale("es", "AR"))
        val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
