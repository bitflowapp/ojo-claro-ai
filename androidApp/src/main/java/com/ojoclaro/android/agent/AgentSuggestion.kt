package com.ojoclaro.android.agent

enum class SuggestionType {
    ROUTINE,
    PENDING_TASK,
    MESSAGE,
    NAVIGATION,
    MEDICATION,
    PREFERENCE
}

data class AgentSuggestion(
    val id: String,
    val type: SuggestionType,
    val text: String,
    val proposedIntent: AgentIntent? = null,
    val requiredConfirmation: Boolean,
    val cooldownKey: String,
    val expiresAtMillis: Long? = null
)

