package com.ojoclaro.android.agent.situation

/**
 * Constructor puro de [ActiveGoal] para el Situation Brain.
 *
 * Fase 8: soporta WRITE_MESSAGE, CALL_CONTACT y OPEN_APP. Otras intenciones
 * devuelven null. Determinista, sin Android APIs, sin NLP.
 *
 * NO completa slots con palabras de confirmación o cancelación: "sí", "dale",
 * "cancelá", "no" nunca se transforman en contact / message / target.
 */
object SituationGoalBuilder {

    /**
     * Construye un [ActiveGoal] a partir de un comando fresco. Devuelve null
     * si la intención no es de las soportadas en esta fase.
     */
    fun goalFromCommand(
        command: String,
        intent: SituationIntent,
        now: Long
    ): ActiveGoal? = when (intent) {
        SituationIntent.WRITE_MESSAGE -> buildWriteMessageGoal(command, now)
        SituationIntent.CALL_CONTACT -> buildCallContactGoal(command, now)
        SituationIntent.OPEN_APP -> buildOpenAppGoal(command, now)
        else -> null
    }

    /**
     * Intenta completar el [goal] con la información del [command]. Si el
     * comando es una confirmación / cancelación / corte duro, devuelve el goal
     * sin cambios — esas palabras NO pueden completar slots.
     */
    fun continueGoal(
        goal: ActiveGoal,
        command: String,
        @Suppress("UNUSED_PARAMETER") now: Long
    ): ActiveGoal {
        val normalized = normalizeSituationCommand(command)
        if (normalized in SituationVocabulary.CONFIRMATION) return goal
        if (normalized in SituationVocabulary.SOFT_CANCEL) return goal
        if (normalized in SituationVocabulary.EMERGENCY_STOP) return goal
        return when (goal.intent) {
            SituationIntent.WRITE_MESSAGE -> continueWriteMessage(goal, command)
            SituationIntent.CALL_CONTACT -> continueCallContact(goal, command)
            SituationIntent.OPEN_APP -> continueOpenApp(goal, command)
            else -> goal
        }
    }

    // --- Builders por intención ---------------------------------------------

    private fun buildWriteMessageGoal(command: String, now: Long): ActiveGoal {
        val contact = SituationSlotExtractor.extractContact(command)
        val message = if (contact.isNotBlank()) {
            SituationSlotExtractor.extractMessageForContact(command, contact)
        } else {
            ""
        }
        val filled = buildMap {
            if (contact.isNotBlank()) put("contact", contact)
            if (message.isNotBlank()) put("message", message)
        }
        val missing = buildSet {
            if (contact.isBlank()) add("contact")
            if (message.isBlank()) add("message")
        }
        return ActiveGoal(
            description = describeWriteMessage(contact, message),
            intent = SituationIntent.WRITE_MESSAGE,
            createdAt = now,
            slotsFilled = filled,
            slotsMissing = missing
        )
    }

    private fun buildCallContactGoal(command: String, now: Long): ActiveGoal {
        val contact = SituationSlotExtractor.extractContact(command)
        return ActiveGoal(
            description = if (contact.isNotBlank()) "llamar a $contact" else "llamar a alguien",
            intent = SituationIntent.CALL_CONTACT,
            createdAt = now,
            slotsFilled = if (contact.isNotBlank()) mapOf("contact" to contact) else emptyMap(),
            slotsMissing = if (contact.isBlank()) setOf("contact") else emptySet()
        )
    }

    private fun buildOpenAppGoal(command: String, now: Long): ActiveGoal {
        val target = SituationSlotExtractor.extractTarget(command, SituationIntent.OPEN_APP)
        return ActiveGoal(
            description = if (target.isNotBlank()) "abrir $target" else "abrir una app",
            intent = SituationIntent.OPEN_APP,
            createdAt = now,
            slotsFilled = if (target.isNotBlank()) mapOf("target" to target) else emptyMap(),
            slotsMissing = if (target.isBlank()) setOf("target") else emptySet()
        )
    }

    // --- Continuación por intención -----------------------------------------

    private fun continueWriteMessage(goal: ActiveGoal, command: String): ActiveGoal {
        var updated = goal
        if ("contact" in goal.slotsMissing) {
            val contact = SituationSlotExtractor.extractContact(command).ifBlank {
                if (looksLikeSingleEntityName(command)) command.trim() else ""
            }
            if (contact.isNotBlank()) {
                updated = updated.withSlotFilled("contact", contact.take(ActiveGoal.MAX_SLOT_VALUE_CHARS))
            }
        }
        if ("message" in updated.slotsMissing) {
            val explicitMessage = SituationSlotExtractor.extractMessageForContact(
                command,
                updated.slotsFilled["contact"].orEmpty()
            )
            val message = explicitMessage.ifBlank {
                if (SituationSlotExtractor.looksLikeMessageContinuation(command)) {
                    cleanMessagePrefix(command)
                } else {
                    ""
                }
            }
            if (message.isNotBlank()) {
                updated = updated.withSlotFilled(
                    "message",
                    message.take(ActiveGoal.MAX_SLOT_VALUE_CHARS)
                )
            }
        }
        return updated
    }

    private fun continueCallContact(goal: ActiveGoal, command: String): ActiveGoal {
        if ("contact" !in goal.slotsMissing) return goal
        val contact = SituationSlotExtractor.extractContact(command).ifBlank {
            if (looksLikeSingleEntityName(command)) command.trim() else ""
        }
        return if (contact.isNotBlank()) {
            goal.withSlotFilled("contact", contact.take(ActiveGoal.MAX_SLOT_VALUE_CHARS))
        } else {
            goal
        }
    }

    private fun continueOpenApp(goal: ActiveGoal, command: String): ActiveGoal {
        if ("target" !in goal.slotsMissing) return goal
        val target = SituationSlotExtractor.extractTarget(command, SituationIntent.OPEN_APP)
            .ifBlank {
                if (looksLikeSingleEntityName(command)) command.trim() else ""
            }
        return if (target.isNotBlank()) {
            goal.withSlotFilled("target", target.take(ActiveGoal.MAX_SLOT_VALUE_CHARS))
        } else {
            goal
        }
    }

    // --- Utilidades ----------------------------------------------------------

    /**
     * True si el comando parece un nombre simple (1-3 palabras cortas) y se
     * puede usar tal cual como contact / target. Si NO parece un nombre simple,
     * no se completa el slot (defensivo).
     */
    private fun looksLikeSingleEntityName(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return false
        val words = trimmed.split(Regex("\\s+"))
        if (words.size > 3) return false
        return words.all { it.length <= 20 }
    }

    private fun cleanMessagePrefix(command: String): String =
        command
            .trim()
            .replaceFirst(Regex("^que\\s+", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun describeWriteMessage(contact: String, message: String): String = when {
        contact.isNotBlank() && message.isNotBlank() -> "avisarle a $contact que $message"
        contact.isNotBlank() -> "avisarle a $contact"
        else -> "preparar un mensaje"
    }
}
