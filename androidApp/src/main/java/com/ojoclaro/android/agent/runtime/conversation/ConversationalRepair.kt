package com.ojoclaro.android.agent.runtime.conversation

data class ConversationalRepairRequest(
    val reason: RobotFailureReason,
    val context: RobotShortTermContext = RobotShortTermContext(),
    val robotEnabled: Boolean = true,
    val externalApp: RobotExternalApp = RobotExternalApp.NONE,
    val pendingState: RobotPendingState = RobotPendingState.NONE,
    val suggestedIntent: RepairSuggestedIntent = RepairSuggestedIntent.NONE
)

data class ConversationalRepairResponse(
    val spokenText: String
)

object ConversationalRepair {

    const val NOT_HEARD: String =
        "No escuché bien. Probá decir: qué hay en pantalla, abrí WhatsApp o repetí."

    const val NOISE: String =
        "No entendí bien. Decime una acción como: qué hay en pantalla, abrí WhatsApp o resetear."

    const val SECOND_FAILURE: String =
        "Sigo sin entender. Tocá Resetear flujo o decime: ayuda."

    const val THIRD_FAILURE: String =
        "Te recomiendo resetear el flujo para empezar limpio."

    const val WAITING_WHATSAPP: String =
        "Estoy esperando una acción de WhatsApp. Podés decir: abrir WhatsApp, chat de un contacto, mensaje para un contacto o cancelar."

    const val SENSITIVE_SCREEN: String =
        "Esta pantalla puede contener información sensible. No voy a leerla."

    const val SAFE_AI_UNAVAILABLE: String =
        "No lo pude resolver con seguridad. Decime una acción concreta."

    const val CONFIRMATION_UNCLEAR: String =
        "No entendí la confirmación. Decí sí o cancelar."

    const val CONFIRMATION_CANCELLED: String =
        "Listo, no hago nada."

    const val ROBOT_OFF: String =
        "El robot está apagado. Decí ojo claro o tocá Encender robot."

    const val NORMAL_SUGGESTIONS: String =
        "Podés decir: qué hay en pantalla, qué puedo hacer acá o abrir WhatsApp."

    const val WHATSAPP_OPEN_SUGGESTIONS: String =
        "Podés decir: qué chats ves, qué puedo hacer en este chat o cómo mando una foto."

    fun response(request: ConversationalRepairRequest): ConversationalRepairResponse {
        val text = when {
            !request.robotEnabled -> ROBOT_OFF
            request.reason == RobotFailureReason.SENSITIVE_SCREEN -> SENSITIVE_SCREEN
            request.reason == RobotFailureReason.SAFE_AI_UNAVAILABLE -> SAFE_AI_UNAVAILABLE
            request.pendingState == RobotPendingState.WHATSAPP ||
                request.reason == RobotFailureReason.WAITING_WHATSAPP -> WAITING_WHATSAPP
            request.reason == RobotFailureReason.CONFIRMATION_UNCLEAR -> CONFIRMATION_UNCLEAR
            request.context.consecutiveFailures >= 3 -> THIRD_FAILURE
            request.context.consecutiveFailures == 2 -> SECOND_FAILURE
            request.suggestedIntent != RepairSuggestedIntent.NONE ->
                possibleCommand(request.suggestedIntent)
            request.reason == RobotFailureReason.EMPTY_INPUT -> NOT_HEARD
            else -> NOISE
        }
        return ConversationalRepairResponse(text)
    }

    fun possibleCommand(intent: RepairSuggestedIntent): String =
        "¿Quisiste decir ${intent.spokenLabel}?"

    fun contextualSuggestions(
        robotEnabled: Boolean,
        externalApp: RobotExternalApp,
        pendingState: RobotPendingState
    ): String =
        when {
            !robotEnabled -> ROBOT_OFF
            pendingState == RobotPendingState.WHATSAPP -> WAITING_WHATSAPP
            externalApp == RobotExternalApp.WHATSAPP -> WHATSAPP_OPEN_SUGGESTIONS
            else -> NORMAL_SUGGESTIONS
        }

    fun containsPublicDebugToken(text: String): Boolean {
        val lower = text.lowercase()
        return forbiddenPublicTokens.any { token -> token in lower }
    }

    private val forbiddenPublicTokens: Set<String> = setOf(
        "no_match",
        "pending_whatsapp_action",
        "assistant_api",
        "fallback",
        "proxy",
        "intent",
        "handler",
        "slot",
        "no estoy usando la " + "ia"
    )
}
