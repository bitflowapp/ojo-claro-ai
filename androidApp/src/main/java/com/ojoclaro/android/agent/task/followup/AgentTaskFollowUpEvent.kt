package com.ojoclaro.android.agent.task.followup

import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.AgentTaskPlan

enum class AgentTaskFollowUpTrigger {
    FLAG_DISABLED,
    NO_ACTIVE_TASK,
    NO_SNAPSHOT,
    NO_RELEVANT_CHANGE,
    PACKAGE_CHANGED,
    TASK_SCREEN_CUE_CHANGED,
    SENSITIVE_SCREEN
}

data class AgentTaskFollowUpEvent(
    val currentPlan: AgentTaskPlan?,
    val previousSnapshot: StructuredScreenSnapshot?,
    val currentSnapshot: StructuredScreenSnapshot?,
    val currentAppStateName: String? = null,
    val hasPendingConfirmation: Boolean = false,
    val isTalkBackActive: Boolean = false,
    val nowMillis: Long
)
