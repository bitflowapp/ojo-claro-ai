package com.ojoclaro.android.external

/**
 * Eventos externos que la capa UI/Android debe ejecutar.
 *
 * Regla:
 * Estos eventos no deben guardar datos sensibles ni ejecutar acciones irreversibles.
 * Solo abren apps, preparan acciones o piden lectura visible bajo control del usuario.
 */
sealed interface ExternalActionEvent {

    /**
     * Handoff honesto a una app externa.
     *
     * La UI debe:
     *  - Pausar el loop de voz antes de ejecutar [delegate].
     *  - Avisar que Ojo Claro no escucha de forma confiable dentro de la app externa.
     *  - Mostrar, si el sistema lo permite, una notificación de retorno.
     *
     * Esta señal NO habilita escucha en background, hotword, taps automáticos ni
     * navegación con AccessibilityService.
     */
    data class ExternalAppHandoff(
        val externalAppName: String,
        val reason: String,
        val returnHint: String,
        val spokenText: String,
        val delegate: ExternalActionEvent
    ) : ExternalActionEvent

    /**
     * Abre WhatsApp si está instalado.
     * No abre chats específicos ni envía mensajes.
     */
    data object OpenWhatsApp : ExternalActionEvent

    /**
     * Abre el marcador del sistema sin numero.
     *
     * Importante:
     * No requiere CALL_PHONE y no inicia una llamada.
     */
    data object OpenPhone : ExternalActionEvent

    /**
     * Prepara un mensaje para WhatsApp.
     *
     * Importante:
     * El mensaje NO se envía automáticamente.
     * El usuario debe revisar y confirmar manualmente dentro de WhatsApp.
     */
    data class ComposeWhatsAppMessage(
        val confirmationId: String,
        val contactName: String,
        val messageText: String
    ) : ExternalActionEvent

    /**
     * Abre WhatsApp directamente sobre el chat de un contacto guardado en memoria.
     *
     * Importante:
     *  - NO incluye mensaje. El campo de texto del chat queda vacío.
     *  - NO envía nada automáticamente.
     *  - El número debe venir resuelto desde memoria local (TRUSTED_CONTACT /
     *    EMERGENCY_CONTACT). Esta capa nunca lo busca en la libreta del sistema.
     *  - Usa Intent.ACTION_VIEW con URI https://wa.me/<solo dígitos>.
     */
    data class OpenWhatsAppChat(
        val confirmationId: String,
        val contactName: String,
        val phoneE164: String
    ) : ExternalActionEvent

    /**
     * Lee texto visible de la pantalla usando AccessibilityService.
     *
     * No debe guardar contenido.
     * No debe enviar contenido al backend.
     * No debe leer contraseñas.
     */
    data object ReadVisibleScreen : ExternalActionEvent

    /**
     * Abre el marcador del sistema con un numero preparado.
     *
     * Importante:
     * Usa ACTION_DIAL, nunca ACTION_CALL. El usuario toca llamar manualmente.
     */
    data class DialPhoneNumber(
        val contactName: String,
        val phoneNumber: String?
    ) : ExternalActionEvent

    data object OpenMaps : ExternalActionEvent

    /**
     * Abre una app instalada por paquete, usando solo ACTION_MAIN / LAUNCHER.
     *
     * Importante:
     * No toca botones dentro de la app, no envia mensajes, no compra, no paga
     * y no confirma acciones sensibles. Es solamente un handoff seguro.
     */
    data class OpenSafeApp(
        val appName: String,
        val packageName: String,
        val userConfirmed: Boolean = false
    ) : ExternalActionEvent

    data class OpenCurrentLocation(
        val latitude: Double,
        val longitude: Double
    ) : ExternalActionEvent

    data class NavigateToDestination(
        val destination: String
    ) : ExternalActionEvent

    data class NavigateToCoordinates(
        val label: String,
        val latitude: Double,
        val longitude: Double
    ) : ExternalActionEvent

    data object RequestLocationPermission : ExternalActionEvent
}
