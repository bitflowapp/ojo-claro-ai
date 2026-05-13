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

fun RobotSessionState.canTransitionTo(next: RobotSessionState): Boolean =
    when (this) {
        RobotSessionState.OFF -> next in arrayOf(
            RobotSessionState.OFF,
            RobotSessionState.READY,
            RobotSessionState.ERROR_RECOVERABLE
        )
        RobotSessionState.READY -> next in arrayOf(
            RobotSessionState.READY,
            RobotSessionState.LISTENING,
            RobotSessionState.PROCESSING,
            RobotSessionState.SPEAKING,
            RobotSessionState.WAITING_WHATSAPP,
            RobotSessionState.WAITING_CONFIRMATION,
            RobotSessionState.ERROR_RECOVERABLE,
            RobotSessionState.OFF
        )
        RobotSessionState.LISTENING -> next in arrayOf(
            RobotSessionState.LISTENING,
            RobotSessionState.PROCESSING,
            RobotSessionState.SPEAKING,
            RobotSessionState.ERROR_RECOVERABLE,
            RobotSessionState.READY,
            RobotSessionState.OFF
        )
        RobotSessionState.PROCESSING -> next in arrayOf(
            RobotSessionState.PROCESSING,
            RobotSessionState.SPEAKING,
            RobotSessionState.READY,
            RobotSessionState.WAITING_WHATSAPP,
            RobotSessionState.WAITING_CONFIRMATION,
            RobotSessionState.ERROR_RECOVERABLE,
            RobotSessionState.OFF
        )
        RobotSessionState.SPEAKING -> next in arrayOf(
            RobotSessionState.SPEAKING,
            RobotSessionState.READY,
            RobotSessionState.LISTENING,
            RobotSessionState.OFF,
            RobotSessionState.ERROR_RECOVERABLE
        )
        RobotSessionState.WAITING_WHATSAPP -> next in arrayOf(
            RobotSessionState.WAITING_WHATSAPP,
            RobotSessionState.PROCESSING,
            RobotSessionState.SPEAKING,
            RobotSessionState.READY,
            RobotSessionState.WAITING_CONFIRMATION,
            RobotSessionState.OFF,
            RobotSessionState.ERROR_RECOVERABLE
        )
        RobotSessionState.WAITING_CONFIRMATION -> next in arrayOf(
            RobotSessionState.WAITING_CONFIRMATION,
            RobotSessionState.PROCESSING,
            RobotSessionState.SPEAKING,
            RobotSessionState.READY,
            RobotSessionState.OFF,
            RobotSessionState.ERROR_RECOVERABLE
        )
        RobotSessionState.ERROR_RECOVERABLE -> next in arrayOf(
            RobotSessionState.ERROR_RECOVERABLE,
            RobotSessionState.READY,
            RobotSessionState.LISTENING,
            RobotSessionState.SPEAKING,
            RobotSessionState.OFF
        )
    }

fun RobotSessionState.requiresMicPaused(): Boolean =
    this in setOf(
        RobotSessionState.OFF,
        RobotSessionState.PROCESSING,
        RobotSessionState.SPEAKING,
        RobotSessionState.ERROR_RECOVERABLE
    )

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
