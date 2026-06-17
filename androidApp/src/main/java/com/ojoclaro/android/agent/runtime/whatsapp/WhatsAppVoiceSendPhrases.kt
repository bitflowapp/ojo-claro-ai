package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.2 — frases del flujo de envío seguro y audios de WhatsApp.
 *
 * Reglas duras:
 *  - "sí" / "dale" / "ok" a secas NUNCA confirman un envío (regla histórica
 *    del piloto): la persona tiene que decir "enviá" / "mandalo" / "confirmo".
 *  - "no" a secas SÍ cancela (la dirección segura siempre es no enviar).
 *  - Los mensajes con pinta de credencial/datos bancarios no se envían por
 *    voz: [looksSensitive] los bloquea ANTES de pedir confirmación.
 */
object WhatsAppVoiceSendPhrases {

    /** "Enviá el mensaje" — pide leer el borrador del campo y confirmar. */
    private val SEND_DRAFT_COMMANDS = setOf(
        "envia el mensaje", "envialo", "mandalo", "manda el mensaje",
        "enviar el mensaje", "mandar el mensaje", "envia eso", "manda eso",
        "mandale el mensaje", "enviar mensaje", "envia ese mensaje",
        "manda ese mensaje", "ya podes enviarlo", "envialo ahora"
    )

    /** Confirmaciones válidas cuando hay un envío pendiente leído en voz alta. */
    private val CONFIRM_SEND = setOf(
        "envia", "envialo", "mandalo", "enviar", "mandar",
        "si envia", "si envialo", "si mandalo", "si enviar", "si manda",
        "confirmo", "confirmar", "acepto", "aceptar", "si confirmo",
        "confirmo envio", "confirmar envio",
        "envia el mensaje", "mandalo ya"
    )

    /** Cancelaciones: cualquier "no" gana siempre. */
    private val CANCEL_SEND = setOf(
        "no", "no no", "cancelar", "cancela", "cancelalo", "cancelar envio",
        "no envies", "no lo envies", "no lo mandes", "no mandes nada",
        "no envies nada", "borralo", "borra el mensaje", "no cancela",
        "no cancelalo", "mejor no", "para", "para para",
        // Anxiety hardening: frenar el envío real también con estas formas.
        "no mandes", "me equivoque", "deja", "dejalo", "pare", "basta", "ya no",
        // Fluency: "no, me equivoqué" entero.
        "no me equivoque", "no me equivoco"
    )

    /** Pedidos de mandar AUDIO: hoy solo fallback honesto. */
    private val SEND_AUDIO_MARKERS = listOf(
        // El lexicon reescribe "mandale"→"mandar" antes de este parser:
        // cubrimos ambas formas.
        "manda un audio", "mandale un audio", "envia un audio",
        "mandar un audio", "enviar un audio", "graba un audio",
        "grabale un audio", "quiero mandar un audio",
        "mandale audio", "mandar audio", "enviar audio"
    )

    /** Reproducir un audio visible en el chat abierto. */
    private val PLAY_AUDIO_COMMANDS = setOf(
        "reproduci el audio", "reproduce el audio", "reproducir el audio",
        "reproducir audio", "escucha el audio", "escuchar el audio",
        "pone el audio", "pon el audio", "poneme el audio",
        // El lexicon reescribe "poneme"→"poner" antes de este parser.
        "poner el audio", "poner el ultimo audio",
        "reproduci el ultimo audio", "reproduce el ultimo audio",
        "pone el ultimo audio", "escucha el ultimo audio",
        "reproducir el ultimo audio", "poneme el ultimo audio"
    )

    private val SENSITIVE_KEYWORDS = listOf(
        "contraseña", "contrasena", "password", "clave", "token", "pin",
        "cvv", "cbu", "codigo de seguridad", "numero de tarjeta",
        "tarjeta de credito", "tarjeta de debito"
    )

    /** Corridas largas de dígitos (tarjetas, CBU): 8+ dígitos seguidos
     *  permitiendo espacios o guiones entre grupos. */
    private val LONG_DIGIT_RUN = Regex("(?:\\d[ -]?){8,}")

    fun isSendDraftCommand(rawText: String): Boolean =
        fold(rawText) in SEND_DRAFT_COMMANDS

    fun isConfirmSend(rawText: String): Boolean =
        fold(rawText) in CONFIRM_SEND

    fun isCancelSend(rawText: String): Boolean =
        fold(rawText) in CANCEL_SEND

    fun isSendAudioRequest(rawText: String): Boolean {
        val text = fold(rawText)
        return SEND_AUDIO_MARKERS.any { text.contains(it) }
    }

    fun isPlayAudioCommand(rawText: String): Boolean =
        fold(rawText) in PLAY_AUDIO_COMMANDS

    /**
     * True si el contenido parece credencial o dato bancario. En ese caso el
     * envío por voz se bloquea: la persona debe revisarlo y enviarlo a mano.
     */
    fun looksSensitive(messageText: String): Boolean {
        val folded = messageText.lowercase().removeSpanishAccents()
        if (SENSITIVE_KEYWORDS.any { folded.contains(it) }) return true
        return LONG_DIGIT_RUN.containsMatchIn(folded)
    }

    /**
     * True si el texto NOMBRA una credencial (clave/token/pin/CBU...). Para
     * destinatarios: una corrida de dígitos sola es un número legítimo, pero
     * "marco mi clave 12345678" jamás debe resolverse como contacto.
     */
    fun mentionsSensitiveKeyword(text: String): Boolean {
        val folded = text.lowercase().removeSpanishAccents()
        return SENSITIVE_KEYWORDS.any { folded.contains(it) }
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
