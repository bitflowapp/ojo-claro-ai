package com.ojoclaro.android.agent.diagnostics

enum class RobotCapability {
    VOICE_INPUT,
    TEXT_TO_SPEECH,
    ACCESSIBILITY_SCREEN_READING,
    OCR_READING,
    SCREEN_UNDERSTANDING,
    OPEN_APP,
    OPEN_DIALER,
    PREPARE_WHATSAPP_MESSAGE,
    CONTACT_LOOKUP,
    CONTACT_INSERT_REQUEST,
    WHATSAPP_VISIBLE_CHAT_DETECTION,
    SITUATION_BRAIN_CONTEXT,
    SITUATION_BRAIN_CONFIRMATION,
    HARD_CANCEL
}

enum class CapabilityReadiness {
    READY_STATIC,
    PARTIAL,
    NEEDS_RUNTIME_QA,
    BLOCKED,
    NOT_IMPLEMENTED
}

data class CapabilityStatus(
    val capability: RobotCapability,
    val status: CapabilityReadiness,
    val reason: String,
    val requiresRuntimeDevice: Boolean
) {
    init {
        require(reason.isNotBlank()) { "reason must not be blank" }
    }
}
