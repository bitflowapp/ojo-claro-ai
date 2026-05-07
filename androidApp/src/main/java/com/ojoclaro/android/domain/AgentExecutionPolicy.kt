package com.ojoclaro.android.domain

import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.global.GlobalAssistantCapability
import com.ojoclaro.android.global.GlobalAssistantMode

sealed class AgentDecision(open val debugLabel: String) {
    data class AskQuestion(
        val spokenText: String,
        val targetState: AgentState,
        override val debugLabel: String
    ) : AgentDecision(debugLabel)

    data class RequestConfirmation(
        val spokenText: String,
        override val debugLabel: String
    ) : AgentDecision(debugLabel)

    data class ExecuteExternalAction(
        val spokenText: String,
        val externalEvent: ExternalActionEvent,
        override val debugLabel: String
    ) : AgentDecision(debugLabel)

    data class StayInApp(
        val spokenText: String,
        val targetState: AgentState? = null,
        override val debugLabel: String
    ) : AgentDecision(debugLabel)

    data class RejectUnsafe(
        val spokenText: String,
        override val debugLabel: String
    ) : AgentDecision(debugLabel)

    data class RetryListening(
        val spokenText: String,
        override val debugLabel: String
    ) : AgentDecision(debugLabel)

    data class Cancel(
        val spokenText: String = "Cancelado.",
        override val debugLabel: String = "CANCEL"
    ) : AgentDecision(debugLabel)
}

data class AgentExecutionInput(
    val originalText: String,
    val normalizedText: String,
    val detectedIntent: AgentIntent,
    val currentState: AgentState?,
    val hasPendingAction: Boolean,
    val externalContinuation: GlobalAssistantCapability,
    val safetyAllowed: Boolean = true
)

class AgentExecutionPolicy {
    fun decideWhatsAppGuidedStart(input: AgentExecutionInput): AgentDecision {
        if (!input.safetyAllowed) {
            return AgentDecision.RejectUnsafe("No puedo hacer eso de forma segura.", "REJECT_UNSAFE")
        }
        if (input.detectedIntent == AgentIntent.UNKNOWN) {
            return AgentDecision.RetryListening("No entendi. Proba de nuevo.", "UNKNOWN_RETRY")
        }
        if (input.externalContinuation.canSafelyContinueOutsideApp) {
            return AgentDecision.ExecuteExternalAction(
                spokenText = GlobalAssistantMode.WHATSAPP_CONTINUATION_TEXT,
                externalEvent = handoffEvent(
                    externalAppName = "WhatsApp",
                    spokenText = GlobalAssistantMode.WHATSAPP_CONTINUATION_TEXT,
                    delegate = ExternalActionEvent.OpenWhatsApp
                ),
                debugLabel = "EXECUTE_WHATSAPP_GLOBAL_CONTINUATION"
            )
        }
        return AgentDecision.AskQuestion(
            spokenText = AgentConversationManager.WHATSAPP_GUIDED_QUESTION,
            targetState = AgentState.WAITING_WHATSAPP_ACTION,
            debugLabel = "ASK_WHATSAPP_ACTION_IN_APP"
        )
    }

    fun decidePrincipalAppOpen(
        externalAppName: String,
        spokenText: String,
        delegate: ExternalActionEvent,
        capability: GlobalAssistantCapability
    ): AgentDecision {
        if (capability.canSafelyContinueOutsideApp || capability.fallbackReturnReady) {
            return AgentDecision.ExecuteExternalAction(
                spokenText = spokenText,
                externalEvent = handoffEvent(
                    externalAppName = externalAppName,
                    spokenText = spokenText,
                    delegate = delegate
                ),
                debugLabel = if (capability.canSafelyContinueOutsideApp) {
                    "EXECUTE_EXTERNAL_WITH_CONTINUATION"
                } else {
                    "EXECUTE_EXTERNAL_WITH_FALLBACK_RETURN"
                }
            )
        }
        return AgentDecision.StayInApp(
            spokenText = "Para seguir, activa notificaciones y volve a Ojo Claro.",
            debugLabel = "BLOCK_EXTERNAL_NO_RETURN"
        )
    }

    fun decideNonPrincipalAppOpen(
        promptText: String,
        waitingState: AgentState,
        capability: GlobalAssistantCapability,
        externalAppName: String,
        spokenText: String,
        delegate: ExternalActionEvent
    ): AgentDecision {
        if (capability.canSafelyContinueOutsideApp) {
            return AgentDecision.ExecuteExternalAction(
                spokenText = spokenText,
                externalEvent = handoffEvent(
                    externalAppName = externalAppName,
                    spokenText = spokenText,
                    delegate = delegate
                ),
                debugLabel = "EXECUTE_EXTERNAL_WITH_CONTINUATION"
            )
        }
        return AgentDecision.AskQuestion(
            spokenText = promptText,
            targetState = waitingState,
            debugLabel = "ASK_BEFORE_EXTERNAL_APP"
        )
    }

    fun forbidsExternalExecutionAfterFallback(spokenText: String): Boolean {
        val normalized = spokenText.lowercase()
        return listOf(
            "no entendi",
            "no escuch",
            "proba de nuevo",
            "no pude conectar"
        ).any { token -> normalized.contains(token) }
    }

    private fun handoffEvent(
        externalAppName: String,
        spokenText: String,
        delegate: ExternalActionEvent
    ): ExternalActionEvent.ExternalAppHandoff =
        ExternalActionEvent.ExternalAppHandoff(
            externalAppName = externalAppName,
            reason = spokenText,
            returnHint = "Para seguir, toca Escuchar o volve a Ojo Claro.",
            spokenText = spokenText,
            delegate = delegate
        )
}
