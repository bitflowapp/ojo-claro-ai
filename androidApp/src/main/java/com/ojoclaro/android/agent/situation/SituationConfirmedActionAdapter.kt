package com.ojoclaro.android.agent.situation

/**
 * Adapter puro entre efectos de UI del Situation Brain y acciones confirmadas.
 *
 * La capa Android decide como ejecutar la accion; este objeto solo valida y
 * arma el contrato interno.
 */
object SituationConfirmedActionAdapter {

    fun fromExecuteEffect(effect: SituationUiEffect.Execute): SituationConfirmedAction? {
        val pending = effect.pendingAction ?: return null
        if (situationIntentFromPendingAction(pending) != effect.intent) return null

        val command = when (effect.intent) {
            SituationIntent.WRITE_MESSAGE -> {
                if (!SituationMessageSafety.isSafeWriteMessagePendingAction(pending)) return null
                pending.messageCommandForExecution()
            }
            SituationIntent.OPEN_APP,
            SituationIntent.CALL_CONTACT -> pending.commandForExecution()
            else -> return null
        }.takeIf { it.isNotBlank() } ?: return null

        return SituationConfirmedAction(
            intent = effect.intent,
            commandForExecution = command,
            pendingAction = pending,
            alreadyConfirmed = true
        )
    }
}
