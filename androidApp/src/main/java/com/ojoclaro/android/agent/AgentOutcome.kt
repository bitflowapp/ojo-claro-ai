package com.ojoclaro.android.agent

import com.ojoclaro.android.external.ExternalActionEvent

data class AgentOutcome(
    val spokenText: String,
    val displayText: String = spokenText,
    val targetState: AgentState,
    val needsConfirmation: Boolean = false,
    val missingSlot: String? = null,
    val suggestedIntent: ParsedAgentIntent? = null,
    val externalEvent: ExternalActionEvent? = null,
    val safetyNotice: String? = null,
    val isError: Boolean = false,
    val shouldListenAgain: Boolean = true
)
