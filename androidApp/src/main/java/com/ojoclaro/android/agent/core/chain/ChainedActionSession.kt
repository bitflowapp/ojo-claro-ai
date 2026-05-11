package com.ojoclaro.android.agent.core.chain

import com.ojoclaro.android.agent.core.AgentPlan
import com.ojoclaro.android.agent.core.AgentPlanStatus

/**
 * Sesión que coordina la ejecución supervisada de un plan multi-paso.
 *
 * Reglas:
 *  - Cada paso necesita confirmación antes de ejecutarse — no encadena
 *    handoffs ciegamente.
 *  - Después de un handoff externo, el siguiente paso queda en
 *    AWAITING_NEXT_STEP_CONFIRMATION y solo avanza cuando el usuario vuelve
 *    y dice "confirmar" (o equivalente).
 *  - Cancelar limpia y deja la sesión sin plan.
 *
 * Esta clase NO ejecuta. Solo administra estado y traduce eventos a próximos
 * pasos esperables.
 */
class ChainedActionSession(
    private val store: PendingPlanStore = PendingPlanStore()
) {

    val activePlan: AgentPlan?
        get() = store.active

    fun start(plan: AgentPlan, nowMillis: Long): ChainedActionEvent {
        val initial = plan.withStatus(
            newStatus = AgentPlanStatus.AWAITING_FIRST_CONFIRMATION,
            nowMillis = nowMillis
        )
        store.put(initial)
        return ChainedActionEvent.PresentingStep(
            plan = initial,
            spokenText = initial.currentStep.confirmationPrompt
                ?: initial.currentStep.spokenPrompt
        )
    }

    fun confirmCurrentStep(nowMillis: Long): ChainedActionEvent {
        val current = store.active ?: return ChainedActionEvent.NoActivePlan
        if (current.status != AgentPlanStatus.AWAITING_FIRST_CONFIRMATION &&
            current.status != AgentPlanStatus.AWAITING_NEXT_STEP_CONFIRMATION
        ) {
            return ChainedActionEvent.WaitingForUserResponse(
                spokenText = current.currentStep.confirmationPrompt
                    ?: current.currentStep.spokenPrompt
            )
        }
        val executing = current.withStatus(AgentPlanStatus.EXECUTING_STEP, nowMillis)
        store.put(executing)
        return ChainedActionEvent.ExecutingStep(plan = executing)
    }

    fun onStepExecutedExternally(nowMillis: Long): ChainedActionEvent {
        val current = store.active ?: return ChainedActionEvent.NoActivePlan
        if (current.isFinalStep) {
            val completed = current.withStatus(AgentPlanStatus.COMPLETED, nowMillis)
            store.put(completed)
            return ChainedActionEvent.PlanCompleted(plan = completed)
        }
        val advanced = current.advance(nowMillis)
        store.put(advanced)
        return ChainedActionEvent.AwaitingReturn(
            plan = advanced,
            spokenText = advanced.currentStep.confirmationPrompt
                ?: advanced.currentStep.spokenPrompt
        )
    }

    fun cancel(nowMillis: Long): ChainedActionEvent {
        val current = store.active ?: return ChainedActionEvent.NoActivePlan
        val canceled = current.withStatus(AgentPlanStatus.CANCELED, nowMillis)
        store.put(canceled)
        store.clear()
        return ChainedActionEvent.Cancelled
    }

    fun expireIfNeeded(nowMillis: Long, ttlMillis: Long = PlanContinuationPolicy.DEFAULT_PLAN_TTL_MILLIS): ChainedActionEvent {
        val current = store.active ?: return ChainedActionEvent.NoActivePlan
        if (nowMillis - current.updatedAtMillis < ttlMillis) {
            return ChainedActionEvent.StillFresh
        }
        val expired = current.withStatus(AgentPlanStatus.EXPIRED, nowMillis)
        store.put(expired)
        store.clear()
        return ChainedActionEvent.Expired
    }
}

sealed class ChainedActionEvent {
    object NoActivePlan : ChainedActionEvent()
    object StillFresh : ChainedActionEvent()
    object Cancelled : ChainedActionEvent()
    object Expired : ChainedActionEvent()
    data class PresentingStep(val plan: AgentPlan, val spokenText: String) : ChainedActionEvent()
    data class WaitingForUserResponse(val spokenText: String) : ChainedActionEvent()
    data class ExecutingStep(val plan: AgentPlan) : ChainedActionEvent()
    data class AwaitingReturn(val plan: AgentPlan, val spokenText: String) : ChainedActionEvent()
    data class PlanCompleted(val plan: AgentPlan) : ChainedActionEvent()
}
