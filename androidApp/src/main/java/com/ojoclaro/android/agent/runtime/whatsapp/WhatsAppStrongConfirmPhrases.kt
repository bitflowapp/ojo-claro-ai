package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Frases FUERTES y EXACTAS que confirman, en el segundo paso, una acción
 * peligrosa de WhatsApp. Cada una termina en "ahora" (o es una orden inequívoca
 * de grabación) para que un "sí"/"dale" a secas JAMÁS dispare nada.
 *
 * Contrato:
 *  - Sólo clasifica texto; no ejecuta ni toca nada.
 *  - "sí"/"dale"/"ok"/"ajá" → débiles, nunca fuertes ([isWeak]).
 *  - Las acciones distintas tienen frases distintas: confirmar un envío no
 *    confirma una llamada, etc.
 */
object WhatsAppStrongConfirmPhrases {

    private val SEND_NOW = setOf(
        "mandalo ahora", "enviar ahora", "envialo ahora", "mandar ahora",
        "manda ahora", "mandalo ya", "si mandalo ahora", "si enviar ahora",
        "confirmo enviar ahora"
    )
    private val CALL_NOW = setOf(
        "llamar ahora", "llama ahora", "llamalo ahora", "llamala ahora",
        "si llamar ahora", "hacer la llamada ahora", "iniciar llamada ahora"
    )
    private val VIDEO_CALL_NOW = setOf(
        "videollamar ahora", "videollama ahora", "video llamar ahora",
        "hacer la videollamada ahora", "iniciar videollamada ahora",
        "si videollamar ahora"
    )
    private val AUDIO_SEND_NOW = setOf(
        "mandar audio ahora", "enviar audio ahora", "manda el audio ahora",
        "manda audio ahora", "mandar el audio ahora", "enviar el audio ahora"
    )
    private val RECORD_START = setOf(
        "empezar grabacion", "empezar a grabar", "empeza a grabar",
        "comenzar grabacion", "empezar grabar", "arrancar grabacion"
    )
    private val RECORD_STOP = setOf(
        "terminar grabacion", "terminar de grabar", "cortar grabacion",
        "parar grabacion", "frenar grabacion", "terminar grabar"
    )

    fun isStrongSend(text: String): Boolean = fold(text) in SEND_NOW
    fun isStrongCall(text: String): Boolean = fold(text) in CALL_NOW
    fun isStrongVideoCall(text: String): Boolean = fold(text) in VIDEO_CALL_NOW
    fun isStrongAudioSend(text: String): Boolean = fold(text) in AUDIO_SEND_NOW
    fun isStartRecording(text: String): Boolean = fold(text) in RECORD_START
    fun isStopRecording(text: String): Boolean = fold(text) in RECORD_STOP

    /** Frase fuerte EXACTA para una acción concreta. */
    fun isStrongConfirmFor(action: WhatsAppActionType, text: String): Boolean = when (action) {
        WhatsAppActionType.SEND_MESSAGE -> isStrongSend(text)
        WhatsAppActionType.CALL -> isStrongCall(text)
        WhatsAppActionType.VIDEO_CALL -> isStrongVideoCall(text)
        WhatsAppActionType.SEND_AUDIO -> isStrongAudioSend(text)
        WhatsAppActionType.RECORD_AUDIO -> isStartRecording(text)
        else -> false
    }

    /** "sí"/"dale"/"ok"/"ajá" a secas: jamás alcanza para una acción peligrosa. */
    fun isWeak(text: String): Boolean =
        VoicePhraseNormalizer.isNeverConfirm(text) ||
            VoicePhraseNormalizer.isAffirmativeNoise(text) ||
            fold(text) in setOf("si", "dale", "ok", "okey", "bueno", "aja", "claro")

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
