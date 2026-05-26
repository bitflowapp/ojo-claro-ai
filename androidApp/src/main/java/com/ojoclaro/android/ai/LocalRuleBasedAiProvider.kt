package com.ojoclaro.android.ai

import com.ojoclaro.android.agent.core.emergency.EmergencyPolicy
import com.ojoclaro.android.help.VoiceHelpCenter
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.shared.model.ConfidenceLevel

/**
 * Provider 100% on-device.
 *
 * No usa red, no llama IA cloud, no guarda datos y no ejecuta acciones.
 * Solo transforma AiTask + AiContext en una respuesta local, breve y segura.
 */
class LocalRuleBasedAiProvider : AiProvider {

    override suspend fun process(task: AiTask, context: AiContext): AiResult = when (task) {
        AiTask.READ_TEXT -> handleReadText(context)

        AiTask.READ_VISIBLE_SCREEN,
        AiTask.EXPLAIN_SCREEN -> handleReadScreen(context)

        AiTask.DESCRIBE_SCENE -> describeSceneFallback()

        AiTask.COMPOSE_MESSAGE -> composeMessageHint()

        AiTask.EMERGENCY_HELP -> emergencyHelp()

        AiTask.HELP -> helpResponse()

        AiTask.UNKNOWN -> unknownCommand()
    }

    private fun handleReadText(context: AiContext): AiResult {
        val text = context.ocrText?.trim()

        if (text.isNullOrBlank()) {
            return AiResult(
                spokenText = "Para leer texto, tocá Leer texto y apuntá la cámara.",
                confidence = ConfidenceLevel.MEDIUM
            )
        }

        val safeText = PrivacyGuard.sanitizeForSpeech(text)
        val prefix = if (context.preferShortAnswers) {
            "Dice:"
        } else {
            "El texto dice:"
        }

        return AiResult(
            spokenText = "$prefix $safeText",
            confidence = ConfidenceLevel.HIGH,
            safetyNotice = context.riskSummaries.firstOrNull()
        )
    }

    private fun handleReadScreen(context: AiContext): AiResult {
        val screen = context.visibleScreenText?.trim()

        if (screen.isNullOrBlank()) {
            return AiResult(
                spokenText = "No encontré texto visible en pantalla. Para usar esto, activá Estela en accesibilidad del sistema.",
                confidence = ConfidenceLevel.LOW
            )
        }

        val safeText = PrivacyGuard.sanitizeScreenText(screen)
        val prefix = if (context.preferShortAnswers) {
            "La pantalla dice:"
        } else {
            "La pantalla dice:"
        }

        return AiResult(
            spokenText = "$prefix $safeText",
            confidence = ConfidenceLevel.HIGH,
            safetyNotice = context.riskSummaries.firstOrNull()
        )
    }

    private fun describeSceneFallback() = AiResult(
        spokenText = "Para describir lo que tenés enfrente, todavía necesito IA avanzada. Por ahora puedo ayudarte con lectura local y acciones básicas.",
        confidence = ConfidenceLevel.LOW,
        safetyNotice = "La descripción visual avanzada todavía no está activada."
    )

    private fun composeMessageHint() = AiResult(
        spokenText = "Para preparar un mensaje, decí: mandale a un contacto que estoy llegando. No lo voy a enviar automáticamente.",
        confidence = ConfidenceLevel.MEDIUM
    )

    private fun emergencyHelp() = AiResult(
        spokenText = EmergencyPolicy().safeOfferText(),
        confidence = ConfidenceLevel.HIGH,
        safetyNotice = "En una emergencia real, priorizar servicios de emergencia o ayuda presencial."
    )

    private fun helpResponse() = AiResult(
        spokenText = VoiceHelpCenter.SPOKEN_HELP,
        confidence = ConfidenceLevel.HIGH
    )

    private fun unknownCommand() = AiResult(
        spokenText = "No entendí ese comando. Podés decir: qué puedo decir.",
        confidence = ConfidenceLevel.LOW
    )
}
