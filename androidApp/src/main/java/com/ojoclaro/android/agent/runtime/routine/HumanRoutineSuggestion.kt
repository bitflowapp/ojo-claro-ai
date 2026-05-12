package com.ojoclaro.android.agent.runtime.routine

/**
 * Sugerencia que el sistema le hace al usuario para que apruebe (o descarte)
 * el guardado de un patrón observado.
 *
 *  - [confirmationId]: id estable que el caller debe pasar a
 *    HumanRoutineUseCase.confirmSuggestion(...) o discardSuggestion(...).
 *  - [spokenPrompt]: texto fijo (sin contenido privado) que se le habla al
 *    usuario.
 */
data class HumanRoutineSuggestion(
    val confirmationId: String,
    val candidate: HumanRoutineCandidate,
    val spokenPrompt: String
) {
    init {
        require(confirmationId.isNotBlank())
        require(spokenPrompt.isNotBlank())
    }
}
