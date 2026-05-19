package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentRiskLevel

/**
 * Decisión pura del Situation Brain: "qué debería pasar después".
 *
 * Fase 2: el Brain produce una de estas decisiones pero NADIE la ejecuta
 * todavía. No abre apps, no habla por TTS, no llama use cases. La capa de
 * aplicación (Fase 3) será la que traduzca cada decisión a un efecto real.
 */
sealed class SituationDecision {

    /** No hay nada útil que hacer (comando vacío, ruido, control sin efecto). */
    data object Ignore : SituationDecision()

    /** Solo cambiar de estado, sin otro efecto. */
    data class ChangeState(
        val newState: SituationState
    ) : SituationDecision()

    /** Hay una acción lista que requiere confirmación humana explícita. */
    data class AskConfirmation(
        val prompt: String,
        val pendingAction: PendingAction,
        val nextState: SituationState = SituationState.WAITING_CONFIRMATION
    ) : SituationDecision()

    /**
     * Ejecutar una intención (la capa de aplicación elige el handler real).
     *
     * @param pendingAction si la ejecución viene de confirmar una acción
     *   pendiente, transporta una copia de esa acción (con su originalCommand /
     *   target / payload) para que la capa de aplicación pueda ejecutarla sin
     *   depender del "sí" del turno de confirmación. Null para ExecuteIntent
     *   directos (sin confirmación previa).
     */
    data class ExecuteIntent(
        val intent: SituationIntent,
        val reason: String,
        val nextState: SituationState,
        val pendingAction: PendingAction? = null
    ) : SituationDecision()

    /** Responder con un mensaje hablado. */
    data class Speak(
        val message: String,
        val nextState: SituationState = SituationState.SPEAKING
    ) : SituationDecision()

    /** Rechazo seguro: pedido sensible o transición imposible. */
    data class Reject(
        val reason: String,
        val riskLevel: AgentRiskLevel,
        val nextState: SituationState = SituationState.ERROR_RECOVERY
    ) : SituationDecision()

    /** Cancelación: el usuario cortó todo. */
    data class Cancel(
        val reason: String = "Cancelado",
        val hard: Boolean = true,
        val nextState: SituationState = SituationState.CANCELLED
    ) : SituationDecision()

    /** Continuar un objetivo de tarea que ya estaba vivo. */
    data class ContinueGoal(
        val goal: ActiveGoal,
        val nextState: SituationState
    ) : SituationDecision()

    /** Solo actualizar el contexto, sin un efecto hablado o externo. */
    data class UpdateContext(
        val context: SituationContext
    ) : SituationDecision()
}
