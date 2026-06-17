package com.ojoclaro.android.qa

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState

data class VoiceRealWorldCase(
    val spokenByUser: String,
    val recognizedByAndroid: String,
    val normalizedText: String,
    val agentState: AgentState,
    val expectedIntent: AgentIntent,
    val expectedSlots: Map<String, String>,
    val actualResult: String,
    val expectedResult: String,
    val notes: String
)

