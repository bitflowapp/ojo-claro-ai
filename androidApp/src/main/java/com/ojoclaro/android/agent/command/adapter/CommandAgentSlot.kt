package com.ojoclaro.android.agent.command.adapter

import com.ojoclaro.android.agent.command.CommandConfidence

enum class CommandAgentSlotName {
    APP_NAME,
    CONTACT_NAME
}

data class CommandAgentSlot(
    val name: CommandAgentSlotName,
    val value: String,
    val confidence: CommandConfidence,
    val isSensitive: Boolean = false
)
