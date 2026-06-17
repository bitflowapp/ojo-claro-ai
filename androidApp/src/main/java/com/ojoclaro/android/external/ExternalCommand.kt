package com.ojoclaro.android.external

enum class ExternalCommandType {
    OPEN_WHATSAPP,
    OPEN_WHATSAPP_CHAT,
    OPEN_PHONE,
    COMPOSE_WHATSAPP_MESSAGE,
    CALL_CONTACT,
    NAVIGATE_TO_DESTINATION,
    NAVIGATE_TO_COORDINATES,
    READ_VISIBLE_SCREEN,
    REMEMBER_MEMORY,
    LIST_MEMORY,
    FORGET_LAST_MEMORY,
    CLEAR_MEMORY,
    CONFIRM_PENDING_ACTION,
    CANCEL_PENDING_ACTION,
    UNSUPPORTED
}

enum class CommandConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class ExternalCommand(
    val type: ExternalCommandType,
    val rawText: String,

    // Texto normalizado para evitar que otras capas vuelvan a normalizar.
    val normalizedText: String? = null,

    // Campos específicos actuales para WhatsApp.
    val contactName: String? = null,
    val messageText: String? = null,

    // Campos genéricos para memoria / agente.
    val targetName: String? = null,
    val payloadText: String? = null,

    // Seguridad del parseo.
    val confidence: CommandConfidence = CommandConfidence.HIGH
)

sealed class CommandResult {
    data class Success(val spokenText: String) : CommandResult()

    data class NeedsConfirmation(
        val spokenText: String,
        val confirmationId: String
    ) : CommandResult()

    data class Failed(
        val spokenText: String,
        val recoverable: Boolean
    ) : CommandResult()

    data class NotSupported(val spokenText: String) : CommandResult()
}

data class PendingConfirmation(
    val id: String,
    val command: ExternalCommand,
    val spokenText: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long? = null
) {
    fun isExpired(nowMillis: Long): Boolean {
        return expiresAtMillis?.let { nowMillis >= it } == true
    }
}

data class ExternalCommandRoute(
    val command: ExternalCommand,
    val result: CommandResult,
    val pendingConfirmation: PendingConfirmation? = null,
    val clearsPending: Boolean = false
)
