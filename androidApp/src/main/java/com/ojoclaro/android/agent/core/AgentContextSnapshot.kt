package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.risk.RiskWarning

/**
 * Snapshot inmutable de "qué sabe el agente ahora" cuando se invoca al planner.
 *
 * No persiste. No guarda PII más allá de los snapshots de memoria/preferencias
 * que el caller decida pasar.
 *
 * Los campos [screenRiskWarnings] y [commandRiskWarnings] son opcionales:
 * cuando el caller los precomputa (vía [AgentContext.build]), el evaluador no
 * vuelve a correr el detector y trabaja sobre datos ya filtrados. Si quedan
 * vacíos, el evaluador puede caer al fallback regex sobre rawText.
 */
data class AgentContextSnapshot(
    val mode: AgentExecutionMode,
    val agentState: AgentState,
    val screen: AgentScreenContext? = null,
    val memory: AgentMemoryContext = AgentMemoryContext(),
    val hasPendingExternalAction: Boolean = false,
    val hasActiveChainedPlan: Boolean = false,
    val isInEmergency: Boolean = false,
    val nowMillis: Long,
    val screenRiskWarnings: List<RiskWarning> = emptyList(),
    val commandRiskWarnings: List<RiskWarning> = emptyList()
) {
    val isPrivacyHotZone: Boolean
        get() = screen?.isSensitive == true

    val hasAnyRiskWarning: Boolean
        get() = screenRiskWarnings.isNotEmpty() || commandRiskWarnings.isNotEmpty()
}
