package com.ojoclaro.android.ai

import com.ojoclaro.android.consent.ConsentLevel
import com.ojoclaro.shared.model.ConfidenceLevel

data class AiResult(
    val spokenText: String,
    val displayText: String = spokenText,
    val confidence: ConfidenceLevel = ConfidenceLevel.HIGH,

    // La IA puede sugerir que una acción requiere confirmación,
    // pero el Orchestrator decide y crea el pending real.
    val requiresConfirmation: Boolean = false,
    val requiredConsentLevel: ConsentLevel = ConsentLevel.NONE,

    // Acción sugerida en forma semántica, no Android-specific.
    val suggestedAction: AiSuggestedAction? = null,

    // Seguridad y privacidad.
    val safetyNotice: String? = null,
    val riskSummaries: List<String> = emptyList(),
    val shouldRedactSensitiveText: Boolean = true
)

sealed interface AiSuggestedAction {
    data object None : AiSuggestedAction

    data class ComposeMessage(
        val targetContact: String,
        val message: String
    ) : AiSuggestedAction

    data object ReadVisibleScreen : AiSuggestedAction

    data object ReadOcrText : AiSuggestedAction

    data class RememberPreference(
        val label: String,
        val value: String
    ) : AiSuggestedAction
}
