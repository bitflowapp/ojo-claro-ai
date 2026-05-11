package com.ojoclaro.android.agent.core.chain

import com.ojoclaro.android.agent.core.AgentPlan

/**
 * Almacenamiento in-memory de planes pendientes (típicamente uno solo en cada
 * momento). NO persiste a disco: si el proceso muere, el plan se pierde,
 * preferimos eso a ejecutar pasos viejos automáticamente al volver.
 */
class PendingPlanStore {

    private var plan: AgentPlan? = null

    val active: AgentPlan?
        get() = plan

    val hasActivePlan: Boolean
        get() = plan != null

    fun put(plan: AgentPlan) {
        this.plan = plan
    }

    fun clear() {
        plan = null
    }

    fun update(transform: (AgentPlan) -> AgentPlan) {
        val current = plan ?: return
        plan = transform(current)
    }
}
