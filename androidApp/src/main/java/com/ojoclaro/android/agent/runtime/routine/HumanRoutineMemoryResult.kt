package com.ojoclaro.android.agent.runtime.routine

/**
 * Resultado de procesar un texto de voz con [HumanRoutineUseCase.handleVoice].
 *
 * Sealed para forzar manejo explícito en el caller. NotARoutineCommand significa
 * "no consumas este input — seguí tu flujo normal".
 */
sealed class HumanRoutineMemoryResult {

    object NotARoutineCommand : HumanRoutineMemoryResult()

    data class Saved(val spokenAck: String) : HumanRoutineMemoryResult()

    data class Forgotten(val spokenAck: String) : HumanRoutineMemoryResult()

    data class BlockedBySafety(
        val reason: String,
        val spokenText: String
    ) : HumanRoutineMemoryResult()

    data class LearningDisabled(val spokenAck: String) : HumanRoutineMemoryResult()

    data class LearningEnabled(val spokenAck: String) : HumanRoutineMemoryResult()
}
