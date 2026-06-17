package com.ojoclaro.android.agent.runtime.conversation

enum class RobotRecognizedKind {
    NONE,
    EMPTY_INPUT,
    NOISE,
    POSSIBLE_COMMAND,
    COMMAND,
    SUCCESS,
    FAILURE
}

enum class RobotFailureReason {
    NONE,
    EMPTY_INPUT,
    RECOGNIZER_NOISE,
    LOW_CONFIDENCE,
    WAITING_WHATSAPP,
    SENSITIVE_SCREEN,
    SAFE_AI_UNAVAILABLE,
    CONFIRMATION_UNCLEAR
}

enum class RepairSuggestedIntent(
    val spokenLabel: String
) {
    NONE(""),
    OPEN_WHATSAPP("abrir WhatsApp"),
    READ_VISIBLE_SCREEN("qué hay en pantalla"),
    WHAT_CAN_I_DO("qué puedo hacer acá"),
    READ_VISIBLE_CHATS("qué chats ves"),
    REPEAT_LAST("repetir"),
    RESET_FLOW("resetear"),
    STOP_SPEAKING("callate"),
    PAUSE_ROBOT("pausar robot"),
    ENABLE_ROBOT("encender robot"),
    HELP("ayuda")
}

enum class RobotActiveHandler {
    NONE,
    VOICE_CORRECTION,
    ROBOT_SESSION,
    STOP_SPEAKING,
    RESET_FLOW,
    REPEAT_LAST,
    HELP,
    SCREEN_UNDERSTANDING,
    WHATSAPP_GUIDED,
    VISIBLE_CHATS,
    SAFE_AI_FALLBACK,
    EXTERNAL_COMMAND,
    AGENT_CONVERSATION,
    HUMAN_ROUTINE
}

enum class RobotExternalApp {
    NONE,
    WHATSAPP,
    OTHER
}

enum class RobotPendingState {
    NONE,
    WHATSAPP,
    CONFIRMATION
}

data class RobotShortTermContext(
    val lastRecognizedKind: RobotRecognizedKind = RobotRecognizedKind.NONE,
    val lastSuggestedIntent: RepairSuggestedIntent = RepairSuggestedIntent.NONE,
    val lastAskedConfirmation: Boolean = false,
    val lastFailureReason: RobotFailureReason = RobotFailureReason.NONE,
    val lastActiveHandler: RobotActiveHandler = RobotActiveHandler.NONE,
    val lastExternalApp: RobotExternalApp = RobotExternalApp.NONE,
    val lastPendingState: RobotPendingState = RobotPendingState.NONE,
    val consecutiveFailures: Int = 0
) {
    fun recordFailure(
        reason: RobotFailureReason,
        kind: RobotRecognizedKind = RobotRecognizedKind.FAILURE,
        suggestedIntent: RepairSuggestedIntent = lastSuggestedIntent,
        activeHandler: RobotActiveHandler = lastActiveHandler,
        externalApp: RobotExternalApp = lastExternalApp,
        pendingState: RobotPendingState = lastPendingState,
        askedConfirmation: Boolean = false
    ): RobotShortTermContext =
        copy(
            lastRecognizedKind = kind,
            lastSuggestedIntent = suggestedIntent,
            lastAskedConfirmation = askedConfirmation,
            lastFailureReason = reason,
            lastActiveHandler = activeHandler,
            lastExternalApp = externalApp,
            lastPendingState = pendingState,
            consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(MAX_FAILURES)
        )

    fun recordSuccess(
        activeHandler: RobotActiveHandler,
        suggestedIntent: RepairSuggestedIntent = RepairSuggestedIntent.NONE,
        externalApp: RobotExternalApp = lastExternalApp,
        pendingState: RobotPendingState = RobotPendingState.NONE
    ): RobotShortTermContext =
        copy(
            lastRecognizedKind = RobotRecognizedKind.SUCCESS,
            lastSuggestedIntent = suggestedIntent,
            lastAskedConfirmation = false,
            lastFailureReason = RobotFailureReason.NONE,
            lastActiveHandler = activeHandler,
            lastExternalApp = externalApp,
            lastPendingState = pendingState,
            consecutiveFailures = 0
        )

    fun withPendingState(pendingState: RobotPendingState): RobotShortTermContext =
        copy(lastPendingState = pendingState)

    fun withConfirmation(
        suggestedIntent: RepairSuggestedIntent = lastSuggestedIntent,
        activeHandler: RobotActiveHandler = lastActiveHandler,
        externalApp: RobotExternalApp = lastExternalApp
    ): RobotShortTermContext =
        copy(
            lastRecognizedKind = RobotRecognizedKind.POSSIBLE_COMMAND,
            lastSuggestedIntent = suggestedIntent,
            lastAskedConfirmation = true,
            lastFailureReason = RobotFailureReason.LOW_CONFIDENCE,
            lastActiveHandler = activeHandler,
            lastExternalApp = externalApp,
            lastPendingState = RobotPendingState.CONFIRMATION
        )

    fun reset(): RobotShortTermContext = RobotShortTermContext()

    companion object {
        private const val MAX_FAILURES = 3
    }
}
