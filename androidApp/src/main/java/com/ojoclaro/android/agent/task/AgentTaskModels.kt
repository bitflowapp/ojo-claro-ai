package com.ojoclaro.android.agent.task

enum class AgentTaskType {
    REQUEST_RIDE,
    SEND_MESSAGE,
    OPEN_APP,
    FILL_FORM_GUIDANCE,
    CHECK_SCREEN_RISK,
    UNKNOWN
}

enum class AgentTaskState {
    ACTIVE,
    WAITING_FOR_USER,
    WAITING_FOR_SCREEN,
    REQUIRES_CONFIRMATION,
    COMPLETED,
    CANCELLED,
    BLOCKED,
    FAILED
}

enum class AgentTaskTicketStatus {
    PENDING,
    ACTIVE,
    WAITING_FOR_USER,
    WAITING_FOR_SCREEN,
    REQUIRES_CONFIRMATION,
    COMPLETED,
    CANCELLED,
    BLOCKED,
    FAILED
}

enum class AgentTaskRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class AgentTaskKnownApp(
    val displayName: String,
    val packageName: String? = null
)

data class AgentTaskTicket(
    val id: String,
    val title: String,
    val description: String,
    val status: AgentTaskTicketStatus,
    val requiredData: Set<String> = emptySet(),
    val resolvedData: Map<String, String> = emptyMap(),
    val riskLevel: AgentTaskRiskLevel = AgentTaskRiskLevel.LOW,
    val confirmationRequired: Boolean = false,
    val appPackageHint: String? = null,
    val safeForAutomation: Boolean = false,
    val completedAt: Long? = null
) {
    init {
        require(id.isNotBlank()) { "ticket id must not be blank" }
        require(title.isNotBlank()) { "ticket title must not be blank" }
        require(description.isNotBlank()) { "ticket description must not be blank" }
        require(resolvedData.keys.all { it in requiredData }) {
            "resolved data keys must be declared in requiredData"
        }
        if (status == AgentTaskTicketStatus.COMPLETED) {
            require(completedAt != null) { "completed ticket must have completedAt" }
        }
        if (status != AgentTaskTicketStatus.COMPLETED) {
            require(completedAt == null) { "only completed tickets can have completedAt" }
        }
        if (riskLevel >= AgentTaskRiskLevel.HIGH) {
            require(!safeForAutomation) { "high-risk task tickets must not be automation-safe" }
        }
    }

    val missingData: Set<String>
        get() = requiredData - resolvedData.keys
}

data class AgentTaskPlan(
    val id: String,
    val type: AgentTaskType,
    val title: String,
    val userGoal: String,
    val status: AgentTaskState,
    val tickets: List<AgentTaskTicket>,
    val createdAt: Long,
    val updatedAt: Long,
    val riskLevel: AgentTaskRiskLevel,
    val requiresFinalConfirmation: Boolean,
    val safeSummaryForSpeech: String,
    val cancellationReason: String? = null
) {
    init {
        require(id.isNotBlank()) { "plan id must not be blank" }
        require(title.isNotBlank()) { "plan title must not be blank" }
        require(userGoal.isNotBlank()) { "userGoal must not be blank" }
        require(tickets.isNotEmpty()) { "plan must have tickets" }
        require(createdAt >= 0L) { "createdAt must be non-negative" }
        require(updatedAt >= createdAt) { "updatedAt must be >= createdAt" }
        require(safeSummaryForSpeech.isNotBlank()) { "safe summary must not be blank" }
        if (riskLevel >= AgentTaskRiskLevel.HIGH) {
            require(requiresFinalConfirmation) {
                "high-risk task plans must require final confirmation"
            }
        }
    }

    val currentTicket: AgentTaskTicket?
        get() = tickets.firstOrNull { it.status == AgentTaskTicketStatus.WAITING_FOR_USER }
            ?: tickets.firstOrNull { it.status == AgentTaskTicketStatus.REQUIRES_CONFIRMATION }
            ?: tickets.firstOrNull { it.status == AgentTaskTicketStatus.WAITING_FOR_SCREEN }
            ?: tickets.firstOrNull { it.status == AgentTaskTicketStatus.ACTIVE }
            ?: tickets.firstOrNull { it.status == AgentTaskTicketStatus.PENDING }

    val missingData: Set<String>
        get() = tickets.flatMap { it.missingData }.toSet()

    val isWaitingForUser: Boolean
        get() = status == AgentTaskState.WAITING_FOR_USER ||
            tickets.any { it.status == AgentTaskTicketStatus.WAITING_FOR_USER }

    fun ticketById(ticketId: String): AgentTaskTicket? =
        tickets.firstOrNull { it.id == ticketId }

    fun safeStatusSummary(): String {
        val missing = firstMissingDataForSpeech()
        return if (missing != null) {
            "Estoy preparando la tarea $title. Falta confirmar $missing."
        } else {
            val step = currentTicket?.title ?: "sin paso activo"
            "Estoy preparando la tarea $title. Paso actual: $step."
        }
    }

    fun activeStepForUi(): String =
        currentTicket?.title.orEmpty()

    private fun firstMissingDataForSpeech(): String? =
        when {
            missingData.contains(AgentTaskRequiredData.DESTINATION) -> "destino"
            missingData.contains(AgentTaskRequiredData.CURRENT_LOCATION) -> "ubicacion actual"
            else -> missingData.firstOrNull()
        }
}

object AgentTaskRequiredData {
    const val DESTINATION: String = "destination"
    const val CURRENT_LOCATION: String = "current_location"
    const val PAYMENT_METHOD: String = "payment_method"
    const val PRICE_AND_DRIVER: String = "price_and_driver"
    const val FINAL_RIDE_CONFIRMATION: String = "final_ride_confirmation"
}
