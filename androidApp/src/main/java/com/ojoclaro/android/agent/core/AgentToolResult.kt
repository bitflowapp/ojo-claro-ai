package com.ojoclaro.android.agent.core

/**
 * Resultado de ejecutar (o intentar ejecutar) un AgentTool.
 *
 * No se confunde con CommandResult ni con AgentOutcome — es la "vista" que el
 * agent-core tiene del paso ejecutado, traducida de las capas Android.
 */
sealed class AgentToolResult {

    /** El tool se preparó/ejecutó con éxito desde el punto de vista del agente. */
    data class Success(
        val toolId: AgentToolId,
        val spokenText: String,
        val handedOffToExternalApp: Boolean
    ) : AgentToolResult()

    /** El tool necesita más datos del usuario. */
    data class NeedsSlot(
        val toolId: AgentToolId,
        val missingSlot: String,
        val spokenPrompt: String
    ) : AgentToolResult()

    /** El tool necesita confirmación explícita. */
    data class NeedsConfirmation(
        val toolId: AgentToolId,
        val spokenText: String,
        val confirmationId: String
    ) : AgentToolResult()

    /**
     * El tool fue bloqueado por seguridad. Distinto de fallido: bloqueado
     * significa "no podemos ejecutarlo bajo ninguna circunstancia ahora".
     */
    data class Blocked(
        val toolId: AgentToolId,
        val spokenText: String,
        val reason: String
    ) : AgentToolResult()

    /** Falla recuperable: app no instalada, error transitorio, etc. */
    data class Failed(
        val toolId: AgentToolId,
        val spokenText: String,
        val recoverable: Boolean
    ) : AgentToolResult()
}
