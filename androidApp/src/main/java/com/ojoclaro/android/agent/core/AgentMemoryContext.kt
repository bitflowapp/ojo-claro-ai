package com.ojoclaro.android.agent.core

/**
 * Contexto de memoria que el planner puede leer en alto nivel.
 *
 * Se construye desde PersonalMemorySnapshot del usuario. NO contiene los strings
 * crudos de mensajes/conversaciones — solo metadata útil para tomar decisiones.
 */
data class AgentMemoryContext(
    val knownContactNames: List<String> = emptyList(),
    val knownPlaceAliases: List<String> = emptyList(),
    val frequentDestinations: List<String> = emptyList(),
    val frequentCommands: List<String> = emptyList(),
    val activePreferences: List<AgentUserPreference> = emptyList(),
    val hasEmergencyContact: Boolean = false,
    val learningOptedIn: Boolean = false
) {
    fun preference(key: String): AgentUserPreference? =
        activePreferences.firstOrNull { it.key == key }
}
