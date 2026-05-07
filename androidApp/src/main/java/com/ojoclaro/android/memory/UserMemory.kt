package com.ojoclaro.android.memory

data class UserMemory(
    val id: String,
    val type: MemoryType,

    // Texto corto para identificar el recuerdo.
    // Ejemplo: "Sofi", "respuestas cortas", "transferencias".
    val label: String,

    // Valor seguro y breve.
    // Nunca debe contener chats completos, contraseñas, códigos ni pantallas completas.
    val value: String,

    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val expiresAtMillis: Long? = null,

    // Seguridad.
    val isSensitive: Boolean = false,
    val userApproved: Boolean = false,

    // Control de uso por el agente.
    val canBeSpoken: Boolean = true,
    val canBeUsedForSuggestions: Boolean = true,
    val requiresConfirmationBeforeUse: Boolean = false,

    // Trazabilidad local.
    val source: MemorySource = MemorySource.USER_COMMAND,
    val confidence: MemoryConfidence = MemoryConfidence.HIGH
)

enum class MemorySource {
    USER_COMMAND,
    OBSERVED_PATTERN,
    SAFETY_RULE,
    IMPORTED_DEFAULT
}

enum class MemoryConfidence {
    HIGH,
    MEDIUM,
    LOW
}
