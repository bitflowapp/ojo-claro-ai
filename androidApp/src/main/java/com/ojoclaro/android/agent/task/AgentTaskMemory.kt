package com.ojoclaro.android.agent.task

class AgentTaskMemory(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var current: AgentTaskPlan? = null

    fun currentPlan(): AgentTaskPlan? = current

    fun replaceCurrentPlan(plan: AgentTaskPlan): AgentTaskPlan {
        current = plan.copy(updatedAt = clock())
        return current!!
    }

    fun startPlan(plan: AgentTaskPlan): AgentTaskPlan {
        current = plan.copy(updatedAt = clock())
        return current!!
    }

    fun advance(ticketId: String): AgentTaskPlan? {
        val plan = current ?: return null
        if (plan.ticketById(ticketId) == null) return plan
        val now = clock()
        val updatedTickets = plan.tickets.map { ticket ->
            when {
                ticket.id == ticketId -> ticket.copy(status = AgentTaskTicketStatus.ACTIVE)
                ticket.status == AgentTaskTicketStatus.ACTIVE -> ticket.copy(status = AgentTaskTicketStatus.PENDING)
                else -> ticket
            }
        }
        return store(plan.copy(tickets = updatedTickets, status = AgentTaskState.ACTIVE, updatedAt = now))
    }

    fun completeTicket(ticketId: String): AgentTaskPlan? {
        val plan = current ?: return null
        val target = plan.ticketById(ticketId) ?: return plan
        val now = clock()
        val completed = target.copy(
            status = AgentTaskTicketStatus.COMPLETED,
            completedAt = now
        )
        val ticketsWithCompleted = plan.tickets.map { ticket ->
            if (ticket.id == ticketId) completed else ticket
        }
        val nextPendingId = ticketsWithCompleted
            .firstOrNull { it.status == AgentTaskTicketStatus.PENDING }
            ?.id
        val updatedTickets = ticketsWithCompleted.map { ticket ->
            if (ticket.id == nextPendingId) ticket.copy(status = AgentTaskTicketStatus.ACTIVE) else ticket
        }
        val nextState = when {
            updatedTickets.all { it.status == AgentTaskTicketStatus.COMPLETED } ->
                AgentTaskState.COMPLETED
            updatedTickets.any { it.status == AgentTaskTicketStatus.WAITING_FOR_USER } ->
                AgentTaskState.WAITING_FOR_USER
            updatedTickets.any { it.status == AgentTaskTicketStatus.REQUIRES_CONFIRMATION } ->
                AgentTaskState.REQUIRES_CONFIRMATION
            else -> AgentTaskState.ACTIVE
        }
        return store(plan.copy(tickets = updatedTickets, status = nextState, updatedAt = now))
    }

    fun markWaitingForUser(ticketId: String, missingData: String): AgentTaskPlan? {
        val plan = current ?: return null
        if (plan.ticketById(ticketId) == null) return plan
        val now = clock()
        val cleanMissing = missingData.trim().takeIf { it.isNotBlank() } ?: return plan
        val updatedTickets = plan.tickets.map { ticket ->
            if (ticket.id == ticketId) {
                ticket.copy(
                    status = AgentTaskTicketStatus.WAITING_FOR_USER,
                    requiredData = ticket.requiredData + cleanMissing
                )
            } else {
                ticket
            }
        }
        return store(
            plan.copy(
                tickets = updatedTickets,
                status = AgentTaskState.WAITING_FOR_USER,
                updatedAt = now
            )
        )
    }

    fun cancelCurrentPlan(reason: String): AgentTaskPlan? {
        val plan = current ?: return null
        val now = clock()
        val safeReason = AgentTaskPlanner.sanitizeOperationalText(reason).ifBlank { "cancelled" }
        val cancelledTickets = plan.tickets.map { ticket ->
            when (ticket.status) {
                AgentTaskTicketStatus.COMPLETED,
                AgentTaskTicketStatus.FAILED,
                AgentTaskTicketStatus.BLOCKED -> ticket
                else -> ticket.copy(status = AgentTaskTicketStatus.CANCELLED)
            }
        }
        val cancelled = plan.copy(
            status = AgentTaskState.CANCELLED,
            tickets = cancelledTickets,
            updatedAt = now,
            cancellationReason = safeReason
        )
        current = null
        return cancelled
    }

    fun reset() {
        current = null
    }

    private fun store(plan: AgentTaskPlan): AgentTaskPlan {
        current = plan
        return plan
    }
}
