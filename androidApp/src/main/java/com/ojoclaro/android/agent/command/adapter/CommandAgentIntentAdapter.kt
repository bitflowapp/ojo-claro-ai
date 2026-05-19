package com.ojoclaro.android.agent.command.adapter

import com.ojoclaro.android.agent.command.CommandIntent
import com.ojoclaro.android.agent.command.CommandNormalizer
import com.ojoclaro.android.agent.command.CommandSlot
import com.ojoclaro.android.agent.command.CommandSlotName
import com.ojoclaro.android.agent.command.ParsedCommand

class CommandAgentIntentAdapter {

    fun adapt(command: ParsedCommand): CommandAdapterResult {
        val sensitive = command.isPotentiallySensitive || hasDangerousLanguage(command)

        return when (command.intent) {
            CommandIntent.READ_SCREEN ->
                mapped(command, sensitive, CommandAgentIntent.READ_VISIBLE_SCREEN)

            CommandIntent.SUMMARIZE_SCREEN ->
                mapped(command, sensitive, CommandAgentIntent.SUMMARIZE_VISIBLE_SCREEN)

            CommandIntent.EXPLAIN_SCREEN ->
                mapped(command, sensitive, CommandAgentIntent.EXPLAIN_VISIBLE_SCREEN)

            CommandIntent.OPEN_APP ->
                adaptOpenApp(command, sensitive)

            CommandIntent.PREPARE_MESSAGE ->
                adaptPrepareMessage(command, sensitive)

            CommandIntent.CONFIRM ->
                mapped(command, sensitive, CommandAgentIntent.CONFIRM_PENDING_ACTION)

            CommandIntent.CANCEL ->
                mapped(command, sensitive, CommandAgentIntent.CANCEL_PENDING_ACTION)

            CommandIntent.REPEAT ->
                mapped(command, sensitive, CommandAgentIntent.REPEAT_LAST_RESPONSE)

            CommandIntent.SHORTER ->
                mapped(command, sensitive, CommandAgentIntent.MODIFY_RESPONSE_SHORTER)

            CommandIntent.MORE_DETAIL ->
                mapped(command, sensitive, CommandAgentIntent.MODIFY_RESPONSE_MORE_DETAIL)

            CommandIntent.RISK_CHECK ->
                mapped(command, sensitive, CommandAgentIntent.CHECK_SCREEN_RISK)

            CommandIntent.UNKNOWN ->
                unsupported(command, sensitive)
        }
    }

    private fun adaptOpenApp(
        command: ParsedCommand,
        sensitive: Boolean
    ): CommandAdapterResult {
        val appName = command.slotValue(CommandSlotName.APP_NAME)?.takeIf { it.isNotBlank() }
        if (appName == null) {
            return CommandAdapterResult(
                sourceIntent = command.intent,
                status = CommandAdapterStatus.NEEDS_SLOT,
                intent = CommandAgentIntent.OPEN_APP,
                missingSlots = listOf(CommandAgentSlotName.APP_NAME),
                requiresContext = command.requiresContext,
                isPotentiallySensitive = sensitive,
                requiresConfirmation = sensitive,
                reason = CommandAdapterReason.MISSING_REQUIRED_SLOT,
                debugReason = appendDebug(command.debugReason, "missing_app_name")
            )
        }

        return mapped(
            command = command,
            sensitive = sensitive,
            intent = CommandAgentIntent.OPEN_APP,
            slots = listOfNotNull(command.toAgentSlot(CommandSlotName.APP_NAME))
        )
    }

