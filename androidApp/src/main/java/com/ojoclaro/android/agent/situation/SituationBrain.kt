package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentRiskLevel

/**
 * Resultado de un turno del Situation Brain: el contexto actualizado más la
 * decisión pura de qué debería pasar después.
 */
data class SituationBrainResult(
    val updatedContext: SituationContext,
    val decision: SituationDecision
)

/**
 * Cerebro de decisión puro del Situation Brain.
 *
 * Fase 2: NO está cableado al flujo real. NO ejecuta acciones, NO llama a
 * Android, NO habla por TTS, NO abre apps, NO lee pantalla, NO llama use cases.
 * Solo recibe un [SituationContext] y decide. Es determinista y testeable.
 *
 * Toda transición de estado pasa por [SituationStateMachine]. Si una transición
 * resultara ilegal, el Brain devuelve un [SituationDecision.Reject] y deja el
 * contexto en un estado seguro — nunca rompe silenciosamente ni lanza.
 */
class SituationBrain(
    private val intentClassifier: IntentClassifier = IntentClassifier()
) {

    fun process(context: SituationContext): SituationBrainResult {
        val now = context.timestamp
        val rawCommand = context.rawCommand
        if (rawCommand.isBlank()) {
            return SituationBrainResult(context, SituationDecision.Ignore)
        }
        val normalized = normalizeSituationCommand(
            context.normalizedCommand.ifBlank { rawCommand }
        )

        // 1. Limpieza de objetivo expirado (antes de clasificar: el clasificador
        //    usa activeGoal como desempate).
        val goalCleaned = if (context.activeGoal?.isExpired(now) == true) {
            context.withGoal(null)
        } else {
            context
        }

        // 2. Clasificación de intención.
        val intent = intentClassifier.classify(
            rawCommand = rawCommand,
            normalizedCommand = normalized,
            activeGoal = goalCleaned.activeGoal
        )

        // 3. Registrar el turno del usuario YA con la intención clasificada
        //    (nunca con UNKNOWN). recentTurns se mantiene en máximo 5.
        val withTurn = goalCleaned
            .copy(situationIntent = intent)
            .withUserTurnFromCurrentCommand()

        // 4. Corte duro: prioridad absoluta, desde cualquier estado.
        if (isHardCancel(normalized)) {
            val cleared = withTurn.clearedForCancellation(now)
            return SituationBrainResult(
                cleared,
                SituationDecision.Cancel(reason = "Cancelado", hard = true)
            )
        }

        // 5. Entrar a UNDERSTANDING (la intención y el turno ya están seteados).
        val understanding = toUnderstanding(withTurn)

        // 6. Una acción pendiente tiene prioridad: un sí/cancelá la resuelve
        //    directamente, sin importar qué clasificó el clasificador. Cualquier
        //    otro comando re-pide la confirmación en vez de arrancar algo nuevo:
        //    la conversación queda "trabada" hasta que el usuario resuelva.
        goalCleaned.pendingAction?.let { pending ->
            if (isConfirmation(normalized)) {
                return executePending(understanding, pending)
            }
            if (isSoftCancel(normalized)) {
                return cancelResult(understanding, now)
            }
            return reaskConfirmation(understanding, pending)
        }

        // 7. Continuación de objetivo activo (Fase 8): si la intención coincide
        //    con el objetivo activo y todavía no venció, se completa el slot
        //    faltante o se pide confirmación según corresponda.
        val goal = goalCleaned.activeGoal
        if (goal != null && goal.intent == intent && intent in CONTINUABLE_INTENTS) {
            return handleActiveGoalContinuation(understanding, goal, normalized, now)
        }

        // 8. Despacho por intención.
        return when (intent) {
            SituationIntent.EMERGENCY_STOP ->
                // Defensivo: el corte duro ya se atrapó en el paso 1.
                cancelResult(understanding, now)

            SituationIntent.UNSAFE_REQUEST ->
                rejectResult(
                    understanding,
                    "Esa acción parece sensible o insegura. No la puedo hacer.",
                    AgentRiskLevel.BLOCKED
                )

            SituationIntent.CONTROL ->
                handleControl(understanding, normalized, now)

            SituationIntent.HELP_ME_WORK ->
                handleHelpMeWork(understanding)

            SituationIntent.READ_SCREEN,
            SituationIntent.SUMMARIZE_SCREEN,
            SituationIntent.EXPLAIN_WHAT_I_SEE ->
                handleReadScreen(understanding, intent)

            SituationIntent.GUIDE_USER ->
                handleGuide(understanding, intent)

            SituationIntent.WRITE_MESSAGE,
            SituationIntent.CALL_CONTACT,
            SituationIntent.OPEN_APP,
            SituationIntent.MANAGE_MEMORY ->
                handleActionIntent(understanding, intent, normalized)

            SituationIntent.UNKNOWN ->
                handleUnknown(understanding)
        }
    }

    // --- Handlers por intención ----------------------------------------------

    private fun handleControl(
        ctx: SituationContext,
        normalized: String,
        now: Long
    ): SituationBrainResult {
        if (isSoftCancel(normalized)) {
            return cancelResult(ctx, now)
        }
        if (isConfirmation(normalized)) {
            // El caso con acción pendiente ya se atrapó antes; acá no hay nada
            // que confirmar.
            return speakResult(
                ctx,
                "No hay ninguna acción pendiente para confirmar. Decime qué necesitás."
            )
        }
        return SituationBrainResult(ctx, SituationDecision.Ignore)
    }

    private fun handleHelpMeWork(ctx: SituationContext): SituationBrainResult {
        val moved = safeTransition(ctx, SituationState.SPEAKING)
            ?: return rejectResult(ctx, "No puedo activar el modo compañero ahora.", ctx.riskLevel)
        return SituationBrainResult(
            moved.copy(companionModeActive = true),
            SituationDecision.Speak(
                message = COMPANION_MODE_GREETING,
                nextState = SituationState.SPEAKING
            )
        )
    }

    private fun handleReadScreen(
        ctx: SituationContext,
        intent: SituationIntent
    ): SituationBrainResult {
        val moved = safeTransition(ctx, SituationState.READING_SCREEN)
            ?: return rejectResult(ctx, "No puedo leer la pantalla ahora.", ctx.riskLevel)
        return SituationBrainResult(
            moved,
            SituationDecision.ExecuteIntent(
                intent = intent,
                reason = "Lectura/resumen de pantalla solicitado por el usuario.",
                nextState = SituationState.READING_SCREEN
            )
        )
    }

    private fun handleGuide(
        ctx: SituationContext,
        intent: SituationIntent
    ): SituationBrainResult {
        val moved = safeTransition(ctx, SituationState.PLANNING)
            ?: return rejectResult(ctx, "No puedo guiarte en este momento.", ctx.riskLevel)
        return SituationBrainResult(
            moved,
            SituationDecision.ExecuteIntent(
                intent = intent,
                reason = "El usuario pidió una guía paso a paso.",
                nextState = SituationState.PLANNING
            )
        )
    }

    private fun handleActionIntent(
        ctx: SituationContext,
        intent: SituationIntent,
        normalized: String
    ): SituationBrainResult {
        // Fase 8: para WRITE_MESSAGE / CALL_CONTACT / OPEN_APP el comando se
        // traduce a un ActiveGoal con slots. Si el goal está completo se pide
        // confirmación (o se ejecuta directo si no la requiere); si falta algún
        // slot, se pregunta y el goal queda vivo en updatedContext.
        if (intent in GOAL_BUILDABLE_INTENTS) {
            val goal = SituationGoalBuilder.goalFromCommand(ctx.rawCommand, intent, ctx.timestamp)
            if (goal != null) {
                return dispatchGoal(ctx, goal, normalized, ctx.timestamp)
            }
            // Si goalFromCommand devolvió null (no soportado todavía), cae al
            // camino legacy.
        }

        // Camino legacy: MANAGE_MEMORY (y cualquier otra acción no migrada).
        if (requiresConfirmation(intent, normalized)) {
            val pending = PendingAction(
                label = labelFor(intent),
                intentName = intent.name,
                riskLevel = riskFor(intent),
                confirmationPrompt = confirmationPromptFor(intent),
                expiresAt = ctx.timestamp + PENDING_ACTION_TTL_MILLIS
            )
            val moved = safeTransition(ctx, SituationState.WAITING_CONFIRMATION)
                ?: return rejectResult(ctx, "No puedo preparar esa acción ahora.", ctx.riskLevel)
            return SituationBrainResult(
                moved.withPendingAction(pending).copy(needsConfirmation = true),
                SituationDecision.AskConfirmation(
                    prompt = pending.confirmationPrompt,
                    pendingAction = pending,
                    nextState = SituationState.WAITING_CONFIRMATION
                )
            )
        }
        // No requiere confirmación: se planifica directo.
        val moved = safeTransition(ctx, SituationState.PLANNING)
            ?: return rejectResult(ctx, "No puedo continuar con esa acción.", ctx.riskLevel)
        return SituationBrainResult(
            moved,
            SituationDecision.ExecuteIntent(
                intent = intent,
                reason = "Acción directa sin confirmación.",
                nextState = SituationState.PLANNING
            )
        )
    }

    // --- Fase 8: continuación de ActiveGoal multi-turno ----------------------

    private fun handleActiveGoalContinuation(
        ctx: SituationContext,
        goal: ActiveGoal,
        normalized: String,
        now: Long
    ): SituationBrainResult {
        if (isSoftCancel(normalized)) {
            return cancelResult(ctx, now)
        }
        if (isConfirmation(normalized) && !goal.isComplete()) {
            // "sí" en medio de slot-filling no completa nada: se re-pide el slot.
            return askForGoalSlot(ctx.copy(activeGoal = goal), goal)
        }
        val continued = SituationGoalBuilder.continueGoal(goal, ctx.rawCommand, now)
        return dispatchGoal(ctx, continued, normalized, now)
    }

    /**
     * Dispara la decisión que corresponde a un goal: si está completo, pide
     * confirmación (o ejecuta directo); si todavía le faltan slots, guarda el
     * goal en el contexto y pregunta el siguiente slot.
     */
    private fun dispatchGoal(
        ctx: SituationContext,
        goal: ActiveGoal,
        normalized: String,
        now: Long
    ): SituationBrainResult = if (goal.isComplete()) {
        if (requiresConfirmationForGoal(goal, normalized)) {
            buildAskConfirmationFromCompletedGoal(ctx, goal, now)
        } else {
            executeGoalDirect(ctx, goal)
        }
    } else {
        askForGoalSlot(ctx.copy(activeGoal = goal), goal)
    }

    private fun askForGoalSlot(
        ctx: SituationContext,
        goal: ActiveGoal
    ): SituationBrainResult {
        val slot = preferredMissingSlot(goal)
        val prompt = slotPromptFor(goal, slot)
        val moved = safeTransition(ctx, SituationState.SPEAKING)
            ?: return rejectResult(ctx, prompt, ctx.riskLevel)
        return SituationBrainResult(
            moved,
            SituationDecision.Speak(prompt, SituationState.SPEAKING)
        )
    }

    private fun buildAskConfirmationFromCompletedGoal(
        ctx: SituationContext,
        goal: ActiveGoal,
        now: Long
    ): SituationBrainResult {
        val pending = goalToPendingAction(goal, now)
        // El goal se "consume" en la pendingAction: ya no vive como activeGoal.
        val cleared = ctx.copy(activeGoal = null)
        val moved = safeTransition(cleared, SituationState.WAITING_CONFIRMATION)
            ?: return rejectResult(
                cleared,
                "No puedo preparar esa acción ahora.",
                cleared.riskLevel
            )
        return SituationBrainResult(
            moved.withPendingAction(pending).copy(needsConfirmation = true),
            SituationDecision.AskConfirmation(
                prompt = pending.confirmationPrompt,
                pendingAction = pending,
                nextState = SituationState.WAITING_CONFIRMATION
            )
        )
    }

    private fun executeGoalDirect(
        ctx: SituationContext,
        goal: ActiveGoal
    ): SituationBrainResult {
        val cleared = ctx.copy(activeGoal = null)
        val moved = safeTransition(cleared, SituationState.PLANNING)
            ?: return rejectResult(cleared, "No puedo continuar con esa acción.", cleared.riskLevel)
        return SituationBrainResult(
            moved,
            SituationDecision.ExecuteIntent(
                intent = goal.intent,
                reason = "Acción directa sin confirmación.",
                nextState = SituationState.PLANNING
            )
        )
    }

    private fun requiresConfirmationForGoal(goal: ActiveGoal, normalized: String): Boolean =
        when (goal.intent) {
            SituationIntent.WRITE_MESSAGE,
            SituationIntent.CALL_CONTACT -> true
            SituationIntent.OPEN_APP -> {
                val target = goal.slotsFilled["target"].orEmpty().lowercase()
                // Si el target es la propia app, no se exige confirmación.
                !target.contains("ojo claro") &&
                    !normalized.contains("ojo claro")
            }
            else -> true
        }

    private fun confirmationPromptForGoal(goal: ActiveGoal): String = when (goal.intent) {
        SituationIntent.OPEN_APP -> {
            val target = goal.slotsFilled["target"].orEmpty()
            if (target.isNotBlank()) "¿Querés que abra $target?"
            else "Voy a abrir esa app. Para continuar, decí: confirmar."
        }
        SituationIntent.CALL_CONTACT -> {
            val contact = goal.slotsFilled["contact"].orEmpty()
            if (contact.isNotBlank()) "¿Querés que abra el marcador para llamar a $contact?"
            else "Voy a abrir el marcador. Para continuar, decí: confirmar."
        }
        SituationIntent.WRITE_MESSAGE -> {
            val contact = goal.slotsFilled["contact"].orEmpty()
            val message = goal.slotsFilled["message"].orEmpty()
            when {
                contact.isNotBlank() && message.isNotBlank() ->
                    "Preparo un mensaje para $contact: '$message'. No lo envío solo. ¿Confirmás?"
                contact.isNotBlank() ->
                    "Voy a preparar un mensaje para $contact. No lo envío solo. ¿Confirmás?"
                else ->
                    "Voy a preparar el mensaje, no lo envío solo. Para continuar, decí: confirmar."
            }
        }
        else -> "Para continuar, decí: confirmar."
    }

    private fun reconstructCommandForGoal(goal: ActiveGoal): String = when (goal.intent) {
        SituationIntent.OPEN_APP -> {
            val target = goal.slotsFilled["target"].orEmpty()
            if (target.isNotBlank()) "abrí $target" else ""
        }
        SituationIntent.CALL_CONTACT -> {
            val contact = goal.slotsFilled["contact"].orEmpty()
            if (contact.isNotBlank()) "llamá a $contact" else ""
        }
        SituationIntent.WRITE_MESSAGE -> {
            val contact = goal.slotsFilled["contact"].orEmpty()
            val message = goal.slotsFilled["message"].orEmpty()
            when {
                contact.isNotBlank() && message.isNotBlank() ->
                    "avisale a $contact que $message"
                contact.isNotBlank() -> "avisale a $contact"
                else -> ""
            }
        }
        else -> ""
    }

    private fun preferredMissingSlot(goal: ActiveGoal): String = when (goal.intent) {
        SituationIntent.WRITE_MESSAGE -> when {
            "contact" in goal.slotsMissing -> "contact"
            "message" in goal.slotsMissing -> "message"
            else -> goal.slotsMissing.firstOrNull().orEmpty()
        }
        else -> goal.slotsMissing.firstOrNull().orEmpty()
    }

    private fun slotPromptFor(goal: ActiveGoal, slot: String): String = when (goal.intent) {
        SituationIntent.WRITE_MESSAGE -> when (slot) {
            "contact" -> "¿A quién querés escribirle?"
            "message" -> {
                val contact = goal.slotsFilled["contact"].orEmpty()
                if (contact.isNotBlank()) "¿Qué querés decirle a $contact?"
                else "¿Qué mensaje querés mandar?"
            }
            else -> "Necesito un poco más de contexto. ¿Podés repetir?"
        }
        SituationIntent.CALL_CONTACT -> when (slot) {
            "contact" -> "¿A quién querés llamar?"
            else -> "Necesito un poco más de contexto. ¿Podés repetir?"
        }
        SituationIntent.OPEN_APP -> when (slot) {
            "target" -> "¿Qué app querés abrir?"
            else -> "Necesito un poco más de contexto. ¿Podés repetir?"
        }
        else -> "Necesito un poco más de contexto. ¿Podés repetir?"
    }

    /**
     * Convierte un goal completo en una PendingAction lista para ejecutar tras
     * confirmación. El originalCommand se RECONSTRUYE desde los slots (no se
     * usa el rawCommand del último turno, que puede ser solo "sí").
     */
    private fun goalToPendingAction(goal: ActiveGoal, now: Long): PendingAction {
        val target = when (goal.intent) {
            SituationIntent.OPEN_APP -> goal.slotsFilled["target"].orEmpty()
            SituationIntent.CALL_CONTACT,
            SituationIntent.WRITE_MESSAGE -> goal.slotsFilled["contact"].orEmpty()
            else -> ""
        }
        val reconstructed = reconstructCommandForGoal(goal)
        val payload = buildMap {
            put("intent", goal.intent.name)
            if (reconstructed.isNotBlank()) {
                put("originalCommand", reconstructed.take(PendingAction.MAX_PAYLOAD_VALUE_CHARS))
            }
            if (target.isNotBlank()) {
                put("target", target.take(PendingAction.MAX_PAYLOAD_VALUE_CHARS))
            }
            goal.slotsFilled["contact"]?.takeIf { it.isNotBlank() }?.let {
                put("contact", it.take(PendingAction.MAX_PAYLOAD_VALUE_CHARS))
            }
            goal.slotsFilled["message"]?.takeIf { it.isNotBlank() }?.let {
                put("message", it.take(PendingAction.MAX_PAYLOAD_VALUE_CHARS))
            }
        }
        return PendingAction(
            label = labelFor(goal.intent),
            intentName = goal.intent.name,
            riskLevel = riskFor(goal.intent),
            confirmationPrompt = confirmationPromptForGoal(goal),
            expiresAt = now + PENDING_ACTION_TTL_MILLIS,
            originalCommand = reconstructed.take(PendingAction.MAX_ORIGINAL_COMMAND_CHARS),
            target = target.take(PendingAction.MAX_TARGET_CHARS),
            payload = payload
        )
    }

    private fun handleUnknown(ctx: SituationContext): SituationBrainResult {
        // Si hay un goal activo (caso defensivo: en la práctica el clasificador
        // devuelve goal.intent y este branch no se alcanza), se pregunta el
        // siguiente slot faltante en vez de tirar fallback genérico.
        val goal = ctx.activeGoal
        if (goal != null && goal.hasMissingSlots()) {
            return askForGoalSlot(ctx, goal)
        }
        val message = if (ctx.companionModeActive) {
            // Modo compañero: fallback más corto.
            "No entendí. ¿Leer pantalla, preparar un mensaje o ayuda?"
        } else {
            "No entendí bien. Decime si querés leer pantalla, preparar un mensaje o pedir ayuda."
        }
        return speakResult(ctx, message)
    }

    private fun executePending(
        ctx: SituationContext,
        pending: PendingAction
    ): SituationBrainResult {
        // Si el intentName de la acción pendiente no se reconoce, NO se ejecuta
        // nada: se rechaza de forma segura.
        val intent = situationIntentFromPendingAction(pending)
            ?: return rejectResult(
                ctx,
                "No reconozco esa acción pendiente. Decime de nuevo qué necesitás.",
                ctx.riskLevel
            )
        val moved = safeTransition(ctx, SituationState.EXECUTING_GUIDED_ACTION)
            ?: return rejectResult(
                ctx,
                "No puedo continuar con la acción pendiente.",
                ctx.riskLevel
            )
        return SituationBrainResult(
            // updatedContext queda SIN pendingAction (memoria limpia); la
            // decisión SÍ la transporta para que la capa de aplicación pueda
            // ejecutarla.
            moved.withPendingAction(null).copy(needsConfirmation = false),
            SituationDecision.ExecuteIntent(
                intent = intent,
                reason = "Confirmación de la acción pendiente: ${pending.label}.",
                nextState = SituationState.EXECUTING_GUIDED_ACTION,
                pendingAction = pending
            )
        )
    }

    /**
     * Hay una acción pendiente pero el comando no la confirma ni la cancela:
     * se re-pide la confirmación, conservando estado y pendingAction.
     */
    private fun reaskConfirmation(
        ctx: SituationContext,
        pending: PendingAction
    ): SituationBrainResult =
        SituationBrainResult(
            ctx,
            SituationDecision.Speak(
                message = pending.confirmationPrompt,
                nextState = ctx.situationState
            )
        )

    // --- Helpers de resultado ------------------------------------------------

    private fun cancelResult(ctx: SituationContext, now: Long): SituationBrainResult {
        val cleared = ctx.clearedForCancellation(now)
        return SituationBrainResult(
            cleared,
            SituationDecision.Cancel(reason = "Cancelado", hard = true)
        )
    }

    private fun speakResult(ctx: SituationContext, message: String): SituationBrainResult {
        val moved = safeTransition(ctx, SituationState.SPEAKING)
            ?: return rejectResult(ctx, message, ctx.riskLevel)
        return SituationBrainResult(
            moved,
            SituationDecision.Speak(message, SituationState.SPEAKING)
        )
    }

    /**
     * Rechazo seguro. Lleva el contexto a ERROR_RECOVERY si la transición es
     * legal; si no (caso CANCELLED, que solo puede ir a IDLE), cae a IDLE. Nunca
     * lanza.
     */
    private fun rejectResult(
        ctx: SituationContext,
        reason: String,
        risk: AgentRiskLevel
    ): SituationBrainResult {
        val moved = if (
            SituationStateMachine.canTransition(ctx.situationState, SituationState.ERROR_RECOVERY)
        ) {
            SituationStateMachine.transition(ctx, SituationState.ERROR_RECOVERY)
        } else {
            ctx.withState(SituationState.IDLE)
        }
        return SituationBrainResult(
            moved.copy(riskLevel = risk),
            SituationDecision.Reject(reason, risk, moved.situationState)
        )
    }

    // --- Helpers de estado / transición --------------------------------------

    /**
     * Lleva el contexto a UNDERSTANDING por un camino legal. Desde IDLE pasa por
     * LISTENING. Desde SPEAKING (caso típico tras pedir un slot al usuario)
     * pasa por LISTENING. Si ya está en un estado de trabajo (WAITING_CONFIRMATION
     * con pendingAction, etc.) lo deja como está: esos casos los maneja cada handler.
     */
    private fun toUnderstanding(ctx: SituationContext): SituationContext =
        when (ctx.situationState) {
            SituationState.UNDERSTANDING -> ctx
            SituationState.IDLE -> {
                val listening = SituationStateMachine.transition(ctx, SituationState.LISTENING)
                SituationStateMachine.transition(listening, SituationState.UNDERSTANDING)
            }
            SituationState.LISTENING ->
                SituationStateMachine.transition(ctx, SituationState.UNDERSTANDING)
            SituationState.SPEAKING -> {
                // SPEAKING -> LISTENING -> UNDERSTANDING (camino legal).
                val listening = SituationStateMachine.transition(ctx, SituationState.LISTENING)
                SituationStateMachine.transition(listening, SituationState.UNDERSTANDING)
            }
            else -> ctx
        }

    private fun safeTransition(
        ctx: SituationContext,
        target: SituationState
    ): SituationContext? =
        if (SituationStateMachine.canTransition(ctx.situationState, target)) {
            SituationStateMachine.transition(ctx, target)
        } else {
            null
        }

    // --- Helpers de clasificación de control ---------------------------------

    private fun isHardCancel(command: String): Boolean =
        normalizeSituationCommand(command) in SituationVocabulary.EMERGENCY_STOP

    private fun isConfirmation(command: String): Boolean =
        normalizeSituationCommand(command) in SituationVocabulary.CONFIRMATION

    private fun isSoftCancel(command: String): Boolean =
        normalizeSituationCommand(command) in SituationVocabulary.SOFT_CANCEL

    private fun requiresConfirmation(intent: SituationIntent, normalized: String): Boolean =
        when (intent) {
            SituationIntent.WRITE_MESSAGE,
            SituationIntent.CALL_CONTACT -> true
            SituationIntent.MANAGE_MEMORY ->
                DESTRUCTIVE_MEMORY_TOKENS.any { normalized.contains(it) }
            SituationIntent.OPEN_APP ->
                !normalized.contains("ojo claro")
            else -> false
        }

    private fun riskFor(intent: SituationIntent): AgentRiskLevel =
        when (intent) {
            SituationIntent.WRITE_MESSAGE,
            SituationIntent.CALL_CONTACT -> AgentRiskLevel.MEDIUM
            SituationIntent.MANAGE_MEMORY -> AgentRiskLevel.HIGH
            SituationIntent.OPEN_APP -> AgentRiskLevel.LOW
            else -> AgentRiskLevel.NONE
        }

    private fun labelFor(intent: SituationIntent): String =
        when (intent) {
            SituationIntent.WRITE_MESSAGE -> "preparar un mensaje"
            SituationIntent.CALL_CONTACT -> "abrir el marcador"
            SituationIntent.OPEN_APP -> "abrir una app"
            SituationIntent.MANAGE_MEMORY -> "actualizar la memoria"
            else -> "continuar"
        }

    private fun confirmationPromptFor(intent: SituationIntent, target: String = ""): String =
        when (intent) {
            SituationIntent.OPEN_APP ->
                if (target.isNotBlank()) {
                    "¿Querés que abra $target?"
                } else {
                    "Voy a abrir esa app. Para continuar, decí: confirmar."
                }
            SituationIntent.CALL_CONTACT ->
                if (target.isNotBlank()) {
                    "¿Querés que abra el marcador para llamar a $target?"
                } else {
                    "Voy a abrir el marcador, la llamada la disparás vos. Para continuar, decí: confirmar."
                }
            SituationIntent.WRITE_MESSAGE ->
                "Voy a preparar el mensaje, no lo envío solo. Para continuar, decí: confirmar."
            SituationIntent.MANAGE_MEMORY ->
                "Voy a actualizar tu memoria. Para continuar, decí: confirmar."
            else ->
                "Para continuar, decí: confirmar."
        }

    private companion object {
        const val PENDING_ACTION_TTL_MILLIS = 120_000L

        const val COMPANION_MODE_GREETING =
            "Listo. Te acompaño. Puedo leer una pantalla, preparar un mensaje o guiarte paso a paso."

        /** Intenciones que pueden continuar un objetivo de tarea vivo. */
        val CONTINUABLE_INTENTS: Set<SituationIntent> = setOf(
            SituationIntent.WRITE_MESSAGE,
            SituationIntent.CALL_CONTACT,
            SituationIntent.OPEN_APP,
            SituationIntent.GUIDE_USER,
            SituationIntent.MANAGE_MEMORY
        )

        /**
         * Intenciones para las que [handleActionIntent] construye un ActiveGoal
         * con slot-filling (Fase 8). MANAGE_MEMORY queda fuera y sigue el
         * camino legacy con PendingAction mínima.
         */
        val GOAL_BUILDABLE_INTENTS: Set<SituationIntent> = setOf(
            SituationIntent.WRITE_MESSAGE,
            SituationIntent.CALL_CONTACT,
            SituationIntent.OPEN_APP
        )

        val DESTRUCTIVE_MEMORY_TOKENS: List<String> = listOf(
            "olvida",
            "borrar",
            "eliminar"
        )
    }
}

/**
 * Mapea el intentName (String) de una [PendingAction] a su [SituationIntent].
 *
 * Pura y segura: devuelve null si el nombre no corresponde a ninguna intención
 * conocida. No usa reflection ni lanza por datos inválidos.
 */
internal fun situationIntentFromPendingAction(action: PendingAction): SituationIntent? =
    SituationIntent.entries.firstOrNull { it.name == action.intentName }
