package com.ojoclaro.android.agent.command

enum class CommandSlotName {
    APP_NAME,
    CONTACT_NAME
}

data class CommandSlot(
    val name: CommandSlotName,
    val value: String,
    val confidence: CommandConfidence = CommandConfidence.HIGH,
    val isSensitive: Boolean = false
)
