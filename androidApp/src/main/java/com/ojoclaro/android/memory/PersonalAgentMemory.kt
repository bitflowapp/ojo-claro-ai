package com.ojoclaro.android.memory

data class PersonalAgentMemory(
    val id: String,
    val type: PersonalMemoryType,
    val label: String,
    val value: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val expiresAtMillis: Long? = null,
    val isSensitive: Boolean = false,
    val userApproved: Boolean = false,
    val canBeSpoken: Boolean = true,
    val canBeUsedForSuggestions: Boolean = true,
    val requiresConfirmationBeforeUse: Boolean = false
)

