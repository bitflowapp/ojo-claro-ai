package com.ojoclaro.android.agent.situation

/**
 * Efecto de UI puro derivado de una [SituationDecision]. NO depende de Android,
 * NO habla por TTS, NO abre apps. Solo describe "qué debería hacer la capa de
 * aplicación". HomeViewModel es quien traduce esto a efectos reales.
 */
sealed class SituationUiEffect {
    /** Nada que hacer; el caller puede caer al flujo viejo. */
    data object NoOp : SituationUiEffect()

    /** Decir un mensaje al usuario. */
    data class Speak(val message: String) : SituationUiEffect()

    /** Cancelar / cortar lo que esté en curso. */
    data class Cancel(val hard: Boolean, val reason: String) : SituationUiEffect()

    /** Cambiar de estado sin otro efecto. */
    data class ChangeState(val state: SituationState) : SituationUiEffect()

    /** Pedir confirmación de una acción pendiente. */
    data class AskConfirmation(
        val prompt: String,
        val pendingAction: PendingAction
    ) : SituationUiEffect()

    /** Rechazo seguro: decir el motivo, no ejecutar nada. */
    data class Reject(val reason: String) : SituationUiEffect()

    /**
     * Una intención lista para ejecutar. El applier NO la ejecuta: solo la
     * representa. La capa de aplicación (HomeViewModel) decide si la cablea a un
     * handler real o cae al flujo viejo.
     *
     * @param pendingAction copia de la acción pendiente confirmada (si la hubo),
     *   con su originalCommand / target / payload para ejecutarla sin el "sí".
     */
    data class Execute(
        val intent: SituationIntent,
        val reason: String,
        val nextState: SituationState,
        val pendingAction: PendingAction? = null
    ) : SituationUiEffect()

    /** La decisión existe pero todavía no está cableada en esta fase. */
    data class Unsupported(val reason: String) : SituationUiEffect()
}

/**
 * Traduce una [SituationDecision] a un [SituationUiEffect].
 *
 * Fase 5: ExecuteIntent se mapea a [SituationUiEffect.Execute] (la capa de
 * aplicación decide si lo cablea o no). ContinueGoal sigue como Unsupported.
 * El applier sigue siendo puro: no llama Android ni ejecuta use cases.
 */
class SituationDecisionApplier {

    fun toUiEffect(decision: SituationDecision): SituationUiEffect =
        when (decision) {
            is SituationDecision.Ignore ->
                SituationUiEffect.NoOp

            is SituationDecision.UpdateContext ->
                SituationUiEffect.NoOp

            is SituationDecision.Speak ->
                SituationUiEffect.Speak(decision.message)

            is SituationDecision.Cancel ->
                SituationUiEffect.Cancel(hard = decision.hard, reason = decision.reason)

            is SituationDecision.ChangeState ->
                SituationUiEffect.ChangeState(decision.newState)

            is SituationDecision.AskConfirmation ->
                SituationUiEffect.AskConfirmation(
                    prompt = decision.prompt,
                    pendingAction = decision.pendingAction
                )

            is SituationDecision.Reject ->
                SituationUiEffect.Reject(decision.reason)

            is SituationDecision.ContinueGoal ->
                SituationUiEffect.Unsupported("Continuación de objetivo todavía no cableada")

            is SituationDecision.ExecuteIntent ->
                SituationUiEffect.Execute(
                    intent = decision.intent,
                    reason = decision.reason,
                    nextState = decision.nextState,
                    pendingAction = decision.pendingAction
                )
        }
}
