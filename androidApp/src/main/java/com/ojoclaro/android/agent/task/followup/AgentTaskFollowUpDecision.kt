package com.ojoclaro.android.agent.task.followup

import com.ojoclaro.android.agent.task.screen.AgentTaskScreenUpdateResult

enum class AgentTaskFollowUpAction {
    NO_OP,
    OBSERVE_ONLY,
    SPEAK,
    SUPPRESS
}

enum class AgentTaskFollowUpImportance {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

enum class AgentTaskFollowUpSuppressReason {
    FLAG_DISABLED,
    NO_ACTIVE_TASK,
    NO_SNAPSHOT,
    NO_RELEVANT_CHANGE,
    NO_SPEECH_CANDIDATE,
    PENDING_CONFIRMATION,
    TALKBACK_ACTIVE,
    COOLDOWN,
    UNSAFE_SPEECH
}

data class AgentTaskFollowUpDecision(
    val action: AgentTaskFollowUpAction,
    val trigger: AgentTaskFollowUpTrigger,
    val importance: AgentTaskFollowUpImportance = AgentTaskFollowUpImportance.LOW,
    val observationResult: AgentTaskScreenUpdateResult? = null,
    val spokenText: String? = null,
    val semanticKey: String? = null,
    val reasonKey: String? = null,
    val suppressReason: AgentTaskFollowUpSuppressReason? = null,
    val forceSpeech: Boolean = false
) {
    val observed: Boolean
        get() = observationResult != null

    companion object {
        fun noOp(
            trigger: AgentTaskFollowUpTrigger,
            suppressReason: AgentTaskFollowUpSuppressReason
        ): AgentTaskFollowUpDecision = AgentTaskFollowUpDecision(
            action = AgentTaskFollowUpAction.NO_OP,
            trigger = trigger,
            suppressReason = suppressReason
        )
    }
}
