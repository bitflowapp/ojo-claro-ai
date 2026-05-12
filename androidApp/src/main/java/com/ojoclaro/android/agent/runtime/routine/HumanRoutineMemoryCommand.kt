package com.ojoclaro.android.agent.runtime.routine

/**
 * Comando explícito de memoria/preferencia parseado desde el texto del usuario.
 *
 * Solo el parser ([HumanRoutineCommandParser]) construye estos. NotARoutineCommand
 * indica que el texto no era un comando de Routine Learning y el caller debe
 * seguir su flujo normal.
 */
sealed class HumanRoutineMemoryCommand {

    object NotARoutineCommand : HumanRoutineMemoryCommand()

    data class SetPreference(
        val key: String,
        val value: String
    ) : HumanRoutineMemoryCommand()

    data class SetFrequentContact(
        val name: String,
        val isPrimary: Boolean
    ) : HumanRoutineMemoryCommand()

    data class ForgetFrequentContact(
        val name: String
    ) : HumanRoutineMemoryCommand()

    data class SaveQuickMessage(
        val text: String
    ) : HumanRoutineMemoryCommand()

    object ForgetLastQuickMessage : HumanRoutineMemoryCommand()

    object ForgetAllPreferences : HumanRoutineMemoryCommand()

    object DisableLearning : HumanRoutineMemoryCommand()

    object EnableLearning : HumanRoutineMemoryCommand()
}
