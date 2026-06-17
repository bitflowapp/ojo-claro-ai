package com.ojoclaro.android.agent.runtime.routine

/**
 * Acumulador de observaciones del mismo [kind].
 *
 * Cuando [occurrenceCount] cruza un umbral (definido por
 * [HumanRoutineLearningPolicy.minimumOccurrencesToSuggest]), el use case
 * puede generar una sugerencia. NUNCA persiste solo, siempre pide confirmación.
 */
data class HumanRoutineCandidate(
    val kind: String,
    val labelHint: String,
    val occurrenceCount: Int,
    val firstObservedAtMillis: Long,
    val lastObservedAtMillis: Long
) {
    init {
        require(kind.isNotBlank())
        require(labelHint.isNotBlank())
        require(occurrenceCount >= 1)
        require(lastObservedAtMillis >= firstObservedAtMillis)
    }
}
