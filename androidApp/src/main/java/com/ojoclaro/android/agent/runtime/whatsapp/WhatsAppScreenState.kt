package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Estado inferido de la pantalla actual respecto a WhatsApp.
 *
 * Importante:
 *  - No contiene contenido de mensajes ni labels de elementos. Solo flags
 *    booleanos y un nivel de confianza. Eso garantiza que el resto del
 *    sistema NO pueda accidentalmente filtrar contenido privado del chat.
 *  - Se construye desde un ScreenSnapshot por [WhatsAppScreenDetector].
 *  - No se persiste y no se serializa.
 */
data class WhatsAppScreenState(
    val isOpen: Boolean,
    val isInChat: Boolean,
    val hasMessageField: Boolean,
    val hasCameraButton: Boolean,
    val hasAttachButton: Boolean,
    val hasSendButton: Boolean,
    val hasMicrophoneButton: Boolean,
    val hasBackButton: Boolean,
    val confidence: WhatsAppDetectionConfidence,
    val packageNameMatched: Boolean
) {

    val isUnknown: Boolean
        get() = confidence == WhatsAppDetectionConfidence.UNKNOWN

    companion object {
        val UNKNOWN: WhatsAppScreenState = WhatsAppScreenState(
            isOpen = false,
            isInChat = false,
            hasMessageField = false,
            hasCameraButton = false,
            hasAttachButton = false,
            hasSendButton = false,
            hasMicrophoneButton = false,
            hasBackButton = false,
            confidence = WhatsAppDetectionConfidence.UNKNOWN,
            packageNameMatched = false
        )
    }
}

/**
 * Confianza de la detección.
 *
 *  - HIGH: package name coincide con WhatsApp.
 *  - MEDIUM: package name no expuesto, pero hay señales fuertes (labels o
 *    estructura típica de WhatsApp).
 *  - LOW: hay alguna señal débil pero no es confiable.
 *  - UNKNOWN: no hay snapshot o no se puede afirmar nada.
 */
enum class WhatsAppDetectionConfidence {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH
}
