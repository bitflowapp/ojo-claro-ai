package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Respuesta del WhatsAppGuidedWorkflowUseCase.
 *
 * Sealed para forzar manejo explícito. La regla más importante es
 * [NotAWhatsAppCommand]: si el texto del usuario NO es una consulta guiada
 * de WhatsApp (e.g., "repetí", "abrí WhatsApp", "resumí la pantalla"), el
 * use case devuelve este caso y el caller sigue su flujo normal sin tocar
 * el comportamiento previo.
 */
sealed class WhatsAppGuidedResponse {

    /** El texto no era un comando guiado de WhatsApp. NO consumir. */
    object NotAWhatsAppCommand : WhatsAppGuidedResponse()

    /**
     * El comando se entendió, pero el snapshot no confirma que estemos en
     * WhatsApp. Pedimos abrir la app primero.
     */
    data class NotInWhatsApp(
        val spokenText: String
    ) : WhatsAppGuidedResponse()

    /**
     * Hay señales débiles. Honestos: no afirmamos nada y pedimos que el
     * usuario abra el chat y vuelva a pedirlo.
     */
    data class StateNotConfident(
        val spokenText: String
    ) : WhatsAppGuidedResponse()

    /**
     * Hay confianza para guiar. El [spokenText] es la guía verbal que la UI
     * debe hablar. Nunca incluye contenido del chat — solo plantillas fijas
     * de instrucción.
     */
    data class Guidance(
        val command: WhatsAppGuidedCommand,
        val state: WhatsAppScreenState,
        val spokenText: String
    ) : WhatsAppGuidedResponse()
}
