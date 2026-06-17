package com.ojoclaro.android.agent.situation

/**
 * Accion que el Situation Brain ya pidio confirmar y el usuario confirmo.
 *
 * No representa texto crudo del usuario: es un handoff interno hacia la capa
 * legacy para evitar repetir la misma confirmacion cuando exista una ruta
 * segura de preparacion.
 */
data class SituationConfirmedAction(
    val intent: SituationIntent,
    val commandForExecution: String,
    val pendingAction: PendingAction,
    val alreadyConfirmed: Boolean = true
) {
    init {
        require(commandForExecution.isNotBlank()) { "commandForExecution must not be blank" }
        require(alreadyConfirmed) { "SituationConfirmedAction must be already confirmed" }
        require(situationIntentFromPendingAction(pendingAction) == intent) {
            "pendingAction intentName must match intent"
        }
    }
}
