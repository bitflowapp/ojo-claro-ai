package com.ojoclaro.android.agent.core.chain

import com.ojoclaro.android.agent.core.AgentPlan
import com.ojoclaro.android.agent.core.AgentPlanStatus

/**
 * Política conservadora para decidir si continuar a un próximo paso es seguro.
 *
 * Reglas:
 *  - Si el plan está bloqueado/cancelado/expirado/completado: NO continuar.
 *  - Si el plan ya está esperando confirmación: NO emitir otra confirmación,
 *    devolver Wait.
 *  - Si el plan TTL venció: Expire.
 *  - Siempre devolver un paso explícito a presentar — nunca encadenar más de
 *    un handoff externo sin confirmación entre medio.
 */
object PlanContinuationPolicy {

    const val DEFAULT_PLAN_TTL_MILLIS: Long = 5 * 60 * 1000L

    fun decide(
        plan: AgentPlan,
        nowMillis: Long,
        ttlMillis: Long = DEFAULT_PLAN_TTL_MILLIS
    ): PlanContinuationDecision {
        val age = nowMillis - plan.updatedAtMillis
        if (age > ttlMillis) {
            return PlanContinuationDecision.Expire(
                spokenText = "Pasó mucho tiempo. Olvidé los pasos siguientes. Decime de nuevo si querés seguir."
            )
        }
        return when (plan.status) {
            AgentPlanStatus.BLOCKED -> PlanContinuationDecision.Stop(
                spokenText = "No puedo seguir con esto por seguridad."
            )
            AgentPlanStatus.CANCELED -> PlanContinuationDecision.Stop(
                spokenText = "Cancelaste esta secuencia."
            )
            AgentPlanStatus.EXPIRED -> PlanContinuationDecision.Expire(
                spokenText = "Esa secuencia ya venció. Decime de nuevo si querés."
            )
            AgentPlanStatus.COMPLETED -> PlanContinuationDecision.Done(
                spokenText = "Esta secuencia ya terminó."
            )
            AgentPlanStatus.AWAITING_FIRST_CONFIRMATION,
            AgentPlanStatus.AWAITING_NEXT_STEP_CONFIRMATION -> PlanContinuationDecision.Wait(
                spokenText = plan.currentStep.confirmationPrompt
                    ?: plan.currentStep.spokenPrompt
            )
            AgentPlanStatus.EXECUTING_STEP,
            AgentPlanStatus.PENDING -> if (plan.isFinalStep) {
                PlanContinuationDecision.PresentFinal(
                    plan = plan,
                    spokenText = plan.currentStep.confirmationPrompt
                        ?: plan.currentStep.spokenPrompt
                )
            } else {
                PlanContinuationDecision.PresentNext(
                    plan = plan,
                    spokenText = plan.currentStep.confirmationPrompt
                        ?: plan.currentStep.spokenPrompt
                )
            }
        }
    }
}

sealed class PlanContinuationDecision {
    data class PresentNext(val plan: AgentPlan, val spokenText: String) : PlanContinuationDecision()
    data class PresentFinal(val plan: AgentPlan, val spokenText: String) : PlanContinuationDecision()
    data class Wait(val spokenText: String) : PlanContinuationDecision()
    data class Stop(val spokenText: String) : PlanContinuationDecision()
    data class Done(val spokenText: String) : PlanContinuationDecision()
    data class Expire(val spokenText: String) : PlanContinuationDecision()
}
