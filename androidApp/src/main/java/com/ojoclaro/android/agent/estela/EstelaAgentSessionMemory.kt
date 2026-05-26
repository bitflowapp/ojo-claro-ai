package com.ojoclaro.android.agent.estela

class EstelaAgentSessionMemory(
    initialContext: EstelaAgentContext = EstelaAgentContext()
) {
    private var context: EstelaAgentContext = initialContext

    fun replace(nextContext: EstelaAgentContext) {
        context = nextContext
    }

    fun updateAfterPlan(
        rawUserText: String,
        intent: EstelaIntent,
        plan: EstelaPlan,
        pendingConfirmation: EstelaPendingConfirmation?,
        agentMessage: String
    ): EstelaAgentContext {
        context = context.copy(
            lastUserCommand = rawUserText,
            currentExternalApp = appOpenedBy(plan) ?: context.currentExternalApp,
            pendingPlan = plan,
            pendingConfirmation = pendingConfirmation,
            lastIntent = intent.traceLabel(),
            lastPlanSummary = plan.traceSummary(),
            lastVisibleTarget = visibleTargetFrom(plan) ?: context.lastVisibleTarget,
            lastAgentMessage = agentMessage,
            followUpMode = EstelaFollowUpMode.WAITING_CONFIRMATION,
            cancelled = false
        )
        return context
    }

    fun updateAfterAction(
        rawUserText: String,
        intent: EstelaIntent,
        plan: EstelaPlan,
        agentMessage: String,
        nowMillis: Long
    ): EstelaAgentContext {
        val openedApp = appOpenedBy(plan)
        context = context.copy(
            lastUserCommand = rawUserText,
            currentExternalApp = openedApp ?: context.currentExternalApp,
            lastExternalApp = openedApp ?: context.lastExternalApp,
            lastExternalHandoffAt = if (openedApp != null) nowMillis else context.lastExternalHandoffAt,
            pendingPlan = null,
            pendingConfirmation = null,
            lastIntent = intent.traceLabel(),
            lastPlanSummary = plan.traceSummary(),
            lastVisibleTarget = visibleTargetFrom(plan) ?: context.lastVisibleTarget,
            lastAgentMessage = agentMessage,
            lastSuccessfulAction = plan.userGoal,
            lastSuccessfulActionAt = nowMillis,
            followUpMode = followUpModeAfterCompletedPlan(plan),
            cancelled = false
        )
        return context
    }

    fun clearPending(): EstelaAgentContext {
        context = context.copy(
            pendingPlan = null,
            pendingConfirmation = null,
            followUpMode = if (context.currentExternalApp.equals("WhatsApp", ignoreCase = true)) {
                EstelaFollowUpMode.EXTERNAL_APP
            } else {
                EstelaFollowUpMode.NONE
            }
        )
        return context
    }

    fun cancel(rawUserText: String, agentMessage: String): EstelaAgentContext {
        context = context.copy(
            lastUserCommand = rawUserText,
            pendingPlan = null,
            pendingConfirmation = null,
            lastIntent = EstelaIntent.Cancel.traceLabel(),
            lastPlanSummary = null,
            lastAgentMessage = agentMessage,
            followUpMode = EstelaFollowUpMode.NONE,
            cancelled = true
        )
        return context
    }

    fun resetSession() {
        context = EstelaAgentContext()
    }

    fun snapshot(): EstelaAgentContext = context

    private fun followUpModeAfterCompletedPlan(plan: EstelaPlan): EstelaFollowUpMode =
        if (
            appOpenedBy(plan).equals("WhatsApp", ignoreCase = true) ||
            context.currentExternalApp.equals("WhatsApp", ignoreCase = true)
        ) {
            EstelaFollowUpMode.EXTERNAL_APP
        } else {
            EstelaFollowUpMode.NONE
        }

    private fun appOpenedBy(plan: EstelaPlan): String? =
        plan.steps.asSequence()
            .mapNotNull { it as? EstelaPlanStep.OpenExternalApp }
            .firstOrNull()
            ?.app

    private fun visibleTargetFrom(plan: EstelaPlan): String? =
        plan.steps.asSequence()
            .mapNotNull { it as? EstelaPlanStep.FindVisibleTarget }
            .firstOrNull()
            ?.target
}
