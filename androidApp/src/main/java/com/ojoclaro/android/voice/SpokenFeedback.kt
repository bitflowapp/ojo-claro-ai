package com.ojoclaro.android.voice

/**
 * Semantic voice feedback request.
 *
 * This model describes what should be spoken, not how Android TTS speaks it.
 * Callers provide a stable [semanticKey] so repeated status messages can be
 * deduplicated even when their text changes slightly.
 */
data class SpokenFeedback(
    val semanticKey: String,
    val text: String,
    val category: SpokenFeedbackCategory = SpokenFeedbackCategory.INFO,
    val priority: SpokenFeedbackPriority = SpokenFeedbackPriority.NORMAL,
    val force: Boolean = false,
    val dedupWindowMs: Long = DEFAULT_DEDUP_WINDOW_MS,
    val alternates: List<String> = emptyList(),
    val createdAtMillis: Long? = null
) {
    init {
        require(semanticKey.isNotBlank()) { "semanticKey must not be blank" }
        require(text.isNotBlank()) { "text must not be blank" }
        require(dedupWindowMs >= 0L) { "dedupWindowMs must be >= 0" }
        require(alternates.none { it.isBlank() }) { "alternates must not contain blank text" }
    }

    companion object {
        const val DEFAULT_DEDUP_WINDOW_MS: Long = 6_000L
    }
}

enum class SpokenFeedbackCategory {
    INFO,
    CONFIRMATION_REQUIRED,
    CONFIRMED,
    CANCELLED,
    REJECTED,
    NEEDS_SLOT,
    ERROR,
    SAFETY_WARNING
}

enum class SpokenFeedbackPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}
