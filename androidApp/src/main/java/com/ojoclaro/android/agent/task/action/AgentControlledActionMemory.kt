package com.ojoclaro.android.agent.task.action

/**
 * Paquete 6E -- Memoria de la propuesta de accion controlada actual.
 *
 * Mantiene UNA sola propuesta viva en RAM. No persiste en disco todavia (eso
 * queda para un paquete futuro). Es efimera y se limpia al cancelar la tarea
 * o al reiniciar la sesion del agente.
 */
class AgentControlledActionMemory(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var current: AgentControlledActionProposal? = null

    /** Propuesta de accion activa, o null si no hay ninguna. */
    fun currentActionProposal(): AgentControlledActionProposal? = current

    /** Guarda (o reemplaza) la propuesta activa. */
    fun saveProposal(proposal: AgentControlledActionProposal): AgentControlledActionProposal {
        val stored = proposal.copy(updatedAt = maxOf(proposal.updatedAt, clock()))
        current = stored
        return stored
    }

    /** Limpia la propuesta activa sin dejar rastro. */
    fun clearProposal() {
        current = null
    }

    /**
     * Cancela la propuesta activa: la marca como CANCELLED, la limpia de la
     * memoria viva y devuelve la copia cancelada. Devuelve null si no habia
     * ninguna propuesta activa.
     */
    fun cancelProposal(reason: String): AgentControlledActionProposal? {
        val proposal = current ?: return null
        val cancelled = proposal.copy(
            status = AgentControlledActionStatus.CANCELLED,
            allowedToExecuteNow = false,
            blockedReason = reason.trim().takeIf { it.isNotBlank() }
                ?: proposal.blockedReason,
            updatedAt = maxOf(proposal.updatedAt, clock())
        )
        current = null
        return cancelled
    }

    /** Texto seguro para voz sobre la propuesta actual. */
    fun proposalSummaryForSpeech(): String =
        current?.spokenText ?: "No hay una propuesta de accion activa."

    fun reset() {
        current = null
    }
}
