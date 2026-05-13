package com.ojoclaro.android.model

enum class RobotSessionState {
    OFF,
    READY,
    LISTENING,
    PROCESSING,
    SPEAKING,
    WAITING_WHATSAPP,
    WAITING_CONFIRMATION,
    ERROR_RECOVERABLE
}

fun RobotSessionState.humanLabel(): String =
    when (this) {
        RobotSessionState.OFF -> "Robot apagado"
        RobotSessionState.READY -> "Listo para ayudarte"
        RobotSessionState.LISTENING -> "Te estoy escuchando"
        RobotSessionState.PROCESSING -> "Procesando"
        RobotSessionState.SPEAKING -> "Estoy respondiendo"
        RobotSessionState.WAITING_WHATSAPP -> "Esperando WhatsApp"
        RobotSessionState.WAITING_CONFIRMATION -> "Esperando confirmación"
        RobotSessionState.ERROR_RECOVERABLE -> "Necesito que repitas o resetees"
    }
