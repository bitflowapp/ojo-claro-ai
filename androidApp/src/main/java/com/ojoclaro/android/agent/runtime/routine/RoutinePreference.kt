package com.ojoclaro.android.agent.runtime.routine

/**
 * Preferencia segura del usuario, dicha explícitamente.
 *
 * Solo claves del whitelist [RoutinePreferenceKeys] se aceptan; cualquier
 * otra clave la rechaza el HumanRoutineLearningPolicy.
 */
data class RoutinePreference(
    val key: String,
    val value: String,
    val updatedAtMillis: Long
) {
    init {
        require(key.isNotBlank()) { "preference key must not be blank" }
        require(value.isNotBlank()) { "preference value must not be blank" }
        require(key.length <= MAX_KEY_LENGTH)
        require(value.length <= MAX_VALUE_LENGTH)
    }

    companion object {
        const val MAX_KEY_LENGTH: Int = 60
        const val MAX_VALUE_LENGTH: Int = 60
    }
}

/**
 * Whitelist de claves de preferencia. Cualquier otra clave es rechazada por
 * el policy — eso evita que un futuro caller (o LLM) inyecte claves
 * arbitrarias como "password" o similares.
 */
object RoutinePreferenceKeys {
    const val RESPONSE_LENGTH: String = "response.length"
    const val RESPONSE_SPEED: String = "response.speed"
    const val RESPONSE_CLARITY: String = "response.clarity"

    val ALL: Set<String> = setOf(RESPONSE_LENGTH, RESPONSE_SPEED, RESPONSE_CLARITY)
}

object RoutinePreferenceValues {
    const val LENGTH_SHORT: String = "short"
    const val LENGTH_NORMAL: String = "normal"
    const val SPEED_NORMAL: String = "normal"
    const val SPEED_SLOW: String = "slow"
    const val CLARITY_NORMAL: String = "normal"
    const val CLARITY_CLEAR: String = "clear"

    val LENGTH_VALUES: Set<String> = setOf(LENGTH_SHORT, LENGTH_NORMAL)
    val SPEED_VALUES: Set<String> = setOf(SPEED_NORMAL, SPEED_SLOW)
    val CLARITY_VALUES: Set<String> = setOf(CLARITY_NORMAL, CLARITY_CLEAR)

    fun isValid(key: String, value: String): Boolean = when (key) {
        RoutinePreferenceKeys.RESPONSE_LENGTH -> value in LENGTH_VALUES
        RoutinePreferenceKeys.RESPONSE_SPEED -> value in SPEED_VALUES
        RoutinePreferenceKeys.RESPONSE_CLARITY -> value in CLARITY_VALUES
        else -> false
    }
}
