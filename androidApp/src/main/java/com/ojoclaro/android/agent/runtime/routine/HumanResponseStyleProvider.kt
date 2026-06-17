package com.ojoclaro.android.agent.runtime.routine

/**
 * Lectura de solo-lectura del estilo actual de respuesta del usuario.
 *
 * Lo usa el HomeViewModel (vía RoutinePreferenceApplier) y, eventualmente,
 * SpeechController para velocidad de TTS. El provider NO escribe; las
 * escrituras pasan exclusivamente por HumanRoutineUseCase.
 */
fun interface HumanResponseStyleProvider {
    fun current(): HumanResponseStyle
}

/**
 * Implementación default que arma el [HumanResponseStyle] leyendo el store
 * de Routine Learning. Mapea string-values a enum values y cae al default
 * cuando la preferencia no está seteada.
 */
class StoreBackedHumanResponseStyleProvider(
    private val store: HumanRoutineMemoryStore
) : HumanResponseStyleProvider {

    override fun current(): HumanResponseStyle {
        val length = when (store.preference(RoutinePreferenceKeys.RESPONSE_LENGTH)?.value) {
            RoutinePreferenceValues.LENGTH_SHORT -> ResponseLength.SHORT
            RoutinePreferenceValues.LENGTH_NORMAL -> ResponseLength.NORMAL
            else -> ResponseLength.NORMAL
        }
        val speed = when (store.preference(RoutinePreferenceKeys.RESPONSE_SPEED)?.value) {
            RoutinePreferenceValues.SPEED_SLOW -> ResponseSpeed.SLOW
            RoutinePreferenceValues.SPEED_NORMAL -> ResponseSpeed.NORMAL
            else -> ResponseSpeed.NORMAL
        }
        val clarity = when (store.preference(RoutinePreferenceKeys.RESPONSE_CLARITY)?.value) {
            RoutinePreferenceValues.CLARITY_CLEAR -> ResponseClarity.CLEAR
            RoutinePreferenceValues.CLARITY_NORMAL -> ResponseClarity.NORMAL
            else -> ResponseClarity.NORMAL
        }
        return HumanResponseStyle(
            length = length,
            speed = speed,
            clarity = clarity
        )
    }
}
