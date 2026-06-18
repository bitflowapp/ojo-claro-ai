package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * WhatsApp Blind-First — destino de una acción (abrir / preparar borrador) con
 * la precisión justa para decidir si se puede escribir y para confirmarlo por
 * voz SIN filtrar PII.
 *
 * Por qué existe: una persona no vidente no ve a quién le va a escribir. Antes
 * de preparar cualquier borrador, Estela tiene que saber —y poder decir— a quién
 * va dirigido, sin leer el número completo ni el contenido del chat.
 *
 * Privacidad (reglas duras):
 *  - [redactedLabel] es un nombre para mostrar (contacto/remitente), NUNCA un
 *    número. Se acota a [MAX_LABEL_CHARS].
 *  - [phoneEnding] son SOLO los últimos [PHONE_ENDING_DIGITS] dígitos, o null.
 *    Jamás el número completo: ese, si existe, vive en otra estructura del
 *    servicio y no pasa por acá.
 *  - [redactedForLog] no expone label ni dígitos: solo longitudes/flags.
 *
 * Es un value object PURO: sin Android, sin estado, sin IO.
 */
enum class WhatsAppDestinationSource {
    /** El chat ya abierto y verificado en pantalla (inChat=true). */
    CURRENT_CHAT,

    /** Contacto resuelto por memoria/agenda local. */
    CONTACT,

    /** Remitente de una notificación reciente (WA-3). */
    NOTIFICATION,

    /** Número/enlace directo (wa.me) dictado o resuelto. */
    DEEP_LINK
}

enum class WhatsAppDestinationConfidence { HIGH, MEDIUM, LOW }

data class WhatsAppDestination(
    val source: WhatsAppDestinationSource,
    val confidence: WhatsAppDestinationConfidence,
    val redactedLabel: String,
    val phoneEnding: String? = null
) {

    /** ¿Hay un destinatario nombrable? */
    val hasLabel: Boolean get() = redactedLabel.isNotBlank()

    /**
     * Solo con confianza ALTA se permite preparar un borrador. Con media/baja se
     * pregunta; sin confirmar, no se escribe (regla de seguridad del sprint).
     */
    val canPrepareDraft: Boolean
        get() = confidence == WhatsAppDestinationConfidence.HIGH

    /** Confirmación hablada del destino (últimos 4 dígitos si hay; nunca el total). */
    fun spokenConfirmation(): String =
        "Voy a usar el chat de ${who()}${endingClause()}. ¿Es correcto?"

    /** Anuncio tras abrir el chat, sin prometer envío. */
    fun spokenOpened(): String =
        "Abrí el chat de ${who()}${endingClause()}. No escribí nada."

    /** Línea de log SIN PII: ni label ni dígitos, solo metadatos. */
    fun redactedForLog(): String =
        "source=$source conf=$confidence labelLen=${redactedLabel.length} " +
            "hasEnding=${!phoneEnding.isNullOrBlank()}"

    private fun who(): String = if (hasLabel) redactedLabel else "ese contacto"

    private fun endingClause(): String =
        phoneEnding?.takeIf { it.isNotBlank() }?.let { ", terminado en $it" } ?: ""

    companion object {
        const val MAX_LABEL_CHARS = 60
        const val PHONE_ENDING_DIGITS = 4

        /**
         * Construye un destino redactando el label y derivando los últimos 4
         * dígitos. El [phoneE164] completo se usa solo para calcular el final y
         * NO se guarda: el value object jamás retiene el número completo.
         */
        fun of(
            source: WhatsAppDestinationSource,
            confidence: WhatsAppDestinationConfidence,
            label: String,
            phoneE164: String? = null
        ): WhatsAppDestination = WhatsAppDestination(
            source = source,
            confidence = confidence,
            redactedLabel = label.replace(Regex("\\s+"), " ").trim().take(MAX_LABEL_CHARS),
            phoneEnding = phoneE164
                ?.filter(Char::isDigit)
                ?.takeIf { it.length >= PHONE_ENDING_DIGITS }
                ?.takeLast(PHONE_ENDING_DIGITS)
        )
    }
}
