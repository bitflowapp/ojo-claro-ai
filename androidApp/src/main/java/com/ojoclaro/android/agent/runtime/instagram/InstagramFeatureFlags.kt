package com.ojoclaro.android.agent.runtime.instagram

/**
 * Flags de habilitación de las acciones PELIGROSAS de Instagram Direct.
 *
 * Contrato de seguridad: TODAS arrancan en `false`. Por defecto Estela jamás
 * envía un mensaje real ni inicia una videollamada en Instagram. Habilitar
 * cualquiera es un cambio DELIBERADO (código + autorización explícita del
 * usuario), NUNCA por voz y NUNCA por defecto. Mismo patrón que
 * [com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppFeatureFlags].
 *
 * Value object PURO. El runtime mantiene una sola instancia (por defecto
 * [DISABLED]); no se persiste y vuelve a [DISABLED] en cada arranque.
 */
data class InstagramFeatureFlags(
    /** Tocar el botón ENVIAR de un mensaje de texto en Instagram. */
    val realSendEnabled: Boolean = false,
    /** Iniciar una videollamada de Instagram. */
    val videoCallEnabled: Boolean = false,
) {
    companion object {
        /** Configuración por defecto: TODO lo peligroso DESACTIVADO. */
        val DISABLED = InstagramFeatureFlags()
    }
}
