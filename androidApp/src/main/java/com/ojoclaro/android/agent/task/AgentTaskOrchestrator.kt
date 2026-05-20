package com.ojoclaro.android.agent.task

import com.ojoclaro.android.agent.command.ParsedCommand
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot

class AgentTaskOrchestrator(
    private val planner: AgentTaskPlanner = AgentTaskPlanner(),
    private val memory: AgentTaskMemory = AgentTaskMemory()
) {

    fun handle(
        rawUserCommand: String,
        parsedCommand: ParsedCommand? = null,
        currentScreenSnapshot: StructuredScreenSnapshot? = null,
        knownApps: List<AgentTaskKnownApp> = emptyList(),
        userPreferences: Map<String, String> = emptyMap(),
        hasPendingBridgeConfirmation: Boolean = false
    ): AgentTaskOrchestratorResult {
        val normalized = AgentTaskPlanner.normalize(rawUserCommand)
        val currentPlan = memory.currentPlan()

        if (isCancelTaskCommand(normalized) && currentPlan != null) {
            val cancelled = memory.cancelCurrentPlan("user_cancelled")
            return AgentTaskOrchestratorResult.Handled(
                kind = AgentTaskOrchestratorResultKind.CANCELLED,
                spokenText = "Tarea ${cancelled?.title ?: currentPlan.title} cancelada.",
                plan = null
            )
        }

        if (isStatusQuery(normalized) && currentPlan != null) {
            return AgentTaskOrchestratorResult.Handled(
                kind = AgentTaskOrchestratorResultKind.STATUS,
                spokenText = currentPlan.safeStatusSummary(),
                plan = currentPlan
            )
        }

        val candidatePlan = planner.plan(
            rawUserCommand = rawUserCommand,
            parsedCommand = parsedCommand,
            currentScreenSnapshot = currentScreenSnapshot,
            knownApps = knownApps,
            userPreferences = userPreferences
        )
        if (candidatePlan.type != AgentTaskType.REQUEST_RIDE) {
            return AgentTaskOrchestratorResult.NotHandled
        }

        if (hasPendingBridgeConfirmation) {
            return AgentTaskOrchestratorResult.Handled(
                kind = AgentTaskOrchestratorResultKind.BLOCKED_BY_PENDING_CONFIRMATION,
                spokenText = "Hay una accion pendiente que requiere confirmacion. Deci confirmar o cancelar antes de iniciar otra tarea.",
                plan = null
            )
        }

        if (currentPlan != null) {
            return AgentTaskOrchestratorResult.Handled(
                kind = AgentTaskOrchestratorResultKind.STATUS,
                spokenText = currentPlan.safeStatusSummary(),
                plan = currentPlan
            )
        }

        val started = memory.startPlan(candidatePlan)
        return AgentTaskOrchestratorResult.Handled(
            kind = AgentTaskOrchestratorResultKind.PLAN_STARTED,
            spokenText = creationSpeech(started),
            plan = started
        )
    }

    fun currentPlan(): AgentTaskPlan? = memory.currentPlan()

    fun reset() {
        memory.reset()
    }

    private fun creationSpeech(plan: AgentTaskPlan): String =
        if (plan.missingData.contains(AgentTaskRequiredData.DESTINATION)) {
            "${plan.safeSummaryForSpeech} A donde queres ir?"
        } else {
            plan.safeSummaryForSpeech
        }

    companion object {
        fun isCancelTaskCommand(normalized: String): Boolean =
            normalized == "cancelar tarea" ||
                normalized == "cancela tarea" ||
                normalized == "cancelame la tarea" ||
                normalized == "cancela eso" ||
                normalized == "cancelar eso" ||
                normalized == "olvidalo"

        fun isStatusQuery(normalized: String): Boolean =
            normalized == "que estas haciendo" ||
                normalized == "en que paso estas" ||
                normalized == "que falta" ||
                normalized == "que falta hacer" ||
                normalized == "estado de la tarea"
    }
}

sealed class AgentTaskOrchestratorResult {
    data object NotHandled : AgentTaskOrchestratorResult()

    data class Handled(
        val kind: AgentTaskOrchestratorResultKind,
        val spokenText: String,
        val plan: AgentTaskPlan?
    ) : AgentTaskOrchestratorResult() {
        val activeTaskTitle: String
            get() = plan?.title.orEmpty()

        val activeTaskStep: String
            get() = plan?.activeStepForUi().orEmpty()

        val activeTaskSummary: String
            get() = plan?.safeStatusSummary().orEmpty()

        val waitingForUser: Boolean
            get() = plan?.isWaitingForUser == true

        val pendingDebugLabel: String
            get() = when {
                plan == null -> ""
                waitingForUser -> "TASK_${plan.type.name}_WAITING_USER"
                else -> "TASK_${plan.type.name}"
            }
    }
}

enum class AgentTaskOrchestratorResultKind {
    PLAN_STARTED,
    STATUS,
    CANCELLED,
    BLOCKED_BY_PENDING_CONFIRMATION
}
