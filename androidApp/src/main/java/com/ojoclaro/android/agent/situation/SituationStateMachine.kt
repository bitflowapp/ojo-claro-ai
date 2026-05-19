package com.ojoclaro.android.agent.situation

/**
 * Máquina de estados pura del Situation Brain.
 *
 * Define qué transiciones entre [SituationState] son legales. No tiene estado
 * propio ni dependencias de Android: recibe un contexto, valida, y devuelve una
 * copia con el nuevo estado.
 *
 * Reglas:
 *  - CANCELLED y ERROR_RECOVERY son alcanzables desde cualquier otro estado
 *    (escape de emergencia / recuperación).
 *  - Desde CANCELLED solo se puede ir a IDLE (estado de salida).
 *  - Desde WAITING_CONFIRMATION solo se puede ir a EXECUTING_GUIDED_ACTION,
 *    CANCELLED o ERROR_RECOVERY.
 *  - El resto sigue el grafo de transiciones normales declarado abajo.
 */
object SituationStateMachine {

    /**
     * Transiciones "normales" permitidas. Las reglas de escape (CANCELLED /
     * ERROR_RECOVERY) y las restricciones de CANCELLED y WAITING_CONFIRMATION
     * se aplican aparte en [canTransition].
     */
    private val NORMAL_TRANSITIONS: Map<SituationState, Set<SituationState>> = mapOf(
        SituationState.IDLE to setOf(SituationState.LISTENING),
        SituationState.LISTENING to setOf(SituationState.UNDERSTANDING),
        SituationState.UNDERSTANDING to setOf(
            SituationState.READING_SCREEN,
            SituationState.PLANNING,
            SituationState.WAITING_CONFIRMATION,
            SituationState.SPEAKING
        ),
        SituationState.READING_SCREEN to setOf(
            SituationState.PLANNING,
            SituationState.SPEAKING
        ),
        SituationState.PLANNING to setOf(
            SituationState.WAITING_CONFIRMATION,
            SituationState.EXECUTING_GUIDED_ACTION,
            SituationState.SPEAKING
        ),
        // WAITING_CONFIRMATION se maneja como caso especial en canTransition.
        SituationState.WAITING_CONFIRMATION to emptySet(),
        SituationState.EXECUTING_GUIDED_ACTION to setOf(
            SituationState.SPEAKING,
            SituationState.IDLE
        ),
        SituationState.SPEAKING to setOf(
            SituationState.IDLE,
            SituationState.LISTENING
        ),
        // CANCELLED se maneja como caso especial en canTransition.
        SituationState.CANCELLED to emptySet(),
        SituationState.ERROR_RECOVERY to setOf(
            SituationState.LISTENING,
            SituationState.IDLE
        )
    )

    /** True si la transición [from] -> [to] es legal. */
    fun canTransition(from: SituationState, to: SituationState): Boolean {
        // Regla 1: desde CANCELLED solo se puede ir a IDLE.
        if (from == SituationState.CANCELLED) {
            return to == SituationState.IDLE
        }
        // Regla 2: CANCELLED y ERROR_RECOVERY son alcanzables desde cualquier
        // otro estado (escape de emergencia / recuperación).
        if (to == SituationState.CANCELLED || to == SituationState.ERROR_RECOVERY) {
            return true
        }
        // Regla 3: WAITING_CONFIRMATION solo puede ir a EXECUTING_GUIDED_ACTION
        // (CANCELLED y ERROR_RECOVERY ya quedaron cubiertos por la Regla 2).
        if (from == SituationState.WAITING_CONFIRMATION) {
            return to == SituationState.EXECUTING_GUIDED_ACTION
        }
        // Regla 4: transiciones normales declaradas en el grafo.
        return to in NORMAL_TRANSITIONS.getOrDefault(from, emptySet())
    }

    /**
     * Aplica una transición al [context]. Si la transición no es legal, lanza
     * [IllegalStateException].
     */
    fun transition(context: SituationContext, to: SituationState): SituationContext {
        val from = context.situationState
        check(canTransition(from, to)) {
            "Illegal situation transition: $from -> $to"
        }
        return context.withState(to)
    }
}
