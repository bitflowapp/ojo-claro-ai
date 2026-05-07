package com.ojoclaro.android.capabilities

enum class Capability(
    val technicalName: String,
    val userFriendlyName: String
) {
    CAMERA(
        technicalName = "camera",
        userFriendlyName = "cámara"
    ),
    ACCESSIBILITY_SERVICE(
        technicalName = "accessibility_service",
        userFriendlyName = "accesibilidad"
    ),
    WHATSAPP(
        technicalName = "whatsapp",
        userFriendlyName = "WhatsApp"
    ),
    TEXT_TO_SPEECH(
        technicalName = "text_to_speech",
        userFriendlyName = "voz"
    ),
    OCR_LOCAL(
        technicalName = "ocr_local",
        userFriendlyName = "lectura de texto con cámara"
    ),
    CLOUD_AI(
        technicalName = "cloud_ai",
        userFriendlyName = "IA avanzada"
    );

    companion object {
        const val MSG_CAMERA_MISSING =
            "Para leer texto, activá la cámara. Solo la uso cuando vos me lo pedís."

        const val MSG_ACCESSIBILITY_MISSING =
            "Para leer esta pantalla, activá Ojo Claro en Accesibilidad. Solo leo texto visible cuando vos me lo pedís."

        const val MSG_WHATSAPP_MISSING =
            "No encontré WhatsApp instalado. Cuando lo tengas, te ayudo a preparar mensajes."

        const val MSG_TTS_MISSING =
            "La voz del sistema todavía no está lista. Esperá un momento y probá de nuevo."

        const val MSG_OCR_MISSING =
            "Por ahora no puedo leer texto con la cámara en este teléfono."

        const val MSG_CLOUD_AI_MISSING =
            "La IA avanzada todavía no está activada. Por ahora puedo ayudarte con lectura local y acciones básicas."
    }
}

data class CapabilityStatus(
    val capability: Capability,
    val isAvailable: Boolean,
    val userMessageWhenMissing: String,
    val canRequestUserAction: Boolean
) {
    val technicalName: String get() = capability.technicalName
    val userFriendlyName: String get() = capability.userFriendlyName
}
