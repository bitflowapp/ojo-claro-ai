package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState

data class LlmAgentRequest(
    val originalText: String,
    val normalizedText: String,
    val locale: String,
    val agentState: AgentState,
    val externalApp: String?,
    val memorySummary: String,
    val knownSafeContacts: List<String>,
    val knownPlaces: List<String>,
    val activePendingTasks: List<String>,
    val allowedIntents: List<AgentIntent>,
    val forbiddenActions: List<String>
)

