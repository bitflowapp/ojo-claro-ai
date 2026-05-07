package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent

data class LlmAgentResponse(
    val intent: AgentIntent?,
    val responseType: String? = null,
    val confidence: Float,
    val contactName: String?,
    val messageText: String?,
    val proposedMessage: String?,
    val destination: String?,
    val locationAlias: String?,
    val routineName: String?,
    val pendingTask: String?,
    val missingSlots: List<String>,
    val userFacingQuestion: String?,
    val suggestionText: String?,
    val requiresConfirmation: Boolean,
    val shouldExecuteImmediately: Boolean,
    val safetyNotes: String?
)
