package com.ojoclaro.android.agent.core.generic

/**
 * Solicitud de ejecutar una acción genérica sobre una app de terceros.
 *
 * Esto NO se ejecuta directamente. Pasa por GenericAppSafetyGate y por
 * GenericAppExecutionPolicy antes de cualquier acción real. En v1, la única
 * forma en que esto resulta en algo es si la capacidad es OPEN_APP/READ_SCREEN/
 * SUMMARIZE_SCREEN/GUIDE_USER.
 */
data class GenericAppActionRequest(
    val packageName: String,
    val capability: GenericAppCapability,
    val description: String,
    val source: GenericAppRequestSource = GenericAppRequestSource.LOCAL_PARSER
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(description.isNotBlank()) { "description must not be blank" }
    }
}

enum class GenericAppRequestSource {
    LOCAL_PARSER,
    LLM_FALLBACK,
    USER_DIRECT
}