    private fun adaptPrepareMessage(
        command: ParsedCommand,
        sensitive: Boolean
    ): CommandAdapterResult {
        val contactSlot = command.toAgentSlot(CommandSlotName.CONTACT_NAME)
        if (contactSlot == null) {
            return CommandAdapterResult(
                sourceIntent = command.intent,
                status = CommandAdapterStatus.NEEDS_SLOT,
                intent = CommandAgentIntent.PREPARE_MESSAGE,
                missingSlots = listOf(CommandAgentSlotName.CONTACT_NAME),
                requiresContext = command.requiresContext,
                isPotentiallySensitive = sensitive,
                requiresConfirmation = sensitive,
                reason = if (sensitive) {
                    CommandAdapterReason.SENSITIVE_COMMAND_REQUIRES_CONFIRMATION
                } else {
                    CommandAdapterReason.MISSING_REQUIRED_SLOT
                },
                debugReason = appendDebug(command.debugReason, "missing_contact_name")
            )
        }

        return mapped(
            command = command,
            sensitive = sensitive,
            intent = CommandAgentIntent.PREPARE_MESSAGE,
            slots = listOf(contactSlot)
        )
    }

    private fun mapped(
        command: ParsedCommand,
        sensitive: Boolean,
        intent: CommandAgentIntent,
        slots: List<CommandAgentSlot> = emptyList()
    ): CommandAdapterResult =
        CommandAdapterResult(
            sourceIntent = command.intent,
            status = CommandAdapterStatus.READY,
            intent = intent,
            slots = slots,
            requiresContext = command.requiresContext,
            isPotentiallySensitive = sensitive,
            requiresConfirmation = sensitive,
            isExecutable = false,
            reason = if (sensitive) {
                CommandAdapterReason.SENSITIVE_COMMAND_REQUIRES_CONFIRMATION
            } else {
                CommandAdapterReason.MAPPED
            },
            debugReason = command.debugReason
        )

    private fun unsupported(
        command: ParsedCommand,
        sensitive: Boolean
    ): CommandAdapterResult =
        CommandAdapterResult(
            sourceIntent = command.intent,
            status = CommandAdapterStatus.UNSUPPORTED,
            intent = null,
            requiresContext = command.requiresContext,
            isPotentiallySensitive = sensitive,
            requiresConfirmation = sensitive,
            isExecutable = false,
            reason = if (sensitive) {
                CommandAdapterReason.SENSITIVE_COMMAND_REQUIRES_CONFIRMATION
            } else {
                CommandAdapterReason.UNSUPPORTED_INTENT
            },
            debugReason = appendDebug(command.debugReason, "unsupported_intent")
        )

    private fun ParsedCommand.toAgentSlot(slotName: CommandSlotName): CommandAgentSlot? {
        val slot = slots.firstOrNull { it.name == slotName } ?: return null
        if (slot.value.isBlank()) return null
        return slot.toAgentSlot()
    }

    private fun CommandSlot.toAgentSlot(): CommandAgentSlot =
        CommandAgentSlot(
            name = when (name) {
                CommandSlotName.APP_NAME -> CommandAgentSlotName.APP_NAME
                CommandSlotName.CONTACT_NAME -> CommandAgentSlotName.CONTACT_NAME
            },
            value = value,
            confidence = confidence,
            isSensitive = isSensitive
        )

    private fun hasDangerousLanguage(command: ParsedCommand): Boolean {
        val normalized = command.normalizedInput.ifBlank {
            CommandNormalizer.normalize(command.rawInput)
        }
        if (normalized.isBlank()) return false

        return dangerousPhrases.any { phrase ->
            normalized.containsPhrase(phrase)
        }
    }

    private fun String.containsPhrase(phrase: String): Boolean =
        " $this ".contains(" $phrase ")

    private fun appendDebug(existing: String, addition: String): String =
        listOf(existing, addition)
            .filter { it.isNotBlank() }
            .joinToString(separator = "; ")

    private companion object {
        private val dangerousPhrases = setOf(
            "envia ya",
            "enviar ya",
            "mandalo",
            "mandalo ya",
            "pagar",
            "paga",
            "transferir",
            "transferi",
            "borrar",
            "borra",
            "eliminar",
            "elimina",
            "comprar",
            "compra",
            "llamar",
            "llama"
        )
    }
}
