package com.ojoclaro.android.agent

object SuggestionPolicy {
    fun canShow(
        suggestion: AgentSuggestion,
        nowMillis: Long,
        cooldownStore: SuggestionCooldownStore
    ): Boolean {
        if (suggestion.text.isBlank()) return false
        if (cooldownStore.isOnCooldown(suggestion.cooldownKey, nowMillis)) return false
        return true
    }

    fun defaultCooldownMillis(type: SuggestionType): Long = when (type) {
        SuggestionType.ROUTINE -> 6 * 60 * 60 * 1000L
        SuggestionType.PENDING_TASK -> 24 * 60 * 60 * 1000L
        SuggestionType.MESSAGE -> 2 * 60 * 60 * 1000L
        SuggestionType.NAVIGATION -> 60 * 60 * 1000L
        SuggestionType.MEDICATION -> 6 * 60 * 60 * 1000L
        SuggestionType.PREFERENCE -> 24 * 60 * 60 * 1000L
    }
}

