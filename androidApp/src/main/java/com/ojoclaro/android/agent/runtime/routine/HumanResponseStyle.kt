package com.ojoclaro.android.agent.runtime.routine

/**
 * Estilo de respuesta humano resuelto a partir de las preferencias del usuario.
 *
 * Inmutable. El [HumanResponseStyleProvider] recompone este objeto en cada
 * lectura desde el store, así que siempre refleja el estado más reciente.
 */
data class HumanResponseStyle(
    val length: ResponseLength,
    val speed: ResponseSpeed,
    val clarity: ResponseClarity
) {
    val isShort: Boolean get() = length == ResponseLength.SHORT
    val isSlow: Boolean get() = speed == ResponseSpeed.SLOW
    val isClear: Boolean get() = clarity == ResponseClarity.CLEAR

    companion object {
        val DEFAULT: HumanResponseStyle = HumanResponseStyle(
            length = ResponseLength.NORMAL,
            speed = ResponseSpeed.NORMAL,
            clarity = ResponseClarity.NORMAL
        )
    }
}

enum class ResponseLength { SHORT, NORMAL }

enum class ResponseSpeed {
    SLOW,
    NORMAL;

    /**
     * Multiplicador de velocidad para `TextToSpeech.setSpeechRate`. Aún no se
     * conecta a SpeechController por riesgo (ver reporte de la iteración).
     * Quedan disponibles los valores para el wire-up futuro.
     */
    val ttsRateMultiplier: Float
        get() = when (this) {
            SLOW -> 0.85f
            NORMAL -> 1.0f
        }
}

enum class ResponseClarity { CLEAR, NORMAL }
