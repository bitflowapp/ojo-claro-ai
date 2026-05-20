package com.ojoclaro.android.agent.estela

import com.ojoclaro.android.external.ExternalActionEvent

data class EstelaAgentContext(
    val lastUserCommand: String? = null,
    val currentExternalApp: String? = null,
    val lastExternalApp: String? = null,
    val lastExternalHandoffAt: Long? = null,
    val currentScreenPackage: String? = null,
    val currentScreenSummary: String? = null,
    val pendingPlan: EstelaPlan? = null,
    val pendingConfirmation: EstelaPendingConfirmation? = null,
    val lastAgentMessage: String? = null,
    val lastSuccessfulAction: String? = null,
    val followUpMode: EstelaFollowUpMode = EstelaFollowUpMode.NONE,
    val cancelled: Boolean = false
)

enum class EstelaFollowUpMode {
    NONE,
    EXTERNAL_APP,
    WAITING_CONFIRMATION
}

sealed interface EstelaIntent {
    data class OpenApp(val appName: String) : EstelaIntent
    data object ReadScreen : EstelaIntent
    data class OpenVisibleChat(val targetName: String) : EstelaIntent
    data class ComposeMessage(val target: String, val message: String) : EstelaIntent
    data class DialContact(val contactName: String) : EstelaIntent
    data class SearchYouTube(val query: String) : EstelaIntent
    data class OpenSpotify(val query: String? = null) : EstelaIntent
    data object DescribeEnvironment : EstelaIntent
    data object ReadCameraText : EstelaIntent
    data object Confirm : EstelaIntent
    data object Cancel : EstelaIntent
    data object Help : EstelaIntent
    data class Unknown(val rawText: String) : EstelaIntent
}

data class EstelaPlan(
    val id: String,
    val userGoal: String,
    val steps: List<EstelaPlanStep>,
    val riskLevel: EstelaRiskLevel,
    val requiresConfirmation: Boolean,
    val spokenSummary: String,
    val visibleSummary: String
)

sealed interface EstelaPlanStep {
    data class Speak(val text: String) : EstelaPlanStep
    data class UpdateState(val state: EstelaLiveState) : EstelaPlanStep
    data class OpenExternalApp(val app: String) : EstelaPlanStep
    data object ReadVisibleScreen : EstelaPlanStep
    data class FindVisibleTarget(val target: String) : EstelaPlanStep
    data class RequestConfirmation(
        val summary: String,
        val confirmationToken: String
    ) : EstelaPlanStep

    data class ClickVisibleTarget(val targetId: String) : EstelaPlanStep
    data class PrepareMessage(val target: String, val message: String) : EstelaPlanStep
    data class OpenDialer(val contactOrNumber: String) : EstelaPlanStep
    data class SearchExternalApp(val app: String, val query: String) : EstelaPlanStep
    data object CancelPendingAction : EstelaPlanStep
    data class Complete(val result: String) : EstelaPlanStep
}

enum class EstelaLiveState {
    Idle,
    Listening,
    Understanding,
    Planning,
    ReadingScreen,
    WaitingConfirmation,
    Executing,
    Completed,
    Cancelled,
    NeedsPermission,
    BlockedForSafety,
    ErrorRecoverable
}

enum class EstelaRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    PROHIBITED
}

data class EstelaSafetyDecision(
    val allowed: Boolean,
    val requiresConfirmation: Boolean,
    val blockedReason: String? = null,
    val safeAlternative: String? = null
)

data class EstelaPendingConfirmation(
    val token: String,
    val plan: EstelaPlan,
    val summary: String,
    val createdAtMillis: Long
)

data class EstelaPlanningResult(
    val intent: EstelaIntent,
    val plan: EstelaPlan?
)

data class EstelaRuntimeResult(
    val handled: Boolean,
    val intent: EstelaIntent,
    val plan: EstelaPlan?,
    val context: EstelaAgentContext,
    val liveStates: List<EstelaLiveState>,
    val spokenText: String?,
    val externalAction: ExternalActionEvent? = null,
    val pendingConfirmation: EstelaPendingConfirmation? = null,
    val fallbackToLegacy: Boolean = false,
    val safetyDecision: EstelaSafetyDecision? = null
)
