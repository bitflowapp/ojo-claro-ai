package com.ojoclaro.android.voice

import com.ojoclaro.android.agent.core.runtime.BridgeDispatchOutcome

/**
 * Pieza pura que une [AgentBridgeVoiceMapper] con [VoiceFeedbackController].
 *
 * El caller (ej. `HomeViewModel`) entrega el [BridgeDispatchOutcome] crudo y
 * obtiene una [BridgeVoiceRoute] indicando si debe emitir voz, suprimirla por
 * dedup semántico, o dejar pasar el flujo legacy.
 *
 * Diseño:
 *  - Sin Android APIs.
 *  - Sin coroutines.
 *  - No invoca TTS de bajo nivel — sólo decide.
 *  - El [VoiceFeedbackController] interno aplica dedup por semanticKey/text
 *    dentro de la ventana configurada en cada [SpokenFeedback].
 */
class AgentBridgeVoiceCoordinator(
    private val controller: VoiceFeedbackController = VoiceFeedbackController(),
    private val mapper: (BridgeDispatchOutcome) -> SpokenFeedback? =
        AgentBridgeVoiceMapper::toSpokenFeedback
) {

    fun route(outcome: BridgeDispatchOutcome): BridgeVoiceRoute {
        val feedback = mapper(outcome) ?: return BridgeVoiceRoute.PassThrough
        val critical = feedback.force ||
            feedback.priority == SpokenFeedbackPriority.CRITICAL
        return when (val decision = controller.decide(feedback)) {
            is VoiceFeedbackDecision.Speak -> BridgeVoiceRoute.Speak(
                text = decision.text,
                force = critical,
                reason = decision.reason
            )
            is VoiceFeedbackDecision.Suppress -> BridgeVoiceRoute.Suppress(
                reason = decision.reason
            )
        }
    }

    fun resetMemory() {
        controller.resetMemory()
    }
}

/**
 * Resultado del enrutador de voz semántica para outcomes del Agent Bridge.
 *
 * - [Speak]: el caller debe emitir el `text` por el mecanismo de voz actual.
 * - [Suppress]: el caller NO debe emitir voz, pero sí actualizar la UI con el
 *   `speakText` del outcome original.
 * - [PassThrough]: el caller debe seguir el flujo legacy intacto (corresponde
 *   a `BridgeDispatchOutcome.FallbackToLegacy`).
 */
sealed class BridgeVoiceRoute {
    data class Speak(
        val text: String,
        val force: Boolean,
        val reason: String
    ) : BridgeVoiceRoute()

    data class Suppress(val reason: String) : BridgeVoiceRoute()

    object PassThrough : BridgeVoiceRoute()
}
