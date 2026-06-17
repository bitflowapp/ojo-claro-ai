package com.ojoclaro.android.message

import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.risk.RiskDetector

class LocalMessageTemplateComposer(
    private val riskDetector: RiskDetector = RiskDetector()
) : HumanMessageComposer {

    override fun compose(request: MessageCompositionRequest): MessageCompositionResult {
        val combined = "${request.originalText} ${request.messageHint}"
        if (!PrivacyGuard.isSafeMessagePayload(combined) ||
            riskDetector.detectFromCommand(combined).any { it.requiresConfirmation }
        ) {
            return MessageCompositionResult(
                proposedMessage = "",
                spokenProposal = "No puedo preparar ese mensaje porque parece sensible.",
                styleUsed = request.style,
                requiresConfirmation = false,
                shouldSendAutomatically = false,
                safetyNotes = "Mensaje bloqueado por privacidad.",
                blockedReason = "sensitive_message"
            )
        }

        val style = resolveStyle(request)
        val draft = buildDraft(request, style)
        return MessageCompositionResult(
            proposedMessage = draft,
            spokenProposal = "Puedo preparar este mensaje para ${request.contactName}: '$draft'. Para prepararlo en WhatsApp, decí: confirmar.",
            styleUsed = style,
            requiresConfirmation = true,
            shouldSendAutomatically = false,
            safetyNotes = "Preparado localmente."
        )
    }

    private fun resolveStyle(request: MessageCompositionRequest): MessageStyle {
        val memoryStyle = request.memorySnapshot.messageStyles.firstOrNull()?.value
            ?.lowercase()
            .orEmpty()
        return when {
            memoryStyle.contains("cari") -> MessageStyle.WARM
            memoryStyle.contains("formal") -> MessageStyle.FORMAL
            memoryStyle.contains("tranquil") -> MessageStyle.CALM
            memoryStyle.contains("profes") -> MessageStyle.PROFESSIONAL
            memoryStyle.contains("breve") -> MessageStyle.BRIEF
            else -> request.style
        }
    }

    private fun buildDraft(request: MessageCompositionRequest, style: MessageStyle): String {
        val lowerHint = request.messageHint.lowercase()
        val cleanHint = cleanHint(request.messageHint)
        return when {
            lowerHint.contains("llego tarde") || lowerHint.contains("voy tarde") ->
                when (style) {
                    MessageStyle.WARM -> "Voy un poco demorado. Llego en unos minutos."
                    MessageStyle.FORMAL -> "Voy con una demora breve. Llego en unos minutos."
                    MessageStyle.CALM -> "Voy un poco demorado, llego en unos minutos."
                    MessageStyle.PROFESSIONAL -> "Voy con una demora breve; llego en unos minutos."
                    MessageStyle.BRIEF -> "Llego en unos minutos."
                    MessageStyle.NEUTRAL -> "Voy un poco demorado, llego en unos minutos."
                }

            lowerHint.contains("llego en 10") || lowerHint.contains("llego en diez") ->
                when (style) {
                    MessageStyle.WARM -> "Llego en 10 minutos. Ya salgo."
                    MessageStyle.FORMAL -> "Llego en 10 minutos."
                    MessageStyle.CALM -> "Llego en 10 minutos."
                    MessageStyle.PROFESSIONAL -> "Llego en 10 minutos."
                    MessageStyle.BRIEF -> "Llego en 10."
                    MessageStyle.NEUTRAL -> "Llego en 10 minutos."
                }

            lowerHint.contains("estoy llegando") ->
                when (style) {
                    MessageStyle.WARM -> "Ya estoy llegando."
                    else -> "Ya estoy llegando."
                }

            else ->
                when (style) {
                    MessageStyle.WARM -> cleanHint
                    MessageStyle.FORMAL -> cleanHint.replaceFirstChar { it.uppercase() }
                    MessageStyle.CALM -> cleanHint
                    MessageStyle.PROFESSIONAL -> cleanHint.replaceFirstChar { it.uppercase() }
                    MessageStyle.BRIEF -> briefHint(cleanHint)
                    MessageStyle.NEUTRAL -> cleanHint
                }
        }
    }

    private fun briefHint(text: String): String =
        text.split(Regex("\\s+")).take(10).joinToString(" ").trim().ifBlank { "Hola." }

    private fun cleanHint(text: String): String =
        text.trim()
            .trim('.', ',', ';', ':', '!', '?')
            .replace(Regex("\\s+"), " ")
            .ifBlank { "Hola" }
}
