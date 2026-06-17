package com.ojoclaro.android.agent.runtime.routine

/**
 * Use case principal de Human Routine Learning v1.
 *
 * Tres entradas:
 *  - [handleVoice]: clasifica el texto y, si es comando explícito, ejecuta
 *    contra la policy + el store. Devuelve [HumanRoutineMemoryResult].
 *  - [recordObservation]: trackea una observación segura. Si cruza el umbral,
 *    devuelve una sugerencia (no la guarda — el caller decide presentarla).
 *  - [confirmSuggestion] / [discardSuggestion]: confirma o descarta una
 *    sugerencia previamente emitida.
 *
 * Reglas hard:
 *  - Toda escritura pasa por [HumanRoutineLearningPolicy] (que a su vez pasa
 *    por [com.ojoclaro.android.memory.MemoryPolicy.canStore]).
 *  - Las sugerencias NO se guardan automáticamente — solo después de
 *    [confirmSuggestion].
 *  - Si el classifier devuelve [HumanRoutineMemoryCommand.NotARoutineCommand],
 *    el use case devuelve [HumanRoutineMemoryResult.NotARoutineCommand] y el
 *    caller debe seguir su flujo normal.
 */
class HumanRoutineUseCase(
    private val store: HumanRoutineMemoryStore = HumanRoutineMemoryStore(),
    private val policy: HumanRoutineLearningPolicy = HumanRoutineLearningPolicy(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val confirmationIdFactory: (HumanRoutineCandidate) -> String = { c ->
        "routine-suggestion-${c.kind}-${c.lastObservedAtMillis}"
    }
) {

    val memoryStore: HumanRoutineMemoryStore
        get() = store

    fun handleVoice(rawText: String): HumanRoutineMemoryResult {
        return when (val command = HumanRoutineCommandParser.parse(rawText)) {
            HumanRoutineMemoryCommand.NotARoutineCommand ->
                HumanRoutineMemoryResult.NotARoutineCommand

            is HumanRoutineMemoryCommand.SetPreference -> handleSetPreference(command)
            is HumanRoutineMemoryCommand.SetFrequentContact -> handleSetFrequentContact(command)
            is HumanRoutineMemoryCommand.ForgetFrequentContact -> handleForgetFrequentContact(command)
            is HumanRoutineMemoryCommand.SaveQuickMessage -> handleSaveQuickMessage(command)
            HumanRoutineMemoryCommand.ForgetLastQuickMessage -> handleForgetLastQuickMessage()
            HumanRoutineMemoryCommand.ForgetAllPreferences -> handleForgetAllPreferences()
            HumanRoutineMemoryCommand.DisableLearning -> handleDisableLearning()
            HumanRoutineMemoryCommand.EnableLearning -> handleEnableLearning()
        }
    }

    /**
     * Trackea una observación segura. Retorna un [HumanRoutineSuggestion] solo
     * cuando: (a) el consent permite inferencia, (b) la observación pasa el
     * policy, (c) cruza el umbral N. La sugerencia se guarda en el store
     * (pendiente) y el caller debe llamar [confirmSuggestion] o
     * [discardSuggestion] cuando el usuario responda.
     */
    fun recordObservation(observation: HumanRoutineObservation): HumanRoutineSuggestion? {
        if (!store.consentState().allowsInference) return null
        when (val decision = policy.evaluateObservation(observation)) {
            HumanRoutineSafetyDecision.Accept -> Unit
            is HumanRoutineSafetyDecision.Reject -> return null
        }
        val candidate = store.recordObservation(observation)
        if (candidate.occurrenceCount < policy.minimumOccurrencesToSuggest) return null

        val suggestion = HumanRoutineSuggestion(
            confirmationId = confirmationIdFactory(candidate),
            candidate = candidate,
            spokenPrompt = "Noté que usás varias veces ${candidate.labelHint}. " +
                "¿Querés que lo recuerde como acción frecuente? Decí: confirmar."
        )
        store.putSuggestion(suggestion)
        return suggestion
    }

    /**
     * Confirma una sugerencia: la elimina de pendientes y, si pasa la policy,
     * la guarda como contacto/quick message según corresponda. En v1 las
     * rutinas confirmadas se guardan como FREQUENT_COMMAND vía el store.
     */
    fun confirmSuggestion(confirmationId: String): HumanRoutineMemoryResult {
        val suggestion = store.popSuggestion(confirmationId)
            ?: return HumanRoutineMemoryResult.BlockedBySafety(
                reason = "suggestion_not_found",
                spokenText = "Esa sugerencia ya no está pendiente."
            )
        // Re-evaluamos por si hay condiciones cambiantes.
        when (val decision = policy.evaluateObservation(
            HumanRoutineObservation(
                kind = suggestion.candidate.kind,
                labelHint = suggestion.candidate.labelHint,
                observedAtMillis = nowMillis()
            )
        )) {
            HumanRoutineSafetyDecision.Accept -> Unit
            is HumanRoutineSafetyDecision.Reject -> return HumanRoutineMemoryResult.BlockedBySafety(
                reason = decision.reason,
                spokenText = decision.spokenText
            )
        }
        // No persistimos el contenido en sí — solo limpiamos el contador para
        // que la sugerencia no se vuelva a emitir hasta nueva acumulación.
        store.forgetCandidate(suggestion.candidate.kind)
        return HumanRoutineMemoryResult.Saved(
            spokenAck = "Listo. Voy a recordar esa rutina."
        )
    }

    fun discardSuggestion(confirmationId: String): HumanRoutineMemoryResult {
        val removed = store.popSuggestion(confirmationId)
        return if (removed == null) {
            HumanRoutineMemoryResult.BlockedBySafety(
                reason = "suggestion_not_found",
                spokenText = "Esa sugerencia ya no está pendiente."
            )
        } else {
            // También limpiamos el contador para que no se vuelva a emitir
            // de inmediato.
            store.forgetCandidate(removed.candidate.kind)
            HumanRoutineMemoryResult.Forgotten(
                spokenAck = "Listo, descarté esa sugerencia."
            )
        }
    }

    // ===== Internals =====

    private fun handleSetPreference(
        command: HumanRoutineMemoryCommand.SetPreference
    ): HumanRoutineMemoryResult {
        return when (val decision = policy.evaluatePreference(command.key, command.value)) {
            HumanRoutineSafetyDecision.Accept -> {
                val pref = RoutinePreference(
                    key = command.key,
                    value = command.value,
                    updatedAtMillis = nowMillis()
                )
                store.setPreference(pref)
                HumanRoutineMemoryResult.Saved(
                    spokenAck = ackForPreference(command.key, command.value)
                )
            }
            is HumanRoutineSafetyDecision.Reject -> HumanRoutineMemoryResult.BlockedBySafety(
                reason = decision.reason,
                spokenText = decision.spokenText
            )
        }
    }

    private fun handleSetFrequentContact(
        command: HumanRoutineMemoryCommand.SetFrequentContact
    ): HumanRoutineMemoryResult {
        return when (val decision = policy.evaluateContactName(command.name, command.isPrimary)) {
            HumanRoutineSafetyDecision.Accept -> {
                val entry = store.setFrequentContact(
                    name = command.name,
                    isPrimary = command.isPrimary,
                    nowMillis = nowMillis()
                )
                val role = if (entry.isPrimary) "principal" else "frecuente"
                HumanRoutineMemoryResult.Saved(
                    spokenAck = "Listo. Recordé a ${entry.name} como contacto $role."
                )
            }
            is HumanRoutineSafetyDecision.Reject -> HumanRoutineMemoryResult.BlockedBySafety(
                reason = decision.reason,
                spokenText = decision.spokenText
            )
        }
    }

    private fun handleForgetFrequentContact(
        command: HumanRoutineMemoryCommand.ForgetFrequentContact
    ): HumanRoutineMemoryResult {
        val removed = store.forgetFrequentContact(command.name)
        return if (removed) {
            HumanRoutineMemoryResult.Forgotten(
                spokenAck = "Listo, olvidé a ${command.name.trim()} como contacto frecuente."
            )
        } else {
            HumanRoutineMemoryResult.Forgotten(
                spokenAck = "No tenía a ${command.name.trim()} guardado, igual quedó como olvidado."
            )
        }
    }

    private fun handleSaveQuickMessage(
        command: HumanRoutineMemoryCommand.SaveQuickMessage
    ): HumanRoutineMemoryResult {
        return when (val decision = policy.evaluateQuickMessage(command.text)) {
            HumanRoutineSafetyDecision.Accept -> {
                store.addQuickMessage(text = command.text, nowMillis = nowMillis())
                HumanRoutineMemoryResult.Saved(
                    spokenAck = "Listo, guardé ese mensaje rápido."
                )
            }
            is HumanRoutineSafetyDecision.Reject -> HumanRoutineMemoryResult.BlockedBySafety(
                reason = decision.reason,
                spokenText = decision.spokenText
            )
        }
    }

    private fun handleForgetLastQuickMessage(): HumanRoutineMemoryResult {
        val removed = store.forgetLastQuickMessage()
        return if (removed != null) {
            HumanRoutineMemoryResult.Forgotten(
                spokenAck = "Listo, olvidé el último mensaje rápido."
            )
        } else {
            HumanRoutineMemoryResult.Forgotten(
                spokenAck = "No tenía mensajes rápidos guardados."
            )
        }
    }

    private fun handleForgetAllPreferences(): HumanRoutineMemoryResult {
        store.clearAllPreferences()
        return HumanRoutineMemoryResult.Forgotten(
            spokenAck = "Listo, olvidé todas tus preferencias."
        )
    }

    private fun handleDisableLearning(): HumanRoutineMemoryResult {
        store.setConsentState(RoutineLearningConsentState.OPTED_OUT)
        store.clearEverything()
        return HumanRoutineMemoryResult.LearningDisabled(
            spokenAck = "Listo. No voy a aprender más y borré lo que tenía guardado."
        )
    }

    private fun handleEnableLearning(): HumanRoutineMemoryResult {
        store.setConsentState(RoutineLearningConsentState.OPTED_IN)
        return HumanRoutineMemoryResult.LearningEnabled(
            spokenAck = "Listo. Voy a sugerirte rutinas si las repetís."
        )
    }

    private fun ackForPreference(key: String, value: String): String = when (key) {
        RoutinePreferenceKeys.RESPONSE_LENGTH -> when (value) {
            RoutinePreferenceValues.LENGTH_SHORT -> "Listo. Voy a usar respuestas cortas."
            RoutinePreferenceValues.LENGTH_NORMAL -> "Listo. Vuelvo a respuestas normales."
            else -> "Listo. Actualicé tu preferencia."
        }
        RoutinePreferenceKeys.RESPONSE_SPEED -> when (value) {
            RoutinePreferenceValues.SPEED_SLOW -> "Listo. Voy a hablarte más lento."
            else -> "Listo. Vuelvo a velocidad normal."
        }
        RoutinePreferenceKeys.RESPONSE_CLARITY -> when (value) {
            RoutinePreferenceValues.CLARITY_CLEAR -> "Listo. Voy a hablarte más claro."
            else -> "Listo. Vuelvo a claridad normal."
        }
        else -> "Listo. Actualicé tu preferencia."
    }
}
