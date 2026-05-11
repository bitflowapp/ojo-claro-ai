package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentState

/**
 * Snapshot inmutable de "qué sabe el agente ahora" cuando se invoca al planner.
 *
 * No persiste. No guarda PII más allá de los snapshots de memoria/preferencias
 * que el caller decida pasar.
 */
data class AgentContextSnapshot(
    val mode: AgentExecutionMode,
    val agentState: AgentState,
    val screen: AgentScreenContext? = null,
    val memory: AgentMemoryContext = AgentMemoryContext(),
    val hasPendingExternalAction: Boolean = false,
    val hasActiveChainedPlan: Boolean = false,
    val isInEmergency: Boolean = false,
    val nowMillis: Long
) {
    val isPrivacyHotZone: Boolean
        get() = screen?.isSensitive == true
}
