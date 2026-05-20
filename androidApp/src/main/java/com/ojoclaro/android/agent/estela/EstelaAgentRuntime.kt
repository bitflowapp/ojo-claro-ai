package com.ojoclaro.android.agent.estela

import com.ojoclaro.android.external.ExternalActionEvent

class EstelaAgentRuntime(
    private val planner: EstelaSimplePlanner = EstelaSimplePlanner(),
    private val safetyPolicy: EstelaSafetyPolicy = EstelaSafetyPolicy(),
    private val skillRegistry: EstelaSkillRegistry = EstelaSkillRegistry(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val sessionMemory = EstelaAgentSessionMemory()
    private val sessionId: String = "estela-session-${clockMillis()}"
    private var turnCounter: Long = 0L

    fun contextSnapshot(): EstelaAgentContext = sessionMemory.snapshot()

    fun replaceContext(context: EstelaAgentContext) {
        sessionMemory.replace(context)
    }

    fun reset() {
        sessionMemory.resetSession()
        turnCounter = 0L
    }

    fun handle(rawText: String): EstelaRuntimeResult =
        handle(rawText = rawText, context = sessionMemory.snapshot())

    fun handle(rawText: String, context: EstelaAgentContext): EstelaRuntimeResult {
        sessionMemory.replace(context)
        val workingContext = sessionMemory.snapshot()
        val trace = newTraceDraft(rawText)
        val states = mutableListOf(EstelaLiveState.Understanding, EstelaLiveState.Planning)

        val rawSafety = safetyPolicy.evaluateRawText(rawText)
        if (!rawSafety.allowed) {
            trace.events += EstelaAgentTraceEvent.SafetyEvaluated
            trace.events += EstelaAgentTraceEvent.BlockedForSafety
            states += EstelaLiveState.BlockedForSafety
            val message = rawSafety.safeAlternative
            val nextContext = workingContext.copy(
                lastUserCommand = rawText,
                pendingPlan = null,
                pendingConfirmation = null,
                lastIntent = EstelaIntent.Unknown(rawText).traceLabel(),
                lastPlanSummary = null,
                lastAgentMessage = message,
                cancelled = false
            )
            sessionMemory.replace(nextContext)
            trace.events += EstelaAgentTraceEvent.ContextUpdated
            val finishedTrace = trace.finish(
                intent = EstelaIntent.Unknown(rawText),
                plan = null,
                safetyDecision = rawSafety,
                liveStates = states,
                externalActionEvent = null,
                fallbackToLegacy = false,
                finalAgentMessage = message
            )
            return EstelaRuntimeResult(
                handled = true,
                intent = EstelaIntent.Unknown(rawText),
                plan = null,
                context = nextContext,
                liveStates = states,
                spokenText = message,
                safetyDecision = rawSafety,
                trace = finishedTrace
            )
        }

        val planning = planner.plan(rawText = rawText, context = workingContext)
        trace.events += EstelaAgentTraceEvent.IntentDetected
        val plan = planning.plan
        if (plan == null) {
            trace.events += EstelaAgentTraceEvent.FallbackToLegacy
            val finishedTrace = trace.finish(
                intent = planning.intent,
                plan = null,
                safetyDecision = rawSafety,
                liveStates = states,
                externalActionEvent = null,
                fallbackToLegacy = true,
                finalAgentMessage = null
            )
            return EstelaRuntimeResult(
                handled = false,
                intent = planning.intent,
                plan = null,
                context = workingContext,
                liveStates = states,
                spokenText = null,
                fallbackToLegacy = true,
                safetyDecision = rawSafety,
                trace = finishedTrace
            )
        }

        trace.events += EstelaAgentTraceEvent.PlanCreated
        val decision = safetyPolicy.evaluate(rawText = rawText, intent = planning.intent, plan = plan)
        trace.events += EstelaAgentTraceEvent.SafetyEvaluated
        if (!decision.allowed) {
            return blockPlannedAction(
                rawText = rawText,
                context = workingContext,
                intent = planning.intent,
                plan = plan,
                decision = decision,
                states = states,
                trace = trace
            )
        }

        return when (planning.intent) {
            EstelaIntent.Confirm -> confirmPending(rawText, workingContext, plan, decision, states, trace)
            EstelaIntent.Cancel -> cancelPending(rawText, workingContext, plan, decision, states, trace)
            else -> executeOrQueuePlan(rawText, planning.intent, plan, decision, states, trace)
        }
    }

    private fun blockPlannedAction(
        rawText: String,
        context: EstelaAgentContext,
        intent: EstelaIntent,
        plan: EstelaPlan,
        decision: EstelaSafetyDecision,
        states: MutableList<EstelaLiveState>,
        trace: EstelaTraceDraft
    ): EstelaRuntimeResult {
        trace.events += EstelaAgentTraceEvent.BlockedForSafety
        states += EstelaLiveState.BlockedForSafety
        val message = decision.safeAlternative
        val nextContext = context.copy(
            lastUserCommand = rawText,
            pendingPlan = null,
            pendingConfirmation = null,
            lastIntent = intent.traceLabel(),
            lastPlanSummary = plan.traceSummary(),
            lastAgentMessage = message,
            cancelled = false
        )
        sessionMemory.replace(nextContext)
        trace.events += EstelaAgentTraceEvent.ContextUpdated
        val finishedTrace = trace.finish(
            intent = intent,
            plan = plan,
            safetyDecision = decision,
            liveStates = states,
            externalActionEvent = null,
            fallbackToLegacy = false,
            finalAgentMessage = message
        )
        return EstelaRuntimeResult(
            handled = true,
            intent = intent,
            plan = plan,
            context = nextContext,
            liveStates = states,
            spokenText = message,
            safetyDecision = decision,
            trace = finishedTrace
        )
    }

    private fun executeOrQueuePlan(
        rawText: String,
        intent: EstelaIntent,
        plan: EstelaPlan,
        decision: EstelaSafetyDecision,
        states: MutableList<EstelaLiveState>,
        trace: EstelaTraceDraft
    ): EstelaRuntimeResult {
        ensureSkillsRegistered(plan)

        if (decision.requiresConfirmation || plan.requiresConfirmation) {
            states += EstelaLiveState.WaitingConfirmation
            trace.events += EstelaAgentTraceEvent.ConfirmationRequested
            val pending = EstelaPendingConfirmation(
                token = confirmationTokenFor(plan),
                plan = plan,
                summary = plan.spokenSummary,
                createdAtMillis = clockMillis()
            )
            val nextContext = sessionMemory.updateAfterPlan(
                rawUserText = rawText,
                intent = intent,
                plan = plan,
                pendingConfirmation = pending,
                agentMessage = plan.spokenSummary
            )
            trace.events += EstelaAgentTraceEvent.ContextUpdated
            val finishedTrace = trace.finish(
                intent = intent,
                plan = plan,
                safetyDecision = decision,
                liveStates = states,
                externalActionEvent = null,
                fallbackToLegacy = false,
                finalAgentMessage = plan.spokenSummary
            )
            return EstelaRuntimeResult(
                handled = true,
                intent = intent,
                plan = plan,
                context = nextContext,
                liveStates = states,
                spokenText = plan.spokenSummary,
                pendingConfirmation = pending,
                safetyDecision = decision,
                trace = finishedTrace
            )
        }

        if (plan.steps.any { it is EstelaPlanStep.ReadVisibleScreen }) {
            states += EstelaLiveState.ReadingScreen
        }
        states += EstelaLiveState.Executing
        val externalAction = externalActionForPlan(plan, null)
        if (externalAction != null) {
            trace.events += EstelaAgentTraceEvent.ActionDispatched
        }
        states += EstelaLiveState.Completed
        val nextContext = sessionMemory.updateAfterAction(
            rawUserText = rawText,
            intent = intent,
            plan = plan,
            agentMessage = plan.visibleSummary,
            nowMillis = clockMillis()
        )
        trace.events += EstelaAgentTraceEvent.ContextUpdated
        trace.events += EstelaAgentTraceEvent.Completed
        val finishedTrace = trace.finish(
            intent = intent,
            plan = plan,
            safetyDecision = decision,
            liveStates = states,
            externalActionEvent = externalAction,
            fallbackToLegacy = false,
            finalAgentMessage = plan.visibleSummary
        )
        return EstelaRuntimeResult(
            handled = true,
            intent = intent,
            plan = plan,
            context = nextContext,
            liveStates = states,
            spokenText = plan.visibleSummary,
            externalAction = externalAction,
            safetyDecision = decision,
            trace = finishedTrace
        )
    }

    private fun confirmPending(
        rawText: String,
        context: EstelaAgentContext,
        controlPlan: EstelaPlan,
        decision: EstelaSafetyDecision,
        states: MutableList<EstelaLiveState>,
        trace: EstelaTraceDraft
    ): EstelaRuntimeResult {
        val pending = context.pendingConfirmation
        if (pending == null) {
            states += EstelaLiveState.ErrorRecoverable
            trace.events += EstelaAgentTraceEvent.Error
            val spoken = "No hay ninguna acción pendiente para confirmar."
            val nextContext = context.copy(
                lastUserCommand = rawText,
                lastIntent = EstelaIntent.Confirm.traceLabel(),
                lastPlanSummary = controlPlan.traceSummary(),
                lastAgentMessage = spoken,
                cancelled = false
            )
            sessionMemory.replace(nextContext)
            trace.events += EstelaAgentTraceEvent.ContextUpdated
            val finishedTrace = trace.finish(
                intent = EstelaIntent.Confirm,
                plan = controlPlan,
                safetyDecision = decision,
                liveStates = states,
                externalActionEvent = null,
                fallbackToLegacy = false,
                finalAgentMessage = spoken
            )
            return EstelaRuntimeResult(
                handled = true,
                intent = EstelaIntent.Confirm,
                plan = controlPlan,
                context = nextContext,
                liveStates = states,
                spokenText = spoken,
                safetyDecision = decision,
                trace = finishedTrace
            )
        }

        states += EstelaLiveState.Executing
        val externalAction = externalActionForPlan(pending.plan, pending.token)
        if (externalAction != null) {
            trace.events += EstelaAgentTraceEvent.ActionDispatched
        }
        val spoken = confirmedSpeechFor(pending.plan)
        states += EstelaLiveState.Completed
        val nextContext = sessionMemory.updateAfterAction(
            rawUserText = rawText,
            intent = EstelaIntent.Confirm,
            plan = pending.plan,
            agentMessage = spoken,
            nowMillis = clockMillis()
        )
        sessionMemory.clearPending()
        trace.events += EstelaAgentTraceEvent.ContextUpdated
        trace.events += EstelaAgentTraceEvent.Completed
        val finishedTrace = trace.finish(
            intent = EstelaIntent.Confirm,
            plan = pending.plan,
            safetyDecision = decision,
            liveStates = states,
            externalActionEvent = externalAction,
            fallbackToLegacy = false,
            finalAgentMessage = spoken
        )
        return EstelaRuntimeResult(
            handled = true,
            intent = EstelaIntent.Confirm,
            plan = pending.plan,
            context = nextContext,
            liveStates = states,
            spokenText = spoken,
            externalAction = externalAction,
            safetyDecision = decision,
            trace = finishedTrace
        )
    }

    private fun cancelPending(
        rawText: String,
        context: EstelaAgentContext,
        controlPlan: EstelaPlan,
        decision: EstelaSafetyDecision,
        states: MutableList<EstelaLiveState>,
        trace: EstelaTraceDraft
    ): EstelaRuntimeResult {
        states += EstelaLiveState.Cancelled
        trace.events += EstelaAgentTraceEvent.Cancelled
        val spoken = "Cancelado."
        sessionMemory.replace(context)
        val nextContext = sessionMemory.cancel(
            rawUserText = rawText,
            agentMessage = spoken
        )
        trace.events += EstelaAgentTraceEvent.ContextUpdated
        val finishedTrace = trace.finish(
            intent = EstelaIntent.Cancel,
            plan = controlPlan,
            safetyDecision = decision,
            liveStates = states,
            externalActionEvent = null,
            fallbackToLegacy = false,
            finalAgentMessage = spoken
        )
        return EstelaRuntimeResult(
            handled = true,
            intent = EstelaIntent.Cancel,
            plan = controlPlan,
            context = nextContext,
            liveStates = states,
            spokenText = spoken,
            safetyDecision = decision,
            trace = finishedTrace
        )
    }

    private fun newTraceDraft(rawText: String): EstelaTraceDraft {
        turnCounter += 1L
        val normalized = EstelaSafetyPolicy.normalize(rawText)
        return EstelaTraceDraft(
            sessionId = sessionId,
            turnId = turnCounter,
            rawUserText = safeTraceText(rawText),
            normalizedUserText = safeTraceText(normalized),
            timestamp = clockMillis()
        )
    }

    private fun externalActionForPlan(
        plan: EstelaPlan,
        confirmationToken: String?
    ): ExternalActionEvent? {
        val token = confirmationToken ?: confirmationTokenFor(plan)
        plan.steps.forEach { step ->
            when (step) {
                is EstelaPlanStep.OpenExternalApp -> {
                    if (step.app.equals("WhatsApp", ignoreCase = true)) {
                        return ExternalActionEvent.ExternalAppHandoff(
                            externalAppName = "WhatsApp",
                            reason = "Abrir WhatsApp pedido por voz",
                            returnHint = "Volvé a Estela para seguir con lo que ves en pantalla.",
                            spokenText = plan.visibleSummary,
                            delegate = ExternalActionEvent.OpenWhatsApp
                        )
                    }
                }

                EstelaPlanStep.ReadVisibleScreen ->
                    return ExternalActionEvent.ReadVisibleScreen

                is EstelaPlanStep.PrepareMessage ->
                    return ExternalActionEvent.ComposeWhatsAppMessage(
                        confirmationId = token,
                        contactName = step.target,
                        messageText = step.message
                    )

                is EstelaPlanStep.OpenDialer ->
                    return ExternalActionEvent.DialPhoneNumber(
                        contactName = step.contactOrNumber,
                        phoneNumber = null
                    )

                else -> Unit
            }
        }
        return null
    }

    private fun confirmationTokenFor(plan: EstelaPlan): String =
        plan.steps.asSequence()
            .mapNotNull { it as? EstelaPlanStep.RequestConfirmation }
            .firstOrNull()
            ?.confirmationToken
            ?: "${plan.id}-confirmation"

    private fun confirmedSpeechFor(plan: EstelaPlan): String =
        when {
            plan.steps.any { it is EstelaPlanStep.PrepareMessage } ->
                "Confirmado. Voy a preparar el mensaje en WhatsApp. No lo envío automáticamente."
            plan.steps.any { it is EstelaPlanStep.OpenDialer } ->
                "Confirmado. Voy a abrir Teléfono con el contacto preparado. No voy a llamar automáticamente."
            plan.steps.any { it is EstelaPlanStep.FindVisibleTarget } ->
                "Confirmado. Busco ese chat en pantalla. Si no es seguro, te voy a guiar."
            else ->
                "Confirmado."
        }

    private fun ensureSkillsRegistered(plan: EstelaPlan) {
        plan.steps.forEach { step ->
            skillRegistry.skillFor(step)
        }
    }
}
