package com.ojoclaro.android.agent.estela

import com.ojoclaro.android.external.ExternalActionEvent

class EstelaAgentRuntime(
    private val planner: EstelaSimplePlanner = EstelaSimplePlanner(),
    private val safetyPolicy: EstelaSafetyPolicy = EstelaSafetyPolicy(),
    private val skillRegistry: EstelaSkillRegistry = EstelaSkillRegistry(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private var memory: EstelaAgentContext = EstelaAgentContext()

    fun contextSnapshot(): EstelaAgentContext = memory

    fun replaceContext(context: EstelaAgentContext) {
        memory = context
    }

    fun reset() {
        memory = EstelaAgentContext()
    }

    fun handle(rawText: String): EstelaRuntimeResult =
        handle(rawText = rawText, context = memory)

    fun handle(rawText: String, context: EstelaAgentContext): EstelaRuntimeResult {
        val states = mutableListOf(EstelaLiveState.Understanding, EstelaLiveState.Planning)
        val textSafety = safetyPolicy.evaluateRawText(rawText)
        if (!textSafety.allowed) {
            states += EstelaLiveState.BlockedForSafety
            val nextContext = context.copy(
                lastUserCommand = rawText,
                pendingPlan = null,
                pendingConfirmation = null,
                lastAgentMessage = textSafety.safeAlternative,
                cancelled = false
            )
            memory = nextContext
            return EstelaRuntimeResult(
                handled = true,
                intent = EstelaIntent.Unknown(rawText),
                plan = null,
                context = nextContext,
                liveStates = states,
                spokenText = textSafety.safeAlternative,
                safetyDecision = textSafety
            )
        }

        val planning = planner.plan(rawText = rawText, context = context)
        val plan = planning.plan
        if (plan == null) {
            return EstelaRuntimeResult(
                handled = false,
                intent = planning.intent,
                plan = null,
                context = context,
                liveStates = states,
                spokenText = null,
                fallbackToLegacy = true
            )
        }

        return when (planning.intent) {
            EstelaIntent.Confirm -> confirmPending(rawText, context, plan, states)
            EstelaIntent.Cancel -> cancelPending(rawText, context, plan, states)
            else -> executeOrQueuePlan(rawText, context, planning.intent, plan, states)
        }
    }

    private fun executeOrQueuePlan(
        rawText: String,
        context: EstelaAgentContext,
        intent: EstelaIntent,
        plan: EstelaPlan,
        states: MutableList<EstelaLiveState>
    ): EstelaRuntimeResult {
        val decision = safetyPolicy.evaluate(rawText = rawText, intent = intent, plan = plan)
        if (!decision.allowed) {
            states += EstelaLiveState.BlockedForSafety
            val nextContext = context.copy(
                lastUserCommand = rawText,
                pendingPlan = null,
                pendingConfirmation = null,
                lastAgentMessage = decision.safeAlternative,
                cancelled = false
            )
            memory = nextContext
            return EstelaRuntimeResult(
                handled = true,
                intent = intent,
                plan = plan,
                context = nextContext,
                liveStates = states,
                spokenText = decision.safeAlternative,
                safetyDecision = decision
            )
        }

        ensureSkillsRegistered(plan)

        if (decision.requiresConfirmation || plan.requiresConfirmation) {
            states += EstelaLiveState.WaitingConfirmation
            val pending = EstelaPendingConfirmation(
                token = confirmationTokenFor(plan),
                plan = plan,
                summary = plan.spokenSummary,
                createdAtMillis = clockMillis()
            )
            val nextContext = contextAfterPlanning(
                context = context,
                rawText = rawText,
                plan = plan,
                lastAgentMessage = plan.spokenSummary
            ).copy(
                pendingPlan = plan,
                pendingConfirmation = pending,
                followUpMode = EstelaFollowUpMode.WAITING_CONFIRMATION,
                cancelled = false
            )
            memory = nextContext
            return EstelaRuntimeResult(
                handled = true,
                intent = intent,
                plan = plan,
                context = nextContext,
                liveStates = states,
                spokenText = plan.spokenSummary,
                pendingConfirmation = pending,
                safetyDecision = decision
            )
        }

        if (plan.steps.any { it is EstelaPlanStep.ReadVisibleScreen }) {
            states += EstelaLiveState.ReadingScreen
        }
        states += EstelaLiveState.Executing
        val externalAction = externalActionForPlan(plan, null)
        states += EstelaLiveState.Completed
        val nextContext = contextAfterExecution(
            context = context,
            rawText = rawText,
            plan = plan,
            lastAgentMessage = plan.visibleSummary
        )
        memory = nextContext
        return EstelaRuntimeResult(
            handled = true,
            intent = intent,
            plan = plan,
            context = nextContext,
            liveStates = states,
            spokenText = plan.visibleSummary,
            externalAction = externalAction,
            safetyDecision = decision
        )
    }

    private fun confirmPending(
        rawText: String,
        context: EstelaAgentContext,
        controlPlan: EstelaPlan,
        states: MutableList<EstelaLiveState>
    ): EstelaRuntimeResult {
        val pending = context.pendingConfirmation
        if (pending == null) {
            states += EstelaLiveState.ErrorRecoverable
            val spoken = "No hay ninguna acción pendiente para confirmar."
            val nextContext = context.copy(
                lastUserCommand = rawText,
                lastAgentMessage = spoken,
                cancelled = false
            )
            memory = nextContext
            return EstelaRuntimeResult(
                handled = true,
                intent = EstelaIntent.Confirm,
                plan = controlPlan,
                context = nextContext,
                liveStates = states,
                spokenText = spoken
            )
        }

        states += EstelaLiveState.Executing
        val externalAction = externalActionForPlan(pending.plan, pending.token)
        val spoken = confirmedSpeechFor(pending.plan)
        states += EstelaLiveState.Completed
        val nextContext = contextAfterExecution(
            context = context,
            rawText = rawText,
            plan = pending.plan,
            lastAgentMessage = spoken
        ).copy(
            pendingPlan = null,
            pendingConfirmation = null,
            cancelled = false
        )
        memory = nextContext
        return EstelaRuntimeResult(
            handled = true,
            intent = EstelaIntent.Confirm,
            plan = pending.plan,
            context = nextContext,
            liveStates = states,
            spokenText = spoken,
            externalAction = externalAction
        )
    }

    private fun cancelPending(
        rawText: String,
        context: EstelaAgentContext,
        controlPlan: EstelaPlan,
        states: MutableList<EstelaLiveState>
    ): EstelaRuntimeResult {
        states += EstelaLiveState.Cancelled
        val spoken = "Cancelado."
        val nextContext = context.copy(
            lastUserCommand = rawText,
            pendingPlan = null,
            pendingConfirmation = null,
            lastAgentMessage = spoken,
            followUpMode = EstelaFollowUpMode.NONE,
            cancelled = true
        )
        memory = nextContext
        return EstelaRuntimeResult(
            handled = true,
            intent = EstelaIntent.Cancel,
            plan = controlPlan,
            context = nextContext,
            liveStates = states,
            spokenText = spoken
        )
    }

    private fun contextAfterPlanning(
        context: EstelaAgentContext,
        rawText: String,
        plan: EstelaPlan,
        lastAgentMessage: String
    ): EstelaAgentContext =
        context.copy(
            lastUserCommand = rawText,
            currentExternalApp = appOpenedBy(plan) ?: context.currentExternalApp,
            lastAgentMessage = lastAgentMessage
        )

    private fun contextAfterExecution(
        context: EstelaAgentContext,
        rawText: String,
        plan: EstelaPlan,
        lastAgentMessage: String
    ): EstelaAgentContext {
        val openedApp = appOpenedBy(plan)
        return context.copy(
            lastUserCommand = rawText,
            currentExternalApp = openedApp ?: context.currentExternalApp,
            lastExternalApp = openedApp ?: context.lastExternalApp,
            lastExternalHandoffAt = if (openedApp != null) clockMillis() else context.lastExternalHandoffAt,
            pendingPlan = null,
            pendingConfirmation = null,
            lastAgentMessage = lastAgentMessage,
            lastSuccessfulAction = plan.userGoal,
            followUpMode = followUpModeAfterCompletedPlan(plan, context),
            cancelled = false
        )
    }

    private fun followUpModeAfterCompletedPlan(
        plan: EstelaPlan,
        context: EstelaAgentContext
    ): EstelaFollowUpMode =
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
