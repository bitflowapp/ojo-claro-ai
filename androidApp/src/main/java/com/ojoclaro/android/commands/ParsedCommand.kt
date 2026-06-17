package com.ojoclaro.android.commands

enum class ParsedCommandType {
    OPEN_WHATSAPP,
    COMPOSE_WHATSAPP_MESSAGE,
    READ_VISIBLE_SCREEN,
    DESCRIBE_SCENE,
    READ_OCR_TEXT,
    EMERGENCY_HELP,
    STOP_SPEAKING,
    CONFIRM,
    CANCEL,

    REMEMBER_MEMORY,
    LIST_MEMORY,
    FORGET_LAST_MEMORY,
    CLEAR_MEMORY,

    HELP,
    UNKNOWN
}

enum class CommandConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class ParsedCommand(
    val type: ParsedCommandType,
    val rawText: String,
    val normalizedText: String,

    val targetName: String? = null,
    val message: String? = null,
    val payloadText: String? = null,

    val confidence: CommandConfidence = CommandConfidence.HIGH,
    val requiresConfirmation: Boolean = false,
    val isSensitive: Boolean = false
)
