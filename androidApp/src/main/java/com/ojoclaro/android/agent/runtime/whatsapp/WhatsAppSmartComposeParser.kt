package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.8 — compose inteligente: "mandale a Marco que estoy llegando" se parsea
 * ANTES de tocar nada, para confirmar primero el CONTACTO (nombre + número
 * terminado en 4 dígitos) y recién después preparar y confirmar el envío.
 *
 * Reglas:
 *  - La confirmación de contacto acepta "sí"/"ese"/"correcto" (elegir a
 *    quién no es sensible).
 *  - El ENVÍO final sigue exigiendo "enviá"/"mandalo"/"confirmo": un "sí"
 *    jamás envía (contrato de WhatsAppVoiceSendPhrases).
 */
object WhatsAppSmartComposeParser {

    /** [recipientQuery] null = hay mensaje pero falta a quién (preguntar). */
    data class ComposeRequest(val recipientQuery: String?, val message: String?)

    // El lexicon reescribe "mandale"→"mandar", "decile"→"decir", "escribile"→
    // "escribir", pero NO cubre "enviá"/"mandá"/"avisale": acá se aceptan las
    // formas crudas además de las canónicas. "mandame" queda afuera a
    // propósito ("mandame a casa" es navegación, no compose).
    private const val VERBS =
        "(?:manda(?:r|le)?|envia(?:r|le)?|escribi(?:r|le)?|deci(?:r|le)?|avisa(?:r|le)?)"
    private const val NOUN = "(?:(?:un|el|este) )?(?:whatsapp|mensaje)"
    private const val TO = "(?:a|al|para)"

    // Separadores hablados entre destinatario y mensaje. El orden importa:
    // "diciendo que" antes que "diciendo" para que no quede un "que " colgado
    // al principio del mensaje.
    private const val SEP =
        "(?:diciendo que|diciendo|que dice|que diga|con el texto|con mensaje|mensaje|que)"

    // Medios que NUNCA son compose de texto (audio tiene su propio flujo).
    private const val MEDIA = "(?:un |una |el |la |mi )?(?:audio|foto|fotos|video|imagen|ubicacion)"

    // Orden: separador hablado primero (el destinatario nunca cruza un ':'),
    // después puntuación dictada ("a Juan: ya salí").
    private val COMPOSE_PATTERNS = listOf(
        // "enviar un whatsapp a juan diciendo estoy llegando"
        Regex("^$VERBS $NOUN $TO ([^:]+?) $SEP (.+)$"),
        // "mandar a juan que estoy llegando" / "avisale a marco que llego"
        Regex("^$VERBS $TO ([^:]+?) $SEP (.+)$"),
        // "mandar este mensaje a juan: ya sali" / "escribir a juan: estoy llegando"
        Regex("^$VERBS (?:$NOUN )?$TO (.+?)\\s*[:,]\\s*(.+)$")
    )

    // "mandar un mensaje diciendo estoy llegando": hay mensaje pero falta el
    // destinatario; el servicio tiene que preguntar a quién.
    private val NO_RECIPIENT_PATTERNS = listOf(
        Regex("^$VERBS $NOUN $SEP (.+)$"),
        Regex("^$VERBS $NOUN\\s*:\\s*(.+)$")
    )

    // "mandar a juan" / "mandar un whatsapp a juan": falta el mensaje.
    private val NO_MESSAGE_PATTERN = Regex("^$VERBS (?:$NOUN )?$TO ([^:]+?)$")

    // "mandar mi clave 123 a marco" / "mandale un saludo a la abuela":
    // mensaje-primero. Existe sobre todo para que los contenidos sensibles
    // entren al bloqueo de handleSmartCompose en vez de irse al fallback.
    private val MESSAGE_FIRST_PATTERN = Regex("^$VERBS (?!$MEDIA\\b)(.+?) $TO ([^:]+?)$")

    /** Confirmaciones de CONTACTO: acá "sí" alcanza (no es el envío). */
    private val CONTACT_YES = setOf(
        "si", "si es ese", "ese", "es ese", "correcto", "si a ese",
        "si ese", "si correcto", "exacto", "si exacto"
    )

    /** Elección entre varias opciones leídas. */
    private val ORDINAL_CHOICES = mapOf(
        "el primero" to 0, "primero" to 0, "uno" to 0, "la primera" to 0,
        "el segundo" to 1, "segundo" to 1, "dos" to 1, "la segunda" to 1,
        "el tercero" to 2, "tercero" to 2, "tres" to 2, "la tercera" to 2
    )

    private val READ_FULL_NUMBER = setOf(
        "leeme el numero completo", "el numero completo", "numero completo",
        "decime el numero completo", "leeme el numero", "decime el numero"
    )

    fun parseCompose(rawText: String): ComposeRequest? {
        val folded = foldKeepingColon(rawText)
        for (pattern in COMPOSE_PATTERNS) {
            val match = pattern.find(folded) ?: continue
            val recipient = match.groupValues[1].trim().take(60)
            if (recipient.isBlank()) continue
            val message = match.groupValues[2].trim()
                .takeIf { it.isNotBlank() }?.take(300)
            return ComposeRequest(recipient, message)
        }
        for (pattern in NO_RECIPIENT_PATTERNS) {
            val match = pattern.find(folded) ?: continue
            val message = match.groupValues[1].trim()
                .takeIf { it.isNotBlank() }?.take(300) ?: continue
            return ComposeRequest(recipientQuery = null, message = message)
        }
        NO_MESSAGE_PATTERN.find(folded)?.let { match ->
            val recipient = match.groupValues[1].trim().take(60)
            if (recipient.isNotBlank()) return ComposeRequest(recipient, message = null)
        }
        MESSAGE_FIRST_PATTERN.find(folded)?.let { match ->
            val message = match.groupValues[1].trim()
                .removePrefix("que ").trim()
                .takeIf { it.isNotBlank() }?.take(300)
            val recipient = match.groupValues[2].trim().take(60)
            if (message != null && recipient.isNotBlank()) {
                return ComposeRequest(recipient, message)
            }
        }
        return null
    }

    fun isContactYes(rawText: String): Boolean = fold(rawText) in CONTACT_YES

    fun ordinalChoice(rawText: String): Int? = ORDINAL_CHOICES[fold(rawText)]

    fun isReadFullNumber(rawText: String): Boolean = fold(rawText) in READ_FULL_NUMBER

    /** Últimos 4 dígitos hablables: jamás el número completo por defecto. */
    fun phoneLast4(phoneE164: String): String =
        phoneE164.filter(Char::isDigit).takeLast(4)

    private fun fold(rawText: String): String =
        foldKeepingColon(rawText).replace(":", " ").replace(Regex("\\s+"), " ").trim()

    private fun foldKeepingColon(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.;]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
