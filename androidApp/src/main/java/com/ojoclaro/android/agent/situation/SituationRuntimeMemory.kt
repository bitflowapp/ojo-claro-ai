package com.ojoclaro.android.agent.situation

/**
 * Snapshot efímero de la memoria runtime del Situation Brain.
 *
 * Solo conserva campos seguros para sobrevivir entre comandos. NO guarda
 * rawCommand, normalizedCommand, screenContext ni texto crudo de pantalla.
 * lastAssistantMessage y recentTurns ya vienen resumidos/truncados por
 * [SituationContext].
 */
data class SituationRuntimeSnapshot(
    val activeGoal: ActiveGoal? = null,
    val pendingAction: PendingAction? = null,
    val companionModeActive: Boolean = false,
    val lastAssistantMessage: String = "",
    val recentTurns: List<TurnSummary> = emptyList(),
    val situationState: SituationState = SituationState.IDLE,
    val mutedThroughRequestId: Long = 0L
) {
    init {
        require(recentTurns.size <= MAX_RECENT_TURNS) {
            "recentTurns must not exceed $MAX_RECENT_TURNS elements"
        }
        require(mutedThroughRequestId >= 0L) { "mutedThroughRequestId must be >= 0" }
    }

    /** True si el snapshot es el vacío por defecto (sin memoria acumulada). */
    fun isEmpty(): Boolean = this == empty()

    companion object {
        const val MAX_RECENT_TURNS = 5

        fun empty(): SituationRuntimeSnapshot = SituationRuntimeSnapshot()
    }
}

/**
 * Memoria runtime mínima del Situation Brain: vive en RAM, no persiste a disco.
 *
 * Si el proceso muere, la memoria se pierde — y eso es correcto: preferimos un
 * Brain amnésico entre sesiones antes que ejecutar tareas viejas al volver.
 *
 * Pura, sin APIs de Android. Mutable pero encapsulada: solo expone copias
 * inmutables del snapshot.
 */
class SituationRuntimeMemory {

    private var snapshot: SituationRuntimeSnapshot = SituationRuntimeSnapshot.empty()

    /** Snapshot actual (inmutable). */
    fun current(): SituationRuntimeSnapshot = snapshot

    /**
     * Copia desde un [SituationContext] solo los campos seguros que deben
     * sobrevivir al próximo comando.
     */
    fun updateFrom(context: SituationContext) {
        snapshot = SituationRuntimeSnapshot(
            activeGoal = context.activeGoal,
            pendingAction = context.pendingAction,
            companionModeActive = context.companionModeActive,
            lastAssistantMessage = context.lastAssistantMessage,
            recentTurns = context.recentTurns.takeLast(SituationRuntimeSnapshot.MAX_RECENT_TURNS),
            situationState = context.situationState,
            mutedThroughRequestId = context.mutedThroughRequestId
        )
    }

    /**
     * Limpia la memoria tras una cancelación: borra objetivo, acción pendiente,
     * modo compañero y turnos recientes. Conserva el estado y el corte de
     * respuestas viejas que trae el contexto ya cancelado.
     */
    fun clearForCancellation(context: SituationContext) {
        snapshot = snapshot.copy(
            activeGoal = null,
            pendingAction = null,
            companionModeActive = false,
            recentTurns = emptyList(),
            situationState = context.situationState,
            mutedThroughRequestId = maxOf(
                snapshot.mutedThroughRequestId,
                context.mutedThroughRequestId
            )
        )
    }

    /** Borra el objetivo activo si ya venció su TTL. */
    fun clearExpiredGoals(now: Long) {
        val goal = snapshot.activeGoal ?: return
        if (goal.isExpired(now)) {
            snapshot = snapshot.copy(activeGoal = null)
        }
    }

    /** Actualiza solo el último mensaje hablado del asistente. */
    fun rememberAssistantMessage(message: String) {
        snapshot = snapshot.copy(lastAssistantMessage = message)
    }

    /**
     * Olvida solo la acción pendiente, sin tocar el resto de la memoria.
     * Útil cuando la capa de aplicación decide no cablear esa confirmación.
     */
    fun forgetPendingAction() {
        snapshot = snapshot.copy(pendingAction = null)
    }

    /** Vuelve la memoria al estado vacío. */
    fun reset() {
        snapshot = SituationRuntimeSnapshot.empty()
    }
}
