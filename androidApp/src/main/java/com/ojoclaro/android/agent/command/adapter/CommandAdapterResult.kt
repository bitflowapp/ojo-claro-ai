package com.ojoclaro.android.agent.command.adapter

import com.ojoclaro.android.agent.command.CommandIntent

enum class CommandAdapterStatus {
    READY,
    NEEDS_SLOT,
    UNSUPPORTED
}

enum class CommandAdapterReason {
    MAPPED,
    MISSING_REQUIRED_SLOT,
    UNSUPPORTED_INTENT,
    SENSITIVE_COMMAND_REQUIRES_CONFIRMATION
}

data class CommandAdapterResult(
    val sourceIntent: CommandIntent,
    val status: CommandAdapterStatus,
    val intent: CommandAgentIntent?,
    val slots: List<CommandAgentSlot> = emptyList(),
    val missingSlots: List<CommandAgentSlotName> = emptyList(),
    val requiresContext: Boolean = false,
    val isPotentiallySensitive: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val isExecutable: Boolean = false,
    val reason: CommandAdapterReason,
    val debugReason: String = ""
) {
    fun slotValue(name: CommandAgentSlotName): String? =
        slots.firstOrNull { it.name == name }?.value
}
