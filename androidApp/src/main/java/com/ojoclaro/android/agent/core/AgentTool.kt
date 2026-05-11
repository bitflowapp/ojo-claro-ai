package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentIntent

/**
 * Metadata declarativa de una herramienta del agente.
 *
 * No contiene ejecución. La ejecución real sigue viviendo en la capa Android
 * (WhatsAppIntentHelper, MapsActionExecutor, PhoneActionExecutor,
 * AccessibilityScreenReader, etc.). Este modelo solo describe qué hace el tool,
 * qué slots necesita (nombres del AgentSlotName), qué riesgo tiene y bajo qué
 * modo se puede usar.
 */
data class AgentTool(
    val id: AgentToolId,
    val displayName: String,
    val description: String,
    val capabilities: Set<AgentToolCapability>,
    val requiredSlots: Set<String>,
    val optionalSlots: Set<String> = emptySet(),
    val risk: AgentRiskLevel,
    val requiresConfirmation: Boolean,
    val supportedModes: Set<AgentExecutionMode>,
    val emergencyCapable: Boolean,
    val coversIntents: Set<AgentIntent>
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(description.isNotBlank()) { "description must not be blank" }
        require(supportedModes.isNotEmpty()) { "tool must support at least one execution mode" }
        if (risk == AgentRiskLevel.BLOCKED) {
            require(!requiresConfirmation) {
                "blocked tool never asks for confirmation — it must not run at all"
            }
        }
    }

    fun isAvailableIn(mode: AgentExecutionMode): Boolean =
        mode in supportedModes && risk != AgentRiskLevel.BLOCKED

    fun covers(intent: AgentIntent): Boolean = intent in coversIntents
}
