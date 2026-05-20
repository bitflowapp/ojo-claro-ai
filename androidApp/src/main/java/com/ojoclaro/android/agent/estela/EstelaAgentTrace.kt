package com.ojoclaro.android.agent.estela

import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.privacy.PrivacyGuard

data class EstelaAgentTrace(
    val sessionId: String,
    val turnId: Long,
    val rawUserText: String,
    val normalizedUserText: String,
    val detectedIntent: String?,
    val planSummary: String?,
    val riskLevel: EstelaRiskLevel?,
    val safetyDecision: EstelaSafetyDecision?,
    val requiresConfirmation: Boolean,
    val liveStateTransitions: List<EstelaLiveState>,
    val externalAction: String?,
    val fallbackToLegacy: Boolean,
    val blockedReason: String?,
    val finalAgentMessage: String?,
    val timestamp: Long,
    val events: List<EstelaAgentTraceEvent>
) {
    fun debugSummary(): String {
        val understood = detectedIntent ?: "sin intención"
        val plan = planSummary ?: "sin plan"
        val risk = riskLevel?.debugLabel() ?: "sin riesgo"
        val state = liveStateTransitions.lastOrNull()?.name ?: EstelaLiveState.Idle.name
        val fallback = if (fallbackToLegacy) "sí" else "no"
        return "Estela entendió: $understood\n" +
            "Plan: $plan\n" +
            "Seguridad: $risk\n" +
            "Estado: $state\n" +
            "Fallback legacy: $fallback"
    }

    companion object {
        fun empty(): EstelaAgentTrace = EstelaAgentTrace(
            sessionId = "",
            turnId = 0L,
            rawUserText = "",
            normalizedUserText = "",
            detectedIntent = null,
            planSummary = null,
            riskLevel = null,
            safetyDecision = null,
            requiresConfirmation = false,
            liveStateTransitions = emptyList(),
            externalAction = null,
            fallbackToLegacy = false,
            blockedReason = null,
            finalAgentMessage = null,
            timestamp = 0L,
            events = emptyList()
        )
    }
}

enum class EstelaAgentTraceEvent {
    CommandReceived,
    IntentDetected,
    PlanCreated,
    SafetyEvaluated,
    ConfirmationRequested,
    ActionDispatched,
    FallbackToLegacy,
    BlockedForSafety,
    ContextUpdated,
    Completed,
    Cancelled,
    Error
}

internal data class EstelaTraceDraft(
    val sessionId: String,
    val turnId: Long,
    val rawUserText: String,
    val normalizedUserText: String,
    val timestamp: Long,
    val events: MutableList<EstelaAgentTraceEvent> = mutableListOf(EstelaAgentTraceEvent.CommandReceived)
) {
    fun finish(
        intent: EstelaIntent,
        plan: EstelaPlan?,
        safetyDecision: EstelaSafetyDecision?,
        liveStates: List<EstelaLiveState>,
        externalActionEvent: ExternalActionEvent?,
        fallbackToLegacy: Boolean,
        finalAgentMessage: String?
    ): EstelaAgentTrace =
        EstelaAgentTrace(
            sessionId = sessionId,
            turnId = turnId,
            rawUserText = rawUserText,
            normalizedUserText = normalizedUserText,
            detectedIntent = intent.traceLabel(),
            planSummary = plan?.traceSummary(),
            riskLevel = plan?.riskLevel ?: if (safetyDecision?.allowed == false) {
                EstelaRiskLevel.PROHIBITED
            } else {
                null
            },
            safetyDecision = safetyDecision,
            requiresConfirmation = safetyDecision?.requiresConfirmation == true ||
                plan?.requiresConfirmation == true,
            liveStateTransitions = liveStates.toList(),
            externalAction = externalActionEvent?.traceLabel(),
            fallbackToLegacy = fallbackToLegacy,
            blockedReason = safetyDecision?.blockedReason,
            finalAgentMessage = finalAgentMessage,
            timestamp = timestamp,
            events = events.toList()
        )
}

