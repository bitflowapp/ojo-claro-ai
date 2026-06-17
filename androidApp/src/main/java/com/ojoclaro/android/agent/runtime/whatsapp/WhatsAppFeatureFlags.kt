package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Flags de habilitación de las acciones PELIGROSAS de WhatsApp.
 *
 * Contrato de seguridad: TODAS arrancan en `false`. Por defecto Estela jamás
 * envía un mensaje real, no graba ni manda audios, no llama ni videollama.
 * Habilitar cualquiera es un cambio DELIBERADO (código + autorización explícita
 * del usuario), NUNCA por voz y NUNCA por defecto.
 *
 * Value object PURO. El runtime mantiene una sola instancia (por defecto
 * [DISABLED]); no se persiste y vuelve a [DISABLED] en cada arranque.
 */
data class WhatsAppFeatureFlags(
    /** Tocar el botón ENVIAR de un mensaje de texto. */
    val realSendEnabled: Boolean = false,
    /** Iniciar la grabación de un audio. */
    val audioRecordEnabled: Boolean = false,
    /** Enviar un audio grabado. */
    val audioSendEnabled: Boolean = false,
    /** Iniciar una llamada de voz. */
    val callEnabled: Boolean = false,
    /** Iniciar una videollamada. */
    val videoCallEnabled: Boolean = false,
) {
    /** ¿Está habilitada la ejecución real de [action]? Las no-peligrosas: true. */
    fun isEnabled(action: WhatsAppActionType): Boolean = when (action) {
        WhatsAppActionType.SEND_MESSAGE -> realSendEnabled
        WhatsAppActionType.RECORD_AUDIO -> audioRecordEnabled
        WhatsAppActionType.SEND_AUDIO -> audioSendEnabled
        WhatsAppActionType.CALL -> callEnabled
        WhatsAppActionType.VIDEO_CALL -> videoCallEnabled
        else -> true
    }

    companion object {
        /** Configuración por defecto: TODO lo peligroso DESACTIVADO. */
        val DISABLED = WhatsAppFeatureFlags()
    }
}
