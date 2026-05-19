package com.ojoclaro.android.voice

import com.ojoclaro.android.agent.core.runtime.BridgeDispatchKind
import com.ojoclaro.android.agent.core.runtime.BridgeDispatchOutcome

/**
 * Adapter puro que traduce un [BridgeDispatchOutcome] del Agent Bridge a una
 * [SpokenFeedback] semántica consumible por [VoiceFeedbackController].
 *
 * Reglas:
 *  - [BridgeDispatchOutcome.FallbackToLegacy] -> `null` (el legacy decide).
 *  - Para cada [BridgeDispatchKind] se asigna `semanticKey`, `category` y
 *    `priority` estables. Esto permite dedup semántico aunque el `speakText`
 *    cambie ligeramente entre llamadas.
 *  - Las acciones que el usuario debe oír sí o sí (REJECTED, EXPIRED) se
 *    emiten con `force = true` para sortear el dedup.
 *  - NUNCA afirma ejecución real: el bridge sólo confirma autorización.
 *    Las frases forbidden están validadas por AgentBridgeVoiceMapperTest.
 *
 * Sin Android APIs. Sin coroutines. Sin TTS. 100% testeable.
 */
object AgentBridgeVoiceMapper {

    private const val PENDING_DEFAULT: String =
        "Esta acción requiere confirmación. Decime confirmar o cancelar."
    private const val CONFIRMED_DEFAULT: String =
        "Confirmado. La acción quedó autorizada."
    private const val CANCELLED_DEFAULT: String = "Cancelado."
    private const val REJECTED_DEFAULT: String = "No puedo hacer esa acción."
    private const val NEEDS_SLOT_DEFAULT: String = "Necesito un dato más para seguir."
    private const val NO_PENDING_DEFAULT: String =
        "No hay ninguna acción pendiente para confirmar."
    private const val EXPIRED_DEFAULT: String =
        "La confirmación venció. Volvé a pedir la acción."
    private const val READY_DEFAULT: String = "Acción autorizada."

    fun toSpokenFeedback(outcome: BridgeDispatchOutcome): SpokenFeedback? =
        when (outcome) {
            is BridgeDispatchOutcome.FallbackToLegacy -> null
            is BridgeDispatchOutcome.Handled -> handleHandled(outcome)
        }

    private fun handleHandled(outcome: BridgeDispatchOutcome.Handled): SpokenFeedback {
        val bridgeText = outcome.speakText.trim()
        val prompt = outcome.pendingPrompt?.trim().orEmpty()

        return when (outcome.kind) {
            BridgeDispatchKind.PENDING -> SpokenFeedback(
                semanticKey = "agent.confirmation.required",
                text = bridgeText.ifBlank { PENDING_DEFAULT },
                category = SpokenFeedbackCategory.CONFIRMATION_REQUIRED,
                priority = SpokenFeedbackPriority.HIGH
            )

            BridgeDispatchKind.CONFIRMED -> SpokenFeedback(
                semanticKey = "agent.confirmation.confirmed",
                text = bridgeText.ifBlank { CONFIRMED_DEFAULT },
                category = SpokenFeedbackCategory.CONFIRMED,
                priority = SpokenFeedbackPriority.NORMAL
            )

            BridgeDispatchKind.CANCELLED -> SpokenFeedback(
                semanticKey = "agent.confirmation.cancelled",
                text = bridgeText.ifBlank { CANCELLED_DEFAULT },
                category = SpokenFeedbackCategory.CANCELLED,
                priority = SpokenFeedbackPriority.NORMAL
            )

            BridgeDispatchKind.REJECTED -> SpokenFeedback(
                semanticKey = "agent.action.rejected",
                text = bridgeText.ifBlank { REJECTED_DEFAULT },
                category = SpokenFeedbackCategory.REJECTED,
                priority = SpokenFeedbackPriority.HIGH,
                force = true
            )

            BridgeDispatchKind.NEEDS_SLOT -> SpokenFeedback(
                semanticKey = "agent.needs.slot",
                text = prompt.ifBlank { bridgeText.ifBlank { NEEDS_SLOT_DEFAULT } },
                category = SpokenFeedbackCategory.NEEDS_SLOT,
                priority = SpokenFeedbackPriority.HIGH
            )

            BridgeDispatchKind.NO_PENDING -> SpokenFeedback(
                semanticKey = "agent.confirmation.no_pending",
                text = bridgeText.ifBlank { NO_PENDING_DEFAULT },
                category = SpokenFeedbackCategory.INFO,
                priority = SpokenFeedbackPriority.NORMAL
            )

            BridgeDispatchKind.EXPIRED -> SpokenFeedback(
                semanticKey = "agent.confirmation.expired",
                text = bridgeText.ifBlank { EXPIRED_DEFAULT },
                category = SpokenFeedbackCategory.INFO,
                priority = SpokenFeedbackPriority.HIGH,
                force = true
            )

            BridgeDispatchKind.READY -> SpokenFeedback(
                semanticKey = "agent.action.ready",
                text = bridgeText.ifBlank { READY_DEFAULT },
                category = SpokenFeedbackCategory.INFO,
                priority = SpokenFeedbackPriority.NORMAL
            )
        }
    }
}