internal fun EstelaIntent.traceLabel(): String =
    when (this) {
        is EstelaIntent.OpenApp -> "OpenApp($appName)"
        EstelaIntent.ReadScreen -> "ReadScreen"
        is EstelaIntent.OpenVisibleChat -> "OpenVisibleChat($targetName)"
        is EstelaIntent.ComposeMessage -> "ComposeMessage(target=$target)"
        is EstelaIntent.DialContact -> "DialContact($contactName)"
        is EstelaIntent.SearchYouTube -> "SearchYouTube"
        is EstelaIntent.OpenSpotify -> "OpenSpotify"
        EstelaIntent.DescribeEnvironment -> "DescribeEnvironment"
        EstelaIntent.ReadCameraText -> "ReadCameraText"
        EstelaIntent.Confirm -> "Confirm"
        EstelaIntent.Cancel -> "Cancel"
        EstelaIntent.Help -> "Help"
        is EstelaIntent.Unknown -> "Unknown"
    }

internal fun EstelaPlan.traceSummary(): String =
    steps.joinToString(" -> ") { it.traceLabel() }

internal fun EstelaPlanStep.traceLabel(): String =
    when (this) {
        is EstelaPlanStep.Speak -> "Speak"
        is EstelaPlanStep.UpdateState -> "UpdateState(${state.name})"
        is EstelaPlanStep.OpenExternalApp -> "OpenExternalApp($app)"
        EstelaPlanStep.ReadVisibleScreen -> "ReadVisibleScreen"
        is EstelaPlanStep.FindVisibleTarget -> "FindVisibleTarget($target)"
        is EstelaPlanStep.RequestConfirmation -> "RequestConfirmation"
        is EstelaPlanStep.ClickVisibleTarget -> "ClickVisibleTarget"
        is EstelaPlanStep.PrepareMessage -> "PrepareMessage(target=$target)"
        is EstelaPlanStep.OpenDialer -> "OpenDialer($contactOrNumber)"
        is EstelaPlanStep.SearchExternalApp -> "SearchExternalApp($app)"
        EstelaPlanStep.CancelPendingAction -> "CancelPendingAction"
        is EstelaPlanStep.Complete -> "Complete($result)"
    }

internal fun ExternalActionEvent.traceLabel(): String =
    when (this) {
        is ExternalActionEvent.ExternalAppHandoff ->
            "ExternalAppHandoff($externalAppName -> ${delegate.traceLabel()})"
        ExternalActionEvent.OpenWhatsApp -> "OpenWhatsApp"
        ExternalActionEvent.OpenPhone -> "OpenPhone"
        is ExternalActionEvent.ComposeWhatsAppMessage -> "ComposeWhatsAppMessage(contact=$contactName)"
        is ExternalActionEvent.OpenWhatsAppChat -> "OpenWhatsAppChat(contact=$contactName)"
        ExternalActionEvent.ReadVisibleScreen -> "ReadVisibleScreen"
        is ExternalActionEvent.DialPhoneNumber -> "DialPhoneNumber(contact=$contactName)"
        ExternalActionEvent.OpenMaps -> "OpenMaps"
        is ExternalActionEvent.OpenCurrentLocation -> "OpenCurrentLocation"
        is ExternalActionEvent.NavigateToDestination -> "NavigateToDestination"
        is ExternalActionEvent.NavigateToCoordinates -> "NavigateToCoordinates"
        ExternalActionEvent.RequestLocationPermission -> "RequestLocationPermission"
    }

internal fun EstelaRiskLevel.debugLabel(): String =
    when (this) {
        EstelaRiskLevel.LOW -> "riesgo bajo"
        EstelaRiskLevel.MEDIUM -> "riesgo medio"
        EstelaRiskLevel.HIGH -> "riesgo alto"
        EstelaRiskLevel.PROHIBITED -> "bloqueado"
    }

internal fun safeTraceText(text: String): String =
    PrivacyGuard.redactSensitiveText(text).replace(Regex("\\s+"), " ").trim()
