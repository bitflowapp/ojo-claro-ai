package com.ojoclaro.android.agent.command

data class ParsedCommand(
    val rawInput: String,
    val normalizedInput: String,
    val intent: CommandIntent,
    val confidence: CommandConfidence,
    val slots: List<CommandSlot> = emptyList(),
    val requiresContext: Boolean = false,
    val isPotentiallySensitive: Boolean = false,
    val debugReason: String = ""
) {
    fun slotValue(name: CommandSlotName): String? =
        slots.firstOrNull { it.name == name }?.value
}
